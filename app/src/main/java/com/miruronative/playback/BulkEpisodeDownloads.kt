package com.miruronative.playback

import android.content.Context
import com.miruronative.data.AppGraph
import com.miruronative.data.model.Category
import com.miruronative.data.model.EpisodeItem
import com.miruronative.data.model.EpisodesResult
import com.miruronative.data.settings.DownloadQuality
import com.miruronative.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a batch stopped, for the line the UI shows when it is over. */
enum class BulkDownloadOutcome { RUNNING, FINISHED, CANCELLED, OUT_OF_SPACE }

data class BulkDownloadProgress(
    val seriesTitle: String,
    val total: Int,
    val queued: Int = 0,
    val failed: Int = 0,
    /** Episode label currently being resolved, so the UI can say more than a bare count. */
    val current: String? = null,
    val outcome: BulkDownloadOutcome = BulkDownloadOutcome.RUNNING,
) {
    val done: Int get() = queued + failed
    val isRunning: Boolean get() = outcome == BulkDownloadOutcome.RUNNING
}

/**
 * Queues a run of episodes for offline download, one at a time.
 *
 * Sources are resolved **as each episode reaches the front of the queue**, never upfront. Several
 * providers hand out URLs that are short-lived, IP-bound or single-use, so resolving forty
 * episodes in advance would leave most of the batch holding links that expired before the
 * downloader reached them. It also means a batch costs one provider request per episode rather
 * than a burst of forty, which is what a scraper would rate-limit.
 *
 * Only one batch runs at a time; starting another replaces it.
 */
object BulkEpisodeDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _progress = MutableStateFlow<BulkDownloadProgress?>(null)
    val progress = _progress.asStateFlow()

    /** Episodes after (and including) [fromNumber] that are not already downloaded. */
    fun pendingEpisodes(
        episodes: List<EpisodeItem>,
        anilistId: Int,
        category: String,
        fromNumber: Double?,
        alreadyHave: Set<String>,
    ): List<EpisodeItem> = episodes
        .asSequence()
        .filter { fromNumber == null || it.number >= fromNumber }
        .filterNot { EpisodeDownloads.idFor(anilistId, category, it.displayNumber) in alreadyHave }
        .toList()

    fun cancel() {
        job?.cancel()
        job = null
        _progress.value = _progress.value?.copy(
            current = null,
            outcome = BulkDownloadOutcome.CANCELLED,
        )
    }

    /** Clears a finished batch's status line. */
    fun dismiss() {
        if (_progress.value?.isRunning != true) _progress.value = null
    }

    fun start(
        context: Context,
        anilistId: Int,
        seriesTitle: String,
        artworkUrl: String?,
        category: Category,
        preferredProvider: String,
        episodes: List<EpisodeItem>,
        catalog: EpisodesResult,
        quality: DownloadQuality,
    ) {
        if (episodes.isEmpty()) return
        job?.cancel()
        val app = context.applicationContext
        _progress.value = BulkDownloadProgress(seriesTitle = seriesTitle, total = episodes.size)
        DiagnosticsLog.event(
            "Bulk download start id=$anilistId episodes=${episodes.size} category=${category.api}",
        )

        job = scope.launch {
            var queued = 0
            var failed = 0
            try {
                for (episode in episodes) {
                    // Checked per episode, not once upfront: the batch itself is what fills the
                    // disk, and a stick can run dry three episodes in.
                    if (!DownloadStorage.hasRoomFor(app, quality.maxHeight)) {
                        DiagnosticsLog.event("Bulk download stopped: out of space after $queued")
                        _progress.value = _progress.value?.copy(
                            queued = queued,
                            failed = failed,
                            current = null,
                            outcome = BulkDownloadOutcome.OUT_OF_SPACE,
                        )
                        return@launch
                    }
                    _progress.value = _progress.value?.copy(
                        queued = queued,
                        failed = failed,
                        current = episode.displayNumber,
                    )

                    val ok = runCatching {
                        queueOne(app, anilistId, seriesTitle, artworkUrl, category, preferredProvider, episode, catalog, quality)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        DiagnosticsLog.throwable(
                            "Bulk download failed episode=${episode.displayNumber}",
                            error,
                        )
                    }.getOrDefault(false)

                    if (ok) queued++ else failed++

                    // A short breather between provider hits; a tight loop over a scraper is the
                    // fastest way to get the whole batch rate-limited.
                    delay(BETWEEN_EPISODES_MS)
                }
                DiagnosticsLog.event("Bulk download finished queued=$queued failed=$failed")
                _progress.value = _progress.value?.copy(
                    queued = queued,
                    failed = failed,
                    current = null,
                    outcome = BulkDownloadOutcome.FINISHED,
                )
            } catch (e: CancellationException) {
                DiagnosticsLog.event("Bulk download cancelled queued=$queued failed=$failed")
                throw e
            }
        }
    }

    /** Resolves one episode and hands it to the downloader. Returns false if nothing was playable. */
    private suspend fun queueOne(
        context: Context,
        anilistId: Int,
        seriesTitle: String,
        artworkUrl: String?,
        category: Category,
        preferredProvider: String,
        episode: EpisodeItem,
        catalog: EpisodesResult,
        quality: DownloadQuality,
    ): Boolean {
        val resolution = AppGraph.repository.resolveSources(
            anilistId = anilistId,
            number = episode.number,
            preferred = preferredProvider,
            category = category,
            episodes = catalog,
        )
        val resolved = resolution.resolved ?: return false
        val stream = resolved.sources.streams.firstOrNull(EpisodeDownloads::canDownload)
            ?: return false

        val metadata = EpisodeDownloadMetadata(
            anilistId = anilistId,
            seriesTitle = seriesTitle,
            episodeNumber = episode.displayNumber,
            episodeTitle = episode.title,
            artworkUrl = artworkUrl,
            provider = resolved.provider,
            category = category.api,
            referer = stream.referer,
            headers = stream.headers,
            subtitles = resolved.sources.subtitles.map {
                EpisodeDownloadSubtitle(url = it.url, label = it.label, language = it.language)
            },
        )
        EpisodeDownloads.enqueue(context, metadata, stream, quality)
        return true
    }

    private const val BETWEEN_EPISODES_MS = 1_200L

    /**
     * Batch sizes offered in the download dialog.
     *
     * Deliberately bounded rather than "all of them". A long-runner like Naruto Shippuden has 500
     * episodes; queueing the lot is roughly 200 GB, hours of provider requests, and nothing a
     * viewer can meaningfully supervise. A viewer who wants more simply starts another batch.
     */
    val BATCH_SIZES = listOf(5, 10, 25)
}

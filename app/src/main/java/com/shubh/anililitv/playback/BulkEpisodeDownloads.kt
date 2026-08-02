package com.shubh.anililitv.playback

import android.content.Context
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.data.model.Category
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.data.model.EpisodesResult
import com.shubh.anililitv.data.settings.DownloadQuality
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BulkDownloadOutcome { RUNNING, FINISHED, CANCELLED, OUT_OF_SPACE }

data class BulkDownloadProgress(
    val seriesTitle: String,
    val total: Int,
    val queued: Int = 0,
    val failed: Int = 0,
    val current: String? = null,
    val outcome: BulkDownloadOutcome = BulkDownloadOutcome.RUNNING,
) {
    val done: Int get() = queued + failed
    val isRunning: Boolean get() = outcome == BulkDownloadOutcome.RUNNING
}

object BulkEpisodeDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _progress = MutableStateFlow<BulkDownloadProgress?>(null)
    val progress = _progress.asStateFlow()

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

    val BATCH_SIZES = listOf(5, 10, 25)
}

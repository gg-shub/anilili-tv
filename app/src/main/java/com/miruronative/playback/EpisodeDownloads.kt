package com.miruronative.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebSettings
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.miruronative.data.model.StreamItem
import com.miruronative.data.model.SubtitleItem
import com.miruronative.data.settings.DownloadQuality
import com.miruronative.MainActivity
import com.miruronative.R
import com.miruronative.diagnostics.DiagnosticsLog
import com.miruronative.ui.nav.Routes
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class EpisodeDownloadSubtitle(
    val url: String,
    val label: String,
    val language: String,
    val fileName: String? = null,
)

@Serializable
data class EpisodeDownloadMetadata(
    val anilistId: Int,
    val seriesTitle: String,
    val episodeNumber: String,
    val episodeTitle: String? = null,
    val artworkUrl: String? = null,
    val provider: String,
    val category: String,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<EpisodeDownloadSubtitle> = emptyList(),
    val quality: String? = null,
    val streamType: String? = null,
)

enum class EpisodeDownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    REMOVING,
    RESTARTING,
    STOPPED,
}

data class EpisodeDownload(
    val id: String,
    val uri: String,
    val metadata: EpisodeDownloadMetadata,
    val state: EpisodeDownloadState,
    val percent: Float?,
    val bytesDownloaded: Long,
    val contentLength: Long?,
    val updatedAtMs: Long,
) {
    val isComplete: Boolean get() = state == EpisodeDownloadState.COMPLETED

    /** Adaptive downloads are a manifest plus segments; direct ones are already a single file. */
    val isAdaptive: Boolean get() = metadata.streamType?.equals("hls", true)
        ?: uri.contains(".m3u8", ignoreCase = true)

    val isActive: Boolean get() = state in setOf(
        EpisodeDownloadState.QUEUED,
        EpisodeDownloadState.DOWNLOADING,
        EpisodeDownloadState.RESTARTING,
        EpisodeDownloadState.STOPPED,
    )
}

/**
 * Owns Media3's persistent download index and non-evicting download cache.
 *
 * Download request metadata contains the HTTP profile needed by the provider. A resolving data
 * source applies that profile to the manifest and every child playlist/segment request, including
 * child URLs hosted on a different CDN. Downloads are deliberately serialized so the active
 * profile is unambiguous.
 */
@OptIn(UnstableApi::class)
object EpisodeDownloads {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloads = MutableStateFlow<List<EpisodeDownload>>(emptyList())
    private val _preparingIds = MutableStateFlow<Set<String>>(emptySet())
    val preparingIds: StateFlow<Set<String>> = _preparingIds.asStateFlow()

    private val metadataById = ConcurrentHashMap<String, EpisodeDownloadMetadata>()
    private val manifestIdByUri = ConcurrentHashMap<String, String>()
    private val manifestIdByHost = ConcurrentHashMap<String, String>()
    private val requestByUri = ConcurrentHashMap<String, DownloadRequest>()
    private val subtitleJobs = ConcurrentHashMap<String, Job>()

    @Volatile private var initialized = false
    @Volatile private var activeDownloadId: String? = null
    @Volatile private var preparingDownloadId: String? = null
    private var progressPoller: Job? = null

    private lateinit var appContext: Context
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var downloadCache: SimpleCache
    private lateinit var upstreamFactory: DataSource.Factory
    private lateinit var manager: DownloadManager
    private lateinit var userAgent: String
    private val subtitleClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun downloads(context: Context): StateFlow<List<EpisodeDownload>> {
        initialize(context)
        return _downloads.asStateFlow()
    }

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            databaseProvider = ExoDatabase.provider(appContext)
            downloadCache = SimpleCache(
                File(appContext.filesDir, DOWNLOAD_DIRECTORY),
                NoOpCacheEvictor(),
                databaseProvider,
            )
            userAgent = runCatching {
                WebSettings.getDefaultUserAgent(appContext).replace("; wv", "")
            }.getOrDefault(FALLBACK_USER_AGENT)
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
            upstreamFactory = ResolvingDataSource.Factory(
                httpFactory,
                ResolvingDataSource.Resolver { dataSpec ->
                    val headers = requestHeadersFor(dataSpec.uri)
                    if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
                },
            )
            manager = DownloadManager(
                appContext,
                databaseProvider,
                downloadCache,
                upstreamFactory,
                Runnable::run,
            ).apply {
                maxParallelDownloads = 1
                addListener(downloadListener)
            }
            initialized = true
            refreshDownloads()
            DiagnosticsLog.event("EpisodeDownloads initialized")
            // Safe to call from inside the lock: this object is already flagged initialized, so
            // the export observer's own call back into here returns before reaching it.
            EpisodeExport.initialize(appContext)
        }
    }

    fun getDownloadManager(context: Context): DownloadManager {
        initialize(context)
        return manager
    }

    fun downloadCache(context: Context): Cache {
        initialize(context)
        return downloadCache
    }

    /**
     * Adds the download cache in front of the normal streaming source without writing new playback
     * traffic into it. Media3 will therefore play a completed download offline while ordinary
     * streams continue to use the app's bounded playback cache.
     */
    fun readOnlyPlaybackFactory(
        context: Context,
        upstream: DataSource.Factory,
    ): DataSource.Factory = CacheDataSource.Factory()
        .setCache(downloadCache(context))
        .setUpstreamDataSourceFactory(upstream)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun idFor(anilistId: Int, category: String, episodeNumber: String): String =
        "episode:$anilistId:${category.trim().lowercase()}:${episodeNumber.trim()}"

    fun canDownload(stream: StreamItem?): Boolean =
        stream != null &&
            !stream.isEmbed &&
            stream.playlistKey == null &&
            (stream.isHls || stream.isDirectFile())

    /**
     * Whether the Downloads folder can receive this episode — the same set as [canDownload],
     * because everything now reaches it the same way: into the library cache first, then rewrapped
     * into a single MP4 by [EpisodeExport].
     *
     * Handing direct files to the system downloader instead would be one pass rather than two, but
     * it produces a file the app has no record of (and leaves .mkv/.webm sources unconverted), so
     * the episode would vanish from the offline library. One route keeps the library honest.
     *
     * Callers must still check for Android 10, which is where MediaStore's Downloads collection
     * arrived.
     */
    fun canSaveToDevice(stream: StreamItem?): Boolean = canDownload(stream)

    /**
     * Prepares the adaptive manifest first so Media3 records the selected HLS stream keys instead
     * of blindly downloading every rendition in a multivariant playlist.
     */
    fun enqueue(
        context: Context,
        metadata: EpisodeDownloadMetadata,
        stream: StreamItem,
        quality: DownloadQuality = DownloadQuality.BEST,
        onResult: (Result<Unit>) -> Unit = {},
    ) {
        initialize(context)
        if (!canDownload(stream)) {
            onResult(Result.failure(IllegalArgumentException("This stream cannot be downloaded")))
            return
        }
        val id = idFor(metadata.anilistId, metadata.category, metadata.episodeNumber)
        val preparedMetadata = metadata.copy(
            // Providers routinely list the same track twice — BLACK TORCH offers two identical
            // "English" tracks. Downloading both wastes a request and, once exported, drops two
            // sidecars next to the MP4 that the viewer has no way to tell apart.
            subtitles = metadata.subtitles
                .distinctBy { it.language.trim().lowercase() to it.label.trim().lowercase() }
                .mapIndexed { index, subtitle ->
                    subtitle.copy(fileName = subtitle.fileName ?: subtitleFileName(id, index, subtitle.url))
                },
            quality = if (stream.isHls) quality.label else stream.label,
            streamType = if (stream.isHls) "hls" else "direct",
        )
        metadataById[id] = preparedMetadata
        manifestIdByUri[stream.url] = id
        Uri.parse(stream.url).host?.let { manifestIdByHost[it] = id }
        preparingDownloadId = id
        _preparingIds.value += id

        if (!stream.isHls) {
            val result = runCatching {
                val data = json.encodeToString(preparedMetadata).encodeToByteArray()
                val request = DownloadRequest.Builder(id, Uri.parse(stream.url))
                    .setMimeType(directFileFormat(stream).mimeType)
                    .setData(data)
                    .build()
                requestByUri[request.uri.toString()] = request
                DownloadService.sendAddDownload(
                    appContext,
                    EpisodeDownloadService::class.java,
                    request,
                    false,
                )
                DiagnosticsLog.event(
                    "Direct episode download queued id=$id provider=${preparedMetadata.provider} " +
                        "host=${request.uri.host ?: "unknown"} quality=${preparedMetadata.quality}",
                )
                downloadSubtitleFiles(id, preparedMetadata)
            }
            finishPreparing(id)
            onResult(result)
            return
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setUri(stream.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        val helper = DownloadHelper.Factory()
            .setDataSourceFactory(upstreamFactory)
            .setRenderersFactory(DefaultRenderersFactory(appContext))
            .create(mediaItem)
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                val result = runCatching {
                    val trackParameters = TrackSelectionParameters.Builder()
                        .setForceHighestSupportedBitrate(true)
                        .apply {
                            quality.maxHeight?.let { height ->
                                setMaxVideoSize(Int.MAX_VALUE, height)
                            }
                        }
                        .build()
                    for (periodIndex in 0 until helper.periodCount) {
                        helper.replaceTrackSelections(periodIndex, trackParameters)
                    }
                    // The chosen resolution is a ceiling, not a promise: a source offering only a
                    // single 1080p rendition still yields 1080p. Record what was actually selected,
                    // otherwise the library and the exported filename both claim a resolution the
                    // file does not have.
                    val resolvedMetadata = selectedVideoHeight(helper)
                        ?.let { preparedMetadata.copy(quality = "${it}p") }
                        ?: preparedMetadata
                    metadataById[id] = resolvedMetadata
                    val data = json.encodeToString(resolvedMetadata).encodeToByteArray()
                    val request = helper.getDownloadRequest(id, data)
                    requestByUri[request.uri.toString()] = request
                    DownloadService.sendAddDownload(
                        appContext,
                        EpisodeDownloadService::class.java,
                        request,
                        false,
                    )
                    DiagnosticsLog.event(
                        "Episode download queued id=$id provider=${preparedMetadata.provider} " +
                            "host=${request.uri.host ?: "unknown"} tracks=${request.streamKeys.size} " +
                            "subtitles=${preparedMetadata.subtitles.size} quality=${quality.label}",
                    )
                    downloadSubtitleFiles(id, preparedMetadata)
                }
                helper.release()
                finishPreparing(id)
                onResult(result)
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                helper.release()
                finishPreparing(id)
                DiagnosticsLog.throwable("Episode download prepare failed id=$id", e)
                onResult(Result.failure(e))
            }
        })
    }

    fun remove(context: Context, id: String) {
        initialize(context)
        DownloadService.sendRemoveDownload(
            appContext,
            EpisodeDownloadService::class.java,
            id,
            false,
        )
        DiagnosticsLog.event("Episode download remove requested id=$id")
    }

    fun localSubtitles(
        context: Context,
        metadata: EpisodeDownloadMetadata,
    ): List<SubtitleItem> {
        initialize(context)
        val directory = subtitleDirectory()
        return metadata.subtitles.mapNotNull { subtitle ->
            val fileName = subtitle.fileName ?: return@mapNotNull null
            val file = File(directory, fileName).takeIf(File::isFile) ?: return@mapNotNull null
            SubtitleItem(
                url = Uri.fromFile(file).toString(),
                label = subtitle.label,
                language = subtitle.language,
            )
        }
    }

    /** The on-disk subtitle files for a download, paired with their entry, for sidecar export. */
    internal fun subtitleFiles(
        context: Context,
        metadata: EpisodeDownloadMetadata,
    ): List<Pair<EpisodeDownloadSubtitle, File>> {
        initialize(context)
        val directory = subtitleDirectory()
        return metadata.subtitles.mapNotNull { subtitle ->
            val fileName = subtitle.fileName ?: return@mapNotNull null
            val file = File(directory, fileName).takeIf(File::isFile) ?: return@mapNotNull null
            subtitle to file
        }
    }

    /** Filename-safe tag for a subtitle sidecar, e.g. `Episode 3 [1080p].en.vtt`. */
    internal fun subtitleTag(subtitle: EpisodeDownloadSubtitle): String {
        val raw = subtitle.language.takeIf(String::isNotBlank)
            ?: subtitle.label.takeIf(String::isNotBlank)
            ?: "sub"
        return safeFilePart(raw).replace(' ', '-')
    }

    /**
     * Reads the download cache first and only falls back to the network for anything missing, with
     * the provider's header profile still applied. That is what lets an export run entirely
     * offline once the download itself is complete.
     */
    internal fun exportDataSourceFactory(context: Context): DataSource.Factory {
        initialize(context)
        return readOnlyPlaybackFactory(context, upstreamFactory)
    }

    /**
     * Reuses the persisted adaptive stream keys when the normal player opens a downloaded URL.
     * This prevents the player from requesting an undownloaded rendition while offline.
     */
    fun buildMediaItem(
        context: Context,
        uri: String,
        builder: MediaItem.Builder,
    ): MediaItem {
        initialize(context)
        val request = requestByUri[uri]
        return request
            ?.toMediaItem(builder)
            ?.buildUpon()
            ?.setMediaId(uri)
            ?.build()
            ?: builder.build()
    }

    /** Height of the video track Media3 actually selected, once track selections are applied. */
    private fun selectedVideoHeight(helper: DownloadHelper): Int? =
        (0 until helper.periodCount)
            .asSequence()
            .flatMap { helper.getTracks(it).groups.asSequence() }
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length)
                    .asSequence()
                    .filter(group::isTrackSelected)
                    .map { group.getTrackFormat(it).height }
            }
            .filter { it != Format.NO_VALUE }
            .maxOrNull()

    private fun finishPreparing(id: String) {
        _preparingIds.value -= id
        if (preparingDownloadId == id) preparingDownloadId = null
    }

    private fun requestHeadersFor(uri: Uri): Map<String, String> {
        val exactId = manifestIdByUri[uri.toString()]
        val sameHostId = uri.host?.let(manifestIdByHost::get)
        val metadata = metadataById[exactId ?: activeDownloadId ?: preparingDownloadId ?: sameHostId]
            ?: return emptyMap()
        val referer = metadata.referer?.takeIf { it.isNotBlank() } ?: return metadata.headers
        val refererUri = Uri.parse(referer)
        val origin = if (refererUri.scheme != null && refererUri.host != null) {
            "${refererUri.scheme}://${refererUri.host}"
        } else {
            referer
        }
        return buildMap {
            put("Referer", referer)
            put("Origin", origin)
            putAll(metadata.headers)
        }
    }

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            register(download)
            if (download.state == Download.STATE_DOWNLOADING) {
                activeDownloadId = download.request.id
            } else if (activeDownloadId == download.request.id) {
                activeDownloadId = null
            }
            finalException?.let {
                DiagnosticsLog.throwable("Episode download failed id=${download.request.id}", it)
            }
            notifyTerminalState(download)
            refreshDownloads()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            subtitleJobs.remove(download.request.id)?.cancel()
            val metadata = metadataById[download.request.id] ?: decodeMetadata(download.request)
            metadata?.let(::deleteSubtitleFiles)
            metadataById.remove(download.request.id)
            manifestIdByUri.entries.removeAll { it.value == download.request.id }
            manifestIdByHost.entries.removeAll { it.value == download.request.id }
            requestByUri.remove(download.request.uri.toString())
            if (activeDownloadId == download.request.id) activeDownloadId = null
            refreshDownloads()
        }
    }

    /**
     * Posts a notification when a download reaches a terminal state.
     *
     * The service's foreground notification disappears with the service, so without this a
     * download that finishes while the app is in the background leaves nothing behind and the
     * viewer has to open the library to find out whether it worked.
     */
    private fun notifyTerminalState(download: Download) {
        val label = decodeMetadata(download.request)
            ?.let { "${it.seriesTitle} · Episode ${it.episodeNumber}" }
        val helper = DownloadNotificationHelper(appContext, DOWNLOAD_CHANNEL_ID)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Routes.EXTRA_ROUTE, Routes.MORE)
        }
        val pending = PendingIntent.getActivity(
            appContext,
            download.request.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = when (download.state) {
            Download.STATE_COMPLETED ->
                helper.buildDownloadCompletedNotification(appContext, R.drawable.ic_notification, pending, label)
            Download.STATE_FAILED ->
                helper.buildDownloadFailedNotification(appContext, R.drawable.ic_notification, pending, label)
            else -> return
        }
        // Keyed on the download so a batch produces one line per episode rather than overwriting.
        runCatching {
            NotificationUtil.setNotification(
                appContext,
                TERMINAL_NOTIFICATION_ID_BASE + (download.request.id.hashCode() and 0xFFFF),
                notification,
            )
        }.onFailure { DiagnosticsLog.throwable("Episode download notification failed", it) }
    }

    private fun refreshDownloads() {
        if (!initialized) return
        scope.launch {
            val loaded = runCatching {
                buildList {
                    manager.downloadIndex.getDownloads().use { cursor ->
                        while (cursor.moveToNext()) {
                            val download = cursor.download
                            register(download)
                            download.toEpisodeDownload()?.let(::add)
                        }
                    }
                }.sortedByDescending(EpisodeDownload::updatedAtMs)
            }.onFailure {
                DiagnosticsLog.throwable("Episode download index query failed", it)
            }.getOrDefault(_downloads.value)
            _downloads.value = loaded
            if (loaded.any { it.state == EpisodeDownloadState.DOWNLOADING }) {
                startProgressPoller()
            } else {
                stopProgressPoller()
            }
        }
    }

    private fun register(download: Download) {
        val metadata = decodeMetadata(download.request) ?: return
        metadataById[download.request.id] = metadata
        manifestIdByUri[download.request.uri.toString()] = download.request.id
        download.request.uri.host?.let { manifestIdByHost[it] = download.request.id }
        requestByUri[download.request.uri.toString()] = download.request
    }

    private fun Download.toEpisodeDownload(): EpisodeDownload? {
        val metadata = decodeMetadata(request) ?: return null
        val rawPercent = percentDownloaded
        return EpisodeDownload(
            id = request.id,
            uri = request.uri.toString(),
            metadata = metadata,
            state = state.toEpisodeDownloadState(),
            percent = when {
                state == Download.STATE_COMPLETED -> 100f
                rawPercent.isFinite() && rawPercent >= 0f -> rawPercent.coerceIn(0f, 100f)
                else -> null
            },
            bytesDownloaded = bytesDownloaded,
            contentLength = contentLength.takeIf { it > 0 },
            updatedAtMs = updateTimeMs,
        )
    }

    private fun decodeMetadata(request: DownloadRequest): EpisodeDownloadMetadata? =
        runCatching {
            json.decodeFromString<EpisodeDownloadMetadata>(request.data.decodeToString())
        }.onFailure {
            DiagnosticsLog.throwable("Episode download metadata invalid id=${request.id}", it)
        }.getOrNull()

    private fun downloadSubtitleFiles(id: String, metadata: EpisodeDownloadMetadata) {
        if (metadata.subtitles.isEmpty()) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val directory = subtitleDirectory().apply { mkdirs() }
                val headers = requestHeaders(metadata) + ("User-Agent" to userAgent)
                metadata.subtitles.forEach { subtitle ->
                    val fileName = subtitle.fileName ?: return@forEach
                    val target = File(directory, fileName)
                    val temporary = File(directory, "$fileName.part")
                    runCatching {
                        val request = Request.Builder()
                            .url(subtitle.url)
                            .apply { headers.forEach { (name, value) -> header(name, value) } }
                            .build()
                        subtitleClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw IOException("Subtitle HTTP ${response.code}")
                            }
                            val body = response.body ?: throw IOException("Subtitle response was empty")
                            temporary.outputStream().use { output ->
                                body.byteStream().use { input -> input.copyTo(output) }
                            }
                        }
                        kotlin.coroutines.coroutineContext.ensureActive()
                        if (target.exists() && !target.delete()) {
                            throw IOException("Could not replace subtitle file")
                        }
                        if (!temporary.renameTo(target)) {
                            temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }
                        DiagnosticsLog.event(
                            "Episode subtitle downloaded id=$id language=${subtitle.language} " +
                                "label=${subtitle.label.take(48)}",
                        )
                    }.onFailure {
                        temporary.delete()
                        if (it is CancellationException) throw it
                        DiagnosticsLog.throwable(
                            "Episode subtitle download failed id=$id language=${subtitle.language}",
                            it,
                        )
                    }
                }
            } finally {
                subtitleJobs.remove(id)
            }
        }
        subtitleJobs.put(id, job)?.cancel()
        job.start()
    }

    private fun requestHeaders(metadata: EpisodeDownloadMetadata): Map<String, String> {
        val referer = metadata.referer?.takeIf { it.isNotBlank() } ?: return metadata.headers
        val refererUri = Uri.parse(referer)
        val origin = if (refererUri.scheme != null && refererUri.host != null) {
            "${refererUri.scheme}://${refererUri.host}"
        } else {
            referer
        }
        return buildMap {
            put("Referer", referer)
            put("Origin", origin)
            putAll(metadata.headers)
        }
    }

    private fun StreamItem.isDirectFile(): Boolean {
        if (isHls || isEmbed) return false
        val normalizedType = type.trim().lowercase()
        if (normalizedType in setOf("dash", "mpd", "webrtc")) return false
        val path = runCatching { Uri.parse(url).path.orEmpty().lowercase() }.getOrDefault("")
        return !path.endsWith(".mpd")
    }

    private data class DirectFileFormat(val extension: String, val mimeType: String)

    private fun directFileFormat(stream: StreamItem): DirectFileFormat {
        val path = runCatching { Uri.parse(stream.url).path.orEmpty().lowercase() }.getOrDefault("")
        return when {
            path.endsWith(".webm") || stream.type.equals("webm", true) ->
                DirectFileFormat("webm", "video/webm")
            path.endsWith(".mkv") || stream.type.equals("mkv", true) ->
                DirectFileFormat("mkv", "video/x-matroska")
            else -> DirectFileFormat("mp4", MimeTypes.VIDEO_MP4)
        }
    }

    private fun safeFilePart(value: String): String =
        value.trim()
            .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim('.', ' ')
            .ifBlank { "Episode" }

    /**
     * Each series gets its own folder under Downloads/Anilili. A long-runner otherwise buries
     * everything else in the same flat list, and the file manager has no way to group them.
     */
    internal fun deviceSeriesFolder(metadata: EpisodeDownloadMetadata): String =
        safeFilePart(metadata.seriesTitle).take(80).trim()

    internal fun deviceRelativePath(seriesFolder: String): String =
        "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DOWNLOAD_SUBDIRECTORY/$seriesFolder/"

    internal fun deviceFileStem(metadata: EpisodeDownloadMetadata): String = buildString {
        val qualityLabel = metadata.quality
        append("Episode ")
        append(safeFilePart(metadata.episodeNumber))
        metadata.episodeTitle
            ?.takeIf(String::isNotBlank)
            ?.let { append(" - ").append(safeFilePart(it)) }
        qualityLabel?.takeIf { it.isNotBlank() && !it.equals("auto", true) }
            ?.let { append(" [").append(safeFilePart(it)).append(']') }
    }.take(180).trim()

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    internal fun uniqueDeviceFileName(
        context: Context,
        seriesFolder: String,
        stem: String,
        extension: String,
    ): String {
        val relativePath = deviceRelativePath(seriesFolder)
        var copy = 1
        while (copy <= 999) {
            val suffix = if (copy == 1) "" else " ($copy)"
            val candidate = "$stem$suffix.$extension"
            val exists = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(candidate, relativePath),
                null,
            )?.use { it.moveToFirst() } == true
            if (!exists) return candidate
            copy += 1
        }
        return "$stem-${System.currentTimeMillis()}.$extension"
    }

    private fun deleteSubtitleFiles(metadata: EpisodeDownloadMetadata) {
        val directory = subtitleDirectory()
        metadata.subtitles.forEach { subtitle ->
            subtitle.fileName?.let { fileName ->
                File(directory, fileName).delete()
                File(directory, "$fileName.part").delete()
            }
        }
    }

    private fun subtitleDirectory(): File =
        File(appContext.filesDir, SUBTITLE_DIRECTORY)

    private fun subtitleFileName(
        id: String,
        index: Int,
        url: String,
    ): String {
        val extension = Uri.parse(url).lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in SUPPORTED_SUBTITLE_EXTENSIONS }
            ?: "vtt"
        return "${id.hashCode().toUInt().toString(16)}-$index.$extension"
    }

    private fun startProgressPoller() {
        if (progressPoller?.isActive == true) return
        progressPoller = scope.launch {
            while (isActive) {
                delay(PROGRESS_REFRESH_MS)
                refreshDownloads()
                if (_downloads.value.none { it.state == EpisodeDownloadState.DOWNLOADING }) break
            }
        }
    }

    private fun stopProgressPoller() {
        progressPoller?.cancel()
        progressPoller = null
    }

    private fun Int.toEpisodeDownloadState(): EpisodeDownloadState = when (this) {
        Download.STATE_DOWNLOADING -> EpisodeDownloadState.DOWNLOADING
        Download.STATE_COMPLETED -> EpisodeDownloadState.COMPLETED
        Download.STATE_FAILED -> EpisodeDownloadState.FAILED
        Download.STATE_REMOVING -> EpisodeDownloadState.REMOVING
        Download.STATE_RESTARTING -> EpisodeDownloadState.RESTARTING
        Download.STATE_STOPPED -> EpisodeDownloadState.STOPPED
        else -> EpisodeDownloadState.QUEUED
    }

    private const val DOWNLOAD_CHANNEL_ID = "episode_downloads"
    private const val TERMINAL_NOTIFICATION_ID_BASE = 7_400
    private const val DOWNLOAD_DIRECTORY = "episode-downloads"
    private const val SUBTITLE_DIRECTORY = "episode-download-subtitles"
    private const val PUBLIC_DOWNLOAD_SUBDIRECTORY = "Anilili"
    private const val PROGRESS_REFRESH_MS = 1_000L
    private val SUPPORTED_SUBTITLE_EXTENSIONS = setOf("vtt", "srt", "ass", "ssa", "ttml", "xml")
    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
}

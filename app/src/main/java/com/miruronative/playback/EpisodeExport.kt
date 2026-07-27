package com.miruronative.playback

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.miruronative.diagnostics.DiagnosticsLog
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class EpisodeExportState { PENDING, RUNNING, COMPLETED, FAILED }

data class EpisodeExportStatus(
    val downloadId: String,
    val state: EpisodeExportState,
    /** Null until the transmuxer can estimate; the manifest has to be read first. */
    val percent: Int? = null,
    val fileName: String? = null,
    val error: String? = null,
)

@Serializable
data class ExportedSubtitle(val uri: String, val label: String, val language: String)

/**
 * An episode that now lives as an MP4 in the device's Downloads folder.
 *
 * This is the app's own record, not Media3's. Once an episode is exported the segment cache is
 * usually dropped, so the Media3 download index no longer knows about it — this record is what
 * keeps it in the offline library, and its [uri] is what the player opens.
 */
@Serializable
data class ExportedEpisode(
    val downloadId: String,
    val uri: String,
    val fileName: String,
    val metadata: EpisodeDownloadMetadata,
    val sizeBytes: Long = 0,
    val exportedAtMs: Long = 0,
    val subtitles: List<ExportedSubtitle> = emptyList(),
)

/**
 * Turns a completed HLS download into a single MP4 in the device's public Downloads folder.
 *
 * An HLS download is a manifest plus a few hundred segments in Media3's cache — useful to this app
 * and nothing else. Exporting rewraps those segments into one MP4 container, which is what a file
 * manager, a USB stick, or any other player can actually open.
 *
 * Two properties make this cheap. The segments are already on disk, so the export never touches the
 * network and works offline; and with no effects and no MIME override, Transformer *transmuxes* —
 * it copies the encoded samples across untouched rather than decoding and re-encoding them. The
 * cost is close to a large file copy rather than a real conversion.
 *
 * Reading back through the download cache (rather than the origin) also inherits everything the
 * download already solved: the right rendition via the persisted stream keys, the provider's
 * Referer/Origin profile, and AES-128 segments that the HLS source has already decrypted.
 */
@OptIn(UnstableApi::class)
object EpisodeExport {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _statuses = MutableStateFlow<Map<String, EpisodeExportStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, EpisodeExportStatus>> = _statuses.asStateFlow()
    private val _exported = MutableStateFlow<List<ExportedEpisode>>(emptyList())
    val exported: StateFlow<List<ExportedEpisode>> = _exported.asStateFlow()

    @Volatile private var initialized = false
    private var observer: Job? = null

    /**
     * MediaStore's Downloads collection is the only way to write there without legacy storage
     * permissions, and it arrived in Android 10.
     */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun statuses(context: Context): StateFlow<Map<String, EpisodeExportStatus>> {
        initialize(context)
        return statuses
    }

    fun exported(context: Context): StateFlow<List<ExportedEpisode>> {
        initialize(context)
        return exported
    }

    /** Removes the MP4 and its sidecars from Downloads, and forgets the episode. */
    fun deleteExported(context: Context, downloadId: String) {
        val app = context.applicationContext
        scope.launch {
            val record = _exported.value.firstOrNull { it.downloadId == downloadId }
                ?: return@launch
            runCatching {
                val resolver = app.contentResolver
                (listOf(record.uri) + record.subtitles.map { it.uri }).forEach { uri ->
                    runCatching { resolver.delete(Uri.parse(uri), null, null) }
                }
            }.onFailure {
                DiagnosticsLog.throwable("Exported episode delete failed id=$downloadId", it)
            }
            saveExported(app, _exported.value.filterNot { it.downloadId == downloadId })
            _statuses.value -= downloadId
            DiagnosticsLog.event("Exported episode deleted id=$downloadId")
        }
    }

    fun initialize(context: Context) {
        if (initialized || !isSupported) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            val app = context.applicationContext
            // One observer covers both cases: an export asked for while the download was still
            // running, and one left half-finished by a process death. Both surface here as a
            // completed download that is still marked pending.
            observer = scope.launch {
                restoreExported(app)
                EpisodeDownloads.downloads(app).collect { downloads ->
                    val pending = pendingIds(app)
                    downloads.asSequence()
                        .filter { it.isComplete && it.id in pending }
                        .filter { _statuses.value[it.id]?.state != EpisodeExportState.RUNNING }
                        .forEach { EpisodeExportWorker.enqueue(app, it.id) }
                }
            }
        }
    }

    /**
     * Marks [downloadId] for export. The work starts as soon as the download itself is complete,
     * so this is safe to call while it is still downloading.
     */
    fun request(context: Context, downloadId: String, deleteAppCopyAfter: Boolean = false) {
        if (!isSupported) return
        val app = context.applicationContext
        initialize(app)
        val prefs = prefs(app)
        val deleting = prefs.getStringSet(KEY_DELETE_AFTER, emptySet()).orEmpty()
        prefs.edit()
            .putStringSet(KEY_PENDING, pendingIds(app) + downloadId)
            .putStringSet(
                KEY_DELETE_AFTER,
                if (deleteAppCopyAfter) deleting + downloadId else deleting - downloadId,
            )
            .apply()
        setStatus(EpisodeExportStatus(downloadId, EpisodeExportState.PENDING))
        DiagnosticsLog.event(
            "Episode export requested id=$downloadId deleteAppCopy=$deleteAppCopyAfter",
        )
        // Only start work for a download that is already sitting complete. Anything still running
        // is left to the observer, which fires when it finishes — starting now would just produce
        // a worker that finds nothing to rewrap.
        val ready = EpisodeDownloads.downloads(app).value
            .firstOrNull { it.id == downloadId }?.isComplete == true
        if (ready) EpisodeExportWorker.enqueue(app, downloadId)
    }

    /**
     * Runs one export end to end. Called by [EpisodeExportWorker] so the work keeps its foreground
     * notification and survives the app being backgrounded.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    internal suspend fun performExport(
        context: Context,
        downloadId: String,
        onProgress: suspend (Int?) -> Unit,
    ): Result<String> {
        val app = context.applicationContext
        initialize(app)
        // A record already present means the MP4 landed and only the bookkeeping was interrupted —
        // by process death between writing the file and clearing the request, say. Finishing that
        // bookkeeping is right; rebuilding the file and reporting a failure over it is not.
        _exported.value.firstOrNull { it.downloadId == downloadId }?.let { record ->
            clearPending(app, downloadId)
            return Result.success(record.fileName)
        }
        // The index is loaded asynchronously, so on a cold start after process death the download
        // list is briefly empty. Waiting beats reporting an episode as missing that is right there.
        val download = withTimeoutOrNull(DOWNLOAD_LOOKUP_TIMEOUT_MS) {
            EpisodeDownloads.downloads(app)
                .mapNotNull { downloads -> downloads.firstOrNull { it.id == downloadId } }
                .first()
        } ?: return fail(app, downloadId, "That download is no longer on this device.")
        if (!download.isComplete) {
            // Enqueued ahead of the download finishing. Stay pending rather than failing: the
            // observer re-runs this the moment the download completes.
            setStatus(EpisodeExportStatus(downloadId, EpisodeExportState.PENDING))
            return Result.failure(IllegalStateException("The download has not finished yet."))
        }

        val metadata = download.metadata
        val seriesFolder = EpisodeDownloads.deviceSeriesFolder(metadata)
        val fileName = EpisodeDownloads.uniqueDeviceFileName(
            context = app,
            seriesFolder = seriesFolder,
            stem = EpisodeDownloads.deviceFileStem(metadata),
            extension = "mp4",
        )
        setStatus(EpisodeExportStatus(downloadId, EpisodeExportState.RUNNING, null, fileName))

        // Transformer writes to a path, so the MP4 is built in the cache and then streamed into
        // MediaStore. That means the episode transiently needs room for a second copy of itself.
        val expected = download.contentLength ?: download.bytesDownloaded
        if (DownloadStorage.freeBytes(app) < expected + DownloadStorage.HEADROOM_BYTES) {
            return fail(app, downloadId, "Not enough free space to build the MP4.")
        }

        val workingDirectory = File(app.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        val temporary = File(workingDirectory, "${downloadId.hashCode().toUInt().toString(16)}.mp4")
        temporary.delete()

        return runCatching {
            transmux(app, download, temporary.absolutePath) { percent ->
                setStatus(
                    EpisodeExportStatus(downloadId, EpisodeExportState.RUNNING, percent, fileName),
                )
                onProgress(percent)
            }
            val target = publish(app, temporary, seriesFolder, fileName)
            val sidecars = copySubtitlesBeside(app, metadata, seriesFolder, fileName)
            // Recorded before the cache copy is dropped: this record is what keeps the episode in
            // the offline library once Media3's download index no longer knows about it.
            rememberExported(
                app,
                ExportedEpisode(
                    downloadId = downloadId,
                    uri = target.toString(),
                    fileName = fileName,
                    metadata = metadata,
                    sizeBytes = temporary.length(),
                    exportedAtMs = System.currentTimeMillis(),
                    subtitles = sidecars,
                ),
            )
            setStatus(
                EpisodeExportStatus(downloadId, EpisodeExportState.COMPLETED, 100, fileName),
            )
            DiagnosticsLog.event(
                "Episode export finished id=$downloadId file=$fileName uri=$target " +
                    "subtitles=${sidecars.size}",
            )
            if (shouldDeleteAppCopy(app, downloadId)) {
                EpisodeDownloads.remove(app, downloadId)
            }
            clearPending(app, downloadId)
            fileName
        }.onFailure { error ->
            temporary.delete()
            if (error is CancellationException) {
                _statuses.value -= downloadId
                throw error
            }
            DiagnosticsLog.throwable("Episode export failed id=$downloadId", error)
            setStatus(
                EpisodeExportStatus(
                    downloadId = downloadId,
                    state = EpisodeExportState.FAILED,
                    fileName = fileName,
                    error = error.message ?: "The MP4 could not be created.",
                ),
            )
        }.also { temporary.delete() }
    }

    /**
     * Rewraps the downloaded stream into [outputPath].
     *
     * Transformer is single-threaded and callback driven, so it is driven from the main looper and
     * awaited; the actual work happens on its own internal threads. No effects and no MIME override
     * are set anywhere here — that is what keeps this a transmux instead of a re-encode.
     */
    private suspend fun transmux(
        context: Context,
        download: EpisodeDownload,
        outputPath: String,
        onProgress: suspend (Int) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val completion = CompletableDeferred<Unit>()
        val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
            context,
            DefaultDecoderFactory.Builder(context).build(),
            Clock.DEFAULT,
            DefaultMediaSourceFactory(EpisodeDownloads.exportDataSourceFactory(context)),
        )
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoaderFactory)
            .setLooper(Looper.getMainLooper())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    completion.complete(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    completion.completeExceptionally(exportException)
                }

                // Worth logging loudly: a fallback means some track could not be copied as-is and
                // is being re-encoded, which turns a file copy into real CPU work.
                override fun onFallbackApplied(
                    composition: Composition,
                    originalTransformationRequest: TransformationRequest,
                    fallbackTransformationRequest: TransformationRequest,
                ) {
                    DiagnosticsLog.event(
                        "Episode export re-encoding id=${download.id} " +
                            "audio=${fallbackTransformationRequest.audioMimeType} " +
                            "video=${fallbackTransformationRequest.videoMimeType}",
                    )
                }
            })
            .build()

        val builder = MediaItem.Builder().setUri(download.uri)
        if (download.isAdaptive) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        val mediaItem = EpisodeDownloads.buildMediaItem(context, download.uri, builder)
        transformer.start(EditedMediaItem.Builder(mediaItem).build(), outputPath)

        val poller = launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
            }
        }
        try {
            completion.await()
        } catch (error: Throwable) {
            transformer.cancel()
            throw error
        } finally {
            poller.cancel()
        }
    }

    /** Streams the finished MP4 into the public Downloads collection and returns its uri. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun publish(
        context: Context,
        source: File,
        seriesFolder: String,
        fileName: String,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MimeTypes.VIDEO_MP4)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                EpisodeDownloads.deviceRelativePath(seriesFolder),
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Downloads folder rejected the file")
        runCatching {
            resolver.openOutputStream(target)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Downloads folder could not be opened for writing")
        }.onFailure {
            resolver.delete(target, null, null)
            throw it
        }
        resolver.update(
            target,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        target
    }

    /**
     * Drops the downloaded subtitle files next to the MP4 under a matching name. Media3's MP4
     * muxer will not take a text track, and a sidecar is what external players look for anyway.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun copySubtitlesBeside(
        context: Context,
        metadata: EpisodeDownloadMetadata,
        seriesFolder: String,
        fileName: String,
    ): List<ExportedSubtitle> = withContext(Dispatchers.IO) {
        val stem = fileName.substringBeforeLast('.')
        val used = mutableSetOf<String>()
        EpisodeDownloads.subtitleFiles(context, metadata).mapNotNull { (subtitle, file) ->
            runCatching {
                val tag = EpisodeDownloads.subtitleTag(subtitle)
                val name = generateSequence(0) { it + 1 }
                    .map { index -> if (index == 0) "$stem.$tag" else "$stem.$tag.$index" }
                    .first { it !in used }
                used += name
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.${file.extension}")
                    put(MediaStore.MediaColumns.MIME_TYPE, subtitleMimeType(file.extension))
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        EpisodeDownloads.deviceRelativePath(seriesFolder),
                    )
                }
                val target = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                context.contentResolver.openOutputStream(target)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                ExportedSubtitle(
                    uri = target.toString(),
                    label = subtitle.label,
                    language = subtitle.language,
                )
            }.onFailure {
                DiagnosticsLog.throwable(
                    "Episode export subtitle copy failed language=${subtitle.language}",
                    it,
                )
            }.getOrNull()
        }
    }

    private fun subtitleMimeType(extension: String): String = when (extension.lowercase()) {
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "ttml", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.TEXT_VTT
    }

    private fun fail(context: Context, downloadId: String, message: String): Result<String> {
        clearPending(context, downloadId)
        setStatus(
            EpisodeExportStatus(
                downloadId = downloadId,
                state = EpisodeExportState.FAILED,
                error = message,
            ),
        )
        DiagnosticsLog.event("Episode export rejected id=$downloadId reason=$message")
        return Result.failure(IllegalStateException(message))
    }

    private fun setStatus(status: EpisodeExportStatus) {
        _statuses.value += status.downloadId to status
    }

    /**
     * Rebuilds the session's view of exports from disk.
     *
     * Records are pruned against MediaStore on the way in: the viewer is free to delete an MP4 in
     * any file manager, and an episode the library offers to play that is no longer there is worse
     * than one that quietly disappears from the list.
     */
    private fun restoreExported(context: Context) {
        val stored = runCatching {
            prefs(context).getString(KEY_RECORDS, null)
                ?.let { json.decodeFromString<List<ExportedEpisode>>(it) }
                .orEmpty()
        }.onFailure {
            DiagnosticsLog.throwable("Exported episode records unreadable", it)
        }.getOrDefault(emptyList())

        val alive = stored.filter { record -> exists(context, record.uri) }
        if (alive.size != stored.size) {
            DiagnosticsLog.event(
                "Exported episodes pruned removed=${stored.size - alive.size} kept=${alive.size}",
            )
            saveExported(context, alive)
        } else {
            _exported.value = alive.sortedByDescending(ExportedEpisode::exportedAtMs)
        }
        _statuses.value = alive.associate {
            it.downloadId to EpisodeExportStatus(
                downloadId = it.downloadId,
                state = EpisodeExportState.COMPLETED,
                percent = 100,
                fileName = it.fileName,
            )
        } + pendingIds(context).associateWith {
            EpisodeExportStatus(it, EpisodeExportState.PENDING)
        }
    }

    private fun exists(context: Context, uri: String): Boolean = runCatching {
        context.contentResolver.query(
            Uri.parse(uri),
            arrayOf(MediaStore.MediaColumns._ID),
            null,
            null,
            null,
        )?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    private fun rememberExported(context: Context, record: ExportedEpisode) {
        saveExported(
            context,
            _exported.value.filterNot { it.downloadId == record.downloadId } + record,
        )
    }

    private fun saveExported(context: Context, records: List<ExportedEpisode>) {
        val ordered = records.sortedByDescending(ExportedEpisode::exportedAtMs)
        _exported.value = ordered
        prefs(context).edit()
            .putString(KEY_RECORDS, runCatching { json.encodeToString(ordered) }.getOrNull())
            .apply()
    }

    private fun pendingIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PENDING, emptySet()).orEmpty()

    private fun shouldDeleteAppCopy(context: Context, downloadId: String): Boolean =
        prefs(context).getStringSet(KEY_DELETE_AFTER, emptySet()).orEmpty().contains(downloadId)

    private fun clearPending(context: Context, downloadId: String) {
        val prefs = prefs(context)
        prefs.edit()
            .putStringSet(KEY_PENDING, pendingIds(context) - downloadId)
            .putStringSet(
                KEY_DELETE_AFTER,
                prefs.getStringSet(KEY_DELETE_AFTER, emptySet()).orEmpty() - downloadId,
            )
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "episode_exports"
    private const val KEY_PENDING = "pending"
    private const val KEY_DELETE_AFTER = "delete_after"
    private const val KEY_RECORDS = "records"
    private const val EXPORT_DIRECTORY = "episode-exports"
    private const val PROGRESS_INTERVAL_MS = 700L
    private const val DOWNLOAD_LOOKUP_TIMEOUT_MS = 10_000L
}

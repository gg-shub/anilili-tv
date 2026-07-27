package com.miruronative.playback

/**
 * One episode in the offline library, whichever way it happens to be stored.
 *
 * Since exports default to replacing the segment cache with a single MP4, an episode can be held
 * as a Media3 download, as an MP4 in the device's Downloads folder, or briefly as both while an
 * export runs. The library should not care: it shows one card per episode either way, and the
 * player picks whichever copy is actually on disk.
 */
data class OfflineEpisode(
    val id: String,
    val download: EpisodeDownload?,
    val exported: ExportedEpisode?,
) {
    val metadata: EpisodeDownloadMetadata
        get() = download?.metadata ?: requireNotNull(exported).metadata

    val updatedAtMs: Long
        get() = maxOf(download?.updatedAtMs ?: 0L, exported?.exportedAtMs ?: 0L)

    /** Whether something on this device can actually be played right now. */
    val isPlayable: Boolean get() = download?.isComplete == true || exported != null

    val isDownloading: Boolean get() = download?.isActive == true

    /** True once the MP4 exists, whether or not the library copy was kept alongside it. */
    val isInDownloadsFolder: Boolean get() = exported != null

    val sizeBytes: Long
        get() = exported?.sizeBytes?.takeIf { it > 0 } ?: download?.bytesDownloaded ?: 0L
}

/**
 * Folds Media3's download index together with the app's exported-MP4 records into one list, newest
 * first. An episode present in both — an export that kept the library copy, or one still running —
 * collapses to a single entry rather than appearing twice.
 */
fun offlineEpisodes(
    downloads: List<EpisodeDownload>,
    exported: List<ExportedEpisode>,
): List<OfflineEpisode> {
    val exportedById = exported.associateBy(ExportedEpisode::downloadId)
    val fromDownloads = downloads.map { download ->
        OfflineEpisode(download.id, download, exportedById[download.id])
    }
    val downloadIds = downloads.mapTo(mutableSetOf(), EpisodeDownload::id)
    val exportOnly = exported
        .filterNot { it.downloadId in downloadIds }
        .map { OfflineEpisode(it.downloadId, null, it) }
    return (fromDownloads + exportOnly).sortedByDescending(OfflineEpisode::updatedAtMs)
}

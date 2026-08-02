package com.shubh.anililitv.playback

data class OfflineEpisode(
    val id: String,
    val download: EpisodeDownload?,
    val exported: ExportedEpisode?,
) {
    val metadata: EpisodeDownloadMetadata
        get() = download?.metadata ?: requireNotNull(exported).metadata

    val updatedAtMs: Long
        get() = maxOf(download?.updatedAtMs ?: 0L, exported?.exportedAtMs ?: 0L)

    val isPlayable: Boolean get() = download?.isComplete == true || exported != null

    val isDownloading: Boolean get() = download?.isActive == true

    val isInDownloadsFolder: Boolean get() = exported != null

    val sizeBytes: Long
        get() = exported?.sizeBytes?.takeIf { it > 0 } ?: download?.bytesDownloaded ?: 0L
}

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

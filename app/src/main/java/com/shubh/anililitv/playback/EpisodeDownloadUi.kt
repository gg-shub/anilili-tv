package com.shubh.anililitv.playback

enum class EpisodeDownloadStage { QUEUED, DOWNLOADING, CONVERTING, SAVED, FAILED }

data class EpisodeDownloadUi(
    val stage: EpisodeDownloadStage,
    val progress: Float? = null,
) {
    val isBusy: Boolean get() = stage != EpisodeDownloadStage.SAVED && stage != EpisodeDownloadStage.FAILED
}

fun episodeDownloadBadges(
    downloads: List<EpisodeDownload>,
    exportStatuses: Map<String, EpisodeExportStatus>,
    exported: List<ExportedEpisode>,
): Map<String, EpisodeDownloadUi> {
    val badges = mutableMapOf<String, EpisodeDownloadUi>()

    exported.forEach { badges[it.downloadId] = EpisodeDownloadUi(EpisodeDownloadStage.SAVED, 1f) }

    downloads.forEach { download ->
        val stage = when (download.state) {
            EpisodeDownloadState.DOWNLOADING -> EpisodeDownloadStage.DOWNLOADING
            EpisodeDownloadState.FAILED -> EpisodeDownloadStage.FAILED
            EpisodeDownloadState.COMPLETED -> EpisodeDownloadStage.SAVED
            EpisodeDownloadState.REMOVING -> return@forEach
            else -> EpisodeDownloadStage.QUEUED
        }
        badges[download.id] = EpisodeDownloadUi(
            stage = stage,
            progress = download.percent
                ?.takeIf { stage == EpisodeDownloadStage.DOWNLOADING }
                ?.let { (it / 100f).coerceIn(0f, 1f) }
                ?: 1f.takeIf { stage == EpisodeDownloadStage.SAVED },
        )
    }

    exportStatuses.forEach { (id, status) ->
        when (status.state) {
            EpisodeExportState.RUNNING -> badges[id] = EpisodeDownloadUi(
                stage = EpisodeDownloadStage.CONVERTING,
                progress = status.percent?.let { (it / 100f).coerceIn(0f, 1f) },
            )
            EpisodeExportState.PENDING ->
                if (badges[id]?.stage == EpisodeDownloadStage.SAVED) {
                    badges[id] = EpisodeDownloadUi(EpisodeDownloadStage.CONVERTING, null)
                }
            EpisodeExportState.FAILED -> badges[id] = EpisodeDownloadUi(EpisodeDownloadStage.FAILED)
            EpisodeExportState.COMPLETED -> badges[id] = EpisodeDownloadUi(EpisodeDownloadStage.SAVED, 1f)
        }
    }

    return badges
}

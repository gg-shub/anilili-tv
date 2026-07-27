package com.miruronative.playback

/** What an episode's cover should be saying about its download, in the order the stages happen. */
enum class EpisodeDownloadStage { QUEUED, DOWNLOADING, CONVERTING, SAVED, FAILED }

data class EpisodeDownloadUi(
    val stage: EpisodeDownloadStage,
    /** 0f..1f where the stage can measure itself, null where it cannot (queued, starting up). */
    val progress: Float? = null,
) {
    val isBusy: Boolean get() = stage != EpisodeDownloadStage.SAVED && stage != EpisodeDownloadStage.FAILED
}

/**
 * Collapses the three places download state lives — Media3's index, the export queue, and the
 * exported-MP4 records — into one badge per episode id.
 *
 * The screens showing this (watch, detail, library) should not each re-derive which of the three
 * wins: an episode mid-export is simultaneously a completed download and a running export, and
 * "converting" is the honest thing to show.
 */
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

    // Export wins over a completed download: the episode is not finished from the viewer's point
    // of view until the MP4 exists, and with the default destination the cached copy is about to
    // disappear anyway.
    exportStatuses.forEach { (id, status) ->
        when (status.state) {
            EpisodeExportState.RUNNING -> badges[id] = EpisodeDownloadUi(
                stage = EpisodeDownloadStage.CONVERTING,
                progress = status.percent?.let { (it / 100f).coerceIn(0f, 1f) },
            )
            EpisodeExportState.PENDING ->
                // Only meaningful once the download itself is done; before that the download's own
                // progress is the more useful thing to show.
                if (badges[id]?.stage == EpisodeDownloadStage.SAVED) {
                    badges[id] = EpisodeDownloadUi(EpisodeDownloadStage.CONVERTING, null)
                }
            EpisodeExportState.FAILED -> badges[id] = EpisodeDownloadUi(EpisodeDownloadStage.FAILED)
            EpisodeExportState.COMPLETED -> badges[id] = EpisodeDownloadUi(EpisodeDownloadStage.SAVED, 1f)
        }
    }

    return badges
}

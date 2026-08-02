package com.shubh.anililitv.playback

import android.content.Context
import android.os.StatFs

object DownloadStorage {

    private val ESTIMATED_EPISODE_BYTES = mapOf(
        1080 to 550L * 1024 * 1024,
        720 to 300L * 1024 * 1024,
        480 to 150L * 1024 * 1024,
    )
    private const val FALLBACK_EPISODE_BYTES = 400L * 1024 * 1024

    const val HEADROOM_BYTES = 1_000L * 1024 * 1024

    fun freeBytes(context: Context): Long = runCatching {
        val path = context.filesDir ?: return@runCatching 0L
        val stat = StatFs(path.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(0L)

    fun estimatedEpisodeBytes(heightPx: Int?): Long =
        ESTIMATED_EPISODE_BYTES[heightPx] ?: FALLBACK_EPISODE_BYTES

    fun hasRoomFor(context: Context, heightPx: Int?): Boolean =
        freeBytes(context) > estimatedEpisodeBytes(heightPx) + HEADROOM_BYTES
}

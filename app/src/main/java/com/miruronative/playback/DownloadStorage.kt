package com.miruronative.playback

import android.content.Context
import android.os.StatFs

/**
 * Free-space checks for the offline library.
 *
 * This matters far more on TV than on a phone. A Fire TV stick ships around 8 GB of flash with
 * roughly 5 GB usable, and a 24-minute episode at 1080p lands somewhere around 300-600 MB, so a
 * handful of episodes fills the device — and when a TV runs out of space it does not fail politely,
 * it starts evicting other apps. Downloads are offered on TV exactly as on mobile, so the least we
 * can do is say how much room is left before the download starts.
 */
object DownloadStorage {

    /** Rough size of one episode at a given resolution, used only to warn before starting. */
    private val ESTIMATED_EPISODE_BYTES = mapOf(
        1080 to 550L * 1024 * 1024,
        720 to 300L * 1024 * 1024,
        480 to 150L * 1024 * 1024,
    )
    private const val FALLBACK_EPISODE_BYTES = 400L * 1024 * 1024

    /**
     * Keep this much free after a download finishes. Android starts misbehaving well before zero —
     * system updates, app caches, and the download's own temp files all need somewhere to go.
     */
    const val HEADROOM_BYTES = 1_000L * 1024 * 1024

    /** Free bytes on the volume the app's own storage lives on. */
    fun freeBytes(context: Context): Long = runCatching {
        val path = context.filesDir ?: return@runCatching 0L
        val stat = StatFs(path.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(0L)

    fun estimatedEpisodeBytes(heightPx: Int?): Long =
        ESTIMATED_EPISODE_BYTES[heightPx] ?: FALLBACK_EPISODE_BYTES

    /**
     * Whether one more episode at [heightPx] plausibly fits, leaving [HEADROOM_BYTES] behind.
     * Deliberately an estimate: the real size is not known until the manifest is parsed, and a
     * warning that arrives before the download beats an accurate one that arrives after.
     */
    fun hasRoomFor(context: Context, heightPx: Int?): Boolean =
        freeBytes(context) > estimatedEpisodeBytes(heightPx) + HEADROOM_BYTES
}

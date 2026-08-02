package com.shubh.anililitv.data.library

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.ui.nav.Routes
import java.util.concurrent.ConcurrentHashMap

object WatchNextManager {
    private const val PREFS = "anilili_watch_next"
    private const val PUBLISH_THROTTLE_MS = 60_000L

    private val lastPublished = ConcurrentHashMap<Int, Long>()

    @SuppressLint("RestrictedApi")
    fun publish(context: Context, entry: HistoryEntry) {
        if (!AppGraph.isTv) return
        val now = System.currentTimeMillis()
        val last = lastPublished[entry.anilistId] ?: 0L
        if (now - last < PUBLISH_THROTTLE_MS) return
        lastPublished[entry.anilistId] = now

        runCatching {
            val app = context.applicationContext
            val watchIntent = Intent(app, com.shubh.anililitv.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(
                    Routes.EXTRA_ROUTE,
                    Routes.watch(entry.anilistId, entry.provider, entry.category, entry.episodeLabel),
                )
            }
            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(now)
                .setTitle(entry.title)
                .setEpisodeTitle(entry.episodeTitle ?: "Episode ${entry.episodeLabel}")
                .setEpisodeNumber(entry.episodeNumber.toInt())
                .setInternalProviderId(entry.anilistId.toString())
                .setIntentUri(Uri.parse(watchIntent.toUri(Intent.URI_INTENT_SCHEME)))
                .apply {
                    entry.cover?.let { setPosterArtUri(Uri.parse(it)) }
                    if (entry.durationMs > 0) {
                        setDurationMillis(entry.durationMs.toInt())
                        setLastPlaybackPositionMillis(entry.positionMs.coerceAtLeast(0).toInt())
                    }
                }
                .build()

            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existingId = prefs.getLong(entry.anilistId.toString(), -1L)
            if (existingId > 0) {
                val updated = app.contentResolver.update(
                    TvContractCompat.buildWatchNextProgramUri(existingId),
                    program.toContentValues(),
                    null,
                    null,
                )
                if (updated > 0) return
            }
            val uri = app.contentResolver.insert(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                program.toContentValues(),
            )
            uri?.lastPathSegment?.toLongOrNull()?.let { id ->
                prefs.edit().putLong(entry.anilistId.toString(), id).apply()
                DiagnosticsLog.event("WatchNext published id=${entry.anilistId} programId=$id")
            }
        }.onFailure {
        }
    }

    fun remove(context: Context, anilistId: Int) {
        if (!AppGraph.isTv) return
        runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existingId = prefs.getLong(anilistId.toString(), -1L)
            if (existingId > 0) {
                app.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(existingId), null, null)
                prefs.edit().remove(anilistId.toString()).apply()
            }
        }
    }

    fun removeAll(context: Context) {
        if (!AppGraph.isTv) return
        runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.all.keys.toList().forEach { key ->
                val id = prefs.getLong(key, -1L)
                if (id > 0) {
                    app.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
                }
            }
            prefs.edit().clear().apply()
        }
    }
}

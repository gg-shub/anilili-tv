package com.shubh.anililitv.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaCache {
    @Volatile private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            File(context.applicationContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            ExoDatabase.provider(context),
        ).also { instance = it }
    }

    fun clear(context: Context) {
        val cache = get(context)
        for (key in cache.keys.toList()) {
            try {
                cache.removeResource(key)
            } catch (_: Exception) {
            }
        }
    }

    private const val MAX_BYTES = 512L * 1024 * 1024
}

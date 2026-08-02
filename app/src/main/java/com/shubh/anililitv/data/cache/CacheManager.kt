package com.shubh.anililitv.data.cache

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.playback.MediaCache
import java.io.File

object CacheManager {
    private val MANAGED_DIRS = listOf("media", "images", "http")

    fun usageBytes(context: Context): Long {
        val root = context.applicationContext.cacheDir
        return MANAGED_DIRS.sumOf { directorySize(File(root, it)) }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clear(context: Context) {
        val app = context.applicationContext
        
        runCatching {
            val cacheDir = app.cacheDir
            if (cacheDir != null && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.absolutePath.startsWith(cacheDir.absolutePath)) {
                        file.deleteRecursively()
                    }
                }
            }
        }.onFailure { DiagnosticsLog.throwable("Clear cacheDir failed", it) }

        runCatching {
            app.imageLoader.memoryCache?.clear()
        }.onFailure { DiagnosticsLog.throwable("Clear image memory cache failed", it) }
        
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkBottomUp().filter(File::isFile).sumOf(File::length)
    }
}

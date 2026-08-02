package com.shubh.anililitv.ui.watch

import android.content.Context
import coil.Coil
import com.shubh.anililitv.diagnostics.DiagnosticsLog

internal fun releaseImageMemoryForPlayback(context: Context) {
    runCatching {
        val cache = Coil.imageLoader(context).memoryCache ?: return@runCatching
        val beforeKb = cache.size / 1024
        cache.clear()
        DiagnosticsLog.event(
            "TV image cache released beforeKb=$beforeKb maxKb=${cache.maxSize / 1024}",
        )
    }
}

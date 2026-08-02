package com.shubh.anililitv.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SubtitleDelay {
    const val MAX_MS = 30_000L
    const val STEP_MS = 250L

    private val _delayMs = MutableStateFlow(0L)
    val delayMs: StateFlow<Long> = _delayMs.asStateFlow()

    @Volatile
    var delayUs: Long = 0L
        private set

    @Volatile
    var isAutomatic: Boolean = false
        private set

    fun set(ms: Long, automatic: Boolean = false) {
        val clamped = ms.coerceIn(-MAX_MS, MAX_MS)
        delayUs = clamped * 1_000L
        isAutomatic = automatic && clamped != 0L
        _delayMs.value = clamped
    }

    fun nudge(deltaMs: Long) = set(_delayMs.value + deltaMs)

    fun reset() = set(0L)
}

@UnstableApi
internal class SubtitleDelayRenderer(renderer: Renderer) : ForwardingRenderer(renderer) {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        super.render(shift(positionUs), elapsedRealtimeUs)
    }

    private fun shift(positionUs: Long): Long {
        val delayUs = SubtitleDelay.delayUs
        return if (delayUs == 0L) positionUs else positionUs - delayUs
    }
}

@UnstableApi
class SubtitleDelayRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val first = out.size
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        for (i in first until out.size) out[i] = SubtitleDelayRenderer(out[i])
    }
}

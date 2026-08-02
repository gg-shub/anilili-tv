package com.shubh.anililitv.ui.watch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface GestureLevel {
    val fraction: Float

    data class Brightness(override val fraction: Float) : GestureLevel
    data class Volume(override val fraction: Float) : GestureLevel
}

private enum class GestureZone { Brightness, Volume }

private enum class GestureDragAxis { Horizontal, Vertical }

private data class SeekGesture(
    val targetMs: Long,
    val deltaMs: Long,
    val durationMs: Long,
)

private const val GESTURE_EDGE_FRACTION = 0.28f
private val GESTURE_EDGE_MIN = 64.dp
private val GESTURE_EDGE_MAX = 160.dp
private const val SEEK_MS_PER_SCREEN = 120_000L

@Composable
internal fun PlayerGestureControls(
    modifier: Modifier = Modifier,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onSeek: ((Long) -> Unit)? = null,
    onHoldSpeed: ((active: Boolean) -> Unit)? = null,
    dragGestures: Boolean = true,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnHoldSpeed by rememberUpdatedState(onHoldSpeed)
    val currentPositionMs by rememberUpdatedState(positionMs)
    val currentDurationMs by rememberUpdatedState(durationMs)

    var level by remember { mutableStateOf<GestureLevel?>(null) }
    var seekGesture by remember { mutableStateOf<SeekGesture?>(null) }
    var levelTick by remember { mutableIntStateOf(0) }
    var holdSpeedActive by remember { mutableStateOf(false) }
    LaunchedEffect(levelTick) {
        if (level != null) {
            delay(700)
            level = null
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(activity, audioManager, dragGestures) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val edge = (size.width * GESTURE_EDGE_FRACTION)
                            .coerceIn(GESTURE_EDGE_MIN.toPx(), GESTURE_EDGE_MAX.toPx())
                        val zone = when {
                            !dragGestures -> null
                            down.position.x <= edge -> GestureZone.Brightness
                            down.position.x >= size.width - edge -> GestureZone.Volume
                            else -> null
                        }
                        var value = when (zone) {
                            GestureZone.Brightness -> readBrightness(activity)
                            GestureZone.Volume -> readVolume(audioManager)
                            null -> 0f
                        }
                        val seekStartPositionMs = currentPositionMs
                        val seekDurationMs = currentDurationMs
                        var dragAxis: GestureDragAxis? = null
                        var holding = false
                        var up: PointerInputChange? = null
                        val holdDeadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis

                        while (true) {
                            val event = if (dragAxis == null && !holding && dragGestures && currentOnHoldSpeed != null) {
                                val remaining = holdDeadline - android.os.SystemClock.uptimeMillis()
                                if (remaining <= 0) {
                                    null
                                } else {
                                    withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                holding = true
                                holdSpeedActive = true
                                currentOnHoldSpeed?.invoke(true)
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                up = change
                                break
                            }
                            if (dragAxis == null && !holding) {
                                val dy = change.position.y - down.position.y
                                val dx = change.position.x - down.position.x
                                dragAxis = when {
                                    abs(dx) > slop && abs(dx) > abs(dy) -> GestureDragAxis.Horizontal
                                    abs(dy) > slop && abs(dy) > abs(dx) -> GestureDragAxis.Vertical
                                    else -> null
                                }
                            }
                            when (dragAxis) {
                                GestureDragAxis.Horizontal -> {
                                    if (dragGestures && currentOnSeek != null && seekDurationMs > 0L) {
                                        val target = playerSlideSeekTarget(
                                            startPositionMs = seekStartPositionMs,
                                            durationMs = seekDurationMs,
                                            horizontalDragPx = change.position.x - down.position.x,
                                            widthPx = size.width.toFloat(),
                                        )
                                        seekGesture = SeekGesture(
                                            targetMs = target,
                                            deltaMs = target - seekStartPositionMs,
                                            durationMs = seekDurationMs,
                                        )
                                    }
                                    change.consume()
                                }
                                GestureDragAxis.Vertical -> {
                                    if (zone != null) {
                                        val delta = -change.positionChange().y / size.height.toFloat()
                                        value = (value + delta).coerceIn(0f, 1f)
                                        when (zone) {
                                            GestureZone.Brightness -> {
                                                applyBrightness(activity, value)
                                                level = GestureLevel.Brightness(value)
                                            }
                                            GestureZone.Volume -> {
                                                applyVolume(audioManager, value)
                                                level = GestureLevel.Volume(value)
                                            }
                                        }
                                        levelTick++
                                    }
                                    change.consume()
                                }
                                null -> Unit
                            }
                            if (holding) change.consume()
                        }

                        if (holding) {
                            holdSpeedActive = false
                            currentOnHoldSpeed?.invoke(false)
                            up?.consume()
                            return@awaitEachGesture
                        }
                        if (dragAxis != null) {
                            val completedSeek = seekGesture
                            seekGesture = null
                            if (dragAxis == GestureDragAxis.Horizontal && up != null) {
                                completedSeek?.let { currentOnSeek?.invoke(it.targetMs) }
                            }
                            up?.consume()
                            return@awaitEachGesture
                        }
                        if (up == null) return@awaitEachGesture
                        up.consume()
                        if (currentOnDoubleTap == null) {
                            currentOnTap?.invoke()
                            return@awaitEachGesture
                        }
                        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (secondDown == null) {
                            currentOnTap?.invoke()
                        } else {
                            secondDown.consume()
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == secondDown.id } ?: break
                                c.consume()
                                if (!c.pressed) break
                            }
                            currentOnDoubleTap?.invoke()
                        }
                    }
                },
        )

        level?.let { GestureLevelIndicator(it, Modifier.align(Alignment.Center)) }
        seekGesture?.let { SeekGestureIndicator(it, Modifier.align(Alignment.Center)) }
        if (holdSpeedActive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "2x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = "Playing at double speed",
                    tint = Color.White,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                )
            }
        }
    }
}

internal fun playerSlideSeekTarget(
    startPositionMs: Long,
    durationMs: Long,
    horizontalDragPx: Float,
    widthPx: Float,
): Long {
    val start = if (durationMs > 0L) startPositionMs.coerceIn(0L, durationMs) else startPositionMs.coerceAtLeast(0L)
    if (durationMs <= 0L || widthPx <= 0f || !widthPx.isFinite() || !horizontalDragPx.isFinite()) return start
    val target = start.toDouble() + horizontalDragPx / widthPx * SEEK_MS_PER_SCREEN
    return target.coerceIn(0.0, durationMs.toDouble()).toLong()
}

@Composable
internal fun PlaybackGestureIndicator(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(76.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(38.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = if (isPlaying) "Playing" else "Paused",
            tint = Color.White,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun GestureLevelIndicator(level: GestureLevel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = when (level) {
            is GestureLevel.Brightness -> Icons.Default.BrightnessHigh
            is GestureLevel.Volume ->
                if (level.fraction <= 0.001f) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp
        }
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(6.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${(level.fraction * 100).roundToInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SeekGestureIndicator(seek: SeekGesture, modifier: Modifier = Modifier) {
    val forward = seek.deltaMs >= 0L
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (forward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = (if (forward) "+" else "−") + formatPlayerTime(abs(seek.deltaMs)),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            text = "${formatPlayerTime(seek.targetMs)} / ${formatPlayerTime(seek.durationMs)}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

internal fun resetPlayerBrightness(context: Context) {
    val window = context.findActivity()?.window ?: return
    window.attributes = window.attributes.apply {
        screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}

@Composable
internal fun MediaVolumeSlider(
    modifier: Modifier = Modifier,
    showPercentLabel: Boolean = false,
    onInteract: () -> Unit = {},
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var volume by remember { mutableFloatStateOf(readVolume(audioManager)) }
    var lastAudible by remember { mutableFloatStateOf(volume.takeIf { it > 0f } ?: 0.5f) }
    var lastInteractMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(audioManager) {
        while (true) {
            delay(500)
            if (System.currentTimeMillis() - lastInteractMs > 700) {
                volume = readVolume(audioManager)
            }
        }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                lastInteractMs = System.currentTimeMillis()
                if (volume > 0.001f) {
                    lastAudible = volume
                    applyVolume(audioManager, 0f)
                    volume = 0f
                } else {
                    val target = lastAudible.coerceAtLeast(0.1f)
                    applyVolume(audioManager, target)
                    volume = target
                }
                onInteract()
            },
        ) {
            Icon(volumeIcon(volume), contentDescription = if (volume > 0.001f) "Mute" else "Unmute", tint = Color.White)
        }
        Slider(
            value = volume,
            onValueChange = {
                lastInteractMs = System.currentTimeMillis()
                volume = it
                applyVolume(audioManager, it)
                onInteract()
            },
            colors = whiteSliderColors(),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Volume" }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -0.05f
                        Key.DirectionRight -> +0.05f
                        else -> return@onPreviewKeyEvent false
                    }
                    val next = (volume + delta).coerceIn(0f, 1f)
                    if (next == volume) {
                        false
                    } else {
                        lastInteractMs = System.currentTimeMillis()
                        volume = next
                        applyVolume(audioManager, next)
                        onInteract()
                        true
                    }
                },
        )
        if (showPercentLabel) {
            Text(
                "${(volume * 100).roundToInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 10.dp).widthIn(min = 44.dp),
            )
        }
    }
}

private fun volumeIcon(fraction: Float): ImageVector = when {
    fraction <= 0.001f -> Icons.AutoMirrored.Filled.VolumeOff
    fraction < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
    else -> Icons.AutoMirrored.Filled.VolumeUp
}

private fun readBrightness(activity: Activity?): Float {
    val attr = activity?.window?.attributes?.screenBrightness ?: return 0.5f
    if (attr >= 0f) return attr.coerceIn(0f, 1f)
    val system = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull() ?: 128
    return (system / 255f).coerceIn(0f, 1f)
}

private fun applyBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.02f, 1f) }
}

private fun readVolume(audioManager: AudioManager?): Float {
    audioManager ?: return 0.5f
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return 0.5f
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun applyVolume(audioManager: AudioManager?, value: Float) {
    audioManager ?: return
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return
    audioManager.setStreamVolume(
        AudioManager.STREAM_MUSIC,
        (value * max).roundToInt().coerceIn(0, max),
        0,
    )
}

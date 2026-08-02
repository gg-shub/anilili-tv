package com.shubh.anililitv.ui.watch

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shubh.anililitv.ui.adaptive.focusHighlight

import androidx.compose.material.icons.automirrored.filled.ViewList

internal enum class TvPlayerControl {
    PREVIOUS,
    REWIND,
    PLAY_PAUSE,
    FORWARD,
    NEXT,
    EPISODES,
    MUTE,
    SETTINGS,
    FULLSCREEN,
}

internal fun tvPlayerControlOrder(
    hasEpisodes: Boolean = false,
    hasSettings: Boolean = false,
    hasFullscreen: Boolean = false,
): List<TvPlayerControl> = buildList {
    add(TvPlayerControl.PREVIOUS)
    add(TvPlayerControl.REWIND)
    add(TvPlayerControl.PLAY_PAUSE)
    add(TvPlayerControl.FORWARD)
    add(TvPlayerControl.NEXT)

    add(TvPlayerControl.MUTE)
    if (hasSettings) add(TvPlayerControl.SETTINGS)
    if (hasFullscreen) add(TvPlayerControl.FULLSCREEN)
}

internal fun opensTvPlayerControls(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
    keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
    keyCode == KeyEvent.KEYCODE_ENTER ||
    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

internal fun opensTvPlayerControls(key: Key): Boolean = key == Key.DirectionLeft ||
    key == Key.DirectionRight ||
    key == Key.DirectionUp ||
    key == Key.DirectionDown ||
    key == Key.DirectionCenter ||
    key == Key.Enter ||
    key == Key.NumPadEnter

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TvPlayerControls(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    isPlaying: Boolean,
    isMuted: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playPauseFocusRequester: FocusRequester,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    onToggleMute: () -> Unit,

    onSettings: (() -> Unit)? = null,
    onFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val position = positionProvider()
    val duration = durationProvider()
    val progress = if (duration > 0L) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }


    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { enter = { playPauseFocusRequester } }
            .focusGroup()
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(50)
            runCatching { playPauseFocusRequester.requestFocus() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatTvPlayerTime(positionProvider())} / ${formatTvPlayerTime(durationProvider())}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Row(
                modifier = Modifier
                    .focusProperties { enter = { playPauseFocusRequester } }
                    .focusGroup()
                    .focusRestorer { playPauseFocusRequester },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvControlButton("Previous episode", enabled = hasPrevious, onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                }
                TvControlButton("Rewind 10 seconds", onClick = onRewind) {
                    Icon(Icons.Default.Replay10, contentDescription = null)
                }
                TvControlButton(
                    label = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    modifier = Modifier.focusRequester(playPauseFocusRequester),
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                }
                TvControlButton("Forward 10 seconds", onClick = onForward) {
                    Icon(Icons.Default.Forward10, contentDescription = null)
                }
                TvControlButton("Next episode", enabled = hasNext, onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }

                TvControlButton(if (isMuted) "Unmute" else "Mute", onClick = onToggleMute) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                    )
                }
                onSettings?.let { callback ->
                    TvControlButton("Playback settings", onClick = callback) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                }
                onFullscreen?.let { callback ->
                    TvControlButton("Toggle fullscreen", onClick = callback) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvControlButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .semantics { contentDescription = label }
            .focusHighlight(RoundedCornerShape(28.dp), focusedScale = 1.12f)
            .clip(RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

private fun formatTvPlayerTime(valueMs: Long): String {
    val totalSeconds = valueMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

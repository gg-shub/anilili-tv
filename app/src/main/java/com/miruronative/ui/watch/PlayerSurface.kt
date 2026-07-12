package com.miruronative.ui.watch

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import com.miruronative.data.model.SkipTimes
import com.miruronative.data.model.StreamItem
import com.miruronative.data.model.SubtitleItem
import com.miruronative.playback.PlaybackService
import com.miruronative.ui.adaptive.LocalAppDeviceProfile
import com.miruronative.ui.nav.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** Media3 player surface backed by [PlaybackService] for PiP and system media controls. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    stream: StreamItem,
    subtitles: List<SubtitleItem>,
    skip: SkipTimes?,
    seriesTitle: String,
    episodeTitle: String,
    artworkUrl: String?,
    animeId: Int,
    provider: String,
    category: String,
    episode: String,
    onEnded: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    onToggleFullscreen: (() -> Unit)? = null,
    startPositionMs: Long = 0,
    onProgress: ((Long, Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val device = LocalAppDeviceProfile.current
    val controllerFuture = remember(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync()
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            { controller = runCatching { controllerFuture.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { MediaController.releaseFuture(controllerFuture) }
    }

    DisposableEffect(controller) {
        val activeController = controller
        if (activeController == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded()
                }

                override fun onPlayerError(error: PlaybackException) {
                    onError(error.localizedMessage ?: "Playback failed")
                }
            }
            activeController.addListener(listener)
            onDispose {
                onProgress?.invoke(
                    activeController.currentPosition.coerceAtLeast(0),
                    activeController.duration.coerceAtLeast(0),
                )
                activeController.removeListener(listener)
            }
        }
    }

    LaunchedEffect(controller, stream.url, subtitles) {
        val activeController = controller ?: return@LaunchedEffect
        if (activeController.currentMediaItem?.mediaId == stream.url) return@LaunchedEffect

        PlaybackService.configureRequestHeaders(stream.referer)
        val watchRoute = Routes.watch(animeId, provider, category, episode)
        val metadata = MediaMetadata.Builder()
            .setTitle(episodeTitle)
            .setArtist(seriesTitle)
            .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .setExtras(Bundle().apply {
                putString(PlaybackService.EXTRA_WATCH_ROUTE, watchRoute)
            })
            .build()
        val item = MediaItem.Builder()
            .setMediaId(stream.url)
            .setUri(stream.url)
            .setMediaMetadata(metadata)
            .apply { if (stream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setSubtitleConfigurations(
                subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                        .setMimeType(mimeFor(subtitle.url))
                        .setLanguage(subtitle.language)
                        .setLabel(subtitle.label)
                        .build()
                },
            )
            .build()
        activeController.setMediaItem(item, startPositionMs.coerceAtLeast(0))
        activeController.prepare()
        activeController.playWhenReady = true
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controller) {
        val activeController = controller ?: return@LaunchedEffect
        while (isActive) {
            positionMs = activeController.currentPosition.coerceAtLeast(0)
            if (activeController.isPlaying) {
                onProgress?.invoke(positionMs, activeController.duration.coerceAtLeast(0))
            }
            delay(500)
        }
    }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var controllerVisible by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var seekFlash by remember { mutableIntStateOf(0) } // -10 / +10, 0 = hidden
    var seekFlashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekFlashTick) {
        if (seekFlash != 0) {
            delay(650)
            seekFlash = 0
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller
                    useController = true
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    controllerShowTimeoutMs = if (device.isTv) 6_000 else 5_000
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                            if (visibility != View.VISIBLE) settingsExpanded = false
                        },
                    )
                    if (onToggleFullscreen != null) {
                        setFullscreenButtonClickListener { onToggleFullscreen() }
                    }
                    bindUnifiedSettingsButton { settingsExpanded = true }
                    if (device.isTv) post { requestFocus() }
                    playerView = this
                }
            },
            update = {
                it.player = controller
                it.bindUnifiedSettingsButton { settingsExpanded = true }
            },
            onRelease = {
                it.player = null
                playerView = null
            },
            modifier = Modifier.fillMaxSize(),
        )

        // On-screen seeking: with the controller hidden, single tap summons it and a double tap
        // on either half of the screen seeks ±10 s without going through the controller.
        if (!controllerVisible && controller != null && !device.isTv) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(controller) {
                        detectTapGestures(
                            onTap = { playerView?.showController() },
                            onDoubleTap = { offset ->
                                val active = controller ?: return@detectTapGestures
                                if (offset.x < size.width / 2f) {
                                    active.seekBack()
                                    seekFlash = -10
                                } else {
                                    active.seekForward()
                                    seekFlash = +10
                                }
                                seekFlashTick++
                            },
                        )
                    },
            )
        }

        if (seekFlash != 0) {
            Text(
                if (seekFlash > 0) "+10 s" else "−10 s",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(if (seekFlash > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        PlaybackSettingsMenu(
            controller = controller,
            expanded = settingsExpanded && controllerVisible,
            onDismiss = { settingsExpanded = false },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 72.dp),
        )

        if (controller == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        val introStartMs = ((skip?.introStart ?: 0.0) * 1000).toLong()
        val introEndMs = skip?.introEnd?.times(1000)?.toLong()
        val outroStartMs = skip?.outroStart?.times(1000)?.toLong()
        val outroEndMs = skip?.outroEnd?.times(1000)?.toLong()
        val action: Pair<String, () -> Unit>? = when {
            introEndMs != null && positionMs in introStartMs..introEndMs ->
                "Skip Intro" to { controller?.seekTo(introEndMs); Unit }
            outroStartMs != null && outroEndMs != null && positionMs in outroStartMs..outroEndMs ->
                "Next Episode" to onEnded
            else -> null
        }
        action?.let { (label, onClick) ->
            Button(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Sit above the controller's bottom bar when it is showing so the button
                    // never covers the seek/progress bar.
                    .padding(end = 24.dp, bottom = if (controllerVisible) 96.dp else 24.dp),
            ) {
                Text(label)
            }
        }
    }
}

/** Unified settings opened from Media3's built-in settings button. */
@OptIn(UnstableApi::class)
@Composable
private fun PlaybackSettingsMenu(
    controller: MediaController?,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (controller == null) return
    var pinnedHeight by remember(controller) { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val trackNameProvider = remember(context) { DefaultTrackNameProvider(context.resources) }

    Box(modifier) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            val heights = controller.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).height } }
                .filter { it > 0 }
                .distinct()
                .sortedDescending()
            SectionLabel("Quality")
            DropdownMenuItem(
                text = { Text(if (pinnedHeight == null) "Auto ✓" else "Auto") },
                onClick = {
                    applyVideoHeight(controller, null)
                    pinnedHeight = null
                    onDismiss()
                },
            )
            heights.forEach { height ->
                DropdownMenuItem(
                    text = { Text(if (pinnedHeight == height) "${height}p ✓" else "${height}p") },
                    onClick = {
                        applyVideoHeight(controller, height)
                        pinnedHeight = height
                        onDismiss()
                    },
                )
            }
            if (heights.isEmpty()) {
                DropdownMenuItem(text = { Text("Only one quality available") }, onClick = onDismiss)
            }

            HorizontalDivider()
            SectionLabel("Playback speed")
            PlaybackSpeeds.forEach { speed ->
                val selected = abs(controller.playbackParameters.speed - speed) < 0.01f
                DropdownMenuItem(
                    text = { Text("${speed.formatSpeed()}${if (selected) " ✓" else ""}") },
                    onClick = {
                        controller.setPlaybackSpeed(speed)
                        onDismiss()
                    },
                )
            }

            val audioTracks = audioTrackOptions(controller, trackNameProvider)
            if (audioTracks.isNotEmpty()) {
                HorizontalDivider()
                SectionLabel("Audio")
                DropdownMenuItem(
                    text = { Text("Auto") },
                    onClick = {
                        applyAudioTrack(controller, null)
                        onDismiss()
                    },
                )
                audioTracks.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.name}${if (option.selected) " ✓" else ""}") },
                        onClick = {
                            applyAudioTrack(controller, option)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun applyVideoHeight(controller: MediaController, height: Int?) {
    val builder: TrackSelectionParameters.Builder = controller.trackSelectionParameters.buildUpon()
    if (height == null) {
        builder.clearVideoSizeConstraints()
    } else {
        builder.setMaxVideoSize(Int.MAX_VALUE, height).setMinVideoSize(0, height)
    }
    controller.trackSelectionParameters = builder.build()
}

private fun audioTrackOptions(
    controller: MediaController,
    trackNameProvider: DefaultTrackNameProvider,
): List<AudioTrackOption> = controller.currentTracks.groups
    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
    .flatMap { group ->
        (0 until group.length)
            .filter { group.isTrackSupported(it) }
            .map { index ->
                AudioTrackOption(
                    trackGroup = group.getMediaTrackGroup(),
                    trackIndex = index,
                    name = trackNameProvider.getTrackName(group.getTrackFormat(index)),
                    selected = group.isTrackSelected(index),
                )
            }
    }

private fun applyAudioTrack(controller: MediaController, option: AudioTrackOption?) {
    val builder = controller.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
    if (option != null) {
        builder.setOverrideForType(TrackSelectionOverride(option.trackGroup, listOf(option.trackIndex)))
    }
    controller.trackSelectionParameters = builder.build()
}

private fun PlayerView.bindUnifiedSettingsButton(onClick: () -> Unit) {
    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener {
        showController()
        onClick()
    }
}

private fun Float.formatSpeed(): String = if (this % 1f == 0f) {
    "${toInt()}x"
} else {
    "${this}x"
}

private data class AudioTrackOption(
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val name: String,
    val selected: Boolean,
)

private val PlaybackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun mimeFor(url: String): String = when {
    url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
    url.contains(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.TEXT_VTT
}

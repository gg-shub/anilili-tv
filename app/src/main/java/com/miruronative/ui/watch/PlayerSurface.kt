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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.miruronative.data.model.SkipTimes
import com.miruronative.data.model.StreamItem
import com.miruronative.data.model.SubtitleItem
import com.miruronative.playback.PlaybackService
import com.miruronative.ui.adaptive.LocalAppDeviceProfile
import com.miruronative.ui.nav.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
                        },
                    )
                    if (onToggleFullscreen != null) {
                        setFullscreenButtonClickListener { onToggleFullscreen() }
                    }
                    if (device.isTv) post { requestFocus() }
                    playerView = this
                }
            },
            update = { it.player = controller },
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

        QualityMenu(
            controller = controller,
            visible = controllerVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
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

/** Manual resolution picker: Auto (adaptive) or pin one of the stream's video heights. */
@OptIn(UnstableApi::class)
@Composable
private fun QualityMenu(
    controller: MediaController?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (controller == null || !visible) return
    var expanded by remember { mutableStateOf(false) }
    var pinnedHeight by remember(controller) { mutableStateOf<Int?>(null) }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Settings, contentDescription = "Video quality", tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val heights = controller.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).height } }
                .filter { it > 0 }
                .distinct()
                .sortedDescending()
            DropdownMenuItem(
                text = { Text(if (pinnedHeight == null) "Auto ✓" else "Auto") },
                onClick = {
                    applyVideoHeight(controller, null)
                    pinnedHeight = null
                    expanded = false
                },
            )
            heights.forEach { height ->
                DropdownMenuItem(
                    text = { Text(if (pinnedHeight == height) "${height}p ✓" else "${height}p") },
                    onClick = {
                        applyVideoHeight(controller, height)
                        pinnedHeight = height
                        expanded = false
                    },
                )
            }
            if (heights.isEmpty()) {
                DropdownMenuItem(text = { Text("Only one quality available") }, onClick = { expanded = false })
            }
        }
    }
}

private fun applyVideoHeight(controller: MediaController, height: Int?) {
    val builder: TrackSelectionParameters.Builder = controller.trackSelectionParameters.buildUpon()
    if (height == null) {
        builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE).setMinVideoSize(0, 0)
    } else {
        builder.setMaxVideoSize(Int.MAX_VALUE, height).setMinVideoSize(0, height)
    }
    controller.trackSelectionParameters = builder.build()
}

private fun mimeFor(url: String): String = when {
    url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
    url.contains(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.TEXT_VTT
}

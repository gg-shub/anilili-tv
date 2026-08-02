@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.shubh.anililitv.ui.watch

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import androidx.mediarouter.app.MediaRouteDialogFactory
import com.shubh.anililitv.R
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.data.model.SkipTimes
import com.shubh.anililitv.data.model.StreamItem
import com.shubh.anililitv.data.model.SubtitleItem
import com.shubh.anililitv.data.settings.CaptionEdgeStyle
import com.shubh.anililitv.data.settings.CaptionStyle
import com.shubh.anililitv.data.settings.DefaultQuality
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.playback.PlaybackService
import com.shubh.anililitv.playback.EpisodeDownloads
import com.shubh.anililitv.playback.SubtitleDelay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.rememberScreenReaderActive
import com.shubh.anililitv.ui.components.CaptionAppearanceDialog
import com.shubh.anililitv.ui.nav.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal class ThemedMediaRouteChooserDialogFragment : MediaRouteChooserDialogFragment() {
    override fun onCreateChooserDialog(context: Context, savedInstanceState: Bundle?): MediaRouteChooserDialog =
        super.onCreateChooserDialog(
            ContextThemeWrapper(context, R.style.Theme_MiruroNative_MediaRouter),
            savedInstanceState,
        )
}

internal class ThemedMediaRouteControllerDialogFragment : MediaRouteControllerDialogFragment() {
    override fun onCreateControllerDialog(context: Context, savedInstanceState: Bundle?): MediaRouteControllerDialog =
        super.onCreateControllerDialog(
            ContextThemeWrapper(context, R.style.Theme_MiruroNative_MediaRouter),
            savedInstanceState,
        )
}

private class ThemedMediaRouteDialogFactory : MediaRouteDialogFactory() {
    override fun onCreateChooserDialogFragment(): MediaRouteChooserDialogFragment =
        ThemedMediaRouteChooserDialogFragment()

    override fun onCreateControllerDialogFragment(): MediaRouteControllerDialogFragment =
        ThemedMediaRouteControllerDialogFragment()
}

@OptIn(UnstableApi::class)
private class EpisodeControlPlayer(
    player: Player,
    private val hasNextEpisode: Boolean,
    private val hasPreviousEpisode: Boolean,
    private val onNextEpisode: () -> Unit,
    private val onPreviousEpisode: () -> Unit,
) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .removeAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .addIf(Player.COMMAND_SEEK_TO_NEXT, hasNextEpisode)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextEpisode)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, hasPreviousEpisode)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousEpisode)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        -> hasNextEpisode
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        -> hasPreviousEpisode
        else -> super.isCommandAvailable(command)
    }

    override fun hasNextMediaItem(): Boolean = hasNextEpisode
    override fun hasPreviousMediaItem(): Boolean = hasPreviousEpisode

    override fun seekToNext() {
        if (hasNextEpisode) onNextEpisode()
    }

    override fun seekToNextMediaItem() {
        if (hasNextEpisode) onNextEpisode()
    }

    override fun seekToPrevious() {
        if (hasPreviousEpisode) onPreviousEpisode()
    }

    override fun seekToPreviousMediaItem() {
        if (hasPreviousEpisode) onPreviousEpisode()
    }
}

@OptIn(UnstableApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerSurface(
    stream: StreamItem,
    qualityStreams: List<StreamItem> = listOf(stream),
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
    onNextEpisode: () -> Unit = onEnded,
    onError: (String, String, Long) -> Unit,
    modifier: Modifier = Modifier,
    onToggleFullscreen: (() -> Unit)? = null,
    startPositionMs: Long = 0,
    onProgress: ((Long, Long) -> Unit)? = null,
    onPreviousEpisode: (() -> Unit)? = null,
    hasNextEpisode: Boolean = true,
    hasPreviousEpisode: Boolean = true,
    focusPlayerOnStart: Boolean = true,
    isFullscreen: Boolean = false,
    subtitleOffsetMs: Long = 0L,
    notificationRoute: String? = null,
    episodes: List<EpisodeItem> = emptyList(),
    currentIndex: Int = 0,
    onSelectEpisode: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val device = LocalAppDeviceProfile.current
    val playerGestures by SettingsStore.playerGestures.collectAsState()
    DisposableEffect(Unit) { onDispose { resetPlayerBrightness(context) } }
    LaunchedEffect(stream.url, subtitleOffsetMs) {
        SubtitleDelay.set(subtitleOffsetMs, automatic = true)
    }
    val subtitleDelayMs by SubtitleDelay.delayMs.collectAsState()
    val controllerFuture = remember(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync()
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val currentProvider by rememberUpdatedState(provider)
    val currentCategory by rememberUpdatedState(category)
    val currentOnError by rememberUpdatedState(onError)
    var activeStream by remember(stream.url) { mutableStateOf(stream) }
    var nextStartPositionMs by remember(stream.url) { mutableLongStateOf(startPositionMs) }
    var playbackIsPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }


    var tracksRevision by remember { mutableIntStateOf(0) }
    var decoderRetryDone by remember(stream.url) { mutableStateOf(false) }
    var pendingTvSeekTargetMs by remember(stream.url) { mutableStateOf<Long?>(null) }
    var tvSeekRequest by remember(stream.url) { mutableIntStateOf(0) }
    var lastUserSeekRealtimeMs by remember(stream.url) { mutableLongStateOf(Long.MIN_VALUE) }
    var lastUserSeekTargetMs by remember(stream.url) { mutableLongStateOf(startPositionMs.coerceAtLeast(0L)) }
    var seekErrorRecoveryDone by remember(stream.url) { mutableStateOf(false) }
    var recoverySeekInFlight by remember(stream.url) { mutableStateOf(false) }

    val nativeQualityStreams = remember(stream.url, qualityStreams) {
        (listOf(stream) + qualityStreams)
            .filterNot(StreamItem::isEmbed)
            .distinctBy(StreamItem::url)
    }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess {
                        DiagnosticsLog.event("PlayerSurface MediaController connected")
                        controller = it
                    }
                    .onFailure { DiagnosticsLog.throwable("PlayerSurface MediaController connection failed", it) }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { 
            controller?.stop()
            controller?.clearMediaItems()
            controller?.release()
            MediaController.releaseFuture(controllerFuture) 
        }
    }

    LaunchedEffect(controller) {
        if (controller == null) {
            delay(5_000)
            if (controller == null) {
                DiagnosticsLog.event("PlayerSurface controller still null after 5000ms")
            }
        }
    }

    LaunchedEffect(controller, stream.url) {
        controller?.let(::clearVideoSelection)
    }

    DisposableEffect(controller, activeStream.url) {
        val activeController = controller
        if (activeController == null) {
            onDispose { }
        } else {
            playbackIsPlaying = activeController.isPlaying
            val listener = object : Player.Listener {
                private var audioPreferenceAppliedFor: String? = null

                override fun onPlaybackStateChanged(playbackStateUpdate: Int) {
                    playbackState = playbackStateUpdate
                    DiagnosticsLog.event(
                        "PlayerSurface playbackState=${playbackStateUpdate.stateName()} " +
                            "mediaId=${activeController.currentMediaItem?.mediaId?.take(120) ?: "none"}",
                    )
                    if (playbackStateUpdate == Player.STATE_READY) tracksRevision++
                    if (playbackStateUpdate == Player.STATE_ENDED) onEnded()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playbackIsPlaying = isPlaying
                    DiagnosticsLog.event("PlayerSurface isPlaying=$isPlaying")
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                    lastUserSeekTargetMs = newPosition.positionMs.coerceAtLeast(0L)
                    lastUserSeekRealtimeMs = SystemClock.elapsedRealtime()
                    if (recoverySeekInFlight) {
                        recoverySeekInFlight = false
                    } else {
                        seekErrorRecoveryDone = false
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    DiagnosticsLog.throwable("PlayerSurface player error code=${error.errorCodeName}", error)
                    val failedMediaId = activeController.currentMediaItem?.mediaId.orEmpty()
                    if (failedMediaId.isNotBlank() && failedMediaId != activeStream.url) {
                        DiagnosticsLog.event(
                            "PlayerSurface ignored stale error " +
                                "failedHost=${runCatching { Uri.parse(failedMediaId).host }.getOrNull() ?: "unknown"} " +
                                "activeHost=${activeStream.host()}",
                        )
                        return
                    }
                    if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED && !decoderRetryDone) {
                        decoderRetryDone = true
                        val resumeAt = activeController.currentPosition.coerceAtLeast(0L)
                        activeController.trackSelectionParameters = activeController.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .setMaxVideoSize(1280, 720)
                            .build()
                        activeController.prepare()
                        activeController.seekTo(resumeAt)
                        activeController.play()
                        DiagnosticsLog.event(
                            "PlayerSurface decoder failed; retrying same stream capped at 720p resumeMs=$resumeAt",
                        )
                        return
                    }
                    val elapsedSinceSeekMs = if (lastUserSeekRealtimeMs == Long.MIN_VALUE) {
                        Long.MAX_VALUE
                    } else {
                        SystemClock.elapsedRealtime() - lastUserSeekRealtimeMs
                    }
                    if (
                        shouldRecoverSeekError(
                            errorCode = error.errorCode,
                            elapsedSinceSeekMs = elapsedSinceSeekMs,
                            recoveryAlreadyAttempted = seekErrorRecoveryDone,
                        )
                    ) {
                        seekErrorRecoveryDone = true
                        recoverySeekInFlight = true
                        val resumeAt = lastUserSeekTargetMs.coerceAtLeast(0L)
                        activeController.prepare()
                        activeController.seekTo(resumeAt)
                        activeController.play()
                        DiagnosticsLog.event(
                            "PlayerSurface seek I/O error; retrying same stream " +
                                "code=${error.errorCodeName} resumeMs=$resumeAt",
                        )
                        return
                    }
                    currentOnError(
                        error.localizedMessage ?: "Playback failed",
                        failedMediaId,
                        activeController.currentPosition.coerceAtLeast(0L),
                    )
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    DiagnosticsLog.event("PlayerSurface tracks ${tracks.diagnosticSummary()}")
                    tracksRevision++
                    val mediaId = activeController.currentMediaItem?.mediaId ?: return
                    if (currentProvider !in MULTI_AUDIO_PROVIDERS || audioPreferenceAppliedFor == mediaId) return
                    if (applyCategoryAudioPreference(activeController, currentCategory, currentProvider)) {
                        audioPreferenceAppliedFor = mediaId
                    }
                }
            }
            DiagnosticsLog.event("PlayerSurface listener attached")
            activeController.addListener(listener)
            onDispose {
                onProgress?.invoke(
                    activeController.currentPosition.coerceAtLeast(0),
                    activeController.duration.coerceAtLeast(0),
                )
                activeController.removeListener(listener)
                DiagnosticsLog.event("PlayerSurface listener removed")
            }
        }
    }

    LaunchedEffect(controller, activeStream.url, subtitles) {
        val activeController = controller ?: return@LaunchedEffect
        if (activeController.currentMediaItem?.mediaId == activeStream.url) {
            DiagnosticsLog.event(
                "PlayerSurface media item already active " +
                    "host=${activeStream.host()} type=${activeStream.typeLabel()}",
            )
            return@LaunchedEffect
        }

        DiagnosticsLog.event(
            "PlayerSurface prepare stream type=${activeStream.typeLabel()} host=${activeStream.host()} " +
                "height=${activeStream.declaredVideoHeight() ?: "auto"} subtitles=${subtitles.size} " +
                "startMs=$nextStartPositionMs",
        )
        PlaybackService.configureRequestHeaders(
            activeStream.referer,
            activeStream.playlistKey,
            activeStream.headers,
            activeStream.avoidCronet,
        )
        val watchRoute = notificationRoute ?: Routes.watch(animeId, provider, category, episode)
        val metadata = MediaMetadata.Builder()
            .setTitle(episodeTitle)
            .setArtist(seriesTitle)
            .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .setExtras(Bundle().apply {
                putString(PlaybackService.EXTRA_WATCH_ROUTE, watchRoute)
            })
            .build()
        val itemBuilder = MediaItem.Builder()
            .setMediaId(activeStream.url)
            .setUri(activeStream.url)
            .setMediaMetadata(metadata)
            .apply { if (activeStream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setSubtitleConfigurations(
                subtitles.mapIndexed { index, subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                        .setMimeType(mimeFor(subtitle.url))
                        .setLanguage(subtitle.language)
                        .setLabel(subtitle.label)
                        .apply {
                            if (index == 0 && (category != "dub" || SettingsStore.subtitlesWithDub.value)) {
                                setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            }
                        }
                        .build()
                },
            )
        val item = EpisodeDownloads.buildMediaItem(context, activeStream.url, itemBuilder)
        activeController.setMediaItem(item, nextStartPositionMs.coerceAtLeast(0))
        activeController.prepare()
        activeController.playWhenReady = true
        DiagnosticsLog.event("PlayerSurface prepare called playWhenReady=true")
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val queueTvSeek: (Long) -> Unit = { offsetMs ->
        controller?.let { activeController ->
            val knownDurationMs = activeController.duration
                .takeIf { it != C.TIME_UNSET && it > 0L }
                ?: durationMs
            val targetMs = tvSeekTargetMs(
                currentPositionMs = pendingTvSeekTargetMs
                    ?: activeController.currentPosition.coerceAtLeast(0L),
                durationMs = knownDurationMs,
                offsetMs = offsetMs,
            )
            pendingTvSeekTargetMs = targetMs
            positionMs = targetMs
            tvSeekRequest++
            DiagnosticsLog.event(
                "PlayerSurface TV seek queued offsetMs=$offsetMs targetMs=$targetMs request=$tvSeekRequest",
            )
        }
    }
    LaunchedEffect(controller, activeStream.url, tvSeekRequest) {
        val activeController = controller ?: return@LaunchedEffect
        val targetMs = pendingTvSeekTargetMs ?: return@LaunchedEffect
        delay(TV_SEEK_COALESCE_MS)
        if (pendingTvSeekTargetMs != targetMs) return@LaunchedEffect
        lastUserSeekTargetMs = targetMs
        lastUserSeekRealtimeMs = SystemClock.elapsedRealtime()
        seekErrorRecoveryDone = false
        pendingTvSeekTargetMs = null
        activeController.seekTo(targetMs)
        DiagnosticsLog.event("PlayerSurface TV seek committed targetMs=$targetMs")
    }
    LaunchedEffect(controller) {
        val activeController = controller ?: return@LaunchedEffect
        while (isActive) {
            if (pendingTvSeekTargetMs == null) {
                positionMs = activeController.currentPosition.coerceAtLeast(0)
            }
            durationMs = activeController.duration.coerceAtLeast(0)
            if (activeController.isPlaying) {
                onProgress?.invoke(positionMs, durationMs)
            }
            delay(500)
        }
    }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var controllerVisible by remember { mutableStateOf(false) }
    var tvControlsVisible by remember { mutableStateOf(false) }
    
    BackHandler(enabled = device.isTv && tvControlsVisible) {
        tvControlsVisible = false
    }

    var tvControlsInteraction by remember { mutableIntStateOf(0) }
    var skipActionFocused by remember { mutableStateOf(false) }
    var lastAudibleVolume by remember { mutableFloatStateOf(1f) }
    val tvPlayPauseFocus = remember { FocusRequester() }
    val tvPlayerFocus = remember { FocusRequester() }
    var tvControlsFocusRestoreRequest by remember(activeStream.url) { mutableIntStateOf(0) }
    
    val restoreTvControlsFocus = {
        if (device.isTv && focusPlayerOnStart) {
            tvControlsVisible = true
            tvControlsInteraction++
            tvControlsFocusRestoreRequest++
        }
    }
    LaunchedEffect(tvControlsVisible, focusPlayerOnStart) {
        if (!tvControlsVisible || !focusPlayerOnStart) return@LaunchedEffect
        delay(150)
        runCatching { tvPlayPauseFocus.requestFocus() }
    }
    LaunchedEffect(tvControlsFocusRestoreRequest, focusPlayerOnStart) {
        if (tvControlsFocusRestoreRequest == 0 || !focusPlayerOnStart) return@LaunchedEffect
        delay(150)
        runCatching { tvPlayPauseFocus.requestFocus() }
        DiagnosticsLog.event(
            "PlayerSurface TV controls focus restored request=$tvControlsFocusRestoreRequest",
        )
    }
    val screenReaderActive = rememberScreenReaderActive()
    var settingsExpanded by remember { mutableStateOf(false) }
    var captionAppearanceVisible by remember { mutableStateOf(false) }
    LaunchedEffect(screenReaderActive, focusPlayerOnStart, activeStream.url) {
        if (device.isTv && screenReaderActive && focusPlayerOnStart) {
            tvControlsVisible = true
        }
    }
    LaunchedEffect(
        tvControlsVisible,
        tvControlsInteraction,
        focusPlayerOnStart,
        screenReaderActive,
        settingsExpanded,
        captionAppearanceVisible,
    ) {
        if (!focusPlayerOnStart) {
            tvControlsVisible = false
            return@LaunchedEffect
        }
        if (!tvControlsVisible) return@LaunchedEffect
        if (screenReaderActive) return@LaunchedEffect
        if (settingsExpanded || captionAppearanceVisible) return@LaunchedEffect
        delay(4_000)
        tvControlsVisible = false
        runCatching { tvPlayerFocus.requestFocus() }
    }
    LaunchedEffect(activeStream.url, playerView, device.isTv, focusPlayerOnStart, screenReaderActive) {
        if (device.isTv && focusPlayerOnStart && playerView != null && !screenReaderActive) {
            delay(150)
            runCatching { tvPlayerFocus.requestFocus() }
        }
    }
    val currentOnNextEpisode by rememberUpdatedState(onNextEpisode)
    val currentOnPreviousEpisode by rememberUpdatedState(onPreviousEpisode)
    val currentHasNext by rememberUpdatedState(hasNextEpisode)
    val currentHasPrevious by rememberUpdatedState(hasPreviousEpisode)
    val canGoPrevious = hasPreviousEpisode && onPreviousEpisode != null
    val playerControls = remember(controller, hasNextEpisode, canGoPrevious) {
        controller?.let { activeController ->
            EpisodeControlPlayer(
                player = activeController,
                hasNextEpisode = hasNextEpisode,
                hasPreviousEpisode = canGoPrevious,
                onNextEpisode = { currentOnNextEpisode() },
                onPreviousEpisode = { currentOnPreviousEpisode?.invoke() },
            )
        }
    }

    DisposableEffect(Unit) {
        DiagnosticsLog.event("PlayerSurface episode navigator registered hasPrev=$hasPreviousEpisode hasNext=$hasNextEpisode")
        val navigator: (Int) -> Unit = { direction ->
            DiagnosticsLog.event("PlayerSurface episode navigator direction=$direction")
            when {
                direction > 0 && currentHasNext -> currentOnNextEpisode()
                direction < 0 && currentHasPrevious -> currentOnPreviousEpisode?.invoke()
            }
        }
        PlaybackService.episodeNavigator = navigator
        onDispose {
            if (PlaybackService.episodeNavigator === navigator) {
                PlaybackService.episodeNavigator = null
            }
            DiagnosticsLog.event("PlayerSurface episode navigator cleared")
        }
    }
    var phoneControlsVisible by remember { mutableStateOf(true) }
    var phoneControlsInteraction by remember { mutableIntStateOf(0) }
    var contentScale by remember { mutableStateOf(PlayerContentScale.FIT) }
    val trackNameProvider = remember(context) { DefaultTrackNameProvider(context.resources) }
    LaunchedEffect(phoneControlsVisible, phoneControlsInteraction, playbackIsPlaying, device.isTv) {
        if (device.isTv || !phoneControlsVisible || !playbackIsPlaying) return@LaunchedEffect
        delay(4_000)
        phoneControlsVisible = false
    }
    val captionStyle by SettingsStore.captionStyle.collectAsState()
    var pinnedVideoHeight by remember(controller, stream.url) { mutableStateOf<Int?>(null) }
    fun changeVideoHeight(activeController: MediaController, height: Int?): Boolean {
        val applied = when {
            height == null -> {
                clearVideoSelection(activeController)
                if (activeStream.url != stream.url) {
                    nextStartPositionMs = activeController.currentPosition.coerceAtLeast(0)
                    activeStream = stream
                }
                DiagnosticsLog.event("PlayerSurface quality selection mode=auto")
                true
            }
            activeController.hasVideoHeight(height) -> applyVideoHeight(activeController, height)
            else -> {
                val source = nativeQualityStreams.firstOrNull { it.declaredVideoHeight() == height }
                if (source == null) {
                    DiagnosticsLog.event("PlayerSurface quality selection rejected height=$height unavailable")
                    false
                } else {
                    clearVideoSelection(activeController)
                    nextStartPositionMs = activeController.currentPosition.coerceAtLeast(0)
                    activeStream = source
                    DiagnosticsLog.event(
                        "PlayerSurface quality selection mode=manual height=$height " +
                            "source=${source.typeLabel()} host=${source.host()}",
                    )
                    true
                }
            }
        }
        if (applied) pinnedVideoHeight = height
        return applied
    }
    val defaultQuality by SettingsStore.defaultQuality.collectAsState()
    var defaultQualityApplied by remember(stream.url) { mutableStateOf(false) }
    LaunchedEffect(controller, tracksRevision, defaultQuality) {
        val activeController = controller ?: return@LaunchedEffect
        if (defaultQualityApplied || pinnedVideoHeight != null) return@LaunchedEffect
        if (defaultQuality == DefaultQuality.AUTO) return@LaunchedEffect
        if (activeController.currentMediaItem?.mediaId != activeStream.url) return@LaunchedEffect
        if (activeController.playbackState != Player.STATE_READY) return@LaunchedEffect
        val heights = availableVideoHeights(activeController, nativeQualityStreams)
        val target = defaultQuality.pickHeight(heights) ?: return@LaunchedEffect
        defaultQualityApplied = true
        DiagnosticsLog.event(
            "PlayerSurface default quality=${defaultQuality.storedValue} target=${target}p heights=$heights",
        )
        changeVideoHeight(activeController, target)
    }
    var preHoldSpeed by remember { mutableFloatStateOf(1f) }
    var playbackGestureIsPlaying by remember { mutableStateOf<Boolean?>(null) }
    val autoSkipIntroOutro by SettingsStore.autoSkipIntroOutro.collectAsState()
    val autoplay by SettingsStore.autoplay.collectAsState()
    val introStartMs = skip?.introStart?.times(1000)?.toLong() ?: 0L
    val introEndMs = skip?.introEnd?.times(1000)?.toLong()
    val outroStartMs = skip?.outroStart?.times(1000)?.toLong()
    val outroEndMs = skip?.outroEnd?.times(1000)?.toLong()
    var introAutoSkipped by remember(activeStream.url, introStartMs, introEndMs) { mutableStateOf(false) }
    var outroAutoHandled by remember(activeStream.url, outroStartMs, outroEndMs) { mutableStateOf(false) }

    LaunchedEffect(playbackGestureIsPlaying) {
        if (playbackGestureIsPlaying != null) {
            delay(650)
            playbackGestureIsPlaying = null
        }
    }

    LaunchedEffect(
        autoSkipIntroOutro,
        autoplay,
        controller,
        positionMs,
        introStartMs,
        introEndMs,
        outroStartMs,
        outroEndMs,
    ) {
        val activeController = controller ?: return@LaunchedEffect
        if (!autoSkipIntroOutro || !activeController.isPlaying) return@LaunchedEffect

        if (!introAutoSkipped && isInSkipWindow(positionMs, introStartMs, introEndMs)) {
            introAutoSkipped = true
            activeController.seekTo(introEndMs ?: return@LaunchedEffect)
            return@LaunchedEffect
        }

        if (
            autoplay &&
            !outroAutoHandled &&
            isInSkipWindow(positionMs, outroStartMs, outroEndMs)
        ) {
            outroAutoHandled = true
            onNextEpisode()
        }
    }

    var tvSeekIndicator by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tvSeekIndicator) {
        if (tvSeekIndicator != null) {
            kotlinx.coroutines.delay(1000)
            tvSeekIndicator = null
        }
    }

    val remoteModifier = if (device.isTv && focusPlayerOnStart) {
        Modifier
            .focusRequester(tvPlayerFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val mediaSeekOffsetMs = when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> -TV_SEEK_STEP_MS
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> TV_SEEK_STEP_MS
                    else -> null
                }
                if (mediaSeekOffsetMs != null) {
                    queueTvSeek(mediaSeekOffsetMs)
                    return@onPreviewKeyEvent true
                }
                if (!opensTvPlayerControls(event.key)) return@onPreviewKeyEvent false
                if (settingsExpanded || captionAppearanceVisible) return@onPreviewKeyEvent false
                tvControlsInteraction++
                if (!tvControlsVisible) {
                    when (event.key) {
                        androidx.compose.ui.input.key.Key.DirectionLeft -> {
                            tvSeekIndicator = "-30 sec"
                            queueTvSeek(-30_000L)
                            return@onPreviewKeyEvent true
                        }
                        androidx.compose.ui.input.key.Key.DirectionRight -> {
                            tvSeekIndicator = "+30 sec"
                            queueTvSeek(30_000L)
                            return@onPreviewKeyEvent true
                        }
                        androidx.compose.ui.input.key.Key.DirectionUp,
                        androidx.compose.ui.input.key.Key.DirectionDown -> {
                            tvControlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        androidx.compose.ui.input.key.Key.DirectionCenter,
                        androidx.compose.ui.input.key.Key.Enter,
                        androidx.compose.ui.input.key.Key.NumPadEnter -> {
                            if (skipActionFocused) {
                                return@onPreviewKeyEvent false
                            } else {
                                tvControlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                        }
                        else -> {
                            tvControlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                    }
                } else {
                    false
                }
            }
            .semantics {
                contentDescription = "Video player"
                onClick(label = "Show player controls") {
                    tvControlsInteraction++
                    tvControlsVisible = true
                    true
                }
            }
            .focusable(!tvControlsVisible)
    } else {
        Modifier
    }

    Box(modifier.then(remoteModifier)) {
        AndroidView(
            factory = { ctx ->
                DiagnosticsLog.event("PlayerSurface AndroidView factory create PlayerView")
                PlayerView(ctx).apply {
                    player = playerControls
                    useController = false
                    keepScreenOn = true
                    isFocusable = !device.isTv
                    isFocusableInTouchMode = !device.isTv
                    setShowSubtitleButton(true)
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    controllerShowTimeoutMs = 4_000
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                            if (visibility == View.VISIBLE && device.isTv) {
                                post {
                                    findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                                        ?.requestFocus()
                                }
                            } else if (visibility != View.VISIBLE) {
                                if (device.isTv) post { requestFocus() }
                            }
                        },
                    )
                    if (onToggleFullscreen != null) {
                        setFullscreenButtonClickListener { onToggleFullscreen() }
                    }
                    bindUnifiedSettingsButton { settingsExpanded = true }
                    playerView = this
                }
            },
            update = {
                it.player = playerControls
                it.isFocusable = !device.isTv
                it.isFocusableInTouchMode = !device.isTv
                it.resizeMode = when (contentScale) {
                    PlayerContentScale.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    PlayerContentScale.CROP -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    PlayerContentScale.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                if (device.isTv) it.clearFocus()
                val controlsVisible = if (device.isTv) tvControlsVisible else phoneControlsVisible
                it.applyCaptionStyle(captionStyle, controlsVisible)
                it.bindUnifiedSettingsButton { settingsExpanded = true }
                
                DiagnosticsLog.event(
                    "PlayerSurface AndroidView update controller=${controller != null} " +
                        "size=${it.width}x${it.height} shown=${it.isShown}",
                )
            },
            onRelease = {
                it.player = null
                playerView = null
                DiagnosticsLog.event("PlayerSurface AndroidView release size=${it.width}x${it.height}")
            },
            modifier = Modifier.fillMaxSize(),
        )

        val controlsShowing = if (device.isTv) tvControlsVisible else phoneControlsVisible
        LaunchedEffect(captionStyle, controlsShowing, playerView) {
            playerView?.applyCaptionStyle(captionStyle, controlsShowing)
        }

        if (device.isTv && focusPlayerOnStart && controller != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tvControlsVisible) {
                        detectTapGestures {
                            tvControlsInteraction++
                            if (tvControlsVisible) {
                                DiagnosticsLog.event("PlayerSurface TV controls closed pointer")
                                tvControlsVisible = false
                                runCatching { tvPlayerFocus.requestFocus() }
                            } else {
                                DiagnosticsLog.event("PlayerSurface TV controls opened pointer")
                                tvControlsVisible = true
                            }
                        }
                    },
            )
        }

        if (controller != null && !device.isTv) {
            PlayerGestureControls(
                positionMs = positionMs,
                durationMs = durationMs,
                dragGestures = playerGestures,
                onTap = {
                    phoneControlsVisible = !phoneControlsVisible
                    phoneControlsInteraction++
                },
                onDoubleTap = {
                    val active = controller ?: return@PlayerGestureControls
                    playbackGestureIsPlaying = togglePlayerPlayback(active)
                },
                onSeek = { target ->
                    controller?.seekTo(target)
                    phoneControlsInteraction++
                },
                onHoldSpeed = { active ->
                    val activeController = controller ?: return@PlayerGestureControls
                    if (active) {
                        preHoldSpeed = activeController.playbackParameters.speed
                        activeController.setPlaybackSpeed(2f)
                    } else {
                        activeController.setPlaybackSpeed(preHoldSpeed)
                    }
                },
            )
        }

        if (controller != null && !device.isTv && phoneControlsVisible) {
            PlayerControlsScaffold(
                isPlaying = playbackIsPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                hasPrevious = canGoPrevious,
                hasNext = hasNextEpisode,
                onPrevious = { currentOnPreviousEpisode?.invoke() },
                onRewind = {
                    controller?.seekBack()
                    phoneControlsInteraction++
                },
                onPlayPause = {
                    controller?.let(::togglePlayerPlayback)
                    phoneControlsInteraction++
                },
                onForward = {
                    controller?.seekForward()
                    phoneControlsInteraction++
                },
                onNext = { currentOnNextEpisode() },
                onSeek = { target ->
                    controller?.seekTo(target)
                    phoneControlsInteraction++
                },
                onInteract = { phoneControlsInteraction++ },
            ) {

                PlayerControlIconButton(
                    "Subtitles",
                    Icons.Default.ClosedCaption,
                    onClick = {
                        controller?.let { toggleSubtitles(it, trackNameProvider) }
                        phoneControlsInteraction++
                    },
                )
                CastButton(Modifier.size(48.dp))
                PlayerControlIconButton(
                    if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    onClick = {
                        onToggleFullscreen?.invoke()
                        phoneControlsInteraction++
                    },
                )
                PlayerControlIconButton(
                    "Settings",
                    Icons.Default.Settings,
                    onClick = {
                        settingsExpanded = true
                        phoneControlsInteraction++
                    },
                )
            }
        }

        playbackGestureIsPlaying?.let { isPlaying ->
            PlaybackGestureIndicator(isPlaying, Modifier.align(Alignment.Center))
        }



        if (settingsExpanded && controller != null) {
            val activeController = controller!!
            val trackHeights = availableVideoHeights(activeController, nativeQualityStreams)
            val qualityOptions = buildList {
                add(PlayerQualityOption("Auto", pinnedVideoHeight == null) { changeVideoHeight(activeController, null) })
                trackHeights.forEach { height ->
                    add(PlayerQualityOption("${height}p", pinnedVideoHeight == height) { changeVideoHeight(activeController, height) })
                }
            }
            val audioTracks = trackOptions(activeController, trackNameProvider, C.TRACK_TYPE_AUDIO)
            val hasAudioOverride = activeController.hasTrackOverride(C.TRACK_TYPE_AUDIO)
            val audioOptions = if (audioTracks.size > 1) {
                buildList {
                    add(PlayerQualityOption("Auto", !hasAudioOverride) { applyAudioTrack(activeController, null) })
                    audioTracks.forEach { track ->
                        add(PlayerQualityOption(track.name, hasAudioOverride && track.selected) {
                            applyAudioTrack(activeController, track)
                        })
                    }
                }
            } else {
                emptyList()
            }
            val subtitleTracks = trackOptions(activeController, trackNameProvider, C.TRACK_TYPE_TEXT)
            val subtitleOptions = if (subtitleTracks.isNotEmpty()) {
                buildList {
                    add(PlayerQualityOption("Off", subtitleTracks.none { it.selected }) {
                        applyTextTrack(activeController, null)
                    })
                    subtitleTracks.forEach { track ->
                        add(PlayerQualityOption(track.name, track.selected) { applyTextTrack(activeController, track) })
                    }
                }
            } else {
                emptyList()
            }
            PlayerSettingsSheet(
                onDismiss = {
                    settingsExpanded = false
                    restoreTvControlsFocus()
                },
                autoplay = autoplay,
                onAutoplayChange = SettingsStore::setAutoplay,
                speed = activeController.playbackParameters.speed,
                onSpeedChange = { activeController.setPlaybackSpeed(it) },
                qualityOptions = qualityOptions,
                subtitleOptions = subtitleOptions,
                audioOptions = audioOptions,
                contentScale = contentScale,
                onContentScaleChange = { scale ->
                    contentScale = scale
                    playerView?.resizeMode = when (scale) {
                        PlayerContentScale.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        PlayerContentScale.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        PlayerContentScale.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                onCaptionAppearance = {
                    settingsExpanded = false
                    captionAppearanceVisible = true
                },
                subtitleDelayMs = subtitleDelayMs.takeIf { subtitleOptions.size > 1 },
                onSubtitleDelayChange = { SubtitleDelay.set(it) },
                autoSkip = autoSkipIntroOutro,
                onAutoSkipChange = SettingsStore::setAutoSkipIntroOutro,
            )
        }

        if (captionAppearanceVisible) {
            CaptionAppearanceDialog(
                onDismiss = {
                    captionAppearanceVisible = false
                    restoreTvControlsFocus()
                },
            )
        }

        if (controller == null) {
            com.shubh.anililitv.ui.components.WaterFillLogoIndicator(
                modifier = Modifier.align(Alignment.Center),
                size = 72.dp,
            )
        }

        if (tvSeekIndicator != null && !tvControlsVisible && device.isTv) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(
                    text = tvSeekIndicator!!,
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(32.dp)
                )
            }
        }
        
        val isBuffering = playbackState == Player.STATE_BUFFERING
        if ((!playbackIsPlaying || isBuffering) && controller != null && !tvControlsVisible && device.isTv) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                androidx.compose.material3.Text(
                    text = if (isBuffering) "LOADING" else "PAUSED",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(32.dp).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (device.isTv && focusPlayerOnStart && tvControlsVisible && controller != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    androidx.compose.material3.Text(
                        seriesTitle,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.material3.Text(
                        episodeTitle,
                        color = Color.Gray,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
            }

            TvPlayerControls(
                positionProvider = { positionMs },
                durationProvider = { durationMs },
                isPlaying = playbackIsPlaying,
                isMuted = controller?.volume?.let { it <= 0.001f } == true,
                hasPrevious = canGoPrevious,
                hasNext = hasNextEpisode,
                playPauseFocusRequester = tvPlayPauseFocus,
                onPrevious = { currentOnPreviousEpisode?.invoke() },
                onRewind = {
                    DiagnosticsLog.event("PlayerSurface TV control rewind")
                    queueTvSeek(-TV_SEEK_STEP_MS)
                },
                onPlayPause = {
                    DiagnosticsLog.event("PlayerSurface TV control playPause")
                    controller?.let(::togglePlayerPlayback)
                },
                onForward = {
                    DiagnosticsLog.event("PlayerSurface TV control forward")
                    queueTvSeek(TV_SEEK_STEP_MS)
                },
                onNext = currentOnNextEpisode,
                onToggleMute = {
                    DiagnosticsLog.event("PlayerSurface TV control toggleMute")
                    controller?.let { active ->
                        if (active.volume > 0.001f) {
                            lastAudibleVolume = active.volume
                            active.volume = 0f
                        } else {
                            active.volume = lastAudibleVolume.coerceAtLeast(0.1f)
                        }
                    }
                },
                onSettings = {
                    DiagnosticsLog.event("PlayerSurface TV control settings")
                    settingsExpanded = true
                },
                onFullscreen = onToggleFullscreen,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }



        val actionState = remember(introStartMs, introEndMs, outroStartMs, outroEndMs, controller, device.isTv, onNextEpisode) {
            androidx.compose.runtime.derivedStateOf {
                when {
                    introEndMs != null && positionMs in introStartMs..introEndMs ->
                        "Skip Intro" to { 
                            controller?.seekTo(introEndMs)
                            if (device.isTv) tvControlsVisible = false
                        }
                    outroStartMs != null && outroEndMs != null && positionMs in outroStartMs..outroEndMs ->
                        "Next Episode" to {
                            onNextEpisode()
                            if (device.isTv) tvControlsVisible = false
                        }
                    else -> null
                }
            }
        }
        val action = actionState.value
        val skipActionFocus = remember { FocusRequester() }
        LaunchedEffect(action?.first, tvControlsVisible, device.isTv, focusPlayerOnStart) {
            if (device.isTv && focusPlayerOnStart) {
                if (tvControlsVisible) {
                    kotlinx.coroutines.delay(150)
                    runCatching { tvPlayPauseFocus.requestFocus() }
                } else if (action != null) {
                    kotlinx.coroutines.delay(150)
                    runCatching { skipActionFocus.requestFocus() }
                }
            }
        }
        @Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
        action?.let { (label, onClick) ->
            val controlsVisible = if (device.isTv) tvControlsVisible else phoneControlsVisible
            val edgeInset = if (device.isTv) TV_SAFE_AREA_INSET else 24.dp
            val actionModifier = if (controlsVisible) {
                Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (device.isTv) TV_SAFE_AREA_INSET else 16.dp)
            } else {
                Modifier.align(Alignment.BottomStart).padding(start = edgeInset, bottom = edgeInset)
            }
            var isFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isFocused) Color.White else Color.Black.copy(alpha = 0.5f),
                    contentColor = if (isFocused) Color.Black else Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = actionModifier
                    .focusRequester(skipActionFocus)
                    .focusProperties { 
                        down = if (controlsVisible) tvPlayPauseFocus else FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                    .onFocusChanged { 
                        isFocused = it.isFocused
                        skipActionFocused = it.isFocused
                    },
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun togglePlayerPlayback(controller: MediaController): Boolean {
    val willPlay = playerToggleWillPlay(controller.playWhenReady)
    if (willPlay) controller.play() else controller.pause()
    return willPlay
}

internal fun playerToggleWillPlay(playWhenReady: Boolean): Boolean = !playWhenReady

@OptIn(UnstableApi::class)
private fun toggleSubtitles(controller: MediaController, trackNameProvider: DefaultTrackNameProvider) {
    val tracks = trackOptions(controller, trackNameProvider, C.TRACK_TYPE_TEXT)
    if (tracks.isEmpty()) return
    if (tracks.any { it.selected }) applyTextTrack(controller, null)
    else applyTextTrack(controller, tracks.first())
}

@OptIn(UnstableApi::class)
@Composable
private fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val router = remember(context) { runCatching { MediaRouter.getInstance(context) }.getOrNull() }
    val selector = remember(context) {
        runCatching { CastContext.getSharedInstance(context).mergedSelector }.getOrNull()
    }
    var castRoutesAvailable by remember { mutableStateOf(false) }

    DisposableEffect(router, selector) {
        if (router == null || selector == null) {
            castRoutesAvailable = false
            onDispose { }
        } else {
            fun refresh() {
                castRoutesAvailable = router.isRouteAvailable(
                    selector,
                    MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE,
                )
            }
            val callback = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            }
            router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            refresh()
            onDispose { router.removeCallback(callback) }
        }
    }

    if (castRoutesAvailable) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val themed = ContextThemeWrapper(ctx, R.style.Theme_MiruroNative_MediaRouter)
                val button = MediaRouteButton(themed)
                button.setDialogFactory(ThemedMediaRouteDialogFactory())
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx, button) }
                button
            },
        )
    } else {
        androidx.compose.material3.IconButton(
            onClick = { openSystemCastPicker(context) },
            modifier = modifier,
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.Cast,
                contentDescription = "Cast to TV",
                tint = Color.White,
            )
        }
    }
}

private fun openSystemCastPicker(context: Context) {
    val candidates = listOf(
        android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS),
        android.content.Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
        android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    DiagnosticsLog.event("CastButton no system cast settings activity found")
}

private fun availableVideoHeights(
    controller: MediaController,
    qualityStreams: List<StreamItem>,
): List<Int> = (
    controller.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        .flatMap { group ->
            (0 until group.length).filter(group::isTrackSupported)
                .map { group.getTrackFormat(it).height }
        }
        .filter { it > 0 } + qualityStreams.mapNotNull(StreamItem::declaredVideoHeight)
    ).distinct().sortedDescending()

private fun applyVideoHeight(controller: MediaController, height: Int?): Boolean {
    val builder = controller.videoSelectionBuilder()
    if (height == null) {
        controller.trackSelectionParameters = builder.build()
        DiagnosticsLog.event("PlayerSurface quality selection mode=auto")
        return true
    }

    val option = controller.currentTracks.groups.asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        .flatMap { group ->
            (0 until group.length).asSequence()
                .filter(group::isTrackSupported)
                .map { index -> group to index }
        }
        .firstOrNull { (group, index) -> group.getTrackFormat(index).height == height }
    if (option == null) {
        DiagnosticsLog.event("PlayerSurface quality selection rejected height=$height unavailable")
        return false
    }

    val (group, index) = option
    builder.setOverrideForType(TrackSelectionOverride(group.getMediaTrackGroup(), listOf(index)))
    controller.trackSelectionParameters = builder.build()
    DiagnosticsLog.event(
        "PlayerSurface quality selection mode=manual height=$height index=$index tracks=${group.length}",
    )
    return true
}

private fun clearVideoSelection(controller: MediaController) {
    controller.trackSelectionParameters = controller.videoSelectionBuilder().build()
}

private fun MediaController.videoSelectionBuilder(): TrackSelectionParameters.Builder =
    trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .clearVideoSizeConstraints()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)

private fun MediaController.hasVideoHeight(height: Int): Boolean = currentTracks.groups.any { group ->
    group.type == C.TRACK_TYPE_VIDEO && group.isSupported &&
        (0 until group.length).any { index ->
            group.isTrackSupported(index) && group.getTrackFormat(index).height == height
        }
}

private fun MediaController.hasTrackOverride(trackType: Int): Boolean =
    trackSelectionParameters.overrides.values.any { it.type == trackType }

@OptIn(UnstableApi::class)
private fun trackOptions(
    controller: MediaController,
    trackNameProvider: DefaultTrackNameProvider,
    trackType: Int,
): List<TrackOption> = controller.currentTracks.groups
    .filter { it.type == trackType && it.isSupported }
    .flatMap { group ->
        (0 until group.length)
            .filter { group.isTrackSupported(it) }
            .map { index ->
                TrackOption(
                    trackGroup = group.getMediaTrackGroup(),
                    trackIndex = index,
                    name = trackNameProvider.getTrackName(group.getTrackFormat(index)),
                    selected = group.isTrackSelected(index),
                )
            }
    }

private fun applyAudioTrack(controller: Player, option: TrackOption?) {
    val builder = controller.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
    if (option != null) {
        builder.setOverrideForType(TrackSelectionOverride(option.trackGroup, listOf(option.trackIndex)))
    }
    controller.trackSelectionParameters = builder.build()
    DiagnosticsLog.event(
        if (option == null) {
            "PlayerSurface audio selection mode=auto"
        } else {
            "PlayerSurface audio selection mode=manual name=${option.name.take(80)}"
        },
    )
}

internal fun applyCategoryAudioPreference(controller: Player, category: String, provider: String): Boolean {
    val wantsDub = category.equals("dub", ignoreCase = true)
    val options = controller.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { group.isTrackSupported(it) }
                .map { index ->
                    val format = group.getTrackFormat(index)
                    TrackOption(
                        trackGroup = group.getMediaTrackGroup(),
                        trackIndex = index,
                        name = listOfNotNull(format.label, format.language).joinToString(" ").ifBlank { "Audio" },
                        selected = group.isTrackSelected(index),
                    )
                }
        }
    if (options.size < 2) return true
    val selected = options.firstOrNull { it.selected }
    val preferred = options.minByOrNull { categoryAudioRank(it.name, wantsDub) }
        ?.takeIf { categoryAudioRank(it.name, wantsDub) < 50 }
        ?: return true
    if (selected?.trackGroup == preferred.trackGroup && selected.trackIndex == preferred.trackIndex) return true
    applyAudioTrack(controller, preferred)
    DiagnosticsLog.event(
        "PlayerSurface multi-audio selected provider=$provider category=$category name=${preferred.name.take(80)}",
    )
    return true
}

internal fun categoryAudioRank(name: String, wantsDub: Boolean): Int {
    val lower = name.lowercase()
    return if (wantsDub) {
        when {
            lower.contains("english") || lower.contains(" eng") || lower == "en" -> 0
            lower.contains("dub") -> 5
            else -> 100
        }
    } else {
        when {
            lower.contains("japanese") || lower.contains(" jpn") || lower.contains(" ja") || lower == "ja" -> 0
            lower.contains("native") -> 5
            else -> 100
        }
    }
}

private val MULTI_AUDIO_PROVIDERS = setOf("reanime", "kaa")

private fun applyTextTrack(controller: MediaController, option: TrackOption?) {
    val builder = controller.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, option == null)
    if (option != null) {
        builder.setOverrideForType(TrackSelectionOverride(option.trackGroup, listOf(option.trackIndex)))
    }
    controller.trackSelectionParameters = builder.build()
    DiagnosticsLog.event(
        if (option == null) {
            "PlayerSurface subtitle selection mode=off"
        } else {
            "PlayerSurface subtitle selection mode=manual name=${option.name.take(80)}"
        },
    )
}

@OptIn(UnstableApi::class)
private fun PlayerView.applyCaptionStyle(style: CaptionStyle, controlsVisible: Boolean = false) {
    val view = subtitleView ?: return
    view.setApplyEmbeddedStyles(false)
    view.setApplyEmbeddedFontSizes(false)
    view.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
    view.setStyle(
        CaptionStyleCompat(
            style.textArgb,
            style.backgroundArgb,
            android.graphics.Color.TRANSPARENT,
            style.edgeStyle.toMedia3EdgeType(),
            android.graphics.Color.BLACK,
            if (style.boldText) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT,
        ),
    )
    view.setFractionalTextSize(
        SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.textScalePercent / 100f,
    )
    val effectiveMarginPercent = if (controlsVisible) {
        (style.bottomMarginPercent + 10).coerceAtMost(40)
    } else {
        style.bottomMarginPercent
    }
    view.setBottomPaddingFraction(effectiveMarginPercent / 100f)
}

@OptIn(UnstableApi::class)
private fun CaptionEdgeStyle.toMedia3EdgeType(): Int = when (this) {
    CaptionEdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
    CaptionEdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
    CaptionEdgeStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindUnifiedSettingsButton(onClick: () -> Unit) {
    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener {
        showController()
        onClick()
    }
}

private fun isInSkipWindow(positionMs: Long, startMs: Long?, endMs: Long?): Boolean {
    val start = startMs ?: 0L
    val end = endMs ?: return false
    return end > start && positionMs in start until end
}

internal fun Float.formatPlaybackSpeed(): String = if (this % 1f == 0f) {
    "${toInt()}x"
} else {
    "${this}x"
}

private data class TrackOption(
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val name: String,
    val selected: Boolean,
)

internal val PlaybackSpeeds = listOf(
    0.25f,
    0.5f,
    0.75f,
    0.9f,
    1f,
    1.1f,
    1.25f,
    1.5f,
    1.75f,
    2f,
    2.5f,
    3f,
    3.5f,
    4f,
)

private fun Int.stateName(): String = when (this) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> toString()
}

private fun StreamItem.typeLabel(): String = when {
    isEmbed -> "embed"
    isHls -> "hls"
    else -> "direct"
}

private fun StreamItem.host(): String =
    runCatching { Uri.parse(url).host }.getOrNull() ?: "unknown"

private fun StreamItem.declaredVideoHeight(): Int? = height ?: declaredVideoHeight(quality)

internal fun declaredVideoHeight(label: String?): Int? = label
    ?.let { Regex("""(?<!\d)(\d{3,4})p\b""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
    ?.toIntOrNull()
    ?.takeIf { it in 144..4320 }

private fun androidx.media3.common.Tracks.diagnosticSummary(): String = groups
    .filter { it.isSupported }
    .joinToString(separator = ";", limit = 12, truncated = "…") { group ->
        val type = when (group.type) {
            C.TRACK_TYPE_VIDEO -> "video"
            C.TRACK_TYPE_AUDIO -> "audio"
            C.TRACK_TYPE_TEXT -> "text"
            else -> "type${group.type}"
        }
        val options = (0 until group.length)
            .filter(group::isTrackSupported)
            .joinToString(separator = ",", limit = 12, truncated = "…") { index ->
                val format = group.getTrackFormat(index)
                val label = when (group.type) {
                    C.TRACK_TYPE_VIDEO -> format.height.takeIf { it > 0 }?.let { "${it}p" } ?: "unknown"
                    else -> listOfNotNull(format.label, format.language).joinToString("/").ifBlank { "unknown" }
                }
                label + if (group.isTrackSelected(index)) "*" else ""
            }
        "$type=$options"
    }

private fun mimeFor(url: String): String = when {
    url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
    url.contains(".ass", ignoreCase = true) || url.contains(".ssa", ignoreCase = true) ->
        MimeTypes.TEXT_SSA
    url.contains(".ttml", ignoreCase = true) || url.contains(".xml", ignoreCase = true) ->
        MimeTypes.APPLICATION_TTML
    else -> MimeTypes.TEXT_VTT
}

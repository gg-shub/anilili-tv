package com.shubh.anililitv.ui.watch

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.foundation.lazy.list.itemsIndexed as tvItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shubh.anililitv.data.ProviderCatalog
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.data.library.WatchlistEntry
import com.shubh.anililitv.data.model.Category
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.data.model.StreamItem
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.playback.EpisodeDownload
import com.shubh.anililitv.playback.EpisodeDownloadMetadata
import com.shubh.anililitv.playback.EpisodeDownloadState
import com.shubh.anililitv.playback.EpisodeDownloadSubtitle
import com.shubh.anililitv.playback.BulkDownloadOutcome
import com.shubh.anililitv.playback.BulkEpisodeDownloads
import com.shubh.anililitv.playback.DownloadStorage
import com.shubh.anililitv.playback.EpisodeDownloads
import com.shubh.anililitv.playback.EpisodeDownloadUi
import com.shubh.anililitv.playback.EpisodeExport
import com.shubh.anililitv.playback.episodeDownloadBadges
import com.shubh.anililitv.ui.components.DownloadCoverBadge
import com.shubh.anililitv.playback.OfflineEpisode
import com.shubh.anililitv.playback.offlineEpisodes
import com.shubh.anililitv.playback.PlaybackService
import com.shubh.anililitv.data.settings.DownloadDestination
import com.shubh.anililitv.data.settings.DownloadQuality
import com.shubh.anililitv.data.settings.EpisodeLayout
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.components.EPISODE_BROWSER_MIN_EPISODES
import com.shubh.anililitv.ui.components.EpisodeBrowserBar
import com.shubh.anililitv.ui.components.EpisodeNumberChip
import com.shubh.anililitv.ui.components.ErrorBox
import com.shubh.anililitv.ui.components.LoadingBox
import com.shubh.anililitv.ui.components.blockIndexContaining
import com.shubh.anililitv.ui.components.episodeBlocks
import com.shubh.anililitv.ui.components.episodeWatchFraction
import com.shubh.anililitv.ui.components.filterEpisodes
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.focusHighlight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WatchScreen(
    animeId: Int,
    provider: String,
    category: String,
    episode: String,
    showEpisodeListInitially: Boolean = false,
    inPictureInPicture: Boolean = false,
    onPictureInPictureReadyChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    vm: WatchViewModel = viewModel(),
) {
    LaunchedEffect(animeId, provider, category, episode) {
        DiagnosticsLog.event("WatchScreen composed id=$animeId provider=$provider category=$category episode=$episode")
        vm.start(animeId, provider, category, episode)
    }
    val state by vm.state.collectAsState()
    val watchlist by LibraryStore.watchlist.collectAsState()
    val context = LocalContext.current
    val device = LocalAppDeviceProfile.current
    var webFallback by remember { mutableStateOf(false) }
    var fullscreen by remember(animeId, showEpisodeListInitially, device.isTv) {
        mutableStateOf(device.isTv && !showEpisodeListInitially)
    }
    val activity = remember(context) { context.findActivity() }
    val currentOnBack by rememberUpdatedState(onBack)
    var embeddedPlaybackStopper by remember { mutableStateOf<(() -> Unit)?>(null) }
    val currentEmbeddedPlaybackStopper by rememberUpdatedState(embeddedPlaybackStopper)
    val pictureInPictureReady = state is UiState.Success
    DisposableEffect(pictureInPictureReady) {
        onPictureInPictureReadyChanged(pictureInPictureReady)
        onDispose {
            if (pictureInPictureReady) onPictureInPictureReadyChanged(false)
        }
    }
    val pauseAndBack = remember {
        {
            currentEmbeddedPlaybackStopper?.invoke()
            vm.commitPlaybackPosition()
            PlaybackService.pauseActivePlayback()
            currentOnBack()
        }
    }

    LaunchedEffect(webFallback) {
        DiagnosticsLog.event("WatchScreen webFallback=$webFallback")
        if (webFallback) PlaybackService.stopActivePlayback()
    }

    LaunchedEffect(state, webFallback) {
        when (val s = state) {
            is UiState.Loading -> {
                delay(10_000)
                if (state is UiState.Loading && !webFallback) {
                    DiagnosticsLog.event("WatchScreen still loading after 10000ms id=$animeId provider=$provider")
                }
            }
            is UiState.Error -> DiagnosticsLog.event("WatchScreen error visible message=${s.message.take(160)}")
            is UiState.Success -> {
                val stream = s.data.chosenStream
                if (stream?.quality.equals("Downloaded", ignoreCase = true) ||
                    stream?.url?.startsWith("file:", ignoreCase = true) == true ||
                    stream?.url?.startsWith("content:", ignoreCase = true) == true
                ) {
                    fullscreen = true
                }
                DiagnosticsLog.event(
                    "WatchScreen success visible provider=${s.data.provider} episode=${s.data.current.displayNumber} " +
                        "stream=${stream?.let { if (it.isEmbed) "embed" else if (it.isHls) "hls" else "direct" } ?: "none"} " +
                        "resolving=${s.data.isResolving}",
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (device.isTv) releaseImageMemoryForPlayback(context)
    }

    DisposableEffect(fullscreen, device.isTv) {
        val window = activity?.window
        if (activity != null && window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (fullscreen) {
                if (!device.isTv) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                if (!device.isTv) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = activity?.window
            if (activity != null && w != null) {
                if (!device.isTv) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var showCloseDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen) { showCloseDialog = true }

    if (showCloseDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { androidx.compose.material3.Text("Close Playback") },
            text = { androidx.compose.material3.Text("Do you want to close playback?") },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = androidx.compose.ui.graphics.Color(0xFF161616),
            titleContentColor = androidx.compose.ui.graphics.Color.White,
            textContentColor = androidx.compose.ui.graphics.Color.White,
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { 
                        PlaybackService.stopActivePlayback()
                        showCloseDialog = false
                        pauseAndBack()
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                ) {
                    androidx.compose.material3.Text("Yes", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showCloseDialog = false },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                ) {
                    androidx.compose.material3.Text("No", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            currentEmbeddedPlaybackStopper?.invoke()
            vm.commitPlaybackPosition()
            PlaybackService.pauseActivePlayback()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (webFallback) {
            var trackMenuVisible by remember { mutableStateOf(false) }

            if (trackMenuVisible) {
                val watchData = (state as? UiState.Success)?.data
                com.shubh.anililitv.ui.components.TrackListMenu(
                    entry = WatchlistEntry(
                        anilistId = animeId,
                        title = watchData?.seriesTitle ?: "",
                        cover = watchData?.artworkUrl ?: "",
                        format = watchData?.seriesFormat ?: "",
                        averageScore = watchData?.averageScore,
                    ),
                    onDismiss = { trackMenuVisible = false }
                )
            }

            EmbedWebView(
                url = "https://www.miruro.to/info/$animeId",
                referer = "https://www.miruro.to/",
                seriesTitle = "",
                episodeTitle = "",
                modifier = Modifier.fillMaxSize(),
                onFullscreenChanged = { fullscreen = it },
                onProgress = vm::onProgress,
                onPlaybackStopperChanged = { embeddedPlaybackStopper = it },
            )
            BackButton(pauseAndBack, Modifier.align(Alignment.TopStart))
            
            val saved = watchlist.any { it.anilistId == animeId }
            var addFocused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .onFocusChanged { addFocused = it.isFocused }
                    .focusHighlight(androidx.compose.foundation.shape.CircleShape)
                    .background(if (saved) Color.White else Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { trackMenuVisible = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (saved) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "Add to list",
                    tint = if (addFocused || saved) Color.Black else Color.White
                )
            }
            return@Box
        }

        when (val s = state) {
            is UiState.Loading -> {
                var showSlowNote by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(1_500)
                    showSlowNote = true
                }
                val loadingStatus by vm.loadingStatus.collectAsState()
                LoadingBox(
                    message = loadingStatus ?: if (showSlowNote) {
                        "Finding a source for this episode.\n" +
                            "The first time you open a title we check every server, so it can take a few seconds."
                    } else {
                        null
                    },
                )
                BackButton(pauseAndBack, Modifier.align(Alignment.TopStart))
            }
            is UiState.Error -> Column(Modifier.fillMaxSize()) {
                ErrorBox(s.message, onRetry = vm::retry, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { webFallback = true },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 24.dp)
                        .focusHighlight(RoundedCornerShape(20.dp)),
                ) { Text("Open in web player") }
                BackButton(pauseAndBack, Modifier.align(Alignment.Start))
            }
            is UiState.Success -> {
                val saved = watchlist.any { it.anilistId == s.data.anilistId }
                var trackMenuVisible by remember { mutableStateOf(false) }

                if (trackMenuVisible) {
                    com.shubh.anililitv.ui.components.TrackListMenu(
                        entry = WatchlistEntry(
                            anilistId = s.data.anilistId,
                            title = s.data.seriesTitle,
                            cover = s.data.artworkUrl,
                            format = s.data.seriesFormat,
                            averageScore = s.data.averageScore,
                        ),
                        onDismiss = { trackMenuVisible = false }
                    )
                }

                WatchContent(
                    data = s.data,
                    fullscreen = fullscreen || inPictureInPicture,
                    saved = saved,
                    onToggleSaved = {
                        trackMenuVisible = true
                    },
                    onBack = pauseAndBack,
                    onPrev = vm::prev,
                    onNext = vm::next,
                    onChangeSource = vm::changeSource,
                    onChangeCategory = vm::changeCategory,
                    onSelectEpisode = { index ->
                        if (device.isTv) fullscreen = true
                        vm.playIndex(index)
                    },
                    onWebFallback = { webFallback = true },
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onFullscreenChanged = { fullscreen = it },
                    onProgress = vm::onProgress,
                    onPlaybackError = vm::onPlaybackError,
                    onPlaybackStopperChanged = { embeddedPlaybackStopper = it },
                    onPlayerClosed = vm::commitPlaybackPosition,
                    onDownloadSeries = { episodes, quality ->
                        BulkEpisodeDownloads.start(
                            context = context,
                            anilistId = s.data.anilistId,
                            seriesTitle = s.data.seriesTitle,
                            artworkUrl = s.data.artworkUrl,
                            category = s.data.category,
                            preferredProvider = s.data.provider,
                            episodes = episodes,
                            catalog = vm.episodeCatalog(),
                            quality = quality,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchContent(
    data: WatchData,
    fullscreen: Boolean,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChangeSource: (String, String) -> Unit,
    onChangeCategory: (String) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onWebFallback: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    onProgress: (Long, Long) -> Unit,
    onPlaybackError: (String, String, Long) -> Unit,
    onPlaybackStopperChanged: (((() -> Unit)?) -> Unit)? = null,
    onPlayerClosed: () -> Unit = {},
    onDownloadSeries: (List<EpisodeItem>, DownloadQuality) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val device = LocalAppDeviceProfile.current
    val downloads by EpisodeDownloads.downloads(context).collectAsState()
    val exportedEpisodes by EpisodeExport.exported(context).collectAsState()
    val preparingIds by EpisodeDownloads.preparingIds.collectAsState()
    val downloadId = EpisodeDownloads.idFor(
        data.anilistId,
        data.category.api,
        data.current.displayNumber,
    )
    val episodeDownload = downloads.firstOrNull { it.id == downloadId }
    val streamForDownload = data.chosenStream?.takeIf(EpisodeDownloads::canDownload)
    val canSaveToDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        EpisodeDownloads.canSaveToDevice(streamForDownload)
    val downloadPreparing = downloadId in preparingIds
    val defaultDownloadQuality by SettingsStore.downloadQuality.collectAsState()
    val defaultDownloadDestination by SettingsStore.downloadDestination.collectAsState()
    var downloadDialogVisible by remember(
        data.anilistId,
        data.category,
        data.current.pipeId,
        data.provider,
    ) { mutableStateOf(false) }
    var selectedDownloadQuality by remember { mutableStateOf(defaultDownloadQuality) }
    var downloadBatchSize by remember { mutableIntStateOf(1) }
    val remainingEpisodes = remember(
        data.episodes,
        data.current.number,
        data.anilistId,
        data.category,
        downloads,
        exportedEpisodes,
    ) {
        BulkEpisodeDownloads.pendingEpisodes(
            episodes = data.episodes,
            anilistId = data.anilistId,
            category = data.category.api,
            fromNumber = data.current.number,
            alreadyHave = offlineEpisodes(downloads, exportedEpisodes)
                .filter(OfflineEpisode::isPlayable)
                .map(OfflineEpisode::id)
                .toSet(),
        )
    }
    var selectedDownloadDestination by remember { mutableStateOf(defaultDownloadDestination) }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingDownloadAction
        pendingDownloadAction = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(
                context,
                "Notification permission is needed for background downloads.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val queueCurrentEpisode: (DownloadQuality, DownloadDestination) -> Unit = queue@{ quality, destination ->
        streamForDownload?.let { stream ->
            val selectedStream = selectDownloadStream(
                current = stream,
                available = data.sources.streams,
                quality = quality,
            )
            val effectiveDestination = if (canSaveToDevice) {
                destination
            } else {
                DownloadDestination.APP_ONLY
            }
            val metadata = EpisodeDownloadMetadata(
                anilistId = data.anilistId,
                seriesTitle = data.seriesTitle,
                episodeNumber = data.current.displayNumber,
                episodeTitle = data.current.title,
                artworkUrl = data.artworkUrl,
                provider = data.provider,
                category = data.category.api,
                referer = selectedStream.referer,
                headers = selectedStream.headers,
                subtitles = data.sources.subtitles.map { subtitle ->
                    EpisodeDownloadSubtitle(
                        url = subtitle.url,
                        label = subtitle.label,
                        language = subtitle.language,
                    )
                },
            )
            val appDownloadCanStart = episodeDownload == null ||
                episodeDownload.state == EpisodeDownloadState.FAILED
            val exportsToDevice = effectiveDestination.includesDevice
            val needsAppDownload = effectiveDestination.includesApp || exportsToDevice
            val enqueue = {
                if (exportsToDevice) {
                    EpisodeExport.request(
                        context = context,
                        downloadId = downloadId,
                        deleteAppCopyAfter = !effectiveDestination.includesApp && appDownloadCanStart,
                    )
                }
                if (needsAppDownload && appDownloadCanStart) {
                    EpisodeDownloads.enqueue(
                        context = context,
                        metadata = metadata,
                        stream = selectedStream,
                        quality = quality,
                    ) { appResult ->
                        val message = when {
                            appResult.isFailure -> appResult.exceptionOrNull()?.message
                                ?: "Could not start the download."
                            exportsToDevice && effectiveDestination.includesApp ->
                                "Episode added to Anilili. The MP4 is written to Downloads once it finishes."
                            exportsToDevice ->
                                "Downloading now. The MP4 is written to Downloads once it finishes."
                            else -> "Episode added to Anilili downloads."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    val message = when {
                        exportsToDevice && episodeDownload?.isComplete == true ->
                            "Saving this episode to Downloads as MP4."
                        exportsToDevice ->
                            "Already downloading. The MP4 follows once it finishes."
                        effectiveDestination.includesApp -> "This episode is already in Anilili downloads."
                        else -> "Could not start the download."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            if (
                needsAppDownload &&
                appDownloadCanStart &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                pendingDownloadAction = enqueue
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enqueue()
            }
        }
        Unit
    }
    val showDownloadDialog: () -> Unit = {
        selectedDownloadQuality = defaultDownloadQuality
        selectedDownloadDestination = if (canSaveToDevice) {
            if (
                episodeDownload?.state == EpisodeDownloadState.COMPLETED &&
                defaultDownloadDestination == DownloadDestination.APP_ONLY
            ) {
                DownloadDestination.DEVICE_ONLY
            } else {
                defaultDownloadDestination
            }
        } else {
            DownloadDestination.APP_ONLY
        }
        downloadDialogVisible = true
    }

    if (downloadDialogVisible && streamForDownload != null) {
        EpisodeDownloadDialog(
            stream = streamForDownload,
            quality = selectedDownloadQuality,
            destination = selectedDownloadDestination,
            canSaveToDevice = canSaveToDevice,
            alreadyInApp = episodeDownload?.state == EpisodeDownloadState.COMPLETED,
            alreadyInDownloads = exportedEpisodes.any { it.downloadId == downloadId },
            onQualityChange = { selectedDownloadQuality = it },
            onDestinationChange = { selectedDownloadDestination = it },
            batchSize = downloadBatchSize,
            onBatchSizeChange = { downloadBatchSize = it },
            remainingCount = remainingEpisodes.size,
            onDismiss = { downloadDialogVisible = false },
            onConfirm = {
                val quality = selectedDownloadQuality
                val destination = selectedDownloadDestination
                val batch = downloadBatchSize
                downloadDialogVisible = false
                if (batch > 1 && remainingEpisodes.isNotEmpty()) {
                    onDownloadSeries(remainingEpisodes.take(batch), quality)
                } else {
                    queueCurrentEpisode(quality, destination)
                }
            },
        )
    }
    val summaryFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val tvEpisodeListState = rememberLazyListState()
    var hasShownFullscreen by remember { mutableStateOf(fullscreen) }

    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            hasShownFullscreen = true
        } else if (device.isTv) {
            delay(if (hasShownFullscreen) 180 else 950)
            runCatching { summaryFocus.requestFocus() }
                .onSuccess { DiagnosticsLog.event("Watch TV selector focus requested") }
                .onFailure { DiagnosticsLog.throwable("Watch TV selector focus failed", it) }
        }
    }

    LaunchedEffect(data.currentIndex, fullscreen, device.isTv) {
        if (device.isTv && !fullscreen) {
            val returningFromFullscreen = hasShownFullscreen
            delay(if (returningFromFullscreen) 260 else 850)
            tvEpisodeListState.scrollToItem(0)
            if (!returningFromFullscreen) {
                delay(300)
                tvEpisodeListState.scrollToItem(0)
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(if (fullscreen) Modifier else Modifier.statusBarsPadding()),
    ) {
        val availableWidthDp = maxWidth
        val availableHeightDp = maxHeight
        Column(Modifier.fillMaxSize()) {
        val playerModifier = if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            val naturalHeight = availableWidthDp * 9f / 16f
            val landscape = availableWidthDp > availableHeightDp
            val maxHeightFraction = when {
                device.isTv -> 0.56f
                landscape -> 0.66f
                else -> 0.62f
            }
            val playerHeight = minOf(naturalHeight, availableHeightDp * maxHeightFraction)
            Modifier.fillMaxWidth().height(playerHeight)
        }
        val playerFocusModifier = if (device.isTv && !fullscreen) {
            Modifier
                .semantics { contentDescription = "Video player" }
        } else {
            Modifier
        }
        Box(playerModifier.then(playerFocusModifier).background(Color.Black)) {
            val stream = data.chosenStream
            val playerKind = when {
                stream == null -> "none"
                stream.isEmbed || ProviderCatalog.isEmbed(data.provider) -> "embed"
                else -> "native"
            }
            key(playerKind) {
                when {
                    stream == null -> NoSource(onWebFallback)
                    stream.isEmbed || ProviderCatalog.isEmbed(data.provider) ->
                        Box(Modifier.fillMaxSize()) {
                            LaunchedEffect(stream.url) { PlaybackService.stopActivePlayback() }
                            EmbedEpisodeNavigationEffect(
                                hasPrevious = data.hasPrev,
                                hasNext = data.hasNext,
                                onPrevious = onPrev,
                                onNext = onNext,
                            )
                            EmbedWebView(
                                url = stream.url,
                                referer = stream.referer,
                                seriesTitle = data.seriesTitle,
                                episodeTitle = data.current.title ?: "Episode ${data.current.displayNumber}",
                                modifier = Modifier.fillMaxSize(),
                                qualityStreams = data.sources.embedStreams,
                                startPositionMs = data.startPositionMs,
                                skip = data.sources.skip,
                                onPreviousEpisode = onPrev,
                                onNextEpisode = onNext,
                                hasPreviousEpisode = data.hasPrev,
                                hasNextEpisode = data.hasNext,
                                focusPlayerOnStart = fullscreen,
                                isFullscreen = fullscreen,
                                onToggleFullscreen = onToggleFullscreen,
                                onFullscreenChanged = onFullscreenChanged,
                                onProgress = onProgress,
                                onPlaybackError = onPlaybackError.takeIf { data.provider == "allanime" },
                                onPlaybackStopperChanged = onPlaybackStopperChanged,
                                episodes = data.episodes,
                                currentIndex = data.currentIndex,
                                artworkUrl = data.artworkUrl,
                                onSelectEpisode = onSelectEpisode,
                            )
                        }
                    else -> PlayerSurface(
                        stream = stream,
                        qualityStreams = data.sources.streams.filterNot(StreamItem::isEmbed),
                        subtitles = data.sources.subtitles,
                        subtitleOffsetMs = data.sources.subtitleOffsetMs,
                        skip = data.sources.skip,
                        seriesTitle = data.seriesTitle,
                        episodeTitle = "Episode ${data.current.displayNumber}" +
                            (data.current.title?.let { ": $it" } ?: ""),
                        artworkUrl = data.artworkUrl,
                        animeId = data.anilistId,
                        provider = data.provider,
                        category = data.category.api,
                        episode = data.current.displayNumber,
                        onEnded = { if (com.shubh.anililitv.data.settings.SettingsStore.autoplay.value) onNext() },
                        onNextEpisode = onNext,
                        onError = onPlaybackError,
                        modifier = Modifier.fillMaxSize(),
                        onToggleFullscreen = onToggleFullscreen,
                        startPositionMs = data.startPositionMs,
                        onProgress = onProgress,
                        onPreviousEpisode = onPrev,
                        hasNextEpisode = data.hasNext,
                        hasPreviousEpisode = data.hasPrev,
                        focusPlayerOnStart = fullscreen,
                        isFullscreen = fullscreen,
                        episodes = data.episodes,
                        currentIndex = data.currentIndex,
                        onSelectEpisode = onSelectEpisode,
                    )
                }
            }
            if (device.isTv && !fullscreen) {
                LaunchedEffect(Unit) {
                    PlaybackService.pauseActivePlayback()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            PlaybackService.resumeActivePlayback()
                            onToggleFullscreen()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .semantics {
                                contentDescription = "Play"
                                onClick(label = "Play") {
                                    PlaybackService.resumeActivePlayback()
                                    onToggleFullscreen()
                                    true
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                val keyCode = event.nativeKeyEvent.keyCode
                                val activate = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                                    keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                                if (!activate) {
                                    false
                                } else {
                                    if (event.type == KeyEventType.KeyUp) {
                                        PlaybackService.resumeActivePlayback()
                                        onToggleFullscreen()
                                    }
                                    true
                                }
                            }
                            .focusHighlight(CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .focusable()
                            .size(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
            }
            if (data.isResolving) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    com.shubh.anililitv.ui.components.WaterFillLogoIndicator(size = 72.dp)
                }
            }
            if (!fullscreen) BackButton(onBack, Modifier.align(Alignment.TopStart))
        }

        if (fullscreen) return@Column

        if (!device.isTv) {
            MobileWatchDetails(
                data = data,
                saved = saved,
                onToggleSaved = onToggleSaved,
                episodeDownload = episodeDownload,
                downloadPreparing = downloadPreparing,
                canDownload = streamForDownload != null,
                canSaveToDevice = canSaveToDevice,
                onDownload = showDownloadDialog,
                focusRequester = sourceFocus,
                onChangeSource = onChangeSource,
                onChangeCategory = onChangeCategory,
                onSelectEpisode = onSelectEpisode,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            return@Column
        }

        var episodeQuery by remember(data.anilistId) { mutableStateOf("") }
        var chosenBlockIndex by remember(data.anilistId) { mutableStateOf<Int?>(null) }
        val blocks = remember(data.episodes) { episodeBlocks(data.episodes) }
        val indexByPipeId = remember(data.episodes) {
            data.episodes.withIndex().associate { (index, episode) -> episode.pipeId to index }
        }
        val tvBlockIndex = (chosenBlockIndex ?: blockIndexContaining(blocks, data.current.number))
            .coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
        val tvShownEpisodes = if (episodeQuery.isNotBlank()) {
            filterEpisodes(data.episodes, episodeQuery)
        } else {
            blocks.getOrNull(tvBlockIndex)?.episodes.orEmpty()
        }
        val episodeRows = remember(tvShownEpisodes, device.episodeColumns) {
            tvShownEpisodes.chunked(device.episodeColumns)
        }
        val tvBrowserVisible = data.episodes.size > EPISODE_BROWSER_MIN_EPISODES
        val tvBrowserFocus = remember { FocusRequester() }
        val tvHistory by LibraryStore.history.collectAsState()
        val tvResume = tvHistory.firstOrNull { it.anilistId == data.anilistId }
        val tvEpisodeListState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()
        androidx.tv.foundation.lazy.list.TvLazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = tvEpisodeListState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                WatchEpisodeSummary(
                    data = data,
                    saved = saved,
                    onToggleSaved = onToggleSaved,
                    episodeDownload = episodeDownload,
                    downloadPreparing = downloadPreparing,
                    canDownload = streamForDownload != null,
                    canSaveToDevice = canSaveToDevice,
                    onDownload = showDownloadDialog,
                    focusRequester = summaryFocus,
                    nextFocusRequester = sourceFocus,
                    modifier = Modifier.padding(
                        start = device.pagePadding,
                        end = device.pagePadding,
                        top = 12.dp,
                        bottom = 6.dp,
                    ),
                )
            }
            item {
                SourceSelectors(
                    data = data,
                    onChangeSource = onChangeSource,
                    onChangeCategory = onChangeCategory,
                    focusRequester = sourceFocus,
                    onToggleFullscreen = onToggleFullscreen,
                    downFocus = tvBrowserFocus.takeIf { tvBrowserVisible },
                )
                data.notice?.let { notice ->
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = device.pagePadding,
                            end = device.pagePadding,
                            bottom = 6.dp,
                        ),
                    )
                }
            }
            if (tvBrowserVisible) {
                item {
                    EpisodeBrowserBar(
                        blocks = blocks,
                        selectedBlockIndex = tvBlockIndex,
                        onSelectBlock = { chosenBlockIndex = it },
                        query = episodeQuery,
                        onQueryChange = { episodeQuery = it },
                        layout = EpisodeLayout.LIST,
                        onToggleLayout = {},
                        showLayoutToggle = false,
                        focusRequester = tvBrowserFocus,
                        modifier = Modifier.padding(horizontal = device.pagePadding, vertical = 6.dp),
                    )
                }
            }
            tvItemsIndexed(
                items = episodeRows,
                key = { _: Int, row: List<com.shubh.anililitv.data.model.EpisodeItem> -> row.first().pipeId },
                contentType = { _: Int, _: List<com.shubh.anililitv.data.model.EpisodeItem> -> "episode_row" }
            ) { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = device.pagePadding, vertical = 5.dp)
                        .then(
                            if (rowIndex == 0 && tvBrowserVisible) {
                                Modifier.focusProperties { up = tvBrowserFocus }
                            } else {
                                Modifier
                            }
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { episode ->
                        val flatIndex = indexByPipeId[episode.pipeId] ?: -1
                        EpisodeNumberChip(
                            episode = episode,
                            selected = flatIndex == data.currentIndex,
                            watchedFraction = episodeWatchFraction(tvResume, episode.number),
                            modifier = Modifier.weight(1f),
                            onClick = { if (flatIndex >= 0) onSelectEpisode(flatIndex) }
                        )
                    }
                    repeat(device.episodeColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (episodeQuery.isNotBlank() && tvShownEpisodes.isEmpty()) {
                item {
                    Text(
                        text = "No episode matches “$episodeQuery”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = device.pagePadding, vertical = 12.dp),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun WatchEpisodeSummary(
    data: WatchData,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    episodeDownload: EpisodeDownload?,
    downloadPreparing: Boolean,
    canDownload: Boolean,
    canSaveToDevice: Boolean,
    onDownload: () -> Unit,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val localHeartFocus = remember { FocusRequester() }
    val heartFocus = focusRequester ?: localHeartFocus
    var restoreHeartFocus by remember { mutableStateOf(false) }
    val toggleSaved = {
        restoreHeartFocus = nextFocusRequester != null
        onToggleSaved()
    }

    LaunchedEffect(saved) {
        if (restoreHeartFocus) {
            delay(60)
            runCatching { heartFocus.requestFocus() }
            restoreHeartFocus = false
        }
    }

    Column(modifier = modifier.fillMaxWidth().focusGroup()) {
        Text(
            text = data.current.title?.takeIf { it.isNotBlank() }
                ?: "Episode ${data.current.displayNumber}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = data.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = data.seriesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                data.popularity?.let { popularity ->
                    Text(
                        text = "${compactPopularity(popularity)} popularity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = if (saved) "Remove from list" else "Add to list"
                        onClick(label = if (saved) "Remove from list" else "Add to list") {
                            toggleSaved()
                            true
                        }
                    }
                    .focusRequester(heartFocus)
                    .focusProperties {
                        nextFocusRequester?.let { down = it }
                    }
                    .onPreviewKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        ) {
                            if (event.type == KeyEventType.KeyUp) toggleSaved()
                            return@onPreviewKeyEvent true
                        }
                        when {
                            keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN && nextFocusRequester != null -> {
                                if (event.type == KeyEventType.KeyUp) {
                                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        kotlinx.coroutines.delay(32)
                                        nextFocusRequester.requestFocus()
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusHighlight(CircleShape)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .background(if (saved) Color.White else Color.Transparent)
                    .pointerInput(saved) { detectTapGestures { toggleSaved() } }
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (saved) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = if (saved) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeDownloadDialog(
    stream: StreamItem,
    quality: DownloadQuality,
    destination: DownloadDestination,
    canSaveToDevice: Boolean,
    alreadyInApp: Boolean,
    alreadyInDownloads: Boolean,
    onQualityChange: (DownloadQuality) -> Unit,
    onDestinationChange: (DownloadDestination) -> Unit,
    batchSize: Int,
    onBatchSizeChange: (Int) -> Unit,
    remainingCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val confirmEnabled = !alreadyInApp ||
        (canSaveToDevice && destination.includesDevice)
    val context = LocalContext.current
    val device = LocalAppDeviceProfile.current
    val freeBytes = remember(quality) { DownloadStorage.freeBytes(context) }
    val perEpisodeBytes = remember(quality) { DownloadStorage.estimatedEpisodeBytes(quality.maxHeight) }
    val batchCount = batchSize.coerceAtMost(remainingCount)
    val batching = batchCount > 1
    val estimatedBytes = perEpisodeBytes * (if (batching) batchCount.toLong() else 1L)
    val tight = freeBytes <= estimatedBytes + DownloadStorage.HEADROOM_BYTES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download episode") },
        text = {
            Column {
                Text(
                    "Resolution",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    DownloadQuality.entries.forEach { option ->
                        FilterChip(
                            selected = quality == option,
                            onClick = { onQualityChange(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                        )
                    }
                }
                Text(
                    if (stream.isHls) {
                        "Anilili saves the best available rendition at or below this resolution."
                    } else {
                        "Direct files use the closest resolution offered by this source."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Text(
                    "Save to",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                if (canSaveToDevice) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        DownloadDestination.entries.forEach { option ->
                            FilterChip(
                                selected = destination == option,
                                onClick = { onDestinationChange(option) },
                                enabled = option != DownloadDestination.APP_ONLY || !alreadyInApp,
                                label = { Text(option.label) },
                                modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                            )
                        }
                    }
                    if (destination.includesDevice) {
                        Text(
                            "Episodes download to the library first, then get rewrapped into one MP4 " +
                                "under Downloads/Anilili — no re-encoding, and subtitles are saved alongside it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (alreadyInApp) {
                        Text(
                            "This episode is already in the Anilili offline library. You can still add a device copy.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (alreadyInDownloads) {
                        Text(
                            "An MP4 of this episode is already in your Downloads folder. Downloading " +
                                "again will save a second copy alongside it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    Text(
                        "Anilili offline library · Public device downloads require Android 10 or newer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                if (remainingCount > 0) {
                    Text(
                        "How much",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        FilterChip(
                            selected = !batching,
                            onClick = { onBatchSizeChange(1) },
                            label = { Text("This episode") },
                            modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                        )
                        BulkEpisodeDownloads.BATCH_SIZES
                            .filter { it <= remainingCount }
                            .forEach { size ->
                                FilterChip(
                                    selected = batchCount == size,
                                    onClick = { onBatchSizeChange(size) },
                                    label = { Text("Next $size") },
                                    modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                                )
                            }
                        if (remainingCount > 1 && BulkEpisodeDownloads.BATCH_SIZES.none { it <= remainingCount }) {
                            FilterChip(
                                selected = batching,
                                onClick = { onBatchSizeChange(remainingCount) },
                                label = { Text("All $remainingCount left") },
                                modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                            )
                        }
                    }
                    if (batching) {
                        Text(
                            "Episodes are fetched one at a time, and each one's source is resolved " +
                                "as its turn comes — links from these servers expire too quickly to " +
                                "collect up front. You can keep watching while it runs.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Text(
                    "Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    buildString {
                        append("About ")
                        append(Formatter.formatShortFileSize(context, estimatedBytes))
                        append(if (batching) " for $batchCount episodes · " else " for this episode · ")
                        append(Formatter.formatShortFileSize(context, freeBytes))
                        append(" free")
                        if (device.isTv && freeBytes > 0L) {
                            append(" (~")
                            append(freeBytes / estimatedBytes.coerceAtLeast(1L))
                            append(" episodes)")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (tight) {
                    Text(
                        "Storage is nearly full. Downloading now may fail part-way or crowd out " +
                            "other apps — free some space or pick a lower resolution first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    when {
                        tight -> "Download anyway"
                        batching -> "Download $batchCount"
                        else -> "Download"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun selectDownloadStream(
    current: StreamItem,
    available: List<StreamItem>,
    quality: DownloadQuality,
): StreamItem {
    if (current.isHls) return current
    val directCandidates = (listOf(current) + available)
        .filter(EpisodeDownloads::canSaveToDevice)
        .filter { candidate ->
            current.audio == null || candidate.audio == null || candidate.audio == current.audio
        }
        .distinctBy(StreamItem::url)
        .mapNotNull { candidate ->
            val height = candidate.height ?: declaredVideoHeight(candidate.quality)
            height?.let { candidate to it }
        }
    if (directCandidates.isEmpty()) return current
    val maxHeight = quality.maxHeight
    return if (maxHeight == null) {
        directCandidates.maxByOrNull { it.second }?.first ?: current
    } else {
        directCandidates
            .filter { it.second <= maxHeight }
            .maxByOrNull { it.second }
            ?.first
            ?: directCandidates.minByOrNull { it.second }?.first
            ?: current
    }
}

private fun String.cleanAniListDescription(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun compactPopularity(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000f)
    else -> value.toString()
}.replace(".0K", "K").replace(".0M", "M")

@Composable
private fun MobileWatchDetails(
    data: WatchData,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    episodeDownload: EpisodeDownload?,
    downloadPreparing: Boolean,
    canDownload: Boolean,
    canSaveToDevice: Boolean,
    onDownload: () -> Unit,
    focusRequester: FocusRequester,
    onChangeSource: (String, String) -> Unit,
    onChangeCategory: (String) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = LocalAppDeviceProfile.current
    val pad = device.pagePadding
    val historyEntries by LibraryStore.history.collectAsState()
    val resume = historyEntries.firstOrNull { it.anilistId == data.anilistId }
    val badgeContext = LocalContext.current
    val downloadIndex by EpisodeDownloads.downloads(badgeContext).collectAsState()
    val exportStatuses by EpisodeExport.statuses(badgeContext).collectAsState()
    val exportedEpisodes by EpisodeExport.exported(badgeContext).collectAsState()
    val downloadBadges = remember(downloadIndex, exportStatuses, exportedEpisodes) {
        episodeDownloadBadges(downloadIndex, exportStatuses, exportedEpisodes)
    }
    val episodeLayout by SettingsStore.episodeLayout.collectAsState()
    var episodeQuery by remember(data.anilistId) { mutableStateOf("") }
    var chosenBlockIndex by remember(data.anilistId) { mutableStateOf<Int?>(null) }
    val blocks = remember(data.episodes) { episodeBlocks(data.episodes) }
    val indexByPipeId = remember(data.episodes) {
        data.episodes.withIndex().associate { (index, episode) -> episode.pipeId to index }
    }
    val blockIndex = (chosenBlockIndex ?: blockIndexContaining(blocks, data.current.number))
        .coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    val shownEpisodes = if (episodeQuery.isNotBlank()) {
        filterEpisodes(data.episodes, episodeQuery)
    } else {
        blocks.getOrNull(blockIndex)?.episodes.orEmpty()
    }
    LazyColumn(modifier = modifier) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                WatchEpisodeSummary(
                    data = data,
                    saved = saved,
                    onToggleSaved = onToggleSaved,
                    episodeDownload = episodeDownload,
                    downloadPreparing = downloadPreparing,
                    canDownload = canDownload,
                    canSaveToDevice = canSaveToDevice,
                    onDownload = onDownload,
                    modifier = Modifier.padding(start = pad, end = pad, top = 16.dp, bottom = 4.dp),
                )
            }
            SourceSelectors(
                data = data,
                onChangeSource = onChangeSource,
                onChangeCategory = onChangeCategory,
                focusRequester = focusRequester,
            )
            data.notice?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = pad, vertical = 5.dp),
                )
            }
            BulkDownloadStatus(modifier = Modifier.padding(horizontal = pad))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "  ${data.episodes.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (data.episodes.size > EPISODE_BROWSER_MIN_EPISODES) {
            item {
                EpisodeBrowserBar(
                    blocks = blocks,
                    selectedBlockIndex = blockIndex,
                    onSelectBlock = { chosenBlockIndex = it },
                    query = episodeQuery,
                    onQueryChange = { episodeQuery = it },
                    layout = episodeLayout,
                    onToggleLayout = { SettingsStore.setEpisodeLayout(episodeLayout.toggled()) },
                    modifier = Modifier.padding(horizontal = pad).padding(bottom = 6.dp),
                )
            }
        }
        when {
            shownEpisodes.isNotEmpty() && episodeLayout == EpisodeLayout.GRID -> items(
                shownEpisodes.chunked(device.episodeColumns),
                key = { row -> row.first().pipeId },
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { episode ->
                        val index = indexByPipeId[episode.pipeId] ?: -1
                        EpisodeNumberChip(
                            episode = episode,
                            selected = index == data.currentIndex,
                            watchedFraction = episodeWatchFraction(resume, episode.number),
                            modifier = Modifier.weight(1f),
                            onClick = { if (index >= 0) onSelectEpisode(index) },
                        )
                    }
                    repeat(device.episodeColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            shownEpisodes.isNotEmpty() -> items(shownEpisodes, key = EpisodeItem::pipeId) { episode ->
                val index = indexByPipeId[episode.pipeId] ?: -1
                MobileEpisodeRow(
                    episode = episode,
                    fallbackImage = data.artworkUrl,
                    selected = index == data.currentIndex,
                    watchedFraction = episodeWatchFraction(resume, episode.number),
                    downloadState = downloadBadges[
                        EpisodeDownloads.idFor(data.anilistId, data.category.api, episode.displayNumber)
                    ],
                    onClick = { if (index >= 0) onSelectEpisode(index) },
                )
            }
            episodeQuery.isNotBlank() -> item {
                Text(
                    text = "No episode matches “$episodeQuery”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = pad, vertical = 12.dp),
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun MobileEpisodeRow(
    episode: EpisodeItem,
    fallbackImage: String?,
    selected: Boolean,
    onClick: () -> Unit,
    watchedFraction: Float = 0f,
    downloadState: EpisodeDownloadUi? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalAppDeviceProfile.current.pagePadding, vertical = 5.dp)
            .focusHighlight(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp)) {
            AsyncImage(
                model = episode.image.takeIf { !it.isNullOrBlank() && it != "null" } ?: fallbackImage,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Text(
                text = "EP ${episode.displayNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(if (watchedFraction > 0.01f) Alignment.TopEnd else Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
            com.shubh.anililitv.ui.components.WatchProgressBar(
                fraction = watchedFraction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            )
            DownloadCoverBadge(
                state = downloadState,
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(9.dp)),
                compact = true,
            )
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            val title = episode.distinctTitle
            Text(
                text = title ?: "Episode ${episode.displayNumber}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                selected -> "Now playing"
                title != null -> "Episode ${episode.displayNumber}"
                else -> null
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSelectors(
    data: WatchData,
    onChangeSource: (String, String) -> Unit,
    onChangeCategory: (String) -> Unit,
    focusRequester: FocusRequester,
    onToggleFullscreen: (() -> Unit)? = null,
    downFocus: FocusRequester? = null,
) {
    val device = LocalAppDeviceProfile.current
    var audioFilter by rememberSaveable { mutableStateOf(AudioFilter.ANY) }
    var languageFilter by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(data.knownLanguages) {
        if (languageFilter != null && languageFilter !in data.knownLanguages) languageFilter = null
    }
    val filteredOptions = remember(data.sourceOptions, data.capabilities, audioFilter, languageFilter) {
        filterSourceOptions(data.sourceOptions, data.capabilities, audioFilter, languageFilter)
    }
    val servers = remember(filteredOptions) { filteredOptions.map { it.provider }.distinct() }
    val categoriesByServer = remember(data.sourceOptions) {
        data.sourceOptions
            .groupBy { it.provider }
            .mapValues { (_, options) -> options.map { it.category }.distinct() }
    }
    val audioForServer = remember(data.sourceOptions, data.provider) {
        data.sourceOptions.filter { it.provider == data.provider }.map { it.category }.distinct()
    }
    var showServerDialog by remember { mutableStateOf(false) }
    val mobileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tvDialogFocus = remember { FocusRequester() }

    LaunchedEffect(showServerDialog, device.isTv) {
        if (showServerDialog && device.isTv) {
            delay(120)
            runCatching { tvDialogFocus.requestFocus() }
                .onFailure { DiagnosticsLog.throwable("TV server dialog focus failed", it) }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = device.pagePadding, vertical = 4.dp)
            .then(downFocus?.let { target -> Modifier.focusProperties { down = target } } ?: Modifier)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactClickablePill(
            label = ProviderCatalog.label(data.provider) +
                if (data.provider == data.preferredProvider) " ★" else "",
            downloadable = ProviderCatalog.supportsOfflineDownload(data.provider),
            enabled = servers.isNotEmpty(),
            focusRequester = focusRequester,
            onClick = { showServerDialog = true }
        )
        val alternateAudio = audioForServer.firstOrNull { it != data.category }
        CompactClickablePill(
            label = if (data.category == com.shubh.anililitv.data.model.Category.DUB) "Dub" else "Sub",
            enabled = alternateAudio != null,
            active = data.category == com.shubh.anililitv.data.model.Category.DUB,
            showArrow = false,
            onClick = { alternateAudio?.let { onChangeCategory(it.api) } },
        )
        if (device.isTv && onToggleFullscreen != null) {
            CompactClickablePill(
                label = "Fullscreen",
                enabled = true,
                showArrow = false,
                onClick = onToggleFullscreen,
            )
        }
        if (data.isLoadingMoreSources) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "More servers…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showServerDialog && !device.isTv) {
        ModalBottomSheet(
            onDismissRequest = { showServerDialog = false },
            sheetState = mobileSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            MobileServerPickerContent(
                data = data,
                servers = servers,
                audio = audioFilter,
                onAudioChange = { audioFilter = it },
                language = languageFilter,
                onLanguageChange = { languageFilter = it },
                onSelect = { server ->
                    showServerDialog = false
                    if (server != data.provider || server != data.preferredProvider) {
                        val options = data.sourceOptions.filter { it.provider == server }
                        val nextCategory = options.firstOrNull { it.category == data.category }?.category
                            ?: options.first().category
                        onChangeSource(server, nextCategory.api)
                    }
                },
                onClose = { showServerDialog = false },
            )
        }
    }

    if (showServerDialog && device.isTv) {
        Dialog(
            onDismissRequest = {
                showServerDialog = false
                if (device.isTv) runCatching { focusRequester.requestFocus() }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = device.isTv,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (device.isTv) 0.dp else 84.dp),
                contentAlignment = if (device.isTv) Alignment.Center else Alignment.BottomCenter,
            ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(0.dp))
                    .background(Color.Black)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(0.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Server for this episode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (data.isLoadingMoreSources) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = if (data.isLoadingMoreSources) {
                                    "Finding servers… ${servers.size} so far"
                                } else if (data.preferredProvider == "auto") {
                                    "Applies to this episode only · ${servers.size} available"
                                } else {
                                    "This episode only · Settings pins ${ProviderCatalog.label(data.preferredProvider)} · ${servers.size} available"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = FastServerColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Fast servers — these usually start quickest",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SourceFilterRow(
                        audio = audioFilter,
                        onAudioChange = { audioFilter = it },
                        languages = data.knownLanguages,
                        language = languageFilter,
                        onLanguageChange = { languageFilter = it },
                        stillChecking = !languageFilterIsComplete(data.sourceOptions, data.capabilities),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val columns = if (device.isTv) 4 else 3
                        val rows = servers.chunked(columns)
                        rows.forEachIndexed { rowIndex, rowCells ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCells.forEachIndexed { columnIndex, server ->
                                    val selected = server == data.provider
                                    val preferred = server == data.preferredProvider
                                    val downloadable = ProviderCatalog.supportsOfflineDownload(server)
                                    val bg = when {
                                        selected -> Color.White
                                        else -> Color(0xFF222222)
                                    }
                                    val textColor = if (selected) Color.Black else Color.White
                                    val selectServer = {
                                        showServerDialog = false
                                        if (device.isTv) runCatching { focusRequester.requestFocus() }
                                        if (!selected || !preferred) {
                                            val options = data.sourceOptions.filter { it.provider == server }
                                            val category = options.firstOrNull { it.category == data.category }?.category
                                                ?: options.first().category
                                            onChangeSource(server, category.api)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(
                                                if (rowIndex == 0 && columnIndex == 0) {
                                                    Modifier.focusRequester(tvDialogFocus)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .heightIn(min = 58.dp)
                                            .onPreviewKeyEvent { event ->
                                                val keyCode = event.nativeKeyEvent.keyCode
                                                val activate = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                                                    keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                                                    keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                                                if (!activate) {
                                                    false
                                                } else {
                                                    if (event.type == KeyEventType.KeyUp) selectServer()
                                                    true
                                                }
                                            }
                                            .focusHighlight(RoundedCornerShape(0.dp))
                                            .clip(RoundedCornerShape(0.dp))
                                            .background(bg)
                                            .border(
                                                1.dp,
                                                if (selected || preferred) Color.White
                                                else MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(0.dp)
                                            )
                                            .clickable(onClick = selectServer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (ProviderCatalog.isFast(server)) {
                                                    Icon(
                                                        Icons.Default.Bolt,
                                                        contentDescription = "Fast server",
                                                        tint = if (selected) textColor else FastServerColor,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                                if (downloadable) {
                                                    Icon(
                                                        Icons.Default.Download,
                                                        contentDescription = "Can be saved offline",
                                                        tint = if (selected) textColor else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                                Text(
                                                    text = ProviderCatalog.label(server) + if (preferred) " ★" else "",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected || preferred) FontWeight.Bold else FontWeight.Normal,
                                                    color = textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                categoriesByServer[server].orEmpty().forEach { category ->
                                                    SourceCategoryBadge(category = category, selected = selected)
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat(columns - rowCells.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            showServerDialog = false
                            if (device.isTv) runCatching { focusRequester.requestFocus() }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MobileServerPickerContent(
    data: WatchData,
    servers: List<String>,
    audio: AudioFilter,
    onAudioChange: (AudioFilter) -> Unit,
    language: String?,
    onLanguageChange: (String?) -> Unit,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val categoriesByServer = remember(data.sourceOptions) {
        data.sourceOptions
            .groupBy { it.provider }
            .mapValues { (_, options) -> options.map { it.category }.distinct() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Server for this episode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (data.isLoadingMoreSources) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = if (data.isLoadingMoreSources) {
                        "Finding servers… ${servers.size} so far"
                    } else if (data.preferredProvider == "auto") {
                        "Applies to this episode only · ${servers.size} available"
                    } else {
                        "This episode only · Settings pins ${ProviderCatalog.label(data.preferredProvider)} · ${servers.size} available"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = FastServerColor,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Fast servers usually start quickest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SourceFilterRow(
            audio = audio,
            onAudioChange = onAudioChange,
            languages = data.knownLanguages,
            language = language,
            onLanguageChange = onLanguageChange,
            stillChecking = !languageFilterIsComplete(data.sourceOptions, data.capabilities),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val columns = 2
            servers.chunked(columns).forEach { rowServers ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowServers.forEach { server ->
                        val selected = server == data.provider
                        val preferred = server == data.preferredProvider
                        val downloadable = ProviderCatalog.supportsOfflineDownload(server)
                        val textColor = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected || preferred) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(11.dp),
                                )
                                .clickable { onSelect(server) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (ProviderCatalog.isFast(server)) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = "Fast server",
                                        tint = if (selected) textColor else FastServerColor,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                Text(
                                    text = ProviderCatalog.label(server) + if (preferred) " ★" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (selected || preferred) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (downloadable) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Can be saved offline",
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                categoriesByServer[server].orEmpty().forEach { category ->
                                    SourceCategoryBadge(category = category, selected = selected)
                                }
                            }
                        }
                    }
                    repeat(columns - rowServers.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }


        }

        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
            Text("Close")
        }
    }
}

@Composable
private fun SourceCategoryBadge(category: Category, selected: Boolean) {
    val backgroundColor = if (selected) Color.White else Color.White.copy(alpha = 0.16f)
    val textColor = if (selected) Color.Black else Color.White
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactClickablePill(
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    active: Boolean = false,
    showArrow: Boolean = true,
    downloadable: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val activate = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                if (!enabled || !activate) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                }
            }
            .focusHighlight(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) Color.White else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (active) Color.White else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (downloadable) {
            Icon(
                Icons.Default.Download,
                contentDescription = "Can be saved offline",
                tint = if (active) {
                    Color.Black
                } else {
                    Color.White
                },
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(15.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface,
        )
        if (showArrow) {
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactDropdown(
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusHighlight(RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@Composable
private fun NoSource(onWebFallback: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No playable source on this server.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            TextButton(
                onClick = onWebFallback,
                modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
            ) { Text("Open in web player") }
        }
    }
}

@Composable
private fun EmbedEpisodeNavigationEffect(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val currentHasPrevious by rememberUpdatedState(hasPrevious)
    val currentHasNext by rememberUpdatedState(hasNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)

    DisposableEffect(Unit) {
        val navigator: (Int) -> Unit = { direction ->
            DiagnosticsLog.event("Embed player episode navigator direction=$direction")
            when {
                direction > 0 && currentHasNext -> currentOnNext()
                direction < 0 && currentHasPrevious -> currentOnPrevious()
            }
        }
        PlaybackService.episodeNavigator = navigator
        DiagnosticsLog.event("Embed player episode navigator registered hasPrev=$hasPrevious hasNext=$hasNext")
        onDispose {
            if (PlaybackService.episodeNavigator === navigator) {
                PlaybackService.episodeNavigator = null
            }
            DiagnosticsLog.event("Embed player episode navigator cleared")
        }
    }
}

private val FastServerColor = Color(0xFFFFB300)

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val tvFocusPolicy = if (LocalAppDeviceProfile.current.isTv) {
        Modifier.focusProperties { canFocus = false }
    } else {
        Modifier
    }
    IconButton(
        onClick = onBack,
        modifier = modifier
            .then(tvFocusPolicy)
            .statusBarsPadding()
            .padding(4.dp)
            .focusHighlight(RoundedCornerShape(24.dp)),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
        )
    }
}


@Composable
private fun BulkDownloadStatus(modifier: Modifier = Modifier) {
    val progress by BulkEpisodeDownloads.progress.collectAsState()
    val state = progress ?: return

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = when (state.outcome) {
                BulkDownloadOutcome.RUNNING -> buildString {
                    append("Downloading ${state.done + 1} of ${state.total}")
                    state.current?.let { append(" · episode $it") }
                }
                BulkDownloadOutcome.FINISHED ->
                    "Queued ${state.queued} of ${state.total}" +
                        if (state.failed > 0) " · ${state.failed} had no downloadable source" else ""
                BulkDownloadOutcome.CANCELLED -> "Stopped after ${state.queued} of ${state.total}"
                BulkDownloadOutcome.OUT_OF_SPACE ->
                    "Out of space after ${state.queued} of ${state.total}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.outcome == BulkDownloadOutcome.OUT_OF_SPACE) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { if (state.isRunning) BulkEpisodeDownloads.cancel() else BulkEpisodeDownloads.dismiss() },
            modifier = Modifier.focusHighlight(RoundedCornerShape(20.dp)),
        ) {
            Text(if (state.isRunning) "Stop" else "Dismiss")
        }
    }
}

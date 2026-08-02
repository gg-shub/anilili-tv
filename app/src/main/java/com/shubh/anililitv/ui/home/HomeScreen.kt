package com.shubh.anililitv.ui.home

import androidx.compose.ui.focus.focusRestorer

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onKeyEvent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shubh.anililitv.R
import com.shubh.anililitv.data.library.HistoryEntry
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.components.ErrorBox
import com.shubh.anililitv.ui.components.LoadingBox
import com.shubh.anililitv.ui.components.AnimeCard
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.focusHighlight
import com.shubh.anililitv.ui.components.PullRefreshContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAnimeClick: (Int) -> Unit,
    onWatchNow: (Int) -> Unit,
    onResume: (HistoryEntry) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val historyRaw by LibraryStore.history.collectAsState()
    val history = remember(historyRaw) { historyRaw.filter { !it.fromRemote && !it.hiddenFromHome }.sortedByDescending { it.updatedAt } }
    val device = LocalAppDeviceProfile.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var slowStartup by remember { mutableStateOf(false) }
    var diagnosticsMessage by remember { mutableStateOf<String?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isTopBarVisible by remember {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }
    androidx.activity.compose.BackHandler {
        showExitDialog = true
    }

    LaunchedEffect(Unit) {
        com.shubh.anililitv.data.update.UpdateManager.check(context, false)
    }

    LaunchedEffect(state) {
        slowStartup = false
        diagnosticsMessage = null
        if (state is UiState.Loading) {
            delay(10_000)
            slowStartup = true
            DiagnosticsLog.event("Home still loading after 10 seconds")
        }
    }

    if (state is UiState.Loading) {
        if (slowStartup) {
            StartupStillLoading(
                message = diagnosticsMessage,
                onRetry = { vm.load(force = true) },
                onShareDiagnostics = {
                    DiagnosticsLog.share(context)
                        .onFailure { diagnosticsMessage = it.message ?: "Couldn't share diagnostics" }
                },
                modifier = modifier,
            )
        } else {
            Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    com.shubh.anililitv.ui.components.WaterFillLogoIndicator(size = 88.dp)
                }
                Text(
                    text = "Checking for updates...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                )
            }
        }
    } else {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(32.dp)
                            .focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onSearchClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search anime", modifier = Modifier.size(18.dp))
                    }
                },
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val homeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                            LaunchedEffect(Unit) {
                                runCatching { homeFocus.requestFocus() }
                            }
                            Row(
                                modifier = Modifier
                                    .focusRequester(homeFocus)
                                    .focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable {  }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Text("HOME", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(
                                modifier = Modifier
                                    .focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable(onClick = onLibraryClick)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Text("LIBRARY", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(
                                modifier = Modifier
                                    .focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable(onClick = onScheduleClick)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Text("SCHEDULE", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                actions = {
                    val unread by com.shubh.anililitv.data.reminder.NotificationCenter.unread.collectAsState()
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(32.dp)
                            .focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onNotificationsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.BadgedBox(
                            badge = {
                                if (unread > 0) {
                                    androidx.compose.material3.Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text(if (unread > 99) "99+" else unread.toString(), color = androidx.compose.ui.graphics.Color.Black)
                                    }
                                }
                            }
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Notifications, contentDescription = "Notifications", modifier = Modifier.size(18.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(32.dp)
                            .focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onSettingsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF121212).copy(alpha = 0.9f)),
                modifier = Modifier.heightIn(max = 48.dp),
            )
        },
    ) { padding ->
        when (val s = state) {
            is UiState.Error -> ErrorBox(s.message, vm::load, Modifier.padding(padding))
            is UiState.Success -> PullRefreshContainer(
                isRefreshing = isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                HomeContent(
                    data = s.data,
                    history = history,
                    onAnimeClick = onAnimeClick,
                    onWatchNow = onWatchNow,
                    onResume = onResume,
                    listState = listState,
                )
            }
            else -> {}
        }
        
        if (showExitDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit App", fontWeight = FontWeight.Bold) },
                text = { Text("Do you want to exit the app?") },
                containerColor = Color(0xFF101010),
                shape = androidx.compose.ui.graphics.RectangleShape,
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { activity?.finish() },
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        modifier = Modifier.focusHighlight(androidx.compose.ui.graphics.RectangleShape)
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showExitDialog = false },
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        modifier = Modifier.focusHighlight(androidx.compose.ui.graphics.RectangleShape)
                    ) {
                        Text("No")
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun StartupStillLoading(
    message: String?,
    onRetry: () -> Unit,
    onShareDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            Text(
                "Still loading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "If the screen stays blank, share diagnostics from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onRetry, modifier = Modifier.focusHighlight(RectangleShape)) {
                    Text("Retry")
                }
                Button(onClick = onShareDiagnostics, modifier = Modifier.focusHighlight(RectangleShape)) {
                    Text("Share diagnostics")
                }
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    history: List<HistoryEntry>,
    onAnimeClick: (Int) -> Unit,
    onWatchNow: (Int) -> Unit,
    onResume: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val device = LocalAppDeviceProfile.current
    val continueFocusRequester = remember { FocusRequester() }
    LaunchedEffect(data, history.size) {
        DiagnosticsLog.event(
            "HomeContent rendered spotlight=${data.spotlight.size} history=${history.size}"
        )
    }
    val firstRailFocusRequester = remember { FocusRequester() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero", contentType = "hero_banner") {
            HeroPager(
                items = data.spotlight.take(6),
                onAnimeClick = onAnimeClick,
                onWatchNow = onWatchNow,
                onMoveDown = {
                    if (history.isNotEmpty()) {
                        runCatching { continueFocusRequester.requestFocus() }.isSuccess
                    } else {
                        runCatching { firstRailFocusRequester.requestFocus() }.isSuccess
                    }
                }
            )
        }
        if (history.isNotEmpty()) {
            item(key = "continue", contentType = "horizontal_rail") { ContinueRail(history.take(12), onResume, onAnimeClick, continueFocusRequester) }
        }
        item(key = "newest", contentType = "horizontal_rail") { MediaRail("Latest Releases", data.tab(HomeTab.NEWEST), onAnimeClick, firstRailFocusRequester) }
        item(key = "trending", contentType = "horizontal_rail") { MediaRail("Trending Now", data.spotlight, onAnimeClick) }
        item(key = "popular", contentType = "horizontal_rail") { MediaRail("Popular This Season", data.tab(HomeTab.POPULAR), onAnimeClick) }
        item(key = "movies", contentType = "horizontal_rail") { MediaRail("Movies", data.tab(HomeTab.MOVIES), onAnimeClick) }
        item(key = "top_rated", contentType = "horizontal_rail") { MediaRail("Top Rated", data.tab(HomeTab.TOP_RATED), onAnimeClick) }
    }
}



@Composable
private fun HeroPager(
    items: List<Media>,
    onAnimeClick: (Int) -> Unit,
    onWatchNow: (Int) -> Unit,
    onMoveDown: (() -> Boolean)?,
) {
    if (items.isEmpty()) return
    val device = LocalAppDeviceProfile.current
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val isCinematic by com.shubh.anililitv.data.settings.SettingsStore.cinematicSlideshow.collectAsState()
    val heroHeight = when {
        device.isTv -> if (isCinematic) 400.dp else 360.dp
        device.isExpanded -> if (isCinematic) 400.dp else 360.dp
        device.isTablet -> if (isCinematic) 450.dp else 400.dp
        else -> if (isCinematic) 350.dp else 300.dp
    }
    val cardFocusRequesters = remember(items.size) { Array(items.size) { FocusRequester() } }
    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        LaunchedEffect(pagerState.currentPage) {
            delay(8000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % items.size
                scope.launch {
                    runCatching { pagerState.animateScrollToPage(next) }
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !device.isTv,
        ) { page ->
            HeroCard(
                media = items[page],
                focusRequester = cardFocusRequesters[page],
                onAnimeClick = onAnimeClick,
                canGoPrevious = page > 0,
                canGoNext = page < items.lastIndex,
                onPrevious = {
                    scope.launch {
                        pagerState.animateScrollToPage(page - 1)
                        runCatching { cardFocusRequesters[page - 1].requestFocus() }
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(page + 1)
                        runCatching { cardFocusRequesters[page + 1].requestFocus() }
                    }
                },
                onMoveDown = onMoveDown,
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(items.size) { i ->
                Box(
                    Modifier
                        .height(5.dp)
                        .width(if (i == pagerState.currentPage) 18.dp else 5.dp)
                        .clip(RectangleShape)
                        .background(if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else Color.White.copy(.4f)),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    media: Media,
    focusRequester: FocusRequester,
    onAnimeClick: (Int) -> Unit,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMoveDown: (() -> Boolean)?,
    modifier: Modifier = Modifier,
) {
    val device = LocalAppDeviceProfile.current
    Box(
        Modifier.fillMaxSize()
            .focusRequester(focusRequester)
            .focusHighlight(RectangleShape)
            .background(Color(0xFF0F172A))
            .clickable { onAnimeClick(media.id) }
            .onPreviewKeyEvent { event ->
                if (device.isTv && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (canGoPrevious) {
                                onPrevious()
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (canGoNext) {
                                onNext()
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            onMoveDown?.invoke() ?: false
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        val isCinematic by com.shubh.anililitv.data.settings.SettingsStore.cinematicSlideshow.collectAsState()
        val repo = com.shubh.anililitv.data.AppGraph.repository
        var logoUrl by remember { mutableStateOf<String?>(null) }
        var backdropUrl by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(media.id, isCinematic) {
            if (isCinematic) {
                val meta = repo.getMetadata(media.id)
                logoUrl = meta?.logoUrl
                backdropUrl = meta?.backdropUrl
            }
        }
        
        val heroImage = backdropUrl ?: media.bannerImage ?: media.coverImage.best
        val imageUrl = if (isCinematic) heroImage else media.coverImage.best
        val isLowResFallback = imageUrl?.contains("anilist.co") == true
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .apply {
                    if (isLowResFallback) {
                        transformations(com.shubh.anililitv.ui.components.BlurTransformation(LocalContext.current, radius = 25f, sampling = 4f))
                    }
                }
                .crossfade(true)
                .size(if (isCinematic) coil.size.Size.ORIGINAL else coil.size.Size(200, 200))
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().let { 
                if (isCinematic) it else it.alpha(0.6f).graphicsLayer {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                    }
                }
            },
            contentScale = ContentScale.Crop,
        )
        if (isCinematic) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    startY = 0f
                )
            ))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
        }
        Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = device.pagePadding, vertical = 24.dp)
                    .align(if (isCinematic) Alignment.Bottom else Alignment.CenterVertically),
            ) {
                media.nextAiringEpisode?.episode?.let { Badge("NEW EPISODE $it SOON") }
                
                if (isCinematic && !logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = media.title.preferred,
                        modifier = Modifier.padding(top = 8.dp).height(100.dp).fillMaxWidth(0.8f),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                } else {
                    Text(
                        media.title.preferred,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                
                Text(
                    listOfNotNull(media.seasonYear?.toString(), media.format).joinToString("  •  "),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(.82f),
                    modifier = Modifier.padding(top = 5.dp, bottom = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch Now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            if (!isCinematic) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(media.coverImage.best)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    )
                }
            }
        }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MediaRail(
    title: String,
    media: List<Media>,
    onAnimeClick: (Int) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    val device = LocalAppDeviceProfile.current
    val posterSize by com.shubh.anililitv.data.settings.SettingsStore.posterSize.collectAsState()
    val posterWidth = posterSize.widthDp.dp
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = device.pagePadding),
        )
        LazyRow(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.focusGroup().focusRestorer(),
            contentPadding = PaddingValues(horizontal = device.pagePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (device.isTv) 18.dp else 10.dp),
        ) {
            itemsIndexed(media, key = { _, it -> it.id }, contentType = { _, _ -> "MediaCard" }) { index, item ->
                AnimeCard(
                    media = item,
                    onClick = { onAnimeClick(item.id) },
                    modifier = Modifier.width(posterWidth)
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ContinueRail(
    history: List<HistoryEntry>,
    onResume: (HistoryEntry) -> Unit,
    onAnimeClick: (Int) -> Unit,
    firstItemFocusRequester: FocusRequester,
) {
    val device = LocalAppDeviceProfile.current
    val cardWidth = when {
        device.isTv -> 240.dp
        device.isExpanded -> 220.dp
        device.isTablet -> 200.dp
        else -> 174.dp
    }
    Column {
        Text(
            "Continue Watching",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = device.pagePadding),
        )
        LazyRow(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.focusGroup().focusRestorer(),
            contentPadding = PaddingValues(horizontal = device.pagePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (device.isTv) 18.dp else 10.dp),
        ) {
            itemsIndexed(history, key = { _, item -> item.anilistId }, contentType = { _, _ -> "ContinueCard" }) { index, entry ->
                val isFinished = entry.progressFraction >= 0.85f
                val nextEp = entry.episodeNumber + 1.0
                val targetEntry = if (isFinished) entry.copy(episodeNumber = nextEp) else entry
                val episodeText = if (isFinished) "Next Episode ${targetEntry.episodeLabel}" else "Episode ${entry.episodeLabel}"

                var episodeImageUrl by remember(entry.anilistId, targetEntry.episodeNumber) { mutableStateOf<String?>(null) }
                LaunchedEffect(entry.anilistId, targetEntry.episodeNumber) {
                    val meta = com.shubh.anililitv.ui.detail.fetchAniListEpisodeMeta(entry.anilistId)
                    val epNumInt = targetEntry.episodeNumber.toInt()
                    episodeImageUrl = meta[epNumInt]?.thumbnail
                }

                var actionsVisible by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                var longPressHandled by remember { mutableStateOf(false) }
                
                var isMenuClickable by remember { mutableStateOf(false) }
                LaunchedEffect(actionsVisible) {
                    if (actionsVisible) {
                        isMenuClickable = false
                        delay(400)
                        isMenuClickable = true
                    } else {
                        isMenuClickable = false
                    }
                }

                Box {
                Column(
                    Modifier
                        .width(cardWidth)
                        .wrapContentHeight()
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .focusHighlight()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                    if (event.nativeKeyEvent.repeatCount == 0) {
                                        longPressJob?.cancel()
                                        longPressHandled = false
                                        longPressJob = scope.launch {
                                            delay(500)
                                            actionsVisible = true
                                            longPressHandled = true
                                        }
                                    }
                                } else if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
                                    longPressJob?.cancel()
                                    longPressJob = null
                                    if (longPressHandled) {
                                        longPressHandled = false
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }
                            false
                        }
                        .clickable { onResume(targetEntry) },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RectangleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        AsyncImage(model = episodeImageUrl ?: entry.cover, contentDescription = "Resume ${entry.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(.3f)))
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(.25f))) {
                            Box(Modifier.fillMaxWidth(entry.progressFraction.coerceAtLeast(.03f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.wrapContentHeight().padding(top = 6.dp))
                    Text(episodeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.wrapContentHeight())
                }
                    androidx.compose.material3.DropdownMenu(
                        expanded = actionsVisible,
                        onDismissRequest = { actionsVisible = false },
                        modifier = Modifier
                            .background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                            .border(1.dp, Color.White.copy(alpha=0.1f), androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                    ) {
                        var miFocused by remember { mutableStateOf(false) }
                        var lastClickTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("More Info", color = if (miFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold) },
                            onClick = { 
                                if (!isMenuClickable) return@DropdownMenuItem
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastClickTime > 500) {
                                    lastClickTime = now
                                    actionsVisible = false
                                    onAnimeClick(entry.anilistId) 
                                }
                            },
                            modifier = Modifier
                                .onFocusChanged { miFocused = it.isFocused }
                                .background(if (miFocused) Color.White else Color.Transparent)
                        )
                        var rmFocused by remember { mutableStateOf(false) }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Remove", color = if (rmFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold) },
                            onClick = { 
                                if (!isMenuClickable) return@DropdownMenuItem
                                actionsVisible = false
                                com.shubh.anililitv.data.library.LibraryStore.hideFromHome(entry.anilistId)
                            },
                            modifier = Modifier
                                .onFocusChanged { rmFocused = it.isFocused }
                                .background(if (rmFocused) Color.White else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(Modifier.clip(RectangleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}


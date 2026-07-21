package com.miruronative.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.material.icons.Icons
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
import com.miruronative.R
import com.miruronative.data.library.HistoryEntry
import com.miruronative.data.library.LibraryStore
import com.miruronative.data.model.Media
import com.miruronative.diagnostics.DiagnosticsLog
import com.miruronative.ui.UiState
import com.miruronative.ui.components.ErrorBox
import com.miruronative.ui.components.LoadingBox
import com.miruronative.ui.components.AnimeCard
import com.miruronative.ui.adaptive.LocalAppDeviceProfile
import com.miruronative.ui.adaptive.focusHighlight
import com.miruronative.ui.components.PullRefreshContainer
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
    val history by LibraryStore.history.collectAsState()
    val device = LocalAppDeviceProfile.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var slowStartup by remember { mutableStateOf(false) }
    var diagnosticsMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        slowStartup = false
        diagnosticsMessage = null
        if (state is UiState.Loading) {
            delay(10_000)
            slowStartup = true
            DiagnosticsLog.event("Home still loading after 10 seconds")
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search anime")
                    }
                },
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val homeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(Unit) {
                            runCatching { homeFocus.requestFocus() }
                        }
                        TextButton(onClick = { /* already here */ }, modifier = Modifier.focusRequester(homeFocus).focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)) {
                            Text("HOME", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onLibraryClick, modifier = Modifier.focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)) {
                            Text("LIBRARY", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onScheduleClick, modifier = Modifier.focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true)) {
                            Text("SCHEDULE", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.focusHighlight(androidx.compose.foundation.shape.CircleShape, showBorder = true),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (val s = state) {
            is UiState.Loading -> {
                if (slowStartup) {
                    StartupStillLoading(
                        message = diagnosticsMessage,
                        onRetry = { vm.load(force = true) },
                        onShareDiagnostics = {
                            DiagnosticsLog.share(context)
                                .onFailure { diagnosticsMessage = it.message ?: "Couldn't share diagnostics" }
                        },
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    LoadingBox(Modifier.padding(padding))
                }
            }
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
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeroPager(
                items = data.spotlight.take(6),
                onAnimeClick = onAnimeClick,
                onWatchNow = onWatchNow,
                onMoveDown = {
                    if (history.isNotEmpty()) {
                        runCatching { continueFocusRequester.requestFocus() }
                    } else {
                        runCatching { firstRailFocusRequester.requestFocus() }
                    }
                }
            )
        }
        if (history.isNotEmpty()) {
            item { ContinueRail(history.take(12), onResume, continueFocusRequester) }
        }
        item { MediaRail("Trending Now", data.spotlight, onAnimeClick, firstRailFocusRequester) }
        item { MediaRail("Popular", data.tab(HomeTab.POPULAR), onAnimeClick) }
        item { MediaRail("Top Rated", data.tab(HomeTab.TOP_RATED), onAnimeClick) }
        item { MediaRail("Latest", data.tab(HomeTab.NEWEST), onAnimeClick) }
        item { MediaRail("Movies", data.tab(HomeTab.MOVIES), onAnimeClick) }
    }
}



@Composable
private fun HeroPager(
    items: List<Media>,
    onAnimeClick: (Int) -> Unit,
    onWatchNow: (Int) -> Unit,
    onMoveDown: (() -> Unit)?,
) {
    if (items.isEmpty()) return
    val device = LocalAppDeviceProfile.current
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val heroHeight = when {
        device.isTv -> 300.dp
        device.isExpanded -> 360.dp
        device.isTablet -> 320.dp
        else -> 270.dp
    }
    val cardFocusRequesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var isFocused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (device.isTv) device.pagePadding else 0.dp)
            .height(heroHeight)
            .clip(if (device.isTv) RectangleShape else RectangleShape)
            .onFocusChanged { isFocused = it.hasFocus },
    ) {
        LaunchedEffect(isFocused) {
            while (!isFocused) {
                delay(5000)
                if (items.isNotEmpty() && !pagerState.isScrollInProgress) {
                    val next = if (pagerState.currentPage < items.lastIndex) pagerState.currentPage + 1 else 0
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
    onMoveDown: (() -> Unit)?,
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
                            onMoveDown?.invoke()
                            onMoveDown != null
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = device.pagePadding, vertical = 24.dp)
                    .align(Alignment.CenterVertically),
            ) {
                media.nextAiringEpisode?.episode?.let { Badge("NEW EPISODE $it SOON") }
                Text(
                    media.title.preferred,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch Now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
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

@Composable
private fun MediaRail(
    title: String,
    media: List<Media>,
    onAnimeClick: (Int) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    val device = LocalAppDeviceProfile.current
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = device.pagePadding),
        )
        LazyRow(
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(horizontal = device.pagePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (device.isTv) 18.dp else 10.dp),
        ) {
            itemsIndexed(media, key = { _, it -> it.id }) { index, item ->
                AnimeCard(
                    media = item,
                    onClick = { onAnimeClick(item.id) },
                    modifier = Modifier.animateItem().width(device.posterWidth)
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun ContinueRail(
    history: List<HistoryEntry>,
    onResume: (HistoryEntry) -> Unit,
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
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(horizontal = device.pagePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (device.isTv) 18.dp else 10.dp),
        ) {
            itemsIndexed(history, key = { _, item -> item.anilistId }) { index, entry ->
                Column(
                    Modifier
                        .animateItem()
                        .width(cardWidth)
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .focusHighlight()
                        .clickable { onResume(entry) },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RectangleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        AsyncImage(model = entry.cover, contentDescription = "Resume ${entry.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(.3f)))
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(.25f))) {
                            Box(Modifier.fillMaxWidth(entry.progressFraction.coerceAtLeast(.03f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    val isFinished = entry.progressFraction > 0.85f
                    val displayEp = if (isFinished) (entry.episodeNumber + 1.0) else entry.episodeNumber
                    val displayStr = if (displayEp % 1.0 == 0.0) displayEp.toInt().toString() else displayEp.toString()
                    val label = if (isFinished) "Play Episode $displayStr" else "Resume Episode $displayStr"
                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


package com.miruronative.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import com.miruronative.data.ProviderCatalog
import com.miruronative.data.library.HistoryEntry
import com.miruronative.data.library.LibraryStore
import com.miruronative.data.library.WatchlistEntry
import com.miruronative.data.model.Category
import com.miruronative.data.model.EpisodeItem
import com.miruronative.data.model.Media
import com.miruronative.ui.UiState
import com.miruronative.ui.components.ErrorBox
import com.miruronative.ui.components.LoadingBox
import com.miruronative.ui.adaptive.LocalAppDeviceProfile
import com.miruronative.ui.adaptive.focusHighlight
import com.miruronative.ui.components.PullRefreshContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    animeId: Int,
    onBack: () -> Unit,
    onPlay: (animeId: Int, provider: String, category: String, episode: String) -> Unit,
    onAnimeClick: (Int) -> Unit,
    onStudioClick: (com.miruronative.data.model.StudioNode) -> Unit,
    onSeasonWatch: (Int) -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    LaunchedEffect(animeId) { vm.load(animeId) }
    val state by vm.state.collectAsState()
    val watchlist by LibraryStore.watchlist.collectAsState()
    val history by LibraryStore.history.collectAsState()

    Scaffold(containerColor = Color.Black) { padding ->
        when (val s = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(s.message, { vm.load(animeId, force = true) }, Modifier.padding(padding))
            is UiState.Success -> {
                DetailContent(
                    data = s.data,
                    watchlist = watchlist,
                    history = history,
                    onPlay = onPlay,
                    onBack = onBack,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DetailContent(
    data: DetailData,
    watchlist: List<WatchlistEntry>,
    history: List<HistoryEntry>,
    onPlay: (Int, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    val info = data.info
    val isSaved = watchlist.any { it.anilistId == info.id }
    val historyEntry = history.firstOrNull { it.anilistId == info.id }
    val sidebarFocusRequester = remember { FocusRequester() }
    
    val aniProgress = 0
    val localEp = historyEntry?.episodeNumber
    val localFinished = (historyEntry?.progressFraction ?: 0f) > 0.9f
    
    val playEpisodeNum = when {
        localEp != null && localEp >= aniProgress -> if (localFinished) localEp + 1.0 else localEp
        aniProgress > 0 -> aniProgress + 1.0
        else -> 1.0
    }
    val isResume = localEp != null && localEp == playEpisodeNum && !localFinished
    val playEpisodeStr = if (playEpisodeNum % 1.0 == 0.0) playEpisodeNum.toInt().toString() else playEpisodeNum.toString()
    val isMovie = info.format?.uppercase() == "MOVIE"
    val playLabel = if (isMovie) {
        if (isResume) "Resume" else "Play"
    } else {
        if (isResume) "Resume Episode $playEpisodeStr" else "Play Episode $playEpisodeStr"
    }

    val playCurrent: () -> Unit = {
        when {
            historyEntry != null -> onPlay(info.id, historyEntry.provider, historyEntry.category, historyEntry.episodeLabel)
            data.episodes.isNotEmpty() -> onPlay(data.selectedSeasonId, "auto", data.preferredCategory.api, data.episodes.first().displayNumber)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Fullscreen Background
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(info.bannerImage ?: info.coverImage?.extraLarge)
                .size(200, 200) // downscale heavily for super fast blur
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(24.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
        )
        // Background Gradient (Darker on the right where the episodes are, and at the bottom)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Color.Black.copy(alpha = 0.3f),
                    0.5f to Color.Black.copy(alpha = 0.5f),
                    1.0f to Color.Black.copy(alpha = 0.85f),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.7f),
                )
            )
        )

        // Content Row (Left: Info, Right: Episodes Sidebar)
        Row(Modifier.fillMaxSize()) {
            // Left Panel
            Column(
                Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 48.dp, top = 32.dp, bottom = 32.dp, end = 24.dp)
            ) {
                Box(Modifier.padding(bottom = 24.dp)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.focusProperties { right = sidebarFocusRequester }.focusHighlight(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }

                Spacer(Modifier.weight(1f))

                // Title
                Text(
                    text = info.title.preferred,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Metadata Row
                Row(
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.duration != null) {
                        Text("${info.duration} min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (info.seasonYear != null) {
                        Text("${info.seasonYear}–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (info.averageScore != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${info.averageScore / 10.0}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.padding(start = 4.dp).size(20.dp))
                        }
                    }
                }

                // Genres
                Text("GENRES", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                FlowRow(Modifier.padding(top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.genres.take(3).forEach { genre ->
                        PillBadge(genre)
                    }
                }

                // Summary
                Text("SUMMARY", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                val cleanDesc = remember(info.description) { info.description?.replace(Regex("<[^>]*>"), "")?.trim() ?: "No summary available." }
                val playFocus = remember { FocusRequester() }
                Text(
                    text = cleanDesc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 32.dp)
                        .focusProperties { down = playFocus; next = playFocus }
                        .focusHighlight(RectangleShape)
                        .clickable { expanded = !expanded }
                        .animateContentSize(androidx.compose.animation.core.tween(300))
                )

                // Action Buttons Row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Play / Resume pill button
                    val episodes = data.episodes
                    
                    var playFocused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .focusRequester(playFocus)
                            .focusHighlight(RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .onFocusChanged { playFocused = it.isFocused }
                            .background(if (playFocused) Color.White else Color.White.copy(alpha = 0.15f))
                            .border(if (playFocused) 1.dp else 0.dp, Color.White, RoundedCornerShape(4.dp))
                            .clickable {
                                playCurrent()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (playFocused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                        Text(playLabel, color = if (playFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    // Add to list circle button
                    CircleActionButton(
                        icon = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                        onClick = {
                            LibraryStore.toggleWatchlist(
                                WatchlistEntry(
                                    info.id,
                                    info.title.preferred,
                                    info.coverImage.best,
                                    info.format,
                                    info.averageScore,
                                )
                            )
                        }
                    )
                    if (historyEntry != null) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            CircleActionButton(
                                icon = Icons.Default.MoreVert,
                                onClick = { showMenu = true }
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from watch history", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        showMenu = false
                                        LibraryStore.removeHistory(info.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Right Sidebar (Episodes & Servers)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                EpisodesSidebar(
                    data = data,
                    historyEntry = historyEntry,
                    onPlay = onPlay,
                    modifier = Modifier.focusRequester(sidebarFocusRequester),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun EpisodesSidebar(
    data: DetailData,
    historyEntry: com.miruronative.data.library.HistoryEntry?,
    onPlay: (Int, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val episodes = data.episodes

    var selectedRangeIndex by remember { mutableStateOf(0) }
    val chunkSize = 50
    val episodeChunks = remember(episodes) { episodes.chunked(chunkSize) }
    // If the selected index is now out of bounds due to provider change, clamp it.
    if (selectedRangeIndex >= episodeChunks.size && episodeChunks.isNotEmpty()) {
        selectedRangeIndex = episodeChunks.size - 1
    }
    val currentEpisodes = episodeChunks.getOrNull(selectedRangeIndex).orEmpty()
    
    
    val listState = rememberLazyListState()
    var initialScrollDone by remember { mutableStateOf(false) }
    var targetItemIndex by remember { mutableStateOf(-1) }
    val episodeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(episodes.size) {
        if (!initialScrollDone && episodes.isNotEmpty()) {
            val aniProgress = 0
            val localEp = historyEntry?.episodeNumber
            val localFinished = (historyEntry?.progressFraction ?: 0f) > 0.9f
            val playEpisodeNum = when {
                localEp != null && localEp >= aniProgress -> if (localFinished) localEp + 1.0 else localEp
                aniProgress > 0 -> aniProgress + 1.0
                else -> 1.0
            }
            var foundChunkIndex = 0
            var foundItemIndex = -1
            
            episodeChunks.forEachIndexed { cIndex, chunk ->
                val indexInChunk = chunk.indexOfFirst { (it.displayNumber.toDoubleOrNull() ?: 0.0) >= playEpisodeNum }
                if (indexInChunk != -1 && foundItemIndex == -1) {
                    foundChunkIndex = cIndex
                    foundItemIndex = indexInChunk
                }
            }
            
            if (foundItemIndex != -1) {
                selectedRangeIndex = foundChunkIndex
                targetItemIndex = foundItemIndex
                listState.scrollToItem(foundItemIndex)
            } else if (episodes.isNotEmpty()) {
                targetItemIndex = 0
            }
            initialScrollDone = true
        }
    }

    LaunchedEffect(targetItemIndex) {
        if (targetItemIndex != -1) {
            try { episodeFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    Column(modifier.fillMaxSize().focusGroup()) {
        // MIDDLE ROW: Episode ranges
        if (episodeChunks.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(vertical = 12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodeChunks.size) { index ->
                    val chunk = episodeChunks[index]
                    val isSelected = index == selectedRangeIndex
                    val label = if (chunk.size == 1) chunk.first().displayNumber else "${chunk.first().displayNumber}-${chunk.last().displayNumber}"
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedRangeIndex = index }
                            .focusHighlight(RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        } else if (episodeChunks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
                if (false) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("No episodes found.", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        // BOTTOM AREA: Episodes Grid/List
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.65f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(currentEpisodes, key = { _, ep -> ep.pipeId }) { index, ep ->
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .then(if (index == targetItemIndex && initialScrollDone) Modifier.focusRequester(episodeFocusRequester) else Modifier)
                        .onFocusChanged { isFocused = it.isFocused }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (isFocused) Color.White else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onPlay(data.selectedSeasonId, "auto", data.preferredCategory.api, ep.displayNumber) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val imageUrl = ep.image ?: data.info.coverImage?.best
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Episode Thumbnail",
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${ep.displayNumber}. ${ep.title ?: "Episode ${ep.displayNumber}"}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (ep.filler) {
                        Box(
                            Modifier.padding(start = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("FILLER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillBadge(text: String) {
    Box(
        Modifier
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .focusHighlight(CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CircleActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .focusHighlight(CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}


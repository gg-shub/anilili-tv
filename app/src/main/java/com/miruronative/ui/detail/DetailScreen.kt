package com.miruronative.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import com.miruronative.data.ProviderCatalog
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
    onPlay: (provider: String, category: String, episode: String) -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    LaunchedEffect(animeId) { vm.load(animeId) }
    val state by vm.state.collectAsState()
    val watchlist by LibraryStore.watchlist.collectAsState()

    Scaffold(containerColor = Color.Black) { padding ->
        when (val s = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(s.message, { vm.load(animeId, force = true) }, Modifier.padding(padding))
            is UiState.Success -> {
                DetailContent(
                    data = s.data,
                    watchlist = watchlist,
                    selectedProvider = vm.selectedProvider,
                    selectedCategory = vm.selectedCategory,
                    onSelectProvider = vm::selectProvider,
                    onSelectCategory = vm::selectCategory,
                    onPlay = onPlay,
                    onBack = onBack,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    data: DetailData,
    watchlist: List<WatchlistEntry>,
    selectedProvider: String?,
    selectedCategory: Category,
    onSelectProvider: (String) -> Unit,
    onSelectCategory: (Category) -> Unit,
    onPlay: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    val info = data.info
    val isSaved = watchlist.any { it.anilistId == info.id }
    
    Box(Modifier.fillMaxSize()) {
        // Fullscreen Background
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(info.bannerImage ?: info.coverImage?.extraLarge)
                .size(Size.ORIGINAL)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(24.dp),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
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
                    .padding(start = 48.dp, top = 32.dp, bottom = 32.dp, end = 24.dp)
            ) {
                Box(Modifier.padding(bottom = 24.dp)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.focusHighlight(CircleShape)
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
                Text(
                    text = cleanDesc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 32.dp)
                        .focusHighlight(RectangleShape)
                        .clickable { expanded = !expanded }
                        .animateContentSize(tween(300))
                )

                // Action Buttons Row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Removed Trailer button
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
                    // Removed Eye button
                    CircleActionButton(icon = Icons.Default.Share, onClick = { /* Handle Share */ })
                }
            }

            // Right Sidebar (Episodes & Servers)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.65f)) // Glassmorphism backdrop fallback
            ) {
                EpisodesSidebar(
                    data = data,
                    selectedProvider = selectedProvider,
                    selectedCategory = selectedCategory,
                    onSelectProvider = onSelectProvider,
                    onSelectCategory = onSelectCategory,
                    onPlay = onPlay
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodesSidebar(
    data: DetailData,
    selectedProvider: String?,
    selectedCategory: Category,
    onSelectProvider: (String) -> Unit,
    onSelectCategory: (Category) -> Unit,
    onPlay: (String, String, String) -> Unit
) {
    val provider = selectedProvider?.let { data.episodes.provider(it) }
    val episodes = provider?.episodes(selectedCategory).orEmpty()
    val visibleProviders = remember(data.episodes.providerNames, data.loadingMore) {
        val pending = if (data.loadingMore) ProviderCatalog.anivexaProviders else emptyList()
        (data.episodes.providerNames + pending).distinct().sortedBy(ProviderCatalog::sortKey)
    }

    var selectedRangeIndex by remember { mutableStateOf(0) }
    val chunkSize = 50
    val episodeChunks = remember(episodes) { episodes.chunked(chunkSize) }
    // If the selected index is now out of bounds due to provider change, clamp it.
    if (selectedRangeIndex >= episodeChunks.size && episodeChunks.isNotEmpty()) {
        selectedRangeIndex = episodeChunks.size - 1
    }
    val currentEpisodes = episodeChunks.getOrNull(selectedRangeIndex).orEmpty()
    val categories = provider?.categories.orEmpty()

    Column(Modifier.fillMaxSize()) {
        // TOP ROW: Servers and Sub/Dub Toggle
        Row(
            Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleProviders) { name ->
                    val isSelected = name == selectedProvider
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.1f))
                            .clickable { onSelectProvider(name) }
                            .focusHighlight(RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            ProviderCatalog.label(name),
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (categories.isNotEmpty()) {
                // Sub / Dub Toggle
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(4.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable { onSelectCategory(cat) }
                                .focusHighlight(RoundedCornerShape(6.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                cat.api.uppercase(),
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // MIDDLE ROW: Episode ranges
        if (episodeChunks.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 16.dp),
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
            Box(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 16.dp)) {
                if (data.loadingMore) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(data.episodesError ?: "No episodes found.", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        // BOTTOM AREA: Episodes Grid/List
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 32.dp, end = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(currentEpisodes) { ep ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .focusHighlight(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { selectedProvider?.let { onPlay(it, selectedCategory.api, ep.displayNumber) } }
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


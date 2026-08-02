package com.shubh.anililitv.ui.detail

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
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
import androidx.activity.compose.BackHandler
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
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed as tvItemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.focus.onFocusChanged
import com.shubh.anililitv.data.ProviderCatalog
import com.shubh.anililitv.data.library.HistoryEntry
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.data.library.WatchlistEntry
import com.shubh.anililitv.data.model.Category
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.components.ErrorBox
import com.shubh.anililitv.ui.components.LoadingBox
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.focusHighlight
import com.shubh.anililitv.ui.components.PullRefreshContainer
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

data class AniListEpisodeMeta(val title: String?, val thumbnail: String?)
val aniListMetaCache = mutableMapOf<Int, Map<Int, AniListEpisodeMeta>>()

suspend fun fetchAniListEpisodeMeta(animeId: Int): Map<Int, AniListEpisodeMeta> = withContext(Dispatchers.IO) {
    if (aniListMetaCache.containsKey(animeId)) return@withContext aniListMetaCache[animeId]!!
    val map = mutableMapOf<Int, AniListEpisodeMeta>()
    try {
        val url = "https://api.ani.zip/mappings?anilist_id=$animeId"
        val response = URL(url).readText()
        val json = JSONObject(response)
        val episodesObj = json.optJSONObject("episodes")
        if (episodesObj != null) {
            episodesObj.keys().forEach { key ->
                val epNum = key.toIntOrNull()
                if (epNum != null) {
                    val epObj = episodesObj.optJSONObject(key)
                    if (epObj != null) {
                        val thumb = epObj.optString("image", "").takeIf { it.isNotBlank() && it != "null" }
                        val titleObj = epObj.optJSONObject("title")
                        val title = titleObj?.optString("en", "")?.takeIf { it.isNotBlank() && it != "null" }
                        map[epNum] = AniListEpisodeMeta(title, thumb)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    aniListMetaCache[animeId] = map
    map
}

enum class DetailTab { EPISODES, SEASONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    animeId: Int,
    onBack: () -> Unit,
    onPlay: (animeId: Int, provider: String, category: String, episode: String) -> Unit,
    onAnimeClick: (Int) -> Unit,
    onStudioClick: (com.shubh.anililitv.data.model.StudioNode) -> Unit,
    onSeasonWatch: (Int) -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    LaunchedEffect(animeId) { vm.load(animeId) }
    val state by vm.state.collectAsState()
    val watchlist by LibraryStore.watchlist.collectAsState()
    val history by LibraryStore.history.collectAsState()

    Scaffold(containerColor = Color.Transparent) { padding ->
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
                    onSeasonWatch = { vm.selectSeason(it) },
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
    onSeasonWatch: (Int) -> Unit,
) {
    val backArrowRequester = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }

    val info = data.info
    val isMovie = info.format?.uppercase() == "MOVIE"

    BackHandler(enabled = !backFocused) {
        if (isMovie) {
            onBack()
        } else {
            runCatching { backArrowRequester.requestFocus() }
        }
    }


    val isSaved = watchlist.any { it.anilistId == info.id }
    val historyEntry = history.firstOrNull { it.anilistId == info.id }
    
    var selectedTab by remember { mutableStateOf(DetailTab.EPISODES) }
    
    val useLegacyDetailLayout by com.shubh.anililitv.data.settings.SettingsStore.useLegacyDetailLayout.collectAsState()
    val episodeThumbnails = true
    val dynamicTitleLogos by com.shubh.anililitv.data.settings.SettingsStore.dynamicTitleLogos.collectAsState()
    val cinematicBackdrops by com.shubh.anililitv.data.settings.SettingsStore.cinematicBackdrops.collectAsState()

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



    val shouldRenderCompact = isMovie || useLegacyDetailLayout

    if (shouldRenderCompact) {
        LegacyDetailContent(
            data = data,
            watchlist = watchlist,
            history = history,
            onPlay = onPlay,
            onBack = onBack,
            onSeasonWatch = onSeasonWatch
        )
        return
    }

    val bgUrl = if (cinematicBackdrops && data.aniZipMetadata?.backdropUrl != null) {
        data.aniZipMetadata.backdropUrl
    } else {
        info.bannerImage ?: info.coverImage?.extraLarge
    }

    Box(Modifier.fillMaxSize()) {
        if (isMovie || bgUrl == null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(info.coverImage?.extraLarge)
                    .crossfade(true)
                    .transformations(com.shubh.anililitv.ui.components.BlurTransformation(context, radius = 25f, sampling = 4f))
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)))
        } else {
            val context = LocalContext.current
            val isLowResFallback = !cinematicBackdrops || data.aniZipMetadata?.backdropUrl == null
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(bgUrl)
                    .crossfade(true)
                    .apply {
                        if (isLowResFallback) {
                            transformations(com.shubh.anililitv.ui.components.BlurTransformation(context, radius = 25f, sampling = 4f))
                        }
                    }
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val vertBrush = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.2f),
                    0.4f to Color.Black.copy(alpha = 0.6f),
                    0.8f to Color.Black.copy(alpha = 0.9f),
                    1.0f to Color.Black,
                )
                val horizBrush = Brush.horizontalGradient(
                    0.0f to Color.Black.copy(alpha = 0.6f),
                    1.0f to Color.Transparent,
                )
                onDrawBehind {
                    drawRect(vertBrush)
                    drawRect(horizBrush)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 64.dp, end = 48.dp, top = 48.dp, bottom = 48.dp)
        ) {
            item {
                Column(Modifier.fillParentMaxHeight()) {
                    if (!isMovie) {
                        Box(Modifier.padding(bottom = 24.dp)) {
                        val backScale by animateFloatAsState(if (backFocused) 1.05f else 1f)
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .focusRequester(backArrowRequester)
                                .onFocusChanged { backFocused = it.isFocused }
                                .graphicsLayer { scaleX = backScale; scaleY = backScale }
                                .focusHighlight(CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                    }
                Spacer(Modifier.weight(1f))
                if (!isMovie && dynamicTitleLogos && !data.aniZipMetadata?.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(data.aniZipMetadata.logoUrl).crossfade(true).build(),
                        contentDescription = info.title.preferred,
                        modifier = Modifier.height(80.dp).padding(bottom = 12.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                } else {
                    Text(
                        text = info.title.preferred,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp).padding(bottom = 12.dp)) {
                    val playFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        runCatching { playFocusRequester.requestFocus() }
                    }
                    var playFocused by remember { mutableStateOf(false) }
                    val playScale by animateFloatAsState(if (playFocused) 1.05f else 1f)
                    Row(
                        Modifier
                            .focusRequester(playFocusRequester)
                            .onFocusChanged { playFocused = it.isFocused }
                            .graphicsLayer { scaleX = playScale; scaleY = playScale }
                            .focusHighlight(RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (playFocused) Color.White else Color.White.copy(alpha = 0.2f))
                            .border(if (playFocused) 1.dp else 0.dp, Color.White, RoundedCornerShape(8.dp))
                            .clickable { playCurrent() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (playFocused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                        Text(playLabel, color = if (playFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    
                    var trackMenuVisible by remember { mutableStateOf(false) }
                    if (trackMenuVisible) {
                        com.shubh.anililitv.ui.components.TrackListMenu(
                            entry = WatchlistEntry(anilistId = info.id, title = info.title.preferred, cover = info.coverImage.best, format = info.format, averageScore = info.averageScore),
                            onDismiss = { trackMenuVisible = false }
                        )
                    }

                    var optimisticIsSaved by remember(isSaved) { mutableStateOf(isSaved) }
                    var addFocused by remember { mutableStateOf(false) }
                    val addScale by animateFloatAsState(if (addFocused) 1.05f else 1f)
                    Row(
                        Modifier
                            .onFocusChanged { addFocused = it.isFocused }
                            .graphicsLayer { scaleX = addScale; scaleY = addScale }
                            .focusHighlight(RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (addFocused) Color.White else Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                trackMenuVisible = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(if (optimisticIsSaved) Icons.Default.Check else Icons.Default.Add, contentDescription = null, tint = if (addFocused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                        Text(if (optimisticIsSaved) "In List" else "Add to List", color = if (addFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }

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

                val cleanDesc = remember(info.description) { info.description?.replace(Regex("<[^>]*>"), "")?.trim() ?: "No summary available." }
                Column(modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(0.9f)) {
                    Text(
                        text = info.title.preferred,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cleanDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.duration != null) {
                        Text("${info.duration} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (info.seasonYear != null) {
                        Text("${info.seasonYear}–", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (info.averageScore != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${info.averageScore / 10.0}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        info.genres.forEach { genre ->
                            PillBadge(genre)
                        }
                    }
                }
            }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DetailTab.values().filter { 
                        when (it) {
                            DetailTab.EPISODES -> !(isMovie && data.episodes.size <= 1)
                            DetailTab.SEASONS -> !isMovie
                        }
                    }.forEach { tab ->
                        val isSelected = selectedTab == tab
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) Color.White else if (isSelected) Color.White.copy(alpha=0.2f) else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                    color = if (isFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (tab == DetailTab.SEASONS && data.seriesLoading) {
                                    Spacer(Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        color = if (isFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            when (selectedTab) {
                DetailTab.EPISODES -> {
                    if (!(isMovie && data.episodes.size <= 1)) {
                        item {
                            EpisodesSidebar(
                                data = data,
                                historyEntry = historyEntry,
                                onPlay = onPlay,
                                onSeasonWatch = onSeasonWatch,
                                showThumbnails = episodeThumbnails,
                                modifier = Modifier.fillMaxWidth().height(500.dp)
                            )
                        }
                    }
                }
                DetailTab.SEASONS -> {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Text(
                                "(i) Note: Season mappings are automatically aggregated and may not be 100% accurate.",
                                color = Color.White.copy(alpha = 0.6f),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                        ) {
                            items(data.seasons, key = { it.id }, contentType = { "SeasonCard" }) { season ->
                                val isSelected = season.id == data.selectedSeasonId
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .wrapContentHeight()
                                        .then(if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                                ) {
                                    com.shubh.anililitv.ui.components.AnimeCard(
                                        media = season,
                                        onClick = { onSeasonWatch(season.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun EpisodesSidebar(
    data: DetailData,
    historyEntry: com.shubh.anililitv.data.library.HistoryEntry?,
    onPlay: (Int, String, String, String) -> Unit,
    onSeasonWatch: (Int) -> Unit,
    showThumbnails: Boolean = true,
    onListFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val episodes = data.episodes

    var selectedRangeIndex by remember { mutableStateOf(0) }
    val chunkSize = 30
    val episodeChunks = remember(episodes) { episodes.chunked(chunkSize) }
    if (selectedRangeIndex >= episodeChunks.size && episodeChunks.isNotEmpty()) {
        selectedRangeIndex = episodeChunks.size - 1
    }
    val currentEpisodes = episodeChunks.getOrNull(selectedRangeIndex).orEmpty()
    
    var aniListEpisodeMeta by remember(data.info.id) { mutableStateOf<Map<Int, AniListEpisodeMeta>>(emptyMap()) }
    LaunchedEffect(data.info.id) {
        aniListEpisodeMeta = fetchAniListEpisodeMeta(data.info.id)
    }
    
    val listState = rememberTvLazyListState()
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


    Column(modifier.fillMaxSize().focusGroup()) {
        val chunksFocusRequester = remember { FocusRequester() }
        
        if (episodeChunks.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(vertical = 12.dp)
                    .focusRequester(chunksFocusRequester)
                    .focusRestorer(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodeChunks.size) { index ->
                    val chunk = episodeChunks[index]
                    val isSelected = index == selectedRangeIndex
                    val label = if (chunk.size == 1) chunk.first().displayNumber else "${chunk.first().displayNumber}-${chunk.last().displayNumber}"
                    EpisodeRangeButton(
                        label = label,
                        isSelected = isSelected,
                        onClick = { selectedRangeIndex = index }
                    )
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

        val episodeListMode by com.shubh.anililitv.data.settings.SettingsStore.episodeListMode.collectAsState()
        
        val listContent: androidx.tv.foundation.lazy.list.TvLazyListScope.() -> Unit = {
            tvItemsIndexed(currentEpisodes, key = { _, ep -> ep.pipeId }, contentType = { _, _ -> "EpisodeCard" }) { index, ep ->
                val epNumInt = ep.displayNumber.toDoubleOrNull()?.toInt() ?: (index + 1)
                val meta = aniListEpisodeMeta[epNumInt]

                val epImage = ep.image.takeIf { !it.isNullOrBlank() && it != "null" }
                val epTitle = ep.title.takeIf { !it.isNullOrBlank() && it != "null" }

                val imageUrl = epImage ?: meta?.thumbnail ?: data.info.coverImage?.large
                val titleStr = epTitle.takeIf { it != ep.displayNumber && it != "1" } ?: meta?.title ?: "Episode ${ep.displayNumber}"

                val isHoriz = episodeListMode == com.shubh.anililitv.data.settings.EpisodeListMode.HORIZONTAL
                
                EpisodeCard(
                    ep = ep,
                    imageUrl = imageUrl,
                    titleStr = titleStr,
                    showThumbnails = showThumbnails,
                    isHoriz = isHoriz,
                    isTargetItem = index == targetItemIndex && initialScrollDone,
                    focusRequester = episodeFocusRequester,
                    onPlay = { onPlay(data.selectedSeasonId, "auto", data.preferredCategory.api, ep.displayNumber) }
                )
            }
        }
        
        androidx.compose.runtime.key(selectedRangeIndex) {
        if (episodeListMode == com.shubh.anililitv.data.settings.EpisodeListMode.HORIZONTAL) {
            TvLazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .focusRestorer()
                    .onFocusChanged { onListFocusChanged(it.hasFocus) }
                    .focusProperties {
                        if (episodeChunks.size > 1) up = chunksFocusRequester
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                content = listContent
            )
        } else {
            androidx.tv.foundation.lazy.list.TvLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .onFocusChanged { onListFocusChanged(it.hasFocus) }
                    .focusProperties {
                        if (episodeChunks.size > 1) up = chunksFocusRequester
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = listContent
            )
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


@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LegacyDetailContent(
    data: DetailData,
    watchlist: List<WatchlistEntry>,
    history: List<HistoryEntry>,
    onPlay: (Int, String, String, String) -> Unit,
    onBack: () -> Unit,
    onSeasonWatch: (Int) -> Unit,
) {
    val info = data.info
    val isSaved = watchlist.any { it.anilistId == info.id }
    val historyEntry = history.firstOrNull { it.anilistId == info.id }

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
        if (isResume) "Resume E$playEpisodeStr" else "Play E$playEpisodeStr"
    }

    val playCurrent: () -> Unit = {
        when {
            historyEntry != null -> onPlay(info.id, historyEntry.provider, historyEntry.category, historyEntry.episodeLabel)
            data.episodes.isNotEmpty() -> onPlay(data.selectedSeasonId, "auto", data.preferredCategory.api, data.episodes.first().displayNumber)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(info.coverImage?.extraLarge)
                .crossfade(true)
                .transformations(com.shubh.anililitv.ui.components.BlurTransformation(context, radius = 25f, sampling = 4f))
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)))
        
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            val leftWeight = if (isMovie && data.episodes.size <= 1) 1.0f else 0.4f
            Column(Modifier.weight(leftWeight).fillMaxHeight()) {
                Box(Modifier.padding(bottom = 16.dp)) {
                    var backFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .onFocusChanged { backFocused = it.isFocused }
                            .focusHighlight(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                var trackMenuVisibleLegacy by remember { mutableStateOf(false) }
                var optimisticIsSavedLegacy by remember(isSaved) { mutableStateOf(isSaved) }
                if (trackMenuVisibleLegacy) {
                    com.shubh.anililitv.ui.components.TrackListMenu(
                        entry = com.shubh.anililitv.data.library.WatchlistEntry(anilistId = info.id, title = info.title.preferred, cover = info.coverImage?.best, format = info.format, averageScore = info.averageScore),
                        onDismiss = { trackMenuVisibleLegacy = false }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(info.coverImage?.extraLarge).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.width(130.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text(info.title.preferred, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        if (info.seasonYear != null) Text("${info.seasonYear}", color = Color.White.copy(0.7f))
                        if (info.duration != null) Text("${info.duration} min", color = Color.White.copy(0.7f))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionPill(Icons.Default.PlayArrow, playLabel, playCurrent)
                            CircleActionButton(if (optimisticIsSavedLegacy) Icons.Default.Check else Icons.Default.Add) {
                                trackMenuVisibleLegacy = true
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                val cleanDesc = remember(info.description) { info.description?.replace(Regex("<[^>]*>"), "")?.trim() ?: "No summary available." }
                Column {
                    Text(info.title.preferred, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cleanDesc, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.85f), maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
            }
            
            if (!(isMovie && data.episodes.size <= 1)) {
                Column(Modifier.weight(0.6f).fillMaxHeight()) {
                    LegacyEpisodesSidebar(
                        data = data,
                        historyEntry = historyEntry,
                        onPlay = onPlay,
                        onSeasonWatch = onSeasonWatch,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LegacyEpisodesSidebar(
    data: DetailData,
    historyEntry: com.shubh.anililitv.data.library.HistoryEntry?,
    onPlay: (Int, String, String, String) -> Unit,
    onSeasonWatch: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val episodes = data.episodes

    var selectedRangeIndex by remember { mutableStateOf(0) }
    val chunkSize = 30
    val episodeChunks = remember(episodes) { episodes.chunked(chunkSize) }
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

    Column(modifier.fillMaxSize().focusGroup()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isMovie = data.info.format?.uppercase() == "MOVIE"
            if (data.seasons.size > 1 && !isMovie) {
                var seasonsMenuVisible by remember { mutableStateOf(false) }
                Box {
                    var seasonsFocused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .onFocusChanged { seasonsFocused = it.isFocused }
                            .focusHighlight(RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (seasonsFocused) Color.White else Color.Black.copy(alpha = 0.4f))
                            .clickable { seasonsMenuVisible = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = if (seasonsFocused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                        Text("Seasons", color = if (seasonsFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        if (data.seriesLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = seasonsMenuVisible,
                        onDismissRequest = { seasonsMenuVisible = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        data.seasons.forEach { season ->
                            val isSelected = season.id == data.selectedSeasonId
                            var miFocused by remember { mutableStateOf(false) }
                            DropdownMenuItem(
                                text = { Text(season.title.preferred, color = if (miFocused) Color.Black else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { seasonsMenuVisible = false; onSeasonWatch(season.id) },
                                modifier = Modifier.onFocusChanged { miFocused = it.isFocused }.background(if (miFocused) Color.White else Color.Transparent)
                            )
                        }
                    }
                }
            }
            
            if (episodeChunks.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(episodeChunks.size) { index ->
                        val chunk = episodeChunks[index]
                        val isSelected = index == selectedRangeIndex
                        val label = if (chunk.size == 1) chunk.first().displayNumber else "${chunk.first().displayNumber}-${chunk.last().displayNumber}"
                    EpisodeRangeButton(
                        label = label,
                        isSelected = isSelected,
                        onClick = { selectedRangeIndex = index }
                    )
                    }
                }
            }
        }

        androidx.compose.runtime.key(selectedRangeIndex) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.65f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(currentEpisodes, key = { _, ep -> ep.pipeId }, contentType = { _, _ -> "EpisodeCard" }) { index, ep ->
                val titleStr = ep.title.takeIf { !it.isNullOrBlank() && it != "null" && it != ep.displayNumber && it != "1" } ?: "Episode ${ep.displayNumber}"

                EpisodeCard(
                    ep = ep,
                    imageUrl = ep.image,
                    titleStr = titleStr,
                    showThumbnails = true,
                    isHoriz = false,
                    isTargetItem = index == targetItemIndex && initialScrollDone,
                    focusRequester = episodeFocusRequester,
                    onPlay = { onPlay(data.selectedSeasonId, "auto", data.preferredCategory.api, ep.displayNumber) }
                )
            }
        }
        }
    }
}



@Composable
private fun EpisodeRangeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isFocused) 1.08f else 1f)
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .border(if (isFocused) 2.dp else 0.dp, Color.White, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (isFocused) Color.Black else Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EpisodeCard(
    ep: com.shubh.anililitv.data.model.EpisodeItem,
    imageUrl: String?,
    titleStr: String,
    showThumbnails: Boolean,
    isHoriz: Boolean,
    isTargetItem: Boolean,
    focusRequester: FocusRequester,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val cardWidth = if (isHoriz) 260.dp else 260.dp
    
    if (isHoriz) {
        Column(
            Modifier
                .width(cardWidth)
                .height(210.dp)
                .then(if (isTargetItem) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    1.dp,
                    if (isFocused) Color.White else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onPlay)
                .padding(12.dp)
        ) {
            if (showThumbnails && imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Episode Thumbnail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "${ep.displayNumber}. $titleStr",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (ep.filler) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("FILLER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .height(114.dp)
                .then(if (isTargetItem) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    1.dp,
                    if (isFocused) Color.White else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onPlay)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showThumbnails && imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Episode Thumbnail",
                    modifier = Modifier
                        .width(160.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.wrapContentHeight()) {
                Text(
                    "${ep.displayNumber}. $titleStr",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (ep.filler) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
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

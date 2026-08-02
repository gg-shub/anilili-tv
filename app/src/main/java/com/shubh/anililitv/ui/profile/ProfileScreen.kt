package com.shubh.anililitv.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.focus.FocusRequester
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import com.shubh.anililitv.data.auth.AccountService
import com.shubh.anililitv.data.auth.AuthManager
import com.shubh.anililitv.data.auth.MalAuthManager

import com.shubh.anililitv.data.library.HistoryEntry
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.data.library.WatchlistEntry
import com.shubh.anililitv.playback.EpisodeDownloads
import com.shubh.anililitv.playback.EpisodeDownload
import com.shubh.anililitv.playback.EpisodeDownloadState
import com.shubh.anililitv.data.model.MediaListEntry
import androidx.compose.material.icons.filled.Delete
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.focusHighlight
import com.shubh.anililitv.ui.components.PullRefreshContainer
import com.shubh.anililitv.ui.components.RatingBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class LibraryView(val label: String) {
    WATCHLIST("My watchlist"),
    WATCHING("Watching"),
    REWATCHING("Re-watching"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    DROPPED("Dropped"),
}

private data class SelectOption(val value: String?, val label: String)

private data class SavedAnimeCardData(
    val id: Int,
    val title: String,
    val cover: String?,
    val format: String?,
    val airingStatus: String?,
    val status: String?,
    val userScore: Double?,
    val averageScore: Int?,
    val progress: Int?,
    val totalEpisodes: Int?,
)

private val formatOptions = listOf(
    SelectOption(null, "Any format"),
    SelectOption("TV", "TV"),
    SelectOption("MOVIE", "Movie"),
    SelectOption("ONA", "ONA"),
    SelectOption("OVA", "OVA"),
    SelectOption("SPECIAL", "Special"),
)

private val airingOptions = listOf(
    SelectOption(null, "Any airing status"),
    SelectOption("RELEASING", "Ongoing"),
    SelectOption("FINISHED", "Completed"),
    SelectOption("NOT_YET_RELEASED", "Upcoming"),
    SelectOption("HIATUS", "On hiatus"),
    SelectOption("CANCELLED", "Cancelled"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onAnimeClick: (Int) -> Unit,
    onResume: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(),
) {
    val device = LocalAppDeviceProfile.current
    val token by AuthManager.token.collectAsState()
    val context = LocalContext.current
    val malLoggedIn by MalAuthManager.loggedIn.collectAsState()
    val profileState by vm.profile.collectAsState()
    val episodeDownloads by EpisodeDownloads.downloads(context).collectAsState()
    val historyRaw by LibraryStore.history.collectAsState()
    val history = remember(historyRaw) { historyRaw.sortedByDescending { it.updatedAt } }
    val downloads by com.shubh.anililitv.playback.EpisodeDownloads.downloads(context).collectAsState(initial = emptyList())
    val watchlist by LibraryStore.watchlist.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    var loginService by remember { mutableStateOf<AccountService?>(null) }
    val aniLoggedIn = token != null
    val anyLoggedIn = aniLoggedIn || malLoggedIn
    var selectedViewName by rememberSaveable { mutableStateOf(LibraryView.WATCHLIST.name) }
    var selectedFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAiring by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryTab by rememberSaveable { mutableStateOf("History") }

    LaunchedEffect(anyLoggedIn) {
        if (!anyLoggedIn) {
            selectedViewName = LibraryView.WATCHLIST.name
        } else {
            selectedViewName = LibraryView.WATCHING.name
        }
        vm.loadIfLoggedIn()
    }

    when (loginService) {
        AccountService.ANILIST -> {
            LoginWebView(
                authorizeUrl = remember { AuthManager.authorizeUrl() },
                isRedirect = AuthManager::isRedirect,
                extractResult = AuthManager::extractToken,
                onResult = { loginService = null; vm.onLoggedIn(it) },
                onCancel = { loginService = null },
            )
            return
        }
        AccountService.MAL -> {
            LoginWebView(
                authorizeUrl = remember { MalAuthManager.authorizeUrl() },
                isRedirect = MalAuthManager::isRedirect,
                extractResult = MalAuthManager::extractCode,
                onResult = { loginService = null; vm.onMalCode(it) },
                onCancel = { loginService = null },
            )
            return
        }
        null -> Unit
    }

    val profile = (profileState as? UiState.Success)?.data
    val combinedWatchlist = remember(profile, watchlist, history) {
        buildCombinedWatchlist(profile, watchlist, history)
    }
    val selectedView = LibraryView.valueOf(selectedViewName)
    val localCards = remember(watchlist, history) {
        val historyById = history.associateBy { it.anilistId }
        watchlist.map { saved ->
            SavedAnimeCardData(
                id = saved.anilistId,
                title = saved.title,
                cover = saved.cover,
                format = saved.format,
                airingStatus = null,
                status = saved.status,
                userScore = null,
                averageScore = saved.averageScore,
                progress = historyById[saved.anilistId]?.episodeNumber?.toInt(),
                totalEpisodes = null,
            )
        }
    }

    val selectedCards = remember(profile, combinedWatchlist, localCards, selectedView, selectedFormat, selectedAiring) {
        val source = if (profile != null) {
            when (selectedView) {
                LibraryView.WATCHLIST -> combinedWatchlist
                LibraryView.WATCHING -> aniListCards(profile.watching)
                LibraryView.REWATCHING -> aniListCards(profile.rewatching)
                LibraryView.PAUSED -> aniListCards(profile.paused)
                LibraryView.COMPLETED -> aniListCards(profile.completed)
                LibraryView.DROPPED -> aniListCards(profile.dropped)
            }
        } else {
            val statusFilter = when (selectedView) {
                LibraryView.WATCHLIST -> "PLANNING"
                LibraryView.WATCHING -> "CURRENT"
                LibraryView.REWATCHING -> "REPEATING"
                LibraryView.PAUSED -> "PAUSED"
                LibraryView.COMPLETED -> "COMPLETED"
                LibraryView.DROPPED -> "DROPPED"
            }
            localCards.filter { it.status == statusFilter }
        }
        
        source.filter { entry ->
            (selectedFormat == null || entry.format == selectedFormat) &&
                (selectedAiring == null || entry.airingStatus == selectedAiring)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Library", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    androidx.compose.material3.IconButton(
                        onClick = { backDispatcher?.onBackPressed() },
                        modifier = Modifier.focusHighlight(androidx.compose.foundation.shape.CircleShape)
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullRefreshContainer(
            isRefreshing = isRefreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val gridFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            val downloadedAnime = downloads.groupBy { it.metadata.anilistId }.map { it.value.first() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    @Composable
                    fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
                        var isFocused by remember { mutableStateOf(false) }
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = onClick,
                            label = { Text(text, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.padding(horizontal = 4.dp).onFocusChanged { isFocused = it.isFocused },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                containerColor = if (isFocused) Color.White else Color.Transparent,
                                labelColor = if (isFocused) Color.Black else Color.White.copy(alpha = 0.7f),
                                selectedContainerColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                selectedLabelColor = if (isFocused) Color.Black else Color.White
                            ),
                            border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color.White.copy(alpha = 0.5f),
                                selectedBorderColor = Color.Transparent
                            )
                        )
                    }
                    TabButton("History", libraryTab == "History") { libraryTab = "History" }
                    TabButton("My List", libraryTab == "My List") { libraryTab = "My List" }
                }
            }

            if (libraryTab == "History") {
                if (history.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().focusGroup().padding(horizontal = device.pagePadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            history.forEach { entry ->
                                HistoryCard(entry, onResume, { onAnimeClick(it) })
                            }
                        }
                    }
                } else {
                    item { EmptyPanel("Your watch history is empty") }
                }
            } else {
                item {
                    ProfileHero(
                        loggedIn = anyLoggedIn,
                        state = profileState,
                        onLogin = { loginService = AccountService.ANILIST },
                        onSync = { vm.loadIfLoggedIn() },
                        onLogout = vm::logout,
                        malLoggedIn = malLoggedIn,
                        onMalLogin = { loginService = AccountService.MAL },
                    )
                }


            item {
                LibraryFilters(
                    selectedView = selectedView,
                    onViewChange = { selectedViewName = it.name },
                    selectedFormat = selectedFormat,
                    onFormatChange = { selectedFormat = it },
                    selectedAiring = selectedAiring,
                    onAiringChange = { selectedAiring = it },
                    resultCount = selectedCards.size,
                    showAniListLists = profile != null,
                    gridFocus = gridFocus,
                )
            }

            if (selectedCards.isEmpty()) {
                item {
                    EmptyPanel(
                        if (selectedView == LibraryView.WATCHLIST && selectedFormat == null && selectedAiring == null) {
                            "Click Add to List + on any show to add them to your watchlist"
                        } else {
                            "No anime match these filters"
                        },
                    )
                }
            } else {
                item {
                    LazyRow(
                        modifier = Modifier.focusGroup().focusRequester(gridFocus),
                        contentPadding = PaddingValues(horizontal = device.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(if (device.isTv) 18.dp else 12.dp),
                    ) {
                        items(selectedCards, key = { it.id }) { entry ->
                            SavedAnimeCard(entry, { onAnimeClick(it) })
                        }
                    }
                }
            }
            }
            }
        }
    }
}

@Composable
private fun ProfileHero(
    loggedIn: Boolean,
    state: UiState<AniListProfile>?,
    onLogin: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    malLoggedIn: Boolean = false,
    onMalLogin: () -> Unit = {},
) {
    val device = LocalAppDeviceProfile.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = device.pagePadding)
    ) {
        if (!loggedIn) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Your anime, in one place", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "Connect AniList to browse every list, score, and episode progress from Anilili.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onLogin, modifier = Modifier.focusHighlight(CircleShape)) {
                        Text("Login with AniList", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onMalLogin, 
                        modifier = Modifier.focusHighlight(CircleShape),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E51A2)
                        )
                    ) {
                        Text("Login with MyAnimeList", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            return
        }

        when (state) {
            is UiState.Success -> {
                val viewer = state.data.viewer
                val stats = state.data.viewer.statistics?.anime
                val days = (stats?.minutesWatched ?: 0L) / 1440.0
                
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = viewer.avatar?.large,
                        contentDescription = viewer.name,
                        modifier = Modifier
                            .size(if (device.isTv) 120.dp else 100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                    
                    Spacer(Modifier.width(24.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(viewer.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text(
                            listOfNotNull(if (malLoggedIn) "Signed in via MyAnimeList" else "Signed in via AniList", joinedLabel(viewer.createdAt)).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStat((stats?.count ?: 0).toString(), "Total Anime")
                        ProfileStat(String.format(java.util.Locale.US, "%.1f", days), "Days Watched")
                        ProfileStat(String.format(java.util.Locale.US, "%.1f", stats?.meanScore ?: 0.0), "Avg Score")
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onSync, modifier = Modifier.focusHighlight(CircleShape)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync")
                            }
                            IconButton(onClick = onLogout, modifier = Modifier.focusHighlight(CircleShape)) {
                                Icon(Icons.Default.Close, contentDescription = "Logout")
                            }
                        }
                    }
                }
            }
            is UiState.Error -> Column(Modifier.padding(18.dp)) {
                Text("AniList could not be loaded", fontWeight = FontWeight.Bold)
                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = onSync) { Text("Try again") }
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            }
            else -> Text(if (malLoggedIn) "Syncing your MyAnimeList profile…" else "Syncing your AniList profile…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun LibraryFilters(
    selectedView: LibraryView,
    onViewChange: (LibraryView) -> Unit,
    selectedFormat: String?,
    onFormatChange: (String?) -> Unit,
    selectedAiring: String?,
    onAiringChange: (String?) -> Unit,
    resultCount: Int,
    showAniListLists: Boolean,
    gridFocus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val device = LocalAppDeviceProfile.current
    val viewOptions = LibraryView.entries
    var showFilterMenu by remember { mutableStateOf(false) }

    Box(Modifier.padding(horizontal = device.pagePadding)) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LazyRow(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewOptions) { option ->
                        val isSelected = option == selectedView
                        val bgColor = if (isSelected) Color.White else Color(0xFF222222)
                        val textColor = if (isSelected) Color.Black else Color.White
                        Box(
                            modifier = Modifier
                                .focusHighlight(RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .clickable { onViewChange(option) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(option.label, fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }
                
                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier.focusHighlight(CircleShape)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    
                    if (showFilterMenu) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showFilterMenu = false },
                            shape = RectangleShape,
                            containerColor = Color.Black,
                            titleContentColor = Color.White,
                            textContentColor = Color.White,
                            modifier = Modifier.border(1.dp, Color.White, RectangleShape).fillMaxWidth(0.9f),
                            title = {
                                Text("Filter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column {
                                        Text("Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        SelectorField(
                                            value = formatOptions.first { it.value == selectedFormat }.label,
                                            options = formatOptions,
                                            onSelect = onFormatChange,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Column {
                                        Text("Airing status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        SelectorField(
                                            value = airingOptions.first { it.value == selectedAiring }.label,
                                            options = airingOptions,
                                            onSelect = onAiringChange,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { showFilterMenu = false },
                                    shape = RectangleShape,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) {
                                    Text("Show results", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            }
            Text(
                "$resultCount anime",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectorField(
    value: String,
    options: List<SelectOption>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(8.dp)).clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { expanded = false; onSelect(option.value) },
                )
            }
        }
    }
}

@Composable
private fun ProfileSectionTitle(title: String, subtitle: String) {
    val device = LocalAppDeviceProfile.current
    Column(Modifier.padding(horizontal = device.pagePadding, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(entry: HistoryEntry, onResume: (HistoryEntry) -> Unit, onDetails: (Int) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val device = LocalAppDeviceProfile.current
    val minPerRow = 5
    val spacing = 16.dp
    val cardWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - device.pagePadding * 2 - spacing * (minPerRow - 1)) / minPerRow
    Column(Modifier.width(cardWidth).focusHighlight().clickable(onClick = { onDetails(entry.anilistId) })) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RectangleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(entry.cover, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.White, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = .4f))) {
                Box(Modifier.fillMaxWidth(entry.progressFraction.coerceAtLeast(.02f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
        Text(entry.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        Text("EP ${entry.episodeLabel}  ·  ${entry.provider}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedAnimeCard(entry: SavedAnimeCardData, onAnimeClick: (Int) -> Unit) {
    val device = LocalAppDeviceProfile.current
    Column(Modifier.width(device.posterWidth).focusHighlight().clickable { onAnimeClick(entry.id) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RectangleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(entry.cover, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            when {
                entry.userScore != null -> CornerBadge(
                    text = String.format(Locale.US, "★ %.1f", entry.userScore),
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                )
                entry.averageScore != null -> RatingBadge(entry.averageScore, Modifier.align(Alignment.TopStart).padding(5.dp))
            }
            entry.progress?.let { progress ->
                CornerBadge(
                    text = "$progress/${entry.totalEpisodes ?: "?"}",
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                )
            }
        }
        Text(
            entry.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            listOfNotNull(entry.status?.toDisplayLabel(), entry.format?.replace('_', ' ')).joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CornerBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RectangleShape,
        color = Color.Black.copy(alpha = .82f),
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun EmptyPanel(text: String) {
    val device = LocalAppDeviceProfile.current
    Box(Modifier.padding(horizontal = device.pagePadding)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RectangleShape
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content,
    )
}

private fun joinedLabel(createdAt: Long?): String? {
    if (createdAt == null || createdAt <= 0) return null
    return runCatching {
        "Joined " + Instant.ofEpochSecond(createdAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM yyyy"))
    }.getOrNull()
}

private fun buildCombinedWatchlist(
    profile: AniListProfile?,
    local: List<WatchlistEntry>,
    history: List<HistoryEntry>,
): List<SavedAnimeCardData> {
    val aniListPlanning = profile?.planning.orEmpty().distinctBy { it.media?.id }
    val byMediaId = aniListPlanning.mapNotNull { entry -> entry.media?.id?.let { it to entry } }.toMap()
    val historyById = history.associateBy { it.anilistId }

    return buildList {
        local.forEach { saved ->
            val aniListEntry = byMediaId[saved.anilistId]
            val media = aniListEntry?.media
            add(
                SavedAnimeCardData(
                    id = saved.anilistId,
                    title = media?.title?.preferred ?: saved.title,
                    cover = media?.coverImage?.best ?: saved.cover,
                    format = media?.format ?: saved.format,
                    airingStatus = media?.status,
                    status = aniListEntry?.status,
                    userScore = aniListEntry?.score?.takeIf { it > 0 },
                    averageScore = media?.averageScore ?: saved.averageScore,
                    progress = aniListEntry?.progress ?: historyById[saved.anilistId]?.episodeNumber?.toInt(),
                    totalEpisodes = media?.episodes,
                ),
            )
        }
        aniListPlanning.forEach { aniListEntry ->
            val media = aniListEntry.media ?: return@forEach
            if (local.any { it.anilistId == media.id }) return@forEach
            add(
                SavedAnimeCardData(
                    id = media.id,
                    title = media.title.preferred,
                    cover = media.coverImage.best,
                    format = media.format,
                    airingStatus = media.status,
                    status = aniListEntry.status,
                    userScore = aniListEntry.score.takeIf { it > 0 },
                    averageScore = media.averageScore,
                    progress = aniListEntry.progress,
                    totalEpisodes = media.episodes,
                ),
            )
        }
    }
}

private fun aniListCards(entries: List<MediaListEntry>): List<SavedAnimeCardData> =
    entries.mapNotNull { entry ->
        val media = entry.media ?: return@mapNotNull null
        SavedAnimeCardData(
            id = media.id,
            title = media.title.preferred,
            cover = media.coverImage.best,
            format = media.format,
            airingStatus = media.status,
            status = entry.status,
            userScore = entry.score.takeIf { it > 0 },
            averageScore = media.averageScore,
            progress = entry.progress,
            totalEpisodes = media.episodes,
        )
    }.distinctBy { it.id }

private fun String.toDisplayLabel(): String = when (this) {
    "CURRENT" -> "Watching"
    "REPEATING" -> "Re-watching"
    "PLANNING" -> "Planning"
    "PAUSED" -> "Paused"
    else -> lowercase().replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

@Composable
private fun Modifier.tvEscapeDown(target: androidx.compose.ui.focus.FocusRequester? = null): Modifier {
    val device = LocalAppDeviceProfile.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return this.onPreviewKeyEvent { event ->
        if (device.isTv && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            keyboard?.hide()
            if (target != null) {
                try { target.requestFocus() } catch (e: Exception) {}
            } else {
                focusManager.moveFocus(FocusDirection.Down)
            }
            true
        } else {
            false
        }
        }
    }

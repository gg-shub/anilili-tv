package com.miruronative.ui.profile

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.miruronative.data.auth.AuthManager
import com.miruronative.data.library.HistoryEntry
import com.miruronative.data.library.LibraryStore
import com.miruronative.data.library.WatchlistEntry
import com.miruronative.data.model.MediaListEntry
import com.miruronative.data.settings.SettingsStore
import com.miruronative.ui.UiState
import com.miruronative.ui.adaptive.LocalAppDeviceProfile
import com.miruronative.ui.adaptive.focusHighlight
import com.miruronative.ui.components.RatingBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ProfileListKind(val label: String) {
    WATCHING("Watching"),
    REWATCHING("Re-watching"),
    COMPLETED("Completed"),
    PLANNING("Planning"),
    PAUSED("Paused"),
    DROPPED("Dropped"),
    ;

    fun entries(profile: AniListProfile): List<MediaListEntry> = when (this) {
        WATCHING -> profile.watching
        REWATCHING -> profile.rewatching
        COMPLETED -> profile.completed
        PLANNING -> profile.planning
        PAUSED -> profile.paused
        DROPPED -> profile.dropped
    }
}

private data class SelectOption(val value: String?, val label: String)

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
    val profileState by vm.profile.collectAsState()
    val history by LibraryStore.history.collectAsState()
    val watchlist by LibraryStore.watchlist.collectAsState()
    val autoplay by SettingsStore.autoplay.collectAsState()
    val autoSync by SettingsStore.autoSyncAniList.collectAsState()
    val preferDub by SettingsStore.preferDub.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    var selectedListName by rememberSaveable { mutableStateOf(ProfileListKind.WATCHING.name) }
    var selectedFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAiring by rememberSaveable { mutableStateOf<String?>(null) }
    var titleFilter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(token) { vm.loadIfLoggedIn() }

    if (showLogin) {
        LoginWebView(
            onToken = { showLogin = false; vm.onLoggedIn(it) },
            onCancel = { showLogin = false },
        )
        return
    }

    val selectedList = ProfileListKind.valueOf(selectedListName)
    val profile = (profileState as? UiState.Success)?.data
    val listEntries = remember(profile, selectedList, selectedFormat, selectedAiring, titleFilter) {
        profile?.let(selectedList::entries).orEmpty().filter { entry ->
            val media = entry.media ?: return@filter false
            (selectedFormat == null || media.format == selectedFormat) &&
                (selectedAiring == null || media.status == selectedAiring) &&
                (titleFilter.isBlank() || media.title.preferred.contains(titleFilter, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Library", fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ProfileHero(
                    loggedIn = token != null,
                    state = profileState,
                    onLogin = { showLogin = true },
                    onSync = vm::loadIfLoggedIn,
                    onLogout = vm::logout,
                )
            }

            profile?.let { loaded ->
                item { ProfileStats(loaded) }
                item {
                    LibraryFilters(
                        profile = loaded,
                        selectedList = selectedList,
                        onListChange = { selectedListName = it.name },
                        selectedFormat = selectedFormat,
                        onFormatChange = { selectedFormat = it },
                        selectedAiring = selectedAiring,
                        onAiringChange = { selectedAiring = it },
                        titleFilter = titleFilter,
                        onTitleFilterChange = { titleFilter = it },
                        resultCount = listEntries.size,
                    )
                }
                item { AniListTableHeader(selectedList, listEntries.size) }
                if (listEntries.isEmpty()) {
                    item { EmptyPanel("No anime match these filters") }
                } else {
                    items(listEntries, key = { it.id }) { entry ->
                        AniListTableRow(entry, onAnimeClick)
                    }
                }
            }

            item { ProfileSectionTitle("Continue Watching", "Pick up exactly where you left off") }
            if (history.isEmpty()) {
                item { EmptyPanel("Nothing watched yet") }
            } else {
                item {
                    LazyRow(
                        modifier = Modifier.focusGroup(),
                        contentPadding = PaddingValues(horizontal = device.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(if (device.isTv) 18.dp else 12.dp),
                    ) {
                        items(history, key = { it.anilistId }) { entry -> HistoryCard(entry, onResume) }
                    }
                }
            }

            item { ProfileSectionTitle("Watchlist", "Saved on this device") }
            if (watchlist.isEmpty()) {
                item { EmptyPanel("Tap the heart on any anime to save it") }
            } else {
                items(watchlist.chunked(device.episodeColumns.coerceAtMost(6))) { row ->
                    val columns = device.episodeColumns.coerceAtMost(6)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = device.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { entry -> WatchlistCard(entry, onAnimeClick, Modifier.weight(1f)) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            item {
                SettingsPanel(
                    autoplay = autoplay,
                    preferDub = preferDub,
                    autoSync = autoSync,
                )
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
) {
    val device = LocalAppDeviceProfile.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = device.pagePadding)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
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
                Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp).focusHighlight(RoundedCornerShape(9.dp))) {
                    Text("Login with AniList", fontWeight = FontWeight.Bold)
                }
            }
            return
        }

        when (state) {
            is UiState.Success -> {
                val viewer = state.data.viewer
                Box(Modifier.fillMaxWidth().height(if (device.isTv) 230.dp else 180.dp)) {
                    AsyncImage(
                        model = viewer.bannerImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    )
                    AsyncImage(
                        model = viewer.avatar?.large,
                        contentDescription = viewer.name,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .size(if (device.isTv) 104.dp else 88.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(viewer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(
                            listOfNotNull("Signed in via AniList", joinedLabel(viewer.createdAt)).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onSync, modifier = Modifier.focusHighlight(RoundedCornerShape(9.dp))) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync AniList")
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.focusHighlight(RoundedCornerShape(9.dp))) {
                        Icon(Icons.Default.Close, contentDescription = "Logout")
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
            else -> Text("Syncing your AniList profile…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun ProfileStats(profile: AniListProfile) {
    val device = LocalAppDeviceProfile.current
    val stats = profile.viewer.statistics?.anime
    val days = (stats?.minutesWatched ?: 0L) / 1440.0
    Panel(Modifier.padding(horizontal = device.pagePadding)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            ProfileStat((stats?.count ?: 0).toString(), "Total anime", Modifier.weight(1f))
            ProfileStat(String.format(Locale.US, "%.1f", days), "Days watched", Modifier.weight(1f))
            ProfileStat(String.format(Locale.US, "%.1f", stats?.meanScore ?: 0.0), "Average score", Modifier.weight(1f))
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
    profile: AniListProfile,
    selectedList: ProfileListKind,
    onListChange: (ProfileListKind) -> Unit,
    selectedFormat: String?,
    onFormatChange: (String?) -> Unit,
    selectedAiring: String?,
    onAiringChange: (String?) -> Unit,
    titleFilter: String,
    onTitleFilterChange: (String) -> Unit,
    resultCount: Int,
) {
    val device = LocalAppDeviceProfile.current
    Panel(Modifier.padding(horizontal = device.pagePadding)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val listOptions = ProfileListKind.entries.map { kind ->
                SelectOption(kind.name, "${kind.label} (${kind.entries(profile).size})")
            }
            SelectorField(
                value = listOptions.first { it.value == selectedList.name }.label,
                options = listOptions,
                onSelect = { value -> ProfileListKind.entries.firstOrNull { it.name == value }?.let(onListChange) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectorField(
                    value = formatOptions.first { it.value == selectedFormat }.label,
                    options = formatOptions,
                    onSelect = onFormatChange,
                    modifier = Modifier.weight(1f),
                )
                SelectorField(
                    value = airingOptions.first { it.value == selectedAiring }.label,
                    options = airingOptions,
                    onSelect = onAiringChange,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = titleFilter,
                onValueChange = onTitleFilterChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter by title") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (titleFilter.isNotEmpty()) {
                    { IconButton(onClick = { onTitleFilterChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear title filter") } }
                } else null,
                shape = RoundedCornerShape(9.dp),
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$resultCount ${selectedList.label}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(9.dp)).clickable { expanded = true },
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.background,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun AniListTableHeader(kind: ProfileListKind, count: Int) {
    val device = LocalAppDeviceProfile.current
    Column(Modifier.padding(horizontal = device.pagePadding, vertical = 4.dp)) {
        Text("${kind.label} list", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            Text("Title", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Score", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
            Text("Progress", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(68.dp))
        }
        Text("$count entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AniListTableRow(entry: MediaListEntry, onAnimeClick: (Int) -> Unit) {
    val media = entry.media ?: return
    val device = LocalAppDeviceProfile.current
    val score = when {
        entry.score > 0 -> String.format(Locale.US, "%.1f", entry.score)
        media.averageScore != null -> String.format(Locale.US, "%.1f", media.averageScore / 10.0)
        else -> "—"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = device.pagePadding)
            .focusHighlight(RoundedCornerShape(9.dp))
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .clickable { onAnimeClick(media.id) }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        AsyncImage(
            model = media.coverImage.best,
            contentDescription = media.title.preferred,
            modifier = Modifier.padding(start = 9.dp).size(width = 44.dp, height = 58.dp).clip(RoundedCornerShape(7.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(
            media.title.preferred,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(score, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(58.dp))
        Text("${entry.progress}/${media.episodes ?: "?"}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(68.dp))
    }
}

@Composable
private fun ProfileSectionTitle(title: String, subtitle: String) {
    val device = LocalAppDeviceProfile.current
    Column(Modifier.padding(horizontal = device.pagePadding, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onResume: (HistoryEntry) -> Unit) {
    val device = LocalAppDeviceProfile.current
    Column(Modifier.width(device.posterWidth).focusHighlight().clickable { onResume(entry) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
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
private fun WatchlistCard(entry: WatchlistEntry, onAnimeClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.focusHighlight().clickable { onAnimeClick(entry.anilistId) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
            AsyncImage(entry.cover, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            entry.averageScore?.let { RatingBadge(it, Modifier.align(Alignment.TopStart).padding(5.dp)) }
        }
        Text(entry.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        Text(entry.format.orEmpty().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsPanel(autoplay: Boolean, preferDub: Boolean, autoSync: Boolean) {
    val device = LocalAppDeviceProfile.current
    Panel(Modifier.padding(horizontal = device.pagePadding, vertical = 8.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Playback & sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            SettingSwitch("Autoplay next episode", "Continue automatically", autoplay, SettingsStore::setAutoplay)
            SettingSwitch("Prefer dubbed audio", "Use dub first when available", preferDub, SettingsStore::setPreferDub)
            SettingSwitch("Sync progress to AniList", "Update episode progress while watching", autoSync, SettingsStore::setAutoSyncAniList)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
            TextButton(onClick = LibraryStore::clearHistory, modifier = Modifier.padding(horizontal = 8.dp).focusHighlight(RoundedCornerShape(8.dp))) {
                Text("Clear viewing history")
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(9.dp)).clickable { onCheckedChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange, Modifier.focusProperties { canFocus = false })
    }
}

@Composable
private fun EmptyPanel(text: String) {
    val device = LocalAppDeviceProfile.current
    Panel(Modifier.padding(horizontal = device.pagePadding)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
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

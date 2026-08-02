
package com.shubh.anililitv.ui.settings
import kotlinx.coroutines.flow.last
import androidx.compose.foundation.layout.height


import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ClosedCaption

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

import androidx.compose.material.icons.filled.KeyboardArrowRight
import com.shubh.anililitv.data.ProviderCatalog
import com.shubh.anililitv.ui.components.CaptionAppearanceDialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shubh.anililitv.data.auth.AuthManager
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.data.library.MalExportFile
import com.shubh.anililitv.data.reminder.AutomaticReleaseManager
import com.shubh.anililitv.data.reminder.ReleaseSyncScheduler
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.data.update.UpdateManager
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.adaptive.LocalAppDeviceProfile
import com.shubh.anililitv.ui.adaptive.focusHighlight
import com.shubh.anililitv.ui.profile.AniListProfile
import com.shubh.anililitv.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(),
) {
    val context = LocalContext.current
    val device = LocalAppDeviceProfile.current
    val token by AuthManager.token.collectAsState()
    val profileState by vm.profile.collectAsState()
    val history by LibraryStore.history.collectAsState()
    val watchlist by LibraryStore.watchlist.collectAsState()
    val autoplay by SettingsStore.autoplay.collectAsState()

    val autoSkip by SettingsStore.autoSkipIntroOutro.collectAsState()
    val autoSync by SettingsStore.autoSyncAniList.collectAsState()
    val preferDub by SettingsStore.preferDub.collectAsState()
    val releaseNotifications by SettingsStore.releaseNotifications.collectAsState()
    val hideAdultContent by SettingsStore.hideAdultContent.collectAsState()
    val subtitlesWithDub by SettingsStore.subtitlesWithDub.collectAsState()
    val syncSavedToAniList by SettingsStore.syncSavedToAniList.collectAsState()
    val slideshowCategory by SettingsStore.slideshowCategory.collectAsState()
    val cinematicSlideshow by SettingsStore.cinematicSlideshow.collectAsState()
    val useLegacyDetailLayout by SettingsStore.useLegacyDetailLayout.collectAsState()
    val dynamicTitleLogos by SettingsStore.dynamicTitleLogos.collectAsState()
    val cinematicBackdrops by SettingsStore.cinematicBackdrops.collectAsState()
    val updateState by UpdateManager.state.collectAsState()
    val defaultQuality by SettingsStore.defaultQuality.collectAsState()
    val serverPriority by SettingsStore.serverPriority.collectAsState()
    val menuLanguage by SettingsStore.menuLanguage.collectAsState()
    val cacheSizeMb by SettingsStore.cacheSizeMb.collectAsState()
    val profile = (profileState as? UiState.Success<AniListProfile>)?.data
    val scope = rememberCoroutineScope()
    var pendingMalExport by remember { mutableStateOf<MalExportFile?>(null) }
    var malExportBusy by remember { mutableStateOf(false) }
    var malExportMessage by remember { mutableStateOf<String?>(null) }
    val malLoggedIn by com.shubh.anililitv.data.auth.MalAuthManager.loggedIn.collectAsState()
    val malImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        val entries = com.shubh.anililitv.data.library.MalImport.parse(bytes).last().getOrThrow()
                        malExportMessage = "Imported ${entries.size} entries"
                    }
                } catch (e: Exception) {
                    malExportMessage = "Import failed: ${e.message}"
                }
            }
        }
    }
    var captionAppearanceVisible by remember { mutableStateOf(false) }
    var diagnosticsMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) { vm.loadIfLoggedIn() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        SettingsStore.setReleaseNotifications(granted)
        if (granted) ReleaseSyncScheduler.runNow(context)
    }
    val malExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml"),
    ) { uri ->
        val file = pendingMalExport
        pendingMalExport = null
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(file.xml.toByteArray(Charsets.UTF_8))
            } ?: error("Couldn't open export file")
        }.onSuccess {
            malExportMessage = buildString {
                append("Exported ${file.exportedCount} anime")
                if (file.skippedCount > 0) append("; skipped ${file.skippedCount} without MAL IDs")
            }
        }.onFailure { error ->
            malExportMessage = error.message ?: "MAL export failed"
        }
    }

    fun setReleaseNotifications(enabled: Boolean) {
        if (!enabled) {
            SettingsStore.setReleaseNotifications(false)
            AutomaticReleaseManager.cancelAll()
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SettingsStore.setReleaseNotifications(true)
            ReleaseSyncScheduler.runNow(context)
        }
    }

    fun setWatchlistSync(enabled: Boolean) {
        SettingsStore.setSyncSavedToAniList(enabled)
        if (enabled) {
            LibraryStore.syncSavedToRemote()
            vm.loadIfLoggedIn(refresh = true)
        }
    }

    fun exportMal() {
        if (malExportBusy) return
        scope.launch {
            malExportBusy = true
            malExportMessage = null
            runCatching { vm.buildMalExport(profile, watchlist, history) }
                .onSuccess { file ->
                    if (file.exportedCount == 0) {
                        malExportMessage = "No MAL-mapped anime to export"
                    } else {
                        pendingMalExport = file
                        malExportLauncher.launch(file.fileName)
                    }
                }
                .onFailure { error -> malExportMessage = error.message ?: "MAL export failed" }
            malExportBusy = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Black) },
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
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(
                start = device.pagePadding,
                end = device.pagePadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
                        item {
                SettingsAccordion("Playback", defaultExpanded = true) {
                    SettingSelection(
                        title = "Default quality",
                        description = "Preferred video resolution",
                        value = defaultQuality,
                        options = com.shubh.anililitv.data.settings.DefaultQuality.entries,
                        labelFor = { it.label },
                        onValueChange = SettingsStore::setDefaultQuality
                    )
                    SettingSwitch("Autoplay next episode", "Continue automatically", autoplay, SettingsStore::setAutoplay)
                    SettingSwitch("Auto-skip intro and outro", "Use provider skip times when available", autoSkip, SettingsStore::setAutoSkipIntroOutro)
                    SettingSwitch("Prefer dubbed audio", "Use dub first when available", preferDub, SettingsStore::setPreferDub)
                    SettingSwitch(
                        "Subtitles with dubbed audio",
                        "Show subtitles on dubbed episodes too (applies from the next episode)",
                        subtitlesWithDub,
                        SettingsStore::setSubtitlesWithDub,
                    )
                }
            }
item { 
                SettingsAccordion("Appearance") {
                    SettingSelection(
                        title = "Poster Size",
                        description = "Size of standard anime posters on Home Screen",
                        value = com.shubh.anililitv.data.settings.SettingsStore.posterSize.collectAsState().value,
                        options = com.shubh.anililitv.data.settings.PosterSize.entries,
                        labelFor = { it.label },
                        onValueChange = SettingsStore::setPosterSize
                    )
                    SettingSelection(
                        title = "Slideshow Content",
                        description = "Choose which anime category populates the Home Screen hero slideshow",
                        value = slideshowCategory,
                        options = com.shubh.anililitv.data.settings.SlideshowCategory.entries,
                        labelFor = { it.label },
                        onValueChange = SettingsStore::setSlideshowCategory
                    )
                    SettingSwitch(
                        "New Slideshow layout",
                        "Show full-screen hero art and logos for featured anime",
                        cinematicSlideshow,
                        SettingsStore::setCinematicSlideshow,
                    )
                    SettingSwitch(
                        "Compact detail layout",
                        "Use the old side-by-side layout for anime details",
                        useLegacyDetailLayout,
                        SettingsStore::setUseLegacyDetailLayout,
                    )
                    SettingSwitch(
                        "Show title logos",
                        "Replace text titles with logos in detail screen when available.",
                        dynamicTitleLogos,
                        SettingsStore::setDynamicTitleLogos,
                    )
                    SettingSwitch(
                        "High resolution backdrops",
                        "Use high resolution art as detail screen background when available.",
                        cinematicBackdrops,
                        SettingsStore::setCinematicBackdrops,
                    )
                    SettingsAction(
                        title = "Caption appearance",
                        icon = null,
                        trailingIcon = { Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, contentDescription = null) },
                        enabled = true,
                        onClick = { captionAppearanceVisible = true },
                    )
                }
            }
            item {
                SettingsAccordion("Servers") {
                    ServerPrioritySetting(serverPriority, SettingsStore::setServerPriority)
                }
            }
            item {
                SettingsAccordion("Content") {
                    SettingSwitch(
                        "Hide adult content",
                        "Keep hentai out of Home, Search, Browse, and Schedule",
                        hideAdultContent,
                        SettingsStore::setHideAdultContent,
                    )
                }
            }

            item {
                SettingsAccordion("List sync (AniList / MyAnimeList)") {
                    SettingSwitch("Sync episode progress", "Update watched episodes while playing", autoSync, SettingsStore::setAutoSyncAniList)
                    SettingSwitch(
                        "Sync watchlist with Planning",
                        "Import Planning after login and add new saves without replacing active progress",
                        syncSavedToAniList,
                        ::setWatchlistSync,
                    )
                    SettingsAction(
                        title = if (malExportBusy) "Preparing MyAnimeList export..." else "Export MyAnimeList XML",
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        enabled = !malExportBusy && (token == null || profile != null),
                        onClick = ::exportMal,
                    )
                    SettingsAction(
                        title = "Import MyAnimeList XML",
                        icon = { Icon(androidx.compose.material.icons.Icons.Default.Upload, contentDescription = null) },
                        enabled = true,
                        onClick = { malImportLauncher.launch("*/*") },
                    )
                    malExportMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            item {
                SettingsAccordion("Notifications") {
                    SettingSwitch(
                        "Notification alerts",
                        "Logged in: AniList notifications; logged out: saved anime releases",
                        releaseNotifications,
                        ::setReleaseNotifications,
                    )
                }
            }

            item {
                SettingsAccordion("App") {
                    var cacheFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusHighlight(RoundedCornerShape(8.dp))
                            .onFocusChanged { cacheFocused = it.isFocused }
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            val steps = listOf(100, 200, 400, 800)
                                            val currentIdx = steps.indexOf(cacheSizeMb).takeIf { it >= 0 } ?: steps.indexOf(steps.minByOrNull { kotlin.math.abs(it - cacheSizeMb) }!!)
                                            if (currentIdx > 0) SettingsStore.setCacheSizeMb(steps[currentIdx - 1])
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            val steps = listOf(100, 200, 400, 800)
                                            val currentIdx = steps.indexOf(cacheSizeMb).takeIf { it >= 0 } ?: steps.indexOf(steps.minByOrNull { kotlin.math.abs(it - cacheSizeMb) }!!)
                                            if (currentIdx < steps.size - 1) SettingsStore.setCacheSizeMb(steps[currentIdx + 1])
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cache Limit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray.copy(alpha = 0.3f))) {
                            val steps = listOf(100, 200, 400, 800)
                            val currentIdx = steps.indexOf(cacheSizeMb).takeIf { it >= 0 } ?: steps.indexOf(steps.minByOrNull { kotlin.math.abs(it - cacheSizeMb) }!!)
                            val progress = if (steps.size > 1) currentIdx.toFloat() / (steps.size - 1) else 0f
                            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(targetValue = progress, label = "Progress")
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction = animatedProgress).background(MaterialTheme.colorScheme.primary))
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                        Text("${cacheSizeMb} MB", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    SettingsAction(
                        title = "Clear viewing history",
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                        enabled = history.isNotEmpty(),
                        onClick = LibraryStore::clearHistory,
                    )
                    SettingsAction(
                        title = "Clear cache",
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                        enabled = true,
                        onClick = {
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.shubh.anililitv.data.cache.CacheManager.clear(context) }
                                android.widget.Toast.makeText(context, "Cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    var showAboutMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    SettingsAction(
                        title = "About",
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        enabled = true,
                        onClick = { showAboutMenu = true },
                    )
                    if (showAboutMenu) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showAboutMenu = false },
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = { Text("About", fontWeight = FontWeight.Bold) },
                            text = {
                                androidx.compose.foundation.layout.Column {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            showAboutMenu = false
                                            runCatching {
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/gg-shub/anilili-tv")).apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                            }
                                        },
                                        shape = androidx.compose.ui.graphics.RectangleShape,
                                        modifier = Modifier.fillMaxWidth().focusHighlight(androidx.compose.ui.graphics.RectangleShape),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Info, contentDescription = "GitHub", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.width(8.dp))
                                        Text("GitHub: gg-shub/anilili-tv", color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { showAboutMenu = false },
                                    shape = androidx.compose.ui.graphics.RectangleShape,
                                    modifier = Modifier.focusHighlight(androidx.compose.ui.graphics.RectangleShape)
                                ) { Text("Close") }
                            }
                        )
                    }
                    SettingsAction(
                        title = "Check for updates",
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        enabled = true,
                        onClick = { UpdateManager.check(context, manual = true) },
                    )
                    Text(
                        "Version ${UpdateManager.currentVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
    
    if (captionAppearanceVisible) {
        CaptionAppearanceDialog(onDismiss = { captionAppearanceVisible = false })
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
private fun SettingsAction(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            icon()
            Text(title, modifier = Modifier.padding(start = 10.dp).weight(1f))
        } else {
            Text(title, modifier = Modifier.weight(1f))
        }
        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    )
}

@Composable
private fun <T> SettingSelection(
    title: String,
    description: String,
    value: T,
    options: List<T>,
    labelFor: (T) -> String,
    onValueChange: (T) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(8.dp))
            .clickable { expanded = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Box {
            Text(labelFor(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(labelFor(option)) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerPrioritySetting(
    priority: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val hideAdult by SettingsStore.hideAdultContent.collectAsState()
    val servers = remember(hideAdult) { ProviderCatalog.anivexaProvidersFor(hideAdult) + ProviderCatalog.fastProviders }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            "Server priority",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            if (priority.isEmpty()) {
                "Pick up to ${com.shubh.anililitv.data.settings.MAX_SERVER_PRIORITY} servers in the order you want them tried. " +
                    "With none picked, the built-in order is used."
            } else {
                priority.mapIndexed { index, server ->
                    "${index + 1}. ${ProviderCatalog.label(server)}"
                }.joinToString("   ")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            servers.distinct().forEach { server ->
                val rank = priority.indexOf(server)
                val picked = rank >= 0
                val full = priority.size >= com.shubh.anililitv.data.settings.MAX_SERVER_PRIORITY
                com.shubh.anililitv.ui.components.CustomFilterChip(
                    selected = picked,
                    onClick = {
                        if (picked || !full) {
                            onChange(
                                if (picked) priority.filterNot { it == server } else priority + server,
                            )
                        }
                    },
                    label = ProviderCatalog.label(server),
                    leadingIcon = if (picked) {
                        { Text("${rank + 1}", fontWeight = FontWeight.Bold) }
                    } else {
                        null
                    },
                )
            }
        }
        if (priority.isNotEmpty()) {
            TextButton(
                onClick = { onChange(emptyList()) },
                modifier = Modifier.height(32.dp).focusHighlight(RoundedCornerShape(16.dp), customBorderColor = androidx.compose.ui.graphics.Color.White),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)
            ) {
                Text("Clear")
            }
        }
    }
}


@Composable
fun SettingsAccordion(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(defaultExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
                content()
            }
        }
    }
}

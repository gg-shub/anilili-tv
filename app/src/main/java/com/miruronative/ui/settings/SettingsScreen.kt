package com.miruronative.ui.settings

import coil.imageLoader
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import com.miruronative.ui.components.CaptionAppearanceDialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miruronative.data.auth.AuthManager
import com.miruronative.data.library.LibraryStore
import com.miruronative.data.library.MalExportFile
import com.miruronative.data.reminder.AutomaticReleaseManager
import com.miruronative.data.reminder.ReleaseSyncScheduler
import com.miruronative.data.settings.SettingsStore
import com.miruronative.data.update.UpdateManager
import com.miruronative.diagnostics.DiagnosticsLog
import com.miruronative.ui.UiState
import com.miruronative.ui.adaptive.LocalAppDeviceProfile
import com.miruronative.ui.adaptive.focusHighlight
import com.miruronative.ui.profile.AniListProfile
import com.miruronative.ui.profile.ProfileViewModel
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
    val useExternalPlayer by SettingsStore.useExternalPlayer.collectAsState()
    val syncSavedToAniList by SettingsStore.syncSavedToAniList.collectAsState()
    val updateState by UpdateManager.state.collectAsState()
    val profile = (profileState as? UiState.Success<AniListProfile>)?.data
    val scope = rememberCoroutineScope()
    var pendingMalExport by remember { mutableStateOf<MalExportFile?>(null) }
    var malExportBusy by remember { mutableStateOf(false) }
    var malExportMessage by remember { mutableStateOf<String?>(null) }
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
            LibraryStore.syncSavedToAniList()
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
            item { SettingsSectionTitle("Playback") }

            item { SettingSwitch("Autoplay next episode", "Continue automatically", autoplay, SettingsStore::setAutoplay) }
            item { SettingSwitch("Auto-skip intro and outro", "Use provider skip times when available", autoSkip, SettingsStore::setAutoSkipIntroOutro) }
            item { SettingSwitch("Prefer dubbed audio", "Use dub first when available", preferDub, SettingsStore::setPreferDub) }
            item {
                SettingSwitch(
                    "Subtitles with dubbed audio",
                    "Show subtitles on dubbed episodes too (applies from the next episode)",
                    subtitlesWithDub,
                    SettingsStore::setSubtitlesWithDub,
                )
            }

            item {
                SettingSwitch(
                    "Use external player",
                    "Play videos in an external app like VLC or MX Player",
                    useExternalPlayer,
                    SettingsStore::setUseExternalPlayer,
                )
            }
            item {
                SettingsAction(
                    title = "Caption appearance",
                    icon = null,
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, contentDescription = null) },
                    enabled = true,
                    onClick = { captionAppearanceVisible = true },
                )
            }
            item { SectionDivider() }

            item { SettingsSectionTitle("Content") }
            item {
                SettingSwitch(
                    "Hide adult content",
                    "Keep hentai out of Home, Search, Browse, and Schedule",
                    hideAdultContent,
                    SettingsStore::setHideAdultContent,
                )
            }
            item { SectionDivider() }

            item { SettingsSectionTitle("AniList") }
            item { SettingSwitch("Sync episode progress", "Update watched episodes while playing", autoSync, SettingsStore::setAutoSyncAniList) }
            item {
                SettingSwitch(
                    "Sync watchlist with Planning",
                    "Import Planning after login and add new saves without replacing active progress",
                    syncSavedToAniList,
                    ::setWatchlistSync,
                )
            }
            item { SectionDivider() }

            item { SettingsSectionTitle("Notifications") }
            item {
                SettingSwitch(
                    "Notification alerts",
                    "Logged in: AniList notifications; logged out: saved anime releases",
                    releaseNotifications,
                    ::setReleaseNotifications,
                )
            }
            item { SectionDivider() }

            item { SettingsSectionTitle("Data") }

            item {
                SettingsAction(
                    title = if (malExportBusy) "Preparing MyAnimeList export..." else "Export MyAnimeList XML",
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    enabled = !malExportBusy && (token == null || profile != null),
                    onClick = ::exportMal,
                )
            }
            malExportMessage?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                SettingsAction(
                    title = "Clear viewing history",
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                    enabled = history.isNotEmpty(),
                    onClick = LibraryStore::clearHistory,
                )
            }
            item {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current
                var cacheCleared by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                SettingsAction(
                    title = if (cacheCleared) "Cache cleared" else "Clear cache",
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                    enabled = !cacheCleared,
                    onClick = {
                        scope.launch {
                            com.miruronative.data.AppGraph.repository.clearCache()
                            com.miruronative.data.AppGraph.httpClient.cache?.evictAll()
                            context.imageLoader.diskCache?.clear()
                            context.imageLoader.memoryCache?.clear()
                            cacheCleared = true
                        }
                    },
                )
            }
            item { SectionDivider() }

            item { SettingsSectionTitle("App") }
            item {
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
            }
            item {
                SettingsAction(
                    title = "Check for updates",
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    enabled = true,
                    onClick = { UpdateManager.check(context, manual = true) },
                )
            }
            item {
                Text(
                    "Version ${UpdateManager.currentVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
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
        modifier = Modifier.padding(top = 10.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = .7f),
    )
}
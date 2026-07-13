package com.miruronative.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruronative.data.AppGraph
import com.miruronative.data.auth.AuthManager
import com.miruronative.data.model.MediaListEntry
import com.miruronative.data.model.Viewer
import com.miruronative.data.library.HistoryEntry
import com.miruronative.data.library.LibraryStore
import com.miruronative.data.library.MalExport
import com.miruronative.data.library.MalExportEntry
import com.miruronative.data.library.MalExportFile
import com.miruronative.data.library.WatchlistEntry
import com.miruronative.data.settings.SettingsStore
import com.miruronative.ui.UiState
import com.miruronative.ui.rethrowIfCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

data class AniListProfile(
    val viewer: Viewer,
    val watching: List<MediaListEntry>,
    val rewatching: List<MediaListEntry>,
    val planning: List<MediaListEntry>,
    val paused: List<MediaListEntry>,
    val completed: List<MediaListEntry>,
    val dropped: List<MediaListEntry>,
)

class ProfileViewModel : ViewModel() {
    private val repo = AppGraph.repository

    private val _profile = MutableStateFlow<UiState<AniListProfile>?>(null)
    val profile = _profile.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun loadIfLoggedIn(refresh: Boolean = false) {
        if (!AuthManager.isLoggedIn) {
            _profile.value = null
            return
        }
        viewModelScope.launch {
            if (refresh && _profile.value is UiState.Success) _isRefreshing.value = true else _profile.value = UiState.Loading
            try {
                val viewer = repo.viewer() ?: error("Couldn't load your AniList profile")
                val lists = repo.userAnimeList(viewer.id)?.lists ?: emptyList()
                val watching = lists.filter { it.status == "CURRENT" }.flatMap { it.entries }
                val rewatching = lists.filter { it.status == "REPEATING" }.flatMap { it.entries }
                val planning = lists.filter { it.status == "PLANNING" }.flatMap { it.entries }
                val paused = lists.filter { it.status == "PAUSED" }.flatMap { it.entries }
                val completed = lists.filter { it.status == "COMPLETED" }.flatMap { it.entries }
                val dropped = lists.filter { it.status == "DROPPED" }.flatMap { it.entries }
                if (SettingsStore.syncSavedToAniList.value) {
                    LibraryStore.hydrateWatchlistFromAniList(
                        planning.mapNotNull { entry ->
                            val media = entry.media ?: return@mapNotNull null
                            WatchlistEntry(
                                anilistId = media.id,
                                title = media.title.preferred,
                                cover = media.coverImage.best,
                                format = media.format,
                                averageScore = media.averageScore,
                            )
                        },
                    )
                }
                _profile.value = UiState.Success(
                    AniListProfile(viewer, watching, rewatching, planning, paused, completed, dropped),
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _profile.value = UiState.Error(e.message ?: "Failed to load AniList")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refresh() = loadIfLoggedIn(refresh = true)

    fun onLoggedIn(token: String) {
        AuthManager.setToken(token)
        LibraryStore.syncSavedToAniList()
        loadIfLoggedIn()
    }

    fun logout() {
        AuthManager.logout()
        _profile.value = null
    }

    suspend fun buildMalExport(
        profile: AniListProfile?,
        watchlist: List<WatchlistEntry>,
        history: List<HistoryEntry>,
    ): MalExportFile = withContext(Dispatchers.IO) {
        val entries = LinkedHashMap<Int, MalExportEntry>()
        var skipped = 0

        suspend fun addMediaList(entry: MediaListEntry) {
            val media = entry.media ?: run {
                skipped++
                return
            }
            val resolved = if (media.idMal != null) media else repo.animeInfo(media.id) ?: media
            val (status, rewatching) = MalExport.statusFromAniList(entry.status)
            val exportEntry = MalExport.entryFromMedia(
                media = resolved,
                status = status,
                progress = entry.progress,
                score = entry.score,
                rewatching = rewatching,
            )
            if (exportEntry == null) skipped++ else entries[resolved.id] = exportEntry
        }

        listOf(
            profile?.watching.orEmpty(),
            profile?.rewatching.orEmpty(),
            profile?.completed.orEmpty(),
            profile?.paused.orEmpty(),
            profile?.dropped.orEmpty(),
            profile?.planning.orEmpty(),
        ).flatten().forEach { addMediaList(it) }

        val historyById = history.associateBy { it.anilistId }
        watchlist.forEach { saved ->
            if (entries.containsKey(saved.anilistId)) return@forEach
            val media = repo.animeInfo(saved.anilistId) ?: run {
                skipped++
                return@forEach
            }
            val progress = historyById[saved.anilistId]?.episodeNumber?.toInt() ?: 0
            val exportEntry = MalExport.entryFromMedia(
                media = media,
                status = MalExport.statusFromLocal(progress, media.episodes),
                progress = progress,
            )
            if (exportEntry == null) skipped++ else entries[saved.anilistId] = exportEntry
        }

        history.forEach { item ->
            if (entries.containsKey(item.anilistId)) return@forEach
            val media = repo.animeInfo(item.anilistId) ?: run {
                skipped++
                return@forEach
            }
            val progress = item.episodeNumber.toInt()
            val exportEntry = MalExport.entryFromMedia(
                media = media,
                status = MalExport.statusFromLocal(progress, media.episodes),
                progress = progress,
            )
            if (exportEntry == null) skipped++ else entries[item.anilistId] = exportEntry
        }

        MalExport.fromEntries(profile?.viewer?.name, entries.values.toList(), skipped)
    }
}

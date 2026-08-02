package com.shubh.anililitv.ui.profile
import kotlinx.coroutines.flow.last

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.data.auth.AccountService
import com.shubh.anililitv.data.auth.AuthManager
import com.shubh.anililitv.data.auth.MalAuthManager
import com.shubh.anililitv.data.model.MediaListEntry
import com.shubh.anililitv.data.model.MediaListCollection
import com.shubh.anililitv.data.model.Viewer
import com.shubh.anililitv.data.library.HistoryEntry
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.data.library.MalExport
import com.shubh.anililitv.data.library.MalExportEntry
import com.shubh.anililitv.data.library.MalExportFile
import com.shubh.anililitv.data.library.MalImport
import com.shubh.anililitv.data.library.MalImportSummary
import com.shubh.anililitv.data.library.WatchlistEntry
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.rethrowIfCancellation
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
    val service: AccountService = AccountService.ANILIST,
)

class ProfileViewModel : ViewModel() {
    private val repo = AppGraph.repository

    private val _profile = MutableStateFlow<UiState<AniListProfile>?>(null)
    val profile = _profile.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun loadIfLoggedIn(refresh: Boolean = false) {
        val service = AccountService.active
        if (service == null) {
            _profile.value = null
            return
        }
        viewModelScope.launch {
            if (refresh && _profile.value is UiState.Success) _isRefreshing.value = true else _profile.value = UiState.Loading
            try {
                val (viewer, entries) = when (service) {
                    AccountService.ANILIST -> {
                        val viewer = repo.viewer() ?: error("Couldn't load your AniList profile")
                        viewer to repo.userAnimeList(viewer.id).allEntries()
                    }
                    AccountService.MAL -> repo.malViewer() to repo.malAnimeList()
                }
                val watching = entries.filter { it.status == "CURRENT" }
                val rewatching = entries.filter { it.status == "REPEATING" }
                val planning = entries.filter { it.status == "PLANNING" }
                val paused = entries.filter { it.status == "PAUSED" }
                val completed = entries.filter { it.status == "COMPLETED" }
                val dropped = entries.filter { it.status == "DROPPED" }
                LibraryStore.hydrateRemoteLibrary(entries)
                _profile.value = UiState.Success(
                    AniListProfile(viewer, watching, rewatching, planning, paused, completed, dropped, service),
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _profile.value = UiState.Error(e.message ?: "Failed to load ${service.label}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refresh() = loadIfLoggedIn(refresh = true)

    fun onLoggedIn(token: String) {
        AuthManager.setToken(token)
        LibraryStore.syncSavedToRemote()
        loadIfLoggedIn()
    }

    fun onMalCode(code: String) {
        _profile.value = UiState.Loading
        viewModelScope.launch {
            try {
                MalAuthManager.exchangeCode(code)
                LibraryStore.syncSavedToRemote()
                loadIfLoggedIn()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                MalAuthManager.logout()
                _profile.value = UiState.Error(e.message ?: "MyAnimeList login failed")
            }
        }
    }

    fun logout() {
        AuthManager.logout()
        MalAuthManager.logout()
        LibraryStore.clearRemoteLibrary()
        _profile.value = null
    }

    suspend fun importMalXml(bytes: ByteArray): MalImportSummary = withContext(Dispatchers.IO) {
        val parsed = MalImport.parse(bytes).last().getOrThrow()
        if (parsed.isEmpty()) error("No anime entries found in that file")
        val mediaByMalId = repo.mediaByMalIds(parsed.map { it.malId })
            .filter { it.idMal != null }
            .associateBy { it.idMal!! }
        val entries = parsed.mapNotNull { entry ->
            val media = mediaByMalId[entry.malId] ?: return@mapNotNull null
            WatchlistEntry(
                anilistId = media.id,
                title = media.title.preferred,
                cover = media.coverImage.best,
                format = media.format,
                averageScore = media.averageScore,
            )
        }
        val added = LibraryStore.importWatchlist(entries)
        MalImportSummary(
            totalEntries = parsed.size,
            added = added,
            alreadySaved = entries.size - added,
            unmatched = parsed.size - entries.size,
        )
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
            if (item.fromRemote) return@forEach
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

internal fun MediaListCollection?.allEntries(): List<MediaListEntry> = this?.lists.orEmpty()
    .flatMap { it.entries }
    .distinctBy { it.id }

package com.miruronative.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruronative.data.AppGraph
import com.miruronative.data.auth.AuthManager
import com.miruronative.data.auth.MalAuthManager
import kotlinx.coroutines.launch
import com.miruronative.data.model.MediaListEntry
import com.miruronative.data.model.MediaListCollection
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
        if (!AuthManager.isLoggedIn && !MalAuthManager.isLoggedIn) {
            _profile.value = null
            return
        }
        viewModelScope.launch {
            if (refresh && _profile.value is UiState.Success) _isRefreshing.value = true else _profile.value = UiState.Loading
            try {
                if (AuthManager.isLoggedIn) {
                    val viewer = repo.viewer() ?: error("Couldn't load your AniList profile")
                    val entries = repo.userAnimeList(viewer.id).allEntries()
                    val watching = entries.filter { it.status == "CURRENT" }
                    val rewatching = entries.filter { it.status == "REPEATING" }
                    val planning = entries.filter { it.status == "PLANNING" }
                    val paused = entries.filter { it.status == "PAUSED" }
                    val completed = entries.filter { it.status == "COMPLETED" }
                    val dropped = entries.filter { it.status == "DROPPED" }
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
                } else if (MalAuthManager.isLoggedIn) {
                    val malUser = repo.mal.viewer()
                    val viewer = com.miruronative.data.model.Viewer(
                        id = malUser.id,
                        name = malUser.name,
                        avatar = malUser.picture?.let { com.miruronative.data.model.UserAvatar(it) },
                        bannerImage = null,
                        statistics = com.miruronative.data.model.ViewerStatistics(
                            anime = com.miruronative.data.model.AnimeStat(
                                count = malUser.animeStatistics?.numItems ?: 0,
                                episodesWatched = 0,
                                minutesWatched = (malUser.animeStatistics?.numDaysWatched?.times(24 * 60) ?: 0.0).toLong(),
                                meanScore = malUser.animeStatistics?.meanScore ?: 0.0
                            )
                        )
                    )
                    val malEntries = repo.mal.animeList().map { entry ->
                        com.miruronative.data.model.MediaListEntry(
                            id = entry.malId,
                            progress = entry.progress,
                            score = entry.score,
                            status = entry.status,
                            media = com.miruronative.data.model.Media(
                                id = entry.malId,
                                title = com.miruronative.data.model.MediaTitle(userPreferred = entry.title),
                                coverImage = com.miruronative.data.model.CoverImage(extraLarge = entry.coverImage),
                                format = null,
                                averageScore = 0,
                                episodes = entry.totalEpisodes,
                            )
                        )
                    }
                    val watching = malEntries.filter { it.status == "CURRENT" }
                    val rewatching = malEntries.filter { it.status == "REPEATING" }
                    val planning = malEntries.filter { it.status == "PLANNING" }
                    val paused = malEntries.filter { it.status == "PAUSED" }
                    val completed = malEntries.filter { it.status == "COMPLETED" }
                    val dropped = malEntries.filter { it.status == "DROPPED" }
                    _profile.value = UiState.Success(
                        AniListProfile(viewer, watching, rewatching, planning, paused, completed, dropped),
                    )
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _profile.value = UiState.Error(e.message ?: "Failed to load profile")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refresh() = loadIfLoggedIn(refresh = true)    fun onLoggedIn(token: String) {
        AuthManager.setToken(token)
        LibraryStore.syncSavedToRemote()
        loadIfLoggedIn()
    }

    /** MAL redirect hands back a code; trade it for tokens before loading the profile. */
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

/** Custom-list-only entries are duplicated across groups; flatten and classify by entry status. */
internal fun MediaListCollection?.allEntries(): List<MediaListEntry> = this?.lists.orEmpty()
    .flatMap { it.entries }
    .distinctBy { it.id }

package com.shubh.anililitv.data.library

import android.content.Context
import android.content.SharedPreferences
import com.shubh.anililitv.data.reminder.ReleaseSyncScheduler
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.data.auth.AccountService
import com.shubh.anililitv.data.auth.AuthManager
import com.shubh.anililitv.data.model.MediaListEntry
import com.shubh.anililitv.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

object LibraryStore {
    private const val KEY_HISTORY = "history"
    private const val KEY_WATCHLIST = "watchlist"
    private const val KEY_REMOTE_STATUSES = "remote_statuses"
    private const val KEY_DISMISSED_REMOTE_HISTORY = "dismissed_remote_history"
    private const val MAX_HISTORY = 100
    private const val REMOTE_REFRESH_INTERVAL_MS = 30_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aniListSyncMutex = Mutex()
    private var remoteRefreshJob: Job? = null
    @Volatile private var lastRemoteRefreshAt = 0L

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history = _history.asStateFlow()

    private val _watchlist = MutableStateFlow<List<WatchlistEntry>>(emptyList())
    val watchlist = _watchlist.asStateFlow()

    private val _remoteStatuses = MutableStateFlow<Map<Int, String>>(emptyMap())
    val remoteStatuses = _remoteStatuses.asStateFlow()
    private var dismissedRemoteHistory = emptySet<Int>()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("miruro_library", Context.MODE_PRIVATE)
        val storedHistory = decodeList(prefs.getString(KEY_HISTORY, null), HistoryEntry.serializer())
        val orderedHistory = sortHistoryLatestFirst(storedHistory).take(MAX_HISTORY)
        _history.value = orderedHistory
        if (orderedHistory != storedHistory) {
            persist(KEY_HISTORY, orderedHistory, HistoryEntry.serializer())
        }
        _watchlist.value = decodeList(prefs.getString(KEY_WATCHLIST, null), WatchlistEntry.serializer())
        _remoteStatuses.value = decodeList(
            prefs.getString(KEY_REMOTE_STATUSES, null),
            RemoteListStatus.serializer(),
        ).associate { it.anilistId to it.status }
        dismissedRemoteHistory = prefs.getStringSet(KEY_DISMISSED_REMOTE_HISTORY, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()
    }


    fun upsertHistory(entry: HistoryEntry) {
        if (entry.anilistId in dismissedRemoteHistory) {
            dismissedRemoteHistory = dismissedRemoteHistory - entry.anilistId
            persistDismissedRemoteHistory()
        }
        val stamped = entry.copy(updatedAt = System.currentTimeMillis(), hiddenFromHome = false)
        val updated = sortHistoryLatestFirst(
            buildList {
                add(stamped)
                addAll(_history.value.filter { it.anilistId != entry.anilistId })
            },
        ).take(MAX_HISTORY)
        _history.value = updated
        persist(KEY_HISTORY, updated, HistoryEntry.serializer())
        scope.launch { WatchNextManager.publish(appContext, stamped) }
    }

    fun updateProgress(anilistId: Int, episodeNumber: Double, positionMs: Long, durationMs: Long) {
        val existing = _history.value.firstOrNull { it.anilistId == anilistId } ?: return
        if (existing.episodeNumber != episodeNumber) return
        upsertHistory(existing.copy(positionMs = positionMs, durationMs = durationMs))
    }

    fun historyFor(anilistId: Int): HistoryEntry? = _history.value.firstOrNull { it.anilistId == anilistId }

    fun removeHistory(anilistId: Int, dismissRemoteSeed: Boolean = true) {
        val updated = _history.value.filter { it.anilistId != anilistId }
        if (updated == _history.value) return
        _history.value = updated
        persist(KEY_HISTORY, updated, HistoryEntry.serializer())
        if (dismissRemoteSeed && AccountService.active != null) {
            dismissedRemoteHistory = dismissedRemoteHistory + anilistId
            persistDismissedRemoteHistory()
        }
        scope.launch { WatchNextManager.remove(appContext, anilistId) }
    }

    fun hideFromHome(anilistId: Int) {
        val currentList = _history.value
        val index = currentList.indexOfFirst { it.anilistId == anilistId }
        if (index == -1 || currentList[index].hiddenFromHome) return

        val updated = currentList.toMutableList()
        updated[index] = updated[index].copy(hiddenFromHome = true)
        
        _history.value = updated
        persist(KEY_HISTORY, updated, HistoryEntry.serializer())
        scope.launch { WatchNextManager.remove(appContext, anilistId) }
    }

    fun clearHistory() {
        if (AccountService.active != null) {
            dismissedRemoteHistory = dismissedRemoteHistory + _history.value.map(HistoryEntry::anilistId)
            persistDismissedRemoteHistory()
        }
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
        scope.launch { WatchNextManager.removeAll(appContext) }
    }


    fun isInWatchlist(anilistId: Int): Boolean = _watchlist.value.any { it.anilistId == anilistId }

    fun updateWatchlistStatus(entry: WatchlistEntry, status: String) {
        val updatedEntry = entry.copy(addedAt = System.currentTimeMillis(), status = status)
        val updated = listOf(updatedEntry) + _watchlist.value.filter { it.anilistId != entry.anilistId }
        _watchlist.value = updated
        persist(KEY_WATCHLIST, updated, WatchlistEntry.serializer())
        ReleaseSyncScheduler.runNow(appContext)
        val service = AccountService.active
        val syncable = !com.shubh.anililitv.data.remote.isHanimeMediaId(entry.anilistId)
        if (service != null && syncable && SettingsStore.syncSavedToAniList.value) {
            scope.launch {
                aniListSyncMutex.withLock {
                    runCatching {
                        AppGraph.repository.updateAnimeListStatus(entry.anilistId, status)
                    }.onSuccess {
                        refreshRemoteLibrary(force = true)
                    }.onFailure {
                        com.shubh.anililitv.diagnostics.DiagnosticsLog.throwable(
                            "${service.label} status update failed id=${entry.anilistId} status=$status",
                            it,
                        )
                    }
                }
            }
        }
    }

    fun toggleWatchlist(entry: WatchlistEntry) {
        val updated = if (isInWatchlist(entry.anilistId)) {
            _watchlist.value.filter { it.anilistId != entry.anilistId }
        } else {
            listOf(entry.copy(addedAt = System.currentTimeMillis())) + _watchlist.value
        }
        _watchlist.value = updated
        persist(KEY_WATCHLIST, updated, WatchlistEntry.serializer())
        ReleaseSyncScheduler.runNow(appContext)
        val service = AccountService.active
        val syncable = !com.shubh.anililitv.data.remote.isHanimeMediaId(entry.anilistId)
        if (service != null && syncable && SettingsStore.syncSavedToAniList.value) {
            val saved = updated.any { it.anilistId == entry.anilistId }
            scope.launch {
                aniListSyncMutex.withLock {
                    runCatching {
                        when (service) {
                            AccountService.ANILIST -> AppGraph.repository.syncSavedAnime(entry.anilistId, saved)
                            AccountService.MAL -> AppGraph.repository.malSyncSavedAnime(entry.anilistId, saved)
                        }
                    }.onSuccess {
                        refreshRemoteLibrary(force = true)
                    }.onFailure {
                        com.shubh.anililitv.diagnostics.DiagnosticsLog.throwable(
                            "${service.label} saved sync failed id=${entry.anilistId} saved=$saved",
                            it,
                        )
                    }
                }
            }
        }
    }

    fun syncSavedToRemote() {
        val service = AccountService.active ?: return
        if (!SettingsStore.syncSavedToAniList.value) return
        val savedIds = _watchlist.value.map { it.anilistId }
        scope.launch {
            aniListSyncMutex.withLock {
                runCatching {
                    when (service) {
                        AccountService.ANILIST -> AppGraph.repository.syncSavedAnime(savedIds)
                        AccountService.MAL -> AppGraph.repository.malSyncSavedAnime(savedIds)
                    }
                }.onSuccess {
                    refreshRemoteLibrary(force = true)
                }.onFailure {
                    com.shubh.anililitv.diagnostics.DiagnosticsLog.throwable(
                        "${service.label} watchlist sync failed (${savedIds.size} titles)",
                        it,
                    )
                }
            }
        }
    }

    fun importWatchlist(entries: List<WatchlistEntry>): Int {
        val current = _watchlist.value
        val existing = current.mapTo(mutableSetOf()) { it.anilistId }
        val now = System.currentTimeMillis()
        val newEntries = entries
            .distinctBy { it.anilistId }
            .filter { it.anilistId !in existing }
            .map { it.copy(addedAt = now) }
        if (newEntries.isEmpty()) return 0
        val updated = newEntries + current
        _watchlist.value = updated
        persist(KEY_WATCHLIST, updated, WatchlistEntry.serializer())
        ReleaseSyncScheduler.runNow(appContext)
        syncSavedToRemote()
        return newEntries.size
    }

    fun hydrateWatchlistFromAniList(entries: List<WatchlistEntry>) {
        val merged = mergeWatchlistEntries(_watchlist.value, entries)
        if (merged == _watchlist.value) return
        _watchlist.value = merged
        persist(KEY_WATCHLIST, merged, WatchlistEntry.serializer())
        ReleaseSyncScheduler.runNow(appContext)
    }

    fun hydrateRemoteLibrary(entries: List<MediaListEntry>) {
        val statuses = remoteListStatuses(entries)
        _remoteStatuses.value = statuses
        persist(
            KEY_REMOTE_STATUSES,
            statuses.map { (id, status) -> RemoteListStatus(id, status) },
            RemoteListStatus.serializer(),
        )
        lastRemoteRefreshAt = System.currentTimeMillis()
        seedHistoryFromRemote(entries)

        if (SettingsStore.syncSavedToAniList.value) {
            hydrateWatchlistFromAniList(
                entries.mapNotNull { entry ->
                    if (entry.status != "PLANNING") return@mapNotNull null
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
    }

    private fun seedHistoryFromRemote(entries: List<MediaListEntry>) {
        val preferDub = SettingsStore.preferDub.value
        val local = _history.value.filterNot { it.fromRemote }
        val localIds = local.mapTo(mutableSetOf()) { it.anilistId }
        val seeded = entries.mapNotNull { entry ->
            if (entry.status != "CURRENT" && entry.status != "REPEATING") return@mapNotNull null
            val media = entry.media ?: return@mapNotNull null
            if (media.id in dismissedRemoteHistory) return@mapNotNull null
            if (media.id in localIds) return@mapNotNull null
            val nextEpisode = entry.progress + 1
            val total = media.episodes
            if (total != null && total > 0 && nextEpisode > total) return@mapNotNull null
            HistoryEntry(
                anilistId = media.id,
                title = media.title.preferred,
                cover = media.coverImage.best,
                episodeNumber = nextEpisode.toDouble(),
                provider = "auto",
                category = if (preferDub) "dub" else "sub",
                fromRemote = true,
            )
        }
        val updated = sortHistoryLatestFirst(local + seeded).take(MAX_HISTORY)
        if (updated == _history.value) return
        _history.value = updated
        persist(KEY_HISTORY, updated, HistoryEntry.serializer())
    }

    fun updateRemoteStatus(anilistId: Int, status: String?) {
        val normalized = status?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val updated = _remoteStatuses.value.toMutableMap().apply {
            if (normalized == null) remove(anilistId) else put(anilistId, normalized)
        }
        _remoteStatuses.value = updated
        persist(
            KEY_REMOTE_STATUSES,
            updated.map { (id, value) -> RemoteListStatus(id, value) },
            RemoteListStatus.serializer(),
        )
    }

    @Synchronized
    fun refreshRemoteLibrary(force: Boolean = false) {
        val service = AccountService.active
        if (service == null) {
            if (_remoteStatuses.value.isNotEmpty()) clearRemoteLibrary()
            return
        }
        val now = System.currentTimeMillis()
        if (remoteRefreshJob?.isActive == true) return
        if (!force && now - lastRemoteRefreshAt < REMOTE_REFRESH_INTERVAL_MS) return
        lastRemoteRefreshAt = now
        remoteRefreshJob = scope.launch {
            runCatching {
                aniListSyncMutex.withLock {
                    val entries = when (service) {
                        AccountService.ANILIST -> {
                            val viewerId = AuthManager.viewerId() ?: AppGraph.repository.viewer()?.id
                                ?: error("Couldn't load your AniList account")
                            val collection = AppGraph.repository.userAnimeList(viewerId)
                                ?: error("Couldn't load your AniList library")
                            collection.lists
                                .flatMap { it.entries }
                                .distinctBy { it.id }
                        }
                        AccountService.MAL -> AppGraph.repository.malAnimeList()
                    }
                    hydrateRemoteLibrary(entries)
                    com.shubh.anililitv.diagnostics.DiagnosticsLog.event(
                        "${service.label} library refreshed statuses=${_remoteStatuses.value.size}",
                    )
                }
            }.onFailure {
                lastRemoteRefreshAt = 0L
                com.shubh.anililitv.diagnostics.DiagnosticsLog.throwable(
                    "${service.label} library refresh failed",
                    it,
                )
            }
        }
    }

    fun clearRemoteLibrary() {
        remoteRefreshJob?.cancel()
        remoteRefreshJob = null
        lastRemoteRefreshAt = 0L
        _remoteStatuses.value = emptyMap()
        dismissedRemoteHistory = emptySet()
        prefs.edit()
            .remove(KEY_REMOTE_STATUSES)
            .remove(KEY_DISMISSED_REMOTE_HISTORY)
            .apply()
        val localOnly = sortHistoryLatestFirst(_history.value.filterNot { it.fromRemote })
        if (localOnly != _history.value) {
            _history.value = localOnly
            persist(KEY_HISTORY, localOnly, HistoryEntry.serializer())
        }
    }


    private fun <T> persist(key: String, list: List<T>, serializer: kotlinx.serialization.KSerializer<T>) {
        val encoded = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer), list)
        prefs.edit().putString(key, encoded).apply()
    }

    private fun persistDismissedRemoteHistory() {
        prefs.edit()
            .putStringSet(KEY_DISMISSED_REMOTE_HISTORY, dismissedRemoteHistory.map(Int::toString).toSet())
            .apply()
    }

    private fun <T> decodeList(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer), raw)
        }.getOrDefault(emptyList())
    }
}

package com.shubh.anililitv.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.rethrowIfCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

enum class HomeTab(val label: String) {
    POPULAR("POPULAR"),
    NEWEST("NEWEST"),
    MOVIES("MOVIES"),
    TOP_RATED("TOP RATED"),
}

data class HomeData(
    val spotlight: List<Media>,
    val newest: List<Media>,
    val popular: List<Media>,
    val movies: List<Media>,
    val topRated: List<Media>,
) {
    fun tab(tab: HomeTab): List<Media> = when (tab) {
        HomeTab.NEWEST -> newest
        HomeTab.POPULAR -> popular
        HomeTab.MOVIES -> movies
        HomeTab.TOP_RATED -> topRated
    }
}

class HomeViewModel : ViewModel() {
    private val repo = AppGraph.repository

    private val _state = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    var selectedTab by mutableStateOf(HomeTab.POPULAR)
        private set

    init { 
        viewModelScope.launch {
            com.shubh.anililitv.data.settings.SettingsStore.slideshowCategory.collectLatest { category ->
                if (_state.value is UiState.Success) {
                    load(force = false)
                }
            }
        }
        load() 
    }

    fun selectTab(tab: HomeTab) { selectedTab = tab }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            DiagnosticsLog.event("Home load start force=$force")
            if (force && _state.value is UiState.Success) _isRefreshing.value = true else _state.value = UiState.Loading
            try {
                val collections = repo.homeCollections(force)
                val isCinematic = com.shubh.anililitv.data.settings.SettingsStore.cinematicSlideshow.value
                val spotlight = kotlinx.coroutines.coroutineScope {
                    val category = com.shubh.anililitv.data.settings.SettingsStore.slideshowCategory.value
                    val baseList = when (category) {
                        com.shubh.anililitv.data.settings.SlideshowCategory.POPULAR_THIS_SEASON -> collections.popular
                        com.shubh.anililitv.data.settings.SlideshowCategory.LATEST_RELEASES -> collections.newest
                        com.shubh.anililitv.data.settings.SlideshowCategory.TOP_RATED -> collections.topRated
                        com.shubh.anililitv.data.settings.SlideshowCategory.TRENDING_NOW -> collections.spotlight
                    }.filterNot {
                        val format = it.format ?: ""
                        format.equals("MOVIE", ignoreCase = true) ||
                        format.equals("SPECIAL", ignoreCase = true) ||
                        format.equals("OVA", ignoreCase = true) ||
                        format.equals("ONA", ignoreCase = true)
                    }
                    baseList.map { item: com.shubh.anililitv.data.model.Media ->
                        async(kotlinx.coroutines.Dispatchers.IO) {
                            var meta: com.shubh.anililitv.data.remote.AniZipClient.AniZipMetadata? = null
                            try {
                                meta = runCatching { repo.getMetadata(item.id) }.getOrNull()
                            } catch (e: Exception) {
                            }
                            meta
                        }
                    }.awaitAll().mapIndexedNotNull { index, meta ->
                        val item = baseList[index]
                        if (meta != null) {
                            item.copy(
                                bannerImage = meta.backdropUrl ?: item.bannerImage,
                                logoUrl = meta.logoUrl
                            )
                        } else {
                            item
                        }
                    }
                }
                
                val data = HomeData(
                    spotlight = spotlight,
                    newest = collections.newest,
                    popular = collections.popular,
                    movies = collections.movies,
                    topRated = collections.topRated,
                )
                DiagnosticsLog.event(
                    "Home load success spotlight=${data.spotlight.size} newest=${data.newest.size} " +
                        "popular=${data.popular.size} movies=${data.movies.size} topRated=${data.topRated.size}",
                )
                _state.value = UiState.Success(data)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                DiagnosticsLog.throwable("Home load failed", e)
                _state.value = UiState.Error(e.message ?: "Failed to load home")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refresh() = load(force = true)
}
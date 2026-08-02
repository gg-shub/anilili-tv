package com.shubh.anililitv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.data.SERIES_AIRING_ORDER
import com.shubh.anililitv.data.seasonNeighbors
import com.shubh.anililitv.data.model.Category
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.data.remote.KonohaEpisode
import java.time.LocalDate
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.ui.UiState
import com.shubh.anililitv.ui.rethrowIfCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import org.json.JSONObject
import java.net.URL

data class DetailData(
    val info: Media,
    val episodes: List<EpisodeItem>,
    val preferredCategory: Category,
    val series: List<Media> = listOf(info),
    val seriesLoading: Boolean = true,
    val selectedSeasonId: Int = info.id,
    val seasonEpisodesLoading: Boolean = false,
    val aniZipMetadata: com.shubh.anililitv.data.remote.AniZipClient.AniZipMetadata? = null,
) {
    val seasons: List<Media>
        get() = series.filter { it.id == info.id || it.format in SEASON_FORMATS }
}

private val SEASON_FORMATS = setOf("TV", "TV_SHORT", "ONA")

class DetailViewModel : ViewModel() {
    private val repo = AppGraph.repository

    private val _state = MutableStateFlow<UiState<DetailData>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private var loadedId: Int? = null

    fun load(id: Int, force: Boolean = false) {
        if (!force && loadedId == id && _state.value is UiState.Success) return
        loadedId = id
        viewModelScope.launch {
            val cachedData = repo.detailCache[id]
            if (force && _state.value is UiState.Success) {
                _isRefreshing.value = true
            } else if (cachedData != null && !force) {
                _state.value = UiState.Success(cachedData)
                _isRefreshing.value = true
            } else {
                _state.value = UiState.Loading
            }
            try {
                SettingsStore.awaitLoaded()
                
                val currentData = (_state.value as? UiState.Success)?.data
                val isBackgroundRefresh = force && currentData != null
                val targetId = if (isBackgroundRefresh) currentData!!.selectedSeasonId else id
                
                val info = repo.animeInfo(targetId, force = force) ?: error("Anime not found")
                val seededSeries = (listOf(info) + info.seasonNeighbors())
                    .distinctBy(Media::id)
                    .sortedWith(SERIES_AIRING_ORDER)
                
                val initial = if (isBackgroundRefresh) {
                    val lockedInfo = info.copy(
                        title = currentData!!.info.title,
                        description = currentData.info.description,
                        startDate = currentData.info.startDate,
                        coverImage = currentData.info.coverImage
                    )
                    currentData.copy(
                        info = lockedInfo,
                        episodes = anilistEpisodeCatalog(lockedInfo),
                        series = seededSeries,
                        selectedSeasonId = targetId,
                    )
                } else {
                    DetailData(
                        info = info,
                        episodes = anilistEpisodeCatalog(info),
                        preferredCategory = if (SettingsStore.preferDub.value) Category.DUB else Category.SUB,
                        series = seededSeries,
                        selectedSeasonId = targetId,
                    )
                }
                _state.value = UiState.Success(initial)

                val seriesDeferred = viewModelScope.async { runCatching { repo.animeSeries(info) }.getOrDefault(seededSeries) }
                enrichMetadata(targetId)
                val series = seriesDeferred.await()
                
                if (loadedId == id) {
                    val current = (_state.value as? UiState.Success)?.data ?: initial
                    val finalData = current.copy(
                        info = current.info.copy(
                            title = initial.info.title,
                            coverImage = initial.info.coverImage,
                            description = initial.info.description,
                            averageScore = initial.info.averageScore,
                            startDate = initial.info.startDate
                        ),
                        series = series, 
                        seriesLoading = false
                    )
                    _state.value = UiState.Success(finalData)
                    repo.detailCache[id] = finalData
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _state.value = UiState.Error(e.message ?: "Failed to load")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refresh(id: Int) = load(id, force = true)

    fun selectSeason(seasonId: Int) {
        val current = (_state.value as? UiState.Success)?.data ?: return
        if (current.selectedSeasonId == seasonId) return
        if (seasonId == current.info.id) {
            _state.value = UiState.Success(
                current.copy(
                    selectedSeasonId = seasonId,
                    episodes = anilistEpisodeCatalog(current.info),
                    seasonEpisodesLoading = false,
                ),
            )
            return
        }
        _state.value = UiState.Success(
            current.copy(selectedSeasonId = seasonId, episodes = emptyList(), seasonEpisodesLoading = true),
        )
        viewModelScope.launch {
            val seasonInfo = runCatching { repo.animeInfo(seasonId) }.getOrNull()
                ?: current.series.firstOrNull { it.id == seasonId }
            val episodes = seasonInfo?.let(::anilistEpisodeCatalog).orEmpty()
            val latest = (_state.value as? UiState.Success)?.data ?: return@launch
            if (latest.selectedSeasonId == seasonId) {
                _state.value = UiState.Success(latest.copy(
                    info = seasonInfo ?: latest.info,
                    episodes = episodes
                ))
                enrichMetadata(seasonId)
                
                val finalState = (_state.value as? UiState.Success)?.data ?: return@launch
                if (finalState.selectedSeasonId == seasonId) {
                    _state.value = UiState.Success(finalState.copy(seasonEpisodesLoading = false))
                }
            }
        }
    }

    private suspend fun enrichMetadata(seasonId: Int) {
            val aniZipMeta = runCatching { repo.getMetadata(seasonId) }.getOrNull()
            
            val aniZipEpisodes = withTimeoutOrNull(3000) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val url = "https://api.ani.zip/mappings?anilist_id=$seasonId"
                        val response = URL(url).readText()
                        val json = JSONObject(response)
                        val episodesObj = json.optJSONObject("episodes")
                        val map = mutableMapOf<Int, KonohaEpisode>()
                        if (episodesObj != null) {
                            episodesObj.keys().forEach { key ->
                                val epNum = key.toIntOrNull()
                                if (epNum != null) {
                                    val epObj = episodesObj.optJSONObject(key)
                                    if (epObj != null) {
                                        val thumb = epObj.optString("image", "").takeIf { it.isNotBlank() && it != "null" }
                                        val titleObj = epObj.optJSONObject("title")
                                        val title = titleObj?.optString("en", "")?.takeIf { it.isNotBlank() && it != "null" }
                                        map[epNum] = KonohaEpisode(
                                            number = epNum.toDouble(),
                                            title = title,
                                            still = thumb
                                        )
                                    }
                                }
                            }
                        }
                        map.values.toList().takeIf { it.isNotEmpty() }
                    }.getOrNull()
                }
            }

            val aniZipList = aniZipEpisodes ?: emptyList()
            
            val data = (_state.value as? UiState.Success)?.data ?: return
            if (data.selectedSeasonId != seasonId) return
            
            val posterUrl = data.info.coverImage?.extraLarge ?: data.info.coverImage?.large
            
            val updatedEpisodes = if (aniZipList.isNotEmpty()) {
                mergeEpisodeMetadata(data.episodes, aniZipList, seasonId, posterUrl)
            } else {
                data.episodes.map { ep ->
                    ep.copy(
                        title = "Episode ${if (ep.number % 1.0 == 0.0) ep.number.toInt().toString() else ep.number.toString()}",
                        image = posterUrl
                    )
                }
            }
            
            _state.value = UiState.Success(
                data.copy(
                    aniZipMetadata = aniZipMeta ?: data.aniZipMetadata,
                    episodes = updatedEpisodes
                )
            )
    }
}

internal fun mergeEpisodeMetadata(
    base: List<EpisodeItem>,
    meta: List<KonohaEpisode>,
    animeId: Int,
    fallbackImage: String? = null
): List<EpisodeItem> {
    val byNumber = meta.mapNotNull { ep -> ep.number?.let { it to ep } }.toMap()
    
    val fallbackTitle: (Double) -> String = { num ->
        "Episode ${if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()}"
    }

    if (base.isNotEmpty()) {
        val mergedBase = base.map { episode ->
            val extra = byNumber[episode.number] ?: meta.firstOrNull { it.number?.toInt() == episode.number.toInt() }
            
            val mappedTitle = extra?.title.takeIf { !it.isNullOrBlank() && it != "null" }
            val mappedImage = extra?.still.takeIf { !it.isNullOrBlank() && it != "null" }
            
            episode.copy(
                title = mappedTitle ?: fallbackTitle(episode.number),
                image = mappedImage ?: fallbackImage,
            )
        }
        
        val maxBaseNumber = base.maxOfOrNull { it.number } ?: 0.0
        val today = LocalDate.now()
        val extraEpisodes = meta
            .filter { ep -> (ep.number ?: 0.0) > maxBaseNumber }
            .filter { ep ->
                ep.number != null && ep.number >= 1 &&
                    (ep.air_date == null || runCatching { !LocalDate.parse(ep.air_date.take(10)).isAfter(today) }.getOrDefault(true))
            }
            .sortedBy { it.number }
            .map { ep ->
                EpisodeItem(
                    pipeId = "anilist:$animeId:${ep.number?.toInt()}",
                    number = ep.number ?: 0.0,
                    title = ep.title.takeIf { !it.isNullOrBlank() && it != "null" } ?: fallbackTitle(ep.number ?: 0.0),
                    image = ep.still.takeIf { !it.isNullOrBlank() && it != "null" } ?: fallbackImage,
                    filler = false,
                )
            }
            
        return mergedBase + extraEpisodes
    }
    
    val today = LocalDate.now()
    return meta
        .filter { ep ->
            ep.number != null && ep.number >= 1 &&
                (ep.air_date == null || runCatching { !LocalDate.parse(ep.air_date.take(10)).isAfter(today) }.getOrDefault(true))
        }
        .sortedBy { it.number }
        .map { ep ->
            EpisodeItem(
                pipeId = "anilist:$animeId:${ep.number?.toInt()}",
                number = ep.number ?: 0.0,
                title = ep.title.takeIf { !it.isNullOrBlank() && it != "null" } ?: fallbackTitle(ep.number ?: 0.0),
                image = ep.still.takeIf { !it.isNullOrBlank() && it != "null" } ?: fallbackImage,
                filler = false,
            )
        }
}

internal fun anilistEpisodeCatalog(info: Media): List<EpisodeItem> {
    val releasedBeforeNext = info.nextAiringEpisode?.episode?.minus(1)?.coerceAtLeast(0)
    val count = when {
        releasedBeforeNext != null && info.episodes != null -> minOf(releasedBeforeNext, info.episodes)
        releasedBeforeNext != null -> releasedBeforeNext
        else -> info.episodes ?: 0
    }.coerceAtLeast(0)

    return (1..count).map { number ->
        EpisodeItem(
            pipeId = "anilist:${info.id}:$number",
            number = number.toDouble(),
            title = null,
            image = null,
            filler = false,
        )
    }
}

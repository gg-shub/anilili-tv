package com.shubh.anililitv.data

import com.shubh.anililitv.data.model.AiringSchedule
import com.shubh.anililitv.data.model.Category
import com.shubh.anililitv.data.model.EpisodeItem
import com.shubh.anililitv.data.model.EpisodesResult
import com.shubh.anililitv.data.model.DiscoverFilters
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.data.model.MediaPage
import com.shubh.anililitv.data.model.StudioNode
import com.shubh.anililitv.data.model.HomeCollections
import com.shubh.anililitv.data.model.SourcesResult
import com.shubh.anililitv.data.cache.AppCache
import com.shubh.anililitv.data.remote.searchHanimeCatalogue
import com.shubh.anililitv.data.auth.AccountService
import com.shubh.anililitv.data.auth.AuthManager
import com.shubh.anililitv.data.model.DiscoverOptions
import com.shubh.anililitv.data.model.SkipTimes
import com.shubh.anililitv.data.model.AnimeStat
import com.shubh.anililitv.data.model.MediaListEntry
import com.shubh.anililitv.data.model.UserAvatar
import com.shubh.anililitv.data.model.Viewer
import com.shubh.anililitv.data.model.ViewerStatistics
import com.shubh.anililitv.data.remote.AniListClient
import com.shubh.anililitv.data.remote.AniSkipClient
import com.shubh.anililitv.data.remote.AnivexaClient
import com.shubh.anililitv.data.remote.JikanClient
import com.shubh.anililitv.data.remote.KonohaClient
import com.shubh.anililitv.data.remote.KonohaEpisode
import com.shubh.anililitv.data.remote.MalClient
import com.shubh.anililitv.data.remote.MediaListProgressSnapshot
import com.shubh.anililitv.data.remote.MediaListProgressUpdate
import com.shubh.anililitv.data.remote.PipeClient
import com.shubh.anililitv.data.remote.planMediaListProgressUpdate
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.data.settings.DEFAULT_PREFERRED_PROVIDER
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

internal fun providerAttemptOrder(
    preferred: String,
    providerNames: List<String>,
    fallbacks: List<String> = emptyList(),
): List<String> {
    val chosen = buildList {
        add(preferred)
        fallbacks.forEach { fallback ->
            val name = fallback.trim().lowercase()
            if (name.isNotBlank() && name != DEFAULT_PREFERRED_PROVIDER && name !in this) add(name)
        }
    }
    val available = (chosen + providerNames.sortedBy { ProviderCatalog.sortKey(it) }).distinct()
    val preferredSource = ProviderCatalog.sourceOf(preferred)
    val rest = available.filterNot { it in chosen }
    val sameSource = rest.filter { ProviderCatalog.sourceOf(it) == preferredSource }
    val otherSource = rest.filter { ProviderCatalog.sourceOf(it) != preferredSource }

    return buildList {
        addAll(chosen)
        repeat(maxOf(sameSource.size, otherSource.size)) { index ->
            otherSource.getOrNull(index)?.let(::add)
            sameSource.getOrNull(index)?.let(::add)
        }
    }
}

internal data class ProviderSourceCandidate(
    val provider: String,
    val pipeId: String,
)

internal data class ProviderSourceResult(
    val sources: SourcesResult,
    val provider: String,
)

internal data class ProviderCandidateResolution(
    val resolved: ProviderSourceResult?,
    val unavailableProviders: Set<String>,
)

internal suspend fun resolveProviderCandidates(
    candidates: List<ProviderSourceCandidate>,
    excludedProviders: Set<String>,
    maxAttempts: Int,
    attemptTimeoutMs: Long,
    onAttempt: (String) -> Unit = {},
    onFailure: (String, Exception) -> Unit = { _, _ -> },
    onTimeout: (String) -> Unit = {},
    onEmpty: (String) -> Unit = {},
    load: suspend (ProviderSourceCandidate) -> SourcesResult,
): ProviderCandidateResolution {
    require(maxAttempts >= 0) { "maxAttempts must not be negative" }
    require(attemptTimeoutMs > 0L) { "attemptTimeoutMs must be positive" }

    val unavailable = linkedSetOf<String>()
    var attempts = 0
    for (candidate in candidates) {
        if (attempts >= maxAttempts) break
        if (candidate.provider in excludedProviders) continue
        attempts++
        onAttempt(candidate.provider)

        val result = try {
            withTimeoutOrNull(attemptTimeoutMs) { load(candidate) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unavailable += candidate.provider
            onFailure(candidate.provider, e)
            continue
        }
        if (result == null) {
            unavailable += candidate.provider
            onTimeout(candidate.provider)
            continue
        }

        if (result.streams.isNotEmpty()) {
            return ProviderCandidateResolution(
                resolved = ProviderSourceResult(result, candidate.provider),
                unavailableProviders = unavailable,
            )
        }
        unavailable += candidate.provider
        onEmpty(candidate.provider)
    }
    return ProviderCandidateResolution(resolved = null, unavailableProviders = unavailable)
}

class MiruroRepository(
    private val aniList: AniListClient,
    private val pipe: PipeClient,
    private val anivexa: AnivexaClient,
    private val jikan: JikanClient,
    private val aniSkip: AniSkipClient,
    private val mal: MalClient,
    private val konoha: KonohaClient,
    private val cache: AppCache,
) {
    val detailCache = java.util.concurrent.ConcurrentHashMap<Int, com.shubh.anililitv.ui.detail.DetailData>()

    private val hideAdult: Boolean get() = SettingsStore.hideAdultContent.value

    private val seriesFetchGate = Semaphore(SERIES_FETCH_CONCURRENCY)

    suspend fun homeCollections(force: Boolean = false): HomeCollections {
        val adultHidden = hideAdult
        val collections = cache.getOrFetch(
            key = "home:v3:${if (adultHidden) "sfw" else "all"}",
            serializer = HomeCollections.serializer(),
            ttlMs = HOME_TTL,
            forceRefresh = force,
        ) { aniList.homeCollections(adultHidden) }
        if (!adultHidden) return collections
        return collections.copy(
            spotlight = collections.spotlight.filterNot { it.isAdult },
            newest = collections.newest.filterNot { it.isAdult },
            popular = collections.popular.filterNot { it.isAdult },
            movies = collections.movies.filterNot { it.isAdult },
            topRated = collections.topRated.filterNot { it.isAdult },
        )
    }

    suspend fun trending(page: Int = 1, force: Boolean = false): MediaPage = mediaPage("trending:$page", COLLECTION_TTL, force) {
        aniList.collection("TRENDING_DESC", page = page, perPage = 30, hideAdult = hideAdult)
    }
    suspend fun popular(page: Int = 1, force: Boolean = false): MediaPage = mediaPage("popular:$page", COLLECTION_TTL, force) {
        aniList.collection("POPULARITY_DESC", page = page, perPage = 30, hideAdult = hideAdult)
    }
    suspend fun topRated(page: Int = 1, force: Boolean = false): MediaPage = mediaPage("top:$page", COLLECTION_TTL, force) {
        aniList.collection("SCORE_DESC", page = page, perPage = 30, hideAdult = hideAdult)
    }
    suspend fun movies(page: Int = 1, force: Boolean = false): MediaPage = mediaPage("movies:$page", COLLECTION_TTL, force) {
        aniList.collection("POPULARITY_DESC", format = "MOVIE", page = page, perPage = 30, hideAdult = hideAdult)
    }
    suspend fun recentlyReleased(page: Int = 1, force: Boolean = false): MediaPage =
        mediaPage("recent:$page", AIRING_TTL, force) {
            aniList.collection("START_DATE_DESC", status = "RELEASING", page = page, perPage = 30, hideAdult = hideAdult)
        }

    suspend fun airing(page: Int = 1, force: Boolean = false): MediaPage =
        mediaPage("airing:$page", AIRING_TTL, force) {
            aniList.collection("POPULARITY_DESC", status = "RELEASING", page = page, perPage = 40, hideAdult = hideAdult)
        }

    suspend fun schedule(dayOffset: Int, force: Boolean = false): List<AiringSchedule> {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone).plusDays(dayOffset.toLong())
        val start = day.atStartOfDay(zone).toEpochSecond()
        val end = day.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val schedules = cache.getOrFetch(
            key = "schedule:$day",
            serializer = ListSerializer(AiringSchedule.serializer()),
            ttlMs = SCHEDULE_TTL,
            forceRefresh = force,
        ) { aniList.airingSchedule(start, end) }
        return if (hideAdult) schedules.filterNot { it.media?.isAdult == true } else schedules
    }

    suspend fun search(query: String, page: Int = 1, force: Boolean = false): MediaPage {
        val remote = mediaPage("search:${query.trim().lowercase()}:$page", SEARCH_TTL, force) {
            aniList.search(query, page, hideAdult = hideAdult)
        }
        return remote.copy(items = withHanimeResults(query, remote.items, page))
    }

    suspend fun warmHanimeCatalogue() {
        if (hideAdult) return
        anivexa.refreshHanimeCatalogue()
    }

    private suspend fun withHanimeResults(query: String, remote: List<Media>, page: Int): List<Media> {
        if (hideAdult || page != 1 || query.isBlank()) return remote
        val catalogue = runCatching { anivexa.hanimeCatalogue() }.getOrElse {
            DiagnosticsLog.throwable("Hanime catalogue unavailable for search", it)
            return remote
        }
        val hits = searchHanimeCatalogue(query, catalogue)
        if (hits.isEmpty()) return remote
        DiagnosticsLog.event("Hanime search hits=${hits.size} query=${query.take(40)}")
        return (remote + hits).distinctBy { it.id }
    }

    suspend fun discover(filters: DiscoverFilters, page: Int = 1, force: Boolean = false): MediaPage =
        mediaPage("discover:${filters.cacheKey()}:$page", COLLECTION_TTL, force) { aniList.discover(filters, page, hideAdult = hideAdult) }

    suspend fun searchStudios(query: String): List<StudioNode> = aniList.searchStudios(query)

    suspend fun discoverOptions(): DiscoverOptions {
        val adultHidden = hideAdult
        return cache.getOrFetch(
            key = "discover-options:${if (adultHidden) "sfw" else "all"}",
            serializer = DiscoverOptions.serializer(),
            ttlMs = OPTIONS_TTL,
        ) { aniList.discoverOptions(adultHidden) }
    }

    suspend fun viewer() = aniList.viewer()
    suspend fun mediaByMalIds(malIds: List<Int>) = aniList.mediaByMalIds(malIds)
    suspend fun notifications(markAllRead: Boolean = false) = aniList.notifications(markAllRead)
    suspend fun favouriteAnime() = aniList.favouriteAnime()
    suspend fun userAnimeList(userId: Int) = aniList.userAnimeList(userId)
    suspend fun saveAniListProgress(mediaId: Int, progress: Int, totalEpisodes: Int?) =
        aniList.syncMediaListProgress(mediaId, progress, totalEpisodes)
    suspend fun syncSavedAnime(mediaId: Int, saved: Boolean) = aniList.syncSavedAnime(mediaId, saved)

    suspend fun updateAnimeListStatus(anilistId: Int, status: String) {
        require(status in LIST_STATUSES) { "Unsupported anime list status: $status" }
        val service = AccountService.active ?: error("Sign in to AniList or MyAnimeList first")
        when (service) {
            AccountService.ANILIST -> aniList.updateMediaListStatus(anilistId, status)
            AccountService.MAL -> {
                val malId = animeInfo(anilistId)?.idMal?.takeIf { it > 0 }
                    ?: error("This anime could not be matched to MyAnimeList")
                mal.updateListStatus(
                    malId,
                    status = MalClient.malStatus(status),
                    isRewatching = status == "REPEATING",
                )
            }
        }
        DiagnosticsLog.event("${service.label} status moved id=$anilistId status=$status")
    }

    suspend fun syncSavedAnime(mediaIds: Collection<Int>) {
        val viewerId = AuthManager.viewerId() ?: aniList.viewer()?.id ?: return
        aniList.syncSavedAnime(mediaIds, viewerId)
    }


    suspend fun malViewer(): Viewer {
        val user = mal.viewer()
        val stats = user.animeStatistics
        return Viewer(
            id = user.id,
            name = user.name,
            avatar = UserAvatar(large = user.picture),
            bannerImage = null,
            createdAt = user.joinedAt?.let { raw ->
                runCatching { java.time.OffsetDateTime.parse(raw).toEpochSecond() }.getOrNull()
            },
            statistics = ViewerStatistics(
                anime = AnimeStat(
                    count = stats?.numItems ?: 0,
                    minutesWatched = ((stats?.numDaysWatched ?: 0.0) * 1440).toLong(),
                    meanScore = stats?.meanScore ?: 0.0,
                ),
            ),
        )
    }

    suspend fun malAnimeList(): List<MediaListEntry> = coroutineScope {
        val entries = mal.animeList()
        val malIds = entries.map { it.malId }.distinct()
        val mapGate = Semaphore(10)

        val deferredMappings = malIds.map { malId ->
            async {
                mapGate.withPermit {
                    val anilistId = runCatching { konoha.resolveAnilistIdFromMal(malId) }.getOrNull()
                    malId to anilistId
                }
            }
        }
        val resolvedMap = deferredMappings.awaitAll().toMap()
        
        val mappedAnilistIds = resolvedMap.values.filterNotNull().distinct()
        val unmappedMalIds = resolvedMap.filter { it.value == null }.keys.toList()

        val byMalId = mutableMapOf<Int, Media>()

        if (mappedAnilistIds.isNotEmpty()) {
            mappedAnilistIds.sorted().chunked(50).forEach { chunk ->
                cache.getOrFetch(
                    key = "anilist_batch:v1:${chunk.hashCode()}",
                    serializer = ListSerializer(Media.serializer()),
                    ttlMs = MAL_MAP_TTL,
                ) { aniList.mediaByIds(chunk) }
                    .forEach { media ->
                        media.idMal?.let { byMalId[it] = media }
                        
                        val malId = resolvedMap.filter { it.value == media.id }.keys.firstOrNull()
                        if (malId != null) {
                            byMalId[malId] = media
                        }
                    }
            }
        }

        if (unmappedMalIds.isNotEmpty()) {
            unmappedMalIds.sorted().chunked(50).forEach { chunk ->
                cache.getOrFetch(
                    key = "malmap:v1:${chunk.hashCode()}",
                    serializer = ListSerializer(Media.serializer()),
                    ttlMs = MAL_MAP_TTL,
                ) { aniList.mediaByMalIds(chunk) }
                    .forEach { media -> media.idMal?.let { byMalId[it] = media } }
            }
        }

        entries.mapNotNull { entry ->
            val media = byMalId[entry.malId] ?: return@mapNotNull null
            MediaListEntry(
                id = entry.malId,
                progress = entry.progress,
                score = entry.score,
                status = entry.status,
                media = media,
            )
        }
    }

    suspend fun malSyncSavedAnime(anilistId: Int, saved: Boolean) {
        val malId = animeInfo(anilistId)?.idMal?.takeIf { it > 0 }
        if (malId == null) {
            DiagnosticsLog.event("MAL saved sync skipped id=$anilistId: no MAL id on AniList")
            return
        }
        val current = mal.listStatus(malId)
        when {
            saved && current == null -> {
                mal.updateListStatus(malId, status = "plan_to_watch")
                DiagnosticsLog.event("MAL saved sync added malId=$malId (id=$anilistId)")
            }
            !saved && current?.status == "plan_to_watch" -> {
                mal.deleteListEntry(malId)
                DiagnosticsLog.event("MAL saved sync removed malId=$malId (id=$anilistId)")
            }
            else -> DiagnosticsLog.event(
                "MAL saved sync no-op malId=$malId (id=$anilistId) saved=$saved currentStatus=${current?.status}",
            )
        }
    }

    suspend fun malSyncSavedAnime(anilistIds: Collection<Int>) {
        if (anilistIds.isEmpty()) return
        val onMal = mal.animeList().map { it.malId }.toSet()
        anilistIds.forEach { id ->
            runCatching {
                val malId = animeInfo(id)?.idMal?.takeIf { it > 0 }
                when {
                    malId == null -> DiagnosticsLog.event("MAL saved sync skipped id=$id: no MAL id on AniList")
                    malId !in onMal -> {
                        mal.updateListStatus(malId, status = "plan_to_watch")
                        DiagnosticsLog.event("MAL saved sync added malId=$malId (id=$id)")
                    }
                }
            }.onFailure { DiagnosticsLog.throwable("MAL saved sync failed id=$id", it) }
        }
    }

    suspend fun saveMalProgress(
        anilistId: Int,
        progress: Int,
        totalEpisodes: Int?,
    ): MediaListProgressUpdate? {
        val media = animeInfo(anilistId)
        val malId = media?.idMal?.takeIf { it > 0 } ?: return null
        val current = mal.listStatus(malId)?.let {
            MediaListProgressSnapshot(id = malId, status = MalClient.anilistStatus(it), progress = it.numEpisodesWatched)
        }
        val update = planMediaListProgressUpdate(current, progress, totalEpisodes ?: media.episodes) ?: return null
        mal.updateListStatus(malId, progress = update.progress, status = update.status?.let(MalClient::malStatus))
        return update
    }

    suspend fun fillerEpisodes(malId: Int?): Set<Int> {
        if (malId == null || malId <= 0) return emptySet()
        return cache.getOrFetch(
            key = "filler:$malId",
            serializer = SetSerializer(Int.serializer()),
            ttlMs = OPTIONS_TTL,
        ) { withContext(Dispatchers.IO) { jikan.fillerEpisodes(malId) } }
    }

    suspend fun skipTimes(anilistId: Int, episode: Double): SkipTimes? {
        if (episode % 1.0 != 0.0 || episode < 1) return null
        val malId = animeInfo(anilistId)?.idMal?.takeIf { it > 0 } ?: return null
        return cache.getOrFetch(
            key = "aniskip:$malId:${episode.toInt()}",
            serializer = SkipTimes.serializer().nullable,
            ttlMs = OPTIONS_TTL,
        ) { withContext(Dispatchers.IO) { runCatching { aniSkip.skipTimes(malId, episode.toInt()) }.getOrNull() } }
    }

    suspend fun getMetadata(anilistId: Int): com.shubh.anililitv.data.remote.AniZipClient.AniZipMetadata? = konoha.getMetadata(anilistId)

    suspend fun animeInfo(id: Int, force: Boolean = false): Media? = cache.getOrFetch(
        key = "anime:v4:$id",
        serializer = Media.serializer().nullable,
        ttlMs = INFO_TTL,
        forceRefresh = force,
    ) { aniList.animeInfo(id) }

    suspend fun konohaEpisodes(anilistId: Int): List<KonohaEpisode> {
        if (anilistId < 0) return emptyList()
        if (animeInfo(anilistId)?.isAdult == true) {
            DiagnosticsLog.event("Konoha skipped for adult title id=$anilistId (TMDB has no hentai)")
            return emptyList()
        }
        return runCatching { konoha.episodes(anilistId) }
            .onFailure { DiagnosticsLog.throwable("Konoha episodes failed id=$anilistId", it) }
            .getOrDefault(emptyList())
    }

    suspend fun animeSeries(root: Media): List<Media> {
        val idsSerializer = ListSerializer(Int.serializer())
        cache.getIfFresh("$SERIES_KEY_PREFIX${root.id}", idsSerializer)?.let { ids ->
            val members = ids.mapNotNull { id ->
                if (id == root.id) root else runCatching { animeInfo(id) }.getOrNull()
            }
            if (members.isNotEmpty()) {
                val withRoot = if (members.none { it.id == root.id }) members + root else members
                return withRoot.sortedWith(SERIES_AIRING_ORDER)
            }
        }

        val chain = walkSeriesChain(root)
        val payload = kotlinx.serialization.json.Json.encodeToString(idsSerializer, chain.map(Media::id))
        cache.putBatch(chain.associate { "$SERIES_KEY_PREFIX${it.id}" to payload }, INFO_TTL)
        return chain
    }

    private suspend fun walkSeriesChain(root: Media): List<Media> = coroutineScope {
        val found = linkedMapOf(root.id to root)
        val expanded = mutableSetOf<Int>()
        var frontier = listOf(root)
        var depth = 0

        while (frontier.isNotEmpty() && found.size < MAX_SERIES_ENTRIES && depth < MAX_SERIES_DEPTH) {
            val batch = frontier
                .filter { expanded.add(it.id) }
                .take(MAX_SERIES_ENTRIES - found.size + 1)
            if (batch.isEmpty()) break

            val detailed = batch.map { media ->
                async {
                    if (media.id == root.id && media.relations.edges.isNotEmpty()) {
                        media
                    } else {
                        seriesFetchGate.withPermit { runCatching { animeInfo(media.id) }.getOrNull() } ?: media
                    }
                }
            }.awaitAll()

            val next = mutableListOf<Media>()
            detailed.forEach { media ->
                found[media.id] = media
                media.seasonNeighbors().forEach { neighbor ->
                    if (hideAdult && neighbor.isAdult) return@forEach
                    if (neighbor.id !in found && found.size < MAX_SERIES_ENTRIES) {
                        found[neighbor.id] = neighbor
                    }
                    if (neighbor.id !in expanded) next += neighbor
                }
            }
            frontier = next.distinctBy(Media::id)
            depth++
        }

        found.values.sortedWith(SERIES_AIRING_ORDER)
    }

    suspend fun miruroEpisodes(anilistId: Int, force: Boolean = false): EpisodesResult = cache.getOrFetch(
        key = "episodes:v3:miruro:$anilistId",
        serializer = EpisodesResult.serializer(),
        ttlMs = EPISODES_TTL,
        forceRefresh = force,
    ) {
        pipe.getEpisodes(anilistId).also {
            check(!it.isEmpty) { "Miruro returned no episode providers" }
        }
    }.withFillerMarks(anilistId)

    suspend fun anivexaEpisodes(anilistId: Int, force: Boolean = false): EpisodesResult {
        val seed = runCatching { animeInfo(anilistId) }.getOrNull()
        val expected = ProviderCatalog.anivexaProvidersFor(hideAdult).size
        return cache.getOrFetch(
            key = "episodes:v4:anivexa:$anilistId",
            serializer = EpisodesResult.serializer(),
            ttlMs = EPISODES_TTL,
            forceRefresh = force,
            cacheIf = { it.providers.size >= (expected * MIN_CATALOG_COVERAGE).toInt() },
        ) {
            anivexa.getEpisodes(anilistId, seed).also {
                check(!it.isEmpty) { "Anivexa returned no episode providers" }
            }
        }.withFillerMarks(anilistId)
    }

    private suspend fun EpisodesResult.withFillerMarks(anilistId: Int): EpisodesResult {
        if (isEmpty) return this
        val fillers = kotlinx.coroutines.withTimeoutOrNull(FILLER_FETCH_TIMEOUT_MS) {
            runCatching { fillerEpisodes(animeInfo(anilistId)?.idMal) }.getOrDefault(emptySet())
        } ?: return this
        if (fillers.isEmpty()) return this
        fun mark(episodes: List<EpisodeItem>): List<EpisodeItem> = episodes.map { episode ->
            val isFiller = episode.number % 1.0 == 0.0 && episode.number.toInt() in fillers
            if (isFiller && !episode.filler) episode.copy(filler = true) else episode
        }
        return EpisodesResult(providers.map { it.copy(sub = mark(it.sub), dub = mark(it.dub)) })
    }

    suspend fun fastAnivexaEpisodes(anilistId: Int, extraProviders: Set<String> = emptySet()): EpisodesResult {
        val providers = (
            ProviderCatalog.fastAnivexaProviders +
                extraProviders.filter { it in ProviderCatalog.anivexaProviders }
            ).distinct()
        val seed = runCatching { animeInfo(anilistId) }.getOrNull()
        return cache.getOrFetch(
            key = "episodes:v4:anivexa-fast:${providers.sorted().joinToString(",")}:$anilistId",
            serializer = EpisodesResult.serializer(),
            ttlMs = EPISODES_TTL,
        ) {
            anivexa.getEpisodes(
                anilistId = anilistId,
                seedMedia = seed,
                providers = providers,
                providerTimeoutMs = FAST_CATALOG_PROVIDER_TIMEOUT_MS,
            ).also {
                check(!it.isEmpty) { "Fast providers returned no episodes" }
            }
        }.withFillerMarks(anilistId)
    }

    suspend fun episodes(anilistId: Int): EpisodesResult = coroutineScope {
        val miruro = async {
            runCatching { miruroEpisodes(anilistId) }
                .onFailure { DiagnosticsLog.throwable("Miruro episodes failed id=$anilistId", it) }
                .getOrDefault(EpisodesResult(emptyList()))
        }
        val anivexa = async {
            runCatching { anivexaEpisodes(anilistId) }
                .onFailure { DiagnosticsLog.throwable("Anivexa episodes failed id=$anilistId", it) }
                .getOrDefault(EpisodesResult(emptyList()))
        }
        mergeProviders(miruro.await(), anivexa.await())
    }

    fun mergeProviders(a: EpisodesResult, b: EpisodesResult): EpisodesResult {
        val replaced = b.providerNames.toSet()
        return EpisodesResult(
            (a.providers.filterNot { it.name in replaced } + b.providers)
                .sortedBy { ProviderCatalog.sortKey(it.name) },
        )
    }

    suspend fun sources(
        pipeId: String,
        provider: String,
        category: Category,
        anilistId: Int,
    ): SourcesResult = when (ProviderCatalog.sourceOf(provider)) {
        ProviderCatalog.Source.ANIVEXA -> anivexa.getSources(pipeId, animeInfo(anilistId))
        ProviderCatalog.Source.MIRURO -> pipe.getSources(pipeId, provider, category, anilistId)
    }

    data class ResolvedSources(val sources: SourcesResult, val provider: String)

    data class SourceResolution(
        val resolved: ResolvedSources?,
        val unavailableProviders: Set<String>,
    )

    suspend fun resolveSources(
        anilistId: Int,
        number: Double,
        preferred: String,
        category: Category,
        episodes: EpisodesResult,
        excludedProviders: Set<String> = emptySet(),
        maxAttempts: Int = 5,
    ): SourceResolution {
        val ordered = providerAttemptOrder(
            preferred = preferred,
            providerNames = episodes.providerNames,
            fallbacks = SettingsStore.serverPriority.value.drop(1),
        )
        val candidates = ordered.mapNotNull { name ->
            val provider = episodes.provider(name) ?: return@mapNotNull null
            val episode = provider.episodes(category).firstOrNull { it.number == number }
                ?: return@mapNotNull null
            ProviderSourceCandidate(name, episode.pipeId)
        }
        val resolution = resolveProviderCandidates(
            candidates = candidates,
            excludedProviders = excludedProviders,
            maxAttempts = maxAttempts,
            attemptTimeoutMs = PROVIDER_SOURCE_ATTEMPT_TIMEOUT_MS,
            onAttempt = { provider ->
                DiagnosticsLog.event(
                    "Source resolve attempt provider=$provider id=$anilistId episode=$number " +
                        "timeoutMs=$PROVIDER_SOURCE_ATTEMPT_TIMEOUT_MS",
                )
            },
            onFailure = { provider, error ->
                DiagnosticsLog.throwable(
                    "Source resolve failed provider=$provider id=$anilistId episode=$number",
                    error,
                )
            },
            onTimeout = { provider ->
                DiagnosticsLog.event(
                    "Source resolve timeout provider=$provider id=$anilistId episode=$number " +
                        "afterMs=$PROVIDER_SOURCE_ATTEMPT_TIMEOUT_MS",
                )
            },
            onEmpty = { provider ->
                DiagnosticsLog.event(
                    "Source resolve empty provider=$provider id=$anilistId episode=$number",
                )
            },
        ) { candidate ->
            sources(candidate.pipeId, candidate.provider, category, anilistId)
        }
        return SourceResolution(
            resolved = resolution.resolved?.let { ResolvedSources(it.sources, it.provider) },
            unavailableProviders = resolution.unavailableProviders,
        )
    }

    private suspend fun mediaPage(
        key: String,
        ttlMs: Long,
        force: Boolean = false,
        fetch: suspend () -> MediaPage,
    ): MediaPage {
        val hideAdult = hideAdult
        val page = cache.getOrFetch(
            key = "media:v2:$key:${if (hideAdult) "sfw" else "all"}",
            serializer = MediaPage.serializer(),
            ttlMs = ttlMs,
            forceRefresh = force,
            fetch = fetch,
        )
        return if (hideAdult) page.copy(items = page.items.filterNot { it.isAdult }) else page
    }

    private fun DiscoverFilters.cacheKey(): String = listOf(
        query.trim().lowercase(),
        genres.sorted().joinToString(","),
        tags.sorted().joinToString(","),
        studioId.orEmpty(),
        year.orEmpty(),
        status.orEmpty(),
        format.orEmpty(),
        minimumScore.orEmpty(),
        sort,
    ).joinToString("|")

    private fun Any?.orEmpty(): String = this?.toString() ?: ""

    private companion object {
        val LIST_STATUSES =
            setOf("CURRENT", "REPEATING", "PLANNING", "PAUSED", "COMPLETED", "DROPPED")
        const val MAX_SERIES_ENTRIES = 16
        const val MAX_SERIES_DEPTH = 8
        const val SERIES_FETCH_CONCURRENCY = 2
        const val SCHEDULE_TTL = 15L * 60 * 1000
        const val HOME_TTL = 30L * 60 * 1000
        const val MAL_MAP_TTL = 24L * 60 * 60 * 1000
        const val SEARCH_TTL = 30L * 60 * 1000
        const val AIRING_TTL = 30L * 60 * 1000
        const val COLLECTION_TTL = 4L * 60 * 60 * 1000
        const val EPISODES_TTL = 2L * 60 * 60 * 1000

        const val MIN_CATALOG_COVERAGE = 0.5
        const val FILLER_FETCH_TIMEOUT_MS = 3_500L
        const val FAST_CATALOG_PROVIDER_TIMEOUT_MS = 6_000L
        const val PROVIDER_SOURCE_ATTEMPT_TIMEOUT_MS = 8_000L
        const val INFO_TTL = 24L * 60 * 60 * 1000
        const val OPTIONS_TTL = 7L * 24 * 60 * 60 * 1000
        const val SERIES_KEY_PREFIX = "series:v1:"
    }
}

internal val SERIES_AIRING_ORDER: Comparator<Media> = compareBy(
    { it.startDate?.year ?: it.seasonYear ?: Int.MAX_VALUE },
    { it.startDate?.month ?: Int.MAX_VALUE },
    { it.startDate?.day ?: Int.MAX_VALUE },
    { it.id },
)

internal fun Media.seasonNeighbors(): List<Media> = relations.edges
    .asSequence()
    .filter { it.relationType == "PREQUEL" || it.relationType == "SEQUEL" }
    .mapNotNull { it.node }
    .distinctBy(Media::id)
    .toList()

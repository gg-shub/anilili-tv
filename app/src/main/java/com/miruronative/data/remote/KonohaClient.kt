package com.miruronative.data.remote

import android.content.Context
import com.miruronative.data.cache.AppCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
internal data class CachedMapping(
    val anilistId: Int?,
)

@Serializable
private data class KonohaMappingResponse(
    val anilist_id: Int,
)

/** One episode from Konoha's per-anime `episodes.json` (TMDB-backed titles and stills). */
@Serializable
data class KonohaEpisode(
    val number: Double? = null,
    val title: String? = null,
    val overview: String? = null,
    val air_date: String? = null,
    val still: String? = null,
    val runtime: Int? = null,
)

class KonohaClient(
    private val context: Context,
    private val client: OkHttpClient,
    private val json: kotlinx.serialization.json.Json,
    private val cache: AppCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    @Volatile
    private var malToAnilistMap: Map<Int, Int>? = null

    init {
        scope.launch {
            loadAssetMap()
        }
    }

    /**
     * Resolves an AniList ID from a MyAnimeList ID.
     * Checks the pre-loaded asset map first (instant), then falls back to AppCache/CDN.
     */
    suspend fun resolveAnilistIdFromMal(malId: Int): Int? {
        val assetMap = malToAnilistMap ?: loadAssetMap()
        assetMap[malId]?.let { return it }

        return withContext(Dispatchers.IO) {
            val cacheKey = "konohamal:v1:$malId"
            
            val cached = cache.getOrFetch(
                key = cacheKey,
                serializer = CachedMapping.serializer(),
                ttlMs = TTL_HIT_MS,
            ) {
                val shard = malId / 1000
                val url = "https://raw.githubusercontent.com/AlokRepo/Konoha/main/data/mappings/mal/$shard/$malId.json"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                    
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (response.code == 404) {
                            CachedMapping(null)
                        } else if (!response.isSuccessful) {
                            error("Konoha HTTP ${response.code}")
                        } else {
                            val body = response.body?.string().orEmpty()
                            val parsed = json.decodeFromString(KonohaMappingResponse.serializer(), body)
                            CachedMapping(parsed.anilist_id)
                        }
                    }
                }.getOrElse {
                    throw it
                }
            }
            
            cached.anilistId
        }
    }

    /**
     * Episode metadata (titles, thumbnails, synopses) for one AniList id from the Konoha CDN.
     * Zero rate limits and ~CDN latency, so the detail page can show a full episode list
     * instantly. Empty when Konoha doesn't know the title or on any failure.
     */
    suspend fun episodes(anilistId: Int): List<KonohaEpisode> = withContext(Dispatchers.IO) {
        cache.getOrFetch(
            key = "konohaeps:v1:$anilistId",
            serializer = ListSerializer(KonohaEpisode.serializer()),
            ttlMs = EPISODES_TTL_MS,
        ) {
            val shard = anilistId / 1000
            val url = "https://cdn.jsdelivr.net/gh/AlokRepo/Konoha@main/data/anime/$shard/$anilistId/episodes.json"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> emptyList()
                    !response.isSuccessful -> error("Konoha HTTP ${response.code}")
                    else -> json.decodeFromString(
                        ListSerializer(KonohaEpisode.serializer()),
                        response.body?.string().orEmpty(),
                    )
                }
            }
        }
    }

    private fun loadAssetMap(): Map<Int, Int> {
        malToAnilistMap?.let { return it }
        return synchronized(this) {
            malToAnilistMap?.let { return it }
            val map = mutableMapOf<Int, Int>()
            try {
                val inputStream = context.assets.open("id-map.json")
                val rawText = inputStream.bufferedReader().use { it.readText() }
                
                val root = json.parseToJsonElement(rawText).jsonObject
                root.forEach { (anilistIdStr, element) ->
                    val anilistId = anilistIdStr.toIntOrNull() ?: return@forEach
                    val malId = element.jsonObject["mal"]?.jsonPrimitive?.intOrNull ?: return@forEach
                    map[malId] = anilistId
                }
            } catch (e: Exception) {
                com.miruronative.diagnostics.DiagnosticsLog.throwable(
                    "Failed to load id-map.json from assets", e
                )
            }
            malToAnilistMap = map
            map
        }
    }

    private companion object {
        const val TTL_HIT_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        /** Airing shows gain an episode weekly; six hours keeps lists fresh without re-fetching. */
        const val EPISODES_TTL_MS = 6L * 60 * 60 * 1000
    }
}

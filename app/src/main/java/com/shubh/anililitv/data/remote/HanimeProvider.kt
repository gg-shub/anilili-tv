package com.shubh.anililitv.data.remote

import com.shubh.anililitv.data.cache.AppCache
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.data.model.SourcesResult
import com.shubh.anililitv.data.model.StreamItem
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal class HanimeProvider(
    private val context: android.content.Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val cache: AppCache,
) {
    @Volatile private var bundled: List<HanimeVideo>? = null

    suspend fun catalogue(): List<HanimeVideo> =
        cache.getIfFresh(CACHE_KEY, ListSerializer(HanimeVideo.serializer()))
            ?.takeIf { it.isNotEmpty() }
            ?: bundledCatalogue()

    suspend fun refresh(force: Boolean = false) {
        runCatching {
            cache.getOrFetch(
                key = CACHE_KEY,
                serializer = ListSerializer(HanimeVideo.serializer()),
                ttlMs = CATALOGUE_TTL_MS,
                forceRefresh = force,
            ) { download() }
        }.onFailure { DiagnosticsLog.throwable("Hanime catalogue refresh failed", it) }
    }

    private fun bundledCatalogue(): List<HanimeVideo> {
        bundled?.let { return it }
        val parsed = runCatching {
            context.assets.open(BUNDLED_ASSET).use { stream ->
                json.decodeFromString(
                    ListSerializer(HanimeVideo.serializer()),
                    stream.readBytes().decodeToString(),
                )
            }
        }.onFailure { DiagnosticsLog.throwable("Hanime bundled catalogue unreadable", it) }
            .getOrDefault(emptyList())
        DiagnosticsLog.event("Hanime bundled catalogue loaded entries=${parsed.size}")
        bundled = parsed
        return parsed
    }

    suspend fun episodeAvailability(media: Media): EpisodeAvailability {
        val episodes = seriesFor(media)
        if (episodes.isEmpty()) error("hanime has no match for this title")
        return EpisodeAvailability(episodes.map(HanimeVideo::episodeNumber).toSet(), emptySet())
    }

    suspend fun sources(media: Media, episode: Int): SourcesResult {
        val video = seriesFor(media).firstOrNull { it.episodeNumber == episode }
            ?: error("hanime episode $episode is not in the catalogue")
        val hls = HanimeBridge.resolveStream(video.slug)
            ?: error("hanime did not return a stream for ${video.slug}")
        val stream = StreamItem(
            url = hls,
            type = "hls",
            quality = "hanime",
            audio = null,
            referer = "$ORIGIN/",
            isActive = true,
            width = null,
            height = null,
        )
        DiagnosticsLog.event("Hanime resolved slug=${video.slug} episode=$episode")
        return SourcesResult(sortHanimeStreams(listOf(stream)), emptyList(), null, null)
    }

    private suspend fun seriesFor(media: Media): List<HanimeVideo> {
        val catalogue = runCatching { catalogue() }.getOrElse {
            DiagnosticsLog.throwable("Hanime catalogue unavailable", it)
            return emptyList()
        }
        if (isHanimeMediaId(media.id)) {
            val videoId = hanimeVideoId(media.id)
            val anchor = catalogue.firstOrNull { it.id == videoId } ?: return emptyList()
            return catalogue.filter { it.seriesSlug == anchor.seriesSlug }
                .sortedBy(HanimeVideo::episodeNumber)
        }
        return matchHanimeSeries(media, catalogue)?.episodes.orEmpty()
    }

    private suspend fun download(): List<HanimeVideo> {
        val credentials = HanimeBridge.credentials()
            ?: error("hanime credentials unavailable; the resolver WebView is not attached")
        val request = Request.Builder()
            .url(CATALOGUE_URL)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$ORIGIN/")
            .header("Origin", ORIGIN)
            .header("X-Signature-Version", "web2")
            .header("X-Time", credentials.time)
            .header("X-Signature", credentials.signature)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("hanime catalogue HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val parsed = json.decodeFromString(ListSerializer(HanimeVideo.serializer()), body)
            DiagnosticsLog.event("Hanime catalogue downloaded entries=${parsed.size}")
            return parsed
        }
    }

    private companion object {
        const val ORIGIN = "https://hanime.tv"
        const val CATALOGUE_URL = "https://guest.freeanimehentai.net/api/v11/search_hvs"
        const val CACHE_KEY = "hanime-catalogue"
        const val BUNDLED_ASSET = "hanime-catalogue.json"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"

        const val CATALOGUE_TTL_MS = 24L * 60 * 60 * 1000
    }
}

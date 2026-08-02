package com.shubh.anililitv.data.remote

import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.data.model.SourcesResult
import com.shubh.anililitv.data.model.StreamItem
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.util.Base64Compat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

internal class HentaiHavenProvider(private val client: OkHttpClient, private val json: Json) {
    private val slugs = ConcurrentHashMap<Int, String>()

    fun episodeAvailability(media: Media): EpisodeAvailability {
        val slug = seriesSlug(media) ?: error("Hentai Haven has no match for this title")
        val episodes = HentaiHavenParser.episodeNumbers(get("$BASE/watch/$slug/"), slug)
        if (episodes.isEmpty()) error("Hentai Haven listed no episodes for $slug")
        return EpisodeAvailability(episodes, emptySet())
    }

    fun sources(media: Media, episode: Int): SourcesResult {
        val slug = seriesSlug(media) ?: error("Hentai Haven has no match for this title")
        val episodeUrl = "$BASE/watch/$slug/episode-$episode/"
        val data = HentaiHavenParser.playerDataParam(get(episodeUrl))
            ?: error("Hentai Haven episode $episode has no player iframe")

        val playerUrl = "$PLUGIN/player.php?data=$data&lang=en"
        val token = HentaiHavenParser.secureToken(get(playerUrl, referer = episodeUrl))
            ?: error("Hentai Haven player page carried no secure token")
        val config = HentaiHavenParser.decodeSecureToken(token)
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: error("Hentai Haven secure token did not decode")

        val en = config.string("en") ?: error("Hentai Haven token had no payload")
        val iv = config.string("iv") ?: error("Hentai Haven token had no iv")
        val reply = json.parseToJsonElement(postPlayerApi(en, iv, playerUrl)).jsonObject
        val hls = reply["data"]?.jsonObject?.get("sources")?.jsonArray
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.string("src") }
            ?: error("Hentai Haven returned no sources for episode $episode")

        val stream = StreamItem(
            url = hls,
            type = "hls",
            quality = "Hentai Haven",
            audio = null,
            referer = "$BASE/",
            isActive = true,
            width = null,
            height = null,
            headers = authorizationHeaders(reply["authorization"] as? JsonObject),
            avoidCronet = true,
        )
        DiagnosticsLog.event("HentaiHaven resolved slug=$slug episode=$episode")
        return SourcesResult(listOf(stream), emptyList(), null, null)
    }

    private fun authorizationHeaders(auth: JsonObject?): Map<String, String> {
        val token = auth?.string("token") ?: return emptyMap()
        return buildMap {
            put("X-Video-Token", token)
            auth.number("expiration")?.let { put("X-Video-Expiration", it) }
            auth.string("ip")?.let { put("X-Video-Ip", it) }
        }
    }

    private fun seriesSlug(media: Media): String? {
        slugs[media.id]?.let { return it }
        val candidates = aniListTitleCandidates(media)
        if (candidates.isEmpty()) return null
        val found = candidates.firstNotNullOfOrNull { title ->
            val query = HentaiHavenParser.searchQuery(title)
            if (query.isBlank()) return@firstNotNullOfOrNull null
            val html = runCatching { get("$BASE/?s=${query.urlEncoded()}&post_type=wp-manga") }
                .getOrElse { return@firstNotNullOfOrNull null }
            HentaiHavenParser.searchSlugs(html)
                .mapNotNull { slug ->
                    val score = candidates.maxOf { titleSimilarity(it, slugAsTitle(slug)) }
                    if (score >= MATCH_THRESHOLD) slug to score else null
                }
                .maxByOrNull { it.second }
                ?.first
        }
        return found?.also { slugs[media.id] = it }
    }

    private fun postPlayerApi(en: String, iv: String, referer: String): String {
        val body = FormBody.Builder()
            .add("action", "zarat_get_data_player_ajax")
            .add("a", en)
            .add("b", iv)
            .build()
        val request = Request.Builder().url("$PLUGIN/api.php")
            .header("User-Agent", USER_AGENT)
            .header("Referer", referer)
            .header("Origin", BASE)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Hentai Haven player API HTTP ${response.code}")
            return text
        }
    }

    private fun get(url: String, referer: String = "$BASE/"): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", referer)
            .header("Accept", "text/html,application/json,*/*")
            .header("Cookie", "agev=1")
            .get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Hentai Haven HTTP ${response.code}")
            return text
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private fun JsonObject.number(key: String): String? =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull?.toString() ?: it.contentOrNull }

    private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val BASE = "https://hentaihaven.xxx"
        private const val PLUGIN = "$BASE/wp-content/plugins/player-logic"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"

        private const val MATCH_THRESHOLD = 0.85

        fun slugAsTitle(slug: String): String = slug.replace('-', ' ')
    }
}

internal object HentaiHavenParser {
    fun rot13(value: String): String = value.map { char ->
        when {
            char in 'A'..'Z' -> 'A' + (char - 'A' + 13) % 26
            char in 'a'..'z' -> 'a' + (char - 'a' + 13) % 26
            else -> char
        }
    }.joinToString("")

    fun decodeSecureToken(token: String): String? = runCatching {
        var value = token.removePrefix("sha512-").trim()
        repeat(3) { value = String(Base64Compat.decode(rot13(value)), Charsets.ISO_8859_1) }
        value
    }.getOrNull()?.takeIf { it.trimStart().startsWith("{") }

    fun searchQuery(title: String): String = title.trim()
        .split(WHITESPACE)
        .filter(String::isNotBlank)
        .take(4)
        .joinToString(" ")
        .trimEnd(*TRAILING_PUNCTUATION)

    fun searchSlugs(html: String): List<String> = SEARCH_RESULT.findAll(html)
        .map { it.groupValues[1] }
        .filter { it != "feed" }
        .distinct()
        .toList()

    fun episodeNumbers(html: String, slug: String): Set<Int> {
        val scoped = Regex("""/watch/${Regex.escape(slug)}/episode-(\d+)""")
        return scoped.findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
    }

    fun playerDataParam(html: String): String? =
        PLAYER_IFRAME.find(html)?.groupValues?.get(1)?.takeIf(String::isNotBlank)

    fun secureToken(html: String): String? =
        SECURE_TOKEN.find(html)?.groupValues?.get(1)?.takeIf(String::isNotBlank)

    private val WHITESPACE = Regex("""\s+""")
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ':', ';', '!', '?', '-', '…', '\'', '"')
    private val SEARCH_RESULT = Regex("""href="https://hentaihaven\.xxx/watch/([a-z0-9-]+)/"""")
    private val EPISODE_LINK = Regex("""/watch/[a-z0-9-]+/episode-(\d+)""")
    private val PLAYER_IFRAME = Regex("""player\.php\?data=([^"&]+)""")
    private val SECURE_TOKEN = Regex("""name="x-secure-token"\s+content="([^"]+)"""")
}

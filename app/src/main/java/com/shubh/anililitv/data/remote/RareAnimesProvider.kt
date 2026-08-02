package com.shubh.anililitv.data.remote

import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.data.model.SourcesResult
import com.shubh.anililitv.data.model.StreamItem
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

internal class RareAnimesProvider(private val client: OkHttpClient) {
    private data class Catalog(val label: String, val episodes: List<RareAnimesParser.Episode>)

    private val catalogs = boundedMap<Int, Catalog>(64)
    private val misses = boundedMap<Int, Long>(128)

    fun episodeAvailability(media: Media): EpisodeAvailability {
        val episodes = catalog(media).episodes
        return EpisodeAvailability(emptySet(), episodes.map { it.number }.toSet())
    }

    fun sources(media: Media, audio: String, episode: Int): SourcesResult {
        val entry = catalog(media).episodes.firstOrNull { it.number == episode }
            ?: error("RareAnimes episode $episode is not in the catalog")
        val resolvable = entry.languages.mapNotNull { (language, links) ->
            RareAnimesParser.preferred(links)?.let { language to it }
        }.sortedBy { (language, _) -> LANGUAGE_PRIORITY.indexOf(language).takeIf { it >= 0 } ?: LANGUAGE_PRIORITY.size }
        val streams = mapParallel(resolvable, RESOLVE_CONCURRENCY, RESOLVE_BUDGET_MS) { (language, link) ->
            runCatching { resolveStream(link.url, language) }
                .onFailure {
                    DiagnosticsLog.throwable(
                        "RareAnimes resolve failed ep=$episode lang=$language",
                        it,
                    )
                }
                .getOrNull()
        }.filterNotNull()
        if (streams.isEmpty()) error("RareAnimes episode $episode has no playable streams")
        val active = streams.firstOrNull { it.audio.equals("Hindi", ignoreCase = true) } ?: streams.first()
        return SourcesResult(
            streams.map { it.copy(isActive = it === active) }.distinctBy(StreamItem::url),
            emptyList(),
            null,
            null,
        )
    }

    private fun catalog(media: Media): Catalog {
        catalogs[media.id]?.let { return it }
        misses[media.id]?.let { seenAt ->
            if (System.currentTimeMillis() - seenAt < MISS_TTL_MS) {
                error("RareAnimes has no entry for this title")
            }
            misses.remove(media.id)
        }
        val built = build(media)
        if (built == null) {
            misses[media.id] = System.currentTimeMillis()
            error("RareAnimes has no entry for this title")
        }
        catalogs[media.id] = built
        return built
    }

    private fun build(media: Media): Catalog? {
        val candidates = rankedCandidates(media)
        if (candidates.isEmpty()) return null

        val parsed = HashMap<String, List<RareAnimesParser.Episode>>()
        fun episodesOf(candidate: RareAnimesParser.SearchResult): List<RareAnimesParser.Episode> =
            synchronized(parsed) { parsed[candidate.url] } ?: run {
                val episodes = runCatching {
                    RareAnimesParser.parseEpisodes(get(candidate.url, referer = "$BASE/"))
                }.getOrDefault(emptyList())
                synchronized(parsed) { parsed[candidate.url] = episodes }
                episodes
            }

        val single = candidates.take(MAX_PROBE_POSTS)
            .firstNotNullOfOrNull { candidate ->
                episodesOf(candidate).takeIf { it.isNotEmpty() }?.let { Catalog(candidate.title, it) }
            }

        val expected = expectedEpisodeCount(media)
        if (single != null && (expected == null || single.episodes.size >= expected - EPISODE_COUNT_SLACK)) {
            return single
        }

        val series = RareAnimesParser.seriesTitle((single?.label ?: candidates.first().title))
        fun sameSeries(all: List<RareAnimesParser.SearchResult>) = all.filter {
            RareAnimesParser.seriesTitle(it.title).equals(series, ignoreCase = true)
        }
        val deep = sameSeries(candidates + rankedCandidates(media, pages = 2..MAX_SEARCH_PAGES))
        val stitched = stitchSeasons(deep, ::episodesOf, expected)
        if (stitched != null && stitched.episodes.size > (single?.episodes?.size ?: 0)) {
            DiagnosticsLog.event(
                "RareAnimes stitched ${stitched.episodes.size} episodes for id=${media.id} " +
                    "(best single post had ${single?.episodes?.size ?: 0}, AniList expects $expected)",
            )
            return stitched
        }
        return single
    }

    private fun stitchSeasons(
        candidates: List<RareAnimesParser.SearchResult>,
        episodesOf: (RareAnimesParser.SearchResult) -> List<RareAnimesParser.Episode>,
        expected: Int?,
    ): Catalog? {
        val bySeason = candidates
            .mapNotNull { candidate -> RareAnimesParser.seasonNumber(candidate.title)?.let { it to candidate } }
            .distinctBy { it.first }
            .sortedBy { it.first }
        val seasons = mutableListOf<Pair<Int, RareAnimesParser.SearchResult>>()
        bySeason.forEach { entry -> if (entry.first == seasons.size + 1) seasons += entry }
        if (seasons.size < 2) return null
        seasons.subList(minOf(seasons.size, MAX_SEASON_POSTS), seasons.size).clear()

        mapParallel(seasons, FETCH_CONCURRENCY) { (_, candidate) -> episodesOf(candidate) }

        var next = 1
        val absolute = mutableListOf<RareAnimesParser.Episode>()
        seasons.forEach { (_, candidate) ->
            episodesOf(candidate).sortedBy { it.number }.forEach { episode ->
                absolute += episode.copy(number = next++)
            }
        }
        if (absolute.isEmpty()) return null
        if (expected != null && absolute.size < expected - EPISODE_COUNT_SLACK) {
            DiagnosticsLog.event(
                "RareAnimes stitched only ${absolute.size}/$expected episodes; numbering may drift",
            )
        }
        val trimmed = if (expected != null) absolute.take(expected) else absolute
        return Catalog("${seasons.size} season posts", trimmed)
    }

    private fun rankedCandidates(
        media: Media,
        pages: IntRange = 1..1,
    ): List<RareAnimesParser.SearchResult> {
        val titles = listOfNotNull(media.title.english, media.title.romaji, media.title.native)
            .filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) return emptyList()
        val requests = searchQueries(titles).flatMap { query -> pages.map { query to it } }
        val hits = mapParallel(requests, FETCH_CONCURRENCY) { (query, page) ->
            val suffix = if (page > 1) "&paged=$page" else ""
            runCatching {
                RareAnimesParser.parseSearch(get("$BASE/?s=${enc(query)}$suffix", referer = "$BASE/"))
            }.getOrDefault(emptyList())
        }.filterNotNull().flatten().distinctBy { it.url }
        fun score(candidate: RareAnimesParser.SearchResult) =
            titles.maxOf {
                NativeProviderParsers.titleSelectionScore(it, RareAnimesParser.seriesTitle(candidate.title))
            }
        return hits.filter { score(it) >= MIN_TITLE_SCORE }.sortedByDescending(::score)
    }

    private fun searchQueries(titles: List<String>): List<String> = titles
        .flatMap { title -> listOf(title, title.substringBefore(':').trim()) }
        .filter { it.length >= 3 }
        .distinctBy { it.lowercase() }
        .take(MAX_SEARCH_QUERIES)

    private fun expectedEpisodeCount(media: Media): Int? = media.episodes?.takeIf { it > 0 }
        ?: media.nextAiringEpisode?.episode?.minus(1)?.takeIf { it > 0 }

    private fun resolveStream(zipperUrl: String, language: String): StreamItem {
        val multiquality = get(zipperUrl, referer = "$BASE/")
        val embedUrl = RareAnimesParser.embedUrl(multiquality)
            ?: error("codedew page has no argon embed")
        val embed = get(embedUrl, referer = CODEDEW_ORIGIN)
        val blob = JuicyCodesDecoder.extractBlob(embed) ?: error("argon embed has no juicycodes blob")
        JuicyCodesDecoder.config(JuicyCodesDecoder.decode(blob))
        return StreamItem(
            url = embedUrl,
            type = "embed",
            quality = "MultiQuality",
            audio = language.replaceFirstChar { it.uppercase() },
            referer = CODEDEW_ORIGIN,
            isActive = false,
            width = null,
            height = null,
        )
    }

    private fun get(url: String, referer: String?): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .apply { referer?.let { header("Referer", it) } }
            .get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("RareAnimes HTTP ${response.code} for $url")
            return response.body?.string().orEmpty()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun <T, R> mapParallel(
        items: List<T>,
        concurrency: Int,
        timeoutMs: Long = Long.MAX_VALUE,
        block: (T) -> R,
    ): List<R?> {
        if (items.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(concurrency, items.size))
        return try {
            val tasks = items.map { item -> Callable { runCatching { block(item) }.getOrNull() } }
            val futures = if (timeoutMs == Long.MAX_VALUE) {
                pool.invokeAll(tasks)
            } else {
                pool.invokeAll(tasks, timeoutMs, TimeUnit.MILLISECONDS)
            }
            futures.map { future -> runCatching { future.get() }.getOrNull() }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            emptyList()
        } finally {
            pool.shutdownNow()
        }
    }

    private fun <K, V> boundedMap(maxEntries: Int): MutableMap<K, V> = Collections.synchronizedMap(
        object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxEntries
        },
    )

    companion object {
        private const val BASE = "https://www.rareanimes.mov"
        private const val CODEDEW_ORIGIN = "https://codedew.com/"
        private const val MIN_TITLE_SCORE = 0.28
        private const val MAX_SEARCH_QUERIES = 3
        private const val MAX_SEARCH_PAGES = 5
        private const val MAX_PROBE_POSTS = 6
        private const val MAX_SEASON_POSTS = 24
        private const val FETCH_CONCURRENCY = 4
        private const val RESOLVE_CONCURRENCY = 3
        private const val RESOLVE_BUDGET_MS = 6_000L
        private val LANGUAGE_PRIORITY = listOf("hindi", "tamil", "telugu")
        private const val EPISODE_COUNT_SLACK = 2
        private const val MISS_TTL_MS = 6L * 60 * 60 * 1000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

internal object RareAnimesParser {
    data class SearchResult(val url: String, val title: String)
    data class ServerLink(val url: String, val anchor: String)

    data class Episode(val number: Int, val languages: Map<String, List<ServerLink>>)

    private val ARTICLE = Regex(
        """<article\b[^>]*\bherald-lay-b\b[^>]*>([\s\S]*?)</article>""",
        RegexOption.IGNORE_CASE,
    )
    private val ARTICLE_LINK = Regex(
        """<a\b[^>]*?href="(https?://[^"]+)"[^>]*?title="([^"]+)"[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val EPISODE_HEAD = Regex(""">\s*Episode\s+0*(\d+)""", RegexOption.IGNORE_CASE)
    private val LANGUAGE = Regex("""\b(Hindi|Tamil|Telugu)\b""", RegexOption.IGNORE_CASE)
    private val SEASON = Regex("""\bSeason\s*0*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val TITLE_BOILERPLATE = Regex(
        """\s*(?:[-–—:|]\s*)?\b(?:Season|Episodes?|Hindi|Tamil|Telugu|Bengali|Malayalam|""" +
            """Dubbed|Dual\s+Audio|Download|Watch|Complete|Uncut|Movie|Film|Series)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ZIPPER_LINK = Regex(
        """<a\b[^>]*?href="(https?://codedew\.com/zipper/\?url=[^"]+)"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val IFRAME_EMBED = Regex(
        """<iframe\b[^>]*?\bsrc="(https?://[a-z0-9.-]+/embed/[A-Za-z0-9]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val BARE_EMBED = Regex("""https:

    fun parseSearch(html: String): List<SearchResult> = ARTICLE.findAll(html).mapNotNull { article ->
        ARTICLE_LINK.find(article.groupValues[1])?.let { link ->
            SearchResult(link.groupValues[1], NativeProviderParsers.decodeEntities(link.groupValues[2]))
        }
    }.distinctBy { it.url }.toList()

    fun seasonNumber(title: String): Int? = SEASON.find(title)?.groupValues?.get(1)?.toIntOrNull()

    fun seriesTitle(postTitle: String): String {
        val cut = TITLE_BOILERPLATE.find(postTitle)?.range?.first ?: postTitle.length
        return postTitle.substring(0, cut).trim().trim('-', '–', '—', ':', '|', '(', '[').trim()
    }

    fun parseEpisodes(html: String, defaultLanguage: String = "hindi"): List<Episode> {
        val heads = EPISODE_HEAD.findAll(html).toList()
        return heads.mapIndexed { index, head ->
            val end = heads.getOrNull(index + 1)?.range?.first ?: html.length
            val block = html.substring(head.range.first, end)
            val labels = LANGUAGE.findAll(block).toList()
            val languages = if (labels.isEmpty()) {
                links(block).takeIf { it.isNotEmpty() }?.let { mapOf(defaultLanguage to it) }.orEmpty()
            } else {
                labels.mapIndexedNotNull { labelIndex, label ->
                    val sectionEnd = labels.getOrNull(labelIndex + 1)?.range?.first ?: block.length
                    val sectionLinks = links(block.substring(label.range.first, sectionEnd))
                    if (sectionLinks.isEmpty()) null else label.value.lowercase() to sectionLinks
                }.toMap()
            }
            Episode(head.groupValues[1].toInt(), languages)
        }.filter { it.languages.isNotEmpty() }.distinctBy { it.number }
    }

    private fun links(section: String): List<ServerLink> = ZIPPER_LINK.findAll(section)
        .map { match ->
            ServerLink(
                url = NativeProviderParsers.decodeEntities(match.groupValues[1]),
                anchor = NativeProviderParsers.stripTags(match.groupValues[2]),
            )
        }
        .toList()

    fun preferred(links: List<ServerLink>): ServerLink? {
        fun letters(value: String) = value.lowercase().filter(Char::isLetter)
        return links.firstOrNull { letters(it.anchor).contains("multiquality") }
            ?: links.firstOrNull { letters(it.anchor).contains("multquality") }
            ?: links.firstOrNull { it.anchor.contains("watch", ignoreCase = true) }
            ?: links.firstOrNull()
    }

    fun embedUrl(multiqualityHtml: String): String? =
        IFRAME_EMBED.find(multiqualityHtml)?.groupValues?.get(1)
            ?: BARE_EMBED.find(multiqualityHtml)?.value
}

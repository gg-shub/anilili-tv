package com.miruronative.data.remote

import com.miruronative.data.model.Media
import com.miruronative.data.model.SourcesResult
import com.miruronative.data.model.StreamItem
import com.miruronative.diagnostics.DiagnosticsLog
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * RareAnimes (rareanimes.mov, "RareToonsIndia") — Hindi/Tamil/Telugu dub source. The site is plain
 * WordPress; every episode's WatchMultiQuality link chains through codedew.com
 * (`/zipper/?url=…` → 302 → `/multiquality/?url=<fid>`) into an `argon.*` JW Player embed whose
 * `_juicycodes` blob decodes client-side to a single HLS master playlist (see [JuicyCodesDecoder]).
 * That master is session-gated, so what this provider hands back is the embed — see [resolveStream].
 *
 * All languages are exposed through the pipe's single `dub` channel with the language name on
 * [StreamItem.audio], until the sub|dub category model grows real per-language rows.
 *
 * **Post selection.** Title similarity alone picks the wrong page surprisingly often: for
 * "Demon Slayer: Kimetsu no Yaiba" the Infinity Castle *movie* post scores 0.64 while the Season 1
 * post carrying all 26 episodes scores 0.20. So candidates are ranked but then *probed* — a post
 * that parses to zero episodes is skipped rather than accepted.
 *
 * **Season stitching.** The site splits long series into per-season posts that each restart at
 * episode 1, while AniList numbers them absolutely (Naruto Shippuden = one 500-episode entry).
 * When the AniList entry clearly outruns the best single post, the season posts are fetched and
 * laid end to end so the numbering lines up with the rest of the app.
 */
internal class RareAnimesProvider(private val client: OkHttpClient) {
    private data class Catalog(val label: String, val episodes: List<RareAnimesParser.Episode>)

    private val catalogs = boundedMap<Int, Catalog>(64)
    /** Titles the site genuinely has nothing for, so a miss costs one search per [MISS_TTL_MS]. */
    private val misses = boundedMap<Int, Long>(128)

    fun episodeAvailability(media: Media): EpisodeAvailability {
        val episodes = catalog(media).episodes
        // Every track is a dub (Hindi/Tamil/Telugu); there is no Japanese-audio "sub" offering.
        return EpisodeAvailability(emptySet(), episodes.map { it.number }.toSet())
    }

    /**
     * @param audio unused — this provider is dub-only, and [episodeAvailability] reports an empty
     *   sub set, so the catalog never hands the app a sub row that could reach here.
     */
    fun sources(media: Media, audio: String, episode: Int): SourcesResult {
        val entry = catalog(media).episodes.firstOrNull { it.number == episode }
            ?: error("RareAnimes episode $episode is not in the catalog")
        // Hindi first: it is the site's primary track and the one most likely to resolve, so if
        // the batch deadline below cuts things short, that is the track we keep.
        val resolvable = entry.languages.mapNotNull { (language, links) ->
            RareAnimesParser.preferred(links)?.let { language to it }
        }.sortedBy { (language, _) -> LANGUAGE_PRIORITY.indexOf(language).takeIf { it >= 0 } ?: LANGUAGE_PRIORITY.size }
        // Each language is an independent two-hop resolve against a slow host.
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
        // Hindi is the site's primary track; fall back to whatever resolved so one stream is
        // always marked active for players that key their default off it.
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

        // Best-ranked post that actually carries episodes. Movie posts and stub pages score high
        // on title alone but parse to nothing, so they fall through here instead of winning.
        val single = candidates.take(MAX_PROBE_POSTS)
            .firstNotNullOfOrNull { candidate ->
                episodesOf(candidate).takeIf { it.isNotEmpty() }?.let { Catalog(candidate.title, it) }
            }

        val expected = expectedEpisodeCount(media)
        if (single != null && (expected == null || single.episodes.size >= expected - EPISODE_COUNT_SLACK)) {
            return single
        }

        // Only stitch posts belonging to the same series as the post we trust, or "Naruto" would
        // absorb the Shippuden season posts that its own search returns.
        val series = RareAnimesParser.seriesTitle((single?.label ?: candidates.first().title))
        fun sameSeries(all: List<RareAnimesParser.SearchResult>) = all.filter {
            RareAnimesParser.seriesTitle(it.title).equals(series, ignoreCase = true)
        }
        // One search page holds ~6 posts, so a 22-season run is invisible from page 1 alone. Pay
        // for the extra pages only here, on the long series that actually need them.
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

    /**
     * Lays the site's per-season posts end to end so episode numbers match AniList's absolute
     * numbering.
     *
     * Only an unbroken run from season 1 takes part. A missing season in the middle would slide
     * every later season's episodes onto numbers that belong to the gap, so the run stops at the
     * first hole: fewer episodes offered, but every one of them numbered correctly.
     */
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

        // Warm every season page in parallel; each is ~500 KB and the caller is on a budget.
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
        // AniList splits some series into per-season entries while the site keeps stitching past
        // them (Jujutsu Kaisen's entry is season 1 only). Trimming to the entry's own length keeps
        // the leading seasons aligned instead of inventing episodes the entry does not have.
        val trimmed = if (expected != null) absolute.take(expected) else absolute
        return Catalog("${seasons.size} season posts", trimmed)
    }

    /** Search hits across the AniList titles, best title match first. */
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
        // Score against the series name alone. Every post title trails the site's boilerplate
        // ("Season 1 – Episodes Hindi Dubbed Download HD Jio Cinema"), which drags a perfectly
        // good season post down to 0.20 while a same-series movie post sails past on its longer,
        // more literal name.
        fun score(candidate: RareAnimesParser.SearchResult) =
            titles.maxOf {
                NativeProviderParsers.titleSelectionScore(it, RareAnimesParser.seriesTitle(candidate.title))
            }
        return hits.filter { score(it) >= MIN_TITLE_SCORE }.sortedByDescending(::score)
    }

    /**
     * WordPress search is literal, so AniList's fuller names miss: searching
     * "Demon Slayer: Kimetsu no Yaiba" returns only the Infinity Castle movie, while
     * "Demon Slayer" returns every season post. Query the part before the colon too, and keep the
     * total capped — each search page is ~430 KB and this provider runs on every title.
     */
    private fun searchQueries(titles: List<String>): List<String> = titles
        .flatMap { title -> listOf(title, title.substringBefore(':').trim()) }
        .filter { it.length >= 3 }
        .distinctBy { it.lowercase() }
        .take(MAX_SEARCH_QUERIES)

    /** How many episodes the AniList entry is expected to carry, if it is knowable. */
    private fun expectedEpisodeCount(media: Media): Int? = media.episodes?.takeIf { it > 0 }
        ?: media.nextAiringEpisode?.episode?.minus(1)?.takeIf { it > 0 }

    /**
     * codedew zipper → (302) multiquality → argon embed. OkHttp follows the zipper's redirect
     * itself, so [get] on the zipper URL returns the multiquality page directly.
     *
     * The result is the **embed page**, not the HLS master inside it. The master decodes fine, but
     * its CDN ("JUICY NGINX") authorises per player session: the embed registers itself through a
     * token and a `/api/videos/…/ping` route, and every request that did not come from that page
     * is answered with a branded 403 — no Referer, Origin, or cookie combination gets past it.
     * Handing the WebView the embed lets the page do its own handshake, which is exactly how kiwi
     * is handled for the same reason (see `pickProviderStream`).
     *
     * The blob is still decoded, because a page whose config does not carry a master playlist has
     * nothing to play and should fail here rather than at the player.
     */
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

    /**
     * Runs [block] over [items] on a short-lived pool; failures come back as null.
     *
     * [timeoutMs] bounds the whole batch, not each item: whatever has not finished by then is
     * cancelled and reported as null. Callers that are themselves on a deadline need that — the
     * watch screen gives a provider 8 seconds total, and three of this site's 283 KB player pages
     * do not reliably fit, so it is better to return the tracks that did land than to time out
     * with nothing.
     */
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
        /** Extra search pages, walked only when a series needs season stitching. */
        private const val MAX_SEARCH_PAGES = 5
        /** Posts probed for episodes before giving up on a title. */
        private const val MAX_PROBE_POSTS = 6
        private const val MAX_SEASON_POSTS = 24
        private const val FETCH_CONCURRENCY = 4
        private const val RESOLVE_CONCURRENCY = 3
        /** Inside the watch screen's 8s per-provider budget, with room to return what resolved. */
        private const val RESOLVE_BUDGET_MS = 6_000L
        private val LANGUAGE_PRIORITY = listOf("hindi", "tamil", "telugu")
        /** AniList and the site disagree by an episode or two often enough to tolerate it. */
        private const val EPISODE_COUNT_SLACK = 2
        private const val MISS_TTL_MS = 6L * 60 * 60 * 1000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

/** Pure-HTML parsing for RareAnimes pages, kept separate so unit tests never touch the network. */
internal object RareAnimesParser {
    data class SearchResult(val url: String, val title: String)
    data class ServerLink(val url: String, val anchor: String)

    /** Episode number → per-language server links (`hindi`/`tamil`/`telugu`). */
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
    private val BARE_EMBED = Regex("""https://[a-z0-9.-]+/embed/[A-Za-z0-9]+""")

    fun parseSearch(html: String): List<SearchResult> = ARTICLE.findAll(html).mapNotNull { article ->
        ARTICLE_LINK.find(article.groupValues[1])?.let { link ->
            SearchResult(link.groupValues[1], NativeProviderParsers.decodeEntities(link.groupValues[2]))
        }
    }.distinctBy { it.url }.toList()

    /** The season a post covers, e.g. "Demon Slayer Season 4 (Hashira Training Arc)" → 4. */
    fun seasonNumber(title: String): Int? = SEASON.find(title)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The series name with the site's stock suffix removed, e.g.
     * "Demon Slayer Season 1 – Episodes Hindi Dubbed Download HD Jio Cinema" → "Demon Slayer".
     * Used for both ranking and for grouping a series' season posts together.
     */
    fun seriesTitle(postTitle: String): String {
        val cut = TITLE_BOILERPLATE.find(postTitle)?.range?.first ?: postTitle.length
        return postTitle.substring(0, cut).trim().trim('-', '–', '—', ':', '|', '(', '[').trim()
    }

    /**
     * Episode blocks run from one `>Episode NN` heading to the next. Inside a block, a language
     * label ("Hindi Uncut", "Tamil", "Telugu") opens that language's section — but plenty of posts
     * are single-language and label their rows by *dub source* instead ("New Sony Dub",
     * "Crunchyroll"). Those blocks carry no language word at all, so the whole block is taken as
     * [defaultLanguage]; without that fallback a post like Jujutsu Kaisen Season 1 parses to one
     * episode out of twenty-four.
     */
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

    /**
     * WatchMultiQuality is the only server we can decode natively. The site spells it both
     * "WatchMultiQuality" and "WatchMultQuality", so match on letters only rather than either
     * literal, and keep the other servers as a fallback order.
     */
    fun preferred(links: List<ServerLink>): ServerLink? {
        fun letters(value: String) = value.lowercase().filter(Char::isLetter)
        return links.firstOrNull { letters(it.anchor).contains("multiquality") }
            ?: links.firstOrNull { letters(it.anchor).contains("multquality") }
            ?: links.firstOrNull { it.anchor.contains("watch", ignoreCase = true) }
            ?: links.firstOrNull()
    }

    /** The argon (or successor-domain) player iframe on a codedew multiquality page. */
    fun embedUrl(multiqualityHtml: String): String? =
        IFRAME_EMBED.find(multiqualityHtml)?.groupValues?.get(1)
            ?: BARE_EMBED.find(multiqualityHtml)?.value
}

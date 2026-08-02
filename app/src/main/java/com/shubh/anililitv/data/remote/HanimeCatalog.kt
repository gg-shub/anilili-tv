package com.shubh.anililitv.data.remote

import com.shubh.anililitv.data.model.CoverImage
import com.shubh.anililitv.data.model.Media
import com.shubh.anililitv.data.model.MediaTag
import com.shubh.anililitv.data.model.MediaTitle
import com.shubh.anililitv.data.model.StreamItem
import com.shubh.anililitv.data.model.StudioConnection
import com.shubh.anililitv.data.model.StudioNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HanimeVideo(
    val id: Int,
    val name: String = "",
    val slug: String = "",
    @SerialName("search_titles") val searchTitles: String = "",
    val brand: String = "",
    val description: String = "",
    @SerialName("poster_url") val posterUrl: String = "",
    @SerialName("released_at") val releasedAt: String = "",
    val tags: List<String> = emptyList(),
) {
    val seriesSlug: String get() = hanimeSeriesSlug(slug)
    val episodeNumber: Int get() = hanimeEpisodeNumber(slug)
}

fun hanimeSeriesSlug(slug: String): String = slug.trim().replace(TRAILING_SLUG_NUMBER, "")

fun hanimeEpisodeNumber(slug: String): Int =
    TRAILING_SLUG_NUMBER.find(slug.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 1

fun normalizeHanimeTitle(value: String): String = value
    .lowercase()
    .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
    .joinToString("")
    .split(' ')
    .filter(String::isNotBlank)
    .joinToString(" ")

fun hanimeTitleWithoutEpisode(value: String): String =
    value.trim().replace(TRAILING_TITLE_NUMBER, "").trim()

fun titleSimilarity(left: String, right: String): Double {
    val a = normalizeHanimeTitle(left)
    val b = normalizeHanimeTitle(right)
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    val pairs = bigrams(a)
    val other = bigrams(b)
    if (pairs.isEmpty() || other.isEmpty()) return 0.0
    val counts = HashMap<String, Int>(other.size)
    other.forEach { counts[it] = (counts[it] ?: 0) + 1 }
    var shared = 0
    pairs.forEach { pair ->
        val left1 = counts[pair] ?: 0
        if (left1 > 0) {
            counts[pair] = left1 - 1
            shared++
        }
    }
    return (2.0 * shared) / (pairs.size + other.size)
}

fun aniListTitleCandidates(media: Media): List<String> = listOfNotNull(
    media.title.romaji,
    media.title.english,
    media.title.native,
    media.title.userPreferred,
).map(String::trim).filter(String::isNotEmpty).distinct()

fun hanimeTitleCandidates(video: HanimeVideo): List<String> = listOf(
    video.name,
    hanimeTitleWithoutEpisode(video.name),
    video.searchTitles,
).map(String::trim).filter(String::isNotEmpty).distinct()

data class HanimeSeriesMatch(val seriesSlug: String, val episodes: List<HanimeVideo>, val score: Double)

const val HANIME_MATCH_THRESHOLD = 0.85

fun matchHanimeSeries(
    media: Media,
    catalogue: List<HanimeVideo>,
    threshold: Double = HANIME_MATCH_THRESHOLD,
): HanimeSeriesMatch? {
    val wanted = aniListTitleCandidates(media)
    if (wanted.isEmpty() || catalogue.isEmpty()) return null
    var best: HanimeSeriesMatch? = null
    catalogue.groupBy(HanimeVideo::seriesSlug).forEach { (slug, episodes) ->
        var score = 0.0
        episodes.forEach { episode ->
            hanimeTitleCandidates(episode).forEach { candidate ->
                wanted.forEach { target ->
                    val similarity = titleSimilarity(target, candidate)
                    if (similarity > score) score = similarity
                }
            }
        }
        if (score >= threshold && score > (best?.score ?: 0.0)) {
            best = HanimeSeriesMatch(slug, episodes.sortedBy(HanimeVideo::episodeNumber), score)
        }
    }
    return best
}

fun sortHanimeStreams(streams: List<StreamItem>): List<StreamItem> =
    streams.sortedByDescending { it.height ?: 0 }

fun hanimeMediaId(videoId: Int): Int = -videoId

fun isHanimeMediaId(id: Int): Boolean = id < 0

fun hanimeVideoId(mediaId: Int): Int = -mediaId

const val HANIME_GENRE = "Hentai"

fun hanimeSeriesAsMedia(episodes: List<HanimeVideo>): Media? {
    val ordered = episodes.sortedBy(HanimeVideo::episodeNumber)
    val first = ordered.firstOrNull() ?: return null
    val title = hanimeTitleWithoutEpisode(first.name).ifBlank { first.name }
    return Media(
        id = hanimeMediaId(first.id),
        title = MediaTitle(romaji = title, english = title, userPreferred = title),
        coverImage = CoverImage(large = first.posterUrl, extraLarge = first.posterUrl),
        description = ordered.firstNotNullOfOrNull { it.description.takeIf(String::isNotBlank) },
        episodes = ordered.size,
        isAdult = true,
        seasonYear = hanimeReleaseYear(first.releasedAt),
        genres = listOf(HANIME_GENRE),
        tags = ordered.asSequence()
            .flatMap(HanimeVideo::tags)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .map { MediaTag(name = it, isAdult = true) }
            .toList(),
        studios = StudioConnection(
            nodes = listOfNotNull(first.brand.takeIf(String::isNotBlank)?.let { StudioNode(name = it) }),
        ),
    )
}

fun hanimeReleaseYear(releasedAt: String): Int? =
    releasedAt.take(4).toIntOrNull()?.takeIf { it in 1900..2999 }

fun searchHanimeCatalogue(
    query: String,
    catalogue: List<HanimeVideo>,
    limit: Int = 30,
): List<Media> {
    val wanted = normalizeHanimeTitle(query)
    if (wanted.isBlank()) return emptyList()
    return catalogue.groupBy(HanimeVideo::seriesSlug).values
        .mapNotNull { episodes ->
            val best = episodes.maxOf { episode ->
                hanimeTitleCandidates(episode).maxOfOrNull { candidate ->
                    hanimeQueryScore(wanted, candidate)
                } ?: 0.0
            }
            if (best <= 0.0) null else episodes to best
        }
        .sortedWith(compareByDescending<Pair<List<HanimeVideo>, Double>> { it.second }
            .thenBy { hanimeTitleWithoutEpisode(it.first.first().name) })
        .take(limit)
        .mapNotNull { (episodes, _) -> hanimeSeriesAsMedia(episodes) }
}

private fun hanimeQueryScore(normalizedQuery: String, candidate: String): Double {
    val target = normalizeHanimeTitle(candidate)
    if (target.isBlank()) return 0.0
    return when {
        target == normalizedQuery -> 1.0
        target.startsWith(normalizedQuery) -> 0.95
        target.contains(normalizedQuery) -> 0.9
        else -> titleSimilarity(normalizedQuery, target).takeIf { it >= 0.7 } ?: 0.0
    }
}

private val TRAILING_SLUG_NUMBER = Regex("-(\\d{1,2})$")
private val TRAILING_TITLE_NUMBER = Regex("\\s+\\d{1,2}$")

private fun bigrams(value: String): List<String> =
    if (value.length < 2) listOf(value) else (0 until value.length - 1).map { value.substring(it, it + 2) }

package com.miruronative.data.remote

import java.util.Base64

/**
 * JuicyCodes (the `argon.*` embed host used by codedew's WatchMultiQuality links) obfuscates its
 * JW Player config inside a `_juicycodes("…" + "…")` blob in the embed HTML. The decoder ships in
 * the site's own player.js, so the whole scheme is replicable natively:
 *
 * 1. The blob's last 3 characters form the salt: each contributes `(charCode - 100)`, concatenated
 *    as decimal strings, parsed back as one integer (e.g. "fgh" → "2","3","4" → 234).
 * 2. The rest is URL-safe base64 (`_`→`+`, `-`→`/`, padded with `=` to a multiple of 4).
 * 3. Every decoded character is an index into the symbol map below, written out as its decimal
 *    index (0-9), giving one long digit string.
 * 4. The digits are grouped into chunks of 4; each chunk's character is `(chunk % 1000) - salt`.
 *
 * The result is JavaScript source (`var config = {…}; JuicyCodes.JWPlayer('jc-player', config)`)
 * which we never execute — the HLS url and quality labels are lifted out with regexes instead.
 */
internal object JuicyCodesDecoder {
    private val SYMBOLS = listOf('`', '%', '-', '+', '*', '$', '!', '_', '^', '=')
    private val CALL = Regex("""_juicycodes\(([\s\S]*?)\);?""")
    private val STRING_PART = Regex("\"([^\"]*)\"")

    data class EmbedConfig(
        val title: String?,
        val hlsUrl: String,
        val qualityLabels: Map<String, String>,
        val thumbnailsVtt: String?,
    )

    /** The `_juicycodes("…" + "…" + …)` argument, reassembled from its string-literal parts. */
    fun extractBlob(embedHtml: String): String? {
        val expr = CALL.find(embedHtml)?.groupValues?.get(1) ?: return null
        val parts = STRING_PART.findAll(expr).map { it.groupValues[1] }.toList()
        return parts.takeIf { it.isNotEmpty() }?.joinToString("")
    }

    /** Decodes a blob from [extractBlob] into the embed's inline JavaScript source. */
    fun decode(blob: String): String {
        require(blob.length > 3) { "JuicyCodes blob too short" }
        val salt = blob.takeLast(3).map { (it.code - 100).toString() }.joinToString("").toInt()
        var body = blob.dropLast(3).replace('_', '+').replace('-', '/')
        body += "===".substring((body.length + 3) % 4)
        val decoded = String(Base64.getDecoder().decode(body), Charsets.ISO_8859_1)
        val digits = buildString(decoded.length) {
            decoded.forEach { ch ->
                val index = SYMBOLS.indexOf(ch)
                require(index >= 0) { "Unexpected JuicyCodes symbol: $ch" }
                append(index)
            }
        }
        require(digits.length % 4 == 0) { "JuicyCodes digit stream is misaligned" }
        return buildString(digits.length / 4) {
            var i = 0
            while (i < digits.length) {
                append(((digits.substring(i, i + 4).toInt() % 1000) - salt).toChar())
                i += 4
            }
        }
    }

    /** Lifts the stream data out of the decoded JavaScript without executing it. */
    fun config(decodedJs: String): EmbedConfig {
        // The config holds two "file" keys (thumbnails VTT first, then the HLS master), so
        // anchor on the .m3u8 extension instead of taking the first match.
        val hls = Regex("\"file\"\\s*:\\s*\"((?:\\\\.|[^\"])*?\\.m3u8)\"")
            .find(decodedJs)?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?: error("JuicyCodes config carries no HLS url")
        val labelsBody = labelsBody(decodedJs)
        val labels = Regex("\"(\\d+)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            .findAll(labelsBody)
            .associate { it.groupValues[1] to it.groupValues[2] }
        return EmbedConfig(
            title = jsString(decodedJs, "title"),
            hlsUrl = hls,
            qualityLabels = labels,
            thumbnailsVtt = Regex("\"kind\"\\s*:\\s*\"thumbnails\"\\s*,\\s*\"file\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .find(decodedJs)?.groupValues?.get(1)?.replace("\\/", "/"),
        )
    }

    /**
     * The body of the `"labels": { … }` object, found by scanning rather than by regex.
     *
     * The pattern this replaces — `"labels"\s*:\s*\{([^}]*)\}` — compiles on the JVM but threw
     * `PatternSyntaxException` on device (verified on a Galaxy S25). Because it lived in a field
     * initialiser, the whole object failed to load and every decode after it died with
     * `NoClassDefFoundError`, so RareAnimes could never resolve a stream. Unit tests run against
     * the JVM's regex engine and cannot catch this class of divergence at all.
     *
     * Brace-matching here is plain index arithmetic, which sidesteps the question entirely. Note
     * that escaped braces are not universally broken on Android — `AniBdProvider.trackSubtitles`
     * relies on them in production — so treat this as one pattern that Android rejected, not a
     * general rule.
     */
    private fun labelsBody(js: String): String {
        val key = js.indexOf("\"labels\"")
        if (key < 0) return ""
        val open = js.indexOf('{', key)
        if (open < 0) return ""
        val close = js.indexOf('}', open)
        if (close < 0) return ""
        return js.substring(open + 1, close)
    }

    private fun jsString(js: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            .find(js)?.groupValues?.get(1)
}

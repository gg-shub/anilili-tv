package com.shubh.anililitv.data.remote

import java.util.Base64

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

    fun extractBlob(embedHtml: String): String? {
        val expr = CALL.find(embedHtml)?.groupValues?.get(1) ?: return null
        val parts = STRING_PART.findAll(expr).map { it.groupValues[1] }.toList()
        return parts.takeIf { it.isNotEmpty() }?.joinToString("")
    }

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

    fun config(decodedJs: String): EmbedConfig {
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

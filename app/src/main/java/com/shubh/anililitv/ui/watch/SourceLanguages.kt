package com.shubh.anililitv.ui.watch

import java.util.Locale


private val CATEGORY_LABELS = setOf("sub", "dub", "subbed", "dubbed", "raw", "default", "auto", "und")

private val ALIASES = mapOf(
    "espanol" to "Spanish",
    "espanol latino" to "Spanish",
    "latino" to "Spanish",
    "castellano" to "Spanish",
    "francais" to "French",
    "deutsch" to "German",
    "italiano" to "Italian",
    "portugues" to "Portuguese",
    "brazilian" to "Portuguese",
    "brasil" to "Portuguese",
    "nihongo" to "Japanese",
    "jpn" to "Japanese",
    "eng" to "English",
    "filipino" to "Tagalog",
    "bahasa" to "Indonesian",
    "bahasa indonesia" to "Indonesian",
    "farsi" to "Persian",
    "cn" to "Chinese",
    "zh" to "Chinese",
    "mandarin" to "Chinese",
)

private val NOISE = Regex(
    """\b(dub(bed)?|sub(bed|titles?|s)?|audio|uncut|multi|hd|fhd|sd|soft|hard|cc|forced|full)\b""",
    RegexOption.IGNORE_CASE,
)

private val LANGUAGE_TAG = Regex("""^([A-Za-z]{2,3})[-_][A-Za-z0-9]{2,4}$""")

fun normalizeLanguage(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    LANGUAGE_TAG.find(trimmed)?.let { match ->
        languageFor(match.groupValues[1].lowercase(Locale.US))?.let { return it }
    }

    val cleaned = NOISE.replace(trimmed, " ")
        .replace(Regex("""[\[\](){}_/|]"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('-', '·', ',', '.')
        .trim()
    val key = cleaned.lowercase(Locale.US)
    if (key.isEmpty() || key in CATEGORY_LABELS) return null

    languageFor(key)?.let { return it }

    return cleaned.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.US) }
    }
}

private fun languageFor(key: String): String? {
    ALIASES[key]?.let { return it }
    if (key.length in 2..3) {
        val display = Locale.forLanguageTag(key).getDisplayLanguage(Locale.US)
        if (display.isNotBlank() && !display.equals(key, ignoreCase = true)) return display
    }
    return null
}

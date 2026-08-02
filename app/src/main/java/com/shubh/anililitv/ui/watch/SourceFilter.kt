package com.shubh.anililitv.ui.watch

import com.shubh.anililitv.data.model.Category

enum class AudioFilter(val label: String, val category: Category?) {
    ANY("All", null),
    SUB("Sub", Category.SUB),
    DUB("Dub", Category.DUB),
}

fun filterSourceOptions(
    options: List<WatchSourceOption>,
    capabilities: Map<Pair<String, Category>, SourceCapabilities>,
    audio: AudioFilter,
    language: String?,
): List<WatchSourceOption> = options.filter { option ->
    val matchesAudio = audio.category == null || option.category == audio.category
    if (!matchesAudio) return@filter false
    if (language == null) return@filter true
    val capability = capabilities[option.provider to option.category] ?: SourceCapabilities()
    !capability.known || language in capability.languages
}

fun languageFilterIsComplete(
    options: List<WatchSourceOption>,
    capabilities: Map<Pair<String, Category>, SourceCapabilities>,
): Boolean = options.all { capabilities[it.provider to it.category]?.known == true }

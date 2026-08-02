package com.shubh.anililitv.data

object ProviderCatalog {
    enum class Source { MIRURO, ANIVEXA }

    private val miruroOrder = listOf(
        "bonk", "kiwi", "pewe", "bee", "ally", "moo", "hop",
        "nun", "bun", "twin", "cog", "telli",
    )
    private val miruroEmbed = setOf("nun", "bun", "twin", "cog", "telli")

    private val externalDownloadProviders = setOf("bonk", "kiwi", "ally", "moo")

    val anivexaProviders = listOf(
        "senshi", "anibd", "anikoto", "kaa", "allanime", "animekai", "reanime", "anizone", "animegg", "anineko", "2dhive",
        "rareanimes",
        "hanime", "hentaihaven",
    )

    val adultProviders = setOf("hanime", "hentaihaven")

    fun isAdultOnly(provider: String): Boolean = provider in adultProviders

    fun anivexaProvidersFor(hideAdult: Boolean): List<String> =
        if (hideAdult) anivexaProviders.filterNot(::isAdultOnly) else anivexaProviders

    val webViewResolverProviders = setOf("reanime")

    val fastAnivexaProviders = listOf("senshi", "anibd", "anikoto", "kaa")

    val fastProviders: Set<String> =
        (miruroOrder - miruroEmbed).toSet() + fastAnivexaProviders

    fun isFast(provider: String): Boolean = provider in fastProviders
    fun supportsExternalDownloads(provider: String): Boolean = provider in externalDownloadProviders

    fun supportsOfflineDownload(provider: String): Boolean = isNative(provider)

    private val leaders = listOf("bonk", "anibd", "senshi")
    private val order = leaders +
        (miruroOrder + anivexaProviders).filterNot { it in leaders }
            .sortedByDescending { it in fastProviders }

    fun sourceOf(provider: String): Source =
        if (provider in anivexaProviders) Source.ANIVEXA else Source.MIRURO

    fun isEmbed(provider: String): Boolean = provider in miruroEmbed
    fun isNative(provider: String): Boolean = !isEmbed(provider)

    fun sortKey(provider: String): Int =
        order.indexOf(provider).let { if (it >= 0) it else Int.MAX_VALUE }

    fun selectableProviders(hideAdult: Boolean): List<String> =
        if (hideAdult) order.filterNot(::isAdultOnly) else order

    fun label(provider: String): String = when (provider) {
        "anibd" -> "AniBD"
        "2dhive" -> "2Dhive"
        "allanime" -> "AllAnime"
        "animekai" -> "AnimeKai"
        "kaa" -> "KickAssAnime"
        "rareanimes" -> "RareAnimes"
        "hentaihaven" -> "Hentai Haven"
        else -> provider.replaceFirstChar { it.uppercase() }
    }
}

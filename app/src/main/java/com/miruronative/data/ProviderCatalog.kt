package com.miruronative.data

/**
 * Provider classification across both streaming backends:
 * - **Miruro** pipe providers (via the on-device WebView bridge)
 * - **Anivexa** providers (via an Anivexa-API instance over HTTP)
 * A provider name is globally unique, so routing is by name.
 */
object ProviderCatalog {
    enum class Source { MIRURO, ANIVEXA }

    // Miruro pipe providers.
    private val miruroOrder = listOf(
        "bonk", "kiwi", "pewe", "bee", "ally", "moo", "hop", // native HLS
        "nun", "bun", "twin", "cog", "telli",                 // iframe embeds
    )
    private val miruroEmbed = setOf("nun", "bun", "twin", "cog", "telli")

    /**
     * Miruro providers whose source response can include a separate download page. This mirrors
     * the live provider capability map; the current provider is also checked dynamically because
     * Miruro can change the map between app releases.
     */
    private val externalDownloadProviders = setOf("bonk", "kiwi", "ally", "moo")

    // Anivexa providers we query (reliable, self-hosted sources).
    val anivexaProviders = listOf(
        "senshi", "anibd", "anikoto", "kaa", "allanime", "animekai", "reanime", "anizone", "animegg", "anineko", "2dhive",
        "rareanimes",
        "hanime", "hentaihaven",
    )

    /**
     * Providers that only ever carry adult material. They are dropped from every catalog and from
     * the server picker while "Hide adult content" is on, so a viewer who has not opted in is
     * never offered one — even on a title that happens to match by name.
     */
    val adultProviders = setOf("hanime", "hentaihaven")

    fun isAdultOnly(provider: String): Boolean = provider in adultProviders

    /** The providers to query for [hideAdult]; the adult-only ones drop out when it is set. */
    fun anivexaProvidersFor(hideAdult: Boolean): List<String> =
        if (hideAdult) anivexaProviders.filterNot(::isAdultOnly) else anivexaProviders

    // Providers whose resolution drives the hidden resolver WebView rather than plain HTTP. That
    // page runs a real player, so resolving one competes for the hardware video decoder with
    // whatever is already on screen — background validation skips these during playback.
    val webViewResolverProviders = setOf("reanime")

    // The consistently quick Anivexa lookups (API-backed, not full-page scrapers). Raced as an
    // early partial catalog when the Miruro pipe is down or slow, so playback never waits for
    // the 15-second stragglers; the remaining providers still merge in behind.
    val fastAnivexaProviders = listOf("senshi", "anibd", "anikoto", "kaa")

    // Providers that consistently resolve and start quickly: the Miruro pipe's native HLS set
    // (one pipe call away) and the API-backed Anivexa lookups. Embeds and full-page scrapers
    // are excluded. Drives the ⚡ badge and the speed-first sort below.
    val fastProviders: Set<String> =
        (miruroOrder - miruroEmbed).toSet() + fastAnivexaProviders

    fun isFast(provider: String): Boolean = provider in fastProviders
    /** Whether the provider's own website offers download links, reached by opening a browser. */
    fun supportsExternalDownloads(provider: String): Boolean = provider in externalDownloadProviders

    /**
     * Whether the app can save an episode from this provider into the offline library.
     *
     * Deliberately separate from [supportsExternalDownloads]: an embed plays inside a WebView and
     * exposes no stream the downloader can fetch, so a provider can offer download links on its
     * site while the app can do nothing with it. Conflating the two put a download glyph on Kiwi
     * and then hid the download button.
     */
    fun supportsOfflineDownload(provider: String): Boolean = isNative(provider)

    // Default row order: bonk (Miruro pipe) then anibd (Anivexa) lead as the two default
    // sources — independent backends, both fast and reliable — with senshi right behind.
    // After the leaders, fast providers sort before embeds and slow scrapers so the quick
    // options are always at the top of the server list. A user's saved favourite provider
    // always overrides this order.
    private val leaders = listOf("bonk", "anibd", "senshi")
    private val order = leaders +
        (miruroOrder + anivexaProviders).filterNot { it in leaders }
            .sortedByDescending { it in fastProviders }

    fun sourceOf(provider: String): Source =
        if (provider in anivexaProviders) Source.ANIVEXA else Source.MIRURO

    /** Only Miruro has provider-level iframe embeds; Anivexa decides embed per-stream. */
    fun isEmbed(provider: String): Boolean = provider in miruroEmbed
    fun isNative(provider: String): Boolean = !isEmbed(provider)

    fun sortKey(provider: String): Int =
        order.indexOf(provider).let { if (it >= 0) it else Int.MAX_VALUE }

    /** Every server a user can pin in Settings, in the same order the player's picker shows. */
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

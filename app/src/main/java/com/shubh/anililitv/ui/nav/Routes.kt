package com.shubh.anililitv.ui.nav

import android.net.Uri

object Routes {
    const val EXTRA_ROUTE = "com.shubh.anililitv.extra.ROUTE"
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_DESTINATION = "$SEARCH?studioId={studioId}&studioName={studioName}"
    const val SCHEDULE = "schedule"
    const val MORE = "more"
    const val SETTINGS = "settings"

    val tabRoutes = setOf(HOME, SEARCH, SCHEDULE, MORE, SETTINGS)

    const val NOTIFICATIONS = "notifications"
    const val DOWNLOAD = "download/{downloadId}"
    fun download(downloadId: String) = "download/${Uri.encode(downloadId)}"

    const val DETAIL = "detail/{id}"
    fun detail(id: Int) = "detail/$id"

    fun studioSearch(studioId: Int, studioName: String) =
        "$SEARCH?studioId=$studioId&studioName=${Uri.encode(studioName)}"

    fun tabRoute(destinationRoute: String?): String? = destinationRoute?.substringBefore('?')

    fun shouldRestoreTabState(route: String): Boolean = route != HOME

    const val WATCH = "watch/{id}/{provider}/{category}/{episode}?showEpisodes={showEpisodes}"
    fun watch(id: Int, provider: String, category: String, episode: String) =
        "watch/$id/$provider/$category/${Uri.encode(episode)}"

    fun episodes(id: Int, provider: String, category: String, episode: String) =
        withEpisodeList(watch(id, provider, category, episode))

    internal fun withEpisodeList(watchRoute: String): String = "$watchRoute?showEpisodes=true"

    object Arg {
        const val ID = "id"
        const val STUDIO_ID = "studioId"
        const val STUDIO_NAME = "studioName"
        const val PROVIDER = "provider"
        const val CATEGORY = "category"
        const val EPISODE = "episode"
        const val SHOW_EPISODES = "showEpisodes"
        const val DOWNLOAD_ID = "downloadId"

    }
}

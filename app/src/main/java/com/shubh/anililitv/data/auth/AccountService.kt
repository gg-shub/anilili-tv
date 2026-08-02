package com.shubh.anililitv.data.auth

enum class AccountService(val label: String) {
    ANILIST("AniList"),
    MAL("MyAnimeList"),
    ;

    companion object {
        val active: AccountService?
            get() = when {
                AuthManager.isLoggedIn -> ANILIST
                MalAuthManager.isLoggedIn -> MAL
                else -> null
            }
    }
}

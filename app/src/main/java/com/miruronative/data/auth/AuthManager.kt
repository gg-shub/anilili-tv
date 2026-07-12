package com.miruronative.data.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.miruronative.data.reminder.ReleaseSyncScheduler

/**
 * AniList OAuth (Implicit Grant). We open the authorize URL in a WebView and capture the
 * access token from the `http://localhost/#access_token=…` redirect fragment — no external
 * browser, no client secret in the app. Tokens are long-lived (~1 year; AniList has no refresh).
 */
object AuthManager {
    const val CLIENT_ID = "45552"
    const val REDIRECT = "http://localhost"
    val AUTHORIZE_URL: String = Uri.Builder()
        .scheme("https")
        .authority("anilist.co")
        .path("/api/v2/oauth/authorize")
        .appendQueryParameter("client_id", CLIENT_ID)
        .appendQueryParameter("redirect_uri", REDIRECT)
        .appendQueryParameter("response_type", "token")
        .build()
        .toString()

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val _token = MutableStateFlow<String?>(null)
    val token = _token.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("miruro_auth", Context.MODE_PRIVATE)
        _token.value = prefs.getString("anilist_token", null)
    }

    fun current(): String? = _token.value
    val isLoggedIn: Boolean get() = _token.value != null

    fun setToken(token: String) {
        prefs.edit().putString("anilist_token", token).apply()
        _token.value = token
        ReleaseSyncScheduler.runNow(appContext)
    }

    fun logout() {
        prefs.edit().remove("anilist_token").apply()
        _token.value = null
        ReleaseSyncScheduler.runNow(appContext)
    }

    fun openLogin(context: Context): Boolean {
        val uri = Uri.parse(AUTHORIZE_URL)
        return runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
    }

    /** True once a redirect URL carries the token; extract it with [extractToken]. */
    fun isRedirect(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "http" && uri.host == "localhost" && uri.fragment?.contains("access_token=") == true
    }

    fun extractToken(url: String): String? {
        if (!url.contains("access_token=")) return null
        return url.substringAfter("access_token=").substringBefore("&").takeIf { it.isNotBlank() }
    }
}

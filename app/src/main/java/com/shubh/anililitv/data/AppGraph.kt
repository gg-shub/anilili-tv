package com.shubh.anililitv.data

import android.content.Context
import com.shubh.anililitv.data.cache.AppCache
import com.shubh.anililitv.data.remote.AniListClient
import com.shubh.anililitv.data.remote.AniSkipClient
import com.shubh.anililitv.data.remote.AnivexaClient
import com.shubh.anililitv.data.remote.JikanClient
import com.shubh.anililitv.data.remote.KonohaClient
import com.shubh.anililitv.data.remote.MalClient
import com.shubh.anililitv.data.remote.PipeClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Cache
import java.io.File
import java.util.concurrent.TimeUnit

object AppGraph {
    lateinit var repository: MiruroRepository
        private set
    lateinit var httpClient: OkHttpClient
        private set

    var isTv: Boolean = false
        private set

    fun init(context: Context) {
        if (::repository.isInitialized) return

        isTv = (context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager)
            ?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        httpClient = OkHttpClient.Builder()
            .cache(Cache(File(context.applicationContext.cacheDir, "http"), com.shubh.anililitv.data.settings.SettingsStore.cacheSizeMb.value * 1024L * 1024L))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val aniList = AniListClient(httpClient, json)
        val cache = AppCache(context, json)
        repository = MiruroRepository(
            aniList = aniList,
            pipe = PipeClient(json),
            anivexa = AnivexaClient(context, httpClient, json, aniList, cache),
            jikan = JikanClient(httpClient, json),
            aniSkip = AniSkipClient(httpClient, json),
            mal = MalClient(httpClient, json),
            konoha = KonohaClient(context, httpClient, json, cache),
            cache = cache,
        )
    }
}

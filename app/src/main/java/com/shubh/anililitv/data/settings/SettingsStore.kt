package com.shubh.anililitv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.diagnostics.CrashReporter
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class MenuLanguage(val storedValue: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    SPANISH("es");

    fun usesSpanish(systemLanguage: String = Locale.getDefault().language): Boolean =
        this == SPANISH || (this == SYSTEM && systemLanguage.equals("es", ignoreCase = true))

    companion object {
        fun fromStored(value: String?): MenuLanguage = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

enum class EpisodeLayout(val storedValue: String) {
    LIST("list"),
    GRID("grid");

    fun toggled(): EpisodeLayout = if (this == LIST) GRID else LIST

    companion object {
        fun fromStored(value: String?): EpisodeLayout =
            entries.firstOrNull { it.storedValue == value } ?: LIST
    }
}

enum class EpisodeListMode(val storedValue: String) {
    HORIZONTAL("horizontal"),
    VERTICAL("vertical");
    
    companion object {
        fun fromStored(value: String?): EpisodeListMode =
            entries.firstOrNull { it.storedValue == value } ?: HORIZONTAL
    }
}

enum class PosterSize(val storedValue: String, val label: String, val widthDp: Int) {
    SMALL("small", "Small", 110),
    MEDIUM("medium", "Medium", 130),
    DEFAULT("default", "Default", 150),
    LARGE("large", "Large", 180);

    companion object {
        fun fromStored(value: String?): PosterSize =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

enum class PlayerContentScale(val storedValue: String, val label: String) {
    FIT("fit", "Fit"),
    CROP("crop", "Crop"),
    FILL("fill", "Fill");

    companion object {
        fun fromStored(value: String?): PlayerContentScale =
            entries.firstOrNull { it.storedValue == value } ?: FIT
    }
}

enum class DefaultQuality(val storedValue: String, val label: String) {
    AUTO("auto", "Auto"),
    HIGHEST("highest", "Highest"),
    P1080("1080", "1080p"),
    P720("720", "720p"),
    P480("480", "480p"),
    P360("360", "360p");

    fun pickHeight(heights: List<Int>): Int? = when (this) {
        AUTO -> null
        HIGHEST -> heights.maxOrNull()
        else -> {
            val target = storedValue.toInt()
            heights.filter { it <= target }.maxOrNull() ?: heights.minOrNull()
        }
    }

    companion object {
        fun fromStored(value: String?): DefaultQuality =
            entries.firstOrNull { it.storedValue == value } ?: HIGHEST
    }
}

enum class DownloadQuality(
    val storedValue: String,
    val label: String,
    val maxHeight: Int?,
) {
    BEST("best", "Best available", null),
    P1080("1080", "1080p", 1080),
    P720("720", "720p", 720),
    P480("480", "480p", 480),
    P360("360", "360p", 360);

    companion object {
        fun fromStored(value: String?): DownloadQuality =
            entries.firstOrNull { it.storedValue == value } ?: BEST
    }
}

enum class SlideshowCategory(val storedValue: String, val label: String) {
    TRENDING_NOW("trending", "Trending Now"),
    POPULAR_THIS_SEASON("popular", "Popular This Season"),
    LATEST_RELEASES("latest", "Latest Releases"),
    TOP_RATED("top_rated", "Top Rated");

    companion object {
        fun fromStored(value: String?): SlideshowCategory =
            entries.firstOrNull { it.storedValue == value } ?: TRENDING_NOW
    }
}

enum class DownloadDestination(val storedValue: String, val label: String) {
    APP_ONLY("app", "Anilili library"),
    DEVICE_ONLY("device", "Device Downloads"),
    BOTH("both", "Both");

    val includesApp: Boolean get() = this != DEVICE_ONLY
    val includesDevice: Boolean get() = this != APP_ONLY

    companion object {
        fun fromStored(value: String?): DownloadDestination =
            entries.firstOrNull { it.storedValue == value } ?: DEVICE_ONLY
    }
}

private fun deviceDefaultQuality(): DefaultQuality =
    if (AppGraph.isTv) DefaultQuality.AUTO else DefaultQuality.HIGHEST

const val DEFAULT_PREFERRED_PROVIDER = "auto"

const val MAX_SERVER_PRIORITY = 3

object SettingsStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: DataStore<Preferences>

    private val _autoplay = MutableStateFlow(true)
    val autoplay = _autoplay.asStateFlow()

    private val _autoSyncAniList = MutableStateFlow(true)
    val autoSyncAniList = _autoSyncAniList.asStateFlow()

    private val _preferDub = MutableStateFlow(false)
    val preferDub = _preferDub.asStateFlow()

    private val _releaseNotifications = MutableStateFlow(true)
    val releaseNotifications = _releaseNotifications.asStateFlow()

    private val _syncSavedToAniList = MutableStateFlow(true)
    val syncSavedToAniList = _syncSavedToAniList.asStateFlow()

    private val _autoSkipIntroOutro = MutableStateFlow(false)
    val autoSkipIntroOutro = _autoSkipIntroOutro.asStateFlow()

    private val _hideAdultContent = MutableStateFlow(true)
    val hideAdultContent = _hideAdultContent.asStateFlow()

    private val _playerGestures = MutableStateFlow(true)
    val playerGestures = _playerGestures.asStateFlow()

    private val _subtitlesWithDub = MutableStateFlow(false)
    val subtitlesWithDub = _subtitlesWithDub.asStateFlow()

    private val _updateCheckOnLaunch = MutableStateFlow(true)
    val updateCheckOnLaunch = _updateCheckOnLaunch.asStateFlow()

    private val _cinematicSlideshow = MutableStateFlow(false)
    val cinematicSlideshow = _cinematicSlideshow.asStateFlow()

    private val _slideshowCategory = MutableStateFlow(SlideshowCategory.TRENDING_NOW)
    val slideshowCategory = _slideshowCategory.asStateFlow()

    private val _cacheSizeMb = MutableStateFlow(400)
    val cacheSizeMb = _cacheSizeMb.asStateFlow()

    private val _useLegacyDetailLayout = MutableStateFlow(false)
    val useLegacyDetailLayout = _useLegacyDetailLayout.asStateFlow()

    private val _dynamicTitleLogos = MutableStateFlow(true)
    val dynamicTitleLogos = _dynamicTitleLogos.asStateFlow()

    private val _cinematicBackdrops = MutableStateFlow(true)
    val cinematicBackdrops = _cinematicBackdrops.asStateFlow()
    
    private val _logoLanguagePriority = MutableStateFlow(true)
    val logoLanguagePriority = _logoLanguagePriority.asStateFlow()

    private val _captionStyle = MutableStateFlow(CaptionStyle())
    val captionStyle = _captionStyle.asStateFlow()

    private val _menuLanguage = MutableStateFlow(MenuLanguage.SYSTEM)
    val menuLanguage = _menuLanguage.asStateFlow()

    private val _defaultQuality = MutableStateFlow(deviceDefaultQuality())
    val defaultQuality = _defaultQuality.asStateFlow()

    private val _playerContentScale = MutableStateFlow(PlayerContentScale.FIT)
    val playerContentScale = _playerContentScale.asStateFlow()

    private val _downloadQuality = MutableStateFlow(DownloadQuality.BEST)
    val downloadQuality = _downloadQuality.asStateFlow()

    private val _downloadDestination = MutableStateFlow(DownloadDestination.DEVICE_ONLY)
    val downloadDestination = _downloadDestination.asStateFlow()

    private val _episodeLayout = MutableStateFlow(EpisodeLayout.LIST)
    val episodeLayout = _episodeLayout.asStateFlow()

    private val _episodeListMode = MutableStateFlow(EpisodeListMode.HORIZONTAL)
    val episodeListMode = _episodeListMode.asStateFlow()

    private val _posterSize = MutableStateFlow(PosterSize.DEFAULT)
    val posterSize = _posterSize.asStateFlow()

    private val _lastWorkingPipeOrigin = MutableStateFlow("")
    val lastWorkingPipeOrigin = _lastWorkingPipeOrigin.asStateFlow()

    private val _serverPriority = MutableStateFlow<List<String>>(emptyList())
    val serverPriority = _serverPriority.asStateFlow()

    private val _preferredProvider = MutableStateFlow(DEFAULT_PREFERRED_PROVIDER)
    val preferredProvider = _preferredProvider.asStateFlow()
    private val loaded = MutableStateFlow(false)

    fun init(context: Context) {
        val app = context.applicationContext
        store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { app.preferencesDataStoreFile("anilili_settings") },
        )
        scope.launch {
            runCatching { migrateLegacyPreferences(app) }
                .onFailure { CrashReporter.logNonFatal("Settings migration failed", it) }
            store.data
                .catch { error ->
                    if (error !is IOException) CrashReporter.logNonFatal("Settings read failed", error)
                    emit(emptyPreferences())
                }
                .collect(::applyPreferences)
        }
    }

    fun setAutoplay(value: Boolean) = save(AUTOPLAY, value, _autoplay)
    fun setAutoSyncAniList(value: Boolean) = save(AUTO_SYNC, value, _autoSyncAniList)
    fun setPreferDub(value: Boolean) = save(PREFER_DUB, value, _preferDub)
    fun setReleaseNotifications(value: Boolean) = save(RELEASE_NOTIFICATIONS, value, _releaseNotifications)
    fun setSyncSavedToAniList(value: Boolean) = save(SYNC_SAVED_TO_ANILIST, value, _syncSavedToAniList)
    fun setAutoSkipIntroOutro(value: Boolean) = save(AUTO_SKIP_INTRO_OUTRO, value, _autoSkipIntroOutro)
    fun setHideAdultContent(value: Boolean) = save(HIDE_ADULT_CONTENT, value, _hideAdultContent)
    fun setSubtitlesWithDub(value: Boolean) = save(SUBTITLES_WITH_DUB, value, _subtitlesWithDub)
    fun setUpdateCheckOnLaunch(value: Boolean) = save(UPDATE_CHECK_ON_LAUNCH, value, _updateCheckOnLaunch)
    fun setCacheSizeMb(value: Int) {
        _cacheSizeMb.value = value
        scope.launch { store.edit { it[CACHE_SIZE_MB] = value } }
    }
    fun setCinematicSlideshow(value: Boolean) {
        _cinematicSlideshow.value = value
        if (value) {
            _slideshowCategory.value = SlideshowCategory.TOP_RATED
        }
        scope.launch {
            store.edit { prefs ->
                prefs[CINEMATIC_SLIDESHOW] = value
                if (value) {
                    prefs[SLIDESHOW_CATEGORY] = SlideshowCategory.TOP_RATED.storedValue
                }
            }
        }
    }
    fun setSlideshowCategory(value: SlideshowCategory) {
        _slideshowCategory.value = value
        scope.launch { store.edit { it[SLIDESHOW_CATEGORY] = value.storedValue } }
    }
    fun setUseLegacyDetailLayout(value: Boolean) = save(USE_LEGACY_DETAIL_LAYOUT, value, _useLegacyDetailLayout)
    fun setDynamicTitleLogos(value: Boolean) = save(DYNAMIC_TITLE_LOGOS, value, _dynamicTitleLogos)
    fun setCinematicBackdrops(value: Boolean) = save(CINEMATIC_BACKDROPS, value, _cinematicBackdrops)
    fun setLogoLanguagePriority(value: Boolean) = save(LOGO_LANGUAGE_PRIORITY, value, _logoLanguagePriority)

    fun setCaptionBackgroundOpacity(percent: Int) =
        editCaptionStyle { it.copy(backgroundOpacityPercent = percent.coerceIn(0, 100)) }
    fun setCaptionBackgroundColor(value: CaptionBackgroundColor) =
        editCaptionStyle { it.copy(backgroundColor = value) }
    fun setCaptionTextScale(percent: Int) =
        editCaptionStyle { it.copy(textScalePercent = percent.coerceIn(CaptionStyle.MIN_TEXT_SCALE_PERCENT, CaptionStyle.MAX_TEXT_SCALE_PERCENT)) }
    fun setCaptionBold(value: Boolean) = editCaptionStyle { it.copy(boldText = value) }
    fun setCaptionBottomMargin(percent: Int) =
        editCaptionStyle { it.copy(bottomMarginPercent = percent.coerceIn(CaptionStyle.MIN_BOTTOM_MARGIN_PERCENT, CaptionStyle.MAX_BOTTOM_MARGIN_PERCENT)) }
    fun setCaptionTextColor(value: CaptionTextColor) = editCaptionStyle { it.copy(textColor = value) }
    fun setCaptionEdgeStyle(value: CaptionEdgeStyle) = editCaptionStyle { it.copy(edgeStyle = value) }
    fun resetCaptionStyle() = editCaptionStyle { CaptionStyle() }

    fun setDefaultQuality(value: DefaultQuality) {
        _defaultQuality.value = value
        scope.launch { store.edit { it[DEFAULT_QUALITY] = value.storedValue } }
    }

    fun setPlayerContentScale(value: PlayerContentScale) {
        _playerContentScale.value = value
        scope.launch { store.edit { it[PLAYER_CONTENT_SCALE] = value.storedValue } }
    }

    fun setDownloadQuality(value: DownloadQuality) {
        _downloadQuality.value = value
        scope.launch { store.edit { it[DOWNLOAD_QUALITY] = value.storedValue } }
    }

    fun setDownloadDestination(value: DownloadDestination) {
        _downloadDestination.value = value
        scope.launch { store.edit { it[DOWNLOAD_DESTINATION] = value.storedValue } }
    }

    fun setEpisodeLayout(value: EpisodeLayout) {
        _episodeLayout.value = value
        scope.launch { store.edit { it[EPISODE_LAYOUT] = value.storedValue } }
    }

    fun setEpisodeListMode(value: EpisodeListMode) {
        _episodeListMode.value = value
        scope.launch { store.edit { it[EPISODE_LIST_MODE] = value.storedValue } }
    }

    fun setPosterSize(value: PosterSize) {
        _posterSize.value = value
        scope.launch { store.edit { it[POSTER_SIZE] = value.storedValue } }
    }

    fun setMenuLanguage(value: MenuLanguage) {
        _menuLanguage.value = value
        scope.launch { store.edit { it[MENU_LANGUAGE] = value.storedValue } }
    }
    private fun applyServerPriority(value: List<String>) {
        val clean = value.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != DEFAULT_PREFERRED_PROVIDER }
            .distinct()
            .take(MAX_SERVER_PRIORITY)
        _serverPriority.value = clean
        _preferredProvider.value = clean.firstOrNull() ?: DEFAULT_PREFERRED_PROVIDER
    }

    fun setPlayerGestures(value: Boolean) {
        _playerGestures.value = value
        scope.launch { store.edit { it[PLAYER_GESTURES] = value } }
    }

    fun setLastWorkingPipeOrigin(value: String) {
        if (_lastWorkingPipeOrigin.value == value) return
        _lastWorkingPipeOrigin.value = value
        scope.launch { store.edit { it[LAST_PIPE_ORIGIN] = value } }
    }

    fun setServerPriority(value: List<String>) {
        applyServerPriority(value)
        val stored = _serverPriority.value.joinToString(",")
        scope.launch { store.edit { it[SERVER_PRIORITY] = stored } }
    }

    fun setPreferredProvider(value: String) {
        val name = value.trim().lowercase().ifBlank { DEFAULT_PREFERRED_PROVIDER }
        if (name == DEFAULT_PREFERRED_PROVIDER) {
            setServerPriority(emptyList())
        } else {
            setServerPriority(listOf(name) + _serverPriority.value.filterNot { it == name })
        }
    }

    suspend fun awaitLoaded() {
        loaded.first { it }
    }

    private fun save(key: Preferences.Key<Boolean>, value: Boolean, state: MutableStateFlow<Boolean>) {
        state.value = value
        scope.launch { store.edit { it[key] = value } }
    }

    private fun editCaptionStyle(transform: (CaptionStyle) -> CaptionStyle) {
        val next = transform(_captionStyle.value)
        _captionStyle.value = next
        scope.launch {
            store.edit { prefs ->
                prefs[CAPTION_BACKGROUND_OPACITY] = next.backgroundOpacityPercent
                prefs[CAPTION_BACKGROUND_COLOR] = next.backgroundColor.storedValue
                prefs[CAPTION_TEXT_SCALE] = next.textScalePercent
                prefs[CAPTION_BOLD_TEXT] = next.boldText
                prefs[CAPTION_BOTTOM_MARGIN] = next.bottomMarginPercent
                prefs[CAPTION_TEXT_COLOR] = next.textColor.storedValue
                prefs[CAPTION_EDGE_STYLE] = next.edgeStyle.storedValue
            }
        }
    }

    internal fun readCaptionStyle(prefs: Preferences): CaptionStyle = CaptionStyle(
        backgroundOpacityPercent = prefs[CAPTION_BACKGROUND_OPACITY]?.coerceIn(0, 100)
            ?: CaptionStyle.DEFAULT_BACKGROUND_OPACITY_PERCENT,
        backgroundColor = CaptionBackgroundColor.fromStored(prefs[CAPTION_BACKGROUND_COLOR]),
        textScalePercent = prefs[CAPTION_TEXT_SCALE]?.coerceIn(CaptionStyle.MIN_TEXT_SCALE_PERCENT, CaptionStyle.MAX_TEXT_SCALE_PERCENT)
            ?: CaptionStyle.DEFAULT_TEXT_SCALE_PERCENT,
        boldText = prefs[CAPTION_BOLD_TEXT] ?: CaptionStyle.DEFAULT_BOLD_TEXT,
        bottomMarginPercent = prefs[CAPTION_BOTTOM_MARGIN]?.coerceIn(CaptionStyle.MIN_BOTTOM_MARGIN_PERCENT, CaptionStyle.MAX_BOTTOM_MARGIN_PERCENT)
            ?: CaptionStyle.DEFAULT_BOTTOM_MARGIN_PERCENT,
        textColor = CaptionTextColor.fromStored(prefs[CAPTION_TEXT_COLOR]),
        edgeStyle = CaptionEdgeStyle.fromStored(prefs[CAPTION_EDGE_STYLE]),
    )

    private fun applyPreferences(prefs: Preferences) {
        _autoplay.value = prefs[AUTOPLAY] ?: true
        _autoSyncAniList.value = prefs[AUTO_SYNC] ?: true
        _preferDub.value = prefs[PREFER_DUB] ?: false
        _releaseNotifications.value = prefs[RELEASE_NOTIFICATIONS] ?: true
        _syncSavedToAniList.value = prefs[SYNC_SAVED_TO_ANILIST] ?: true
        _autoSkipIntroOutro.value = prefs[AUTO_SKIP_INTRO_OUTRO] ?: false
        _hideAdultContent.value = prefs[HIDE_ADULT_CONTENT] ?: true
        _subtitlesWithDub.value = prefs[SUBTITLES_WITH_DUB] ?: false
        _updateCheckOnLaunch.value = prefs[UPDATE_CHECK_ON_LAUNCH] ?: true
        _cinematicSlideshow.value = prefs[CINEMATIC_SLIDESHOW] ?: false
        _slideshowCategory.value = SlideshowCategory.fromStored(prefs[SLIDESHOW_CATEGORY])
        _cacheSizeMb.value = prefs[CACHE_SIZE_MB] ?: 400
        _useLegacyDetailLayout.value = prefs[USE_LEGACY_DETAIL_LAYOUT] ?: false
        _dynamicTitleLogos.value = prefs[DYNAMIC_TITLE_LOGOS] ?: true
        _cinematicBackdrops.value = prefs[CINEMATIC_BACKDROPS] ?: true
        _logoLanguagePriority.value = prefs[LOGO_LANGUAGE_PRIORITY] ?: true
        _captionStyle.value = readCaptionStyle(prefs)
        _menuLanguage.value = MenuLanguage.fromStored(prefs[MENU_LANGUAGE])
        _defaultQuality.value = prefs[DEFAULT_QUALITY]?.let(DefaultQuality::fromStored)
            ?: deviceDefaultQuality()
        _playerContentScale.value = PlayerContentScale.fromStored(prefs[PLAYER_CONTENT_SCALE])
        _downloadQuality.value = DownloadQuality.fromStored(prefs[DOWNLOAD_QUALITY])
        _downloadDestination.value = DownloadDestination.fromStored(prefs[DOWNLOAD_DESTINATION])
        _episodeLayout.value = EpisodeLayout.fromStored(prefs[EPISODE_LAYOUT])
        _episodeListMode.value = EpisodeListMode.fromStored(prefs[EPISODE_LIST_MODE])
        _posterSize.value = PosterSize.fromStored(prefs[POSTER_SIZE])
        applyServerPriority(
            prefs[SERVER_PRIORITY]?.takeIf(String::isNotBlank)?.split(",")
                ?: listOfNotNull(prefs[PREFERRED_PROVIDER]?.takeIf(String::isNotBlank)),
        )
        _playerGestures.value = prefs[PLAYER_GESTURES] ?: true
        _lastWorkingPipeOrigin.value = prefs[LAST_PIPE_ORIGIN].orEmpty()
        loaded.value = true
    }

    private suspend fun migrateLegacyPreferences(context: Context) {
        val current = store.data.first()
        if (current[MIGRATED] == true) return
        val legacy = context.getSharedPreferences("anilili_settings", Context.MODE_PRIVATE)
        store.edit { prefs ->
            prefs[AUTOPLAY] = legacy.getBoolean("autoplay", true)
            prefs[AUTO_SYNC] = legacy.getBoolean("auto_sync_anilist", true)
            prefs[PREFER_DUB] = legacy.getBoolean("prefer_dub", false)
            prefs[RELEASE_NOTIFICATIONS] = true
            prefs[SYNC_SAVED_TO_ANILIST] = true
            prefs[AUTO_SKIP_INTRO_OUTRO] = false
            prefs[MIGRATED] = true
        }
        legacy.edit().clear().apply()
    }

    private val AUTOPLAY = booleanPreferencesKey("autoplay")
    private val AUTO_SYNC = booleanPreferencesKey("auto_sync_anilist")
    private val PREFER_DUB = booleanPreferencesKey("prefer_dub")
    private val RELEASE_NOTIFICATIONS = booleanPreferencesKey("release_notifications")
    private val SYNC_SAVED_TO_ANILIST = booleanPreferencesKey("sync_saved_to_anilist")
    private val AUTO_SKIP_INTRO_OUTRO = booleanPreferencesKey("auto_skip_intro_outro")
    private val HIDE_ADULT_CONTENT = booleanPreferencesKey("hide_adult_content")
    private val SUBTITLES_WITH_DUB = booleanPreferencesKey("subtitles_with_dub")
    private val UPDATE_CHECK_ON_LAUNCH = booleanPreferencesKey("update_check_on_launch")
    private val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
    private val CINEMATIC_SLIDESHOW = booleanPreferencesKey("cinematic_slideshow")
    private val SLIDESHOW_CATEGORY = stringPreferencesKey("slideshow_category")
    private val USE_LEGACY_DETAIL_LAYOUT = booleanPreferencesKey("use_legacy_detail_layout")
    private val DYNAMIC_TITLE_LOGOS = booleanPreferencesKey("dynamic_title_logos")
    private val CINEMATIC_BACKDROPS = booleanPreferencesKey("cinematic_backdrops")
    private val LOGO_LANGUAGE_PRIORITY = booleanPreferencesKey("logo_language_priority")
    private val CAPTION_BACKGROUND_OPACITY = intPreferencesKey("caption_background_opacity")
    private val CAPTION_BACKGROUND_COLOR = stringPreferencesKey("caption_background_color")
    private val CAPTION_TEXT_SCALE = intPreferencesKey("caption_text_scale")
    private val CAPTION_BOLD_TEXT = booleanPreferencesKey("caption_bold_text")
    private val CAPTION_BOTTOM_MARGIN = intPreferencesKey("caption_bottom_margin")
    private val CAPTION_TEXT_COLOR = stringPreferencesKey("caption_text_color")
    private val CAPTION_EDGE_STYLE = stringPreferencesKey("caption_edge_style")
    private val MENU_LANGUAGE = stringPreferencesKey("menu_language")
    private val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    private val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
    private val DOWNLOAD_DESTINATION = stringPreferencesKey("download_destination")
    private val EPISODE_LAYOUT = stringPreferencesKey("episode_layout")
    private val EPISODE_LIST_MODE = stringPreferencesKey("episode_list_mode")
    private val POSTER_SIZE = stringPreferencesKey("poster_size")
    private val PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
    private val SERVER_PRIORITY = stringPreferencesKey("server_priority")
    private val LAST_PIPE_ORIGIN = stringPreferencesKey("last_pipe_origin")
    private val PLAYER_GESTURES = booleanPreferencesKey("player_gestures")
    private val PLAYER_CONTENT_SCALE = stringPreferencesKey("player_content_scale")
    private val MIGRATED = booleanPreferencesKey("migrated_from_shared_preferences")
}

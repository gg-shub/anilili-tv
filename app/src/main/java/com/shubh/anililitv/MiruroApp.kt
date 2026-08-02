package com.shubh.anililitv

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.os.StrictMode
import android.os.SystemClock
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.shubh.anililitv.data.AppGraph
import com.shubh.anililitv.data.auth.AuthManager
import com.shubh.anililitv.data.auth.MalAuthManager
import com.shubh.anililitv.diagnostics.CrashReporter
import com.shubh.anililitv.data.library.LibraryStore
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.data.reminder.ReminderManager
import com.shubh.anililitv.data.reminder.AutomaticReleaseManager
import com.shubh.anililitv.data.reminder.AniListNotificationPushManager
import com.shubh.anililitv.data.reminder.ReleaseSyncScheduler
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import com.shubh.anililitv.playback.EpisodeDownloads

class MiruroApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.init(this)
        DiagnosticsLog.event("MiruroApp.onCreate start")
        DiagnosticsLog.installLifecycleCallbacks(this)
        DiagnosticsLog.startMainThreadWatchdog()
        DiagnosticsLog.snapshot(this, "MiruroApp.onCreate")
        DiagnosticsLog.event(
            "MiruroApp process=${currentProcessName() ?: "unknown"} pid=${Process.myPid()} " +
                "thread=${Thread.currentThread().name} abis=${Build.SUPPORTED_ABIS.joinToString()}",
        )
        DiagnosticsLog.webViewPackage("MiruroApp.onCreate")
        if (isDiagnosticsProcess()) {
            DiagnosticsLog.event("MiruroApp diagnostics process; skipping normal app init")
            return
        }
        runCatching { EpisodeDownloads.initialize(this) }
            .onFailure { DiagnosticsLog.throwable("EpisodeDownloads initialization failed", it) }
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build(),
            )
        }
        diagnosticsStep("CrashReporter.init") { CrashReporter.init(this) }
        diagnosticsStep("AppGraph.init") { AppGraph.init(this) }
        diagnosticsStep("LibraryStore.init") { LibraryStore.init(this) }
        diagnosticsStep("SearchHistoryStore.init") { com.shubh.anililitv.data.library.SearchHistoryStore.init(this) }
        diagnosticsStep("AuthManager.init") { AuthManager.init(this) }
        diagnosticsStep("MalAuthManager.init") { MalAuthManager.init(this, AppGraph.httpClient) }
        diagnosticsStep("SettingsStore.init") { SettingsStore.init(this) }
        diagnosticsStep("ReminderManager.init") { ReminderManager.init(this) }
        diagnosticsStep("AutomaticReleaseManager.init") { AutomaticReleaseManager.init(this) }
        diagnosticsStep("AniListNotificationPushManager.init") { AniListNotificationPushManager.init(this) }
        diagnosticsStep("ReleaseSyncScheduler.schedule") { ReleaseSyncScheduler.schedule(this) }
        DiagnosticsLog.event("MiruroApp.onCreate complete")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        DiagnosticsLog.event("MiruroApp.onTrimMemory level=$level")
        DiagnosticsLog.snapshot(this, "trimMemory")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        DiagnosticsLog.event("MiruroApp.onLowMemory")
        DiagnosticsLog.snapshot(this, "lowMemory")
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { AppGraph.httpClient.newBuilder().cache(null).build() }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(com.shubh.anililitv.data.settings.SettingsStore.cacheSizeMb.value * 1024L * 1024L)
                .build()
        }
        .crossfade(150)
        .respectCacheHeaders(false)
        .build()

    private inline fun diagnosticsStep(name: String, block: () -> Unit) {
        val startedAt = SystemClock.elapsedRealtime()
        DiagnosticsLog.event("$name start")
        try {
            block()
            DiagnosticsLog.event("$name complete in ${SystemClock.elapsedRealtime() - startedAt}ms")
        } catch (throwable: Throwable) {
            DiagnosticsLog.throwable("$name failed after ${SystemClock.elapsedRealtime() - startedAt}ms", throwable)
            throw throwable
        }
    }

    private fun isDiagnosticsProcess(): Boolean = currentProcessName() == "$packageName:diagnostics"

    private fun currentProcessName(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = Process.myPid()
            @Suppress("DEPRECATION")
            (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }
}

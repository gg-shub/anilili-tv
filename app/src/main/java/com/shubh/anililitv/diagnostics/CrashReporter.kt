package com.shubh.anililitv.diagnostics

import android.content.Context
import android.os.Build
import com.shubh.anililitv.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val MAX_LOG_BYTES = 200_000L
    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        val file = File(context.filesDir, "last_crash.txt")
        logFile = file
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                DiagnosticsLog.throwable("FATAL on thread ${thread.name}", throwable)
                file.appendText(entry("FATAL on thread ${thread.name}", throwable))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logNonFatal(what: String, throwable: Throwable) {
        val file = logFile ?: return
        runCatching {
            DiagnosticsLog.throwable("NON-FATAL: $what", throwable)
            if (file.length() < MAX_LOG_BYTES) file.appendText(entry("NON-FATAL: $what", throwable))
        }
    }

    fun pendingReport(): String? = logFile
        ?.takeIf { it.exists() && it.length() > 0 }
        ?.runCatching { readText() }
        ?.getOrNull()

    fun clear() {
        runCatching { logFile?.delete() }
    }

    private fun entry(headline: String, throwable: Throwable): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("== $headline ==")
            appendLine("time: $time")
            appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine(trace)
            appendLine()
        }
    }
}

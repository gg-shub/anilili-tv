package com.miruronative.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.miruronative.MainActivity
import com.miruronative.R
import com.miruronative.ui.nav.Routes

/**
 * Runs one episode export as long-running foreground work.
 *
 * WorkManager rather than a hand-rolled service: an export is usually kicked off by a download
 * finishing, which can happen while the app is in the background — exactly the case where starting
 * a foreground service directly throws on Android 12+. WorkManager owns that dance, and it also
 * survives process death, so a transmux interrupted by a low-memory kill resumes instead of leaving
 * a half-written file behind.
 */
class EpisodeExportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val downloadId: String? get() = inputData.getString(KEY_DOWNLOAD_ID)

    override suspend fun doWork(): Result {
        val id = downloadId ?: return Result.failure()
        // Best effort: if the system refuses the foreground promotion the export still runs, it
        // just loses its notification and the protection that comes with it.
        runCatching { setForeground(foregroundInfo(null)) }
        val outcome = EpisodeExport.performExport(applicationContext, id) { percent ->
            runCatching { setForeground(foregroundInfo(percent)) }
        }
        // A failure is already recorded in the export status and surfaced in the library, so the
        // work itself is done either way; retrying a codec or storage failure would just loop.
        return if (outcome.isSuccess) Result.success() else Result.failure()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    private fun foregroundInfo(percent: Int?): ForegroundInfo {
        val context = applicationContext
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.export_notification_title))
            .setContentText(
                percent?.let { context.getString(R.string.export_notification_progress, it) }
                    ?: context.getString(R.string.export_notification_preparing),
            )
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setContentIntent(libraryPendingIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun libraryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Routes.EXTRA_ROUTE, Routes.MORE)
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.export_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.export_notification_channel_description)
            },
        )
    }

    companion object {
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val CHANNEL_ID = "episode_exports"
        private const val NOTIFICATION_ID = 7_301

        private fun workName(downloadId: String) = "episode-export:$downloadId"

        fun enqueue(context: Context, downloadId: String) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(downloadId),
                // KEEP, not REPLACE: the observer fires on every download-list change, and an
                // export already under way must not be restarted from zero each time.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<EpisodeExportWorker>()
                    .setInputData(workDataOf(KEY_DOWNLOAD_ID to downloadId))
                    .build(),
            )
        }

    }
}

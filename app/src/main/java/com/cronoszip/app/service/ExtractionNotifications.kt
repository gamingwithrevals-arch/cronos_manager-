package com.cronoszip.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** The notification channel and the notifications an extraction shows while it runs and when it ends. */
object ExtractionNotifications {
    private const val CHANNEL_ID = "extraction"
    const val PROGRESS_ID = 4101
    private const val RESULT_ID = 4102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Extraction progress", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Shows progress while an archive is extracted."
        manager.createNotificationChannel(channel)
    }

    /** The ongoing progress notification. A null [percent] shows an indeterminate bar. */
    fun ongoing(context: Context, title: String, text: String, percent: Int?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (percent == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, percent.coerceIn(0, 100), false)
        }
        return builder.build()
    }

    /** Refreshes the progress notification with the latest percentage and time left. */
    @Suppress("MissingPermission")
    fun update(context: Context, percent: Int, text: String) {
        val notification = ongoing(context, "Extracting archive", text, percent)
        runCatching { NotificationManagerCompat.from(context).notify(PROGRESS_ID, notification) }
    }

    /** Posts the notice that an extraction finished, failed, or was paused. */
    @Suppress("MissingPermission")
    fun finished(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(RESULT_ID, notification) }
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Time left as text, based on the average speed so far. */
fun etaText(done: Long, total: Long, speed: Double): String {
    val remaining = (total - done).coerceAtLeast(0L)
    if (total <= 0L || speed <= 0.0) return "calculating time left"
    if (remaining == 0L) return "almost done"
    val seconds = (remaining / speed).toLong()
    return when {
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }
}

package com.cronoszip.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the app running in the background while an extraction is in progress. The extraction itself
 * is still driven by the view model. This service only holds the process in the foreground.
 */
class ExtractionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ExtractionNotifications.ensureChannel(this)
        val notification = ExtractionNotifications.ongoing(this, "Extracting archive", "Starting…", percent = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ExtractionNotifications.PROGRESS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ExtractionNotifications.PROGRESS_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, ExtractionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ExtractionService::class.java))
        }
    }
}

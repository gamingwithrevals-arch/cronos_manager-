package com.cronoszip.app.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the LAN share screen shows. The service keeps this up to date. */
object LanShare {
    const val DEFAULT_PORT = 2121

    data class Status(
        val running: Boolean = false,
        val addresses: List<String> = emptyList(),
        val port: Int = DEFAULT_PORT,
        val username: String = "",
        val password: String = "",
        val error: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun publish(value: Status) {
        _status.value = value
    }
}

/**
 * Runs the FTP share in the foreground so it keeps working while the app is in the background.
 * A notification with a Stop button stays visible while sharing is on.
 */
class LanServerService : Service() {
    private var server: FtpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) startSharing()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSharing()
        super.onDestroy()
    }

    private fun startSharing() {
        ensureChannel()
        val password = randomPassword()
        val port = LanShare.DEFAULT_PORT
        val addresses = localAddresses()
        // startForeground must be called promptly after startForegroundService.
        val text = addresses.firstOrNull()?.let { "$it:$port" } ?: "no network address"
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        try {
            val root = Environment.getExternalStorageDirectory()
            server = FtpServer(root, USERNAME, password, port).also { it.start() }
            LanShare.publish(
                LanShare.Status(
                    running = true,
                    addresses = addresses,
                    port = port,
                    username = USERNAME,
                    password = password,
                ),
            )
        } catch (e: Exception) {
            LanShare.publish(LanShare.Status(error = "Could not start sharing: ${e.message ?: "unknown error"}"))
            stopSelf()
        }
    }

    private fun stopSharing() {
        server?.close()
        server = null
        LanShare.publish(LanShare.Status())
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun notification(address: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LanServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Sharing files on the local network")
            .setContentText("Other phones can connect to $address")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "LAN sharing", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun randomPassword(): String {
        val random = SecureRandom()
        return buildString {
            repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }

    /** IPv4 addresses this phone can be reached on: Wi-Fi, hotspot, or both. */
    private fun localAddresses(): List<String> {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        return Collections.list(interfaces)
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    }

    companion object {
        const val ACTION_STOP = "com.cronoszip.app.lan.STOP_SHARING"
        private const val CHANNEL_ID = "lan_share"
        private const val NOTIFICATION_ID = 4201
        private const val USERNAME = "cronos"
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LanServerService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LanServerService::class.java).setAction(ACTION_STOP))
        }
    }
}

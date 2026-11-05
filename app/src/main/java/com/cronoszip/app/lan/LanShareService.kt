package com.cronoszip.app.lan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cronoszip.app.util.hasStorageAccess
import java.net.Inet4Address
import java.net.NetworkInterface
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
        val error: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun publish(value: Status) {
        _status.value = value
    }
}

/**
 * Runs the share as a foreground service so it keeps working with the screen off or the app in
 * the background. A partial wake lock and a Wi-Fi lock keep the connection alive while sharing.
 * Both locks are released when sharing stops.
 */
class LanServerService : Service() {
    private var server: FtpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) startSharing()
        // Restart automatically if the system kills the service.
        return START_STICKY
    }

    override fun onDestroy() {
        stopSharing()
        super.onDestroy()
    }

    private fun startSharing() {
        ensureChannel()
        // startForeground must be called right away, or Android stops the service with an error.
        promoteToForeground(notification("Starting…"))

        if (!hasStorageAccess(this)) {
            release()
            LanShare.publish(
                LanShare.Status(
                    error = "Turn on All files access first. Without it, other phones only see folders.",
                ),
            )
            stopSelf()
            return
        }

        val port = LanShare.DEFAULT_PORT
        val addresses = localAddresses()
        try {
            val root = Environment.getExternalStorageDirectory()
            server = FtpServer(root, port).also { it.start() }
            acquireLocks()
            promoteToForeground(notification(addresses.firstOrNull()?.let { "$it:$port" } ?: "no network address"))
            LanShare.publish(LanShare.Status(running = true, addresses = addresses, port = port))
        } catch (e: Exception) {
            release()
            LanShare.publish(LanShare.Status(error = "Could not start sharing: ${e.message ?: "unknown error"}"))
            stopSelf()
        }
    }

    private fun stopSharing() {
        release()
        LanShare.publish(LanShare.Status())
    }

    /** Closes the server, releases the locks and removes the notification. Does not touch the status. */
    private fun release() {
        runCatching { server?.close() }
        server = null
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
        runCatching { wifiLock?.let { if (it.isHeld) it.release() } }
        wifiLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CronosZip:lan")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifi?.createWifiLock(mode, "CronosZip:lan")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(text: String): Notification {
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
            .setContentText(text)
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

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LanServerService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LanServerService::class.java).setAction(ACTION_STOP))
        }
    }
}

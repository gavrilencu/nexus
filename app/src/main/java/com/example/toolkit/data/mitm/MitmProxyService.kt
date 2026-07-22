package com.example.toolkit.data.mitm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.toolkit.MainActivity
import com.example.toolkit.R
import java.net.NetworkInterface

/**
 * Foreground service that runs the local HTTP(S) MITM proxy.
 * Use with Wi‑Fi proxy = phoneIP:8888 after installing the NEXUS CA.
 */
class MitmProxyService : Service() {

    private var proxy: MitmProxyServer? = null
    private var ca: MitmCaManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProxy()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startProxy()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    private fun startProxy() {
        if (proxy != null) return
        startForeground(NOTIF_ID, buildNotification())
        ca = MitmCaManager(applicationContext)
        val p = MitmProxyServer(ca!!)
        p.start()
        proxy = p
        val ip = localIpv4() ?: "127.0.0.1"
        MitmCaptureBus.setRunning(
            true,
            mode = "proxy",
            port = p.listenPort,
            hint = "$ip:${p.listenPort}"
        )
    }

    private fun stopProxy() {
        proxy?.stop()
        proxy = null
        MitmCaptureBus.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val channelId = "nexus_mitm"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "NEXUS MITM Proxy", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, MitmProxyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hint = MitmCaptureBus.stats.value.listenHint.ifBlank { "port ${MitmProxyServer.DEFAULT_PORT}" }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NEXUS MITM Proxy")
            .setContentText("Listening on $hint — install CA for HTTPS")
            .setSmallIcon(R.drawable.ic_stat_nexus)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.toolkit.mitm.PROXY_STOP"
        private const val NOTIF_ID = 7201

        fun localIpv4(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') != true }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}

package com.example.toolkit.data.linux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.toolkit.MainActivity
import com.example.toolkit.R

/**
 * Keeps the app process at foreground priority while a Linux shell/service
 * started via [LinuxEnvironmentHolder] is running.
 *
 * Without this, Android is free to kill the whole process the moment the
 * user leaves this screen or backgrounds the app — which would silently
 * kill every process running inside the Ubuntu rootfs, including anything
 * the user deliberately started as a background service (a dev web server,
 * a worker, etc). That was the root cause of "it's not a real Linux, I
 * can't run services": there was nothing on Android's side protecting the
 * process.
 *
 * This service does not own the shell itself — [LinuxEnvironmentHolder]'s
 * shared [LinuxEnvironment] does, so the same bash/PRoot process a user
 * started from the terminal screen is what keeps running. This service's
 * only job is to hold a persistent notification (required by Android for
 * any foreground service) and stay alive so the OS doesn't reclaim the
 * process under memory pressure.
 */
class LinuxSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                LinuxEnvironmentHolder.get(this).stopShell()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startAsForeground()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "nexus_linux_session"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "NEXUS Linux session", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LinuxSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NEXUS Linux session active")
            .setContentText("Ubuntu shell and any services you started keep running in the background")
            .setSmallIcon(R.drawable.ic_stat_nexus)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.toolkit.linux.STOP"
        private const val NOTIF_ID = 7202

        fun start(context: Context) {
            val intent = Intent(context, LinuxSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stops the protective foreground service only — does not touch the shell. */
        fun stop(context: Context) {
            context.stopService(Intent(context, LinuxSessionService::class.java))
        }
    }
}

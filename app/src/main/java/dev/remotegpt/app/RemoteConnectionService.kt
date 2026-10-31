package dev.remotegpt.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the process-wide WorkbenchViewModel alive while the user explicitly
 * enables background connection. The actual SSH ownership remains in the
 * shared session object, so Activity recreation does not create a second set
 * of sockets.
 */
class RemoteConnectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(tr(R.string.ui_keep_remote_connection_active))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(NotificationCompat.Action(R.drawable.ic_notification, tr(R.string.ui_stop_keeping_alive), stopIntent()))
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, notification)
        (application as RemoteApplication).workbench().setBackgroundKeepAlive(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        (application as RemoteApplication).workbench().setBackgroundKeepAlive(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun stopIntent() = PendingIntent.getService(this, 1, Intent(this, RemoteConnectionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, tr(R.string.ui_background_connection), NotificationManager.IMPORTANCE_LOW).apply { description = tr(R.string.ui_keep_ssh_and_codex_sessions_connected) }
        )
    }

    companion object {
        private const val CHANNEL_ID = "remote_connection"
        private const val NOTIFICATION_ID = 40
        private const val ACTION_STOP = "dev.remotegpt.app.STOP_BACKGROUND_CONNECTION"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RemoteConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteConnectionService::class.java))
        }
    }
}

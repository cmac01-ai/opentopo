package org.opentopo.app.gnss

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.opentopo.app.MainActivity

/**
 * Keeps a field session alive while the display is off.
 *
 * The actual Bluetooth/USB/NTRIP objects are still owned by MainActivity in
 * this phase; the foreground service raises process priority and holds a
 * PARTIAL_WAKE_LOCK so serial/network reader coroutines are not suspended by
 * screen-off CPU sleep.
 */
class GnssKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "gnss_field_session"
        private const val NOTIFICATION_ID = 2001
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("OpenTopo · sessão GNSS ativa")
            .setContentText("Mantendo GNSS/NTRIP ativos com a tela desligada")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenTopo:GNSSFieldSession",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sessão GNSS de campo",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mantém receptor GNSS e NTRIP ativos com a tela apagada."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}

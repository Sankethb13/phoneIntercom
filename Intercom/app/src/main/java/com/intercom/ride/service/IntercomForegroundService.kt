package com.intercom.ride.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.intercom.ride.IntercomApp
import com.intercom.ride.R
import com.intercom.ride.core.ConnectionState
import com.intercom.ride.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the intercom alive with the screen off, the phone in a pocket or
 * another app in front. Holds the microphone (type "microphone") and the
 * peer link (type "connectedDevice"), a partial wake lock and a low-latency
 * Wi-Fi lock for the duration of a session only.
 */
class IntercomForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = IntercomApp.instance.session
        when (intent?.action) {
            ACTION_TOGGLE_MUTE -> { session.toggleMute(); return START_NOT_STICKY }
            ACTION_HANG_UP -> { session.disconnect(); return START_NOT_STICKY }
        }
        // Every startForegroundService() call must be answered with startForeground().
        val type = if (Build.VERSION.SDK_INT >= 30) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification("Connecting…", false), type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        started = true
        acquireLocks()
        session.onServiceStarted()
        scope.launch {
            combine(session.state, session.muted, session.audioRoute) { s, m, r -> Triple(s, m, r) }
                .collect { (s, muted, route) ->
                    val text = when (s) {
                        is ConnectionState.Connected -> "Talking with ${s.peerName} · ${route.name}"
                        is ConnectionState.Reconnecting -> "Reconnecting to ${s.peerName}…"
                        is ConnectionState.Authenticating -> "Securing link with ${s.peerName}…"
                        is ConnectionState.Requesting -> "Waiting for ${s.peer.name} to accept…"
                        else -> "Intercom"
                    }
                    ContextCompat.getSystemService(this@IntercomForegroundService, android.app.NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, buildNotification(text, muted))
                }
        }
        // Not sticky: if the system kills us, the microphone must not reopen without the user.
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String, muted: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mute = PendingIntent.getService(
            this, 1, Intent(this, IntercomForegroundService::class.java).setAction(ACTION_TOGGLE_MUTE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hang = PendingIntent.getService(
            this, 2, Intent(this, IntercomForegroundService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, IntercomApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_intercom)
            .setContentTitle(if (muted) "Intercom · mic muted" else "Intercom active")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (muted) "Unmute" else "Mute", mute)
            .addAction(0, "Disconnect", hang)
            .build()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Intercom::voice").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // safety cap: 6 hours
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Intercom::link").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (started) IntercomApp.instance.session.onServiceStopped()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        started = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IntercomService"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_TOGGLE_MUTE = "com.intercom.ride.TOGGLE_MUTE"
        private const val ACTION_HANG_UP = "com.intercom.ride.HANG_UP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IntercomForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IntercomForegroundService::class.java))
        }
    }
}

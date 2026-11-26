package com.intercom.ride

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.intercom.ride.core.SettingsRepository
import com.intercom.ride.net.ConnectionSessionManager
import com.intercom.ride.permissions.PermissionManager

class IntercomApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var session: ConnectionSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        session = ConnectionSessionManager(this, settings, PermissionManager(this))
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "intercom_active"
        lateinit var instance: IntercomApp
            private set
    }
}

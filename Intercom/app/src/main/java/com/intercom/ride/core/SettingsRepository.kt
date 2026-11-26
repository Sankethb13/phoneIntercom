package com.intercom.ride.core

import android.content.Context
import android.os.Build
import com.intercom.ride.audio.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class IntercomSettings(
    val deviceName: String,
    val deviceId: String,
    val role: Role?,
    val quality: AudioQuality,
    val noiseSuppression: Boolean,
    val autoReconnect: Boolean,
    val onboarded: Boolean,
)

/** Local-only preferences. No personal data leaves the phone except the display name shown to the peer. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("intercom", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<IntercomSettings> = _settings.asStateFlow()
    val current: IntercomSettings get() = _settings.value

    private fun load(): IntercomSettings {
        val id = prefs.getString(K_ID, null) ?: UUID.randomUUID().toString().replace("-", "").take(16).also {
            prefs.edit().putString(K_ID, it).apply()
        }
        return IntercomSettings(
            deviceName = prefs.getString(K_NAME, null) ?: Build.MODEL.take(24),
            deviceId = id,
            role = prefs.getString(K_ROLE, null)?.let { Role.fromWire(it) },
            quality = AudioQuality.fromName(prefs.getString(K_QUALITY, null)),
            noiseSuppression = prefs.getBoolean(K_NS, true),
            autoReconnect = prefs.getBoolean(K_RECONNECT, true),
            onboarded = prefs.getBoolean(K_ONBOARDED, false),
        )
    }

    fun update(transform: (IntercomSettings) -> IntercomSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString(K_NAME, s.deviceName.trim().ifEmpty { Build.MODEL }.take(24))
            .putString(K_ROLE, s.role?.name)
            .putString(K_QUALITY, s.quality.name)
            .putBoolean(K_NS, s.noiseSuppression)
            .putBoolean(K_RECONNECT, s.autoReconnect)
            .putBoolean(K_ONBOARDED, s.onboarded)
            .apply()
        _settings.value = s
    }

    private companion object {
        const val K_ID = "device_id"
        const val K_NAME = "device_name"
        const val K_ROLE = "role"
        const val K_QUALITY = "quality"
        const val K_NS = "noise_suppression"
        const val K_RECONNECT = "auto_reconnect"
        const val K_ONBOARDED = "onboarded"
    }
}

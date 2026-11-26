package com.intercom.ride.audio

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioRoute(val name: String, val isHeadset: Boolean, val isBluetooth: Boolean)

/**
 * Chooses where intercom audio goes, and re-chooses whenever a headset is
 * plugged in, paired, switched off or drops out.
 *
 * Priority: Bluetooth LE Audio headset → Bluetooth (HFP/SCO) helmet headset or
 * earbuds → wired/USB headset → built-in speaker.
 *
 * Android 12+: AudioManager.setCommunicationDevice.
 * Android 10–11: Bluetooth SCO / speakerphone legacy APIs.
 * Only the standard HFP and LE Audio voice paths are used: A2DP is a
 * playback-only profile and cannot carry a headset microphone.
 */
class AudioRouter(private val am: AudioManager) {
    private val handler = Handler(Looper.getMainLooper())
    private val _route = MutableStateFlow(AudioRoute("Phone speaker", isHeadset = false, isBluetooth = false))
    val route: StateFlow<AudioRoute> = _route.asStateFlow()
    private var started = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = reroute()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = reroute()
    }

    fun start() {
        if (started) return
        started = true
        am.registerAudioDeviceCallback(callback, handler)
        reroute()
    }

    fun stop() {
        if (!started) return
        started = false
        am.unregisterAudioDeviceCallback(callback)
        if (Build.VERSION.SDK_INT >= 31) {
            am.clearCommunicationDevice()
        } else {
            legacyStopSco()
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
    }

    /** Re-evaluate the best route (called on device changes and after audio interruptions). */
    fun reroute() {
        if (!started) return
        if (Build.VERSION.SDK_INT >= 31) applyModern() else applyLegacy()
    }

    @RequiresApi(31)
    private fun applyModern() {
        val best = am.availableCommunicationDevices.minByOrNull { priority(it.type) }
        if (best != null) {
            am.setCommunicationDevice(best)
            _route.value = describe(best)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacy() {
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val sco = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val wired = outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        when {
            sco != null -> {
                am.isSpeakerphoneOn = false
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                _route.value = describe(sco)
            }
            wired != null -> {
                legacyStopSco()
                am.isSpeakerphoneOn = false
                _route.value = describe(wired)
            }
            else -> {
                legacyStopSco()
                am.isSpeakerphoneOn = true
                _route.value = AudioRoute("Phone speaker", isHeadset = false, isBluetooth = false)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyStopSco() {
        if (am.isBluetoothScoOn) {
            am.isBluetoothScoOn = false
            am.stopBluetoothSco()
        }
    }

    private fun describe(d: AudioDeviceInfo): AudioRoute {
        val product = d.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL }
        val kind = when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
            else -> "Phone speaker"
        }
        val bt = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        val headset = bt || d.type in WIRED
        return AudioRoute(if (product != null && headset) "$kind · $product" else kind, headset, bt)
    }

    private fun priority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLE_HEADSET -> 0
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 1
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> 2
        AudioDeviceInfo.TYPE_USB_HEADSET -> 3
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 4
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 5
        else -> 9
    }

    private companion object {
        val WIRED = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}

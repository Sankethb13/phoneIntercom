package com.intercom.ride.ui

import androidx.lifecycle.ViewModel
import com.intercom.ride.IntercomApp
import com.intercom.ride.audio.AudioQuality
import com.intercom.ride.core.PeerDevice
import com.intercom.ride.core.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Screen { WELCOME, ROLE, NEARBY, SETTINGS }

/** Thin UI adapter over the session manager; holds only navigation state. */
class IntercomViewModel : ViewModel() {
    private val app = IntercomApp.instance
    val session = app.session
    val settingsRepo = app.settings

    val state = session.state
    val peers = session.peers
    val stats = session.stats
    val muted = session.muted
    val peerMuted = session.peerMuted
    val warning = session.warning
    val authDigits = session.pendingAuthDigits
    val route = session.audioRoute
    val micLevel = session.audio.micLevel
    val speakerLevel = session.audio.speakerLevel
    val interrupted = session.audio.interrupted
    val settings = settingsRepo.settings

    private val _screen = MutableStateFlow(
        if (settingsRepo.current.onboarded && settingsRepo.current.role != null) Screen.NEARBY else Screen.WELCOME,
    )
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private var previous = Screen.NEARBY

    fun go(s: Screen) {
        if (s == Screen.SETTINGS) previous = _screen.value
        _screen.value = s
    }

    fun back() {
        _screen.value = when (_screen.value) {
            Screen.SETTINGS -> previous
            Screen.ROLE -> Screen.WELCOME
            Screen.NEARBY -> Screen.ROLE
            Screen.WELCOME -> Screen.WELCOME
        }
    }

    fun chooseRole(role: Role) {
        settingsRepo.update { it.copy(role = role, onboarded = true) }
        _screen.value = Screen.NEARBY
    }

    fun startDiscovery() = session.startDiscovery()
    fun refresh() = session.refreshDiscovery()
    fun stopDiscovery() = session.stopDiscovery()
    fun connect(p: PeerDevice) = session.connect(p)
    fun accept() = session.acceptRequest()
    fun reject() = session.rejectRequest()
    fun toggleMute() = session.toggleMute()
    fun disconnect() = session.disconnect()
    fun dismissError() = session.dismissError()
    fun dismissWarning() = session.dismissWarning()

    fun setName(n: String) = settingsRepo.update { it.copy(deviceName = n.take(24)) }
    fun setQuality(q: AudioQuality) { settingsRepo.update { it.copy(quality = q) }; session.applyAudioSettings() }
    fun setNoiseSuppression(b: Boolean) { settingsRepo.update { it.copy(noiseSuppression = b) }; session.applyAudioSettings() }
    fun setAutoReconnect(b: Boolean) = settingsRepo.update { it.copy(autoReconnect = b) }
    fun setRole(r: Role) = settingsRepo.update { it.copy(role = r) }
}

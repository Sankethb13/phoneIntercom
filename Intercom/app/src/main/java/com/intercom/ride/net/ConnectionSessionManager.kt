package com.intercom.ride.net

import android.content.Context
import android.util.Log
import com.intercom.ride.audio.AudioSessionManager
import com.intercom.ride.core.AudioStats
import com.intercom.ride.core.ConnectionEvent
import com.intercom.ride.core.ConnectionRequest
import com.intercom.ride.core.ConnectionState
import com.intercom.ride.core.ConnectionStateManager
import com.intercom.ride.core.IntercomError
import com.intercom.ride.core.LinkQualityEstimator
import com.intercom.ride.core.PeerDevice
import com.intercom.ride.core.Role
import com.intercom.ride.core.SettingsRepository
import com.intercom.ride.permissions.PermissionManager
import com.intercom.ride.service.IntercomForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The intercom's brain. Coordinates discovery, the single active peer link,
 * the authentication handshake, automatic reconnection, link-quality probes
 * and the audio pipeline. UI and service only talk to this class.
 *
 * Nearby callbacks arrive on the main thread; audio callbacks on audio threads.
 */
class ConnectionSessionManager(
    private val context: Context,
    private val settings: SettingsRepository,
    val permissions: PermissionManager,
) : DeviceDiscoveryService.Listener, PeerConnectionManager.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val peerManager = PeerConnectionManager(context, this)
    private val discovery = DeviceDiscoveryService(peerManager.client, peerManager.lifecycle, this)
    private val reconnectPolicy = ReconnectPolicy()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()
    private val _stats = MutableStateFlow(AudioStats())
    val stats: StateFlow<AudioStats> = _stats.asStateFlow()
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _peerMuted = MutableStateFlow(false)
    val peerMuted: StateFlow<Boolean> = _peerMuted.asStateFlow()
    private val _warning = MutableStateFlow<IntercomError?>(null)
    /** Non-fatal notices (e.g. Wi-Fi off) shown as a banner. */
    val warning: StateFlow<IntercomError?> = _warning.asStateFlow()
    private val _pendingAuthDigits = MutableStateFlow<String?>(null)
    val pendingAuthDigits: StateFlow<String?> = _pendingAuthDigits.asStateFlow()

    val audio = AudioSessionManager(
        context,
        sendPacket = { codec, seq, ts, payload ->
            val ep = activeEndpoint
            if (ep != null && authorized) peerManager.send(ep, Message.Audio(codec, seq, ts, payload))
        },
        onError = { msg -> scope.launch { _warning.value = IntercomError.Audio(msg) } },
    )
    val audioRoute get() = audio.router.route

    // ---- session fields ----
    @Volatile private var activeEndpoint: String? = null
    @Volatile private var authorized = false
    private var pendingEndpoint: String? = null
    private var peerDeviceId: String? = null
    private var peerName: String = ""
    private var peerRole: Role = Role.RIDER
    private var myNonce: ByteArray = ByteArray(0)
    private var peerNonce: ByteArray? = null
    private var sessionId: String? = null
    private var sessionSecret: ByteArray? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var authTimeoutJob: Job? = null
    private var reconnectAttempt = 0
    private var reconnectRequestInFlight = false
    private val endpointsByDevice = HashMap<String, String>()
    private var pingId = 0
    private var lastPongAt = 0L
    private var rttMs = -1

    private fun selfInfo() = settings.current.let {
        EndpointInfo(it.deviceId, it.role ?: Role.RIDER, it.deviceName)
    }

    private fun dispatch(event: ConnectionEvent) {
        val before = _state.value
        val after = ConnectionStateManager.reduce(before, event)
        if (after != before) Log.i(TAG, "$before --$event--> $after")
        _state.value = after
    }

    // ================= Public API (UI) =================

    fun startDiscovery() {
        if (_state.value.isSessionActive) return
        permissions.readinessError()?.let { dispatch(ConnectionEvent.Error(it)); return }
        _warning.value = if (!permissions.isWifiOn()) IntercomError.WifiOff else null
        _peers.value = emptyList()
        dispatch(ConnectionEvent.StartDiscovery)
        discovery.restart(selfInfo())
    }

    fun refreshDiscovery() {
        if (_state.value.isSessionActive) return
        if (_state.value is ConnectionState.Failed) dispatch(ConnectionEvent.UserDisconnect)
        startDiscovery()
    }

    fun stopDiscovery() {
        discovery.stop()
        dispatch(ConnectionEvent.StopDiscovery)
    }

    fun connect(peer: PeerDevice) {
        val s = _state.value
        if (s.isSessionActive || s is ConnectionState.Requesting || s is ConnectionState.AwaitingDecision) return
        if (!permissions.hasMic()) {
            dispatch(ConnectionEvent.Error(IntercomError.PermissionDenied(listOf("Microphone")))); return
        }
        newSessionCredentials(clear = true)
        pendingEndpoint = peer.endpointId
        dispatch(ConnectionEvent.RequestSent(peer))
        IntercomForegroundService.start(context) // must start while app is in foreground
        peerManager.request(selfInfo(), peer.endpointId)
    }

    fun acceptRequest() {
        val s = _state.value as? ConnectionState.AwaitingDecision ?: return
        if (!permissions.hasMic()) {
            rejectRequest()
            dispatch(ConnectionEvent.Error(IntercomError.PermissionDenied(listOf("Microphone")))); return
        }
        newSessionCredentials(clear = true)
        IntercomForegroundService.start(context)
        peerManager.accept(s.request.endpointId)
        dispatch(ConnectionEvent.Accepted(s.request.name))
    }

    fun rejectRequest() {
        val s = _state.value as? ConnectionState.AwaitingDecision ?: return
        peerManager.reject(s.request.endpointId)
        pendingEndpoint = null
        _pendingAuthDigits.value = null
        dispatch(ConnectionEvent.RejectedByUser)
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        audio.muted = muted
        activeEndpoint?.takeIf { authorized }?.let { peerManager.send(it, Message.MuteState(muted)) }
    }

    fun toggleMute() = setMuted(!_muted.value)

    /** Deliberate hang-up: tell the peer so it does not try to reconnect. */
    fun disconnect() {
        activeEndpoint?.let { ep ->
            peerManager.send(ep, Message.Bye)
            activeEndpoint = null // so endSession doesn't cut the link before Bye is delivered
            scope.launch { delay(150); peerManager.disconnect(ep) }
        }
        pendingEndpoint?.let { peerManager.disconnect(it) }
        endSession(ConnectionEvent.UserDisconnect)
    }

    fun dismissError() {
        if (_state.value is ConnectionState.Failed) dispatch(ConnectionEvent.UserDisconnect)
    }

    fun dismissWarning() { _warning.value = null }

    fun applyAudioSettings() {
        audio.quality = settings.current.quality
        audio.noiseSuppression = settings.current.noiseSuppression
    }

    // ================= Service hooks =================

    /** Called by the foreground service once it is in the foreground. */
    fun onServiceStarted() {
        applyAudioSettings()
        audio.muted = _muted.value
        if (!audio.start()) {
            _warning.value = IntercomError.MicUnavailable
        }
        audio.transmitEnabled = authorized
    }

    fun onServiceStopped() {
        audio.stop()
    }

    // ================= Discovery callbacks =================

    override fun onPeerFound(endpointId: String, info: EndpointInfo) {
        if (info.deviceId == settings.current.deviceId) return
        endpointsByDevice[info.deviceId] = endpointId
        val peer = PeerDevice(endpointId, info.name, info.deviceId, info.role)
        _peers.value = (_peers.value.filterNot { it.deviceId == info.deviceId } + peer).sortedBy { it.name }
        // Reconnect: the leader re-dials as soon as the lost peer reappears.
        if (_state.value is ConnectionState.Reconnecting && info.deviceId == peerDeviceId) tryReconnectRequest()
    }

    override fun onPeerLost(endpointId: String) {
        _peers.value = _peers.value.filterNot { it.endpointId == endpointId }
        endpointsByDevice.entries.removeAll { it.value == endpointId }
    }

    override fun onDiscoveryError(message: String) {
        if (_state.value is ConnectionState.Reconnecting) {
            Log.w(TAG, "discovery error during reconnect: $message"); return
        }
        dispatch(ConnectionEvent.Error(IntercomError.Discovery(message)))
    }

    // ================= Connection callbacks =================

    override fun onConnectionInitiated(endpointId: String, info: EndpointInfo?, authDigits: String, incoming: Boolean) {
        val s = _state.value
        if (info == null) { peerManager.reject(endpointId); return }

        // Reconnect: accept the known peer automatically; it must still prove the session secret.
        if (s is ConnectionState.Reconnecting) {
            if (info.deviceId == peerDeviceId && activeEndpoint == null) {
                pendingEndpoint = endpointId
                peerManager.accept(endpointId)
            } else {
                peerManager.reject(endpointId)
            }
            return
        }
        // Only one peer at a time: refuse anything while a session exists.
        if (s.isSessionActive || (pendingEndpoint != null && pendingEndpoint != endpointId)) {
            peerManager.reject(endpointId); return
        }
        _pendingAuthDigits.value = authDigits
        if (incoming) {
            pendingEndpoint = endpointId
            dispatch(
                ConnectionEvent.RequestReceived(
                    ConnectionRequest(endpointId, info.name, info.deviceId, info.role, authDigits, isIncoming = true),
                ),
            )
        } else {
            // We dialled: accept our side; the other person decides on theirs.
            peerManager.accept(endpointId)
        }
    }

    override fun onConnected(endpointId: String) {
        if (endpointId != pendingEndpoint && _state.value !is ConnectionState.Reconnecting) {
            peerManager.disconnect(endpointId); return
        }
        pendingEndpoint = null
        reconnectRequestInFlight = false
        activeEndpoint = endpointId
        authorized = false
        audio.transmitEnabled = false
        val s = _state.value
        val name = when (s) {
            is ConnectionState.Requesting -> s.peer.name
            is ConnectionState.Authenticating -> s.peerName
            is ConnectionState.Reconnecting -> s.peerName
            else -> peerName
        }
        if (s is ConnectionState.Requesting) dispatch(ConnectionEvent.Accepted(name))
        // Point-to-point: stop radios we no longer need (saves battery, frees bandwidth).
        discovery.stop()
        myNonce = SessionAuth.newNonce()
        peerNonce = null
        val me = settings.current
        peerManager.send(
            endpointId,
            Message.Hello(Message.VERSION, me.deviceId, me.deviceName, (me.role ?: Role.RIDER).name, myNonce),
        )
        authTimeoutJob?.cancel()
        authTimeoutJob = scope.launch {
            delay(AUTH_TIMEOUT_MS)
            if (!authorized && activeEndpoint == endpointId) {
                Log.w(TAG, "authentication timed out")
                peerManager.disconnect(endpointId)
                onDisconnected(endpointId)
            }
        }
    }

    override fun onRejected(endpointId: String) {
        if (_state.value is ConnectionState.Reconnecting) { reconnectRequestInFlight = false; return }
        if (endpointId == pendingEndpoint) {
            pendingEndpoint = null
            _pendingAuthDigits.value = null
            dispatch(ConnectionEvent.RejectedByPeer)
            IntercomForegroundService.stop(context)
        }
    }

    override fun onConnectionError(endpointId: String, message: String) {
        if (_state.value is ConnectionState.Reconnecting) { reconnectRequestInFlight = false; return }
        if (endpointId == pendingEndpoint || endpointId == activeEndpoint) {
            pendingEndpoint = null
            endSession(ConnectionEvent.Error(IntercomError.Connection(message)))
        }
    }

    override fun onDisconnected(endpointId: String) {
        if (endpointId != activeEndpoint) return
        activeEndpoint = null
        authorized = false
        audio.transmitEnabled = false
        audio.jitter.reset()
        pingJob?.cancel()
        authTimeoutJob?.cancel()
        val canReconnect = settings.current.autoReconnect && sessionSecret != null && peerDeviceId != null
        if (canReconnect) beginReconnect() else endSession(ConnectionEvent.Error(IntercomError.PeerLost))
    }

    override fun onMessage(endpointId: String, message: Message) {
        // Session-level authorization: ignore anything not from the one active link.
        if (endpointId != activeEndpoint) return
        when (message) {
            is Message.Audio -> if (authorized) audio.onIncoming(message.codec, message.seq, message.timestampMs, message.payload)
            is Message.Hello -> onHello(endpointId, message)
            is Message.SessionKey -> onSessionKey(message)
            is Message.Proof -> onProof(endpointId, message)
            is Message.Ping -> peerManager.send(endpointId, Message.Pong(message.id, message.sentAtMs))
            is Message.Pong -> {
                lastPongAt = System.currentTimeMillis()
                rttMs = (lastPongAt - message.sentAtMs).toInt()
            }
            is Message.MuteState -> _peerMuted.value = message.muted
            Message.Bye -> {
                // Peer hung up on purpose: no reconnection.
                sessionSecret = null
                peerManager.disconnect(endpointId)
                activeEndpoint = null
                endSession(ConnectionEvent.UserDisconnect)
            }
        }
    }

    // ================= Handshake =================

    private fun onHello(endpointId: String, hello: Message.Hello) {
        if (hello.protocolVersion != Message.VERSION) {
            failAuth(endpointId); return
        }
        val reconnecting = _state.value is ConnectionState.Reconnecting
        if (reconnecting && hello.deviceId != peerDeviceId) { failAuth(endpointId); return }
        peerDeviceId = hello.deviceId
        peerName = hello.name
        peerRole = Role.fromWire(hello.role)
        peerNonce = hello.nonce
        val me = settings.current.deviceId
        val secret = sessionSecret
        val sid = sessionId
        if (reconnecting && secret != null && sid != null) {
            peerManager.send(endpointId, Message.Proof(sid, SessionAuth.proof(secret, hello.nonce, me)))
        } else if (SessionAuth.isLeader(me, hello.deviceId)) {
            // Fresh, human-approved session: leader issues the shared secret.
            val newSecret = SessionAuth.newSecret()
            val newId = SessionAuth.newSessionId()
            sessionSecret = newSecret; sessionId = newId
            peerManager.send(endpointId, Message.SessionKey(newId, newSecret))
            authorize()
        }
        // Non-leader on a fresh session waits for SessionKey.
    }

    private fun onSessionKey(key: Message.SessionKey) {
        if (_state.value is ConnectionState.Reconnecting) return // keys are only issued on fresh sessions
        if (key.secret.size != 32) return
        sessionSecret = key.secret
        sessionId = key.sessionId
        authorize()
    }

    private fun onProof(endpointId: String, proof: Message.Proof) {
        val secret = sessionSecret
        val pid = peerDeviceId
        if (secret == null || pid == null || proof.sessionId != sessionId ||
            !SessionAuth.verify(secret, myNonce, pid, proof.mac)
        ) {
            failAuth(endpointId); return
        }
        authorize()
    }

    private fun failAuth(endpointId: String) {
        sessionSecret = null
        peerManager.disconnect(endpointId)
        activeEndpoint = null
        endSession(ConnectionEvent.AuthFailed)
    }

    private fun authorize() {
        if (authorized) return
        authorized = true
        authTimeoutJob?.cancel()
        reconnectJob?.cancel()
        reconnectAttempt = 0
        _pendingAuthDigits.value = null
        audio.jitter.reset()
        audio.transmitEnabled = true
        val ep = activeEndpoint ?: return
        peerManager.send(ep, Message.MuteState(_muted.value))
        dispatch(ConnectionEvent.Authorized(peerName, peerRole, System.currentTimeMillis()))
        startPings()
    }

    // ================= Reconnection =================

    private fun beginReconnect() {
        reconnectAttempt = 1
        reconnectRequestInFlight = false
        dispatch(ConnectionEvent.LinkLost(peerName, reconnectAttempt))
        discovery.restart(selfInfo())
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive && _state.value is ConnectionState.Reconnecting) {
                delay(reconnectPolicy.delayFor(reconnectAttempt))
                if (_state.value !is ConnectionState.Reconnecting) break
                reconnectAttempt++
                if (!reconnectPolicy.shouldRetry(reconnectAttempt)) {
                    endSession(ConnectionEvent.ReconnectExhausted); break
                }
                dispatch(ConnectionEvent.LinkLost(peerName, reconnectAttempt))
                if (reconnectAttempt % 6 == 0) {
                    reconnectRequestInFlight = false
                    discovery.restart(selfInfo())
                }
                tryReconnectRequest()
            }
        }
    }

    /** Only the leader dials, so both phones never dial each other at once. */
    private fun tryReconnectRequest() {
        val pid = peerDeviceId ?: return
        if (!SessionAuth.isLeader(settings.current.deviceId, pid)) return
        if (reconnectRequestInFlight || activeEndpoint != null) return
        val ep = endpointsByDevice[pid] ?: return
        reconnectRequestInFlight = true
        pendingEndpoint = ep
        peerManager.request(selfInfo(), ep)
    }

    // ================= Link quality =================

    private fun startPings() {
        pingJob?.cancel()
        lastPongAt = System.currentTimeMillis()
        pingJob = scope.launch {
            while (isActive) {
                val ep = activeEndpoint ?: break
                peerManager.send(ep, Message.Ping(pingId++, System.currentTimeMillis()))
                val stale = System.currentTimeMillis() - lastPongAt > 3_000
                val loss = audio.jitter.lossPercent()
                _stats.value = AudioStats(
                    micLevel = audio.micLevel.value,
                    speakerLevel = audio.speakerLevel.value,
                    rttMs = rttMs,
                    packetLossPct = loss,
                    jitterDepthMs = audio.jitter.targetMs,
                    codec = "${audio.encoderName} ↑ / ${audio.decoderName} ↓",
                    quality = LinkQualityEstimator.estimate(rttMs, loss, stale),
                )
                delay(1_000)
            }
        }
    }

    // ================= Teardown =================

    private fun newSessionCredentials(clear: Boolean) {
        if (clear) { sessionSecret = null; sessionId = null; peerDeviceId = null }
        authorized = false
        _peerMuted.value = false
    }

    private fun endSession(event: ConnectionEvent) {
        reconnectJob?.cancel(); pingJob?.cancel(); authTimeoutJob?.cancel()
        reconnectJob = null
        activeEndpoint?.let { peerManager.disconnect(it) }
        activeEndpoint = null
        pendingEndpoint = null
        authorized = false
        sessionSecret = null; sessionId = null; peerDeviceId = null
        _pendingAuthDigits.value = null
        _peerMuted.value = false
        _stats.value = AudioStats()
        discovery.stop()
        IntercomForegroundService.stop(context)
        dispatch(event)
    }

    companion object {
        private const val TAG = "IntercomSession"
        private const val AUTH_TIMEOUT_MS = 8_000L

    }
}

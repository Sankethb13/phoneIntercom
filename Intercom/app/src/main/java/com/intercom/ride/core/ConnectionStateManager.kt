package com.intercom.ride.core

/** Lifecycle of the intercom link. */
sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Discovering : ConnectionState()
    data class Requesting(val peer: PeerDevice) : ConnectionState()
    data class AwaitingDecision(val request: ConnectionRequest) : ConnectionState()
    data class Authenticating(val peerName: String) : ConnectionState()
    data class Connected(val peerName: String, val peerRole: Role, val sinceMs: Long) : ConnectionState()
    data class Reconnecting(val peerName: String, val attempt: Int) : ConnectionState()
    data class Failed(val error: IntercomError) : ConnectionState()

    val isSessionActive: Boolean
        get() = this is Authenticating || this is Connected || this is Reconnecting
}

/** Events that move the state machine. */
sealed class ConnectionEvent {
    data object StartDiscovery : ConnectionEvent()
    data object StopDiscovery : ConnectionEvent()
    data class RequestSent(val peer: PeerDevice) : ConnectionEvent()
    data class RequestReceived(val request: ConnectionRequest) : ConnectionEvent()
    data class Accepted(val peerName: String) : ConnectionEvent()
    data object RejectedByPeer : ConnectionEvent()
    data object RejectedByUser : ConnectionEvent()
    data class Authorized(val peerName: String, val role: Role, val nowMs: Long) : ConnectionEvent()
    data object AuthFailed : ConnectionEvent()
    data class LinkLost(val peerName: String, val attempt: Int) : ConnectionEvent()
    data object ReconnectExhausted : ConnectionEvent()
    data class Error(val error: IntercomError) : ConnectionEvent()
    data object UserDisconnect : ConnectionEvent()
}

/**
 * Pure transition function so the rules are unit-testable without devices.
 * Illegal events for the current state are ignored (state returned unchanged),
 * which also prevents duplicate connections: a second request while a session
 * is active cannot replace it.
 */
object ConnectionStateManager {
    fun reduce(state: ConnectionState, event: ConnectionEvent): ConnectionState = when (event) {
        ConnectionEvent.StartDiscovery ->
            if (state.isSessionActive || state is ConnectionState.Requesting) state else ConnectionState.Discovering
        ConnectionEvent.StopDiscovery ->
            if (state is ConnectionState.Discovering) ConnectionState.Idle else state
        is ConnectionEvent.RequestSent ->
            if (state.isSessionActive) state else ConnectionState.Requesting(event.peer)
        is ConnectionEvent.RequestReceived ->
            if (state.isSessionActive || state is ConnectionState.AwaitingDecision) state
            else ConnectionState.AwaitingDecision(event.request)
        is ConnectionEvent.Accepted ->
            when (state) {
                is ConnectionState.Requesting, is ConnectionState.AwaitingDecision, is ConnectionState.Reconnecting ->
                    ConnectionState.Authenticating(event.peerName)
                else -> state
            }
        ConnectionEvent.RejectedByPeer ->
            if (state is ConnectionState.Requesting) ConnectionState.Failed(IntercomError.Rejected) else state
        ConnectionEvent.RejectedByUser ->
            if (state is ConnectionState.AwaitingDecision) ConnectionState.Discovering else state
        is ConnectionEvent.Authorized ->
            if (state is ConnectionState.Authenticating || state is ConnectionState.Reconnecting)
                ConnectionState.Connected(event.peerName, event.role, event.nowMs)
            else state
        ConnectionEvent.AuthFailed -> ConnectionState.Failed(IntercomError.AuthFailed)
        is ConnectionEvent.LinkLost ->
            if (state.isSessionActive) ConnectionState.Reconnecting(event.peerName, event.attempt) else state
        ConnectionEvent.ReconnectExhausted ->
            if (state is ConnectionState.Reconnecting) ConnectionState.Failed(IntercomError.ReconnectGaveUp) else state
        is ConnectionEvent.Error -> ConnectionState.Failed(event.error)
        ConnectionEvent.UserDisconnect -> ConnectionState.Idle
    }
}

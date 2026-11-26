package com.intercom.ride.core

import com.intercom.ride.core.ConnectionEvent as E
import com.intercom.ride.core.ConnectionState as S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStateManagerTest {
    private val peer = PeerDevice("ep1", "Pillion", "dev2", Role.PILLION)
    private val request = ConnectionRequest("ep1", "Rider", "dev1", Role.RIDER, "1234", isIncoming = true)
    private fun run(vararg events: E): S = events.fold(S.Idle as S, ConnectionStateManager::reduce)

    @Test fun discoveryStartsAndStops() {
        assertEquals(S.Discovering, run(E.StartDiscovery))
        assertEquals(S.Idle, run(E.StartDiscovery, E.StopDiscovery))
    }

    @Test fun outgoingConnectionEstablishes() {
        val s = run(E.StartDiscovery, E.RequestSent(peer), E.Accepted("Pillion"), E.Authorized("Pillion", Role.PILLION, 10))
        assertEquals(S.Connected("Pillion", Role.PILLION, 10), s)
    }

    @Test fun incomingConnectionEstablishes() {
        val s = run(E.StartDiscovery, E.RequestReceived(request), E.Accepted("Rider"), E.Authorized("Rider", Role.RIDER, 5))
        assertTrue(s is S.Connected)
    }

    @Test fun rejectionByPeerFails() {
        assertEquals(S.Failed(IntercomError.Rejected), run(E.StartDiscovery, E.RequestSent(peer), E.RejectedByPeer))
    }

    @Test fun rejectionByUserReturnsToDiscovery() {
        assertEquals(S.Discovering, run(E.StartDiscovery, E.RequestReceived(request), E.RejectedByUser))
    }

    @Test fun duplicateConnectionsAreRefused() {
        val connected = run(E.StartDiscovery, E.RequestSent(peer), E.Accepted("P"), E.Authorized("P", Role.PILLION, 1))
        assertEquals(connected, ConnectionStateManager.reduce(connected, E.RequestReceived(request)))
        assertEquals(connected, ConnectionStateManager.reduce(connected, E.RequestSent(peer)))
        assertEquals(connected, ConnectionStateManager.reduce(connected, E.StartDiscovery))
        val waiting = run(E.StartDiscovery, E.RequestReceived(request))
        val other = request.copy(endpointId = "ep9")
        assertEquals(waiting, ConnectionStateManager.reduce(waiting, E.RequestReceived(other)))
    }

    @Test fun reconnectionRestoresSession() {
        val connected = run(E.StartDiscovery, E.RequestSent(peer), E.Accepted("P"), E.Authorized("P", Role.PILLION, 1))
        val lost = ConnectionStateManager.reduce(connected, E.LinkLost("P", 1))
        assertEquals(S.Reconnecting("P", 1), lost)
        assertTrue(lost.isSessionActive)
        val back = ConnectionStateManager.reduce(lost, E.Authorized("P", Role.PILLION, 99))
        assertEquals(S.Connected("P", Role.PILLION, 99), back)
    }

    @Test fun reconnectionGivesUp() {
        val s = run(E.StartDiscovery, E.RequestSent(peer), E.Accepted("P"), E.Authorized("P", Role.PILLION, 1),
            E.LinkLost("P", 1), E.ReconnectExhausted)
        assertEquals(S.Failed(IntercomError.ReconnectGaveUp), s)
    }

    @Test fun authFailureAndUserDisconnect() {
        assertEquals(S.Failed(IntercomError.AuthFailed), run(E.RequestSent(peer), E.Accepted("P"), E.AuthFailed))
        assertEquals(S.Idle, run(E.RequestSent(peer), E.Accepted("P"), E.Authorized("P", Role.RIDER, 1), E.UserDisconnect))
    }

    @Test fun linkLostIgnoredWithoutSession() {
        assertEquals(S.Discovering, run(E.StartDiscovery, E.LinkLost("x", 1)))
    }
}

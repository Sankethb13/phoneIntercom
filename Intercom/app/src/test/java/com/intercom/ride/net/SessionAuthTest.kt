package com.intercom.ride.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAuthTest {
    @Test fun proofVerifiesWithSameSecret() {
        val secret = SessionAuth.newSecret()
        val aliceNonce = SessionAuth.newNonce()
        // Bob proves to Alice using Alice's nonce and Bob's id.
        val mac = SessionAuth.proof(secret, aliceNonce, "bob")
        assertTrue(SessionAuth.verify(secret, aliceNonce, "bob", mac))
    }

    @Test fun proofFailsWithWrongSecretNonceOrIdentity() {
        val secret = SessionAuth.newSecret()
        val nonce = SessionAuth.newNonce()
        val mac = SessionAuth.proof(secret, nonce, "bob")
        assertFalse(SessionAuth.verify(SessionAuth.newSecret(), nonce, "bob", mac))
        assertFalse(SessionAuth.verify(secret, SessionAuth.newNonce(), "bob", mac))
        assertFalse(SessionAuth.verify(secret, nonce, "mallory", mac))
    }

    @Test fun exactlyOneLeader() {
        assertNotEquals(SessionAuth.isLeader("a1", "b2"), SessionAuth.isLeader("b2", "a1"))
    }

    @Test fun randomValuesAreFreshAndSized() {
        assertEquals(32, SessionAuth.newSecret().size)
        assertEquals(16, SessionAuth.newNonce().size)
        assertNotEquals(SessionAuth.newSessionId(), SessionAuth.newSessionId())
    }
}

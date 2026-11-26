package com.intercom.ride.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun backoffGrowsAndCaps() {
        val p = ReconnectPolicy(initialMs = 1000, maxMs = 8000, maxAttempts = 5)
        assertEquals(1000, p.delayFor(1))
        assertEquals(2000, p.delayFor(2))
        assertEquals(4000, p.delayFor(3))
        assertEquals(8000, p.delayFor(4))
        assertEquals(8000, p.delayFor(30))
    }

    @Test fun stopsAfterMaxAttempts() {
        val p = ReconnectPolicy(maxAttempts = 3)
        assertTrue(p.shouldRetry(3))
        assertFalse(p.shouldRetry(4))
    }
}

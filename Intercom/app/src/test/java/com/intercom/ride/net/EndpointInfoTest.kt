package com.intercom.ride.net

import com.intercom.ride.core.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointInfoTest {
    @Test fun roundTrip() {
        val info = EndpointInfo("dev42", Role.PILLION, "Asha's Pixel")
        assertEquals(info, EndpointInfo.decode(info.encode()))
    }

    @Test fun separatorsAndLongNamesAreSanitised() {
        val decoded = EndpointInfo.decode(EndpointInfo("d", Role.RIDER, "a|b" + "x".repeat(50)).encode())!!
        assertEquals(EndpointInfo.MAX_NAME, decoded.name.length)
        assertEquals("a b", decoded.name.take(3))
    }

    @Test fun foreignAdvertisementsAreIgnored() {
        assertNull(EndpointInfo.decode(null))
        assertNull(EndpointInfo.decode("SomeOtherApp".toByteArray()))
        assertNull(EndpointInfo.decode("IC1||RIDER|x".toByteArray()))
    }
}

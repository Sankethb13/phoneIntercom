package com.intercom.ride.permissions

import com.intercom.ride.permissions.PermissionRequirements as P
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequirementsTest {
    @Test fun android10NeedsLocationNotNewBluetooth() {
        val r = P.required(29)
        assertTrue(P.RECORD_AUDIO in r && P.FINE_LOCATION in r)
        assertFalse(P.BT_SCAN in r || P.NEARBY_WIFI in r)
        assertTrue(P.needsLocationServices(29))
    }

    @Test fun android12NeedsBluetoothAndLocation() {
        val r = P.required(31)
        assertTrue(P.BT_SCAN in r && P.BT_ADVERTISE in r && P.BT_CONNECT in r && P.FINE_LOCATION in r)
        assertFalse(P.NEARBY_WIFI in r)
    }

    @Test fun android13PlusUsesNearbyWifiWithoutLocation() {
        for (sdk in listOf(33, 34, 35)) {
            val r = P.required(sdk)
            assertTrue(P.NEARBY_WIFI in r && P.BT_CONNECT in r && P.NOTIFICATIONS in r)
            assertFalse(P.FINE_LOCATION in r)
            assertFalse(P.needsLocationServices(sdk))
        }
    }

    @Test fun notificationsAreNotCritical() {
        assertFalse(P.isCritical(P.NOTIFICATIONS))
        assertTrue(P.isCritical(P.RECORD_AUDIO))
    }
}

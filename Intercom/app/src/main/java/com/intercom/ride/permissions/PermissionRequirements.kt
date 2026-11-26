package com.intercom.ride.permissions

/**
 * Which runtime permissions Intercom needs on a given Android version.
 * Pure function of the SDK level so it can be unit-tested.
 * Strings match android.Manifest.permission constants.
 */
object PermissionRequirements {
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val BT_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BT_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    const val BT_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val NEARBY_WIFI = "android.permission.NEARBY_WIFI_DEVICES"
    const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    fun required(sdkInt: Int): List<String> = buildList {
        add(RECORD_AUDIO)
        if (sdkInt >= 31) {
            add(BT_SCAN); add(BT_ADVERTISE); add(BT_CONNECT)
        }
        // Nearby Connections still needs location on Android 12L and older.
        if (sdkInt <= 32) {
            add(FINE_LOCATION); add(COARSE_LOCATION)
        }
        if (sdkInt >= 33) {
            add(NEARBY_WIFI); add(NOTIFICATIONS)
        }
    }

    /** Notifications are nice-to-have; everything else blocks the intercom. */
    fun isCritical(permission: String): Boolean = permission != NOTIFICATIONS

    /** Location services toggle must be ON for discovery on these versions. */
    fun needsLocationServices(sdkInt: Int): Boolean = sdkInt <= 32

    fun friendlyName(permission: String): String = when (permission) {
        RECORD_AUDIO -> "Microphone"
        FINE_LOCATION, COARSE_LOCATION -> "Location"
        BT_SCAN, BT_ADVERTISE, BT_CONNECT -> "Nearby devices (Bluetooth)"
        NEARBY_WIFI -> "Nearby devices (Wi-Fi)"
        NOTIFICATIONS -> "Notifications"
        else -> permission.substringAfterLast('.')
    }
}

package com.intercom.ride.core

/** Who is using this phone. Purely informational; both roles are full-duplex peers. */
enum class Role(val label: String) {
    RIDER("Rider"),
    PILLION("Pillion");

    companion object {
        fun fromWire(value: String): Role = entries.firstOrNull { it.name == value } ?: RIDER
    }
}

/** A nearby phone running Intercom, as seen by discovery. */
data class PeerDevice(
    val endpointId: String,
    val name: String,
    val deviceId: String,
    val role: Role,
)

/** An incoming connection the user must accept or reject. */
data class ConnectionRequest(
    val endpointId: String,
    val name: String,
    val deviceId: String,
    val role: Role,
    /** Short code shown on both phones so riders can confirm they paired with each other. */
    val authDigits: String,
    val isIncoming: Boolean,
)

enum class LinkQuality { EXCELLENT, GOOD, FAIR, POOR, UNKNOWN }

data class AudioStats(
    val micLevel: Float = 0f,
    val speakerLevel: Float = 0f,
    val rttMs: Int = -1,
    val packetLossPct: Float = 0f,
    val jitterDepthMs: Int = 0,
    val codec: String = "-",
    val quality: LinkQuality = LinkQuality.UNKNOWN,
)

/** User-visible, structured errors. Never fail silently. */
sealed class IntercomError(val message: String) {
    data object BluetoothOff : IntercomError("Bluetooth is turned off. Turn it on to find nearby riders.")
    data object LocationOff : IntercomError("Location services are off. Android 12 and older need Location on for nearby discovery.")
    data object WifiOff : IntercomError("Wi-Fi is off. Intercom still works over Bluetooth, but Wi-Fi gives better audio and range.")
    data class PermissionDenied(val which: List<String>) :
        IntercomError("Missing permissions: ${which.joinToString()}. Grant them in Settings → Permissions.")
    data object MicUnavailable : IntercomError("The microphone is in use by another app or unavailable.")
    data class Discovery(val detail: String) : IntercomError("Nearby discovery failed: $detail")
    data class Connection(val detail: String) : IntercomError("Connection failed: $detail")
    data object Rejected : IntercomError("The other phone declined the connection.")
    data object AuthFailed : IntercomError("The other phone could not prove it belongs to this session. Disconnected for safety.")
    data object PeerLost : IntercomError("Lost contact with the other phone. Trying to reconnect…")
    data object ReconnectGaveUp : IntercomError("Could not reconnect. Bring the phones closer and connect again.")
    data class Audio(val detail: String) : IntercomError("Audio problem: $detail")
    data object PlayServicesMissing : IntercomError("Google Play services is required for offline nearby connections.")
}

package com.intercom.ride.net

import com.intercom.ride.core.Role

/**
 * Data advertised over Nearby so peers can show a name and recognise the same
 * phone again after a dropout (Nearby endpoint IDs change on every session).
 * Format: "IC1|<deviceId>|<role>|<name>" in UTF-8, name truncated to keep the
 * advertisement small enough for BLE.
 */
data class EndpointInfo(val deviceId: String, val role: Role, val name: String) {
    fun encode(): ByteArray {
        val safeName = name.replace("|", " ").trim().take(MAX_NAME).ifEmpty { "Intercom" }
        return "$PREFIX|$deviceId|${role.name}|$safeName".toByteArray(Charsets.UTF_8)
    }

    companion object {
        private const val PREFIX = "IC1"
        const val MAX_NAME = 24

        fun decode(bytes: ByteArray?): EndpointInfo? {
            if (bytes == null) return null
            val parts = String(bytes, Charsets.UTF_8).split("|", limit = 4)
            if (parts.size != 4 || parts[0] != PREFIX || parts[1].isBlank()) return null
            return EndpointInfo(parts[1], Role.fromWire(parts[2]), parts[3])
        }
    }
}

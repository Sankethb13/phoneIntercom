package com.intercom.ride.net

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Session-level authorization.
 *
 * 1. A fresh connection is only possible after a person accepts it on the
 *    receiving phone (after comparing the 4-digit code shown on both).
 * 2. Over that encrypted link, one side sends a random 256-bit session secret.
 * 3. On any automatic reconnect, each side must prove it holds that secret by
 *    returning HMAC-SHA256(secret, peerNonce || ownDeviceId). Audio is neither
 *    sent nor played until the proof verifies.
 */
object SessionAuth {
    private val random = SecureRandom()

    fun newNonce(): ByteArray = ByteArray(16).also { random.nextBytes(it) }
    fun newSecret(): ByteArray = ByteArray(32).also { random.nextBytes(it) }
    fun newSessionId(): String = ByteArray(8).also { random.nextBytes(it) }.toHex()

    fun proof(secret: ByteArray, peerNonce: ByteArray, ownDeviceId: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(peerNonce)
        mac.update(ownDeviceId.toByteArray(Charsets.UTF_8))
        return mac.doFinal()
    }

    fun verify(secret: ByteArray, ownNonce: ByteArray, peerDeviceId: String, mac: ByteArray): Boolean =
        MessageDigest.isEqual(proof(secret, ownNonce, peerDeviceId), mac)

    /** Deterministic tie-break so exactly one side generates the key / initiates reconnects. */
    fun isLeader(ownDeviceId: String, peerDeviceId: String): Boolean = ownDeviceId < peerDeviceId

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

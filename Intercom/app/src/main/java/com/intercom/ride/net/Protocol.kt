package com.intercom.ride.net

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Codec identifiers carried in every audio packet so each side can pick its own encoder. */
object CodecId {
    const val OPUS: Byte = 1
    const val MULAW: Byte = 2

    fun name(id: Byte): String = when (id) {
        OPUS -> "Opus"
        MULAW -> "G.711 µ-law"
        else -> "unknown"
    }
}

/**
 * Wire protocol. Every Nearby BYTES payload is exactly one [Message].
 * The Nearby channel itself is encrypted (UKEY2); these messages add
 * session-level authorization on top.
 */
sealed class Message {
    /** First message after a link opens. */
    data class Hello(
        val protocolVersion: Int,
        val deviceId: String,
        val name: String,
        val role: String,
        val nonce: ByteArray,
    ) : Message()

    /** Sent once on a fresh, user-approved session: the shared secret for later reconnects. */
    data class SessionKey(val sessionId: String, val secret: ByteArray) : Message()

    /** Proof of possession of the session secret, bound to the peer's nonce. */
    data class Proof(val sessionId: String, val mac: ByteArray) : Message()

    data class Audio(val codec: Byte, val seq: Int, val timestampMs: Int, val payload: ByteArray) : Message()
    data class Ping(val id: Int, val sentAtMs: Long) : Message()
    data class Pong(val id: Int, val sentAtMs: Long) : Message()
    data class MuteState(val muted: Boolean) : Message()
    /** Deliberate hang-up: peer must not auto-reconnect. */
    data object Bye : Message()

    companion object {
        const val VERSION = 1
        private const val T_HELLO: Byte = 1
        private const val T_KEY: Byte = 2
        private const val T_PROOF: Byte = 3
        private const val T_AUDIO: Byte = 4
        private const val T_PING: Byte = 5
        private const val T_PONG: Byte = 6
        private const val T_MUTE: Byte = 7
        private const val T_BYE: Byte = 8
        private const val MAX_FIELD = 4096

        fun encode(m: Message): ByteArray = when (m) {
            is Hello -> build(T_HELLO) {
                putInt(m.protocolVersion); str(m.deviceId); str(m.name); str(m.role); bytes(m.nonce)
            }
            is SessionKey -> build(T_KEY) { str(m.sessionId); bytes(m.secret) }
            is Proof -> build(T_PROOF) { str(m.sessionId); bytes(m.mac) }
            is Audio -> {
                val b = ByteBuffer.allocate(1 + 1 + 4 + 4 + m.payload.size)
                b.put(T_AUDIO).put(m.codec).putInt(m.seq).putInt(m.timestampMs).put(m.payload)
                b.array()
            }
            is Ping -> ByteBuffer.allocate(13).put(T_PING).putInt(m.id).putLong(m.sentAtMs).array()
            is Pong -> ByteBuffer.allocate(13).put(T_PONG).putInt(m.id).putLong(m.sentAtMs).array()
            is MuteState -> byteArrayOf(T_MUTE, if (m.muted) 1 else 0)
            Bye -> byteArrayOf(T_BYE)
        }

        /** Returns null for malformed or unknown input instead of throwing. */
        fun decode(data: ByteArray): Message? = try {
            val b = ByteBuffer.wrap(data)
            when (b.get()) {
                T_HELLO -> Hello(b.int, b.str(), b.str(), b.str(), b.bytes())
                T_KEY -> SessionKey(b.str(), b.bytes())
                T_PROOF -> Proof(b.str(), b.bytes())
                T_AUDIO -> {
                    val codec = b.get(); val seq = b.int; val ts = b.int
                    val p = ByteArray(b.remaining()); b.get(p)
                    Audio(codec, seq, ts, p)
                }
                T_PING -> Ping(b.int, b.long)
                T_PONG -> Pong(b.int, b.long)
                T_MUTE -> MuteState(b.get().toInt() != 0)
                T_BYE -> Bye
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        private class Writer {
            val parts = ArrayList<ByteArray>()
            fun putInt(v: Int) { parts += ByteBuffer.allocate(4).putInt(v).array() }
            fun bytes(v: ByteArray) { putInt(v.size); parts += v }
            fun str(v: String) = bytes(v.toByteArray(StandardCharsets.UTF_8))
        }

        private inline fun build(type: Byte, fill: Writer.() -> Unit): ByteArray {
            val w = Writer().apply(fill)
            val out = ByteBuffer.allocate(1 + w.parts.sumOf { it.size })
            out.put(type)
            w.parts.forEach { out.put(it) }
            return out.array()
        }

        private fun ByteBuffer.bytes(): ByteArray {
            val n = int
            require(n in 0..MAX_FIELD && n <= remaining())
            return ByteArray(n).also { get(it) }
        }

        private fun ByteBuffer.str(): String = String(bytes(), StandardCharsets.UTF_8)
    }
}

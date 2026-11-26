package com.intercom.ride.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    private fun <T : Message> roundTrip(m: T): Message = Message.decode(Message.encode(m))!!

    @Test fun helloRoundTrip() {
        val nonce = SessionAuth.newNonce()
        val out = roundTrip(Message.Hello(1, "abc123", "Rider Phone ✓", "RIDER", nonce)) as Message.Hello
        assertEquals("abc123", out.deviceId)
        assertEquals("Rider Phone ✓", out.name)
        assertEquals("RIDER", out.role)
        assertArrayEquals(nonce, out.nonce)
    }

    @Test fun audioRoundTrip() {
        val payload = ByteArray(60) { it.toByte() }
        val out = roundTrip(Message.Audio(CodecId.OPUS, 1234, 99_000, payload)) as Message.Audio
        assertEquals(CodecId.OPUS, out.codec)
        assertEquals(1234, out.seq)
        assertEquals(99_000, out.timestampMs)
        assertArrayEquals(payload, out.payload)
    }

    @Test fun audioHeaderIsSmall() {
        assertEquals(10 + 40, Message.encode(Message.Audio(CodecId.OPUS, 1, 1, ByteArray(40))).size)
    }

    @Test fun controlMessagesRoundTrip() {
        val key = roundTrip(Message.SessionKey("sid", ByteArray(32) { 7 })) as Message.SessionKey
        assertEquals("sid", key.sessionId); assertEquals(32, key.secret.size)
        val proof = roundTrip(Message.Proof("sid", ByteArray(32) { 3 })) as Message.Proof
        assertEquals(3.toByte(), proof.mac[5])
        assertEquals(Message.Ping(5, 777L), roundTrip(Message.Ping(5, 777L)))
        assertEquals(Message.Pong(5, 777L), roundTrip(Message.Pong(5, 777L)))
        assertEquals(Message.MuteState(true), roundTrip(Message.MuteState(true)))
        assertTrue(roundTrip(Message.Bye) === Message.Bye)
    }

    @Test fun malformedInputIsRejected() {
        assertNull(Message.decode(ByteArray(0)))
        assertNull(Message.decode(byteArrayOf(99)))
        assertNull(Message.decode(byteArrayOf(1, 0, 0)))
        // Hello claiming a huge string length must not allocate or crash.
        assertNull(Message.decode(byteArrayOf(1, 0, 0, 0, 1, 0x7f, 0x7f, 0x7f, 0x7f)))
    }
}

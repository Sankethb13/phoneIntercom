package com.intercom.ride.audio

import com.intercom.ride.net.CodecId
import com.intercom.ride.net.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * Simulates two phones streaming at the same time over an in-memory link:
 * capture → encode → wire format → jitter buffer → decode, in both
 * directions concurrently. Uses the pure-Kotlin codec so it runs on the JVM.
 */
class FullDuplexPipelineTest {
    private class Phone(val freq: Double) {
        val outbox = LinkedBlockingQueue<ByteArray>()
        val jitter = JitterBuffer(minFrames = 2)
        val enc = MuLawEncoder()
        val dec = MuLawDecoder()
        var seq = 0
        var muted = false
        val heard = ArrayList<ShortArray>()

        fun speakFrame(i: Int) {
            if (!TransmitGate.allows(muted, authorized = true, interrupted = false)) return
            val pcm = ShortArray(AudioConfig.FRAME_SAMPLES) { n ->
                (sin(2 * PI * freq * (i * AudioConfig.FRAME_SAMPLES + n) / AudioConfig.SAMPLE_RATE) * 10000).toInt().toShort()
            }
            enc.encode(pcm).forEach { outbox.put(Message.encode(Message.Audio(enc.codecId, seq++, i * 20, it))) }
        }

        fun receive(bytes: ByteArray, now: Long) {
            val m = Message.decode(bytes) as Message.Audio
            jitter.push(m.seq, m.timestampMs, m.codec, m.payload, now)
        }

        fun playFrame() {
            when (val r = jitter.pop()) {
                is JitterResult.Packet -> heard += dec.decode(r.payload)
                else -> Unit
            }
        }
    }

    @Test fun bothSidesTalkAndHearSimultaneously() {
        val rider = Phone(300.0)
        val pillion = Phone(700.0)
        val frames = 100
        val done = CountDownLatch(2)
        // Both phones "talk" on their own threads at the same time.
        listOf(rider, pillion).forEach { p ->
            Thread { repeat(frames) { p.speakFrame(it) }; done.countDown() }.start()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        var t = 0L
        repeat(frames) {
            t += 20
            rider.outbox.poll()?.let { pillion.receive(it, t) }
            pillion.outbox.poll()?.let { rider.receive(it, t) }
            rider.playFrame(); pillion.playFrame()
        }
        assertTrue("pillion heard ${pillion.heard.size}", pillion.heard.size >= frames - 5)
        assertTrue("rider heard ${rider.heard.size}", rider.heard.size >= frames - 5)
        assertEquals(0L, rider.jitter.lost + pillion.jitter.lost)
        // Each side hears the other's tone, not its own: compare zero-crossing rates.
        assertTrue(zeroCrossings(pillion.heard) < zeroCrossings(rider.heard))
    }

    @Test fun mutedSideSendsNothingButStillHears() {
        val rider = Phone(300.0).apply { muted = true }
        val pillion = Phone(700.0)
        repeat(20) { rider.speakFrame(it); pillion.speakFrame(it) }
        assertEquals(0, rider.outbox.size)
        assertEquals(20, pillion.outbox.size)
        var t = 0L
        while (pillion.outbox.isNotEmpty()) { t += 20; rider.receive(pillion.outbox.take(), t); rider.playFrame() }
        assertTrue(rider.heard.isNotEmpty())
    }

    @Test fun gateBlocksUnauthorisedAndInterruptedAudio() {
        assertTrue(TransmitGate.allows(muted = false, authorized = true, interrupted = false))
        assertTrue(!TransmitGate.allows(muted = false, authorized = false, interrupted = false))
        assertTrue(!TransmitGate.allows(muted = false, authorized = true, interrupted = true))
        assertTrue(!TransmitGate.allows(muted = true, authorized = true, interrupted = false))
        assertEquals("G.711 µ-law", CodecId.name(CodecId.MULAW))
    }

    private fun zeroCrossings(frames: List<ShortArray>): Int {
        var c = 0; var prev = 0
        frames.forEach { f -> f.forEach { s -> if ((s >= 0) != (prev >= 0)) c++; prev = s.toInt() } }
        return c
    }
}

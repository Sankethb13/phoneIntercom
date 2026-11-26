package com.intercom.ride.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {
    private fun JitterBuffer.feed(seq: Int, at: Long) = push(seq, seq * 20, 2, byteArrayOf(seq.toByte()), at)
    private fun JitterResult.seq(): Int = ((this as JitterResult.Packet).payload[0]).toInt()

    @Test fun buffersBeforePlaying() {
        val jb = JitterBuffer(minFrames = 2)
        jb.feed(0, 0)
        assertEquals(JitterResult.Empty, jb.pop())
    }

    @Test fun reordersOutOfOrderPackets() {
        val jb = JitterBuffer(minFrames = 2)
        jb.feed(1, 20); jb.feed(0, 21); jb.feed(2, 40)
        assertEquals(0, jb.pop().seq())
        assertEquals(1, jb.pop().seq())
        assertEquals(2, jb.pop().seq())
    }

    @Test fun reportsLossAndDropsLatePackets() {
        val jb = JitterBuffer(minFrames = 2)
        jb.feed(0, 0); jb.feed(1, 20); jb.feed(3, 60)
        assertEquals(0, jb.pop().seq())
        assertEquals(1, jb.pop().seq())
        assertEquals(JitterResult.Lost, jb.pop())   // seq 2 missing → conceal
        jb.feed(2, 80)                              // arrives too late
        assertEquals(3, jb.pop().seq())
        assertEquals(1L, jb.lost)
        assertEquals(1L, jb.late)
    }

    @Test fun underrunRebuffersAndGrowsTarget() {
        val jb = JitterBuffer(minFrames = 2)
        for (i in 0..2) jb.feed(i, i * 20L)
        repeat(3) { jb.pop() }
        val before = jb.targetMs
        assertEquals(JitterResult.Empty, jb.pop())
        assertEquals(1L, jb.underruns)
        assertTrue(jb.targetMs > before)
    }

    @Test fun adaptsToJitterAndCapsLatency() {
        val jb = JitterBuffer(minFrames = 2, maxFrames = 12)
        // Bursty arrival: packets come in clumps of 5 every 100 ms.
        var t = 0L
        for (i in 0 until 50) { if (i % 5 == 0) t += 100; jb.feed(i, t) }
        assertTrue("target ${jb.targetMs}", jb.targetMs >= 80)
        assertTrue("depth ${jb.depthMs}", jb.depthMs <= 12 * 20)
    }

    @Test fun duplicatesIgnored() {
        val jb = JitterBuffer(minFrames = 2)
        jb.feed(0, 0); jb.feed(0, 1); jb.feed(1, 20); jb.feed(2, 40)
        assertEquals(0, jb.pop().seq()); assertEquals(1, jb.pop().seq())
    }
}

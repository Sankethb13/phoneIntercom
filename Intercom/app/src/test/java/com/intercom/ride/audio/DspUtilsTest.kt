package com.intercom.ride.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DspUtilsTest {
    @Test fun levelOfSilenceAndLoudSignal() {
        assertEquals(0f, DspUtils.level(ShortArray(320)), 0f)
        assertTrue(DspUtils.level(ShortArray(320) { if (it % 2 == 0) 20000 else -20000 }) > 0.9f)
    }

    @Test fun resample48kTo16k() {
        val out = DspUtils.resample(ShortArray(960) { 1000 }, 960, 48_000, 16_000)
        assertEquals(320, out.size)
        assertTrue(out.all { it.toInt() == 1000 })
    }

    @Test fun resampleNonInteger() {
        assertEquals(320, DspUtils.resample(ShortArray(480) { 5 }, 480, 24_000, 16_000).size)
        assertEquals(10, DspUtils.resample(ShortArray(10) { 5 }, 10, 16_000, 16_000).size)
    }

    @Test fun stereoToMono() {
        val m = DspUtils.toMono(shortArrayOf(100, 300, -50, 50), 4, 2)
        assertEquals(listOf<Short>(200, 0), m.toList())
    }
}

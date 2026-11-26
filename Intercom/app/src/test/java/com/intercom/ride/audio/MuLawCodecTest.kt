package com.intercom.ride.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MuLawCodecTest {
    @Test fun roundTripKeepsSpeechWithinG711Error() {
        val pcm = ShortArray(AudioConfig.FRAME_SAMPLES) { (sin(2 * PI * 440 * it / 16000.0) * 12000).toInt().toShort() }
        val enc = MuLawCodec.encode(pcm)
        assertEquals(pcm.size, enc.size)               // 8 bits per sample
        val dec = MuLawCodec.decode(enc)
        for (i in pcm.indices) {
            val err = abs(pcm[i] - dec[i])
            // µ-law quantisation step is about 1/16 of the magnitude, plus a small floor.
            assertTrue("sample $i err $err", err <= abs(pcm[i].toInt()) / 15 + 40)
        }
    }

    @Test fun extremesAndSilence() {
        assertEquals(0, MuLawCodec.decodeSample(MuLawCodec.encodeSample(0)).toInt())
        assertTrue(MuLawCodec.decodeSample(MuLawCodec.encodeSample(32767)) > 30000)
        assertTrue(MuLawCodec.decodeSample(MuLawCodec.encodeSample(-32768)) < -30000)
    }
}

package com.intercom.ride.audio

/**
 * ITU-T G.711 µ-law. Pure Kotlin fallback used when a phone has no Opus
 * encoder. 16 kHz × 8 bit = 128 kbps, which Nearby's Bluetooth and Wi-Fi
 * links carry comfortably.
 */
object MuLawCodec {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun encode(pcm: ShortArray, count: Int = pcm.size): ByteArray =
        ByteArray(count) { encodeSample(pcm[it].toInt()) }

    fun decode(data: ByteArray): ShortArray = ShortArray(data.size) { decodeSample(data[it]) }

    fun encodeSample(sample: Int): Byte {
        var s = sample
        val sign = if (s < 0) 0x80 else 0
        if (s < 0) s = -s
        if (s > CLIP) s = CLIP
        s += BIAS
        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && (s and mask) == 0) {
            exponent--
            mask = mask shr 1
        }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    fun decodeSample(b: Byte): Short {
        val u = b.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u shr 4) and 0x07
        val mantissa = u and 0x0F
        var s = ((mantissa shl 3) + BIAS) shl exponent
        s -= BIAS
        return (if (sign != 0) -s else s).toShort()
    }
}

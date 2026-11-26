package com.intercom.ride.audio

import kotlin.math.log10
import kotlin.math.sqrt

object DspUtils {
    /** RMS level mapped to 0..1 on a −60…0 dBFS scale, for the activity meters. */
    fun level(pcm: ShortArray, count: Int = pcm.size): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val s = pcm[i] / 32768.0
            sum += s * s
        }
        val rms = sqrt(sum / count)
        if (rms <= 1e-6) return 0f
        val db = 20 * log10(rms)
        return ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Linear-interpolating mono resampler. Used when a decoder outputs at a rate
     * other than 16 kHz (Android's Opus decoder commonly outputs 48 kHz). For an
     * exact integer down-ratio it averages each group, which also low-passes.
     */
    fun resample(input: ShortArray, inCount: Int, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || inCount == 0) return input.copyOf(inCount)
        if (fromRate > toRate && fromRate % toRate == 0) {
            val r = fromRate / toRate
            val n = inCount / r
            return ShortArray(n) { i ->
                var acc = 0
                for (k in 0 until r) acc += input[i * r + k]
                (acc / r).toShort()
            }
        }
        val outCount = (inCount.toLong() * toRate / fromRate).toInt()
        val step = fromRate.toDouble() / toRate
        return ShortArray(outCount) { i ->
            val pos = i * step
            val idx = pos.toInt()
            val frac = pos - idx
            val a = input[idx.coerceAtMost(inCount - 1)]
            val b = input[(idx + 1).coerceAtMost(inCount - 1)]
            (a + (b - a) * frac).toInt().toShort()
        }
    }

    /** Down-mix interleaved stereo to mono. */
    fun toMono(interleaved: ShortArray, count: Int, channels: Int): ShortArray {
        if (channels <= 1) return interleaved.copyOf(count)
        val frames = count / channels
        return ShortArray(frames) { f ->
            var acc = 0
            for (c in 0 until channels) acc += interleaved[f * channels + c]
            (acc / channels).toShort()
        }
    }

    /** Fade the previous frame for simple packet-loss concealment. */
    fun attenuate(pcm: ShortArray, factor: Float): ShortArray =
        ShortArray(pcm.size) { (pcm[it] * factor).toInt().toShort() }
}

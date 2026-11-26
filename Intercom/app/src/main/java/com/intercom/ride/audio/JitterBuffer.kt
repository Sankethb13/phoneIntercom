package com.intercom.ride.audio

import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.ceil

sealed class JitterResult {
    class Packet(val codec: Byte, val payload: ByteArray) : JitterResult()
    /** A frame was lost; the player should conceal it. */
    data object Lost : JitterResult()
    /** Nothing to play yet (buffering or underrun); the player outputs silence. */
    data object Empty : JitterResult()
}

/**
 * Adaptive jitter buffer keyed by sequence number.
 *
 * - Reorders packets and drops late/duplicate ones.
 * - Estimates inter-arrival jitter (RFC 3550 style) and sizes the target depth to it.
 * - Grows after an underrun, and trims old frames when latency builds up, so delay
 *   stays low on a good link and audio stays smooth on a bad one.
 *
 * Thread-safe: network thread calls [push], playback thread calls [pop].
 */
class JitterBuffer(
    private val frameMs: Int = AudioConfig.FRAME_MS,
    private val minFrames: Int = 2,
    private val maxFrames: Int = 12,
) {
    private class Entry(val codec: Byte, val payload: ByteArray)

    private val lock = Any()
    private val packets = TreeMap<Int, Entry>()
    private var playing = false
    private var nextSeq = 0
    private var jitterMs = 0.0
    private var lastArrival = -1L
    private var lastTimestamp = 0
    private var targetFrames = minFrames + 1

    var received = 0L; private set
    var lost = 0L; private set
    var late = 0L; private set
    var underruns = 0L; private set

    fun push(seq: Int, timestampMs: Int, codec: Byte, payload: ByteArray, arrivalMs: Long) = synchronized(lock) {
        received++
        if (lastArrival >= 0) {
            val d = (arrivalMs - lastArrival) - (timestampMs - lastTimestamp).toLong()
            jitterMs += (abs(d.toDouble()) - jitterMs) / 16.0
        }
        lastArrival = arrivalMs
        lastTimestamp = timestampMs
        recomputeTarget()

        if (playing && seq < nextSeq) { late++; return }
        if (packets.containsKey(seq)) return
        packets[seq] = Entry(codec, payload)

        // Latency control: never let delay grow far beyond the target.
        if (packets.size > maxFrames) {
            while (packets.size > targetFrames) packets.pollFirstEntry()
            if (playing) nextSeq = packets.firstKey()
        }
    }

    fun pop(): JitterResult = synchronized(lock) {
        if (!playing) {
            if (packets.size < targetFrames) return JitterResult.Empty
            playing = true
            nextSeq = packets.firstKey()
        }
        if (packets.isEmpty()) {
            underruns++
            playing = false
            targetFrames = (targetFrames + 1).coerceAtMost(maxFrames)
            return JitterResult.Empty
        }
        val e = packets.remove(nextSeq)
        nextSeq++
        if (e == null) {
            lost++
            return JitterResult.Lost
        }
        JitterResult.Packet(e.codec, e.payload)
    }

    fun reset() = synchronized(lock) {
        packets.clear(); playing = false; lastArrival = -1; jitterMs = 0.0
        targetFrames = minFrames + 1
        received = 0; lost = 0; late = 0; underruns = 0
    }

    val depthMs: Int get() = synchronized(lock) { packets.size * frameMs }
    val targetMs: Int get() = synchronized(lock) { targetFrames * frameMs }
    val jitterEstimateMs: Double get() = synchronized(lock) { jitterMs }

    fun lossPercent(): Float = synchronized(lock) {
        val total = received + lost
        if (total == 0L) 0f else lost * 100f / total
    }

    private fun recomputeTarget() {
        val wanted = ceil((2.0 * jitterMs + frameMs) / frameMs).toInt()
        // Only shrink slowly (one frame at a time) to avoid oscillation.
        targetFrames = when {
            wanted > targetFrames -> wanted
            wanted < targetFrames -> targetFrames - 1
            else -> targetFrames
        }.coerceIn(minFrames, maxFrames)
    }
}

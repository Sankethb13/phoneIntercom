package com.intercom.ride.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.intercom.ride.net.CodecId
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Opus encoder using Android's platform codec (available from Android 10,
 * so no native library is shipped). Synchronous mode keeps latency tiny.
 */
class OpusEncoder(bitrate: Int) : AudioEncoder {
    override val codecId = CodecId.OPUS
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val info = MediaCodec.BufferInfo()
    private var ptsUs = 0L

    init {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, AudioConfig.SAMPLE_RATE, 1)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AudioConfig.FRAME_SAMPLES * 2)
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            throw e
        }
    }

    override fun encode(pcm: ShortArray): List<ByteArray> {
        val inIdx = codec.dequeueInputBuffer(5_000)
        if (inIdx >= 0) {
            val buf = codec.getInputBuffer(inIdx) ?: return emptyList()
            buf.clear()
            buf.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm)
            codec.queueInputBuffer(inIdx, 0, pcm.size * 2, ptsUs, 0)
            ptsUs += AudioConfig.FRAME_MS * 1000L
        }
        val out = ArrayList<ByteArray>(1)
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 0)
            if (idx < 0) {
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break else continue
            }
            val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (!isConfig && info.size > 0) {
                val ob = codec.getOutputBuffer(idx)
                if (ob != null) {
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    out += ByteArray(info.size).also { ob.get(it) }
                }
            }
            codec.releaseOutputBuffer(idx, false)
        }
        return out
    }

    override fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }
}

/** Opus decoder using the platform codec; output is converted to 16 kHz mono. */
class OpusDecoder : AudioDecoder {
    private val codec: MediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val info = MediaCodec.BufferInfo()
    private var outRate = OPUS_RATE
    private var outChannels = 1
    private var ptsUs = 0L

    init {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, OPUS_RATE, 1)
        fmt.setByteBuffer("csd-0", ByteBuffer.wrap(opusHead()))
        fmt.setByteBuffer("csd-1", nanos(PRE_SKIP * 1_000_000_000L / OPUS_RATE))
        fmt.setByteBuffer("csd-2", nanos(80_000_000L))
        try {
            codec.configure(fmt, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            throw e
        }
    }

    override fun decode(payload: ByteArray): ShortArray {
        val inIdx = codec.dequeueInputBuffer(5_000)
        if (inIdx >= 0) {
            val buf = codec.getInputBuffer(inIdx) ?: return ShortArray(0)
            buf.clear(); buf.put(payload)
            codec.queueInputBuffer(inIdx, 0, payload.size, ptsUs, 0)
            ptsUs += AudioConfig.FRAME_MS * 1000L
        }
        var collected = ShortArray(0)
        var wait = 4_000L
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, wait)
            wait = 0
            when {
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    outRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    outChannels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                idx < 0 -> if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                else -> {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(idx)
                        if (ob != null) {
                            ob.position(info.offset); ob.limit(info.offset + info.size)
                            val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val raw = ShortArray(sb.remaining()).also { sb.get(it) }
                            val mono = DspUtils.toMono(raw, raw.size, outChannels)
                            val pcm = DspUtils.resample(mono, mono.size, outRate, AudioConfig.SAMPLE_RATE)
                            collected = if (collected.isEmpty()) pcm else collected + pcm
                        }
                    }
                    codec.releaseOutputBuffer(idx, false)
                }
            }
        }
        return collected
    }

    override fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private companion object {
        const val OPUS_RATE = 48_000
        const val PRE_SKIP = 312

        /** RFC 7845 identification header for a mono stream. */
        fun opusHead(): ByteArray = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray(Charsets.US_ASCII))
            put(1)                                   // version
            put(1)                                   // channels
            putShort(PRE_SKIP.toShort())             // pre-skip
            putInt(AudioConfig.SAMPLE_RATE)          // original input rate
            putShort(0)                              // output gain
            put(0)                                   // mapping family
        }.array()

        fun nanos(v: Long): ByteBuffer =
            ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(v).apply { flip() }
    }
}

object CodecFactory {
    private const val TAG = "CodecFactory"

    /** Opus when the phone has an encoder, else µ-law. Each side picks independently. */
    fun createEncoder(quality: AudioQuality): AudioEncoder = try {
        OpusEncoder(quality.opusBitrate)
    } catch (e: Exception) {
        Log.w(TAG, "Opus encoder unavailable, falling back to µ-law", e)
        MuLawEncoder()
    }

    fun createDecoder(codecId: Byte): AudioDecoder? = when (codecId) {
        CodecId.OPUS -> try { OpusDecoder() } catch (e: Exception) {
            Log.e(TAG, "Opus decoder unavailable", e); null
        }
        CodecId.MULAW -> MuLawDecoder()
        else -> null
    }
}

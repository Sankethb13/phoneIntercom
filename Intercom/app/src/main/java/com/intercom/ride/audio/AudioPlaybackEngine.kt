package com.intercom.ride.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * Pulls frames from the jitter buffer every 20 ms, decodes them and plays
 * them through a low-latency voice-communication AudioTrack. AudioTrack's
 * blocking write is the playout clock.
 */
class AudioPlaybackEngine(
    private val jitter: JitterBuffer,
    private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile private var running = false
    @Volatile var paused = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private val decoders = HashMap<Byte, AudioDecoder>()
    private var lastFrame = ShortArray(AudioConfig.FRAME_SAMPLES)
    private var concealCount = 0

    @Volatile var activeCodec: Byte = 0; private set

    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioTrack.getMinBufferSize(
            AudioConfig.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, AudioConfig.FRAME_SAMPLES * 2 * 3))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            onError("Speaker unavailable: ${e.message}"); return false
        }
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release(); onError("Could not open audio output"); return false
        }
        t.play()
        track = t
        running = true
        thread = Thread({ loop(t) }, "ic-playback").apply { start() }
        return true
    }

    private fun loop(t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val silence = ShortArray(AudioConfig.FRAME_SAMPLES)
        while (running) {
            if (paused) {
                t.write(silence, 0, silence.size)
                continue
            }
            val pcm: ShortArray = when (val r = jitter.pop()) {
                is JitterResult.Packet -> decode(r)
                JitterResult.Lost -> conceal()
                JitterResult.Empty -> { onLevel(0f); silence }
            }
            if (pcm.isEmpty()) continue
            val w = t.write(pcm, 0, pcm.size)
            if (w < 0) {
                Log.e(TAG, "AudioTrack.write error $w")
                if (running) onError("Audio output stopped (error $w)")
                running = false
            }
        }
    }

    private fun decode(p: JitterResult.Packet): ShortArray {
        val dec = decoders.getOrPut(p.codec) {
            CodecFactory.createDecoder(p.codec) ?: return ShortArray(0)
        }
        activeCodec = p.codec
        val pcm = try { dec.decode(p.payload) } catch (e: Exception) {
            Log.w(TAG, "decode failed", e); return conceal()
        }
        if (pcm.isNotEmpty()) {
            lastFrame = pcm
            concealCount = 0
            onLevel(DspUtils.level(pcm))
        }
        return pcm
    }

    /** Repeat the last frame, fading out, for up to three lost frames. */
    private fun conceal(): ShortArray {
        concealCount++
        if (concealCount > 3) return ShortArray(AudioConfig.FRAME_SAMPLES)
        lastFrame = DspUtils.attenuate(lastFrame, 0.5f)
        return lastFrame
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        decoders.values.forEach { it.release() }
        decoders.clear()
    }

    private companion object { const val TAG = "AudioPlayback" }
}

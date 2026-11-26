package com.intercom.ride.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log

/**
 * Continuously captures 20 ms microphone frames on a real-time thread.
 * Uses the VOICE_COMMUNICATION source so the platform echo canceller is
 * engaged, plus explicit AEC/NS/AGC effects where the phone provides them.
 * Nothing is ever written to storage.
 */
class AudioCaptureEngine(
    private val onFrame: (ShortArray) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    @Volatile var noiseSuppression = true
        set(value) { field = value; ns?.setEnabled(value) }

    val effectsSummary: String
        get() = listOfNotNull(
            if (aec?.enabled == true) "Echo cancel" else null,
            if (ns?.enabled == true) "Noise suppression" else null,
            if (agc?.enabled == true) "Auto gain" else null,
        ).ifEmpty { listOf("Platform voice processing") }.joinToString(" · ")

    /** Caller must have verified RECORD_AUDIO. Returns false if the mic could not be opened. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) { onError("Microphone does not support 16 kHz capture"); return false }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AudioConfig.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, AudioConfig.FRAME_SAMPLES * 2 * 4),
            )
        } catch (e: Exception) {
            onError("Microphone unavailable: ${e.message}"); return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); onError("Microphone is in use by another app"); return false
        }
        attachEffects(rec.audioSessionId)
        try { rec.startRecording() } catch (e: Exception) {
            releaseEffects(); rec.release(); onError("Could not start microphone: ${e.message}"); return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            releaseEffects(); rec.release(); onError("Microphone is in use by another app"); return false
        }
        record = rec
        running = true
        thread = Thread({ loop(rec) }, "ic-capture").apply { start() }
        return true
    }

    private fun loop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frame = ShortArray(AudioConfig.FRAME_SAMPLES)
        while (running) {
            var filled = 0
            while (filled < frame.size && running) {
                val n = rec.read(frame, filled, frame.size - filled)
                if (n < 0) {
                    Log.e(TAG, "AudioRecord.read error $n")
                    if (running) onError("Microphone stopped (error $n)")
                    running = false
                    return
                }
                filled += n
            }
            if (!running) break
            onLevel(DspUtils.level(frame))
            onFrame(frame.copyOf())
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        releaseEffects()
    }

    private fun attachEffects(session: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            aec = runCatching { AcousticEchoCanceler.create(session)?.apply { setEnabled(true) } }.getOrNull()
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = runCatching { NoiseSuppressor.create(session)?.apply { setEnabled(noiseSuppression) } }.getOrNull()
        }
        if (AutomaticGainControl.isAvailable()) {
            agc = runCatching { AutomaticGainControl.create(session)?.apply { setEnabled(true) } }.getOrNull()
        }
    }

    private fun releaseEffects() {
        aec?.release(); ns?.release(); agc?.release()
        aec = null; ns = null; agc = null
    }

    private companion object { const val TAG = "AudioCapture" }
}

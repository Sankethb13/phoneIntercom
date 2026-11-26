package com.intercom.ride.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the whole voice pipeline:
 *   mic → AudioCaptureEngine → AudioEncoder → [network]
 *   [network] → JitterBuffer → AudioDecoder → AudioPlaybackEngine → headset
 * plus audio focus (phone calls interrupt us and we resume afterwards),
 * communication mode and headset routing.
 */
class AudioSessionManager(
    context: Context,
    private val sendPacket: (codec: Byte, seq: Int, timestampMs: Int, payload: ByteArray) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val router = AudioRouter(am)
    val jitter = JitterBuffer()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()
    private val _speakerLevel = MutableStateFlow(0f)
    val speakerLevel: StateFlow<Float> = _speakerLevel.asStateFlow()
    private val _interrupted = MutableStateFlow(false)
    val interrupted: StateFlow<Boolean> = _interrupted.asStateFlow()

    @Volatile var muted = false
    /** Only true once the peer is authorized; gates outgoing audio. */
    @Volatile var transmitEnabled = false

    private var encoder: AudioEncoder? = null
    private var capture: AudioCaptureEngine? = null
    private var playback: AudioPlaybackEngine? = null
    private var focusRequest: AudioFocusRequest? = null
    private var seq = 0
    private val startedAt = System.currentTimeMillis()
    @Volatile var running = false; private set

    var quality: AudioQuality = AudioQuality.STANDARD
    var noiseSuppression = true
        set(value) { field = value; capture?.noiseSuppression = value }

    val encoderName: String get() = encoder?.let { com.intercom.ride.net.CodecId.name(it.codecId) } ?: "-"
    val decoderName: String get() = playback?.activeCodec?.let { com.intercom.ride.net.CodecId.name(it) } ?: "-"
    val effects: String get() = capture?.effectsSummary ?: "-"

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // A phone call or another voice app took over; stay connected but go quiet.
                _interrupted.value = true
                playback?.paused = true
                jitter.reset()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                _interrupted.value = false
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                router.reroute()
                playback?.paused = false
            }
        }
    }

    fun start(): Boolean {
        if (running) return true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val fr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = fr
        if (am.requestAudioFocus(fr) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus not granted (call in progress?)")
            _interrupted.value = true
        }
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        router.start()

        encoder = CodecFactory.createEncoder(quality)
        val pb = AudioPlaybackEngine(jitter, { _speakerLevel.value = it }, onError)
        pb.paused = _interrupted.value
        val cap = AudioCaptureEngine(::onMicFrame, { _micLevel.value = if (muted) 0f else it }, onError)
        cap.noiseSuppression = noiseSuppression
        if (!pb.start() || !cap.start()) {
            pb.stop(); cap.stop()
            stop()
            return false
        }
        playback = pb
        capture = cap
        running = true
        return true
    }

    private fun onMicFrame(pcm: ShortArray) {
        if (!TransmitGate.allows(muted, transmitEnabled, _interrupted.value)) return
        val enc = encoder ?: return
        val ts = (System.currentTimeMillis() - startedAt).toInt()
        try {
            for (packet in enc.encode(pcm)) sendPacket(enc.codecId, seq++, ts, packet)
        } catch (e: Exception) {
            Log.e(TAG, "encode failed", e)
        }
    }

    fun onIncoming(codec: Byte, seq: Int, timestampMs: Int, payload: ByteArray) {
        jitter.push(seq, timestampMs, codec, payload, System.currentTimeMillis())
    }

    fun stop() {
        running = false
        transmitEnabled = false
        capture?.stop(); capture = null
        playback?.stop(); playback = null
        encoder?.release(); encoder = null
        router.stop()
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
        am.mode = AudioManager.MODE_NORMAL
        jitter.reset()
        _micLevel.value = 0f; _speakerLevel.value = 0f; _interrupted.value = false
    }

    private companion object { const val TAG = "AudioSession" }
}

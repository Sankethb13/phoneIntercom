package com.intercom.ride.audio

/** Fixed speech format used end-to-end: 16 kHz mono 16-bit, 20 ms frames. */
object AudioConfig {
    const val SAMPLE_RATE = 16_000
    const val FRAME_MS = 20
    const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000 // 320
}

/** User-selectable quality → Opus bitrate. */
enum class AudioQuality(val label: String, val opusBitrate: Int) {
    DATA_SAVER("Battery saver (16 kbps)", 16_000),
    STANDARD("Standard (24 kbps)", 24_000),
    HIGH("High (32 kbps)", 32_000);

    companion object {
        fun fromName(n: String?): AudioQuality = entries.firstOrNull { it.name == n } ?: STANDARD
    }
}

/** Single rule for whether a captured frame may leave the phone. */
object TransmitGate {
    fun allows(muted: Boolean, authorized: Boolean, interrupted: Boolean): Boolean =
        !muted && authorized && !interrupted
}

# Audio engine

**Format:** 16 kHz, mono, 16-bit PCM, 20 ms frames (320 samples).

```
AudioRecord(VOICE_COMMUNICATION) → AEC/NS/AGC → TransmitGate(mute, authorized, call) → AudioEncoder → Nearby
Nearby → JitterBuffer → AudioDecoder → (resample to 16 kHz) → AudioTrack(USAGE_VOICE_COMMUNICATION, LOW_LATENCY)
```

## Codec
- **Opus** through Android's built-in `MediaCodec` (`audio/opus`). The encoder ships in the platform from Android 10, so no native library is bundled. Bitrate 16/24/32 kbps (Settings → Audio quality).
- **G.711 µ-law** pure-Kotlin fallback if a phone lacks an Opus encoder.
- Each packet carries its codec ID; each phone encodes with what it has and decodes what it receives, so mixed phones work with no negotiation.
- Android's Opus decoder outputs 48 kHz; it is down-mixed/resampled to 16 kHz. The decoder is configured with a synthesized RFC 7845 `OpusHead`.

## Echo, noise, gain
`VOICE_COMMUNICATION` source + `MODE_IN_COMMUNICATION` engage the platform voice DSP. `AcousticEchoCanceler`, `NoiseSuppressor` (toggle in Settings) and `AutomaticGainControl` are attached when available. With headsets acoustic echo is minimal; on speaker the platform AEC handles it.

## Jitter buffer
Sequence-ordered; starts at 3 frames (60 ms). Estimates inter-arrival jitter RFC 3550-style and sets target ≈ 2×jitter + 1 frame (40–240 ms). Underrun → rebuffer and grow; excess depth → drop oldest to cap latency; shrink one frame at a time when stable. Late/duplicate packets dropped. Lost frames → repeat last frame at −6 dB up to 3 times, then silence.

## Latency budget (typical, Wi-Fi-upgraded link)
capture 20 ms + encode ~5 + network 10–40 + jitter 40–80 + playout ~20–40 ≈ **100–180 ms** mouth-to-ear. On Bluetooth-only links expect more.

## Routing (`AudioRouter`)
Priority: BLE Audio headset → Bluetooth HFP/SCO → wired → USB → built-in speaker. Android 12+: `setCommunicationDevice`; Android 10–11: `startBluetoothSco` / speakerphone. Re-evaluated on every `AudioDeviceCallback`, so a helmet unit can disconnect and reconnect mid-ride.

## Interruptions
Audio focus (`AUDIOFOCUS_GAIN`, voice-communication attributes). A phone call or other voice app → playback paused, sending stopped, link kept. On focus regain → comm mode and routing re-applied, audio resumes.

## Privacy
Audio exists only in memory buffers. Nothing is recorded or written to storage.

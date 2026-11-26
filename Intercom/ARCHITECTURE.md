# Architecture

Fully native Kotlin. The original brief allowed React Native, but every core feature here (Nearby Connections, real-time AudioRecord/AudioTrack, MediaCodec Opus, audio routing, a microphone foreground service) is native-only; a JS bridge would add latency and failure points on the 20 ms audio path while giving nothing back. The UI is Jetpack Compose.

```
 UI (Compose)  ──▶  IntercomViewModel  ──▶  ConnectionSessionManager  ◀── IntercomForegroundService
                                              │        │                  (keeps process + mic alive)
                         ┌────────────────────┘        └───────────────┐
                 Networking                                       Audio
   DeviceDiscoveryService  PeerConnectionManager      AudioSessionManager
        (advertise/discover)   (links, payloads)       ├─ AudioCaptureEngine → AudioEncoder ─┐
                 └── Nearby Connections ──┘            ├─ JitterBuffer → AudioDecoder → AudioPlaybackEngine
                                                       └─ AudioRouter (headsets), audio focus, comm mode
   Protocol (Message), SessionAuth, EndpointInfo, ReconnectPolicy, ConnectionStateManager (pure, unit-tested)
```

| Module | File | Role |
|---|---|---|
| DeviceDiscoveryService | `net/NearbyTransport.kt` | Advertise + discover over Nearby |
| PeerConnectionManager | `net/NearbyTransport.kt` | Request/accept/reject/send/disconnect |
| ConnectionSessionManager | `net/ConnectionSessionManager.kt` | Orchestrates everything; single source of truth |
| ConnectionStateManager | `core/ConnectionStateManager.kt` | Pure state machine (prevents duplicates) |
| Protocol / SessionAuth | `net/Protocol.kt`, `net/SessionAuth.kt` | Wire format, HMAC session proofs |
| AudioCaptureEngine | `audio/AudioCaptureEngine.kt` | Mic → 20 ms frames, AEC/NS/AGC |
| AudioEncoder/Decoder | `audio/AudioCodec.kt`, `audio/OpusCodec.kt` | Opus via MediaCodec; µ-law fallback |
| JitterBuffer | `audio/JitterBuffer.kt` | Reorder, loss detection, adaptive delay |
| AudioPlaybackEngine | `audio/AudioPlaybackEngine.kt` | Decode + low-latency AudioTrack |
| AudioRouter | `audio/AudioRouter.kt` | Headset selection & hot-plug |
| AudioSessionManager | `audio/AudioSessionManager.kt` | Pipeline, focus, comm mode |
| IntercomForegroundService | `service/IntercomForegroundService.kt` | Screen-off operation, notification |
| PermissionManager | `permissions/` | Per-SDK permissions, radio checks |
| SettingsRepository | `core/SettingsRepository.kt` | Local preferences |

**Threads:** Nearby callbacks and session logic on the main thread (coroutines for timers, pings, reconnect); capture and playback each on a dedicated `THREAD_PRIORITY_URGENT_AUDIO` thread (coroutine dispatchers are not suitable for hard 20 ms deadlines). Cross-thread fields are `@Volatile`; the jitter buffer is synchronized.

**Errors:** every failure maps to a typed `IntercomError` with a user message, surfaced either as `ConnectionState.Failed` (blocking) or a warning banner (non-blocking).

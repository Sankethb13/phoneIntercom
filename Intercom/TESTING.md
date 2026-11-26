# Testing

## Automated (JVM unit tests) — `./gradlew testDebugUnitTest`

| Area | Test |
|---|---|
| Discovery / connection / rejection / duplicates / reconnection | `ConnectionStateManagerTest`, `ReconnectPolicyTest`, `EndpointInfoTest` |
| Secure session authorization | `SessionAuthTest` |
| Wire protocol & malformed input | `ProtocolTest` |
| Audio encode/decode | `MuLawCodecTest`, `DspUtilsTest` |
| Jitter, reorder, loss, late packets, underrun | `JitterBufferTest` |
| Full-duplex streaming (two simulated phones on threads), mute, interruption gating | `FullDuplexPipelineTest` |
| Permissions per Android version | `PermissionRequirementsTest` |
| Link quality grading | `LinkQualityEstimatorTest` |

Status at hand-off: **44/44 passing** when compiled and run with Kotlin 2.0.21 on JDK 21 (pure-logic sources + tests). The Android-framework classes (Nearby, MediaCodec Opus, AudioRecord/Track, foreground service) cannot run on the JVM; they are verified by the CI build compiling them and by the device plan below. Opus via MediaCodec and the foreground-service lifecycle need instrumented/on-device tests — not yet automated.

## Physical-device test plan

Setup: **Phone A = Rider**, **Phone B = Pillion**. Same APK on both. **Airplane mode ON, then turn Bluetooth and Wi-Fi back ON** (guarantees no internet/cellular). Headsets paired to each phone.

| # | Step | Expected |
|---|---|---|
| 1 | Open app on both, Start, pick roles, grant permissions | Device list; each sees the other within ~10 s |
| 2 | A taps Connect on B | B shows request + 4-digit code equal to A's |
| 3 | B taps Reject | A shows "declined"; both can retry |
| 4 | Connect again, B Accepts | Both show Connected, timer runs, quality shown |
| 5 | Both talk at once | Each hears the other clearly, no echo |
| 6 | A mutes | B hears nothing, sees "(muted)"; A still hears B |
| 7 | Lock both screens 5 min, pockets | Audio continues |
| 8 | Switch A to another app (maps, music) | Audio continues |
| 9 | Call phone A from a third phone (if SIM) | Intercom pauses; resumes after call |
| 10 | Turn headset off/on mid-session | Falls back to speaker, returns to headset |
| 11 | Walk B away until link drops, return | "Reconnecting", then Connected without user action |
| 12 | B toggles Bluetooth off 10 s, on | Reconnects |
| 13 | A taps Disconnect | Both return to list; no auto-reconnect |
| 14 | 30-min ride test on the bike | Note dropouts, latency, battery % used |
| 15 | Deny mic permission | Clear error with Grant / App settings |
| 16 | Turn off Bluetooth before discovery | "Turn on Bluetooth" prompt |

Record phone models, Android versions, headset models and results for each run. Do not mark this plan as passed until it has actually been performed.

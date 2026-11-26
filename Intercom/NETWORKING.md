# Networking

## Transport evaluation

| Option | Verdict |
|---|---|
| **Nearby Connections (P2P_POINT_TO_POINT)** | **Chosen.** Discovers over BLE/Bluetooth, connects over Bluetooth, auto-upgrades to Wi-Fi Direct/hotspot when possible. Works with zero internet. Encrypted (UKEY2) with a human-verifiable auth code. Handles radio quirks across OEMs. |
| Bluetooth Classic RFCOMM | Works offline, but needs pairing UI, ~100–200 kbps practical, and an active audio headset competes for the same radio. Nearby uses it anyway as its base layer. |
| BLE GATT/L2CAP | Too little throughput/too much jitter for steady duplex voice on many phones. |
| Wi-Fi Direct (raw) | Fast, but group-owner negotiation prompts, flaky reconnects and OEM bugs; Nearby wraps it with fallbacks. |
| Wi-Fi Aware (NAN) | Not supported on many phones. |
| Local hotspot + sockets | Requires one user to configure a hotspot; breaks the "just open the app" flow. |

Voice needs ~20–40 kbps (Opus) or 128 kbps (µ-law fallback) each way, which every Nearby medium carries.

## Discovery & connection

- Both phones **advertise and discover** simultaneously with service ID `com.intercom.ride.v1`.
- Advertised endpoint info: `IC1|<deviceId>|<role>|<name>` (`EndpointInfo`). `deviceId` is a random ID stored locally so a phone can be recognised again after a dropout (Nearby endpoint IDs change).
- User taps **Connect** → `requestConnection`. The other phone shows the request with Nearby's **4-digit auth code**; both screens show the same code. Accept/Reject.
- While a session exists, every other incoming request is rejected (no duplicate connections). Discovery and advertising stop once linked.

## Session security

1. Nearby link encryption (UKEY2 key exchange, authenticated by the visible code).
2. After linking, each phone sends `Hello{version, deviceId, name, role, nonce}`.
3. Fresh session: the "leader" (lower `deviceId`) sends `SessionKey{sessionId, 256-bit secret}` over the encrypted link.
4. Reconnect: each side must send `Proof = HMAC-SHA256(secret, peerNonce ‖ ownDeviceId)`. Mismatch → disconnect and error.
5. Audio is neither sent nor played until authorized; packets from any other endpoint are dropped. An 8 s auth timeout tears down stuck handshakes.
The secret lives only in memory for the session.

## Wire format (`Protocol.kt`)

One Nearby BYTES payload = one message. Audio: `type(1) codec(1) seq(4) ts(4) payload` — 10 bytes overhead, ~60-byte Opus frames every 20 ms. Control: Hello, SessionKey, Proof, Ping/Pong (1 Hz RTT probe), MuteState, Bye (deliberate hang-up: no reconnect).

## Reconnection

On unexpected disconnect with auto-reconnect on: state → `Reconnecting`, both phones restart advertise+discover, and only the leader re-dials when the known `deviceId` reappears (avoids both dialling at once). The follower auto-accepts only that device, which must then pass the HMAC proof. Backoff 1 s → 8 s, up to 40 attempts (~5 min); discovery is restarted every 6 attempts. The microphone stays open through the gap (Android 14 forbids reopening it from the background).

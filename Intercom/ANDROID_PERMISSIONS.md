# Android permissions

| Permission | Android | Why |
|---|---|---|
| RECORD_AUDIO | all | Microphone |
| ACCESS_FINE/COARSE_LOCATION | 10–12L (≤32) | Required by Android for Bluetooth/Wi-Fi scanning on these versions. Location is not read. |
| BLUETOOTH, BLUETOOTH_ADMIN | 10–11 (≤30) | Legacy Bluetooth |
| BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT | 12+ | Nearby over Bluetooth/BLE; headset routing |
| NEARBY_WIFI_DEVICES (neverForLocation) | 13+ | Nearby over Wi-Fi Direct/hotspot |
| ACCESS/CHANGE_WIFI_STATE, ACCESS/CHANGE_NETWORK_STATE | all (normal) | Wi-Fi upgrade of the link |
| MODIFY_AUDIO_SETTINGS | all (normal) | Communication mode, headset routing |
| FOREGROUND_SERVICE, _MICROPHONE, _CONNECTED_DEVICE | all / 14+ | Screen-off operation |
| POST_NOTIFICATIONS | 13+ | Ongoing-call notification (optional; app works without) |
| WAKE_LOCK | all | Keep CPU awake during a session |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | all | Optional button in Settings |

Runtime handling: requested together on first reaching the device list. Missing critical permissions → error card with **Grant** and **Open app settings** (for "Don't ask again"). Bluetooth off → system enable dialog. Location services off (≤12L) → opens Location settings. Wi-Fi off → non-blocking warning.

Foreground service starts only from a user tap (connect/accept) while the app is visible, as Android 12+/14+ require. Service is not sticky: if Android kills it, the mic is not silently re-opened.

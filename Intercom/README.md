# Intercom — offline Android-to-Android motorcycle intercom

Two Android phones, two headsets, **no internet, no SIM, no servers**. Rider and pillion talk and listen at the same time (full-duplex, not push-to-talk) over a direct phone-to-phone link built from Bluetooth and local Wi-Fi.

- **App ID:** `com.intercom.ride` · **Min Android:** 10 (API 29) · **Target:** API 35
- **Stack:** 100 % native Kotlin, Jetpack Compose UI, Google Nearby Connections, Android `AudioRecord`/`AudioTrack`/`MediaCodec` (Opus)

## Get the APK (from a phone, no laptop)

1. Create an empty GitHub repository and upload this project (in a Codespace: drag the zip into the Explorer, then `unzip Intercom.zip && cd Intercom`).
2. Push to `main`:
   ```bash
   git init && git add . && git commit -m "Intercom" && git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git && git push -u origin main
   ```
3. Open the repo's **Actions** tab → **Build Intercom APK** → latest run → **Artifacts** → download `intercom-release-apk` (or `intercom-debug-apk`). Unzip; the `.apk` is inside.
4. Install the same APK on both phones (allow "Install unknown apps" for your browser/files app when asked).

The build takes about 6–10 minutes. Details and stable signing: [BUILD_AND_RELEASE.md](BUILD_AND_RELEASE.md).

## Use it

1. Pair each helmet headset with its own phone in Android Bluetooth settings (it must support phone calls).
2. Turn **Bluetooth on** on both phones (Wi-Fi on too for better quality — no network needed). On Android 10–12, Location must be on.
3. Open Intercom on both → **Start** → choose **Rider** or **Pillion** → grant permissions.
4. On one phone tap **Connect** next to the other phone. On the other phone check the 4-digit code matches, then **Accept**.
5. Talk. Lock the screen and pocket the phone — the link keeps running; a notification has Mute and Disconnect.

## Features

Real offline discovery · accept/reject with matching security code · encrypted link + session authorization · full-duplex Opus voice (µ-law fallback) · adaptive jitter buffer & loss concealment · echo cancellation, noise suppression, AGC · Bluetooth (HFP/LE Audio), wired and USB headsets with live switching · foreground service for screen-off use · audio-focus handling for phone calls · automatic reconnection · link-quality, round-trip, loss and audio meters · dark UI with 6 screens.

## Docs

[ARCHITECTURE](ARCHITECTURE.md) · [NETWORKING](NETWORKING.md) · [AUDIO_ENGINE](AUDIO_ENGINE.md) · [ANDROID_PERMISSIONS](ANDROID_PERMISSIONS.md) · [BUILD_AND_RELEASE](BUILD_AND_RELEASE.md) · [TESTING](TESTING.md) · [CHANGELOG](CHANGELOG.md)

## Known limitations

- Needs **Google Play services** on both phones (Nearby Connections lives there). Phones without it (e.g. Huawei with HMS only) are not supported.
- Range is that of phone-to-phone Bluetooth/Wi-Fi Direct: typically 10–50 m in the open; on one bike the phones are under 1 m apart.
- A2DP-only earbuds/headphones have no usable microphone in call mode; the phone mic is used. Helmet intercom units must expose the **Hands-Free (HFP)** profile.
- Some OEM battery managers (Xiaomi, Oppo, Vivo, Samsung "deep sleep") can still kill background apps; set Intercom to *Unrestricted* battery use (Settings screen has a button).
- Two phones per session (point-to-point). Group riding is not implemented.
- Physical two-device testing has not been performed by the author of this code; follow [TESTING.md](TESTING.md).

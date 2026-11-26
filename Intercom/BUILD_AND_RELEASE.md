# Build & release

## Cloud build (GitHub Actions) — recommended
`.github/workflows/android.yml` runs on every push to `main`, on tags `v*`, and manually (**Actions → Build Intercom APK → Run workflow**). It:
1. runs unit tests, 2. builds the **debug APK**, 3. builds the **release APK** and **production AAB**, 4. runs lint, 5. uploads artifacts (`intercom-debug-apk`, `intercom-release-apk`, `intercom-release-aab`, `reports`).
Pushing a tag like `v1.0.0` also attaches the APK/AAB to a GitHub Release (a stable download link).

### Signing
Without secrets the release is signed with a **throwaway** key generated in the runner — installable, but each build has a different key, so you must uninstall before installing a newer build. For stable signing (needed for updates and Play Store):
```bash
bash scripts/make-release-keystore.sh      # in a Codespace
```
Then add repository secrets `INTERCOM_KEYSTORE_BASE64`, `INTERCOM_KEYSTORE_PASSWORD`, `INTERCOM_KEY_ALIAS`, `INTERCOM_KEY_PASSWORD` (GitHub → Settings → Secrets and variables → Actions). Download the `.jks` and keep it safe; never commit it (`.gitignore` blocks `*.jks`, `keystore.properties`).

## Codespaces build
```bash
bash scripts/setup-android-sdk.sh   # once (also runs automatically via .devcontainer)
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk  (right-click → Download)
```
Use a 4-core / 8 GB Codespace.

## Why not EAS Build
The app is native Kotlin (see ARCHITECTURE.md), so Expo/EAS is not used; GitHub Actions builds it with the standard Android Gradle Plugin.

## Versions
Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.0.21 · compileSdk/targetSdk 35 · minSdk 29 · JDK 17 · Compose BOM 2024.12.01 · play-services-nearby 19.3.0.
Bump `versionCode`/`versionName` in `app/build.gradle.kts` for each release.

Debug builds use application ID `com.intercom.ride.debug`, so debug and release can be installed side by side. **Debug and release builds can talk to each other** — discovery uses the Nearby service ID, not the app ID.

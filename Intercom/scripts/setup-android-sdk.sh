#!/usr/bin/env bash
# Installs the Android SDK inside GitHub Codespaces (or any Linux box) so
# ./gradlew assembleDebug works without Android Studio.
set -euo pipefail
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  cd /tmp
  curl -fsSLo cmdline.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o cmdline.zip
  rm -rf "$SDK/cmdline-tools/latest"
  mv cmdline-tools "$SDK/cmdline-tools/latest"
fi
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
echo "sdk.dir=$SDK" > "$(dirname "$0")/../local.properties"
echo "Android SDK ready at $SDK"

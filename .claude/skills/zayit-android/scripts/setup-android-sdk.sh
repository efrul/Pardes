#!/usr/bin/env bash
# Installs the Android SDK pieces Pardes (the Android app based on Zayit) needs and creates the test AVD.
# Idempotent: re-running skips what is already there.
#
# Usage: setup-android-sdk.sh            (SDK + AVD "zayit33")
#        ANDROID_HOME=/opt/android-sdk AVD=zayit33 setup-android-sdk.sh
#
# Installs: cmdline-tools, platform-tools, platforms;android-36, build-tools;36.0.0, emulator,
# system-images;android-33;google_apis;x86_64 (google_apis, not google_apis_playstore: it allows `adb root`,
# which the test flow needs to fix file ownership and to tune dexopt). ~6 GB on disk.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
AVD="${AVD:-zayit33}"
IMAGE="system-images;android-33;google_apis;x86_64"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

if ! command -v java >/dev/null; then
  echo "Installing a JDK (Gradle 9 + AGP 8.12 need JDK 17+; lucene-android needs 21+)"
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-21-jdk-headless >/dev/null || \
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-25-jdk-headless >/dev/null
fi

SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKM" ]; then
  echo "Downloading Android cmdline-tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  zip=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
        | grep -oE 'commandlinetools-linux-[0-9]+_latest\.zip' | sort -t- -k3 -n | tail -1)
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/ct.zip" "https://dl.google.com/android/repository/$zip"
  unzip -q "$tmp/ct.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

yes | "$SDKM" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
echo "Installing SDK packages (this downloads a few GB)"
"$SDKM" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-36" "build-tools;36.0.0" "emulator" "$IMAGE" >/dev/null

if [ ! -d "$HOME/.android/avd/$AVD.avd" ]; then
  echo "Creating AVD $AVD (Pixel 6, 1080x2400 @ 420dpi)"
  echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMAGE" -d pixel_6 --force >/dev/null
fi

# The Android build reads sdk.dir from local.properties (not committed).
LP="$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null || echo .)/androidApp/local.properties"
[ -f "$LP" ] || echo "sdk.dir=$ANDROID_HOME" > "$LP"

echo "Done. SDK: $ANDROID_HOME  AVD: $AVD"
echo "KVM: $([ -e /dev/kvm ] && echo available || echo 'not available -> the emulator will run with -accel off (slow)')"

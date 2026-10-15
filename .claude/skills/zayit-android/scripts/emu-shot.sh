#!/usr/bin/env bash
# Saves an emulator screenshot: emu-shot.sh <name> [dir]  -> <dir>/<name>.png (default dir: $SHOTS or /tmp/zayit-shots)
# View it with the Read tool. Read shows a downscaled image and states the factor to map coordinates back
# ("Multiply coordinates by 1.20") — use the device coordinates for `adb shell input tap`.
set -euo pipefail
ADB="${ADB:-${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb}"
dir="${2:-${SHOTS:-/tmp/zayit-shots}}"
mkdir -p "$dir"
$ADB exec-out screencap -p > "$dir/$1.png"
echo "$dir/$1.png"

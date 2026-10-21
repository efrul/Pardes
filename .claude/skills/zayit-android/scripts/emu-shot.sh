#!/usr/bin/env bash
# Saves an emulator screenshot: emu-shot.sh <name> [dir]  -> <dir>/<name>.png (default dir: $SHOTS or /tmp/zayit-shots)
# View it with the Read tool. Read shows a downscaled image and states the factor to map coordinates back
# ("Multiply coordinates by 1.20") — use the device coordinates for `adb shell input tap`.
set -euo pipefail
ADB="${ADB:-${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb}"
dir="${2:-${SHOTS:-/tmp/zayit-shots}}"
mkdir -p "$dir"
# Android 4.4 (the pardes19 AVD) has no `adb exec-out` ("error: closed"): there the shot goes through a file.
if ! $ADB exec-out screencap -p > "$dir/$1.png" 2>/dev/null || [ ! -s "$dir/$1.png" ]; then
  $ADB shell screencap -p /data/local/tmp/emu-shot.png >/dev/null
  $ADB pull /data/local/tmp/emu-shot.png "$dir/$1.png" >/dev/null 2>&1
fi
echo "$dir/$1.png"

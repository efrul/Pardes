#!/usr/bin/env bash
# Starts the test emulator headless. Run it with the Bash tool's run_in_background (it blocks while the
# emulator runs) and wait for boot with:  emu-wait.sh boot
#
# Without /dev/kvm (cloud containers) the emulator runs in software (TCG): a cold boot takes 5-15 min and
# everything is 10-50x slower than a phone. Background commands are killed after 2 hours; the user has
# approved restarting the emulator whenever that happens. `wm size` survives a restart: reset it after boot.
# GPU picks the renderer (default swiftshader_indirect). That host SwiftShader segfaults (exit 139, after "Failed to find
# ColorBuffer" errors) at 480x854 and 2560x1600, natively or through `wm size`; stay at the AVD's 1080x2400 when you can.
# GPU=guest does not help: this system image has no guest rendering, falls back to lavapipe, and system_server then
# restarted over and over.
set -euo pipefail
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
AVD="${AVD:-zayit33}"
LOG="${1:-/tmp/zayit-emulator.log}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

accel="-accel off"
[ -e /dev/kvm ] && accel="-accel on"
cpus=$(nproc)
cores=$(( cpus > 4 ? 4 : (cpus > 1 ? cpus - 1 : 1) ))
# CORES overrides it. Under TCG every vCPU runs on one host thread (`emulator -help-cores`: only useful with
# acceleration), and a 4-CPU container's 3 vCPUs once left system_server killed by its watchdog every 4-6 min:
# CORES=1 then booted in 450 s and stayed up (references/pitfalls.md, TCG instability).
cores="${CORES:-$cores}"

exec "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim $accel \
  -gpu "${GPU:-swiftshader_indirect}" -no-snapshot-save -memory 3072 -cores "$cores" > "$LOG" 2>&1

#!/usr/bin/env bash
# Waits for the emulator without foreground `sleep` (the harness blocks long sleeps: run this through the
# Monitor tool, which streams its output and ends when the script exits).
#
#   emu-wait.sh boot              until sys.boot_completed=1 and the package/window services answer
#   emu-wait.sh stable [maxsec]   until those services answer 4 checks in a row, 10 s apart, then wakes and unlocks the
#                                 user (apps cannot be started while it is RUNNING_LOCKED) and mounts shared storage
#                                 if it stayed unmounted. Under TCG, system_server
#                                 often crashes once right after boot ("Lost network stack") and restarts; installing
#                                 during that window fails with "Can't find service: package"
#   emu-wait.sh settle [maxsec]   until two screenshots 6 s apart are identical (UI finished drawing)
#   emu-wait.sh focus             until the app's activity has window focus (or an ANR dialog does)
#   emu-wait.sh file PATH         until a file exists on the device (e.g. a finished search index)
set -uo pipefail
ADB="${ADB:-${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb}"
mode="${1:-settle}"
case "$mode" in
  boot)
    start=$(date +%s)
    # boot_completed alone can read 1 while system services are still coming up (seen right after a restart),
    # so also wait for the package and window services that install/wm/screencap need.
    until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] \
      && $ADB shell service check package 2>/dev/null | grep -q ": found" \
      && $ADB shell service check window 2>/dev/null | grep -q ": found"; do sleep 10; done
    echo "BOOTED after $(( $(date +%s) - start ))s" ;;
  stable)
    max="${2:-900}"; ok=0; t=0
    while [ "$ok" -lt 4 ] && [ "$t" -lt "$max" ]; do
      if $ADB shell service check package 2>/dev/null | grep -q ": found" \
        && $ADB shell service check window 2>/dev/null | grep -q ": found" \
        && $ADB shell pm path android >/dev/null 2>&1; then ok=$((ok + 1)); else ok=0; fi
      sleep 10; t=$((t + 10))
    done
    # Until the user is unlocked (RUNNING_LOCKED after a cold boot) the app is not resolvable: am start and
    # deep links fail with "unable to resolve Intent" / "Activity class does not exist".
    $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1; $ADB shell wm dismiss-keyguard >/dev/null 2>&1
    $ADB shell svc power stayon true >/dev/null 2>&1
    for _ in $(seq 30); do
      [ "$($ADB shell am get-started-user-state 0 2>/dev/null | tr -d '\r')" = RUNNING_UNLOCKED ] && break; sleep 5
    done
    # After system_server restarted during boot, shared storage once stayed unmounted ("emulated;0 unmounted"): apps
    # then cannot see Android/data and the app sent its intact library to the reinstall screen. Mount it if so.
    for _ in $(seq 12); do
      $ADB shell sm list-volumes 2>/dev/null | grep -q "^emulated;0 mounted" && break
      $ADB shell "sm mount 'emulated;0'" >/dev/null 2>&1; sleep 5
    done
    echo "stable=$ok after ${t}s, user $($ADB shell am get-started-user-state 0 2>/dev/null | tr -d '\r'), storage $($ADB shell sm list-volumes 2>/dev/null | grep '^emulated;0' | cut -d' ' -f2)" ;;
  settle)
    max="${2:-300}"; t=0
    a=$($ADB exec-out screencap | md5sum)
    while [ "$t" -lt "$max" ]; do
      sleep 6; t=$((t + 6))
      b=$($ADB exec-out screencap | md5sum)
      if [ "$a" = "$b" ]; then echo "settled after ${t}s"; exit 0; fi
      a=$b
    done
    echo "still changing after ${max}s" ;;
  focus)
    until $ADB shell dumpsys window | grep -qE "mCurrentFocus=Window.*(seforimapp|Not Responding)"; do sleep 4; done
    $ADB shell dumpsys window | grep mCurrentFocus | head -1 ;;
  file)
    until $ADB shell ls "$2" >/dev/null 2>&1; do sleep 10; done
    echo "exists: $2" ;;
  *) echo "unknown mode $mode"; exit 2 ;;
esac

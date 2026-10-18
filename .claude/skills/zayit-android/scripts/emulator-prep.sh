#!/usr/bin/env bash
# One-time tuning after the emulator has booted. Makes a software-emulated Android usable for UI tests:
# root shell, screen always on, no animations, no ANR/crash dialogs covering the app, and the heavy Google
# apps disabled (GMS, launcher, assistant... starve the CPU under TCG and caused system_server crashes and
# ANR loops). Disabling packages persists across reboots; the rest is re-applied each run.
set -uo pipefail
ADB="${ADB:-${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb}"

$ADB root >/dev/null 2>&1; $ADB wait-for-device
$ADB shell svc power stayon true
$ADB shell settings put system screen_off_timeout 2147483647
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  $ADB shell settings put global $k 0
done
$ADB shell settings put global hide_error_dialogs 1
$ADB shell settings put global anr_show_background 0
# Small R8 APK + baseline profiles: install-time compilation is fast enough and makes the app far quicker.
$ADB shell setprop pm.dexopt.install speed-profile

if [ "${KEEP_GOOGLE_APPS:-0}" != 1 ]; then
  for p in com.google.android.gms com.google.android.gsf com.google.android.apps.nexuslauncher \
    com.google.android.googlequicksearchbox com.google.android.as com.google.android.as.oss \
    com.google.android.apps.wellbeing com.google.android.apps.wallpaper com.google.android.apps.wallpaper.nexus \
    com.google.android.apps.customization.pixel com.google.android.calendar com.google.android.contacts \
    com.google.android.gm com.google.android.deskclock com.google.android.dialer com.google.android.apps.messaging \
    com.google.android.apps.photos com.google.android.youtube com.google.android.apps.youtube.music \
    com.google.android.apps.maps com.google.android.apps.docs com.android.chrome com.google.android.projection.gearhead \
    com.google.android.feedback com.google.android.apps.restore com.google.android.partnersetup \
    com.google.android.marvin.talkback com.google.android.gms.supervision com.google.android.odad \
    com.google.android.ondevicepersonalization.services com.google.android.settings.intelligence \
    com.google.android.cellbroadcastreceiver com.google.android.tag com.google.android.adservices.api \
    com.google.android.tts com.google.android.markup; do
    $ADB shell pm disable-user --user 0 "$p" >/dev/null 2>&1
  done
fi
$ADB shell input keyevent KEYCODE_WAKEUP
echo "screen: $($ADB shell wm size | tail -1)  density: $($ADB shell wm density | tail -1)"
echo "free on /data: $($ADB shell df -h /data | tail -1 | awk '{print $4}')"

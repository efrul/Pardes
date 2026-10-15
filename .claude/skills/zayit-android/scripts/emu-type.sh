#!/usr/bin/env bash
# Types text (Hebrew works) into the focused field through the ADBKeyBoard test IME (see emulator-testing.md).
#
#   emu-type.sh "בראשית א ב"      types the text
#   emu-type.sh --enter "..."      types it, then sends the IME action (Search/Enter)
#   emu-type.sh --clear "..."      deletes up to 40 characters first
#
# ADBKeyBoard writes through the InputConnection it got when the field gained focus. After the app restarts input
# (force-stop, a popup opening under the field, a new screen) that connection goes stale and broadcasts are silently
# dropped, while a real keyboard (Gboard) keeps working. Re-selecting the IME right before typing gives it a fresh
# connection, so this script always does that first; tap the field once more after it if typing still does not land.
set -uo pipefail
ADB="${ADB:-${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb}"
IME=com.android.adbkeyboard/.AdbIME
enter=0; clear=0
while [ $# -gt 1 ]; do case "$1" in --enter) enter=1; shift ;; --clear) clear=1; shift ;; *) break ;; esac; done
$ADB shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME >/dev/null 2>&1
$ADB shell ime set "$IME" >/dev/null
sleep 2
if [ $clear = 1 ]; then for _ in $(seq 40); do $ADB shell input keyevent KEYCODE_DEL; done; fi
# adb shell joins its arguments into one device command line: quote the text again for the device shell, or a
# text with spaces arrives as several arguments and the broadcast is dropped.
q=$(printf "%s" "$1" | sed "s/'/'\\\\''/g")
$ADB shell "am broadcast -a ADB_INPUT_TEXT --es msg '$q'" >/dev/null
if [ $enter = 1 ]; then sleep 2; $ADB shell am broadcast -a ADB_EDITOR_CODE --ei code 3 >/dev/null; fi
echo "typed: $1"

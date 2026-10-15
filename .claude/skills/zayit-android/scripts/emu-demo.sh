#!/usr/bin/env bash
# Clean status bar for release screenshots (SystemUI demo mode), and back.
#
#   emu-demo.sh on [HHMM]   fixed clock (default 1000), full battery, wifi, no notification icons,
#                           and the ADB keyboard swapped for Gboard so no "ADB Keyboard {ON}" chip shows
#   emu-demo.sh off         normal status bar
#
# Run it before the shots, after the app is in the state to capture; emu-shot.sh as usual.
set -euo pipefail
ADB=${ADB:-adb}
demo() { $ADB shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }

case "${1:-on}" in
  on)
    $ADB shell settings put global sysui_demo_allowed 1
    demo enter
    demo clock -e hhmm "${2:-1000}"
    demo battery -e level 100 -e plugged false
    demo network -e wifi show -e level 4 -e fully true
    demo network -e mobile hide
    demo notifications -e visible false
    $ADB shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME >/dev/null 2>&1 || true
    ;;
  off)
    demo exit
    ;;
  *) echo "usage: $0 on [HHMM] | off" >&2; exit 2 ;;
esac

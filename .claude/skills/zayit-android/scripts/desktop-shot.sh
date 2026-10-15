#!/usr/bin/env bash
# Screenshot of the headless desktop app (see desktop-start.sh), optionally after a click.
#
#   desktop-shot.sh <name> [x y] [wait-seconds]    -> $SHOTS/<name>.png (default dir /tmp/zayit-desktop-shots)
#
# Other input: DISPLAY=:99 xdotool type --delay 100 "בראשית"  (Hebrew works with LC_ALL=C.UTF-8),
# xdotool key ctrl+t / Escape / Return, xdotool click 4|5 (wheel up/down). Separate tool windows (settings,
# tools) are their own X windows: `xwininfo -root -tree | grep zayit` and `import -window <id>`.
set -uo pipefail
export DISPLAY=:99 LC_ALL=C.UTF-8
dir="${SHOTS:-/tmp/zayit-desktop-shots}"; mkdir -p "$dir"
if [ -n "${2:-}" ] && [ -n "${3:-}" ]; then xdotool mousemove "$2" "$3" click 1; fi
sleep "${4:-2}"
import -window root "$dir/$1.png" && echo "$dir/$1.png"

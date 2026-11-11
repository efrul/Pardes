#!/usr/bin/env bash
# Screenshot of the headless app (start.sh), optionally after a click, optionally cut to a region to read small text.
#
#   shot.sh <name> [x y] [wait-seconds]     -> $SHOTS/<name>.png (default /tmp/pardes-desktop-shots)
#   CROP=700x500+370+200 shot.sh <name>     -> also <name>c.png, that region alone (ImageMagick geometry)
#
# Give x and y only to click: `shot.sh name 0 0 5` clicks at the corner. To wait without a click, pass "" "" 5.
# Other input: xdotool type --delay 100 "ברכות" (Hebrew needs LC_ALL=C.UTF-8, set here), xdotool key ctrl+t /
# Escape / Return, xdotool click 4|5 (wheel up/down, the mouse over the list first).
set -uo pipefail
export DISPLAY=:99 LC_ALL=C.UTF-8
dir="${SHOTS:-/tmp/pardes-desktop-shots}"
mkdir -p "$dir"
if [ -n "${2:-}" ] && [ -n "${3:-}" ]; then xdotool mousemove "$2" "$3" click 1; fi
sleep "${4:-3}"
import -window root "$dir/$1.png" && echo "$dir/$1.png"
if [ -n "${CROP:-}" ]; then convert "$dir/$1.png" -crop "$CROP" +repage "$dir/$1c.png" && echo "$dir/$1c.png"; fi

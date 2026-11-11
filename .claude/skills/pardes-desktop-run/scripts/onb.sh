#!/usr/bin/env bash
# Drives the install wizard that onboarding.sh opens (720x460 at +360+220 of the display).
#
#   onb.sh to-disk             welcome, the licence (scrolled to its end, ticked), on to the disk-space step
#   onb.sh pick <path>         the folder chooser, once it is open: types the path and opens it
#   onb.sh click <x> <y> [s]   a click at the wizard's own coordinates, then s seconds (default 6)
#   onb.sh shot <name>         $SHOTS/<name>.png (the display) and <name>c.png (the wizard alone)
#
# The folder chooser is a Swing "Open" dialog here (no native one in the container): its path field is typed into.
# With no window manager it sometimes does not open a second time: start the wizard again.
set -uo pipefail
export DISPLAY=:99 LC_ALL=C.UTF-8
dir="${SHOTS:-/tmp/pardes-desktop-shots}"
mkdir -p "$dir"
w() { timeout $(( $1 + 5 )) bash -c "sleep $1"; }
case "${1:-}" in
  to-disk)
    xdotool mousemove 720 633 click 1; w 6
    xdotool mousemove 720 500; for _ in $(seq 1 60); do xdotool click 5; done; w 4
    xdotool mousemove 825 570 click 1; w 3
    xdotool mousemove 720 633 click 1; w 7 ;;
  pick)
    xdotool mousemove 772 521 click 1; w 1
    xdotool key ctrl+a; xdotool type --delay 40 "$2"; w 1
    xdotool mousemove 840 589 click 1; w 7 ;;
  click)
    xdotool mousemove $(( $2 + 360 )) $(( $3 + 220 )) click 1; w "${4:-6}" ;;
  shot)
    import -window root "$dir/$2.png" && convert "$dir/$2.png" -crop 720x460+360+220 +repage "$dir/$2c.png" && echo "$dir/$2c.png" ;;
  *) sed -n '2,9p' "$0" ;;
esac

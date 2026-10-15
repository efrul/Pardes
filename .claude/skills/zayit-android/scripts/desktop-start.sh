#!/usr/bin/env bash
# Installs the released desktop Zayit (.deb) if missing and runs it headless on Xvfb :99 (1440x900), so its screens
# can be screenshotted as the reference for the Android UI. Run with run_in_background (it keeps Xvfb in the
# foreground); then use desktop-shot.sh. The desktop app needs its library (fetch-desktop-library.sh puts it in the
# folder desktop reads) or it starts on its own first-run download flow.
#
#   desktop-start.sh [version]     default 0.6.1 (the version the Android port was matched against)
set -uo pipefail
V="${1:-0.6.1}"
if ! command -v zayit >/dev/null; then
  d=$(mktemp -d); curl -fsSL -o "$d/zayit_${V}_amd64.deb" "https://github.com/kdroidFilter/Zayit/releases/download/v$V/zayit_${V}_amd64.deb"
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$d/zayit_${V}_amd64.deb" xvfb xdotool x11-utils imagemagick fonts-noto-core >/dev/null
  rm -rf "$d"
fi
export DISPLAY=:99 LC_ALL=C.UTF-8 LANG=C.UTF-8
pgrep -f "Xvfb :99" >/dev/null || { Xvfb :99 -screen 0 1440x900x24 -nolisten tcp & }
sleep 2
zayit > "${TMPDIR:-/tmp}/zayit-desktop.log" 2>&1 &
# Maximise the main window once it appears (Xvfb has no window manager).
for _ in $(seq 1 60); do w=$(xdotool search --name "זית" 2>/dev/null | tail -1); [ -n "$w" ] && break; sleep 2; done
[ -n "${w:-}" ] && { xdotool windowmove "$w" 0 0; xdotool windowsize "$w" 1440 900; echo "desktop window $w"; }
wait

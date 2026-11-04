#!/usr/bin/env bash
# Runs Pardes desktop built from this checkout (./gradlew :SeforimApp:run) on Xvfb :99 at 1440x900, on the library in
# $LIB (make-library.sh), and maximises its window. Run it with run_in_background: it waits on the app. Then use
# shot.sh. The first run compiles the app (about 8 minutes); later ones start in about a minute.
#
# The app's preferences (java.util.prefs, Brand.settings()'s node io/github/efrul/pardes) are written first: the
# onboarding done, the library's seforim.db, and no start-up prompts (the semantic pack's, the update check), or the
# app opens on its first-run flow. This replaces whatever preferences an earlier run saved.
set -uo pipefail
ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
LIB="${LIB:-/home/user/pardes-lib}"
LOG="${LOG:-${TMPDIR:-/tmp}/pardes-desktop.log}"
export DISPLAY=:99 LC_ALL=C.UTF-8 LANG=C.UTF-8 LIBGL_ALWAYS_SOFTWARE=1
[ -f "$LIB/seforim.db" ] || { echo "no library in $LIB: run make-library.sh first"; exit 1; }

P="$HOME/.java/.userPrefs/io/github/efrul/pardes"
mkdir -p "$P"
cat > "$P/prefs.xml" <<EOF
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE map SYSTEM "http://java.sun.com/dtd/preferences.dtd">
<map MAP_XML_VERSION="1.0">
  <entry key="onboarding_finished" value="true"/>
  <entry key="database_path" value="$LIB/seforim.db"/>
  <entry key="semantic_pack_prompt" value="false"/>
  <entry key="auto_update_check" value="false"/>
</map>
EOF

# Not `pgrep -f "Xvfb :99"`: it matches this script's own command line. Ask the display itself.
if ! xwininfo -root >/dev/null 2>&1; then
  Xvfb :99 -screen 0 1440x900x24 -nolisten tcp >/dev/null 2>&1 &
  sleep 2
fi

cd "$ROOT" && ./gradlew :SeforimApp:run --console=plain >"$LOG" 2>&1 &
app=$!
w=""
for _ in $(seq 1 240); do
  w=$(xdotool search --name "פרדס" 2>/dev/null | head -1)
  [ -n "$w" ] && break
  grep -qE "BUILD FAILED|Failed to create EGL" "$LOG" && { echo "the app did not start, see $LOG"; tail -20 "$LOG"; exit 1; }
  sleep 5
done
if [ -n "$w" ]; then
  sleep 5
  xdotool windowmove "$w" 0 0
  xdotool windowsize "$w" 1440 900
  echo "pardes window $w (log $LOG)"
else
  echo "no window after 20 minutes, see $LOG"
fi
wait "$app"

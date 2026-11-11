#!/usr/bin/env bash
# As start.sh, but on the first-run flow: no library chosen and the onboarding not finished, so the app opens its
# install wizard (a 720x460 window at +360+220 of the 1440x900 display). Run it with run_in_background, drive the
# wizard with onb.sh, and close it with `stop.sh wizard`.
#
#   INSTALL_DIR=/dev/shm/pardes-install onboarding.sh     the folder the wizard offers for the library
#
# PARDES_FEEDBACK_URL is set to a test address, so the build has a feedback service to name and the wizard shows its
# feedback step; nothing is sent while no one presses on.
set -uo pipefail
LOG="${LOG:-${TMPDIR:-/tmp}/pardes-onboarding.log}"
export DISPLAY=:99 LC_ALL=C.UTF-8 LANG=C.UTF-8 LIBGL_ALWAYS_SOFTWARE=1
P="$HOME/.java/.userPrefs/io/github/efrul/pardes"
mkdir -p "$P"
{
  echo '<?xml version="1.0" encoding="UTF-8" standalone="no"?>'
  echo '<!DOCTYPE map SYSTEM "http://java.sun.com/dtd/preferences.dtd">'
  echo '<map MAP_XML_VERSION="1.0">'
  echo '  <entry key="semantic_pack_prompt" value="false"/>'
  echo '  <entry key="auto_update_check" value="false"/>'
  [ -n "${INSTALL_DIR:-}" ] && echo "  <entry key=\"library_install_dir\" value=\"$INSTALL_DIR\"/>"
  echo '</map>'
} > "$P/prefs.xml"
if ! xwininfo -root >/dev/null 2>&1; then
  Xvfb :99 -screen 0 1440x900x24 -nolisten tcp >/dev/null 2>&1 &
  sleep 2
fi
PARDES_FEEDBACK_URL=https://feedback.test "$(dirname "$0")/gradle.sh" :SeforimApp:run >"$LOG" 2>&1

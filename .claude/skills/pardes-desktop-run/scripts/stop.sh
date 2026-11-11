#!/usr/bin/env bash
# Closes the app started by start.sh or onboarding.sh (the main window's close button; the wizard's with
# `stop.sh wizard`) and waits until its process is gone: only then may Gradle run again (gradle.sh).
set -uo pipefail
export DISPLAY=:99 LC_ALL=C.UTF-8
if [ "${1:-}" = "wizard" ]; then xdotool mousemove 1059 239 click 1; else xdotool mousemove 1420 20 click 1; fi
timeout 90 bash -c 'while pgrep -f "[n]ucleus/jdk/" >/dev/null; do sleep 3; done'
if pgrep -f "[n]ucleus/jdk/" >/dev/null; then echo "still running: look at the screen (a dialog may be open)"; exit 1; fi
echo "closed"

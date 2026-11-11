#!/usr/bin/env bash
# ./gradlew as a Pardes build has to run in the cloud container:
#   gradle.sh :SeforimApp:jvmTest ktlintCheck detekt
# - a UTF-8 locale (test classes with Hebrew names, pardes-desktop-run's SKILL.md › The locale);
# - a gh that fails first on PATH: settings.gradle.kts asks gh for a token to Zayit's private packages, which a Pardes
#   build must never read;
# - not while the app runs: a build rewrites the jars the running app loads its classes from, and the app then
#   crashes with NoClassDefFoundError at the next screen it opens. Close it first (stop.sh).
set -euo pipefail
ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
if pgrep -f "[n]ucleus/jdk/" >/dev/null; then echo "Pardes is running: close it first (stop.sh)"; exit 1; fi
stub=$(mktemp -d)
printf '#!/bin/sh\nexit 1\n' > "$stub/gh"
chmod +x "$stub/gh"
cd "$ROOT" && PATH="$stub:$PATH" LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew --console=plain "$@"

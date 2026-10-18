#!/usr/bin/env bash
# Second gate for lucene-android, on the R8-built APK: check-min-api.sh proves the jar dexed by D8, but R8 may move
# code between classes. It once moved AndroidCompatApi33's VarHandle methods into GroupVIntUtil, which every search
# loads, and Android 11's verifier rejected that class (VerifyError: no search). Fails unless every
# signature-polymorphic VarHandle call of the APK is in AndroidCompatApi33, the one class only Android 13+ loads.
#
# Usage: ./check-release-dex.sh <app-release.apk> <mapping.txt>
# Env: ANDROID_HOME (default: sdk.dir of ../local.properties) for build-tools/*/dexdump.
set -euo pipefail

apk="$1"
mapping="$2"
here="$(cd "$(dirname "$0")" && pwd)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f "$here/../local.properties" ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' "$here/../local.properties")"
fi
dexdump="$(ls -d "$sdk"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1 || true)"
[ -x "$dexdump" ] || { echo "dexdump not found under $sdk/build-tools" >&2; exit 2; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$work"

# Obfuscated class names (as dexdump prints them) of the classes holding a VarHandle invoke-polymorphic
holders="$("$dexdump" -d "$work"/classes*.dex 2>/dev/null \
  | awk '/Class descriptor/ { c = $4 } /invoke-polymorphic/ && /Ljava\/lang\/invoke\/VarHandle;/ { print c }' \
  | sed "s/^'L//; s/;'$//; s#/#.#g" | sort -u)"
[ -n "$holders" ] || { echo "no VarHandle call in $apk: is lucene-android in it?" >&2; exit 1; }

failed=false
for obfuscated in $holders; do
  original="$(awk -v o="$obfuscated" '!/^ / && !/^#/ && $3 == o":" { print $1; exit }' "$mapping")"
  original="${original:-$obfuscated}"
  echo "VarHandle calls in $original ($obfuscated)"
  [ "$original" = org.apache.lucene.util.AndroidCompatApi33 ] || failed=true
done
if $failed; then
  echo "FAIL: VarHandle calls outside AndroidCompatApi33; check the -keep rule in app/proguard-rules.pro" >&2
  exit 1
fi
echo "OK: VarHandle calls only in AndroidCompatApi33"

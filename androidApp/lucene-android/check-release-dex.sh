#!/usr/bin/env bash
# Second gate for lucene-android, on the R8-built APK: check-min-api.sh proves the jar dexed by D8, but R8 may move
# code between classes. It once moved AndroidCompatApi33's VarHandle methods into GroupVIntUtil, which every search
# loads, and Android 11's verifier rejected that class (VerifyError: no search). Fails unless every
# signature-polymorphic VarHandle call of the APK is in AndroidCompatApi33, the one class only Android 13+ loads.
#
# --legacy, for the APK of Pardes for Android 4.4 (minSdk of ../legacy/build.gradle.kts, 19), shrunk by R8 with
# the Lucene jar of jarApi19 and core library desugaring. Fails unless:
#  - every dex is version 035, the one Dalvik reads;
#  - the app's dex files (all but the desugared library's, which only define the j$ classes they use) take
#    nothing from the platform above the min API (gate/DexApiLevels.java), apart from LEGACY_ALLOWED below.
#    Lint's NewApi does not see the shared desktop sources (legacy/lint.xml) nor the libraries' bytecode;
#  - every class named by Lucene's META-INF/services files is in the dex: R8 must keep the codecs and formats
#    the index is read with (lucene-android/proguard-lucene.pro).
#
# Usage: ./check-release-dex.sh <app-release.apk> <mapping.txt>
#        ./check-release-dex.sh --legacy <legacy-release.apk>
# Env: ANDROID_HOME (default: sdk.dir of ../local.properties) for build-tools/*/dexdump and the compile SDK's
#      data/api-versions.xml.
set -euo pipefail

# --legacy: references above the min API that the APK may have, each behind an SDK_INT check in legacy's own
# sources or in the main app's files it compiles in (sharedAppSources in ../legacy/build.gradle.kts), which lint's
# NewApi checks in both ("<level> <kind> <owner> <member>" as DexApiLevels prints them).
LEGACY_ALLOWED=(
  # SearchService.libraryKey (shared): a file's inode by Os.stat from API 21, its time and size below
  "21	field	android/system/StructStat	st_ino:J"
  "21	method	android/system/Os	stat(Ljava/lang/String;)Landroid/system/StructStat;"
  "21	type	android/system/Os	"
  "21	type	android/system/StructStat	"
  "21	method	android/view/Window	setStatusBarColor(I)V"
  "24	method	android/text/Html	fromHtml(Ljava/lang/String;I)Landroid/text/Spanned;"
  "26	method	android/widget/TextView	setJustificationMode(I)V"
  # The install from files (legacy ui/InstallActivity.kt, InstallService.kt): storage access asked for from API 23, a
  # notification channel from API 26
  "23	method	android/app/Activity	checkSelfPermission(Ljava/lang/String;)I"
  "23	method	android/app/Activity	onRequestPermissionsResult(I[Ljava/lang/String;[I)V"
  "23	method	android/app/Activity	requestPermissions([Ljava/lang/String;I)V"
  "26	method	android/app/Notification\$Builder	<init>(Landroid/content/Context;Ljava/lang/String;)V"
  "26	method	android/app/NotificationChannel	<init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V"
  "26	method	android/app/NotificationManager	createNotificationChannel(Landroid/app/NotificationChannel;)V"
  "26	type	android/app/NotificationChannel	"
  # Core library desugaring's bridges on a List the app implements (kotlinx.serialization's JsonArray, the bundle
  # manifest's): stream() and spliterator() convert to the platform's types, and only Android 7.0+ calls them
  "24	type	java/util/Spliterator	"
  "24	type	java/util/stream/Stream	"
)

usage() {
  echo "usage: $0 <app-release.apk> <mapping.txt>" >&2
  echo "       $0 --legacy <legacy-release.apk>" >&2
  exit 2
}

legacy=false
if [ "${1:-}" = --legacy ]; then
  legacy=true
  shift
fi
apk="${1:-}"
mapping="${2:-}"
[ -f "$apk" ] || usage
# Without mapping.txt the holders below would keep their obfuscated names (and awk would read stdin): the main
# app's check needs it.
$legacy || [ -f "$mapping" ] || usage
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

if $legacy; then
  failed=false
  min_api="$(sed -n 's/^ *minSdk = \([0-9][0-9]*\).*/\1/p' "$here/../legacy/build.gradle.kts" | head -1)"
  compile_sdk="$(sed -n 's/^ *compileSdk = \([0-9][0-9.]*\).*/\1/p' "$here/../legacy/build.gradle.kts" | head -1)"
  api_versions="$sdk/platforms/android-$compile_sdk/data/api-versions.xml"
  [ -f "$api_versions" ] || { echo "no $api_versions (set ANDROID_HOME)" >&2; exit 2; }
  mkdir -p "$work/app" "$work/library"
  : > "$work/classes.txt"
  for dex in "$work"/classes*.dex; do
    version="$(head -c 8 "$dex" | tail -c 4 | tr -d '\0')"
    if [ "$version" != 035 ]; then
      echo "$(basename "$dex"): dex version $version, Dalvik reads 035 only"
      failed=true
    fi
    "$dexdump" "$dex" 2>/dev/null | sed -n "s/^ *Class descriptor *: 'L\(.*\);'$/\1/p" > "$dex.classes"
    cat "$dex.classes" >> "$work/classes.txt"
    # The desugared library's dex (L8): only j$ classes and the java.* ones it adds.
    if grep -q '^j\$/' "$dex.classes" && ! grep -qv -e '^j\$/' -e '^java/' "$dex.classes"; then
      mv "$dex" "$work/library/"
    else
      mv "$dex" "$work/app/"
    fi
  done
  java "$here/gate/DexApiLevels.java" levels "$api_versions" "$min_api" - "$work/app" --defined "$work/library" \
    > "$work/levels.txt"
  sed -n '/^above API/,/^not in api-versions/p' "$work/levels.txt" | sed '1d;$d' | sed 's/^  //' > "$work/above.txt"
  allowed=0
  unexpected=()
  while IFS= read -r line; do
    if printf '%s\n' "${LEGACY_ALLOWED[@]}" | grep -qxF "$line"; then
      allowed=$((allowed + 1))
    else
      unexpected+=("$line")
    fi
  done < "$work/above.txt"
  echo "$(ls "$work/app" | wc -l) app dex, $(ls "$work/library" | wc -l) desugared library dex:" \
    "${#unexpected[@]} references above API $min_api, $allowed allowed (guarded, linted)"
  if [ ${#unexpected[@]} -gt 0 ]; then
    printf '  %s\n' "${unexpected[@]}" >&2
    failed=true
  fi
  services=0
  for service in $(unzip -Z1 "$apk" 'META-INF/services/org.apache.lucene.*' 2>/dev/null); do
    for provider in $(unzip -p "$apk" "$service" | sed 's/#.*//' | tr -d ' \r'); do
      services=$((services + 1))
      if ! grep -qxF "$(echo "$provider" | tr . /)" "$work/classes.txt"; then
        echo "  ${service#META-INF/services/}: $provider is not in the dex" >&2
        failed=true
      fi
    done
  done
  echo "Lucene's META-INF/services: $services providers"
  [ "$services" -gt 0 ] || { echo "no Lucene META-INF/services in $apk: is lucene-android in it?" >&2; failed=true; }
  if $failed; then
    echo "FAIL" >&2
    exit 1
  fi
  echo "OK"
  exit 0
fi

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

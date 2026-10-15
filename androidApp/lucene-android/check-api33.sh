#!/usr/bin/env bash
# Gate for the Android build of lucene-core: dexes the jar the way AGP does for minSdk 33 (D8 with
# desugaring, no core library desugaring) and runs R8's TraceReferences against the API 33 android.jar.
# Every reported class/method/field would be a NoClassDefFoundError/NoSuchMethodError on Android 13,
# so the check fails unless nothing is missing.
#
# Usage: ./check-api33.sh [--baseline] [jar...]
#   Without jars it builds :lucene-android:jar and checks that. --baseline also reports (without
#   failing) the unpatched org.apache.lucene:lucene-core jar of the same version from the Gradle cache.
# Env: ANDROID_HOME (default: sdk.dir of ../local.properties) for build-tools/*/lib/d8.jar;
#      API33_JAR to use a specific API 33 android.jar (default: SDK platforms/android-33*, else the
#      platform-33-ext5_r01 zip from dl.google.com, cached in build/check-api33).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
work="$here/build/check-api33"
mkdir -p "$work"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f "$here/../local.properties" ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' "$here/../local.properties")"
fi
d8="$(ls "$sdk"/build-tools/*/lib/d8.jar 2>/dev/null | sort -V | tail -1 || true)"
if [ ! -f "$d8" ]; then
  echo "d8.jar not found under '$sdk/build-tools' (set ANDROID_HOME)" >&2
  exit 2
fi

api33="${API33_JAR:-}"
if [ -z "$api33" ]; then
  api33="$(ls "$sdk"/platforms/android-33*/android.jar 2>/dev/null | head -1 || true)"
fi
if [ -z "$api33" ]; then
  api33="$work/android-33.jar"
  if [ ! -f "$api33" ]; then
    zip="$work/platform-33-ext5_r01.zip"
    echo "Downloading the API 33 platform..." >&2
    curl -fsSL -o "$zip" https://dl.google.com/android/repository/platform-33-ext5_r01.zip
    unzip -p "$zip" "$(unzip -Z1 "$zip" | grep -m1 '/android\.jar$')" > "$api33.tmp"
    mv "$api33.tmp" "$api33"
    rm -f "$zip"
  fi
fi

baseline=false
jars=()
for arg in "$@"; do
  case "$arg" in
    --baseline) baseline=true ;;
    *) jars+=("$arg") ;;
  esac
done
if [ ${#jars[@]} -eq 0 ]; then
  (cd "$here/.." && ./gradlew -q :lucene-android:jar)
  jars=("$(ls -t "$here"/build/libs/lucene-android-*.jar | head -1)")
fi

# Lines of a trace that name something Android 13 lacks. com.android.tools.r8.* is left out: those are
# classes D8/R8 generate themselves while dexing (RecordTag, the base class of desugared records), not
# platform classes, and TraceReferences of build-tools 37 lists RecordTag although D8 put it in the dex.
missing_lines() {
  grep 'Missing \(class\|method\|field\) ' "$1" | grep -v 'Missing class com\.android\.tools\.r8\.' || true
}

# Prints "<missing count>" after dexing and tracing $1; details land in $work/<jar name>/.
trace() {
  local jar="$1" out
  out="$work/$(basename "$jar" .jar)"
  rm -rf "$out"
  mkdir -p "$out/dex"
  if ! java -cp "$d8" com.android.tools.r8.D8 --release --min-api 33 --lib "$api33" \
      --output "$out/dex" "$jar" > "$out/d8.log" 2>&1; then
    cat "$out/d8.log" >&2
    return 2
  fi
  local sources=()
  for dex in "$out"/dex/*.dex; do sources+=(--source "$dex"); done
  # Missing definitions are mapped to info, so a non-zero exit is a failed run (bad jar, unknown flag in a newer
  # build-tools...), which must not read as "0 missing".
  if ! java -cp "$d8" com.android.tools.r8.tracereferences.TraceReferences --check --lib "$api33" \
      "${sources[@]}" --map-diagnostics:MissingDefinitionsDiagnostic error info > "$out/trace.txt" 2>&1; then
    cat "$out/trace.txt" >&2
    return 2
  fi
  missing_lines "$out/trace.txt" | grep -c . || true
}

echo "d8/TraceReferences: $d8"
echo "API 33 library:     $api33"
if $baseline; then
  version="$(basename "${jars[0]}" | sed -n 's/^lucene-android-\(.*\)-android\.jar$/\1/p')"
  original="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" -name "lucene-core-$version.jar" | head -1)"
  if [ -n "$version" ] && [ -n "$original" ]; then
    echo "baseline $(basename "$original"): $(trace "$original") missing references on API 33"
  else
    echo "baseline lucene-core-$version.jar not in the Gradle cache, skipped"
  fi
fi

status=0
for jar in "${jars[@]}"; do
  missing="$(trace "$jar")"
  name="$(basename "$jar" .jar)"
  echo "$name: $missing missing references on API 33"
  if [ "$missing" != "0" ]; then
    missing_lines "$work/$name/trace.txt" | sed 's/^Info: //' | cut -c1-240 >&2
    status=1
  fi
done
exit $status

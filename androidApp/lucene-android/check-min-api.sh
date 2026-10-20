#!/usr/bin/env bash
# Gate for the Android build of lucene-core: dexes the jar the way AGP does for the app's minSdk 26 and
# fails on any reference to the platform that Android 8.0 (API 26) lacks, each of which would be a
# NoClassDefFoundError/NoSuchMethodError there:
#  - the dexer is AGP's own D8 (com.android.tools.build:builder-<agp>.jar in the Gradle cache, agp from
#    ../gradle/libs.versions.toml). The D8 of a newer build-tools backports more JDK methods (the
#    Arrays.equals ranges, for one), so it would pass call sites the APK still has;
#  - levels (gate/DexApiLevels.java): every class, field and method the dex takes from the platform must
#    have an API level of at most the min API in data/api-versions.xml of the compile SDK platform. What
#    that file does not list (covariant-return overloads such as ByteBuffer.flip()ByteBuffer, which ART
#    has from API 29; members of hidden superclasses such as StringBuilder.getChars) is info, left to:
#  - TraceReferences --check against the API 33 android.jar, and against the min API's android.jar when
#    one is installed (CI installs platforms;android-26): nothing missing.
# AndroidCompatApi33 is the one class meant to call API 33 methods (it only runs on API 33+): it is dexed
# and checked on its own, against API 33.
#
# The jar of Pardes for Android 4.4 (jarApi19, see build.gradle.kts), --legacy: the same for minSdk 19 with
# core library desugaring (D8 --desugared-lib, with the L8 output of desugar_jdk_libs counted as defined
# and given to TraceReferences as class files), all classes at the min API (its AndroidCompatApi33 is a
# stub), and below API 26 and 21 what a missing-reference list does not show:
#  - below API 26: no signature-polymorphic call and no method handle constant (gate/PolyScan.java). D8
#    turns each into a throw below API 26, and AGP stops the build on them;
#  - below API 21 (Dalvik, Android 4.4): no class the KitKat verifier rejects (gate/DalvikVerify44.java:
#    a throw of a value typed with a class it cannot resolve, ...) and no catch handler whose catch types
#    all fail to resolve (gate/dexcatch.py on dexdump's output). Either is a VerifyError for the whole class.
#    A method DalvikVerify44 could not analyse, or a dex file dexcatch.py gave no result for, fails too.
#
# Usage: ./check-min-api.sh [--baseline] [--legacy] [--min-api N] [--desugared-lib default|nio] [jar...]
#   Without jars it builds :lucene-android:jar and checks that. --baseline also reports (without
#   failing) the unpatched org.apache.lucene:lucene-core jar of the same version from the Gradle cache.
#   --min-api N dexes and checks for API N (default: minSdk of ../app/build.gradle.kts, else 26).
#   --desugared-lib dexes with that flavour of desugar_jdk_libs (default or nio), at the version of
#   ../gradle/libs.versions.toml. --legacy: --min-api from ../legacy/build.gradle.kts (19),
#   --desugared-lib nio, and without jars it builds and checks :lucene-android:jarApi19.
# Env: ANDROID_HOME (default: sdk.dir of ../local.properties) for platforms/;
#      COMPILE_SDK_PLATFORM: the platform dir whose android.jar D8 gets as --lib and whose api-versions.xml
#        gives the levels (default: platforms/android-<compileSdk of ../app/build.gradle.kts>);
#      API33_JAR to use a specific API 33 android.jar (default: SDK platforms/android-33*, else the
#        platform-33-ext5_r01 zip from dl.google.com, cached in build/check-min-api);
#      MIN_API_JAR: the android.jar of the min API level (default: SDK platforms/android-<min api>, if there);
#      R8_JAR: a D8/R8 jar to use instead of AGP's.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
work="$here/build/check-min-api"
mkdir -p "$work"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
levels_tool="$here/gate/DexApiLevels.java"
api33_classes="org/apache/lucene/util/AndroidCompatApi33"

baseline=false
legacy=false
desugared_lib=""
min_api=""
jars=()
while [ $# -gt 0 ]; do
  case "$1" in
    --baseline) baseline=true ;;
    --legacy) legacy=true ;;
    --min-api) min_api="$2"; shift ;;
    --desugared-lib) desugared_lib="$2"; shift ;;
    *) jars+=("$1") ;;
  esac
  shift
done
# The app's minSdk (the Android 4.4 edition's with --legacy), read as compileSdk is below
app="app"
$legacy && app="legacy"
if [ -z "$min_api" ]; then
  min_api="$(sed -n 's/^ *minSdk = \([0-9][0-9]*\).*/\1/p' "$here/../$app/build.gradle.kts" 2>/dev/null | head -1 || true)"
  min_api="${min_api:-26}"
fi
if $legacy && [ -z "$desugared_lib" ]; then
  desugared_lib=nio
fi
case "$desugared_lib" in
  ""|default|nio) ;;
  *) echo "--desugared-lib: default or nio, not '$desugared_lib'" >&2; exit 2 ;;
esac

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f "$here/../local.properties" ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' "$here/../local.properties")"
fi

platform="${COMPILE_SDK_PLATFORM:-}"
if [ -z "$platform" ]; then
  compile_sdk="$(sed -n 's/^ *compileSdk = \([0-9][0-9.]*\).*/\1/p' "$here/../app/build.gradle.kts" | head -1)"
  platform="$sdk/platforms/android-$compile_sdk"
fi
if [ ! -f "$platform/android.jar" ] || [ ! -f "$platform/data/api-versions.xml" ]; then
  echo "no android.jar and data/api-versions.xml in '$platform' (set ANDROID_HOME or COMPILE_SDK_PLATFORM)" >&2
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
min_jar="${MIN_API_JAR:-}"
if [ -z "$min_jar" ] && [ -f "$sdk/platforms/android-$min_api/android.jar" ]; then
  min_jar="$sdk/platforms/android-$min_api/android.jar"
fi

if [ ${#jars[@]} -eq 0 ]; then
  if $legacy; then
    (cd "$here/.." && ./gradlew -q :lucene-android:jarApi19)
    jars=("$(ls -t "$here"/build/libs/lucene-android-*-android-api19.jar | head -1)")
  else
    (cd "$here/.." && ./gradlew -q :lucene-android:jar)
    jars=("$(ls -t "$here"/build/libs/lucene-android-*-android.jar | head -1)")
  fi
fi

# ASM for the Dalvik checks and desugar_jdk_libs, from the Gradle cache (build.gradle.kts, gateTools).
tools="$work/tools.txt"
if [ -n "$desugared_lib" ] || [ "$min_api" -lt 26 ]; then
  (cd "$here/.." && ./gradlew -q :lucene-android:gateTools)
fi
tool() {
  sed -n "s/^$1=//p" "$tools"
}

# Any Gradle run of ../ has resolved AGP (the root project applies its plugin), so its jar is cached.
r8="${R8_JAR:-}"
if [ -z "$r8" ]; then
  agp="$(sed -n 's/^agp *= *"\([^"]*\)".*/\1/p' "$here/../gradle/libs.versions.toml" | head -1)"
  r8="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.android.tools.build/builder/$agp" \
    -name "builder-$agp.jar" 2>/dev/null | head -1 || true)"
  if [ -z "$r8" ]; then
    echo "builder-$agp.jar (AGP's D8) is not in the Gradle cache: run any build of androidApp first" >&2
    exit 2
  fi
fi

# Lines of a trace that name something the platform lacks. com.android.tools.r8.* is left out: those are
# classes D8/R8 generate themselves while dexing (RecordTag, the base class of desugared records), not
# platform classes, and TraceReferences lists RecordTag although D8 put it in the dex.
missing_lines() {
  grep 'Missing \(class\|method\|field\) ' "$1" | grep -v 'Missing class com\.android\.tools\.r8\.' || true
}

# With --desugared-lib: the flavour's desugar.json for D8, and L8's output of the library as dex (counted as
# defined by levels) and as class files (a library for TraceReferences), made once per run.
desugar_json=""
l8_dex=""
l8_classes=""
if [ -n "$desugared_lib" ]; then
  IFS=: read -r -a desugar_jars <<< "$(tool "$desugared_lib")"
  l8="$work/l8-$desugared_lib-$min_api"
  rm -rf "$l8"
  mkdir -p "$l8/dex"
  desugar_json="$l8/desugar.json"
  for jar in "${desugar_jars[@]}"; do
    unzip -p "$jar" META-INF/desugar/d8/desugar.json > "$desugar_json" 2>/dev/null && break
  done
  [ -s "$desugar_json" ] || { echo "no META-INF/desugar/d8/desugar.json in ${desugar_jars[*]}" >&2; exit 2; }
  if ! "$java" -cp "$r8" com.android.tools.r8.L8 --release --min-api "$min_api" --lib "$platform/android.jar" \
      --desugared-lib "$desugar_json" --output "$l8/dex" "${desugar_jars[@]}" > "$l8/l8.log" 2>&1 ||
     ! "$java" -cp "$r8" "$here/gate/L8Cf.java" "$desugar_json" "$min_api" "$platform/android.jar" \
      "$l8/classes.jar" "${desugar_jars[@]}" >> "$l8/l8.log" 2>&1; then
    cat "$l8/l8.log" >&2
    exit 2
  fi
  l8_dex="$l8/dex"
  l8_classes="$l8/classes.jar"
fi

# dex <out dir> <program jar> [<classpath jar>]
dex() {
  local out="$1" program="$2" classpath=() desugar=()
  [ -n "${3:-}" ] && classpath=(--classpath "$3")
  [ -n "$desugar_json" ] && desugar=(--desugared-lib "$desugar_json")
  mkdir -p "$out"
  if ! "$java" -cp "$r8" com.android.tools.r8.D8 --release --min-api "$min_api" --lib "$platform/android.jar" \
      "${classpath[@]}" "${desugar[@]}" --output "$out" "$program" > "$out.log" 2>&1; then
    cat "$out.log" >&2
    return 2
  fi
}

# trace <report> <dex dir> <android.jar> [<extra lib jar>]: prints the number of missing references.
trace() {
  local report="$1" sources=() libs=(--lib "$3")
  for file in "$2"/*.dex; do sources+=(--source "$file"); done
  [ -n "${4:-}" ] && libs+=(--lib "$4")
  [ -n "$l8_classes" ] && libs+=(--lib "$l8_classes")
  # Missing definitions are mapped to info, so a non-zero exit is a failed run (bad jar, unknown flag...),
  # which must not read as "0 missing".
  if ! "$java" -cp "$r8" com.android.tools.r8.tracereferences.TraceReferences --check "${libs[@]}" \
      "${sources[@]}" --map-diagnostics:MissingDefinitionsDiagnostic error info > "$report" 2>&1; then
    cat "$report" >&2
    return 2
  fi
  missing_lines "$report" | grep -c . || true
}

# levels <report> <max level> <program jar> <dex dir>: prints the number of references above the level.
levels() {
  local defined=()
  [ -n "$l8_dex" ] && defined=(--defined "$l8_dex")
  if ! "$java" "$levels_tool" levels "$platform/data/api-versions.xml" "$2" "$3" "$4" "${defined[@]}" > "$1" 2>&1; then
    cat "$1" >&2
    return 2
  fi
  tail -1 "$1" | cut -d' ' -f1
}

# check <jar> [--report-only]: one summary line (plus the AndroidCompatApi33 line); details in $work/<jar>/.
failed=false
check() {
  local jar="$1" name out above missing33 missing30 holders summary
  name="$(basename "$jar" .jar)"
  out="$work/$name"
  rm -rf "$out"
  mkdir -p "$out"
  # The Android 4.4 jar's AndroidCompatApi33 is a stub that names no API 33 method: checked with the rest.
  if unzip -p "$jar" META-INF/MANIFEST.MF | grep -q '^X-Android-Patched: API 19'; then
    cp "$jar" "$out/main.jar"
    holders=0
  else
    holders="$("$java" "$levels_tool" split "$jar" "$api33_classes" "$out/main.jar" "$out/api33.jar")"
  fi
  local api33_jar=""
  [ "$holders" != "0" ] && api33_jar="$out/api33.jar"
  dex "$out/dex" "$out/main.jar" "$api33_jar"
  above="$(levels "$out/levels.txt" "$min_api" "$jar" "$out/dex")"
  missing33="$(trace "$out/trace-33.txt" "$out/dex" "$api33" "$api33_jar")"
  summary="$name: $above references above API $min_api, $missing33 missing on API 33"
  if [ -n "$min_jar" ]; then
    missing30="$(trace "$out/trace-$min_api.txt" "$out/dex" "$min_jar" "$api33_jar")"
    summary="$summary, $missing30 missing on API $min_api"
  else
    missing30=0
    summary="$summary (API $min_api trace skipped: no android-$min_api.jar, see MIN_API_JAR)"
  fi
  echo "$summary"
  local dalvik_failed=false
  if [ "$min_api" -lt 26 ]; then
    dalvik_checks "$jar" "$out" || dalvik_failed=true
  fi
  if [ "${2:-}" = --report-only ]; then
    return
  fi
  $dalvik_failed && failed=true
  if [ "$above" != "0" ] || [ "$missing33" != "0" ] || [ "$missing30" != "0" ]; then
    if [ "$above" != "0" ]; then
      sed -n '/^above API/,/^not in api-versions/p' "$out/levels.txt" | sed '1d;$d' >&2
    fi
    for report in "$out"/trace-*.txt; do
      missing_lines "$report" | sed -e 's/^Info: //' -e "s/^/  $(basename "$report" .txt): /" | cut -c1-240 >&2
    done
    failed=true
  fi
  if [ -n "$api33_jar" ]; then
    local holder_above holder_missing
    dex "$out/dex-api33" "$api33_jar" "$out/main.jar"
    holder_above="$(levels "$out/levels-api33.txt" 33 "$jar" "$out/dex-api33")"
    holder_missing="$(trace "$out/trace-api33.txt" "$out/dex-api33" "$api33" "$out/main.jar")"
    echo "  $(basename "$api33_classes") ($holders classes, run on API 33+ only): $holder_above references above API 33, $holder_missing missing on API 33"
    if [ "$holder_above" != "0" ] || [ "$holder_missing" != "0" ]; then
      sed -n '/^above API/,/^not in api-versions/p' "$out/levels-api33.txt" | sed '1d;$d' >&2
      missing_lines "$out/trace-api33.txt" | sed -e 's/^Info: //' -e 's/^/  trace-api33: /' | cut -c1-240 >&2
      failed=true
    fi
  fi
  # Info: what api-versions.xml does not list (dalvik annotations left out), for TraceReferences to judge.
  sed -n '/^not in api-versions/,$p' "$out/levels.txt" | sed '$d' \
    | awk -F'\t' 'NR == 1 { sub(/^not in api-versions.xml \(info\): /, ""); print "  info, not in api-versions.xml: " $0; next }
                  { print "    " $2 " " $3 " " $4 }'
}

# dalvik_checks <jar> <out dir>: below API 26 the signature-polymorphic calls, below 21 the KitKat verifier's
# class rejections and catch handlers; one line, details in <out dir>. Fails if any is found.
dalvik_checks() {
  local jar="$1" out="$2" asm poly line hard unchecked handlers
  asm="$(tool asm)"
  "$java" -cp "$asm" "$here/gate/PolyScan.java" "$jar" > "$out/polyscan.txt" 2>&1 || { cat "$out/polyscan.txt" >&2; return 2; }
  poly="$(tail -1 "$out/polyscan.txt" | cut -d' ' -f1)"
  line="  below API 26: $poly signature-polymorphic calls or method handle constants"
  if [ "$min_api" -lt 21 ]; then
    local dexdump
    dexdump="$(ls -d "$sdk"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1 || true)"
    [ -x "$dexdump" ] || { echo "dexdump not found under $sdk/build-tools" >&2; return 2; }
    "$java" -cp "$asm" "$here/gate/DalvikVerify44.java" "$out/main.jar" "$out/levels.txt" > "$out/dalvik.txt" 2>&1 \
      || { cat "$out/dalvik.txt" >&2; return 2; }
    hard="$(tail -1 "$out/dalvik.txt" | cut -d' ' -f1)"
    # A method the replay could not analyse (ANALYSIS-ERROR, or an ASM error that names no missing type: OTHER) is
    # not a pass: counted, and fails the gate like a hard failure.
    unchecked="$(grep -c -e $'\tOTHER ' -e $'\tANALYSIS-ERROR ' "$out/dalvik.txt" || true)"
    # Likewise a failed dexdump or dexcatch.py run (pipefail) must not read as "0 rejected catch handlers": each
    # dex file needs its own "<n> hard," line.
    local dexes=0
    : > "$out/dexcatch.txt"
    for file in "$out"/dex/*.dex; do
      dexes=$((dexes + 1))
      if ! "$dexdump" -d "$file" 2> "$out/dexdump.log" \
          | python3 -I "$here/gate/dexcatch.py" - "$out/levels.txt" >> "$out/dexcatch.txt"; then
        cat "$out/dexdump.log" >&2
        echo "dexdump -d $file | dexcatch.py failed" >&2
        return 2
      fi
    done
    if [ "$(grep -cE '^[0-9]+ hard,' "$out/dexcatch.txt" || true)" != "$dexes" ]; then
      echo "dexcatch.py did not report on each of the $dexes dex files: $out/dexcatch.txt" >&2
      return 2
    fi
    handlers="$(grep -E '^[0-9]+ hard,' "$out/dexcatch.txt" | awk '{ n += $1 } END { print n + 0 }')"
    line="$line; Dalvik (API $min_api): $hard hard verifier failures, $unchecked methods not analysed,"
    line="$line $handlers rejected catch handlers"
  else
    hard=0
    unchecked=0
    handlers=0
  fi
  echo "$line"
  if [ "$poly" != "0" ] || [ "$hard" != "0" ] || [ "$unchecked" != "0" ] || [ "$handlers" != "0" ]; then
    grep -v ' signature-polymorphic calls or method handle constants$' "$out/polyscan.txt" | sed 's/^/  polyscan: /' | cut -c1-240 >&2
    [ -f "$out/dalvik.txt" ] && grep $'\t' "$out/dalvik.txt" | sed 's/^/  dalvik: /' | cut -c1-240 >&2
    grep 'HARD' "$out/dexcatch.txt" 2>/dev/null | sed 's/^/  dexcatch: /' | cut -c1-240 >&2
    return 1
  fi
}

echo "D8/TraceReferences: $r8 ($("$java" -cp "$r8" com.android.tools.r8.D8 --version | head -1))"
echo "compile platform:   $platform"
echo "API 33 library:     $api33"
echo "API $min_api library:     ${min_jar:-none}"
if [ -n "$desugared_lib" ]; then
  echo "desugared library:  $desugared_lib ($(tool "$desugared_lib" | tr ':' '\n' | xargs -n1 basename | paste -sd' '))"
fi
if $baseline; then
  version="$(basename "${jars[0]}" | sed -n 's/^lucene-android-\(.*\)-android\(-api19\)\{0,1\}\.jar$/\1/p')"
  original="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" -name "lucene-core-$version.jar" | head -1)"
  if [ -n "$version" ] && [ -n "$original" ]; then
    printf 'baseline '
    check "$original" --report-only
  else
    echo "baseline lucene-core-$version.jar not in the Gradle cache, skipped"
  fi
fi

for jar in "${jars[@]}"; do
  check "$jar"
done
$failed && exit 1
exit 0

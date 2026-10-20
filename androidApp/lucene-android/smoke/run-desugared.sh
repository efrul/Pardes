#!/usr/bin/env bash
# Runs a single-file smoke program on the JVM the way Pardes for Android 4.4 runs Lucene: the jar of the 4.4
# edition (jarApi19) and the program, desugared together by AGP's D8 for minSdk 19 with desugar_jdk_libs_nio (to
# class files: every java.nio.file, java.util.function, stream... call goes to the j$ classes of the desugared
# library), on L8's class files of that library, with two stand-ins in front of them:
#  - android.os.Build$VERSION.SDK_INT is 19 (-Dshadow.sdk=N for another), for AndroidCompat;
#  - j$.adapter.AndroidVersionTest says API 19 (is24OrAbove and is26OrAbove false), so the desugared library takes
#    the platform for one without java.nio.file and uses its own emulated file system, as on Android 4.4 to 7.1.
# Lucene's META-INF/services (codecs, formats) are put on the class path as they are in the jar.
# The JDK still has the API 24-26 classes Android 4.4 lacks, so this does not replace check-min-api.sh --legacy.
#
# Usage: ./run-desugared.sh [--jar <lucene-android jar>] <Program.java> [args...]
#   Without --jar it builds :lucene-android:jarApi19 and runs on that. Expected figures: README.md.
# Env: ANDROID_HOME (default: sdk.dir of ../../local.properties) for the compile SDK's android.jar;
#      R8_JAR: a D8/R8 jar to use instead of AGP's.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"
work="$root/build/smoke-desugared"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
jartool="${JAVA_HOME:+$JAVA_HOME/bin/}jar"

jar=""
if [ "${1:-}" = --jar ]; then
  jar="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
  shift 2
fi
[ $# -ge 1 ] || { sed -n 's/^# Usage: //p' "$0" >&2; exit 2; }
program="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
shift
main="$(basename "$program" .java)"

if [ -z "$jar" ]; then
  (cd "$root/.." && ./gradlew -q :lucene-android:jarApi19 :lucene-android:gateTools)
  jar="$(ls -t "$root"/build/libs/lucene-android-*-android-api19.jar | head -1)"
else
  (cd "$root/.." && ./gradlew -q :lucene-android:gateTools)
fi
desugar_jars="$(sed -n 's/^nio=//p' "$root/build/check-min-api/tools.txt")"
IFS=: read -r -a desugar_jars <<< "$desugar_jars"

min_api="$(sed -n 's/^ *minSdk = \([0-9][0-9]*\).*/\1/p' "$root/../legacy/build.gradle.kts" | head -1)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f "$root/../local.properties" ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' "$root/../local.properties")"
fi
compile_sdk="$(sed -n 's/^ *compileSdk = \([0-9][0-9.]*\).*/\1/p' "$root/../legacy/build.gradle.kts" | head -1)"
android_jar="$sdk/platforms/android-$compile_sdk/android.jar"
[ -f "$android_jar" ] || { echo "no $android_jar (set ANDROID_HOME)" >&2; exit 2; }

r8="${R8_JAR:-}"
if [ -z "$r8" ]; then
  agp="$(sed -n 's/^agp *= *"\([^"]*\)".*/\1/p' "$root/../gradle/libs.versions.toml" | head -1)"
  r8="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.android.tools.build/builder/$agp" \
    -name "builder-$agp.jar" 2>/dev/null | head -1 || true)"
  [ -n "$r8" ] || { echo "builder-$agp.jar (AGP's D8) is not in the Gradle cache: run any build of androidApp first" >&2; exit 2; }
fi

rm -rf "$work/program" "$work/shadow" "$work/resources" "$work/desugared.jar" "$work/program.jar"
mkdir -p "$work/program" "$work/shadow/src/android/os" "$work/shadow/src/j\$/adapter" "$work/resources"

# The desugared library's configuration and L8's class files, once per library.
desugar_json="$work/desugar.json"
for lib in "${desugar_jars[@]}"; do
  unzip -p "$lib" META-INF/desugar/d8/desugar.json > "$desugar_json" 2>/dev/null && break
done
l8="$work/l8-$min_api-$(basename "${desugar_jars[0]}" .jar).jar"
if [ ! -f "$l8" ]; then
  "$java" -cp "$r8" "$root/gate/L8Cf.java" "$desugar_json" "$min_api" "$android_jar" "$work/l8.tmp.jar" \
    "${desugar_jars[@]}"
  mv "$work/l8.tmp.jar" "$l8"
fi

"$javac" -nowarn -XDsuppressNotes --release 17 -cp "$jar" -d "$work/program" "$program"
(cd "$work/program" && "$jartool" cf "$work/program.jar" .)
"$java" -cp "$r8" com.android.tools.r8.D8 --classfile --release --min-api "$min_api" --lib "$android_jar" \
  --desugared-lib "$desugar_json" --output "$work/desugared.jar" "$jar" "$work/program.jar" > "$work/d8.log" 2>&1 \
  || { cat "$work/d8.log" >&2; exit 2; }

cat > "$work/shadow/src/android/os/Build.java" <<'EOF'
package android.os;

/** Stand-in for AndroidCompat.sdkInt(): Android 4.4 (API 19), or -Dshadow.sdk. */
public class Build {
  public static class VERSION {
    public static final int SDK_INT = Integer.getInteger("shadow.sdk", 19);
  }
}
EOF
cat > "$work/shadow/src/j\$/adapter/AndroidVersionTest.java" <<'EOF'
package j$.adapter;

/** Stand-in for the desugared library's platform probe: Android 4.4, no java.nio.file (its emulated file system). */
public class AndroidVersionTest {
  public static final boolean is24OrAbove = false;
  public static final boolean is26OrAbove = false;
  public static final boolean isHeadfull = false;
}
EOF
"$javac" -nowarn --release 17 -d "$work/shadow" $(find "$work/shadow/src" -name '*.java')
unzip -q -o "$jar" 'META-INF/services/*' -d "$work/resources"

exec "$java" -cp "$work/shadow:$work/desugared.jar:$l8:$work/resources" "$main" "$@"

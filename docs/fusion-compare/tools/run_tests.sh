#!/usr/bin/env bash
# The semantic package's tests that need no Compose, outside Gradle (IMPLEMENTATION.md): build.sh's build of this
# checkout, the tests compiled with the same kotlinc against it, run with JUnit 4.
#
#   run_tests.sh WORK_DIR
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../.." && pwd)"
work="$1"; JAVA="${JAVA:-java}"
mkdir -p "$work/testjars"; work="$(cd "$work" && pwd)"
bash "$here/build.sh" "$work/build" > "$work/build.log" 2>&1 || { tail -n 30 "$work/build.log"; exit 1; }
fetch() {  # group artifact version: from Maven Central, retried (it answers 429 to bursts)
    local g="$1" a="$2" v="$3" f="$2-$3.jar" i
    [ -s "$work/testjars/$f" ] && return
    for i in 1 2 3 4 5 6; do
        curl -fsSL -o "$work/testjars/$f" "https://repo.maven.apache.org/maven2/${g//.//}/$a/$v/$f" && return
        sleep $((i * 5))
    done
    echo "could not fetch $f" >&2; exit 1
}
fetch junit junit 4.13.2
fetch org.hamcrest hamcrest-core 1.3
fetch org.jetbrains.kotlin kotlin-test 2.4.20
fetch org.jetbrains.kotlin kotlin-test-junit 2.4.20
cp_of() { ls "$1"/*.jar | tr '\n' ':'; }
b="$work/build"; t="$repo/SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic"
rm -rf "$work/testclasses"; mkdir -p "$work/testclasses"
"$JAVA" -Xmx3g -cp "$(cp_of "$b/kotlinc")" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
    -jvm-target 21 -Xfriend-paths="$b/classes" -cp "$b/classes:$(cp_of "$b/jars")$(cp_of "$work/testjars")" \
    -d "$work/testclasses" "$t/HybridSearchEngineFallbackTest.kt" "$t/HybridSearchEngineFusionTest.kt" \
    "$t/HybridSearchEngineKeepLeadersTest.kt" "$t/LevelPrecedenceTest.kt"
classes=$(cd "$work/testclasses" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
"$JAVA" -cp "$work/testclasses:$b/classes:$(cp_of "$b/jars")$(cp_of "$work/testjars")" org.junit.runner.JUnitCore $classes

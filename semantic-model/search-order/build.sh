#!/usr/bin/env bash
# Builds harness/*.kt (and eval-100/harness/Eval.kt) with a standalone Kotlin compiler, outside Gradle: SeforimLibrary's
# search sources, the app's semantic package, LongQuerySearchEngine and CoroutineUtils, eval-100's LoggerStub, and a
# copy of LuceneSearchEngine.kt changed by harness/bm25-knob.patch (for Bm25.kt; the other programs never set the knob,
# so they search as the app does).
#
#   build.sh OUT_DIR
#
# Needs a JDK 21 (JAVA, or java on PATH), patch and curl. The jars come from the Gradle cache when they are there, from
# Maven Central otherwise. On Windows (Git Bash) give an OUT_DIR whose path has only ASCII letters: Java misreads other
# letters passed from the shell. Then run, for example:
#   "$JAVA" -cp "$(cat OUT_DIR/classpath.txt)" HistoryKt seforim.db history.tsv history.jsonl
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
out="$1"
JAVA="${JAVA:-java}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
rm -rf "$out/src" "$out/classes"
mkdir -p "$out/src" "$out/classes" "$out/jars" "$out/kotlinc"

sep=":"
native() { printf '%s' "$1"; }
if command -v cygpath > /dev/null; then
    sep=";"
    native() { cygpath -w "$1"; }
fi

# group artifact version dir: the jar, from the Gradle cache or Maven Central
fetch() {
    local g="$1" a="$2" v="$3" dir="$4" f="$2-$3.jar" cached
    [ -f "$dir/$f" ] && return
    cached="$(find "$HOME/.gradle/caches/modules-2/files-2.1/$g/$a/$v" -name "$f" 2> /dev/null | head -1 || true)"
    if [ -n "$cached" ]; then
        cp "$cached" "$dir/"
    else
        curl -fsSL -o "$dir/$f" "https://repo1.maven.org/maven2/${g//.//}/$a/$v/$f"
    fi
}

fetch org.apache.lucene lucene-core 10.5.2 "$out/jars"
fetch org.apache.lucene lucene-analysis-common 10.5.2 "$out/jars"
fetch org.apache.lucene lucene-queryparser 10.5.2 "$out/jars"
fetch co.touchlab kermit-jvm 2.1.0 "$out/jars"
fetch co.touchlab kermit-core-jvm 2.1.0 "$out/jars"
fetch org.jsoup jsoup 1.22.1 "$out/jars"
fetch org.jetbrains.kotlinx kotlinx-coroutines-core-jvm 1.10.2 "$out/jars"
fetch org.xerial sqlite-jdbc 3.50.3.0 "$out/jars"
fetch com.microsoft.onnxruntime onnxruntime 1.17.1 "$out/jars"
fetch org.json json 20240303 "$out/jars"
fetch org.jetbrains.kotlin kotlin-stdlib 2.4.20 "$out/jars"
fetch org.jetbrains annotations 23.0.0 "$out/jars"

fetch org.jetbrains.kotlin kotlin-compiler-embeddable 2.4.20 "$out/kotlinc"
fetch org.jetbrains.kotlin kotlin-stdlib 2.4.20 "$out/kotlinc"
fetch org.jetbrains.kotlin kotlin-script-runtime 2.4.20 "$out/kotlinc"
fetch org.jetbrains.kotlin kotlin-reflect 2.4.10 "$out/kotlinc"
fetch org.jetbrains.kotlin kotlin-daemon-embeddable 2.4.20 "$out/kotlinc"
fetch org.jetbrains.intellij.deps trove4j 1.0.20200330 "$out/kotlinc"
fetch org.jetbrains annotations 13.0 "$out/kotlinc"
fetch org.jetbrains.kotlinx kotlinx-coroutines-core-jvm 1.8.0 "$out/kotlinc"

app="$repo/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp"
cp "$repo"/SeforimLibrary/search/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/search/*.kt "$out/src/"
cp "$app"/features/search/semantic/*.kt "$out/src/"
cp "$app/framework/search/LongQuerySearchEngine.kt" "$app/core/coroutines/CoroutineUtils.kt" "$out/src/"
cp "$repo/semantic-model/eval-100/harness/LoggerStub.kt" "$repo/semantic-model/eval-100/harness/Eval.kt" "$out/src/"
cp "$here"/harness/*.kt "$out/src/"
# The checkout may have CRLF line endings, the patch has LF
sed -i 's/\r$//' "$out/src/LuceneSearchEngine.kt"
sed 's/\r$//' "$here/harness/bm25-knob.patch" | patch -s -d "$out/src" -p1

cp_of() { local list=""; for j in "$1"/*.jar; do list="$list$(native "$j")$sep"; done; printf '%s' "${list%"$sep"}"; }
"$JAVA" -Xmx2g -cp "$(cp_of "$out/kotlinc")" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
    -jvm-target 21 -cp "$(cp_of "$out/jars")" -d "$(native "$out/classes")" "$(native "$out/src")"
printf '%s%s%s' "$(native "$out/classes")" "$sep" "$(cp_of "$out/jars")" > "$out/classpath.txt"
echo "built: $out/classes; classpath in $out/classpath.txt"

#!/usr/bin/env bash
# Builds FeedbackHistory.kt with a standalone Kotlin compiler, outside Gradle, against this repository's search code:
# SeforimLibrary's search sources, the app's semantic package, LongQuerySearchEngine, PardesLexicon and
# CoroutineUtils (as pardes-semantic's search-order/build.sh, without its BM25 patch, so the lexical side searches as
# the app does). CategoryStub.kt stands in for SeforimLibrary core's Category, which MeaningFacets.kt reads.
#
#   build.sh OUT_DIR
#
# Needs a JDK 21 (JAVA, or java on PATH) and curl, and SeforimLibrary checked out:
#   git submodule update --init SeforimLibrary
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../.." && pwd)"
[ -d "$repo/SeforimLibrary/search" ] || { echo "git submodule update --init SeforimLibrary" >&2; exit 1; }
out="$1"; JAVA="${JAVA:-java}"
mkdir -p "$out"; out="$(cd "$out" && pwd)"
rm -rf "$out/src" "$out/classes"; mkdir -p "$out/src" "$out/classes" "$out/jars" "$out/kotlinc"

fetch() {  # group artifact version dir: from Maven Central, retried (it answers 429 to bursts)
    local g="$1" a="$2" v="$3" dir="$4" f="$2-$3.jar" i
    [ -s "$dir/$f" ] && return
    for i in 1 2 3 4 5 6; do
        curl -fsSL -o "$dir/$f" "https://repo.maven.apache.org/maven2/${g//.//}/$a/$v/$f" && return
        sleep $((i * 5))
    done
    echo "could not fetch $f" >&2; exit 1
}
fetch org.apache.lucene lucene-core 10.5.2 "$out/jars"
fetch org.apache.lucene lucene-analysis-common 10.5.2 "$out/jars"
fetch org.apache.lucene lucene-queryparser 10.5.2 "$out/jars"
fetch org.apache.lucene lucene-queries 10.5.2 "$out/jars"
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
cp "$app/framework/search/LongQuerySearchEngine.kt" "$app/framework/search/PardesLexicon.kt" "$app/core/coroutines/CoroutineUtils.kt" "$out/src/"
cp "$here/FeedbackHistory.kt" "$here/OtzariaLexical.kt" "$here/CategoryStub.kt" "$here/LoggerStub.kt" "$here/ExtraForms.kt" "$out/src/"
# A word's extra forms (-DextraForms, EXPANSIONS.md); without it the search is the app's own
python3 "$here/extra_forms_patch.py" "$out/src/LuceneSearchEngine.kt"

cp_of() { local list=""; for j in "$1"/*.jar; do list="$list$j:"; done; printf '%s' "${list%:}"; }
"$JAVA" -Xmx3g -cp "$(cp_of "$out/kotlinc")" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
    -jvm-target 21 -cp "$(cp_of "$out/jars")" -d "$out/classes" "$out/src"
printf '%s:%s' "$out/classes" "$(cp_of "$out/jars")" > "$out/classpath.txt"
echo "built: $out/classes; classpath in $out/classpath.txt"

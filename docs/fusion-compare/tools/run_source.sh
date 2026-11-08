#!/usr/bin/env bash
# The "where is it written" test (SOURCE_TEST.md), from its queries to the numbers and the page:
#
#   run_source.sh WORK_DIR PARDES_SEMANTIC_DIR [library tag] [pack tag]
#
# Uses the same library, pack and build as run.sh (in WORK_DIR, fetched and built when missing). Writes WORK_DIR/source:
# the searches, the run, results-100.txt and results-137.txt (source_eval.py), and source_compare.html.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$1"; export PARDES_SEMANTIC="$2"
lib_tag="${3:-v3-20261012193827}"; pack_tag="${4:-semantic-2-v3-20261012193827}"
src="$work/source"
mkdir -p "$work/library" "$src/out"
[ -f "$work/library/seforim.db" ] || python3 "$here/fetch_library.py" "$lib_tag" "$work/library"
[ -d "$work/library/pardes.vec.lucene" ] || python3 "$here/fetch_pack.py" "$pack_tag" "$work/library"
[ -f "$work/build/classpath.txt" ] || bash "$here/build.sh" "$work/build"
python3 "$here/source_searches.py" "$here/../source_test/queries.tsv" "$src"
java -Xmx6g --add-modules jdk.incubator.vector -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    -cp "$(cat "$work/build/classpath.txt")" FeedbackHistoryKt "$work/library/seforim.db" "$src/searches.tsv" "$src/results.jsonl" \
    2> "$src/run.err"
python3 "$here/fuse_all.py" "$src/results.jsonl" "$work/library/seforim.db" "$src/searches.tsv" \
    "$src/search_meta.json" "$src/grades_target.json" "$src/out"
python3 "$here/make_html.py" "$src/out/orderings.json" "$src/source_compare.html" "בדיקת חיפוש מקור: 157 שאלות, כל שיטות המיון"
python3 "$here/source_eval.py" "$src/out" "$src/targets.json" "$work/library/seforim.db" --sample 100 > "$src/results-100.txt"
python3 "$here/source_eval.py" "$src/out" "$src/targets.json" "$work/library/seforim.db" > "$src/results-137.txt"

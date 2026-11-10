#!/usr/bin/env bash
# Otzaria's lexical search against Pardes's (OTZARIA_LEXICAL.md), on the "where is it written" test and on the feedback's
# graded searches:
#
#   run_otzaria.sh WORK_DIR PARDES_SEMANTIC_DIR
#
# Needs WORK_DIR as run.sh and run_source.sh left it: the library, the build (with OtzariaLexical.kt; build.sh again if
# it is older), WORK_DIR/run (the feedback's run, its grades) and WORK_DIR/source (the test's run). Writes WORK_DIR/otzaria:
# every mode's lexical candidates, its fusions, source-<mode>.txt (source_eval.py --all) and feedback-graded.txt
# (graded_compare.py).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$1"; export PARDES_SEMANTIC="$2"
o="$work/otzaria"; db="$work/library/seforim.db"
mkdir -p "$o/source" "$o/run"
java_run() {
    java -Xmx6g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "$(cat "$work/build/classpath.txt")" \
        OtzariaLexicalKt "$work/library/seforim.db.lucene" "$work/library/lexical.db" "$1" "$2" 2> "$2/run.err"
}
java_run "$work/source/searches.tsv" "$o/source"
java_run "$work/run/searches.tsv" "$o/run"
modes="smart exact gram all smartspell"

# The test: every search, every lexical side
python3 "$here/source_eval.py" "$work/source/out" "$work/source/targets.json" "$db" --all > "$o/source-pardes.txt"
for m in $modes; do
    python3 "$here/swap_lexical.py" "$work/source/results.jsonl" "$o/source/$m.jsonl" "$o/source/results_$m.jsonl"
    python3 "$here/fuse_all.py" "$o/source/results_$m.jsonl" "$db" "$work/source/searches.tsv" "$work/source/search_meta.json" \
        "$work/source/grades_target.json" "$o/source/out_$m" > /dev/null
    python3 "$here/source_eval.py" "$o/source/out_$m" "$work/source/targets.json" "$db" --all > "$o/source-$m.txt"
done

# The feedback's graded searches
g="$work/run/grades.json"; runs=""
python3 "$here/swap_lexical.py" "$work/run/results.jsonl" - "$o/run/results_pardes.jsonl" --graded "$g"
python3 "$here/fuse_all.py" "$o/run/results_pardes.jsonl" "$db" "$work/run/searches.tsv" "$work/run/search_meta.json" "$g" \
    "$o/run/out_pardes" > /dev/null
for m in $modes; do
    python3 "$here/swap_lexical.py" "$work/run/results.jsonl" "$o/run/$m.jsonl" "$o/run/results_$m.jsonl" --graded "$g"
    python3 "$here/fuse_all.py" "$o/run/results_$m.jsonl" "$db" "$work/run/searches.tsv" "$work/run/search_meta.json" "$g" \
        "$o/run/out_$m" > /dev/null
    runs="$runs $m=$o/run/out_$m"
done
# shellcheck disable=SC2086
python3 "$here/graded_compare.py" "$g" "$o/run/out_pardes" $runs --methods app,p12w,p12wl,combmnz,lex > "$o/feedback-graded.txt"

# How many of the feedback's searches each lexical side finds nothing for
python3 - "$work/run/results.jsonl" "$o/run" $modes > "$o/feedback-empty.txt" <<'PY'
import json, statistics, sys
pardes = [json.loads(l).get("lexTotal", 0) or 0 for l in open(sys.argv[1], encoding="utf-8")]
print(f"{len(pardes)} searches | lexical side | none found | 3 or fewer | median found")
print(f"pardes | {sum(1 for v in pardes if not v)} | {sum(1 for v in pardes if v <= 3)} | {statistics.median(pardes)}")
for m in sys.argv[3:]:
    t = [json.loads(l)["total"] for l in open(f"{sys.argv[2]}/{m}.jsonl", encoding="utf-8")]
    print(f"{m} | {sum(1 for v in t if not v)} | {sum(1 for v in t if v <= 3)} | {statistics.median(t)}")
PY

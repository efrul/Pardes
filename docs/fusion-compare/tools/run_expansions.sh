#!/usr/bin/env bash
# Extra forms in the lexical side (EXPANSIONS.md): the Aramaic dictionary, synonyms and full/defective spelling, on the
# "where is it written" test and the feedback's graded searches, and how much each side's order counts:
#
#   run_expansions.sh WORK_DIR PARDES_SEMANTIC_DIR
#
# Needs WORK_DIR as run.sh and run_source.sh left it (the library, WORK_DIR/run with its grades, WORK_DIR/source) and a
# build of build.sh that has ExtraForms.kt and SpellingForms.kt (build.sh again if it is older). Writes
# WORK_DIR/expansions: the tables, the runs, their fusions, source-<table>.txt (source_eval.py --all),
# feedback-<depth>.txt (graded_compare.py) and order-*.txt (order_shuffle.py).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$1"; export PARDES_SEMANTIC="$2"
e="$work/expansions"; db="$work/library/seforim.db"; cp="$(cat "$work/build/classpath.txt")"
mkdir -p "$e"
python3 "$here/expansion_tables.py" "$e"
python3 "$here/swap_lexical.py" "$work/run/results.jsonl" - "$e/fb_base.jsonl" --graded "$work/run/grades.json"
python3 - "$e/fb_base.jsonl" "$work/run/searches.tsv" "$e/fb_searches.tsv" <<'PY'
import json, sys
ids = {json.loads(l)["id"] for l in open(sys.argv[1], encoding="utf-8")}
rows = open(sys.argv[2], encoding="utf-8").read().splitlines()
open(sys.argv[3], "w", encoding="utf-8").write("\n".join([rows[0]] + [r for r in rows[1:] if int(r.split("\t")[0]) in ids]) + "\n")
PY
java -Dfile.encoding=UTF-8 -cp "$cp" SpellingFormsKt "$e/spelling.tsv" "$work/source/searches.tsv" "$e/fb_searches.tsv"
for t in aramaic synonyms spelling; do
    for s in source fb; do
        q="$work/source/searches.tsv"; [ $s = fb ] && q="$e/fb_searches.tsv"
        java -Xmx6g --add-modules jdk.incubator.vector -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
            -DextraForms="$e/$t.tsv" -DextraFormsIndex="$work/library/seforim.db.lucene" -DextraFormsKeep=4 \
            -cp "$cp" FeedbackHistoryKt "$db" "$q" "$e/${s}_$t.jsonl" 2> "$e/${s}_$t.err"
    done
done
for t in base aramaic synonyms spelling; do
    src="$e/source_$t.jsonl"; fb="$e/fb_$t.jsonl"; [ $t = base ] && src="$work/source/results.jsonl"
    python3 "$here/fuse_all.py" "$src" "$db" "$work/source/searches.tsv" "$work/source/search_meta.json" \
        "$work/source/grades_target.json" "$e/out_source_$t" > /dev/null
    python3 "$here/source_eval.py" "$e/out_source_$t" "$work/source/targets.json" "$db" --all > "$e/source-$t.txt"
    python3 "$here/fuse_all.py" "$fb" "$db" "$work/run/searches.tsv" "$work/run/search_meta.json" "$work/run/grades.json" \
        "$e/out_fb_$t" > /dev/null
done
for d in 10 3; do
    python3 "$here/graded_compare.py" "$work/run/grades.json" "$e/out_fb_base" aramaic="$e/out_fb_aramaic" \
        synonyms="$e/out_fb_synonyms" spelling="$e/out_fb_spelling" --methods app,p12wl,p12wsl,p12wl_w2,p12wl_w15 --depth $d \
        > "$e/feedback-$d.txt"
done
python3 "$here/graded_compare.py" "$work/run/grades.json" "$e/out_fb_base" aramaic="$e/out_fb_aramaic" \
    synonyms="$e/out_fb_synonyms" spelling="$e/out_fb_spelling" --methods all --depth 10 --condensed > "$e/feedback-condensed-10.txt"
python3 "$here/order_shuffle.py" "$work/source/results.jsonl" "$e/out_source_base/lines.pkl" "$db" "$work/source/targets.json" \
    > "$e/order-source.txt"
python3 "$here/order_shuffle.py" "$e/fb_base.jsonl" "$e/out_fb_base/lines.pkl" "$db" > "$e/order-feedback.txt"
for s in source fb; do
    g="$work/run/grades.json"; flag="--feedback"; [ $s = source ] && { g="$work/source/grades_target.json"; flag=""; }
    # shellcheck disable=SC2086
    python3 "$here/merge_runs.py" "$g" "$e/merged_$s.json" $flag "$e/out_${s}_base" "ar:ארמית=$e/out_${s}_aramaic" \
        "syn:נרדפות=$e/out_${s}_synonyms" "sp:כתיב מלא/חסר=$e/out_${s}_spelling"
    python3 "$here/make_html.py" "$e/merged_$s.json" "$e/${s}_expansions.html" "בלי תוספות, עם המילון הארמי, עם מילים נרדפות, עם כתיב מלא/חסר"
done

#!/usr/bin/env bash
# The whole run, from a feedback export to the page:
#
#   run.sh feedback.json WORK_DIR PARDES_SEMANTIC_DIR [library tag] [pack tag]
#
# WORK_DIR gets the library (about 15 GB with the pack), the build, the run, the page (WORK_DIR/out/fusion_compare.html)
# and the numbers of ANALYSIS.md (WORK_DIR/out/analysis.txt).
# PARDES_SEMANTIC_DIR is a checkout of efrul/pardes-semantic: fuse_all.py uses its search-order/order_common.py and
# eval-real/level_order.py (keepLeaders, holdsQuote, verbatimKey, the books' levels). Needs a JDK 21, Python 3 with
# zstandard, curl, and SeforimLibrary checked out in this repository.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
feedback="$1"; work="$2"; export PARDES_SEMANTIC="$3"
lib_tag="${4:-v3-20261012193827}"; pack_tag="${5:-semantic-2-v3-20261012193827}"
mkdir -p "$work/library" "$work/run" "$work/out"
[ -f "$work/library/seforim.db" ] || python3 "$here/fetch_library.py" "$lib_tag" "$work/library"
[ -d "$work/library/pardes.vec.lucene" ] || python3 "$here/fetch_pack.py" "$pack_tag" "$work/library"
[ -f "$work/build/classpath.txt" ] || bash "$here/build.sh" "$work/build"
python3 "$here/feedback_searches.py" "$feedback" "$work/run"
java -Xmx6g --add-modules jdk.incubator.vector -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    -cp "$(cat "$work/build/classpath.txt")" FeedbackHistoryKt "$work/library/seforim.db" "$work/run/searches.tsv" "$work/run/results.jsonl" \
    2> "$work/run/run.err"
python3 "$here/fuse_all.py" "$work/run/results.jsonl" "$work/library/seforim.db" "$work/run/searches.tsv" \
    "$work/run/search_meta.json" "$work/run/grades.json" "$work/out"
python3 "$here/make_html.py" "$work/out/orderings.json" "$work/out/fusion_compare.html"
python3 "$here/analyze.py" "$work/out" "$work/run/grades.json" "$work/library/seforim.db" > "$work/out/analysis.txt"

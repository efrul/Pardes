"""The run of FeedbackHistory.kt with another lexical side: OtzariaLexical.kt's, for one of its modes.

    python swap_lexical.py results.jsonl MODE.jsonl|- out.jsonl [--graded grades.json]

Each search keeps its dense candidates; its lexical candidates become MODE.jsonl's first 150 (with their scores, marked
"lexRaw": no base books' boost to take off), and its app page is dropped: fuse_all.py's `app` then recomputes the app's
fusion (`apprrf`) over the new candidates. With `-` the run is copied as it is. With --graded, only the searches whose
query has a grade from the feedback (grades.json, feedback_searches.py's) are kept: graded_compare.py's searches, in a
fraction of fuse_all.py's time.
"""

import json
import sys

graded = None
if "--graded" in sys.argv:
    grades = json.load(open(sys.argv[sys.argv.index("--graded") + 1], encoding="utf-8"))
    graded = {k.split("\t")[0] for k, v in grades.items() if v.get("by") != "claude"}
other = {}
if sys.argv[2] != "-":
    for line in open(sys.argv[2], encoding="utf-8"):
        x = json.loads(line)
        other[x["id"]] = x
with open(sys.argv[3], "w", encoding="utf-8") as f:
    n = 0
    for line in open(sys.argv[1], encoding="utf-8"):
        r = json.loads(line)
        if graded is not None and r["query"] not in graded:
            continue
        if sys.argv[2] != "-":
            o = other[r["id"]]
            r.update(lexCand=o["hits"][:150], lexTotal=o["total"], lexRaw=True, hybrid=[])
        f.write(json.dumps(r, ensure_ascii=False) + "\n")
        n += 1
print(f"{n} searches")

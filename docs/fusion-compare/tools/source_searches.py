"""The searches of the "where is it written" test (SOURCE_TEST.md), from its queries, for FeedbackHistory.kt and fuse_all.py.

    python source_searches.py queries.tsv out_dir

queries.tsv is source_test/queries.tsv (id, group, type, query, lineId, book, ref, ...). Writes to out_dir:
searches.tsv (every query in the "auto" scope, as the app searches with no scope chosen: the base books, and all
the books when the lexical side finds nothing there), search_meta.json (empty: these searches have no feedback),
targets.json (each search's source line, for source_eval.py) and grades_target.json (the source line as grade 3 by
"target", so that the page marks it).
"""

import csv
import json
import sys
from pathlib import Path

rows = list(csv.DictReader(open(sys.argv[1], encoding="utf-8"), delimiter="\t"))
out = Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
targets, grades = {}, {}
with open(out / "searches.tsv", "w", encoding="utf-8") as f:
    f.write("id\tquery\tkind\tscopeId\text\n")
    for r in rows:
        f.write(f"{r['id']}\t{r['query']}\tauto\t\t0\n")
        line = int(r["lineId"])
        targets[r["id"]] = {"line": line, "group": r["group"], "type": r["type"], "book": r["book"], "ref": r["ref"],
                            "q": r["query"]}
        grades[f"{r['query']}\t{line}"] = {"grade": 3, "by": "target"}
json.dump({}, open(out / "search_meta.json", "w", encoding="utf-8"))
json.dump(targets, open(out / "targets.json", "w", encoding="utf-8"), ensure_ascii=False)
json.dump(grades, open(out / "grades_target.json", "w", encoding="utf-8"), ensure_ascii=False)
print(f"{len(rows)} searches")

"""The judging pool of eval-real: per search, the union of each engine's first K results, with the engines and places
that gave each line and the grade it already has.

    python pool.py results.jsonl queries.tsv grades.tsv [K=3] [first] [last]

results.jsonl: eval-100/harness/Eval.kt run on the searches (README.md). The text printed is the book's, from
results.jsonl: it stays out of the repository, only the line ids, their refs and grades go into grades.tsv.
Engines: L lexical, H hybrid, B hybrid in the base books only, D the dense side alone.
"""

import csv
import json
import re
import sys

TAG = {"lexical": "L", "hybrid": "H", "hybridBase": "B", "dense": "D"}


def main() -> None:
    rows = {r["id"]: r for r in map(json.loads, open(sys.argv[1], encoding="utf-8"))}
    queries = {int(r["id"]): r for r in csv.DictReader(open(sys.argv[2], encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE)}
    graded = {(int(r["qid"]), int(r["lineId"])): r["grade"]
              for r in csv.DictReader(open(sys.argv[3], encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE)}
    k = int(sys.argv[4]) if len(sys.argv) > 4 else 3
    first = int(sys.argv[5]) if len(sys.argv) > 5 else min(rows)
    last = int(sys.argv[6]) if len(sys.argv) > 6 else max(rows)
    for qid in range(first, last + 1):
        r, q = rows[qid], queries[qid]
        hint = f" (searched in: {q['hint_book']})" if q["hint_book"] else ""
        print(f"\n### {qid} [{r['scope']}{hint}] {r['query']}")
        pool = {}
        for engine, tag in TAG.items():
            for i, h in enumerate(r[engine][:k]):
                pool.setdefault(h["lineId"], (h, []))[1].append(f"{tag}{i + 1}")
        for lid, (h, tags) in pool.items():
            text = re.sub(r"[֑-ׇ]", "", re.sub(r"<[^>]+>", "", h["text"]))
            grade = graded.get((qid, lid), "?")
            print(f"  {lid} {' '.join(tags)} [{grade}] | {h['heRef'] or h['book']} | {text[:150]}")


if __name__ == "__main__":
    main()

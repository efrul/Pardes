"""Where each engine puts the target line of each eval-v1 query (before the graded judgments exist).

    python score_v1.py queries.tsv results.jsonl [--details | --md]

results.jsonl: eval-100/harness/Eval.kt run on queries.tsv converted to its columns (id as a number, intent as group,
target_line as expected). Exact: the target line in the first ten; near: a line of the same siman / chapter / daf
(its heRef without the last part). By intent and by family; the noise queries (X) count how many results each engine
still gives. The need queries (from batch 2: no target_line, target_ref only a hint of the source in mind) have no
single answer and are left to the graded judgments. --md prints the per-query table of results-*.md: each engine's rank of the target, ≈ when only a line of
its section is there, — when neither is; the first result of the hybrid by its heRef only (no text).
"""

import csv
import json
import sys
from collections import defaultdict

SYSTEMS = ("lexical", "hybrid", "dense", "hybridBase")


def section(ref: str) -> str:
    """The heRef without its last part: "שולחן ערוך, אורח חיים רח, ז" -> "שולחן ערוך, אורח חיים רח"."""
    ref = " ".join(ref.split())  # the library's refs carry double spaces, queries.tsv's not always
    return ref.rsplit(",", 1)[0].strip() if "," in ref else ref


def main() -> None:
    queries = {int(r["id"][3:]): r for r in csv.DictReader(open(sys.argv[1], encoding="utf-8"), delimiter="\t",
                                                          quoting=csv.QUOTE_NONE)}
    rows = [json.loads(line) for line in open(sys.argv[2], encoding="utf-8")]
    systems = [s for s in SYSTEMS if any(r.get(s) for r in rows)]
    per = defaultdict(lambda: defaultdict(list))
    noise = defaultdict(list)
    details = []
    table = []
    for r in rows:
        q = queries[r["id"]]
        if q["intent"] == "X":
            for s in systems:
                noise[s].append(len(r[s]))
            continue
        if not q["target_line"]:
            continue
        target, target_section = int(q["target_line"]), section(q["target_ref"])
        ranks = {}
        for s in systems:
            hits = r[s][:10]
            exact = next((i + 1 for i, h in enumerate(hits) if h["lineId"] == target), None)
            near = next((i + 1 for i, h in enumerate(hits) if section(h["heRef"]) == target_section), None)
            ranks[s] = (exact, near)
            for key in ("ALL", "intent " + q["intent"], "family " + q["family"]):
                per[key][s].append(ranks[s])
        details.append((q["id"], q["intent"], q["family"], r["scope"], ranks, q["query"]))
        first = r["hybrid"][0] if r["hybrid"] else None
        table.append((q, r["scope"], ranks, (first["heRef"] or first["book"]) if first else "", r.get("errors", {})))

    def fmt(v, i):
        found = [x[i] for x in v]
        return f"{sum(1 for x in found if x)}/{len(found)} ({sum(1 for x in found if x == 1)})"

    print("| | " + " | ".join(f"{s}: exact (first) / near" for s in systems) + " |")
    print("|---|" + "---|" * len(systems))
    for key in sorted(per, key=lambda k: (k != "ALL", k)):
        print(f"| {key} | " + " | ".join(f"{fmt(per[key][s], 0)} / {fmt(per[key][s], 1)}" for s in systems) + " |")
    print("\nnoise queries, results per engine:", {s: noise[s] for s in systems})
    if "--md" in sys.argv:
        names = {"lexical": "רגיל", "hybrid": "משולב", "dense": "סמנטי", "hybridBase": "משולב בבסיס"}
        print("\n| # | סגנון | שאלה | יעד | היקף | " + " | ".join(names[s] for s in systems) + " | הראשון במשולב | שגיאה |")
        print("|---|---|---|---|---|" + "---|" * len(systems) + "---|---|")
        for q, scope, ranks, first, errors in table:
            cells = " | ".join(str(ranks[s][0]) if ranks[s][0] else f"≈{ranks[s][1]}" if ranks[s][1] else "—" for s in systems)
            err = ", ".join(sorted(set(errors.values()))) if errors else ""
            print(f"| {int(q['id'][3:])} | {q['intent']} | {q['query']} | {q['target_ref']} | {scope} | {cells} | {first} | {err} |")
    if "--details" in sys.argv:
        for qid, intent, family, scope, ranks, text in details:
            cells = " ".join(f"{s}={ranks[s][0] or '-'}/{ranks[s][1] or '-'}" for s in systems)
            print(f"{qid} {intent} {family} {scope} {cells} | {text}")


if __name__ == "__main__":
    main()

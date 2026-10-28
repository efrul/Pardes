"""eval-v1 by the graded pools (batch 2 on): per engine, the first result good (grade 2 or more), a good one in the
first three, the source (grade 3) in the first three, nDCG@3, as eval-real's score.py, by kind of query.

    python score_graded.py queries.tsv results.jsonl grades.tsv [--md]

Kinds: target (written for a target line), need (no target line: any good answer counts), typo (Y: a copy of another
query of the batch with a typo, matched to it by target_line, or by family and target_ref for a need query). A query
no line was judged for (the noise, X) is left out. --md prints the per-query table of results-*.md: each engine's best
grade in its first three, and the hybrid's first result by its ref only (no text).
"""

import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "eval-real"))
from score import dcg, load_grades  # noqa: E402

ENGINES = ("lexical", "hybrid", "hybridBase", "dense")
NAMES = {"lexical": "רגיל", "hybrid": "משולב", "hybridBase": "משולב בבסיס", "dense": "סמנטי"}
KINDS = {"target": "עם יעד", "need": "צורך", "typo": "שגיאת הקלדה"}


def kind(q) -> str:
    return "typo" if q["intent"] == "Y" else "target" if q["target_line"] else "need"


def originals(queries):
    """Each typo query's id -> the id of the query it copies."""
    out = {}
    for qid, q in queries.items():
        if q["intent"] != "Y":
            continue
        same = [o for o, c in queries.items() if c["intent"] != "Y" and (
            c["target_line"] == q["target_line"] if q["target_line"]
            else not c["target_line"] and (c["family"], c["target_ref"]) == (q["family"], q["target_ref"]))]
        if len(same) == 1:
            out[qid] = same[0]
    return out


def main() -> None:
    queries = {int(r["id"][3:]): r for r in csv.DictReader(open(sys.argv[1], encoding="utf-8"), delimiter="\t",
                                                          quoting=csv.QUOTE_NONE)}
    rows = {r["id"]: r for r in map(json.loads, open(sys.argv[2], encoding="utf-8"))}
    grades = load_grades(sys.argv[3])
    best = defaultdict(dict)
    stats = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for qid, r in sorted(rows.items()):
        if qid not in grades:
            continue
        g = grades[qid]
        ideal = dcg(sorted(g.values(), reverse=True)[:3])
        q = queries[qid]
        for e in ENGINES:
            top = [g.get(h["lineId"], 0) for h in r[e][:3]]
            best[qid][e] = max(top, default=0)
            for key in ("all", kind(q), "style " + q["intent"], "family " + q["family"]):
                s = stats[key][e]
                s["first"].append(bool(top) and top[0] >= 2)
                s["three"].append(best[qid][e] >= 2)
                s["source"].append(best[qid][e] == 3)
                if ideal:
                    s["ndcg"].append(dcg(top) / ideal)
    print(f"{len(best)} queries judged\n")
    print("| | " + " | ".join(f"{e}: first good / good in 3 / source in 3 / nDCG@3" for e in ENGINES) + " |")
    print("|---|" + "---|" * len(ENGINES))
    order = ["all", *KINDS, *sorted(k for k in stats if k.startswith("style ")),
             *sorted(k for k in stats if k.startswith("family "))]
    for key in order:
        if key not in stats:
            continue
        cells = []
        for e in ENGINES:
            s = stats[key][e]
            nd = f"{sum(s['ndcg']) / len(s['ndcg']):.3f}" if s["ndcg"] else "-"
            cells.append(f"{sum(s['first'])} / {sum(s['three'])} / {sum(s['source'])} / {nd}")
        print(f"| {key} ({len(stats[key]['hybrid']['first'])}) | " + " | ".join(cells) + " |")
    for a, b in (("hybrid", "lexical"), ("hybridBase", "hybrid")):
        won = [q for q, x in best.items() if x[a] >= 2 > x[b]]
        lost = [q for q, x in best.items() if x[b] >= 2 > x[a]]
        print(f"\n{a} good in its first three where {b} not: {len(won)} {won}")
        print(f"{b} good where {a} not: {len(lost)} {lost}")
    neither = [q for q, x in best.items() if all(x[e] < 2 for e in ENGINES)]
    print(f"\nno engine good in its first three: {len(neither)} {neither}")
    copies = originals(queries)
    print("\ntypos, the best grade in the first three of the original -> of the copy:")
    for e in ENGINES:
        pairs = [(best[o][e], best[t][e]) for t, o in sorted(copies.items()) if t in best and o in best]
        kept = sum(1 for o, t in pairs if o >= 2 and t >= 2)
        lost = sum(1 for o, t in pairs if o >= 2 > t)
        good = sum(1 for o, _ in pairs if o >= 2)
        print(f"  {e}: good on the original {good}/{len(pairs)}, kept by the copy {kept}, lost {lost}")
    if "--md" in sys.argv:
        names = " | ".join(NAMES[e] for e in ENGINES)
        print(f"\n| # | סוג | סגנון | שאלה | היקף | {names} | הראשון במשולב | ציון |")
        print("|---|---|---|---|---|" + "---|" * len(ENGINES) + "---|---|")
        for qid in sorted(best):
            r, q = rows[qid], queries[qid]
            first = r["hybrid"][0] if r["hybrid"] else None
            cells = " | ".join(str(best[qid][e]) if r[e] else "—" for e in ENGINES)
            ref = (first["heRef"] or first["book"]) if first else ""
            grade = str(grades[qid].get(first["lineId"], 0)) if first else ""
            print(f"| {qid} | {KINDS[kind(q)]} | {q['intent']} | {q['query']} | {r['scope']} | {cells} | {ref} | "
                  f"{grade} |")


if __name__ == "__main__":
    main()

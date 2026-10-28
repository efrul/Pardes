"""How each engine answers the real searches, by the graded pool: the first result good (grade 2 or more), a good one
in the first three, the source (grade 3) in the first three, nDCG@3.

    python score.py results.jsonl queries.tsv grades.tsv [--md]

nDCG@3: gain 2^grade - 1, the ideal from all the lines judged for the search (so it moves when more lines are judged;
compare engines on the same grades). A line never judged counts 0. The noise searches (NOISE) are left out.
--md prints the per-search table of results-full-student.md: each engine's best grade in its first three, and the
hybrid's first result by its ref only (no text).
"""

import csv
import json
import math
import sys
from collections import defaultdict

ENGINES = ("lexical", "hybrid", "hybridBase", "dense")
NAMES = {"lexical": "רגיל", "hybrid": "משולב", "hybridBase": "משולב בבסיס", "dense": "סמנטי"}
NOISE = {75, 76}  # "asdfgh qwerty zxcv", "מחיר דירה בתל אביב"


def dcg(grades):
    return sum((2 ** g - 1) / math.log2(i + 2) for i, g in enumerate(grades))


def load_grades(path):
    grades = defaultdict(dict)
    for r in csv.DictReader(open(path, encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE):
        grades[int(r["qid"])][int(r["lineId"])] = int(r["grade"])
    return grades


def main() -> None:
    rows = {r["id"]: r for r in map(json.loads, open(sys.argv[1], encoding="utf-8"))}
    queries = {int(r["id"]): r for r in csv.DictReader(open(sys.argv[2], encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE)}
    grades = load_grades(sys.argv[3])
    best = defaultdict(dict)
    stats = defaultdict(lambda: defaultdict(list))
    for qid, r in rows.items():
        if qid in NOISE:
            continue
        g = grades[qid]
        ideal = dcg(sorted(g.values(), reverse=True)[:3])
        for e in ENGINES:
            top = [g.get(h["lineId"], 0) for h in r[e][:3]]
            best[qid][e] = max(top, default=0)
            stats[e]["first"].append(bool(top) and top[0] >= 2)
            stats[e]["three"].append(best[qid][e] >= 2)
            stats[e]["source"].append(best[qid][e] == 3)
            if ideal:
                stats[e]["ndcg"].append(dcg(top) / ideal)
    print(f"{len(best)} searches (noise left out)\n")
    print("| engine | first result good | good in the first 3 | source in the first 3 | nDCG@3 |")
    print("|---|---|---|---|---|")
    for e in ENGINES:
        s = stats[e]
        print(f"| {e} | {sum(s['first'])} | {sum(s['three'])} | {sum(s['source'])} | {sum(s['ndcg']) / len(s['ndcg']):.3f} |")
    won = [q for q, b in best.items() if b["hybrid"] >= 2 > b["lexical"]]
    lost = [q for q, b in best.items() if b["lexical"] >= 2 > b["hybrid"]]
    neither = [q for q, b in best.items() if b["lexical"] < 2 and b["hybrid"] < 2]
    print(f"\nhybrid good where lexical not: {len(won)} {won}\nlexical good where hybrid not: {len(lost)} {lost}")
    print(f"neither good in its first three: {len(neither)}")
    base_won = [q for q, b in best.items() if b["hybridBase"] >= 2 > b["hybrid"]]
    base_lost = [q for q, b in best.items() if b["hybrid"] >= 2 > b["hybridBase"]]
    print(f"base books only better: {base_won}; worse: {base_lost}")
    if "--md" in sys.argv:
        print("\n| # | חיפוש | הספר שבו חופש | " + " | ".join(NAMES[e] for e in ENGINES) + " | הראשון במשולב | ציון |")
        print("|---|---|---|" + "---|" * len(ENGINES) + "---|---|")
        for qid in sorted(rows):
            r = rows[qid]
            first = r["hybrid"][0] if r["hybrid"] else None
            if qid in NOISE:
                cells, grade = ["רעש"] * len(ENGINES), ""
            else:
                cells = [str(best[qid][e]) if r[e] else "—" for e in ENGINES]
                grade = str(grades[qid].get(first["lineId"], 0)) if first else ""
            ref = (first["heRef"] or first["book"]) if first else ""
            print(f"| {qid} | {r['query']} | {queries[qid]['hint_book']} | " + " | ".join(cells) + f" | {ref} | {grade} |")


if __name__ == "__main__":
    main()

"""eval-real (pardes-semantic/eval-real): each method's first 3 against the graded lines, on the 153 searches that are
not noise (IMPLEMENTATION.md).

    python evalreal_score.py <pardes-semantic>/eval-real OUT_DIR [more_grades.tsv ...]

OUT_DIR is fuse_all.py's output for eval-real's searches (their ids are eval-real's qid). The grades are eval-real's
grades.tsv and the files given (same columns: qid, lineId, grade, ref; ../eval_real/grades_new.tsv). For each method:
the first line good (grade 2 or 3), a good line in the first 3, nDCG@3 (gain 2^grade - 1 as graded_compare.py, the
ideal from all the search's grades, a line with no grade counts 0), the share of the first 3 that has a grade, and the
sign test on nDCG@3 against `apprrf` (the app before IMPLEMENTATION.md).
"""

import csv
import json
import math
import sys
from pathlib import Path

NOISE = {75, 76}
er, out = Path(sys.argv[1]), Path(sys.argv[2])
grades = {}
for f in [er / "grades.tsv"] + [Path(p) for p in sys.argv[3:]]:
    for r in csv.DictReader(open(f, encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE):
        grades.setdefault(int(r["qid"]), {})[int(r["lineId"])] = int(r["grade"])
o = json.load(open(out / "orderings.json", encoding="utf-8"))
methods = [m[0] for m in o["methods"]]
searches = [s for s in o["searches"] if s["id"] not in NOISE]


def dcg(gs):
    return sum((2 ** g - 1) / math.log2(i + 2) for i, g in enumerate(gs))


def sign_p(w, l):
    n = w + l
    return 1.0 if n == 0 else min(1.0, 2 * sum(math.comb(n, i) for i in range(min(w, l) + 1)) / 2 ** n)


rows = {}
for m in methods:
    first = in3 = graded = shown = 0
    nd = []
    for s in searches:
        known = grades.get(s["id"], {})
        top = s["tops"].get(m, [])[:3]
        first += bool(top) and known.get(top[0], 0) >= 2
        in3 += any(known.get(d, 0) >= 2 for d in top)
        graded += sum(d in known for d in top)
        shown += len(top)
        ideal = dcg(sorted(known.values(), reverse=True)[:3])
        nd.append(dcg([known.get(d, 0) for d in top]) / ideal if ideal else 0.0)
    rows[m] = (first, in3, sum(nd) / len(nd), graded / max(shown, 1), nd)

base = rows["apprrf"][4]
print(f"{len(searches)} searches (noise {sorted(NOISE)} left out), {sum(len(v) for v in grades.values())} grades")
print(f"{'method':12} {'first':>6} {'in 3':>6} {'nDCG@3':>7} {'graded':>7}  vs apprrf (better/worse, p)")
for m in sorted(methods, key=lambda m: -rows[m][2]):
    first, in3, mean, cov, nd = rows[m]
    w = sum(a > b + 1e-12 for a, b in zip(nd, base))
    l = sum(a < b - 1e-12 for a, b in zip(nd, base))
    print(f"{m:12} {first:6} {in3:6} {mean:7.3f} {cov:7.0%}  {w:3}/{l:<3} p={sign_p(w, l):.3f}")

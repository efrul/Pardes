"""The feedback's graded searches, every lexical side and method side by side (OTZARIA_LEXICAL.md, ANALYSIS.md "after
grading").

    python graded_compare.py grades.json BASE_OUT [LABEL=OUT ...] [--methods app,p12wl]

grades.json is feedback_searches.py's (the feedback's grades, and claude_grades.tsv's where the feedback has none).
BASE_OUT is fuse_all.py's output for the app's own run, LABEL=OUT those of runs with another lexical side
(swap_lexical.py). The searches measured are those of BASE_OUT with a grade from the feedback on a line in the first 50
of any method (89 on the feedback of 8.11.2026). For every run and method: the first line good (grade 2 or 3), good
lines in the first 3, lines graded 0 in the first 3, nDCG@3 (a line with no grade counts 0), MRR@10, the share of the
first 3 that has a grade, and the sign test against the base run's `app`. Last, the lines in the first 3 that have no
grade: to grade before trusting a row whose share is under 100%.
"""

import json
import math
import statistics
import sys

args = [a for a in sys.argv[1:] if not a.startswith("--")]
methods = sys.argv[sys.argv.index("--methods") + 1].split(",") if "--methods" in sys.argv else ["app", "p12wl"]
if "--methods" in sys.argv:
    args.remove(sys.argv[sys.argv.index("--methods") + 1])
grades = json.load(open(args[0], encoding="utf-8"))
runs = [("pardes", args[1])] + [tuple(a.split("=", 1)) for a in args[2:]]


def grade(q, d):
    x = grades.get(f"{q}\t{d}")
    return None if x is None else x["grade"]


def dcg(v):
    return sum((2 ** a - 1) / math.log2(i + 2) for i, a in enumerate(v))


def sign_p(w, l):
    n = w + l
    return 1.0 if n == 0 else min(1.0, 2 * sum(math.comb(n, i) for i in range(min(w, l) + 1)) / 2 ** n)


by_query = {}
for k, v in grades.items():
    by_query.setdefault(k.split("\t")[0], []).append(v["grade"])
base_full = json.load(open(f"{runs[0][1]}/tops50.json", encoding="utf-8"))
measured = {x["id"] for x in base_full
            if any(f"{x['q']}\t{d}" in grades and grades[f"{x['q']}\t{d}"].get("by") != "claude"
                   for t in x["tops"].values() for d in t)}
print(f"{len(measured)} searches with grades from the feedback; grades: "
      f"feedback {sum(1 for v in grades.values() if v.get('by') != 'claude')}, Claude {sum(1 for v in grades.values() if v.get('by') == 'claude')}")
base_app, unjudged = None, {}
for label, out in runs:
    full = [x for x in json.load(open(f"{out}/tops50.json", encoding="utf-8")) if x["id"] in measured]
    for m in methods:
        first = good3 = zero3 = judged3 = 0
        nd, rr = {}, []
        for x in full:
            q, order = x["q"], x["tops"][m]
            top = [grade(q, d) for d in order[:3]]
            for d in order[:3]:
                if grade(q, d) is None:
                    unjudged.setdefault((q, d), set()).add(f"{label}/{m}")
            judged3 += sum(v is not None for v in top)
            first += bool(top and (top[0] or 0) >= 2)
            good3 += sum((v or 0) >= 2 for v in top)
            zero3 += sum(v == 0 for v in top)
            ideal = dcg(sorted(by_query.get(q, []), reverse=True)[:3]) or 1e-9
            nd[x["id"]] = dcg([v or 0 for v in top]) / ideal
            at = next((i for i, d in enumerate(order[:10]) if (grade(q, d) or 0) >= 2), None)
            rr.append(0 if at is None else 1 / (at + 1))
        if base_app is None:
            base_app = nd
        w = sum(nd[i] > base_app[i] + 1e-9 for i in nd)
        l = sum(nd[i] < base_app[i] - 1e-9 for i in nd)
        print(f"{label:11} {m:8} graded in 3 {judged3 / (3 * len(full)):4.0%} | good first {first:2} | good in 3 {good3:3} | "
              f"0 in 3 {zero3:3} | nDCG@3 {statistics.fmean(nd.values()):.3f} | MRR@10 {statistics.fmean(rr):.3f} | "
              f"vs the app +{w} -{l} p={sign_p(w, l):.3f}")
print(f"\n{len(unjudged)} lines in the first 3 with no grade" + (":" if unjudged else ""))
for (q, d), where in sorted(unjudged.items()):
    print(f"  {q}\t{d}\t{', '.join(sorted(where))}")

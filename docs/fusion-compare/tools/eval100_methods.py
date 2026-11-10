"""eval-100 (pardes-semantic/eval-100: the expected source's heRef patterns, expected.py) over fuse_all.py's orderings:
for each method, the source first, in the first 3, in the first 10 and MRR@10, exact / same siman, chapter or daf
(score.py's relaxed) (IMPLEMENTATION.md).

    python eval100_methods.py <pardes-semantic>/eval-100 OUT_DIR [method,...]
"""

import json
import pickle
import re
import sys
from pathlib import Path

e100, out = Path(sys.argv[1]), Path(sys.argv[2])
sys.path.insert(0, str(e100))
from expected import E  # noqa: E402
from score import relaxed  # noqa: E402

o = json.load(open(out / "orderings.json", encoding="utf-8"))
lines = pickle.load(open(out / "lines.pkl", "rb"))  # fuse_all.py's: {lineId: (bookId, heRef, content)}
methods = sys.argv[3].split(",") if len(sys.argv) > 3 else [m[0] for m in o["methods"]]


def place(top, pats):
    for i, d in enumerate(top[:10]):
        ref = lines.get(d, (0, "", ""))[1] or ""
        if any(re.search(p, ref) for p in pats):
            return i + 1
    return None


def summary(places):
    return (sum(p == 1 for p in places), sum(bool(p) and p <= 3 for p in places), sum(bool(p) for p in places),
            sum(1 / p for p in places if p) / len(places))


rows = []
for m in methods:
    exact, loose = [], []
    for s in o["searches"]:
        if s["id"] in E:
            top = s["tops"].get(m, [])
            exact.append(place(top, E[s["id"]]))
            loose.append(place(top, relaxed(E[s["id"]])))
    rows.append((m, summary(exact), summary(loose), len(exact)))
print(f"{rows[0][3]} queries with a source; exact / same siman")
print(f"{'method':12} {'first':>9} {'in 3':>9} {'in 10':>9} {'MRR@10':>13}")
for m, a, b, _ in sorted(rows, key=lambda r: -r[1][3]):
    print(f"{m:12} {a[0]:4}/{b[0]:<4} {a[1]:4}/{b[1]:<4} {a[2]:4}/{b[2]:<4} {a[3]:6.3f}/{b[3]:.3f}")

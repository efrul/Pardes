"""Where every ordering puts a search's source: the measure of the "where is it written" test (SOURCE_TEST.md).

    PARDES_SEMANTIC=<pardes-semantic checkout> python source_eval.py OUT_DIR targets.json seforim.db [--lex-top 10] [--sample 100]
        [--write queries.tsv]

OUT_DIR holds fuse_all.py's tops50.json and lines.pkl for the test's searches; targets.json gives each search's source
line ({id: {"line": lineId, "group", "type", "q", ...}}). A line with the source's very text (verbatimKey) counts as
the source too: a verse or a halacha the library holds twice. The test keeps the searches whose source the lexical
side finds among its first --lex-top (10): searches for a source that the words find. With --sample, that many of
them at random (seed 5785), as many from each kind of book as their share.

Prints, for every method: the source first / in the first 3 / in the first 10 / MRR@10, how many it loses (in the
lexical side's first 3 but not in the method's first 10), and the same by the source's kind of book and the query's
kind (Q a quote, V a quote with a change, K key words). Strict: the source line (or its very text); lenient: also a
line holding the query (holds_quote or holds_verbatim) in a book of an earlier level, or for a Mishnah its copy in
the Bavli, the Yerushalmi or the Tosefta. Last, apart, the searches whose source the lexical side does not find in its
first --lex-top (most of them queries with a change), with where every method puts it.
"""

import json
import os
import pickle
import sys
from collections import defaultdict
from pathlib import Path

SM = Path(os.environ.get("PARDES_SEMANTIC", Path(__file__).resolve().parents[4] / "pardes-semantic"))
sys.path.insert(0, str(SM / "search-order"))
from order_common import holds_quote, holds_verbatim, verbatim_key  # noqa: E402
sys.path.insert(0, str(SM / "eval-real"))
from level_order import ORDER, book_levels  # noqa: E402
import random  # noqa: E402
import sqlite3  # noqa: E402

out = Path(sys.argv[1])
targets = json.load(open(sys.argv[2], encoding="utf-8"))
con = sqlite3.connect(f"file:{sys.argv[3]}?mode=ro", uri=True)
level_of, about = book_levels(con)
sample = int(sys.argv[sys.argv.index("--sample") + 1]) if "--sample" in sys.argv else None
lex_top = int(sys.argv[sys.argv.index("--lex-top") + 1]) if "--lex-top" in sys.argv else 10
full = json.load(open(out / "tops50.json", encoding="utf-8"))
lines = pickle.load(open(out / "lines.pkl", "rb"))
keys = {}


def key(d):
    if d not in keys:
        keys[d] = verbatim_key(lines[d][2]) if d in lines else None
    return keys[d]


def rank(order, target_key, target):
    for i, d in enumerate(order):
        if d == target or (target_key and key(d) == target_key):
            return i + 1
    return None


CHAZAL_COPIES = ("תלמוד > בבלי", "תלמוד > ירושלמי", "תוספתא")


def lenient_rank(order, target_key, target, q, group):
    tl = ORDER.get(level_of.get(lines[target][0], "לא ידוע"), 4) if target in lines else 9
    for i, d in enumerate(order):
        if d == target or (target_key and key(d) == target_key):
            return i + 1
        if d not in lines:
            continue
        text = lines[d][2]
        if not (holds_verbatim(text, q) or holds_quote(text, q)):
            continue
        b = lines[d][0]
        if ORDER.get(level_of.get(b, "לא ידוע"), 4) < tl:
            return i + 1
        if group == "mishna" and any(about[b][1].startswith(c) for c in CHAZAL_COPIES) and " על " not in about[b][0]:
            return i + 1
    return None


rows, kept = [], []
for x in full:
    t = targets[str(x["id"])]
    tk = key(t["line"]) if t["line"] in lines else None
    lr = rank(x["lex"], tk, t["line"])
    dr = rank(x["dense"], tk, t["line"])
    rows.append((x, t, tk, lr, dr))
    if lr is not None and lr <= lex_top:
        kept.append((x, t, tk, lr, dr))

print(f"{len(full)} searches written; the lexical side finds the source in its first {lex_top} in {len(kept)}")
if sample and sample < len(kept):
    rnd = random.Random(5785)
    by = defaultdict(list)
    for r in kept:
        by[r[1]["group"]].append(r)
    share = {g: round(sample * len(v) / len(kept)) for g, v in by.items()}
    while sum(share.values()) > sample:
        share[max(share, key=share.get)] -= 1
    while sum(share.values()) < sample:
        share[max(by, key=lambda g: len(by[g]) - share[g])] += 1
    kept = [r for g, v in by.items() for r in rnd.sample(v, share[g])]
    kept.sort(key=lambda r: r[0]["id"])
    print(f"  of them {len(kept)} at random: " + ", ".join(f"{g} {share[g]}" for g in sorted(share)))
if "--write" in sys.argv:  # every search with its lexical and dense rank, and whether it is in the measured set
    measured = {r[0]["id"] for r in kept}
    with open(sys.argv[sys.argv.index("--write") + 1], "w", encoding="utf-8") as f:
        f.write("id\tgroup\ttype\tquery\tlineId\tbook\tref\tlexical_rank\tdense_rank\tmeasured\n")
        for x, t, tk, lr, dr in rows:
            f.write(f"{x['id']}\t{t['group']}\t{t['type']}\t{t['q']}\t{t['line']}\t{t['book']}\t{t['ref']}\t{lr or ''}\t{dr or ''}\t"
                    f"{1 if x['id'] in measured else 0}\n")
lexr = [r[3] for r in kept]
denr = [r[4] for r in kept]
print(f"  lexical rank 1: {sum(1 for r in lexr if r == 1)}, 2-3: {sum(1 for r in lexr if 2 <= r <= 3)}, 4-10: {sum(1 for r in lexr if r >= 4)}")
print(f"  the dense side: in its 150 {sum(1 for r in denr if r)}, first 10 {sum(1 for r in denr if r and r <= 10)}, first {sum(1 for r in denr if r == 1)}")

methods = list(full[0]["tops"])
res = {}
for m in methods:
    rs = [rank(x["tops"][m], tk, t["line"]) for x, t, tk, lr, dr in kept]
    lost = sum(1 for (x, t, tk, lr, dr), r in zip(kept, rs) if lr <= 3 and (r is None or r > 10))
    res[m] = rs
    first = sum(1 for r in rs if r == 1)
    in3 = sum(1 for r in rs if r and r <= 3)
    in10 = sum(1 for r in rs if r and r <= 10)
    mrr = sum(1 / r for r in rs if r and r <= 10) / len(rs)
    ls = [lenient_rank(x["tops"][m], tk, t["line"], t["q"], t["group"]) for x, t, tk, lr, dr in kept]
    res[m + "~"] = ls
    lfirst = sum(1 for r in ls if r == 1)
    lin3 = sum(1 for r in ls if r and r <= 3)
    print(f"{m:9}| first {first:3d} | in 3 {in3:3d} | in 10 {in10:3d} | MRR@10 {mrr:.3f} | lost {lost:2d} || lenient: first {lfirst:3d} | in 3 {lin3:3d}")

for label, field in (("the source's book", "group"), ("the query", "type")):
    print(f"\n== source first, by {label} ==")
    groups = defaultdict(list)
    for i, (x, t, tk, lr, dr) in enumerate(kept):
        groups[t[field]].append(i)
    sel = ["app", "lex", "dense", "rrf60", "rrf60w15", "dbsf", "combmnz", "otzaria", "p12", "p12w", "p12wl", "window", "combo"]
    sel = [m for m in sel if m in res]
    print("group (n)    | " + " | ".join(f"{m:8}" for m in sel) + "   (strict / lenient)")
    for g, ids in sorted(groups.items()):
        print(f"{g:8}({len(ids):3d}) | " + " | ".join(f"{sum(1 for i in ids if res[m][i] == 1):3d}/{sum(1 for i in ids if res[m + '~'][i] == 1):<4d}" for m in sel))

print("\n== the searches the app loses (the source in the lexical first 3, not in the app's first 10) ==")
for (x, t, tk, lr, dr), r in zip(kept, res["app"]):
    if lr <= 3 and (r is None or r > 10):
        others = {m: res[m][kept.index((x, t, tk, lr, dr))] for m in ("p12", "p12w", "p12wl", "dbsf", "window")}
        print(f"  [{t['group']}/{t['type']}] {t['q']}  ({t['ref']}): lexical {lr}, dense {dr}, app {r}; " +
              ", ".join(f"{m} {v}" for m, v in others.items()))

missed = [r for r in rows if r[3] is None or r[3] > lex_top]
print(f"\n== the {len(missed)} searches whose source the lexical side does not find in its first {lex_top} (not measured above) ==")
for m in methods:
    rs = [rank(x["tops"][m], tk, t["line"]) for x, t, tk, lr, dr in missed]
    print(f"{m:9}| first {sum(1 for r in rs if r == 1):2d} | in 3 {sum(1 for r in rs if r and r <= 3):2d} | in 10 {sum(1 for r in rs if r and r <= 10):2d}")

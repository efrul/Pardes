"""The numbers of ANALYSIS.md, from fuse_all.py's output.

    PARDES_SEMANTIC=<pardes-semantic checkout> python analyze.py OUT_DIR grades.json seforim.db

OUT_DIR holds fuse_all.py's tops50.json, lines.pkl and links.pkl. Prints:
1. what every method's first 10 are made of, over all the searches (no grades);
2. on the searches with grades in the feedback: P@1, nDCG@3 (an ungraded line counts 0), the share of the first 3 that
   is graded, the same on condensed lists (ungraded lines left out: Sakai's remedy for incomplete judgments), and the
   reciprocal rank of the first good line (grade 2 or 3) in the first 50; and each method against the app, search by
   search, with a two-sided sign test;
3. condensed nDCG@3 by the query's length and scope;
4. every method with the app's keepLeaders on top;
5. the searches where three methods gain or lose most against the app.
"""

import json
import math
import os
import pickle
import sqlite3
import statistics
import sys
from pathlib import Path

SM = Path(os.environ.get("PARDES_SEMANTIC", Path(__file__).resolve().parents[4] / "pardes-semantic"))
sys.path.insert(0, str(SM / "search-order"))
sys.path.insert(0, str(SM / "eval-real"))
from order_common import holds_quote, keep, verbatim_key  # noqa: E402
from level_order import book_levels  # noqa: E402

out, grades_p, dbp = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
full = json.load(open(out / "tops50.json", encoding="utf-8"))
grades = json.load(open(grades_p, encoding="utf-8"))
lines = pickle.load(open(out / "lines.pkl", "rb"))
con = sqlite3.connect(f"file:{dbp}?mode=ro", uri=True)
base = dict(con.execute("SELECT id, isBaseBook FROM book"))
level_of, _ = book_levels(con)
M = list(full[0]["tops"])
by_query = {}
for k, v in grades.items():
    by_query.setdefault(k.split("\t")[0], []).append(v["grade"])
nwords = {}


def words(d):
    if d not in nwords:
        nwords[d] = len(verbatim_key(lines[d][2]).split())
    return nwords[d]


def g(q, d):
    x = grades.get(f"{q}\t{d}")
    return None if x is None else x["grade"]


def dcg(gains):
    return sum((2 ** v - 1) / math.log2(i + 2) for i, v in enumerate(gains))


def sign_p(w, l):
    n = w + l
    if n == 0:
        return 1.0
    return min(1.0, 2 * sum(math.comb(n, i) for i in range(min(w, l) + 1)) / 2 ** n)


print("== 1. the first 10, over all", len(full), "searches ==")
print("method   | same as app | lex only | dense only | both | <=8 words | >300 words | base books | Mikra+Chazal | books")
for m in M:
    same = lo = do = bo = sh = lg = bb = early = nb = n = 0
    for x in full:
        top = x["tops"][m][:10]
        L, D = set(x["lex"]), set(x["dense"])
        same += len(set(x["tops"]["app"][:10]) & set(top))
        nb += len({lines[d][0] for d in top})
        for d in top:
            n += 1
            lo += d in L and d not in D
            do += d in D and d not in L
            bo += d in L and d in D
            sh += words(d) <= 8
            lg += words(d) > 300
            bb += base.get(lines[d][0], 0) == 1
            early += level_of.get(lines[d][0]) in ("מקרא", 'חז"ל')
    k = len(full)
    print(f"{m:9}| {same / k:4.1f} | {lo / n:4.0%} | {do / n:4.0%} | {bo / n:4.0%} | {sh / n:4.0%} | {lg / n:4.0%} | {bb / n:4.0%} | {early / n:4.0%} | {nb / k:4.1f}")

gs = [x for x in full if any(g(x["q"], d) is not None for t in x["tops"].values() for d in t)]
print(f"\n== 2. the {len(gs)} searches with grades ==")


def measure(order, q):
    ideal = dcg(sorted(by_query.get(q, []), reverse=True)[:3]) or 1e-9
    top3 = [g(q, d) for d in order[:3]]
    cond = [g(q, d) for d in order if g(q, d) is not None][:3]
    first = next((i for i, d in enumerate(order) if (g(q, d) or 0) >= 2), None)
    return {
        "p1": bool(top3 and top3[0] is not None and top3[0] >= 2),
        "ndcg": dcg([v or 0 for v in top3]) / ideal,
        "judged3": sum(v is not None for v in top3),
        "p1c": bool(cond and cond[0] >= 2),
        "ndcgc": dcg(cond) / ideal,
        "rr": 0 if first is None else 1 / (first + 1),
    }


res = {m: {x["id"]: measure(x["tops"][m], x["q"]) for x in gs} for m in M}


def line_of(m, r):
    v = list(r.values())
    return (f"P@1 {sum(e['p1'] for e in v):2d} | nDCG@3 {statistics.fmean(e['ndcg'] for e in v):.3f} | "
            f"graded in 3 {sum(e['judged3'] for e in v) / (3 * len(v)):4.0%} | condensed: P@1 {sum(e['p1c'] for e in v):2d} "
            f"nDCG@3 {statistics.fmean(e['ndcgc'] for e in v):.3f} | MRR@50 {statistics.fmean(e['rr'] for e in v):.3f}")


def versus(r):
    w = sum(r[i]["ndcgc"] > res["app"][i]["ndcgc"] + 1e-9 for i in r)
    l = sum(r[i]["ndcgc"] < res["app"][i]["ndcgc"] - 1e-9 for i in r)
    return f"vs app +{w} -{l} p={sign_p(w, l):.2f}"


for m in M:
    print(f"{m:9}| {line_of(m, res[m])} | {versus(res[m]) if m != 'app' else ''}")


def qlen(q):
    return len(verbatim_key(q).split())


print("\n== 3. condensed nDCG@3 by length and scope ==")
groups = {
    "1-2 words": lambda x: qlen(x["q"]) <= 2, "3-4 words": lambda x: 3 <= qlen(x["q"]) <= 4,
    "5+ words": lambda x: qlen(x["q"]) >= 5, "book": lambda x: x["scope"] == "book",
    "base/fallback": lambda x: x["scope"] in ("base", "fallback"), "all/category": lambda x: x["scope"] in ("all", "cat"),
}
sel = ["app", "dense", "rrf60w15", "dbsf", "combmnz", "otzaria", "cc_mm50", "p12", "window", "combo"]
print("group (n)          | " + " | ".join(f"{m:8}" for m in sel))
for name, f in groups.items():
    ids = [x["id"] for x in gs if f(x)]
    print(f"{name:14}({len(ids):2d}) | " + " | ".join(f"{statistics.fmean(res[m][i]['ndcgc'] for i in ids):8.3f}" for m in sel))


print("\n== 4. with the app's keepLeaders on top ==")


def with_leaders(x, order):
    lead = [(d, (1, 3)[j]) for j, d in enumerate(x["lex"][:2]) if holds_quote(lines[d][2], x["q"])]
    return keep(list(order), lead)[:50]


for m in ["rrf60", "rrf60w15", "rrf100", "cc_mm50", "cc_z50", "dbsf", "combmnz", "otzaria", "window", "links", "level", "p12", "combo"]:
    r = {x["id"]: measure(with_leaders(x, x["tops"][m]), x["q"]) for x in gs}
    print(f"{m + '+kl':12}| {line_of(m, r)} | {versus(r)}")


print("\n== 5. examples (rank, grade, reference of the first three graded lines) ==")
byid = {x["id"]: x for x in gs}


def graded3(q, order):
    out = []
    for rank, d in enumerate(order):
        if g(q, d) is not None:
            out.append((rank + 1, g(q, d), lines[d][1][:30]))
            if len(out) == 3:
                break
    return out


for m in ("dbsf", "window", "combo"):
    diffs = sorted((res[m][i]["ndcgc"] - res["app"][i]["ndcgc"], i) for i in res[m])
    for delta, i in diffs[-3:][::-1] + diffs[:3]:
        if abs(delta) < 1e-9:
            continue
        x = byid[i]
        print(f"  {m} {delta:+.2f} [{x['scope']}] {x['q'][:60]}\n     app: {graded3(x['q'], x['tops']['app'])}\n     {m}: {graded3(x['q'], x['tops'][m])}")

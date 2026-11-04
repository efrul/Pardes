"""The lexical side of Cands.kt (nodense) per dictionary variant: eval-100 (source in the first ten, MRR, same
siman/chapter/daf) and eval-real (a good line, grade 2 or more, first / in the first three, the source in the first
three, nDCG@3, and how many of the first three lines are not graded).

    python score.py <dir of c_<variant>_e100.jsonl and c_<variant>_real.jsonl> seforim.db <variant> [<variant> ...]
"""
import csv
import json
import math
import re
import sys
import sqlite3
from collections import defaultdict
from pathlib import Path

here = Path(__file__).resolve().parent
sys.path.insert(0, str(here.parent / "eval-100"))
from expected import E  # noqa: E402
from score import relaxed  # noqa: E402

out, dbp, variants = sys.argv[1], sys.argv[2], sys.argv[3:]
db = sqlite3.connect(f"file:{dbp}?mode=ro", uri=True)
ref = {}


def refs(ids):
    need = [i for i in ids if i not in ref]
    if need:
        for i, r in db.execute(f"select id, heRef from line where id in ({','.join('?' * len(need))})", need):
            ref[i] = r or ""
    return [ref.get(i, "") for i in ids]


G = defaultdict(dict)
for r in csv.DictReader(open(here.parent / "eval-real" / "grades.tsv", encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE):
    G[int(r["qid"])][int(r["lineId"])] = int(r["grade"])
NOISE = {75, 76}


def dcg(gs):
    return sum((2 ** g - 1) / math.log2(i + 2) for i, g in enumerate(gs))


print("| | eval-100 hit@10 | MRR | relaxed hit@10 | no result (both) | eval-real good@1 | good@3 | source@3 | nDCG@3 | not graded in top 3 |")
print("|---|---|---|---|---|---|---|---|---|---|")
for v in variants:
    e = [json.loads(line) for line in open(f"{out}/c_{v}_e100.jsonl", encoding="utf-8")]
    rr = [json.loads(line) for line in open(f"{out}/c_{v}_real.jsonl", encoding="utf-8")]
    hit = mrr = rel = n = empty = 0
    for r in e:
        empty += not r["lexCand"]
        if r["id"] not in E:
            continue
        n += 1
        hr = refs(r["lexCand"][:10])
        rk = next((i + 1 for i, h in enumerate(hr) if any(re.search(p, h) for p in E[r["id"]])), None)
        rl = next((i + 1 for i, h in enumerate(hr) if any(re.search(p, h) for p in relaxed(E[r["id"]]))), None)
        hit += bool(rk)
        mrr += 1 / rk if rk else 0
        rel += bool(rl)
    g1 = g3 = s3 = nd = unj = m = 0
    for r in rr:
        q = r["id"]
        if q in NOISE:
            continue
        empty += not r["lexCand"]
        m += 1
        top = r["lexCand"][:3]
        gs = [G[q].get(i, 0) for i in top]
        unj += sum(1 for i in top if i not in G[q])
        g1 += bool(gs and gs[0] >= 2)
        g3 += any(x >= 2 for x in gs)
        s3 += any(x == 3 for x in gs)
        ideal = dcg(sorted(G[q].values(), reverse=True)[:3])
        nd += dcg(gs) / ideal if ideal else 0
    print(f"| {v} | {hit}/{n} | {mrr / n:.3f} | {rel}/{n} | {empty} | {g1}/{m} | {g3}/{m} | {s3}/{m} | {nd / m:.3f} | {unj} |")

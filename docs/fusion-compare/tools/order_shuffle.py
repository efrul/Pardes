"""How much each side's order decides the app's ranking (EXPANSIONS.md): the app's fusion (fuse_all.py's apprrf: RRF
k=60, the dense side x3, keepLeaders, LevelPrecedence) recomputed with one side's candidates shuffled, reversed or
removed, against the app's own first 10.

    PARDES_SEMANTIC=<pardes-semantic checkout> python order_shuffle.py results.jsonl lines.pkl seforim.db [targets.json]

results.jsonl is FeedbackHistory.kt's run, lines.pkl fuse_all.py's cache of the lines (its OUT_DIR/lines.pkl), and
targets.json source_searches.py's: with it, how often the source comes first too. A shuffle is averaged over 3 seeds.
"""
import json, pickle, random, sqlite3, sys, os
from collections import defaultdict
from pathlib import Path
SM = Path(os.environ.get('PARDES_SEMANTIC', Path(__file__).resolve().parents[4] / 'pardes-semantic'))
sys.path.insert(0, str(SM / 'search-order')); sys.path.insert(0, str(SM / 'eval-real'))
from order_common import holds_quote, keep, verbatim_key
from level_order import ORDER, book_levels, holds_phrase
results, lines_p, db = sys.argv[1], sys.argv[2], sys.argv[3]
targets_p = sys.argv[4] if len(sys.argv) > 4 else None
con = sqlite3.connect(f'file:{db}?mode=ro', uri=True)
level_of, _ = book_levels(con)
lines = pickle.load(open(lines_p, 'rb'))
rows = [json.loads(l) for l in open(results)]
def rrf(lex, dense, k=60, w=3.0):
    sc = defaultdict(float)
    for i, d in enumerate(lex): sc[d] += 1.0 / (k + 1 + i)
    for i, d in enumerate(dense): sc[d] += w / (k + 1 + i)
    return sc
def ranked(sc): return [d for d, _ in sorted(sc.items(), key=lambda x: (-x[1], x[0]))]
def level_first(page, q):
    k = verbatim_key(q).split()
    if len(k) < 2: return page
    quoted = len(k) >= 3
    lv = lambda d: ORDER.get(level_of.get(lines[d][0], 'לא ידוע'), 4)
    holders = {d for d in page if d in lines and (quoted or lv(d) == 0) and holds_phrase(q, lines[d][2], spelling=quoted)}
    if not holders: return page
    place = {}
    for i, d in enumerate(page): place.setdefault(d, i)
    earliest = lambda d: (lv(d), place[d])
    top = list(page[:20]); slots = [i for i, d in enumerate(top) if d in holders]; out = page
    if slots:
        chosen = sorted(holders, key=earliest)[:len(slots)]
        left = [d for d in top if d in holders and d not in chosen]
        for j, i in enumerate(slots): top[i] = chosen[j]
        out = top + left + [d for d in page[20:] if d not in chosen]
    first = min(holders, key=earliest)
    if out[0] not in holders and lv(first) < lv(out[0]): out = [first] + [d for d in out if d != first]
    return out
def app(lex, dense, q):
    leaders = [(d, (1, 3)[j]) for j, d in enumerate(lex[:2]) if d in lines and holds_quote(lines[d][2], q)]
    return level_first(keep(ranked(rrf(lex, dense)), leaders), q)
targets = json.load(open(targets_p)) if targets_p else None
def key(d): return verbatim_key(lines[d][2]) if d in lines else None
def first_is_source(order, r):
    t = targets[str(r['id'])]['line']; tk = key(t) if t in lines else None
    return bool(order and (order[0] == t or (tk and key(order[0]) == tk)))
variants = {'as is': lambda l, d, rnd: (l, d), 'lexical shuffled': lambda l, d, rnd: (rnd.sample(l, len(l)), d),
            'lexical reversed': lambda l, d, rnd: (l[::-1], d), 'semantic shuffled': lambda l, d, rnd: (l, rnd.sample(d, len(d))),
            'lexical removed': lambda l, d, rnd: ([], d), 'semantic removed': lambda l, d, rnd: (l, [])}
base = {r['id']: app([h[0] for h in r['lexCand']], [h[0] for h in r['denseCand']], r['query']) for r in rows if r['lexCand'] or r['denseCand']}
for name, f in variants.items():
    ov, firsts, n = [], 0, 0
    for seed in range(3 if 'shuffled' in name else 1):
        rnd = random.Random(seed)
        for r in rows:
            if r['id'] not in base: continue
            l, d = f([h[0] for h in r['lexCand']], [h[0] for h in r['denseCand']], rnd)
            o = app(l, d, r['query'])
            ov.append(len(set(o[:10]) & set(base[r['id']][:10])) / max(1, min(10, len(base[r['id']]))))
            if targets: firsts += first_is_source(o, r); n += 1
    msg = f"{name:18} | same as the app in the first 10: {100 * sum(ov) / len(ov):5.1f}%"
    if targets: msg += f" | source first: {firsts / (n / len(base)):.1f} of {len(base)}"
    print(msg)

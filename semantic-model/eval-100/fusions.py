"""Other fusions of the same candidates, offline: the first 150 of each side that HybridSearchEngine fuses (lexCand,
denseCand in harness/Eval.kt's jsonl), RRF with other weights, and the lexical hits kept at given places.

    python fusions.py results.jsonl seforim.db

"In the pack's books": the Shulchan Aruch and the five tractates the trial pack's vector index holds.
"""
import json, re, sqlite3, sys
from collections import defaultdict
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent)); from expected import E
rows = [json.loads(l) for l in open(sys.argv[1], encoding="utf-8")]
con = sqlite3.connect(f"file:{sys.argv[2]}?mode=ro", uri=True)
ids = {i for r in rows for i in r["lexCand"] + r["denseCand"]}
ref = {}
for chunk in [list(ids)[k:k + 900] for k in range(0, len(ids), 900)]:
    ref.update(con.execute(f"SELECT id, heRef FROM line WHERE id IN ({','.join('?' * len(chunk))})", chunk).fetchall())
# The text of each query's first two lexical hits, for the word for word test of the app's keepLeaders
leads = list({i for r in rows for i in r["lexCand"][:2]})
text = {}
for chunk in [leads[k:k + 900] for k in range(0, len(leads), 900)]:
    text.update(con.execute(f"SELECT id, content FROM line WHERE id IN ({','.join('?' * len(chunk))})", chunk).fetchall())

# HybridSearchEngine.verbatimKey / quoteKey: letters and digits, single spaces; markup, niqqud, teamim, gershayim and
# quotes removed (a tag inside a word leaves it whole), maqaf and punctuation as spaces, final letters as base forms
_BLOCK, _TAG, _ENT = re.compile(r"<br\s*/?>|</p>|</div>", re.I), re.compile(r"<[^>]*>"), re.compile(r"&#?\w+;")
_MARKS = set(range(0x0591, 0x05BE)) | {0x05BF, 0x05C1, 0x05C2, 0x05C4, 0x05C5, 0x05C7}
_FIN = str.maketrans("ךםןףץ", "כמנפצ")
def verbatim_key(s):
    s = _ENT.sub(" ", _TAG.sub("", _BLOCK.sub(" ", s)))
    out, space = [], True
    for c in s:
        if ord(c) in _MARKS or c in "\u05F3\u05F4\"'":
            continue
        if c.isalnum():
            out.append(c.lower().translate(_FIN)); space = False
        elif not space:
            out.append(" "); space = True
    return "".join(out).rstrip()
def quote_key(q):
    k = verbatim_key(q)
    return k if k and k.count(" ") + 1 >= 2 else None

# HybridSearchEngine.holdsQuote: all the query's words, up to two one-letter prefixes off words of more than 3 letters,
# within a window of the query's length plus 2 words, in any order
_PREFIXES = "והבכלמש"
def stem(w):
    for _ in range(2):
        if len(w) > 3 and w[0] in _PREFIXES:
            w = w[1:]
    return w
def holds_quote(t, query):
    k = quote_key(query)
    if not k:
        return False
    words = [stem(w) for w in k.split()]
    wanted, line, window = set(words), [stem(w) for w in verbatim_key(t).split()], len(words) + 2
    return any(line[i] in wanted and wanted <= set(line[i:i + window]) for i in range(len(line)))
COVER = re.compile(r"^(שולחן ערוך|ברכות |שבת |בבא קמא |בבא מציעא |סנהדרין )")
cov = {i for i, p in E.items() if any(COVER.search(x.lstrip("^")) for x in p)}

def rrf(lex, den, w):
    s = defaultdict(float)
    for r, d in enumerate(lex): s[d] += 1 / (61 + r)
    for r, d in enumerate(den): s[d] += w / (61 + r)
    return [d for d, _ in sorted(s.items(), key=lambda x: -x[1])]

def keep(fused, leaders):
    # HybridSearchEngine.keepLeaders: each leader (in order, with its place, 0-based) moved up to that place, or left
    # where the fusion ranks it higher; one whose place an earlier one took comes right after it
    place_of = {}
    for d, wanted in leaders:
        at = fused.index(d) if d in fused else -1
        p = at if 0 <= at <= wanted else wanted
        while p in place_of.values():
            p += 1
        place_of[d] = p
    out = [d for d in fused if d not in place_of]
    for d, p in sorted(place_of.items(), key=lambda x: x[1]):
        out.insert(min(p, len(out)), d)
    return out[:10]

def pin(lex, fused, k, places=(0, 2)):
    # the first k lexical hits kept, whatever they hold
    return keep(fused, list(zip(lex[:k], places)))

def pin_verbatim(query, lex, fused, places=(1, 3)):
    # only those of the first two lexical hits that hold the query word for word (the app until 2026-10-26, 20:00)
    q = quote_key(query)
    leaders = [(d, places[j]) for j, d in enumerate(lex[:2]) if q and q in verbatim_key(text.get(d) or "")]
    return keep(fused, leaders)

def pin_quotes(query, lex, fused, places=(1, 3)):
    # the app: only those of the first two lexical hits that hold the query as a quote (holds_quote)
    leaders = [(d, places[j]) for j, d in enumerate(lex[:2]) if holds_quote(text.get(d) or "", query)]
    return keep(fused, leaders)

S_ = {
    "lexical": lambda r: r["lexCand"],
    "dense": lambda r: r["denseCand"],
    "rrf w1": lambda r: rrf(r["lexCand"], r["denseCand"], 1),
    "rrf w2": lambda r: rrf(r["lexCand"], r["denseCand"], 2),
    "rrf w3": lambda r: rrf(r["lexCand"], r["denseCand"], 3),
    "w3 + lexical 1,2 at places 1,3": lambda r: pin(r["lexCand"], rrf(r["lexCand"], r["denseCand"], 3), 2, (0, 2)),
    "w3 + lexical 1,2 at places 2,4": lambda r: pin(r["lexCand"], rrf(r["lexCand"], r["denseCand"], 3), 2, (1, 3)),
    "w3 + lexical 1,2 at places 2,4 when word for word": lambda r: pin_verbatim(r["query"], r["lexCand"], rrf(r["lexCand"], r["denseCand"], 3)),
    "w3 + lexical 1,2 at places 2,4 when a quote (the app)": lambda r: pin_quotes(r["query"], r["lexCand"], rrf(r["lexCand"], r["denseCand"], 3)),
    "w3 + lexical 1,2 at places 3,6": lambda r: pin(r["lexCand"], rrf(r["lexCand"], r["denseCand"], 3), 2, (2, 5)),
}
def rank(lst, pats):
    for i, d in enumerate(lst[:10]):
        if any(re.search(p, ref.get(d) or "") for p in pats): return i + 1
# sanity: the app's hybrid page against the fusions of its candidates (keepLeaders of lexical hits holding the query as a
# quote since 2026-10-26 at 20:00, word for word just before, the first two whatever they held that morning, rrf w3
# before); quoted queries differ, the app searching them by the words alone
for name in ("rrf w3", "w3 + lexical 1,2 at places 2,4", "w3 + lexical 1,2 at places 2,4 when word for word",
             "w3 + lexical 1,2 at places 2,4 when a quote (the app)"):
    same = sum(1 for r in rows if [h["lineId"] for h in r["hybrid"]] == S_[name](r)[:len(r["hybrid"])])
    print(f"app hybrid == {name} offline on {same}/{len(rows)} queries")
print()
print("| fusion | all 95: hit@10 / first | in the pack's books (44) | outside (51) | exact quotes F (10) |")
print("|---|---|---|---|---|")
for name, f in S_.items():
    res = {r["id"]: rank(f(r), E[r["id"]]) for r in rows if r["id"] in E}
    def c(sub): 
        v = [res[i] for i in sub]; return f"{sum(1 for x in v if x)}/{len(v)} / {sum(1 for x in v if x == 1)}"
    print(f"| {name} | {c(list(res))} | {c([i for i in res if i in cov])} | {c([i for i in res if i not in cov])} | {c([i for i in res if 76 <= i <= 85])} |")
# dense top score: real queries vs noise
tops = {r["id"]: (r["denseScores"][0] if r["denseScores"] else None) for r in rows}
print("\ndense top score (Lucene COSINE, (1+cos)/2): noise", [round(tops[i], 3) for i in range(96, 101)])
real = sorted(round(tops[i], 3) for i in E if tops[i] is not None)
print("real queries: min", real[0], "p10", real[len(real) // 10], "median", real[len(real) // 2])

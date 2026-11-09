"""Every ordering of research note SEARCH_FUSION_RESEARCH.md over the feedback's searches, from the candidates
FeedbackHistory.kt wrote (the app's hybrid page, and the 150 of each side with their scores).

    PARDES_SEMANTIC=<pardes-semantic checkout> python fuse_all.py results.jsonl seforim.db searches.tsv search_meta.json grades.json out_dir

Writes out_dir/orderings.json (every method's first 10 for every search, with what the page needs),
out_dir/orderings.tsv (one row per search, method and place) and out_dir/tops50.json (every method's first 50, for
analyze.py).
"""

import json
import math
import os
import pickle
import re
import sqlite3
import statistics
import sys
import html as htmlmod
from collections import defaultdict
from pathlib import Path

SM = Path(os.environ.get("PARDES_SEMANTIC", Path(__file__).resolve().parents[4] / "pardes-semantic"))  # a checkout of efrul/pardes-semantic
sys.path.insert(0, str(SM / "search-order"))
sys.path.insert(0, str(SM / "eval-real"))
from order_common import holds_quote, holds_verbatim, keep, stem, verbatim_key  # noqa: E402
from level_order import ORDER, book_levels, holds_phrase  # noqa: E402

results, dbp, searches_tsv, meta_p, grades_p, out = sys.argv[1:7]
out = Path(out)
out.mkdir(parents=True, exist_ok=True)
con = sqlite3.connect(f"file:{dbp}?mode=ro", uri=True)

rows = [json.loads(l) for l in open(results, encoding="utf-8")]
meta = json.load(open(meta_p, encoding="utf-8"))
grades = json.load(open(grades_p, encoding="utf-8"))

# ---------------------------------------------------------------- the library
books = {}
for b, title, base, order, cat in con.execute("SELECT id, title, isBaseBook, orderIndex, categoryId FROM book"):
    books[b] = (title, base, order, cat)
cat_title = dict(con.execute("SELECT id, title FROM category"))
level_of, _about = book_levels(con)

ids_needed = set()
for r in rows:
    for h in r["lexCand"] + r["denseCand"] + r["hybrid"]:
        ids_needed.add(h[0])
cache = out / "lines.pkl"
lines = pickle.load(open(cache, "rb")) if cache.exists() else {}
missing = sorted(ids_needed - set(lines))
print(f"lines: {len(ids_needed)} needed, {len(missing)} to read", flush=True)
for i in range(0, len(missing), 900):
    chunk = missing[i:i + 900]
    for d, b, h, c in con.execute(f"SELECT id, bookId, heRef, content FROM line WHERE id IN ({','.join('?' * len(chunk))})", chunk):
        lines[d] = (b, h or "", c)
if missing:
    pickle.dump(lines, open(cache, "wb"))

# links: distinct books on the other side of a link, both directions
lcache = out / "links.pkl"
links = pickle.load(open(lcache, "rb")) if lcache.exists() else {}
missing = sorted(ids_needed - set(links))
print(f"links: {len(missing)} to count", flush=True)
for i in range(0, len(missing), 900):
    chunk = missing[i:i + 900]
    q = ",".join("?" * len(chunk))
    seen = defaultdict(set)
    for t, b in con.execute(f"SELECT targetLineId, sourceBookId FROM link WHERE targetLineId IN ({q})", chunk):
        seen[t].add(b)
    for s, b in con.execute(f"SELECT sourceLineId, targetBookId FROM link WHERE sourceLineId IN ({q})", chunk):
        seen[s].add(b)
    for d in chunk:
        links[d] = len(seen.get(d, ()))
    if i % 45000 == 0:
        print(f"  {i}/{len(missing)}", flush=True)
if missing:
    pickle.dump(links, open(lcache, "wb"))

# ---------------------------------------------------------------- per line helpers
_TAG = re.compile(r"<[^>]*>")


def plain(d):
    return htmlmod.unescape(_TAG.sub(" ", lines[d][2])).replace("\xa0", " ")


words_cache = {}


def words(d):
    w = words_cache.get(d)
    if w is None:
        w = words_cache[d] = [stem(x) for x in verbatim_key(lines[d][2]).split()]
    return w


def query_words(q):
    return list(dict.fromkeys(stem(w) for w in verbatim_key(q).split() if len(w) > 1))


def coverage(d, qw):
    """The best window's share of the query's distinct words: window of max(2n, n+4) words."""
    if len(qw) < 2:
        return 0.0
    line, want, n = words(d), set(qw), len(qw)
    win = max(2 * n, n + 4)
    best = 0
    for i, w in enumerate(line):
        if w in want:
            got = len(want & set(line[i:i + win]))
            if got > best:
                best = got
                if best == n:
                    break
    return best / n


def best_window_start(d, qw):
    line, want = words(d), set(qw)
    if not want:
        return 0
    win = max(2 * len(qw), len(qw) + 4)
    best, at = 0, 0
    for i, w in enumerate(line):
        if w in want:
            got = len(want & set(line[i:i + win]))
            if got > best:
                best, at = got, i
    return at


LEVEL_P = {"מקרא": 1.0, 'חז"ל': 0.95, "גאונים": 0.85, "ראשונים": 0.8, "אחרונים": 0.65, "בני זמננו": 0.55, "לא ידוע": 0.65}


def level_p(d):
    return LEVEL_P.get(level_of.get(lines[d][0], "לא ידוע"), 0.65)


def level_first(page, q):
    """LevelPrecedence.order of the app: among the lines that hold the query as a sentence (3 words or more: spelling
    apart, holds_phrase), the earlier level takes the places they hold in the first 20, and the first place too when
    it is earlier than the line there; a query of two words: only a verse holding them as written takes place 1."""
    k = verbatim_key(q).split()
    if len(k) < 2:
        return page
    quoted = len(k) >= 3
    lv = lambda d: ORDER.get(level_of.get(lines[d][0], "לא ידוע"), 4)  # noqa: E731
    holders = {d for d in page if (quoted or lv(d) == 0) and holds_phrase(q, lines[d][2], spelling=quoted)}
    if not holders:
        return page
    place = {}
    for i, d in enumerate(page):
        place.setdefault(d, i)
    earliest = lambda d: (lv(d), place[d])  # noqa: E731
    top = list(page[:20])
    slots = [i for i, d in enumerate(top) if d in holders]
    out = page
    if slots:
        chosen = sorted(holders, key=earliest)[:len(slots)]
        left = [d for d in top if d in holders and d not in chosen]
        for j, i in enumerate(slots):
            top[i] = chosen[j]
        out = top + left + [d for d in page[20:] if d not in chosen]
    first = min(holders, key=earliest)
    if out[0] not in holders and lv(first) < lv(out[0]):
        out = [first] + [d for d in out if d != first]
    return out


def link_p(d):
    return min(1.0, math.log1p(links.get(d, 0)) / math.log1p(30))


def raw_bm25(d, s):
    """The lexical score without the base books' boost (LuceneSearchEngine.mapScoreDocs)."""
    b = books.get(lines[d][0])
    if b and b[1]:
        return s / (1.0 + max(120 - b[2], 0) / 60.0)
    return s


# ---------------------------------------------------------------- fusion functions
def rrf(lex, dense, k=60, w=3.0, lw=None):
    sc = defaultdict(float)
    for i, d in enumerate(lex):
        sc[d] += (lw(d) if lw else 1.0) / (k + 1 + i)
    for i, d in enumerate(dense):
        sc[d] += w / (k + 1 + i)
    return sc


def ranked(sc):
    return [d for d, _ in sorted(sc.items(), key=lambda x: (-x[1], x[0]))]


def minmax(vals):
    lo, hi = min(vals.values()), max(vals.values())
    return {d: (v - lo) / (hi - lo) if hi > lo else 1.0 for d, v in vals.items()}


def zs(vals):
    xs = list(vals.values())
    mu = statistics.fmean(xs)
    sd = statistics.pstdev(xs) or 1.0
    return {d: (v - mu) / sd for d, v in vals.items()}


def dbsf(vals):
    xs = list(vals.values())
    mu = statistics.fmean(xs)
    sd = statistics.pstdev(xs) or 1.0
    lo, hi = mu - 3 * sd, mu + 3 * sd
    return {d: min(1.0, max(0.0, (v - lo) / (hi - lo))) for d, v in vals.items()}


def convex(L, D, alpha, norm, missing="min"):
    nl = norm(L) if L else {}
    nd = norm(D) if D else {}
    ml = (min(nl.values()) if nl else 0) if missing == "min" else 0.0
    md = (min(nd.values()) if nd else 0) if missing == "min" else 0.0
    sc = {}
    for d in set(nl) | set(nd):
        sc[d] = alpha * nl.get(d, ml) + (1 - alpha) * nd.get(d, md)
    return sc


def query_alpha(q):
    """Otzaria's lexical weight by kind of query (SemanticQueryTypeAlphas)."""
    n = len(verbatim_key(q).split())
    if re.search(r'(^|\s)["״“]\S', q):  # a quoted phrase (an acronym's gershayim inside a word is not one)
        return 1.0
    if n <= 2 and re.search(r"\d", q):
        return 0.85
    if n <= 2:
        return 0.7
    if n <= 4:
        return 0.5
    return 0.3


def diversify(order, per_book=2, top=10):
    out, rest, count = [], [], defaultdict(int)
    for d in order:
        b = lines[d][0]
        if len(out) < top and count[b] < per_book:
            out.append(d)
            count[b] += 1
        else:
            rest.append(d)
    return out + rest


# ---------------------------------------------------------------- the methods
METHODS = [
    ("app", "האפליקציה היום", "מה שפרדס מציג: RRF ‏(k=60, סמנטי ×3) על 150+150, ואחריו keepLeaders ו־LevelPrecedence"),
    ("apprrf", "האפליקציה, מחושבת מחדש", "RRF ‏(k=60, סמנטי ×3) על המועמדים, keepLeaders ו־LevelPrecedence, כאן בפייתון: מה שהאפליקציה עושה, כשהצד הרגיל אחר (swap_lexical.py)"),
    ("lex", "רגיל בלבד (להשוואה)", "סדר החיפוש במילים לבדו, עם בונוס ספרי היסוד, בלי הסמנטי"),
    ("dense", "סמנטי בלבד (להשוואה)", "סדר התלמיד לבדו, לפי הקוסינוס"),
    ("rrf60", "RRF k=60 ×3, בלי כללים", "הנוסחה של האפליקציה לבדה, בלי keepLeaders ו־LevelPrecedence"),
    ("rrf60w15", "RRF k=60, סמנטי ×1.5", "המשקל שנתן את ה־nDCG@3 הטוב ב־results-dense-weight"),
    ("rrf20", "RRF k=20 ×3", "k קטן: המקומות הראשונים בכל צד שוקלים יותר"),
    ("rrf100", "RRF k=100 ×3", "k גדול: ההבדל בין המקומות קטן"),
    ("cc_mm50", "צירוף ליניארי, min-max, חצי־חצי", "0.5·מילים + 0.5·סמנטי, כל צד מנורמל ל־0–1 בשאילתה (חסר = 0)"),
    ("cc_mm30", "צירוף ליניארי, min-max, 0.3 מילים", "0.3·מילים + 0.7·סמנטי, min-max"),
    ("cc_z30", "צירוף ליניארי, z-score, 0.3 מילים", "0.3·מילים + 0.7·סמנטי, כל צד בסטיות תקן מהממוצע של השאילתה"),
    ("cc_z50", "צירוף ליניארי, z-score, חצי־חצי", "0.5·מילים + 0.5·סמנטי, z-score"),
    ("dbsf", "DBSF ‏(Qdrant), חצי־חצי", "נרמול לממוצע ± 3 סטיות תקן, וסכום"),
    ("combmnz", "CombMNZ", "סכום ה־min-max כפול מספר הצדדים שמצאו את השורה"),
    ("ssl", "SSL: המרה לפי השורות המשותפות", "רגרסיה מציון BM25 לקוסינוס על השורות ששני הצדדים מצאו, ואז כל שורה בסולם אחד (פחות מ־3 משותפות: RRF)"),
    ("otzaria", "כמו אוצריא: משקל לפי סוג השאילתה", "BM25 ב־s/(10+s), סמנטי מתחת ל־0.55 לא נספר, משקל המילים 0.85/0.7/0.5/0.3 לפי סוג השאילתה, ‎+0.1‎ כששניהם מצאו"),
    ("z_qtype", "z-score עם משקל לפי סוג השאילתה", "כמו cc_z, והמשקל של המילים לפי סוג השאילתה של אוצריא"),
    ("p12", "הצעות 1+2 של SEARCH_ORDER_ISSUES", "RRF ×3; שורה רגילה שמכילה את השאילתה מילה במילה (3 מילים ומעלה) שוקלת 3; שורות סמנטיות בלבד של 8 מילים או פחות יוצאות (לא בספר שנבחר); keepLeaders"),
    ("p12w", "הצעות 1+2 עם החלון", "הצעות 1+2 של SEARCH_ORDER_ISSUES, ועוד 0.03·(חלק ממילות השאילתה שבחלון הטוב)², ו־keepLeaders"),
    ("p12wl", "הצעות 1+2 עם החלון וקדימות הרמה", "p12w, ואחריו LevelPrecedence של האפליקציה: בין השורות שמכילות את השאילתה כמשפט (גם בכתיב מלא/חסר), הרמה המוקדמת קודמת"),
    ("p12wl_w2", "p12wl, סמנטי ×2", "p12wl עם משקל 2 לצד הסמנטי במקום 3 (EXPANSIONS.md)"),
    ("p12wl_w15", "p12wl, סמנטי ×1.5", "p12wl עם משקל 1.5 לצד הסמנטי במקום 3 (EXPANSIONS.md)"),
    ("p12wsl", "p12wl, מילה במילה גם בכתיב אחר", "p12wl, והשורה הרגילה שוקלת 3 כשהיא מכילה את השאילתה כמשפט גם בכתיב מלא/חסר ועם קידומות (holds_phrase של LevelPrecedence), לא רק מילה במילה (EXPANSIONS.md)"),
    ("window", "מיון מחדש לפי החלון הטוב (כמו ויקיפדיה)", "RRF ×3, ועוד 0.03·(חלק ממילות השאילתה שבחלון הטוב)²"),
    ("links", "ציון לפי הקישורים (PageRank פשוט)", "RRF ×3 כפול (0.7 + 0.3·log של מספר הספרים המקושרים)"),
    ("level", "ציון לפי רמת הספר", "RRF ×3 כפול (0.7 + 0.3·רמה): מקרא 1, חז\"ל 0.95, גאונים 0.85, ראשונים 0.8, אחרונים 0.65, בני זמננו 0.55"),
    ("diverse", "גיוון: עד 2 שורות מכל ספר", "הסדר של האפליקציה, עם לכל היותר 2 שורות מאותו ספר בעשר הראשונות"),
    ("combo", "הכול יחד (משקלים ידניים, לא מכוילים)", "z-score עם משקל לפי סוג השאילתה, ‎+0.5‎ כששניהם מצאו, החלון, הקישורים, רמת הספר, מילה במילה, הורדת שורות סמנטיות קצרות, keepLeaders"),
]


def order_for(method, r):
    q = r["query"]
    lex = [h[0] for h in r["lexCand"]]
    dense = [h[0] for h in r["denseCand"]]
    # A lexical side other than the app's (OtzariaLexical.kt, swap_lexical.py) has no base books' boost to take off
    L = {h[0]: h[1] if r.get("lexRaw") else raw_bm25(h[0], h[1]) for h in r["lexCand"]}
    D = {h[0]: h[1] for h in r["denseCand"]}
    qw = query_words(q)
    general = r["scope"] != "book"
    leaders = [(d, (1, 3)[j]) for j, d in enumerate(lex[:2]) if holds_quote(plain(d), q)]

    if method == "app":
        return [h[0] for h in r["hybrid"]] or order_for("apprrf", r)
    if method == "apprrf":
        return level_first(keep(ranked(rrf(lex, dense)), leaders), q)
    if method == "lex":
        return lex
    if method == "dense":
        return dense
    if not lex or not dense:  # one side only: its order (as the app)
        return lex or dense
    if method == "rrf60":
        return ranked(rrf(lex, dense))
    if method == "rrf60w15":
        return ranked(rrf(lex, dense, w=1.5))
    if method == "rrf20":
        return ranked(rrf(lex, dense, k=20))
    if method == "rrf100":
        return ranked(rrf(lex, dense, k=100))
    if method == "cc_mm50":
        return ranked(convex(L, D, 0.5, minmax, missing="zero"))
    if method == "cc_mm30":
        return ranked(convex(L, D, 0.3, minmax, missing="zero"))
    if method == "cc_z30":
        return ranked(convex(L, D, 0.3, zs))
    if method == "cc_z50":
        return ranked(convex(L, D, 0.5, zs))
    if method == "dbsf":
        return ranked(convex(L, D, 0.5, dbsf, missing="zero"))
    if method == "combmnz":
        nl, nd = minmax(L), minmax(D)
        return ranked({d: (nl.get(d, 0) + nd.get(d, 0)) * ((d in nl) + (d in nd)) for d in set(nl) | set(nd)})
    if method == "ssl":
        both = [d for d in L if d in D]
        if len(both) < 3:
            return ranked(rrf(lex, dense))
        xs = [math.log1p(L[d]) for d in both]
        ys = [D[d] for d in both]
        mx, my = statistics.fmean(xs), statistics.fmean(ys)
        vx = sum((x - mx) ** 2 for x in xs)
        b = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / vx if vx > 0 else 0.0
        if b <= 0:
            return ranked(rrf(lex, dense))
        a = my - b * mx
        lo, hi = min(D.values()) - 0.02, max(D.values()) + 0.02
        mapped = {d: min(hi, max(lo, a + b * math.log1p(s))) for d, s in L.items()}
        sc = {}
        for d in set(L) | set(D):
            if d in L and d in D:
                sc[d] = (mapped[d] + D[d]) / 2 + 0.005
            else:
                sc[d] = D.get(d, mapped.get(d))
        return ranked(sc)
    if method == "otzaria":
        alpha = query_alpha(q)
        sl = {d: s / (10.0 + s) for d, s in L.items()}
        sd = {d: s for d, s in D.items() if s >= 0.55}
        sc = {}
        for d in set(sl) | set(sd):
            sc[d] = alpha * sl.get(d, 0) + (1 - alpha) * sd.get(d, 0) + (0.1 if d in sl and d in sd else 0)
        return ranked(sc)
    if method == "z_qtype":
        return ranked(convex(L, D, query_alpha(q), zs))
    if method == "p12":
        many = len(verbatim_key(q).split()) >= 3
        lw = (lambda d: 3.0 if holds_verbatim(lines[d][2], q) else 1.0) if many else None
        lexset = set(lex)
        dd = [d for d in dense if not (general and d not in lexset and len(words(d)) <= 8)]
        return keep(ranked(rrf(lex, dd, lw=lw)), leaders)
    if method in ("p12w", "p12w_w2", "p12w_w15", "p12ws"):
        w = {"p12w": 3.0, "p12w_w2": 2.0, "p12w_w15": 1.5, "p12ws": 3.0}[method]
        many = len(verbatim_key(q).split()) >= 3
        if method == "p12ws":  # the query as a sentence, spelling apart (LevelPrecedence's holds_phrase), not word for word
            lw = (lambda d: 3.0 if holds_phrase(q, lines[d][2], spelling=True) else 1.0) if many else None
        else:
            lw = (lambda d: 3.0 if holds_verbatim(lines[d][2], q) else 1.0) if many else None
        lexset = set(lex)
        dd = [d for d in dense if not (general and d not in lexset and len(words(d)) <= 8)]
        sc = rrf(lex, dd, w=w, lw=lw)
        return keep(ranked({d: s + 0.03 * coverage(d, qw) ** 2 for d, s in sc.items()}), leaders)
    if method in ("p12wl", "p12wl_w2", "p12wl_w15", "p12wsl"):
        return level_first(order_for({"p12wsl": "p12ws"}.get(method, method.replace("p12wl", "p12w")), r), q)
    if method == "window":
        sc = rrf(lex, dense)
        return ranked({d: s + 0.03 * coverage(d, qw) ** 2 for d, s in sc.items()})
    if method == "links":
        sc = rrf(lex, dense)
        return ranked({d: s * (0.7 + 0.3 * link_p(d)) for d, s in sc.items()})
    if method == "level":
        sc = rrf(lex, dense)
        return ranked({d: s * (0.7 + 0.3 * level_p(d)) for d, s in sc.items()})
    if method == "diverse":
        return diversify([h[0] for h in r["hybrid"]] or ranked(rrf(lex, dense)))
    if method == "combo":
        alpha = query_alpha(q)
        base = convex(L, D, alpha, zs)
        lexset, many = set(lex), len(verbatim_key(q).split()) >= 3
        sc = {}
        for d, s in base.items():
            s += 0.5 if (d in L and d in D) else 0
            s += 1.0 * coverage(d, qw) ** 2
            s += 0.5 * link_p(d)
            s += 1.0 * (level_p(d) - 0.65)
            if many and d in L and holds_verbatim(lines[d][2], q):
                s += 1.0
            if general and d not in lexset and len(words(d)) <= 8:
                s -= 1.0
            sc[d] = s
        return keep(ranked(sc), leaders)
    raise ValueError(method)


# ---------------------------------------------------------------- run and write
def scope_label(r):
    if r["kind"] == "book":
        return "בספר: " + books.get(r["scopeId"], ("?",))[0]
    if r["kind"] == "cat":
        return "בקטגוריה: " + cat_title.get(r["scopeId"], "?")
    return {"all": "כל הספרים", "base": "ספרי יסוד", "fallback": "ספרי יסוד ← כל הספרים (מעבר אוטומטי)"}[r["scope"]]


def snippet(d, qw, n=45):
    text = plain(d).split()
    if len(text) <= n:
        return " ".join(text)
    at = best_window_start(d, qw) if qw else 0
    # word positions of verbatim_key and of the plain split differ slightly; good enough for a glimpse
    start = max(0, min(at - 10, len(text) - n))
    s = " ".join(text[start:start + n])
    return ("… " if start > 0 else "") + s + (" …" if start + n < len(text) else "")


out_searches, line_info, book_info = [], {}, {}
full = []  # every method's first 50, for analyze.py
tsv = open(out / "orderings.tsv", "w", encoding="utf-8")
tsv.write("search\tquery\tscope\tmethod\tplace\tlineId\tbook\tref\tgrade\n")
for r in rows:
    q = r["query"]
    qw = query_words(q)
    lex_rank = {h[0]: i + 1 for i, h in enumerate(r["lexCand"])}
    dense_rank = {h[0]: i + 1 for i, h in enumerate(r["denseCand"])}
    tops, shown, firsts = {}, set(), {}
    for key, _, _ in METHODS:
        firsts[key] = order_for(key, r)[:50]
        top = firsts[key][:10]
        tops[key] = top
        shown.update(top)
    full.append({"id": r["id"], "q": q, "scope": r["scope"], "kind": r["kind"],
                 "lex": [h[0] for h in r["lexCand"]], "dense": [h[0] for h in r["denseCand"]], "tops": firsts})
    per_line = {}
    for d in shown:
        g = grades.get(f"{q}\t{d}")
        per_line[d] = [lex_rank.get(d, 0), dense_rank.get(d, 0), g["grade"] if g else -1, links.get(d, 0),
                       {"claude": "c", "target": "t"}.get(g.get("by"), "u") if g else "u"]
        if d not in line_info:
            b = lines[d][0]
            line_info[d] = [b, lines[d][1], snippet(d, qw), len(words(d))]
            if b not in book_info:
                book_info[b] = [books.get(b, ("?",))[0], level_of.get(b, "לא ידוע"), books.get(b, ("", 0))[1]]
        else:
            line_info[d][2] = line_info[d][2]  # the first search's glimpse is kept
    m = meta.get(str(r["id"]), {})
    out_searches.append({
        "id": r["id"], "q": q, "scope": scope_label(r), "lexTotal": r["lexTotal"], "count": m.get("count", 1),
        "sources": m.get("sources", []), "tops": tops, "lines": per_line,
        "graded": sum(1 for d in per_line if per_line[d][2] >= 0),
    })
    for key, _, _ in METHODS:
        for i, d in enumerate(tops[key]):
            gg = per_line[d][2]
            tsv.write(f"{r['id']}\t{q}\t{scope_label(r)}\t{key}\t{i + 1}\t{d}\t{book_info[lines[d][0]][0]}\t{lines[d][1]}\t{'' if gg < 0 else gg}\n")
tsv.close()

json.dump(full, open(out / "tops50.json", "w", encoding="utf-8"), ensure_ascii=False)
json.dump({"methods": METHODS, "searches": out_searches, "lines": line_info, "books": book_info},
          open(out / "orderings.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(f"{len(out_searches)} searches, {len(line_info)} lines shown, {len(METHODS)} methods", flush=True)

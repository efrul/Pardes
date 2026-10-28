"""The owner's proposal, offline: when lines match a search equally, the book of the earlier level comes first; the
passage's length, and the small score gaps it makes on both sides, decides only within a level.

    python level_order.py results.jsonl seforim.db (--grades grades.tsv | --targets | --quotes bavli|mishna|tanakh)
        [--cache lines.pkl] [--rubric] [--headings] [--weight 3] [--condensed] [--details]

A book's level: מקרא, חז"ל, גאונים, ראשונים, אחרונים, בני זמננו. The era of its authors in the library (author.era: GN,
RI, AH, CO; the latest when several), else a category named for an era on its path (ראשונים, אחרונים, מחברי זמננו,
גאונים; תרגומים as Chazal's), else the books of the Tanakh themselves (מקרא) and Chazal's categories (משנה, תלמוד,
תוספתא, מדרש) but for their commentaries, aids, references and notes (חז"ל). Compilations of Chazal's words rank by
their compilers: ילקוט שמעוני with the Rishonim, עין יעקב with the Acharonim. A book of no known era ranks with the
Acharonim, the era of most such books.

"Equally": the line holds the search as a quote. On the app's page (RRF w3 of the first 150 of each side, then
holdsQuote's leaders at places 2 and 4), the lines holding it trade the places they hold in its first 20, by level, then
by their place; those that no longer fit come right after the 20th:
  L1     the holders of holdsQuote (fusions.py's near2: all the words, within the query's length + 2) in the first 20;
  L2     the same, every holder among the app's candidates (the first 150 of each side) joining in;
  V2     the holders of the same sentence: the words in a row and in order (holds_phrase), a line's word also matching
         behind one or two one-letter prefixes of any length (near2 strips them only off words of four letters or more,
         so the Gemara's "שכל ישראל" does not hold "כל ישראל");
  V2s    V2, spelling apart: a word also matching by its skeleton, without its inner ו and י (מלא and חסר: תרדוף for
         תרדף, עיוור for עור), a final ין as ים (בודקים for בודקין), the divine name as the query writes it (ה' for
         יהוה, אלוקים for אלהים);
  V2s 3+ V2s for searches of three words or more: two words are a subject more than a sentence ("דין תפילה" is held by
         "מדין תפלה");
  V2s1 3+  V2s 3+, and the earliest holder takes place 1 too when its level is earlier than that of the line there;
  V2s1   the same for searches of two words too;
  V2s1 3+, 2 exact  V2s1 3+, and for a search of two words, its holders of V2 (no spelling apart);
  V2s1 3+, 2 verse  V2s1 3+, and for a search of two words, the verses of the Tanakh among its holders of V2: the
         first of them takes place 1.
With --headings, the page they start from gives the dense side's headings (headings.py) a weight of 1 instead of 3.
With --weight, the dense side weighs that instead of 3 (the model's hybridDenseWeight); headings, at most 1.
--grades (eval-real, eval-v1): first good, good in 3, source in 3, source first, nDCG@3; a line no one judged counts 0
and is listed at the end, to judge first; with --condensed, it is left out of the page instead (a condensed list: for
comparing models whose pages hold many lines no one judged, between counting them 0 and judging them). With --rubric,
the grades by the rule for several right answers first: among a search's lines that answer (2 or 3), those of the
earliest level get 3 and the others 2. --targets (eval-100): its sources (expected.py) in the first ten, first,
MRR@10. --quotes: the source of a quote first, in 3, in 10: a line holding the search's `expected` words (niqqud and
teamim off), of the Bavli (bavli), of the Mishnah or the Talmuds (mishna: the Talmuds hold the Mishnah too), or of the
Tanakh (tanakh).
"""

import json
import os
import pickle
import re
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from common_source import app_page, arg  # noqa: E402
from fusions import PREFIXES, near, stem, verbatim_key  # noqa: E402
from score import NOISE, dcg, load_grades  # noqa: E402

LEVELS = ("מקרא", 'חז"ל', "גאונים", "ראשונים", "אחרונים", "בני זמננו")
UNKNOWN = "לא ידוע"
ORDER = {**{name: i for i, name in enumerate(LEVELS)}, UNKNOWN: LEVELS.index("אחרונים")}
ERA = {"GN": "גאונים", "RI": "ראשונים", "AH": "אחרונים", "CO": "בני זמננו"}
ERA_OF_CATEGORY = {"גאונים": "גאונים", "ראשונים": "ראשונים", "אחרונים": "אחרונים", "מחברי זמננו": "בני זמננו",
                   "תרגומים": 'חז"ל'}
CHAZAL = {"משנה", "תלמוד", "תוספתא", "מדרש"}
LATER = {"מפרשים", "ספרות עזר", "מראי מקומות", "כללי הש״ס", "מפרשים על המסכתות הקטנות"}
TANAKH = {"תורה", "נביאים", "כתובים"}
COMPILATIONS = {"ילקוט שמעוני": "ראשונים", "עין יעקב": "אחרונים"}
NOTES = ("הערות", "הגהות", "נוסחא", "שינויי נוסחאות")
# The orders --details shows, where one of them changes the first 3
DETAILS = ("V2s 3+", "V2s1 3+", "V2s1", "V2s1 3+, 2 exact", "V2s1 3+, 2 verse")
FIRST = 20


def book_levels(con):
    """{bookId: level}, {bookId: (title, category path)}."""
    parent = dict(con.execute("SELECT id, parentId FROM category"))
    name = dict(con.execute("SELECT id, title FROM category"))

    def path(c):
        out = []
        while c:
            out.append(name[c])
            c = parent.get(c)
        return out[::-1]

    eras = defaultdict(set)
    for b, e in con.execute("SELECT ba.bookId, a.era FROM book_author ba JOIN author a ON a.id = ba.authorId"):
        if e in ERA:
            eras[b].add(ERA[e])
    level, about = {}, {}
    for b, c, title in con.execute("SELECT id, categoryId, title FROM book"):
        p = path(c)
        named = [ERA_OF_CATEGORY[x] for x in p if x in ERA_OF_CATEGORY]
        if eras[b]:
            level[b] = max(eras[b], key=ORDER.get)
        elif p[:1] == ["תנ״ך"] and p[1:2] and p[1] in TANAKH:
            level[b] = "מקרא"
        elif named:
            level[b] = named[-1]
        elif any(title.startswith(c) for c in COMPILATIONS):
            level[b] = next(v for c, v in COMPILATIONS.items() if title.startswith(c))
        elif p and p[0] in CHAZAL and not LATER & set(p) and not title.startswith(NOTES):
            level[b] = 'חז"ל'
        else:
            level[b] = UNKNOWN
        about[b] = (title, " > ".join(p))
    return level, about


def fetch_lines(con, ids, cache):
    """{lineId: (bookId, heRef, content)}, read in id order (the table's own order: far faster on a cold disk) and kept
    in [cache] between runs (written whole and then renamed, so that runs side by side never leave it half written)."""
    lines = pickle.load(open(cache, "rb")) if cache and Path(cache).exists() else {}
    missing = sorted(set(ids) - set(lines))
    for i in range(0, len(missing), 900):
        chunk = missing[i:i + 900]
        sql = f"SELECT id, bookId, heRef, content FROM line WHERE id IN ({','.join('?' * len(chunk))})"
        for d, b, h, c in con.execute(sql, chunk):
            lines[d] = (b, h or "", c)
    if cache and missing:
        part = f"{cache}.{os.getpid()}"
        with open(part, "wb") as out:
            pickle.dump(lines, out)
        os.replace(part, cache)
    return lines


def forms(w):
    """What a line's word may stand for: itself, its stem, and itself without one or two one-letter prefixes."""
    out = {w, stem(w)}
    if len(w) > 1 and w[0] in PREFIXES:
        out.add(w[1:])
        if len(w) > 2 and w[1] in PREFIXES:
            out.add(w[2:])
    return out


def holds_phrase(query, text, spelling=False):
    """Whether [text] holds [query]'s words in a row and in order, a line's word as any of its forms(); with [spelling],
    a word also matches by its skeleton() (the query's word as written, or the divine name as the text writes it)
    against the skeletons of the line word's forms."""
    words = verbatim_key(query).split()
    if len(words) < 2:
        return False
    want = [{w, stem(w)} for w in words]
    line = [forms(w) for w in key(text).split()]
    if spelling:
        # The skeleton of the query's word as written, not of its stem: a stem may have lost a root letter taken for a
        # prefix (מגדל, גדל), and would then match another word's skeleton (גדול)
        want_skeleton = [{skeleton(w)} | {skeleton(x) for x in names(w)} for w in words]
        line_skeleton = [{skeleton(x) for x in f} for f in line]
    n = len(want)

    def match(i, j):
        return bool(line[i] & want[j]) or (spelling and bool(line_skeleton[i] & want_skeleton[j]))

    return any(all(match(i + j, j) for j in range(n)) for i in range(len(line) - n + 1))


ENTITY = re.compile(r"&#?\w+;")


def key(text):
    """verbatim_key, an HTML entity (&nbsp;, &thinsp;) a space as in HybridSearchEngine.verbatimKey, not a word."""
    return verbatim_key(ENTITY.sub(" ", text))


def skeleton(word):
    """[word] (a verbatim_key word, final letters as their base forms) without the ו and י after its first letter, a
    final ין as ים first: the same word in full and defective spelling, and the Mishnah's plural as today's."""
    if word.endswith("ינ"):
        word = word[:-1] + "מ"
    return word[:1] + word[1:].replace("ו", "").replace("י", "")


def names(word):
    """The divine name as a query writes it, and as the text does: ה (from ה') for יהוה, אלוק and אלק for אלה."""
    if word == "ה":
        return {"ה", "יהוה", "יי", "השמ"}
    for short in ("אלוק", "אלק"):
        if word.startswith(short) and len(word) > len(short):
            return {word, "אלה" + word[len(short):]}
    if word in ("אלוק", "אלק"):
        return {word, "אלוה"}
    return {word}


def by_level(page, holders, rank, level_of):
    """[page] with [holders] (quote holders) on the places its quote holders take in its first FIRST lines, by level,
    then by [rank] (a line's place on the page, or after it); the holders that no longer fit come right after."""
    slots = [i for i, d in enumerate(page[:FIRST]) if d in holders]
    if not slots:
        return page
    pool = sorted(holders, key=lambda d: (ORDER[level_of(d)], rank(d)))
    chosen = pool[:len(slots)]
    top = list(page[:FIRST])
    for i, d in zip(slots, chosen):
        top[i] = d
    left = [d for d in page[:FIRST] if d in holders and d not in chosen]
    rest = [d for d in page[FIRST:] if d not in chosen]
    return top + left + rest


def main() -> None:
    rows = {r["id"]: r for r in map(json.loads, open(sys.argv[1], encoding="utf-8"))}
    con = sqlite3.connect(f"file:{sys.argv[2]}?mode=ro", uri=True)
    targets, quotes = "--targets" in sys.argv, arg("--quotes")
    quoting = quotes is not None
    if targets:
        sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "eval-100"))
        from expected import E
        rows = {q: r for q, r in rows.items() if q in E}
    elif not quoting:
        grades = load_grades(arg("--grades"))
        rows = {q: r for q, r in rows.items() if q not in NOISE and q in grades}
    level, about = book_levels(con)
    # The app takes the first 150 of each side (a --quotes run may carry more lexical ones)
    rows = {q: dict(r, lexCand=r["lexCand"][:150]) for q, r in rows.items()}
    need = {d for r in rows.values() for d in r["lexCand"] + r["denseCand"]}
    if not targets and not quoting:
        need |= {d for q in rows for d in grades[q]}
    lines = fetch_lines(con, need, arg("--cache"))
    text = {d: v[2] for d, v in lines.items()}
    pages = {q: app_page(r, text) for q, r in rows.items()}

    def level_of(d):
        return level[lines[d][0]]

    weight = float(arg("--weight") or 3)
    if "--headings" in sys.argv or weight != 3:
        from headings import heading_lines, page_of
        low = heading_lines(con, rows, text) if "--headings" in sys.argv else {q: set() for q in rows}
        pages = {q: page_of(r, text, r["denseCand"], lambda d, q=q: min(1, weight) if d in low[q] else weight)
                 for q, r in rows.items()}

    if "--rubric" in sys.argv:
        before = sum(map(len, grades.values()))
        changes = Counter()
        for q in rows:
            answers = [d for d, g in grades[q].items() if g >= 2]
            if not answers:
                continue
            top = min(ORDER[level_of(d)] for d in answers)
            for d in answers:
                g = 3 if ORDER[level_of(d)] == top else 2
                if g != grades[q][d]:
                    changes[f"{grades[q][d]}→{g}"] += 1
                    grades[q][d] = g
        print(f"The rule for several right answers: of {before} grades, " +
              ", ".join(f"{k} {v}" for k, v in sorted(changes.items())) + "\n")

    page_name = "the app's page" + (f", dense weight {weight:g}" if weight != 3 else "") + (
        ", dense headings at 1" if "--headings" in sys.argv else "")
    variants = {page_name: pages}
    # Each line's tests once per search: holdsQuote's near2, the same sentence, the same sentence spelling apart
    held = {}
    for q, f in pages.items():
        query = rows[q]["query"]
        held[q] = {
            "near": {d for d in f if near(query, text[d], 2)},
            "phrase": {d for d in f if holds_phrase(query, text[d])},
            "spelling": {d for d in f if holds_phrase(query, text[d], spelling=True)},
        }
    specs = {"L1": "near", "L2": "near", "V2": "phrase", "V2s": "spelling", "V2s 3+": "spelling", "V2s1 3+": "spelling",
             "V2s1": "spelling", "V2s1 3+, 2 exact": "spelling", "V2s1 3+, 2 verse": "spelling"}
    for name, test in specs.items():
        out = {}
        for q, f in pages.items():
            pool = held[q][test]
            words = len(verbatim_key(rows[q]["query"]).split())
            if name == "L1":
                pool = pool & set(f[:FIRST])
            if "3+" in name and words < 3:
                pool = held[q]["phrase"] if name.endswith(("2 exact", "2 verse")) and words == 2 else set()
                if name.endswith("2 verse"):
                    pool = {d for d in pool if level_of(d) == "מקרא"}
            place = {d: i for i, d in enumerate(f)}
            out[q] = by_level(f, pool, place.get, level_of)
            if name.startswith("V2s1") and pool and out[q][0] not in pool:
                best = min(pool, key=lambda d: (ORDER[level_of(d)], place[d]))
                if ORDER[level_of(best)] < ORDER[level_of(out[q][0])]:
                    out[q] = [best] + [d for d in out[q] if d != best]
        variants[name] = out

    roots = {"bavli": ("תלמוד > בבלי",), "mishna": ("משנה", "תלמוד > בבלי", "תלמוד > ירושלמי"), "tanakh": ("תנ״ך",)}
    source_level = "מקרא" if quotes == "tanakh" else 'חז"ל'
    sources = {b for b, (_, p) in about.items() if p.startswith(roots.get(quotes, ())) and level[b] == source_level}
    plain = re.compile(r"[^א-ת ]")

    def is_source(d, words):
        key = " ".join(plain.sub(" ", re.sub(r"[֑-ׇ]", "", re.sub(r"<[^>]+>", "", text[d]).replace("־", " "))).split())
        return lines[d][0] in sources and words in key

    def ref(d):
        b, h, _ = lines[d]
        return " ".join((h or about[b][0]).split())

    unjudged = defaultdict(set)

    def measure(lists):
        if quoting:
            ranks = [next((i + 1 for i, d in enumerate(f[:10]) if is_source(d, rows[q]["expected"])), None)
                     for q, f in lists.items()]
            return (f"{sum(1 for x in ranks if x == 1)} | {sum(1 for x in ranks if x and x <= 3)} | "
                    f"{sum(1 for x in ranks if x)}")
        if targets:
            ranks = [next((i + 1 for i, d in enumerate(f[:10]) if any(re.search(p, ref(d)) for p in E[q])), None)
                     for q, f in lists.items()]
            hit, top = sum(1 for x in ranks if x), sum(1 for x in ranks if x == 1)
            return f"{hit}/{len(ranks)} | {top} | {sum(1 / x for x in ranks if x) / len(ranks):.3f}"
        first_good = three = source = source_first = 0
        ndcg = []
        for q, f in lists.items():
            for d in f[:3]:
                if d not in grades[q]:
                    unjudged[q].add(d)
            if "--condensed" in sys.argv:
                f = [d for d in f if d in grades[q]]
            g = [grades[q].get(d, 0) for d in f[:3]]
            first_good += bool(g) and g[0] >= 2
            three += max(g, default=0) >= 2
            source += max(g, default=0) == 3
            source_first += bool(g) and g[0] == 3
            ideal = dcg(sorted(grades[q].values(), reverse=True)[:3])
            if ideal:
                ndcg.append(dcg(g) / ideal)
        return f"{first_good} | {three} | {source} | {source_first} | {sum(ndcg) / len(ndcg):.3f}"

    if quoting:
        head = "the source first | in 3 | in 10"
    elif targets:
        head = "hit@10 | first | MRR@10"
    else:
        head = "first good | good in 3 | source in 3 | source first | nDCG@3"
    print(f"{len(rows)} queries\n")
    print(f"| order | changed in the first 3 | {head} |")
    print("|---|---|---|")
    for name, lists in variants.items():
        changed = sum(1 for q in lists if lists[q][:3] != pages[q][:3])
        print(f"| {name} | {changed} | {measure(lists)} |")
    # What the levels look like where the order changed, and the lines still to judge
    moved = Counter()
    for q in rows:
        for d in variants["V2s 3+"][q][:3]:
            if d not in pages[q][:3]:
                moved[level_of(d)] += 1
    print("\nLines V2s 3+ brings into the first 3, by level:", ", ".join(f"{k} {v}" for k, v in moved.most_common()))
    if unjudged:
        print(f"\nNot judged ({sum(map(len, unjudged.values()))}):")
        for q in sorted(unjudged):
            for d in sorted(unjudged[q]):
                print(f"{q}\t{d}\t{ref(d)}\t{level_of(d)}")
    if "--details" in sys.argv:
        for q in sorted(rows):
            if all(variants[n][q][:3] == pages[q][:3] for n in DETAILS):
                continue
            print(f"\n#{q} {rows[q]['query']}")
            for name in (next(iter(variants)), *DETAILS):
                print(f"  {name}: " + " / ".join(f"{ref(d)} [{level_of(d)}]" for d in variants[name][q][:3]))


if __name__ == "__main__":
    main()

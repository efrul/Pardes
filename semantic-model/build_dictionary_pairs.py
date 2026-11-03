"""Training pairs from Klei Kodesh's dictionary (Based on Klei Kodesh - https://kleikodesh.github.io/): an Aramaic or
rare word, in a line of the library, swapped for its Hebrew meaning. A user asks in today's Hebrew; the Gemara and
the prophets answer in their own words. Four kinds, each a "type" of its own:

- DICT-ARAMAIC: a span of a Gemara line, its Aramaic words swapped for their meaning in Klei Kodesh's Aramaic-Hebrew
  dictionary (source_kind 1), -> the Gemara line. Klei Kodesh's owner allowed it (2026-11-03), but whether their
  permission covers data they took from elsewhere is still being checked, and its origin is not recorded: --aramaic 0
  leaves it out, its file unread (TRAINING_PLAN.md, decision 2).
- DICT-ARUCH: what Sefer HeAruch quotes from a tractate, its word swapped for the Aruch's explanation ("פי' ..."),
  -> the Gemara line it quotes, found by the quote within the cited daf.
- DICT-METZUDAT: a verse, the words Metzudat Tzion explains swapped for its explanation ("כסלי. ענין בטחון"),
  -> the verse.
- DICT-MIKROPEDIA: a concept of the Mikropedia Talmudit, by its name or its one-line definition, -> the entry's first
  paragraph.

    python build_dictionary_pairs.py --db seforim.db --dictionary KitveiHakodeshDictionary \
        --out build/train/dictionary.jsonl

--dictionary is a clone of KleiKodesh/KitveiHakodeshDictionary: its source/senses/source_01.tsv and the headword index
(source/seforim_headword.tsv, the line of each entry in the library's books) are read, as data. They stay out of this
repository.

Nothing an evaluation measures is touched, as in build_chavruta_pairs.py: no line of the benchmark's books or of its
Rambam halachot, no line an evaluation judges or targets or near one, none near a dev question's target, no line holding
a quote's source or matching eval-100's sources; and no line of the five tractates of crfh1672/search-queries' drafts
(sample_targets.OUTSIDE_SET_TRACTATES), whose lines are not held out yet, nor a copy, word for word, of a line of a book
held out. An anchor is never an evaluation's query.
"""

import argparse
import csv
import json
import random
import re
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "train-v2"))
from build_pairs import DEV_BATCHES, EVAL_QUERIES, letters, read_tsv  # noqa: E402
from build_train_pairs import MAX_PASSAGE_WORDS, SPAN_WORDS, benchmark_held  # noqa: E402
from pardes_semantic.clean import clean  # noqa: E402
from sample_targets import held_book_texts, held_out, neighbors, outside_set_books  # noqa: E402

MARKS = re.compile(r"[֑-ׇ]")
TAGS = re.compile(r"<[^>]+>")
HEB = re.compile(r"[^א-ת]")
GLOSS_WORDS = 4
# A Gemara span is kept when this share of its words, and at least MIN_SWAPS, were swapped
MIN_SWAPS, MIN_SHARE = 2, 0.25
# A dictionary word this frequent in the Mishnah (per million words) is Hebrew too: "אבר" is a limb there, not lead
MISHNAH_PER_MILLION = 30
ARUCH_QUOTE_WORDS = 4
ARUCH_MATCH_WORDS, ARUCH_MATCH_SHARE = 8, 0.6
# The Aruch explains at more length than a glossary
ARUCH_GLOSS_WORDS = 7
ABBREVIATIONS = {'ב"ק': "בבא קמא", 'ב"מ': "בבא מציעא", 'ב"ב': "בבא בתרא", 'ע"ז': "עבודה זרה", 'ר"ה': "ראש השנה",
                 "ב״ק": "בבא קמא", "ב״מ": "בבא מציעא", "ב״ב": "בבא בתרא", "ע״ז": "עבודה זרה", "ר״ה": "ראש השנה",
                 "מ\"ק": "מועד קטן", "מו\"ק": "מועד קטן"}


def as_typed(anchor):
    """An anchor as a user types it: no niqqud, no verse number or parasha mark."""
    anchor = re.sub(r"&[a-z]+;|[\u034F\u200C-\u200F]", "", anchor)
    anchor = re.sub(r"^\([א-ת]{1,3}\)|\{[פס]\}", " ", MARKS.sub("", anchor.replace("־", " ")))
    return " ".join(anchor.split())


def plain(text):
    """Letters only, the words of the text: what a dictionary headword is compared with."""
    return [HEB.sub("", w) for w in MARKS.sub("", TAGS.sub(" ", text).replace("־", " ")).split()]


def bare(word):
    """The word without its vowel letters: the Aruch's headword is a root ("סג" for "סיג")."""
    return word[:1] + re.sub("[וי]", "", word[1:])


def tractate_of(name, books):
    """The tractate a citation names: "ובחולין", "במ\"ק" and "במציעא" are Chullin, Moed Katan and Bava Metzia."""
    name = name.strip()
    for candidate in (name, name[1:] if name[:1] == "ו" else None):
        if not candidate:
            continue
        for c in (candidate, candidate[1:] if candidate[:1] == "ב" else None):
            if c:
                c = ABBREVIATIONS.get(c, c)
                c = {"קמא": "בבא קמא", "מציעא": "בבא מציעא", "בתרא": "בבא בתרא"}.get(c, c)
                if c in books:
                    return c
    return None


def first_gloss(text, most=GLOSS_WORDS):
    """The first meaning of a sense, up to GLOSS_WORDS words, or None."""
    text = MARKS.sub("", re.sub(r"\([^)]*\)|\[[^\]]*\]", " ", text))
    text = re.split(r"[,;:.]| כמו | וכן | ר[\"״]ל | והוא | או | מלשון | לשון ", text)[0]
    if re.match(r"\s*ר[\"״]ל", text):
        return None
    text = re.sub(r"^\s*(ענין|ענינו|הוא|פירוש|פי'|מלשון|מל')\s+", "", text)
    words = [w for w in text.split() if HEB.sub("", w)]
    return " ".join(words) if 0 < len(words) <= most else None


class Guard:
    """What keeps a line out of training (the evaluations' lines and their neighbours), and the evaluations' queries."""

    def __init__(self, con):
        self.con = con
        self.held_books, self.held_lines = benchmark_held(con)
        self.held_books |= outside_set_books(con)
        self.held_texts = held_book_texts(con, self.held_books)
        judged, self.patterns, quotes = held_out(con)
        self.linked, self.near = neighbors(con, judged)
        dev = {int(q["target_line"]) for q in read_tsv(HERE / "train-v2" / "questions.tsv")
               if q["batch"] in DEV_BATCHES}
        self.dev_linked, self.dev_near = neighbors(con, dev)
        self.quotes = [q for q in quotes if q]
        self.queries = {letters(r["query"]) for path in EVAL_QUERIES for r in read_tsv(path)}
        self.dropped = Counter()

    def ok(self, kind, lid, book, index, ref, content):
        bad = (lid in self.held_lines or book in self.held_books or lid in self.linked or (book, index) in self.near
               or lid in self.dev_linked or (book, index) in self.dev_near
               or (ref and any(p.search(ref) for p in self.patterns)))
        if not bad:
            text = " ".join(" ".join(plain(content)).split())
            bad = any(q in text for q in self.quotes) or clean(content) in self.held_texts
        if bad:
            self.dropped[kind] += 1
        return not bad

    def query_ok(self, anchor):
        return letters(anchor) not in self.queries


def bavli_books(con):
    return {r[0]: r[1] for r in con.execute("""SELECT id, title FROM book WHERE isBaseBook = 1 AND categoryId IN
                                              (SELECT descendantId FROM category_closure WHERE ancestorId = 13)""")}


def aramaic(con, guard, dictionary, rng, limit):
    if limit <= 0:
        return []
    senses = read_tsv(dictionary / "source" / "senses" / "source_01.tsv")
    gloss = {}
    for s in senses:
        word = HEB.sub("", MARKS.sub("", s["headword"]))
        meaning = first_gloss(s["text"])
        if len(word) >= 2 and meaning and word not in gloss and HEB.sub("", meaning) != word:
            gloss[word] = meaning
    mishnah = Counter()
    for (content,) in con.execute("""SELECT l.content FROM line l JOIN book b ON b.id = l.bookId
                                     WHERE b.isBaseBook = 1 AND b.title LIKE 'משנה %'"""):
        mishnah.update(plain(content))
    total = sum(mishnah.values())
    hebrew = {w for w in gloss if mishnah[w] * 1_000_000 / total >= MISHNAH_PER_MILLION}
    for w in hebrew:
        del gloss[w]
    print(f"aramaic: {len(gloss)} words with a short meaning ({len(hebrew)} left out as Hebrew too)", file=sys.stderr)

    books = bavli_books(con)
    pairs = []
    rows = con.execute(f"""SELECT id, bookId, lineIndex, heRef, content FROM line
                           WHERE bookId IN ({','.join(map(str, books))})""").fetchall()
    rng.shuffle(rows)
    for lid, book, index, ref, content in rows:
        if len(pairs) >= limit:
            break
        words = clean(content).split()
        if len(words) < SPAN_WORDS[0] or not guard.ok("aramaic", lid, book, index, ref, content):
            continue
        n = min(len(words), rng.randint(*SPAN_WORDS))
        start = rng.randint(0, len(words) - n)
        span, swaps = [], 0
        for w in words[start:start + n]:
            key = HEB.sub("", w)
            if key in gloss:
                span.append(gloss[key])
                swaps += 1
            elif key[:1] == "ו" and key[1:] in gloss:
                span.append("ו" + gloss[key[1:]])
                swaps += 1
            else:
                span.append(w)
        anchor = " ".join(span)
        if swaps >= MIN_SWAPS and swaps / n >= MIN_SHARE and guard.query_ok(anchor):
            pairs.append({"anchor": anchor, "positive": " ".join(words[:MAX_PASSAGE_WORDS]), "type": "DICT-ARAMAIC"})
    return pairs


def entries(con, dictionary, source):
    """(book id in the library, line index, headword) of a reference work's entries in Klei Kodesh's index."""
    titles = {r["id"]: r["title"] for r in read_tsv(dictionary / "source" / "seforim_headword_book.tsv")}
    ids = {}
    for kid, title in titles.items():
        found = con.execute("SELECT id FROM book WHERE title = ?", (title,)).fetchall()
        if len(found) == 1:
            ids[kid] = found[0][0]
        else:
            print(f"{title}: {len(found)} books of that title, left out", file=sys.stderr)
    return [(ids[r["bookId"]], int(r["lineIndex"]), r["word"])
            for r in read_tsv(dictionary / "source" / "seforim_headword.tsv")
            if r["source"] == source and r["bookId"] in ids]


def line_at(con, book, index):
    return con.execute("SELECT id, heRef, content FROM line WHERE bookId = ? AND lineIndex = ?",
                       (book, index)).fetchone()


def aruch(con, guard, dictionary):
    books = {title: bid for bid, title in bavli_books(con).items()}
    citation = re.compile(r"\(([^()]{2,25}?)\s+([א-ת]{1,4})\)")
    pairs = []
    for book, index, head in entries(con, dictionary, "aruch"):
        row = line_at(con, book, index)
        if row is None:
            continue
        text = MARKS.sub("", TAGS.sub(" ", row[2]))
        explained = re.search(r"פי'\s+([^.:;(]+)", text)
        meaning = first_gloss(explained.group(1), ARUCH_GLOSS_WORDS) if explained else None
        if not meaning:
            continue
        head = HEB.sub("", head)
        for m in citation.finditer(text):
            tractate = tractate_of(m.group(1), books)
            if tractate is None:
                continue
            quote = plain(re.split(r"\(|פי'", text[m.end():])[0])
            quote = [w for w in quote if w][:SPAN_WORDS[1]]
            at = next((i for i, w in enumerate(quote) if head and bare(head) in bare(w)), None)
            if at is None or len(quote) < ARUCH_QUOTE_WORDS:
                continue
            # Its text often differs from the library's: the line of the daf holding most of the quote's words
            want = {bare(w) for w in quote[:ARUCH_MATCH_WORDS] if len(w) >= 2}
            best, overlap = None, 0
            for candidate in con.execute(
                    "SELECT id, bookId, lineIndex, heRef, content FROM line WHERE bookId = ? AND heRef LIKE ?",
                    (books[tractate], f"{tractate} {m.group(2)}%")):
                if not re.match(rf"{re.escape(tractate)} {m.group(2)}[.:]", candidate[3] or ""):
                    continue
                have = {bare(w) for w in plain(candidate[4]) if w}
                if bare(quote[at]) in have and len(want & have) > overlap:
                    best, overlap = candidate, len(want & have)
            if best is None or overlap < max(3, ARUCH_MATCH_SHARE * len(want)):
                continue
            lid, lbook, lindex, ref, content = best
            if guard.ok("aruch", lid, lbook, lindex, ref, content):
                anchor = " ".join(quote[:at] + [meaning] + quote[at + 1:])
                if guard.query_ok(anchor):
                    words = clean(content).split()
                    pairs.append({"anchor": anchor, "positive": " ".join(words[:MAX_PASSAGE_WORDS]),
                                  "type": "DICT-ARUCH"})
    return pairs


def metzudat(con, guard, dictionary):
    by_verse = defaultdict(dict)
    for book, index, head in entries(con, dictionary, "metzudat"):
        row = line_at(con, book, index)
        if row is None:
            continue
        lid, ref, content = row
        text = MARKS.sub("", TAGS.sub(" ", content))
        if "." not in text:
            continue
        meaning = first_gloss(text.split(".", 1)[1])
        if not meaning or re.search(r"נוספת|במקום|כפול|כינוי|שם מקום|שם אדם", text):
            continue
        for (verse,) in con.execute("""SELECT k.sourceLineId FROM link k JOIN connection_type ct
                                       ON ct.id = k.connectionTypeId WHERE ct.name = 'COMMENTARY'
                                       AND k.targetLineId = ?""", (lid,)):
            by_verse[verse][HEB.sub("", MARKS.sub("", head))] = meaning
    pairs = []
    for verse, glosses in sorted(by_verse.items()):
        row = con.execute("SELECT bookId, lineIndex, heRef, content FROM line WHERE id = ?", (verse,)).fetchone()
        if row is None or not guard.ok("metzudat", verse, *row):
            continue
        words = clean(row[3]).split()
        out, swaps = [], 0
        for w in words:
            key = HEB.sub("", w)
            if key in glosses:
                out.append(glosses[key])
                swaps += 1
            else:
                out.append(w)
        anchor = " ".join(out)
        if swaps and guard.query_ok(anchor):
            pairs.append({"anchor": anchor, "positive": " ".join(words[:MAX_PASSAGE_WORDS]), "type": "DICT-METZUDAT"})
    return pairs


def mikropedia(con, guard, dictionary):
    pairs = []
    for book, index, head in entries(con, dictionary, "micropedia"):
        rows = con.execute("""SELECT id, lineIndex, heRef, content FROM line
                              WHERE bookId = ? AND lineIndex BETWEEN ? AND ? ORDER BY lineIndex""",
                           (book, index, index + 8)).fetchall()
        if len(rows) < 3:
            continue
        name = " ".join(w for w in plain(rows[0][3]) if w)
        definition = re.match(r"\s*הגדרה\d*\s*[-–]\s*(.+)", TAGS.sub(" ", rows[1][3]))
        body = next((r for r in rows[2:] if len(plain(r[3])) >= 15), None)
        if not name or body is None:
            continue
        lid, lindex, ref, content = body
        if not guard.ok("mikropedia", lid, book, lindex, ref, content):
            continue
        positive = " ".join(re.sub(r"(?<=[א-ת])\d+", "", clean(content)).split()[:MAX_PASSAGE_WORDS])
        anchors = [name]
        if definition:
            anchors.append(" ".join(re.sub(r"\d+", "", definition.group(1)).split()).rstrip("."))
        for anchor in anchors:
            if anchor and guard.query_ok(anchor):
                pairs.append({"anchor": anchor, "positive": positive, "type": "DICT-MIKROPEDIA"})
    return pairs


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--dictionary", type=Path, required=True, help="a clone of KleiKodesh/KitveiHakodeshDictionary")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--aramaic", type=int, default=20_000, help="how many DICT-ARAMAIC pairs (0: none)")
    ap.add_argument("--seed", type=int, default=5821)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    guard = Guard(con)
    pairs = []
    for name, build in (("aruch", lambda: aruch(con, guard, args.dictionary)),
                        ("mikropedia", lambda: mikropedia(con, guard, args.dictionary)),
                        ("metzudat", lambda: metzudat(con, guard, args.dictionary)),
                        ("aramaic", lambda: aramaic(con, guard, args.dictionary, rng, args.aramaic))):
        made = build()
        print(f"{name}: {len(made)} pairs, {guard.dropped[name]} lines left out as near what the evaluations measure",
              file=sys.stderr)
        pairs += made
    rng.shuffle(pairs)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as f:
        for p in pairs:
            p["anchor"] = as_typed(p["anchor"])
            f.write(json.dumps(p, ensure_ascii=False) + "\n")
    print(f"{len(pairs)} pairs -> {args.out}")


if __name__ == "__main__":
    main()

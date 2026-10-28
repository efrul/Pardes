"""Texts like users' searches, for distill_model.py --queries: the student learns how the teacher embeds searches too,
not only passages and slices of them (eval-pool/results-tune.md: the student falls behind the teacher mostly on short
questions). Distilling needs no answers, only texts:

- the training questions (train-v2/questions.tsv), without its dev batches and the questions on the benchmark's lines;
- openings of lines, their first 3-8 words, as a reader states a halacha or a subject ("האבל אסור בשאלת שלום"); those
  that start with a ruling word (מותר, אסור, צריך...) also as a question, half of the time ("האם מותר ...");
- runs of 2-4 words from inside lines: keywords, a phrase half remembered;
- the library's headings (tocText) of two words or more once their numbering is off ("סימן רמב - דיני הדלקת נר" gives
  "דיני הדלקת נר").

Lines are drawn over the whole library (books by their length) and as many again from the halacha families. Niqqud,
teamim, tags, numbering and what is in brackets are left out: a search has none of them.

Nothing the evaluations measure is drawn: no line of the benchmark's books or of its Rambam halachot
(build_train_pairs.benchmark_held), no line an evaluation judges or targets or near one, nor one holding a quote's
source (train-v2/sample_targets.py), no heading of the benchmark's books (the concept task's queries are those of the
Shulchan Aruch), and no text that is an evaluation's query or a dev question (letters compared).

    python build_distill_queries.py --db seforim.db --out build/train/queries.txt [--openings 70000] [--seed 5810]
"""

import argparse
import itertools
import random
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "train-v2"))
from build_pairs import DEV_BATCHES, EVAL_QUERIES, letters, read_tsv  # noqa: E402
from build_train_pairs import benchmark_held  # noqa: E402
from sample_targets import FAMILIES, held_out, neighbors  # noqa: E402

HALACHA = ("tur-sa", "rambam", "acharonim-halacha", "modern-halacha", "responsa")
RULINGS = {"מותר", "אסור", "צריך", "חייב", "פטור", "רשאי", "יכול", "מותרת", "אסורה", "צריכה", "חייבת"}
NUMBERING = {"סימן", "סעיף", "סעיפים", "פרק", "הלכה", "הלכות", "דף", "עמוד", "אות", "חלק", "פסקה", "רמז", "משנה",
             "פסוק", "שער", "מאמר", 'ס"ק', 'ח"א', 'ח"ב', 'ח"ג', 'ח"ד', 'ח"ה', 'ח"ו', "עמ'"}
BRACKETS = re.compile(r"\([^()]*\)|\[[^\[\]]*\]|\{[^{}]*\}|<[^<>]*>")
# A line's own numbering: "(א)", "קלג)", "שם."
LEADING = re.compile(r"^\s*[(\[]?[א-ת]{1,4}['\"]?[)\].:]\s*")
MARKS = re.compile(r"[֑-ׇ]")
NOT_WORD = re.compile(r"[^א-ת\"' ]")
ABBREVIATION = re.compile(r"[א-ת]['\"][א-ת]")
SHORT = re.compile(r"[א-ת]{1,2}'")  # ר', ג'


def numerals():
    """The Hebrew numbers 1-999 as written: with and without a geresh or gershayim."""
    ones, tens = "אבגדהוזחט", "יכלמנסעפצ"
    out = set()
    for h in ("", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק"):
        for t in [""] + list(tens):
            for o in [""] + list(ones):
                n = (h + t + o).replace("יה", "טו").replace("יו", "טז")
                if n:
                    out |= {n, n + "'", n[:-1] + '"' + n[-1] if len(n) > 1 else n}
    return out


NUMERALS = numerals()


def words(text):
    """A text as a search would hold it: no tags, niqqud, teamim, brackets or numbering, letters and quote marks."""
    text = text.replace("־", " ").replace("״", '"').replace("׳", "'")
    text = MARKS.sub("", text)
    for _ in range(3):
        text = BRACKETS.sub(" ", text)
    for _ in range(3):
        text = LEADING.sub("", text)
    out = [w if ABBREVIATION.search(w) or SHORT.fullmatch(w) else w.strip("'\"")
           for w in NOT_WORD.sub(" ", text).split()]
    out = [w for w in out if w]
    while out and (out[0] in NUMBERING or out[0] == "שם"):
        out = out[1:]
        if out and out[0] in NUMERALS:
            out = out[1:]
    return out


def heading(text):
    """A heading without its numbering: a numeral goes when it has a geresh or gershayim, follows a numbering word,
    starts or ends the heading (a "מה" or "לב" inside it stays); a heading of fewer than two words is none."""
    w = words(text)
    out = [x for i, x in enumerate(w) if x not in NUMBERING and not (
        x in NUMERALS and ("'" in x or '"' in x or i == 0 or i == len(w) - 1 or w[i - 1] in NUMBERING))]
    return " ".join(out) if len(out) >= 2 else None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--openings", type=int, default=70_000, help="lines drawn, half over the library, half halacha")
    ap.add_argument("--headings", type=int, default=40_000)
    ap.add_argument("--seed", type=int, default=5810)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)

    held_books, held_lines = benchmark_held(con)
    judged, patterns, quotes = held_out(con)
    linked, near = neighbors(con, judged)
    questions = read_tsv(HERE / "train-v2" / "questions.tsv")
    banned = {letters(r["query"]) for path in EVAL_QUERIES for r in read_tsv(path)}
    banned |= {letters(q["query"]) for q in questions if q["batch"] in DEV_BATCHES}
    marks = ",".join("?" * len(held_books))
    banned |= {letters(" ".join(words(t))) for (t,) in con.execute(
        f"SELECT DISTINCT x.text FROM tocEntry e JOIN tocText x ON x.id = e.textId WHERE e.bookId IN ({marks})",
        sorted(held_books))}
    plain_quotes = [" ".join(words(q)) for q in quotes]
    print(f"held out: {len(held_books)} books, {len(held_lines)} Rambam halachot, {len(linked)} judged lines and their "
          f"links, {len(near)} places near them; {len(banned)} texts banned", file=sys.stderr)

    texts = {}

    def add(text, kind):
        key = letters(text)
        if key and key not in banned and key not in texts:
            texts[key] = (text, kind)

    for q in questions:
        line = int(q["target_line"])
        row = con.execute("SELECT bookId FROM line WHERE id = ?", (line,)).fetchone()
        if q["batch"] not in DEV_BATCHES and line not in held_lines and row and row[0] not in held_books:
            add(q["query"], "question")

    def books(where):
        return [b for b in con.execute(f"SELECT b.id, b.totalLines FROM book b WHERE {where} AND b.totalLines > 0")
                if b[0] not in held_books]

    for pool in (books("1 = 1"), books(" OR ".join(f"({FAMILIES[f]})" for f in HALACHA))):
        cumulative = list(itertools.accumulate(n for _, n in pool))
        drawn = tries = 0
        while drawn < args.openings // 2 and tries < args.openings * 20:
            tries += 1
            book, n = rng.choices(pool, cum_weights=cumulative)[0]
            index = rng.randrange(n)
            if (book, index) in near:
                continue
            row = con.execute("SELECT id, heRef, content FROM line WHERE bookId = ? AND lineIndex = ?",
                              (book, index)).fetchone()
            if row is None or row[0] in held_lines or row[0] in linked:
                continue
            if row[1] and any(p.search(row[1]) for p in patterns):
                continue
            w = words(row[2])
            if len(w) < 6:
                continue
            plain = " ".join(w)
            if any(q and q in plain for q in plain_quotes):
                continue
            drawn += 1
            opening = w[:rng.randint(3, 8)]
            add(" ".join(opening), "opening")
            if opening[0] in RULINGS and rng.random() < 0.5:
                add("האם " + " ".join(opening), "opening as a question")
            if drawn % 2 == 0:
                k = rng.randint(2, 4)
                start = rng.randrange(0, len(w) - k + 1)
                add(" ".join(w[start:start + k]), "run")

    heads = sorted({h for (t,) in con.execute("SELECT text FROM tocText") if (h := heading(t))})
    rng.shuffle(heads)
    before = len(texts)
    for h in heads:
        if len(texts) - before >= args.headings:
            break
        add(h, "heading")

    out = list(texts.values())
    rng.shuffle(out)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("".join(t + "\n" for t, _ in out), "utf-8")
    kinds = {}
    for _, kind in out:
        kinds[kind] = kinds.get(kind, 0) + 1
    print(f"{len(out)} texts ({', '.join(f'{k}: {v}' for k, v in sorted(kinds.items(), key=lambda x: -x[1]))}) "
          f"-> {args.out}")


if __name__ == "__main__":
    main()

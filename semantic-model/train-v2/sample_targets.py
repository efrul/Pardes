"""Draws the target lines of the training questions (train-v2): lines a reader would want found, spread over the
families of books, none of them near an evaluation.

    python sample_targets.py --db seforim.db --counts tur-sa=10,bavli=16 --seed 5801 --out candidates.jsonl
        [--exclude used.txt]

The families are eval-v1's (../eval-v1/sample_targets.py), and a few more. A line is a candidate when its cleaned text
has 15-120 words and it is no heading (../eval-real/headings.py).

Nothing the evaluations measure may be learned, so a line is left out when it is, or is near, a line they judge or
target: every line graded in eval-real or eval-v1, eval-100's sources (its heRef patterns), the sources of the quotes of
eval-real/quotes; with them, every line linked to one of those (the library's links, both ways) and the lines within
NEAR of one in the same book.

The out file holds each candidate's id, family, heRef, level and cleaned text, to read before writing its questions: it
stays out of the repository (some books are not in the public domain), only the line ids and refs go into
questions.tsv.
"""

import argparse
import csv
import importlib.util
import json
import random
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent / "eval-real"))
sys.path.insert(0, str(HERE.parent / "eval-100"))
from expected import E  # noqa: E402
from headings import is_heading  # noqa: E402
from level_order import book_levels  # noqa: E402
from pardes_semantic.clean import clean  # noqa: E402

# eval-v1's families, from its own sample_targets.py (the same name as this file's, hence by path)
_spec = importlib.util.spec_from_file_location("eval_v1_targets", HERE.parent / "eval-v1" / "sample_targets.py")
_eval_v1 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_eval_v1)
EVAL_FAMILIES = _eval_v1.FAMILIES
TOP = "(SELECT id FROM category WHERE title = '{}' AND (parentId IS NULL OR parentId = 0))"
UNDER = "b.categoryId IN (SELECT descendantId FROM category_closure WHERE ancestorId = {})"

FAMILIES = {
    **EVAL_FAMILIES,
    "siddur": UNDER.format(TOP.format("סדר התפילה")),
    "musar": UNDER.format(TOP.format("ספרי מוסר")),
    "responsa": UNDER.format(TOP.format("שו״ת")),
}
NEAR = 3
WORDS = (15, 120)
NIQQUD = re.compile(r"[֑-ׇ]")


def held_out(con):
    """The line ids the evaluations judge or target, and the refs patterns of eval-100's sources."""
    held = set()
    for path in (HERE.parent / "eval-real" / "grades.tsv", HERE.parent / "eval-v1" / "grades.tsv"):
        held |= {int(r["lineId"]) for r in csv.DictReader(open(path, encoding="utf-8"), delimiter="\t",
                                                          quoting=csv.QUOTE_NONE)}
    patterns = [re.compile(p) for ps in E.values() for p in ps]
    quotes = []
    for path in sorted((HERE.parent / "eval-real" / "quotes").glob("*.tsv")):
        quotes += [r["expected"] for r in csv.DictReader(open(path, encoding="utf-8"), delimiter="\t",
                                                         quoting=csv.QUOTE_NONE)]
    return held, patterns, quotes


def neighbors(con, lines):
    """[lines], the lines linked to them (both ways) and the lines within NEAR of them in the same book."""
    ids = sorted(lines)
    out = set(ids)
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        marks = ",".join("?" * len(chunk))
        out |= {r[0] for r in con.execute(f"SELECT targetLineId FROM link WHERE sourceLineId IN ({marks})", chunk)}
        out |= {r[0] for r in con.execute(f"SELECT sourceLineId FROM link WHERE targetLineId IN ({marks})", chunk)}
    places = set()
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        places |= set(con.execute(f"SELECT bookId, lineIndex FROM line WHERE id IN ({','.join('?' * len(chunk))})",
                                  chunk).fetchall())
    near = {(b, x + d) for b, x in places for d in range(-NEAR, NEAR + 1)}
    return out, near


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--counts", required=True, help="family=n,...: n lines of each family named")
    ap.add_argument("--seed", type=int, default=5801)
    ap.add_argument("--exclude", type=Path, help="line ids already used, one per line")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    level, _ = book_levels(con)
    held, patterns, quotes = held_out(con)
    linked, near = neighbors(con, held)
    used = set(map(int, args.exclude.read_text().split())) if args.exclude else set()
    print(f"held out: {len(held)} judged lines, {len(linked)} with their links, {len(near)} places near them, "
          f"{len(patterns)} eval-100 patterns, {len(quotes)} quotes", file=sys.stderr)
    plan = [(fam, int(n)) for fam, n in (x.split("=") for x in args.counts.split(","))]
    with args.out.open("w", encoding="utf-8") as f:
        for family, wanted in plan:
            books = con.execute(f"SELECT b.id, b.totalLines FROM book b WHERE {FAMILIES[family]} AND b.totalLines > 0"
                                ).fetchall()
            if not books:
                print(f"{family}: no books", file=sys.stderr)
                continue
            got, tries = 0, 0
            while got < wanted and tries < wanted * 300:
                tries += 1
                # books weighted by their length, so that every line of the family has about the same chance
                book_id, n = rng.choices(books, weights=[b[1] for b in books])[0]
                index = rng.randrange(n)
                if (book_id, index) in near:
                    continue
                row = con.execute("SELECT id, heRef, content FROM line WHERE bookId = ? AND lineIndex = ?",
                                  (book_id, index)).fetchone()
                if not row or row[0] in used or row[0] in linked:
                    continue
                line_id, ref, content = row
                if ref and any(p.search(ref) for p in patterns):
                    continue
                in_toc = con.execute("SELECT 1 FROM tocEntry WHERE lineId = ?", (line_id,)).fetchone() is not None
                if is_heading(content, in_toc):
                    continue
                text = NIQQUD.sub("", clean(content).replace("\u05BE", " "))
                if not WORDS[0] <= len(text.split()) <= WORDS[1]:
                    continue
                plain = " ".join(re.sub(r"[^א-ת ]", " ", text).split())
                if any(q in plain for q in quotes):
                    continue
                used.add(line_id)
                got += 1
                f.write(json.dumps({"id": line_id, "family": family, "heRef": ref or "", "level": level[book_id],
                                    "text": text}, ensure_ascii=False) + "\n")
            print(f"{family}: {got} ({len(books)} books, {tries} draws)", file=sys.stderr)


if __name__ == "__main__":
    main()

"""Draws the target lines of eval-v1: lines that the queries are written for, spread over the families of books.

    python sample_targets.py --db seforim.db --per-family 10 --seed 5786 --out candidates.jsonl [--exclude used.txt]
    python sample_targets.py --db seforim.db --counts tur-sa=18,bavli=20 --seed 5787 --out c.jsonl --exclude used.txt

--counts draws that many lines from each family named (in the order given) instead of --per-family from all of FAMILIES
(batch 1: --per-family 10, seed 5786, the first eleven families).

A family is a list of books (by title, or a title pattern); a line is a candidate when its cleaned text has 15-90 words
(not a heading, not a whole chapter). The out file holds each candidate's id, family, heRef and cleaned text, to read
before writing its queries: it stays out of the repository (some books are not in the public domain), only the line
ids and refs of the chosen ones go into queries.tsv.
"""

import argparse
import json
import random
import re
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from pardes_semantic.clean import clean  # noqa: E402

MIDRASH = ["בראשית רבה", "שמות רבה", "ויקרא רבה", "במדבר רבה", "דברים רבה", "מדרש תנחומא", "מכילתא דרבי ישמעאל",
           "ספרי במדבר", "ספרי דברים", "ספרא", "פסיקתא רבתי", "פסיקתא דרב כהנא", "אבות דרבי נתן", "פרקי דרבי אליעזר",
           "מדרש תהילים", "איכה רבה", "קהלת רבה", "שיר השירים רבה", "רות רבה", "אסתר רבה"]
MACHSHAVA = ["מסילת ישרים", "אורחות צדיקים", "חובות הלבבות", "הכוזרי", "מורה נבוכים", "ספר החינוך", "תניא"]
ACHARONIM_HALACHA = ["משנה ברורה", "קיצור שלחן ערוך", "ערוך השולחן", "שולחן ערוך הרב"]
# Batch 2 on: where the user's real searches (eval-real) found their practical answers, and Chasidut
MODERN_HALACHA = ["בן איש חי", "חיי אדם", "וביום השבת"]
CHASIDUT = ['ליקוטי מוהר"ן', "שם משמואל", "מאור עינים", "נועם אלימלך", "קדושת לוי", "תולדות יעקב יוסף"]

# family -> SQL condition on book b
FAMILIES = {
    "tanakh": "b.isBaseBook = 1 AND b.categoryId IN (SELECT descendantId FROM category_closure WHERE ancestorId = 1)",
    "mishnah": "b.isBaseBook = 1 AND b.title LIKE 'משנה %' AND b.title NOT LIKE 'משנה תורה%' AND b.title NOT LIKE 'משנה ברורה%'",
    "bavli": "b.isBaseBook = 1 AND b.categoryId IN (SELECT descendantId FROM category_closure WHERE ancestorId = 13)",
    "yerushalmi": "b.title LIKE 'תלמוד ירושלמי %'",
    "midrash": "b.title IN ({})".format(",".join(f"'{t}'" for t in MIDRASH)),
    "rambam": "b.title LIKE 'משנה תורה, הלכות %'",
    "tur-sa": "b.isBaseBook = 1 AND (b.title LIKE 'שולחן ערוך, %' OR b.title = 'טור')",
    "rishonim": "(b.title LIKE 'רש\"י על %' OR b.title LIKE 'תוספות על %' OR b.title LIKE 'רמב\"ן על %')",
    "acharonim-halacha": "b.title IN ({})".format(",".join(f"'{t}'" for t in ACHARONIM_HALACHA)),
    "machshava": "b.title IN ({})".format(",".join(f"'{t}'" for t in MACHSHAVA)),
    "zohar": "(b.title LIKE 'זוהר%' OR b.title LIKE 'זהר%' OR b.title LIKE 'ספר הזהר%')",
    "modern-halacha": "(b.title IN ({}) OR b.title LIKE 'קיצור שע ילקוט יוסף%' OR b.title LIKE 'כף החיים על שולחן ערוך%')".format(
        ",".join(f"'{t}'" for t in MODERN_HALACHA)),
    "chasidut": "(b.title IN ({}) OR b.title LIKE 'שפת אמת%')".format(",".join("'" + t.replace("'", "''") + "'" for t in CHASIDUT)),
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--per-family", type=int, default=10)
    ap.add_argument("--counts", help="family=n,...: these families only, n lines each")
    ap.add_argument("--seed", type=int, default=5786)
    ap.add_argument("--exclude", type=Path, help="line ids already used, one per line")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    used = set(map(int, args.exclude.read_text().split())) if args.exclude else set()
    with args.out.open("w", encoding="utf-8") as f:
        if args.counts:
            plan = [(fam, int(n)) for fam, n in (x.split("=") for x in args.counts.split(","))]
        else:
            plan = [(fam, args.per_family) for fam in list(FAMILIES)[:11]]
        for family, wanted in plan:
            cond = FAMILIES[family]
            books = con.execute(f"SELECT b.id, b.totalLines FROM book b WHERE {cond} AND b.totalLines > 0").fetchall()
            if not books:
                print(f"{family}: no books", file=sys.stderr)
                continue
            got, tries = 0, 0
            while got < wanted and tries < wanted * 200:
                tries += 1
                # books weighted by their length, so a long book is not drawn as often as a short one per line
                book_id, n = rng.choices(books, weights=[b[1] for b in books])[0]
                row = con.execute("SELECT id, heRef, content FROM line WHERE bookId = ? AND lineIndex = ?",
                                  (book_id, rng.randrange(n))).fetchone()
                if not row or row[0] in used:
                    continue
                text = re.sub(r"[֑-ׇ]", "", clean(row[2]))
                if not 15 <= len(text.split()) <= 90:
                    continue
                used.add(row[0])
                got += 1
                f.write(json.dumps({"id": row[0], "family": family, "heRef": row[1] or "", "text": text},
                                   ensure_ascii=False) + "\n")
            print(f"{family}: {got} ({len(books)} books)", file=sys.stderr)


if __name__ == "__main__":
    main()

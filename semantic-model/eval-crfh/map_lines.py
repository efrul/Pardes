"""crfh1672/search-queries' Bavli drafts mapped to seforim.db's line ids: the line of each question's correct quote and
of its near miss, within the tractate (the pardes-semantic-search skill). Their lines number the physical lines of a
pinned Sefaria export, not ours, so each quote is found again in the tractate's lines, its daf first.

    GIT_LFS_SKIP_SMUDGE=1 git clone --filter=blob:none --no-checkout https://github.com/crfh1672/search-queries crfh
    git -C crfh fetch origin <the two commits README.md pins>
    for c in <the two commits>; do git -C crfh archive $c drafts | tar -x -C data; done
    python map_lines.py --db seforim.db --drafts data/drafts --out lines.tsv

lines.tsv holds ids and line ids only: the set has no licence yet, so its questions and quotes stay out of this
repository and are read from the drafts when measuring (spec.py). A quote is found when its words, letters only, are
a run of the line's (or the line's of its quote, for a quote across two lines: the first line holding its start); else
the line of the daf holding most of its words, marked "overlap" with the share found.
"""

import argparse
import csv
import json
import re
import sqlite3
from pathlib import Path

MARKS = re.compile(r"[֑-ׇ]")
TAGS = re.compile(r"<[^>]+>")
NOT_LETTER = re.compile(r"[^א-ת ]")
# The share of a quote's words a line must hold when no line holds the quote as it is
MIN_OVERLAP = 0.6


def letters(text):
    """Letters and spaces only, as the drafts compare (niqqud and tags off, the Name as ה')."""
    text = MARKS.sub("", TAGS.sub(" ", text)).replace("־", " ").replace("יהוה", "ה")
    return " ".join(NOT_LETTER.sub(" ", text).split())


def find(quote, lines):
    """(line id, how) of the line [quote] is from, among [lines] [(id, letters)], or (None, why)."""
    q = letters(quote)
    if not q:
        return None, "empty"
    for lid, text in lines:
        if q in text:
            return lid, "exact"
    # A quote across two lines, or cut where the line is not: its first words
    words = q.split()
    for n in (8, 5):
        if len(words) >= n:
            head = " ".join(words[:n])
            for lid, text in lines:
                if head in text:
                    return lid, f"start{n}"
    want = set(words)
    best, share = None, 0.0
    for lid, text in lines:
        s = len(want & set(text.split())) / len(want)
        if s > share:
            best, share = lid, s
    return (best, f"overlap{share:.2f}") if share >= MIN_OVERLAP else (None, f"none{share:.2f}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--drafts", type=Path, required=True, help="the drafts folder of the set's branches")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    rows = []
    for path in sorted(args.drafts.glob("תלמוד בבלי/*/*.jsonl")):
        tractate = path.stem
        found = con.execute("SELECT id FROM book WHERE isBaseBook = 1 AND title = ?", (tractate,)).fetchall()
        if len(found) != 1:
            raise SystemExit(f"{tractate}: {len(found)} base books of that title")
        book = found[0][0]
        lines = [(lid, ref or "", letters(content)) for lid, ref, content in con.execute(
            "SELECT id, heRef, content FROM line WHERE bookId = ? ORDER BY lineIndex", (book,))]
        everything = [(lid, text) for lid, _, text in lines]
        for line in path.open(encoding="utf-8"):
            r = json.loads(line)
            out = {"id": r["id"], "tractate": tractate, "type": r["type"]}
            for side in ("correct", "near_miss"):
                daf = r[f"{side}_ref"].replace("דף ", "").strip()
                on_daf = [(lid, text) for lid, ref, text in lines if ref.startswith(f"{tractate} {daf},")]
                lid, how = find(r[f"{side}_quote"], on_daf)
                if lid is None:
                    lid, how = find(r[f"{side}_quote"], everything)
                    how = f"tractate-{how}"
                out[f"{side}_line"], out[f"{side}_how"] = lid or "", how
            rows.append(out)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]), delimiter="\t", lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    hows = {}
    for r in rows:
        for side in ("correct", "near_miss"):
            key = re.sub(r"[0-9.]+$", "", r[f"{side}_how"])
            hows[key] = hows.get(key, 0) + 1
    print(f"{len(rows)} questions -> {args.out}; {hows}")


if __name__ == "__main__":
    main()

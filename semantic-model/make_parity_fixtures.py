"""Writes the cases that HfTokenizerParityTest checks the app's cleaner and tokenizer against.

    python make_parity_fixtures.py --tokenizer build/model/tokenizer.json --db seforim.db \
        --out ../SeforimApp/src/jvmTest/resources/semantic

Each line of parity.jsonl.gz: {"text": raw, "clean": clean(raw), "query": ids of QUERY_PREFIX + clean at
QUERY_MAX_TOKENS, "doc": ids of clean at DOCUMENT_MAX_TOKENS}, from the `tokenizers` library. The texts are the
samples, edge cases, and (with --db) lines drawn from the base books only (Tanakh, Mishnah, Talmud, Rambam, Shulchan
Aruch...: public-domain texts), where the tokenizer meets HTML, acronyms and rare characters.
The tokenizer.json is copied gzipped next to it.
"""

import argparse
import gzip
import json
import random
import sqlite3
from pathlib import Path

from tokenizers import Tokenizer

from pardes_semantic import model as m
from pardes_semantic.clean import clean
from pardes_semantic.samples import EXTRA, PAIRS

EDGE_CASES = [
    "", " ", "  ", "&amp;&lt;&gt;&quot;&apos;&nbsp;&#1488;&#x5D1;&#0;&#xD800;&bogus;",
    "ראשי תיבות: רש\"י, רמב\"ם, שו\"ע, חז\"ל, וכשה\"ק, ולכשה\"ש, בגמ' ובתוס'",
    "מקף־עברי ופסק׀ וסוף פסוק׃ ונקודתיים:",
    "<a href=\"x\">קישור</a><br><i>נטוי</i>",
    "ΣΊΣΥΦΟΣ İstanbul Ǆ ß ﬁ Ⅻ ①",
    "emoji 😀 and 𝔘𝔫𝔦𝔠𝔬𝔡𝔢 and 中文",
    "x" * 150 + " " + "א" * 120,
    "line\nbreak\ttab",
    "<foreign>text</foreign>",
    "1,000,000.50 ו-3.14 ו-ב' ו-ב",
    "קארדז'יץ ורבינוביץ בקיימברידג",
    "a" + "́" + "é" + "ְִ֑",
    # The added tokens as written in a text, matched before normalizing; the spacing and enclosing marks (Mc, Me),
    # which StripAccents drops too
    "[MASK] טקסט [UNK] באמצע [CLS][SEP][PAD] [unk] a[MASK]b",
    "aःb 1⃝ а҈ का",
]

BASE_TITLES = ("בראשית", "שמות", "ויקרא", "ישעיהו", "תהילים", "משנה ברכות", "משנה שבת", "ברכות", "שבת", "בבא קמא",
               "בבא מציעא", "סנהדרין", "משנה תורה, הלכות תשובה", "משנה תורה, הלכות שבת", "שולחן ערוך, אורח חיים",
               "שולחן ערוך, יורה דעה")


def corpus_lines(db: Path, n: int, seed: int) -> list[str]:
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    rng = random.Random(seed)
    out = []
    # Editors' notes ("הערות ותיקונים על ...") are base books too, and modern
    books = [b for b in con.execute("SELECT id, title FROM book WHERE isBaseBook = 1").fetchall()
             if not b[1].startswith("הערות")]
    chosen = [b for b in books if b[1] in BASE_TITLES] or books[:20]
    for book_id, _ in chosen:
        rows = con.execute("SELECT content FROM line WHERE bookId = ? ORDER BY lineIndex", (book_id,)).fetchall()
        out += [r[0] for r in rng.sample(rows, min(len(rows), n // (2 * len(chosen)) + 1))]
    # The rest from any base book, never from the whole library: the fixtures are committed, and the library also
    # holds modern works that are not in the public domain
    base_ids = [b[0] for b in books]
    sizes = dict(con.execute("SELECT bookId, count(*) FROM line GROUP BY bookId"))
    while len(out) < n:
        book_id = rng.choice(base_ids)
        if not sizes.get(book_id):
            continue
        # The seeded rng picks the line too (SQLite's random() ignores the seed): the same db gives the same cases
        row = con.execute("SELECT content FROM line WHERE bookId = ? ORDER BY lineIndex LIMIT 1 OFFSET ?",
                          (book_id, rng.randrange(sizes[book_id]))).fetchone()
        if row and row[0].strip():
            out.append(row[0])
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tokenizer", type=Path, required=True)
    ap.add_argument("--db", type=Path)
    ap.add_argument("--lines", type=int, default=1500)
    ap.add_argument("--seed", type=int, default=5785)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    texts = EDGE_CASES + [q for q, _ in PAIRS] + [p for _, p in PAIRS] + EXTRA
    if args.db:
        texts += corpus_lines(args.db, args.lines, args.seed)

    def encoder(max_tokens: int) -> Tokenizer:
        t = Tokenizer.from_file(str(args.tokenizer))
        t.enable_truncation(max_tokens)
        t.no_padding()
        return t

    query_tok, doc_tok = encoder(m.QUERY_MAX_TOKENS), encoder(m.DOCUMENT_MAX_TOKENS)
    args.out.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.out / "parity.jsonl.gz", "wt", encoding="utf-8") as f:
        for raw in texts:
            c = clean(raw)
            case = {"text": raw, "clean": c, "query": query_tok.encode(m.QUERY_PREFIX + c).ids,
                    "doc": doc_tok.encode(m.DOCUMENT_PREFIX + c).ids}
            f.write(json.dumps(case, ensure_ascii=False) + "\n")
    with open(args.tokenizer, "rb") as src, gzip.open(args.out / "tokenizer.json.gz", "wb") as dst:
        dst.write(src.read())
    print(f"{len(texts)} cases -> {args.out}")


if __name__ == "__main__":
    main()

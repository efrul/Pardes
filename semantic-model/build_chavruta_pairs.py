"""Training pairs from Chavruta (חברותא), the library's Hebrew explanation of the Bavli line by line: a span of a line's
Hebrew explanation as the query, the Gemara line it explains as the passage to find. Users ask in Hebrew and the answer
is often Aramaic; this is the bridge between the two.

    python build_chavruta_pairs.py --db seforim.db --out build/train/chavruta.jsonl [--pairs 60000] [--seed 5811]

Chavruta sets the Gemara's own words in bold and weaves its explanation around them. The bold words, and the notes in
small type, are left out of the query: what is learnt is the Hebrew wording of an Aramaic passage, not the words both
share, and so are its references in brackets and what is not a word. A query is a span of SPAN_WORDS words of what is
left, as build_train_pairs.py cuts its queries; a Gemara line gives at most PER_LINE pairs, so that the long sugyot do
not drown the rest.

Nothing an evaluation measures is touched, as in build_train_pairs.py and train-v2/build_pairs.py: no line of the
benchmark's tractates or of its Rambam halachot, no line an evaluation judges or targets or near one (both sides), no
Gemara line holding a quote's source or matching eval-100's sources, and none near a dev question's target
(train-v2's DEV_BATCHES).
"""

import argparse
import json
import random
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "train-v2"))
from build_pairs import DEV_BATCHES, read_tsv  # noqa: E402
from build_train_pairs import MAX_PASSAGE_WORDS, MIN_WORDS, SPAN_WORDS, benchmark_held  # noqa: E402
from pardes_semantic.clean import clean  # noqa: E402
from sample_targets import held_out, neighbors  # noqa: E402

PER_LINE = 3
BOLD = re.compile(r"<b>.*?</b>|<small>.*?</small>", re.S)
# Chavruta's references in brackets ([לקמן נב ב]) and parentheses: no one asks with them
REFERENCE = re.compile(r"\[[^\]]*\]|\([^)]*\)")
WORD = re.compile(r"[\u05D0-\u05EA]{2}")
MARKS = re.compile(r"[֑-ׇ]")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--pairs", type=int, default=60_000)
    ap.add_argument("--seed", type=int, default=5811)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)

    held_books, held_lines = benchmark_held(con)
    judged, patterns, quotes = held_out(con)
    linked, near = neighbors(con, judged)
    dev = {int(q["target_line"]) for q in read_tsv(HERE / "train-v2" / "questions.tsv") if q["batch"] in DEV_BATCHES}
    dev_linked, dev_near = neighbors(con, dev)
    plain_quotes = [q for q in quotes if q]
    chavruta = {r[0] for r in con.execute("SELECT id FROM book WHERE title LIKE 'חברותא על %'")}
    bavli = {r[0] for r in con.execute("""SELECT id FROM book WHERE isBaseBook = 1 AND categoryId IN
                                          (SELECT descendantId FROM category_closure WHERE ancestorId = 13)""")}
    print(f"{len(chavruta)} Chavruta books, {len(bavli)} tractates ({len(bavli & held_books)} of the benchmark's)",
          file=sys.stderr)

    rows = con.execute(f"""SELECT k.sourceLineId, k.targetLineId, k.sourceBookId, k.targetBookId FROM link k
                           JOIN connection_type ct ON ct.id = k.connectionTypeId WHERE ct.name = 'COMMENTARY'
                           AND (k.sourceBookId IN ({','.join(map(str, chavruta))})
                                OR k.targetBookId IN ({','.join(map(str, chavruta))}))""").fetchall()
    links = set()
    for a, b, ba, bb in rows:
        if ba in chavruta and bb in bavli:
            links.add((a, b))
        elif bb in chavruta and ba in bavli:
            links.add((b, a))
    print(f"{len(links)} links of a Chavruta line to a Gemara line", file=sys.stderr)

    def line(lid):
        return con.execute("SELECT bookId, lineIndex, heRef, content FROM line WHERE id = ?", (lid,)).fetchone()

    def touched(lid, row):
        book, index, ref, content = row
        return (lid in held_lines or book in held_books or lid in linked or (book, index) in near
                or lid in dev_linked or (book, index) in dev_near)

    by_gemara = defaultdict(list)
    dropped = 0
    for c, g in sorted(links):
        rc, rg = line(c), line(g)
        if rc is None or rg is None or touched(c, rc) or touched(g, rg):
            dropped += 1
            continue
        if rg[2] and any(p.search(rg[2]) for p in patterns):
            dropped += 1
            continue
        gemara = " ".join(clean(rg[3]).split()[:MAX_PASSAGE_WORDS])
        plain = " ".join(re.sub(r"[^א-ת ]", " ", MARKS.sub("", rg[3].replace("־", " "))).split())
        if any(q in plain for q in plain_quotes):
            dropped += 1
            continue
        explanation = [w for w in clean(REFERENCE.sub(" ", BOLD.sub(" ", rc[3]))).split() if WORD.search(w)]
        if len(explanation) < SPAN_WORDS[0] or len(gemara.split()) < MIN_WORDS:
            continue
        by_gemara[g].append((explanation, gemara))
    print(f"{dropped} links left out as near what the evaluations measure; {len(by_gemara)} Gemara lines",
          file=sys.stderr)

    pairs = []
    for g in sorted(by_gemara):
        choices = by_gemara[g]
        for explanation, gemara in rng.sample(choices, min(PER_LINE, len(choices))):
            n = min(len(explanation), rng.randint(*SPAN_WORDS))
            start = rng.randint(0, len(explanation) - n)
            pairs.append({"anchor": " ".join(explanation[start:start + n]), "positive": gemara, "type": "CHAVRUTA"})
    rng.shuffle(pairs)
    pairs = pairs[:args.pairs]
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as f:
        for p in pairs:
            f.write(json.dumps(p, ensure_ascii=False) + "\n")
    print(f"{len(pairs)} pairs -> {args.out}")


if __name__ == "__main__":
    main()

"""Builds a retrieval benchmark from seforim.db itself: no hand-made answers, the library's own structure is the truth.

    python build_eval_set.py --db seforim.db --out build/eval

Two tasks over one corpus (the Shulchan Aruch and five tractates of the Bavli, ~35k lines by default):

  concept   the title of a siman of the Shulchan Aruch ("דין השכמת הבוקר") -> the seifim of that siman. The title is
            removed from the corpus text of its seif, so it cannot be found by its own words.
  source    a halacha of the Rambam (its first QUERY_WORDS words) -> the lines of the Gemara that the Ein Mishpat links
            to it: Hebrew to Aramaic, the semantic search's hardest case.

Writes corpus.jsonl ({"id", "book", "text"}: cleaned text, what embed_corpus.py embeds) and queries.jsonl
({"qid", "task", "text", "relevant": [line ids]}).
"""

import argparse
import json
import random
import re
import sqlite3
from pathlib import Path

from pardes_semantic.clean import clean

SHULCHAN_ARUCH = ("שולחן ערוך, אורח חיים", "שולחן ערוך, יורה דעה", "שולחן ערוך, אבן העזר", "שולחן ערוך, חושן משפט")
TRACTATES = ("ברכות", "שבת", "בבא קמא", "בבא מציעא", "סנהדרין")
QUERY_WORDS = 25
MIN_WORDS = 3
# "<b>דין השכמת הבוקר. ובו ט סעיפים:</b>": the title is the part before "ובו"
TITLE = re.compile(r"<b>([^<]*?)\.?\s*ובו\s[^<]*סעיפ[^<]*</b>")


def book_ids(con: sqlite3.Connection, titles: tuple[str, ...]) -> dict[int, str]:
    marks = ",".join("?" * len(titles))
    return dict(con.execute(f"SELECT id, title FROM book WHERE isBaseBook = 1 AND title IN ({marks})", titles))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--queries", type=int, default=300, help="per task")
    ap.add_argument("--seed", type=int, default=5785)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)

    sa = book_ids(con, SHULCHAN_ARUCH)
    gemara = book_ids(con, TRACTATES)
    corpus: dict[int, dict] = {}
    concept = []
    for book_id in sa:
        siman: list[int] = []
        title = None
        for line_id, content in con.execute(
                "SELECT id, content FROM line WHERE bookId = ? ORDER BY lineIndex", (book_id,)):
            if content.lstrip().startswith("<h2>"):
                if title and siman:
                    concept.append({"task": "concept", "text": title, "relevant": siman})
                siman, title = [], None
                continue
            m = TITLE.search(content)
            if m:
                title = clean(m.group(1))
                content = content[:m.start()] + content[m.end():]
            text = clean(content)
            if len(text.split()) < MIN_WORDS:
                continue
            corpus[line_id] = {"id": line_id, "book": book_id, "text": text}
            if title is not None:
                siman.append(line_id)
        if title and siman:
            concept.append({"task": "concept", "text": title, "relevant": siman})

    for book_id in gemara:
        for line_id, content in con.execute("SELECT id, content FROM line WHERE bookId = ?", (book_id,)):
            text = clean(content)
            if len(text.split()) >= MIN_WORDS:
                corpus[line_id] = {"id": line_id, "book": book_id, "text": text}

    marks = ",".join("?" * len(gemara))
    links: dict[int, set[int]] = {}
    for gemara_line, rambam_line in con.execute(
            f"""SELECT k.sourceLineId, k.targetLineId FROM link k
                JOIN connection_type ct ON ct.id = k.connectionTypeId
                JOIN book b ON b.id = k.targetBookId
                WHERE ct.name = 'EIN_MISHPAT' AND k.sourceBookId IN ({marks}) AND b.title LIKE 'משנה תורה,%'""",
            tuple(gemara)):
        if gemara_line in corpus:
            links.setdefault(rambam_line, set()).add(gemara_line)
    source = []
    for rambam_line, relevant in sorted(links.items()):
        (content,) = con.execute("SELECT content FROM line WHERE id = ?", (rambam_line,)).fetchone()
        # The halacha's number, "(א)", is not part of what a reader would ask
        words = re.sub(r"^\([^)]{1,4}\)\s*", "", clean(content)).split()
        if len(words) >= 6:
            source.append({"task": "source", "text": " ".join(words[:QUERY_WORDS]), "relevant": sorted(relevant)})

    queries = []
    for task in (concept, source):
        picked = rng.sample(task, min(args.queries, len(task)))
        queries += picked
    for i, q in enumerate(queries):
        q["qid"] = f"{q['task']}-{i}"

    args.out.mkdir(parents=True, exist_ok=True)
    with (args.out / "corpus.jsonl").open("w", encoding="utf-8") as f:
        for doc in corpus.values():
            f.write(json.dumps(doc, ensure_ascii=False) + "\n")
    with (args.out / "queries.jsonl").open("w", encoding="utf-8") as f:
        for q in queries:
            f.write(json.dumps(q, ensure_ascii=False) + "\n")
    print(f"corpus {len(corpus)} lines; concept {len(concept)} (kept {sum(q['task'] == 'concept' for q in queries)}), "
          f"source {len(source)} (kept {sum(q['task'] == 'source' for q in queries)})")


if __name__ == "__main__":
    main()

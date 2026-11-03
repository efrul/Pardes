"""The pool searches of crfh1672/search-queries' Bavli drafts (lines.tsv), for ../pool_rank.py: each question with every
line of its tractate as candidates, so that a model's ranking within the tractate is measured (measure.py), where the
near miss is the competitor that counts (the pardes-semantic-search skill).

    python spec.py --db seforim.db --drafts data/drafts --out crfh.json [--merge pool.json]

The queries are read from the drafts, as they stay out of this repository (map_lines.py). With --merge, another spec's
searches (../eval-pool/pool_spec.py's) come first in the same file, so that one pool run ranks both: keys "crfh/<id>".
"""

import argparse
import csv
import json
import sqlite3
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--drafts", type=Path, required=True)
    ap.add_argument("--lines", type=Path, default=HERE / "lines.tsv")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--merge", type=Path, help="a pool spec whose searches go first")
    args = ap.parse_args()
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    queries = {}
    for path in sorted(args.drafts.glob("תלמוד בבלי/*/*.jsonl")):
        for line in path.open(encoding="utf-8"):
            r = json.loads(line)
            queries[r["id"]] = r["query"]
    tractates = {}
    searches = json.loads(args.merge.read_text("utf-8"))["searches"] if args.merge else []
    before = len(searches)
    for r in csv.DictReader(args.lines.open(encoding="utf-8"), delimiter="\t"):
        t = r["tractate"]
        if t not in tractates:
            tractates[t] = [lid for (lid,) in con.execute(
                "SELECT l.id FROM line l JOIN book b ON b.id = l.bookId WHERE b.isBaseBook = 1 AND b.title = ? "
                "ORDER BY l.lineIndex", (t,))]
        searches.append({"key": f"crfh/{r['id']}", "query": queries[r["id"]], "candidates": tractates[t]})
    args.out.write_text(json.dumps({"searches": searches}) + "\n", "utf-8")
    print(f"{len(searches) - before} crfh searches over {sum(len(v) for v in tractates.values())} lines of "
          f"{len(tractates)} tractates" + (f", after {before} of {args.merge.name}" if args.merge else "")
          + f" -> {args.out}")


if __name__ == "__main__":
    main()

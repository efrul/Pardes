"""Feedback mode's grades (the app's search_feedback table, in the user's user_settings.db) as data for the semantic
search: an evaluation of the searches graded, training pairs with hard negatives, and what the grades say. README.md.

    python collect.py --settings user_settings.db --db seforim.db --out build/feedback [--eval-share 0.2]

The searches are the user's and the lines' text the library's, so everything is written outside the repository:
- queries.tsv: qid, query, its scope (categoryId, bookId, tocId, extended), the model it ran with, when it was graded;
- grades.tsv: qid, lineId, grade (3 exactly this, 2 relevant, 0 not relevant), rank (the result's place, from 0);
- searches.json: each search with its graded lines as candidates (../eval-passages/rank.py, and its judged.py's way of
  scoring), the searches of the evaluation only (--eval-share of them, picked by their key, so a search stays where it
  is from one collection to the next);
- pairs.jsonl: the other searches, in train_model.py's format: the query as anchor, each line graded 2 or 3 as a
  positive, the lines graded 0 in the same search as "negatives" (train-v2/build_pairs.py --negatives' shape). A search
  that is an evaluation's query, or a line an evaluation holds out, is left out, as build_chavruta_pairs.py leaves them
  out: the benchmark's books and Rambam halachot, crfh1672's tractates and a copy of their lines, the lines the
  evaluations judge or target and their neighbors, the dev questions' targets and theirs, a quote's source.
And prints what the grades say: how often the first result was good, by the query's length, and how long the lines
graded 0 are against the good ones (the semantic side's liking for very short lines).
"""

import argparse
import csv
import hashlib
import json
import re
import sqlite3
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "train-v2"))
from build_pairs import DEV_BATCHES, EVAL_QUERIES, letters, read_tsv  # noqa: E402
from build_train_pairs import MAX_PASSAGE_WORDS, benchmark_held  # noqa: E402
from pardes_semantic.clean import clean  # noqa: E402
from sample_targets import held_book_texts, held_out, neighbors, outside_set_books  # noqa: E402

GOOD = 2
SHORT_LINE_WORDS = 8  # docs/SEARCH_ORDER_ISSUES.md, 1: the short lines the semantic side brings first
SHORT_QUERY_WORDS = 3
COLUMNS = ("searchKey", "query", "lineId", "bookId", "grade", "rank", "searchCategoryId", "searchBookId", "searchTocId",
           "searchExtended", "model", "gradedAt")


def read_feedback(settings: Path) -> list[dict]:
    con = sqlite3.connect(f"file:{settings}?mode=ro", uri=True)
    if not con.execute("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'search_feedback'").fetchone():
        sys.exit(f"{settings}: no search_feedback table, so no feedback yet (Settings › ניהול נתונים, feedback mode)")
    return [dict(zip(COLUMNS, row)) for row in
            con.execute(f"SELECT {', '.join(COLUMNS)} FROM search_feedback ORDER BY gradedAt")]


def qid_of(key: str) -> str:
    return "fb-" + hashlib.sha1(key.encode("utf-8")).hexdigest()[:10]


def in_evaluation(key: str, share: float) -> bool:
    """Whether the search [key] is one of the evaluation's, by its key alone: the same answer at every collection."""
    return int(hashlib.sha1(("eval:" + key).encode("utf-8")).hexdigest()[:8], 16) / 0xFFFFFFFF < share


class HeldOut:
    """What the evaluations hold out of training (build_chavruta_pairs.py's checks), for any line."""

    def __init__(self, con: sqlite3.Connection):
        self.con = con
        self.books, self.lines = benchmark_held(con)
        self.books |= outside_set_books(con)
        self.texts = held_book_texts(con, self.books)
        judged, self.patterns, quotes = held_out(con)
        self.linked, self.near = neighbors(con, judged)
        dev = {int(q["target_line"]) for q in read_tsv(ROOT / "train-v2" / "questions.tsv") if q["batch"] in DEV_BATCHES}
        self.dev_linked, self.dev_near = neighbors(con, dev)
        self.quotes = [q for q in quotes if q]
        self.queries = {letters(r["query"]) for path in EVAL_QUERIES for r in read_tsv(path)}

    def query(self, query: str) -> bool:
        return letters(query) in self.queries

    def line(self, line_id: int, row) -> bool:
        book, index, ref, content = row
        if (line_id in self.lines or book in self.books or line_id in self.linked or (book, index) in self.near
                or line_id in self.dev_linked or (book, index) in self.dev_near):
            return True
        if ref and any(p.search(ref) for p in self.patterns):
            return True
        plain = " ".join(re.sub(r"[^א-ת ]", " ", re.sub(r"[֑-ׇ]", "", content.replace("־", " "))).split())
        return any(q in plain for q in self.quotes) or clean(content) in self.texts


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--settings", type=Path, required=True, help="the app's user_settings.db")
    ap.add_argument("--db", type=Path, required=True, help="seforim.db of the library the searches ran on")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--eval-share", type=float, default=0.2, help="the share of searches kept for the evaluation")
    args = ap.parse_args()
    rows = read_feedback(args.settings)
    if not rows:
        sys.exit("No grades yet.")
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    searches = {}
    for r in rows:
        s = searches.setdefault(r["searchKey"], {**{k: r[k] for k in COLUMNS if k not in ("lineId", "bookId", "grade",
                                                                                          "rank")}, "lines": {}})
        s["lines"][r["lineId"]] = (r["grade"], r["rank"])
        s["gradedAt"] = max(s["gradedAt"], r["gradedAt"])
    lines = {}
    ids = sorted({line for s in searches.values() for line in s["lines"]})
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        for lid, book, index, ref, content in con.execute(
                f"SELECT id, bookId, lineIndex, heRef, content FROM line WHERE id IN ({','.join('?' * len(chunk))})",
                chunk):
            lines[lid] = (book, index, ref, content or "")
    held = HeldOut(con)

    args.out.mkdir(parents=True, exist_ok=True)
    spec, pairs = [], []
    dropped = Counter()
    with (args.out / "queries.tsv").open("w", encoding="utf-8", newline="") as qf, \
            (args.out / "grades.tsv").open("w", encoding="utf-8", newline="") as gf:
        qw, gw = csv.writer(qf, delimiter="\t"), csv.writer(gf, delimiter="\t")
        qw.writerow(["qid", "query", "categoryId", "bookId", "tocId", "extended", "model", "gradedAt", "split"])
        gw.writerow(["qid", "lineId", "grade", "rank"])
        for key, s in sorted(searches.items(), key=lambda kv: kv[1]["gradedAt"]):
            qid = qid_of(key)
            evaluation = in_evaluation(key, args.eval_share)
            qw.writerow([qid, s["query"], s["searchCategoryId"] or "", s["searchBookId"] or "", s["searchTocId"] or "",
                         int(bool(s["searchExtended"])), s["model"] or "", s["gradedAt"],
                         "eval" if evaluation else "train"])
            for lid, (grade, rank) in sorted(s["lines"].items(), key=lambda kv: kv[1][1]):
                gw.writerow([qid, lid, grade, rank])
            if evaluation:
                spec.append({"key": f"feedback/{qid}", "query": s["query"], "candidates": sorted(s["lines"])})
                continue
            if held.query(s["query"]):
                dropped["an evaluation's query"] += 1
                continue

            def usable(lid):
                if lid not in lines:
                    dropped["a line not in this library"] += 1
                    return False
                if held.line(lid, lines[lid]):
                    dropped["a line an evaluation holds out"] += 1
                    return False
                return True

            good = [lid for lid, (g, _) in s["lines"].items() if g >= GOOD and usable(lid)]
            bad = [lid for lid, (g, _) in s["lines"].items() if g < GOOD and usable(lid)]

            def passage(lid):
                return " ".join(clean(lines[lid][3]).split()[:MAX_PASSAGE_WORDS])

            for lid in good:
                pairs.append({"anchor": s["query"], "positive": passage(lid), "type": f"feedback-{s['lines'][lid][0]}",
                              "negatives": [passage(b) for b in bad]})
    (args.out / "searches.json").write_text(json.dumps({"searches": spec}, ensure_ascii=False) + "\n", "utf-8")
    with (args.out / "pairs.jsonl").open("w", encoding="utf-8") as f:
        for p in pairs:
            f.write(json.dumps(p, ensure_ascii=False) + "\n")
    report(searches, lines)
    print(f"\n{len(spec)} searches for the evaluation -> {args.out / 'searches.json'}; {len(pairs)} training pairs, "
          f"{sum(len(p['negatives']) for p in pairs)} negatives -> {args.out / 'pairs.jsonl'}"
          + (f"; left out: {dict(dropped)}" if dropped else ""))


def report(searches: dict, lines: dict) -> None:
    grades = [g for s in searches.values() for g, _ in s["lines"].values()]
    print(f"{len(searches)} searches graded, {len(grades)} grades: " +
          ", ".join(f"{n} × {g}" for g, n in sorted(Counter(grades).items(), reverse=True)))
    models = Counter(s["model"] or "no model" for s in searches.values())
    print("models: " + ", ".join(f"{m} ({n})" for m, n in models.most_common()))

    # The first result graded: good or not, by the query's length
    by_length = defaultdict(lambda: [0, 0])
    for s in searches.values():
        first = [g for g, rank in s["lines"].values() if rank == 0]
        if first:
            kind = "short" if len(s["query"].split()) <= SHORT_QUERY_WORDS else "long"
            by_length[kind][0] += 1
            by_length[kind][1] += first[0] >= GOOD
    for kind, (n, good) in sorted(by_length.items()):
        print(f"queries of {'up to' if kind == 'short' else 'more than'} {SHORT_QUERY_WORDS} words, first result graded: "
              f"{n}, good {good} ({good / n:.0%})")

    # How long the lines graded 0 are, against the good ones
    words = defaultdict(list)
    for s in searches.values():
        for lid, (g, _) in s["lines"].items():
            if lid in lines:
                words["good" if g >= GOOD else "not relevant"].append(len(clean(lines[lid][3]).split()))
    for kind, ws in words.items():
        short = sum(1 for w in ws if w <= SHORT_LINE_WORDS)
        print(f"lines graded {kind}: {len(ws)}, median {statistics.median(ws)} words, "
              f"{short} ({short / len(ws):.0%}) of {SHORT_LINE_WORDS} words or fewer")


if __name__ == "__main__":
    main()

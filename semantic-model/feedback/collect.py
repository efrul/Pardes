"""Feedback mode's grades as data for the semantic search: an evaluation of the searches graded, training pairs with
hard negatives, and what the grades say. README.md. The grades come from a user's user_settings.db (the app's
search_feedback table), or from the packages users send (Settings › ניהול נתונים › שליחת משוב, pardes-feedback-*.json
files, or folders of them), or both:

    python collect.py --settings user_settings.db --db seforim.db --out build/feedback [--eval-share 0.2]
    python collect.py --packages inbox/ --db seforim.db --out build/feedback

A package holds what was new since its sender's previous one; a line graded again keeps its newest grade, each sender's
own (the senders' searches stay apart, and a query is on the same side of the evaluation for all of them).

The searches are the users' and the lines' text the library's, so everything is written outside the repository:
- queries.tsv: qid, query, its scope (categoryId, bookId, tocId, extended), the model it ran with, when it was graded,
  and its sender (the first letters of the package's sender id, "local" for --settings);
- grades.tsv: qid, lineId, grade (3 exactly this, 2 relevant, 0 not relevant), rank (the result's place, from 0);
- searches.json: each search with its graded lines as candidates (../eval-passages/rank.py, and its judged.py's way of
  scoring), the searches of the evaluation only (--eval-share of them, picked by their key, so a search stays where it
  is from one collection to the next);
- pairs.jsonl: the other searches, in train_model.py's format: the query as anchor, each line graded 2 or 3 as a
  positive, the lines graded 0 in the same search as "negatives" (train-v2/build_pairs.py --negatives' shape). A search
  that is an evaluation's query, or a line an evaluation holds out, is left out, as build_chavruta_pairs.py leaves them
  out: the benchmark's books and Rambam halachot, crfh1672's tractates and a copy of their lines, the lines the
  evaluations judge or target and their neighbors, the dev questions' targets and theirs, a quote's source.
- history.tsv: the searches made, of the packages that hold them (their senders chose to send them) and of --settings:
  sender, query, scope, how many times, when last;
- daf/<Tractate>.json: the daf view's line review of the packages, in its own format (the app's pardes-daf/feedback/,
  pardes-rashi-ocr's feedback.py reads it), one file a tractate, each line's newest mark of any sender.
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
from datetime import datetime
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


LOCAL = "local"  # the sender of --settings' grades
HISTORY = ("query", "categoryId", "bookId", "tocId", "extended", "count", "lastAt")


def read_feedback(settings: Path) -> tuple[list[dict], list[dict]]:
    """The grades and the searches of a user_settings.db, as a package holds them, from the LOCAL sender."""
    con = sqlite3.connect(f"file:{settings}?mode=ro", uri=True)
    if not con.execute("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'search_feedback'").fetchone():
        sys.exit(f"{settings}: no search_feedback table, so no feedback yet (Settings › ניהול נתונים, feedback mode)")
    grades = [{**dict(zip(COLUMNS, row)), "sender": LOCAL} for row in
              con.execute(f"SELECT {', '.join(COLUMNS)} FROM search_feedback ORDER BY gradedAt")]
    if not con.execute("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'visit_history'").fetchone():
        return grades, []
    history = [{**dict(zip(HISTORY, row)), "sender": LOCAL} for row in con.execute(
        "SELECT searchQuery, searchCategoryId, searchBookId, searchTocId, searchExtended, visitCount, visitedAt "
        "FROM visit_history WHERE kind = 'search' AND trim(coalesce(searchQuery, '')) != '' ORDER BY visitedAt")]
    return grades, history


def package_files(paths: list[Path]) -> list[Path]:
    files = []
    for path in paths:
        files += sorted(path.glob("pardes-feedback-*.json")) if path.is_dir() else [path]
    return files


def read_packages(paths: list[Path]) -> tuple[list[dict], list[dict], list[tuple[str, dict]], Counter]:
    """The grades, the searches and the daf reviews of the packages in [paths], each with its sender, and how many
    packages each sender sent."""
    grades, history, daf, senders = [], [], [], Counter()
    for file in package_files(paths):
        try:
            package = json.loads(file.read_text("utf-8"))
        except (OSError, ValueError) as e:
            print(f"{file}: not read ({e})", file=sys.stderr)
            continue
        if package.get("app") != "Pardes" or package.get("format") != 1 or not package.get("sender"):
            print(f"{file}: not a feedback package of Pardes (format 1)", file=sys.stderr)
            continue
        sender = package["sender"]
        senders[sender] += 1
        for g in package.get("grades", []):
            grades.append({"searchKey": g["search"], "query": g["query"], "lineId": g["lineId"], "bookId": g["bookId"],
                           "grade": g["grade"], "rank": g["rank"], "searchCategoryId": g.get("categoryId"),
                           "searchBookId": g.get("scopeBookId"), "searchTocId": g.get("tocId"),
                           "searchExtended": int(bool(g.get("extended"))), "model": g.get("model"),
                           "gradedAt": g["gradedAt"], "sender": sender})
        history += [{**{k: h.get(k) for k in HISTORY}, "sender": sender} for h in package.get("searches", [])]
        daf += [(sender, review) for review in package.get("daf", [])]
    return grades, history, daf, senders


def newest(rows: list[dict], key, time: str) -> list[dict]:
    """Of the rows with the same [key], the one of the newest [time]: a package sends again what changed."""
    kept = {}
    for row in rows:
        k = key(row)
        if k not in kept or row[time] >= kept[k][time]:
            kept[k] = row
    return sorted(kept.values(), key=lambda r: r[time])


def sender_of(sender: str) -> str:
    return sender if sender == LOCAL else sender[:8]


def qid_of(key: str, sender: str = LOCAL) -> str:
    """The search's id: its key's, a sender's own but for --settings' (the ids of the collections before packages)."""
    return "fb-" + hashlib.sha1((key if sender == LOCAL else f"{sender}|{key}").encode("utf-8")).hexdigest()[:10]


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
    ap.add_argument("--settings", type=Path, help="the app's user_settings.db")
    ap.add_argument("--packages", type=Path, nargs="+", default=[],
                    help="feedback packages users sent (pardes-feedback-*.json), or folders of them")
    ap.add_argument("--db", type=Path, required=True, help="seforim.db of the library the searches ran on")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--eval-share", type=float, default=0.2, help="the share of searches kept for the evaluation")
    args = ap.parse_args()
    if not args.settings and not args.packages:
        ap.error("--settings, --packages or both")
    rows, history = read_feedback(args.settings) if args.settings else ([], [])
    grades, sent, daf, senders = read_packages(args.packages)
    if args.packages:
        print(f"{sum(senders.values())} packages from {len(senders)} senders")
    rows = newest(rows + grades, key=lambda r: (r["sender"], r["searchKey"], r["lineId"]), time="gradedAt")
    history = newest(history + sent, key=lambda h: (h["sender"],) + tuple(h[k] for k in HISTORY[:5]), time="lastAt")
    args.out.mkdir(parents=True, exist_ok=True)
    write_history(history, args.out)
    write_daf(daf, args.out)
    if not rows:
        sys.exit("No grades yet.")
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    searches = {}
    for r in rows:
        s = searches.setdefault((r["sender"], r["searchKey"]), {
            **{k: r[k] for k in COLUMNS if k not in ("lineId", "bookId", "grade", "rank")}, "sender": r["sender"],
            "lines": {}})
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

    spec, pairs = [], []
    dropped = Counter()
    with (args.out / "queries.tsv").open("w", encoding="utf-8", newline="") as qf, \
            (args.out / "grades.tsv").open("w", encoding="utf-8", newline="") as gf:
        qw, gw = csv.writer(qf, delimiter="\t"), csv.writer(gf, delimiter="\t")
        qw.writerow(["qid", "query", "categoryId", "bookId", "tocId", "extended", "model", "gradedAt", "split",
                     "sender"])
        gw.writerow(["qid", "lineId", "grade", "rank"])
        for (sender, key), s in sorted(searches.items(), key=lambda kv: kv[1]["gradedAt"]):
            qid = qid_of(key, sender)
            # By the key alone: a query is on the same side for every sender
            evaluation = in_evaluation(key, args.eval_share)
            qw.writerow([qid, s["query"], s["searchCategoryId"] or "", s["searchBookId"] or "", s["searchTocId"] or "",
                         int(bool(s["searchExtended"])), s["model"] or "", s["gradedAt"],
                         "eval" if evaluation else "train", sender_of(sender)])
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


def write_history(history: list[dict], out: Path) -> None:
    """The searches made, and how many of them are short: the semantic side is weakest there (SEARCH_ORDER_ISSUES 1)."""
    if not history:
        return
    with (out / "history.tsv").open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(["sender"] + list(HISTORY))
        for h in history:
            w.writerow([sender_of(h["sender"])] + ["" if h[k] is None else int(h[k]) if k == "extended" else h[k]
                                                   for k in HISTORY])
    queries = Counter(" ".join(h["query"].split()) for h in history)
    short = sum(1 for q in queries if len(q.split()) <= SHORT_QUERY_WORDS)
    print(f"{len(history)} searches made, {len(queries)} queries, {short} ({short / len(queries):.0%}) of up to "
          f"{SHORT_QUERY_WORDS} words -> {out / 'history.tsv'}")


def marked_at(at: str) -> datetime:
    """A mark's time (Instant.toString: Z, and up to nine digits of a second, which Python reads up to six of)."""
    return datetime.fromisoformat(re.sub(r"(\.\d{6})\d+", r"\1", at.replace("Z", "+00:00")))


def write_daf(daf: list[tuple[str, dict]], out: Path) -> None:
    """The packages' daf reviews, one file a tractate in the app's format, each line's newest mark of any sender; and
    how many lines were marked by more than one sender, and how many of those differently."""
    if not daf:
        return
    tractates, marks = {}, defaultdict(dict)
    for sender, review in daf:
        tractates.setdefault(review["tractate"], review)
        for m in review.get("marks", []):
            line = (review["tractate"], m["amud"], m["c"], m["i"], m["t"])
            marks[line][sender] = max(marks[line].get(sender, m), m, key=lambda x: marked_at(x["at"]))
    folder = out / "daf"
    folder.mkdir(exist_ok=True)
    by_tractate = defaultdict(list)
    shared = differ = 0
    for line, of in marks.items():
        newest_mark = max(of.values(), key=lambda x: marked_at(x["at"]))
        by_tractate[line[0]].append(newest_mark)
        if len(of) > 1:
            shared += 1
            differ += len({(m.get("ok"), m.get("cut", 0)) for m in of.values()}) > 1
    for tractate, kept in sorted(by_tractate.items()):
        review = tractates[tractate]
        kept.sort(key=lambda m: (m["amud"], m["c"], m["i"]))
        (folder / f"{tractate}.json").write_text(json.dumps(
            {"format": 1, "tractate": tractate, "tractateHe": review.get("tractateHe", tractate),
             "library": review.get("library"), "marks": kept}, ensure_ascii=False, indent=2) + "\n", "utf-8")
    print(f"{len(marks)} daf lines marked in {len(by_tractate)} tractates -> {folder}"
          + (f"; {shared} lines marked by more than one sender, {differ} of them differently" if shared else ""))


def report(searches: dict, lines: dict) -> None:
    grades = [g for s in searches.values() for g, _ in s["lines"].values()]
    print(f"{len(searches)} searches graded, {len(grades)} grades: " +
          ", ".join(f"{n} × {g}" for g, n in sorted(Counter(grades).items(), reverse=True)))
    graders = Counter(sender_of(s["sender"]) for s in searches.values())
    if len(graders) > 1 or LOCAL not in graders:
        print("senders: " + ", ".join(f"{s} ({n})" for s, n in graders.most_common()))
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

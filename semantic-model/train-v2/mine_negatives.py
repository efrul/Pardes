"""Hard negatives for the training questions: lines the current model ranks high for a question and that are not its
answer, for the loss to push away from the question.

    python mine_negatives.py candidates.jsonl seforim.db --out negatives.tsv [--per 3] [--depth 30]

candidates.jsonl: the questions run through the search (../eval-100/harness/Cands.kt on questions.tsv, scope auto, with
the dense side; the harness's id is the number of the question's id). From the dense side's candidates, in its order, a
line is left out when it may answer the question too, or teaches nothing:
- the target itself, a line linked to it (both ways: its commentary, its source, a parallel the library links), a line
  of the same book within NEAR of it, a line under the same table-of-contents entry (line_toc);
- a line sharing SHARED words in a row with the target, spelling apart: a copy of it in another book;
- a line an evaluation judges or targets, or near one (sample_targets.py's held_out and neighbors), or holding one of
  the quotes' sources (build_pairs.py's check): the loss would move it too;
- a line holding the question as a phrase, spelling apart (../eval-real/level_order.py's holds_phrase);
- a heading (../eval-real/headings.py).

The lines that pass are written in order, with a grade to give after reading them, on the evaluations' scale (0 not
relevant, 1 related, 2 answers, 3 the source). A negative is a line graded 0 or 1: one graded 2 or 3 answers the
question, and stays in the file only so as not to be mined again. When --out exists, its grades are kept, and the
lines are written until each question has --per lines graded 0 or 1 or not graded yet, among the first --depth: mining
again after grading adds the lines to grade in place of the answers. negatives.tsv: id, rank (on the dense side, from
1), lineId, ref, grade; no text.
"""

import argparse
import csv
import json
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "eval-real"))
from headings import is_heading  # noqa: E402
from level_order import fetch_lines, holds_phrase, key, skeleton  # noqa: E402
from sample_targets import held_out, neighbors  # noqa: E402

NEAR = 3
SHARED = 5


def runs_of(text, n):
    """The runs of [n] words in a row of [text], each word as its skeleton (full and defective spelling the same)."""
    words = [skeleton(w) for w in key(text).split()]
    return {tuple(words[i:i + n]) for i in range(len(words) - n + 1)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("candidates", type=Path)
    ap.add_argument("db", type=Path)
    ap.add_argument("--questions", type=Path, default=HERE / "questions.tsv")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--per", type=int, default=3)
    ap.add_argument("--depth", type=int, default=30)
    ap.add_argument("--cache", help="the lines' cache of level_order.py")
    args = ap.parse_args()
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    questions = {int(q["id"].split("-")[1]): q for q in csv.DictReader(
        open(args.questions, encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE)}
    runs = {r["id"]: r for r in map(json.loads, open(args.candidates, encoding="utf-8"))}
    graded = {}
    if args.out.exists():
        for r in csv.DictReader(open(args.out, encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE):
            if r["grade"]:
                graded[(r["id"], int(r["lineId"]))] = int(r["grade"])

    targets = {int(q["target_line"]) for q in questions.values()}
    pool = {d for r in runs.values() for d in r["denseCand"][:args.depth]}
    lines = fetch_lines(con, targets | pool, args.cache)

    def related(target):
        """The lines that may answer what [target] answers: its links, its neighbors, its section."""
        out = {target}
        out |= {r[0] for r in con.execute("SELECT targetLineId FROM link WHERE sourceLineId = ?", (target,))}
        out |= {r[0] for r in con.execute("SELECT sourceLineId FROM link WHERE targetLineId = ?", (target,))}
        book, index = con.execute("SELECT bookId, lineIndex FROM line WHERE id = ?", (target,)).fetchone()
        out |= {r[0] for r in con.execute("SELECT id FROM line WHERE bookId = ? AND lineIndex BETWEEN ? AND ?",
                                          (book, index - NEAR, index + NEAR))}
        same_entry = """SELECT b.lineId FROM line_toc a JOIN line_toc b ON b.tocEntryId = a.tocEntryId
                        WHERE a.lineId = ?"""
        out |= {r[0] for r in con.execute(same_entry, (target,))}
        return out

    toc = {r[0] for r in con.execute(
        f"SELECT lineId FROM tocEntry WHERE lineId IN ({','.join(map(str, sorted(pool)))})")} if pool else set()
    held, patterns, quotes = held_out(con)
    linked, near = neighbors(con, held)
    place = {}
    ids = sorted(pool)
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        place |= {r[0]: (r[1], r[2]) for r in con.execute(
            f"SELECT id, bookId, lineIndex FROM line WHERE id IN ({','.join('?' * len(chunk))})", chunk)}

    def plain(content):
        return " ".join(re.sub(r"[^א-ת ]", " ", re.sub(r"[֑-ׇ]", "", content.replace("־", " "))).split())

    def evaluated(d):
        ref, content = lines[d][1], lines[d][2]
        return (d in linked or place.get(d) in near or bool(ref and any(p.search(ref) for p in patterns))
                or any(k in plain(content) for k in quotes))
    skip_of, shared_of = {}, {}
    written = to_grade = short = 0
    with args.out.open("w", encoding="utf-8") as f:
        f.write("id\trank\tlineId\tref\tgrade\n")
        for n, q in sorted(questions.items()):
            run = runs.get(n)
            if run is None:
                continue
            target = int(q["target_line"])
            if target not in skip_of:
                skip_of[target] = related(target)
                shared_of[target] = runs_of(lines[target][2], SHARED)
            kept = 0
            for rank, d in enumerate(run["denseCand"][:args.depth], 1):
                if d in skip_of[target] or d not in lines or evaluated(d):
                    continue
                text = lines[d][2]
                if (is_heading(text, d in toc) or holds_phrase(q["query"], text, spelling=True)
                        or runs_of(text, SHARED) & shared_of[target]):
                    continue
                grade = graded.get((q["id"], d))
                ref = " ".join((lines[d][1] or "").split())
                f.write(f"{q['id']}\t{rank}\t{d}\t{ref}\t{'' if grade is None else grade}\n")
                written += 1
                to_grade += grade is None
                if grade is None or grade <= 1:
                    kept += 1
                    if kept == args.per:
                        break
            short += kept < args.per
    print(f"{written} lines for {len(runs)} questions, {to_grade} to grade ({short} questions with fewer than "
          f"{args.per} negatives) -> {args.out}")


if __name__ == "__main__":
    main()

"""The training pairs of the questions (questions.tsv): each question with its target line's text, cleaned as
build_train_pairs.py cleans the links' passages, in the same jsonl (anchor, positive, type) train_model.py reads.

    python build_pairs.py --db seforim.db --out build/train/questions.jsonl [--negatives negatives.tsv]
        [--hold-benchmark] [--hold-dev]

With --negatives, each pair also carries its question's hard negatives (mine_negatives.py's lines graded 0 or 1),
cleaned the same way, as "negatives": a list, short or empty for a question with fewer.

Before writing, it checks that the evaluations stay clean, and stops when one check fails:
- no target, and no negative, is or is near a line an evaluation judges or targets (sample_targets.py's held-out lines,
  their links and the lines within NEAR of them, eval-100's patterns, the quotes' sources);
- no query is one of the evaluations' queries (eval-real, eval-v1, eval-100, eval-real/quotes), letters and digits
  compared;
- every id is unique, and every target line exists.
With --hold-benchmark, the questions whose target is in what ../build_eval_set.py's benchmark measures are left out, and
such negatives, as build_train_pairs.py leaves out such links: a training run that measures on that benchmark trains
with no line of it. With --hold-dev, the questions of the dev batches (DEV_BATCHES) are left out: a run trained without
them is measured on them (eval-pool's dev searches).
The pairs can be appended to build_train_pairs.py's (cat): the question's style is in type, as "question-K".
"""

import argparse
import csv
import json
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
from build_train_pairs import MAX_PASSAGE_WORDS, benchmark_held  # noqa: E402
from pardes_semantic.clean import clean  # noqa: E402
from sample_targets import held_out, neighbors  # noqa: E402

EVAL_QUERIES = [HERE.parent / "eval-real" / "queries.tsv", HERE.parent / "eval-v1" / "queries.tsv",
                HERE.parent / "eval-100" / "queries.tsv", *sorted((HERE.parent / "eval-real" / "quotes").glob("*.tsv"))]
# The batches a training run leaves out with --hold-dev, to be measured on
DEV_BATCHES = ("8", "9")


def read_tsv(path):
    return list(csv.DictReader(open(path, encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE))


def letters(s):
    return " ".join(re.sub(r"[^א-תa-z0-9 ]", " ", s.lower()).split())


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--questions", type=Path, default=HERE / "questions.tsv")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--negatives", type=Path, help="mine_negatives.py's negatives.tsv")
    ap.add_argument("--hold-benchmark", action="store_true", help="leave out the benchmark's lines (build_eval_set.py)")
    ap.add_argument("--hold-dev", action="store_true", help="leave out the dev batches' questions (DEV_BATCHES)")
    args = ap.parse_args()
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    questions = read_tsv(args.questions)

    problems = []
    ids = [q["id"] for q in questions]
    if len(set(ids)) != len(ids):
        problems.append("ids repeat")
    held, patterns, quotes = held_out(con)
    linked, near = neighbors(con, held)
    evals = {letters(r["query"]) for path in EVAL_QUERIES for r in read_tsv(path)}
    negatives = defaultdict(list)
    if args.negatives:
        for r in read_tsv(args.negatives):
            if r["grade"] and int(r["grade"]) <= 1:
                negatives[r["id"]].append(int(r["lineId"]))
    if args.hold_benchmark:
        held_books, held_lines = benchmark_held(con)

        def benchmark(line):
            row = con.execute("SELECT bookId FROM line WHERE id = ?", (line,)).fetchone()
            return line in held_lines or (row is not None and row[0] in held_books)

        kept = [q for q in questions if not benchmark(int(q["target_line"]))]
        dropped = 0
        for qid, lines in negatives.items():
            negatives[qid] = [line for line in lines if not benchmark(line)]
            dropped += len(lines) - len(negatives[qid])
        print(f"the benchmark's lines held out: {len(questions) - len(kept)} questions, {dropped} negatives")
        questions = kept
    if args.hold_dev:
        kept = [q for q in questions if q["batch"] not in DEV_BATCHES]
        print(f"the dev batches held out ({', '.join(DEV_BATCHES)}): {len(questions) - len(kept)} questions")
        questions = kept
    texts = {}

    def check(qid, line):
        """The line's cleaned text into texts, or what keeps it out of training into problems."""
        row = con.execute("SELECT bookId, lineIndex, heRef, content FROM line WHERE id = ?", (line,)).fetchone()
        if row is None:
            problems.append(f"{qid}: no line {line}")
            return
        book, index, ref, content = row
        if line in linked or (book, index) in near or (ref and any(p.search(ref) for p in patterns)):
            problems.append(f"{qid}: line {line} ({ref}) is or is near a line an evaluation uses")
        plain = " ".join(re.sub(r"[^א-ת ]", " ", re.sub(r"[֑-ׇ]", "", content.replace("־", " "))).split())
        if any(k in plain for k in quotes):
            problems.append(f"{qid}: line {line} holds one of the quotes' sources")
        texts[line] = clean(content)

    for q in questions:
        check(q["id"], int(q["target_line"]))
        for line in negatives[q["id"]]:
            check(q["id"], line)
        if letters(q["query"]) in evals:
            problems.append(f"{q['id']}: '{q['query']}' is an evaluation's query")
    if problems:
        print("\n".join(problems))
        sys.exit(1)

    def passage(line):
        return " ".join(texts[line].split()[:MAX_PASSAGE_WORDS])

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as f:
        for q in questions:
            pair = {"anchor": q["query"], "positive": passage(int(q["target_line"])), "type": f"question-{q['style']}"}
            if args.negatives:
                pair["negatives"] = [passage(line) for line in negatives[q["id"]]]
            f.write(json.dumps(pair, ensure_ascii=False) + "\n")
    print(f"{len(questions)} questions, {len({int(q['target_line']) for q in questions})} target lines, "
          f"{sum(len(negatives[q['id']]) for q in questions)} negatives -> {args.out}")


if __name__ == "__main__":
    main()

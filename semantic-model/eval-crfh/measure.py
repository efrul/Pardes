"""Where each model puts crfh1672/search-queries' correct line, within the tractate, and how often its near miss comes
first (README.md): from ../pool_rank.py's ranks files of spec.py's searches.

    python measure.py ranks-base.json ranks-tuned.json ... [--md]

A model is named by its file (ranks-<name>.json). The correct line counts at its place in the model's order of the
tractate's lines; a line too short for the index (pool_rank.py leaves it out) counts as missed. Each pair of models is
compared question by question, on the correct line first and on it in the first ten: the questions only one of them
gets, and whether the difference passes twice its noise (2 * sqrt(b + c), McNemar's), the bar TRAINING_PLAN.md set
before the runs. Dicta's model already has 365 of the 422 in the first ten (results.md): the first place is the one
with room.
"""

import argparse
import csv
import json
import math
from itertools import combinations
from pathlib import Path

HERE = Path(__file__).resolve().parent


def place(ids, line):
    try:
        return ids.index(line) + 1
    except ValueError:
        return None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("ranks", nargs="+", type=Path)
    ap.add_argument("--lines", type=Path, default=HERE / "lines.tsv")
    ap.add_argument("--md", action="store_true", help="markdown tables")
    args = ap.parse_args()
    questions = list(csv.DictReader(args.lines.open(encoding="utf-8"), delimiter="\t"))
    models = {}
    for path in args.ranks:
        ranks = json.loads(path.read_text("utf-8"))
        name = path.stem.removeprefix("ranks-")
        found = {}
        for q in questions:
            r = ranks.get(f"crfh/{q['id']}")
            if r is None:
                continue
            found[q["id"]] = (place(r["ids"], int(q["correct_line"])), place(r["ids"], int(q["near_miss_line"])))
        models[name] = found

    def row(name, kind):
        qs = [q for q in questions if kind in ("all", q["type"]) and q["id"] in models[name]]
        places = [models[name][q["id"]] for q in qs]
        hits = [sum(1 for c, _ in places if c and c <= k) for k in (1, 3, 10)]
        mrr = sum(1 / c for c, _ in places if c and c <= 10) / max(1, len(places))
        before = sum(1 for c, n in places if n and (not c or n < c))
        return [name, kind, len(qs), *hits, f"{mrr:.3f}", before]

    head = ["model", "questions", "n", "@1", "@3", "@10", "MRR@10", "near miss first"]
    rows = [row(name, kind) for kind in ("all", "conceptual", "short") for name in models]
    pairs = []
    for a, b in combinations(models, 2):
        common = [q["id"] for q in questions if q["id"] in models[a] and q["id"] in models[b]]
        for k in (1, 10):
            def within(name, qid):
                c = models[name][qid][0]
                return bool(c and c <= k)

            only_a = sum(1 for qid in common if within(a, qid) and not within(b, qid))
            only_b = sum(1 for qid in common if within(b, qid) and not within(a, qid))
            bar = 2 * math.sqrt(only_a + only_b)
            verdict = ("within the noise" if abs(only_a - only_b) < bar or only_a == only_b
                       else f"{a} better" if only_a > only_b else f"{b} better")
            pairs.append([f"{a} / {b}", f"@{k}", only_a, only_b, f"{bar:.1f}", verdict])
    if args.md:
        print("| " + " | ".join(head) + " |\n|" + "---|" * len(head))
        for r in rows:
            print("| " + " | ".join(map(str, r)) + " |")
        print("\n| models | place | only the first | only the second | noise bar | verdict |")
        print("|---|---|---|---|---|---|")
        for p in pairs:
            print("| " + " | ".join(map(str, p)) + " |")
    else:
        for r in [head] + rows:
            print("\t".join(map(str, r)))
        print()
        for p in pairs:
            print("\t".join(map(str, p)))


if __name__ == "__main__":
    main()

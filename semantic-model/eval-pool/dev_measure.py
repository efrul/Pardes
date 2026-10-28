"""The training questions held out (train-v2's DEV_BATCHES): where each model puts the question's target line, on its
dense side alone and on the app's fused page (README.md).

    python dev_measure.py --results dev.jsonl ranks-base.json ranks-teacher.json ranks-student.json ranks-tuned.json

dev.jsonl is the harness's run of the dev questions (expected: the target line, group: the question's style). The first
row is the app as it ran (its dense side over the whole library); each ranks file gives a model's order of the search's
candidates (pool_spec.py's dev run: the app's 150 of each side, and the target). The fused page is the app's RRF:
K = 60, the dense side at weight 3, the first 150 of each side. A target past the tenth place counts 0 in the MRR.
"""

import argparse
import json
from collections import defaultdict
from pathlib import Path

from pool_spec import CANDIDATES

K, WEIGHT = 60, 3.0


def fused(lexical, dense):
    score = defaultdict(float)
    for weight, side in ((1.0, lexical), (WEIGHT, dense)):
        for i, line in enumerate(side[:CANDIDATES]):
            score[line] += weight / (K + i + 1)
    return sorted(score, key=lambda line: -score[line])


def place(order, line):
    return order.index(line) + 1 if line in order else None


def summary(places):
    hits = [sum(1 for p in places if p and p <= k) for k in (1, 3, 10)]
    mrr = sum(1 / p for p in places if p and p <= 10) / len(places)
    return f"{hits[0]:4d} {hits[1]:4d} {hits[2]:4d} {mrr:.3f}"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", type=Path, required=True)
    ap.add_argument("ranks", type=Path, nargs="*")
    args = ap.parse_args()
    runs = [json.loads(line) for line in open(args.results, encoding="utf-8")]
    models = [("app", {r["id"]: r["denseCand"] for r in runs})]
    for path in args.ranks:
        ranks = json.loads(path.read_text("utf-8"))
        models.append((path.stem.removeprefix("ranks-"), {r["id"]: ranks[f"dev/{r['id']}"]["ids"] for r in runs}))
    print(f"{len(runs)} questions; the target first, in 3, in 10, MRR@10")
    print(f"{'':10s} {'dense alone':>21s}   {'the fused page':>21s}")
    styles = defaultdict(lambda: defaultdict(list))
    for name, dense in models:
        alone, page = [], []
        for r in runs:
            target = int(r["expected"])
            alone.append(place(dense[r["id"]], target))
            page.append(place(fused(r["lexCand"], dense[r["id"]]), target))
            styles[r["group"]][name].append(page[-1])
        print(f"{name:10s} {summary(alone)}   {summary(page)}")
    print("\nthe fused page, the target in 10, by the question's style:")
    print(f"{'':6s}" + "".join(f"{name:>10s}" for name, _ in models))
    for style in sorted(styles, key=lambda s: -len(styles[s]["app"])):
        cells = "".join(f"{sum(1 for p in styles[style][name] if p and p <= 10):>5d}/{len(styles[style][name]):<4d}"
                        for name, _ in models)
        print(f"{style:6s}{cells}")


if __name__ == "__main__":
    main()

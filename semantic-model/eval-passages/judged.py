"""The graded searches (eval-real, eval-v1's second batch) with the lines judged for each as its candidates: does a long
line's parts' vectors (rank.py) put a good line first more often among the lines we know the grade of, and does a long
line no one should see (graded 0) come first more often? A reordering of the judged lines only: no unjudged line takes
part, so it measures the order the parts give, not what they would find over the whole library.

    python judged.py spec --out judged.json
    python judged.py score judged.json build/passages/judged/ranks-*.json [--tokens build/passages/judged/tokens.json]

score: per set, as ../eval-real/score.py counts them: the first line good (grade 2 or 3), a good one in the first three,
the source (3) in the first three, nDCG@3 (gain 2^grade - 1, the ideal from every line judged for the search); then,
against ranks-line.json, the searches whose first line only one of the two has good, and whether the difference passes
twice its noise (McNemar's, as ../eval-crfh/measure.py); and with --tokens, the searches whose first line is a line of
more than 256 tokens graded 0.
"""

import argparse
import csv
import json
import math
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT / "eval-real"))
from score import NOISE, dcg  # noqa: E402

SETS = {
    "real": (ROOT / "eval-real" / "queries.tsv", ROOT / "eval-real" / "grades.tsv", lambda r: int(r["id"])),
    "v1": (ROOT / "eval-v1" / "queries.tsv", ROOT / "eval-v1" / "grades.tsv", lambda r: int(r["id"].split("-")[1])),
}
LONG = 256


def read_tsv(path: Path):
    return list(csv.DictReader(path.open(encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE))


def grades_of(path: Path) -> dict[int, dict[int, int]]:
    out = defaultdict(dict)
    for r in read_tsv(path):
        out[int(r["qid"])][int(r["lineId"])] = int(r["grade"])
    return out


def spec(out: Path) -> None:
    searches = []
    for name, (queries, grades, qid_of) in SETS.items():
        graded = grades_of(grades)
        for r in read_tsv(queries):
            qid = qid_of(r)
            if qid in NOISE and name == "real" or qid not in graded:
                continue
            searches.append({"key": f"{name}/{qid}", "query": r["query"], "candidates": sorted(graded[qid])})
    out.write_text(json.dumps({"searches": searches}, ensure_ascii=False) + "\n", "utf-8")
    print(f"{len(searches)} searches, {sum(len(s['candidates']) for s in searches)} judged lines -> {out}")


def score(spec_path: Path, ranks_paths: list[Path], tokens_path: Path | None) -> None:
    searches = json.loads(spec_path.read_text("utf-8"))["searches"]
    grades = {}
    for name, (_, path, _) in SETS.items():
        for qid, g in grades_of(path).items():
            grades[f"{name}/{qid}"] = g
    tokens = {int(k): v for k, v in json.loads(tokens_path.read_text("utf-8")).items()} if tokens_path else {}
    models = {p.stem.removeprefix("ranks-"): json.loads(p.read_text("utf-8")) for p in ranks_paths}
    keys = {name: [s["key"] for s in searches if s["key"].startswith(name + "/")] for name in SETS}

    def first_good(ranks, key):
        ids = ranks[key]["ids"]
        return bool(ids) and grades[key].get(ids[0], 0) >= 2

    print("| set | ranking | searches | first good | good in 3 | source in 3 | nDCG@3 | long line graded 0 first |")
    print("|---|---|---|---|---|---|---|---|")
    for name, ks in keys.items():
        for model, ranks in models.items():
            first = three = source = 0
            ndcg, long_zero = [], 0
            for key in ks:
                g = grades[key]
                top = [g.get(d, 0) for d in ranks[key]["ids"][:3]]
                first += bool(top) and top[0] >= 2
                three += max(top, default=0) >= 2
                source += max(top, default=0) == 3
                ideal = dcg(sorted(g.values(), reverse=True)[:3])
                ndcg.append(dcg(top) / ideal if ideal else 0.0)
                ids = ranks[key]["ids"]
                long_zero += bool(ids) and tokens.get(ids[0], 0) > LONG and g.get(ids[0], 0) == 0
            print(f"| {name} | {model} | {len(ks)} | {first} | {three} | {source} | {sum(ndcg) / len(ndcg):.3f} | "
                  f"{long_zero if tokens else ''} |")
    if "line" in models:
        print("\n| set | ranking | first good: only today's | only this one | noise bar | verdict |")
        print("|---|---|---|---|---|---|")
        for name, ks in keys.items():
            for model, ranks in models.items():
                if model == "line":
                    continue
                only_line = sum(1 for k in ks if first_good(models["line"], k) and not first_good(ranks, k))
                only_this = sum(1 for k in ks if first_good(ranks, k) and not first_good(models["line"], k))
                bar = 2 * math.sqrt(only_line + only_this)
                verdict = ("within the noise" if abs(only_this - only_line) < bar
                           else "better" if only_this > only_line else "worse")
                print(f"| {name} | {model} | {only_line} | {only_this} | {bar:.1f} | {verdict} |")


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("spec")
    a.add_argument("--out", type=Path, required=True)
    b = sub.add_parser("score")
    b.add_argument("spec", type=Path)
    b.add_argument("ranks", nargs="+", type=Path)
    b.add_argument("--tokens", type=Path)
    args = ap.parse_args()
    if args.cmd == "spec":
        spec(args.out)
    else:
        score(args.spec, args.ranks, args.tokens)


if __name__ == "__main__":
    main()

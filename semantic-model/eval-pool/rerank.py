"""A run of the evaluations with its dense side ranked by another model (pool_rank.py), for the evaluations' scripts
to measure the app's page with it (README.md).

    python rerank.py --ranks ranks-tuned.json --name real --results full.jsonl --out full-tuned.jsonl

Each search keeps its lexical candidates; its dense ones become the model's order of its candidates (lexical and dense,
pool_spec.py), the first 150, and their scores.
"""

import argparse
import json
from pathlib import Path

from pool_spec import CANDIDATES


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ranks", type=Path, required=True)
    ap.add_argument("--name", required=True, help="the NAME the run has in pool_spec.py")
    ap.add_argument("--results", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    ranks = json.loads(args.ranks.read_text("utf-8"))
    with args.out.open("w", encoding="utf-8") as f:
        for line in open(args.results, encoding="utf-8"):
            r = json.loads(line)
            ranked = ranks[f"{args.name}/{r['id']}"]
            r["denseCand"], r["denseScores"] = ranked["ids"][:CANDIDATES], ranked["scores"][:CANDIDATES]
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()

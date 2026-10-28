"""The searches of the evaluations with their candidates, for pool_rank.py to rank by a model (README.md).

    python pool_spec.py --out pool.json real=full.jsonl v1=batch2-full.jsonl e100=eval100-full.jsonl ...

Each NAME=results.jsonl is a run of the evaluations' harness (../eval-100/harness/Cands.kt): a search's candidates
are its lexical and dense ones, the app's 150 of each, in the scope the search ran in. The run named dev, the training
questions held out (train-v2's DEV_BATCHES), has each question's target line (its expected) among its candidates too,
so that every model ranks it. pool.json holds the queries and the candidates' line ids only, no text:
{"searches": [{"key": "NAME/id", "query", "candidates"}]}.
"""

import argparse
import json
from pathlib import Path

CANDIDATES = 150


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("runs", nargs="+", help="NAME=results.jsonl")
    args = ap.parse_args()
    searches = []
    for run in args.runs:
        name, path = run.split("=", 1)
        for line in open(path, encoding="utf-8"):
            r = json.loads(line)
            target = [int(r["expected"])] if name == "dev" else []
            candidates = list(dict.fromkeys(r["lexCand"][:CANDIDATES] + r["denseCand"][:CANDIDATES] + target))
            searches.append({"key": f"{name}/{r['id']}", "query": r["query"], "candidates": candidates})
    args.out.write_text(json.dumps({"searches": searches}) + "\n", "utf-8")
    lines = {d for s in searches for d in s["candidates"]}
    print(f"{len(searches)} searches, {len(lines)} candidate lines -> {args.out}")


if __name__ == "__main__":
    main()

"""The quote searches of ../eval-real/quotes/ (the Gemara's, the Mishnah's, verses) with the lines that hold each quote as
its candidates: does the source come first among the lines that quote it, by a line's vector alone (today) or with its
parts' vectors (rank.py)? docs/SEARCH_ORDER_ISSUES.md, 7: commentators come before the source, a long source line
"dissolves". The source as ../eval-real/level_order.py --quotes counts it: a line holding the search's expected words
(tags, niqqud and teamim off), of the Bavli (gemara), of the Mishnah or the Talmuds (mishna), or of the Tanakh (verses),
at the level of Chazal or of Scripture.

    python quotes.py spec --db seforim.db --out quotes.json [--others 300] [--seed 5814]
    python quotes.py score quotes.json build/passages/quotes/ranks-*.json

spec: one pass over the library's lines; each search's candidates are every source line holding its quote and up to
--others of the other lines holding it, drawn at random (a verse is quoted in thousands of lines). score: per kind, the
source first, in the first three and in the first ten among the candidates; against ranks-line.json, the searches whose
source only one of the two puts first, and whether the difference passes twice its noise (McNemar's).
"""

import argparse
import csv
import json
import math
import random
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT / "eval-real"))

KINDS = {"gemara": "bavli", "mishna": "mishna", "verses": "tanakh"}
ROOTS = {"bavli": ("תלמוד > בבלי",), "mishna": ("משנה", "תלמוד > בבלי", "תלמוד > ירושלמי"), "tanakh": ("תנ״ך",)}
TAG = re.compile(r"<[^>]+>")
MARKS = re.compile(r"[֑-ׇ]")
NOT_HEBREW = re.compile(r"[^א-ת ]")


def plain(text: str) -> str:
    """level_order.py's key of a line for --quotes: no tags, niqqud or teamim, maqaf a space, Hebrew letters only."""
    return " ".join(NOT_HEBREW.sub(" ", MARKS.sub("", TAG.sub("", text).replace("־", " "))).split())


def read_quotes():
    for name, kind in KINDS.items():
        for r in csv.DictReader((ROOT / "eval-real" / "quotes" / f"{name}.tsv").open(encoding="utf-8"),
                                delimiter="\t", quoting=csv.QUOTE_NONE):
            yield name, kind, r


def spec(db: Path, out: Path, others: int, seed: int) -> None:
    from level_order import book_levels

    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    level, about = book_levels(con)
    quotes = list(read_quotes())
    phrases = sorted({plain(r["expected"]) for _, _, r in quotes})
    padded = [(p, f" {p} ") for p in phrases]
    holders = {p: [] for p in phrases}
    for n, (line_id, book, content) in enumerate(con.execute("SELECT id, bookId, content FROM line")):
        if not content:
            continue
        key = f" {plain(content)} "
        for p, pp in padded:
            if pp in key:
                holders[p].append((line_id, book))
        if n % 1_000_000 == 0:
            print(f"{n:,} lines read", file=sys.stderr, flush=True)
    rng = random.Random(seed)
    searches = []
    for name, kind, r in quotes:
        source_level = "מקרא" if kind == "tanakh" else 'חז"ל'
        held = holders[plain(r["expected"])]
        sources = [d for d, b in held if about[b][1].startswith(ROOTS[kind]) and level[b] == source_level]
        source_set = set(sources)
        rest = [d for d, b in held if d not in source_set]
        if not sources:
            print(f"{name}/{r['id']}: no source holds {r['expected']!r}", file=sys.stderr)
            continue
        drawn = rng.sample(rest, min(others, len(rest)))
        searches.append({"key": f"{name}/{r['id']}", "group": r["group"], "query": r["query"],
                         "candidates": sorted(sources + drawn), "sources": sorted(sources), "holders": len(held)})
    out.write_text(json.dumps({"searches": searches}, ensure_ascii=False) + "\n", "utf-8")
    print(f"{len(searches)} quote searches, {sum(len(s['candidates']) for s in searches)} candidates -> {out}")


def score(spec_path: Path, ranks_paths: list[Path]) -> None:
    searches = json.loads(spec_path.read_text("utf-8"))["searches"]
    models = {p.stem.removeprefix("ranks-"): json.loads(p.read_text("utf-8")) for p in ranks_paths}
    sources = {s["key"]: set(s["sources"]) for s in searches}

    def place(ranks, key):
        return next((i + 1 for i, d in enumerate(ranks[key]["ids"]) if d in sources[key]), None)

    print("| quotes | ranking | searches | the source first | in 3 | in 10 |")
    print("|---|---|---|---|---|---|")
    for name in KINDS:
        keys = [s["key"] for s in searches if s["key"].startswith(name + "/")]
        for model, ranks in models.items():
            places = [place(ranks, k) for k in keys]
            hits = [sum(1 for p in places if p and p <= k) for k in (1, 3, 10)]
            print(f"| {name} | {model} | {len(keys)} | {hits[0]} | {hits[1]} | {hits[2]} |")
    if "line" in models:
        print("\n| quotes | ranking | source first: only today's | only this one | noise bar | verdict |")
        print("|---|---|---|---|---|---|")
        keys = [s["key"] for s in searches]
        for model, ranks in models.items():
            if model == "line":
                continue
            only_line = sum(1 for k in keys if place(models["line"], k) == 1 and place(ranks, k) != 1)
            only_this = sum(1 for k in keys if place(ranks, k) == 1 and place(models["line"], k) != 1)
            bar = 2 * math.sqrt(only_line + only_this)
            verdict = ("within the noise" if abs(only_this - only_line) < bar
                       else "better" if only_this > only_line else "worse")
            print(f"| all | {model} | {only_line} | {only_this} | {bar:.1f} | {verdict} |")


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("spec")
    a.add_argument("--db", type=Path, required=True)
    a.add_argument("--out", type=Path, required=True)
    a.add_argument("--others", type=int, default=300)
    a.add_argument("--seed", type=int, default=5814)
    b = sub.add_parser("score")
    b.add_argument("spec", type=Path)
    b.add_argument("ranks", nargs="+", type=Path)
    args = ap.parse_args()
    if args.cmd == "spec":
        spec(args.db, args.out, args.others, args.seed)
    else:
        score(args.spec, args.ranks)


if __name__ == "__main__":
    main()

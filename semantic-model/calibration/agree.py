"""How far Claude's grades agree with the owner's on the calibration pairs: exact, within one, Cohen's kappa
(unweighted and quadratic), good (2 or more) and source (3) as yes or no, the confusion matrix; in all and by set.

    python agree.py pairs.tsv

The owner's "?" (unsure) is left out. The pairs where the two are 2 or more apart are listed, with the owner's notes.
"""

import csv
import sys
from collections import Counter

CATS = (0, 1, 2, 3)


def kappa(pairs, weighted):
    n = len(pairs)
    a, b = Counter(x for x, _ in pairs), Counter(y for _, y in pairs)

    def w(i, j):
        return (i - j) ** 2 / 9 if weighted else float(i != j)

    observed = sum(w(x, y) for x, y in pairs) / n
    expected = sum(a[i] * b[j] * w(i, j) for i in CATS for j in CATS) / n ** 2
    return 1 - observed / expected


def binary_kappa(pairs):
    n = len(pairs)
    agree = sum(1 for x, y in pairs if x == y) / n
    pa, pb = sum(x for x, _ in pairs) / n, sum(y for _, y in pairs) / n
    chance = pa * pb + (1 - pa) * (1 - pb)
    return (agree - chance) / (1 - chance)


def report(name, rows):
    pairs = [(int(r["claude"]), int(r["owner"])) for r in rows if r["owner"] not in ("?", "")]
    n = len(pairs)
    exact = sum(1 for x, y in pairs if x == y)
    near = sum(1 for x, y in pairs if abs(x - y) <= 1)
    good = [(x >= 2, y >= 2) for x, y in pairs]
    source = [(x == 3, y == 3) for x, y in pairs]
    print(f"\n## {name}: {n} pairs ({len(rows) - n} unsure)")
    print(f"exact {exact} ({exact / n:.0%}), within one {near} ({near / n:.0%})")
    print(f"kappa {kappa(pairs, False):.2f}, quadratic kappa {kappa(pairs, True):.2f}")
    owner_only = sum(1 for a, b in good if b and not a)
    claude_only = sum(1 for a, b in good if a and not b)
    print(f"good (2+) agree {sum(1 for a, b in good if a == b)}, kappa {binary_kappa(good):.2f}: "
          f"good for the owner only {owner_only}, for Claude only {claude_only}")
    print(f"source (3) agree {sum(1 for a, b in source if a == b)}, kappa {binary_kappa(source):.2f}")
    print(f"mean owner - Claude {sum(y - x for x, y in pairs) / n:+.2f}")
    m = Counter(pairs)
    print("confusion, rows Claude 0-3, columns the owner 0-3:")
    for i in CATS:
        print(f"  {i}: {[m[(i, j)] for j in CATS]}")


def main() -> None:
    rows = list(csv.DictReader(open(sys.argv[1], encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE))
    report("all", rows)
    report("eval-real, the owner's own searches", [r for r in rows if r["set"] == "real"])
    report("eval-v1 batch 2, Claude's questions", [r for r in rows if r["set"] == "v1"])
    print("\n## two or more apart")
    for r in rows:
        if r["owner"] not in ("?", "") and abs(int(r["owner"]) - int(r["claude"])) >= 2:
            note = f" | {r['note']}" if r["note"] else ""
            print(f"{r['id']} {r['set']} {r['qid']}: Claude {r['claude']}, owner {r['owner']} | {r['query']} | "
                  f"{r['ref']}{note}")


if __name__ == "__main__":
    main()

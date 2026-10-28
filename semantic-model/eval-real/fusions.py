"""Other fusions of the same candidates on the real searches (or an eval-v1 batch judged the same way), offline: the
first 150 of each side that HybridSearchEngine fuses (lexCand, denseCand in eval-100/harness/Eval.kt's jsonl), RRF
with the app's weight 3, with the dense lines of N words or fewer dropped, and with keepLeaders (the lexical side's
first two hits moved up to places 2 and 4) under several rules.

    python fusions.py results.jsonl grades.tsv seforim.db [--drops none,6,8,10] [--rules none,always,verbatim,near2]

Words: pardes_semantic/clean.py's text split on whitespace. A lexical leader is kept: always (PR #3's first version);
verbatim, when its line holds the query word for word (verbatimKey, as HybridSearchEngine does); nearN, when it holds
all the query's words, one-letter prefixes off, within len(query) + N words, in any order. A line no one judged
counts 0; they are listed at the end, to judge (pool them by hand) before trusting a row.
"""

import json
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from pardes_semantic.clean import clean  # noqa: E402
from score import NOISE, dcg, load_grades  # noqa: E402

FINALS = str.maketrans("ךםןףץ", "כמנפצ")
PREFIXES = "והבכלמש"


def verbatim_key(text: str) -> str:
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"[֑-ֽֿ-ׇ]", "", text)  # niqqud and teamim, the maqaf (05BE) kept
    text = re.sub("[\"'׳״]", "", text)
    text = re.sub(r"[־\W_]", " ", text)
    return " ".join(text.translate(FINALS).lower().split())


def stem(word: str) -> str:
    for _ in range(2):
        if len(word) > 3 and word[0] in PREFIXES:
            word = word[1:]
    return word


def verbatim(query: str, text: str) -> bool:
    key = verbatim_key(query)
    return len(key.split()) >= 2 and key in verbatim_key(text)


def near(query: str, text: str, slack: int) -> bool:
    words = [stem(w) for w in verbatim_key(query).split()]
    if len(words) < 2:
        return False
    line = [stem(w) for w in verbatim_key(text).split()]
    need, window = set(words), len(words) + slack
    return any(line[i] in need and need <= set(line[i:i + window]) for i in range(len(line)))


def rrf(lexical, dense, weight=3):
    score = defaultdict(float)
    for i, d in enumerate(lexical):
        score[d] += 1 / (61 + i)
    for i, d in enumerate(dense):
        score[d] += weight / (61 + i)
    return [d for d, _ in sorted(score.items(), key=lambda x: -x[1])]


def keep_leaders(fused, leaders, places=(1, 3)):
    # HybridSearchEngine.keepLeaders: a leader (None: not kept) moves up to its place, or stays where the fusion
    # ranks it higher; one whose place an earlier one took comes right after it
    place_of = {}
    for j, d in enumerate(leaders):
        if d is None:
            continue
        at = fused.index(d) if d in fused else -1
        p = at if 0 <= at <= places[j] else places[j]
        while p in place_of.values():
            p += 1
        place_of[d] = p
    out = [d for d in fused if d not in place_of]
    for d, p in sorted(place_of.items(), key=lambda x: x[1]):
        out.insert(min(p, len(out)), d)
    return out


def arg(name, default):
    return sys.argv[sys.argv.index(name) + 1].split(",") if name in sys.argv else default


def main() -> None:
    rows = {r["id"]: r for r in map(json.loads, open(sys.argv[1], encoding="utf-8"))}
    grades = load_grades(sys.argv[2])
    con = sqlite3.connect(f"file:{sys.argv[3]}?mode=ro", uri=True)
    ids = sorted({d for r in rows.values() for d in r["denseCand"] + r["lexCand"][:2]})
    text = {}
    for k in range(0, len(ids), 900):
        chunk = ids[k:k + 900]
        text.update(con.execute(f"SELECT id, content FROM line WHERE id IN ({','.join('?' * len(chunk))})", chunk))
    words = {d: len(clean(t or "").split()) for d, t in text.items()}
    rules = {
        "none": None,
        "always": lambda q, t: True,
        "verbatim": verbatim,
        **{f"near{n}": (lambda q, t, n=n: near(q, t, n)) for n in range(1, 9)},
    }
    drops = [None if d == "none" else int(d) for d in arg("--drops", ["none", "6", "8", "10"])]
    chosen = arg("--rules", ["none", "always", "verbatim", "near2"])

    same = sum(1 for r in rows.values() if [h["lineId"] for h in r["hybrid"]] == rrf(r["lexCand"], r["denseCand"])[:len(r["hybrid"])])
    print(f"the app's hybrid page (main, befa2a8) == RRF w3 offline on {same}/{len(rows)} searches\n")
    print("| dense lines dropped | leaders kept | fires (leader 1 / 2) | first good | good in 3 | source in 3 | nDCG@3 | not judged |")
    print("|---|---|---|---|---|---|---|---|")
    unjudged = defaultdict(set)
    for drop in drops:
        for name in chosen:
            rule = rules[name]
            first = three = source = fired1 = fired2 = missing = 0
            ndcg = []
            for qid, r in rows.items():
                if qid in NOISE or qid not in grades:  # eval-v1's noise queries have no judged lines
                    continue
                dense = [d for d in r["denseCand"] if drop is None or words.get(d, 0) > drop]
                fused = rrf(r["lexCand"], dense) if dense else list(r["lexCand"])
                if rule:
                    kept = [d if rule(r["query"], text.get(d) or "") else None for d in r["lexCand"][:2]]
                    fired1 += bool(kept and kept[0])
                    fired2 += bool(len(kept) > 1 and kept[1])
                    fused = keep_leaders(fused, kept)
                top = fused[:3]
                new = [d for d in top if d not in grades[qid]]
                missing += len(new)
                unjudged[qid].update(new)
                g = [grades[qid].get(d, 0) for d in top]
                first += bool(g) and g[0] >= 2
                three += max(g, default=0) >= 2
                source += max(g, default=0) == 3
                ideal = dcg(sorted(grades[qid].values(), reverse=True)[:3])
                if ideal:
                    ndcg.append(dcg(g) / ideal)
            print(f"| {'none' if drop is None else f'<= {drop} words'} | {name} | {fired1} / {fired2} | {first} | {three} | "
                  f"{source} | {sum(ndcg) / len(ndcg):.3f} | {missing} |")
    left = {q: sorted(v) for q, v in unjudged.items() if v}
    print(f"\nlines not judged in a first three: {sum(len(v) for v in left.values())}", left or "")


if __name__ == "__main__":
    main()

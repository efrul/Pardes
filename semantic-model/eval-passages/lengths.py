"""How long the library's lines are, in the model's tokens, and what vectors for their parts would add to the index (the
variants of rank.py): a random sample of the lines the index holds (3 words or more, cleaned as embed_corpus.py
cleans), by family of books.

    python lengths.py --db seforim.db --model pardes-model --out lengths.json [--sample 200000] [--seed 5813]

For each size: the share of lines longer than it, their parts (pass<T>: every passage of the line; chunk<C>: the
passages joined into chunks of at most C tokens), and, scaled to the index's lines, the vectors added and the index's
growth. Also the share of the library's tokens past documentMaxTokens, which a line's vector does not read.
"""

import argparse
import json
import random
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))
from pardes_semantic.clean import clean  # noqa: E402
from passages import split  # noqa: E402
from rank import BYTES_PER_VECTOR, MIN_WORDS, SPECIAL_TOKENS, Model, pack  # noqa: E402

INDEX_LINES = 5_725_672  # pack 2's index (BENCHMARK.md, 4)
SIZES = (64, 128, 256, 512)


def family(con: sqlite3.Connection) -> dict[int, str]:
    """Each book's family: base book or not, and the top category it is under."""
    parent = {cid: (pid, title) for cid, pid, title in con.execute("SELECT id, parentId, title FROM category")}

    def top(cid):
        seen = set()
        while cid in parent and parent[cid][0] and parent[cid][0] not in seen:
            seen.add(cid)
            cid = parent[cid][0]
        return parent.get(cid, (None, "?"))[1]

    return {bid: ("base" if base else "other") + ":" + top(cid)
            for bid, cid, base in con.execute("SELECT id, categoryId, isBaseBook FROM book")}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--model", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--sample", type=int, default=200_000)
    ap.add_argument("--seed", type=int, default=5813)
    args = ap.parse_args()
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    families = family(con)
    top_id = con.execute("SELECT MAX(id) FROM line").fetchone()[0]
    rng = random.Random(args.seed)
    ids = sorted(rng.sample(range(1, top_id + 1), min(top_id, int(args.sample * 1.3))))
    sample = []
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        for _, book, content in con.execute(
                f"SELECT id, bookId, content FROM line WHERE id IN ({','.join('?' * len(chunk))})", chunk):
            text = clean(content or "")
            if len(text.split()) >= MIN_WORDS:
                sample.append((families.get(book, "?"), text))
    sample = sample[:args.sample]
    model = Model(args.model)
    limit = model.spec["documentMaxTokens"]
    tokens = model.tokens([t for _, t in sample])
    cuts = {k: split(t) for k, (_, t) in enumerate(sample) if tokens[k] > SIZES[0]}
    flat = [(k, a, b) for k, c in cuts.items() for a, b in c]
    sizes = defaultdict(list)
    for (k, a, b), n in zip(flat, model.tokens([sample[k][1][a:b] for k, a, b in flat], special=False)):
        sizes[k].append(n)

    def summary(members):
        n = len(members)
        total = sum(tokens[k] for k in members)
        out = {"lines": n, "tokens_mean": round(total / max(1, n), 1),
               "tokens_past_document_max": round(sum(max(0, tokens[k] - limit) for k in members) / max(1, total), 4)}
        for size in SIZES:
            long = [k for k in members if tokens[k] > size]
            passages = sum(len(cuts[k]) for k in long)
            chunks = sum(len(pack(cuts[k], sizes[k], size - SPECIAL_TOKENS)) for k in long)
            out[f">{size}"] = {"share": round(len(long) / max(1, n), 4),
                               "pass_per_long_line": round(passages / max(1, len(long)), 1),
                               "pass_added_per_line": round(passages / max(1, n), 3),
                               "chunk_per_long_line": round(chunks / max(1, len(long)), 2),
                               "chunk_added_per_line": round(chunks / max(1, n), 3)}
        return out

    by_family = defaultdict(list)
    for k, (fam, _) in enumerate(sample):
        by_family[fam].append(k)
    result = {"model": model.spec["id"], "sample": len(sample), "index_lines": INDEX_LINES,
              "all": summary(range(len(sample))),
              "families": {fam: summary(ks) for fam, ks in sorted(by_family.items(), key=lambda kv: -len(kv[1]))
                           if len(ks) >= 500}}
    for size in SIZES:
        row = result["all"][f">{size}"]
        for kind in ("pass", "chunk"):
            added = row[f"{kind}_added_per_line"] * INDEX_LINES
            row[f"{kind}_added_vectors"] = int(added)
            row[f"{kind}_added_gb"] = round(added * BYTES_PER_VECTOR / 1e9, 2)
    args.out.write_text(json.dumps(result, indent=1, ensure_ascii=False) + "\n", "utf-8")
    print(json.dumps(result["all"], ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()

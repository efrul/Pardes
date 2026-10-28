"""Ranks each search's candidates by a model, to compare models on the evaluations without embedding the library
(eval-pool/README.md).

    python pool_rank.py --db seforim.db --spec pool.json --out ranks-teacher.json [--source build/teacher]

pool.json (eval-pool/pool_spec.py): the searches, each with its query and its candidates, the app's lexical and dense
ones. Every candidate the semantic index would hold (MIN_WORDS words or more, cleaned as embed_corpus.py cleans) is
embedded with the model as a document, the query as a query, and the candidates ranked by their cosine. Without
--source, the Dicta model. ranks.json: {key: {"ids": [...], "scores": [...]}}, the best first.
"""

import argparse
import json
import sqlite3
import time
from pathlib import Path

import numpy as np

from embed_corpus import MIN_WORDS
from pardes_semantic import model as m
from pardes_semantic.clean import clean


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--spec", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--source", help="a local model (train_model.py, distill_model.py), not the Dicta one")
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--dtype", default="float16")
    ap.add_argument("--batch-tokens", type=int, default=8192, help="padded tokens per batch")
    args = ap.parse_args()
    import torch

    searches = json.loads(args.spec.read_text("utf-8"))["searches"]
    ids = sorted({d for s in searches for d in s["candidates"]})
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    texts = {}
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        for line_id, content in con.execute(f"SELECT id, content FROM line WHERE id IN ({','.join('?' * len(chunk))})",
                                            chunk):
            text = clean(content)
            if len(text.split()) >= MIN_WORDS:
                texts[line_id] = m.DOCUMENT_PREFIX + text
    print(f"{len(searches)} searches, {len(ids)} candidates, {len(texts)} of them in the index", flush=True)

    loaded = m.load(args.device, getattr(torch, args.dtype), args.source)
    t0 = time.time()
    docs = sorted(texts)
    lengths = {}
    for i in range(0, len(docs), 20_000):
        enc = loaded.tokenizer([texts[d] for d in docs[i:i + 20_000]], truncation=True, max_length=m.DOCUMENT_MAX_TOKENS)
        lengths |= {d: len(x) for d, x in zip(docs[i:i + 20_000], enc["input_ids"])}
    # As embed_corpus.py: by length, as many as fit in the token budget once padded to the longest
    docs.sort(key=lambda d: (lengths[d], d))
    vectors = {}
    i = 0
    while i < len(docs):
        j = i + 1
        while j < len(docs) and (j - i + 1) * lengths[docs[j]] <= args.batch_tokens:
            j += 1
        batch = docs[i:j]
        vectors |= dict(zip(batch, m.embed(loaded, [texts[d] for d in batch], m.DOCUMENT_MAX_TOKENS)))
        i = j
    queries = [m.QUERY_PREFIX + s["query"] for s in searches]
    qv = np.concatenate([m.embed(loaded, queries[k:k + 64], m.QUERY_MAX_TOKENS) for k in range(0, len(queries), 64)])
    print(f"embedded in {(time.time() - t0) / 60:.1f} min", flush=True)

    out = {}
    for s, q in zip(searches, qv):
        cands = [d for d in dict.fromkeys(s["candidates"]) if d in vectors]
        scores = np.stack([vectors[d] for d in cands]) @ q if cands else np.zeros(0)
        order = np.argsort(-scores, kind="stable")
        out[s["key"]] = {"ids": [cands[k] for k in order], "scores": [round(float(scores[k]), 5) for k in order]}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(out) + "\n", "utf-8")
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()

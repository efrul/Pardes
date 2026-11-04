"""Ranks pool searches by the app's model (a pack's int8 ONNX, run as check_vectors.py runs it) by a line's whole vector,
as the index holds it today, and with more vectors for the parts of a long line: the long-lines problem of
docs/SEARCH_ORDER_ISSUES.md, 7 (README.md here).

    python rank.py --db seforim.db --model pardes-model --spec crfh.json --out build/passages/crfh \
        [--passages 128,256] [--chunks 128,256,512]

--spec: searches with their candidates (../eval-crfh/spec.py's, judged.py's, quotes.py's, ../eval-pool/pool_spec.py's).
Every candidate the index would hold (3 words or more, cleaned as embed_corpus.py cleans) is embedded as a document,
at most documentMaxTokens tokens, as the index's vectors are. A line's parts are verbatim slices of it, embedded as
documents too:
- pass<T>: a line of more than T tokens, each of its passages (passages.py, the cut of the app's Passages.kt);
- chunk<C>: a line of more than C tokens, its passages joined in order into chunks of at most C tokens (so a line of
  more than documentMaxTokens tokens is read whole, and a long one in a few pieces).
A line with parts scores as the best of its own vector and theirs. Writes ranks-<variant>.json in ../pool_rank.py's
format ({key: {"ids": [...], "scores": [...]}}, the best first), tokens.json (each candidate's tokens, untruncated) and
stats.json (the vectors each variant adds, how often its first line is a long one). Ids and numbers only.
"""

import argparse
import json
import sqlite3
import sys
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))
from pardes_semantic.clean import clean  # noqa: E402
from passages import split  # noqa: E402

MIN_WORDS = 3  # embed_corpus.py's: the index holds the lines of 3 words or more
# 384 bytes of int8 vector and ~59 of graph per vector in pack 2 (2.2 GB + 336 MB for 5,725,672 lines)
BYTES_PER_VECTOR = 384 + 59
SPECIAL_TOKENS = 2  # the tokenizer's [CLS] and [SEP] around a document


class Model:
    """A pack's pardes-model folder: its tokenizer and int8 ONNX graph (mean pooling and L2 inside), as the app runs
    them; batches padded to their longest text, by length, within a token budget."""

    def __init__(self, folder: Path):
        self.spec = json.loads((folder / "pardes-model.json").read_text("utf-8"))
        self.tok = Tokenizer.from_file(str(folder / self.spec["tokenizer"]))
        self.tok.no_padding()
        self.sess = ort.InferenceSession(str(folder / self.spec["onnx"]), providers=["CPUExecutionProvider"])
        self.inputs = {i.name for i in self.sess.get_inputs()}
        self.pad = next((i for i in map(self.tok.token_to_id, ("[PAD]", "<pad>")) if i is not None), 0)

    def tokens(self, texts: list[str], special: bool = True) -> list[int]:
        """Each document's whole length in tokens, as if never truncated."""
        self.tok.no_truncation()
        prefix = self.spec["documentPrefix"] if special else ""
        return [len(e.ids) for e in self.tok.encode_batch([prefix + t for t in texts], add_special_tokens=special)]

    def embed(self, texts: list[str], query: bool = False, budget: int = 16_384) -> np.ndarray:
        prefix = self.spec["queryPrefix" if query else "documentPrefix"]
        self.tok.enable_truncation(self.spec["queryMaxTokens" if query else "documentMaxTokens"])
        encs = [e.ids for e in self.tok.encode_batch([prefix + t for t in texts])]
        order = sorted(range(len(encs)), key=lambda k: len(encs[k]))
        out = np.zeros((len(encs), self.spec["dimension"]), dtype=np.float32)
        i = 0
        while i < len(order):
            j = i + 1
            while j < len(order) and (j - i + 1) * len(encs[order[j]]) <= budget:
                j += 1
            batch = order[i:j]
            width = len(encs[batch[-1]])
            ids = np.full((len(batch), width), self.pad, dtype=np.int64)
            mask = np.zeros((len(batch), width), dtype=np.int64)
            for r, k in enumerate(batch):
                ids[r, :len(encs[k])] = encs[k]
                mask[r, :len(encs[k])] = 1
            feed = {k: v for k, v in (("input_ids", ids), ("attention_mask", mask)) if k in self.inputs}
            vec = self.sess.run([self.spec["output"]], feed)[0]
            out[batch] = vec / np.linalg.norm(vec, axis=1, keepdims=True)
            i = j
        return out


def read_lines(con: sqlite3.Connection, ids: list[int]) -> dict[int, str]:
    """The cleaned text of each line the index would hold."""
    texts = {}
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        for line_id, content in con.execute(
                f"SELECT id, content FROM line WHERE id IN ({','.join('?' * len(chunk))})", chunk):
            text = clean(content or "")
            if len(text.split()) >= MIN_WORDS:
                texts[line_id] = text
    return texts


def pack(cuts: list[tuple[int, int]], sizes: list[int], limit: int) -> list[tuple[int, int]]:
    """The passages [cuts] (slices of a line) joined in order into chunks of at most [limit] tokens each, a passage
    longer than that a chunk alone: slices of the line, from the first passage's start to the last one's end."""
    groups, start, total = [], 0, 0
    for k, n in enumerate(sizes):
        if k > start and total + n > limit:
            groups.append((start, k))
            start, total = k, 0
        total += n
    groups.append((start, len(sizes)))
    return [(cuts[a][0], cuts[b - 1][1]) for a, b in groups]


def parts(texts: dict[int, str], tokens: dict[int, int], model: Model, passages: list[int], chunks: list[int]):
    """{variant: {line: [its parts' texts]}} for the lines long enough for each variant."""
    lowest = min(passages + chunks)
    cuts = {x: split(t) for x, t in texts.items() if tokens[x] > lowest}
    flat = [(x, a, b) for x, c in cuts.items() for a, b in c]
    sizes = model.tokens([texts[x][a:b] for x, a, b in flat], special=False)
    size_of = {}
    for (x, a, b), n in zip(flat, sizes):
        size_of.setdefault(x, []).append(n)
    out = {}
    for t in passages:
        out[f"pass{t}"] = {x: [texts[x][a:b] for a, b in c] for x, c in cuts.items() if tokens[x] > t}
    for c in chunks:
        out[f"chunk{c}"] = {x: [texts[x][a:b] for a, b in pack(cuts[x], size_of[x], c - SPECIAL_TOKENS)]
                            for x in cuts if tokens[x] > c}
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", type=Path, required=True)
    ap.add_argument("--model", type=Path, required=True, help="a pack's pardes-model folder")
    ap.add_argument("--spec", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--passages", default="128,256", help="tokens above which a line's passages get vectors")
    ap.add_argument("--chunks", default="128,256,512", help="chunk sizes, in tokens, for lines longer than that")
    args = ap.parse_args()
    passages = [int(t) for t in args.passages.split(",") if t]
    chunks = [int(t) for t in args.chunks.split(",") if t]
    searches = json.loads(args.spec.read_text("utf-8"))["searches"]
    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    texts = read_lines(con, sorted({d for s in searches for d in s["candidates"]}))
    lines = sorted(texts)
    model = Model(args.model)
    t0 = time.time()
    tokens = dict(zip(lines, model.tokens([texts[x] for x in lines])))
    by_variant = parts(texts, tokens, model, passages, chunks)

    # Every distinct text embedded once: a line, a passage, a chunk (a chunk is often a passage or a whole line)
    unique = {}
    for x in lines:
        unique.setdefault(texts[x], len(unique))
    for per_line in by_variant.values():
        for ps in per_line.values():
            for p in ps:
                unique.setdefault(p, len(unique))
    print(f"{len(searches)} searches, {len(lines)} lines in the index, {len(unique)} distinct texts to embed "
          f"(lines and their parts)", flush=True)
    vectors = model.embed(list(unique))
    query_vec = model.embed([s["query"] for s in searches], query=True)
    print(f"embedded in {(time.time() - t0) / 60:.1f} min", flush=True)

    line_row = np.array([unique[texts[x]] for x in lines])
    row = {x: i for i, x in enumerate(lines)}
    part_rows = {v: {x: [unique[p] for p in ps] for x, ps in per_line.items()} for v, per_line in by_variant.items()}
    variants = ["line"] + list(by_variant)
    ranks = {v: {} for v in variants}
    first_long = {v: {t: 0 for t in (128, 256, 512)} for v in variants}
    for s, q in zip(searches, query_vec):
        cands = [d for d in dict.fromkeys(s["candidates"]) if d in row]
        whole = vectors[line_row[[row[d] for d in cands]]] @ q if cands else np.zeros(0)
        size = np.array([tokens[d] for d in cands])
        for v in variants:
            score = whole.copy()
            if v != "line":
                for k, d in enumerate(cands):
                    rows_of = part_rows[v].get(d)
                    if rows_of:
                        score[k] = max(score[k], float((vectors[rows_of] @ q).max()))
            order = np.argsort(-score, kind="stable")
            ranks[v][s["key"]] = {"ids": [cands[k] for k in order], "scores": [round(float(score[k]), 5) for k in order]}
            if cands:
                for t in first_long[v]:
                    first_long[v][t] += int(size[order[0]] > t)

    args.out.mkdir(parents=True, exist_ok=True)
    for v in variants:
        (args.out / f"ranks-{v}.json").write_text(json.dumps(ranks[v]) + "\n", "utf-8")
    (args.out / "tokens.json").write_text(json.dumps({str(x): tokens[x] for x in lines}) + "\n", "utf-8")
    added = {v: {"lines": len(per_line), "vectors": sum(map(len, per_line.values())),
                 "per_line_of_these": round(sum(map(len, per_line.values())) / len(lines), 3)}
             for v, per_line in by_variant.items()}
    stats = {"model": model.spec["id"], "searches": len(searches), "lines": len(lines),
             "longer_than_document_max_tokens": sum(1 for x in lines if tokens[x] > model.spec["documentMaxTokens"]),
             "added": added, "first_result_longer_than": first_long}
    (args.out / "stats.json").write_text(json.dumps(stats, indent=1, ensure_ascii=False) + "\n", "utf-8")
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()

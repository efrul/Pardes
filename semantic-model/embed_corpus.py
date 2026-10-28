"""Embeds the corpus into the vectors of the Pardes semantic index.

    # the whole library, on a GPU (the production run; ~3.5M lines)
    python embed_corpus.py --db seforim.db --backend torch --device cuda --dtype float16 --out build/vectors
    # a benchmark corpus (build_eval_set.py), on a CPU, with the app's int8 model
    python embed_corpus.py --corpus build/eval/corpus.jsonl --backend onnx --model build/model --out build/eval/vectors

Writes the format SeforimLibrary's generator reads (`-PvectorsBin=`, BuildLuceneIndex.loadVectorProvider) and the
app's VectorIndexBuilder too: ids.i64 (line ids, int64 LE), vecs.f32 (float32 LE, one row per id, L2-normalised),
meta.txt ("<count> <dimension>"), plus embed-info.json (model, cleaner, backend, db release). An interrupted run picks
up where it stopped: the order is deterministic (by length, then id) and the files are appended batch by batch.
"""

import argparse
import json
import sqlite3
import sys
import time
from pathlib import Path

import numpy as np

from pardes_semantic import model as m
from pardes_semantic.clean import CLEANER_ID, clean

MIN_WORDS = 3
# Lengths are measured this many texts at a time: the token lists of 3.5M lines at once are tens of GB of Python ints
LENGTH_CHUNK = 20_000


def lines_from_db(db: Path, min_words: int) -> tuple[list[int], list[str]]:
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    ids, texts = [], []
    for line_id, content in con.execute("SELECT id, content FROM line"):
        text = clean(content)
        # Headings ("סימן א", "דף ב.") and empty lines: nothing for a meaning to be found in
        if len(text.split()) >= min_words:
            ids.append(line_id)
            texts.append(text)
    return ids, texts


def lines_from_corpus(path: Path) -> tuple[list[int], list[str]]:
    ids, texts = [], []
    with path.open(encoding="utf-8") as f:
        for line in f:
            doc = json.loads(line)
            ids.append(int(doc["id"]))
            texts.append(doc["text"])
    return ids, texts


class OnnxBackend:
    def __init__(self, model_dir: Path, threads: int):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        self.manifest = json.loads((model_dir / "pardes-model.json").read_text("utf-8"))
        opts = ort.SessionOptions()
        if threads:
            opts.intra_op_num_threads = threads
        self.session = ort.InferenceSession(str(model_dir / self.manifest["onnx"]), opts,
                                            providers=["CPUExecutionProvider"])
        self.tokenizer = Tokenizer.from_file(str(model_dir / self.manifest["tokenizer"]))
        self.dimension = self.manifest["dimension"]
        self.model_id = self.manifest["id"]
        self.tokenizer.enable_truncation(self.manifest["documentMaxTokens"])
        self.tokenizer.enable_padding(pad_id=self.tokenizer.token_to_id("[PAD]"), pad_token="[PAD]")

    def lengths(self, texts: list[str]) -> list[int]:
        self.tokenizer.no_padding()
        out = []
        for i in range(0, len(texts), LENGTH_CHUNK):
            out += [len(e.ids) for e in self.tokenizer.encode_batch(texts[i:i + LENGTH_CHUNK])]
        self.tokenizer.enable_padding(pad_id=self.tokenizer.token_to_id("[PAD]"), pad_token="[PAD]")
        return out

    def embed(self, texts: list[str]) -> np.ndarray:
        enc = self.tokenizer.encode_batch(texts)
        ids = np.array([e.ids for e in enc], dtype=np.int64)
        mask = np.array([e.attention_mask for e in enc], dtype=np.int64)
        return self.session.run(["sentence_embedding"], {"input_ids": ids, "attention_mask": mask})[0]


class TorchBackend:
    def __init__(self, device: str, dtype: str, source: str | None):
        import torch

        self.loaded = m.load(device, getattr(torch, dtype), source)
        self.dimension = self.loaded.embedder.dimension
        self.model_id = self.loaded.model_id
        self.manifest = {"documentMaxTokens": m.DOCUMENT_MAX_TOKENS, "documentPrefix": m.DOCUMENT_PREFIX}

    def lengths(self, texts: list[str]) -> list[int]:
        out = []
        for i in range(0, len(texts), LENGTH_CHUNK):
            enc = self.loaded.tokenizer(texts[i:i + LENGTH_CHUNK], truncation=True, max_length=m.DOCUMENT_MAX_TOKENS)
            out += [len(x) for x in enc["input_ids"]]
        return out

    def embed(self, texts: list[str]) -> np.ndarray:
        return m.embed(self.loaded, texts, m.DOCUMENT_MAX_TOKENS)


def main() -> int:
    ap = argparse.ArgumentParser()
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--db", type=Path, help="seforim.db: every line of at least --min-words words")
    src.add_argument("--corpus", type=Path, help="a corpus.jsonl of build_eval_set.py")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--backend", choices=("onnx", "torch"), default="onnx")
    ap.add_argument("--model", type=Path, help="onnx: the directory of export_onnx.py")
    ap.add_argument("--device", default="cpu", help="torch: cpu, cuda, cuda:1...")
    ap.add_argument("--dtype", default="float32", help="torch: float32, float16, bfloat16")
    ap.add_argument("--source", help="torch: a local model (train_model.py, distill_model.py), not the Dicta one")
    ap.add_argument("--threads", type=int, default=0, help="onnx: CPU threads (0: all)")
    ap.add_argument("--batch-tokens", type=int, default=8192, help="padded tokens per batch")
    ap.add_argument("--min-words", type=int, default=MIN_WORDS)
    ap.add_argument("--limit", type=int, default=0, help="embed only the first N lines (a dry run)")
    args = ap.parse_args()

    backend = OnnxBackend(args.model, args.threads) if args.backend == "onnx" \
        else TorchBackend(args.device, args.dtype, args.source)
    dim = backend.dimension
    t0 = time.time()
    ids, texts = lines_from_db(args.db, args.min_words) if args.db else lines_from_corpus(args.corpus)
    prefix = backend.manifest.get("documentPrefix", "")
    texts = [prefix + t for t in texts]
    print(f"{len(ids)} lines read in {time.time() - t0:.0f}s", flush=True)

    lengths = backend.lengths(texts)
    order = sorted(range(len(ids)), key=lambda i: (lengths[i], ids[i]))
    if args.limit:
        order = order[:args.limit]

    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)
    ids_path, vecs_path = out / "ids.i64", out / "vecs.f32"
    done = ids_path.stat().st_size // 8 if ids_path.exists() else 0
    if done:
        # Both files cut to the last complete row, in case the run stopped between the two writes
        rows = min(done, vecs_path.stat().st_size // (4 * dim))
        with ids_path.open("r+b") as f:
            f.truncate(rows * 8)
        with vecs_path.open("r+b") as f:
            f.truncate(rows * 4 * dim)
        done = rows
        print(f"Resuming after {done} lines", flush=True)

    info = {
        "model": backend.model_id,
        "cleaner": CLEANER_ID,
        "documentPrefix": prefix,
        "documentMaxTokens": backend.manifest["documentMaxTokens"],
        "backend": args.backend if args.backend == "onnx" else f"torch-{args.dtype}",
        "minWords": args.min_words,
        "source": str(args.db or args.corpus),
    }
    if args.db and (args.db.parent / "release_info.txt").exists():
        info["dbRelease"] = (args.db.parent / "release_info.txt").read_text().strip()
    (out / "embed-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", "utf-8")

    t0 = time.time()
    written = done
    with ids_path.open("ab") as fi, vecs_path.open("ab") as fv:
        i = done
        while i < len(order):
            # As many lines as fit in the token budget once padded to the longest (the order is by length)
            j = i + 1
            while j < len(order) and (j - i + 1) * lengths[order[j]] <= args.batch_tokens:
                j += 1
            batch = order[i:j]
            vecs = backend.embed([texts[k] for k in batch]).astype("<f4")
            fv.write(vecs.tobytes())
            fi.write(np.array([ids[k] for k in batch], dtype="<i8").tobytes())
            written += len(batch)
            i = j
            if written // 2000 != (written - len(batch)) // 2000:
                fi.flush()
                fv.flush()
                rate = (written - done) / (time.time() - t0)
                left = (len(order) - written) / rate if rate else 0
                print(f"{written}/{len(order)}  {rate:.1f} lines/s  ~{left / 60:.0f} min left", flush=True)
    (out / "meta.txt").write_text(f"{written} {dim}\n")
    print(f"Done: {written} vectors in {out} ({(time.time() - t0) / 60:.1f} min)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

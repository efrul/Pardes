"""Exports dicta-il/neodictabert-bilingual-embed to the ONNX model the app loads, and checks it.

    python export_onnx.py --out build/model

writes, in --out:
  model.int8.onnx        the int8 model (input_ids, attention_mask -> sentence_embedding [batch, 768], L2-normalised)
  tokenizer.json         the model's tokenizer, unchanged
  pardes-model.json      the manifest the app reads (dimension, prefixes, token limits, cleaner, checksums)
  NOTICE.md              the CC BY 4.0 attribution that has to travel with the model
  verify-report.json     how close the ONNX models are to PyTorch

and fails (exit 1) if the int8 model drifts from PyTorch beyond the thresholds below.
"""

import argparse
import hashlib
import json
import shutil
import sys
import time
from pathlib import Path

import numpy as np
import torch

from pardes_semantic import model as m
from pardes_semantic.clean import CLEANER_ID, clean
from pardes_semantic.samples import EXTRA, PAIRS

OPSET = 17  # ONNX Runtime 1.17 (the app's) reads up to opset 20
MIN_COS_FP32 = 0.9999
MIN_COS_INT8 = 0.97
MIN_MEAN_COS_INT8 = 0.985  # the fine-tuned model: 0.988, top-1 12/12


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def export_fp32(loaded: m.Loaded, path: Path) -> None:
    enc = m.tokenize(loaded.tokenizer, ["שלום עולם", "ואהבת לרעך כמוך"], 16)
    args = (enc["input_ids"], enc["attention_mask"])
    common = dict(
        input_names=["input_ids", "attention_mask"],
        output_names=["sentence_embedding"],
        opset_version=OPSET,
    )
    try:
        torch.onnx.export(
            loaded.embedder, args, str(path), dynamo=False,
            dynamic_axes={"input_ids": {0: "batch", 1: "tokens"}, "attention_mask": {0: "batch", 1: "tokens"},
                          "sentence_embedding": {0: "batch"}},
            **common,
        )
    except Exception as e:  # noqa: BLE001 - the TorchScript exporter is deprecated; the dynamo one is the fallback
        print(f"TorchScript export failed ({e}); trying the dynamo exporter", file=sys.stderr)
        batch, tokens = torch.export.Dim("batch"), torch.export.Dim("tokens", max=m.DOCUMENT_MAX_TOKENS)
        torch.onnx.export(
            loaded.embedder, args, str(path), dynamo=True, external_data=False,
            dynamic_shapes={"input_ids": {0: batch, 1: tokens}, "attention_mask": {0: batch, 1: tokens}},
            **common,
        )


def quantize(src: Path, dst: Path, gather: bool) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    # With Gather, the 128k x 768 embedding table too (a quarter of the weights): 366 MB instead of 661 MB, mean
    # cosine to PyTorch 0.991 instead of 0.994 on the samples
    ops = ["MatMul", "Gather"] if gather else ["MatMul"]
    # reduce_range (7-bit weights): on x86 CPUs without VNNI (many users', Kaggle's) the int8 kernels saturate with
    # full-range weights; there the model's cosine to PyTorch fell to 0.78. It costs nothing measurable with VNNI
    quantize_dynamic(str(src), str(dst), weight_type=QuantType.QInt8, per_channel=True, reduce_range=True,
                     op_types_to_quantize=ops)


def run_onnx(path: Path, loaded: m.Loaded, texts: list[str], max_tokens: int) -> np.ndarray:
    import onnxruntime as ort

    opts = ort.SessionOptions()
    opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    session = ort.InferenceSession(str(path), opts, providers=["CPUExecutionProvider"])
    out = []
    # One text per run, as the app embeds a query: no padding involved
    for t in texts:
        enc = m.tokenize(loaded.tokenizer, [t], max_tokens)
        feed = {"input_ids": enc["input_ids"].numpy(), "attention_mask": enc["attention_mask"].numpy()}
        out.append(session.run(["sentence_embedding"], feed)[0][0])
    return np.stack(out)


def cosines(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    a = a / np.linalg.norm(a, axis=1, keepdims=True)
    b = b / np.linalg.norm(b, axis=1, keepdims=True)
    return (a * b).sum(axis=1)


def rankings(queries: np.ndarray, passages: np.ndarray) -> list[list[int]]:
    return np.argsort(-(m.quantize_int8(queries).astype(np.float32) @ m.quantize_int8(passages).astype(np.float32).T),
                      axis=1).tolist()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--keep-fp32", action="store_true", help="keep model.fp32.onnx next to the int8 one")
    ap.add_argument("--no-gather", action="store_true", help="leave the embedding table in fp32 (larger, closer)")
    ap.add_argument("--source", help="a local model (train_model.py, distill_model.py) instead of the pinned Dicta one")
    ap.add_argument("--hybrid-dense-weight", type=float, default=1.0,
                    help="the dense side's weight in the app's RRF, as evaluate.py --dense-weights measured it")
    args = ap.parse_args()
    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)

    t0 = time.time()
    loaded = m.load("cpu", torch.float32, args.source)
    print(f"Loaded {loaded.model_id} ({loaded.embedder.dimension} dims) in {time.time() - t0:.0f}s")

    queries = [m.QUERY_PREFIX + clean(q) for q, _ in PAIRS]
    passages = [m.DOCUMENT_PREFIX + clean(p) for _, p in PAIRS]
    docs = passages + [clean(t) for t in EXTRA]

    ref_q = m.embed(loaded, queries, m.QUERY_MAX_TOKENS)
    ref_d = np.concatenate([m.embed(loaded, [d], m.DOCUMENT_MAX_TOKENS) for d in docs])

    # The batched (padded) path must give what one text at a time gives: the corpus is embedded in batches
    batched = m.embed(loaded, docs, m.DOCUMENT_MAX_TOKENS)
    pad_cos = float(cosines(batched, ref_d).min())
    print(f"Batched vs single, min cosine: {pad_cos:.6f}")

    fp32 = out / "model.fp32.onnx"
    int8 = out / "model.int8.onnx"
    t0 = time.time()
    export_fp32(loaded, fp32)
    print(f"Exported fp32 ({fp32.stat().st_size / 1e6:.0f} MB) in {time.time() - t0:.0f}s")
    t0 = time.time()
    quantize(fp32, int8, gather=not args.no_gather)
    print(f"Quantized int8 ({int8.stat().st_size / 1e6:.0f} MB) in {time.time() - t0:.0f}s")

    report = {"padding_min_cos": pad_cos}
    for name, path in (("fp32", fp32), ("int8", int8)):
        q = run_onnx(path, loaded, queries, m.QUERY_MAX_TOKENS)
        d = run_onnx(path, loaded, docs, m.DOCUMENT_MAX_TOKENS)
        cos = np.concatenate([cosines(q, ref_q), cosines(d, ref_d)])
        same_top1 = sum(a[0] == b[0] for a, b in zip(rankings(q, d[:len(PAIRS)]), rankings(ref_q, ref_d[:len(PAIRS)])))
        report[name] = {"min_cos": float(cos.min()), "mean_cos": float(cos.mean()),
                        "same_top1": f"{same_top1}/{len(PAIRS)}"}
        print(f"{name}: cosine to PyTorch min {cos.min():.5f} mean {cos.mean():.5f}, same top-1 {same_top1}/{len(PAIRS)}")

    ref_hits = sum(r[0] == i for i, r in enumerate(rankings(ref_q, ref_d[:len(PAIRS)])))
    report["pytorch_pairs_top1"] = f"{ref_hits}/{len(PAIRS)}"
    print(f"PyTorch: the matching passage ranks first for {ref_hits}/{len(PAIRS)} sample queries")

    # Latency of one short query on this machine's CPU (the app embeds one query per search)
    import onnxruntime as ort
    session = ort.InferenceSession(str(int8), providers=["CPUExecutionProvider"])
    enc = m.tokenize(loaded.tokenizer, [queries[0]], m.QUERY_MAX_TOKENS)
    feed = {"input_ids": enc["input_ids"].numpy(), "attention_mask": enc["attention_mask"].numpy()}
    session.run(None, feed)
    t0 = time.perf_counter()
    for _ in range(20):
        session.run(None, feed)
    report["query_latency_ms"] = round((time.perf_counter() - t0) / 20 * 1000, 1)
    print(f"int8 query latency: {report['query_latency_ms']} ms")

    # Always Dicta's own tokenizer.json: training does not change the tokenizer, but saving a trained model writes
    # its training truncation (256) into the file
    from huggingface_hub import hf_hub_download
    shutil.copy(hf_hub_download(m.MODEL_ID, "tokenizer.json", revision=m.MODEL_REVISION), out / "tokenizer.json")

    manifest = {
        "format": 1,
        "id": loaded.model_id,
        "quantization": "int8 dynamic, per channel, reduce range, " + ("MatMul" if args.no_gather else "MatMul+Gather"),
        "baseModel": m.MODEL_ID,
        "baseModelRevision": m.MODEL_REVISION,
        # A local model is the Dicta one fine-tuned and/or distilled on the library's links (train_model.py)
        "derived": args.source is not None,
        "license": "CC-BY-4.0",
        "attribution": "neodictabert-bilingual-embed by Dicta: The Israel Center for Text Analysis, CC BY 4.0; "
                       + ("fine-tuned on the library's links, " if args.source else "")
                       + "converted to ONNX and quantized to int8 by Pardes",
        "onnx": int8.name,
        "onnxSha256": sha256(int8),
        "tokenizer": "tokenizer.json",
        "tokenizerSha256": sha256(out / "tokenizer.json"),
        "dimension": loaded.embedder.dimension,
        "pooling": "mean",
        "normalized": True,
        "output": "sentence_embedding",
        "queryPrefix": m.QUERY_PREFIX,
        "documentPrefix": m.DOCUMENT_PREFIX,
        "queryMaxTokens": m.QUERY_MAX_TOKENS,
        "documentMaxTokens": m.DOCUMENT_MAX_TOKENS,
        "cleaner": CLEANER_ID,
        "hybridDenseWeight": args.hybrid_dense_weight,
    }
    (out / "pardes-model.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", "utf-8")
    shutil.copy(Path(__file__).parent / "NOTICE.md", out / "NOTICE.md")
    (out / "verify-report.json").write_text(json.dumps(report, indent=2) + "\n", "utf-8")
    if not args.keep_fp32:
        fp32.unlink()

    ok = (report["fp32"]["min_cos"] >= MIN_COS_FP32 and report["int8"]["min_cos"] >= MIN_COS_INT8
          and report["int8"]["mean_cos"] >= MIN_MEAN_COS_INT8 and pad_cos >= MIN_COS_FP32)
    print("OK" if ok else "FAILED: the ONNX model drifts from PyTorch beyond the thresholds")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

"""Distills a model (the Dicta one, or train_model.py's) into a small, fast one: fewer layers, fewer dimensions.

    python distill_model.py --teacher build/teacher --pairs build/train/pairs.jsonl --out build/student   # on a GPU
    python distill_model.py --pairs build/train/pairs.jsonl --out /tmp/smoke --limit 64 --max-steps 2 --device cpu

The student is the teacher with --layers of its layers kept (evenly spaced, the last one always): same tokenizer, so
the app's HfTokenizer and its parity tests hold. Both get a projection to --dim dimensions, the PCA of the teacher's
embeddings of the texts (so the index is half the size at 384), and the student learns to give what the projected
teacher gives (MSE), on the queries and the passages of the pairs, and with --queries on searches like the users'
(build_distill_queries.py: the pairs' queries are slices of passages, not questions). The teacher's ranking is what is
kept: the benchmark (evaluate.py) says how much of it.
"""

import argparse
import copy
import gc
import json
from pathlib import Path

import numpy as np
import torch

from pardes_semantic import model as m
from train_model import deadline_callback, last_checkpoint, load_st, precision


def kept_layers(total: int, keep: int) -> list[int]:
    return sorted({round(i * (total - 1) / (keep - 1)) for i in range(keep)}) if keep > 1 else [total - 1]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--teacher", help="a local model (train_model.py); default: the Dicta model")
    ap.add_argument("--pairs", type=Path, required=True, help="pairs.jsonl: its queries and passages are the texts")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--layers", type=int, default=7)
    ap.add_argument("--dim", type=int, default=384)
    ap.add_argument("--texts", type=int, default=400_000, help="texts of the pairs to distill on")
    ap.add_argument("--queries", type=Path, help="searches to distill on too, one per line (build_distill_queries.py)")
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--epochs", type=float, default=1.0)
    ap.add_argument("--batch-size", type=int, default=128)
    ap.add_argument("--lr", type=float, default=1e-4)
    ap.add_argument("--max-steps", type=int, default=-1)
    ap.add_argument("--limit", type=int, default=0, help="use only the first N pairs (a smoke test)")
    ap.add_argument("--deadline", type=float, default=0, help="Unix time to stop by, saving the model (0: none)")
    args = ap.parse_args()

    from datasets import Dataset
    from sentence_transformers import SentenceTransformerTrainer, SentenceTransformerTrainingArguments
    from sentence_transformers.losses import MSELoss
    from sentence_transformers.models import Dense

    rows = [json.loads(line) for line in args.pairs.open(encoding="utf-8")]
    if args.limit:
        rows = rows[:args.limit]
    texts = []
    for r in rows:
        texts += [m.QUERY_PREFIX + r["anchor"], m.DOCUMENT_PREFIX + r["positive"]]
    texts = list(dict.fromkeys(texts))[:args.texts]
    if args.queries:
        searches = [m.QUERY_PREFIX + q for q in args.queries.read_text("utf-8").splitlines() if q.strip()]
        texts = list(dict.fromkeys(texts + searches))
        print(f"{len(searches)} searches with them")
    print(f"{len(texts)} texts")
    if len(texts) <= args.dim:
        raise SystemExit(f"{len(texts)} texts cannot give a {args.dim}-dimension PCA: use more pairs")

    teacher = load_st(args.teacher, args.device)
    gpu = args.device.startswith("cuda")
    # The teacher's embeddings are the long part on a small GPU: kept in --out, a resumed run does not redo them
    cache = args.out / "teacher-embeddings.npy"
    if cache.exists() and np.load(cache, mmap_mode="r").shape[0] == len(texts):
        raw = np.load(cache)
        print(f"Teacher embeddings from {cache}")
    else:
        dtype = torch.bfloat16 if precision(gpu)["bf16"] else torch.float16
        with torch.autocast("cuda", dtype=dtype, enabled=gpu):
            raw = teacher.encode(texts, batch_size=128, convert_to_numpy=True, show_progress_bar=True)
        raw = raw.astype(np.float32)
        args.out.mkdir(parents=True, exist_ok=True)
        np.save(cache, raw)

    # PCA of the teacher's embeddings: the projection that keeps most of what distinguishes them
    mean = raw.mean(axis=0)
    _, _, vt = np.linalg.svd(raw - mean, full_matrices=False)
    components = vt[: args.dim]
    explained = float(((raw - mean) @ components.T).var(axis=0).sum() / (raw - mean).var(axis=0).sum())
    print(f"PCA to {args.dim} dimensions keeps {explained:.1%} of the variance")
    # No bias, no centring: the student's Dense is this same matrix, so student and targets are projected alike, and
    # the mean only adds the same offset to every vector
    targets = raw @ components.T

    # The student is cut from a copy of the teacher on the CPU: two full models and the encoding's cache on a 16 GB
    # GPU ran out of memory at the first training step
    teacher.to("cpu")
    if gpu:
        torch.cuda.empty_cache()
    student = copy.deepcopy(teacher)
    auto = student[0].auto_model
    total = len(auto.transformer_encoder)
    keep = kept_layers(total, args.layers)
    auto.transformer_encoder = torch.nn.ModuleList([auto.transformer_encoder[i] for i in keep])
    auto.config.num_hidden_layers = len(keep)
    print(f"Student: layers {keep} of {total}")
    hidden = auto.config.hidden_size
    dense = Dense(hidden, args.dim, bias=False, activation_function=torch.nn.Identity())
    with torch.no_grad():
        dense.linear.weight.copy_(torch.from_numpy(components))
    student.append(dense)
    del teacher
    gc.collect()
    student.to(args.device)

    data = Dataset.from_dict({"text": texts, "label": [t.tolist() for t in targets]})
    training = SentenceTransformerTrainingArguments(
        output_dir=str(args.out / "checkpoints"),
        num_train_epochs=args.epochs,
        max_steps=args.max_steps,
        per_device_train_batch_size=args.batch_size,
        learning_rate=args.lr,
        warmup_ratio=0.05,
        **precision(gpu),
        logging_steps=50,
        save_strategy="steps",
        save_steps=500,
        save_total_limit=2,
        report_to="none",
        use_cpu=not gpu,
    )
    callbacks = [deadline_callback(args.deadline)] if args.deadline else None
    trainer = SentenceTransformerTrainer(model=student, args=training, train_dataset=data, loss=MSELoss(student),
                                         callbacks=callbacks)
    resume = last_checkpoint(args.out)
    if resume:
        print(f"Resuming from {resume}")
    trainer.train(resume_from_checkpoint=resume)
    student.save(str(args.out))
    (args.out / "distill-info.json").write_text(json.dumps(
        {"teacher": args.teacher or m.MODEL_ID, "layers": keep, "of": total, "dim": args.dim,
         "pcaExplained": explained, "texts": len(texts)}, indent=2) + "\n", "utf-8")
    print(f"Saved to {args.out}")


if __name__ == "__main__":
    main()

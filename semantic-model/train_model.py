"""Fine-tunes the Dicta model on the library's links (build_train_pairs.py): stage C of the plan.

    python train_model.py --pairs build/train/pairs.jsonl --out build/teacher            # on a GPU (~2-4 h on one A100)
    python train_model.py --pairs build/train/pairs.jsonl --out /tmp/smoke --max-steps 2 --batch-size 4 --device cpu
    python train_model.py --source build/teacher --pairs build/train/pairs.jsonl --limit 20000 \
        --questions build/train/questions.jsonl --question-repeat 10 --out build/tuned      # the teacher on the questions

An interrupted run (a Colab session that ends) starts again from its last checkpoint when run again with the same
--out: keep --out on persistent storage (Google Drive).

The loss is the one Dicta trained the model with (CachedMultipleNegativesRankingLoss): each query against its passage
and every other passage of the batch, so a large batch (--batch-size, default 256) gives many negatives at the memory
of a small one (--mini-batch). The queries get the model's "query: " prefix, as the app's do; no text twice in a batch.

--questions adds the training questions (train-v2/build_pairs.py), each --question-repeat times, to the pairs: they
are few, and a reader's way of asking is what the links' pairs lack. Their hard negatives are not used yet: the two
questions of a target share its text, so batches of questions only could not be filled without repeating one. --more
adds other pairs as they are, such as build_chavruta_pairs.py's (Hebrew explanation -> the Gemara line).

Writes a sentence-transformers directory that export_onnx.py --source and embed_corpus.py --source take.
"""

import argparse
import json
from pathlib import Path

from pardes_semantic import model as m

TRAIN_MAX_TOKENS = 256


def precision(gpu: bool) -> dict[str, bool]:
    """bf16 on Ampere and later (A100, L4...), fp16 on older GPUs (Colab's free T4 has no bf16), none on a CPU."""
    if not gpu:
        return {"bf16": False, "fp16": False}
    import torch

    bf16 = torch.cuda.is_bf16_supported()
    return {"bf16": bf16, "fp16": not bf16}


def last_checkpoint(out: Path) -> str | None:
    """The newest checkpoint of an interrupted run in out/checkpoints, to resume from (a Colab session ends)."""
    ckpts = sorted((out / "checkpoints").glob("checkpoint-*"), key=lambda p: int(p.name.split("-")[1]))
    return str(ckpts[-1]) if ckpts else None


def deadline_callback(deadline: float):
    """Stops the training cleanly (and so saves the model) at [deadline] (Unix seconds): a Kaggle run that outlives its
    session loses its output, a run that stops a little early keeps a usable model."""
    import time

    from transformers import TrainerCallback

    class Deadline(TrainerCallback):
        def on_step_end(self, args, state, control, **kwargs):
            if time.time() > deadline:
                print(f"Deadline reached at step {state.global_step}: stopping and saving")
                control.should_training_stop = True
                control.should_save = True
            return control

    return Deadline()


def load_st(source: str | None, device: str):
    from sentence_transformers import SentenceTransformer

    name, revision = (m.MODEL_ID, m.MODEL_REVISION) if source is None else (source, None)
    st = SentenceTransformer(name, revision=revision, trust_remote_code=True, device=device,
                             prompts={"query": m.QUERY_PREFIX, "document": m.DOCUMENT_PREFIX})
    st.max_seq_length = TRAIN_MAX_TOKENS
    return st


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pairs", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--source", help="start from this local model instead of the Dicta one")
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--epochs", type=float, default=1.0)
    ap.add_argument("--batch-size", type=int, default=256)
    ap.add_argument("--mini-batch", type=int, default=32)
    ap.add_argument("--lr", type=float, default=2e-5)
    ap.add_argument("--max-steps", type=int, default=-1, help="stop after N steps (a smoke test)")
    ap.add_argument("--limit", type=int, default=0, help="use only the first N pairs")
    ap.add_argument("--questions", type=Path, help="train-v2/build_pairs.py's questions, added to the pairs")
    ap.add_argument("--question-repeat", type=int, default=1, help="each question this many times")
    ap.add_argument("--more", type=Path, action="append", default=[],
                    help="more pairs in the same jsonl (build_chavruta_pairs.py), added as they are; repeatable")
    ap.add_argument("--save-steps", type=int, default=200, help="checkpoint every N steps (resumed automatically)")
    ap.add_argument("--deadline", type=float, default=0, help="Unix time to stop by, saving the model (0: none)")
    ap.add_argument("--keep-checkpoints", type=int, default=2,
                    help="checkpoints kept (with the optimizer: ~4.4 GB each for the full-size model)")
    args = ap.parse_args()

    from datasets import Dataset
    from sentence_transformers import SentenceTransformerTrainer, SentenceTransformerTrainingArguments
    from sentence_transformers.losses import CachedMultipleNegativesRankingLoss
    from sentence_transformers.training_args import BatchSamplers

    rows = [json.loads(line) for line in args.pairs.open(encoding="utf-8")]
    if args.limit:
        rows = rows[:args.limit]
    links = len(rows)
    if args.questions:
        questions = [json.loads(line) for line in args.questions.open(encoding="utf-8")]
        rows += questions * args.question_repeat
        print(f"{len(questions)} questions, {args.question_repeat} times each")
    for path in args.more:
        more = [json.loads(line) for line in path.open(encoding="utf-8")]
        rows += more
        print(f"{len(more)} pairs of {path.name}")
    data = Dataset.from_dict({"anchor": [r["anchor"] for r in rows], "positive": [r["positive"] for r in rows]})
    print(f"{len(data)} pairs ({links} of links)")

    st = load_st(args.source, args.device)
    loss = CachedMultipleNegativesRankingLoss(st, mini_batch_size=args.mini_batch)
    gpu = args.device.startswith("cuda")
    training = SentenceTransformerTrainingArguments(
        output_dir=str(args.out / "checkpoints"),
        num_train_epochs=args.epochs,
        max_steps=args.max_steps,
        per_device_train_batch_size=args.batch_size,
        learning_rate=args.lr,
        warmup_ratio=0.05,
        **precision(gpu),
        batch_sampler=BatchSamplers.NO_DUPLICATES,
        prompts={"anchor": m.QUERY_PREFIX, "positive": m.DOCUMENT_PREFIX},
        logging_steps=20,
        save_strategy="steps",
        save_steps=args.save_steps,
        save_total_limit=args.keep_checkpoints,
        report_to="none",
        use_cpu=not gpu,
        dataloader_drop_last=True,
    )
    callbacks = [deadline_callback(args.deadline)] if args.deadline else None
    trainer = SentenceTransformerTrainer(model=st, args=training, train_dataset=data, loss=loss, callbacks=callbacks)
    resume = last_checkpoint(args.out)
    if resume:
        print(f"Resuming from {resume}")
    trainer.train(resume_from_checkpoint=resume)
    st.save(str(args.out))
    print(f"Saved to {args.out}")


if __name__ == "__main__":
    main()

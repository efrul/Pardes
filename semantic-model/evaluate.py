"""Scores lexical, dense and hybrid retrieval on a benchmark of build_eval_set.py.

    python evaluate.py --eval build/eval --vectors build/eval/vectors-int8 --model build/model --out build/eval/report

The queries are embedded as the app embeds them (the int8 ONNX model, "query: " + clean(text), one at a time), the
vectors quantized to int8 as the index holds them, and the dense search is exact (no HNSW: what the model is worth, not
the graph). The hybrid is HybridSearchEngine's: RRF with k = 60 over the first 150 of each side.

The lexical side is BM25 (Lucene's k1 = 1.2, b = 0.75) over words normalised like the app's index (no niqqud or
teamim, final letters folded, punctuation dropped), every word optional. The app's own lexical search is stricter
(its words must all be near each other), so for long queries the real gap between lexical and hybrid is wider still.

Writes report.json and report.md in --out.
"""

import argparse
import json
import math
import re
import time
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

from pardes_semantic import model as m
from pardes_semantic.clean import clean

RRF_K = 60
CANDIDATES = 150
FINALS = str.maketrans("ךםןףץ", "כמנפצ")
MARKS = re.compile("[֑-ׇ]")
NON_WORD = re.compile(r"[^\w]+")


def lexical_terms(text: str) -> list[str]:
    text = MARKS.sub("", text.replace("־", " ")).translate(FINALS).lower()
    return [t for t in NON_WORD.split(text) if t]


class BM25:
    def __init__(self, docs: list[str], k1: float = 1.2, b: float = 0.75):
        self.k1, self.b = k1, b
        self.postings: dict[str, list[tuple[int, int]]] = defaultdict(list)
        self.lengths = np.zeros(len(docs), dtype=np.float32)
        for i, doc in enumerate(docs):
            terms = lexical_terms(doc)
            self.lengths[i] = len(terms)
            for term, tf in Counter(terms).items():
                self.postings[term].append((i, tf))
        self.avg = float(self.lengths.mean())
        self.n = len(docs)

    def search(self, query: str, k: int) -> list[int]:
        scores: dict[int, float] = defaultdict(float)
        for term in set(lexical_terms(query)):
            plist = self.postings.get(term)
            if not plist:
                continue
            idf = math.log(1 + (self.n - len(plist) + 0.5) / (len(plist) + 0.5))
            for doc, tf in plist:
                norm = self.k1 * (1 - self.b + self.b * self.lengths[doc] / self.avg)
                scores[doc] += idf * tf * (self.k1 + 1) / (tf + norm)
        return [d for d, _ in sorted(scores.items(), key=lambda x: -x[1])[:k]]


class QueryEmbedder:
    def __init__(self, model_dir: Path, prefix: str | None):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        manifest = json.loads((model_dir / "pardes-model.json").read_text("utf-8"))
        self.session = ort.InferenceSession(str(model_dir / manifest["onnx"]), providers=["CPUExecutionProvider"])
        self.tokenizer = Tokenizer.from_file(str(model_dir / manifest["tokenizer"]))
        self.tokenizer.enable_truncation(manifest["queryMaxTokens"])
        self.prefix = manifest["queryPrefix"] if prefix is None else prefix

    def __call__(self, text: str) -> np.ndarray:
        e = self.tokenizer.encode(self.prefix + clean(text))
        feed = {"input_ids": np.array([e.ids], dtype=np.int64), "attention_mask": np.array([e.attention_mask],
                                                                                          dtype=np.int64)}
        return self.session.run(["sentence_embedding"], feed)[0][0]


def unit(v: np.ndarray) -> np.ndarray:
    v = v.astype(np.float32)
    return v / np.maximum(np.linalg.norm(v, axis=-1, keepdims=True), 1e-12)


def rrf(*rankings: list[int], weights: tuple[float, ...] | None = None) -> list[int]:
    score: dict[int, float] = defaultdict(float)
    for i, ranking in enumerate(rankings):
        w = weights[i] if weights else 1.0
        for rank, doc in enumerate(ranking):
            score[doc] += w / (RRF_K + rank + 1)
    return [d for d, _ in sorted(score.items(), key=lambda x: -x[1])]


def metrics(ranking: list[int], relevant: set[int]) -> dict[str, float]:
    gains = [1.0 if d in relevant else 0.0 for d in ranking[:10]]
    dcg = sum(g / math.log2(i + 2) for i, g in enumerate(gains))
    ideal = sum(1 / math.log2(i + 2) for i in range(min(10, len(relevant))))
    first = next((i for i, d in enumerate(ranking[:10]) if d in relevant), None)
    return {
        "hit@10": float(first is not None),
        "mrr@10": 0.0 if first is None else 1.0 / (first + 1),
        "ndcg@10": dcg / ideal if ideal else 0.0,
        "recall@10": len(relevant & set(ranking[:10])) / len(relevant),
        "recall@50": len(relevant & set(ranking[:50])) / len(relevant),
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", type=Path, required=True)
    ap.add_argument("--vectors", type=Path, required=True)
    ap.add_argument("--model", type=Path, required=True)
    ap.add_argument("--prefix", default=None, help="override the manifest's query prefix (\"\" for none)")
    ap.add_argument("--dense-weights", default="1", help="hybrid: weights of the dense side to try, e.g. 1,2,3")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    dense_weights = [float(w) for w in args.dense_weights.split(",")]

    docs = [json.loads(line) for line in (args.eval / "corpus.jsonl").open(encoding="utf-8")]
    queries = [json.loads(line) for line in (args.eval / "queries.jsonl").open(encoding="utf-8")]
    row_of = {d["id"]: i for i, d in enumerate(docs)}

    n, dim = map(int, (args.vectors / "meta.txt").read_text().split())
    vec_ids = np.fromfile(args.vectors / "ids.i64", dtype="<i8")[:n]
    vecs = np.fromfile(args.vectors / "vecs.f32", dtype="<f4")[: n * dim].reshape(n, dim)
    # As the index holds them: int8, then compared by cosine
    corpus_vecs = np.zeros((len(docs), dim), dtype=np.float32)
    corpus_vecs[[row_of[int(i)] for i in vec_ids]] = unit(m.quantize_int8(vecs))
    missing = len(docs) - n
    if missing:
        print(f"warning: {missing} corpus lines have no vector")

    t0 = time.time()
    bm25 = BM25([d["text"] for d in docs])
    print(f"BM25 index in {time.time() - t0:.0f}s")
    embed = QueryEmbedder(args.model, args.prefix)

    per_task: dict[str, dict[str, list[dict]]] = defaultdict(lambda: defaultdict(list))
    examples = []
    t0 = time.time()
    for q in queries:
        relevant = {row_of[i] for i in q["relevant"] if i in row_of}
        if not relevant:
            continue
        lexical = bm25.search(q["text"], CANDIDATES)
        qv = unit(m.quantize_int8(embed(q["text"])[None, :]))[0]
        sims = corpus_vecs @ qv
        dense = np.argpartition(-sims, CANDIDATES)[:CANDIDATES]
        dense = dense[np.argsort(-sims[dense])].tolist()
        hybrids = [(("hybrid" if w == 1 else f"hybrid x{w:g}"), rrf(lexical, dense, weights=(1.0, w)))
                   for w in dense_weights]
        for name, ranking in [("lexical", lexical), ("dense", dense)] + hybrids:
            per_task[q["task"]][name].append(metrics(ranking, relevant))
        if len(examples) < 12 and q["qid"].endswith(("-0", "-1", "-2", "-300", "-301", "-302")):
            examples.append({"query": q["text"], "relevant": [docs[i]["text"][:120] for i in list(relevant)[:1]],
                             "dense_top3": [docs[i]["text"][:120] for i in dense[:3]],
                             "lexical_top3": [docs[i]["text"][:120] for i in lexical[:3]]})
    print(f"{len(queries)} queries in {time.time() - t0:.0f}s")

    report = {"corpus": len(docs), "queries": len(queries), "prefix": embed.prefix, "model": str(args.model),
              "vectors": json.loads((args.vectors / "embed-info.json").read_text("utf-8"))
              if (args.vectors / "embed-info.json").exists() else None, "tasks": {}}
    lines = [f"Corpus {len(docs)} lines, {len(queries)} queries, query prefix {embed.prefix!r}", ""]
    for task, systems in per_task.items():
        report["tasks"][task] = {}
        lines += [f"### {task} ({len(next(iter(systems.values())))} queries)", "",
                  "| system | hit@10 | mrr@10 | ndcg@10 | recall@10 | recall@50 |", "|---|---|---|---|---|---|"]
        for name, rows in systems.items():
            avg = {k: float(np.mean([r[k] for r in rows])) for k in rows[0]}
            report["tasks"][task][name] = avg
            lines.append(f"| {name} | " + " | ".join(f"{avg[k]:.3f}" for k in avg) + " |")
        lines.append("")
    report["examples"] = examples
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", "utf-8")
    (args.out / "report.md").write_text("\n".join(lines) + "\n", "utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()

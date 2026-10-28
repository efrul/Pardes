# Benchmark results

`build_eval_set.py` on SeforimLibrary release `v3-20261012193827`: 32,071 lines (the Shulchan Aruch, Berakhot,
Shabbat, Bava Kamma, Bava Metzia, Sanhedrin), 300 queries per task, seed 5785. Dense: exact search over the int8
vectors, queries embedded by the int8 ONNX model. Hybrid: RRF k = 60 over the first 150 of each side, as the app.

- **concept**: a Shulchan Aruch siman title -> the seifim of that siman (title removed from the text)
- **source**: a Rambam halacha (25 words) -> the Gemara lines the Ein Mishpat links to it (Hebrew -> Aramaic)

## 1. neodictabert-bilingual-embed as is (2026-10-24)

`int8, MatMul+Gather` (full-range weights, on a CPU with VNNI), query prefix `"query: "`:

| task | system | hit@10 | mrr@10 | ndcg@10 | recall@10 | recall@50 |
|---|---|---|---|---|---|---|
| concept | lexical (BM25) | 0.613 | 0.386 | 0.203 | 0.178 | 0.299 |
| concept | dense | **0.797** | **0.563** | **0.330** | **0.294** | 0.477 |
| concept | hybrid | 0.793 | 0.554 | 0.317 | 0.284 | 0.478 |
| source | lexical (BM25) | 0.553 | 0.363 | 0.326 | 0.398 | 0.495 |
| source | dense | 0.573 | 0.345 | 0.316 | 0.427 | 0.544 |
| source | hybrid | **0.607** | **0.386** | **0.353** | **0.453** | **0.559** |

Without the query prefix: concept dense 0.863 / 0.612 / 0.370 / 0.333 / 0.518 (better), source dense
0.553 / 0.338 / 0.306 / 0.407 / 0.523 (worse); hybrid about the same.

Reading:
- On concepts, the model as is already finds a relevant seif in the first ten for 80% of the queries, against 61% for
  BM25 (nDCG@10 0.330 against 0.203: +63%).
- From Hebrew to Aramaic (source) it is barely better than BM25: this is what fine-tuning on the library's links has
  to fix (Ein Mishpat, Mesorat HaShas, Targum and commentary links are its training pairs).
- Equal-weight RRF lowers the dense result on concepts, where BM25 is weak: a weight for the dense side is worth
  measuring once the model is final.

App path (`IndependentSemanticSearch` on a `pardes.vec.lucene` built by `VectorIndexBuilder`, Java ONNX Runtime
1.17.1, k = 150 as the app): top 10 overlap 0.967 with the exact search above, same first hit for 57/60 queries,
94 ms per search on a 4-core cloud CPU (embedding the query + HNSW).

## 2. Fine-tuned on the library's links (2026-10-25)

`train_model.py` on Kaggle (2x T4, DataParallel): 150,000 pairs of `build_train_pairs.py` (benchmark books and queries
held out), batch 128 per GPU, CachedMNRL, lr 2e-5, one epoch = 585 steps in 6.6 h. Measured here exactly as model 1
(int8 corpus and queries, `reduce_range`; int8 vs PyTorch: min 0.982, mean 0.988, same top-1 12/12):

| task | system | model 1 | fine-tuned | change |
|---|---|---|---|---|
| concept | dense hit@10 | 0.797 | **0.910** | +14% |
| concept | dense ndcg@10 | 0.330 | **0.415** | +26% |
| concept | dense recall@50 | 0.477 | **0.618** | +30% |
| concept | hybrid hit@10 | 0.793 | 0.860 | +8% |
| source | dense hit@10 | 0.573 | **0.653** | +14% |
| source | dense ndcg@10 | 0.316 | **0.374** | +18% |
| source | dense recall@50 | 0.544 | **0.626** | +15% |
| source | hybrid hit@10 | 0.607 | 0.637 | +5% |

BM25 is unchanged (concept 0.613, source 0.553 hit@10).

Reading:
- Fine-tuning helps on both tasks, most on concepts. From Hebrew to Aramaic (source) the model now clearly beats BM25
  (0.653 vs 0.553 hit@10), which the model as is did not.
- The dense search alone now beats the equal-weight hybrid on both tasks: the RRF should weigh the dense side more
  (or the lexical side only for exact phrases, as the app already does for quoted queries).
- Query latency of the fine-tuned int8 model here: 24 ms.

The dense side's weight in the RRF (`evaluate.py --dense-weights 1,2,3,5`), fine-tuned model, hit@10 / nDCG@10:

| hybrid | concept | source |
|---|---|---|
| dense alone | 0.910 / **0.415** | 0.653 / 0.374 |
| weight 1 (the app until now) | 0.860 / 0.337 | 0.637 / 0.373 |
| weight 2 | 0.893 / 0.378 | 0.663 / 0.383 |
| **weight 3** | **0.920** / 0.401 | 0.663 / **0.384** |
| weight 5 | 0.917 / 0.410 | **0.673** / 0.386 |

Weight 3: the hybrid beats dense alone on hit@10 on both tasks and on nDCG@10 for sources, and keeps the lexical side
for exact words. It is the model's `hybridDenseWeight` in `pardes-model.json` (`export_onnx.py --hybrid-dense-weight 3`),
which `HybridSearchEngine` applies; a model without it keeps the equal weights.

## 3. Distilled student (2026-10-26)

`distill_model.py` on Kaggle (2x T4): the fine-tuned model's layers 0, 4, 9, 14, 18, 22, 27 (7 of 28), a PCA
projection to 384 dimensions (95.2% of the variance), trained on 300,000 texts (MSE to the projected teacher, batch 32
per GPU; 128 ran out of memory), one epoch in 94 min. Measured on Kaggle (corpus embedded by PyTorch fp16, queries by
the int8 model; int8 vs PyTorch: min 0.989, mean 0.994, same top-1 12/12):

| task | system | Dicta as is | fine-tuned | student |
|---|---|---|---|---|
| concept | dense hit@10 | 0.797 | **0.910** | 0.860 |
| concept | dense ndcg@10 | 0.330 | **0.415** | 0.374 |
| concept | dense recall@50 | 0.477 | **0.618** | 0.598 |
| source | dense hit@10 | 0.573 | **0.653** | 0.617 |
| source | dense ndcg@10 | 0.316 | **0.374** | 0.345 |
| source | dense recall@50 | 0.544 | **0.626** | 0.614 |
| source | hybrid (weight 1) hit@10 | 0.607 | 0.637 | 0.630 |

The student keeps about half of what fine-tuning gained on the first ten (more on recall@50), at 6.6 ms per query
instead of 24, half the index (384 bytes per line) and about a quarter of the time to embed the library.

The dense side's weight in the RRF, student (`kaggle_run.py push embed`, measured as above), hit@10 / nDCG@10:

| hybrid | concept | source |
|---|---|---|
| dense alone | 0.860 / 0.374 | 0.617 / 0.345 |
| weight 1 | 0.820 / 0.328 | 0.630 / 0.369 |
| weight 2 | 0.870 / 0.358 | **0.643** / **0.372** |
| **weight 3** | **0.873** / 0.369 | 0.640 / 0.371 |
| weight 5 | **0.873** / **0.385** | **0.647** / 0.369 |

As for the fine-tuned model, weight 3 is the student's `hybridDenseWeight`: 2, 3 and 5 are within the noise of 300
queries of each other, and 3 leaves the lexical side more say for exact words than 5.

Measured again on searches like the users' own, on the whole library (the real searches, eval-v1, eval-100 and the
quote searches): weight 1 is clearly worse, and 1.5 to 4 are within the noise of each other
([`eval-real/results-dense-weight.md`](eval-real/results-dense-weight.md)).

## 4. The whole library (2026-10-26)

The student over the library of release `v3-20261012193827`: the 5,725,672 lines of 3 words or more.

- Embedding (`kaggle_run.py push embed --chosen student`, one T4, fp16): 187 min, after ~45 min of measuring the
  lines' token lengths (tokenizing them all takes ~40 min on 4 CPUs); 274 min for the whole stage.
- `push int8`: the vectors as int8, 2.2 GB (8.8 GB as float32), in 3 min.
- `VectorIndexBuilder` here (4 CPUs, `-Xmx8g`, Panama vector API): 112 min; `pardes.vec.lucene` is 2.5 GB (2.2 GB of
  vectors, a 324 MB graph), every vector on a line of the database.
- Checks: 300 random rows of the vectors against their line's text embedded again by the int8 ONNX model, cosine min
  0.973, mean 0.990 (against the next row: 0.313, what a shifted id would give); the app's query vectors (Java ONNX
  Runtime 1.17.1, `HfTokenizer`) against Python's: cosine min 0.998.
- Through the app's search (`IndependentSemanticSearch`, k = 150 on both fields): 19-24 ms per benchmark query,
  34-39 ms for a 40-word query.

The HNSW graph finds 88% of the exact search's first ten (top-10 overlap 0.880 at k = 150, 0.887 at k = 1000, over 200
queries of 40 words taken from random lines; the line itself is in the first ten for 169/200, against 187/200 by exact
search). A larger k does not recover it: the graph (`Lucene99HnswVectorsFormat(16, 100)`, four flushed segments merged
into one) does not reach some of the nearest lines. To try: M 32 and beam width 200.

The benchmark's queries over the whole library (dense, the app's search), hit@10: concept 0.587, source 0.147. These
are not comparable with the 32,071-line corpus above: the benchmark counts only the seifim (concept) and the Gemara
lines (source) as relevant, and the whole library holds what a user would also want first. A source query (a Rambam
halacha) finds the halacha itself first, then the Shulchan Aruch and the Aruch HaShulchan that bring it; a siman's
title finds the Tur, the Mishnah Berurah and the encyclopaedias on the same siman next to its seifim.

## 5. The fine-tuned model on training questions (2026-10-27)

`kaggle_run.py push tune`: model 2 trained one more epoch on 20,000 of its link pairs and the 390 training questions of
[train-v2](train-v2/README.md)'s batches 1-2 (their targets outside this benchmark), each 10 times: 23,900 pairs in
65 min on 2x T4. Measured as 3 (int8 vs PyTorch: min 0.971, mean 0.985):

| task | system | fine-tuned | on the questions |
|---|---|---|---|
| concept | dense hit@10 | **0.910** | 0.900 |
| concept | dense ndcg@10 | **0.415** | 0.414 |
| concept | dense recall@50 | 0.618 | **0.629** |
| source | dense hit@10 | 0.653 | **0.667** |
| source | dense ndcg@10 | 0.374 | **0.394** |
| source | dense recall@50 | 0.626 | **0.653** |

Nothing learnt from the links is lost. On the users' kind of searches (the real searches, eval-v1, eval-100, the quotes)
and on 430 held-out training questions, the two are within the noise of each other, and both beat the distilled student
on the real searches ([`eval-pool/results-tune.md`](eval-pool/results-tune.md)).

## 6. The student distilled on searches too (2026-10-27)

`kaggle_run.py push distill --teacher tuned --queries`: the same 7-layer / 384-dim student, distilled from model 5 on the
300,000 texts of the pairs and 145,169 texts like searches (`build_distill_queries.py`: openings of lines, headings,
runs of 2-4 words, the training questions), 237 min. Measured as 3 (int8 vs PyTorch: min 0.984, mean 0.994):

| task | system | student 1 | student 2 |
|---|---|---|---|
| concept | dense hit@10 | 0.860 | **0.887** |
| concept | dense ndcg@10 | 0.374 | **0.389** |
| concept | dense recall@50 | 0.598 | **0.611** |
| source | dense hit@10 | 0.617 | **0.633** |
| source | dense ndcg@10 | 0.345 | **0.366** |
| source | dense recall@50 | 0.614 | **0.632** |
| source | hybrid (weight 1) hit@10 | 0.630 | **0.643** |

On the real searches, with every line it brings into the first three judged, it gives a good first result in 61 of
153 against 45-46 and a good one in the first three in 83 against 69, the teacher's level; eval-v1 (128 against 129)
and the held-out questions are within the noise, the quotes' source comes first a little less often
([`eval-pool/results-student2.md`](eval-pool/results-student2.md)).

## 7. The whole library with student 2 (2026-10-28)

Student 2 (`student@695cbe2/pardes-clean-1`) over the 5,725,672 lines of section 4.

- `push embed --chosen student`: 281 min for the whole stage; `push int8`: 2 min, 2.2 GB.
- `VectorIndexBuilder` here (4 CPUs, `-Xmx8g`, other jobs beside it): 116 min. `pardes.vec.lucene` is 2.5 GB (2.2 GB of
  vectors, a 336 MB graph).
- [`check_vectors.py`](check_vectors.py), 300 random rows against their line embedded again by the int8 ONNX model:
  cosine min 0.976, mean 0.991 (0.273 against the next row's line).
- [`eval-100/harness/IndexCheck.kt`](eval-100/harness/IndexCheck.kt) and [`check_index.py`](check_index.py), 455
  queries (the 155 real searches, eval-100's 100, 200 runs of 40 words from random lines):
  - the app's query vectors (Java ONNX Runtime 1.17.1, `HfTokenizer`) against Python's (onnxruntime 1.31, tokenizers):
    cosine min 0.998, mean 0.9998;
  - the app's dense search (`IndependentSemanticSearch`, k = 150, the query's embedding included, 4 idle CPUs):
    13 ms on average for a real search (10 ms over the base books alone), 34 ms for 40 words.

**Through the app, every line it brings into the first three judged** (185 more grades,
[`eval-pool/results-student2.md`](eval-pool/results-student2.md)): on the 153 real searches, a good first result in 58
against student 1's 50 and a good one in the first three in 80 against 69 (nDCG@3 0.578 against 0.477); eval-v1, eval-100
and the held-out questions within the noise; the quotes' source first a little less often (Gemara 32 of 40 against 35).

### The graph misses some nearest lines of a search over all books

The app's search keeps to the base books when the lexical side finds something there (116 of the 155 real searches,
50 of eval-100's 100). Their 242,109 lines are a field of their own, whose graph finds the exact search's first ten
(every vector of the field, by cosine): 0.99. Over all books (the 5,483,563 other lines too) it misses a quarter:

| the exact first ten found, by score | student 1's index | student 2's index |
|---|---|---|
| real searches over all books (39) | 0.744 | 0.767 |
| eval-100 over all books (50) | 0.818 | 0.822 |
| 40 words of a random line, all books (200) | 0.898 | 0.940 |
| real searches over the base books (116) | 0.995 | 0.993 |

By score: a line counts when it scores at least as high as the exact tenth, so lines of the same text count as found
whichever of them comes (9% of the library's lines share their vector with another, in groups of up to 6,389). The
misses are mostly short lines and headings of modern works (מיקרופדיה תלמודית, דור המלקטים, the וזה לשונו series).
Nothing tried brings them back:
- a larger k (the graph search's beam): 0.657 at 150, 0.677 at 2,000 (the 155 real searches, all over all books, on
  student 1's index);
- `SeededKnnVectorQuery`, the graph search started from the lexical side's first 10, 30 or 150 lines: 0.744 to 0.751;
  started from 3 of the exact first ten, it finds 0.768 of them (from all ten, 1.0);
- a denser graph, M 32 and beam width 200 against the app's 16 and 100, on a million lines of student 2 (the 75 books
  holding most misses, one segment each): 0.782 against 0.872 for the real searches, 0.808 against 0.932 for eval-100,
  in twice the build time.

Short queries (with the model's "query: " prefix) sit apart from the lines the graph links, and its greedy search does
not reach them from there: a known weakness of HNSW with queries unlike the documents. **It costs the pages little**:
[`eval-100/harness/ExactCands.kt`](eval-100/harness/ExactCands.kt) runs every evaluation with the exact first 150 as the
dense side, an ideal index:

| | the app (the graph) | an ideal index |
|---|---|---|
| real searches: good first / in the first three / nDCG@3 | 58 / **80** / 0.578 | 58 / 75 / 0.576 |
| eval-v1: good first / in the first three / nDCG@3 | 128 / 154 / 0.632 | 128 / 155 / 0.636 |
| eval-100: the source in ten / first | 36 / 16 | 36 / 16 |
| Gemara quotes, the source first (base books, all books) | 32, 31 | 33, 32 |
| the 430 held-out questions: the target in the page's ten | 169 | **178** |

The lines the graph misses are seldom better answers; only the held-out questions, each with one target line, gain 2%.

## Speed and size of the query model (4-core cloud CPU, one short query)

| model | layers | dimensions | int8 ONNX | query |
|---|---|---|---|---|
| neodictabert-bilingual-embed | 28 | 768 | 366 MB | 88 ms |
| distilled student (`distill_model.py --layers 7 --dim 384`) | 7 | 384 | 166 MB | 6.6 ms |

The student's index is half the fine-tuned model's (384 bytes per line instead of 768): 2.2 GB of vectors for the
5,725,672 lines of 3 words or more of the library.

## int8 on CPUs without VNNI

The same int8 model on Kaggle's CPU (no VNNI): cosine to PyTorch min 0.78, mean 0.95, against 0.98 / 0.99 here
(AVX-512 VNNI). ONNX Runtime's int8 kernels saturate on x86 without VNNI when the weights use the full 8-bit range, and
many desktop CPUs have no VNNI. `export_onnx.py` now quantizes with `reduce_range` (7-bit weights), the documented
remedy: on this CPU it gives the same accuracy (min 0.983, mean 0.991). Its check against PyTorch is what caught it.

# semantic-model

The open semantic search of Pardes: [neodictabert-bilingual-embed](https://huggingface.co/dicta-il/neodictabert-bilingual-embed)
by Dicta (CC BY 4.0, see [NOTICE.md](NOTICE.md)), fine-tuned and distilled on the library's own links, exported to
the int8 ONNX model the app loads, and the vector index it searches. Nothing here comes from Zayit's semantic search
package or its model.

## Pipeline

| Step | Script | Where | Output |
|---|---|---|---|
| Library | `fetch_library.py --out build/library` | anywhere | `seforim.db` (8 GB) of a SeforimLibrary release |
| Benchmark | `build_eval_set.py --db … --out build/eval` | CPU | `corpus.jsonl`, `queries.jsonl` |
| Training pairs | `build_train_pairs.py --db … --out build/train/pairs.jsonl` | CPU | 400k query/passage pairs from the links |
| Fine-tune (optional) | `train_model.py --pairs … --out build/teacher` | GPU | sentence-transformers model |
| Distill (optional) | `distill_model.py --teacher build/teacher --pairs … --out build/student` | GPU | 7 layers, 384 dims |
| Export | `export_onnx.py [--source build/student] --out build/model` | CPU | `model.int8.onnx`, `tokenizer.json`, `pardes-model.json` |
| Embed | `embed_corpus.py --db … --backend torch --device cuda --dtype float16 [--source …] --out build/vectors` | GPU | `ids.i64`, `vecs.f32`, `meta.txt` |
| int8 (optional) | `quantize_vectors.py --vectors build/vectors --out build/vectors-int8` | CPU | `vecs.i8`: the same vectors as the index stores them, a quarter of the size |
| Index | `./gradlew :SeforimApp:buildVectorIndex -Pvectors=build/vectors -Pdb=… -Pout=…/pardes.vec.lucene` | CPU | `pardes.vec.lucene` |
| Check the vectors | `check_vectors.py build/vectors-int8 build/model seforim.db` | CPU | 300 rows against their lines embedded again |
| Check the index | `eval-100/harness/IndexCheck.kt seforim.db queries.tsv check.jsonl`, then `check_index.py check.jsonl …/pardes-model` | CPU | the app's query vectors against Python's, the graph's first ten against an exact search, the search's time |
| Pack | `package_pack.py --model build/model --index …/pardes.vec.lucene --library v3-… --pack N --out build/pack`, then `--verify build/pack` | CPU | `pardes-semantic.tar.zst` (or its `.partNN`) and its manifest, SeforimLibrary's bundle format: efrul/Pardes's `semantic-release.yml` publishes it (its `docs/SEMANTIC_PLAN.md`), titled and described by `release_notes.py` (`semantic-notes.yml` writes them again on a pack already released) |
| Measure | `embed_corpus.py --corpus build/eval/corpus.jsonl --model build/model …` then `evaluate.py` | CPU | `report.md` |

The benchmark's books (the Shulchan Aruch, Berakhot, Shabbat, Bava Kamma, Bava Metzia, Sanhedrin) and the Rambam
halachot it queries with are kept out of the training pairs, so the benchmark measures what training did, not what
it memorised. Every candidate (the Dicta model as is, fine-tuned, distilled) is exported, embeds the same benchmark
corpus and goes through `evaluate.py`; the one that ships is the one whose numbers say so.

`.github/workflows/semantic-model.yml` runs the export and the benchmark of the Dicta model on a CPU runner.

## In the app

Next to the library's `seforim.db.lucene`, in the databases folder:

```
pardes-model/          model.int8.onnx, tokenizer.json, pardes-model.json, NOTICE.md (or PARDES_MODEL_DIR)
pardes.vec.lucene/     the vector index
```

`IndependentSemanticSearch` uses them when both are there and the index was embedded with the model's `id`
(`pardes-model.json` and the index's commit data); otherwise it falls back to what it did before. The query is cleaned
(`OpenModelCleaner`, = `pardes_semantic/clean.py`) and tokenized (`HfTokenizer`, = the tokenizers library, checked by
`HfTokenizerParityTest`) exactly as the corpus was.

Lines added by a delta update after the index was built have no vector: the lexical search finds them, the semantic
one does not until the index is rebuilt for the new release.

## On Colab (the free T4 works)

Open [colab.ipynb](colab.ipynb) in Colab (File → Upload notebook, or from GitHub), choose a T4 GPU, add a GitHub token
as the `GITHUB_TOKEN` secret, and run the cells. It measures the Dicta model, fine-tunes, distills, measures each and
embeds the library with the chosen one; everything long is kept in Google Drive and resumes after a disconnect
(training and distillation from their last checkpoint, embedding from its last batch). On a T4 the scripts use fp16
(it has no bf16).

## On Kaggle (free GPU, runs with the browser closed)

`kaggle_run.py` drives it from any machine with an API token (kaggle.com > Settings > API > Generate New Token, as
`KAGGLE_API_TOKEN`, with the user name as `KAGGLE_USERNAME`; a legacy key works too, as `KAGGLE_KEY`). The account must
be phone-verified for GPU and internet:

```bash
pip install "kaggle>=1.8"
python kaggle_run.py push train && python kaggle_run.py wait train && python kaggle_run.py output train
python kaggle_run.py push distill && python kaggle_run.py wait distill && python kaggle_run.py output distill
python kaggle_run.py push embed --chosen student --release v3-20261012193827   # after reading the reports
python kaggle_run.py wait embed && python kaggle_run.py push int8 && python kaggle_run.py wait int8
python kaggle_run.py output int8      # vectors-int8 (2.2 GB) and model-student, for buildVectorIndex
python kaggle_run.py push tune --questions build/train/questions.jsonl --hours 3   # train's teacher on the questions
python kaggle_run.py push pool --spec build/pool.json --release v3-20261012193827  # the models on the evaluations
```

Each stage is a private script kernel with the code inside it (no access to this repository needed); `distill` reads
`train`'s output, `embed` both, `int8` embed's. `embed` exports the query model first (with `--weight`, its
`hybridDenseWeight`), measures its RRF weights on the benchmark, then embeds the library of `--release`. A stage stops its training before `--hours` (8 by default) so that its output is
kept; `push ... --dry-run` writes the kernel to `build/kaggle` without sending it.

`tune` goes on from train's teacher with the training questions of [train-v2](train-v2/README.md) (`build_pairs.py
--hold-benchmark`, each `--repeat` times) and the first `--links` of train's pairs, then measures it on the benchmark.
`pool` ranks the evaluations' candidates by the base model and by each model of train, distill and tune
([eval-pool](eval-pool/README.md)): the models compared on the real searches without embedding the library.

## On a rented GPU (RunPod, Lambda...)

One A100/H100 (40 GB+), CUDA torch (`pip install torch --index-url https://download.pytorch.org/whl/cu124` before
`pip install -r requirements.txt`):

```bash
python fetch_library.py --out build/library
python build_eval_set.py --db build/library/seforim.db --out build/eval
python build_train_pairs.py --db build/library/seforim.db --out build/train/pairs.jsonl
python train_model.py --pairs build/train/pairs.jsonl --out build/teacher                       # ~2-4 h
python distill_model.py --teacher build/teacher --pairs build/train/pairs.jsonl --out build/student  # ~1-2 h
for m in teacher student; do
  python export_onnx.py --source build/$m --out build/model-$m
  python embed_corpus.py --corpus build/eval/corpus.jsonl --backend onnx --model build/model-$m --out build/eval/vec-$m
  python evaluate.py --eval build/eval --vectors build/eval/vec-$m --model build/model-$m --out build/eval/report-$m
done
# The chosen one, over the whole library (5.7M lines of 3 words or more; ~1 h on a T4 for the student):
python embed_corpus.py --db build/library/seforim.db --backend torch --device cuda --dtype float16 \
    --source build/student --out build/vectors
```

Keep `build/vectors` (with its `embed-info.json`) and `build/model-*`: the vector index is built from them on any
machine, and a model is only usable with vectors it embedded.

## Licence

The scripts are part of Pardes (AGPL-3.0). The model and anything derived from it (the fine-tuned and distilled models,
the vectors) carry Dicta's CC BY 4.0: ship [NOTICE.md](NOTICE.md) with them and keep the attribution in the app.

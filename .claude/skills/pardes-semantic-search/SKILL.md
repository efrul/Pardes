---
name: pardes-semantic-search
description: Pardes desktop's open semantic search (Dicta's neodictabert-bilingual-embed, fine-tuned on the library's links, ONNX int8 + Lucene HNSW + RRF) and its model pipeline in semantic-model/ (export, benchmark, fine-tuning, distillation, Kaggle runs, vector index). Use for any task on semantic or hybrid search, the embedding model, ONNX, the tokenizer parity, the benchmark, training or distilling, Kaggle or Colab GPU runs, pardes.vec.lucene, "smart find", or the common sources line above the search results (שורת מקורות משותפים), even when the request doesn't name them (e.g. "the search doesn't find by meaning", "train the model again", "why is the vector index ignored").
---

# Pardes semantic search

Read `SEMANTIC_SEARCH.md` (app side), `semantic-model/README.md` (pipeline) and `semantic-model/BENCHMARK.md` (numbers)
first, and `docs/COMMON_SOURCES_PLAN.md` before building the common sources line above the results. Everything is open: Dicta's model (CC BY 4.0), ONNX Runtime, Lucene. Never use Zayit's semantic package or its
model for Pardes; the app loads the legacy model only when `SEFORIM_MODEL_DIR` points at it, for a local comparison.

## Invariants (break one and search silently returns wrong lines)

- **Query and corpus share the cleaner and tokenizer.** `pardes_semantic/clean.py` = `OpenModelCleaner.kt`;
  the tokenizers library = `HfTokenizer.kt`. After touching either, regenerate the fixtures
  (`make_parity_fixtures.py --db ...`, base books only: the fixtures are committed) and run `HfTokenizerParityTest`.
  A cleaner change is a new `CLEANER_ID` and a re-embedded corpus.
- **Index and model share the id** (`pardes-model.json` `id` = the index's commit `pardes.model`); the app refuses a
  mismatch. The id covers the weights and the cleaner, not the quantization.
- **int8 with `reduce_range`.** Full-range int8 saturates on x86 without VNNI (Kaggle's CPU: cosine 0.78). The export
  checks int8 against PyTorch and fails below min 0.97 / mean 0.985.
- **Mean pooling and L2 are in the ONNX graph**; output `sentence_embedding` [batch, dim]. Queries get `"query: "`.

## Pitfalls already solved

- NeoBERT's `onnx_eager` attention multiplies scores by the mask (padding leaks into batches): export the SDPA path,
  with `nn.RMSNorm` swapped for an eager one (opset 17, the app's ONNX Runtime 1.17).
- Java's Unicode `\w` includes the joiners (U+200C/D); Oniguruma's doesn't: `HfTokenizer.onigWordClass`.
- ktlint (CLI 1.5.0, the repo's `.editorconfig`, *not* `android_studio`) rewrites `\uXXXX` escapes into invisible
  characters: write regex escapes (`"\\u00A0"`).
- Kaggle: `/kaggle/src` is read-only (unpack to /tmp); GPU and internet need a phone-verified account; a run cut off
  by the session loses its output (`--deadline`); child output is buffered unless `PYTHONUNBUFFERED`; the distillation
  ran out of GPU memory until the student was cut on the CPU; a full-model checkpoint with optimizer is ~4.4 GB.
- Meaning highlights (`Passages`, `IndependentSemanticSearch.bestPassages`): passages are verbatim ranges of the line,
  since `SearchEngine.highlights` finds them again with `indexOf`; they are embedded as documents (`documentPrefix`),
  the query with its prefix. A results snippet's text is HTML-escaped (`&amp;`, `&nbsp;`, `SnippetSources.clean`;
  quotes stay `"`): the cutter must not end a sentence at an entity's `;`. Check a change with the real model on
  the whole library: a page of 25 is refined in 0.8 s on average (1.5 s p90) on 4 CPUs, a line already embedded
  (the preview, the reader) in under 1 ms.
- Gradle can't resolve the Kotlin plugin through the plugins portal in the cloud container: compile the semantic
  package with a standalone kotlinc (`kotlin-compiler-embeddable` from Maven Central) plus SeforimLibrary's
  `search/` types, and let the PR's CI run Gradle.
- A new index: `check_vectors.py`, then `eval-100/harness/IndexCheck.kt` + `check_index.py`, then every evaluation
  through `Eval.kt`/`Cands.kt` and `eval-real/level_order.py` (BENCHMARK.md, section 7). The HNSW graph misses about a
  quarter of the exact first ten in searches over all books with short queries (0.99 over the base books): known, not
  fixed by a larger k, `SeededKnnVectorQuery` or M 32. An ideal index (`ExactCands.kt`) does not improve the judged
  pages, and finds 2% more of the held-out questions' targets.
- The lexical side throws `TooManyNestedClauses` on some long queries (a 12-word Gemara quote, 5 held-out
  questions): `Cands.kt` and `ExactCands.kt` record it in `errors`; `Eval.kt` stops there.
- Publishing a pack: `package_pack.py` (SeforimLibrary's bundle format, so the app's `BundleManifest` and
  `ExtractUseCase` read it), upload its folder as a private Kaggle dataset, then the owner runs efrul/Pardes's
  `semantic-release.yml` (secret `KAGGLE_API_TOKEN`). A cloud session cannot create releases itself ("not permitted
  for this session type"). Releases there are always `--latest=false` (the Android release is the Latest), and few:
  Android 0.3.0 to 0.4.1 reads only the 30 newest. The app installs a pack itself (Settings › ניהול נתונים, code in
  `features/search/semantic/pack/`): the newest `semantic-<pack>-<library>` release (of its builds, the installed
  library's when there is one: SeforimLibrary keeps a line's id from release to release, its DELTA_UPDATE_WORKFLOW.md, so a
  pack works on every library release and is never refused for another), downloaded or imported from the release's files, unpacked beside the library and swapped in at the next start; while
  none is in use, every start opens the settings on that card, which says how to install one (`SemanticPackPrompt`);
  `docs/SEMANTIC_PLAN.md` has the plan and its state.

## Android (Pardes for Android)

The same model and pack run on Android (`androidApp/`, since 2026-10-28): `androidApp/docs/DEVELOPMENT.md` › Semantic
search and `androidApp/docs/PARITY.md` › Pardes desktop's semantic search; the `zayit-android` skill covers the device.
Desktop's files that compile there are built unchanged (`syncDesktopSemantic`), the rest are Android variants in
`androidApp/.../android/search/semantic/` with desktop's packages and names, each with a header of its changes: after a
change here, port it there too (the header says what differs), and run the Android unit tests, which are desktop's.
- ONNX Runtime is 1.22.0 there, the first whose JNI library is 16 KB-aligned too (1.20 and 1.21 align only
  `libonnxruntime.so`), 64-bit only; the model must stay loadable by it.
- Android's regex is ICU: no `Pattern.UNICODE_CHARACTER_CLASS` (it throws). The Android `HfTokenizer` spells out
  Oniguruma's `\w`, `\s` and `\d`; a new tokenizer pattern class needs the same there, and `HfTokenizerParityTest` runs
  on a device (`androidInstrumentedTest`) with this repository's fixtures.

## Running the GPU steps

`kaggle_run.py push train|tune|distill|embed|int8|pool` (token as `KAGGLE_API_TOKEN` or `~/.kaggle/access_token`, user in
`KAGGLE_USERNAME`), then `wait` / `output`; `kaggle kernels logs -f <user>/pardes-semantic-<stage>` shows a running log.
Or `colab.ipynb`. Measure every candidate with `embed_corpus.py --corpus` + `evaluate.py` (`--dense-weights 1,2,3`) on
the same benchmark, and on the real searches with `push pool` (`semantic-model/eval-pool/`), before choosing; export the
chosen one with its `--hybrid-dense-weight`.

Kaggle pitfalls already met:
- The Kaggle account's name never goes into this public repository, its workflows or their logs (it was scrubbed from
  the history on 2026-10-28): name datasets and kernels without their owner (`pardes-semantic-pack-2`, `<user>/…` in
  commands). `semantic-release.yml` takes the owner from the token and masks it, since Kaggle prints it with every file.
- The library is 5,725,672 lines of 3 words or more: its float32 vectors are 8.8 GB; `push int8` makes them 2.2 GB
  before any download. Tokenizing them takes ~40 min on 4 CPUs, twice in `embed_corpus.py` (lengths, then batches).
- A **private** dataset has no whole-dataset download ("Data not available. This dataset may need its archive
  recreated"), only file by file; and Kaggle unpacks every uploaded `.zip`, nested ones too. Give a pack to a user as
  one zip named `<name>.zip.bin` (kept as is), downloaded from the file list, renamed to `.zip`.
- transformers warns of an "incorrect regex pattern" in the model's tokenizer: a false alarm here (52k library lines
  and the benchmark's queries tokenize identically to Dicta's `tokenizer.json`, which the app's queries use).

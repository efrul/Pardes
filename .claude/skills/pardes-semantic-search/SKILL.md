---
name: pardes-semantic-search
description: Pardes desktop's open semantic search (Dicta's neodictabert-bilingual-embed, fine-tuned on the library's links, ONNX int8 + Lucene HNSW + RRF) and its model pipeline in the efrul/pardes-semantic repository (export, benchmark, fine-tuning, distillation, Kaggle runs, vector index). Use for any task on semantic or hybrid search, the embedding model, ONNX, the tokenizer parity, the benchmark or an outside evaluation set (crfh1672/search-queries), training or distilling, Kaggle or Colab GPU runs, pardes.vec.lucene, "smart find", or the common sources line above the search results (שורת מקורות משותפים), even when the request doesn't name them (e.g. "the search doesn't find by meaning", "train the model again", "why is the vector index ignored").
---

# Pardes semantic search

Read `SEMANTIC_SEARCH.md` (app side), and pardes-semantic's `README.md` (pipeline) and `BENCHMARK.md` (numbers)
first. Since 2026-11-05 the model is its own repository, https://github.com/efrul/pardes-semantic: its pipeline,
evaluations and Kaggle runs, and the trained models as its releases (`models.yml`). It holds this repository as its
`pardes` submodule, for the tools that check the model against the app's code. Add it to the session (`add_repo`)
for any model work; the paths of the model's scripts below are in it. Here, `semantic-model/` keeps only `NOTICE.md`
and the packs' `release_notes.py`. Read `docs/COMMON_SOURCES_PLAN.md` before building the common sources line above the results. Everything is open: Dicta's model (CC BY 4.0), ONNX Runtime, Lucene. Never use Zayit's semantic package or its
model for Pardes; the app loads the legacy model only when `SEFORIM_MODEL_DIR` points at it, for a local comparison.

The app side lives in Zayit's code, so the `zayit-fidelity` skill's rules hold for every change: written as the
code around it, the smallest hook in Zayit's files, and every file added or changed here (`features/search/semantic/`,
what is left of `semantic-model/`, their tests) described in `docs/ZAYIT_CHANGE_MAP.md` in the same commit.

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
  pages, and finds 2% more of the held-out questions' targets. A cause found on 2026-11-04: identical lines (a heading
  repeated hundreds of times, "הפרדס לרש"י הלכ' שבת" in קובץ שיטות קמאי, "סעיף קטן נח" in כף החיים) share one vector,
  each the others' neighbors, and a walk that enters such a cluster can't leave it: `vec` returns 33 of 150 for "שבת",
  "יין נסך" and "חמץ בפסח" (all identical). With a books filter Lucene then falls back to an exact search of the whole
  filter (0.4 to 1 s in תלמוד or הלכה). The fix is an index with each text once (SEMANTIC_SEARCH.md, known limitations).
  Until a pack has one, the app gets out of the trap itself (`49574d08`, 2026-11-10): the dense search walks the
  graph on its own, and a walk that comes back short starts again from the words' first 64 hits (4 seeds stayed
  trapped; seeds in base books are not in the `vec` graph). 150 lines in 20 to 30 ms, the Talmud's first page in 56 to
  82 ms where it took 272 to 326; only a search the words find nothing for still scores a whole scope. Lucene 10.5's
  `searchNearestVectors` takes `AcceptDocs`, not `Bits`. `DenseTrapTest` asserts three in four of the exact
  neighbours, which is what the walk reaches.
- **Measure a search by scope on a real library before and after** (`ScopeSearchBench`, run alone; a debug line in the
  log times each stage of a search): the query's vector about 10 ms (the last queries' vectors are kept), the dense
  side 1 to 36 ms, the words 20 to 80, the facets 2 to 22. The slow parts found were around the search, not in it: the
  results' tree read each book from the database (0.2 to 0.8 s the first time in a tab; from the catalog now, 5 to
  58 ms), and the bar's categories read the commentary links at the first letter typed (1.2 s; at start now).
- **Measure what the reader waits for with the app's own view model** (`ScopeFlowBench`, docs/scope-search-speed:
  `SearchResultViewModel` on the app's engines, the screen's flows collected, a tab and "all" after the search, at a
  reader's pace of 300 ms; `tools/bench.init.gradle` runs it, with a JFR recording on demand). A tab waited 0.9 to
  1.8 s (2026-11-12) though the search itself took 25 to 220 ms: every page's passages by meaning (a second and more
  of the model on 4 CPUs) held a thread of the default dispatcher, none stopped when its page was replaced, and the
  tab's code queued behind them; the JFR profile showed no CPU in it. The passages now run one request at a time
  (`passageWork`) and a closed session's stop between the model's runs (`Refining`, `keepGoing`). A bench that waits
  for each page's bold passages before the next click, or one that closes the engine while they run (SIGSEGV in
  onnxruntime), measures something else.
- **The tabs above the results are views of the list found** (`SessionViews`, `67e79fdd`): a hybrid search lists the
  first lines it finds, so searching a category again gave another list. A category with no line in the list is
  still searched. The tabs picked become the bar's chips, and a repeated search searches in them.
- **The library's order** (`CatalogOrderEngine`, `CatalogOrderSession`, `c7394579`): the books that have lines found
  (from the facets) are read a few at a time in the catalog's order, each group in one session of the words; the
  lines the meaning search alone found stand among them, only those that stand out of the dense side's candidates by
  2 standard deviations or more (`STANDOUT`, `HybridSearchEngine.standingOf`; decided 2026-11-12 after a fixed 10 was
  found wrong, docs/catalog-order); the others are counted in the status line ("ועוד N קרובות פחות").
- **Searches of other apps** reach the feedback packages since `e525a560` (an installed Zayit's and Otzaria's, each
  row with its `source`; Otzaria's with its `scope`, `bookTitle`, `heRef` and no line id). efrul/pardes-semantic's
  `feedback/collect.py` keeps the source, and places Otzaria's votes by book title and reference when they name
  exactly one line.
- A filtered search keeping few vectors (at most `EXACT_LIMIT` and one in `EXACT_SHARE` of the field's) is exact in
  `IndependentSemanticSearch.searchField`: Lucene's filtered graph search is slower there and misses some. Lucene's own
  exact search needs `--add-modules jdk.incubator.vector` for SIMD (3 to 5 times faster); the app's JVM doesn't pass it.
- The results list with the pack is the fused list alone, as Zayit's: the lexical side's first 150 and the dense side's
  150, its total its length (the user's decision, 2026-11-04, after `6a1aa27` had it go on past the candidates). Quoted
  queries and a TOC section are lexical, whole, as in Zayit; a search the dense side answers nothing for is lexical,
  whole (Zayit stops at 150 there). The facets' counts (the tree, "עוד N תוצאות מ...") stay lexical, as in Zayit.
- Java in the cloud container reads Hebrew command-line arguments as `?` (POSIX locale): give a harness its queries in a
  file, and `-Dstdout.encoding=UTF-8` for its output.
- The lexical side throws `TooManyNestedClauses` on some long queries (a 12-word Gemara quote, 5 held-out
  questions): `Cands.kt` and `ExactCands.kt` record it in `errors`; `Eval.kt` stops there.
- Publishing a pack: `package_pack.py` (SeforimLibrary's bundle format, so the app's `BundleManifest` and
  `ExtractUseCase` read it), upload its folder as a private Kaggle dataset, then the owner runs efrul/Pardes's
  `semantic-release.yml` (secret `KAGGLE_API_TOKEN`). Its title and notes are `release_notes.py`'s, which the user reads:
  "קובצי החיפוש לפי משמעות" and "גרסה N", never "חבילה"; after changing them, `semantic-notes.yml` writes them again on a
  pack already released. A cloud session cannot create releases itself ("not permitted
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
the same benchmark, and on the real searches with `push pool` (`pardes-semantic/eval-pool/`), before choosing; export the
chosen one with its `--hybrid-dense-weight`.

The next run (teacher 3 and student 3: one teacher on Chavruta and the dictionary, judged by `eval-crfh/`) is planned in
`pardes-semantic/RUN3_PLAN.md`, with its commands; `eval-pool/kernel_spec.py` takes the last `pool.json` out of the pool
kernel's script (`kaggle kernels pull`).

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

## Outside evaluation set: crfh1672/search-queries

[crfh1672/search-queries](https://github.com/crfh1672/search-queries) (as of 2026-11-01; check its branches again)
builds a test set for semantic search over Otzaria's books, to measure Otzaria's smart search against its regular one.
A JSONL record holds a query (`conceptual` or `short`), the correct quote and a near miss from the same book (heading
path, line), `split` and `verified_by_human`; each book pins its otzaria-library `.txt` by commit and SHA-256.
- **Its data:** none on `main`. 422 drafts are on two unmerged PR branches, `add-berakhot-chagiga` and
  `readd-existing-datasets` (`drafts/תלמוד בבלי/<tractate>/`): Berakhot 125, Megillah 106, Moed Katan 105, Chagiga 51,
  Taanit 35. All are `test`, none is human-verified (`verifiers.txt` names no one), and they are probably model-written
  (its `skill/` writes them: its rules, not ours). Questions in today's Hebrew on the Gemara's wording, median 8 words
  (the 36 `short` ones 4, as real searches); a quarter share no word with their quote.
- **Reading it:** it is public, so `add_repo` (read) answers that the proxy serves it. Clone with
  `GIT_LFS_SKIP_SMUDGE=1`, fetch each branch by name, and `git archive origin/<branch> drafts | tar -x` into the
  scratchpad.
- **For measuring only, never for training:** all `test`, unverified, and 422 beside 150k pairs. What it adds is the
  Hebrew → Aramaic bridge in the Bavli (`eval-v1` has 32 Bavli questions with a target), written outside this pipeline.
- **Held out of training by tractate** (2026-11-03): `train-v2/sample_targets.py`'s `OUTSIDE_SET_TRACTATES` (its five
  tractates) are left out whole by `build_chavruta_pairs.py`, `build_dictionary_pairs.py` and
  `train-v2/build_pairs.py --hold-outside` (11 questions). Otherwise its questions leak through Chavruta, whose Hebrew
  explanation of a Gemara line is nearly a question on it. Berakhot is a benchmark book (`build_eval_set.py`) besides.
  - The links' pairs don't read `held_out()` (`build_train_pairs.py` leaves out only the benchmark's books, and `tune`
    reuses the first `--links` of `train`'s file): 2% of them touch these tractates, so every model since the first
    teacher has met some of their lines. Fine for comparing models trained on the same links, not as a locked test.
- **Measuring it: `eval-crfh/`.** Its `*_line` numbers the physical lines of the pinned `.txt` (Sefaria's export), not
  ours: `map_lines.py` finds each quote in our tractate (its daf, then its words as a run, niqqud and tags off: all 844
  found; `lines.tsv`, ids only). `spec.py` writes pool searches whose candidates are the whole tractate (6,974 lines),
  merged into `pool.json` (`--merge`); `measure.py` gives the correct line's place, the near miss before it, and a
  paired McNemar test between models. Dicta's base model already has 365 of 422 in the first ten and 247 first, so
  the first place is the measure (`eval-crfh/results.md`). Over all books, only with graded judging
  (`eval-real/pool.py`, `eval-v1/score_graded.py`). Its near misses are unverified and some answer the question
  (`moedkatan-047`: "האם אבל נוהג ברגל" → "כל שהוא משום אבל — רגל מפסיקו").
- **No license file, and none asked for** (the user's decision, 2026-11-04): its questions stay out of efrul/Pardes.
  Fetch them for every measurement at the two commits `eval-crfh/README.md` pins (`git fetch origin <sha>` works), not
  the branches, so that every run is measured on the same questions; commit only their ids, our line ids and our
  results (as `eval-real` keeps the books' text out).

## Klei Kodesh's data (KitveiHakodesh)

[KleiKodesh/KleiKodeshProject](https://github.com/KleiKodesh/KleiKodeshProject) (AGPL v3) reads the same `seforim.db`.
Its owners allowed Pardes to use its material (2026-11-03); credit it as "Based on Klei Kodesh —
https://kleikodesh.github.io/" and in `TRAINING_PLAN.md`'s licence table. Their Aramaic-Hebrew dictionary is Torat
Emet's: by what Klei Kodesh's developer wrote (2026-11-04), Klei Kodesh received Torat Emet's permission long ago, a
permission given to Klei Kodesh and not said to extend to others, and the user decided to train on it (credit both,
`NOTICE.md`). Anything else they took from elsewhere keeps its source's licence, so check each in their
`sources.json`. `TRAINING_PLAN.md` › שלב 2 has the classification.
- **Reading it:** the GitHub API answers 403 for it here; `git clone --depth 1 --filter=blob:none --sparse` works. The two
  DBs are `KitveiHakodesh/CSharpBackend/KitveiHakodesh.Core/Dictionary/Dictionary.db` (built by the public
  `KleiKodesh/KitveiHakodeshDictionary`: TSVs in `source/`, `sources.json`) and
  `KitveiHakodesh/CSharpBackend/FtsLib-Csharp/SearchExpansion/expansion-routed.db` (built by the private
  `KleiKodesh/SearchExpansion`; its `meta` and `build_meta` tables list sources and licences). No `sqlite3` CLI in the
  container: use Python's `sqlite3` in read-only mode (`file:...?mode=ro`). Read them as data; run none of their scripts.
- **For training:** `build_dictionary_pairs.py --dictionary <a KitveiHakodeshDictionary clone>` (23,293 pairs on
  2026-11-03): their headword index (`source/seforim_headword.tsv`: source, their book id, line index in the book,
  headword; titles in `seforim_headword_book.tsv`; Aruch 8,611, Mikropedia Talmudit 3,907, Metzudat Tzion 20,541, Malbim
  Biur HaMilot 13,549, Machberet Menachem 1,607), their own work over our library's text, and the Aramaic-Hebrew
  dictionary (source_kind 1, 7,605 senses), Torat Emet's `FinalDictionary.txt` (our check, and Klei Kodesh's developer
  on 2026-11-04; their `sources.json` calls it an early Peshita list, yet it shares only 497 headwords and no sense with
  source_kind 11): by the user's decision (2026-11-04) its 13,507 `DICT-ARAMAIC` pairs go into run 3 (`--aramaic 0`
  leaves them out: 9,786 pairs instead of 23,293). A word is swapped for its meaning inside a library line, which stays
  the positive. Every model trained on these pairs carries the credit in `NOTICE.md`.
- **Not for training:** Otzaria's lists (9, 11, and the 1,050 `otzaria-aramaic` bridge rows of
  `expansion-routed.db`) are forbidden. `expansion-routed.db` is word level (folds, inflections, synonyms): query
  expansion, the regular search's session; BHSA in it is CC BY-NC.

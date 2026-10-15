# Offline search on Android: desktop Lucene on API 33

## Contents
- Decision (don't reopen it)
- Index files
- Why stock Lucene fails on Android 13
- The patched jar: `androidApp/lucene-android`
- App wiring
- R8
- Testing search
- Troubleshooting

## Decision (don't reopen it)

These points are settled; don't re-research them:
- **Offline only.** The user requires search to work fully offline; server-side search was rejected.
- **Same engine as desktop.** Android runs the desktop engine (`SeforimLibrary/search`, Apache Lucene) on the **same prebuilt index** desktop downloads. The version must match the index writer: release `20261012193827` was written by **Lucene 10.5.2** (its `Lucene90DocValuesFormat` version 2 makes 10.4.0 throw `IndexFormatTooNewException`), while this fork's SeforimLibrary still declares 10.4.0. Use the version upstream desktop ships; a newer 10.x reads older 10.x indexes, never the other way round. Indexing on the phone is ruled out: it would take hours, and results would differ from desktop.
- **The FTS4 index is gone.** An earlier on-device SQLite FTS4 index (`data/SearchIndex.kt`) was a stopgap, and the Lucene port replaced it.
- **Patching works.** The research was done in session 1 with TraceReferences against the real API-33 platform jar: patching lucene-core is enough, and analysis-common is not needed at runtime. Build on that result.

## Index files

These sit next to `seforim.db` in the library folder, in the same layout desktop uses:

| File | Size (full library) | Used for |
|---|---|---|
| `seforim.db.lucene/` | ~4.95 GB | full-text search (lines), snippets |
| `seforim.db.lookup.lucene/` | small | book/TOC title and acronym lookup (home search suggestions) |
| `lexical.db` | ~80 MB | "magic dictionary" query expansion (SQLite) |
| `catalog.pb` | <1 MB | catalog tree (protobuf) |

Open the indexes with **`NIOFSDirectory`**. `FSDirectory.open` picks MMapDirectory on 64-bit, and Lucene 10's MMapDirectory needs `java.lang.foreign`, which Android doesn't have.

## Why stock Lucene fails on Android 13

These gaps were found with R8 TraceReferences against the API-33 `android.jar`.

**Broken at every API level:**
- `HotspotVMOptions.<clinit>` calls `Class.getModule` outside its try. The failure cascades through `Constants` and `RamUsageEstimator`.
- `VectorizationProvider` uses Module, StackWalker and `Stream.ofNullable`. It must always return the default scalar provider.
- `IndexWriter.setDiagnostics` calls `Runtime.version` (write path only).
- `MMapDirectory`.

**Missing until API 34:**
- `java.lang.ClassValue`. It is subclassed by `AttributeSource$2` and `AttributeFactory$DefaultAttributeFactory$1`, so the first TokenStream throws `NoClassDefFoundError: java.lang.ClassValue`.
- `Stream.toList` (ExactPhraseMatcher, SloppyPhraseMatcher, SegmentDocValues, IndexWriter).
- `Checksum.update(ByteBuffer)`.

**Missing until API 35:**
- `HashMap.newHashMap` (FieldInfos, Lucene90CompoundReader, StandardDirectoryReader, SegmentInfos, PerFieldPostingsFormat).
- `LongBuffer.put(int,long[],int,int)` in BufferedChecksum, reached when `.liv` deletion files exist.
- `ByteBuffer.get(int,byte[],int,int)`.

## The patched jar: `androidApp/lucene-android`

This is a Gradle `java-library` subproject of the Android build (JDK 21+ to build: it compiles against lucene-core's Java 21 class files). `rewriteLucene` (ASM, `rewriter/LuceneAndroidRewriter.java`) copies the original `lucene-core-10.5.2.jar` (`luceneVersion` in its `build.gradle.kts`) into `build/libs/lucene-android-10.5.2-android.jar` with three kinds of change:
1. **Replaced classes**, compiled with `--release 11` from minimally edited 10.5.2 sources in `src/main/java`. Every change is marked `// Android:`.
   - `VectorizationProvider`: always the default scalar provider, no caller check.
   - `HotspotVMOptions`: no `Class.getModule`, only the generic warning.
   - `Version`: no named-module manifest fallback.
   - `TestSecrets`: setters (run from IndexWriter/SegmentReader static init, also on the read path) keep only the set-once check; getters always refuse.
   - `AnalysisSPILoader`: reads `META-INF/services` itself (`ServiceLoader.stream()` is API 35+).
   - `FSDirectory`: `open()` always returns `NIOFSDirectory`.
   - New helpers: `AndroidCompat` (static stand-ins), `CompatClassValue` (ConcurrentHashMap), `CompatHexFormat`, `CompatRuntimeVersion`.
2. **ASM rewrites** of the copied classes:
   - type swaps: `ClassValue` -> `CompatClassValue` (AttributeSource, AttributeFactory, VirtualMethod), `HexFormat` -> `CompatHexFormat` (StringHelper), `Runtime.Version`/`Runtime.version()` -> `CompatRuntimeVersion` (IndexWriter diagnostics);
   - calls redirected to `AndroidCompat`: `HashMap.newHashMap`, `LongBuffer.put(int,long[],int,int)`, `ByteBuffer.get(int,byte[],int,int)`, `Stream.toList`, `Checksum.update(ByteBuffer)`, `StringBuilder.isEmpty`, `List.addFirst`, `List.getFirst`.
3. **Stripped** (69 classes): Panama/MemorySegment/NativeAccess/RefCountedSharedArena, `MMapDirectory`, `ModuleResourceLoader`. Code that names `MMapDirectory` no longer compiles.

Gate the jar with `androidApp/lucene-android/check-api33.sh [--baseline] [jar]`. It dexes the jar like AGP for minSdk 33, runs R8 `TraceReferences --check` with the API-33 platform jar as `--lib`, and must report **0 missing** (stock 10.5.2: 217). Run it after any Lucene version bump or patch change. Upgrading: copy the six edited files again from the new sources jar, re-apply the `// Android:` edits, rebuild, gate, smoke test.

JVM smoke test (read path with Term/Phrase/Prefix queries and an index sort, plus a small write/delete/merge): `java -cp build/libs/lucene-android-10.5.2-android.jar smoke/LuceneSmoke.java <copy of seforim.db.lookup.lucene>`. It proves the patches did not break Lucene, not that ART runs it.

## App wiring

- **Dependency.** `app/build.gradle.kts` declares `implementation(project(":lucene-android"))`. There must be no transitive original `lucene-core`.
- **Search code.** Desktop's 0.6.1 `search` module is compiled from the `androidApp/upstream/SeforimLibrary` submodule. `search/` holds the Android variants, each rebased on its 0.6.1 file with a provenance header. A JVM harness gives byte-identical output to desktop 0.6.1 on the subset library. The variants are:
  - `LuceneSearchEngine`
  - `LuceneLookupSearchService`
  - `MagicDictionaryIndex`, which reads `lexical.db` through Android SQLite instead of JDBC
  - `AcronymFrequencyCache`
  
  `SearchService` builds them lazily off the main thread from `LibraryStore` paths. Unchanged desktop files are compiled in through `srcDir` where possible.
- **UI.** `ui/search/SearchViewModel.kt` mirrors the desktop `SearchResultViewModel`: paging, a category/book tree with counts, and the base-books-only option. Home suggestions come from the lookup index.
- **Missing index.** If the index folders are missing, the search screen explains in Hebrew how to get them: reinstall the library, or run the transfer script. It does not rebuild anything on the device.

## Delta updates

The incremental updater (`delta/`) writes new lines into the full-text index exactly as the generator does. That needs three classes from Lucene's analysis-common module, which the lucene-android jar now also contains. `check-api33.sh` still reports 0 missing. Segments over 512 MB are never rewritten on the phone.

## R8

Lucene loads codecs and formats through `ServiceLoader` (the `META-INF/services` entries), creates attribute implementations by name, and stores some class names in the index. `app/proguard-rules.pro` keeps (each group verified by shrinking the jar and running a search; no blanket `-keep org.apache.lucene.**`):
- the service types' names and their providers' names and public no-arg constructors (Codec, PostingsFormat, DocValuesFormat, KnnVectorsFormat, SortFieldProvider, TokenizerFactory);
- analysis factories' `NAME` field and `(Map)` constructor;
- names of `Attribute` interfaces and of `AttributeImpl` classes with their public no-arg constructor ("Cannot lookup accessible no-arg constructor for PackedTokenAttributeImpl" without it);
- names of `Lucene90StoredFieldsFormat` and the three `PerField*Format` classes, which form keys stored in the index ("missing value for <obfuscated>.mode" without it).

When search works in debug but not in release, suspect these rules first; an SPI crash usually names a codec, for example "An SPI class of type Codec with name 'Lucene104' does not exist".

## Testing search

- **Small index.** A subset library with a matching subset Lucene index (built with the desktop generator's indexing code against `make_subset_db.sh` output) gives fast emulator tests. Push it with the transfer script:
  ```bash
  androidApp/tools/push-desktop-library.sh --source <subset-library-dir>
  ```
- **A test library with chosen books and authors** (built 2026-10-14 to check the 0.6.2 search fixes, 553 MB):
  1. `fetch-desktop-library.sh --no-fulltext --dest <dir>` (8 GB; it resumes dropped transfers).
  2. `CONN_TYPES=1,3 MAX_LINES=5000 FULL_LINES=1500 bash androidApp/tools/make_subset_db.sh <dir>/seforim.db subset.db 1 103 40 122 123 124 208 209 39 132 128 268` (Bereshit, Berakhot, Mishnah Berakhot, Bava Kamma/Metzia/Batra, Tosefta BK/BM, Divrei HaYamim B, Chullin, Avodah Zarah, Midrash Tanchuma; 342 books, 13 s). Run it with bash, not sh.
  3. Delete the full `seforim.db` at once (disk allowance).
  4. Full-text index for the subset: the generator main class with `-DinMemoryDb=false` on a throwaway copy, as in the subset-index tool's `build-subset-library.sh` step 2 (30 s).
  5. Use the **shipped** `seforim.db.lookup.lucene` from the same release, not the generated one: the shipped index has the ~1,080 author documents (author suggestions) and every book, while this fork's generator writes neither. Book suggestions are built from the index, not the DB, so books missing from the subset only fail when opened.
  6. Copy `lexical.db` and `release_info.txt` from the release; push with `push-desktop-library.sh --source <folder>`.
- **Queries.** Search through the deep link (Hebrew can't be typed with adb):
  ```bash
  q=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "בראשית ברא")
  adb shell am start -a android.intent.action.VIEW -p io.github.efrul.pardes -d "zayit://search?q=$q"
  ```
- **Compare with desktop.** Desktop on the same index must give the same hits and counts.
- **JVM smoke test.** For a quick check of the patched jar without a device, open the index with `NIOFSDirectory` and run a TermQuery and a PhraseQuery with plain `java -cp`. This proves the patches didn't break Lucene. It doesn't prove Android compatibility; only the TraceReferences gate and the emulator do.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `NoClassDefFoundError: java.lang.ClassValue` | the unpatched lucene-core got onto the classpath (check `./gradlew :app:dependencies`) |
| `NoSuchMethodError: HashMap.newHashMap` / `Stream.toList` | a call site the rewriter missed: rerun `check-api33.sh` |
| `UnsupportedOperationException` / `java.lang.foreign` | something used `FSDirectory.open` or `MMapDirectory`: use `NIOFSDirectory` |
| SPI "does not exist" only in release | R8 stripped a codec: keep rules |
| No results but no crash | index folder not found next to the DB (check `LibraryStore` paths, permissions after `adb push`: directories need `chmod 777`/files `666` when pushed as shell) |
| `IndexFormatTooNewException` ("needs to be between 0 and 0") | the jar is older than the Lucene that wrote the index: bump `luceneVersion` in `lucene-android/build.gradle.kts` to the release's writer version (`segments_N` records it) and re-run the gate |

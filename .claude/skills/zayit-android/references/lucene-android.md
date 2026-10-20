# Offline search on Android: desktop Lucene on API 26+

## Contents
- Decision (don't reopen it)
- Index files
- Why stock Lucene fails on Android
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
- **Patching works.** The research was done in session 1 with TraceReferences against the real API-33 platform jar: patching lucene-core is enough, and analysis-common is not needed at runtime. Build on that result. The same approach took it down to API 30 (Android 11), then API 26 (Android 8.0), on 2026-10-18. API 26 is the floor: Lucene needs `java.nio.file`, `java.time` and MethodHandle's `invoke-polymorphic`.
- **Not NJA, for now.** The Zayit author's embedded JVM (NucleusFramework/NucleusJvmAccess, 2026-10-17) was weighed on 2026-10-18 and set aside: experimental (0.4.0, plugin unpublished, no real-device runs), 64-bit only while Pardes ships 32-bit ABIs, about +40-80 MB of APK and ~50 MB extracted, a read-only façade (the delta updater writes the index), Java 25 artifacts, and HotSpot (GPLv2 without the Classpath Exception) linked into the app. Look again when upstream merges SeforimLibrary PR #134 and Pardes moves its pin past it, or when the desktop index is written by Lucene 11.

## Index files

These sit next to `seforim.db` in the library folder, in the same layout desktop uses:

| File | Size (full library) | Used for |
|---|---|---|
| `seforim.db.lucene/` | ~4.95 GB | full-text search (lines), snippets |
| `seforim.db.lookup.lucene/` | small | book/TOC title and acronym lookup (home search suggestions) |
| `lexical.db` | ~80 MB | "magic dictionary" query expansion (SQLite) |
| `catalog.pb` | <1 MB | catalog tree (protobuf) |

Open the indexes with **`NIOFSDirectory`**. `FSDirectory.open` picks MMapDirectory on 64-bit, and Lucene 10's MMapDirectory needs `java.lang.foreign`, which Android doesn't have.

## Why stock Lucene fails on Android

These gaps were found with R8 TraceReferences against the API-33 `android.jar` (stock 10.5.2: 217 missing on API 33, 240 on API 30).

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

**Missing until API 33 (Android 8.0 to 12), what Android 13 brought of Java 9-11:**
- the `Arrays` range overloads (`mismatch`, `compare`, `compareUnsigned`, `equals` on byte/char/int/long ranges): BytesRef, StringHelper, the blocktree term seek (SegmentTermsEnum), FST outputs, LZ4, BKD;
- `VarHandle` and `MethodHandles.byteArrayViewVarHandle`: BitUtil's `VH_*` byte views, behind every little-endian read and write (96 call sites);
- `Collection.toArray(IntFunction)` (75 sites), `AtomicInteger.getOpaque` (BaseCompositeReader), `Math.fma`, `ByteArrayOutputStream.toString(Charset)` and `new PrintStream(OutputStream, boolean, Charset)` (CheckIndex, blocktree Stats).
- Java 9-11 methods that AGP's D8 backports at minSdk 26 (`List/Set/Map.copyOf`, `String.isBlank`, `Math.multiplyExact(long,int)`, `Predicate.not`, and at 26 also `Objects.check*` and `List.of`, API 30) need nothing, nor the covariant `ByteBuffer` overloads (ART has them from API 29), which it rewrites; the gate proves it with that D8. The backport has no SDK_INT check: D8 rewrites those call sites for every level, so Android 11 to 13 run D8's code too (for `copyOf`, an unmodifiable copy, not the platform's immutable collection). The results are the same for what Lucene passes. Only reflection could reach the platform method where the platform has it, on hot paths (every `IOContext`), so this one deviation from "11+ and 13+ as before" is accepted.

## The patched jar: `androidApp/lucene-android`

This is a Gradle `java-library` subproject of the Android build (JDK 21+ to build: it compiles against lucene-core's Java 21 class files). `rewriteLucene` (ASM, `rewriter/LuceneAndroidRewriter.java`) copies the original `lucene-core-10.5.2.jar` (`luceneVersion` in its `build.gradle.kts`) into `build/libs/lucene-android-10.5.2-android.jar` with three kinds of change:
1. **Replaced classes**, compiled with `--release 11` from minimally edited 10.5.2 sources in `src/main/java`. Every change is marked `// Android:`.
   - `VectorizationProvider`: always the default scalar provider, no caller check.
   - `HotspotVMOptions`: no `Class.getModule`, only the generic warning.
   - `Version`: no named-module manifest fallback.
   - `TestSecrets`: setters (run from IndexWriter/SegmentReader static init, also on the read path) keep only the set-once check; getters always refuse.
   - `AnalysisSPILoader`: reads `META-INF/services` itself (`ServiceLoader.stream()` is API 35+).
   - `FSDirectory`: `open()` always returns `NIOFSDirectory`.
   - `AttributeFactory`: below API 30 (`AndroidCompat.API_30`) the attributes come from the reflected no-arg constructor. The ART of Android 8 and 9 builds a constructor MethodHandle's instance from the type `asType` gave it, so `findConstructor(...).asType(AttributeImpl)` tried the abstract `AttributeImpl` and every search failed (`UndeclaredThrowableException` / `InstantiationException: Can't instantiate abstract class`, on the API 26 and 28 emulators; 10 not checked, so it takes the same path). No gate sees it: only a search on the device. It also uses `CompatClassValue` itself (the rewriter does not touch replaced classes).
   - New helpers: `AndroidCompat` (static stand-ins), `CompatClassValue` (ConcurrentHashMap), `CompatHexFormat`, `CompatRuntimeVersion`, `CompatVarHandle` (byte-array views), `AndroidCompatApi33` (the API 33 JDK calls, one per method).
   - For Pardes for Android 4.4 (`:legacy`) only, inert in the main app: `NIOFSDirectory` reads the system property `lucene.android.niofsBufferSize` (bytes, raises the 1 KB read buffer); `Lucene99HnswVectorsFormat.fieldsReader` returns a reader of no vectors under `lucene.android.skipVectors`, so the vector files (`*.vec`, `.vex`, `.vem`, `.vemf`) are not read and may be missing (without the property such an index fails with a CorruptIndexException naming the file); `AndroidHeapFilesDirectory`, a FilterDirectory that reads chosen files whole into the heap (the 4.4 edition: `.tip`, `.kdi`, `.fdx` and the small metadata files). `smoke/ReadPath44.java ... legacy` runs as the 4.4 edition (heap files, vectors skipped, one slice).
2. **ASM rewrites** of the copied classes:
   - type swaps: `ClassValue` -> `CompatClassValue` (AttributeSource, AttributeFactory, VirtualMethod), `HexFormat` -> `CompatHexFormat` (StringHelper), `Runtime.Version`/`Runtime.version()` -> `CompatRuntimeVersion` (IndexWriter diagnostics);
   - calls redirected to `AndroidCompat`: `HashMap.newHashMap`, `LongBuffer.put(int,long[],int,int)`, `ByteBuffer.get(int,byte[],int,int)`, `Stream.toList`, `Checksum.update(ByteBuffer)`, `StringBuilder.isEmpty`, `List.addFirst`, `List.getFirst` (always the compat code);
   - the API 33 calls above, redirected to `AndroidCompat` / `CompatVarHandle` (the `VarHandle` type is remapped to it), which dispatch on `AndroidCompat.API_33` (`Build.VERSION.SDK_INT >= 33`, read reflectively once): Android 13+ calls the JDK method through `AndroidCompatApi33`, as before; Android 8.0 to 12 run equivalent Java code. The `PrintStream` constructor becomes its `String` charset-name twin on every level.
3. **Stripped** (69 classes): Panama/MemorySegment/NativeAccess/RefCountedSharedArena, `MMapDirectory`, `ModuleResourceLoader`. Code that names `MMapDirectory` no longer compiles.

Gate the jar with `androidApp/lucene-android/check-min-api.sh [--baseline] [jar]` (CI runs it before every release). It dexes the jar with **AGP's own D8** (the builder jar of the AGP in `gradle/libs.versions.toml`, from the Gradle cache: a newer build-tools D8 backports more, e.g. the `Arrays.equals` ranges, and would pass call sites the APK still has) for the app's minSdk (26, read from `app/build.gradle.kts`; `--min-api N` for another), then requires: 0 references above API 26 by the compile SDK's `api-versions.xml` (`gate/DexApiLevels.java`), 0 missing in `TraceReferences --check` against the API 33 and API 26 `android.jar` (install `platforms;android-26`; the API 33 jar is downloaded once when not installed), and the same for `AndroidCompatApi33` against API 33 alone. Expected: `lucene-android-10.5.2-android: 0 references above API 26, 0 missing on API 33, 0 missing on API 26`, and the same with `--min-api 28` (stock at API 30: 47, 217 and 240). Run it after any Lucene version bump, patch change or AGP upgrade. After an AGP upgrade also check that `AndroidCompatApi33` still calls the platform methods and not D8 backports: `dexdump -d build/check-min-api/*/dex-api33/classes.dex | grep 'Ljava/util/Arrays;'`. Upgrading Lucene: copy the edited files again from the new sources jar, re-apply the `// Android:` edits, rebuild, gate, then the JVM tests.

JVM tests (single-file programs, run from `lucene-android/`), each once as is and once with `-Dlucene.android.forceFallbacks=true`, which runs the Android 8.0-12 code on the JVM:
- `java -XX:-OmitStackTraceInFastThrow [-Dlucene.android.forceFallbacks=true] -cp build/libs/lucene-android-10.5.2-android.jar smoke/CompatFallbacks.java [rounds] [seed]` compares every API 33 stand-in with the JDK method on random and edge-case input, results and exceptions, then runs CheckIndex. The flag is required: without it HotSpot's compiled code throws message-less implicit exceptions and the comparison fails spuriously.
- `java [-Dlucene.android.forceFallbacks=true] -cp build/libs/lucene-android-10.5.2-android.jar smoke/LuceneSmoke.java <copy of seforim.db.lookup.lucene>`: the read path with Term/Phrase/Prefix queries and an index sort, plus a small write/delete/merge. Both runs must print the same apart from the `jar:` line (on a full-text index the queries match too; its last step, the sort, is written for the lookup index's doc values and stops there in both runs).

They prove the patches did not break Lucene and that the fallbacks equal the JDK, not that ART runs them: the gate and the emulators (API 26, 28, 30 and 33) do that.

**R8 and the API 33 class.** The release build is R8-optimized, which the jar gate does not see. R8 once moved AndroidCompatApi33's VarHandle methods (signature-polymorphic `invoke-polymorphic` calls) into GroupVIntUtil, which every search loads; Android 11's verifier rejected that whole class (`VerifyError ... register v1 has type Precise Reference`, then `NoClassDefFoundError`), and the search screen showed "החיפוש נכשל" with an obfuscated class name. `app/proguard-rules.pro` keeps AndroidCompatApi33 (`-keep,allowobfuscation ... { *; }`), and `lucene-android/check-release-dex.sh <apk> <mapping.txt>` (CI runs it after the build) fails unless every VarHandle call of the APK is in that class. Run it after any R8 rule or AGP change.

## App wiring

- **Dependency.** `app/build.gradle.kts` declares `implementation(project(":lucene-android"))`. There must be no transitive original `lucene-core`.
- **Search code.** Desktop's 0.6.1 `search` module is compiled from the `androidApp/upstream/SeforimLibrary` submodule. `search/` holds the Android variants, each rebased on its 0.6.1 file with a provenance header. A JVM harness gives byte-identical output to desktop 0.6.1 on the subset library. The variants are:
  - `LuceneSearchEngine`
  - `LuceneLookupSearchService`
  - `MagicDictionaryIndex`, which reads `lexical.db` through Android SQLite instead of JDBC
  - `AcronymFrequencyCache`
  
  `SearchService` builds them lazily off the main thread from `LibraryStore` paths. Unchanged desktop files are compiled in through `srcDir` where possible.
- **Tuning.** `search/LuceneTuning.kt`: slices, forms per word, facets beside or after the first page, query cache, directory opener. Its defaults are the main app's values (never pass one there). Pardes for Android 4.4 compiles the whole search stack (`legacy/build.gradle.kts`, sharedAppSources) and runs it through `legacy/.../data/LegacySearch.kt`: 1 slice, 8 forms per word (fewer hits: תפילין in the dev subset's base books 152 → 58), facets after page 1, a 2-4 MB query cache, `AndroidHeapFilesDirectory`, vectors skipped, and three levels (full index, lookup index only, the C scan) with failures recorded in its prefs. HeapBench (JVM, dev subset index, one slice): runs at -Xmx12m; a three-word search over all books makes 12,729 reads instead of 25,177. It leaves the read buffers at 1 KB: with 8 KB ones (3,843 reads on the JVM) Dalvik ran a full GC for nearly every new buffer ("Grow heap (frag case) ... for 8204-byte allocation") and the same search took 76 s instead of 46 s on the 4.4 emulator.
- **UI.** `ui/search/SearchViewModel.kt` mirrors the desktop `SearchResultViewModel`: paging, a category/book tree with counts, and the base-books-only option. Home suggestions come from the lookup index.
- **Missing index.** If the index folders are missing, the search screen explains in Hebrew how to get them: reinstall the library, or run the transfer script. It does not rebuild anything on the device.

## Delta updates

The incremental updater (`delta/`) writes new lines into the full-text index exactly as the generator does. That needs three classes from Lucene's analysis-common module, which the lucene-android jar now also contains, under the same rewrites and gate. Segments over 512 MB are never rewritten on the phone.

## R8

Lucene loads codecs and formats through `ServiceLoader` (the `META-INF/services` entries), creates attribute implementations by name, and stores some class names in the index. `androidApp/lucene-android/proguard-lucene.pro`, which both release builds list (`app/build.gradle.kts` and `legacy/build.gradle.kts`, Pardes for Android 4.4), keeps (each group verified by shrinking the jar and running a search; no blanket `-keep org.apache.lucene.**`):
- the service types' names and their providers' names and public no-arg constructors (Codec, PostingsFormat, DocValuesFormat, KnnVectorsFormat, SortFieldProvider, TokenizerFactory);
- analysis factories' `NAME` field and `(Map)` constructor;
- names of `Attribute` interfaces and of `AttributeImpl` classes with their public no-arg constructor ("Cannot lookup accessible no-arg constructor for PackedTokenAttributeImpl" without it);
- names of `Lucene90StoredFieldsFormat` and the three `PerField*Format` classes, which form keys stored in the index ("missing value for <obfuscated>.mode" without it).

Add a new Lucene rule there, not to `app/proguard-rules.pro`, which keeps only the main jar's AndroidCompatApi33 (above) and which the Android 4.4 build does not read. When search works in debug but not in release, suspect these rules first; an SPI crash usually names a codec, for example "An SPI class of type Codec with name 'Lucene104' does not exist".

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
| `NoSuchMethodError: HashMap.newHashMap` / `Stream.toList`, or `Arrays.mismatch` / `VarHandle` on Android 8-12 only | a call site the rewriter missed: rerun `check-min-api.sh` |
| "החיפוש נכשל" with `UndeclaredThrowableException` / `InstantiationException: Can't instantiate abstract class` on Android 8-10 | `AttributeFactory`'s constructor handles: the replaced class must be in the jar (`unzip -l` it) and `AndroidCompat.API_30` false there |
| `UnsupportedOperationException` / `java.lang.foreign` | something used `FSDirectory.open` or `MMapDirectory`: use `NIOFSDirectory` |
| SPI "does not exist" only in release | R8 stripped a codec: keep rules |
| No results but no crash | index folder not found next to the DB (check `LibraryStore` paths, permissions after `adb push`: directories need `chmod 777`/files `666` when pushed as shell) |
| `IndexFormatTooNewException` ("needs to be between 0 and 0") | the jar is older than the Lucene that wrote the index: bump `luceneVersion` in `lucene-android/build.gradle.kts` to the release's writer version (`segments_N` records it) and re-run the gate |

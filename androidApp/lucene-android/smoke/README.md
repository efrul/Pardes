# JVM tests of lucene-android

Run from `androidApp/lucene-android` after `../gradlew :lucene-android:jar :lucene-android:jarApi19`. The main
app's jar is `build/libs/lucene-android-10.5.2-android.jar`, that of Pardes for Android 4.4 (`:legacy`)
`build/libs/lucene-android-10.5.2-android-api19.jar`. Each run below must end without an exception.

## Both jars, plain JVM

- `java -XX:-OmitStackTraceInFastThrow [-Dlucene.android.forceFallbacks=true] -cp <jar> smoke/CompatFallbacks.java [rounds] [seed]`
  compares every API 33 stand-in with the JDK method, then runs CheckIndex; it prints `OK, <n> comparisons`. The
  Android 4.4 jar always runs its fallbacks (`API 26-32 fallbacks` without the property).
- `java [-Dlucene.android.forceFallbacks=true] -cp <jar> smoke/LuceneSmoke.java <copy of seforim.db.lookup.lucene>`
  reads the lookup index and writes a small one. On the release's lookup index: `TermQuery תוספתא: 94`,
  `PhraseQuery q:"תוספתא כלים": 3`, `PrefixQuery תו*: 2741`, then `OK`. The output is the same for every jar and
  mode apart from the `jar:` line.

## The Android 4.4 jar, desugared as on Android 4.4

`smoke/run-desugared.sh <Program.java> [args...]` builds the Android 4.4 jar, desugars it and the program for
minSdk 19 with `desugar_jdk_libs_nio` (AGP's D8, to class files) and runs them on L8's class files of that library,
with `SDK_INT` 19 and the library's own emulated file system, as on Android 4.4 to 7.1 (see the script).
`--jar <jar>` runs another jar instead.

- `smoke/run-desugared.sh smoke/ReadPath44.java <seforim.db.lucene> <seforim.db.lookup.lucene> [single]`: the Lucene
  calls of the main app's search, partitioned or single-slice (`single`). On the development subset's index
  (169,941 documents, built by Lucene 10.5.2 with the release's fields and codecs):
  ```
  docs 169941, slices 4          (slices 1 with single)
  suggestions 5 hits
  results 640 hits 25
  after 25
  facet collectors 4             (1 with single)
  count 640 / 169941
  lookup 21 hits, authors 1080+ hits
  ```
  The lookup line depends on the lookup index: the subset's gives 21 hits, the release's 40. Stock lucene-core on
  a plain JVM (`java -cp lucene-core-10.5.2.jar smoke/ReadPath44.java ...`) prints the same.
- `smoke/run-desugared.sh smoke/LuceneSmoke.java <copy of seforim.db.lookup.lucene>`: the figures above, then `OK`
  after writing through NIOFSDirectory (an fsync of the directory the desugared file system cannot make,
  FSDirectory.syncMetaData) and ByteBuffersDirectory (ConcurrentHashMap.keySet() of the desugared library).

CompatFallbacks does not run desugared: its JDK side calls VarHandle, which D8 cannot keep below API 26.

## The Android 4.4 jar on Android 4.4 itself

ReadPath44 also runs on the Dalvik of the Android 4.4 emulator, outside the app, with `dalvikvm`. After
`./check-min-api.sh --legacy`, `build/check-min-api/` holds the jar dexed for minSdk 19
(`lucene-android-*-api19/dex/classes.dex`) and the desugared library dexed by L8 (`l8-nio-19/dex/classes.dex`). Dex
the program against the jar with D8 (`--min-api 19 --classpath <jar> --desugared-lib l8-nio-19/desugar.json`), put
each `classes.dex` in a zip of its own (the jar's with its `META-INF/services`), push them and the index to
`/data/local/tmp`, and run them with `ANDROID_DATA` set to a folder there that holds an empty `dalvik-cache/`, where
Dalvik writes their optimized dex (the framework's is pre-optimized):
```
ANDROID_DATA=/data/local/tmp/l44 dalvikvm -Xmx48m -cp program.zip:lucene.zip:l8.zip ReadPath44 <index> <lookup> single
```
On 2026-10-20 (the 4.4 AVD, 48 MB heap) ReadPath44 printed the figures above, single and partitioned, with no
verifier message for org.apache.lucene in logcat.

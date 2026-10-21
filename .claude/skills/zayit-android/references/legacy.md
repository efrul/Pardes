# Pardes for Android 4.4 (`:legacy`)

`androidApp/legacy` is Pardes for Android 4.4 to 7.1 (API 19 to 25), for phones the main app (minSdk 26) cannot reach, keypad phones included. [androidApp/legacy/README.md](../../../../androidApp/legacy/README.md) is the full description: why it exists, what it shares, the library and its index on a phone or card, the search levels, the copy script and the install from files, the gates and the harness, and the emulator. This page holds what a session needs to work on it without repeating solved problems.

## Contents
- Decisions (don't reopen them)
- Working on it
- The pardes19 AVD
- Pitfalls already solved

## Decisions (don't reopen them)

- **Same app, older phones.** Same applicationId (`io.github.efrul.pardes`) and library folder as the main app. `versionCode` stays 1, so that the main app installs over it on Android 8.0 and later. A recorded search failure names the build by version name and install time instead. Same name and authorship rules (SKILL.md › Authorship and name).
- **Views, not Compose.** Compose, lifecycle, activity, core, SQLDelight's android-driver and androidx.sqlite declare minSdk 23, and requery and kosherkotlin 21. Every screen is a View-based activity copying the main app's look, texts and behaviour. When unsure, read the main app's code; copy its Hebrew texts word for word.
- **Its own SQLite.** `libpardes_sqlite.so` is SQLite 3.49.2 built by NDK r25 for API 19, with the main app's multiplex VFS. Android 4.4's SQLite 3.7.11 cannot read the schema, and requery starts at API 21.
- **The main app's search, on the release's prebuilt index.** `LegacySearch` runs `SearchService` on `:lucene-android-api19`, a second jar from lucene-android's sources (`jarApi19`), with `desugar_jdk_libs_nio`. It has three levels, Full, Lookup and Scan, and its own tuning (README › Search). It keeps 32 forms per word, as the main app: with 8 it finds fewer lines.
- **Product decisions:**
  - a stack of screens, not tabs;
  - a book opens at the last place read;
  - settings, history and favourites stay in the `pardes_legacy` preferences;
  - no network at all (no INTERNET permission), so no update check and no download;
  - behaviour-preserving `:app` changes land on the same branch as the legacy work.
- **Keypad phones are first-class.** Every new control must be reachable with D-pad, OK, digits, Menu and Back, with a visible focus highlight. Nothing heavy may run on the main thread, and nothing may allocate per frame.

## Working on it

- **Sharing code.** `sharedAppSources` in `legacy/build.gradle.kts` copies 38 of the main app's files in at build time, each marked "Also compiled into the Android 4.4 edition".
  - Code both apps need goes into such a file (move it out of a Compose file first: no Compose, no lifecycle, nothing above API 19 unguarded).
  - Legacy's stand-ins under the main app's names (`androidx/lifecycle/ViewModel.kt`, Compose's `@Immutable`/`@Stable`, `LibraryStore`, `SplitDatabase`, `LexicalDb`, `PreviewText`, `HebrewTextUtils`) let them compile.
  - A change to a shared file must leave the main app exactly as it was: same texts, results and threading, still 7 Kotlin warnings.
- **Checks before a commit:**
  - `:legacy:lintDebug` ("No issues found"; NewApi/InlinedApi, the shared copies included), `:legacy:assembleDebug`, `:legacy:assembleRelease`;
  - with `:app` changed, `:app:compileDebugKotlinAndroid` (7 warnings) and `:app:assembleRelease`;
  - with lucene-android changed, `lucene-android/check-min-api.sh` and `--legacy`, and `check-release-dex.sh` and `--legacy`;
  - then the release APK on the AVD, because R8 can strip what reflection or ServiceLoader needs.
- **Commands:** run Gradle as `./gradlew --console=plain --offline <tasks>`, with timeouts of up to 10 minutes. Only one Gradle build runs at a time in `androidApp/`.
- **A platform call above API 19** needs an `SDK_INT` check, lint clean, and its exact reference in `LEGACY_ALLOWED` of `lucene-android/check-release-dex.sh`. Lint misses calls in libraries and some Kotlin; the release gate sees the dex.
- **Licences.** Every bundled library, font, icon or data file is credited in `ui/TextActivity.kt` (Settings › רישיונות) with its text in assets. `LegacyAssets` copies the texts from `app/src/androidMain/assets/licenses/` (its `licences` list); only legacy's own extras live in `legacy/src/androidMain/assets/licenses/`.
- **Logs:** `adb logcat -s Pardes` prints the search level and how long it took to open (`search: Full, opened in 2729 ms (heap 256 MB)`) and every index failure with its verdict.

## The pardes19 AVD

`system-images;android-19;google_apis;x86`, Nexus S profile (480x800, 240 dpi), Dalvik, 1 GB, 2 cores, hardware keyboard, a 2 GB card image as the phone's storage. README › The Android 4.4 emulator has its creation commands. adb is `/opt/android-sdk/platform-tools/adb`.

- **Start** in the background with a 2-hour timeout:
  ```bash
  /opt/android-sdk/emulator/emulator -avd pardes19 -no-window -no-audio -no-boot-anim -accel off \
    -gpu swiftshader_indirect -no-snapshot-save -memory 1024 -cores 2
  ```
  Not `emulator-start.sh`, which asks for 3 GB and more cores. Wait with `scripts/emu-wait.sh boot` (about a minute). Don't use `stable`, which waits on commands Android 4.4 lacks.
- **Library:** check `adb shell ls -l /storage/sdcard/Android/data/io.github.efrul.pardes/files/library` after every start. The card is often empty after the emulator was killed, or holds empty files written in its last minutes. Copy it back with `androidApp/tools/push-desktop-library.sh --source <library>`: the dev subset with its indexes, 485 MB, takes about 40 s. Run `adb shell sync` after writing to the card.
- **The card is the phone's storage:** a vfat volume (`/mnt/media_rw/sdcard`) behind FUSE `/storage/sdcard`. Settings › ספרייה reports FAT32 there, and the copy script chunks a database over 4 GiB.
- **Heap:** `dalvik.vm.heapsize` is 256m, so small heaps are not simulated. Test them under `dalvikvm -Xmx48m` (`lucene-android/smoke/README.md`) or on the JVM.
- **Speed:** CheckJNI is on for every app there. Measure release builds only: the debug build is several times slower.
- **Screens and input.** `scripts/emu-shot.sh`, `emu-wait.sh settle` and `emu-tap.py` fall back to `adb shell screencap` and `adb pull` there, because `adb exec-out` fails on 4.4 ("error: closed"). `emu-tap.py --list` finds the views' texts and content descriptions. Keypad: `adb shell input keyevent DPAD_UP|DPAD_DOWN|DPAD_LEFT|DPAD_RIGHT|DPAD_CENTER|MENU|BACK|<digit>`. Wait until two screenshots a few seconds apart are equal before judging a screen.
- **Start and links:** `am start` has no `-p`:
  ```bash
  adb shell am start -n io.github.efrul.pardes/io.github.kdroidfilter.seforimapp.legacy.ui.MainActivity \
    [-a android.intent.action.VIEW -d "zayit://search/<url-encoded>" | -d "zayit://book/<bookId>/line/<lineId>"]
  ```
  MainActivity is `singleTask`, so this clears the screens above it.
- **Every adb shell command prints a linker warning first** ("WARNING: linker: libdvm.so has text relocations"). Scripts must look for their answer on a line of its own (the copy scripts find `package:` that way).
- **More recipes** in emulator-testing.md › Putting a library on the emulator:
  - typing Hebrew (ADBKeyBoard with the hardware keyboard off, and the quoting of two words);
  - the licence step of a fresh install;
  - process death (`am kill` from the background, then Recents);
  - a 240x320 keypad phone (`wm size 240x320; wm density 120`; `dumpsys activity top` instead of an empty `uiautomator dump`);
  - library states (too old, unreadable);
  - the install from files.

## Pitfalls already solved

Each is fixed in the code; the line says where, so that it is not broken again.

**Dex, desugaring and Lucene on Dalvik** (gates: `check-min-api.sh --legacy`, `check-release-dex.sh --legacy`; see lucene-android.md)
- AGP stops a minSdk 19 build on any `MethodHandle.invoke`/`invokeExact` or VarHandle call ("Increase the minSdkVersion to 26 or above"), although standalone D8 only warns. `jarApi19` swaps `AndroidCompatApi33` and `AndroidCompatMethodHandles` for stubs (`src/api19/java`), and `AndroidCompatFlags` turns the API 30/33 paths off. `gate/PolyScan.java` must find 0.
- **KitKat's verifier rejects a whole class** in two cases, and both become a VerifyError at the first use:
  - it throws a value whose type does not resolve (CompatVarHandle's `WrongMethodTypeException` now comes from `AndroidCompatMethodHandles`);
  - every catch type of a handler fails to resolve (`java.nio.file` exceptions in SegmentInfos and SegmentCoreReaders).

  `gate/DalvikVerify44.java` and `gate/dexcatch.py` find these.
- **The default `desugar_jdk_libs` has no `java.nio.file`.** Lucene's directories need it, and `StringHelper`'s static initialiser dies on `Paths.get`, which breaks every Term. Hence `desugar_jdk_libs_nio`; it also covers jsoup's API 24 references. Logcat's "VFY: unable to resolve" notes about `j$` classes are harmless: Dalvik replaces those calls, which run on later Android versions only.
- **API 24 types that desugaring lacks** (LongAdder, LongAccumulator, SplittableRandom, CompletableFuture, CompletionException) are redirected to look-alikes by the rewriter's `--api19` mode, which also links `ConcurrentHashMap.keySet()` to the desugared library.
- Other Lucene edits:
  - `FSDirectory.syncMetaData` tolerates a directory the emulated file system cannot fsync;
  - `SameThreadExecutorService` has its own `close()`;
  - each change is marked `// Android:`.
- **R8:** legacy's release lists `lucene-android/proguard-lucene.pro`; a Lucene rule added only to `app/proguard-rules.pro` breaks 4.4 release search. The release gate checks every `META-INF/services` provider is in the dex (11).
- **Startup:** `PardesApp.attachBaseContext` only runs `MultiDex.install`; the search opens lazily on a background thread (about 1,265 classes). Kermit and HebrewNumerals declare a higher minSdk for code that runs on 19: `tools:overrideLibrary` in the manifest, with the release gate as the proof.

**Native SQLite**
- Bionic has `mmap64` only from API 21, and `_FILE_OFFSET_BITS=64` (needed for the 8 GB database) turns `mmap` into it: `android_compat.h` maps it through `mmap2`. NDK r26 dropped API 19, so the NDK is pinned to r25.
- One connection in exclusive locking mode keeps a WAL database's index in memory (`NativeSqliteDriver`), so it needs no mapped `-shm` file on 4.4's FUSE and FAT storage. `SQLITE_THREADSAFE=2`: never share the connection between threads at once. The scan's interrupt cuts a book's query only while it holds the connection (`TextSearch.run`'s `inQuery`).
- The dictionary reads `lexical.db` by column name: `NativeSqlite.columnName` (without it every lookup threw and the word forms were silently lost).

**Search on Android 4.4** (details in lucene-android.md › Tuning and › Speed on Android 4.4)
- **Heap.** Run one slice, with the facets after the first page; a 48 MB heap cannot hold the main app's 4 slices with facets beside them. Below 40 MB the full index stays closed unless the user asks.
- **Read buffers stay at 1 KB.** With 8 KB buffers, Dalvik ran a full GC for nearly every new buffer, and a search took 76 s instead of 46 s. The terms index and the small files go into the heap instead (`AndroidHeapFilesDirectory`).
- **`normalizeHebrew`** compiled three ICU regular expressions per call: 29% of a search's processor time. Legacy's own `HebrewTextUtils` gives the same strings in one pass. Dalvik's `\s` is ICU's White_Space (26 chars).
- **Failures are judged one by one** (`LegacySearch.verdict`): a pasted passage (TooManyClauses) or one OutOfMemoryError must not close the index for good.
- **Order of a first page:** Lucene's hash seed changes per process, so the order (not the totals) can differ between launches. Fix `tests.seed` to compare two builds.
- **A view model's init race.** A `SearchViewModel` made with the query in its destination searched from its init's `Main.immediate` coroutine before its constructor had set its fields: a NullPointerException in the 4.4 release build. Make it with an empty query and search once its index check is done (`SearchActivity`).
- **The catalog row.** Building desktop's patterns for every (book, label) pair of the release's catalog took 85 s. Now there is one alternation per label, and titles that don't start with the label are skipped: 12 s. The Home draws first and adds the row later. `CatalogAccess.load` caches per open library, because a library without `catalog.pb` always had the same "missing file" stamp.

**Views on Android 4.4** (each detailed in pitfalls.md › RTL and bidi)
- **Bidi:**
  - a leading dot is drawn on the Hebrew side of a Latin token: name file types without the dot (`Bidi.marks`);
  - a Latin-led line is laid out left to right: set `TEXT_DIRECTION_RTL`;
  - a line meant to read right to left must start with Hebrew;
  - there are no isolates: `Bidi.compat` turns them into embeddings.
- **StaticLayout throws on a middle ellipsis** with maxLines 1: use `setSingleLine` with an end ellipsis.
- **Justified text** below API 26 (`ui/Justify.kt`): spans on the view's own layout, the text watcher removed, a fling's lines justified when it stops. The fast scroller's thumb is told from a finger by the scroll state it reports in its press.
- **Footnote markers** are hidden as the main app hides them (`LineText.hideFootnoteMarkers`). Otherwise the find bar lights what it never counted.
- **Lists:**
  - `ListView` draws its selector under rows that have a fill: `setDrawSelectorOnTop(true)` with a see-through selector;
  - `setSelectionFromTop` right after `setPadding` measures from the old padding (`BookActivity.place`).
- **LinkMovementMethod in a ScrollView** jumps to links out of sight, and leans on `onPreDraw`: `MarkdownText.KeypadLinks` steps the links itself.
- **Fonts:** bold of the bundled Noto Serif Hebrew is synthesized, as in Compose. The AVD has no colour emoji font.
- **Window flags:** set `FLAG_KEEP_SCREEN_ON` once per run, not at every progress update; each set laid the window out again.

**Screens, storage and scripts**
- **A failed or cancelled install leaves the library closed** under the screens it started over. Each screen that holds the library overrides `BaseActivity.heldLibrary` and is rebuilt on resume (`onLibraryReopened`). See architecture.md › Install from files.
- **The licence step:** the Home is `singleTask` and finishes once it has started the step. The step comes back for a launcher tap or a link, and the Home starts on a task of its own, so Back from the Home leaves the app (`LicenceActivity`).
- **The panes:** a page that cannot be read says "לא ניתן לקרוא את הטקסט" (`ReaderLinks.linksPageOrNull`). An empty page would read as "no texts".
- **The copy scripts below Android 7.0:**
  - there is no `adb shell -T`, and a plain `adb shell` garbles binary through its terminal: each file goes with `adb push`;
  - there are no `stat`, `find` or `awk`: sizes come from toolbox `ls -l`, and free space from `df` as it prints it (1.8G);
  - cards come from `/proc/mounts` by name; `/storage/sdcard0` is a link to the phone's storage, not a card;
  - the shell has no `printf`, and `echo` adds a newline.
- **Android 4.4's system picker** lists storage only after its "Display advanced devices" setting. That is one more reason for the keypad file browser (`InstallActivity`).
- **The dev subset is sparse** for some books (only lines linked to its base books). Test the reader on books it keeps whole, such as רא"ה על ברכות (1510) or בית יעקב על התורה (414).

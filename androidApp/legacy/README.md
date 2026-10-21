# Pardes for Android 4.4 to 7.1 (`:legacy`)

This module builds Pardes (פרדס) for phones that the main app cannot run on: Android 4.4 to 7.1 (API 19 to 25), including keypad phones without a touch screen. Like the main app (`androidApp/app`, Android 8.0 and later), it is based on [Zayit](https://github.com/kdroidFilter/Zayit), the open-source (AGPL-3.0) desktop app by Elie (Elyahou) Gambache. He did not make Pardes, is not responsible for it and does not support it, so please don't report its problems to him or to the Zayit project.

It reads the same library as desktop Zayit and the main app: the same database, the same prebuilt search index and the same search engine, so a search finds what it finds there. Its screens are drawn with Android's own views and copy the main app's look, texts and behaviour screen by screen. It has the main app's application id, `io.github.efrul.pardes`, and the same library folder, so a phone holds one edition or the other. On Android 8.0 and later the main app installs over this edition (its `versionCode` is higher), never the other way round.

No release workflow builds it yet (`.github/workflows/android-release.yml` builds the main app only): build it as below.

## Why a separate edition

Lowering the main app's minSdk to 19 is not possible, for three reasons:

- **The UI libraries.** Jetpack Compose 1.10, AndroidX lifecycle 2.10, activity 1.13, core 1.18, SQLDelight's android-driver 2.3 and androidx.sqlite 2.6 all declare minSdk 23. The main app's bundled SQLite (requery 3.49) and kosherkotlin declare 21.
- **Lucene.** The main app's Lucene jar needs `java.nio.file`, `java.time` and signature-polymorphic MethodHandle calls, which arrived in Android 8.0. At minSdk 19, AGP refuses that jar outright: "Increase the minSdkVersion to 26 or above".
- **SQLite.** Android 4.4 ships SQLite 3.7.11, which cannot read the library's schema (an index on an expression needs 3.9), and requery's build starts at API 21 and calls `mmap64`, which Android 4.4's libc lacks.

So this edition keeps the main app's logic and replaces what cannot run below Android 8.0:

| Part | Main app | This edition |
|---|---|---|
| Screens | Jetpack Compose | Android views (`ui/`), one activity per screen |
| Navigation | Tabs and workspaces | A stack of screens: Back returns to the screen before |
| Library database | Android's SQLite, or requery's on a FAT32 card and below Android 9 | `libpardes_sqlite.so`: SQLite 3.49.2 built for API 19, with the main app's multiplex VFS for a FAT32 card (`src/androidMain/cpp`) |
| Search engine | `:lucene-android` | `:lucene-android-api19`: the same patched lucene-core 10.5.2, built for minSdk 19, with `desugar_jdk_libs_nio` |
| Search tuning | `LuceneTuning`'s defaults | One slice, facets after the first page, a small query cache, the terms index in the heap (below) |
| Settings, history, favourites | `zayit_*` preferences and `user_settings.db` | One preferences file of its own, `pardes_legacy` |

## What it shares with the main app

- **SeforimLibrary.** `core`, `dao` and desktop's `search` sources are compiled unchanged from `../upstream/SeforimLibrary`, as the main app compiles them, with the same SQLDelight database (`SeforimDb`). The two search files that use JDBC or `MMapDirectory` are left out, and their Android variants come from the main app (below). Desktop's `HebrewTextUtils.kt` is also left out: this edition compiles its own, which gives the same strings in one pass (see Search, below).
- **38 of the main app's files**, copied in at build time by the `sharedAppSources` task in `build.gradle.kts`. They are listed there with the reason for each: the search engine and its view models, the install from files, the reader's find, the commentator grouping and panes' reads, the catalog row, the references, the deep links, and the texts of the terms, About, the licences and the shell. Each of them starts with an "Also compiled into the Android 4.4 edition" header: no Compose, no AndroidX lifecycle, and nothing above API 19 that is not behind an `SDK_INT` check. `:legacy:lintDebug` checks NewApi and InlinedApi on these copies too.
- **Stand-ins under the main app's names**, so that the shared files compile here unchanged:
  - `src/androidMain/kotlin/androidx/lifecycle/ViewModel.kt` (a scope, `onCleared`, `viewModelScope`, `Factory`);
  - `src/commonMain/kotlin/androidx/compose/runtime/Immutable.kt` and `Stable.kt`;
  - `src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/`: `LibraryStore`, `SplitDatabase` with `NativeCursor`, `LexicalDb` and `PreviewText` on this edition's SQLite and `Library`, and `HebrewTextUtils`.
- **Assets.** The `LegacyAssets` task copies the main app's fonts (Noto Serif Hebrew, Frank Ruhl Libre and Taamey Frank), the Home's wallpapers, the licence texts, the repository `LICENSE` and Lucene's `LICENSE.txt` and `NOTICE.txt`, word for word.

Because of this, a change to one of the 38 files changes both apps. It must keep the main app behaving exactly as before, and it must stay within API 19 here. Before committing, run `:legacy:lintDebug` ("No issues found"), `:legacy:assembleDebug` and `:legacy:assembleRelease`; when `:app` changed, also `:app:compileDebugKotlinAndroid` (the same 7 Kotlin warnings) and `:app:assembleRelease`, plus the lucene-android gates when Lucene changed (below).

## What it has of its own

Paths below are under `src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/legacy/`.

| Path | What |
|---|---|
| `PardesApp.kt` | The singletons: `library`, `prefs`, `search` (opened lazily, off the main thread) and `installer`. `attachBaseContext` only installs MultiDex. |
| `data/Library.kt` | Where the library is (the phone, each memory card, internal storage), its filesystem from `/proc/mounts`, and the open database with the desktop repository on it |
| `data/LegacySearch.kt` | The main app's `SearchService` with this edition's tuning, its three levels and how failures close an index |
| `data/ScanSearch.kt`, `TextSearch.kt` | The text search without an index: the books read through by `pardes_match()` in C, as a `SearchUiState` for the search screen |
| `data/LegacyInstaller.kt` | Where an install from files puts the library; the install itself is the main app's `LibraryInstallerBase` |
| `data/Prefs.kt` | Settings, history and favourites (`pardes_legacy`) |
| `db/NativeSqlite.kt`, `NativeSqliteDriver.kt` | The JNI calls of `libpardes_sqlite.so`, and SQLDelight's driver over them: one connection in exclusive locking mode, so a WAL database needs no mapped `-shm` file on Android 4.4's FUSE and FAT storage |
| `ui/MainActivity.kt` | The Home: the catalog dropdowns, the logo, the search field, recent books, favourites and the library's sections; without a library, the install from files and where to copy one |
| `ui/LicenceActivity.kt` | The licence, accepted once before the first Home |
| `ui/LibraryActivity.kt`, `TocActivity.kt` | The book list (רשימת הספרים, with "חיפוש ספר") and a book's table of contents |
| `ui/BookActivity.kt` | The reader: the text, its toolbar, the commentaries, targum and sources pane, the find bar (`FindBar.kt`, `ReaderFind.kt`), justified text (`Justify.kt`) |
| `ui/SearchActivity.kt`, `SearchViews.kt` | The search screen on the main app's `SearchViewModel` and `SearchBarViewModel` |
| `ui/SettingsActivity.kt`, `TextActivity.kt` | Settings (תצוגה, ספרייה, אודות, תנאים, רישיונות), the terms and the licences page |
| `ui/InstallActivity.kt`, `InstallService.kt` | The install from files: a file browser made for the keypad, and the foreground service that runs the install |

The native library is built from `src/androidMain/cpp`:
- **The amalgamation:** SQLite 3.49.2, which Gradle downloads from sqlite.org once (the `sqliteAmalgamation` task) and checks against its sha256.
- **The main app's `multiplex.c`**, compiled in.
- **`pardes_sqlite.c`:** the JNI calls and the scan's `pardes_match()`.
- **`android_compat.h`:** gives SQLite an `mmap64` through the `mmap2` system call on 32-bit Android 4.4.

The build uses NDK r25 (`25.2.9519653`, the last that builds for API 19), CMake 3.22.1, and four ABIs: armeabi-v7a, arm64-v8a, x86 and x86_64.

## How it differs from the main app

The screens follow the main app's: the Islands palette with the gold accent, Noto Serif Hebrew for every text, the same Hebrew texts, the ⋮ menu, the three themes, the licence step, About, the licences, the history and favourites, the book list, the reader with its toolbar, pane, find bar and עוד menu, and the search screen with its bar, tabs, grouping, knowledge panel and states. What differs, and why:

- **A stack of screens instead of tabs.** Every live tab would cost memory that a phone with a 48 MB heap does not have.
- **A book opens at the place it was last read.**
- **"System" theme means light** below Android 10, which has no dark mode of its own.
- **No network at all.** The app does not hold the INTERNET permission. The library comes from a computer or from files on the phone (below), so there is no download, no library update, and no app update check (Android 4.4 would also need TLS 1.2 turned on by hand).
- **Left out:** the tools (zmanim, calendar and the rest), notes and highlights, the launcher widgets, workspaces, text selection by word, pinch zoom, שנים מקרא, the profile settings, and most fonts (three book fonts are bundled). Settings › אודות says so in Hebrew ("הגרסה הזו").
- **Keypad first.** Every control is reachable with ▲▼◀▶, OK, Menu and Back, with a visible focus highlight. In the reader the digits are shortcuts, listed under עוד › מקשי קיצור:

  | Key | Action |
  |---|---|
  | 1 | Table of contents |
  | 2 / 8 | Scroll up or down (the pane when it is open) |
  | 3 | Commentaries |
  | 4 / 6 | Next or previous commentator |
  | 5 | Niqqud |
  | 7 / 9 | Smaller or larger text |
  | 0 | Home |
  | * | Find in the page |
  | # | Add or remove a favourite |
  | Menu | עוד |

  On the other screens with a ⋮ menu, the Menu key opens it.

## Build

The requirements are the main app's (JDK 21, Android SDK 36; see [../README.md](../README.md)), plus NDK `25.2.9519653` and CMake 3.22.1, which Gradle installs once the SDK licences are accepted. The first build needs the network once, for the SQLite amalgamation; it is kept in `build/sqlite/`.

```bash
cd androidApp
./gradlew :legacy:assembleRelease   # legacy/build/outputs/apk/release/legacy-release.apk (R8, about 5.4 MB)
./gradlew :legacy:assembleDebug     # legacy/build/outputs/apk/debug/legacy-debug.apk (about 14 MB)
./gradlew :legacy:lintDebug         # API level checks on this module and the shared copies: "No issues found"
```

- **Release is the build to test.** On the 4.4 emulator, the debug build searched several times slower (82-89 s against 10-12 s for one search). The release build is shrunk by R8 with `proguard-rules.pro` and Lucene's keep rules, `../lucene-android/proguard-lucene.pro`, which the main app lists too. Lucene finds its codecs by name, so a missing keep rule shows up in release builds only.
- **Signing.** Both build types are signed with the project's debug key (`../debug.keystore`), as the main app's. With `PARDES_KEYSTORE`, `PARDES_KEYSTORE_PASSWORD`, `PARDES_KEY_ALIAS` and `PARDES_KEY_PASSWORD` set, the release build is signed with that key instead.
- **Versions.** `versionCode` stays 1 in every build, below the main app's, so that the main app always installs over this edition. Only `versionName` changes.
- **Installing on a phone.** `adb install -r legacy/build/outputs/apk/release/legacy-release.apk`, or copy the APK to the phone and open it (Android 4.4: Settings › Security › Unknown sources).

## The library on a phone or a memory card

The library is desktop Zayit's databases folder:

| File | Needed for | Size in the full release |
|---|---|---|
| `seforim.db` | Everything (the app looks for this file) | 8.02 GB |
| `release_info.txt` | The version check: the release must be at least `20261011170830`, the main app's `MINIMUM_REQUIRED_VERSION` | 14 B |
| `seforim.db.lucene/` | Searching the text in the index | 4.95 GB, of which 1.68 GB are vector files that this edition never reads and that may be left out |
| `seforim.db.lookup.lucene/` | Book and author suggestions | 1.4 MB |
| `lexical.db` | The forms of each word the search looks for | 81 MB |
| `catalog.pb` | The Home's catalog dropdowns (else they are read from the database) | under 1 MB |

`data/Library.kt` looks for it in this order:
1. The folder it opened last.
2. The app's folder on the phone's shared storage, `/sdcard/Android/data/io.github.efrul.pardes/files/library/`.
3. The same folder on each memory card (`/storage/<card>/Android/data/io.github.efrul.pardes/files/library/`).
4. The app's internal storage.

A `seforim.db` dropped into `files/` beside the `library/` folder is moved into it. No storage permission is needed for these folders, and Android deletes them when the app is uninstalled.

**FAT32.** Memory cards on Android 4.4 to 7.1 are nearly always FAT32 (stock Android 4.4 mounts no other filesystem), and some phones keep their own shared storage on a FAT32 partition too (the emulator below does). FAT32 takes no file of 4 GB or more, so there `seforim.db` is kept in SQLite multiplex chunks: `seforim.db` (2 GiB less 64 KiB), then `seforim.db001`, `seforim.db002`… The app's own SQLite reads them as one database. Lucene's files fit as they are; the largest, the text index's `.doc`, is 1.42 GiB.

Android 4.4 has no API for a volume's filesystem, so `Library` reads `/proc/mounts` and finds the `/mnt/media_rw/<name>` mount behind the FUSE `/storage/<name>` path. Settings › ספרייה shows what it found: where the library is, its filesystem, the database's size and chunks, the release, the search level and the SQLite version. It also has "חפש את הספרייה מחדש" (look for the library again), "התקנה מקבצים" and "נקה היסטוריה".

There are three ways to put a library on the phone.

### The copy script, from a computer with desktop Zayit

`../tools/push-desktop-library.sh` (Linux and macOS) and `push-desktop-library.ps1` (Windows, run by `push-desktop-library.cmd`) copy the installed desktop library over adb and make it readable for the app, with no root. [../README.md](../README.md#getting-a-library-onto-the-device) describes them. Below Android 7.0 they work differently, because Android 4.4 to 6.0 lack what the Android 7+ path uses:

- **Copying:** each file goes with `adb push` straight into the app's folder. `adb shell -T` does not exist there, and a plain `adb shell` has a terminal that garbles a binary stream.
- **Sizes and free space:** read from toolbox `ls -l` and `df` as Android 4.4 prints them. Android 4.4 has no `stat`, `find` or `awk`.
- **Memory cards:** listed from `/proc/mounts` (before 6.0 there is no `sm`), by name rather than by UUID (`sdcard1`, `extSdCard`). `--sdcard` / `-SdCard` accepts this edition (`versionCode` 1).
- **FAT32:** `seforim.db` goes in chunks when the target is a FAT32 card, or the phone's own storage is FAT32.
- **One library:** after the copy, a library on the other storage is removed, because this edition opens the folder it opened last. Its old `.zayit-old-*` copies are not deleted by this edition, so the script names them for you to delete.

```bash
androidApp/tools/push-desktop-library.sh                 # the desktop library, to the phone's storage
androidApp/tools/push-desktop-library.sh --sdcard        # to the memory card
androidApp/tools/push-desktop-library.sh --source DIR    # another library folder (a Zayit Subset library, the dev subset)
```

The `.ps1` has the same changes but was only checked by reading: there is no PowerShell in the development container.

### Install from files, on the phone

"התקנה מקבצים" is offered by the Home without a library and by Settings › ספרייה. It installs the same files as the main app's offline install, with the main app's code (`LibraryInstallerBase`, `DatabaseChunks`):

- the `.partNN` pieces of a bundle with their manifest (a SeforimLibrary release's, or one that Zayit Export made);
- a single `.tar.zst`;
- a `seforim.db.zst` or a `seforim.db` with its `release_info.txt`;
- a library folder.

A library folder on the same volume as the library is moved in by renames and needs no room. Otherwise it is copied, with the free space and FAT32's limit checked first.

Its first page has the system picker where the phone has one (Android 4.4's lists the storage only after its "Display advanced devices" setting), what it found in Pardes's folders and in Download ("נמצאו במכשיר"), and the places to browse with the keypad: Pardes's folders, Download, the phone's storage, each card. Picking a `.part01` takes the bundle's other parts and its manifest from the same folder.

The install runs in a foreground service with a notification and ביטול (which asks first). It checks the parts against the manifest, `library_size.txt` against the free space, and `release_info.txt` against the minimum version before it deletes anything. On a FAT32 card it writes `seforim.db` in chunks. Storage access is asked for from Android 6.0; 4.4 and 5.x grant it at install.

### By hand, over USB

With no library, the Home lists the steps:
1. Find desktop Zayit's `databases` folder; on Windows it is `%APPDATA%\io.github.kdroidfilter.seforimapp\databases`.
2. Connect the phone in file-transfer (MTP) mode.
3. Copy the files above into `Android/data/io.github.efrul.pardes/files/library/`, on the phone or on the card.
4. Choose "חפש שוב".

The app creates that folder and a `README.txt` in it, and has the media scanner list the note. Android 4.4 shows a computer only the files the media scanner knows, so without the note the folder would not show.

## Search

`PardesApp.search` (`data/LegacySearch.kt`) runs the main app's `SearchService` and engine on the library's prebuilt indexes. It opens them on a background thread once the Home has the library, never in `attachBaseContext`: the first open loads about 1,265 Lucene classes and takes about 3 s on the 4.4 emulator. It then runs a small search on each index, so that a class this Android lacks, or a missing or corrupt file, shows up at once rather than in the user's first search. Logcat says what it opened:

```bash
adb logcat -s Pardes      # "search: Full, opened in 2729 ms (heap 256 MB)"
```

### Levels

The level comes from what the library folder holds, less what the phone cannot run:

| Level | When | What it searches |
|---|---|---|
| Full | `seforim.db.lucene/` is there, it opened, and the heap is at least 40 MB (or the user chose to open it anyway) | The main app's search: the same results and totals |
| Lookup | Only `seforim.db.lookup.lucene/` (1.4 MB) is usable | Book and author suggestions, the knowledge panel and "חיפוש ספר" from the lookup index; the text by the scan |
| Scan | Neither index is usable | The text by the scan; books by their titles in the database |

The scan reads the books themselves through `pardes_match()` in C. It finds a phrase, or all of its words, ignoring niqqud, cantillation, maqaf and punctuation. It is quick in a book or a category, and takes minutes over the whole library.

Below Full, the search screen shows the scan's results under the main app's text that says why: "אינדקס החיפוש של הספרייה חסר", "החיפוש נכשל" with "נסה שוב", or a heap too small, with a button to open the index anyway (`Prefs.searchIndexOnSmallHeap`). The level is checked again at each search, so an index copied in or removed counts at once.

### Tuning

This edition runs Lucene for a heap of about 48 MB, one or two cores and a FUSE memory card (`LegacySearch.tuning`). The main app passes no tuning and keeps `LuceneTuning`'s defaults.

- **Slices:** one search slice; the main app uses up to 4.
- **Forms per word:** 32, as the main app. With 8, תפילין over the dev subset's books found 1,266 lines instead of 4,817.
- **Facets:** the category counts run after the first page, not beside it.
- **Query cache:** 2 MB, or 4 MB with a heap of 96 MB or more.
- **Heap files:** `AndroidHeapFilesDirectory` keeps the terms index (`.tip`) and the small metadata files (`.kdi`, `.fdx`, `.tmd`, `.psm`, `.nvm`, `.dvm`, `.kdm`, `.fdm`, up to 2 MB each) in the heap. On the JVM, that removed 8,342 of 25,177 reads from a three-word search.
- **Vectors:** `lucene.android.skipVectors` leaves the vector files unopened, so a library may be copied without them.
- **Read buffers:** they stay at Lucene's 1 KB. With 8 KB buffers, Dalvik ran a full garbage collection for nearly every new buffer, and the same search took 76 s instead of 46 s.
- **`android:largeHeap` is on.**

On the JVM, a three-word search with this tuning runs in a 16 MB heap.

### Failures

A failure closes an index for as long as its cause lasts (`LegacySearch.verdict`):

| Failure | What happens |
|---|---|
| A class this Android cannot load, an index of another Lucene version, or anything else thrown while the index opens | Closed, and recorded in `search_failure` with the library's release and this build (version name and install time). It is not opened again at every start until the library or the app changes, or "נסה שוב" asks again. |
| A failed read (a corrupt, missing or truncated file, a card removed), or the heap running out at the open or in a third search | Closed for this session |
| Anything else a search throws (a pasted passage too long for Lucene's clause limit, the heap running out once) | That search fails, and the index stays open |

A lookup index that fails is closed alone, and the full text stays searched without it.

### Speed

Measure release builds only. On the 4.4 emulator (release build, software emulation), the first page of אמר רבי יוחנן takes about 9.5 s in the base books and 10-12 s over all books. The first pages and facets of the four test searches, base books and all books, take about 50 s in all.

Lucene's hash seed changes from one process to the next, so the order of a first page (never its totals) can differ between two launches, as it does in the main app.

## Checks: lint, gates and the JVM harness

| Check | What it guards | Expected |
|---|---|---|
| `./gradlew :legacy:lintDebug` | NewApi and InlinedApi in this module and the 38 shared copies (`lint.xml` leaves out the upstream SeforimLibrary sources, which hold calls this edition never makes) | No issues found |
| `lucene-android/check-min-api.sh --legacy` | The Android 4.4 Lucene jar, dexed by AGP's D8 for minSdk 19 with `desugar_jdk_libs_nio`, then traced against API 19 and 33 and checked for signature-polymorphic calls and what Dalvik's verifier rejects | see below |
| `lucene-android/check-release-dex.sh --legacy legacy/build/outputs/apk/release/legacy-release.apk` | The release APK after R8: dex version 035, nothing above API 19 outside the allowed guarded calls (`LEGACY_ALLOWED` in the script), and every Lucene codec named in `META-INF/services` kept | see below |

Expected output of the two gates (October 2026):

```
lucene-android-10.5.2-android-api19: 0 references above API 19, 0 missing on API 33, 0 missing on API 19
  below API 26: 0 signature-polymorphic calls or method handle constants; Dalvik (API 19): 0 hard verifier failures, 0 methods not analysed, 0 rejected catch handlers

1 app dex, 1 desugared library dex: 0 references above API 19, 18 allowed (guarded, linted)
Lucene's META-INF/services: 11 providers
OK
```

A new platform call above API 19 needs three things:
- an `SDK_INT` check;
- a line in `LEGACY_ALLOWED` in `check-release-dex.sh`;
- `:legacy:lintDebug` still clean.

Lint does not see the libraries' bytecode, and its Kotlin frontend has missed calls before, which is why the release gate exists.

**The JVM harness** ([../lucene-android/smoke/README.md](../lucene-android/smoke/README.md)) runs the Android 4.4 jar as on Android 4.4: desugared by AGP's D8 for minSdk 19, on L8's class files of `desugar_jdk_libs_nio`, with `SDK_INT` 19 and the desugared library's own file system. On the dev subset's index:

```bash
cd androidApp/lucene-android
smoke/run-desugared.sh smoke/ReadPath44.java <library>/seforim.db.lucene <library>/seforim.db.lookup.lucene legacy
# mode single ..., heap files, buffer default, vectors skipped
# docs 169941, slices 1 / results 640 hits 25 / after 25 / count 640 / 169941 / lookup 21 hits, authors 1080+ hits
```

The same README says how to run ReadPath44 under `dalvikvm` on the emulator, with an explicit `-Xmx48m`. That is the way to test a small heap, since the emulator below gives apps 256 MB.

**Test totals.** On the development subset with its indexes, the Full level finds what the main app finds (base books / all books):

| Query | Base books | All books |
|---|---|---|
| תפילין | 152 | 4,817 |
| אמר רבי יוחנן | 378 | 1,958 |
| ברוך ה׳ לעולם | 73 | 638 |
| "קריאת שמע" | 76 | 807 |

## The Android 4.4 emulator

Development uses an Android 4.4.2 AVD named `pardes19`: `system-images;android-19;google_apis;x86`, the Nexus S profile (480x800 at 240 dpi), Dalvik, 1 GB of RAM, 2 cores and a hardware keyboard. It was made this way, with the SDK's `cmdline-tools/latest/bin/sdkmanager` and `avdmanager` and `emulator/mksdcard` and `emulator/emulator`:

```bash
sdkmanager "system-images;android-19;google_apis;x86" "platforms;android-19"
echo no | avdmanager create avd -n pardes19 -k "system-images;android-19;google_apis;x86" -d "Nexus S"
# in ~/.android/avd/pardes19.avd/config.ini: hw.ramSize=1024, hw.keyboard=yes, disk.dataPartition.size=4G,
# and disk.dataPartition.path=<the AVD's folder>/userdata-qemu.img (the data then survives a restart)
mksdcard -l PARDES 2G ~/.android/avd/pardes19.avd/sdcard.img      # and sdcard.size=2G in config.ini
emulator -avd pardes19 -no-window -no-audio -no-boot-anim -accel off -gpu swiftshader_indirect \
  -no-snapshot-save -memory 1024 -cores 2
```

`platforms;android-19` is also the API 19 `android.jar` that `check-min-api.sh --legacy` traces against.

Without KVM it runs under software emulation:
- it boots in about a minute;
- `adb install -r` of the release APK takes about 10 s;
- a cold start of the app to its Home takes about 7 s;
- opening the search index takes about 3 s.

Its quirks:

- **The card is the phone's storage.** `/storage/sdcard` is a FUSE view of the 2 GB card image, a vfat volume at `/mnt/media_rw/sdcard`. The library folder is `/storage/sdcard/Android/data/io.github.efrul.pardes/files/library/`, Settings › ספרייה says FAT32, and the copy script puts a database over 4 GiB there in chunks.
- **The card often comes back empty.** When the emulator is killed (background jobs in the cloud container are stopped after 2 hours), the card frequently comes back without the library at the next start, or with the files written in the last minutes empty. After each start, check with `adb shell ls -l /storage/sdcard/Android/data/io.github.efrul.pardes/files/library` and copy the library again with the copy script. After writing to the card, run `adb shell sync`.
- **The heap is not a small phone's.** `dalvik.vm.heapsize` is 256m, so the app gets 256 MB, and the small-heap path (under 40 MB) and out-of-memory failures are not seen there. Test them on the JVM or under `dalvikvm -Xmx48m` (above).
- **CheckJNI is on for every app** (`ro.kernel.android.checkjni=1`), the release build included, so every JNI call pays for it there and not on a phone.
- **adb on 4.4:**
  - `adb exec-out` fails ("error: closed"): take screenshots with `adb shell screencap -p /sdcard/s.png` and `adb pull /sdcard/s.png`.
  - `am start` has no `-p`: start the activity itself with `adb shell am start -n io.github.efrul.pardes/io.github.kdroidfilter.seforimapp.legacy.ui.MainActivity`, and add `-a android.intent.action.VIEW -d "zayit://search/<url-encoded query>"` or `-d "zayit://book/<bookId>/line/<lineId>"` for a link.
  - `MainActivity` is `singleTask`: starting it clears the screens above it.
  - The main app's launcher name, `io.github.efrul.pardes/io.github.kdroidfilter.seforimapp.android.MainActivity`, is an alias of it, for the scripts.
- **Keypad input:** `adb shell input keyevent DPAD_UP|DPAD_DOWN|DPAD_LEFT|DPAD_RIGHT|DPAD_CENTER|MENU|BACK|<digit>`. Wait for the screen to settle before judging it: two screenshots a few seconds apart must be equal.
- **Typing Hebrew** needs the ADBKeyBoard IME with the hardware keyboard switched off in the IME picker.
- **A fresh install opens on the licence step:** DPAD_DOWN to the box, DPAD_CENTER, DPAD_DOWN to "המשך", DPAD_CENTER.

The `zayit-android` skill (`.claude/skills/zayit-android/references/legacy.md`) has these steps in detail, and the pitfalls met while building this edition.

## Licence and credits

Pardes is a modified version of desktop Zayit and SeforimLibrary, by Elie (Elyahou) Gambache: Copyright (C) 2025 Elie Gambache, under the GNU AGPL v3 with an additional attribution term (§7b); see [../../LICENSE](../../LICENSE). This edition is distributed under the same licence, and its source is this repository.

Powered by the technologies that drive Zayit — https://zayitapp.com/

Every third-party component the APK carries is credited in Settings › רישיונות (`ui/TextActivity.kt`), with its licence text in the APK's `assets/licenses/`: the texts are copied from the main app's `app/src/androidMain/assets/licenses/` by `LegacyAssets` (its `licences` list), and the few that only this edition carries, such as `desugar_jdk_libs-LICENSE.txt`, are in `src/androidMain/assets/licenses/`. A new library, font, icon set or data file needs both, in the same change. Lucene is credited as modified to run from Android 4.4.

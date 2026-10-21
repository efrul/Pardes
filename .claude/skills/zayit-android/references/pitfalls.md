# Pitfalls already hit (and how they were fixed)

Each entry has three parts: the symptom you will see, the cause, and the fix in this codebase. Search this file for the symptom first.

## Contents
- RTL and bidi
- Compose layout
- Android platform
- Data and SQLite
- Desktop parity
- Build and R8
- Emulator and tooling

## RTL and bidi

The whole app runs with `LayoutDirection.Rtl`.

- **A Row is mirrored.**
  - Symptom: the tree panel ended up on the left on tablet, or icons came out in reverse order.
  - Cause: in RTL the **first** child of a `Row` is drawn on the **right**.
  - Fix: write children in reading order, right to left. On tablet the library tree island comes first, so it sits on the right like desktop.
- **The selection bar is on the wrong side.**
  - Symptom: the selected-line accent bar was drawn on the left.
  - Cause: `Modifier.drawBehind` draws in absolute coordinates and ignores RTL.
  - Fix: `val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f` (`ui/reader/LineText.kt`, `BookLine`).
- **Numbers get reversed.**
  - Symptom: "96426 / 12000" displayed as "12000 / 96426".
  - Cause: bidi reorders number runs around "/".
  - Fix: write "96426 מתוך 120000", or wrap the text in LTR isolates. Apply the same care to "a-b" ranges.
- **Hebrew gershayim mismatch.**
  - Symptom: the catalog row lacked תנ״ך.
  - Cause: matching on titles failed because ״ (U+05F4) and " are different characters.
  - Fix: match on desktop **category ids**, not titles (`ui/home/HomeScreen.kt` catalog: תנ״ך 1, משנה 5, בבלי 13, ירושלמי 20, משנה תורה 45, שולחן ערוך 62; טור opens book 380 TOC entries).
- **Truncated titles on narrow phones.**
  - Cause: under 300dp (Jelly Star) the top bar has no room for every action.
  - Fix: `narrowPhone` moves find-in-page into the overflow menu.

## Compose layout

- **Justified text selects beside the word.**
  - Symptom: a long-pressed word is selected and copied whole, but its highlight and handles sit toward the line's start (the right in RTL) and half cover the next word. The error grows along the line. Last lines of paragraphs are fine.
  - Cause: Android draws `TextAlign.Justify` (JUSTIFICATION_MODE_INTER_WORD) stretched, but `Layout.getHorizontal` measures the line unstretched. The selection path, the handles, the long-press hit test, `getBoundingBox` and our note underlines all go through it. This is true on every Android version through main and in every Compose version; no upgrade fixes it.
  - Fix: `ui/reader/Justify.kt` `rememberJustifiedText`. It lays the text out start-aligned once at the width a `BoxWithConstraints` around the Text gives. Then it widens each line's U+0020 spaces with `letterSpacing` spans until the line fills the width, and shows the text with `TextAlign.Start`. Drawing and every position then agree. A layout that no longer matches the plan, such as a downloaded font that finished loading, has the plan made once more. Use it for any selectable justified text: `LineItem`, `LinkItem`, the author bio.
  - Limits:
    - **Paragraph length.** Android's `SpannableString` scans its spans linearly, so one span per space costs more than the square of their number. A 2,000-space paragraph stalled for 12 s, against 3 s before. Texts over `MAX_STRETCHED_SPACES` (600) therefore keep the platform's Justify, and the bug, on under 2% of lines.
    - **Inline images.** Texts with inline images keep the platform's Justify too.
    - **Copies.** A copy carries the spacing spans, so `PlainTextClipboard` and `PlainCopySelectionContainer` write plain text to the clipboard.
- **Selecting text past the screen closed the app.**
  - Symptom: a selection dragged beyond what the screen showed could not be extended without scrolling by hand between drags, and once the line it started on had scrolled away, dragging the other handle closed the app (`NoSuchElementException: Cannot find value for key` in `MultiWidgetSelectionDelegate`/`SelectionLayoutBuilder`). A copy of such a selection also left out the lines scrolled past.
  - Cause: Compose's `SelectionContainer` knows only the texts composed now. Around a `LazyColumn` the lines scrolled away are disposed, so the selection's anchor no longer exists among the selectables it orders by; its handles are popups of their own, so nothing can scroll the list while one is dragged. No Compose version fixes this (checked on foundation 1.10.5).
  - Fix: the reader's lists (book text, commentator columns, sources and targum) no longer use `SelectionContainer`. `ui/reader/ListSelection.kt` keeps the selection as two `TextPoint`s (unit, offset): a unit is an ordered key per selectable text (book: index × `LINE_UNITS` + part; panes: the commentary's place). Texts join with `rememberSelectableText` (modifier after the Text's padding, `onLayout` in its onTextLayout), and texts scrolled away are read from the data (`textOf`, built with `buildHtmlAnnotation` exactly as `rememberHtmlAnnotation` builds them). `SelectableArea` (`TextSelection.kt`) runs the gestures at `PointerEventPass.Initial` (long press then word drag, handle drag, mouse drag; consumed so the list neither scrolls nor taps). It draws the handles itself, and `edgeScroll` scrolls the list while a drag is held in the 40dp band at the top or bottom edge. Actions resolve the selected text on the lines it was read from (`ListSelection.lastUnits`, `ReaderScreen.selectionLines`), not only those on screen. Keep `SelectionContainer` only around texts that are not in a lazy list (the author bio, licences).
- **A pager's keys behind its page count.**
  - Symptom: the app closed with `IndexOutOfBoundsException: Index 2 out of bounds for length 2` in a pager's `key` lambda (`NearestRangeKeyIndexMap`), when a commentator was added in the filter or a line with more commentators was tapped.
  - Cause: `PhoneCommentaries` remembered its `PagerState` above a `BoxWithConstraints` and gave the `HorizontalPager` inside it `key = { shown[it].bookId }`. The `BoxWithConstraints` composes its content later, at measure, so the page count already followed the new list while the key lambda still held the old, shorter one.
  - Fix: read the list through `rememberUpdatedState` in both the page count and the key, with `getOrNull` (`ReaderPanes.kt`). The same holds for any lazy layout whose state is remembered outside a `BoxWithConstraints` or other subcomposition around it.
- **Overflowing toolbar.** The phone reader toolbar uses weighted `ToolbarAction` items with an "עוד" overflow, because fixed widths were cut off on small screens.
- **DropdownMenu narrower than its field.** Wrap the field in `BoxWithConstraints` and give the menu `Modifier.width(maxWidth)`.
- **Back with a drawer open.**
  - Symptom: Back closed the pane or screen behind the drawer.
  - Fix: `ModalNavigationDrawer` doesn't handle Back by itself, so add `BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }`.
- **Tablet taskbar covered content.** The wide layout needs `windowInsetsPadding(WindowInsets.navigationBars)`.
- **A heading that never updates.** A value derived from `LazyListState` or from lines loaded later needs `snapshotFlow { firstVisibleItemIndex to vm.lines.size }`. Reading it once at composition gives a stale value; history saved entries without the chapter because of this.

## Android platform

- **System bar icon colours.**
  - Symptom: dark status-bar icons on the dark theme.
  - Fix: in `MainActivity`, `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark` (same for the navigation bars), driven by the app's theme, not the system's.
- **Screen turning off while reading.** Keep-screen-on is applied with a `DisposableEffect` only while a Book tab is shown, following `prefs.keepScreenOn`.
- **Deep links while running.** MainActivity is `launchMode="singleTask"`: links arrive in `onNewIntent` and go to the `pendingLink` state, which ZayitApp consumes with `onLinkHandled()`. `onCreate` reads `intent.data` only when `savedInstanceState == null`, so a rotation doesn't reopen the link.
- **External storage not mounted yet.**
  - Symptom: onboarding showed again on a cold boot of the emulator.
  - Cause: `getExternalFilesDir` was not ready at app start.
  - Fix: LibraryStore checks both external and internal `files/library` and picks the one that holds `seforim.db`.
- **Opening the DB on the main thread.** `MainActivity` opens the repository in `produceState { withContext(Dispatchers.IO) { ... } }` and shows a loading box meanwhile. An 8 GB DB with WAL recovery can take seconds.

## Data and SQLite

- **SQLite 3.18 on API 26** (3.19 on 27, 3.22 on 28–29, 3.28 on 30, 3.32 on 31–33). There is no FTS5 and some newer syntax is missing. Desktop schema features must work on 3.18, or have a fallback below the version they need; the SQLDelight dialect is `sqlite-3-24`, so it does not catch them. Below 3.24 there is no UPSERT: favourites and visit history run `sqldelight-user-legacy/.../UpsertLegacy.sq` below Android 11 (`FavoritesStore`, `HistoryStore`). `VACUUM INTO` needs 3.27: below, `UserDataBackup` checkpoints and copies the file under the write lock. Below 3.32 one statement takes at most 999 bound parameters, so an `IN ?` list of ids fails with "too many SQL variables" below Android 12 only: bind it in chunks (`SqlVariableLimit`; `LineConnections` uses 400). A library split on a FAT32 card, and every library below Android 9, goes through requery's 3.49 and hides this, so test it on a single-file library on Android 9 to 11.
- **PRAGMA in `execute()` throws on Android.** `data/PragmaSafeDriver.kt` routes row-returning PRAGMAs through `executeQuery`. Wrap any new driver with it.
- **A row wider than the cursor window.**
  - Symptom: opening a category in the book list closed the app with `SQLiteBlobTooBigException: Row too big to fit into CursorWindow requiredPos=3, totalRows=4` (encyclopedias › שם הגדולים).
  - Cause: Android reads a query through a CursorWindow of 2 MB, and a row must fit in one. `book.notesContent` reaches 4.25 MB (שלמי כהן בבא בתרא; מיקרופדיה תלמודית 2.9 MB, 17 books over 0.8 MB) and the dao's book queries are `SELECT *`. The longest `line.content` is 1.28 MB. Desktop reads through JDBC, which has no such window.
  - Fix: `LibraryStore` opens the library with `AndroidSqliteDriver(windowSizeBytes = WIDE_WINDOW_BYTES)` (16 MB, the delta updater's). Below Android 9 Android's window cannot be widened (`CursorWindow(String, long)` is API 28; SQLDelight applies `windowSizeBytes` from 28), so there every library is opened with the bundled requery SQLite and its wide windows (`SplitDatabase.usesBundledSqlite`, which every opener asks). A window only grows to its size as rows need it. For a list of books use `LibraryStore.catalogBooks()` (id, title, category, order in one query), not `getBooksByCategory` per category: that one reads every row's notes (37 MB in all) and four more tables per book.
- **A user-database write that fails closes the app.**
  - Cause: the stores (UserDataStore, FavoritesStore, HistoryStore) write with no catch, and a write launched from `rememberCoroutineScope`, `appScope` or a bare `viewModelScope.launch` has no handler. A full disk (SQLiteFullException) is enough, and a phone that took an 8 GB library gets there.
  - Fix: launch user-data writes through `guardedWrite("what") { ... }` (data/UserDataStore.kt), which logs and returns false; in ReaderViewModel use `launchSafely`. Show a confirmation toast only when it returns true.
- **A long search fails with TooManyNestedClauses.**
  - Symptom: a pasted verse of about 15 words shows "החיפוש נכשל" with "maxClauseCount is set to 1024".
  - Cause: each word adds up to 32 dictionary forms, n-grams, four phrase positions and a fuzzy query of up to 50 terms, plus two boost queries; Lucene counts the rewritten leaves against a static limit of 1024.
  - Fix: `LuceneSearchEngine` raises `IndexSearcher.setMaxClauseCount` to 16,384 when it is built.
- **Read-only pushed DB.**
  - Symptom: "Could not open the database in read/write mode".
  - Cause: the file is owned by `shell` with mode 644 after `adb push`.
  - Fix: `chmod 666`, or `chown` to the app uid as root. Also delete `seforim.db-wal` and `seforim.db-shm` left over from another machine.
- **Copying into the app's folder without root.** What the transfer scripts learned on the API 33 emulator, as adb's `shell` user:
  - `adb push` into `/sdcard/Android/data/<app>/` fails with "remote fchown failed" (folders: "secure_mkdirs failed"): adbd chowns paths outside `/data/` to fs_config's owner. It works in `Download` (FUSE ignores the chown) and in `Android/obb/<app>/` for files, but `mv` from either into `Android/data` is a copy, not a rename: separate mounts.
  - `adb exec-in "cat > f" < file` loses the end of the stream (a 2-byte file arrived empty), and returns before the phone has written the rest. `adb shell -T "cat > f" < file` is exact, waits and returns the exit code; the scripts stream every file this way, in place, as `.zayit-incoming-<item>`, then rename. Not `<item>.part`: the app's `discardStaleDownload` (LibraryInstaller) deletes those when it opens without a library, so opening the app during a transfer used to delete the database being copied ("seforim.db did not arrive complete"). The scripts also say not to open Pardes until they print done.
  - **Not on Windows.** adb.exe reads its standard input as text: `shell -T` cuts each file at its first 0x1A byte (Ctrl-Z), and `exec-in` corrupts it too (3.0 MB arrived as 2.35 MB). Lucene files hold 0x1A within their first bytes, so the size check fails on the first index. `push-desktop-library.ps1` therefore pushes each file to `/data/local/tmp/zayit-push` (adb push reads the file itself) and copies it into place with `cat` on the phone: byte-identical, at the cost of room for the largest file twice. pwsh on Linux does not show this; test the `.ps1` on Windows.
  - **Starting it on Windows.** Windows 11's "Run with PowerShell" is `powershell -file "%1"` under the execution policy, so a downloaded `.ps1` (Zone.Identifier ZoneId=3) is refused: "is not digitally signed" under RemoteSigned, and the default Restricted runs no script at all. `push-desktop-library.cmd` next to it runs it with `-ExecutionPolicy Bypass` and `pause`s; users double-click that (`*.cmd text eol=crlf` in androidApp/.gitattributes keeps it CRLF on the Linux runners that zip it).
  - The app's `files/` folder is `2770 u0_aNNN:ext_data_rw` and `shell` is in `ext_data_rw`, so a library folder the app makes there is writable by adb as long as it keeps the inherited setgid group. `chmod 777` drops the setgid bit; folders the app then creates inside (its own download, `delta-cache`) are `0770 u0_aNNN:u0_aNNN`, which `shell` can rename but not empty. The scripts rename such leftovers `.zayit-old-*` and the app deletes them at startup.
  - "touch: Operation not permitted" on a writable folder: toybox touch also sets the times, which fails on an existing file owned by someone else. Probe with `: > file`.
  - **Android 8 to 10.** Stock Android 8.x has no awk: the scripts sum the phone's file sizes and read `/proc/mounts` on the computer. Below Android 11 the phone's shared storage (sdcardfs) refuses `chmod` (EPERM, checked on the 8.0 emulator) and needs none, so the scripts only try it there.
- **Resetting user data.** `UserDataStore.clearAll` deletes rows through the driver. Deleting the file under an open driver corrupts state.
- **Shabbat without a parasha.**
  - Cause: KosherJava `JewishCalendar(Date)` converts with the default timezone (UTC in the emulator).
  - Fix: `JewishCalendar().apply { setGregorianDate(y, month0, d) }` (`data/Zmanim.kt`).

## Desktop parity

- **Porting from the fork's old sources.** The first Lucene search screen was ported from the fork's June `SearchResultViewModel`: a category tree with checkboxes and "נמצאו N תוצאות". The 0.6.1 the user ran was different: category chips, "כ־N תוצאות בספרי יסוד · חיפוש בכל הספרים", and results grouped by book with "עוד N תוצאות מ…". Always port from the desktop version the port matches, now 0.6.7, which this repository's own `SeforimApp/` has been since the merge of 2026-10-20 (desktop-reference.md).
- **Authorship copied from desktop.** Desktop's welcome ("שנבנה באהבה על ידי אברך ומפתח חרדי"), settings header ("נוצר על ידי אליהו גמבש"), About (first person) and terms ("הוענק לנו", "הגרסאות הרשמיות כוללות") describe desktop Zayit. Copied verbatim, they told users the Android app was made by Zayit's author, which the user rejected. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept. See SKILL.md › Authorship and name.
- **Approximations get caught.** The user compares Android with desktop screenshots. Port the logic instead of guessing:
  - **Commentator grouping:** `data/CommentatorGrouping.kt` is a port of desktop `resolveGroupLabel`, `groupRank`, `canonicalRank` and `extractEarliestYear`. Alphabetical lists under a single "תורה" header were wrong. The "תרגומים" group is filtered out of commentators because targumim have their own pane. Use `shortTitle` for column titles.
  - **Logo:** desktop `LogoImage` tints its lettering with a ColorFilter (a plain image came out white). Pardes's Home draws the word פרדס as text in that tint (HomeScreen.kt `LogoImage`).
  - **Strings:** copy them from `SeforimApp/src/commonMain/composeResources/values/strings.xml` (Hebrew), except the authorship texts (SKILL.md › Authorship and name). Onboarding keeps them in `ui/onboarding/DesktopStrings.kt`; the welcome subtitle is in `AndroidStrings`.
  - **Commentators per page:** follows the desktop setting `maxCommentatorsPerPage`.
- **Translations.** The Android UI is Hebrew-only. Don't introduce English UI strings.

## Build and R8

- **Maven Central returns 429 in the cloud container.** `settings.gradle.kts` lists the GCS mirror `https://maven-central.storage-download.googleapis.com/maven2/` before mavenCentral. Use the same mirror for curl downloads of jars.
- **Debug APK too slow on the emulator.** It is 68 MB unminified and dexopt'ed slowly. Use `assembleRelease`, which is R8-minified and signed with the debug key. Also run `setprop pm.dexopt.install speed-profile` (`emulator-prep.sh` does this).
- **Release-only crashes** come from missing keep rules (`app/proguard-rules.pro`):
  - kotlinx-serialization: `$$serializer`, `Companion`, `serializer()` for `io.github.kdroidfilter.**`. Without them, settings and onboarding crashed when deserializing.
  - zstd-jni: `-keep class com.github.luben.zstd.** { *; }`, because the JNI side looks up fields by name.
  - Lucene: SPI codecs and the reflective attribute implementations. See lucene-android.md.
  - `-dontwarn` for optional deps (jspecify, re2j, awt, javax.annotation, slf4j, brotli, xz, asm).
- **zstd-jni on Android** needs the `@aar` artifact, which ships the `.so` files. The plain jar only has desktop natives.
- **Kotlin and AGP versions** are pinned in `androidApp/gradle/libs.versions.toml`: AGP 8.12.3, Kotlin 2.4.20, Gradle 9.1.0, compileSdk 36. Don't bump them casually; the KMP androidTarget plus the SeforimLibrary sources compile with this set.

- **Kotlin and libraries.** The app is on Kotlin 2.4.20, the same as desktop 0.6.7 (and 0.6.1). The zmanim engine is desktop's `kosherkotlin` 3.0.5, which needs 2.4 metadata. Its AAR also declares `minCompileSdk 37`, while AGP 8.12 tops out at 36. `gradle.properties` therefore sets `android.experimental.disableCompileSdkChecks=true`; its classes link against API 33.
- **Harmless lint output.** `lintVitalAnalyzeRelease` prints `e: ... incompatible version of Kotlin ... expected version is 2.2.0` for kotlin-stdlib and kosherkotlin. Lint bundles an older Kotlin analyser and the build still succeeds; ignore these lines.
- **Never interrupt a Lucene reader thread.** The engine (desktop's, 0.6.1 to 0.6.7) shares one `SearcherManager` across all searches. A `Thread.interrupt` (`runInterruptible`, `shutdownNow`) during an NIOFS read closes the shared `FileChannel`, and every later search then fails with `ClosedChannelException`. Cancel cooperatively instead: `SearchService.countFacets` checks a flag between slices.

## Emulator and tooling

- **Background commands die after 2 h.** This includes the emulator. Restart it with `emulator-start.sh`; the AVD data persists.
- **Emulator segfault (exit 139).** After a run of `Failed to find ColorBuffer` / `Failed to bind to texture` errors in the log, the SwiftShader renderer crashed qemu, twice within minutes of `wm size 2560x1600`. Test tablets at `wm size 1920x1200` + `wm density 240` (the same 1280dp). Any `wm size` change can still trigger it (once at 480x854 too), so take the Jelly Star and tablet shots in one sitting rather than switching sizes back and forth, and expect to restart. The `wm size` override survives the restart, so run `wm size reset; wm density reset` after boot.
- **"יש להתקין מחדש את מסד הנתונים" after an emulator restart.** The library was intact; shared storage was not mounted (`adb shell sm list-volumes` showed `emulated;0 unmounted`, logcat "Failed to find mounted volume"), after system_server restarted during boot. `sm mount 'emulated;0'`, then force-stop and reopen the app. `emu-wait.sh stable` now does the mount.
- **Disk allowance full.** The session's writable disk is a fixed allowance (about 40 GB here). The full desktop library (`seforim.db`, 8 GB) plus the system image (8 GB), Gradle caches (5 GB) and the AVD (3 GB) used it up, and the emulator then refused to start ("does not have enough disk space to run avd"). Once the subset library is built and the desktop screenshots are taken, delete the full `seforim.db` under `~/.local/share/io.github.kdroidfilter.seforimapp/databases/`; `fetch-desktop-library.sh` fetches it again if needed. Check with `df -h /` before long runs.
- **Slow installs under TCG.** `adb install -r` of the ~23 MB release APK can take over 8 minutes while another agent drives the app, and fails with "Broken pipe" / "Can't find service: package" when system_server restarts. Don't wrap it in a short `timeout`; wait for `emu-wait.sh stable`, then retry. Stop idle Gradle daemons (`./gradlew --stop`) first; with them and the emulator together, system_server lost its network stack twice.
- **TCG instability.** Under software emulation `system_server` crashed ("Lost network stack") and apps hit ANR loops. Fixes:
  - 3 cores (`-cores 3`).
  - 3 GB RAM.
  - Disable the Google apps (`emulator-prep.sh`). Boot went from 843 s to 289 s.
  - Disable them again after a test that needed one: with the Pixel launcher (home-screen widgets) and Gboard enabled, every boot went into a `Lost network stack` loop, system_server restarting every ~6 min with the user stuck at BOOTING. `pm disable-user --user 0 com.google.android.apps.nexuslauncher com.google.android.inputmethod.latin` ended it; the placed widgets come back when the launcher is enabled again.
- **Foreground `sleep` is blocked** by the harness. Wait with the Monitor tool and an until-loop (`emu-wait.sh`).
- **`adb input text` can't type Hebrew.** Use `emu-type.sh` (ADBKeyBoard) or deep links (`zayit://search?q=`).
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`.** The installed `io.github.efrul.pardes` APK was signed with another key (the first Pardes test build, 2026-10-15, still had the old Zayit-named key; the pre-rename `io.github.efrul.zayit` app is another package and never clashes). Every build from this repo is signed with `androidApp/debug.keystore`, so that means a changed signing config (a future release key, say). Builds from before the shared key also used the earlier `io.github.kdroidfilter…` id, so they install beside this one instead of clashing. Uninstalling deletes the app's external files, library included: re-push the library afterwards (`push-desktop-library.sh`). Published releases are signed with the release key, so they never install over a development build, and the in-app updater (`update/`) does not offer them there; never uninstall the user's development build to switch without asking.
- **Testing the in-app update on a phone with a real library.** Give the build to update to a higher `versionName` but the same `versionCode`: a higher `versionCode` on the user's phone would stop every later local build from installing over it (a release build is not debuggable, so `install -r -d` cannot downgrade it), and uninstalling deletes the library. "Install unknown apps" is a security setting: let the user switch it on.
- **No on-screen keyboard.** The emulator reports a hardware QWERTY keyboard, so Gboard stays hidden and the search bar may take focus by itself (the app's `hardKeyboard` paths run). To test what a phone shows with its keyboard up (the Home lift, the 240dp search sheet): `adb shell settings put secure show_ime_with_hard_keyboard 1` and `ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`. Gboard can't type Hebrew from adb: type with ADBKeyBoard (`emu-type.sh`) first, then switch.
- **Finding a main-thread stall.** `Skipped N frames` says how long, not where. As root, `kill -3 <pid>` once a second during the stall writes every thread's stack to `/data/anr/trace_*`; pull them, keep the `"main"` thread, and retrace with the release mapping: `java -cp $(ls /opt/android-sdk/build-tools/*/lib/d8.jar | sort -V | tail -1) com.android.tools.r8.retrace.Retrace app/build/outputs/mapping/release/mapping.txt stacks.txt`. That is how the Home's calendar and zmanim work in composition (EarthWidget, תפילת היום, the Temple card) was found.
- **Popups are not in the uiautomator dump.** Compose `DropdownMenu`/`Popup` windows (overflow menu, history popup, tab menu) are often missing from `uiautomator dump`, so `emu-tap.py` can't find their items. Take a screenshot and tap by coordinates.
- **Status bar in release images.** The emulator shows a warning triangle, "3G" and the "ADB Keyboard {ON}" chip. `emu-demo.sh on` cleans them up for store shots.
- **"Viewing full screen" prompt.** After `wm size` changes, Android shows an immersive-mode hint. Tap "Got it" (`emu-tap.py "Got it"`) before taking screenshots.
- **Screen went black.** Run `svc power stayon true` and wake with `input keyevent KEYCODE_WAKEUP` (`emulator-prep.sh`).
- **Workflow concurrency.** Only CPUs−2 (here 2) workflow agents run at once. Long builds by agents queue the rest.

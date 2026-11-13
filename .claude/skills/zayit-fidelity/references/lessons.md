# What the work taught

What went wrong, and what was found, while Pardes's desktop features were built on Zayit 0.6.12 (2026-11-05 to
2026-11-11: the daf view's second round, the links in the texts, the search's tabs and order, the default
commentators, the install from a folder, a desktop's link, the feedback from other apps, the open siddur). Read the
part that fits the change at hand before starting it. `eli-style.md` is how the code must read; this is what the
tools and the code did when it was written.

## Building and checking

- **Only the Change map check runs on a push to a branch.** Lint and tests run in CI on a pull request to `main`.
  So run them here before every push: `.claude/skills/pardes-desktop-run/scripts/gradle.sh :SeforimApp:jvmTest
  ktlintCheck detekt` (the wrapper sets the locale and keeps Zayit's private packages out). A cold build gets through
  Maven Central's 429 with the mirror that `pardes-desktop-run`'s `setup.sh` installs.
- **The check needs the base commit.** In a shallow clone it fails with `git diff … exit 128`:
  `git fetch --depth=1 origin <the base's sha>` first.
- **ktlint:**
  - 140 columns. A chain of calls that does not fit goes one call a line; arguments wrap all or none; a long
    `if`/`else` expression goes in braces on its own lines.
  - The Compose rules: a composable's lambda parameter is in the present tense (`onUseLibrary`, `onScroll`; not
    `onLibraryUsed`, `onScrolled`); a parameter read inside a `LaunchedEffect` goes through `rememberUpdatedState`;
    no `MutableState` parameter, a value and a lambda instead.
  - `ktlintJvmMainSourceSetFormat` and `ktlintJvmTestSourceSetFormat` fix most findings. Check with `git status`
    that they changed only your files.
  - **Escapes turn into characters.** The formatter and the editing tools write a `\uXXXX` escape out as the
    character itself, and an invisible one (a byte order mark, a joiner, a geresh look-alike) is then stripped or
    unreadable: a `'﻿'` literal once became `''`. Keep such a character in a named constant, write it with a
    script that puts the escape's text in the file, and read the bytes back.
  - A finding in a file that is gone is a stale report: `rm -f SeforimApp/build/reports/ktlint/*/*.txt`. When one
    ktlint task fails, the ones after it show old reports: `--continue`.
- **detekt** (60-line methods, 6 parameters, 15 functions a file) is met by splitting, never by suppressing: a small
  class in place of a seventh parameter or a sixth destructured value, a second file for a second concern.
- **`detekt` checks nothing of SeforimApp.** Its `detekt` task is NO-SOURCE (a multiplatform module); its source
  sets' tasks are `:SeforimApp:detektJvmMainSourceSet` and `:SeforimApp:detektJvmTestSourceSet`, which report the
  issues beyond `SeforimApp/detekt-baseline-jvmMainSourceSet.xml` (308 on 2026-11-13, most of them old). Run them on a
  change and look at its own files' lines; `LongParameterList` is not reported on composables. Its
  structured-coroutines rule `SCOPE_003` wants a scope taken by a constructor marked
  `@param:StructuredScope @property:StructuredScope`. The compiler's plugin of the same rules fails the build on a
  plain function that launches on a `CoroutineScope` parameter not marked `@StructuredScope`, and on a scope a class
  makes for itself unless its property is (`DafDownloads`, `PdfBookStore`).
- **Kotlin, as it bit:**
  - a constructor or function that ends in a lambda parameter keeps it last, or every caller's trailing lambda binds
    to the new parameter (`FeedbackSource(database) { … }`);
  - a property named `name` on an enum's extension is shadowed by the enum's own `name`;
  - `open` is a soft keyword, and a function of that name reads as a modifier (`openOn`);
  - a test's `assertEquals(listOf(listOf(30L)), …)` may need its element type said;
  - a sealed interface's subclass may live in another file of its package and module: a tab of Pardes's own
    (`PdfBookTab`, 2026-11-13) leaves `TabsDestination.kt` alone, but takes a branch in each of its five exhaustive
    `when`s (`TabsContent`'s two, `TabsViewModel.withTabId`, `DesktopSession.tabTypeFor`, `ZayitDeepLink.toShareLink`,
    `SessionManager.computeTabTitles`) and a fixed `@SerialName`, as the session keeps it by name. An older Pardes
    cannot read a session that holds it, and starts on a new one.
- **Scratch tests and benches** (`Tmp*`) are deleted before the commit. A benchmark runs alone: mockk in the same JVM
  slows the real repository's calls.

## Compose and Jewel

- `VerticallyScrollableContainer(scrollState)` scrolls its content itself. An inner `verticalScroll` on the same state
  fails with "measured with an infinity maximum height".
- **`remember` does not outlive a rebuilt screen.** A step of the install wizard that reloads, or a tab dragged to
  another window, comes back empty. What must stay goes in a small `ViewModel` of the window's store
  (`viewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current, key = …)`) or in the tab's persisted state.
- **Every tab of a window stays composed** (`TabsContent`'s KDoc; `CLAUDE.md`'s "an LRU of 3" is older): only the
  selected one is measured and drawn. A tab that holds something heavy composes it only while `LocalTabSelected` (a
  PDF book's file and drawn pages), keeps its place outside the composition, and takes focus only then.
- A `LazyColumn` keeps pixels, not places: after a zoom or a resize, a place is kept by scrolling to its item
  (`requestScrollToItem`), not by trusting the offset.
- **A list not laid out is moved with `requestScrollToItem`.** `scrollToItem` waits for the list's first layout, and
  a list kept out of the composition (the daf's, while the scan stood in its place) never has one: the move hung, and
  ran late over a newer place once the list came back (2026-11-13). `requestScrollToItem` sets the place at once, and
  `firstVisibleItemIndex` reads it.
- **Another view of a book goes in the daf's place, not inside the daf.** The scan first sat inside the daf's pages: it
  needed the daf's layout file and kept the two lists in step by hand. In the daf's slot of the text view
  (`DafMode.scan`, `DafScanReader`) it has the frame's keys, zoom and place for nothing: it sets `DafControl`'s
  lambdas (in a `SideEffect`, never cleared on leaving, as the next view sets its own during its composition) and
  tells its place through the daf's `onScroll`, and the text opens there (2026-11-13).
- **Pages side by side follow the view's direction.** A `Row` in a view laid out from the right puts its first child
  on the right: the right page first there, the left one first otherwise. Overriding `LocalLayoutDirection` around
  the pages would flip what inside them reads the direction. A list whose items become rows of two keeps its places
  by the pages' own order, and turns them into rows only where it scrolls (`itemOfAmud`, `rowOf`); turned on or off,
  it reopens at what was at its top (2026-11-13).
- **A Zayit test that builds a Zayit class breaks with a new constructor parameter** (`PostSelectLineTest` builds
  `BookContentViewModel` by hand): add the argument there, a relaxed `mockk`, and write the test into the map.
- **An item that grows is measured at its new size only in a frame to come.** `scrollToItem(index, offset)` into an
  item whose content was just set (a part laid out in place of its placeholder) runs on, before then, through the
  items after it: the book daf's experiment landed four chapters on. Wait until `layoutInfo.visibleItemsInfo` gives
  the item its new size (2026-11-13).
- Text lines are whole pixels high. A long run of lines at a fractional pitch drifts by half a pixel a line: give each
  line the whole pixels that keep it on the pitch (`fix(daf): a long run's lines no longer drift`).
- A menu that closes before the coroutine it launched has ended cancels it: do the work, then dismiss
  (`DesktopLinkRow`).
- Jewel has what is needed before anything is drawn by hand: `Link` for a link inside a sentence,
  `InlineInformationBanner` for a note, `SettingCard` (Zayit's, `SettingsComponents.kt`) for a setting with a
  description that opens. `Tooltip` needs `@OptIn(ExperimentalFoundationApi::class)`.
- **A top-level value that Zayit's code reads in composition** (an add-on's `installed…`) can follow a setting: a
  getter over a snapshot state that `AppSettings` mirrors its preference into (`OpenSiddurSwitch`). A plain
  `StateFlow.value` there would not recompose the menus that read it.

## Data and settings

- `UserSettingsDb.Schema.create` runs at every start with `CREATE TABLE IF NOT EXISTS`: a new table in a `.sq` file
  needs no migration.
- A preference that follows the user to another machine goes in `AppSettings.PORTABLE_KEYS`; `clearAll()` resets each
  flow by hand, a new one too.
- **The library's flags lie for 75 books.** `hasCommentaryConnection` and `hasSourceConnection` are 0 for the 38 books
  of the Chavruta, their notes and four others, though the links exist: a fault of SeforimLibrary's generator, worked
  around in the app. Ask the links, not the flags.
- **The library's links, counted** (release 20261012193827, 4,428,487 in all): COMMENTARY 2,347,737; OTHER 1,615,544;
  EIN_MISHPAT 137,621; RELATED 89,145; REFERENCE 88,869; MESORAT_HASHAS 45,654; TARGUM 34,729; QUOTATION 31,418;
  SUPER_COMMENTARY 24,886; MIDRASH 6,880; MISHNAH_IN_TALMUD 2,390; PARSHANUT 1,963; DIBUR_HAMATCHIL 1,651. The
  generator put under OTHER and RELATED what it could not sort: the Bavli's links to the Tanakh are OTHER (8,894 one
  way, 15,079 the other; 17,195 lines of the Bavli have one). Count a kind before building on it:
  `sqlite3 "file:seforim.db?mode=ro"`, on the whole library, not a cut one.
- Zayit's code checks category ids with `> 0`: a made-up category (a group of commentaries of one time) takes a large
  positive id, from 10,000,000.
- The reader's selection is never empty: a line is selected as a book opens, a heading selects up to 128 lines.
- Another app's database (an installed Zayit's settings, Otzaria's history) is opened read-only (`open_mode=1`) and
  never written; what was read from it is remembered by hashes in Pardes's own database (`OtherAppLedger`).
- **androidApp compiles some desktop files as they are** (`androidApp/app/build.gradle.kts`, its `Sync` lists). A
  change to one of them must still compile there, with no desktop-only import.

## The change map

- **Edit entries by their path**, with `scripts/mapedit.py`, never with a global `sed`: a `Size: 297 lines.` once
  matched two entries.
- The header's "(N files)" are the entries' counts, and the kinds' table follows each file's Kind:
  `python3 .claude/skills/zayit-fidelity/scripts/mapedit.py` prints the counts.
- **Several commits from one working tree:** each commit carries its own entries and must pass
  `check_change_map.py --committed`. Make the earlier state of a file by undoing the later change in a script (exact
  replacements that assert they were found), commit it with its entry, then put the final file back and write the
  final entry. `git stash push -- <paths>` sets whole files aside.
- The documents that list the hooks move with them: `branding/README.md`, the Pardes paragraph of `CLAUDE.md`, the
  licences list (`branding/pardes/assetsExtra/common/licenses/README.md`).

## An add-on of Pardes's own, and someone else's code

- Zayit's private add-ons sit behind `addons/<name>/enabled|disabled`, and Pardes may compile only `disabled`. Pardes
  has given two of them its own open code, in two ways:
  - **the links in the texts:** `addons/quotelinks/disabled`'s four lines hand over Pardes's `TextLinks` where they
    gave null (an edit of Zayit's file, one line);
  - **the siddur:** a third variant, `addons/siddur/open`, which `branding/brand.gradle` compiles in the place of
    `disabled` (an `exclude` on its path and a `srcDir`), with `Brand.OPEN_SIDDUR` telling the app the build carries
    it. No file of Zayit's changes for it but the hooks of its setting.

  Prefer the second for anything larger than a line. Check that both brands still compile:
  `gradle.sh :SeforimApp:compileKotlinJvm -Pseforimapp.brand=zayit`.
- **A dependency of code in `src/jvmMain` goes before `brand.gradle`'s `if (brand == 'zayit') return`**, as both
  brands compile that code. PDFium went after it on 2026-11-12 (the PDF experiment), and the zayit brand did not
  compile until 2026-11-13.
- **A third party's engine is a module of its own** (`siddur-open/` is the example):
  - a copy at a pinned commit, with its licence files, never a submodule (a release must build without another
    repository, and the source archive must hold it);
  - Pardes's own `build.gradle.kts` on the catalog's versions, with ktlint and detekt turned off for the module: the
    sources stay their author's;
  - a `README.md` with the source, the commit and date, what is left out, and **every change Pardes makes to the
    copy, with its date** (the AGPL asks for that notice);
  - `include(":…")` in `settings.gradle.kts`; the resources its jar reads named in
    `SeforimApp/src/graalvm/reachability-metadata.json` (the native image carries only the resources named there,
    and the release is a native image);
  - its notices copied beside the app's licences (`licenceCopies` in `brand.gradle`) and named in the licences list;
  - a row for every file in the map, and "changed by Pardes on <date>" on the rows of the files changed.
- Its tests on a real library are skipped in CI: run them once on the release Pardes installs
  (`SEFORIM_DB=…/seforim.db`), and say so in the commit.
- **A change to someone else's engine is offered back** (an issue or a comment where they offered it), with the diff,
  so that Pardes's copy can drop it at the next update.

## Zayit's later code, and its licence

Pardes stays on 0.6.12; the rest of Zayit is read, never merged (`SKILL.md` › Merging a Zayit release).

- Read it in a scratch clone outside the repository:
  `git fetch --filter=blob:none --shallow-since=2026-10-10 --no-tags https://github.com/kdroidFilter/Zayit master`,
  then `git log FETCH_HEAD -- <path>`. A fork of Zayit is read the same way, to see whose each change is.
- **The licence changed at `1b9f1f21`** (2026-11-01, 16:26 +02:00). A commit is still under the AGPL v3 with §7b
  exactly when it is an ancestor of it: `git merge-base --is-ancestor <commit> 1b9f1f2`.
- Being AGPL is not leave to take it: it is later than Pardes's base, so the user is asked first, with the commit,
  its date and that test. One was taken so, on 2026-11-11: the siddur's keyboard scrolling (`21659516`), recorded in
  the map's section 5.
- **A file from a fork of Zayit may hold Zayit's later code.** Before taking it, diff it against Pardes's own copy
  of the same file and find where each difference comes from.

## A testers' build

A build for the user to try, from a branch, before anything reaches `main`:

1. Put a `## <x.y.z> · <date>` section at the top of `desktop/CHANGELOG.md` (what the build has, for users, in
   Hebrew), and set the map's row of the changelog to that version. Commit as `docs(release): …` and push.
   `desktop-release.yml` takes the version and the release's notes from that section.
2. Run `desktop-release.yml` on the branch (workflow_dispatch) with `quick: "true"` (Windows x64 alone, a
   pre-release) and a `note`. It takes about 20 minutes and gives the tag `desktop-<x.y.z>-<run number>`.
3. Revert the changelog commit (`revert: the changelog's <x.y.z> section waits for the public release`):
   `android-site.yml` publishes the changelog from `main`, and the section belongs to the public release.

A session cannot delete a release. Tell the user which pre-releases can go once tried: Pardes for Android 0.3.0 to
0.4.1 reads only the 30 newest releases.

## What the user asked to be done again

Each of these was built, shown, and sent back. They say what the user expects:

- **The same behaviour everywhere.** The zoom keys changed the daf alone, then the pinch did: the user wanted both to
  zoom the commentaries too, "as in the text". A setting sits with its kind: the daf's fonts stay among the fonts,
  though the daf has a settings page.
- **What was picked shows where it acts.** A link "search in the tabs picked" was refused: the tabs picked become the
  search bar's chips, and a repeated search searches in them.
- **Fewer words, a known sign.** Two link lines under "כל המפרשים" for the default commentators became a pin
  ("תחשוב על העיצוב של זה").
- **A new screen looks like its neighbours.** The install's feedback step was redone "בסגנון העיצוב של פרדס": laid out
  as the wizard's other steps, with the same components.
- **Defaults are the user's to set.** The feedback step's boxes are all ticked ("ברירת מחדל הכל יהיה מסומן"); the
  siddur's days of Iyar are ordinary weekdays until a custom is picked.
- **Zayit's own rendering is the reference.** A wrong link in a Gemara line was settled by the user's screenshot of
  the same page in Zayit: compare with Zayit before defending a result.

Show a screenshot of a choice of design before building on it, and ask of each new control: does it behave as the
one beside it does?

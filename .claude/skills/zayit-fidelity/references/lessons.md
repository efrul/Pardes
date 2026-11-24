# What the work taught

What went wrong, and what was found, while Pardes's desktop features were built on Zayit 0.6.12 (2026-11-05 to
2026-11-11: the daf view's second round, the links in the texts, the search's tabs and order, the default
commentators, the install from a folder, a desktop's link, the feedback from other apps, the open siddur). Read the
part that fits the change at hand before starting it. `eli-style.md` is how the code must read; this is what the
tools and the code did when it was written.

## Building and checking

- **Only the Change map check runs on a push to a branch.** Lint and tests run in CI on a pull request to `main`.
  So run them here before every push: `.claude/skills/pardes-desktop-run/scripts/gradle.sh --continue
  :SeforimApp:jvmTest ktlintCheck :SeforimApp:detektJvmMainSourceSet :SeforimApp:detektJvmTestSourceSet` (the wrapper
  sets the locale and keeps Zayit's private packages out; detekt below). A cold build gets through Maven Central's 429
  with the mirror that `pardes-desktop-run`'s `setup.sh` installs.
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
  change and look at its own files' lines; `LongParameterList` is not reported on composables. They always fail on the
  old issues, so give Gradle `--continue` (or the tests stop with them) and count what is new against the base's own
  run, in a worktree of `main` (`git worktree add --detach <dir> origin/main`, its submodules by `git submodule update
  --init --reference .git/modules/<name>`): an issue is new when its file, rule and message, numbers aside, are not in
  the base's list. Stage 3 ran only `detekt`, and its 32 issues (main 299, the branch 331, none in the tests) came to
  light only at the public release, 2026-11-22. Some of Zayit's functions stand at `detekt.yml`'s limits exactly
  (`NotesPopupContent`, `addCategory`: 60 lines; `desiredPanes`: 15): a hook in them goes in a Pardes function, the
  rest of Zayit's code moved into one of his file's own when even one line is too many. detekt's baseline matches a
  finding by its function's signature: a Zayit function made internal for Pardes shows again (`SuggestionRow`), and its
  id in the baseline takes the new visibility. Its
  structured-coroutines rule `SCOPE_003` wants a scope taken by a constructor marked
  `@param:StructuredScope @property:StructuredScope`. The compiler's plugin of the same rules fails the build on a
  plain function that launches on a `CoroutineScope` parameter not marked `@StructuredScope`, and on a scope a class
  makes for itself unless its property is (`DafDownloads`, `PdfBookStore`).
- **detekt counts `let`, `also`, `forEach` and `use` as nesting in a function's cyclomatic complexity**: a `?.let` and
  an `&&` added to Zayit's `BreadcrumbPopupContent` took it from 15 to 17 (2026-11-22). Move such a step into a shared
  helper (`PopupMarking.reveal`, `markOnPointer` over the list's items) rather than inline it. A file at 15 functions
  takes its new one into a second file for a second concern (`PopupMarking.kt`, `StudySourceField.kt`).
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
- **Maven Central can stop serving a version.** On 2026-11-23 it answered 404 (`x-amz-error-code: NoSuchKey`) for
  `io.github.kdroidfilter:kosherkotlin` 3.0.3 to 3.0.5, its index still listing them, the day after 4.0.0 came out;
  Zayit 0.6.12 is built with 3.0.5, so every clean build failed in its first minutes ("Could not find …"), a testers'
  build among them, while the container, whose init script takes Maven Central from Google's mirror, built on.
  `desktop-release.yml` took that mirror as a fallback for a day; it went with the move to 4.0.1 (2026-11-24): the
  author had withdrawn those versions, at the request of the luach whose method they carried, and a build must not
  fetch a withdrawn version from elsewhere. Ask the repository itself before blaming the change: `curl -sI` on the
  artifact's `.pom`, and a scratch project with a clean Gradle home (`-g`).
- **Building on Windows from a folder whose name has Hebrew letters** (the user's computer, 2026-11-15): the Android
  Gradle plugin refuses the path (`-Pandroid.overridePathCheck=true`), and Kover's agent cannot find its file, its
  path turned to question marks: build from a `subst` drive. Gradle itself needs Java 21 or later (a JBR from
  `~/.jdks`). A test measured again with nothing changed needs `--rerun`, or Gradle skips it.
- **Other sessions' branches may be ahead of `main`.** Before starting, list the branches ahead of it
  (`git fetch origin '+refs/heads/*:refs/remotes/origin/*'`, then `git rev-list --count origin/main..<branch>`) and the
  open pull requests: on 2026-11-23 the newest desktop work (requests 73 to 77, the pages' zoom made a size) was on a
  branch with no pull request yet, and the user asked that it be heeded ("שים לב לכל תיקונים בענפים אחרים שהתקדמו").
  Work that builds on a branch starts from it (a fast-forward), and says so; two branches that conflict with each
  other are not merged together in a third.
- **An experiment kept on a branch is taken up by its commit, not by its branch.** `git cherry-pick -n` of the book
  daf's one commit onto the newest code met five small conflicts where a merge of the branch would have met ten days of
  `main`; code the experiment had moved to a new file had not changed since, which `git diff -U0 <its base> HEAD --
  <file> | grep '^@@'` showed before trusting the move (2026-11-23).
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
- **A control of one view goes in that view, not in the end bar.** The bar of a Chumash's pages (the zoom's two, two
  pages, a whole page, the diacritics, shnayim mikra, the daf, then the panes' five) is full at a window 900 pixels
  tall: one more button pushed the commentaries' button out of the window, with no sign of it (2026-11-23, the book
  daf's style, which became a button at the pages' corner). Count the bar's buttons in the fullest state before adding
  one, and look at the bar's bottom in the screenshot.
- **A binding's internal API, for a bench only, by reflection.** The PDFium binding keeps its reader's constructor and
  its drawing of a page internal (`internal` holds across modules, and Kotlin 2 does not let it be suppressed): a
  test reaches them by their JVM names, a suspend one through `suspendCoroutineUninterceptedOrReturn`
  (`PdfOpenBench`, 2026-11-13). Never in the app's own code.
- **A parameter renamed to a local's name is shadowed by it.** `PdfBookReader`'s `alone` parameter (a tab of its own)
  met its local `alone` (a book added by itself): the local was passed on, and a book by itself got the tab's bar. Name
  them apart (`inTab`), and look for the name before a rename (2026-11-13).
- **The PDF binding's `PdfPage` is slow by design for a scan** (docs/scan-speed, 2026-11-14): it waits 100 ms after its
  size is known, draws a preview at a quarter of the width (which costs 40 to 78% of the page, as PDFium decodes the
  whole scanned image either way) and keeps none of it. PDFium keeps nothing of a closed page, and every call runs on
  one thread, in the order sent: `prefetch` of the next rows goes before the page in view. A view that holds its
  `PdfReaderState` in its own composition reads and opens the whole file again at every switch. Speed comes from
  drawing the page yourself (an image ready in memory shows in the next frame), not from the binding's knobs.
- **A Compose scene in a bench closes each frame's snapshot:** `ImageComposeScene.render()` returns a new native image
  (5 MB at 1440x900); left to the garbage collector, a loop of frames was killed for memory (2026-11-14).
- **A Zayit test that builds a Zayit class breaks with a new constructor parameter** (`PostSelectLineTest` builds
  `BookContentViewModel` by hand): add the argument there, a relaxed `mockk`, and write the test into the map.
- **An item that grows is measured at its new size only in a frame to come.** `scrollToItem(index, offset)` into an
  item whose content was just set (a part laid out in place of its placeholder) runs on, before then, through the
  items after it: the book daf's experiment landed four chapters on. Wait until `layoutInfo.visibleItemsInfo` gives
  the item its new size (2026-11-13).
- **A lazy list's item is composed whole.** The list is lazy by item, not inside one: the book daf's item held all a
  unit's pages, and a chapter with many commentators is hundreds of them, each some hundred and fifty `BasicText`s.
  Composed and measured together on the main thread they made the testers' build stop answering ("לא מגיב", the
  user's screenshot of 2026-11-23), and with all the commentators checked the process grew to 6 GB here until the
  system ended it. An item whose content grows with the data composes the part in view alone (`UnitRows`: an item
  as tall as all its rows, those near the view placed where the others would leave them), and lays a page out when
  it comes, off the UI thread. Try a new view with the most the user can ask of it (every commentator checked, the
  longest chapter), not only with the example's three.
- **`runSuspendCatching { … }.getOrNull()` hides the failure it catches.** A unit that could not be laid out stayed a
  spinner, or the pages before it, with nothing said: give the failure to `warnln` before dropping it.
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
- **The tab strip's first tab is at its left end, from the right as from the left.** A window's tabs (`group.ids`,
  `TabsState.tabs`) start beside the new tab's button: in RTL the strip reverses the list and lays it out from the
  right, so index 0 lands on the left. The split view first drew its halves the other way round from the strip
  (2026-11-19); `TabKeys` reads the order the same way.
- **A click on a row in a pane takes the keyboard's focus**, whether the row asked for it or not: after a click in the
  contents, the arrows go to the contents and not to the text. The tree's keys build on that (`TreeKeys`): Escape and
  Enter on a leaf give the focus back with `BookContentEvent.FocusText`.
- **The structured-coroutines plugin refuses a `launch` on a scope field** unless it is marked `@StructuredScope`
  (`TreeKeys`, `PassageReading`), at compile time (`SCOPE_003`).
- **The tab beside the selected one is shown, not selected.** In a split (`TabSplits`) the other half is measured and
  drawn, but `LocalTabSelected` is false there: the PDF book and the Gemara scan, which draw their pages only in the
  selected tab, left that half blank (2026-11-19). Ask `LocalTabShown` for what is on screen, and `LocalTabSelected`
  for what has the keys and the panes.
- **A composable that many screens use reads `LocalAppGraph` only where it needs it.** A UI test composes it without
  a graph and fails with "No AppGraph provided" (`BookTocViewAutoCenterUiTest`, when `rememberTreeKeys` read it in
  every tree): it reads it only inside a pane that asks for the focus (`LocalPaneFocusKey`, 2026-11-19).
- **A book opened in a tab of the home page gets no Ctrl/Cmd+F** when its destination stays the home page's: Zayit's
  window keys ask for a find only in a tab whose destination is a book's (`hasFindInPage`). A book opened from the
  home page's search bar becomes a book's tab and has it (2026-11-18, the PDF find).
- **Rows moved by the focus scroll in with the scroll's animation; the app's lists jump to theirs.** In the home's
  dropdowns, a held arrow made the list glide behind the keys and the hover tint (animated for the pointer) left a
  fading trail: "מרגישה אחרת בגלילה רציפה משאר האפליקציה" (2026-11-20). `revealAtOnce` answers the rows' bring-into-view
  requests in a `BringIntoViewModifierNode` inside the scroll, and the row the keys mark is tinted at once
  (`keyMarkedBackground`). Compose 1.12 deprecates both `BringIntoViewSpec.scrollAnimationSpec` ("no longer supported")
  and `onFocusedBoundsChanged`: the node is the way left.
- **A popup takes the keys only through a node in focus.** The home's dropdowns (`DropdownButton`) answered no arrow
  until their popup's box was `focusable()` with a `FocusRequester` asked for in a `LaunchedEffect` (request 69 of
  stage 3, 2026-11-19); Jewel's `PopupMenu` does it by itself, which is why a tab's menu became one.
- **A piece of Zayit's file opened for Pardes takes its types along.** `SuggestionRow` made `internal` for the study
  plan's field failed with `EXPOSED_PARAMETER_TYPE`: its `actions` are a `List<RowAction>`, a private class, which had
  to be opened too (2026-11-19). Count each word changed in the map's entry.
- **A UI test launches through the composition too:** `SCOPE_003` refuses a `scope.launch` on a scope taken out of
  `setContent`; drive the change with a state the test sets and a `LaunchedEffect` inside `setContent` (request 60 of
  stage 3).
- **A PDF's page size is nullable** (`PdfDocument.pageSize(i)`, a page that cannot be read): `?.let` it, as the daf's
  whole-page fit does (request 63 of stage 3).

- **`scrollToItem` puts its item at the top.** Zayit's `revealItem` used it for a row not in view, so a step down from
  the row at the bottom (whose next row starts exactly at the view's end, not in `visibleItemsInfo`) was a page's jump,
  and a step up a row's: "למעלה אחד אחד ולמטה מחליף הרבה בבת אחת" (requests 75 and 76 of the list after the public
  release, 2026-11-22). The reader's selected line, the search's results, the find pane and the bar's lists each had a
  way of their own, all with the same jump or an animation. A row below the view comes in at the bottom:
  `scrollToItem`, then back by the room left under it, the layout info current at once (`forceRemeasure`).
- **A zoom kept as a share of the view changes with every pane.** The daf's zoom times the view's width shrank the
  pages as a side pane or the commentaries opened (request 73, 2026-11-22): a zoom is a size (`ZOOM_ONE_WIDTH`), and a
  fit ("עמוד שלם", the view's width) a zoom set once, from the fit the view tells (`PageFits`, by the tab's id).
  A size alone is not the answer either: a pane then cut the page ("הגודל כן יקטן אבל רק כמה שצריך", request 79,
  2026-11-23). The pages are shown at the size, no wider than the view, unless the size was set wider than the view
  it was set in (`daf_page_zoom_wide`, decided where the zoom is set, by the tab's view, never by a view that merely
  narrowed); and every step, key, button, pinch, starts from the size shown, or a step beside a pane jumps past it.
- **An icon is what is customary first.** Asked for a new icon of the pages' fits, a design of Pardes's own came
  first, and the user asked "לגבי האייקנים - תבדוק מה מקובל" (request 81, 2026-11-23). The icons module is mostly
  Material Symbols (composables.com's Kotlin of them, the 960 viewport): take the icon from there when it has one
  (`fit_page_height`, `fit_page_width`), with the Apache License's notice, look at Fluent and the PDF readers beside it,
  and draw only what none has (two thirds of a page) after the one it is nearest to.
- **A jump to a row and back starts the rows between again.** `scrollToItem` disposes the rows it leaves, and a row
  that reads its text once on screen (the find pane's, `produceState`) reads it again, empty first, and grows: after a
  page down the rows above the row revealed grew and pushed it below the view, round after round (request 78,
  2026-11-23). A row a few rows below is scrolled to through them (`scrollInFromBelow`), and the row revealed is kept
  in view while the rows change, until the reader scrolls (`revealSettled`). The reader's scroll is told by the list
  no longer being where the reveal left it, not by `isScrollInProgress`: a scroll that suspends nowhere (a trackpad's
  `dispatchRawDelta`, the scrollbar, the reveal's own) sets it and clears it before any `snapshotFlow` looks.
- **A UI test of rows that change later must let the time pass.** `waitForIdle` runs no `delay` in an effect: the
  first test of request 78 passed without the fix, the rows never growing. `mainClock.advanceTimeBy` lets them; and a
  new test is run once against the code it fixes, to see it fail (2026-11-23).
- **One marking for every popup** (`PopupMarking.kt`, request 74): a popup whose field keeps the focus marks by index,
  its list told the pointer by `markOnPointer` (a `PointerInputModifierNode` on the `LazyColumn`, the positions the
  list's own offsets); a popup with no field marks by focus (`rememberHoverFocus`); Jewel's menus mark one item inside
  `SingleMarkMenus`. A popup's pointer `Enter` is no move, and its first `Move` only noted: a popup opened under a
  still pointer marks nothing. A modifier factory cannot `rememberUpdatedState` its lambda: make it a node whose
  `update` takes the new one, or the lambda reads stale values (`filtered` in the breadcrumb's popup).

## Data and settings

- **The user DB has no migrations** (`Schema.create`, every table `CREATE TABLE IF NOT EXISTS`): what a released
  table lacks goes in a table of its own, keyed by the first's id, which a user DB of before simply gets
  (`study_plan_more`, request 64 of stage 3). A column added to such a table before its release still meets the
  container's own user DB made by an earlier run: `ALTER TABLE` it there (or drop the table) before trying the app.

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
  final entry. `git stash push -- <paths>` sets whole files aside. A hunk of the map staged alone
  (`git diff -U0 | git apply --cached --unidiff-zero`) lands at the working tree's line number: an entry added after
  hunks left unstaged went into the middle of another section's paragraph (2026-11-19, put back by the next commit),
  and `git stash pop` after it wrote the entry twice. The check counts entries, not where they sit: read
  `git diff --cached` of the map before the commit.
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
- **A book of Pardes's own opens by more than one road.** The reading screen's view model opens a book id at its
  creation (`loadBookById`), and a tab already open takes a new one by `openDestination` → `OpenBookById`; each reads
  the library alone (`getBookCore`). A PDF book's id needs the fallback on both (`?: pdfBooks.asBook`): without the
  second, a PDF book picked in a tab's search bar named the tab by its id and left it on the home page (2026-11-15).
- **What a binding lacks, its native library may have.** Nucleus's PDFium binding reads no bookmarks, but the
  libpdfium it ships and loads exports all of PDFium's C API: `PdfOutline.kt` calls `fpdf_doc.h` through the Foreign
  Function API (final in JDK 25), in the library the binding loaded (`SymbolLookup.loaderLookup()`). Its pitfalls
  (2026-11-14):
  - the binding's loader and its PDFium thread are `internal`: call PDFium once the binding has opened a document,
    which also readies it (`FPDF_InitLibrary`);
  - C's `long` is 32 bits on Windows: take `Linker.nativeLinker().canonicalLayouts()["long"]` and call with its type;
  - `invokeExact` wants the exact types, which Kotlin writes from the cast (`as Int` is `I`, a statement `V`; `javap`
    on the class shows them);
  - `FPDFBookmark_GetDest` takes the action's destination when the bookmark has none, another file's too: check
    `FPDFAction_GetType`;
  - the native image calls only the shapes in `reachability-metadata.json`'s `foreign` downcalls: check each new one
    is there;
  - to try PDFium from plain Java in the container, run it with `LANG=C.UTF-8`, or a Hebrew path in its arguments is
    mangled;
  - the binding's `openPdfDocument` and `PdfDocument` are internal too: PDFium is loaded by its public
    `rememberPdfReaderState()` opening an empty PDF (`rememberPdfiumLoaded`), and a test reaches the reader's
    constructor by reflection;
  - a `limitedParallelism(1)` dispatcher runs one at a time but not on one thread: a confined `Arena` lives inside one
    non-suspending block, never across a suspension.
- **The structured-coroutines compiler plugin crashes on `withContext(NonCancellable …)` in a `finally`**
  (`NoSuchMethodError: FirResolvedQualifier.getClassId()`, the file's `try` as its place, 2026-11-15): clean up through
  `currentCoroutineContext().job.invokeOnCompletion { … }` launching on a scope of the class instead (`DafScanPages`).
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
   `android-site.yml` publishes the changelog from `main`, and the section belongs to the public release. `git revert`
   writes its commit as the container's own identity: run it with `-c user.name=efrul -c user.email=…`, or amend it
   with `--reset-author` (a plain `--amend` keeps the author), and check `git log -1 --format='%an | %cn'` before the
   push (2026-11-13: one went out as Claude, and was amended and pushed again). `git revert` takes no `-q`: given it,
   it prints its usage and makes no commit, and an `--amend` after it renames the commit before, the changelog's own
   (2026-11-19: the push was refused, and the branch went back to the pushed commit). Read the revert's line in
   `git log -1` before amending it.

A session cannot delete a release. Tell the user which pre-releases can go once tried: Pardes for Android 0.3.0 to
0.4.1 reads only the 30 newest releases.

### The public release, and the Apple Silicon Mac

The public release is the same run on `main` after the stage's pull request merges, without `quick`, with the
changelog's section kept. Its slow build is the Apple Silicon Mac's native image, on a GitHub runner of 7 GB where
GraalVM spends most of its time in the garbage collector: 2 h 06 for 1.0.7, 2 h 54 for 1.0.9, and for 1.0.10's code
anywhere from 2 h 01 to 3 h 25, or an `OutOfMemoryError` after 3 h 21 (CI of efrul/Pardes#47, 2026-11-22). The others
take under 45 minutes. A build had 180 minutes until 1.0.10 (efrul/Pardes#47 gives 300). When that Mac cannot make
it, `without_mac_arm64` publishes the rest at once, without `latest-mac.yml` (installed Macs keep their version), and
`add_mac_arm64: desktop-<x.y.z>-<run>` builds that Mac from the release's own commit and adds it with the Macs'
feed; the workflow's definition comes from the ref the run is dispatched on (1.0.10's from the branch with the 300
minutes). Before a release, look at the time CI's Mac build took on the same code.

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
- **A new control is drawn as the ones it stands beside** (stage 3, 2026-11-18). The search modes, first Jewel's grey
  segmented control, were redone as one pill as high as the search bar, the mode chosen marked as the category tabs
  mark theirs ("תדייק עיצובית"); the tree's keyboard row, first a gold ring, is shown as the contents' search shows
  its match ("הפוקוס יראה כמו במקומות שהוא קיים, כמו בתוכן העניינים לאחר חיפוש בו"). Look for the control's look in
  the app before drawing a new one. Then the pill went too: the search by meaning became the in-book find's own
  toggle of its smart find, at the search bar's end, and the exact search was dropped ("תוריד את מדויק, והעיצוב
  יתבסס על המצבים הקיימים בחיפוש בתוך ספר", 2026-11-19), on by default ("ברירית מחדל תהיה עם סמנטי"). A control
  the app has already beats a new one, and fewer choices beat a finer one.
- **What opens is ready for the keys.** The book tree and the contents, opened by their button or shortcut, took the
  keys only once clicked ("לאחר פתחיחת העץ אין עדין פוקוס עליו עד שלוחצים עליו", 2026-11-19): a pane opened to be
  used takes the focus.
- **A pane of the reader goes where its kind is.** The find's results, a list to go through as the contents is, moved
  to the text's right beside the contents ("יהיה בימין").
- **One row marked, never two alike.** The home's dropdowns tinted the keys' row and the pointer's alike ("עכשיו שתי
  שורות מסומנות באותו צבע. מה התקן?", 2026-11-20). The standard, as in the OS's menus and IntelliJ's popups: the
  pointer's move marks the row under it, the arrows go on from the row marked, and a still pointer's row is not
  marked again until it moves (`DropdownMarking`, every popup's `PopupMarking` since request 74). Zayit's breadcrumb
  popup took the other accepted way, two tints (the keys' 0.16, the pointer's 0.08), until request 74 made every popup
  mark one row (2026-11-22). Jewel 0.41's `PopupMenu` (the Tools menu, a tab's) moves its focus to the row
  hovered, yet keeps the hover tint, in the same colour, on the row under a still pointer after an arrow: its
  `FocusableComponentState.chooseValue` puts hovered before focused. Jewel's Swing compatibility mode drops the hover
  and pressed tints, so that only focus marks; `LocalSwingCompatMode` is internal, so `SingleMarkPopupMenu` enters the
  app's theme again with `IntUiTheme(…, swingCompatMode = true)`, as Zayit's dialogs enter it.
- **A list's arrows round its ends in every popup alike** ("היא קיימת בחלקם המעגלים, תוודא שבכולם", 2026-11-20).
  Jewel's menus rounded theirs, the home's dropdowns rounded with a blank step (Compose's focus search gives the
  popup's frame the focus between the last row and the first: `frameFocused`, and move again), and the others stopped
  at the ends; the bar's list also stepped its scroll only at the rows in view, which a jump to the other end missed.
  Jewel's combo boxes stay as they are: their arrows change the value as they go, and IntelliJ's combo boxes do not
  round either.
- **IntelliJ's own code is at hand when its standard is asked for.** Its sources come as `-sources.jar` from
  `www.jetbrains.com/intellij-repository/releases` (`com/jetbrains/intellij/platform/ui-tree`, `util-ui`, `core-ui`),
  and single files from `raw.githubusercontent.com/JetBrains/intellij-community` (github.com itself answers 403 here).
  Read there on 2026-11-20: `TreeAction` (in on a leaf or an open folder goes to the next row; out from the top level
  goes to the nearest open folder above, else the first row; a page is the view less four rows) and
  `ListPopupImpl.isMouseMoved` (a popup notes the pointer's first place only, and a move is a new place).
- **A thing is named as the user names it.** The setting that hides the Home's big lettering was titled "הצג את השם
  הגדול בדף הבית"; the user asked to fix the wording ("תתקן נוסח", 2026-11-20), and it says their own words of the
  request now, "הכיתוב הגדול של פרדס".
- **What the app can tell by itself, it does not ask.** The study plan's editor had a "ספר | קטגוריה" choice over two
  fields; the user asked for one field that tells a book from a category as the search's bar does, with the bar's
  code ("זה יהיה אוטמטי כמו בחיפוש הרגיל, הוא יזהה ספר או קטגוריה, אפשר קוד משותף", 2026-11-19). It now finds both
  with the bar's lookup and draws them with its rows, a category in its base books alone too.

- **The same behaviour everywhere, by the same code** ("למה זה לא קוד משותף לכולם?", 2026-11-22). Each popup had kept
  its own rows and tints, and each fix had gone into each popup in place, so the next popup missed it. A behaviour
  asked of one list or menu is built once, in a shared file, and every popup reaches it.
Show a screenshot of a choice of design before building on it, and ask of each new control: does it behave as the
one beside it does?

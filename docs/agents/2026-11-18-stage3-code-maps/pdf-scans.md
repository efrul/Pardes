# Code map: PDF books and the Talmud scan view

- **For:** stage 3 of Pardes for desktop, requests 42, 43, 44, 45, 48, 49, 51, 52 and 60 (`docs/DESKTOP_STAGE3_PLAN.md`).
- **When:** 2026-11-18. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `40df456` (main of that day). The line numbers are that commit's.
- **By:** an Explore agent of Claude Code, one of three run side by side. The report is as it came back; the brief it got is at the end.
- **Checked by hand afterwards:** nothing beyond what the plan cites; the SeforimLibrary submodule was empty when it ran, so its models were not read.

## The report

I've mapped all eight requests to the code below; nothing was edited.

**Before you read:**
- The `SeforimLibrary` submodule is empty in this checkout, so the core models (`Book` defaults, `AltTocEntry`, `AltTocStructure`) couldn't be read.
- In the Hebrew right-to-left layout, the left bar is `EndVerticalBar`; the plan calls it "הסרגל השמאלי" (plan:645).
- In this version every tab stays composed (`TabsContent.kt:98-106`). CLAUDE.md's "only 3 tabs kept composed" is out of date, and this matters for H.

**Files cited** (by name below):
- `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/` — `DafScanView.kt`, `DafScans.kt`, `DafScanPages.kt`, `ScanPageList.kt`, `ScanCutFolder.kt`, `DafView.kt`, `DafPage.kt`, `DafMarksButton.kt`, `DafScanButton.kt`, `DafLayout.kt`
- `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/pdf/` — `PdfPageList.kt`, `PdfBookView.kt`, `PdfBookToc.kt`, `PdfBookReader.kt`, `PdfBookStore.kt`, `PdfTocSearch.kt`, `PdfOutline.kt`, `PdfiumCalls.kt`, `PageSpreadsButton.kt`
- `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/` — `BookContentViewModel.kt`, `BookContentScreen.kt`, `BookContentEvent.kt`, `state/BookContentState.kt`, `state/BookContentStateManager.kt`, `usecases/ContentUseCase.kt`, `usecases/AltTocUseCase.kt`, `usecases/TocSearch.kt`, `usecases/TocUseCase.kt`, `ui/components/VerticalBars.kt`, `ui/panels/booktoc/BookTocPanel.kt`, `ui/panels/booktoc/BookTocView.kt`, `ui/panels/bookcontent/BookContentPanel.kt`, `ui/panels/bookcontent/views/BookContentView.kt`
- `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/` — `daf/DafScansCard.kt`, `daf/DafSettingsScreen.kt`, `semantic/SemanticPackPrompt.kt`, `semantic/SemanticPackCard.kt`, `downloads/StartOffers.kt`, `feedback/FeedbackConsentDialog.kt`, `feedback/OtherAppSearches.kt`
- `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/` — `settings/AppSettings.kt`, `settings/Setting.kt`, `presentation/theme/ThemeUtils.kt`, `presentation/tabs/TabsContent.kt`, `presentation/window/WindowBody.kt`
- `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/reference/ReferenceParser.kt`
- `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/modules/AppCoreBindings.kt`, `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/session/TabPersistedStateStore.kt`
- `/home/user/Pardes/texteffects/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/texteffects/TextZoom.kt`
- `/home/user/Pardes/htmlparser/src/jvmMain/kotlin/io/github/kdroidfilter/seforim/htmlparser/SkiaHtmlImageBuilder.kt`
- `/home/user/Pardes/SeforimApp/src/commonMain/composeResources/values/strings.xml`, `/home/user/Pardes/SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/PdfBooks.sq`
- `/home/user/Pardes/docs/DESKTOP_STAGE2_PLAN.md` ("plan"), `/home/user/Pardes/docs/scan-speed/README.md` ("README")

---

## A. Dark mode for PDF and scans

**How the app knows it is dark**
- `ThemeUtils.isDarkTheme()` (`ThemeUtils.kt:44-53`) reads the theme setting (Light / Dark / System, System via `isSystemInDarkMode()`). It feeds `buildThemeDefinition()` (`:76-101`).
- Dark palette: `text.normal` 0xFFBCBEC4 (`:137`), `panelBackground` 0xFF1E1F22 (`:143`).
- Inside composables the usual check is `JewelTheme.isDark`. There is already a dark-mode precedent for images in books (`BookContentView.kt:693-697`):
  ```kotlin
  val isDarkTheme = JewelTheme.isDark
  … SkiaHtmlImageBuilder.build { if (isDarkTheme) SkiaHtmlImageBuilder.InvertColorFilter else null }
  ```
  The same pattern is in `LineCommentsView.kt:1247-1250` and `LineTargumView.kt:787-790`.
- A ready-made invert matrix exists: `SkiaHtmlImageBuilder.InvertColorFilter` (`SkiaHtmlImageBuilder.kt:33-60`).
- Nothing under `features/pdf` or `features/bookcontent/daf` checks dark mode today (grep found no `isDark` or `ColorFilter`).

**Where pages are drawn, and where a filter would go**
1. **Scans (Pardes draws them from the PNGs):** `ScanPage` in `ScanPageList.kt:60-85`.
   - The paper is hard-coded: `Box(...background(Color.White))` (`:71`).
   - The page is a plain `Image(bitmap, contentScale = FillBounds, filterQuality = Medium)` (`:76-82`). Add `colorFilter = if (dark) … else null` there and change the paper colour at `:71`.
   - Filtering at draw time keeps the decoded-image cache theme-independent. That cache is `shown` (`DafScanPages.kt:96-99`), filled by `prepare` (`:149-157`) and by the scan button (`DafScanButton.kt:62-63`). Images are decoded as 8-bit gray (`DafScanPages.kt:305-313`).
2. **PDF books, and the scan fallback, drawn by Nucleus:** `PdfPage(reader, page, Modifier.width(width).drawnAtMost(MOST_DRAWN_PX), selectableText)` (`PdfPageList.kt:125-127`).
   - This is Nucleus's own composable; the only hook Pardes has is the `Modifier`.
   - Wrap it in a layer filter, either `drawWithContent { saveLayer(Paint(colorFilter)); drawContent(); restore() }` or `graphicsLayer { renderEffect = … }`.
   - That one spot covers `PdfBookView` (`PdfBookView.kt:191`) and the scan fallback `BindingScan` (`DafScanView.kt:287-289`).
   - The binding's text-selection highlight would be inverted too.
3. Page titles and the PDF bar already use theme colours (`PdfPageList.kt:204`, `PdfBookView.kt:229-231`).

**Daf colours to copy**
- The daf view has no dark palette of its own; it uses the theme:
  - ink is `JewelTheme.globalColors.text.normal` and selection is `outlines.focused` (`DafView.kt:385-386`), passed to `DafAmudText(ink)` (`DafPage.kt:344-349`, `:479-487`);
  - amud titles are `text.info` (`DafView.kt:663-669`);
  - the paper is the reading card's `panelBackground` (`BookContentPanel.kt:226-230`, `:515-524`).
- To make a scan look like the daf in dark mode, map black to `text.normal` and white to `panelBackground` with a tint matrix, per channel `c' = ink + (paper − ink)·c/255`, instead of a pure invert.

## B. Scan file matching, page-to-amud mapping, settings

**Today's matching** has no normalisation and no prefix handling:
- `DafScans.of(tractate)` (`DafScans.kt:125-128`): `folder.value?.let { OtzariaScans.findIn(tractate, File(it)) } ?: OtzariaScans.find(tractate, bavli)`.
- `tractate` is `book.title` (`DafScanView.kt:147-149`, `DafScanButton.kt:56-58`).
- User folder, `findIn` (`DafScans.kt:57-69`): `val name = "$tractate.pdf"`, then a breadth-first search up to 3 folders down (`FOLDER_DEPTH`, `:94`) for `File(folder, name).takeIf { it.isFile }`.
- Otzaria, `find` (`:72-86`): the same exact name in each Bavli folder or its direct subfolders.
  - The Bavli folders come from `bavliFolders` (`:38-51`): `library_path.txt`, `library_database_path.txt`, `אוצריא`, `books`, plus `/תלמוד בבלי`.
  - The Otzaria data folder comes from `otzariaDataRoots()` (`OtherAppSearches.kt:313-323`).
- The assumption is written down in the KDoc (`DafScans.kt:25-31`: "Page i … is the tractate's amud i from ב. … a tractate's title is its file's name") and in the user strings (`strings.xml:1195`, `:1200`).
- So "18 - עבודה זרה הוריות עדיות מורחב - מחובר.pdf" can never match.

**What would need to change to match that file**
1. **Matching:** in `findIn`, list `*.pdf` files and compare tokens instead of building one exact file name.
   - Normalise with `ReferenceParser.normalizeName` (`ReferenceParser.kt:147-153`: strips nikud and quotes, turns `,.:;־-` into spaces). The daf layouts already use it for tractate keys (`DafLayout.kt:211`).
   - Drop leading number tokens, then require the tractate's tokens as a contiguous run of whole words, so "בבא קמא" can't match "בבא מציעא". Keep the exact name as the first choice.
   - `of()` runs on every scan-view entry and in every tab that shows the scan button, so the folder listing is worth caching.
2. **Several tractates in one file:** the name can't say where each one starts. Two possible sources:
   - the file's bookmarks via `PdfOutlines.read(file)` (`PdfOutline.kt:30-34`), looking for a bookmark whose title names the tractate (its page is the start);
   - otherwise a per-tractate setting for which PDF page is daf ב.
3. **Return type:** `of()` returns only `File?`. It would carry `(file, firstPage)` into `ScanLoad.Found` (`DafScanView.kt:85-93`, `:148`).

**Where page i is mapped to an amud**
- Only in `DafScanView.kt:70-77`. `DafScanPages`, `ScanPageList` and `ScanCutFolder` work in raw PDF page indices only.
  ```kotlin
  internal fun scanPageOf(amud: String): Int? = amudIndex(amud).takeIf { it != Int.MAX_VALUE }?.minus(FIRST_AMUD)?.takeIf { it >= 0 }
  internal fun amudOfScanPage(page: Int): String = "${page / 2 + 2}${if (page % 2 == 0) 'a' else 'b'}"
  ```
- Call sites: `control.toLine` (`:139`), `rememberPlaced` (`:252`), titles in `ScanList` (`:333`), `FollowTheTab` (`:358`), `TellThePlace` (`:385`), `DafScanButton.kt:62`.
- The two-page layout also assumes page 0 is ב. (first page alone on the left, each odd page on the right; `PdfPageList.kt:72-92`, tested in `PdfPageListTest.kt:25-34`).

**Where an offset would plug in**
- Put `first` into `ScanPlace` (`DafScanView.kt:99-106`) and renumber the list so it only sees the tractate's own pages:
  - `ScanList(pages = count)` (`:297-335`);
  - `ScanPageList` passes `first + page` to `ScanPage` and to `prepare` (`ScanPageList.kt:51-56`).
- Renumbering also keeps the two-page pairing right. With an odd offset it would otherwise pair amud a with amud b of the same daf.
- The cut is per file (`ScanCutFolder.kt:22`, keyed by file name plus a path hash), so a joined file would be cut once for all its tractates.
- The Nucleus fallback (`PdfPageList(reader…)`, which iterates `reader.pageCount`, `PdfPageList.kt:113`) needs the same offset.

**Settings card and persistence**
- The "סריקות הגמרא" card is `DafScansCard()` (`DafScansCard.kt:56-95`):
  - folder path `:76-81`, choose and remove buttons `:82-89`;
  - Otzaria folders found `:90-92`;
  - cut pages size and delete `:93`, `:97-120`.
- It is placed in `DafSettingsScreen` (`DafSettingsScreen.kt:17-28`); strings are at `strings.xml:1199-1209`.
- `DafSetting.kt` is the daf typesetting code, not settings.
- How the folder is persisted:
  - `object DafScanSettings : SettingsGroup("daf_scans") { val folder by json<String?>(null) }` (`DafScans.kt:19-23`), stored under the key `daf_scans.folder` (`Setting.kt:39`);
  - read and written through `appSettings.flow(...)` and `appSettings[...] = ...` (`DafScans.kt:117-122`, `AppSettings.kt:551-561`, `:571-575`);
  - the store is `com.russhwolf.settings.Settings` from `Brand.settings()` (`AppCoreBindings.kt:100`), backed by Java Preferences (`AppSettings.kt:3`, `:506`).
- It is not a portable key (`AppSettings.kt:846-913`), so the backup doesn't carry it, on purpose ("A folder of this computer", `DafScans.kt:21`).
- Data that should travel with backups goes in the user DB instead, e.g. `pdf_book` (`PdfBooks.sq:5-13`, `PdfBookStore.kt:87-101`). The old per-tractate linked-file table was dropped in item 48 (plan:671).
- A per-tractate offset could be `json<Map<String, Int>>` in `DafScanSettings`, or a new `.sq` table if it should be backed up.

## C. Pinch and Ctrl+wheel zoom

**Gesture handling:** a single helper, `rememberTextZoom` (`TextZoom.kt:52-268`), is used by all three views. There is no `detectTransformGestures` anywhere and no Nucleus zoom.
- Ctrl/Cmd + wheel: `onPointerEvent(Scroll)` → `zoomScroll` (`:191-210`, `:214`).
- macOS trackpad swipe arriving as a pan, with Ctrl: `:215-224`.
- Trackpad pinch: `ScaleChange` / `ScaleEnd` (`:225-232`). Nucleus sends these "at the cursor", but only `scaleFactor` is read.
- Touchscreen two-finger zoom: `:233-266`. The centroid is computed for the distance only.
- The pointer position is never used, so **no view zooms around the pointer**.
- During a gesture the new size is shown immediately and committed 120 ms after the last event (`:140-147`). Button and key zoom animates over 300 ms (`:182-188`).

**Per view**
- **PDF:** `PdfPages` (`PdfBookView.kt:170-194`) puts the zoom on its container; page width is `base * (pinch.textSize / PERCENT)` (`:190-191`). It saves to the daf's zoom setting (`AppSettings.zoomDaf`, `:291-300`).
  - Scroll is not adjusted at all: `PageRows` (`PdfPageList.kt:135-174`) keeps the list's first index and pixel offset.
  - Its horizontal scroll is a private `rememberScrollState()` (`:151`) that nothing corrects.
- **Scan:** the same daf zoom from `BookContentView` (`:210-221`, applied at `:1068-1075`, passed at `:1082`), then `ScanList` width `base * zoom` (`DafScanView.kt:330-334`) and the same `PageRows`. No adjustment.
- **Daf:** the same zoom (`BookContentView.kt:1094`). `DafView` keeps the point of the amud at the **top** of the view fixed via `requestScrollToItem` in a `SideEffect` (`DafView.kt:481-496`, helper `DafScale` `:227-237`).
  - So it is top-anchored, vertically only. Its horizontal scroll (`:713-718`) is not adjusted.
- **Buttons and keys:** `zoomIn` / `zoomOut(tabId)` step through the zoom levels when the tab shows a daf, scan or PDF (`AppSettings.kt:302-321`, levels `:921-926`). Tabs register via `dafShown` (`BookContentView.kt:226-229`, `PdfBookView.kt:106-109`). Buttons: `VerticalBars.kt:341-382`.

**Where a change would go**
- `TextZoom` has to report the pivot point: `event.changes.first().position` in the Scroll and ScaleChange handlers, and the touch centroid. `texteffects` is Zayit's module and is shared with the text view, so this needs a change-map entry.
- `PageRows`: hoist its horizontal scroll state, find the item under the pivot from `layoutInfo.visibleItemsInfo`, and re-request the scroll position after the width changes, as `DafView`'s `SideEffect` already does for the top. The 36 dp titles and 8 dp gaps don't scale with zoom (`PdfPageList.kt:67-70`).
- In `DafView`, use the pivot instead of the top, and correct the horizontal scroll too.

## D. TOC sync and "both divisions"

**PDF today**
- The page at the top is reported by `TellPlace` (`PdfBookView.kt:270-302`) with no debounce; only the saved reading position is debounced:
  ```kotlin
  snapshotFlow { placeNow() }.distinctUntilChanged().onEach { currentOnTop(it.page) }
  ```
  `onTop` calls `store.showPages(tabId, PdfPagesShown(pages, page))` (`PdfBookView.kt:116-125`, `PdfBookStore.kt:112-128`).
- It fires on any movement: wheel, Page Up/Down (`turnPage` uses `animateScrollBy`, `PdfPageList.kt:258-260`), and TOC jumps.
- Keys in the PDF view: only Page Up/Down and Ctrl+Home/End (`pageListKey`, `PdfPageList.kt:239-255`, wired at `PdfBookView.kt:187`). There is **no arrow-key handling**: the text's arrow handling (`BookContentView.kt:921-945`) isn't on the PDF branch (`BookContentPanel.kt:244-248`).
- `PdfBookToc` collects `store.shown` (`PdfBookToc.kt:39-41`):
  - with bookmarks: `selected = toc.entryAt(page)` (`:88`, `:140-144`) and the path to it is expanded (`:98`);
  - without bookmarks: `selectedTocEntryId = pages.page + 1L` (`:76`).
- **Auto-scroll: yes**, inherited from Zayit's `BookTocView` (`BookTocView.kt:106-124`).
  - Whenever the selected id changes (`didAutoCenter` is keyed on it, `:108`), it calls `listState.scrollToItem(idx, 0)` (`:120`), which puts the entry at the top of the list.
  - It is skipped once after a click inside the panel (`skipNextAutoCenter`, `:107`, `:141-143`). If you click the page that is already current, that skip flag stays set and swallows the next auto-scroll.

**Regular books (Zayit)**
- The TOC follows the **selected line**, not scrolling:
  - `ContentUseCase.selectLine` sets `toc.selectedEntryId` and the breadcrumb (`:139-147`), as does `loadAndSelectLine` (`:223-231`);
  - arrows select lines (`BookContentViewModel.kt:611-631`);
  - the alternative TOC follows via `selectAltEntryForLine` (`BookContentViewModel.kt:1086-1088`, `AltTocUseCase.kt:182-196`).
- The same `BookTocView` auto-scroll applies.
- In the Shas daf and scan views, scrolling only updates the tab's position (`dafAt` → ContentScrolled, `BookContentView.kt:1020-1023`, `:1087`, `:1110`). So their TOC does not follow scrolling, unlike PDFs.

**Two divisions in Zayit**
- `BookTocPanel.kt:86-121`: `val showAlt = selectedBook.hasAltStructures && !searching`. The regular tree goes in the `PaneSearchLayout` regular slot with `weight(1f)`, then a `HorizontalDivider`, then `AltBookTocSection(weight(1f))` (`:270-343`).
- State is `AltTocState` (`BookContentState.kt:59`), loaded by `AltTocUseCase.loadStructures` (`:23-72`). One structure is chosen by key priority daf / parasha / chapters / topic / section (`:109-120`). There is no UI to switch structures: the `AltTocStructureSelected` event (`BookContentEvent.kt:103`) has no sender.
- TOC search covers both trees: `TocUseCase.search` (`:216-243`) merges them with `withAlt`, giving alternative entries negative ids (`TocSearch.kt:139-155`). Results are drawn as two trees with a divider (`BookTocPanel.kt:231-268`).

**How a PDF could show both**
- A PDF's book is `Book(id, categoryId, sourceId = 0, title)` (`PdfBookStore.kt:84-85`), so `showAlt` is false. The whole PDF TOC lives inside the regular slot (`BookTocPanel.kt:100-108`).
- Simplest route: in `PdfBookToc.kt:55-59`, when bookmarks exist, draw `PdfOutlineView(weight 1f)`, a divider, then the pages `BookTocView(weight 1f)`, copying `BookTocPanel.kt:112-121`. Both lists already select from `pages.page`.
- Search: `PdfTocSearch.contentsOf` (`:65-76`) currently uses bookmarks or pages. Build both, merge with `withAlt` using negative ids for pages, and map back in `open` (`:49-57`).

## E. Fast-paging "stuck, nothing drawn"

**Scans (Pardes's own drawing)**
1. **One failed decode leaves the page blank for good.**
   - `image()` (`DafScanPages.kt:133-146`) treats a null `decode` as "not cut" and calls `want()`. `decode` hides every error: `runCatching`, and the result of `allocPixels` is ignored (`:305-313`, `:309`).
   - But `nextPage` only cuts pages whose stored height is 0 (`:240-244`), so that page is never cut again.
   - `ScanPage` waits on `revision.mapNotNull { image() }.first()` (`ScanPageList.kt:68-70`). Once the whole tractate is cut, `revision` never changes again, so you get a white box with a spinner forever.
   - Fast paging raises the odds of a failed decode:
     - each page decodes to a 6 MB gray bitmap (`:304`);
     - decodes are blocking `withContext(IO)` calls that keep running after the page scrolls away;
     - the same page can be decoded at once by `ScanPage`, `prepare` (`ScanPageList.kt:51-55`) and `DafScanButton.kt:59-64`, with no de-duplication;
     - Skia `Bitmap`, `Codec` and `Data` are never closed, so native memory is freed only by GC.
2. A page whose render failed is marked `FAILED = -1` (`:211-213`) and never retried (`:242`, `:244`): endless spinner.
3. **First open of an uncut tractate:** `want()` is last-in-first-out (`addFirst`, `:186-188`), and every waiting page pushes itself to the front again on each `revision` bump. So the next page cut is whichever waiting page resumed last, not necessarily the one on screen.
   - Compose's lazy-list prefetch also composes the next row's pages, which queue themselves too.
   - Entries are never removed when a page scrolls away.
   - Cutting runs at roughly 0.1–0.2 s per page (plan:743, README:159-165).
4. `prepare` uses `collect`, not `collectLatest` (`ScanPageList.kt:51-55`), so it keeps decoding for positions already passed. The in-memory cache holds 12 pages (`:96-99`, `:301`). In two-page view at low zoom, visible pages plus two rows ahead can exceed 12, causing re-decodes.
5. Pressing "delete pages" in settings while a scan is open empties `cuts` (`:170-179`), so `nextPage` returns null (`:238`) and nothing is cut until the scan is reopened.
6. Only one scan is cut at a time, and `revision` is shared by all scans. `startCutting` cancels the other scan's job (`:194-201`), so two windows showing two tractates fight each other.
7. Every scroll rest saves the tab state on the UI thread: `TellThePlace` → `onScroll` → ContentScrolled → `saveAllStates()` (`DafScanView.kt:377-389`, `BookContentView.kt:1087`, `ContentUseCase.kt:330-337`, `BookContentStateManager.kt:155-163`).

**PDF books and the scan fallback (Nucleus)**
8. **Most likely cause for the two-page case.** All Nucleus work goes through one PDFium thread in order, so visible pages wait behind prefetched ones. Each visible page also costs a 100 ms wait, a preview render and a full render (README:16-20, `:70-74`, `:153`).
   - `PdfPageList` prefetches 2 rows for every distinct first row, using `collect` (`PdfPageList.kt:116-124`), so fast paging queues renders for pages already passed.
   - Two-page view doubles both costs: 2 pages per row, plus 4 prefetches per step.
9. `drawnPx` is a key of that prefetch effect (`:116`). It restarts on every frame of a pinch or of the 300 ms animated zoom (`TextZoom.kt:182-188`), each time prefetching at a new width, while every page also re-renders at its new width.
10. The binding keeps "up to 64 MB" of drawn pages (README:75).
    - In two-page view at higher zoom, pages reach the 1600 px cap.
    - At my rough estimate of ~15 MB per page, four visible plus four prefetched pages exceed that, so visible pages get evicted and re-rendered.
11. PDFium isn't thread-safe, but Pardes calls it on its own thread (`PdfiumCalls.kt:40-47`) to read bookmarks (`PdfBookToc.kt:46-48`, `PdfTocSearch.kt:70`) right while the binding starts rendering the book. This could cause rare stalls.

## F. Nikud button

- It lives in `EndVerticalBar` (`VerticalBars.kt:87-328`), rendered by `WindowBody.kt:160`. The condition (`VerticalBars.kt:147-160`):
  ```kotlin
  if (uiState.content.dafView && selectedBook.isBavli) {
      DafMarksButton()
  } else if (bookHasDiacritics) {   // hasNekudot || hasTeamim
      DiacriticsButton(...)
  ```
- Item 33 (plan:77-82) put `DafMarksButton` (`DafMarksButton.kt:15-33`) in its place for the daf view only.
- **Scan view:** `scanView` isn't checked, so a tractate with nikud keeps the button.
- **PDF books:** whether it appears depends on SeforimLibrary's defaults for `hasNekudot` / `hasTeamim`, since the PDF's book is built with defaults (`PdfBookStore.kt:84-85`). I couldn't verify those defaults.
- **Change:** in the same branch, skip the button when `pdfIdOf(selectedBook.id) != null || (selectedBook.isBavli && uiState.content.scanView)`. `PageSpreadsButton.kt:26-29` already has that test.
- Ctrl+J still cycles nikud (`BookContentScreen.kt:992`; handled at `BookContentViewModel.kt:658-659`); gate it there too if wanted.
- `VerticalBars.kt` is a Zayit file, so the change-map entry at `ZAYIT_CHANGE_MAP.md:970` needs updating.

## G. Cutting scans as an opt-in question

**Where cutting starts today**
- `ScanPages` (`DafScanView.kt:206-241`): after `rememberPdfiumLoaded()` (`:218`, `PdfiumCalls.kt:28-37`) it calls `dafScanPages.open(file)` (`:224`).
- `open` (`DafScanPages.kt:113-124`) either reads the saved cut or calls `begin()` (`:262-285`, which deletes the scan's folder and writes `cut.json`). It then does `if (cut.heights.any { it == 0 }) startCutting(scan)` (`:122`).
- Any page not yet cut also starts cutting via `want()` → `startCutting()` (`:141`, `:181-191`).
- `ScanCutFolder` only stores files. `DafScanButton`'s look-ahead only decodes PNGs that already exist (`DafScanButton.kt:48-65`).
- A display path that never cuts already exists: `CutLoad.None` → `BindingScan`, the Nucleus reader (`DafScanView.kt:228-231`, `:258-291`). It is slower: 0.3–0.7 s per switch (plan:614-618).

**Where the opt-in would go**
- Add a setting to `DafScanSettings` (`DafScans.kt:19-23`), e.g. `val cut by json<Boolean?>(null)` where null means "not asked yet". `SettingsGroup` supports `json`, `boolean` and `enumOrNull` (`Setting.kt:43-62`).
- In `ScanPages`, before calling `open`:
  - null → show an in-place question, modelled on `NoScan` (`DafScanView.kt:169-189`) or `MissingLayout` (`DafView.kt:1158`);
  - false → `BindingScan`, or a new in-memory PDFium render with nothing saved to disk;
  - true → today's path.
- `open()` must also not start cutting when the setting is off.

**Existing one-time questions in Pardes**
- **Semantic pack prompt:** `SemanticPackPrompt.kt:12-36` together with `StartOffers.kt:27-35`, `:76-78`, `:108-109`. It opens Settings at every start until a pack is installed or the user unticks it. The flag is `semantic_pack_prompt` (`AppSettings.kt:430-435`), with a checkbox in `SemanticPackCard.kt:218-224`.
- **Feedback consent:** `FeedbackConsentDialog.kt:62-96`, a modal at start that is asked again if closed without an answer.
- The first-PDF-add question was removed in item 44 (plan:635, decision table :797). No code for it remains.

**Settings switch:** add a `CheckboxRow` to `DafScansCard`'s `CutPages()` (`:97-120`), as in `SemanticPackCard.kt:219-223`. Strings go next to `strings.xml:1207-1209`.

## H. Scan position lost after a TOC click

**How the scan saves its position**
- `TellThePlace` (`DafScanView.kt:367-390`) sets `place.topLineId` and calls `onScroll(lineId)`.
- From there: `dafAt` (`BookContentView.kt:1087`, `:1020-1023`) → ContentScrolled (`BookContentPanel.kt:317-327`) → `BookContentViewModel.kt:661-668` → `updateContentScrollPosition` (`ContentUseCase.kt:300-338`) → `saveAllStates()`.
- That writes `contentAnchorLineId` to `TabPersistedStateStore` (`BookContentStateManager.kt:259-330`, `:294`). There is no "TabStateManager" in this code; `TabPersistedStateStore.kt:12` is the equivalent.
- In memory, the scan keeps `ScanPlace(openAt ?: lineId)`, remembered per book (`DafScanView.kt:99-106`, `:133`). `place.page` is kept current by `ScanList` (`:315-317`).

**How a TOC click moves the scan**
- `BookTocView.kt:199-205` sends `LoadAndSelectLine` → `BookContentViewModel.kt:1194-1210`.
- `ContentUseCase.kt:197-212` sets the selected line, `anchorId` and `scrollToLineTimestamp`.
- `DafScanReader` receives them (`BookContentView.kt:1083`, `:1086`), and `FollowTheTab` (`DafScanView.kt:342-361`) calls `list.reveal(...)`, which is a `scrollToItem` (`:396-398`).

**Why the position is lost**
1. `FollowTheTab` never updates `place.topLineId`; only `TellThePlace` does (`:386`).
2. `TellThePlace` reacts to the list's "scroll in progress" flag going from true to false (`:377-382`). `scrollToItem` sets it true and back to false inside one non-suspending block, so that change is never seen and nothing is reported. Wheel and Page Up/Down use animated scrolling, so they do get reported.
3. The tab stays composed, but `ScanPages` is composed only while the tab is selected (`:160`). On return, `rememberPlaced` recomputes the page from the stale `place.topLineId`:
   ```kotlin
   LaunchedEffect(file) { place.page = place.topLineId?.let { amudOfLine(repository, it) }?.let(::scanPageOf) ?: 0; placed = true }  // :251-254
   ```
   `ScanList` then builds a new list at that old page (`:314`), and `FollowTheTab` skips its first run (`opened`, `:351-356`).
4. Related: turning the scan off after a TOC click hands the stale line to the text through `control.where` (`:143`). `BookContentView.kt:1043-1049` then writes it as the tab's position, so the text also opens at the old place. Find (`control.toLine`, `:138-141`) and Ctrl+Home/End (`control.edge`, `:137`) have the same flaw.

**Fix options** (any one, or combined):
- after `reveal`, set `place.topLineId` to the amud's first line (from `amudLines`);
- or report the position from the list's first visible row with a debounce, plus once when the view leaves, as `PdfBookView`'s `TellPlace` does (`:270-302`). PDF books don't have this bug (`PdfBookReader.kt:73-76`);
- or make `rememberPlaced` compute from `topLineId` only on the first open, or hoist the list state the way the daf view does (`BookContentView.kt:194`).

## Existing tests

**`/home/user/Pardes/SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/pdf/`**
- `PdfPageListTest` (3: one per row, two per row, scan amud b on the right)
- `PdfOutlineTest` (4: nested bookmarks, none, as contents with `entryAt`, TOC search)
- `PdfiumDocumentTest` (2)
- `PdfBookStoreTest` (9), `PdfBookFilesTest` (5), `MyPdfBooksSearchTest` (5), `PdfExperimentTest` (2)
- `PdfOpenBench` (benchmark; runs only with the `PARDES_PDF_BENCH` environment variable)

**`/home/user/Pardes/SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/`**
- `DafScansTest` (8, exact file names only; page↔amud at `:74-85`)
- `DafScanPagesTest` (3: bitonal PNG, background cut and re-read, re-cut / not a PDF / clear)
- `DafSpreadsTest` (4)
- `DafViewStateTest` (1: `dafView` is kept with the tab; nothing for `scanView`)
- `ScanSwitchBench` (benchmark)
- `DafPageTest` (11), plus `DafAnchorsTest`, `DafIndexTest`, `DafLayoutsTest`, `DafLemmasTest`, `DafLinksTest`, `DafReviewTest`, `DafDownloadsTest`

**Elsewhere:** `/home/user/Pardes/SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/settings/DafZoomTest.kt` and `/home/user/Pardes/SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/daf/DafLayoutsCardUiTest.kt`.

**No tests cover:** the `DafScanView` composables (`FollowTheTab`, `TellThePlace`, `rememberPlaced`), `ScanPageList`, the `PdfBookToc` UI, `PdfTocSearch` beyond the outline search case, `TextZoom`, or `VerticalBars`.

## The brief the agent got

> Read-only exploration of the Pardes desktop app (Kotlin, Compose Multiplatform + Jewel) at /home/user/Pardes. Thoroughness: very thorough. Do NOT edit anything. Report file:line for every claim.
>
> Context: Pardes (a fork of the Zayit desktop app) has a PDF reader for the user's own PDF books (`SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/pdf/`) and a scan view of Talmud tractates (`.../features/bookcontent/daf/DafScanView.kt`, `DafScanPages.kt`, `DafScans.kt`, `ScanCutFolder.kt`, `ScanPageList.kt`, `BitonalPng.kt`, `DafSetting.kt`). Stage 2 of the work is described in `docs/DESKTOP_STAGE2_PLAN.md` (sections 41-56 near the end: scans, PdfPageList, two-page spreads `DafSpreads.kt`/`PageSpreadsButton.kt`, bookmarks via `PdfOutline.kt`/`PdfiumCalls.kt` FFM, `PdfTocSearch.kt`, scan cut into PNG images under `pardes-scans`). Read those sections first.
>
> I need a precise map for these user requests (answer each separately, with the code path that implements today's behaviour and where a change would go):
>
> A. Dark mode for PDF and scans: how does the app know dark mode (Jewel theme / `ThemeUtils` / isDark)? How are PDF pages (Nucleus ComposePdfReader / `dev.nucleusframework:pdfium` and Pardes's own drawing from PNGs in DafScanPages / PdfPageList) drawn — is there an Image/Canvas where a ColorFilter (invert matrix) could be applied? Does the daf view (DafView/DafPage) already have dark-mode colours to copy?
>
> B. Scan file matching: how does `DafScans.kt` (or wherever) find a tractate's PDF in the user's scans folder and Otzaria's folders by file name? Show the exact matching logic (normalisation, prefixes). A user's file is named like "18 - עבודה זרה הוריות עדיות מורחב - מחובר.pdf" (a number prefix, several tractates joined in one file, extra words). What would need to change to match it, and how is the PDF page i mapped to the amud (the "page i = amud i from ב." assumption)? Where is the mapping page->amud computed (DafScanPages? ScanPageList?) and could a per-tractate offset ("which PDF page is daf ב") be plugged in? Where are the scans folder settings (the "סריקות הגמרא" card in settings › צורת הדף) and how is a setting persisted (AppSettings / user DB)?
>
> C. Pinch zoom (touchpad) and Ctrl+wheel zoom in the PDF view, the scan view and the daf view: find the gesture handling (detectTransformGestures? onPointerEvent with scroll? Nucleus's zoom?) and say whether zoom is anchored at the pointer or not, and how scroll offset is adjusted on zoom.
>
> D. TOC sync: when the PDF page changes (scrolling, Page Up/Down, arrow keys), how is the TOC panel's selection updated (PdfBookToc.kt, the "bookmark of the page at the top is marked")? Does the TOC list auto-scroll to the selected entry? Compare with how a regular book's TOC follows the reading position (Zayit's booktoc panel under features/bookcontent/ui/panels/booktoc). Also: for a PDF that has bookmarks, the TOC shows only the bookmarks; the user wants both bookmarks and the page list "like books with 2 divisions as already exists today" — find how Zayit shows books with two TOC divisions / alternative TOC structures (search for alt toc / AltToc / tocs / "חלוקה" in SeforimLibrary core models and in the booktoc panel) and how a PDF could offer both.
>
> E. Fast paging bug: "sometimes during fast paging it gets stuck and then nothing is drawn on the pages, especially in two-page view". Look at PdfPageList.kt and DafScanPages.kt rendering/prefetch/cancellation logic (coroutines, caches, LaunchedEffect keys, bitmap recycling) and list plausible causes.
>
> F. The nikud (vowel) toggle icon in the left vertical bar (VerticalBars.kt under features/bookcontent/ui/components): where is it shown and how is it hidden when the daf view is shown (stage 2 item 33 replaced it in the daf view)? How to hide it for PDF books and the scan view.
>
> G. Scan cutting to images (stage 2 item 56): where is the cutting started on first open of a scan (ScanCutFolder, DafScanView...)? The user now wants it opt-in: a question on first opening a Gemara scan, changeable in settings. Find how existing one-time questions/prompts are done in Pardes (e.g. the semantic pack prompt `SemanticPackPrompt`, or the question on first PDF add that stage 2 removed) and the settings card to put a switch in.
>
> H. TOC selection in the scan view not persisted: "When choosing a page in the TOC in the scanned Shas, after returning to the tab the position returns to where it was before choosing in the TOC". Trace how the scan view saves its position for the tab (TabStateManager / saveState / the BookContentViewModel state, `DafScanView` scroll restoration), and how a TOC click moves the scan; find why the saved position isn't updated on a TOC click.
>
> Also list the existing tests for these files (SeforimApp/src/jvmTest/.../features/pdf and .../daf). Keep the report structured A–H, concise but with exact file:line references and short code excerpts where decisive.

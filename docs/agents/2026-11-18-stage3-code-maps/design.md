# Design: split view, the find results pane, search modes, tree keys, zoom at the pointer, PDF paging and scan files

- **For:** stage 3 of Pardes for desktop, requests 59, 55, 48, 54, 47, 44, 43 and 52, and an order of the work (`docs/DESKTOP_STAGE3_PLAN.md`).
- **When:** 2026-11-18. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `40df456` (main of that day). The line numbers are that commit's.
- **By:** a Plan agent of Claude Code, given the three code maps of this folder. The report is as it came back; the brief it got is at the end.
- **Checked by hand afterwards:** its wave order was set aside the same day: the user asked for everything together. Its claims about pdfium-jvm's `pageTextLayout` and Zayit's E2E harness are checked when their steps start.

## The report

# Stage 3 design: items 1–8

This is a read-only design; nothing was edited. Two facts I found myself change parts of the earlier maps:

- **The PDF library already offers text search.** The PDFium library Pardes uses (`pdfium-jvm` 159.0.8152.0) has public `PdfReaderState.pageText(i)` and `pageTextLayout(i)`; the second gives every character with its box. It also has `prefetch(...)`, which cannot be cancelled once called, and `rememberPdfReaderState(cacheBytes = ...)` with a 64 MB default. It opens one PDFium thread per document, and its own comment says PDFium's font code (FreeType) is not safe to call from two threads.
- **Zayit already has a test harness that can measure paging.** It runs with `ZAYIT_E2E_OUT` / `ZAYIT_E2E_SCENARIO`, exports frames straight from the app's composition (not the screen), and new scenarios hook in from `main.kt`.

**Terms:** "Zayit file" means the file has no change-map entry yet, so it needs its first one. "Changed file" means it already has one, to be rewritten. `app/` means `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/`.

---

## 1. Request 59: Split View and Duplicate tab

**Verdict: it can be built in Pardes alone, with no change to Nucleus.** Nucleus's tab workspace has no notion of a pair (it only selects, closes, moves, reorders and tears off). Two existing facts make it cheap:
- Every tab of a window stays composed; only the selected one is measured (`TabsContent.kt:170-194`).
- Every pane, both side bars and the breadcrumb already draw "the selected tab" (`WindowBody.kt:50-55`, `ReaderPanes.kt:276-284`).

So a split view means measuring two tabs instead of one, and the **active side is simply the selected tab**.

### Model
- **New `framework/desktop/TabSplits.kt`:** `data class TabSplit(val a: String, val b: String, val ratio: Float = 0.5f)` and a `TabSplits` class held by `DesktopSession` (`val splits = TabSplits()`).
  - Inside it, a `mutableStateMapOf<groupId, TabSplit>`. A split counts only while both tab ids are still in the window's group.
  - Functions: `slotOf`, `partnerOf`, `pair`, `unsplit`, `setRatio`, `saved`, `restore`, `forget`.
- **Which side is which:** position 0 of a window's tab list is the strip's left edge in both directions (`DesktopSession.kt:194-197`). The tab with the lower position is drawn on the left, so the content always matches the strip in right-to-left too.
- **Limit:** one split per window in the first version.

### Drawing (`TabsContent.kt`, changed file, about 6 lines)
- Each tab gets a slot: whole, left, right, or hidden.
- `LocalTabSelected` becomes "shown", so a PDF or scan on the passive side still opens its file (`PdfBookReader.kt:64`, `DafScanView.kt:160`).
- Zayit's `.layout {}` stays for the normal case. Split tabs use `.then(if (side) Modifier.tabSide(...) else Modifier.layout { his code })`.
  - `tabSide` reports the full width and places the tab at an absolute x with `place(x, 0)`, which is not mirrored. So the Box's right-to-left alignment cannot move it.
- **What "selected" means where:**
  - Home and book tabs keep receiving "active" for their `isSelected`. That drives focus, the Ctrl+Alt+C copy context and reading time.
  - Search tabs receive "shown", because their `isSelected` only drives `SetUiVisible`.
  - A passive Home tab gets an empty search-bar state, because the window's bar state is shared by all Home tabs.
- **Divider:** a new `SplitDivider` after the loop, looking like `ReaderSplitter`. Dragging it changes the ratio, kept between 25% and 75%.
- **Activation:** a press on the passive side selects it (initial pass, not consumed), so the click still reaches the text. The mouse wheel scrolls the passive side without activating it.

### Active side
Panes, both bars, the breadcrumb, window shortcuts, the title bar, Ctrl+W and Ctrl+F all follow the selected tab already, with no change. One fix is needed: `WindowBody.kt:119` must also report "ready" for the passive tab once `panesReadyFor != null`. Otherwise a passive tab that was never selected stays on its loader forever (`BookContentPanel.kt:146-152`).

### Strip (`TabsView.kt`, Zayit file, about 8 lines)
- `RtlAwareTab` and `TabActionsMenu` receive the tab id. A Pardes `rememberSplitMark(tabId)` gives the pair its look:
  - no padding between the two tabs;
  - `AbsoluteRoundedCornerShape` rounding only the pair's outer corners;
  - a shared light tint and a 1 dp outline around both;
  - the usual selected tint on the active half.
- **Menu:** `rememberPardesTabActions(tabId)` is resolved before `TabContextMenu`. The items are drawn by a loop calling Zayit's private `tabContextMenuItem`, so they look native.
- **New strings:**
  - "הצגה לצד הלשונית הנוכחית", on another tab;
  - "פיצול עם לשונית חדשה", on the active tab: opens a fresh Home tab beside it;
  - "סיום התצוגה המפוצלת", on a member of the split;
  - "שכפול הלשונית".
- Creating a split moves the partner next to the active tab with `reorder`.

### How it behaves
- **Ctrl+Tab / Ctrl+PageDown:** the two tabs are adjacent, so stepping between them switches the active side. Stepping out shows the other tab whole; stepping back shows the split again, as in Chrome.
- **Closing one side:** `TabsViewModel.closeTab` selects the partner first (1 line), so no unrelated tab flashes; `DesktopSession.forget` drops the split (1 line).
- **Dragging along the strip:** a small `KeepSplitTogether` watches `workspace.draggedTab`. When a drop leaves the pair apart, the partner is moved next to the tab that was dragged.
- **Dragging to another window or desktop:** the split ends; the other tab stays as a normal tab.
- **Pinning:** `DesktopSession.setPinned` pins or unpins the partner too, so the pinned run stays in one piece.
- **Reopen closed tab, duplicate, move to desktop:** the result is a normal tab.

### Saving
- `WindowSnapshot.split: SavedSplit? = null`, appended last because the session file is ProtoBuf (`SessionModels.kt:154-161`).
- `DesktopSession.restore` puts it back: tab ids are kept, only window ids are new. `windowSnapshot` saves it, and `forgetWindow` drops it.
- Sleeping desktops and desktop links carry it for free, since both are made of `WindowSnapshot`s.

### Tests
- `TabSplitsTest`: slots with the selected tab inside or outside the pair; left and right in both directions; ratio limits; a member leaving; partner selected on close; pinning both; pair rejoined after a drag.
- ProtoBuf round trip with and without `split`.
- `DesktopManagerTest`: save, then reopen the desktop, and the split is back under the new window id.
- A Compose UI test of `tabSide` in right-to-left, and of activation on press.

### Risks
- **Panes jump when switching sides.** If the two tabs have different panes open, the panes are re-applied for each tab, as Zayit does per tab. A later option: keep the dock and change only what the panes draw.
- **Clicks on the absolutely placed halves** must be checked by the UI test.
- **`TabsContent` is already over Zayit's complexity limit** (17 against 15); keep the logic in Pardes helpers.
- **The hover preview's thumbnail** will capture both halves.

**Later versions:** drop a tab on the content's edge to split; move the pair together; several pairs per window; separate panes per side; scrolling the two sides together; a menu item to swap sides; the overflow list keeping both halves visible.

**Mock-up should show:** the joined pair in the strip (active and inactive half, light and dark, beside a pinned run); two books side by side with the divider and a marker on the active side; the panes on the active side; the tab menu with the new items.

### Duplicate tab
`DesktopManager.duplicateTab(tabId, windowId)`, about 20 lines next to `addMovedTab`:

```kotlin
val dest = when (val d = item.destination) {
    is BookContent -> d.copy(tabId = newId, bookId = state.bookContent.selectedBookId.takeIf { it > 0 } ?: d.bookId,
                             lineId = null, endLineId = null, openNotes = false, shnayimMikra = false)
    is Search -> d.copy(tabId = newId, scope = SearchScope.Global, globalExtended = false)
    else -> d.withTabId(newId)
}
```

Then copy the saved state and the thumbnail, and add the tab at the source's position. That is `openTab`'s rule: it lands beside the source on the + side, selected. The pin is copied, as Chrome does.

**Pitfalls:**
1. **A line in the destination overrides the copied place.** "Explicit line navigation wins" (`BookContentViewModel.kt:306-327`). `stripEphemeral` clears `lineId` and `openNotes` but not `endLineId`, so all four must be cleared.
2. **The destination keeps the book the tab was first opened with.** The ViewModel prefers it over the saved book (`:290-291`), so take `selectedBookId`, as `SessionManager.kt:214` does.
3. **A search tab's scope would overwrite the copied state.** `seedNavigationScope` re-applies the destination's scope (`SearchResultViewModel.kt:177-186`), so give the copy a global scope.
4. **The copy takes the last saved place.** The scan view's stale-place bug (report-pdf H) must be fixed before Duplicate.

No shortcut: Edge's Ctrl+Shift+K is already the targum shortcut here. Test in `DesktopManagerTest`.

---

## 2. Request 55, in-book find results in a pane (and PDF search, request 48)

### Who owns the results
**Do not move them into the ViewModel.** That would rewrite about 45 lines of Zayit's find code for no gain, since every tab stays composed. Instead, the text publishes them to a per-tab hub, the way the PDF reader and the contents pane already talk through `PdfBookStore.shown` and `pageRequests` (`PdfBookStore.kt:112-128`).

- **New `features/bookcontent/find/BookFindHub.kt`**, an app-wide singleton reached from AppGraph.
  - `found: StateFlow<Map<tabId, FindListing>>`, where a listing is either text or PDF results.
  - `goTo: SharedFlow<Pair<tabId, FindTarget>>`, the rows' "go here" requests.
- **Hooks in `BookContentView.kt`** (changed file, about 6 lines):
  - publish the results and the current match;
  - clear them on dispose;
  - listen to `goTo`;
  - move the end of `navigateToMatch` (`:839-853`, the daf branch included) into its own `showMatch(match)`, which `navigateToMatch` then calls.
- **`BookFindMatches.kt` stays as it is.** A row loads its line, then `matches.nextAfterLine(lineIndex - 1, true)` gives the match to go to.

### The pane
- **`ReaderPane.FindResults`**: docked on the left (after References), 320 dp, a line pane, movable.
- **`ReaderPanes.kt` edits:** `standard`, `desiredPanes`, `BookPaneBody`, and the two `when`s that leave a pane's size to the dock (about 6 lines).
- **Toggle:** `ContentState.showFindResults` and `BookContentEvent.ToggleFindResults`, with a ViewModel handler. Not saved, like the find state itself.
- **Opening it (`FindInPageBar.kt`, Zayit file):** optional `onShowList` / `listShown` parameters, and a toggle after the divider. Alt+Enter in the field opens it too. A row clicked while the bar is closed reopens the bar with its query, so the highlights come back.
- **Rows, in a new `FindResultsPane.kt`:**
  - Groups follow the table of contents. Each line's entry comes from `getLineTocMappingsForBook` and `getBookToc`, cached per book as `SearchTocUseCase.kt:50-89` does.
  - Each row reuses `ResultRow` and `SnippetText`, as `LinePlacesView.kt:238-248` already does. The snippet is a piece of the line around its matches, made with `findAllMatchesNormalized`, with "(3)" when the line has several.
  - The current match's row is marked; ↑/↓/Enter step through the rows.
  - Lines are loaded 500 at a time, only as rows come into view.
- **Smart mode:** rows are the lines the meaning search found, showing each line's start.

### PDF books: yes, the same pane, but through the PDF library, not new direct PDFium calls
**Why not direct calls (FFM):**
- Text extraction loads fonts, and FreeType is not thread-safe; Pardes's own PDFium thread would run beside the library's rendering thread.
- `FPDFText_CountRects` and `FPDFText_GetRect` need call shapes that are missing from `reachability-metadata.json`, so the Windows build would need new entries. (`GetText`, `CountChars`, `LoadPage` and `FindStart` already have theirs.)

**Design, a new `features/pdf/PdfFind.kt`:**
- Extract page text with `reader.pageTextLayout(i)`, one page at a time, each awaited, so at most one text job waits ahead of the page drawing. Cache it per open book.
- Match with `findAllMatchesNormalized` on a string built from the characters, with an index map back to character positions.
- Stream results page by page. A match's rectangles are the union of its character boxes, merged per line.
- **Scanned PDFs:** a scanned PDF has no text layer (every page has 0 characters). The bar then shows "אין טקסט בקובץ" and the pane explains why.
- **PDFs whose Hebrew is stored in reverse order:** fall back to the reversed query; this must be tried on the user's files.
- **Find bar:** `PdfBookView` overlays Zayit's `FindInPageBar` on the same per-tab find state. Ctrl+F, Ctrl+G and F3 already reach PDF books, because `hasFindInPage` is true for their ids (`WindowUtils.kt:25-30`).
- **Highlights:** `Box(Modifier.width(w)) { PdfPage(...); FindMarks(rects, pageSize, current) }`, drawn outside `drawnAtMost` and outside the dark-mode filter (request 42). Position: x = left/W·w, y = (H−top)/H·h.
- **Going to a match:** row = `rowOf(page, spreads)`, offset = the match's y minus a third of the view.
- **Pane rows** are grouped by bookmark (`PdfOutlineToc.entryAt`), or by page when the PDF has none.

**Tests:**
- `BookFindHubTest`.
- `FindListingTest`: grouping, snippets, a line with several matches, smart rows.
- `PdfFindTest`: matching that ignores nikud and final letters, character mapping, merging rectangles, conversion to pixels, detecting a PDF with no text.
- A small PDF with Latin text, written by the test, read through the real library, as `PdfOutlineTest` writes its own PDF.

**Mock-up should show:** the find bar with the new list button; the pane with groups, snippets and the current row; the PDF version grouped by bookmark; the "no text" state.

---

## 3. Request 54: search modes

**The three modes:**
- **Exact:** the query as if quoted: one strict phrase, words only, no prefixes or word forms.
- **General:** words in all their forms, with no meaning model.
- **Hybrid:** today's words plus meaning.

**Code, a new `features/search/domain/SearchModes.kt`:**
- `enum class SearchMode { EXACT, GENERAL, HYBRID }`.
- `SearchModeEngine(engine, mode: () -> SearchMode) : SearchEngine by engine, WordsEngine`. The words-only engine is `(engine as? WordsEngine)?.words`, which is `LongQuerySearchEngine(Lucene)` (`HybridSearchEngine.kt:51`).
  - **Exact:** the words-only engine searches and counts `asQuote(query)` (`LongQuerySearchEngine.kt:151-156`; it is internal to the same module). Snippets and highlights use the quoted query too. `highlightsByMeaning` is false and `passagesByMeaning` gives nothing. Its `words` is a quoting wrapper, so the library-order listing reads the same results.
  - **General:** the words-only engine, unchanged.
  - **Hybrid:** the engine as today.
- **One line** in `SearchResultViewModel.kt:168`: `CatalogOrderEngine(SearchModeEngine(engine) { mode() }, …)`. The extra category tabs (`:1537`) and the counts follow through it.

**State:**
- `SearchPersistedState.searchMode: SearchMode? = null`, appended last; null means "the default".
- `SearchUiState.searchMode`, and a `SetSearchMode` event modelled on `SetCatalogOrder`, followed by `ExecuteSearch`.
- **The choice is sticky:** a change in any tab becomes `AppSettings.searchMode`, the default for new searches. It starts as hybrid with the semantic pack installed, else general.
- No change to `TabsDestination`: a new tab reads the default when it has no saved mode.

**The results screen:** its passage preview asks the app's own engine whether to highlight by meaning (`SearchResultScreen.kt:941`). It must pass `mode == HYBRID && …` and the effective query (quoted in exact mode). 2 lines.

**Without the semantic pack:**
- Hybrid is shown disabled, with "דורש את חבילת החיפוש לפי משמעות" and a link to Settings › ניהול נתונים.
- A tab saved in hybrid mode runs as general (the engine falls back by itself).
- Whether meaning search is there: `engine.highlightsByMeaning(rawQuery)`.

**The in-book find's "smart" toggle** stays separate. Opening a result turns it on only when the search ran in hybrid mode (`SearchResultViewModel.kt:2306-2312`, one condition).

**Ratings (feedback mode):** add the mode to the key when it isn't hybrid, so ratings of different result lists don't mix. Search history is unchanged in v1 (open question: record the mode there).

**Tests:** `SearchModeEngineTest`, with a fake engine that records the queries it receives; ProtoBuf round trip with and without `searchMode`.

**Two mock-up variants:**
- **A:** a link in the line above the results, "· חיפוש: משולב ▾", opening a menu of the three modes with one line of explanation each. The Home bar's text-search row names the mode.
- **B:** three small pills in the search bar itself (Home and the results tab's own bar): "מדויק | כללי | משולב". The status line just names the mode.

Both should show the disabled hybrid state, and optionally a radio choice in Settings › כללי.

---

## 4. Request 47: keyboard in the book tree and TOC, and tab shortcuts

**A shared helper, new `features/bookcontent/ui/components/TreeKeys.kt`:**
- A pure function `treeMove(rows: List<TreeRow>, focused, key, rtl, pageRows): TreeMove`, where `TreeRow(key, level, expandable, expanded)` and the result is focus, toggle, open or nothing. Easy to unit-test.
- `TreeKeysState`: the focused row and a focus requester.
- `Modifier.treeKeys(...)`: focusable, with a key handler.
- `Modifier.treeRow(state, key)`: a press makes the row focused and takes focus (initial pass); a 1 dp focus ring is drawn only while the tree has focus.

**Keys, right-to-left:**

| Key | Action |
|---|---|
| ↓ / ↑ | next / previous row |
| ← | opens a closed folder, or goes to its first child |
| → | closes an open folder, or goes to its parent |
| Enter | does what a click does |
| Ctrl+Enter | opens in a new tab, for free: `bookClick` reads the modifier keys at click time (`CategoryTreePanel.kt:113-124`) |
| Home / End | first / last row |
| PageUp / PageDown | one view's worth of rows |
| Esc | focus back to the text |

Typing letters still goes to `TypeToSearch` (its own file in Pardes). Extend it so a focused tree wins over the one under the pointer.

**Hooks:**
- **`CategoryBookTreeView.kt` (Zayit file, about 8 lines):** `TreeItem` gains `expandable` and `open`, filled in `buildTreeItems`; the list gets `.treeKeys`; each row gets `.treeRow`.
  - Folder state comes from `expandedCategories`, because `CategorySelected` toggles.
  - "הוספת תיקייה…" in "הספרים שלי" works through `MyPdfBooks.click`.
- **`BookTocView.kt` (Zayit file, about 5 lines):** rows from `VisibleTocEntry`. PDF bookmarks get it for free, since `PdfOutlineView` uses this view.

**Tab shortcuts, new `core/presentation/window/TabKeys.kt`**, one line in each of the two key handlers of `MainAppWindow.kt`:
- **Ctrl+1–8** = the Nth tab in reading order; **Ctrl+9** = the last tab.
- **Ctrl+PageDown / PageUp** = next / previous tab; **Ctrl+Shift+PageDown / PageUp** = move the tab.
- The window's handler runs before the reader's, which takes PageUp/Down whatever modifier is held.
- **Ctrl+Tab goes the wrong way in right-to-left.** In right-to-left the reading order is the reverse of the list order, and today's Ctrl+Tab steps by +1 (`MainAppWindow.kt:202-209`, `:376-393`), which is backwards. Zayit 0.6.13 fixed this under its new licence, so ask the user before having Ctrl+Tab use the same helper.

**Other places worth keys, most useful first:**
1. Home/End/PageUp/PageDown in the search results list.
2. Arrows and Enter in the tab-search, favorites and notes popups.
3. ←/→ through the commentators' pages.
4. Space to tick a book or category in the search filter tree.
5. Alt+1 / Alt+2 to focus the tree / the TOC.
6. Shift+F10 / the Menu key for the tab menu.
7. Window shortcuts in panes floated into their own window.

**Mock-up should show:** the focus ring in light and dark.

---

## 5. Request 44: zoom anchored at the pointer

**Where the pointer is, without touching Zayit's `TextZoom.kt`:** a new Pardes `ZoomPivot.kt` with `Modifier.zoomPivot(state)`. In the initial pass, without consuming anything, it records `changes.first().position` on Scroll, ScaleChange and Move, and the centre of the fingers for a touch pinch. It goes on the same container as the zoom modifier (PDF) or on the scan/daf view inside it.

**During the gesture, preview by scaling, not by relayout (PDF and scan):**
- Lay the pages out at the committed zoom (`dafZoomFlow`).
- While zooming (`onZoomingChange`), apply `graphicsLayer { scale = shown / committed; transformOrigin = pointer }`.
- No relayout and no re-render for each frame. This also removes report-pdf E.9.
- Zoom buttons animate the same way: the committed value jumps, the shown value animates.

**On commit, for `PageRows`** (rows = a title T + a page H = w·a), with s = w′/w and `pad = layoutInfo.beforeContentPadding`:

```
yc = py − pad;  k = visible item with offset_k ≤ yc < offset_k + size_k
yl = yc − offset_k;  yl′ = if (yl ≤ T) yl else T + (yl − T)·s      // titles and gaps don't scale
listState.requestScrollToItem(k, scrollOffset = yl′ − yc)          // negative: the list lays out the rows above at their new size
```

- **Sideways:** move `PageRows`' own horizontal scroll state up so it can be set (`PdfPageList.kt:151`).
  - xs = (rtl ? Vw − px : px) + X; rowW = w or 2w+G; listW = max(Vw, rowW); u = xs − (listW − rowW)/2.
  - The page parts of u scale by s; the gap does not.
  - X′ = clamp(rowStart′ + u′ − (rtl ? Vw − px : px), 0, listW′ − Vw), applied once `maxValue` reflects the new width.
- **Rounding:** keep the last index, offset and position, as `DafScale` does (`DafView.kt:227-237`), so rounding errors don't add up.
- **`DafView`:** keep its per-frame relayout (its text stays sharp). Change its `SideEffect` (`:479-496`) from "the point at the top stays" to "the point under the pointer stays", and add the sideways correction to its horizontal scroll (`:713-718`).
- **Buttons and keys** keep anchoring at the top, as today.

**Tests:** `ZoomPivotTest`, pure math: inside a page, inside a title, in a gap, a negative offset, two pages side by side, a narrow layout becoming wide, edges, right-to-left. A Compose UI test: the content point under the pointer stays within 1 px after a commit.

---

## 6. Request 48: PDF bookmarks and page list together, and the fast-paging fix

**Both divisions:** all inside `PdfBookToc.kt` (a Pardes file).
- With bookmarks: `PdfOutlineView(weight 1f)`, a divider, then the pages' `BookTocView(weight 1f)`, as Zayit splits a book's two divisions (`BookTocPanel.kt:112-121`).
- Search: `PdfTocSearch.contentsOf` merges both with `withAlt`, the pages under negative ids. `open(id < 0)` goes to page −id−1.

**Causes, most likely first.**

PDF books (drawn by the PDF library):
1. **`prefetch` floods the single PDFium thread with work that can never be cancelled.** It is fire-and-forget, and checks the cache only when it starts. Every change of the first visible row queues 2 rows ahead, 4 pages in two-page view, at 75–250 ms each for a scan. The visible pages wait behind all of it.
2. **The 64 MB cache is too small in two-page view.** At the 1600 px cap a page is about 14.5 MB, so 4 visible plus 4 prefetched pages evict what is on screen, which is then drawn again.
3. **Prefetch restarts on every zoom frame** (E.9); item 5 removes it.
4. **Pardes's own PDFium calls run beside the library's thread** (E.11). They only read bookmarks, with no fonts, so this is a low risk; text search therefore goes through the library.

Scans (drawn by Pardes from the PNGs):
1. **A failed decode leaves a blank page for good.** Nothing retries it, and memory pressure makes failures likely: 6 MB gray bitmaps, Skia `Codec` and `Data` never closed, the same page decoded several times at once.
2. **A memory cache of 12 pages, with `prepare` using `collect`,** keeps re-decoding in two-page view.
3. **The last-asked-first queue of pages to cut** only matters before a tractate is cut.
4. **A page that failed to cut is never retried.**
5. **Edge cases:** deleting the cut pages while a scan is open; two windows fighting over the cutting.

**Fixes:**
- **PDF:**
  - Prefetch only after the list has rested for about 150 ms; use `collectLatest`; prefetch in the direction of travel; one row ahead in two-page view; never the same page twice.
  - `rememberPdfReaderState(cacheBytes = …)` sized to the visible plus prefetched pages, about 160 MB in two-page view, with a cap.
- **Scans:**
  - Close the codecs; check the result of `allocPixels`; have `decode` report found / missing / failed.
  - Retry a failed decode with a backoff, and show an error tile with "נסה שוב" instead of an endless spinner.
  - Never decode the same page twice at once.
  - Size the memory cache by the number of visible pages.
  - Use `collectLatest`, and don't decode ahead while scrolling.
  - Make the cutting queue first-in-first-out, and drop a page from it when it leaves the screen.
  - Reset failed pages when the scan is opened again; reopen after "delete pages".

**Reproducing and measuring in the cloud:**
1. **A benchmark (`PdfPagingBench`),** off unless an environment variable turns it on, like `PdfOpenBench`. It builds the PDF library's reader by reflection, as `PdfOpenBench` does, and runs 30 page turns 40 ms apart under the old and the new prefetch rules. It reports when the final visible pages are drawn and how many renders ran, on a Vilna scan PDF and a text PDF.
2. **A Pardes `E2ePdfPagingScenario` in Zayit's test harness,** one line in `main.kt`:
   - two-page view, 30 PageDowns;
   - a frame exported every 250 ms for 10 s;
   - count the pages still blank, which gives the time to settle;
   - the same for the scan view.

   llvmpipe is slow, so compare before and after in the same container, not absolute times.
3. **`DafScanPagesTest`** with an injected decoder: a corrupt PNG, a decode that fails once, a decode cancelled by a newer one.

**Mock-up should show:** the two-division contents.

---

## 7. Requests 43 and 52: scan files

**Lenient matching, a new `features/bookcontent/daf/ScanNames.kt`:**
- Normalise names with `ReferenceParser.normalizeName`, drop leading numbers, and require the tractate's words as a contiguous run of whole words.
  - So "בבא קמא" never matches "בבא מציעא", and "ברכות" never matches "מכות".
- Order in which candidates win: the exact `"$tractate.pdf"`, then a file holding only this tractate, then a joined file, ordered by where the tractate appears in its name.
- Second tier, for spellings with and without yod and vav (e.g. עירובין and ערובין): compare words with yod and vav dropped. A test checks that no two of the 37 tractates collide.
- Cache the folder listing; rescan when the folder changes, or on a button.

**Where a tractate starts inside its file:**
- Order of precedence:
  1. the user's value;
  2. a bookmark whose title names the tractate, or better its child named "ב" / "דף ב", read with `PdfOutlines.read`;
  3. the order of the tractates in the file name, adding up the amud counts of the ones before it (each from its table of contents: last amud − ב. + 1);
  4. otherwise 0.
- **Storage:** `DafScanSettings.firstPages: json<Map<String, Int>>`, keyed `"fileName|tractate"`. It is not portable, like `folder` (`DafScans.kt:21`). Not the user database: the value describes a file on this computer.
- **Where the user sets it:**
  - In the scan view: a slim line over the pages, "«file» · דף ב. בעמוד 212 · תיקון", which opens "העמוד שלמעלה הוא דף [ ] עמוד [א/ב]". Plus "זה דף ב." on a page's menu.
  - In the settings card "סריקות הגמרא": a list of tractates found in joined files, each with an editable first page and "חזרה לניחוש".

**How the offset flows:**
- `DafScans.of()` returns `ScanSource(file, first, count)`, carried by `ScanLoad.Found(source)` into `ScanPlace`.
- `ScanList(pages = count)`.
- `ScanPageList` passes `first + page` to `ScanPage`, `prepare` and `heightOf`.
- The fallback view: `PdfPageList(firstPage, pageCount)`.
- The scan button's look-ahead: `ScanPageAhead` prepares `first + page`.
- `scanPageOf` / `amudOfScanPage` and the two-page pairing stay relative to the tractate's own pages, so ב. still stands alone on the left.
- The cut folder stays one per file, shared by all its tractates (`ScanCutFolder.of`). The shown tractate's pages are cut first through `want()`.

**Request 51 (opt-in cutting):**
- `DafScanSettings.cut: json<Boolean?>(null)`:
  - null: a question in place, in the "no scan" card's style;
  - false: the PDF library's view, with nothing written to disk;
  - true: today's cutting.
- A checkbox in the settings card. `open()` must not start cutting when it is off.

**Tests:**
- `DafScansTest`: number prefix, joined file, extra words, nikud and quotes, the near-name pairs, exact name first, the yod/vav tier.
- `ScanSourceTest`: order of precedence; page mapping; the count cut at the file's end; a bookmark guess from a PDF written by the test.

**Mock-up should show:** the scan-view line and its editor, the settings list, and the wording of the request-51 question.

---

## 8. Wave order (a Windows testers' build at the end of each)

The repository holds no stage-3 request document, so I couldn't match every small-bug number to its item. The small items the maps found are listed by name; please map them to 45, 46, 50, 56, 57, 58 and 60.

**Wave 1: small and visible, plus mock-ups sent out**
- **49:** no nikud button for PDFs and scans; one condition in `VerticalBars`, through the existing `PageSpreadsButton` test.
- **The small items found by the maps:**
  - **scan place lost after a TOC click:** update `topLineId` after a jump, and report the place from the first visible row;
  - **PDF TOC follows the page:** a `skipNextAutoCenter` flag stays stuck after clicking the current entry. It is a Zayit bug, so fix it for both brands;
  - **a link in the search preview opens two tabs:** `passageLine` should ignore a click already consumed;
  - **copy adds a space:** trim at the copy points;
  - **torn-off commentator's title not clickable:** `PaneHeader.onLabelClick`;
  - **settings window doesn't come back when minimized:** un-minimize from outside the window's content; the same in `ToolWindows.kt`;
  - **notes icon not mirrored:** `autoMirror` and mirrored SVGs.
- **Duplicate tab (59b), tab shortcuts (from 47), and 53** as a written research note (ideas only).
- **Mock-ups sent for approval during this wave:** 59, 54 (both variants), 55, the icon for request 42, the 52 editor, the 51 question.
- **Why:** nothing depends on these, and tester value is immediate. Decisions get made while later waves are coded. The scan-place fix must come before Duplicate.

**Wave 2: PDF and scan display (48 paging, 51, 42, 48 two divisions)**
- **Measure first, then fix paging.** Opt-in cutting defaults to "not cutting", which sends users to the PDF library's view, so its paging fix must ship in the same build.
- **42:** colours mapped (black to the theme's text colour, white to the panel's background), for scans through `Image(colorFilter)` and for PDFs through a filter layer. The "צבעים מקוריים" button goes in the slot request 49 frees.
- **Overlays:** the page wrapper keeps room for overlays, so find highlights stay outside the colour filter.
- **Why:** these share files: `PdfPageList`, `ScanPageList`, `DafScanPages`, `DafScanView`, `VerticalBars`.

**Wave 3: scan files and zoom (43, 52, 44)**
- **Why:** both renumber or change `ScanList` / `PageRows` once wave 2 has settled them. The zoom preview builds on wave 2's prefetch rules.

**Wave 4: search (41, 54, 55, then PDF search)**
- **41:** the author's own books after the exact categories, past the `addingScopes` check, narrowed by a category chip.
- **54 (search modes), then 55 (results pane), then PDF search,** which reuses 55's hub and pane.
- **Why:** this isolates the files with the highest merge risk (`SearchResultViewModel` / `SearchResultScreen`, `SearchHomeViewModel`, `HomeView`) in one build.

**Wave 5: keys and Split View (47, 59)**
- **Why:** the biggest change to the tab system. It needs the approved mock-up, Duplicate is already in testers' hands by then, and the keyboard focus work is checked together with focus between the two sides.
- **On Windows check:** focus, the joined drawing in right-to-left, saving and restoring.

---

### Critical files for implementation
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/tabs/TabsContent.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/desktop/DesktopSession.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/BookContentView.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/pdf/PdfPageList.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/SearchResultViewModel.kt

Also central:
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/tabs/TabsView.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panes/ReaderPanes.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafScanView.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafScanPages.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafScans.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/categorytree/CategoryBookTreeView.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/pdf/PdfBookView.kt
- /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/session/SessionModels.kt

## The brief the agent got

> You are designing implementation approaches (read-only; do not edit anything) for the Pardes desktop app at /home/user/Pardes (Kotlin, Compose Multiplatform 1.12.1 + Jewel 0.41, Nucleus windows/docking from a vendored fork in nucleus-fork/; Pardes is a fork of Elie Gambache's Zayit and must stay as close to Zayit as possible: smallest change, new Pardes files reached by one-line hooks from Zayit files, his style; rules in .claude/skills/zayit-fidelity/SKILL.md and references/eli-style.md, references/lessons.md). Every user-visible string goes in strings.xml (Hebrew UI, the app is right-to-left). Tests beside the code (SeforimApp/src/jvmTest).
>
> Three code maps were already made; READ THEM FIRST instead of re-exploring (they have exact file:line references):
> - /tmp/claude-0/-home-user/7a83558a-f5b1-57d5-b162-d0c37b70441f/scratchpad/report-pdf.md (PDF books + Talmud scan view: dark mode, scan file matching, zoom, TOC sync, fast paging bug, nikud button, opt-in cutting, scan position bug)
> - /tmp/claude-0/-home-user/7a83558a-f5b1-57d5-b162-d0c37b70441f/scratchpad/report-search.md (author suggestions, link click in search preview, search modes, in-book find, copy leading space)
> - /tmp/claude-0/-home-user/7a83558a-f5b1-57d5-b162-d0c37b70441f/scratchpad/report-ui.md (book tree keyboard, commentary title in torn-off pane, settings window un-minimize, notes icon, duplicate tab, tab menus/shortcuts)
> Also docs/DESKTOP_STAGE2_PLAN.md (the previous stage's plan, for format and what was built). Note: the SeforimLibrary submodule is empty in this checkout (its core models can't be read).
>
> The user's decisions already taken: request 59 "connected tabs like Chrome" = Chrome's Split View (two tabs side by side in one window, shown joined in the tab strip), plus "Duplicate tab". Request 42: in dark mode PDF pages and scans are drawn inverted automatically, with a button in the left vertical bar (where the nikud button goes away for PDFs/scans, request 49) to show the original colours. Request 54: three search modes — exact = as if the query were in quotes (strict phrase, lexical only, no prefixes/forms), general = broad lexical search with all the word forms but no semantic model, hybrid = today's lexical+semantic fusion; the user wants the right design and code for choosing among them. Request 51: cutting a tractate's scan into images becomes opt-in: a question when a Gemara scan is opened the first time, changeable in settings (default: not cutting until the user agrees).
>
> Design, for each of the following, a concrete approach: which files/functions change or are added (Pardes file vs edit in a Zayit file, and the one-line hook), state/persistence, edge cases, risks, and the unit tests to add. Be concrete and short. Where a design choice needs the user's eye (mock-up), say what the mock-up should show.
>
> 1. Request 59, Split View + Duplicate tab. Read the tab system yourself enough to judge feasibility: navigation/src/commonMain/kotlin/io/github/kdroidfilter/seforim/tabs/ (TabsState, TabsEvents, TabsDestination), SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforim/tabs/TabsViewModel.kt, SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/tabs/TabsContent.kt (all tabs stay composed in this version), .../core/presentation/window/WindowBody.kt, .../core/presentation/tabs/TabsView.kt (strip, TabActionsMenu, RtlAwareTab), .../framework/desktop/DesktopSession.kt and DesktopManager.kt, TabPersistedStateStore. How would two tabs be shown side by side in one window (each with its own content and panes; which one is "active" for keyboard shortcuts, the title bar, Ctrl+W), how is the pair drawn joined in the strip, how is it saved/restored with the window (WindowSnapshot), and how does it interact with dragging a tab to another window, pinning, closing one side, Ctrl+Tab. Propose a minimal first version and what to leave for later. Duplicate: as in report-ui.md E, with its two pitfalls.
>
> 2. Request 55, in-book find results in a side pane (the find bar FindInPageBar + BookFindMatches + ReaderPanes): a ReaderPane.FindResults pane listing all matches grouped by TOC entry with snippets, opened from the find bar; state ownership (move results from composable produceState to the tab's ViewModel?), reuse of ResultRow/SnippetText, clicking a row = navigate like next/previous. Also for PDF books (request 48 asks for search in PDFs): can the same pane list PDF text matches found with PDFium's text API (FPDFText_LoadPage / FPDFText_FindStart / FPDFText_FindNext / FPDFText_GetText, called through Java FFM as PdfOutline.kt/PdfiumCalls.kt already do for bookmarks), and how would matches be highlighted on the drawn page (PdfPageList draws pages via Nucleus's PdfPage or Pardes's own PNGs for scans)? Note scanned PDFs have no text layer.
>
> 3. Request 54, search modes: UI placement (the Home search bar in HomeView.kt, the results tab's SearchStatusLine in SearchResultScreen.kt, maybe Settings default) and the code path (a wrapper engine like CatalogOrderEngine in SearchResultViewModel; exact via LongQuerySearchEngine.asQuote semantics; general via WordsEngine.words; per-tab persisted field appended last in SearchPersistedState for ProtoBuf; what happens without the semantic pack; highlightsByMeaning; facets; the in-book find's own "smart" toggle). Propose 2 mock-up variants.
>
> 4. Request 47, keyboard in the book tree (CategoryBookTreeView.kt, CategoryTreePanel.kt, MyPdfBooks.kt) in code-editor style, right-to-left (← expands/goes to child, → collapses/goes to parent), Enter opens, Ctrl+Enter new tab, Home/End/PageUp/PageDown, focus ring row, type-ahead already exists via TypeToSearch; and the same helper for the TOC (BookTocView.kt). Plus which tab shortcuts to add (Ctrl+1..9, Ctrl+PageUp/PageDown) and other places worth keys, ranked.
>
> 5. Request 44, pinch / Ctrl+wheel zoom anchored at the pointer for the PDF view, the scan view and the daf view (texteffects TextZoom.kt reports no pivot; PageRows in PdfPageList.kt; DafView keeps the top fixed). Design the pivot math for a LazyColumn of rows of fixed-aspect pages plus horizontal scroll, applied during the gesture preview and on commit.
>
> 6. Request 48 (besides search): show both the PDF's bookmarks and its page list, "like books with two divisions" (BookTocPanel's alt-structure split), and fix "fast paging gets stuck and nothing is drawn, especially in two-page view" — rank the candidate causes in report-pdf.md E by likelihood and give the fixes (decode failures and FAILED pages never retried, LIFO queue, collect vs collectLatest prefetch, Nucleus single PDFium thread queue, cache sizes) with a way to reproduce/measure in the cloud container (headless Xvfb run is possible; see .claude/skills/pardes-desktop-run/SKILL.md).
>
> 7. Requests 43 and 52, scan files: lenient matching of file names in the user's scans folder (e.g. "18 - עבודה זרה הוריות עדיות מורחב - מחובר.pdf": number prefix, several tractates in one file, extra words) and a per-tractate "which PDF page is daf ב" setting (where to store it: DafScanSettings json map vs user DB; where the user sets it: the settings card "סריקות הגמרא" and/or in the scan view itself), and how a joined file's offset flows through scanPageOf/amudOfScanPage, ScanList, ScanPageList, the two-page pairing, the cut folder (one cut per file shared by its tractates), and PDF bookmarks as an automatic guess of each tractate's first page.
>
> 8. A wave order for all of stage 3's requests (41-60: small bugs 56, 46, 50, 57, 58, 49, 60, 45; PDF/scans 42, 43, 44, 48, 51, 52; search 41, 54, 55; keys/tabs 47, 59; research 53 = learning from Ariel Daniely's Zayita fork's personal books, ideas only) with reasons, assuming a Windows testers' build at the end of each wave.
>
> Return a structured design document, numbered 1-8, concise but specific (file paths, function names, data structures, tests, risks).

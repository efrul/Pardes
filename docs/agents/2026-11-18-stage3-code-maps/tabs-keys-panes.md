# Code map: the book tree, keys, the torn-off commentator, the settings window, the notes icon and the tabs

- **For:** stage 3 of Pardes for desktop, requests 47, 50, 57, 58 and 59 (`docs/DESKTOP_STAGE3_PLAN.md`).
- **When:** 2026-11-18. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `40df456` (main of that day). The line numbers are that commit's.
- **By:** an Explore agent of Claude Code, one of three run side by side. The report is as it came back; the brief it got is at the end.
- **Checked by hand afterwards:** Ctrl+Tab steps by +1 (`MainAppWindow.kt:377-393`); the settings window un-minimizes only inside its own content (`SettingsWindow.kt:82`), as Zayit's tool windows do (`ToolWindows.kt:121`).

## The report

I made no edits. Two dependency sources could not be read from the repo itself, so I checked them as follows:
- **Jewel:** there are no Jewel jars on disk. `~/.gradle` has no caches, and `/home/user/Pardes/jewel/` contains only a `build.gradle.kts` that re-exports the Jewel libraries (lines 21-42). I read the published 0.41.0 sources jars from Maven Central in memory, without saving anything.
- **Nucleus:** I read the vendored jar in `nucleus-fork/maven/…/nucleus.decorated-window-tao-3.0.0-dev-202610272317-2-tearoff.jar` with `javap`, so those references are class and method names, not line numbers.

**Paths:** `app/` = `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/`, `nav/` = `/home/user/Pardes/navigation/src/commonMain/kotlin/io/github/kdroidfilter/seforim/tabs/`, `icons/` = `/home/user/Pardes/icons/src/commonMain/kotlin/io/github/kdroidfilter/seforimapp/icons/`. Anything else is under `/home/user/Pardes/`. "Zayit file" means the file has no entry in `docs/ZAYIT_CHANGE_MAP.md`, so changing it needs a new entry.

**Out-of-date docs:**
- `CLAUDE.md` and `TAB_SYSTEM_README.md` name `TabStateManager` and `TabDockManager`. Neither exists in the code any more.
  - The per-tab state store is `TabPersistedStateStore`.
  - Tab dragging between windows is done by Nucleus's `TabWorkspace`.
- `CLAUDE.md` gives Jewel 0.31. The real version is `jewel = "0.41.0-262.10968.63"` (`gradle/libs.versions.toml:34`), with Compose 1.12.1 (`:18`).

---

## A. Keyboard in the book tree

**How it works today**
- **Not a Jewel tree.** `LazyTree`, `BasicLazyTree` and `TreeState` are used nowhere in the repo.
  - The tree is a flat list: `TreeItem(id, level, content)` (`app/features/bookcontent/ui/panels/categorytree/CategoryBookTreeView.kt:47-52`), built by `buildTreeItems` (336-428).
  - It is drawn as `VerticallyScrollableContainer { LazyColumn { items(treeItems) { Box(padding(start = level*16)) { item.content() } } } }` (159-177).
  - Category rows are a `Row` with `clickable(indication = null)` (430-481; they are highlighted only in search mode, 450-456). Book rows use `SelectableRow` (483-523, `app/core/presentation/components/SelectableRow.kt:23-47`).
  - There is no focus row and no key handler. `CategoryBookTreeView.kt` is a Zayit file.
- **Panel:** `CategoryTreePanel` (`CategoryTreePanel.kt:31-94`, changed by Pardes) → `NavigationTree` → `MyPdfBooks(…)`, which wraps the tree.
  - `MyPdfBooks` adds the root "הספרים שלי" (id −28), the row "הוספת תיקייה…" (id −29), and the user's folders (`app/features/pdf/MyPdfBooks.kt:30-37, 64-91`). Its own `click` handles the add-folder row (80-86).
  - A book click with Ctrl/Cmd sends `BookSelectedInNewTab`; otherwise it opens the book (`CategoryTreePanel.kt:113-124`).
  - A category click sends `CategorySelected`, which selects the category and **toggles** its expansion (`app/features/bookcontent/usecases/NavigationUseCase.kt:69-95`).
  - Home and book tabs show the same panel (`app/features/bookcontent/ui/panes/ReaderPanes.kt:758-764`, `WindowBody.kt:62-63`). Search tabs use `SearchResultCategoryTreeView`, with checkboxes (`CategoryBookTreeView.kt:180-331`).
- **The only keyboard path today** is the pane's search field:
  - A letter typed while the pointer is over the pane opens the search (`TypeToSearchTarget`, `CategoryTreePanel.kt:41-44`).
  - In the field, ↑/↓ move between matches, Enter opens, Esc closes (`app/features/bookcontent/ui/components/PaneSearch.kt:83-89, 228-243`).

**Jewel 0.41 tree API (from the sources jars)**
- `LazyTree(tree, …, treeState, keyActions = DefaultTreeViewKeyActions(treeState), …)` is marked `@ExperimentalJewelApi` (jewel-ui `LazyTree.kt:83-96`).
- Keys it handles:
  - ↑/↓ and Home/End (foundation `lazy/Keybindings.kt:103-125`).
  - PageUp/PageDown only in `SelectionMode.Multiple` (`tree/KeyActions.kt:414-435`).
  - **← = parent/collapse, → = child/expand, hard-coded** (`tree/DefaultTreeViewKeybindings.kt:19-29`). The class is `open`, so a right-to-left subclass is easy.
  - F2 = edit. There is no Enter binding.
- `onSelectParent` collapses an open node, otherwise moves to the previous row; `onSelectChild` opens a closed node, otherwise moves to the next row (`DefaultTreeViewOnKeyEvent.kt:11-32`).
- Type-ahead is separate: `SpeedSearchArea` + `SpeedSearchableTree` (`search/SpeedSearchableTree.kt:82-85`, experimental).
- **Cost of moving to `LazyTree`:** `TreeState.openNodes` would duplicate `NavigationState.expandedCategories` (saved per tab), plus the custom scroll restore and category reveal (`CategoryBookTreeView.kt:114-154`).
- **Middle option:** `SelectableLazyColumn(keyActions = …)` (foundation `SelectableLazyColumn.kt:162-176`) keeps the app's own flat list and adds keyboard selection.

**How code editors do it** (IntelliJ Project view, VS Code Explorer, WAI-ARIA tree)
- ↑/↓ move a focus row that is separate from the open item. Home/End go to first/last, PageUp/PageDown by a page.
- Expand key: opens a closed node, or goes to its first child. Collapse key: closes an open node, or goes to its parent.
- Enter opens a leaf or toggles a folder. Ctrl/Cmd+Enter opens "to the side", which here means a new tab, like Ctrl+click.
- Type-ahead search. A shortcut that focuses the tree (IntelliJ Alt+1, VS Code Ctrl+Shift+E). Shift+F10 opens the row's menu.

**Right-to-left note**
- The whole app is right-to-left (`app/main.kt:265`), and `ChevronRight` has `autoMirror = true` (`icons/ChevronRight.kt:21`, drawn by `ChevronIcon.kt:21-33`). So a closed node's chevron points **left**.
- That means **← should expand / go to the first child, and → should collapse / go to the parent** — the opposite of Jewel's mapping.
- Zayit already does this for arrows elsewhere:
  - `val towardsEnd = (event.key == Key.DirectionLeft) == isRtl` (`…/views/BreadcrumbPopup.kt:145-149`).
  - `val forward = (accumulated > 0) != isRtl` (`CommentatorsGrid.kt:387`).

**Where the change goes**
Copy the search-results list pattern:
- the key handler at `app/features/search/SearchResultScreen.kt:361-379`;
- `.focusRequester(listFocus).onPreviewKeyEvent(::onListKey).focusable()` at 533-541;
- the row click also calls `listFocus.requestFocus()` (569-572).

Steps in `CategoryBookTreeView.kt`:
1. Add the category or book, the expanded flag and the parent id to `TreeItem` (47-52), and fill them in `buildTreeItems`.
2. Add a `focusedId` state and the handler on the Box/LazyColumn (159-177).
3. Scroll with the existing `LazyListState.revealItem` (`PaneSearch.kt:156-165`).
4. Enter must call the `onBookClick` the panel passes in, so the add-folder row of "הספרים שלי" and Ctrl → new tab keep working.
5. For expand-only / collapse-only, check `expandedCategories` before sending `CategorySelected`, because that event toggles.

Put the right-to-left key logic in a small Pardes helper so `BookTocView` can reuse it.

**Existing keyboard support**
- **Window shortcuts:** handled twice, in the window's `onKeyEvent` and in a Box `onPreviewKeyEvent` (`app/core/presentation/window/MainAppWindow.kt:168-263` and `341-489`).
  - New tab, close tab (pinned tab needs a second press, `TabsViewModel.kt:129-143`), close all, reopen closed tab, next/previous tab (Ctrl+Tab).
  - Tab search (Ctrl+Shift+A), History, Favorites, Home in the current tab, Settings, desktops, Cmd+M, F11.
  - Then the reader shortcuts (246), then `processKeyShortcuts` (249-257). Typed characters nothing else took go to `TypeToSearch` (259-262).
- **`processKeyShortcuts`:** Ctrl+F find, Ctrl+G / Ctrl+Shift+G, Ctrl +/=/− zoom, F3 / Shift+F3 (`app/core/presentation/utils/WindowUtils.kt:32-110`).
- **Reader shortcuts:** Ctrl+B tree (Shift: contents); Ctrl+K commentaries (Shift: targum, Alt: sources, Alt+Shift: references); Ctrl+J nikud (`app/features/bookcontent/BookContentScreen.kt:975-997`, wired in `WindowBody.kt:65-74, 95`).
- **Book text:** ↑/↓ change line (on a daf they step through the selected comment), PageUp/PageDown, Ctrl+Home/End, Esc closes find (`…/views/BookContentView.kt:900-977`, attached at 1074; focus is requested at 986-1001).
- **`TypeToSearch`:** decides by pointer hover, not focus (`app/features/bookcontent/ui/components/TypeToSearch.kt:18-90`). It works only in docked panes (`docs/ZAYIT_CHANGE_MAP.md:5968`).
- **Search results:** ↑/↓/Enter (`SearchResultScreen.kt:361-379`). The screen-level handler is a no-op (345, 389).
- **Other places with keys:**
  - Find bar (`FindInPageBar.kt:116-135`).
  - Breadcrumb popup (`BreadcrumbPopup.kt:125-153`).
  - Home search bar (`HomeView.kt:1705-1850`).
  - Text context menu (`BookContentScreen.kt:403-445`).
  - Daf Ctrl+C / Esc (`daf/DafView.kt:451-466`).
  - PDF PageUp/PageDown, Ctrl+Home/End (`features/pdf/PdfPageList.kt:236-253`).
  - Rename fields (`ListPageComponents.kt:580-590`, `DesktopSwitcher.kt:417-427`).

**Places with no keyboard support**
- **Book tree and search-filter tree:** described above.
- **TOC:** a custom LazyColumn with no keys (`…/booktoc/BookTocView.kt:126-155`; row click toggles or opens at 288-294). Only its search field responds to keys.
- **Commentaries pane:** pages turn by wheel, swipe or dots only (`CommentatorsGrid.kt:326-395, 482-552`). The commentators list has no arrow keys.
- **Search results:** no Home/End/PageUp/PageDown.
- **Tabs:** no Ctrl+1…9, Ctrl+PageUp/PageDown, move-tab shortcut, Shift+F10 menu, or duplicate. Tabs use `.selectable` but cannot be moved between with arrows (`TabsView.kt:964`).
- **Popups and lists without arrow keys:** `TabSearchPopup`, `FavoritesPopup`, `NotesPopup`, the History/Favorites/Notes pages, and `NotesPanel`.
- **Panes floated in their own window:** they get none of the window-level shortcuts.

---

## B. Commentary title not clickable in a separate pane

**Commentaries pane (works)**
- Each column header is `CommentatorHeader` (`…/views/CommentatorsGrid.kt:556-656`). Its Box is the tear-off grip plus `clickable(onClick)` plus a hover underline (593-611).
- The click is `onTitleClick = { config.onOpenCommentatorBook(id) }` (441, wired through `CommentatorCell` 455-476).
- The callback is built in `LineCommentsView.kt:1660-1667`:
  ```kotlin
  onOpenCommentatorBook = { bookId ->
      val baseLineIds = when (selection) { is LineSelection.Single -> listOf(selection.lineId); is LineSelection.Multi -> selection.lineIds }
      onEvent(BookContentEvent.OpenBookByIdInNewTab(bookId, baseLineIds))
  }
  ```
- The ViewModel opens the commentator at the line linked to the base lines, or at the book's start (`BookContentViewModel.kt:592-608`).

**Separate pane (broken)**
- Path to the pane:
  - The header is the tear-off grip and also has an "open in window" button (`CommentatorTearOffs.kt:61-72`; the fork's `satelliteTearOffHandle`, `nucleus-fork/README.md:8-13`).
  - It becomes `ReaderPane.Commentator(name)` (`ReaderPanes.kt:180-200`), declared in `WindowPanes` (290-345).
  - `is ReaderPane.Commentator ->` (812-816) → `DetachedCommentatorPane` (`BookContentPanel.kt:426-444`) → `DetachedCommentatorView` (`LineCommentsView.kt:257`; Pardes added it inside a Zayit file).
- The pane's title is the generic pane header:
  ```kotlin
  PaneHeader(label = commentator, interactionSource = paneInteractionSource, onHide = reattach, actions = { ReattachCommentatorButton(…) })   // LineCommentsView.kt:286-293
  ```
  `PaneHeader` (a Zayit file) draws a plain `Text(label)` with no click (`PaneHeader.kt:88-92`). Its header Column is only the pane's drag handle (76).
- `DetachedCommentatorColumn` (341-411) already finds the commentator's book (`commentatorId`, 354-357) and even builds `config` with `onOpenCommentatorBook` (365-374). But it calls `CommentariesPagedList` directly (386) and never draws a `CommentatorHeader`, so the callback is never used.

**Where to add it**
1. **Make the title itself clickable (recommended).**
   - Add `onLabelClick: (() -> Unit)? = null` to `PaneHeader` (`PaneHeader.kt:56-61`). On the label (88-92) add a hand cursor, `clickable` and a hover underline, as at `CommentatorsGrid.kt:598-611`.
   - In `DetachedCommentatorView`, find the commentator's book id before the header: move the lookup from 354-357 up; the per-branch `groups` are at 296-336.
   - Pass the same `OpenBookByIdInNewTab(id, baseLineIds)` lambda. Ideally pull the lambda at 1660-1667 into a shared helper.
   - `PaneHeader` is a Zayit file, so this needs a change-map entry.
   - The drag handle and click already coexist in `CommentatorHeader`. Check in the app that dragging the docked pane from its title still works.
2. **No Zayit file touched.** Draw `CommentatorHeader(commentator, textSizes.commentTextSize, onClick = { config.onOpenCommentatorBook(commentatorId) })` before `CommentariesPagedList` (386). This matches the grid's look, but the name appears twice.

---

## C. Settings doesn't come to the front when minimized

**What happens on a second open**
- The settings window became its own window in Pardes (stage 2 request 35; `docs/ZAYIT_CHANGE_MAP.md:1825-1842`).
- Opening it again bumps a counter: `private fun reopened(): Int = _state.value.let { if (it.isVisible) it.reopened + 1 else 0 }` (`app/features/settings/SettingsWindowViewModel.kt:23-44`). `main.kt:439-447` passes it to the window.
- The window then runs this (`SettingsWindow.kt:71-85`):
  ```kotlin
  val settingsWindowState = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(700.dp, 500.dp))
  JewelDecoratedWindow(onCloseRequest = onClose, …, state = settingsWindowState) {
      val window = nucleusWindow
      LaunchedEffect(reopened) {
          if (reopened == 0) return@LaunchedEffect
          window.setMinimized(false)
          window.toFront()
          window.requestFocus()
      }
  ```
- Triggers: the title-bar button (`TitleBarActionsButtonsView.kt:165-172`), Ctrl/Cmd+, (`MainAppWindow.kt:220-222, 443-446`), and the macOS Preferences item (`AppNativeMenuBar.kt:186-193`).

**Why it fails when minimized**
- The effect sits inside the window's own content, and that content only updates when the window draws a frame.
- In the Nucleus tao jar, each window has its own frame-driven recomposer (`scene.TaoSceneBundle.frameRecomposer`). `TaoComposeSceneHost.renderFrameSuspending` returns early when `isOutOfSight()` = `window.isMinimized() || occluded`.
- So while the window is minimized, the new `reopened` value never reaches the effect, and nothing runs.
- Pardes already wrote this down: "a window out of sight (hidden app, covered, minimized) renders no frame, so nothing recomposes" (`MainAppWindow.kt:316-318`). Minimizing was never tested (`ZAYIT_CHANGE_MAP.md:1841-1842`).
- Main windows do come back, because `OpenWindow.requestFocus()` (`OpenWindow.kt:117-123`) is called from outside any window's content (`DesktopManager.kt:236-240, 269-273, 443`).
- Zayit's tool windows have the same bug (`app/features/home/widgets/ToolWindows.kt:110-124`).

**Fix:** un-minimize from `SettingsWindowView`, outside the window content, where code keeps running while the window is minimized:
```kotlin
val settingsWindowState = rememberWindowState(…)        // SettingsWindow.kt:71-72
LaunchedEffect(reopened) {                                // outside JewelDecoratedWindow
    if (reopened > 0) settingsWindowState.isMinimized = false
}
```
- This works because Nucleus's `DecoratedWindow` runs `LaunchedEffect(window, state.isMinimized) { if (state.isMinimized != applied.isMinimized) { window.setMinimized(…); applied… } }` (`DecoratedWindowComposableKt$DecoratedWindow$13$1`) in the caller's composition.
- When the user minimizes, the native listener writes `state.isMinimized = true` back (`…DecoratedWindow…lambda$11$4`).
- Once the window draws again, the existing effect (80-85) calls `toFront()` and `requestFocus()`.
- A sturdier version: store the `nucleusWindow` handle in an outer `remember` (as `MainAppWindow.kt:265-273` does) and call `setMinimized(false)`, `toFront()`, `requestFocus()` from the outer effect.
- Apply the same change in `ToolWindows.kt:110-124`.

---

## D. Notes icon for right-to-left

**Sources**
- **`NotebookPen` vector** (Lucide notebook-pen; `icons/NotebookPen.kt:11-68`; Zayit file). The builder has **no `autoMirror`** (17-23).
  - The stripes are `moveTo(2f, 6f) / horizontalLineToRelative(4f)` four times, at x 2–6, on the left (38-45). The outline is at 30-37 and the pen at top-right (53-61).
  - Used by:
    - the notes pane toggle (`VerticalBars.kt:78`);
    - the Notes tab icon (`TabsView.kt:256, 348`, via `rememberTintedVectorPainter`, which keeps `autoMirror`, 2015-2028);
    - tab search (`TabSearchPopup.kt:372`);
    - the Home notes widget (`LibraryWidgets.kt:264`, drawn by 348-354);
    - the notes page rows (`NotesScreen.kt:122` → `ListPageComponents.kt:347-353`).
  - Android compiles this folder too (`androidApp/app/build.gradle.kts:174-175`).
- **Title-bar notes button and popup footer** use an SVG, not the vector: `PathIconKey("icons/notes.svg", …)` (`NotesPopup.kt:43-46, 58, 133`).
  - Files: `SeforimApp/src/jvmMain/resources/icons/notes.svg` and `notes_dark.svg`. Same path, stroke 1.5:
  ```xml
  <path d="M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4M2 6h4M2 10h4M2 14h4M2 18h4" …/>
  <path d="M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z" …/>
  ```
- **macOS menu** uses the system symbol `note.text` (`AppNativeMenuBar.kt:394`).

**Is it mirrored anywhere?** No.
- 10 other icons in the module set `autoMirror = true`, for example `JournalText.kt:21` and `ChevronRight.kt:21`.
- Jewel's `Icon(imageVector)` uses `rememberVectorPainter`, which respects `autoMirror` (jewel-ui `Icon.kt:248-254`).
- One icon is flipped by hand with `graphicsLayer(scaleX = -1f)` (`TabsView.kt:1565, 2002`).
- Jewel's SVG painters never mirror.

**How to make the right-to-left version**
1. **Vector:** add `autoMirror = true` to the builder (`NotebookPen.kt:17-23`). In every vector use the stripes move to the right and the pen to top-left; Android follows too.
2. **SVGs:** mirror both `notes.svg` and `notes_dark.svg`. Either wrap the paths in `<g transform="matrix(-1 0 0 1 24 0)">…</g>`, or use this mirrored path data:
   `M10.6 2H18a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-7.4M22 6h-4M22 10h-4M22 14h-4M22 18h-4`
   and for the pen `M2.622 5.626a1 1 0 1 1 3.004-3.004l5.01 5.012a2 2 0 0 1 .506.854l.837 2.87a.5.5 0 0 1-.62.62l-2.87-.837a2 2 0 0 1-.854-.506z`.
3. **A redrawn glyph instead,** done the way Pardes added the daf marks icon:
   - `icons/MarkedLine.kt:11-62`: an ImageVector built once and cached, 16-unit viewport.
   - Used by `DafMarksButton.kt:21-33`.
   - Change-map entry at `ZAYIT_CHANGE_MAP.md:2431-2442`: "one of three proposals shown to the user".
   - The stage 2 plan's rule is to show a new icon to the user before building on it (`docs/DESKTOP_STAGE2_PLAN.md:40`).

Options 1 and 2 change Zayit files, so both need change-map entries.

---

## E. Duplicate tab, and grouped tabs

**Tab system map**
- **Models:**
  - `TabsDestination` (`nav/TabsDestination.kt`);
  - `TabItem(id, title, destination, tabType, pinned, shortTitle)` (`nav/TabsState.kt:6-17`);
  - `TabsEvents`: OnClose, OnSelect, OnAdd, OnReorder, CloseAll, CloseOthers, CloseLeft, CloseRight (`nav/TabsEvents.kt:3-33`).
- **`TabsViewModel`** (one per window; `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforim/tabs/TabsViewModel.kt`):
  - `onEvent` 95-117, `setPinned` 122-127, `closeTab` 149-158;
  - `openTab` puts the new tab next to the current one (191-193);
  - `withTabId(newId)` 226-238.
- **`DesktopSession`** (`app/framework/desktop/DesktopSession.kt`):
  - owns the tabs and a Nucleus `TabWorkspace` (55);
  - `addTab` 132, applied when the tab is declared (160);
  - `updateDestination` 168, `setPinned` / `pinConstrained` 198-225;
  - closed-tab stack 238-260;
  - `stripEphemeral` 381.
- **Tab declaration:** `app/core/presentation/window/DesktopTabs.kt`.
- **Tab strip:**
  - `TabsView.kt`: `TabEntry` 143-167; the entry builders, right-to-left 233-327 and left-to-right 328-418; `RtlAwareTab` 845;
  - `TabStripLayout.kt`, `PinnedTabLabel.kt`, `ClosePinnedTabHint.kt`.
- **Saved state per tab:** `TabPersistedStateStore` (`app/framework/session/TabPersistedStateStore.kt:12-58`) holding `TabPersistedState(bookContent, search, siddur)` (`SessionModels.kt:30-36`).
  - The book state includes open panes, split-off commentators and daf/scan view.
  - Thumbnails: `TabThumbnailStore.copy` (44-49).
- **Moving tabs between windows:**
  - `BindTabGroupWindow` (`MainAppWindow.kt:291-298`);
  - `DesktopManager`: `linkWorkspaces` / `onForeignDrop` (`DesktopManager.kt:432-466`), `detachTabToNewWindow` via `workspace.tearOff` (321-356), `moveTabToDesktop` (364-403), `addMovedTab` (405-424), `reopenClosedTab` (198-212).

**Duplicate tab**
The template is `addMovedTab` without discarding the source:
```kotlin
// DesktopManager (it has tabPersistedStateStore + thumbnails), next to addMovedTab
val state = tabPersistedStateStore.get(tabId) ?: TabPersistedState(); val newId = UUID.randomUUID().toString()
tabPersistedStateStore.putAll(mapOf(newId to state)); thumbnails.copy(tabId, newId)
win.session.addTab(dest.withTabId(newId), win.groupId, index = indexOfSource,
    title = item.title, tabType = item.tabType, pinned = item.pinned, shortTitle = item.shortTitle)
```
Pitfalls:
1. A destination's `lineId` / `endLineId` / `openNotes` would override the copied reading place ("Explicit line navigation wins", `BookContentViewModel.kt:306-327`). Strip them with `DesktopSession.stripEphemeral` (381-385).
2. A tab's destination keeps the `bookId` the tab was opened with. Moving to another book inside the tab only updates the saved `selectedBookId`, and the ViewModel prefers the destination's id (`bookIdToOpen = argBookId ?: persistedBookId`, `BookContentViewModel.kt:290-291`; arguments seeded at `TabsContent.kt:645`). So build the duplicate's destination from `state.bookContent.selectedBookId`, as `SessionManager.kt:214` does for titles.

UI wiring:
- Add `TabEntry.onDuplicate` and fill it in both builders (298-326, 390-417).
- Add an item to `TabActionsMenu` (1456-1571), whose call sites are 1136-1153 and 1405-1421.
- Add a string next to the tab strings (`SeforimApp/src/commonMain/composeResources/values/strings.xml:344-358`).
- Optionally add an item to the macOS File menu (`AppNativeMenuBar.kt:204-218`).

Chrome puts the copy next to the original, selected, and pinned if the original was pinned.
- Chrome has no shortcut for Duplicate.
- Edge's Ctrl+Shift+K is already the targum shortcut here (`BookContentScreen.kt:986-987`).
- A test fits in `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/desktop/DesktopManagerTest.kt`.

**What exists for grouping**
- **Pinning only:** `pinned` with `pinConstrained` through `workspace.constrainIndex` (`DesktopManager.kt:456-458`), plus a pinned tab's width and label.
- **Virtual desktops:** named sets of tabs ("Move to …" in the tab menu, `TabsView.kt:1510-1521`).
- **No colours or groups:**
  - Nucleus `TabWorkspace` groups are windows (`TabWindowGroup`).
  - Its `TabEntry` has only id, title, group, selected and thumbnail (checked with `javap`).
  - Theme tab colours only colour the selected tab's underline (`IslandsComponentStyling.kt:130, 218`).
- **No split view** of two tabs in Zayit or Pardes. Within one tab there are only the reader panes and the daf/PDF two-page spreads (`AppSettings.kt:50`). Side by side today means detaching a tab to a new window.
- **Chrome-style groups would need:**
  - `TabItem.groupId` plus a group record (name, colour, collapsed) in `DesktopSession`, saved in `SerializableTabTitle` / `WindowSnapshot`;
  - the joining line drawn in `RtlAwareTab` / `TabStripLayout`;
  - groups kept together through `constrainIndex`, the same way as pins;
  - menu items for adding to and removing from a group.

---

## F. Tab context menus and tab shortcuts

**Menus**
1. **Right-click on a tab** (`TabsView.kt:973-982` → `TabActionsMenu` 1137 / 1456-1571):
   - Pin / Unpin (1492).
   - Open in new window, only when the window has more than one tab (1500).
   - Move to <desktop>, one item per other desktop, also only with more than one tab. They are flat items because Jewel submenus always open to the right (1510-1521).
   - Copy link, when the tab has a shareable link (1523).
   - Close all (1533).
   - Close others, with more than one tab (1541).
   - Close tabs on one side / the other (1551-1570). The Hebrew strings are swapped for right-to-left, and so is the left/right mapping (304-306).
2. **Right-click on a row of the overflow list:** the same menu (1384-1388 → 1405-1421).
3. **Right-click on the overflow chip:** "Close hidden tabs" (only when some tabs are visible) and "Close all" (1198-1241).
4. **Middle-click** closes a tab unless it is pinned (975, 1382).

**Tab shortcuts** (`MainAppWindow.kt:168-263` and `341-489`)

| Action | Keys |
|---|---|
| New tab | Ctrl/Cmd+T |
| Close tab (pinned: press twice) | Ctrl/Cmd+W |
| Close all tabs | Ctrl/Cmd+Shift+W |
| Reopen closed tab | Ctrl/Cmd+Shift+T |
| Next / previous tab | Ctrl+Tab / Ctrl+Shift+Tab |
| Tab search | Ctrl/Cmd+Shift+A |
| Current tab → Home | Alt+Home (Cmd+Shift+H on macOS) |
| History tab | Ctrl+H (Cmd+Y on macOS) |
| Favorites tab | Ctrl+Shift+O (Cmd+Alt+B on macOS) |
| Desktops | Ctrl+N, Ctrl+Alt+N, Ctrl+Alt+←/→ |

- The macOS File menu has New Tab, Close Tab and Close All Tabs with no shortcuts set (`AppNativeMenuBar.kt:204-218`).
- **Missing:** Ctrl+1…9, Ctrl+PageUp/PageDown, Ctrl+Shift+PageUp/PageDown to move a tab, Duplicate, and Shift+F10 / the Menu key to open the tab menu.

## The brief the agent got

> Read-only exploration of the Pardes desktop app (Kotlin, Compose Multiplatform + Jewel) at /home/user/Pardes. Thoroughness: very thorough. Do NOT edit anything. Report file:line for every claim. Main code: `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/`. Pardes is a fork of the Zayit desktop app; `docs/ZAYIT_CHANGE_MAP.md` says which files Pardes added or changed (grep it for a file name). `TAB_SYSTEM_README.md` describes the tabs; the window/docking uses Nucleus (a fork is in `nucleus-fork/`).
>
> I need a precise map for these user requests (answer each separately: today's behaviour with its code path, and where a change would go):
>
> A. Keyboard support in the book tree ("there's no keyboard support in the book tree; how is it usually done in code editors? where else can keyboard support be added?"). Find the book tree (category tree panel under features/bookcontent/ui/panels/categorytree, the home/library tree, "הספרים שלי" PDF books there) and how it is rendered (Jewel LazyTree? custom LazyColumn?). Does Jewel's tree component support keyboard navigation (Jewel `LazyTree`/`BasicLazyTree` have `KeyActions`/`DefaultTreeViewKeyActions` with up/down/left/right/Home/End/type-ahead)? Check the Jewel version in gradle/libs.versions.toml and whether its sources/jars are available locally (~/.gradle caches or `jewel/` module) to confirm the API. Also find where keyboard shortcuts already exist in the app (reader keys, Page Up/Down, Ctrl+F, Ctrl+K, Ctrl+W tab close, Ctrl+Tab, the TOC "type to search" `TypeToSearch.kt`, the search results list arrow keys, `onPreviewKeyEvent`/`onKeyEvent` usages) and list places that lack keyboard support (TOC panel arrow navigation, commentaries pane, search results list, tabs). Note RTL: in Hebrew, Right/Left meaning for expand/collapse.
>
> B. "When a commentary is in a separate pane you can't click the commentary's title to open the book." Find the commentaries pane and its header with the commentator's name (clickable title opening the commentator's book in a tab), and the "separate pane" mode (`features/bookcontent/ui/panes/CommentatorTearOffs.kt`, ReaderPanes.kt, nucleus-fork) where a commentator is shown in its own pane. Find why the title isn't clickable there and where to add it, reusing the existing open-book callback.
>
> C. "Clicking Settings will bring it to the front even when it was minimized." Find the settings window (stage 2 item 35 turned it into its own window, like `ToolWindows.kt`); how a second open brings it forward (toFront / requestFocus) and why it fails when the window is minimized (needs `WindowState.isMinimized = false` or `ComposeWindow.extendedState`/`toFront()` after un-minimize). Show the exact code.
>
> D. "Fix the notes icon to fit Hebrew right-to-left — the stripes are on the left of the notebook." Find the notes icon used in the app (the notes pane toggle in VerticalBars.kt or the pane header, Home notes widget, the `icons` module, composeResources drawables/svg), show its source (SVG/ImageVector path), and whether the icon is mirrored in RTL elsewhere. Say how to make an RTL version (mirror horizontally, or a new vector) following how Pardes added other icons (e.g. the daf marks icon in stage 2 item 33, `DafMarksButton.kt`).
>
> E. "Option to duplicate a tab, and showing connected tabs like in Chrome." Map the tab strip (the TabsContent / tab bar composables, TabsViewModel openTab/close/reorder, the tab context menu if any, TabsDestination, TabStateManager saved state per tab) and multi-window tab drag (TabDockManager, DesktopManager). Say how 'Duplicate tab' could be implemented (copying a tab's destination and its saved state under a new tabId) and what exists for grouping/colour/pinning of tabs. Also check if Zayit has a split view (two tabs side by side) or tab groups.
>
> F. Also briefly: list every right-click/context menu on a tab and the keyboard shortcuts for tabs.
>
> Keep the report structured A–F, concise, with exact file:line references and short decisive code excerpts.

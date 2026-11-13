# Code map: the reader's menu and contents pane, the fonts, the daily-study widget and the user's data

- **For:** stage 2 of Pardes for desktop, requests 32, 24, 25, 19 and the backup's part of 25 and 28
  (`docs/DESKTOP_STAGE2_PLAN.md`).
- **When:** 2026-11-11. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `abcb687` (main of that day). The line numbers are that commit's.
- **By:** an Explore agent of Claude Code, one of four run side by side. The report is as it came back; the brief
  it got is at the end.
- **Not checked by hand.** Its last section says what it could not confirm: the SeforimLibrary submodule was not
  initialised in the checkout it read, and Jewel's sources were not at hand.

## The report

All paths are relative to P = /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp
unless shown in full.

### A. "פתח במילון" (open in dictionary)

- String key: strings.xml:364 `context_menu_open_in_dictionary`.
- Reader: features/bookcontent/BookContentScreen.kt, `BookTextMenus` (459). The label is at 472. The menu is
  Compose's `TextContextMenu` overridden as `object : TextContextMenu { Area(...) }` (503), with
  `ContextMenuDataProvider(items = { buildList { … } })` (571). The dictionary item is at 675-685:
  `lookupWord(copiedText)?.let { word -> add(ContextMenuItemOption(icon = AllIconsKeys.Toolwindows.Documentation, label = openInDictionaryLabel) { dictionaryLookup.open(word) }) }`.
  - `copiedText = owned?.text ?: selectedText` (577).
  - Items use Jewel's `org.jetbrains.jewel.ui.component.ContextMenuItemOption` (import at 88).
  - Drawing is the app's own `BookContentContextMenuRepresentationWithKeybindings` (139), installed at 838, which
    calls `contextItems` (206-251). For `is ContextMenuItemOption` it calls
    `selectableItemWithActionType(selected=false, onClick, iconKey=item.icon, actionType=item.actionType, enabled=item.enabled)`
    (237-246). The app's own
    `ContextMenuItemOptionWithKeybinding(icon, keybinding, label, action) : ContextMenuItem` (124-129) also passes
    `enabled = item.enabled` (232).
  - So the drawing path already forwards `enabled` to Jewel's menu item. No call site in the app sets it to false
    today.
- Search-results passage: features/search/PassageReading.kt, `PassageText` (224). It uses the same pattern:
  `TextContextMenu` + `ContextMenuDataProvider(items = { actions.items(...) })` (236-246). The item is in
  `PassageActions.items` at 351:
  `lookupWord(selected)?.let { word -> add(option(AllIconsKeys.Toolwindows.Documentation, labels.dictionary) { lookUp(word) }) }`.
  - `option()` builds a Jewel `ContextMenuItemOption(icon, label, action)` (358-362).
  - `lookUp = dictionary::open` (390), and the label is at 376.
  - This file sets no representation of its own: it uses whatever `LocalContextMenuRepresentation` is in scope.
- When the item shows, in both places: `DictionaryWindow.kt:29 internal fun lookupWord(selection: String): String?`.
  It keeps only the letters (`lettersOnly`), splits on spaces and returns null unless there are 1 to 4 words
  (`MAX_WORDS = 4`, line 25). It does not check whether an entry exists.
- What the item calls: features/home/widgets/library/DictionaryWindow.kt:62 `class DictionaryLookup`
  (`@SingleIn(AppScope)`), `fun open(word: String)` (68). This sets `word` and calls
  `toolWindows.open(DictionaryWidget)`. It is exposed as `AppGraph.dictionaryLookup` (framework/di/AppGraph.kt:85).
- Prefix stripping: DictionaryWindow.kt:44
  `internal fun lookupAsRead(entries: List<DictionaryEntry>, query: String): List<DictionaryEntry>`.
  - Not suspend and pure: it works on a list already in memory and drops at most 2 of `PREFIXES = "והבכלמשד"`,
    keeping words longer than 3 letters (36-38).
  - Its helpers are `lookup()` at DictionaryWidget.kt:252 and `normalizeWord` at 249.
- Where the entries come from: DictionaryWidget.kt `readDictionaries(graph, dictionaries)` (273), which is suspend.
  - It reads books of the library DB through `SeforimRepository` (`readBook` 304, `readIndexed` 322), plus the
    bundled `KleiKodeshPack` texts. It does not use lexical.db.
  - Results are cached per dictionary in
    `private val cache = ConcurrentHashMap<Dictionary, List<DictionaryEntry>>()` (263) behind `Mutex reading` (266).
  - `lookupAsRead` is called only inside the composable at 421.
- There is no cheap "does any of these words have an entry" call. One would be new; the cache is private to
  DictionaryWidget.kt.

### B. Table of contents pane and keyboard input

- Header: features/bookcontent/ui/panels/booktoc/BookTocPanel.kt `BookTocPanel` (33). `PaneHeader(...)` is at 50,
  and
  `PaneSearchButton(isOpen = uiState.toc.search != null, focus = searchFocus, onClick = { onEvent(ToggleTocSearch) })`
  at 56-61. The pane is `.hoverable(paneHoverSource)` (48).
- Search parts, all in features/bookcontent/ui/components/PaneSearch.kt:
  - `PaneSearchFocus.requested` (55) and `rememberPaneSearchFocus` (60).
  - `PaneSearchButton` sets `focus.requested = !isOpen` (73).
  - `PaneSearchLayout` (87), which tracks `activeId` and `pendingSubmit`.
  - `PaneSearchField` (167): `LaunchedEffect(focus.requested) { focusRequester.requestFocus() }` (196-201); the
    query is debounced 120 ms (203-210); `onPreviewKeyEvent` handles Enter → `onSubmit`, ↑/↓ → `onMove`, Esc →
    `onClose` (220-235).
- State: `PaneSearchState<T>(query, result, resultQuery)` at features/bookcontent/state/BookContentState.kt:156;
  `TocFilterResult` at 168; `TocState.search` at 142.
- Events and handling:
  - BookContentViewModel.kt:501-511 maps `ToggleTocSearch` → `tocUseCase.toggleSearch()` and
    `TocSearchQueryChanged` → `tocUseCase.search(query)`. `TocSearchEntrySelected` → `openTocSearchMatch` (1163).
  - In usecases/TocUseCase.kt: `toggleSearch` 203, `closeSearch` 211, `search` 217 (calls `buildTocFilter` on
    Dispatchers.Default), `selectSearchEntry(tocId): Long?` 255 (expands the path, closes the search, returns
    `entry.lineId`).
  - The filter itself: usecases/TocSearch.kt:85 `buildTocFilter`.
- Book tree pane: features/bookcontent/ui/panels/categorytree/CategoryTreePanel.kt:42-62 has the same search
  (`PaneSearchButton` → `ToggleBookTreeSearch`, `PaneSearchLayout`, `minQueryLength = MIN_BOOK_QUERY_LENGTH`).
- Window-level keys:
  - core/presentation/window/MainAppWindow.kt `onKeyEvent` (167): Ctrl/Cmd combinations, F11, then
    `openWindow.readerShortcuts?.invoke` (245), then `processKeyShortcuts`.
  - Same file, `.onPreviewKeyEvent` (348): `selectionCopyShortcut` plus Ctrl shortcuts.
  - core/presentation/window/WindowBody.kt:94 `.onPreviewKeyEvent { handleBookShortcut(...) }`, also set as
    `openWindow.readerShortcuts` (73; field at framework/desktop/OpenWindow.kt:81).
  - `handleBookShortcut` (BookContentScreen.kt:940) returns false when neither Ctrl nor Cmd is held (946): it
    handles B, K, J only.
- Reader: features/bookcontent/ui/panels/bookcontent/views/BookContentView.kt `previewKeyHandler` (930-983),
  attached at 1073 together with `.focusable()` and a focusRequester (986-1001). Plain keys: ↑/↓ step a line,
  PageUp/PageDown scroll a page, Home/End only with Ctrl, Esc closes the find bar. No letter keys.
  - The daf view (features/bookcontent/daf/DafView.kt:442) handles only Ctrl+C and Esc, and only while a selection
    exists.
- Hover tracking:
  - Each pane uses `hoverable(MutableInteractionSource)` (BookTocPanel.kt:48, CategoryTreePanel.kt:36), read by
    `PaneHeader` through `collectIsHoveredAsState()` (PaneHeader.kt:62-63) to show its action buttons.
  - `onPointerEvent(Enter/Exit)` appears in BookContentView.kt:1667 and 1906, and in daf/DafReviewMenu.kt:65-66.

### C. Fonts

- core/presentation/typography/FontCatalog.kt:
  - `data class FontOption(code: String, label: StringResource)` (127).
  - `FontCatalog.options` is a persistentList of 27 entries (134-164).
  - `@Composable fun familyFor(code): FontFamily` is a `when` over the codes using
    `org.jetbrains.compose.resources.Font(Res.font.X, …)` (171-330). An unknown code falls back to Noto Serif Hebrew
    via `else ->` (329).
  - `boldScaleFor` (167).
- Storage in core/settings/AppSettings.kt:
  - Keys 969-974: `font_book`, `font_commentary`, `font_targum`, `font_source`, `font_daf_gemara`,
    `font_daf_commentary`.
  - Getters and setters 353-391, defaults 929-936.
  - All six keys are in `PORTABLE_KEYS` (857-862).
- Font pickers:
  - features/settings/ui/FontsSettingsScreen.kt:66 `val options = FontCatalog.options`, used by six
    `FontSettingCard`s (book, commentary, targum, sources, daf gemara, daf commentary; 77-123).
  - An unknown code shows index 0 in the picker (145).
  - No other place lists the catalog.
  - The daf uses `dafFamily(code)` (DafView.kt:343-348), a special case for notorashihebrew.
- Fonts come only from Compose resources: there is no `.ttf`, `Typeface` or `platform.Font(file)` usage in P.

### D. Daily-study widget (לימוד יומי)

- How widgets are declared and registered:
  - Contract: features/home/widgets/HomeWidget.kt `interface HomeWidget` (24): id, title, defaultSpan, minSpan,
    maxSpan, heightAt, settings, applySettings, toolWindowSize, optionsSize, Content, menuItems, Options, ToolWindow.
  - Registry: generated by KSP in
    /home/user/Pardes/widgetprocessor/src/main/kotlin/io/github/kdroidfilter/seforimapp/widgetprocessor/HomeWidgetProcessorProvider.kt:21-51,
    which writes `availableHomeWidgets` from every `object : HomeWidget`.
  - Defaults: `homeWidgets` (127) and `lowEndHomeWidgets` (137).
  - `availableTools` filter: ToolWindows.kt:41.
- Options dialog: WidgetOptionsDialog.kt:47. It shows `widget.Options(state)` (84) with Reset
  (`state.resetSettings`, 93) and Done; opened through `HomeWidgetsState.optionsOpen` (HomeWidgetsState.kt:182).
- Where choices are stored:
  - The layout is saved under AppSettings key `home_widgets_layout` (`setHomeWidgetsLayout` 530; key at 1004).
  - Per-widget options: `WidgetSettings(widgetId) : SettingsGroup("home_widget." + id)` (WidgetSettings.kt:10;
    prefix at AppSettings.kt:1010). These are portable (`isPortableKey`, 880).
  - Setting types available: core/settings/Setting.kt:27-66 (`boolean`, `float`, `stringSet`, `enumSet`,
    `enumOrNull`, `json`).
  - Read and write through `state[setting]` (HomeWidgetsState.kt:186-192).
- The limud widget, features/home/widgets/luach/:
  - LuachWidgets.kt:106 `object LimudWidget`: id "limud", spans 4x4 default, 4x2 min, 10x2 max.
    `LimudSettings.shown by enumSet(Limud.defaults)` (155-156); `LimudOptions` 161; `LimudPanel` 200.
  - LimudCycles.kt: `enum Limud(id, title, kicker, group)` (27) and `LimudGroup` (17).
  - LuachData.kt: `LibraryPlace(bookTitle, toc, ref, parashaIndex, endRefs, endTocs, parashaCount)` (52),
    `LimudItem` (63), `limudOfDay` (70).
- Opening the library: `rememberOpenInLibrary` (LuachWidgets.kt:654/661) calls `repository.getBookByTitle`, then
  `linesOf(book, place)` (690), then
  `TabsDestination.BookContent(bookId, tabId, lineId, endLineId, shnayimMikra)`. `linesOf` uses
  `getTocEntriesForBook` with `atPath` (721) and `entryEnd` (731).
- Calendar library: KosherKotlin (`io.github.kdroidfilter:kosherkotlin` 3.0.5, gradle/libs.versions.toml:43).
  Shabbat/Yom Tov checks (`isYomTov`, `isAssurBemelacha`) appear in LuachData.kt `upcomingEvents` (237-247).
  - Files that import KosherKotlin: LimudCycles, LuachData, LuachWidgets, ShnayimMikraWidget,
    temple/TempleCountdownWidget, zmanim/ZmanimWidget, features/author/AuthorScreen.kt, and the luach module.

### E. User data and backup

- `.sq` files are in /home/user/Pardes/SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/.
  Tables: category_display_settings, commentator_default, favorite_folder, favorite, reading_time, search_feedback,
  other_app_feedback, shnayim_mikra_aliya, user_highlights, user_notes, visit_history.
- Database: `UserSettingsDb` (SeforimApp/build.gradle.kts:365). It is opened in
  framework/di/modules/AppCoreBindings.kt:104-117 (`JdbcSqliteDriver` + `Schema.create`, which is CREATE IF NOT
  EXISTS) and 147-149.
- Data folder: `FileKit.databasesDir`. `getUserSettingsDatabasePath()` at
  framework/database/UserSettingsDatabase.kt:15 gives `<databasesDir>/settings/user_settings.db`.
- Backup:
  - /home/user/Pardes/backup/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/backup/BackupSource.kt:
    `snapshot(): BackupSnapshot(fingerprint, content: () -> ByteArray)`, `hasPendingRestore`,
    `stageRestore(ByteArray)`. The content is opaque bytes.
  - framework/backup/UserDataBackup.kt:30: the zip holds only `manifest.json` (with the preferences map) and
    `user_settings.db` (93-94). `readArchive` ignores any other entry (104-107). `FORMAT_VERSION = 1`.
  - Extra files would need a format change in UserDataBackup; the `BackupSource` interface itself is
    format-agnostic.

### F. TocEntry

- Fields, as constructed in P/features/bookcontent/usecases/TocSearch.kt:158-168: id, bookId, parentId, textId,
  text, level, lineId, isLastChild, hasChildren.
- TOC calls the app uses: `getBookRootToc(bookId)`, `getTocChildren(entryId)`, `getBookToc(bookId)`,
  `getTocEntriesForBook(bookId)`, `getTocEntry(id)`, `getLineIdsForTocEntry(id)`. Examples: TocUseCase.kt:47 and 80,
  search/domain/BookDetailsLoader.kt:52-53.

### Not confirmed

- /home/user/Pardes/SeforimLibrary is an empty, uninitialised submodule (`git submodule status` shows "-4f07145").
  I could not read the `TocEntry` source, the exact `SeforimRepository` signatures, or whether a "TOC entries at
  level N" call exists. Above, the field list comes from the app's constructor call and the call names from app
  usages.
- I could not open Jewel's source or jar in this container. I did not verify the exact constructor parameters of
  `ContextMenuItemOption`, or whether its `enabled` can be passed as false. The app reads `item.enabled` and
  `item.actionType` (BookContentScreen.kt:232, 242-243), and `ContextMenuItemOptionWithKeybinding` gets `enabled`
  from Compose's `ContextMenuItem`.
- Which context-menu representation is in scope for the search passage (PassageReading.kt) was not traced.

## The brief the agent got

Read-only code mapping in the Kotlin desktop app at /home/user/Pardes (Compose Multiplatform + Jewel). I am writing an
implementation plan and need exact anchors (file path + line numbers + function/val names), not design advice. Do not
change, build or run anything. Thoroughness: very thorough within the scope below.

Base package dir (call it P): /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp
Strings: /home/user/Pardes/SeforimApp/src/commonMain/composeResources/values/strings.xml
Settings keys: P/core/settings/AppSettings.kt

Answer each, with file:line anchors and a short quote of the deciding lines:

A. The "פתח במילון" (open in dictionary) item of the right-click menu. Find its string key in strings.xml, then
every place that adds it: in the reader (the book text's context menu) and in the passage shown beside the search
results. For each: file:line, how the menu is built (which context-menu API: Compose's ContextMenuArea / Jewel's /
the app's own; does that API support a disabled, greyed item — quote the item type's fields), what condition shows
the item today (e.g. selection length), and what it calls. Then the dictionary lookup itself: the function that
finds a word's entry after stripping prefix letters (אותיות השימוש) — look at
`git -C /home/user/Pardes show --stat --format='%h %s' b544755 befe3cb b261feb` for the files. Give its signature,
whether it is suspend / touches the database (which DB: lexical.db?), and whether there is a cheap "does any of
these words have an entry" call or one would be new.

B. The table of contents pane (תוכן העניינים) of the reader: the file and composable of its header, the search icon
in the header and the search field it opens (state holder, how the field gets focus, how the list filters, how
Enter/selection jumps). Then how keyboard input reaches the reader today: where key events are handled
(onPreviewKeyEvent / onKeyEvent / a shortcuts handler) at window level and in the reader, and which plain keys (no
Ctrl/Alt) already do something in the reader (arrows, PageUp/Down, letters?). Is pointer-hover over a pane tracked
anywhere (e.g. `onPointerEvent(Enter/Exit)`, `hoverable`) that a "type while hovering" feature could reuse? Does the
book tree pane (ספרים) have the same header search?

C. Fonts: P/core/presentation/typography/FontCatalog.kt — its structure (how a font is declared: id, label,
resource), how a chosen font id is stored (AppSettings keys) and resolved to a FontFamily, what happens with an
unknown id, and every place that lists the catalog for the user to choose from
(P/features/settings/ui/FontsSettingsScreen.kt, the daf fonts, any other). Are fonts loaded from Compose resources
only, or is there already any path that loads a font from a file on disk?

D. The daily-study widget ("לימוד יומי"): P/features/home/widgets/luach/ (LimudCycles.kt, LuachData.kt,
LuachWidgets.kt) and P/features/home/widgets/ in general. How is a Home widget type declared and registered (the
widget enum/registry, its sizes, its options dialog `WidgetOptionsDialog.kt`), where are the user's widget choices
and per-widget options stored, and how does the limud widget open the library at a line range
(`rememberOpenInLibrary`)? Which calendar library gives Shabbat/Yom Tov (KosherKotlin?) and where is it already
called here?

E. The user's own data: the SQLDelight user database (find the `.sq` files for the user/settings DB, likely under
/home/user/Pardes/SeforimApp/src/commonMain/sqldelight or similar; list its tables and the Kotlin class that opens
it, e.g. `UserSettingsDb`), where the app's data folder is resolved (the helper that returns the per-user data dir),
and what the backup carries (`UserDataBackup` in P/framework/backup/ and the /home/user/Pardes/backup module): user
DB + portable prefs — are arbitrary extra files (e.g. font files) supported by `BackupSource`, or only those two?

F. The book table-of-contents data a custom study plan would need: the `TocEntry` model in
/home/user/Pardes/SeforimLibrary (read only) — its fields (level, lineId, parent), and the repository calls that
list a book's TOC entries at a given level.

Report format: plain text, one section per letter, bullets with `path:line`. Put anything you could not confirm
under a final "Not confirmed" heading rather than guessing. No advice, no plan. Keep it under about 1000 words.

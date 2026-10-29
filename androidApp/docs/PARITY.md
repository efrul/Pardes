# Parity with desktop Zayit 0.6.11

The base is desktop Zayit 0.6.11 (upstream `kdroidFilter/Zayit` tag `v0.6.11`, SeforimLibrary `4f07145`) since 2026-10-28; it was 0.6.10 from 2026-10-26, 0.6.7 from 2026-10-20, and 0.6.1 before. The lists below started as a gap list from comparing the real 0.6.1 sources (tag `v0.6.1` and SeforimLibrary `381fbea`) with this Android app, area by area, on 2026-10-13: 119 gaps, plus 12 from a completeness review. What each later desktop release changed, and what became of it here, is under "Later desktop releases".

- **Severity:** **H** = a missing or broken feature the user will notice; **M** = a noticeable difference; **L** = polish.
- **Tick an item when it lands.** Before porting anything, read the desktop code it names, as 0.6.11 has it (`git diff v0.6.1 v0.6.11 -- <path>` shows what changed since the audit). The full findings, with desktop and Android file:line references and fix plans, were produced by the audit workflow; re-run a focused audit for an area rather than trusting stale line numbers.
- **Not portable from public sources:**
  - Zayit's semantic (meaning-based) search: a private, token-gated package. Pardes's own, on Dicta's open model, is ported from Pardes desktop (see "Pardes desktop's semantic search" below);
  - Google Drive backup: a desktop loopback OAuth client;
  - the full template siddur: a private package;
  - the Talmud's verse quote links (0.6.5): a private package too, in the official builds only.
- **Different on purpose: authorship.** Desktop's welcome ("שנבנה באהבה על ידי אברך ומפתח חרדי"), settings header ("נוצר על ידי אליהו גמבש"), About and terms describe desktop Zayit. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept. Not a gap.

## Later desktop releases (0.6.2 to 0.6.11)

The base was desktop 0.6.1 (SeforimLibrary `381fbea`, in an Android-only submodule) until 2026-10-20, then 0.6.7, 0.6.10 from 2026-10-26, and since 2026-10-28 it is 0.6.11: Android compiles the repository's own SeforimLibrary submodule at `4f07145`, the commit desktop 0.6.11 builds with (0.6.6 to 0.6.10 built with `7ddf045`), and `androidApp/upstream/SeforimLibrary` is gone. Upstream's release commits, for `git log` between them (the `v0.6.1`, `v0.6.7` and `v0.6.10` tags are the ones to fetch): 0.6.2 `41988a7d`, 0.6.3 `56bb1c3a` (SeforimLibrary `2265fcb`), 0.6.4 `b6331c88`, 0.6.5 `c13ffdde`, 0.6.6 `fb78498d` (SeforimLibrary `7ddf045`), 0.6.7 `7549ca72`, 0.6.8 `971f7dd6`, 0.6.9 `de3696ca`, 0.6.10 `02ccd881`, 0.6.11 `44febc5b` (SeforimLibrary `4f07145`). No release since 0.6.1 raised the minimum library version (`20261011170830`).

### 0.6.2 to 0.6.4

From `git diff v0.6.1 v0.6.4` upstream, only these four 0.6.2 fixes were ported, because the bugs they fix exist here too:

| Upstream commit | Fix | Android files |
|---|---|---|
| `2126c238` | Typing `בבא קמא` opens בבא קמא (not בבא בתרא דף קמא), `בבא קמא יז` finds the daf, no crash on a word made only of quotes, book suggestions ranked by title tier | `search/reference/ReferenceParser.kt`, `ReferenceResolver.kt`, `RepositoryReferenceSource.kt`, `search/LuceneLookupSearchService.kt` |
| `90552dbc` | No author or book suggested by reading one typed word twice (`בבא ב` no longer suggests רב בבאר שבע or תוספתא בבא קמא) | `search/LuceneLookupSearchService.kt`, `search/AuthorNames.kt`, `ui/search/SearchBarViewModel.kt` |
| `51463d23` | A title being typed past its first word (`בבא ק`) puts the book before the text search, so Enter opens it | `ui/search/UnifiedSearchBar.kt` (`catBookRows`) |
| `411280b9` | The תפילת היום widget turns to the next Hebrew day at צאת הכוכבים | `ui/home/widgets/LuachWidgets.kt` (`TefilaWidget`) |

Each of these files names the 0.6.2 commit in its header. Not ported then:
- the SeforimLibrary bump (meaning-based highlighting infrastructure; it needed `LuceneSearchEngine.kt` changes with no gain for the user here). It came with the move to 0.6.7 (see "0.6.6 and 0.6.7");
- semantic search (and `41988a7d`, which keeps semantic results out of a quoted search: a quoted search here was always the lexical one alone), Filament and the Windows solar-system work, and dependency updates (nucleus, seforim-siddur, seforim-semantic), none of which exist in this port;
- `25dde83d` (`IntrinsicSize.Min` in the result preview): a crash guard for a zero-width preview that this layout never produces. Ported with 0.6.7 all the same, so the preview's layout stays desktop's.

Kept as desktop has it: typing an author's acronym with its gershayim (`ש"ך`) does not suggest that author (הרב שבתי בן מאיר הכהן). The lookup query drops the quote while the index keeps it (`ש"ך`); desktop 0.6.1 misses him the same way. An Android-only reading that finds him was tried and left out, to keep search the same as desktop.

The Android-only subtitle of תפילת היום in the tools list (`ui/tools/ToolsScreen.kt`) follows the same rule through the shared `tefilaDayAt` helper, so it turns at צאת הכוכבים together with the page it opens.

### 0.6.5

From `git diff v0.6.4 v0.6.5` upstream, these were ported on 2026-10-16, because the features they change exist here too:

| Upstream commit | Change | Android files |
|---|---|---|
| `1723de06`, `9e3d19a8` | A picked author (desktop Tab, here the row's "חיפוש בתוך") is a search scope: the row "חיפוש בספרי X: …" searches the text in all their books, base books or not, with one action (no base/all choice; Ctrl+Enter is Enter). The scope stays through the category tabs and "more from this book" and when they are left. A restored tab gets it back, and its bar searches there before the author's chip has loaded. The history keeps it (the author's id in the key, `search:a<id>:…`, titled "query — author"). The bar lists the same books the search runs in (`getAuthorDetails`). | `data/SearchScope.kt` (`Author`), `data/HistoryStore.kt`, `ui/AppState.kt` (history title), `ui/search/SearchBarViewModel.kt`, `ui/search/UnifiedSearchBar.kt`, `ui/search/SearchViewModel.kt`, `ui/search/SearchScreen.kt` (status line) |
| `f443277d`, `5e016dfa` | Citation links in the accent color, without an underline: it crossed the nikud below the letters. Desktop underlines a link under the pointer only; a finger has no hover. | `htmlparser/.../HtmlCompose.kt` (vendored) |
| `46a1ecb8` | Links and footnote markers in the search's passage preview in the accent color, not the parser's blue | `ui/search/PassagePreview.kt` |
| `e3051846` | Two fingers on the passage preview zoom the text, as in the books (one helper for both) | `ui/components/PinchZoom.kt`, `ui/search/SearchScreen.kt`, `ui/reader/BookTextView.kt` |
| `1d50541d` (its pin mark) | A pinned tab of the tablet strip shows a pin beside its icon or acronym, 16 dp wider (the phone's tab list had one already) | `ui/shell/TopBar.kt` |

The ported code names its 0.6.5 commit in the file's header or beside the change. Different from desktop 0.6.5 on purpose:
- In an author's books the status line says "כ־N תוצאות בספרי X", without the base/all link, as for one book. Desktop still offers "בספרי יסוד · חיפוש בכל הספרים" there, though an author's books are all searched whichever is chosen.
- Once an author is picked, their books are filtered only by what is typed after the pick. Desktop 0.6.5 filters them by the text that named the author (its field is emptied without telling the view model), so few or none of their books are listed.

Found by a review of the port against the 0.6.5 code (2026-10-16), and fixed with it:
- A suggestion lookup still running when an author is picked no longer lists its rows (a place, other authors) under the author's chip, where Enter would have opened them. Desktop 0.6.5 has the same race; here a restored author tab made it show (`SearchBarViewModel.observeQueries`).
- The Home's history widget runs a search again where it ran (its book, TOC entry or author, base or all books), as the history page and desktop's widget do; it searched everywhere (`ui/home/widgets/LibraryWidgets.kt`, `HomeWidgetsState.kt`).
- Pinch zoom acts only while two fingers are down, as desktop's TextZoom: a one-finger drag at an angle could be taken from the scrolling, which stopped the preview (and the reader) for that drag (`ui/components/PinchZoom.kt`).
- Citation links in the passage preview open their line in a new tab, as in the reader; desktop's link handler is window-wide (`ui/search/SearchScreen.kt`).
- Links in the siddur, Shnayim Mikra and the dictionary widget take the accent, as in the books, not the parser's blue, which was hard to read in dark mode (`ui/tools/SiddurTool.kt`, `ShnayimMikraTool.kt`, `ui/home/widgets/DictionaryWidget.kt`).

Not ported from 0.6.5 at the time:
- the rest of `1d50541d` (pinned tabs moved to the strip's start): pinned tabs here already come first, at the right of the RTL strip. The slides that came with it (`a991be12`, `a009b0d4`) were ported with 0.6.7 (see "0.6.6 and 0.6.7");
- `ec91acdc` (an "עוד N" menu for the tabs that no longer fit): the tablet strip scrolls, and the phone lists its tabs. Still a deliberate difference with 0.6.7;
- `90217b2b`, `17080195`, `8c96d1f4`, `b563e911` (verse quote links in the Talmud): a private package, like the siddur. Once the database carries these links, they show here as citation links;
- `f96e98f3` (shortcuts with nothing focused): ported with 0.6.7 (see "0.6.6 and 0.6.7");
- the Nucleus bump `df4b7d12`, CI and docs: desktop only.

To watch: `cc3c6712` fixes text selection in a tab shown again under Compose 1.12 (an unplaced selectable Text never subscribes to its SelectionContainer again), with a patch to Compose's bytecode. Desktop 0.6.7 is on Compose 1.12.1; this app is on Compose foundation 1.10.5 (BOM 2026.03.00), and `TabHost` unplaces hidden tabs the same way. The reader's own text selection (`ui/reader/TextSelection.kt`) does not rely on a SelectionContainer; the plain SelectionContainers are in `ui/author/AuthorScreen.kt` (`PlainCopySelectionContainer`, about :215 and :260), `ui/search/SearchParts.kt` (about :163) and `ui/search/SearchScreen.kt` (about :513). When the BOM moves to Compose 1.12, check text selection there after switching tabs.

### 0.6.6 and 0.6.7

The move to 0.6.7 (2026-10-20), with what 0.6.3 to 0.6.5 had left for it. From `git diff v0.6.1 v0.6.7` upstream and `git diff 381fbea 7ddf045` in SeforimLibrary:

| Upstream commit | Release | Change | Here |
|---|---|---|---|
| SeforimLibrary `c3190c4` (in `2265fcb`) | 0.6.3 | The search API: `rangeSnippet` takes the found line's range in the text, and `LineHit` carries it (`lineStart`, `lineEnd`), so the preview and snippets mark only the found line | **Ported**: the two upstream hunks in `search/LuceneSearchEngine.kt` (Android variant) |
| SeforimLibrary `7ddf045` | 0.6.6 | `zayit cli` tooling | **Not applicable**: no CLI here |
| SeforimLibrary `9bcac65`, `772151b`, `7c8f42f`, `651113a` | 0.6.3 | Generator and blacklist fixes; for the generator, the dao gains two link queries (`LinkQueries.sq` `changes`, `selectLineKeyById`) and their repository calls | **Already present**: library data, which arrives with library release v3. The new dao queries compile and stay unused here. core, `Database.sq`, delta-updater and generator-common are unchanged since `381fbea`, so installed libraries, deltas and the Lucene index stay as they are |
| `4fe26694` | 0.6.3 | The found line highlighted by meaning, without the query's words | **Ported with Pardes's semantic search** (2026-10-28): the results' snippets, the preview and the reader's smart find highlight by `SearchEngine.highlights`; without the pack the engine says no and the query's words are highlighted, as before |
| `25dde83d` | 0.6.4 | The preview's found-line bar drawn, not measured (`IntrinsicSize.Min` overflowed at a collapsed pane's zero width) | **Ported**: `Modifier.drawBehind` on the RTL side, `padding(start = 17.dp)` (`ui/search/PassagePreview.kt`) |
| `a991be12`, `a009b0d4` | 0.6.5 | Tabs slide to their new place on pin, unpin, reorder and reopen | **Ported** with Compose's `Modifier.animateItem()`, on the tablet strip (`ui/shell/TopBar.kt`) and the phone's tab list (`ui/shell/ZayitApp.kt`) |
| `6ba92fb7`, `ce6e2b0c` | 0.6.5 | Desktop's own slide: drag releases spared, the hit area moving with the tab, the farther-travelling tab drawn on top | **Not applicable**: `animateItem` places the items itself, and the strip here has no tab drag |
| `f8057ed1` | 0.6.7 | A new tab grows at its place instead of sliding from the strip's end | **Already the case**: a new tab is inserted next to its opener in one step (`AppViewModel.openInNewTab`), and `animateItem` brings it in there |
| `ec91acdc`, `df4b7d12` | 0.6.5 | "עוד N" chip for the tabs that no longer fit (popup, "סגור את הלשוניות המוסתרות", `+` in its menu), with a compact workspace switcher | **Deliberate difference**: the tablet strip (≥840dp) scrolls and keeps the shown tab in view; the phone lists its tabs. The strings `more_tabs` and `close_hidden_tabs` are not used |
| `f96e98f3` | 0.6.5 | The pane and workspace shortcuts work when nothing has the focus | **Ported**: the keys no view used reach `KeyShortcuts.fallback` (`MainActivity.onKeyDown`), where the front tab puts its book shortcuts: a reader tab Ctrl+B, Ctrl+Shift+B, Ctrl+K and its variants, Ctrl+J (`ui/reader/ReaderScreen.kt` `bookShortcut`), a tablet Home tab Ctrl+B for the book tree (`ui/home/HomeScreen.kt` `WideHome`). The workspace keys (Ctrl+Alt+←/→, Ctrl+Alt+N) were already handled before the views see them (`MainActivity.dispatchKeyEvent`) |
| `90217b2b`, `17080195`, `8c96d1f4`, `b563e911` | 0.6.5 | Verse quote links in the Talmud | **Not included**: a private package of the official builds. `LinesPagingSource.decorate`, the hook they use, has no counterpart in `ReaderViewModel` yet |
| `7380286b` | 0.6.6 | Alt+Home goes Home (`Key.MoveHome`, not `SystemHome`) | **Already the case**: `KEYCODE_MOVE_HOME` (`ui/shell/KeyShortcuts.kt`) |
| `58e8beb5` | 0.6.6 | The search engine's setup shared with `zayit cli` (`AcronymFrequencyCache` takes the path as a function) | **Not applicable**: desktop's CLI. `search/SearchService.kt` wires the same lexical engine |
| `37748ba7` | 0.6.6 | Website page `open.html` that redirects to a `zayit://` link | **Already the case**: the app's `zayit://` filter is BROWSABLE, so a browser hands it such links |
| `0380bb5a`, `fb1fb6e8` | 0.6.6, 0.6.2 | Filament KMP 0.7.10 and Windows fixes for the 3D widgets | **Not applicable**: the Earth, sky and solar-system widgets are drawn in 2D here |
| `2a3a2f30` | 0.6.7 | The find bar keeps its width while typing (the counter keeps a slot while matches are computed) | **Not applicable**: the bar here has a set width (the band's on a phone, up to 420dp on a tablet), so the counter coming and going only resizes the field |
| `aa87fa66` | 0.6.7 | Closing the app's last tab opens a fresh Home tab instead of quitting | **Already the case** |
| `c222f4e4`, `368dd669`, `a08c20db`, `5a1b6068`, `2a604516` | 0.6.3 to 0.6.7 | Nucleus and seforim-siddur bumps, CI, docs | Desktop only |

The deliberate differences recorded under 0.6.5 (the author status line, the author's books filtered by what is typed after the pick) stay as they are. The modified-version notices (Settings › אודות and תנאים, onboarding, the README, the site and the release notes) named Zayit 0.6.7 from then, and their lists of what Pardes does not include add the Talmud's verse quote links. File headers that name a desktop file named it as 0.6.7 has it: the desktop files the port follows are unchanged since 0.6.1, or changed only as this section and the ones above record. The headers of vendored and variant files (`search/`, `search/reference/`, `htmlparser/`) also name the commits that changed their desktop file since 0.6.1.

### 0.6.8 to 0.6.10

The move to 0.6.10 (2026-10-26), from `git diff v0.6.7 v0.6.10` upstream. SeforimLibrary is unchanged (`7ddf045`), so the library, the deltas and the Lucene index stay as they are, and so do the user `.sq` files:

| Upstream commit | Release | Change | Here |
|---|---|---|---|
| `99ce1813`, `aed6e965`, `9a86eada` | 0.6.8 | The Temple count after Rashi (the default, the year minus 68), the Rambam (69) or Tosafot (70, the count until then), each explained from the מראי מקומות with links to the lines of its sources; a gear on the card opens the options; a widget sets its options window's size (`optionsSize`) | **Ported**: `TempleCount` and `templeCountdownAt` (`data/Zmanim.kt`), `TempleSettings` (desktop's key `home_widget.temple_countdown.count`), the gear and the options page (`ui/home/widgets/TempleCountdownWidget.kt`, the explanation being desktop's `files/temple_count.txt`, which `DesktopTextAssets` in `app/build.gradle.kts` copies into the APK's assets), `HomeWidget.optionsSize` (the size of the tablet's options dialog, as desktop's window has it; `WidgetGallery.kt` used to take a tenth off every widget's), `rememberOpenSource` shared with the measures (`MeasuresWidget.kt`). The launcher widget counts after the same setting (`appwidget/WidgetData.kt`), and redraws when it changes (`AppPrefs.WIDGET_KEYS`). A phone's options sheet is laid out at its full height before the text is read: measured without it, the sheet stayed low and the text went under the screen's edge (Android 9) |
| `de3696ca` | 0.6.9 | The options window was empty on Windows: a CRLF checkout of `temple_count.txt` | **Ported**: the same `\r?\n\r?\n` split |
| `c33a13ee` | 0.6.8 | Highlighter and eraser icons in the highlight menu, for the pen beside its label and the cross of its clearing swatch | **Ported**: `ui/reader/HighlightIcons.kt` (desktop's SVG paths); the selection bar's "סמן" button and the clearing swatch of the bar and of the sheet (`ui/reader/TextSelection.kt`). The sheet's "סמן" row keeps its layout, without an icon |
| `971f7dd6` | 0.6.8 | Up to date and checking are a quiet line under the version (icon, text, re-check link); banners only for the states that call for an action | **Ported**: `AppUpdateStatusLine` under the version (`ui/settings/AppUpdateSection.kt`, `SettingsPages.kt` `AppHeaderCard`). Not yet checked (Android's check at start can be turned off) is the line's "בדוק עדכונים" link alone; a development build keeps its note above the header. Beside the icon a phone's line is shorter than desktop's, so the line flows: the re-check link goes under the status whole instead of breaking in two |
| `219899c0`, `48a1ed1b` | 0.6.10 | A notes button in the title bar (Linux and Windows) and its popup, as the favorites one: a search field, the notes grouped by book, newest first, and "הצג את כל ההערות"; the notes page and the popup share their data (`UserNotes`, `rememberUserNotes`, `noteDestination`) | **Ported**: the tablet title bar's notes button and popup, on a phone an "ההערות שלי" item in the overflow and a sheet (`ui/shell/TopBar.kt`, `ShellPopups.kt` `NotesMenuContent`, `ZayitApp.kt`, which shows `NotesPage`); `UserNotes` and `rememberUserNotes` (`ui/notes/NotesScreen.kt`). A note opens its book on its line with the notes pane, in a new tab |
| `219899c0` | 0.6.10 | The notes and favorites pages scroll (a lazy column with the Jewel scrollbar), their cards keeping their folding as they scroll out | **Already the case**: `NotesPage`, `HistoryPage` and `FavoritesPage` are lazy columns, their folding in `rememberSaveable` |
| `ff23a749` | 0.6.10 | The book details pane hidden by default | **Ported**: `ReaderPrefs.bookDetails` (the search's pane shares the setting, as desktop's does) |
| `93cc89d7` | 0.6.10 | The solar system to scale in its full window (NASA's sizes and distances, a zoom up to 3000×) | **Not applicable**: the year cycle here is a flat diagram of the Earth's orbit, without the bodies' sizes, distances or a zoom. Its setting (`toScale`) is not declared here; a settings backup carries it as it is |
| `0d6d3310` | 0.6.10 | The screen kept awake only while the window has the focus, and the Temple animation decoding no frame while the window is hidden | **Already the case**: `FLAG_KEEP_SCREEN_ON` holds only while the window is shown, and the animation plays only while it can be seen (`AnimatedTempleBackground`) |
| `b30831ed`, `bdab85da`, `edf12d71`, `762b17f4`, `f5d800bb`, `ea3971e7`, `39320b54`, `663f7ebd`, `8633afa5`, `4c324013`, `ccefa453`, `659b3eec` | 0.6.8 to 0.6.10 | Nucleus, Zayit's site, README and screenshots, and the e2e scenario that shoots them | Desktop only |

The modified-version notices (Settings › אודות and תנאים, onboarding, the README, the site and the release notes) now name Zayit 0.6.10. File headers that name a desktop file name it as 0.6.10 has it: the desktop files the port follows are unchanged since 0.6.7, or changed only as this section records.

Tested on an Android 9 emulator at phone, Jelly Star (240dp) and tablet sizes. Besides the two phone fixes above, the 240dp phone needed two more: the widgets' options footer flows (`WidgetOptions`: "סיום" squeezed beside "שחזור ברירת המחדל" broke a letter a line, in every widget's options), and under 300dp the settings header puts the icon above the name and version (`AppHeaderCard`: beside it, the update line took a word a line).

### 0.6.11

The move to 0.6.11 (2026-10-28), from `git diff v0.6.10 v0.6.11` upstream and `git diff 7ddf045 4f07145` in SeforimLibrary. The library gains two link queries (`getReferencesForLines`, `getSourcesForLines` in `LinkQueries.sq`) and the long-query fix of the search; no schema change and the same minimum library version, so the library, the deltas, the Lucene index and the user `.sq` files stay as they are:

| Upstream commit | Release | Change | Here |
|---|---|---|---|
| `540b80d3` | 0.6.11 | A מראי מקומות pane: the places that refer to the line (REFERENCE and מסורת הש״ס both ways, עין משפט forward) as the search's results: a book at a time, its first passage cut around its quote of the line, "עוד N מקומות", tabs by kind and by category, a tap opens the book there. The מקור pane is drawn by the same view, and its paging and the line snapshot's sources (the inverse links) go. A heading stands for its section, a multi-selection for its lines, as for the commentaries; the rail's buttons are enabled by the panes' own queries. Beside the text the panes are columns side by side in the rail's order, and under the text the sources come first | **Ported**: `ui/reader/LineReferences.kt` (as desktop's, the lines read 400 at a time for SQLite below 3.32) and `LinePlacesPane.kt`, `ReaderPane.References`. Tablet: its rail button between תרגומים and מקור (desktop's `Library_books` icon), the pane beside the text, with תרגומים and פרטי הספר now columns beside it, and מקורות at the start of the row under the text. Phone: its pane tab and toolbar action, between תרגומים and מקור; the buttons the toolbar can't hold go under עוד from its end, so in a book of the Torah on a 411dp phone (seven buttons for five places) ניקוד and שנים מקרא do, and מקור stays (the owner's choice, 2026-10-28). `LineConnections` reads forward links only, `LinksPane` shows the targumim only. The rail and the pane share one query (`ReaderViewModel.placeLinks`, an LRU of 16) |
| `540b80d3` | 0.6.11 | The search page's result row, title, snippet and filter tabs shared with that pane; the results follow the text zoom (`LocalResultTextScale`); "עוד" beside the tabs a link, not a tab | **Ported**: `ResultRow`, `ResultTitle`, `SnippetText`, `LocalResultTextScale` and `resultSp` in `ui/search/SearchParts.kt`, `FilterTabs` in `SearchScreen.kt` |
| `715aef21` | 0.6.11 | Ctrl+Alt+Shift+K opens the references pane | **Ported**: `bookShortcut` |
| SeforimLibrary `0749fbd` | 0.6.11 | Long queries: the dictionary forms of a query's words share a budget (all of them up to five words, fewer beyond), and Lucene's clause limit is raised to 4,096 | **Ported**: the budget in `search/LuceneSearchEngine.kt`. The clause limit stays Android's own 16,384 |
| `72f2de21` | 0.6.11 | The Earth and solar system widgets turned off on a GPU unfit for 3D, where Filament crashed (Windows), and Nucleus `3.0.0-dev-202610272317` | **Not applicable**: those widgets are drawn with Compose here, without Filament |
| `8ed3c123`, `b19ba7c4`, `c31d5391` | 0.6.11 | The Windows jump list kept while the app is closed; the Linux launcher entry's English name; the commented-out links button removed | **Not applicable** |

Also from this move: the notes glyph is desktop's `NotebookPen` (Lucide's notebook-pen: 0.6.10's title-bar notes menu, the rails, the notes page and widget), and the book tree's button desktop's `Library`, in place of Material's EditNote and LibraryBooks, which looked like the references' `Library_books`.

Tested on the API 33 emulator on 2026-10-28, on a test library of Bereshit, Berakhot and Mishnah Berakhot with their links (the full-text index included), at phone (411dp) and tablet (1280dp) widths:
- מראי מקומות on ברכות מה. (13 books, 21 places, tabs הכל / עין משפט / מסורת הש״ס) and on בראשית א, א (38 books, 164 places, tabs by category: אחרונים, ראשונים, משנה, קבלה, מדרש, עוד); מקור on רש״י על ברכות; the quotes in bold; beside the text on the tablet.
- The quote is the slow part: for a long line (a mishnah, a long Rashi) its search plan took 30 to 45 seconds on this software-emulated CPU, and about two minutes on the first line after the app started, while the dictionary loads; the passages show from their start until then. A real phone is many times faster; short lines took a few seconds even here.
- A 20-word query ran and found its passage, with the quote highlighted.
- The search results grow with the text size (two steps up, from the reader's גודל), and on a Jelly Star (240dp) the pane opens from עוד, its tabs in one scrolling row.

Fixes land in waves:
1. 0.6.1 engine, zmanim, places and network trust.
2. Shell, settings and tools.
3. Search and reader.
4. Final sweep.

## Pardes desktop's semantic search

Pardes desktop's own semantic search (חיפוש לפי משמעות, `SEMANTIC_SEARCH.md` and `docs/SEMANTIC_PLAN.md` at the repository's root): Dicta's open model, fine-tuned and distilled in `semantic-model/`, int8 ONNX, and a Lucene HNSW index of the library's lines, fused with the search by words (RRF). Ported on 2026-10-28 with the same model and the same pack (release `semantic-2-v3-20261012193827`), not approximated: desktop's files that compile on Android are built unchanged (`syncDesktopSemantic`), the others are Android variants with desktop's package and class names in `search/semantic/`, each with a header listing its changes ("Android:"). Desktop's unit tests of the fusion, the precedence by level, the passages, the swap at start, the choice of the pack and the settings opened at start run on the port unchanged (`syncDesktopSemanticTests`, 49 tests), and the tokenizer's parity runs on a device (`HfTokenizerParityTest`).

| Desktop | Here |
|---|---|
| `HybridSearchEngine`: RRF with the model's dense weight, the lexical leaders that hold a quote, the precedence of the earlier level, refined snippets, highlights by meaning, smart find, the fallback to the words alone | **Ported** (`search/semantic/HybridSearchEngine.kt`): the book levels and the lines a dense hit needs are read through the app's own SqlDriver (`BookLevels.kt`, `LibraryLines`), as every other read of `seforim.db` here; the logger is `android.util.Log` |
| `IndependentSemanticSearch`, `OnnxEmbeddingProvider`, `OpenModelSpec`, `HfTokenizer`, `VectorIndexBuilder` (its reading side) | **Ported**: `NIOFSDirectory` (never MMap); ICU's regex has no `UNICODE_CHARACTER_CLASS`, so the tokenizer spells out Oniguruma's `\w`, `\s` and `\d`; org.json's `keys()` instead of `keySet()` and the API 26 forms of `Files`, `Optional` and the hex digits; the model is found beside the index only (no `PARDES_MODEL_DIR`, no legacy model) |
| ONNX Runtime 1.17.1 | **1.22.0**, the first whose two Android libraries (with its JNI library) are aligned to 16 KB pages, arm64-v8a and x86_64 only; R8 keeps `ai.onnxruntime.**` (JNI by name) |
| `withSemanticSearch` (AppCoreBindings): the engine wrapped where the addon is installed | **Ported** (`search/SearchService.kt`): on a 64-bit device that Android does not count as low on memory (`SemanticSearchSupport`: the model takes some 240 MB once loaded); a 32-bit or Android Go device keeps the lexical engine, and Settings shows no semantic section there |
| The pack: `SemanticPackReleases`, `SemanticPackInstaller`, `PendingSemanticPack`, `InstalledPack` | **Ported** (`search/semantic/pack/`). Deliberate differences: a download streams into the staging folder as it unpacks (as the library's download does here), so nothing of 2 GB is kept in between and a stopped download starts over; the swap's request is in the app's private files; the swap runs at the first start of the shell (`MainActivity`), before the search opens |
| Settings › ניהול נתונים › SemanticPackCard and its view model | **Ported** (`ui/settings/SemanticPackSection.kt`, `search/SemanticPackManager.kt`), with desktop's strings. Android: the download or import goes on in a foreground service with a notification (`SemanticPackService`), as the library's; "ייבוא מקבצים…" takes the files picked together (the system's picker shows no folder): the two zips, or the manifest with its parts. Unzipped folders are not offered there: the transfer scripts copy those |
| Desktop 1.0.3: `SemanticPackPrompt` (`main.kt` opens Settings › ניהול נתונים on the card at every start while no pack is in use), the card framed and brought into view, its lines on installing with internet and without, "לפתוח את המסך הזה בכל הפעלה", the room an install needs (`installBytes`), and the wording without "חבילה" | **Ported** in 0.6.1 (`ui/settings/semantic/SemanticPackPrompt.kt`, with desktop's package and names; desktop's `SemanticPackPromptTest` runs on it unchanged). Android: `MainActivity` runs it once the start is Ready, only for a plain start (not a link, a launcher shortcut, a widget or a rotation), after an update dialog; the settings are an overlay of the one window. An install needs the unpacked size and the margin, the download being unpacked as it comes. Desktop's `SemanticPackCardUiTest` (a Compose desktop test) is not run here |
| The find bar's smart mode (`find_smart_mode`, BookContentView `semanticFind`, LineItem `smartRanges`) | **Ported**: the toggle (desktop's magic-button icon) on a 64-bit device, the book's 60 lines closest to the query by meaning, each lit with `highlights`. Under 300dp (Jelly Star) the toggle is left out for the field's room, and the bar is smart while it holds the search's query, as before; without the pack the smart mode finds the lines the search finds by words (Android's, see "Search") |
| `LongQuerySearchEngine` (with the semantic search, in `framework/search/`): a query too large for Lucene's 4,096 clauses searched as an exact quote | **Not needed**: Android's lexical engine allows 16,384 clauses (SeforimLibrary `0749fbd` above), and the fusion goes on with the dense side alone when the lexical one fails, as desktop's does |
| `warmUp` at start (the model and the vector graph loaded in the background) | **Not done**: the model loads when the first search asks for it, so the 166 MB model costs no memory to whoever does not search |
| The transfer of a library from desktop | `tools/push-desktop-library.sh`/`.ps1` copy `pardes-model/` and `pardes.vec.lucene/` with it when desktop has both, and the library's moves to and from a memory card take them along (`LibraryMover`); a full library install deletes the vector index, as desktop's does, and keeps the model |

## Engine and data

- [x] **H** Snippets and search-term highlighting still come from the pre-0.6.1 engine (wrong window, bolds unrelated dictionary variants and 4-gram fragments)
- [x] **H** Pointing the build at seforimlibrary 381fbea: what breaks at compile time, plus a runtime hazard with the shared reader
- [x] **H** Minimum library version is behind desktop's; the 0.6.1 dao would crash getBook() on older libraries
- [x] **H** Author data, author page, author suggestions, knowledge panel and book editions/licenses are missing
- [x] **H** Typed references (חולין יב:, בראשית א ג, שו"ע או"ח שכח ג) do not jump to the place
- [x] **M** Facets and searching are slow on phones: a new reader per search, a full document read per hit, and wide snippet loads
- [x] **M** Magic dictionary ignores ambiguous surfaces and picks the wrong word family (fewer results, different ranking)
- [x] **M** Results are not grouped per book (no 'עוד N תוצאות מ…', no de-duplication)
- [x] **M** Reader find-in-page and search-term highlighting do not use the engine (only visible lines, raw substrings, no count or next/previous)
- [x] **M** No incremental library (delta) updates: every schema bump means a full ~8 GB download (wave 4: `android/delta/`, Settings › ניהול נתונים and the database update flow; in-place transactional apply without desktop's 8 GB backup copy, Lucene lines written like the generator's index writer)
- [x] **M** User settings DB comes from the old schema; history and favorites do not match desktop's user_settings.db
- [x] **L** ~~Semantic (meaning-based) search~~: Zayit's cannot be built from public sources. Since 2026-10-28 Android has Pardes desktop's own (see "Pardes desktop's semantic search"), and without its pack behaves like the desktop community build.

## Search

- [x] **H** Suggestion dropdown is books-only: no author rows, wrong row order, Enter/IME Search does not run the highlighted row, no row-action buttons
- [x] **H** No reference parsing: 'בראשית א ב', 'חולין יב:', 'שו"ע או"ח שכח ג' cannot jump straight to a passage
- [x] **H** Base-books vs all-books choice: telescope chip instead of the 0.6.1 status line; no way to start an all-books search from home
- [x] **H** Category filtering: checkbox tree and 'סינון' sheet instead of the 0.6.1 category tabs
- [x] **H** No grouping by book, no 'עוד N תוצאות מ…' expander and no 'חזרה לכל התוצאות', no duplicate-text removal
- [x] **H** Opening a result does not seed find-in-page, so the searched words are not highlighted in the reader
- [x] **H** Phones: Back from an opened result does not return to the results, and the results scroll position is lost
- [x] **H** Tablet two-pane: wrong threshold and widths, an embedded reader instead of the 0.6.1 PassagePreview, and the selected result cannot be opened
- [x] **H** No in-book search: book chip, TOC suggestions, 'חיפוש בתוך {book}: {q}', scoped results
- [x] **M** Result row format differs from 0.6.1 (title link, book-font full-size snippet, extra full-path line)
- [x] **M** Results page bar is a plain text field, not the shared smart bar with the olive branch
- [x] **M** No entity knowledge panel for queries naming a book or an author
- [x] **M** Executed searches are not recorded in history
- [x] **L** Search placeholder hints differ from desktop
- [x] **L** Progress/cancel header visuals: an idle grey bar is always drawn
- [x] **L** No find within the results; the top-bar 'חיפוש' opens an empty search tab
- [x] **L** Scoped-search TOC pane with hit counts (tablet)
- [x] **L** A zayit:// link pasted into the bar is searched as text

## Reader

- [x] **H** Tab content not keyed per tab: reader scroll/UI state leaks between tabs; new book in same tab opens at old scroll index; position never persisted
- [x] **H** No text selection inside lines or commentaries: copy, highlight and note only work on whole lines
- [x] **H** Find in page: loaded lines only, per-word matching, no match counter, no previous/next, nothing highlighted in panes
- [x] **H** "דווח על טעות" is fake (toast only)
- [x] **H** Diacritics: one global on/off instead of desktop's 3-state mode saved per root category; button shown for books without diacritics
- [x] **H** Commentaries pane: book-level commentator list, no section gathering for headings, wrong names, no open-at-place, loads every commentary text, single-row layout
- [x] **H** Tablet panes are mutually exclusive; rails and phone toolbar ignore what the book or line actually has
- [x] **M** Line selection model: nothing selected on open, TOC click only scrolls, headings don't select their section, no multi-select
- [x] **M** TOC pane: no alt TOC and no inline alt headings, no TOC search, current entry matched by text, no scroll to current, sole root not collapsed, parents navigate
- [x] **M** Breadcrumb is static text; no copy link, share or favorites with folders; tab title lacks the chapter
- [x] **M** Citation links in texts and commentaries are not clickable
- [x] **M** Book details pane incomplete; parts do nothing on phone; visibility not persisted
- [x] **M** Notes pane: no editing or autosave, no timestamps, no section scope, explicit Save button, misleading hint, no note markers in text
- [x] **M** "ההערות שלי" (all notes) screen missing
- [x] **M** Highlights: whole-line only, different palette, ranges imported from desktop shown as whole lines, none in panes
- [x] **M** "העתק עם מקור" uses a different format; copy-without-nikud shown when meaningless
- [x] **M** Shnayim Mikra reading mode missing from the reader
- [x] **M** Marked passage (daily learning / aliya) not supported
- [x] **M** Author page missing
- [x] **M** No fast-scroll / content-aware scrollbar for long books
- [x] **L** Sources pane mixes in REFERENCE links; targum/source section titles not clickable
- [x] **L** Book tree has no in-pane search
- [x] **L** Tablet commentators list not collapsible; no compact mode
- [x] **L** Hardware-keyboard shortcuts absent (tablets with keyboards)
- [x] **L** Inline images in texts not rendered
- [x] **L** Wording differences in reader strings
- [x] **M** A long-pressed word on a justified line was selected whole but highlighted (handles, note underline) toward the line's start, half on its neighbour: Android positions ignore justification, so the app justifies the text itself (`ui/reader/Justify.kt`; found by the user, 2026-10-15)

## Home and tools

- [x] **H** Zmanim calculation engine and opinions do not match desktop (wrong alos, MGA, tzais, candle lighting, havdala; community ignored)
- [x] **H** City/location table is a 15-city subset with different coordinates; location is not reactive
- [x] **H** Home is a fixed column, not the desktop editable widget grid (17 widgets, gallery, persistence)
- [x] **H** Earth globe, Sky and 3D solar system (מעגל השנה) widgets are missing; Android YearCycle is a 2D approximation with a different orbit model
- [x] **M** Zmanim cards: layout, titles/abbreviations and click behaviour differ from desktop
- [x] **M** Shared day/moment state missing: calendar day and zman taps don't drive other tools; no luach popup on dates
- [x] **M** Daily study (לימוד יומי): 6 cycles missing, option groups, ordering/wording, Vezot Haberacha, no marked range
- [x] **M** תפילת היום: rows, wording and order differ from desktop tefilaOfDay
- [x] **M** Molad & Kiddush Levana: window opinion ignores community; molad month logic differs
- [x] **M** Shnayim Mikra: double parshiyot, Vezot Haberacha, progress storage and opening differ
- [x] **M** Dictionary: per-dictionary parsing, normalisation, lookup and result rows differ
- [x] **M** Smart siddur: desktop is a private-package template siddur; Android fallback misses the portable behaviours
- [x] **M** Upcoming events and Next zman (luach widgets) missing; Android substitutes differ in content and wording
- [x] **L** Hebrew calendar (לוח עברי): no holiday names in cells, civil toggle not persisted, extra day card uses wrong zmanim
- [x] **L** Measures (ממיר מידות): Chazon Ish value, number formatting, per-opinion options and sources differ
- [x] **L** Temple countdown: no Jerusalem-sunset rollover, anniversary semantics, subtitle wording, static image
- [x] **L** Home chrome: wallpaper blending and search placeholder hints differ
- [x] **L** Tools menu presentation: subtitles built on divergent data; tablet opens every tool full-screen

## Settings, onboarding, database updates

- [x] **H** No startup database-compatibility gate and no 'עדכון מסד הנתונים' (DatabaseUpdateWindow) flow
- [x] **H** Database version rules differ from 0.6.1: old minimum, and databases without a version are accepted
- [x] **H** User-data backup is not compatible with 0.6.1 (bare .db instead of the zip archive; no preferences; restore not staged)
- [x] **H** 'שמור מצב בין הפעלות' is shown (default on) but nothing saves or restores the session
- [x] **H** Region (country/city) list: 15 Android cities vs desktop's worldPlaces; onboarding region not prefilled; no type-to-search
- [x] **M** Online install wipes the current library before checking that the release is new enough; the fallback manifest can point to an old release
- [x] **M** Fonts page offers 5 fonts instead of desktop's 28
- [x] **M** Setting keys, enum values and defaults differ from desktop AppSettings
- [x] **M** 'איפוס האפליקציה' leaves history, bookmarks, tool settings and leftover files behind
- [x] **M** 'מצב קומפקטי לסרגל הצדדי' (compact mode) toggle has no effect
- [x] **M** ~~Google Drive backup card~~: desktop uses a loopback OAuth client that Android cannot reuse. Left out, like the desktop community build. Backup to a file (and from there to Drive) works.
- [x] **M** Settings › תנאים shows the old licence paragraph
- [x] **L** Onboarding: an existing library is handled differently from desktop's licence step
- [x] **L** Settings page icons are Material icons instead of desktop's

## Shell: tabs, history, favourites, links

- [x] **H** Session restore not implemented (persist-session setting is a no-op)
- [x] **H** Per-tab UI state leaks between tabs; reading position lost on tab switch; tab ViewModels never cleared
- [x] **M** Back or "בית" from a book opened in the Home's own tab (search bar, catalog, drawer) built a new Home: blank, then its widgets one by one with spinners, its catalog row arriving ~2.4 s later and pushing the page down. The tab now keeps its Home composed behind the book (parked: back at its top, its bar empty), and the widgets' and catalog's values are kept app-wide for a Home built anew (found by the user, 2026-10-15)
- [x] **H** Workspaces (מרחבי עבודה) missing entirely
- [x] **H** History storage and recording diverge from desktop (wrong data, no searches, previews pollute it, desktop rows ignored)
- [x] **H** Favorites: no folders, no search, wrong key, separate store; star behaviour differs
- [x] **H** Deep links: desktop search links do not open on Android; no tab-level link/share
- [x] **M** History UI: desktop popup + full day-grouped page vs one flat overlay
- [x] **M** No tab context menu (close others/left/right/all, copy link, pin, move to workspace)
- [x] **M** Pinned tabs missing
- [x] **M** Reopen closed tab (Ctrl+Shift+T) missing; no undo for closing a tab
- [x] **M** Hardware-keyboard shortcuts absent (tablets/DeX/Chromebooks)
- [x] **M** Title-bar magnifier opens an empty search tab instead of toggling find-in-page
- [x] **M** Tab titles: no '<book> - <chapter>' suffix; Home tab label differs
- [x] **M** Startup routing lacks the database-version check (DatabaseUpdateWindow)
- [x] **L** Deep link opens underneath an open overlay
- [x] **L** Tab order: new tabs not placed next to their opener; no drag reorder; strip layout
- [x] **L** System Back turns any tab into Home instead of returning to the opener tab
- [x] **L** Theme toggle and title-bar labels use non-desktop wording
- [x] **L** No launcher shortcuts / Recents title (jump-list & dock-menu equivalents)

## Cross-cutting (completeness review)

- [x] **H** Certificates installed by content filters (NetFree / Rimon / Etrog) are not trusted, so library download and release lookup fail behind Israeli filtered internet
- [x] **M** Tablet search preview writes reading history and leaks one ReaderViewModel per selected result
- [x] **M** Home catalog dropdowns list the wrong books and titles (e.g. שולחן ערוך shows 171 entries including commentaries)
- [x] **M** Tab management missing: pin, close others/all/left/right, reopen closed tab, copy link, reorder, open next to current, keyboard shortcuts
- [x] **M** Search deep links shared from desktop (zayit://search/<encoded>) do not open on Android
- [x] **M** History page lacks desktop's day groups, per-entry delete, clear confirmation, per-chapter rows and open-in-place
- [x] **M** Favorites page has no folders, search, move or folder management, and opens in a new tab
- [x] **M** Tablet reader panes have fixed sizes (no draggable dividers, no per-tab split); home book tree cannot be hidden
- [x] **L** Book tree lists subcategories before a category's own books (desktop: books first)
- [ ] **L** Virtual desktops ('מרחבים') and 'open in new window' are missing
- [x] **L** No app-update check (desktop: title-bar badge, settings banner, dialog) (`update/`, Settings › כללי: a check at start, the badge in the title bar (a tablet button; the phone's overflow dot and first item), the dialog with what is new, the banner; the APK is checked (sha256, package, version, signing certificate) and installed by Android's PackageInstaller. Different on purpose: no silent install on close; a setting turns the check at start off; development builds do not update)
- [x] **L** No launcher shortcuts (Android equivalent of desktop's jump list / dock menu)

## Integration review (wave 4)

- [x] **H** Session restore, "בטל" and Ctrl+Shift+T reopened a book at its selected line, not where it was read (the reader's history call overwrote the tab's anchor)
- [x] **M** A dialog or sheet opened in a tab (ההערות שלי, the breadcrumb, selection, commentator filter, luach, widget, history dialogs) stayed over the tab a deep link or "לשונית חדשה" opened
- [x] **M** Phones had no find-in-page on the search results (magnifier, or the overflow under 300dp)
- [x] **M** Back from an opened search result first closed the seeded find bar instead of returning to the results
- [x] **M** Shnayim Mikra ticks of older Android builds reached the user DB only once the tool was opened (Home widget, backups)
- [x] **L** Catalog and dictionary stamps (and the library folder) were read on the main thread
- [x] **L** Restored book tabs reopened the search result's find bar
- [x] **L** "איפוס האפליקציה" left the last books read in the launcher's shortcuts
- [x] **L** Each search-engine reopen (library update, reinstall) leaked lexical.db handles
- [x] **L** Phone drawers covered the whole screen at 240dp, their tabs overflowing
- [x] **L** Attaching a hardware keyboard (or changing the font scale) restarted the Activity
- [x] **L** Search tabs came back at the top without their category tab (desktop SearchPersistedState)
- [x] **L** Import and reset were allowed during a library update
- [ ] **L** A staged restore (PendingUserDataRestore) still runs on the main thread, once, at the first launch after an import

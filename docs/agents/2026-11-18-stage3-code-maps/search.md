# Code map: the search bar, the results, the in-book find and copying

- **For:** stage 3 of Pardes for desktop, requests 41, 46, 54, 55 and 56 (`docs/DESKTOP_STAGE3_PLAN.md`).
- **When:** 2026-11-18. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `40df456` (main of that day). The line numbers are that commit's.
- **By:** an Explore agent of Claude Code, one of three run side by side. The report is as it came back; the brief it got is at the end.
- **Checked by hand afterwards:** `SearchEntityFinder.authorNamed` is private (`SearchEntityFinder.kt:55`), so a change reuses its test rather than calling it.

## The report

I found causes or likely causes for all five requests, from reading the code only. I did not run the app (SeforimLibrary is an empty submodule here, so its `SearchEngine`/`buildSnippet` code is not readable either). Below, files are cited by name:line; full paths are in the index at the end.

## A. Author's name in the search bar: books, not only folders

**How suggestions are built today** (`SearchHomeViewModel.observeQueries`, `SearchHomeViewModel.kt:256-360`, 120 ms debounce):
- **Books:** `lookup.suggestBooks(q, limit = maxBookPredictive)` (293). The limit is 120 (190). The title index matches a typed word in the title, in the category path, or in an author's name or alias (`BookTitleIndex.kt:74-80`). A one-word query keeps every book it finds, because `wordsApart` returns true when `typed.size < 2` (308).
- **Authors** (303-327):
  ```kotlin
  if (addingScopes) return@async emptyList()   // 305; addingScopes = scopeItems.isNotEmpty() (263-264)
  ```
  Otherwise: `suggestAuthors(q, MAX_AUTHOR_SUGGESTIONS * 3)` (308-317), plus the authors of up to 2 books whose title is typed (`MAX_NAMED_BOOKS`, 319-324), then `withTheirBooks` (`AuthorBooks.kt:10-20`) and `withAuthorsOfBooks(…, MAX_AUTHOR_SUGGESTIONS)` (326; `AuthorNames.kt:103-113`). The cap is 4 (1046).
- **Categories:** `categoriesNamed(q, MAX_CATEGORY_SUGGESTIONS)` (334-337), cap 4 (1051). This goes through `LibraryCategories.named` (`LibraryCategories.kt:70-76`) to `CategorySuggestions.match` (`CategorySuggestions.kt:50-96`). A typed word must start a word of the category's own title; an exact title ranks first.

**The UI:** `catBookRows` sets the order (`HomeView.kt:182-225`), `SuggestionsPanel` draws the rows (831-1002), inside a Popup (2009-2065) capped at `heightIn(max = 220.dp)` (878), so about 5-6 rows are visible.
```kotlin
if (textSearch && textSearchFirst) add(CatBookRow.TextSearch)
jumps…; titledBooks…; typedTitle…
(exactAuthors + bookAuthors).mapTo(this) { CatBookRow.Author(it) }
exactCategories.mapTo(this) { CatBookRow.Category(it) }      // folder rows
acronymBooks…; if (textSearch && !textSearchFirst) add(CatBookRow.TextSearch)
begunBooks…; laterAuthors…; otherCategories…
otherBooks.filter { … }.mapTo(this) { CatBookRow.Book(it) }  // books come last
```

**(1) No scope picked, typing רמב"ן or רש"י:**
- **Author row.** It counts as exact through its name, displayed name or alias (194-197). It shows "N ספרים"; ↵ opens the author page (`openAuthor`, `SearchHomeViewModel.kt:923-931`), Tab is "חיפוש בתוך" (`HomeView.kt:958-972`).
- **Folder rows.** These are the categories named exactly, e.g. תנ"ך › ראשונים › רמב"ן, with a folder icon (1287) and the action "חיפוש בקטגוריה ↵" (984-996; `strings.xml:569`).
- **Then** acronym books, the text-search row "חיפוש במאגר: …", other authors and categories.
- **Books last.** These are title-index hits. Commentaries are not base books, so they never reach `begunBooks` (206-210). They are capped at 120, ranked by title, and mixed with books that only mention the name (e.g. "… על רש"י"). So the author's own books are below the fold and may be cut.
- **The full list exists only through Tab on the author row:** `onPickAuthor` (474-483) calls `repository.getAuthorDetails(author.id)?.books`, then `showAuthorBooks` (508-521) shows all of them with no limit, base books first. Typing then filters them by title (265-271).

**(2) After a category (or several items) was picked:**
- There are no author rows at all (305), so the Tab route to the author's books is gone.
- `textSearchFirst` puts "חיפוש ב<scope>: …" first, then the exact folder rows with "הוספה לחיפוש ↵" (986, 2035), then the same title-index books at the end.
- An author and categories cannot be combined: `showAuthor` clears `scopeItems` (492-504), `applyScopeItems` clears the author (554-571), and `SearchScope.Multi` has no author ids (`SearchScope.kt:45-51`).
- With one book picked (TOC mode, `HomeView.kt:615`), suggestions come from `booksAndCategoriesToAdd` (`SearchHomeViewModel.kt:401-430`): at most 5 books and 3 categories (1054-1055), exact categories first (`tocAddRows`, `HomeView.kt:237-249`), and no authors.

**Book tree search** (`CategoryTreePanel.kt:62-91` → `NavigationUseCase.search`, 116-135): it runs `suggestBooks(query, BOOK_SEARCH_LIMIT = 120)` (21), then `buildBookFilter` (`BookTreeSearch.kt:11-58`). The tree is narrowed to the hit books with all their parent categories expanded (`expandedCategories = result.categoryIds`, `CategoryTreePanel.kt:79`). So the books show inside their folders; there is no author concept there.

**Most likely target: the search bar.** The folder rows ("חיפוש בקטגוריה", folder icon) fill the visible list ahead of the books, and in scope mode the author row, the only way to get all the books, is suppressed.

**Where to add all the author's books:**
- Next to `authorsDeferred` (303-327): for an author named exactly, use the test at `HomeView.kt:194-197` or `SearchEntityFinder.authorNamed` (`SearchEntityFinder.kt:55-65`), which already returns `AuthorDetails` with `.books`. Load `getAuthorDetails(id)?.books` (as at 477-480) and map them like `showAuthorBooks` (512-515).
- Bypass line 305 for these books. With a category chip, you can intersect with `libraryCategories.bookIds(categoryId)` (`LibraryCategories.kt:91-105`).
- Expose them through a new `SearchHomeUiState` field (113-144), or merge them into `bookSuggestions` deduplicated by id. Place them in `catBookRows` right after `exactCategories` (`HomeView.kt:217`). For TOC mode, change `booksAndCategoriesToAdd` and `tocAddRows`. For the tree, add the ids to `hitIds` (`NavigationUseCase.kt:121-133`).
- Limits to revisit: 4/120/4/5/3 (lines 1046, 190, 1051, 1054-1055), the 220dp popup, and the tree's 120. `getAuthorDetails(id).books` itself has no limit.
- These files are Pardes-changed Zayit files with high merge risk (`ZAYIT_CHANGE_MAP.md:1244-1290`, 1500-1532).

## B. A link in a search result opens both the link and the result

**Results list:** `ResultView` (`SearchResultScreen.kt:764-808`) → `ResultRow` (814-862). The whole row has `combinedClickable(onClick = select into preview, onDoubleClick = open)` (843-848, wired at 569-573).
- The snippet is a plain `Text` (`SnippetText`, 997-1017) built by `buildAnnotatedFromHtml` (724-750).
- `<a class="ref" href>` becomes `LinkAnnotation.Url` (`HtmlParser.kt:184`, `HtmlCompose.kt:178-183`). `LocalUriHandler` is `rememberRefLinkUriHandler` (`MainAppWindow.kt:305`; `RefLinkUriHandler.kt:22-38`), which opens the cited line in a new tab.
- Here Compose's link `clickable` consumes the press before the row sees it. Also, a single click on a row only selects it. So the list is not the culprit (and its snippets are probably plain text with `<b>` anyway).

**Preview pane: this is where both fire.** `PassagePreview` (890-985) renders raw line HTML from `repository.getLines` (`PassageReading.kt:188-195`) through `buildAnnotatedFromHtml(line.content, …)` (945-948). The citation links are live, inside a `SelectionContainer` (`PassageReading.kt:262-269`). Each line also has:
```kotlin
.passageLine(passage, line.id) { if (found) onOpen() else onOpenLine(line.id) }   // SearchResultScreen.kt:976
// onOpen = { openResult(shown.hit) } (649) -> actions.onOpenResult(result, true) (359): new tab
// onOpenLine -> actions.onOpenBookAt (650)
```
`Modifier.passageLine` (`PassageReading.kt:277-301`) never checks consumption:
```kotlin
val down = awaitFirstDown(requireUnconsumed = false)                    // 287
val (up, still) = release(down) ?: return@awaitEachGesture             // 293
val again = withTimeoutOrNull(wait) { awaitFirstDown(requireUnconsumed = false) }
if (again == null) open() else …                                        // 297-298
// release(): awaitPointerEvent(PointerEventPass.Final), no isConsumed check (304-311)
```
- The link (a child, Main pass) consumes the press and opens its tab. `passageLine` still calls `open()` after the double-tap timeout, which opens the result in a second tab.
- This is deliberate ("nothing consumed, so the selection takes the same presses", `ZAYIT_CHANGE_MAP.md:3902-3904`). Zayit's preview had a plain `clickable` here (1604-1607).
- Zayit's reader does it correctly: `if (up != null && !up.isConsumed)` (`BookContentView.kt:1668-1669`).

**Fix in `passageLine`, one of:**
1. Bail out when `up.isConsumed`. The Final pass sees the link's Main-pass consumption. Verify that the SelectionContainer doesn't consume a plain single click.
2. Pass the line's `TextLayoutResult` and skip when `getLinkAnnotations(offset, offset)` is non-empty at the press point.
3. Wrap `LocalUriHandler` in `PassageText` so it flags a link click that `open()` checks. `open()` runs after the double-tap timeout, so the flag is set by then.

`ResultRow` and the References pane (`LinePlacesView.kt:238-248`) need nothing.

## C. Exact, general and hybrid search modes

**Pipeline:**
- `AppCoreBindings.kt:211-224`: `LuceneSearchEngine` (SeforimLibrary) → `withSemanticSearch` (`SemanticSearchEngine.kt:16-36`) → `LongQuerySearchEngine` (a too-long query is searched as an exact quote, `LongQuerySearchEngine.kt:47-64`; `asQuote` at 151-156) → `installedSemanticSearch.hybrid(...)`.
- In the community build, `addons/semantic/disabled/.../InstalledSemanticSearch.kt` returns Pardes's `HybridSearchEngine`. Zayit's private variant (`addons/semantic/enabled/.../HybridSearchEngine.kt`) has the same rule for quotes.
- Pardes `HybridSearchEngine.openSession` (102-118):
  ```kotlin
  if (!semanticConfigured || categoryFilter != null || lineIds != null || SearchQueryParser.parse(query).hasExactPhrases)
      return lexical.openSession(...)
  return HybridSession(...)
  ```
- `fuse` (227-335) runs both sides at once:
  - dense: `sem.search(query, 150, …)`
  - lexical: its first 150 hits
  - If the dense side doesn't answer, the result is the lexical side's whole list (279-282, 403-416).
  - `fusedScores` is RRF with k = 60 (593-642). When the lexical side is empty it returns the dense order alone (616-620).
  - Then `keepLeaders` (304-315) and `LevelPrecedence` (317, 342-359).
- Facets are lexical (201-209) plus the books of meaning-only lines (`MeaningFacets.kt:13-31`; `withMeaningHits`, `SearchResultViewModel.kt:1517-1525`). `WordsEngine.words = lexical` (`HybridSearchEngine.kt:51`).
- The ViewModel wraps the engine: `lucene = CatalogOrderEngine(engine, …) { persistedSearchState().catalogOrder }` (`SearchResultViewModel.kt:168`; `CatalogOrderSearch.kt:43-68`, STANDOUT 2.0 at 247). Every session and facets call goes through `lucene` (1098-1105, 1324, 1470-1504, 1794-1812, 2172-2182). `ExecuteSearchUseCase` (25-56) only converts hits to results.

**Options that already exist** (per tab, `SessionModels.kt:207-245`, new fields "appended last" for ProtoBuf at 231):
- `globalExtended` (base books vs all books; falls back to all when base books find nothing, 1337-1345)
- base books only within a category
- `catalogOrder`
- They live in `SearchStatusLine` (`SearchResultScreen.kt:1253-1313`, called at 468): the base/all link (1283-1289), with/without commentaries (1290-1297), library order vs relevance (1298-1311).
- In the bar's text-search row: ↵ "בספרי יסוד", Ctrl+↵ "בכל הספרים" (`HomeView.kt:940-956`, 1768-1775).

**There is no semantic on/off switch.** Dense search runs whenever the pack is installed (`IndependentSemanticSearch.isAvailable`, `HybridSearchEngine.kt:62-71`). The settings page only offers "prompt at start" (`SemanticPackViewModel.kt:167-170`, `AppSettings.kt:430-435`). The in-book find has Zayit's per-tab toggle "חיפוש חכם לפי משמעות" (`FindInPageBar.kt:77-79`, 160-181; `AppSettings.kt:146`, 213-225; `strings.xml:453`). Opening a result turns it on (`SearchResultViewModel.kt:2306-2312`).

**"Exact" today exists only as quotes:**
- `"…"` gives an exact phrase: no prefixes, no dictionary forms, lexical only, and only the words are highlighted (`HybridSearchEngine.kt:113-115`, 146-149; `SEARCH_SYNTAX_REPORT.md:5-21`, 43, 53; `SEMANTIC_SEARCH.md:26`). The ״/׳ and typographic-quote pitfalls are at `SEARCH_SYNTAX_REPORT.md:11-20`, 28-34.
- No "דיוק" option is wired. Zayit left unused strings `search_level_1..5` ("דיוק מושלם" … "חיפוש כללי", `strings.xml:692-715`; `SEARCH_SYNTAX_REPORT.md:49`).
- Background docs: a "search options" window was recommended and the user said "not now" (`SEARCH_SYNTAX_REPORT.md:57`). Otzaria has three modes, מדויק / מתקדם / מקורב (`SEARCH_OTZARIA_AND_LIBRARIES.md:46`, 74-75). `SEARCH_PLAN.md:114-118` lists fusion variants already measured and rejected.

**Passing a mode down:**
- Don't change the `SearchEngine` interface (it's SeforimLibrary's). Instead wrap it like `CatalogOrderEngine` at `SearchResultViewModel.kt:168`:
  - **exact:** `(engine as? WordsEngine)?.words`, or `asQuote(query)` for strict-phrase semantics. Also override `highlightsByMeaning` to false (it is read at `SearchResultViewModel.kt:1537` and `SearchResultScreen.kt:941`).
  - **hybrid:** the engine as today.
  - **general (meaning only):** needs a new hook in `HybridSearchEngine` that runs `fuse` without the lexical side and takes facets from `meaningBookCounts`. A TOC scope (`lineIds`) still forces lexical (113).
- State: a `searchMode` field appended last in `SearchPersistedState`, plus a `SearchUiState` field (104-138). Add an event `SetSearchMode`, modelled on `SetCatalogOrder` (286-288, 369-371), followed by `ExecuteSearch` as at `TabsContent.kt:483-486`.
- Optional: carry it in `TabsDestination.Search` (`TabsDestination.kt:20-27`) and `seedNavigationScope` (`SearchResultViewModel.kt:177-186`) for new tabs, and in the history key (463).
- UI: a third choice in `SearchStatusLine`, and/or on the bar's text-search row. A default could go in Settings › כללי.

## D. Search inside an open book with a results side panel

**Find bar:** `FindInPageBar` (`FindInPageBar.kt:59-206`), overlaid top-right of the text (`BookContentView.kt:1387-1422`).
- Ctrl/Cmd+F: `WindowUtils.kt:51-56` → `AppSettings.showFindBar` (192-202).
- Ctrl+G / F3: `WindowUtils.kt:57-61`, 81-85 → `findNext` (205-211) → collected at `BookContentView.kt:869-871`.
- The state is per tab and not persisted (`AppSettings.kt:143-225`).

**Matching covers the whole book, not only the loaded pages:**
- `findResults` (`BookContentView.kt:637-680`) → `findInBook` (`BookFindMatches.kt:113-165`).
- Lucene `findInBookCandidates(query, bookId)` gives candidate lines. They are read 500 at a time (177) and confirmed with `findAllMatchesNormalized(plainTextFromHtml(...))` (142). There is a full scan as fallback (148-156) and narrowing while typing (168-174).
- Only line index, id and running counts are kept (21-26).
- Smart mode: `semanticFind` (`BookContentView.kt:610-633`) → `searchEngine.semanticFind(query, bookId, 60)` → `fuse(bookIds = listOf(bookId))` (`HybridSearchEngine.kt:190-199`) → `ofLines` (`BookFindMatches.kt:92-99`).

**Next/previous:** `navigateToMatch` (`BookContentView.kt:811-855`) uses `next` / `nextAfterLine` with wrap-around (`BookFindMatches.kt:40-70`). `loadLine` rebuilds the pager when needed (801-809) and `revealFindMatch` centres the match (787-798). On the daf it calls `dafControl.scrollToLine` (841-844). Ctrl+Enter selects the line (859-865).

**Side panels:** `ReaderPane` (`ReaderPanes.kt:104-224`) has Tree, Toc, Notes, Targum, Comments, Sources, BookDetails, References and split-off Commentator panes. They are registered in `standard` (211), `desiredPanes` (701-722) and `BookPaneBody` (742-819). There is no find-results pane.

**An in-book search with a results tab already exists:**
- Tab on a book suggestion ("חיפוש בתוך", `strings.xml:568`; `HomeView.kt:925-929`) puts the bar in TOC mode.
- The text-search row then reads "חיפוש בתוך %2$s: %1$s" (`strings.xml:544`; `HomeView.kt:1158`, 2023).
- `submitSearch` uses `barScope()` = `SearchScope.Book` (`SearchHomeViewModel.kt:800-868`). The results tab then runs `lucene.openSession(query, 5, bookIds = listOf(fetchBookId), baseBookOnly = false)` (`SearchResultViewModel.kt:1489-1491`), with result counts per TOC entry (`SearchTocPane`, `ReaderPanes.kt:850-874` → `SearchBookTocPanel`).
- "Search selected text" in the reader opens an unscoped search tab (`BookContentViewModel.kt:467-478`), although `TabsDestination.Search(scope = …)` already accepts a book scope.

**Building the results pane:**
- **Option A:** add `ReaderPane.FindResults` with a `ContentState` flag and a toggle event, opened together with the find bar. Move `findResults` out of the composable-local `produceState` into the tab's ViewModel (or `tabUi`), load the matched lines with `getLinesByIds`, and group them by TOC. Rows can reuse `ResultRow` / `ResultTitle` / `SnippetText` (internal, `SearchResultScreen.kt:814-883`, 997-1017), as `LinePlacesView.kt:238-248` already does.
- **Option B:** `engine.openSession(query, 5, bookIds = listOf(bookId), baseBookOnly = false)` plus `SearchTocUseCase` for per-entry counts. Reusing `SearchResultViewModel` itself is heavier: it is keyed to a Search tab's id and persisted state (`SearchResultViewModel.kt:163-186`; `tabSearchViewModel`, `ReaderPanes.kt:829`).
- **Option C (smallest):** a "show all" button on the find bar that opens `TabsDestination.Search(query, scope = SearchScope.Book(bookId))`. That gives a tab, not a side panel.

## E. Copying gives a space before the pasted text

**Copy paths:**
- **Reader, plain Ctrl+C or menu "העתקה":** Compose's `SelectionContainer` copies its text as is. Only a Shift+click selection is intercepted (`BookSelection.kt:178-186`), and its text from `resolveRange` (220-239) has no leading separator:
  ```kotlin
  .map { … displayed.substring(selected.first, selected.last + 1) }.filter { it.isNotEmpty() }.joinToString("\n")
  ```
- **Context menu:** `copiedText = owned?.text ?: selectedText` (`BookContentScreen.kt:590-593`).
  - "העתק ללא ניקוד" (607-608): not trimmed.
  - "העתק טקסט בלבד" / Ctrl+Shift+C → `lettersOnly`, which **trims each line** (`LettersOnly.kt:13-28`).
  - "העתק עם מקור" → `"$selectedText\n\n($reference)"` (`CopyWithSource.kt:114-130`, shortcut 137-159): not trimmed.
  - Commentary source (`CommentarySource.kt:128-137`): not trimmed.
- **Search preview:** `PassageText` and its menu (`PassageReading.kt:226-270`, 334-360): not trimmed.
- **Daf view:** `textOf` joins with " " and calls `.trim()` (`DafPage.kt:300-309`), and both Ctrl+C and the menu use it (`DafView.kt:455-458`, 896-897).
- **Clipboard writer:** `ClipEntry(StringSelection(text))`, written as is (`Clipboard.kt:16-19`).

So no code prepends a separator. Whatever whitespace sits at the selection's edge is copied as is; only "העתק טקסט בלבד" trims it.

**Where edge spaces come from (htmlparser, Zayit's unchanged module):**
```kotlin
// HtmlParser.kt:297-306
val needsLeadingSpace = hasLeadingSpace && list.isNotEmpty() && !list.last().isLineBreak && !list.last().text.endsWith(" ")
… needsLeadingSpace -> " $trimmedText"
```
- `list` still holds elements that `buildAnnotatedFromHtml` does not draw: a hidden footnote marker (its stand-in space is added only `if (length > 0 …)`, `HtmlCompose.kt:160-165`) or an image with no image builder (142-155; the preview passes none).
- So a line starting `<sup class="footnote-marker">1</sup> טקסט` is drawn starting with a space (173-174). `HtmlParserTest.kt:83-84` even expects `" ועכשיו נהגו"`.
- Trailing spaces are kept on purpose too (`HtmlParser.kt:249`, 288-290, 305-307), so many lines end with " ".
- Placeholder lines (over 8,000 characters, still being parsed) are made of spaces (`AsyncHtmlAnnotation.kt:131-141`).

**Most likely source:** the space is part of the selected text, never trimmed. It is either a line-edge space kept by the parser, or a drag that starts inside a word gap; the reader's justified lines (`TextAlign.Justify`, `BookContentView.kt:1842`) widen those gaps. Also, a trailing space on Hebrew text pasted into a left-to-right field shows on the right, i.e. "before" the text. **Quick check:** if Ctrl+Shift+C pastes clean and Ctrl+C doesn't, this is the cause.

**Fix: trim both ends at the copy points:**
- Extend `BookSelectionContainer.onPreviewKeyEvent` (`BookSelection.kt:178-186`) to plain Compose selections; the text is mirrored into `SelectionContext` at `BookContentScreen.kt:540-547`.
- Override `copy` in `menuTextManager` for the non-owned case as well (`BookContentScreen.kt:576-585`).
- Trim in `buildCopyWithSourcePayload` (129), `copyWithSourceOf` (`CommentarySource.kt:136`), "ללא ניקוד" (`BookContentScreen.kt:607`) and the `PassageText` menu.
- Optionally fix `needsLeadingSpace` in the parser so it counts only text that is actually drawn.

## Files (full paths)

- **Search:** `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/{SearchHomeViewModel,SearchResultScreen,SearchResultViewModel,PassageReading}.kt`
- **Search domain:** `…/features/search/domain/{AuthorBooks,AuthorNames,CategorySuggestions,ScopeItems,LibraryCategories,SearchEntityFinder,CatalogOrderSearch,ExecuteSearchUseCase}.kt`
- **Semantic:** `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/{HybridSearchEngine,MeaningFacets,IndependentSemanticSearch}.kt`
- **Semantic addons:** `/home/user/Pardes/SeforimApp/addons/semantic/disabled/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/InstalledSemanticSearch.kt` and `/home/user/Pardes/SeforimApp/addons/semantic/enabled/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngine.kt`
- **Search framework:** `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/{LuceneLookupSearchService,SemanticSearchEngine,LongQuerySearchEngine}.kt`, `…/framework/search/titles/{BookTitleIndex,BookTitleIndexLoader}.kt`, `…/framework/di/modules/AppCoreBindings.kt`, `…/framework/session/SessionModels.kt`
- **Reader:** `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/{HomeView,BookContentView,BookSelection,AsyncHtmlAnnotation,LinePlacesView}.kt`, `…/features/bookcontent/ui/panels/categorytree/CategoryTreePanel.kt`, `…/features/bookcontent/ui/panes/ReaderPanes.kt`, `…/features/bookcontent/usecases/{NavigationUseCase,BookTreeSearch,BookFindMatches}.kt`, `…/features/bookcontent/{BookContentScreen,BookContentViewModel}.kt`, `…/features/bookcontent/daf/{DafPage,DafView}.kt`
- **Core:** `/home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/{CopyWithSource,LettersOnly,CommentarySource}.kt`, `…/core/presentation/utils/{Clipboard,WindowUtils}.kt`, `…/core/presentation/components/FindInPageBar.kt`, `…/core/presentation/text/RefLinkUriHandler.kt`, `…/core/settings/AppSettings.kt`
- **HTML parser:** `/home/user/Pardes/htmlparser/src/commonMain/kotlin/io/github/kdroidfilter/seforim/htmlparser/{HtmlParser,HtmlCompose}.kt`
- **Navigation:** `/home/user/Pardes/navigation/src/commonMain/kotlin/io/github/kdroidfilter/seforim/tabs/{SearchScope,TabsDestination}.kt`
- **Strings:** `/home/user/Pardes/SeforimApp/src/commonMain/composeResources/values/strings.xml`
- **Docs:** `/home/user/Pardes/docs/{SEARCH_SYNTAX_REPORT,SEARCH_OTZARIA_AND_LIBRARIES,SEARCH_PLAN,ZAYIT_CHANGE_MAP}.md`, `/home/user/Pardes/SEMANTIC_SEARCH.md`

## The brief the agent got

> Read-only exploration of the Pardes desktop app (Kotlin, Compose Multiplatform + Jewel) at /home/user/Pardes. Thoroughness: very thorough. Do NOT edit anything. Report file:line for every claim. Main code: `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/` (features/search, features/bookcontent, framework/search). Pardes is a fork of the Zayit desktop app; `docs/ZAYIT_CHANGE_MAP.md` says which files Pardes added or changed (grep it for a file name to learn its history). Also see `SEMANTIC_SEARCH.md` and `docs/DESKTOP_STAGE2_PLAN.md` for context.
>
> I need a precise map for these user requests (answer each separately: today's behaviour with its code path, and where a change would go):
>
> A. Request: "Search in a category by an author's name should bring all of their books as suggestions below, not only the folders of their books." Map the search bar's suggestions in `features/search/SearchHomeViewModel.kt` (authorSuggestions, selectedScopeAuthor, onPickAuthor, AuthorBooks.kt, AuthorNames.kt, CategorySuggestions.kt, ScopeItems.kt, LuceneLookupSearchService.suggestAuthors/authorsOfBook) and the UI that renders them (find the composable). Describe exactly what the suggestion list shows when typing an author's name (e.g. רמב"ן, רש"י) (1) with no scope picked, (2) after a category or a book was already picked as the scope ("Books and categories picked: more of them to add, no authors" near line 263). Also check the book tree's own search (the category tree panel under features/bookcontent/ui/panels/categorytree, its search field) — what does typing an author's name there show? Decide which of these the request most likely refers to, and say where all the author's books could be added (how `repository.getAuthorDetails(id).books` is used, limits like MAX_AUTHOR_SUGGESTIONS).
>
> B. Request: "Clicking a link inside the search results today opens both the search result and the link. It should open only the link." In `SearchResultScreen.kt`, find how a result row handles clicks (clickable / pointerInput / onClick opening the result) and how links inside the result's text (annotated string links, LinkAnnotation, quote links / TextLinks from features/bookcontent/quotelinks, commentary links) are rendered and handled. Explain why both fire and where to consume the event. Also check the preview pane beside the results (PassageReading.kt?) for the same issue.
>
> C. Request: plan a choice between exact, general and hybrid search ("like today" = hybrid). Map the search pipeline: where lexical (Lucene) and semantic (Pardes's own HybridSearchEngine / IndependentSemanticSearch under features/search/semantic, `addons/semantic/disabled`) results are fused (RRF), what settings already exist (e.g. a semantic switch, sort by library order, "CatalogOrderSearch"), where the search options UI lives (search bar, chips, filters, settings page for search), and how a mode could be passed down to the engine (SearchEngine interface, ExecuteSearchUseCase, SearchResultViewModel). Note Zayit's own search UI elements (e.g. a full-text/precise toggle if any, `SearchSyntax`, exact phrase in quotes) and the docs in docs/SEARCH_*.md that discuss modes. Also: is there an "exact" mode already (e.g. quotes, a "דיוק" option, `docs/SEARCH_SYNTAX_REPORT.md`)?
>
> D. Request: "searching inside an open book will open the side panel that shows all the results in an organised way". Map the in-book find (Ctrl+F) in the book content view: where the find bar is, how matches are found (in loaded lines only? via Lucene for the whole book?), how next/previous works, and what side panels exist (Nucleus docking panes in features/bookcontent/ui/panes/ReaderPanes.kt: TOC, commentaries, sources, notes...). Is there an existing "search in this book" that opens a results tab (the "Tab: חיפוש בתוך" scope in the search bar)? Say how a results pane could reuse SearchResultViewModel or the Lucene search with a book filter.
>
> E. Bug: "Copying from Pardes and pasting gives one space before the copied text." Find the copy paths: Zayit's copy in the book content (SelectionContainer? custom selection in LINE_SELECTION_README.md, `CopyWithSource`, "העתק עם מקור", "העתק טקסט בלבד" Ctrl+Shift+C added by Pardes, the clipboard writer), the daf view copy, the search results copy. Find where text is assembled from lines/segments (joinToString(" ") with a leading separator, a leading space from HTML parsing, a first segment that is empty, etc.) and identify the most likely source of a leading space. Show the exact code.
>
> Keep the report structured A–E, concise, with exact file:line references and short decisive code excerpts.

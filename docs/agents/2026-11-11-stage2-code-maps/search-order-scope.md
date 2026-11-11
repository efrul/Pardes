# Code map: the search's library order, its scopes, and what is already measured

- **For:** stage 2 of Pardes for desktop, requests 31 and 37 (`docs/DESKTOP_STAGE2_PLAN.md`).
- **When:** 2026-11-11. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `abcb687` (main of that day). The line numbers are that commit's.
- **By:** an Explore agent of Claude Code, one of four run side by side. The report is as it came back; the brief
  it got is at the end.
- **Checked by hand afterwards:** `CategorySearchBench.kt`'s header and its KDoc on the category field (`:1-60`,
  `:173-200`), and what commit `5c6a545` (2026-11-02) measured with it: the filter by the index's own field
  `ancestor_category_ids` against the books' ids, per pass, קל וחומר in תלמוד 2.7 ms against 7.8, in תנ״ך 0.6
  against 3.6, in משנה תורה 0.5 against 2.5, אמר רבא in תלמוד 9.7 against 13.5.
- **Note:** the SeforimLibrary submodule was not initialised in the checkout the agent read, so the engine's own
  filter code was not read (its last section).

## The report

P = /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp

### A. CatalogOrderEngine / CatalogOrderSession (P/features/search/domain/CatalogOrderSearch.kt)

- Wiring: SearchResultViewModel.kt:165
  `CatalogOrderEngine(engine, libraryCategories::bookOrder) { persistedSearchState().catalogOrder }`.
- `openSession` :25-49. It always opens the engine's own session first (:35). If `!byCatalog()` it returns that
  session (:36). It takes `words = (engine as? WordsEngine)?.words` (:37). For HybridSearchEngine that is the lexical
  engine (HybridSearchEngine.kt:50). It keeps the hybrid session as `fused` only if it `is MeaningFacets` (:38), and
  closes it otherwise (:39).
- Fields :79-91: `books: List<Pair<Long,Int>>` (book, number of word hits), `listed: Map<Long, List<LineHit>>`,
  `meaningCounts`, `ready: ArrayDeque<LineHit>`, `meant = HashSet<Long>()` ("those of them the meaning alone found").
- `open()` :109-129:
  - It reads `order = bookOrder()` (:110).
  - The facets come from `words.computeFacets(...)?.bookCounts` (:113-114).
  - `listed = fused.nextPage(MEANING_LINES /*300*/, snippets=false)?.hits.groupBy { it.bookId }` (:116-121). That
    pulls the whole fused list, lexical and dense lines both.
  - `meaningCounts = fused.meaningBookCounts()` (:122).
  - Books are `(counts.keys + meaningCounts.keys).sortedWith(compareBy({ order[it] ?: Int.MAX_VALUE }, { it }))`
    (:125-126).
- `nextPage` :93-107 calls `readBooks` until `limit` lines are ready.
- `readBooks` :132-165. It groups books in order until `count >= wanted` or `MOST_BOOKS` (200) (:138). It opens a
  fresh lexical session `words.openSession(query, near, bookIds = group, ...)` and reads it whole in `READ_LINES`
  (2000) pages (:145-150).
- The merge is at :153-164:
  ```
  val seen = lines.mapTo(HashSet()) { it.lineId }
  for (book in group) {
      if (book !in meaningCounts) continue
      listed[book]?.forEach { line ->
          if (seen.add(line.lineId)) { lines += line; meant += line.lineId }
      }
  }
  ready += lines.sortedWith(compareBy({ order[it.bookId] ?: Int.MAX_VALUE }, { it.bookId }, { it.lineIndex }))
  ```
- What each line carries at that point:
  - Lexical and dense-only lines are in two lists before the sort (`lines` from words, then the appended `listed`
    lines). After that they are told apart only by the `meant: HashSet<Long>` set.
  - `LineHit.score` on lines from `listed` is the fused RRF score: HybridSearchEngine.kt:432
    `hit.copy(score = entry.score, ...)`, and `entry.score = rrf.getValue(lineId)` at :309. On word lines it is the
    lexical session's own score.
  - The raw dense cosine (`DenseHit.score`, IndependentSemanticSearch.kt:27-32) and the dense rank do not reach this
    point. `fuse` passes only `denseHits.map { it.lineId }` to `fusedScores` (HybridSearchEngine.kt:283).
  - The fused list's position (rank) is lost when `groupBy` runs (:121).
- `meant` is used again in `withSnippets` (:170) and `refinedSnippets` (:179).
- `CatalogOrderSession` implements `MeaningFacets` but not `SessionViews` (:66-67).

### B. The sort in the UI

- There is no enum or sealed type. The sort is a Boolean `catalogOrder`:
  - SearchUiState: SearchResultViewModel.kt:113
  - persisted: P/framework/session/SessionModels.kt:236-237
  - event `SetCatalogOrder` SearchResultViewModel.kt:283-284, handled at :366-370, which runs `executeSearch()` again
  - TabsContent.kt (P/core/presentation/tabs):481-482
- String keys: /home/user/Pardes/SeforimApp/src/commonMain/composeResources/values/strings.xml:884
  `search_sort_catalog` "מיון לפי סדר הספרייה" and :885 `search_sort_relevance` "מיון לפי רלוונטיות".
- The switch is a link in `SearchStatusLine`, SearchResultScreen.kt:1229-1283:
  `ResultLink(stringResource(if (catalogOrder) ...search_sort_relevance else ...search_sort_catalog)) { onCatalogOrderChange(!catalogOrder) }`
  (:1274-1280). It is called from :469-470.
- Rendering:
  - The list is flat: `LazyColumn` + `itemsIndexed(items, key = { it.hit.lineId })` → `ResultView` (:539-563), plus a
    loading item (:565-574).
  - The only item type is `private data class ResultItem(val hit: SearchResult, val more: Int)` (:665-670), built by
    `flattenResults` (:678-697).
  - `everyPassage = oneBook || state.catalogOrder` (:236). In catalog order every hit is shown and `more = 0` (:684).
  - The "עוד N תוצאות מ…" row is not a separate list row. It is a `ResultLink` inside `ResultView`, shown when
    `item.more > 0` (:777-783), using string `search_more_from_book` (strings.xml:874).
  - There is no header or separator item type in the list.
- Paging: `loadMore()` → `session.nextPage(LAZY_PAGE_SIZE)` and the results are appended
  (SearchResultViewModel.kt:1419-1463).

### C. Scope filtering

- Category → book ids:
  - `LibraryCategories.bookIds(categoryId, baseBooksOnly)`, P/features/search/domain/LibraryCategories.kt:91-105. It
    returns a `LongArray.asList()` from `Entry`s built once from `CatalogCache` (catalog.pb, in memory) (:25-27,
    :182-190). It falls back to the database `getBooksUnderCategoryTree` (:99-103).
  - `bookOrder()` is at :115-123, cached in `order`.
- Search bar scope → `scopeBookIds()`, SearchResultViewModel.kt:969-985. It flatMaps the categories, then
  `.distinct()`, and is called on every `executeSearch` (:1288) and every `executeFilteredSearch` (:2104).
- Tab filter: `collectBookIdsUnderCategory` (:2251) builds a `mutableSetOf` on each tab click (:2093-2111).
- Lexical side: book ids go into `lucene.openSession(..., bookIds = scopeBookIds)` (:1489) and
  `computeFacets(bookIds = facetsBookIds)` (:1320). The Lucene filter is built inside SeforimLibrary's
  `LuceneSearchEngine` (see Not confirmed). CategorySearchBench's KDoc (jvmTest
  .../features/search/CategorySearchBench.kt:173-177) describes the app's filter as "`book_id` in a set, built again
  for each search", with only the base-books filter cached (:89-90, `StableFiltersCachingPolicy`).
- `HybridSearchEngine.openSession` sends `categoryFilter != null` or `lineIds != null` to the lexical side only
  (HybridSearchEngine.kt:109-110).
- Dense side: IndependentSemanticSearch.kt:65-71 `IntPoint.newSetQuery("book_id", intIds)`, built on every
  `search()`. On every search it also builds the filter `Weight` (:76) and counts the field's vectors and the
  filtered vectors (:141-144).
- Rebuilt on every search:
  - In catalog mode the facets are computed twice per search: VM :1320 and `CatalogOrderSession.open` :113.
  - Each `readBooks` call (each page) opens a new lexical session filtered to up to 200 book ids
    (CatalogOrderSearch.kt:145).

### D. When the words find nothing

- HybridSearchEngine.kt:235: the first dense call uses `onShort = { short = true }`.
- :259-263: the call is repeated with `val seeds = lexHits.take(DENSE_SEEDS).map { it.lineId }` (DENSE_SEEDS = 64,
  :526).
- With no lexical hits the seeds are empty → `seedLines = null` (IndependentSemanticSearch.kt:78) and there is no
  `onShort`, so `searchField` ends at :163:
  `return if (filterWeight != null) exactSearch(field, walk.target, walk.k, filterWeight) else walked` (it scores
  every vector in the scope). The KDoc says the same at :131-133.
- Small filters are always searched exactly, whatever the words find, :143-144:
  `val limit = minOf(EXACT_LIMIT.toLong(), vectors / EXACT_SHARE).toInt(); if (filteredVectors(...) <= limit) return exactSearch(...)`.
  `EXACT_LIMIT = 100_000` (:331), `EXACT_SHARE = 8` (:334).
- The quoted sentence "only a search the words find nothing for still scores a whole scope" is in
  /home/user/Pardes/.claude/skills/pardes-semantic-search/SKILL.md:63.
- If the dense side returns nothing, the list is the lexical hits alone (HybridSearchEngine.kt:269-271).

### E. Measurement tools

- ScopeSearchBench:
  SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/ScopeSearchBench.kt
  - It is a `main` (:38), run by hand. It needs env `SEFORIMAPP_DATABASE_PATH`; arg 1 is the number of rounds
    (default 4); `BENCH_QUERY` gives one query.
  - Scopes (:117-138): the whole library (base books / all), תלמוד, הלכה, מפרשי בבא בתרא, ראשונים על בבא בתרא,
    בבא בתרא.
  - It prints per scope (:105-107):
    `words=… dense=… | vector | dense | words | facets | page | tree of N books`, each a median with the cold first
    round apart.
  - No Gradle task for it was found.
- Debug lines:
  - HybridSearchEngine.kt:312-316: `fused in X ms: words, meaning, the candidates' lines, their order`.
  - IndependentSemanticSearch.kt:89-92: `N books: vector X ms, vec Y ms, vec_base Z ms`.
- DenseTrapTest: same folder, DenseTrapTest.kt. Three tests (:79, :91, :105): a short walk is reported; seeds give at
  least 3/4 of the exact top-K; a large scope with no seeds is scored exactly.
- Other benches:
  - jvmTest .../features/search/SearchBench.kt: headless replay of executeSearch (:29).
  - .../features/search/CategorySearchBench.kt: facets + page per category, filter costs (:105-127),
    `ancestor_category_ids` against a `book_id` set (:173-240).
  - .../features/bookcontent/ReaderBench.kt: not a search bench.
- Related tests: CatalogOrderSessionTest.kt (domain/),
  HybridSearchEngineFusionTest/DenseTest/KeepLeadersTest/FallbackTest (semantic/).

### F. Tabs and their counts

- `SessionViews.view(bookIds)` (P/features/search/semantic/SessionViews.kt:10-16) is implemented by
  `HybridSession.view`, HybridSearchEngine.kt:442-447. It filters the fused list in memory and returns null when the
  list is lexical-only and longer than 150 lines.
- VM: `listed = session as? SessionViews` (:1358). The tab click → `executeFilteredSearch` uses `listed?.view(...)`
  (:2136-2141). If that gives nothing it opens a new session (:2144-2160). In catalog mode `listed` is null, so each
  tab is a new search.
- Counts are not a search per tab. They are the facets of the one search (:1320) plus `withMeaningHits` →
  `meaningBookCounts` → `plusBooks` (:1505-1513; MeaningFacets.kt:34-51), then `applyFacets` →
  `buildSearchTreeUseCase` (:1536-1551).
- Tabs list: `tabCategoriesFlow` (:685-695) plus `showOtherCategories`, which adds count 0 (:1521-1533).
- `CategoryTabs` only sorts by `count` and shows titles (SearchResultScreen.kt:1060-1068).
- `HybridSession.meaningBookCounts` (HybridSearchEngine.kt:478-485) may run one extra lexical query
  (`wordHitsAmong`, :488-496) when there are more than 150 lexical hits.

### Not confirmed

- The SeforimLibrary submodule folder /home/user/Pardes/SeforimLibrary is empty in this checkout. I could not read
  the definitions of `LineHit` / `SearchSession` / `SearchEngine` / `SearchFacets` or `LuceneSearchEngine`'s filter
  code (the query type, and whether the book-id filter is cached). The `LineHit` fields seen in use are bookId,
  bookTitle, lineId, lineIndex, snippet, score, rawText, lineStart, lineEnd.

## The brief the agent got

Read-only code mapping in the Kotlin desktop app at /home/user/Pardes. I am writing an implementation plan and need
exact anchors (file path + line numbers + function/val names), not design advice. Do not change, build or run
anything. Thoroughness: very thorough within the scope below.

Base package dir (call it P): /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp
Search code: P/features/search/ (SearchResultViewModel.kt, SearchResultScreen.kt, domain/CatalogOrderSearch.kt,
semantic/ with HybridSearchEngine and IndependentSemanticSearch), P/framework/search/, tests under
/home/user/Pardes/SeforimApp/src/jvmTest/kotlin/... The search engine interfaces (`SearchEngine`, `SearchSession`,
`LineHit`) come from the submodule /home/user/Pardes/SeforimLibrary (read it, never propose editing it). Background
reading: /home/user/Pardes/SEMANTIC_SEARCH.md and /home/user/Pardes/docs/SEARCH_ORDER_ISSUES.md.

Context: results can be sorted "by the library's order" (סדר הספרייה). With the semantic pack installed the search
is hybrid: a lexical (words) side and a dense (meaning) side, up to 150 lines each. Today in the library's order
every found line has equal standing, so a line that only the dense side found, weakly related, can come first
because its book is early in the catalog.

Answer each, with file:line anchors and a short quote of the deciding lines:

A. P/features/search/domain/CatalogOrderSearch.kt: explain `CatalogOrderEngine` and `CatalogOrderSession` step by
step: where the list of books with hits comes from (facets), how books are read in catalog order, and exactly where
the dense-only lines ("the lines the meaning search alone found") are merged among them. At that point in the code,
what information is available per line: can the code tell a lexical hit from a dense-only one (a flag, a set, two
lists)? Is a dense score / rank available per line? Quote the merge.

B. The UI side of the sort: where the sort modes are declared (enum or sealed type, names, string keys with Hebrew
labels), where the user switches it (SearchResultScreen.kt or a child), and how the results list is rendered: is it a
flat paged list of hits, and does it already have any notion of a section header / separator row between groups of
results (for example the "עוד N תוצאות מ..." row)? Name the list item types.

C. Scope filtering ("search in a category"): where the category/book scope picked in the search bar becomes a
filter: the function that turns categories into book ids, where the lexical query gets the filter and where the
dense (vector) query gets it (which Lucene filter/query type, built per search or cached?). Is the category→books
map taken from the in-memory catalog (which class)? Note any place where a filter over many thousands of book ids is
rebuilt on every search or every page.

D. What happens when the words find nothing in the scope: find the code path that then scores the whole scope on the
dense side ("only a search the words find nothing for still scores a whole scope", `EXACT_LIMIT`, `EXACT_SHARE` in
IndependentSemanticSearch) and quote it.

E. Measurement tools already in the repo: `ScopeSearchBench` (where, how it is run, what it prints), the debug log
line that times each stage of a search (where it is emitted, what stages), `DenseTrapTest`, and any other bench under
jvmTest for the search.

F. The tabs above the results (`SessionViews`) and their counts: where the counts per tab/category are computed and
whether that is a separate search per tab.

Report format: plain text, one section per letter, bullets with `path:line`. Put anything you could not confirm
under a final "Not confirmed" heading rather than guessing. No advice, no plan. Keep it under about 900 words.

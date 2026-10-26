package io.github.kdroidfilter.seforimapp.android.ui.search

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.data.SearchScope
import io.github.kdroidfilter.seforimapp.android.search.BookDetailsLoader
import io.github.kdroidfilter.seforimapp.android.search.SearchEntity
import io.github.kdroidfilter.seforimapp.android.search.SearchEntityFinder
import io.github.kdroidfilter.seforimapp.android.search.SearchService
import io.github.kdroidfilter.seforimapp.android.search.countFacets
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Desktop SearchResultViewModel LAZY_PAGE_SIZE and ExecuteSearchUseCase.DEFAULT_NEAR. */
private const val PAGE_SIZE = 25
private const val DEFAULT_NEAR = 5

/** No book has it: a filter on it finds nothing (desktop NO_BOOK_ID). */
private const val NO_BOOK_ID = -1L

/** Pages a restored search tab loads at most to come back to its place in the list. */
private const val MAX_RESTORE_PAGES = 12

/** Desktop passageContext: about this many characters around the found line, at most this many lines on each side. */
private const val CONTEXT_MAX_LINES = 30
private const val CONTEXT_CHARS = 700

/** An HTML tag, to read a line or snippet as plain text (desktop HTML_TAG). */
internal val HTML_TAG = Regex("<[^>]+>")

/** A category of the results with its hit count and its sub-categories (desktop SearchTreeCategory, without its books). */
data class SearchTreeCategory(val category: Category, val count: Int, val children: List<SearchTreeCategory>)

enum class SearchAvailability { Checking, Ready, IndexMissing }

/** Where the list should scroll to once its items are there: bumped by a new search, a filter, or the way back. */
@Immutable
data class ScrollTarget(val stamp: Long = 0L, val anchorId: Long = -1L, val index: Int = 0, val offset: Int = 0)

@Immutable
data class SearchUiState(
    /** The query to search next. */
    val query: String = "",
    /** The query the shown results were searched with (frozen at search time). */
    val executedQuery: String = "",
    val availability: SearchAvailability = SearchAvailability.Checking,
    /** Why the last search failed (I/O error, or Lucene unable to run on this device). */
    val failure: String? = null,
    /** Off searches the base books only. */
    val globalExtended: Boolean = false,
    val baseBooksHadNoResults: Boolean = false,
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    /** The book (or the book of the TOC entry) the search was scoped to. */
    val scopeBook: Book? = null,
    val scopeTocId: Long? = null,
    /** The author whose books the search was scoped to, as their name shows (AuthorNames.display). */
    val scopeAuthorName: String? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Long? = null,
    /** Exact per-book hit counts (facets): the "עוד N תוצאות מ…" links. */
    val bookCounts: Map<Long, Int> = emptyMap(),
    /** The results' categories with counts (facets), the roots of which are the category tabs. */
    val tree: List<SearchTreeCategory> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    /** The books the results were narrowed to ("more from this book"). */
    val bookFilterIds: Set<Long> = emptySet(),
    /** A filter (category tab, one book) is being applied over the shown results. */
    val isFiltering: Boolean = false,
    /** The scope book's TOC (the scoped search's TOC pane, tablets), its entries' hit counts and the ones checked. */
    val tocTree: SearchTocTree? = null,
    val tocCounts: Map<Long, Int> = emptyMap(),
    val selectedTocIds: Set<Long> = emptySet(),
    val scrollTarget: ScrollTarget = ScrollTarget(),
)

/** A book's TOC: its root entries and each entry's children (desktop TocTree). */
@Immutable
data class SearchTocTree(val roots: List<TocEntry>, val children: Map<Long, List<TocEntry>>)

/** A search run in a tab, for its title and the history (the shell records it). */
data class ExecutedSearch(val query: String, val scope: SearchScope, val globalExtended: Boolean)

/** What a restored search tab showed (desktop SearchPersistedState): its category tab and its place in the list. */
data class SearchViewRestore(val categoryIds: Set<Long>, val index: Int, val offset: Int, val anchorId: Long)

/** A line of the preview, with the ranges of its text (as [buildAnnotatedFromHtml] lays it) the search highlights. */
@Immutable
data class PreviewLine(val line: Line, val ranges: List<IntRange>)

/**
 * The selected result, read in place: its lines around it. Desktop also loads its book's details here for its
 * book-details pane; Android loads them apart ([SearchViewModel.selectedBook]), as they would hold the passage back.
 */
@Immutable
data class PassagePreview(val hit: SearchResult, val lines: List<PreviewLine>)

/**
 * Port of desktop 0.6.10 features/search/SearchResultViewModel.kt on the same Lucene engine:
 * - a search runs its first page and the facets together; nothing in the base books widens it to all of them, and
 *   says so; a book or TOC scope (the bar's) is searched whole, and so are an author's books (0.6.5 1723de06 and
 *   9e3d19a8: kept through the category tabs and "more from this book", and when they are left);
 * - pages of 25 loaded on demand;
 * - the category tabs ([showOnlyCategory]) re-query Lucene on a category, keeping the base-books choice;
 * - "more from this book" ([showMoreFromBook]) narrows to one book, searched whole, keeping the full results to come
 *   back to as they were ([backFromBook]);
 * - the preview of the selected result (tablets) and the knowledge panel of the book or author the query names.
 * Not ported: the cold-boot snapshot (SearchTabCache), the TOC facet pane and the meaning-based snippets (closed add-on).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repo: SeforimRepository,
    private val service: SearchService,
    destination: Destination.Search,
    private val restore: SearchViewRestore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState(query = destination.query.trim(), globalExtended = destination.globalExtended))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Breadcrumb pieces per line id (categories, book, TOC path), requested by the visible rows as on desktop. */
    private val _breadcrumbs = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val breadcrumbs: StateFlow<Map<Long, List<String>>> = _breadcrumbs.asStateFlow()

    private val _executed = MutableSharedFlow<ExecutedSearch>(extraBufferCapacity = 4)

    /** Searches run in this tab after the one it opened with (which the shell recorded when it opened the tab). */
    val executed: SharedFlow<ExecutedSearch> = _executed.asSharedFlow()

    /** Where this tab's search looks (desktop's persisted scope, the one truth of the search). */
    var searchScope: SearchScope = destination.scope.let { if (it is SearchScope.Category) SearchScope.Global else it }
        private set

    private var job: Job? = null
    private var loadMoreJob: Job? = null
    private val sessionMutex = Mutex()
    private var session: SearchSession? = null
    private var tocAllowedLineIds: Set<Long> = emptySet()
    private var generation = 0
    private var currentQuery = ""

    /** The list's position as the user leaves it (desktop onScroll), to start from when it is composed again. */
    var scrollIndex = 0
        private set
    var scrollOffset = 0
        private set
    private var anchorId = -1L

    private val bookCache = HashMap<Long, Book?>()
    private val categoryPathCache = HashMap<Long, List<Category>>()
    private val booksUnderCategory = HashMap<Long, Set<Long>>()
    private val tocIndexes = HashMap<Long, TocLineIndex>()
    private val bookDetailsLoader = BookDetailsLoader(repo)

    /** The name of an author and the ids of their books (the bar lists the same, SearchBarViewModel.showAuthorBooks). */
    private class AuthorScope(val id: Long, val name: String, val bookIds: List<Long>)

    // The author last searched in, loaded once for the status line and their books (desktop authorScope)
    private var authorScopeCache: AuthorScope? = null

    private suspend fun authorScope(authorId: Long): AuthorScope? =
        authorScopeCache?.takeIf { it.id == authorId }
            ?: readOrNull { repo.getAuthorDetails(authorId) }
                ?.let { author -> AuthorScope(author.id, AuthorNames.display(author.name), author.books.map { it.id }) }
                ?.also { authorScopeCache = it }

    /**
     * The books searched when [scope] is an author's, null for any other scope. An author without books searches none:
     * a book id no book has (an empty filter would search everywhere). Desktop authorBookIds.
     */
    private suspend fun authorBookIds(scope: SearchScope = searchScope): List<Long>? {
        val authorId = (scope as? SearchScope.Author)?.authorId ?: return null
        return authorScope(authorId)?.bookIds.orEmpty().ifEmpty { listOf(NO_BOOK_ID) }
    }

    init {
        viewModelScope.launch {
            val ready = runCatching { service.isTextSearchAvailable() }.getOrDefault(false)
            _state.update { it.copy(availability = if (ready) SearchAvailability.Ready else SearchAvailability.IndexMissing) }
            if (ready && _state.value.query.isNotBlank()) {
                executeSearch(report = false)
                restore?.let { restoreView(it) }
            }
        }
    }

    /**
     * A restored tab comes back on its category tab and at its place in the list, once the search (and its facets,
     * which make the tabs) is done; whatever the user does meanwhile wins. Pages are loaded up to that place, a few at most.
     */
    private suspend fun restoreView(r: SearchViewRestore) {
        suspend fun awaitOwn(): Boolean {
            val own = job ?: return false
            own.join()
            return job === own && !own.isCancelled
        }
        if (!awaitOwn()) return
        if (r.categoryIds.isNotEmpty() && _state.value.tree.isNotEmpty()) {
            _state.update { it.copy(selectedCategoryIds = r.categoryIds) }
            executeFilteredSearch()
            if (!awaitOwn()) return
        }
        val own = job
        var pages = 0
        while (pages++ < MAX_RESTORE_PAGES && job === own && _state.value.hasMore &&
            _state.value.results.none { it.lineId == r.anchorId } && _state.value.results.size <= r.index
        ) {
            loadMore()
            loadMoreJob?.join()
        }
        if (job !== own) return
        scrollIndex = r.index
        scrollOffset = r.offset
        anchorId = r.anchorId
        _state.update { it.copy(scrollTarget = ScrollTarget(System.nanoTime(), r.anchorId, r.index, r.offset)) }
    }

    // ---- Selection, preview, entity ----

    // The result picked in the list; the first one until one is
    private val selectedLineId = MutableStateFlow<Long?>(null)

    /** Shows a result in the preview beside the list. */
    fun selectResult(lineId: Long) {
        selectedLineId.value = lineId
    }

    private val selectedHit = combine(_state.map { it.results }.distinctUntilChanged(), selectedLineId) { results, id ->
        results.firstOrNull { it.lineId == id } ?: results.firstOrNull()
    }

    /** The selected result in its book (tablets' second pane); computed only while collected. */
    val preview: StateFlow<PassagePreview?> =
        selectedHit
            .distinctUntilChangedBy { it?.lineId }
            .mapLatest { hit ->
                hit?.let {
                    PassagePreview(
                        hit = it,
                        lines = runCatching { previewLines(it, _state.value.executedQuery) }.onFailure { e -> if (e is CancellationException) throw e }.getOrDefault(emptyList()),
                    )
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The selected result's book (the first result's until one is picked) for the tablets' book-details pane, desktop
     * PassagePreview.book. A flow of its own so the passage never waits for it; computed only while the pane is open.
     */
    val selectedBook: StateFlow<SearchEntity.BookEntity?> =
        selectedHit
            .map { it?.bookId }
            .distinctUntilChanged()
            .mapLatest { bookId -> bookId?.let { bookDetailsLoader.load(it) } }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The book or author the executed query names, for the knowledge panel; null mostly. */
    val entity: StateFlow<SearchEntity?> =
        _state.map { it.executedQuery.trim() }
            .distinctUntilChanged()
            .mapLatest { q ->
                if (q.isEmpty()) {
                    null
                } else {
                    runCatching {
                        val lookup = service.lookup() ?: return@runCatching null
                        SearchEntityFinder(lookup, repo, bookDetailsLoader).find(q)
                    }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The lines around a result, by length rather than count: short lines (a verse, a heading) come many, long
     * paragraphs few, about [CONTEXT_CHARS] characters on each side, one line at least. Desktop passageContext, with the
     * engine's highlight ranges of [query] (desktop's PassagePreview asks searchEngine.highlightRanges per line).
     */
    private suspend fun previewLines(hit: SearchResult, query: String): List<PreviewLine> {
        val window = repo.getLines(hit.bookId, (hit.lineIndex - CONTEXT_MAX_LINES).coerceAtLeast(0), hit.lineIndex + CONTEXT_MAX_LINES)
        val at = window.indexOfFirst { it.id == hit.lineId }
        val lines = if (at < 0) {
            window
        } else {
            fun side(lines: List<Line>): List<Line> {
                var chars = 0
                return lines.takeWhile { line ->
                    val keep = chars == 0 || chars < CONTEXT_CHARS
                    chars += line.content.replace(HTML_TAG, "").length
                    keep
                }
            }
            side(window.subList(0, at).asReversed()).asReversed() + window[at] + side(window.subList(at + 1, window.size))
        }
        val engine = runCatching { service.engine() }.getOrNull()
        return lines.map { line ->
            val ranges = if (engine == null || query.isBlank()) emptyList() else engine.highlightRanges(buildAnnotatedFromHtml(line.content, PREVIEW_LAYOUT_SIZE).text, query)
            PreviewLine(line, ranges)
        }
    }

    // ---- Searching ----

    /** The query to search next; does not search by itself. */
    fun setQuery(query: String) = _state.update { it.copy(query = query.trim()) }

    /** The status line's link: the base books or all of them, searched again (desktop SetGlobalExtended + ExecuteSearch). */
    fun setGlobalExtended(extended: Boolean) {
        _state.update { it.copy(globalExtended = extended) }
        executeSearch()
    }

    /**
     * A search from the bar: [scope] is the bar's (its picked book, TOC entry or author, else everywhere) and replaces
     * this tab's, with its filters; [widen] (the bar's "בכל הספרים" / Ctrl+Enter) searches all books, Enter keeps the choice.
     */
    fun searchFromBar(query: String, scope: SearchScope, widen: Boolean) {
        dropBeforeBook()
        if (widen) _state.update { it.copy(globalExtended = true) }
        setQuery(query)
        searchScope = scope
        _state.update { it.copy(selectedCategoryIds = emptySet(), bookFilterIds = emptySet(), selectedTocIds = emptySet()) }
        executeSearch()
    }

    /** Desktop executeSearch: a new search, with its facets; [report] it for the tab title and the history. */
    fun executeSearch(report: Boolean = true) {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        dropBeforeBook()
        val scope = searchScope
        if (report) _executed.tryEmit(ExecutedSearch(q, scope, _state.value.globalExtended))
        job?.cancel()
        loadMoreJob?.cancel()
        _breadcrumbs.value = emptyMap()
        scrollIndex = 0
        scrollOffset = 0
        anchorId = -1L
        job = viewModelScope.launch {
            val searchJob = this
            _state.update {
                it.copy(
                    isLoading = true, results = emptyList(), hasMore = false, isLoadingMore = false, progressCurrent = 0, progressTotal = null,
                    baseBooksHadNoResults = false, failure = null, bookCounts = emptyMap(), tree = emptyList(), isFiltering = false,
                    scrollTarget = ScrollTarget(stamp = System.nanoTime()),
                )
            }
            guard {
                val engine = service.engine() ?: return@guard indexMissing()
                // The files may have been added since the screen opened (pushed from a computer, reinstall)
                _state.update { it.copy(availability = SearchAvailability.Ready) }
                currentQuery = q
                val fetchBookId = when (scope) {
                    is SearchScope.Book -> scope.bookId
                    is SearchScope.Toc -> scope.bookId
                    else -> null
                }?.takeIf { it > 0 }
                val fetchTocId = (scope as? SearchScope.Toc)?.tocId?.takeIf { it > 0 }
                val scopeBook = fetchBookId?.let { book(it) }
                val authorBooks = authorBookIds(scope)
                val scopeAuthorName = (scope as? SearchScope.Author)?.let { authorScope(it.authorId)?.name }
                tocCountsAcc.clear()
                _state.update {
                    it.copy(scopeBook = scopeBook, scopeTocId = fetchTocId, scopeAuthorName = scopeAuthorName, tocTree = null, tocCounts = emptyMap(), selectedTocIds = emptySet())
                }
                // A book, a TOC entry or an author the user chose is searched whole, base books or not
                var baseOnly = !_state.value.globalExtended && fetchTocId == null && fetchBookId == null && authorBooks == null
                val facetsBookIds = fetchBookId?.let { listOf(it) } ?: authorBooks
                val lineIds = if (fetchTocId != null && fetchBookId != null) lineIdsUnderToc(fetchTocId, fetchBookId) else null
                val pageBookIds = facetsBookIds?.takeIf { lineIds == null }

                // The facets run beside the first page (desktop runs both at once); the results show as soon as their
                // page is there, the tabs and the "more" counts once the facets are counted, which takes longer on a
                // phone. Cancelling the search cancels them (countFacets stops between slices, never interrupting a read)
                fun facetsAsync(baseOnly: Boolean) = searchJob.async(Dispatchers.IO) {
                    try {
                        engine.countFacets(q, DEFAULT_NEAR, bookIds = facetsBookIds, baseBookOnly = baseOnly)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        Log.w(TAG, "search: facets", t)
                        null
                    }
                }
                var facets = facetsAsync(baseOnly)
                var first = openFirstPage(engine, q, baseOnly, bookIds = pageBookIds, lineIds = lineIds)
                if (baseOnly && first.session != null && first.page?.hits.isNullOrEmpty()) {
                    // Nothing in the base books: all of them, and the status line says so (desktop fallback)
                    facets.cancel()
                    withContext(Dispatchers.IO + NonCancellable) { first.session.close() }
                    baseOnly = false
                    _state.update { it.copy(globalExtended = true, baseBooksHadNoResults = true) }
                    facets = facetsAsync(baseOnly)
                    first = openFirstPage(engine, q, baseOnly, bookIds = pageBookIds, lineIds = lineIds)
                }
                tocAllowedLineIds = lineIds.orEmpty()
                show(first, q)
                // The scoped book's TOC for its pane (desktop buildTocTreeForBook), once the first page shows: it maps
                // every line of the book, which must not hold the results back (on a phone the pane is not even shown)
                val tree = scopeBook?.let { b -> runCatching { tocIndex(b.id).tree }.onFailure { if (it is CancellationException) throw it }.getOrNull() }
                if (tree != null) _state.update { it.copy(tocTree = tree) }
                countTocHits(_state.value.results)
                applyFacets(facets.await())
            }
        }
    }

    private class First(val session: SearchSession?, val page: SearchPage?)

    /** Next page of the open session (desktop loadMore, triggered near the end of the list). */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        val gen = generation
        _state.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            val page = runCatching { sessionMutex.withLock { if (gen == generation) session?.let { withContext(Dispatchers.IO + NonCancellable) { it.nextPage(PAGE_SIZE) } } else null } }
                .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "search: next page", it) }
                .getOrNull()
            if (gen != generation) return@launch
            val hits = page?.hits.orEmpty().let { hits -> if (tocAllowedLineIds.isEmpty()) hits else hits.filter { it.lineId in tocAllowedLineIds } }
            val added = hitsToResults(hits, currentQuery)
            _state.update {
                val results = it.results + added
                it.copy(results = results, hasMore = page != null && !page.isLastPage, isLoadingMore = false, progressCurrent = results.size)
            }
            countTocHits(added)
        }
    }

    /** Shows one top category's results only (a tab above the results), or all of them for null. */
    fun showOnlyCategory(categoryId: Long?) {
        dropBeforeBook()
        _state.update { it.copy(selectedCategoryIds = emptySet(), bookFilterIds = emptySet(), selectedTocIds = emptySet()) }
        if (categoryId != null) {
            // The category and its sub-categories in the tree, as desktop setCategoryChecked
            val ids = HashSet<Long>()
            fun collect(node: SearchTreeCategory) {
                ids += node.category.id
                node.children.forEach(::collect)
            }
            findNode(_state.value.tree, categoryId)?.let(::collect) ?: ids.add(categoryId)
            _state.update { it.copy(selectedCategoryIds = ids) }
        } else {
            clearScopeIfNoFilter()
        }
        executeFilteredSearch()
    }

    // The full results, kept while one book's are shown ("more from it"), to come back to as they were
    private class BeforeBook(val ui: SearchUiState, val session: SearchSession?, val generation: Int, val allowed: Set<Long>, val scroll: ScrollTarget)

    private var beforeBook: BeforeBook? = null

    /** Narrows the results to one book ("more from it"), keeping the full ones to come back to. */
    fun showMoreFromBook(bookId: Long) {
        viewModelScope.launch {
            sessionMutex.withLock {
                val ui = _state.value
                // A search still running is cancelled by the book's: nothing whole to come back to then (the way back
                // searches again); the kept session stays open for the way back
                beforeBook = if (ui.isLoading) {
                    null
                } else {
                    BeforeBook(ui.copy(isLoadingMore = false), session, generation, tocAllowedLineIds, ScrollTarget(System.nanoTime(), anchorId, scrollIndex, scrollOffset))
                        .also { session = null }
                }
            }
            _state.update { it.copy(bookFilterIds = it.bookFilterIds + bookId) }
            executeFilteredSearch()
        }
    }

    /** Back to the full results as they were (results, pages, tabs, place in the list): no new search. */
    fun backFromBook() {
        val saved = beforeBook ?: run {
            // Without kept results (a search cut short): drop the book filter, one search
            _state.update { it.copy(bookFilterIds = emptySet()) }
            clearScopeIfNoFilter()
            executeFilteredSearch()
            return
        }
        beforeBook = null
        job?.cancel()
        loadMoreJob?.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) {
                sessionMutex.withLock {
                    val old = session
                    session = saved.session
                    generation++
                    tocAllowedLineIds = saved.allowed
                    withContext(Dispatchers.IO) { old?.close() }
                }
            }
            scrollIndex = saved.scroll.index
            scrollOffset = saved.scroll.offset
            anchorId = saved.scroll.anchorId
            _state.value = saved.ui.copy(isLoading = false, isFiltering = false, scrollTarget = saved.scroll.copy(stamp = System.nanoTime()))
        }
    }

    // A new search drops the kept results
    private fun dropBeforeBook() {
        beforeBook?.session?.let { s -> viewModelScope.launch(Dispatchers.IO + NonCancellable) { runCatching { s.close() } } }
        beforeBook = null
    }

    // With no filter left, the search runs everywhere again (desktop maybeClearFiltersIfNoneChecked)
    private fun clearScopeIfNoFilter() {
        val s = _state.value
        if (s.selectedCategoryIds.isNotEmpty() || s.bookFilterIds.isNotEmpty() || s.selectedTocIds.isNotEmpty()) return
        _state.update { it.copy(scopeBook = null, scopeTocId = null, tocTree = null) }
        // Back to all the results: an author's books stay the searched ones
        searchScope = searchScope as? SearchScope.Author ?: SearchScope.Global
    }

    /** The scoped search's TOC pane: an entry (and its sub-entries) checked or not, searched whole (desktop setTocChecked). */
    fun setTocChecked(tocId: Long, checked: Boolean) {
        _state.update { it.copy(selectedTocIds = if (checked) it.selectedTocIds + tocId else it.selectedTocIds - tocId) }
        if (!checked) clearScopeIfNoFilter()
        executeFilteredSearch()
    }

    // Hits per TOC entry of the scope book, its ancestors included, as pages come (desktop updateTocCountsForHits)
    private val tocCountsAcc = HashMap<Long, Int>()

    private suspend fun countTocHits(results: List<SearchResult>) {
        val bookId = _state.value.scopeBook?.id ?: return
        val subset = results.filter { it.bookId == bookId }
        if (subset.isEmpty()) return
        val index = runCatching { tocIndex(bookId) }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return
        for (hit in subset) {
            var current: Long? = index.lineToToc[hit.lineId] ?: continue
            var guard = 0
            while (current != null && guard++ < 500) {
                tocCountsAcc[current] = (tocCountsAcc[current] ?: 0) + 1
                current = index.parent[current]
            }
        }
        _state.update { it.copy(tocCounts = HashMap(tocCountsAcc)) }
    }

    /**
     * Desktop executeFilteredSearch: re-queries Lucene on the selected category's or book's books; the tabs keep the
     * search's counts. Chosen books are searched whole; a category keeps the base-books choice.
     */
    private fun executeFilteredSearch() {
        val q = _state.value.query.trim().ifBlank { return }
        job?.cancel()
        loadMoreJob?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isFiltering = it.results.isNotEmpty(), isLoadingMore = false, failure = null) }
            guard {
                val engine = service.engine() ?: return@guard indexMissing()
                val s = _state.value
                // "More from this book" searches that book only, even under a category tab (desktop adds the tab's
                // books to it, so the "one book" view showed the whole category)
                val bookIds = HashSet<Long>()
                if (s.bookFilterIds.isNotEmpty()) {
                    bookIds += s.bookFilterIds
                } else {
                    for (catId in s.selectedCategoryIds) bookIds += booksUnder(catId)
                }
                // In an author's books only: theirs among the category tab's, all theirs without one
                val authorBooks = authorBookIds()
                if (authorBooks != null && s.bookFilterIds.isEmpty()) {
                    if (s.selectedCategoryIds.isEmpty()) {
                        bookIds += authorBooks
                    } else {
                        bookIds.retainAll(authorBooks.toSet())
                        if (bookIds.isEmpty()) bookIds += NO_BOOK_ID
                    }
                }
                val baseOnly = !s.globalExtended
                val onlyBooks = s.bookFilterIds.isNotEmpty() || s.selectedCategoryIds.isEmpty() || authorBooks != null
                val tocLineIds = HashSet<Long>()
                for (tocId in s.selectedTocIds) {
                    val bookId = s.scopeBook?.id ?: repo.getTocEntry(tocId)?.bookId ?: continue
                    tocLineIds += lineIdsUnderToc(tocId, bookId)
                }
                currentQuery = q
                tocAllowedLineIds = emptySet()
                // Chosen TOC entries and books are searched whole; a category keeps the base-books choice
                val first = if (tocLineIds.isNotEmpty()) {
                    openFirstPage(engine, q, false, bookIds = null, lineIds = tocLineIds)
                } else if (bookIds.isNotEmpty()) {
                    openFirstPage(engine, q, baseOnly && !onlyBooks, bookIds = bookIds, lineIds = null)
                } else if (s.selectedCategoryIds.isNotEmpty() || s.bookFilterIds.isNotEmpty() || s.selectedTocIds.isNotEmpty()) {
                    First(null, null)
                } else {
                    openFirstPage(engine, q, baseOnly, bookIds = null, lineIds = null)
                }
                show(first, q)
                _state.update { it.copy(scrollTarget = ScrollTarget(stamp = System.nanoTime())) }
            }
            _state.update { it.copy(isFiltering = false) }
        }
    }

    /** Desktop cancelSearch: stops the running search; the results already shown stay. */
    fun cancel() {
        job?.cancel()
        loadMoreJob?.cancel()
        viewModelScope.launch(NonCancellable) {
            sessionMutex.withLock {
                generation++
                val s = session
                session = null
                withContext(Dispatchers.IO) { s?.close() }
            }
        }
        _state.update { it.copy(isLoading = false, isLoadingMore = false, hasMore = false, isFiltering = false) }
    }

    /** Desktop onScroll: where the list stands, to come back there. */
    fun onScroll(anchor: Long, index: Int, offset: Int) {
        anchorId = anchor
        scrollIndex = index
        scrollOffset = offset
    }

    /** Requests the breadcrumb of a visible result once (desktop GetBreadcrumbPiecesUseCase). */
    fun requestBreadcrumb(result: SearchResult) {
        if (_breadcrumbs.value.containsKey(result.lineId)) return
        _breadcrumbs.update { it + (result.lineId to emptyList()) }
        viewModelScope.launch {
            val pieces = runCatching { breadcrumbPieces(result) }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
            if (pieces.isNotEmpty()) _breadcrumbs.update { it + (result.lineId to pieces) }
        }
    }

    suspend fun bookOf(bookId: Long): Book? = book(bookId)

    /** Opens a session and reads its first page; a session opened for a search cancelled meanwhile is closed, not leaked. */
    private suspend fun openFirstPage(engine: SearchEngine, q: String, baseOnly: Boolean, bookIds: Collection<Long>?, lineIds: Collection<Long>?): First {
        val first = withContext(Dispatchers.IO + NonCancellable) {
            val s = engine.openSession(q, DEFAULT_NEAR, bookIds = bookIds, lineIds = lineIds, baseBookOnly = baseOnly) ?: return@withContext First(null, null)
            First(s, runCatching { s.nextPage(PAGE_SIZE) }.onFailure { s.close() }.getOrThrow())
        }
        if (!currentCoroutineContext().isActive) {
            withContext(Dispatchers.IO + NonCancellable) { first.session?.close() }
            throw CancellationException("search replaced")
        }
        return first
    }

    private suspend fun show(first: First, q: String) {
        withContext(NonCancellable) {
            sessionMutex.withLock {
                generation++
                val old = session
                session = first.session
                withContext(Dispatchers.IO) { old?.close() }
            }
        }
        val page = first.page
        val hits = page?.hits.orEmpty().let { hits -> if (tocAllowedLineIds.isEmpty()) hits else hits.filter { it.lineId in tocAllowedLineIds } }
        val results = hitsToResults(hits, q)
        _state.update {
            it.copy(
                isLoading = false,
                results = results,
                executedQuery = q,
                hasMore = page != null && !page.isLastPage,
                progressCurrent = results.size,
                progressTotal = page?.totalHits ?: 0L,
            )
        }
    }

    /** Shows the facet counts and builds the result tree from them (desktop applyFacets). */
    private suspend fun applyFacets(facets: SearchFacets?) {
        if (facets == null) return
        // The "more" counts need no tree: shown at once
        _state.update { it.copy(bookCounts = facets.bookCounts) }
        val tree = buildTree(facets.categoryCounts)
        _state.update { it.copy(tree = tree) }
    }

    /** Runs a search step; Lucene failures (I/O, or a runtime class missing on this Android version) end it with a message. */
    private suspend fun guard(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "search failed", t)
            _state.update { it.copy(isLoading = false, isLoadingMore = false, isFiltering = false, failure = t.message ?: t.javaClass.name) }
        }
    }

    private fun indexMissing() = _state.update { it.copy(availability = SearchAvailability.IndexMissing, isLoading = false, isFiltering = false) }

    /** Desktop ExecuteSearchUseCase.hitsToResults. */
    private fun hitsToResults(hits: List<LineHit>, rawQuery: String): List<SearchResult> {
        val trimmed = rawQuery.trim()
        return hits.map { hit ->
            SearchResult(
                bookId = hit.bookId,
                bookTitle = hit.bookTitle,
                lineId = hit.lineId,
                lineIndex = hit.lineIndex,
                snippet = hit.snippet.ifBlank { hit.rawText },
                // Exact matches rank slightly higher, as on desktop
                rank = hit.score.toDouble() + if (trimmed.isNotEmpty() && hit.rawText.contains(trimmed)) 1e-3 else 0.0,
            )
        }
    }

    // A search cancelled meanwhile stops here (it must not go on writing the state of the one replacing it), and a
    // failed or cancelled read is not kept: a category read as empty would show no result for its tab again
    private suspend fun book(id: Long): Book? = bookCache[id] ?: readOrNull { repo.getBookCore(id) }?.also { bookCache[id] = it }

    private suspend fun booksUnder(categoryId: Long): Set<Long> =
        booksUnderCategory[categoryId] ?: readOrNull { repo.getBooksUnderCategoryTree(categoryId).mapTo(HashSet()) { it.id } }
            ?.also { booksUnderCategory[categoryId] = it }.orEmpty()

    private suspend fun <T> readOrNull(read: suspend () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.w(TAG, "search: read", t)
        null
    }

    /** The lines under a TOC entry and its descendants (desktop SearchTocUseCase.collectLineIdsForTocSubtree). */
    private suspend fun lineIdsUnderToc(tocId: Long, bookId: Long): Set<Long> {
        val index = tocIndex(bookId)
        val out = HashSet<Long>()
        val stack = ArrayDeque<Long>().apply { add(tocId) }
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            index.lines[id]?.let(out::addAll)
            index.children[id]?.let { children -> children.forEach { stack.add(it.id) } }
        }
        return out
    }

    /** A book's TOC with its lines (desktop SearchTocUseCase.ensureTocLineIndex), built once per book. */
    private suspend fun tocIndex(bookId: Long): TocLineIndex = tocIndexes[bookId] ?: withContext(Dispatchers.IO) {
        val mappings = repo.getLineTocMappingsForBook(bookId)
        val entries = repo.getBookToc(bookId)
        TocLineIndex(
            lines = mappings.groupBy({ it.tocEntryId }, { it.lineId }),
            lineToToc = mappings.associate { it.lineId to it.tocEntryId },
            children = entries.filter { it.parentId != null }.groupBy { it.parentId!! },
            parent = entries.associate { it.id to it.parentId },
            roots = entries.filter { it.parentId == null },
        )
    }.also { tocIndexes[bookId] = it }

    private class TocLineIndex(
        val lines: Map<Long, List<Long>>,
        val lineToToc: Map<Long, Long>,
        val children: Map<Long, List<TocEntry>>,
        val parent: Map<Long, Long?>,
        val roots: List<TocEntry>,
    ) {
        val tree: SearchTocTree get() = SearchTocTree(roots, children)
    }

    private suspend fun categoryPath(categoryId: Long): List<Category> = categoryPathCache[categoryId] ?: run {
        val path = ArrayList<Category>()
        var current: Long? = categoryId
        while (current != null && path.size < 64) {
            val cat = repo.getCategory(current) ?: break
            path += cat
            current = cat.parentId
        }
        path.asReversed().also { categoryPathCache[categoryId] = it }
    }

    /** Desktop GetBreadcrumbPiecesUseCase: category path, book title, then the TOC path down to the line. */
    private suspend fun breadcrumbPieces(result: SearchResult): List<String> {
        val book = book(result.bookId) ?: return emptyList()
        val pieces = categoryPath(book.categoryId).map { it.title }.toMutableList()
        pieces += book.title
        val toc = ArrayList<String>()
        var current = repo.getTocEntryIdForLine(result.lineId)
        while (current != null && toc.size < 200) {
            val entry = repo.getTocEntry(current) ?: break
            toc.add(0, entry.text)
            current = entry.parentId
        }
        pieces += if (toc.firstOrNull() == book.title) toc.drop(1) else toc
        return pieces
    }

    /**
     * Desktop BuildSearchTreeUseCase (from facet counts): the categories with hits, sorted by library order. Desktop also
     * reads every book with hits (thousands for a common word in all the books) for its facet pane's book rows, which
     * this app has not: only the categories are read here (each book's categories are already counted in the facets).
     */
    private suspend fun buildTree(categoryCounts: Map<Long, Int>): List<SearchTreeCategory> {
        val categories = HashMap<Long, Category>()
        for (id in categoryCounts.keys) repo.getCategory(id)?.let { categories[it.id] = it }
        if (categories.isEmpty()) return emptyList()
        val children = categories.values.groupBy { it.parentId }.mapValues { (_, v) -> v.sortedBy { it.order } }
        fun node(cat: Category): SearchTreeCategory = SearchTreeCategory(
            category = cat,
            count = categoryCounts[cat.id] ?: 0,
            children = children[cat.id].orEmpty().map(::node),
        )
        return categories.values.filter { it.parentId == null || it.parentId !in categories }.sortedBy { it.order }.map(::node)
    }

    private fun findNode(list: List<SearchTreeCategory>, id: Long): SearchTreeCategory? {
        for (n in list) {
            if (n.category.id == id) return n
            findNode(n.children, id)?.let { return it }
        }
        return null
    }

    override fun onCleared() {
        val s = session
        session = null
        val kept = beforeBook?.session
        beforeBook = null
        runCatching { s?.close() }
        runCatching { kept?.close() }
    }

    companion object {
        private const val TAG = "Zayit"

        /** The text size the preview's highlight ranges are computed at (ranges do not depend on it). */
        private const val PREVIEW_LAYOUT_SIZE = 16f

        fun factory(repo: SeforimRepository, service: SearchService, destination: Destination.Search, restore: SearchViewRestore? = null) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(repo, service, destination, restore) as T
            }
    }
}

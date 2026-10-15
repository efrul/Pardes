package io.github.kdroidfilter.seforimapp.android.ui.search

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.android.data.SearchScope
import io.github.kdroidfilter.seforimapp.android.search.SearchService
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.shell.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceResolver
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.RepositoryReferenceSource
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ResolvedReference
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimapp.framework.search.MIN_BOOK_QUERY_LENGTH
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class BookSuggestionDto(
    val book: Book,
    val path: List<String>,
    // Typed as one of its acronyms, whole
    val exactAcronym: Boolean = false,
)

@Immutable
data class TocSuggestionDto(val toc: TocEntry, val path: List<String>)

@Immutable
data class SearchBarUiState(
    val globalExtended: Boolean = false,
    val suggestionsVisible: Boolean = false,
    val isReferenceLoading: Boolean = false,
    val bookSuggestions: List<BookSuggestionDto> = emptyList(),
    /** Places the typed text points to (`חולין יב:`), listed above the book suggestions. */
    val jumpSuggestions: List<ResolvedReference> = emptyList(),
    /** Authors the typed text may name, listed before the books. */
    val authorSuggestions: List<AuthorHit> = emptyList(),
    /** The author picked: the books listed are then theirs. */
    val selectedScopeAuthor: AuthorHit? = null,
    val tocSuggestionsVisible: Boolean = false,
    val isTocLoading: Boolean = false,
    val tocSuggestions: List<TocSuggestionDto> = emptyList(),
    val selectedScopeBook: Book? = null,
    val selectedScopeToc: TocEntry? = null,
    // The book was picked by the user (back to the bar to type in its TOC), not put back by a restore
    val bookPickedByUser: Boolean = false,
)

/**
 * The smart search bar's state: books, authors and references suggested as the user types, the picked book's TOC,
 * and where a text search runs. Port of desktop 0.6.1 features/search/SearchHomeViewModel.kt (one per Home tab and
 * one per results tab, as desktop TabsContent's "search-bar" ViewModel), with the 0.6.2 fix 90552dbc (author
 * suggestions kept only when typed word by word); the navigation it emits there is done by the caller here with what
 * [resolvePickedReference] / [deepLinkTarget] return. Without the lookup index (or when Lucene cannot open it) books
 * are suggested from the database titles and acronyms, and no author is.
 */
@OptIn(FlowPreview::class)
class SearchBarViewModel(
    private val repository: SeforimRepository,
    private val service: SearchService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchBarUiState())
    val uiState: StateFlow<SearchBarUiState> = _uiState.asStateFlow()

    private val referenceResolver = ReferenceResolver(RepositoryReferenceSource(repository))

    // Author aliases by id, to tell which one a query was typed as; few authors have any
    private val aliasesByAuthor = ConcurrentHashMap<Long, List<String>>()

    private suspend fun authorAliases(authorId: Long): List<String> =
        aliasesByAuthor[authorId] ?: catching { repository.getAuthorDetails(authorId)?.aliases.orEmpty() }
            .orEmpty()
            .also { aliasesByAuthor[authorId] = it }

    // The books of the picked author, filtered by what is typed next
    private var authorBooks: List<BookSuggestionDto> = emptyList()

    private val referenceQuery = MutableStateFlow("")
    private val tocQuery = MutableStateFlow("")

    private val minTocPrefixLen = 1
    private val maxBookPredictive = 120
    private val maxTocPredictive = 300

    private class LruCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    private val categoryPathCache = LruCache<Long, List<String>>(512)
    private val categoryPathMutex = Mutex()
    private val tocPathCache = LruCache<Long, List<String>>(2048)
    private val tocPathMutex = Mutex()
    private val tocCache = ConcurrentHashMap<Long, List<TocSuggestionDto>>()

    init {
        observeQueries()
    }

    /** The lookup index, or null when it is missing or Lucene cannot open it here. */
    private suspend fun lookup(): LuceneLookupSearchService? = catching { service.lookup() }

    private fun matchRank(text: String, query: String): Int = when {
        text.equals(query, ignoreCase = true) -> 0
        text.startsWith(query, ignoreCase = true) -> 1
        text.contains(query, ignoreCase = true) -> 2
        else -> 3
    }

    private suspend fun buildCategoryPathTitlesCached(catId: Long): List<String> {
        categoryPathMutex.withLock { categoryPathCache[catId]?.let { return it } }
        val path = withContext(Dispatchers.IO) { catching { buildCategoryPathTitles(catId) }.orEmpty() }
        categoryPathMutex.withLock { categoryPathCache[catId] = path }
        return path
    }

    private suspend fun buildTocPathTitlesCached(entry: TocEntry, byId: Map<Long, TocEntry> = emptyMap(), bookTitle: String? = null): List<String> {
        tocPathMutex.withLock { tocPathCache[entry.id]?.let { return it } }
        val path = withContext(Dispatchers.IO) { catching { buildTocPathTitles(entry, byId, bookTitle) }.orEmpty() }
        tocPathMutex.withLock { tocPathCache[entry.id] = path }
        return path
    }

    private fun observeQueries() {
        viewModelScope.launch {
            referenceQuery
                .debounce(120)
                .distinctUntilChanged()
                .collectLatest { qRaw ->
                    val q = qRaw.trim()
                    if (_uiState.value.selectedScopeAuthor != null) {
                        _uiState.value = _uiState.value.copy(
                            isReferenceLoading = false,
                            bookSuggestions = authorBooks.filter { it.titleContains(q) },
                            suggestionsVisible = true,
                        )
                    } else if (q.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isReferenceLoading = false,
                            bookSuggestions = emptyList(),
                            jumpSuggestions = emptyList(),
                            authorSuggestions = emptyList(),
                            suggestionsVisible = false,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isReferenceLoading = q.length >= MIN_BOOK_QUERY_LENGTH, suggestionsVisible = true)
                        val lookup = lookup()
                        val result = withContext(Dispatchers.Default) {
                            coroutineScope {
                                // Books: same acronym-aware suggestions as the book tree search
                                val booksDeferred = async(Dispatchers.Default) { bookSuggestions(lookup, q) }
                                val authorsDeferred = async(Dispatchers.IO) {
                                    catching {
                                        // More than shown: those not typed word by word are dropped (`בבא ב`)
                                        lookup?.let { index ->
                                            index
                                                .suggestAuthors(q, limit = MAX_AUTHOR_SUGGESTIONS * 3)
                                                .mapNotNull { hit ->
                                                    val aliases = authorAliases(hit.id)
                                                    val names = listOf(hit.name, AuthorNames.display(hit.name)) + aliases
                                                    if (!AuthorNames.typedAs(q, names, index::typedAsWordsOf)) return@mapNotNull null
                                                    hit.copy(alias = AuthorNames.matchedAlias(q, hit.name, aliases))
                                                }.take(MAX_AUTHOR_SUGGESTIONS)
                                        }
                                    }.orEmpty()
                                }
                                val jumpsDeferred = async(Dispatchers.IO) { catching { referenceResolver.resolve(q) }.orEmpty() }
                                Suggestions(booksDeferred.await(), jumpsDeferred.await(), authorsDeferred.await())
                            }
                        }
                        _uiState.value = _uiState.value.copy(
                            isReferenceLoading = false,
                            bookSuggestions = result.books,
                            jumpSuggestions = result.jumps,
                            authorSuggestions = result.authors,
                            suggestionsVisible = true,
                        )
                    }
                }
        }

        // Debounced suggestions for the TOC query (only when a book is selected)
        viewModelScope.launch {
            tocQuery
                .debounce(120)
                .distinctUntilChanged()
                .collectLatest { qRaw ->
                    val q = qRaw.trim()
                    val book = _uiState.value.selectedScopeBook
                    val cached = book?.let { tocCache[it.id] }.orEmpty()
                    when {
                        book == null -> _uiState.value = _uiState.value.copy(tocSuggestions = emptyList(), tocSuggestionsVisible = false, isTocLoading = false)
                        q.length < minTocPrefixLen ->
                            _uiState.value = _uiState.value.copy(tocSuggestions = cached, tocSuggestionsVisible = cached.isNotEmpty(), isTocLoading = false)
                        else -> {
                            _uiState.value = _uiState.value.copy(isTocLoading = true, tocSuggestionsVisible = true)
                            val suggestions = withContext(Dispatchers.Default) { tocMatching(cached, q) }
                            _uiState.value = _uiState.value.copy(tocSuggestions = suggestions, tocSuggestionsVisible = true, isTocLoading = false)
                        }
                    }
                }
        }
    }

    /** Desktop lookup.suggestBooks; the database titles and acronyms when the lookup index is not there. */
    private suspend fun bookSuggestions(lookup: LuceneLookupSearchService?, q: String): List<BookSuggestionDto> {
        if (lookup != null) {
            catching { lookup.suggestBooks(q, limit = maxBookPredictive) }?.let { hits ->
                return hits.map { hit ->
                    val book = hit.toBook()
                    BookSuggestionDto(book, buildCategoryPathTitlesCached(book.categoryId) + book.title, hit.exactAcronym)
                }
            }
        }
        if (q.length < MIN_BOOK_QUERY_LENGTH) return emptyList()
        val books = catching {
            (repository.findBooksByTitleLike("%$q%", MAX_FALLBACK_BOOKS) + repository.findBooksByAcronymLike("$q%", MAX_FALLBACK_BOOKS / 2))
                .distinctBy { it.id }
                .sortedWith(compareByDescending<Book> { it.isBaseBook }.thenBy { it.order })
                .take(MAX_FALLBACK_BOOKS)
        }.orEmpty()
        return books.map { BookSuggestionDto(it, buildCategoryPathTitlesCached(it.categoryId) + it.title) }
    }

    // The TOC entries containing [q], best matches first
    private fun tocMatching(entries: List<TocSuggestionDto>, q: String): List<TocSuggestionDto> =
        entries.asSequence()
            .filter { it.toc.text.contains(q, ignoreCase = true) }
            .sortedWith(compareBy<TocSuggestionDto> { matchRank(it.toc.text, q) }.thenBy { it.toc.level }.thenBy { it.toc.text.length })
            .toList()

    fun onReferenceQueryChanged(query: String) {
        referenceQuery.value = query
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(selectedScopeBook = null, selectedScopeToc = null, isReferenceLoading = false)
        }
    }

    fun onTocQueryChanged(query: String) {
        tocQuery.value = query
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                selectedScopeToc = null,
                tocSuggestionsVisible = _uiState.value.tocSuggestions.isNotEmpty(),
                isTocLoading = false,
            )
        }
    }

    /** Lists the books of [author], to pick one of them next. */
    fun onPickAuthor(author: AuthorHit) {
        _uiState.value = _uiState.value.copy(
            selectedScopeAuthor = author,
            authorSuggestions = emptyList(),
            jumpSuggestions = emptyList(),
            bookSuggestions = emptyList(),
            isReferenceLoading = true,
            suggestionsVisible = true,
        )
        viewModelScope.launch {
            authorBooks = withContext(Dispatchers.IO) {
                catching { repository.searchBooksByAuthor(author.name) }.orEmpty()
                    // The query matches the name anywhere: keep this author's books only
                    .filter { book -> book.authors.any { it.id == author.id } }
                    .sortedWith(compareByDescending<Book> { it.isBaseBook }.thenBy { it.order })
                    .map { book -> BookSuggestionDto(book, buildCategoryPathTitlesCached(book.categoryId) + book.title) }
            }
            if (_uiState.value.selectedScopeAuthor?.id == author.id) {
                _uiState.value = _uiState.value.copy(bookSuggestions = authorBooks, isReferenceLoading = false)
            }
        }
    }

    fun onClearAuthor() {
        authorBooks = emptyList()
        _uiState.value = _uiState.value.copy(selectedScopeAuthor = null, bookSuggestions = emptyList(), suggestionsVisible = false)
    }

    fun onPickBook(
        book: Book,
        // False to only show the book picked (a results tab's scope), its TOC staying closed
        showSuggestions: Boolean = true,
    ) {
        authorBooks = emptyList()
        _uiState.value = _uiState.value.copy(
            selectedScopeAuthor = null,
            selectedScopeBook = book,
            selectedScopeToc = null,
            bookPickedByUser = showSuggestions,
            suggestionsVisible = false,
            tocSuggestionsVisible = false,
            tocSuggestions = emptyList(),
            isReferenceLoading = false,
            isTocLoading = true,
        )
        viewModelScope.launch {
            val tocEntries = tocCache[book.id] ?: withContext(Dispatchers.Default) {
                val entries = catching { repository.getBookToc(book.id) }.orEmpty()
                // The paths are walked in the entries already loaded, with the title read once: desktop asks the
                // database for the book and each ancestor of every entry, thousands of queries for a large book
                val byId = entries.associateBy { it.id }
                val bookTitle = catching { repository.getBookCore(book.id)?.title }
                val built = mutableListOf<TocSuggestionDto>()
                val sorted = entries.asSequence().filter { it.text.isNotBlank() }.sortedWith(compareBy<TocEntry> { it.level }.thenBy { it.text }).toList()
                for (toc in sorted) {
                    val path = buildTocPathTitlesCached(toc, byId, bookTitle).filter { it.isNotBlank() }
                    if (path.isNotEmpty()) built += TocSuggestionDto(toc, path)
                }
                built += altTocSuggestions(book)
                tocCache[book.id] = built
                built
            }
            // A text typed before the TOC was loaded filters it now
            val typed = tocQuery.value.trim()
            val initial = if (typed.length < minTocPrefixLen) tocEntries.take(maxTocPredictive) else tocMatching(tocEntries, typed)
            if (_uiState.value.selectedScopeBook?.id != book.id) return@launch
            _uiState.value = _uiState.value.copy(
                tocSuggestions = initial,
                tocSuggestionsVisible = showSuggestions && initial.isNotEmpty(),
                isTocLoading = false,
            )
        }
    }

    // Entries of the book's alternative TOCs (parashot, chapter names...), after the main TOC. They travel as TocEntry
    // with the negated alt entry id, opened at their own line (see isAltTocEntry).
    private suspend fun altTocSuggestions(book: Book): List<TocSuggestionDto> = catching {
        repository.getAltTocStructuresForBook(book.id).flatMap { structure ->
            val entries = repository.getAltTocEntriesForStructure(structure.id)
            val byId = entries.associateBy { it.id }
            val label = altTocLabel(structure.key, structure.heTitle, book.title)
            entries.filter { it.text.isNotBlank() }.map { entry ->
                val ancestors = generateSequence(entry.parentId?.let(byId::get)) { it.parentId?.let(byId::get) }
                val path = listOf(label) + ancestors.map { it.text }.toList().asReversed() + entry.text
                TocSuggestionDto(TocEntry(id = -entry.id, bookId = book.id, text = entry.text, level = entry.level, lineId = entry.lineId), path)
            }
        }
    }.orEmpty()

    private fun altTocLabel(key: String, heTitle: String?, bookTitle: String): String =
        heTitle?.takeIf { it.isNotBlank() && it != bookTitle } ?: ALT_TOC_LABELS[key] ?: key

    fun onPickToc(toc: TocEntry) {
        _uiState.value = _uiState.value.copy(selectedScopeToc = toc, tocSuggestionsVisible = false, isTocLoading = false)
    }

    fun onGlobalExtendedChange(extended: Boolean) {
        _uiState.value = _uiState.value.copy(globalExtended = extended)
    }

    /** Dismisses all suggestion popups. */
    fun dismissSuggestions() {
        _uiState.value = _uiState.value.copy(suggestionsVisible = false, tocSuggestionsVisible = false, isReferenceLoading = false, isTocLoading = false)
    }

    /**
     * Puts the bar where [scope] searched, as a results tab opens, so that what it shows is where it searches: a book
     * (a TOC entry's whole book), shown as its chip.
     */
    fun showScope(scope: SearchScope) {
        val bookId = when (scope) {
            is SearchScope.Book -> scope.bookId
            is SearchScope.Toc -> scope.bookId
            else -> return
        }
        viewModelScope.launch {
            catching { repository.getBookCore(bookId) }?.let { onPickBook(it, showSuggestions = false) }
        }
    }

    /** Where the bar searches: its picked TOC entry or book, else everywhere. */
    fun barScope(): SearchScope {
        val selected = _uiState.value
        val toc = selected.selectedScopeToc
        return when {
            // An alternative-TOC entry has no main-TOC scope: search its whole book
            toc != null && !toc.isAltTocEntry() -> SearchScope.Toc(bookId = toc.bookId, tocId = toc.id)
            toc != null -> SearchScope.Book(toc.bookId)
            selected.selectedScopeBook != null -> SearchScope.Book(selected.selectedScopeBook.id)
            else -> SearchScope.Global
        }
    }

    /** A zayit:// link pasted into the bar opens its target instead of a search (desktop submitSearch); null otherwise. */
    suspend fun deepLinkTarget(query: String): Destination? {
        val destination = parseZayitDeepLink(query.trim()) ?: return null
        return when (destination) {
            is Destination.Book -> catching { repository.getBookCore(destination.bookId) }?.let { destination.copy(title = it.title) }
            else -> destination
        }
    }

    /**
     * The selected TOC entry or book and the line to open it at (desktop openSelectedReferenceInCurrentTab): a TOC entry
     * opens at its first line, a book at its beginning.
     */
    suspend fun resolvePickedReference(): Pair<Book, Long?>? {
        val selectedToc = _uiState.value.selectedScopeToc
        val selectedBook = _uiState.value.selectedScopeBook
        val book = selectedBook ?: selectedToc?.let { catching { repository.getBookCore(it.bookId) } } ?: return null
        val anchorLineId: Long? = when {
            selectedToc == null -> null
            selectedToc.isAltTocEntry() -> selectedToc.lineId
            else -> catching { repository.getLineIdsForTocEntry(selectedToc.id).firstOrNull() }
        }
        return book to anchorLineId
    }

    /** The bar as a new one starts: nothing typed, no book, author or chapter picked, nothing suggested. */
    fun reset() {
        authorBooks = emptyList()
        referenceQuery.value = ""
        tocQuery.value = ""
        _uiState.value = SearchBarUiState()
    }

    /** Hides the list once a row opened something (desktop openBook / openJump / openAuthor). */
    fun onOpened() {
        _uiState.value = _uiState.value.copy(suggestionsVisible = false)
    }

    private suspend fun buildCategoryPathTitles(catId: Long): List<String> {
        val path = mutableListOf<String>()
        var currentId: Long? = catId
        var guard = 0
        while (currentId != null && guard++ < 64) {
            currentCoroutineContext().ensureActive()
            val c = repository.getCategory(currentId) ?: break
            path += c.title
            currentId = c.parentId
        }
        return path.asReversed()
    }

    // [byId]: the book's entries when loaded (an ancestor missing there is read from the database), [knownBookTitle]
    // the book's title when read already
    private suspend fun buildTocPathTitles(entry: TocEntry, byId: Map<Long, TocEntry> = emptyMap(), knownBookTitle: String? = null): List<String> {
        val bookTitle = knownBookTitle ?: catching { repository.getBookCore(entry.bookId)?.title }
        val tocTitles = mutableListOf<String>()
        var current: TocEntry? = entry
        var guard = 0
        while (current != null && guard++ < 128) {
            currentCoroutineContext().ensureActive()
            tocTitles += current.text
            current = current.parentId?.let { pid -> byId[pid] ?: catching { repository.getTocEntry(pid) } }
        }
        val path = tocTitles.asReversed()
        return dedupAdjacent(if (bookTitle != null) listOf(bookTitle) + path else path)
    }

    private data class Suggestions(val books: List<BookSuggestionDto>, val jumps: List<ResolvedReference>, val authors: List<AuthorHit>)

    companion object {
        private const val MAX_AUTHOR_SUGGESTIONS = 4
        private const val MAX_FALLBACK_BOOKS = 20

        /** [initialScope]: a results tab's bar opens where its search looked (desktop showScope at its creation). */
        fun factory(repository: SeforimRepository, service: SearchService, initialScope: SearchScope? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SearchBarViewModel(repository, service).apply { initialScope?.let(::showScope) } as T
        }
    }
}

/** Alternative-TOC entries ride in TocSuggestionDto as TocEntry with a negated id. */
internal fun TocEntry.isAltTocEntry(): Boolean = id < 0

// Names of Sefaria's alternative structures, whose heTitle is usually the book's own title
private val ALT_TOC_LABELS = mapOf(
    "Parasha" to "פרשיות",
    "Chapters" to "פרקים",
    "Chapter" to "פרקים",
    "Topic" to "נושאים",
    "Venice" to "דפוס ונציה",
    "Vilna" to "דפוס וילנא",
    "30 Day Cycle" to "מחזור חודשי",
    "Book" to "ספרים",
    "Gate" to "שערים",
    "Letter" to "אותיות",
    "Daf" to "דפים",
    "Contents" to "תוכן",
)

// Typed text against a title, both without nikud or quotes (שו"ת / שו״ת)
private fun BookSuggestionDto.titleContains(typed: String): Boolean =
    typed.isBlank() || ReferenceParser.normalizeName(book.title).contains(ReferenceParser.normalizeName(typed))

/** Desktop runSuspendCatching: a failure (logged) gives null, a cancellation goes on. */
internal suspend fun <T> catching(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Log.w("Zayit", "search bar", t)
    null
}

/**
 * Collapses adjacent breadcrumb segments when the next segment strictly extends the previous by a common separator
 * (comma/space/colon/dash), keeping the most specific path. Desktop HomeView dedupAdjacent.
 */
internal fun dedupAdjacent(parts: List<String>): List<String> {
    if (parts.isEmpty()) return parts
    fun extends(prev: String, next: String): Boolean {
        val a = prev.trim()
        val b = next.trim()
        if (b.length <= a.length || !b.startsWith(a)) return false
        val ch = b[a.length]
        return ch == ',' || ch == ' ' || ch == ':' || ch == '-' || ch == '—'
    }
    val out = ArrayList<String>(parts.size)
    for (p in parts) {
        if (out.isEmpty()) {
            out += p
        } else {
            val last = out.last()
            when {
                p == last -> Unit
                extends(last, p) -> out[out.lastIndex] = p
                else -> out += p
            }
        }
    }
    return out
}

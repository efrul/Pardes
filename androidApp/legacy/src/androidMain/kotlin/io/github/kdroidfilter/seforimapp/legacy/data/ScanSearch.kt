package io.github.kdroidfilter.seforimapp.legacy.data

import android.text.TextUtils
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import io.github.kdroidfilter.seforimapp.android.ui.search.ScrollTarget
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchAvailability
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchTreeCategory
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchUiState
import io.github.kdroidfilter.seforimapp.legacy.ui.LineText
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The text search where LegacySearch has no full-text index (its levels Lookup and Scan): the lines of the books are
 * read one book after the other and tested in C (TextSearch), as this edition searched before it had Lucene. Its
 * results come as the main app's SearchViewModel gives its own (SearchUiState), so that the search screen shows both
 * alike: the books in the library's order, a book's first result with "עוד N תוצאות מ…" (all of them once chosen,
 * and back), the top categories as tabs once the scan has ended, the base books first and all the books when they
 * have nothing or on "חיפוש בכל הספרים".
 *
 * The whole library takes minutes on an old phone, the base books seconds. The scan holds the library's one
 * connection a book at a time: [pause] stops it while a book opened from the results is read, and [resume] goes on
 * from the book it was in. It lives as long as the screen's view models (across a rotation) and ends with [clear].
 */
class ScanSearch(private val library: Library, private val prefs: Prefs) {
    /**
     * Where the scan is, for the status line: [book] being read, the [at]th of [of]; once it has ended, whether it was
     * [stopped] ("בטל חיפוש") or found the first [MAX_HITS] (capped), and whether it looked for each word apart.
     */
    data class Progress(
        val running: Boolean = false,
        val book: String? = null,
        val at: Int = 0,
        val of: Int = 0,
        val found: Int = 0,
        val stopped: Boolean = false,
        val capped: Boolean = false,
        val allWords: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SearchUiState(availability = SearchAvailability.Ready))

    /** The results as the main app's SearchViewModel.state gives its own. */
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    // The search: its query, where it looks, how it compares
    private var query = ""
    private var scopeBooks: List<Book>? = null

    /** What the search was scoped to, as the status line names it (a book's title, "ספרי" and an author), or null. */
    var scopeTitle: String? = null
        private set
    private var allBooks = false
    private var allWords = false
    private var fellBack = false
    private var stopped = false
    private var failure: String? = null

    // What it found, in the library's order, and how many in each book
    private val hits = ArrayList<SearchResult>()
    private val counts = HashMap<Long, Int>()

    // What is shown of it: a top category's ([tab]), one book's ([narrowed]); the lists last published, kept while
    // nothing changes (the screen draws its list again only for new ones)
    private var tab: Long? = null
    private var narrowed: Long? = null
    private var published: Pair<List<SearchResult>, Map<Long, Int>> = emptyList<SearchResult>() to emptyMap()
    private var changed = true

    // Where the list stood before one book's results were shown, to come back there; and where it stands now
    private var beforeBook: ScrollTarget? = null
    private var anchorId = -1L
    private var scrollIndex = 0
    private var scrollOffset = 0

    /** The library's books in the order the search reads them, the base books apart, and each one's top category. */
    private class SearchOrder(val base: List<Entry>, val others: List<Entry>, val rootOf: Map<Long, Long>, val roots: List<Category>)

    private var order: SearchOrder? = null

    // Where the search is: the books it reads (null until known) from [nextBook] on, then [fallback] if the base books
    // have nothing
    private var queue: List<Entry>? = null
    private var nextBook = 0
    private var fallback: List<Entry>? = null

    /** The search has not ended: it is reading, or [paused]. */
    private var running = false

    /** Stopped while another screen reads the library, to go on with [resume]. */
    private var paused = false
    private var scan: Scan? = null
    private var engine: TextSearch? = null
    private var searchJob: Job? = null

    /** The results the scan found that [take] has not moved into [hits] yet; its lock also guards the [Scan]. */
    private val arrived = ArrayList<SearchResult>()

    /**
     * One run of the scan, as its thread tells it (under [arrived]'s lock): the book it is at, and whether that book's
     * query holds the library's one connection, the only time an interrupt stops the scan and not another query on it.
     */
    private class Scan(val from: Int) {
        /** The book it is at: the ones before it are read, and their results handed over. */
        @Volatile
        var done = from

        /** Set and cleared inside the driver's lock (TextSearch.run's inQuery): no other query can run meanwhile. */
        @Volatile
        var reading = false

        /** Stopped: it reads no other book, and drops the results of the one it is in (read again if it goes on). */
        var cancelled = false
    }

    /**
     * Searches [text]: in [books] when given (a book, an author's; named [title]), else in the base books (and all the
     * books if they have nothing), or all the books for [everywhere]; [words] finds each word anywhere in the line
     * rather than the phrase.
     */
    fun search(text: String, books: List<Book>?, title: String?, everywhere: Boolean, words: Boolean = false) {
        val q = text.trim()
        if (q.isEmpty()) return
        stopScan()
        query = q
        scopeBooks = books
        scopeTitle = title
        allBooks = everywhere
        allWords = words
        fellBack = false
        stopped = false
        failure = null
        tab = null
        narrowed = null
        beforeBook = null
        synchronized(arrived) { arrived.clear() }
        hits.clear()
        counts.clear()
        changed = true
        // A scope's books are all read, base books or not, in the library's order
        queue = books?.sortedBy { it.order }?.map { Entry(it.id, it.title, isBook = true, base = it.isBaseBook) }
        nextBook = 0
        fallback = null
        running = true
        paused = false
        publish(ScrollTarget(stamp = System.nanoTime()))
        launchSearch()
    }

    /** The status line's link: the base books or all of them, searched again. */
    fun setGlobalExtended(extended: Boolean) = search(query, scopeBooks, scopeTitle, extended, allWords)

    /** "חיפוש כל מילה בנפרד": the same search, each word anywhere in the line. */
    fun searchWords() = search(query, scopeBooks, scopeTitle, allBooks, words = true)

    /** One top category's results only (a tab), or all of them for null. */
    fun showOnlyCategory(categoryId: Long?) {
        tab = categoryId
        narrowed = null
        beforeBook = null
        changed = true
        publish(ScrollTarget(stamp = System.nanoTime()))
    }

    /** All of one book's results ("עוד N תוצאות מ…"), keeping the place in the list to come back to. */
    fun showMoreFromBook(bookId: Long) {
        beforeBook = ScrollTarget(0L, anchorId, scrollIndex, scrollOffset)
        narrowed = bookId
        changed = true
        publish(ScrollTarget(stamp = System.nanoTime()))
    }

    /** Back to all the results, where the list was. */
    fun backFromBook() {
        val back = beforeBook ?: ScrollTarget()
        beforeBook = null
        narrowed = null
        changed = true
        publish(back.copy(stamp = System.nanoTime()))
    }

    /** Where the list stands, to come back there from one book's results. */
    fun onScroll(anchor: Long, index: Int, offset: Int) {
        anchorId = anchor
        scrollIndex = index
        scrollOffset = offset
    }

    /** "בטל חיפוש": the search ends where it is, with what it found. */
    fun cancel() {
        if (!running) return
        stopScan()
        running = false
        paused = false
        stopped = true
        take()
        publish()
    }

    /**
     * Stops the search while another screen reads the library (a book opened from the results, the screen left), to go
     * on with [resume]: the interrupt that stops a book cannot then meet the reader's queries.
     */
    fun pause() {
        if (!running || paused) return
        paused = true
        stopScan()
    }

    /** Back from the screen that read the library: the search goes on from the book it was in. */
    fun resume() {
        if (!paused) return
        paused = false
        launchSearch()
    }

    /** The screen is gone for good: the scan stops. */
    fun clear() {
        stopScan()
        scope.cancel()
    }

    /**
     * Runs the search from where it is to its end. What it found and where it is are published a few times a second:
     * the scan never waits for the screen, and the list is not drawn again for each book.
     */
    private fun launchSearch() {
        searchJob = scope.launch {
            val ticker = launch {
                while (true) {
                    take()
                    publish()
                    delay(REFRESH_MS)
                }
            }
            val ready = withContext(Dispatchers.IO) {
                runCatching {
                    val opened = library.open()
                    TextSearch(opened) to (order ?: searchOrder(opened))
                }
            }
            val (search, books) = ready.getOrElse { e ->
                if (e is CancellationException) throw e
                ticker.cancel()
                failure = e.message ?: e.javaClass.name
                running = false
                publish()
                return@launch
            }
            engine = search
            order = books
            if (queue == null) {
                queue = if (allBooks) books.base + books.others else books.base
                fallback = if (allBooks) null else books.others
            }
            read(search)
            ticker.cancel()
            running = false
            take()
            publish()
        }
    }

    /** The library's books in the search's order, each one's top category, and the top categories: read once. */
    private fun searchOrder(opened: Library.Opened): SearchOrder {
        val all = opened.booksUnder(null)
        val rootOf = HashMap<Long, Long>(all.size * 2)
        for (book in all) rootOf[book.id] = book.root
        val roots = opened.rootEntries().mapIndexed { i, root -> Category(id = root.id, title = root.title, order = i) }
        return SearchOrder(all.filter { it.base }, all.filterNot { it.base }, rootOf, roots)
    }

    /** Reads [queue] from [nextBook] on, then [fallback] if the base books had nothing. */
    private suspend fun read(search: TextSearch) {
        while (true) {
            take()
            val books = queue ?: return
            val limit = MAX_HITS - hits.size
            if (nextBook < books.size && limit > 0) {
                val s = Scan(nextBook)
                scan = s
                val q = query
                val words = allWords
                val pattern = LineText.highlighter(q, phrase = !words)
                val marks = prefs.diacritics
                withContext(Dispatchers.IO) {
                    runCatching {
                        search.run(
                            q, books.subList(s.from, books.size), limit, words,
                            onBook = { i, _ ->
                                synchronized(arrived) {
                                    if (s.cancelled) throw CancellationException("stopped")
                                    s.done = s.from + i
                                }
                            },
                            inQuery = { reading ->
                                synchronized(arrived) {
                                    // Stopped while the book waited for the connection: not read at all
                                    if (reading && s.cancelled) throw CancellationException("stopped")
                                    s.reading = reading
                                }
                            },
                            onHits = { found ->
                                // Made into results here, off the main thread and out of the connection (the screen's
                                // queries run meanwhile): the line's text with the words found bold
                                val results = found.map { toResult(it, pattern, marks) }
                                synchronized(arrived) {
                                    if (!s.cancelled) {
                                        arrived += results
                                        s.done++
                                    }
                                }
                            },
                        )
                    }
                }
                scan = null
            }
            nextBook = books.size
            take()
            val others = fallback
            if (others == null || hits.isNotEmpty()) return
            // Nothing in the base books: all the others (the main app's fallback).
            fellBack = true
            fallback = null
            queue = others
            nextBook = 0
        }
    }

    /** Moves what the scan found since the last time into the results. */
    private fun take() {
        val found = synchronized(arrived) {
            if (arrived.isEmpty()) return
            ArrayList(arrived).also { arrived.clear() }
        }
        for (r in found) counts[r.bookId] = (counts[r.bookId] ?: 0) + 1
        hits += found
        changed = true
    }

    /**
     * Stops the scan where it is, keeping what it found. The book it is reading is stopped (sqlite3_interrupt) only
     * while its query holds the connection, checked under the lock that query's end takes: at any other time the
     * interrupt would fail another query on the one connection, a result's place or the reader's lines.
     */
    private fun stopScan() {
        val s = scan
        scan = null
        searchJob?.cancel()
        if (s != null) {
            synchronized(arrived) {
                s.cancelled = true
                nextBook = s.done
                if (s.reading) engine?.cancel()
            }
        }
    }

    /** Gives the screen the results as they are now, and where the scan is; [scrollTo] moves its list. */
    private fun publish(scrollTo: ScrollTarget? = null) {
        if (changed) {
            changed = false
            val rootOf = order?.rootOf.orEmpty()
            val book = narrowed
            val root = tab
            val shown = when {
                book != null -> hits.filter { it.bookId == book }
                root != null -> hits.filter { rootOf[it.bookId] == root }
                else -> ArrayList(hits)
            }
            published = shown to HashMap(counts)
        }
        // The tabs once the scan has ended, as the main app's come with its facets: counts still growing would move them
        val tree = if (running) emptyList() else tabs()
        val books = queue.orEmpty()
        val at = (scan?.done ?: nextBook).coerceIn(0, maxOf(0, books.size - 1))
        _state.value = _state.value.copy(
            query = query,
            executedQuery = query,
            availability = SearchAvailability.Ready,
            failure = failure,
            globalExtended = allBooks || fellBack,
            baseBooksHadNoResults = fellBack,
            isLoading = running,
            results = published.first,
            scopeBook = scopeBooks?.singleOrNull(),
            hasMore = false,
            isLoadingMore = false,
            progressCurrent = hits.size,
            progressTotal = hits.size.toLong(),
            bookCounts = published.second,
            tree = tree,
            selectedCategoryIds = setOfNotNull(tab),
            bookFilterIds = setOfNotNull(narrowed),
            scrollTarget = scrollTo ?: _state.value.scrollTarget,
        )
        _progress.value = Progress(
            running = running,
            book = books.getOrNull(at)?.title,
            at = at,
            of = books.size,
            found = hits.size,
            stopped = stopped,
            capped = hits.size >= MAX_HITS,
            allWords = allWords,
        )
    }

    /** The top categories the results are in, with how many each, in the library's order. */
    private fun tabs(): List<SearchTreeCategory> {
        val o = order ?: return emptyList()
        if (hits.isEmpty()) return emptyList()
        val byRoot = HashMap<Long, Int>()
        for (r in hits) o.rootOf[r.bookId]?.let { byRoot[it] = (byRoot[it] ?: 0) + 1 }
        return o.roots.filter { it.id in byRoot }.map { SearchTreeCategory(it, byRoot.getValue(it.id), emptyList()) }
    }

    companion object {
        /** The scan stops after this many results. */
        const val MAX_HITS = 500

        /** How often a running search publishes where it is and what it found. */
        private const val REFRESH_MS = 250L

        /**
         * [hit] as a result of the main app's search: its line's text as the reader shows it (points and cantillation
         * as Settings say), in HTML with the words found bold, as Lucene's snippets come.
         */
        private fun toResult(hit: Hit, pattern: Regex?, marks: DiacriticsMode): SearchResult {
            val text = LineText.render(hit.content, marks).toString().replace('\n', ' ')
            val html = StringBuilder(text.length + 32)
            var at = 0
            pattern?.findAll(text)?.forEach { m ->
                html.append(TextUtils.htmlEncode(text.substring(at, m.range.first)))
                html.append("<b>").append(TextUtils.htmlEncode(m.value)).append("</b>")
                at = m.range.last + 1
            }
            html.append(TextUtils.htmlEncode(text.substring(at)))
            return SearchResult(hit.bookId, hit.bookTitle, hit.lineId, hit.lineIndex, html.toString(), 0.0)
        }
    }
}

package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.semantic.MeaningFacets
import io.github.kdroidfilter.seforimapp.features.search.semantic.MeaningLine
import io.github.kdroidfilter.seforimapp.features.search.semantic.WordsEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [engine], with its sessions listing the lines found in the library's order while [byCatalog] says so: the Tanakh's
 * before the Mishnah's, a book's lines as they stand in it, as a concordance reads ([CatalogOrderSession]). Everything
 * else is [engine]'s own, and so are its sessions when [byCatalog] says no; its facets are counted once for the last
 * searches ([RecentFacets]), which such a list reads again.
 */
class CatalogOrderEngine(
    private val engine: SearchEngine,
    // Every book's place in the library's order
    private val bookOrder: suspend () -> Map<Long, Int>,
    private val byCatalog: () -> Boolean,
) : SearchEngine by engine {
    private val recent = RecentFacets()

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchFacets? =
        recent.get(FacetsKey(query, near, bookFilter, categoryFilter, bookIds?.toSet(), lineIds?.toSet(), baseBookOnly)) {
            engine.computeFacets(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
        }

    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchSession? {
        // The engine's own session says whether the query is one, and lists the lines it finds by meaning
        val own = engine.openSession(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
        if (own == null || !byCatalog()) return own
        val words = (engine as? WordsEngine)?.words
        val byMeaning = own.takeIf { words != null && it is MeaningFacets }
        if (byMeaning == null) own.close()
        return CatalogOrderSession(
            words = words ?: engine,
            snippets = engine,
            fused = byMeaning,
            query = query,
            near = near,
            where = CatalogOrderSession.Where(bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly),
            bookOrder = bookOrder,
            facets = recent,
        )
    }
}

/** How many lines found by meaning alone [session] leaves out of its list: a [CatalogOrderSession]'s, none for another. */
internal suspend fun fartherLinesOf(session: SearchSession): Int = (session as? CatalogOrderSession)?.fartherLines() ?: 0

/**
 * A search's lines in the library's order: the books that have lines found ([words]' facets), in that order, read a
 * few at a time, as many as fill the page asked for, each book's lines as they stand in it. Nothing is ranked: a book
 * with thousands of lines found is read whole when its turn comes.
 *
 * Of the lines the engine finds by meaning alone ([fused], its own session's list) only those that stand out of its
 * candidates by [standout] or more ([MeaningLine.standing]) stand among them, each in its place: where nothing is ranked
 * every line found has the same standing, and a line whose tie to the search is weak came first because its book does.
 * How many stay is the search's own, as close as its lines are. The others are counted ([fartherLines]), and are listed
 * by relevance alone. All of them are counted for the category tabs, as that order counts them ([meaningBookCounts]).
 */
internal class CatalogOrderSession(
    private val words: SearchEngine,
    private val snippets: SearchEngine,
    private val fused: SearchSession?,
    private val query: String,
    private val near: Int,
    private val where: Where,
    private val bookOrder: suspend () -> Map<Long, Int>,
    // How far above the search's other candidates a line of the meaning's own stands, to stand in the list
    private val standout: Double = STANDOUT,
    // The facets of its engine's last searches, the view model's among them
    private val facets: RecentFacets = RecentFacets(),
) : SearchSession,
    MeaningFacets {
    /** Where the search runs, as [SearchEngine.openSession] takes it. */
    class Where(
        val bookFilter: Long? = null,
        val categoryFilter: Long? = null,
        val bookIds: Collection<Long>? = null,
        val lineIds: Collection<Long>? = null,
        val baseBookOnly: Boolean = false,
    ) {
        internal fun facetsKey(
            query: String,
            near: Int,
        ) = FacetsKey(query, near, bookFilter, categoryFilter, bookIds?.toSet(), lineIds?.toSet(), baseBookOnly)

        internal fun facetsOf(
            engine: SearchEngine,
            query: String,
            near: Int,
        ): SearchFacets? = engine.computeFacets(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
    }

    private val mutex = Mutex()

    // The books with lines found, in the library's order, each with how many the words find in it; null until opened
    private var books: List<Pair<Long, Int>>? = null
    private var order: Map<Long, Int> = emptyMap()
    private var total = 0L
    private var nextBook = 0

    // The meaning's own lines that stand in the list, in the engine's order, with how far each stands out; all of them
    // counted by book, and how many the list leaves out
    private var listed: List<LineHit> = emptyList()
    private var standing: List<MeaningLine> = emptyList()
    private var meaningCounts: Map<Long, Int> = emptyMap()
    private var farther = 0

    // The lines read and not yet given, in order, and those of them the meaning alone found
    private val ready = ArrayDeque<LineHit>()
    private val meant = HashSet<Long>()

    override suspend fun nextPage(
        limit: Int,
        snippets: Boolean,
    ): SearchPage? =
        mutex.withLock {
            val found = books ?: open()
            while (ready.size < limit && nextBook < found.size) readBooks(found, limit - ready.size)
            if (ready.isEmpty()) return@withLock null
            val hits = List(minOf(limit, ready.size)) { ready.removeFirst() }
            SearchPage(
                hits = if (snippets) withSnippets(hits) else hits,
                totalHits = total,
                isLastPage = ready.isEmpty() && nextBook >= found.size,
            )
        }

    private suspend fun open(): List<Pair<Long, Int>> {
        order = bookOrder()
        val key = where.facetsKey(query, near)
        val counts =
            withContext(Dispatchers.IO) {
                // Those of a search that holds this one (the view model's same search, or the whole one for a tab), else counted
                facets.bookCounts(key) ?: facets.get(key) { where.facetsOf(words, query, near) }?.bookCounts
            }.orEmpty()
        if (fused != null) {
            val meaning = fused as? MeaningFacets
            // The engine's list made, none of its lines read but its first: of the meaning's own lines, in the engine's
            // order, those that stand out alone are read (the 300 lines of the list were most of its opening)
            fused.nextPage(1, snippets = false)
            val own = meaning?.meaningLines().orEmpty()
            val read = meaning?.meaningHits(own.filter { it.standing >= standout }.map { it.lineId }).orEmpty().associateBy { it.lineId }
            standing = own.filter { it.lineId in read }
            listed = standing.map { read.getValue(it.lineId) }
            meaningCounts = own.groupingBy { it.bookId }.eachCount()
            farther = own.size - listed.size
        }
        total = counts.values.sumOf { it.toLong() } + listed.size
        return (counts.keys + listed.map { it.bookId })
            .sortedWith(compareBy({ order[it] ?: Int.MAX_VALUE }, { it }))
            .map { it to (counts[it] ?: 0) }
            .also { books = it }
    }

    // Reads the next books, as many as hold [wanted] lines between them, and lines them up
    private suspend fun readBooks(
        found: List<Pair<Long, Int>>,
        wanted: Int,
    ) {
        val group = ArrayList<Long>()
        var count = 0
        while (nextBook < found.size && (group.isEmpty() || count < wanted) && group.size < MOST_BOOKS) {
            val (book, lines) = found[nextBook++]
            group += book
            count += lines
        }
        val lines = ArrayList<LineHit>(count)
        if (count > 0) {
            words.openSession(query, near, bookIds = group, lineIds = where.lineIds, baseBookOnly = where.baseBookOnly)?.use { session ->
                while (true) {
                    val page = session.nextPage(READ_LINES, snippets = false) ?: break
                    lines += page.hits
                    if (page.isLastPage || page.hits.isEmpty()) break
                }
            }
        }
        // The meaning's own lines of these books, each once
        val seen = lines.mapTo(HashSet()) { it.lineId }
        for (line in listed) {
            if (line.bookId in group && seen.add(line.lineId)) {
                lines += line
                meant += line.lineId
            }
        }
        ready += lines.sortedWith(compareBy({ order[it.bookId] ?: Int.MAX_VALUE }, { it.bookId }, { it.lineIndex }))
    }

    // The words' lines with the words in bold; a line the meaning alone found has none of them: its text as it is,
    // until its passage closest in meaning is ready (refinedSnippets)
    private suspend fun withSnippets(hits: List<LineHit>): List<LineHit> {
        val worded = snippets.attachSnippets(hits.filter { it.lineId !in meant }, query, near).associateBy { it.lineId }
        return hits.map { hit ->
            worded[hit.lineId] ?: hit.copy(snippet = snippets.rangeSnippet(hit.rawText, emptyList(), hit.lineStart, hit.lineEnd))
        }
    }

    override suspend fun meaningBookCounts(): Map<Long, Int> = mutex.withLock { meaningCounts }

    override suspend fun meaningLines(): List<MeaningLine> = mutex.withLock { standing }

    /** How many lines the meaning alone found are not in this list, less close than those that are; 0 before its first page. */
    suspend fun fartherLines(): Int = mutex.withLock { farther }

    override suspend fun refinedSnippets(hits: List<LineHit>): Map<Long, String> {
        val byMeaning = mutex.withLock { hits.filter { it.lineId in meant } }
        if (byMeaning.isEmpty()) return emptyMap()
        return fused?.refinedSnippets(byMeaning).orEmpty()
    }

    override fun close() {
        fused?.close()
    }

    companion object {
        /**
         * How far above its search's other candidates a line found by meaning alone stands, in standard deviations of
         * their closeness to the query, to be listed in the library's order. Judged blind on eval-real (docs/catalog-order),
         * the lines from 2 up were related to the search in 61% (from 3 up in 71%), those below in 34%; a search keeps
         * 5 to 13 of them, 8 at the median.
         */
        const val STANDOUT = 2.0

        // A book's lines are read this many at a time
        private const val READ_LINES = 2_000

        // Books with a line or two each: no more of them in one reading
        private const val MOST_BOOKS = 200
    }
}

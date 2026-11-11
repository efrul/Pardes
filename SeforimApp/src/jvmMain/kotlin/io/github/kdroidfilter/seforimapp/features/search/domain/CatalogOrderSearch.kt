package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.semantic.MeaningFacets
import io.github.kdroidfilter.seforimapp.features.search.semantic.WordsEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [engine], with its sessions listing the lines found in the library's order while [byCatalog] says so: the Tanakh's
 * before the Mishnah's, a book's lines as they stand in it, as a concordance reads ([CatalogOrderSession]). Everything
 * else is [engine]'s own, and so are its sessions when [byCatalog] says no.
 */
class CatalogOrderEngine(
    private val engine: SearchEngine,
    // Every book's place in the library's order
    private val bookOrder: suspend () -> Map<Long, Int>,
    private val byCatalog: () -> Boolean,
) : SearchEngine by engine {
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
        )
    }
}

/**
 * A search's lines in the library's order: the books that have lines found ([words]' facets), in that order, read a
 * few at a time, as many as fill the page asked for, each book's lines as they stand in it. The lines the engine finds
 * by meaning alone ([fused], its own session's list) stand among them in their place, and are counted
 * ([meaningBookCounts]). Nothing is ranked: a book with thousands of lines found is read whole when its turn comes.
 */
internal class CatalogOrderSession(
    private val words: SearchEngine,
    private val snippets: SearchEngine,
    private val fused: SearchSession?,
    private val query: String,
    private val near: Int,
    private val where: Where,
    private val bookOrder: suspend () -> Map<Long, Int>,
) : SearchSession,
    MeaningFacets {
    /** Where the search runs, as [SearchEngine.openSession] takes it. */
    class Where(
        val bookFilter: Long? = null,
        val categoryFilter: Long? = null,
        val bookIds: Collection<Long>? = null,
        val lineIds: Collection<Long>? = null,
        val baseBookOnly: Boolean = false,
    )

    private val mutex = Mutex()

    // The books with lines found, in the library's order, each with how many the words find in it; null until opened
    private var books: List<Pair<Long, Int>>? = null
    private var order: Map<Long, Int> = emptyMap()
    private var total = 0L
    private var nextBook = 0

    // The fused list's lines by book, and how many of each book's the words don't find
    private var listed: Map<Long, List<LineHit>> = emptyMap()
    private var meaningCounts: Map<Long, Int> = emptyMap()

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
        val counts =
            withContext(Dispatchers.IO) {
                words.computeFacets(query, near, where.bookFilter, where.categoryFilter, where.bookIds, where.lineIds, where.baseBookOnly)
            }?.bookCounts.orEmpty()
        if (fused != null) {
            listed =
                fused
                    .nextPage(MEANING_LINES, snippets = false)
                    ?.hits
                    .orEmpty()
                    .groupBy { it.bookId }
            meaningCounts = (fused as? MeaningFacets)?.meaningBookCounts().orEmpty()
        }
        total = counts.values.sumOf { it.toLong() } + meaningCounts.values.sum()
        return (counts.keys + meaningCounts.keys)
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
        // The fused list's lines of these books that the words did not give: the meaning's own
        val seen = lines.mapTo(HashSet()) { it.lineId }
        for (book in group) {
            if (book !in meaningCounts) continue
            listed[book]?.forEach { line ->
                if (seen.add(line.lineId)) {
                    lines += line
                    meant += line.lineId
                }
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

    override suspend fun refinedSnippets(hits: List<LineHit>): Map<Long, String> {
        val byMeaning = mutex.withLock { hits.filter { it.lineId in meant } }
        if (byMeaning.isEmpty()) return emptyMap()
        return fused?.refinedSnippets(byMeaning).orEmpty()
    }

    override fun close() {
        fused?.close()
    }

    private companion object {
        // The engine's own list holds this many lines at most (its words' first and its meaning's)
        const val MEANING_LINES = 300

        // A book's lines are read this many at a time
        const val READ_LINES = 2_000

        // Books with a line or two each: no more of them in one reading
        const val MOST_BOOKS = 200
    }
}

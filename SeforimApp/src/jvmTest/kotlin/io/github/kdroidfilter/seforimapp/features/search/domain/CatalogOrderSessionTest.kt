package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.semantic.MeaningFacets
import io.github.kdroidfilter.seforimapp.features.search.semantic.MeaningLine
import io.github.kdroidfilter.seforimapp.features.search.semantic.WordsEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A search's lines in the library's order ([CatalogOrderSession]), and the engine that lists them so when asked. */
class CatalogOrderSessionTest {
    private fun hit(
        book: Long,
        index: Int,
        text: String = "",
    ) = LineHit(
        bookId = book,
        bookTitle = "book $book",
        lineId = book * 1000 + index,
        lineIndex = index,
        snippet = "",
        score = 1f,
        rawText = text,
    )

    // An engine over [hits], listed as given (its order of relevance); it remembers the books each session was opened in
    private class Lines(
        private val hits: List<LineHit>,
    ) : SearchEngine {
        val opened = ArrayList<List<Long>>()
        var counted = 0

        private fun found(
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
        ) = hits.filter { (bookIds == null || it.bookId in bookIds) && (lineIds == null || it.lineId in lineIds) }

        override fun openSession(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchSession? {
            if (query.isBlank()) return null
            opened += bookIds?.toList().orEmpty()
            val found = found(bookIds, lineIds)
            return object : SearchSession {
                private var at = 0

                override suspend fun nextPage(
                    limit: Int,
                    snippets: Boolean,
                ): SearchPage? {
                    if (at >= found.size) return null
                    val page = found.subList(at, minOf(found.size, at + limit))
                    at += page.size
                    return SearchPage(page, found.size.toLong(), at >= found.size)
                }

                override fun close() = Unit
            }
        }

        override fun computeFacets(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchFacets {
            counted++
            val found = found(bookIds, lineIds)
            return SearchFacets(found.size.toLong(), emptyMap(), found.groupingBy { it.bookId }.eachCount())
        }

        override suspend fun attachSnippets(
            hits: List<LineHit>,
            query: String,
            near: Int,
        ): List<LineHit> = hits.map { it.copy(snippet = "<b>$query</b>") }

        override fun searchBooksByTitlePrefix(
            query: String,
            limit: Int,
        ): List<Long> = emptyList()

        override fun buildSnippet(
            rawText: String,
            query: String,
            near: Int,
        ): String = rawText

        override fun close() = Unit
    }

    // An engine's own list: [lines], of which the words don't find [meant], each standing out of its candidates by
    // [standings] (by default enough to be listed in the library's order)
    private class Fused(
        private val lines: List<LineHit>,
        private val meant: List<LineHit>,
        private val standings: List<Double> = meant.map { CatalogOrderSession.STANDOUT + 1 },
    ) : SearchSession,
        MeaningFacets {
        var closed = false

        override suspend fun nextPage(
            limit: Int,
            snippets: Boolean,
        ) = SearchPage(lines.take(limit), lines.size.toLong(), true)

        override suspend fun meaningBookCounts(): Map<Long, Int> = meant.groupingBy { it.bookId }.eachCount()

        override suspend fun meaningLines(): List<MeaningLine> =
            meant.mapIndexed { i, line ->
                MeaningLine(line.lineId, standings[i], line.bookId)
            }

        override suspend fun meaningHits(lineIds: Collection<Long>): List<LineHit> = meant.filter { it.lineId in lineIds }

        override suspend fun refinedSnippets(hits: List<LineHit>): Map<Long, String> =
            hits.associate {
                it.lineId to
                    "the passage of ${it.lineId}"
            }

        override fun close() {
            closed = true
        }
    }

    // An engine that adds [own] to what its [words] find
    private class WithMeaning(
        override val words: Lines,
        private val own: Fused,
    ) : SearchEngine by words,
        WordsEngine {
        override fun openSession(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchSession = own
    }

    // The library has book 30 first, then 10, then 20
    private val order = mapOf(30L to 0, 10L to 1, 20L to 2)
    private val found = listOf(hit(20, 4), hit(10, 9), hit(30, 2), hit(10, 1), hit(20, 0), hit(30, 7), hit(10, 5))

    // The words find [found]; the meaning adds a line of book 10 and one of book 40, where the words find none, and
    // lists a line the words find too
    private val own = listOf(hit(10, 3, "אחד שנים"), hit(40, 1, "שלשה ארבעה"))
    private val fused = Fused(listOf(own[0], hit(30, 7), own[1]), own)

    private fun session(
        words: Lines,
        fused: Fused? = null,
        where: CatalogOrderSession.Where = CatalogOrderSession.Where(),
    ) = CatalogOrderSession(words, words, fused, "מילה", 5, where, bookOrder = { order })

    private suspend fun SearchSession.all(limit: Int): List<LineHit> {
        val lines = ArrayList<LineHit>()
        while (true) {
            val page = nextPage(limit, snippets = false) ?: break
            lines += page.hits
            if (page.isLastPage) break
        }
        return lines
    }

    private fun List<LineHit>.places() = map { it.bookId to it.lineIndex }

    @Test
    fun `the lines come book by book in the library's order, each book's as they stand in it`() =
        runTest {
            val lines = session(Lines(found)).all(limit = 3)

            assertEquals(listOf(30L to 2, 30L to 7, 10L to 1, 10L to 5, 10L to 9, 20L to 0, 20L to 4), lines.places())
        }

    @Test
    fun `the books are read as the pages ask for them, and the last page says so`() =
        runTest {
            val words = Lines(found)
            val session = session(words)

            val first = session.nextPage(2, snippets = false)
            assertEquals(listOf(listOf(30L)), words.opened)
            assertEquals(7L, first?.totalHits)
            assertEquals(false, first?.isLastPage)
            session.nextPage(2, snippets = false)
            assertEquals(listOf(listOf(30L), listOf(10L)), words.opened)
            // The third line of book 10 was read with it; then book 20's two
            assertEquals(listOf(10L to 9), session.nextPage(1, snippets = false)?.hits?.places())
            val last = session.nextPage(5, snippets = false)
            assertEquals(listOf(20L to 0, 20L to 4), last?.hits?.places())
            assertEquals(true, last?.isLastPage)
            assertNull(session.nextPage(5, snippets = false))
        }

    @Test
    fun `books with a line or two are read together`() =
        runTest {
            val words = Lines(found)

            session(words).nextPage(6, snippets = false)

            assertEquals(listOf(listOf(30L, 10L, 20L)), words.opened)
        }

    @Test
    fun `a line the meaning alone found stands in its place and is counted, one the words found too comes once`() =
        runTest {
            val session = session(Lines(found), fused)

            val first = session.nextPage(4, snippets = false)
            val lines = first?.hits.orEmpty() + session.all(limit = 4)

            assertEquals(
                listOf(30L to 2, 30L to 7, 10L to 1, 10L to 3, 10L to 5, 10L to 9, 20L to 0, 20L to 4, 40L to 1),
                lines.places(),
            )
            assertEquals(9L, first?.totalHits)
            assertEquals(mapOf(10L to 1, 40L to 1), session.meaningBookCounts())
            assertEquals(0, session.fartherLines())
        }

    @Test
    fun `of the lines the meaning alone found only those that stand out stand in the list, and the others are counted`() =
        runTest {
            // Four of the meaning's own: those of books 40 and 20 stand out of the search's candidates, 30's and 10's not
            val meant = listOf(hit(40, 1), hit(20, 2), hit(30, 5), hit(10, 3))
            val session = session(Lines(found), Fused(meant + hit(30, 7), meant, standings = listOf(3.2, 2.0, 1.9, 0.4)))

            val first = session.nextPage(20, snippets = false)

            assertEquals(
                listOf(30L to 2, 30L to 7, 10L to 1, 10L to 5, 10L to 9, 20L to 0, 20L to 2, 20L to 4, 40L to 1),
                first?.hits?.places(),
            )
            assertEquals(9L, first?.totalHits)
            assertEquals(2, session.fartherLines())
            assertEquals(listOf(MeaningLine(40_001L, 3.2, 40L), MeaningLine(20_002L, 2.0, 20L)), session.meaningLines())
            // The category tabs count them all, as the order of relevance does
            assertEquals(mapOf(40L to 1, 20L to 1, 30L to 1, 10L to 1), session.meaningBookCounts())
        }

    @Test
    fun `how many lines found by meaning stay is the search's own`() =
        runTest {
            val meant = listOf(hit(40, 1), hit(20, 2), hit(30, 5), hit(10, 3))

            // None stands out: the words' lines alone, the meaning's all counted
            val none = session(Lines(found), Fused(meant, meant, standings = listOf(1.5, 1.2, 0.8, 0.1)))
            assertEquals(found.size, none.all(limit = 20).size)
            assertEquals(4, none.fartherLines())
            // All stand out: all of them in their places
            val all = session(Lines(found), Fused(meant, meant, standings = listOf(4.0, 3.5, 3.1, 2.6)))
            assertEquals(found.size + 4, all.all(limit = 20).size)
            assertEquals(0, all.fartherLines())
        }

    @Test
    fun `the words' lines show the words, the meaning's own line its text and then its passage`() =
        runTest {
            val session = session(Lines(found), fused)

            val page = session.nextPage(4, snippets = true)?.hits.orEmpty()

            assertEquals(listOf("<b>מילה</b>", "<b>מילה</b>", "<b>מילה</b>", "אחד שנים"), page.map { it.snippet })
            assertEquals(mapOf(10_003L to "the passage of 10003"), session.refinedSnippets(page))
        }

    @Test
    fun `a search in some lines reads them alone`() =
        runTest {
            val section = listOf(10_001L, 10_005L, 20_004L)

            val lines = session(Lines(found), where = CatalogOrderSession.Where(lineIds = section)).all(limit = 10)

            assertEquals(listOf(10L to 1, 10L to 5, 20L to 4), lines.places())
        }

    @Test
    fun `the session closes the engine's own`() {
        session(Lines(found), fused).close()

        assertTrue(fused.closed)
    }

    @Test
    fun `the engine's own session unless the library's order is asked for`() =
        runTest {
            val words = Lines(found)
            var byCatalog = false
            val engine = CatalogOrderEngine(words, bookOrder = { order }) { byCatalog }

            assertEquals(found.places(), engine.openSession("מילה")?.all(limit = 10)?.places())
            byCatalog = true
            val listed = engine.openSession("מילה")
            assertTrue(listed is CatalogOrderSession)
            assertEquals(listOf(30L to 2, 30L to 7, 10L to 1, 10L to 5, 10L to 9, 20L to 0, 20L to 4), listed.all(limit = 10).places())
            assertNull(engine.openSession(" "))
            // Everything else is the engine's
            assertEquals(listOf(found.size.toLong()), listOf(engine.computeFacets("מילה")?.totalHits))
        }

    @Test
    fun `the list counts no facets the view model counted, nor a tab's the whole search's`() =
        runTest {
            val words = Lines(found)
            val engine = CatalogOrderEngine(words, bookOrder = { order }) { true }

            // The view model counts the search's facets, then opens its list, then a tab of it
            engine.computeFacets("מילה")
            engine.openSession("מילה")?.all(limit = 10)
            val tab = engine.openSession("מילה", bookIds = listOf(10L, 30L))?.all(limit = 10).orEmpty()

            assertEquals(listOf(30L to 2, 30L to 7, 10L to 1, 10L to 5, 10L to 9), tab.places())
            assertEquals(1, words.counted)
        }

    @Test
    fun `an engine that adds lines by meaning has its words read, its own list beside them`() =
        runTest {
            val engine = CatalogOrderEngine(WithMeaning(Lines(found), fused), bookOrder = { order }) { true }

            val lines = engine.openSession("מילה")?.all(limit = 20).orEmpty()

            assertEquals(
                listOf(30L to 2, 30L to 7, 10L to 1, 10L to 3, 10L to 5, 10L to 9, 20L to 0, 20L to 4, 40L to 1),
                lines.places(),
            )
        }
}

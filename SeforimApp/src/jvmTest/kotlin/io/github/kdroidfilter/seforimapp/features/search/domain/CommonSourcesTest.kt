package io.github.kdroidfilter.seforimapp.features.search.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rule of the line of common sources (docs/COMMON_SOURCES_PLAN.md), alone and against what was measured. */
class CommonSourcesTest {
    // Five results of five books: lines 1 to 5 of books 11 to 15
    private val page = (1L..5L).map { it to it + 10 }

    private fun pick(
        links: List<LinkEnd>,
        baseBooks: Set<Long> = emptySet(),
    ) = CommonSources.pick(page, links, baseBooks)

    @Test
    fun `a line linked from three books of the page is a source`() {
        val picks = pick(listOf(LinkEnd(1, 100, 9), LinkEnd(2, 100, 9), LinkEnd(4, 100, 9)))
        assertEquals(listOf(CommonSourcePick(100, 9, 3, listOf(1, 2, 4))), picks)
    }

    @Test
    fun `the lines that link to a source come in the page's order, a line once`() {
        val picks = pick(listOf(LinkEnd(5, 100, 9), LinkEnd(2, 100, 9), LinkEnd(4, 100, 9), LinkEnd(2, 100, 9)))
        assertEquals(listOf(2L, 4L, 5L), picks.single().from)
    }

    @Test
    fun `while a line is linked from three books, two are not enough, and a book counts once`() {
        val picks = pick(from(100, 9, 1, 2, 4) + listOf(LinkEnd(1, 200, 8), LinkEnd(4, 200, 8), LinkEnd(4, 200, 8)))
        assertEquals(listOf(100L), picks.map { it.lineId })
    }

    @Test
    fun `without one, a line linked from two results is a source, in a page of one book too`() {
        val oneBook = (1L..5L).map { it to 11L }
        val links = from(100, 9, 1, 2) + from(200, 8, 4)
        assertEquals(listOf(CommonSourcePick(100, 9, 1, listOf(1, 2))), CommonSources.pick(oneBook, links, emptySet()))
        assertEquals(listOf(CommonSourcePick(100, 9, 2, listOf(1, 2))), pick(links))
    }

    @Test
    fun `without a line linked from two results, any line a result links to, a base book's first, then the earliest result's`() {
        val oneBook = (1L..5L).map { it to 11L }
        val links = from(100, 9, 3) + from(200, 8, 1) + from(300, 7, 5)
        assertEquals(listOf(300L, 200L, 100L), CommonSources.pick(oneBook, links, setOf(7)).map { it.lineId })
    }

    @Test
    fun `without one, more books first, then a base book, then more results`() {
        // Results 1 to 3 of book 11, 4 and 5 of book 12
        val twoBooks = (1L..5L).map { it to if (it <= 3) 11L else 12L }
        val links = from(100, 9, 1, 2, 3) + from(200, 8, 1, 4) + from(300, 7, 2, 5) + from(400, 6, 1, 3)
        assertEquals(listOf(300L, 200L, 100L), CommonSources.pick(twoBooks, links, setOf(7)).map { it.lineId })
    }

    @Test
    fun `the first three results are never a source, the fourth may be`() {
        val links = (listOf(1L, 2L, 5L).map { LinkEnd(it, 4, 14) } + listOf(1L, 4L, 5L).map { LinkEnd(it, 3, 13) })
        assertEquals(listOf(CommonSourcePick(4, 14, 3, listOf(1, 2, 5))), pick(links))
    }

    @Test
    fun `a link of a line to itself does not count`() {
        assertTrue(pick(listOf(LinkEnd(4, 4, 14))).isEmpty())
        assertEquals(listOf(CommonSourcePick(4, 14, 1, listOf(1))), pick(listOf(LinkEnd(4, 4, 14), LinkEnd(1, 4, 14))))
    }

    @Test
    fun `a line of any book is a source, a base book's first among as many books`() {
        val links = from(100, 9, 1, 2, 3) + from(200, 8, 1, 2, 3)
        assertEquals(listOf(100L, 200L), pick(links).map { it.lineId })
        assertEquals(listOf(200L, 100L), pick(links, baseBooks = setOf(8)).map { it.lineId })
    }

    @Test
    fun `more books first, then a base book, then the place in the page, three at most`() {
        val links = from(100, 9, 1, 2, 3, 4, 5) + from(200, 8, 1, 2, 3) + from(300, 7, 1, 2, 3) + from(400, 6, 1, 2, 3, 4)
        assertEquals(listOf(100L, 400L, 300L), pick(links + from(500, 5, 3, 4, 5), baseBooks = setOf(7)).map { it.lineId })
    }

    @Test
    fun `a source linked from fewer than half the first's books is left out`() {
        val wide = (1L..8L).map { it to it + 10 }
        val links = from(100, 9, 1, 2, 3, 4, 5, 6, 7) + from(200, 8, 1, 2, 3) + from(300, 7, 1, 2, 3, 4)
        assertEquals(listOf(100L, 300L), CommonSources.pick(wide, links, emptySet()).map { it.lineId })
    }

    @Test
    fun `the line of eval-100's pages is the one measured where it has one, by the page's order`() {
        val (measured, more) = parity("expectedFirst20Rank") { _, _ -> null }
        assertEquals(47 to 45, measured to more)
    }

    @Test
    fun `with the fused list's order after the page, the line is common_source py's`() {
        parity("expectedWholeRank") { query, page ->
            val fused = query.getJSONObject("fusedRank")
            val order: (Long) -> Int = { line ->
                page.indexOfFirst { it.first == line }.takeIf { it >= 0 } ?: fused.optInt(line.toString(), Int.MAX_VALUE)
            }
            order
        }
    }

    // Every query of the fixture against [expected], with a base books' search's links to base books alone, as measured;
    // [rank] gives a query's ranking, null for the default. Where the measured line has none, only lines linked from
    // fewer books. The queries with the measured line, and with those others, counted
    private fun parity(
        expected: String,
        rank: (JSONObject, List<Pair<Long, Long>>) -> ((Long) -> Int)?,
    ): Pair<Int, Int> {
        val fixture =
            GZIPInputStream(javaClass.getResourceAsStream("/search/common-sources-e100.json.gz")!!).bufferedReader().use {
                JSONObject(it.readText())
            }
        val baseBooks = fixture.getJSONArray("baseBookIds").let { ids -> (0 until ids.length()).mapTo(HashSet()) { ids.getLong(it) } }
        val queries = fixture.getJSONArray("queries")
        var measured = 0
        var more = 0
        for (i in 0 until queries.length()) {
            val query = queries.getJSONObject(i)
            val page = query.getJSONArray("page").pairs()
            val links = query.getJSONObject("links")
            val baseOnly = query.getString("scope") == "base"
            val ends =
                links
                    .keys()
                    .asSequence()
                    .flatMap { line -> links.getJSONArray(line).pairs().map { (other, book) -> LinkEnd(line.toLong(), other, book) } }
                    .filter { !baseOnly || it.otherBook in baseBooks }
                    .toList()
            val order = rank(query, page)
            val picks = if (order == null) CommonSources.pick(page, ends, baseBooks) else CommonSources.pick(page, ends, baseBooks, order)
            val want = query.getJSONArray(expected).pairs().map { (line, books) -> line to books.toInt() }
            if (want.isEmpty()) {
                assertTrue(picks.all { it.books < CommonSources.MIN_BOOKS }, "query ${query.getInt("qid")}")
                if (picks.isNotEmpty()) more++
            } else {
                assertEquals(want, picks.map { it.lineId to it.books }, "query ${query.getInt("qid")}")
                measured++
            }
        }
        return measured to more
    }

    // The links of [results] (lines of the page) to [line], of [book]
    private fun from(
        line: Long,
        book: Long,
        vararg results: Long,
    ) = results.map { LinkEnd(it, line, book) }

    private fun JSONArray.pairs(): List<Pair<Long, Long>> =
        (0 until length()).map { getJSONArray(it).let { pair -> pair.getLong(0) to pair.getLong(1) } }
}

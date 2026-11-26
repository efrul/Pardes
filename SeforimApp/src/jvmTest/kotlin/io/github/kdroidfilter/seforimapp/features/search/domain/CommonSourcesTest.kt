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
        baseOnly: Boolean = false,
        baseBooks: Set<Long> = emptySet(),
    ) = CommonSources.pick(page, links, baseBooks, baseOnly)

    @Test
    fun `a line linked from three books of the page is a source`() {
        val picks = pick(listOf(LinkEnd(1, 100, 9), LinkEnd(2, 100, 9), LinkEnd(4, 100, 9)))
        assertEquals(listOf(CommonSourcePick(100, 9, 3)), picks)
    }

    @Test
    fun `two books are not enough, and a book counts once`() {
        val picks = pick(listOf(LinkEnd(1, 100, 9), LinkEnd(4, 100, 9), LinkEnd(4, 100, 9)))
        assertTrue(picks.isEmpty())
    }

    @Test
    fun `the first three results are never a source, the fourth may be`() {
        val links = (listOf(1L, 2L, 5L).map { LinkEnd(it, 4, 14) } + listOf(1L, 4L, 5L).map { LinkEnd(it, 3, 13) })
        assertEquals(listOf(CommonSourcePick(4, 14, 3)), pick(links))
    }

    @Test
    fun `a link of a line to itself does not count`() {
        val picks = pick(listOf(LinkEnd(4, 4, 14), LinkEnd(1, 4, 14), LinkEnd(2, 4, 14)))
        assertTrue(picks.isEmpty())
    }

    @Test
    fun `a base books' search takes only a line of a base book`() {
        val links = listOf(1L, 2L, 3L).map { LinkEnd(it, 100, 9) }
        assertTrue(pick(links, baseOnly = true).isEmpty())
        assertEquals(1, pick(links, baseOnly = true, baseBooks = setOf(9)).size)
        assertEquals(1, pick(links, baseOnly = false).size)
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
        assertEquals(listOf(100L, 300L), CommonSources.pick(wide, links, emptySet(), false).map { it.lineId })
    }

    @Test
    fun `the line of eval-100's pages is the one measured, by the page's order`() {
        val (lines, sources) = parity("expectedFirst20Rank") { _, _ -> null }
        assertEquals(47 to 86, lines to sources)
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

    // Every query of the fixture against [expected]; [rank] gives a query's ranking, null for the default. The lines and
    // sources the line shows, counted
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
        var lines = 0
        var sources = 0
        for (i in 0 until queries.length()) {
            val query = queries.getJSONObject(i)
            val page = query.getJSONArray("page").pairs()
            val links = query.getJSONObject("links")
            val ends =
                links
                    .keys()
                    .asSequence()
                    .flatMap { line -> links.getJSONArray(line).pairs().map { (other, book) -> LinkEnd(line.toLong(), other, book) } }
                    .toList()
            val baseOnly = query.getString("scope") == "base"
            val order = rank(query, page)
            val picks =
                if (order == null) {
                    CommonSources.pick(page, ends, baseBooks, baseOnly)
                } else {
                    CommonSources.pick(page, ends, baseBooks, baseOnly, order)
                }
            val want = query.getJSONArray(expected).pairs().map { (line, books) -> line to books.toInt() }
            assertEquals(want, picks.map { it.lineId to it.books }, "query ${query.getInt("qid")}")
            if (picks.isNotEmpty()) lines++
            sources += picks.size
        }
        return lines to sources
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

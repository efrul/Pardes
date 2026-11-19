package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookFindMatches
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.TextHighlights
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A book's find in its results pane (request 55): its lines, under their contents' entries, each with its piece. */
class FindResultsTest {
    // Lines 3, 7 and 9 of a book, matched once, twice and once
    private val matches = BookFindMatches(intArrayOf(3, 7, 9), longArrayOf(103, 107, 109), intArrayOf(0, 1, 3, 4))

    @Test
    fun `the lines found are listed once each, in reading order, with their counts`() {
        assertEquals(
            listOf(FoundLine(3, 103, 1), FoundLine(7, 107, 2), FoundLine(9, 109, 1)),
            matches.foundLines(),
        )
        assertEquals(emptyList(), BookFindMatches.Empty.foundLines())
    }

    @Test
    fun `each run of lines comes under the entry of the contents it belongs to`() {
        val lines = matches.foundLines()
        val rows = findRows(lines, mapOf(103L to "דף ב.", 107L to "דף ב.", 109L to "דף ג."))
        assertEquals(
            listOf(
                FindRow.Entry("דף ב."),
                FindRow.Found(lines[0]),
                FindRow.Found(lines[1]),
                FindRow.Entry("דף ג."),
                FindRow.Found(lines[2]),
            ),
            rows,
        )
        assertEquals(lines.map(FindRow::Found), findRows(lines, emptyMap()), "a book with no contents")
    }

    @Test
    fun `a line's piece is cut around its first match at words, nikud aside, and its start when nothing matches`() {
        val text = "אמר רבי יוחנן " + "מילה ".repeat(40) + "בָּרוּךְ שאמר והיה העולם " + "סוף ".repeat(40)
        val piece = findSnippet(text, "ברוך שאמר")
        assertEquals(true, piece.startsWith("…") && piece.endsWith("…"))
        assertEquals(true, "בָּרוּךְ שאמר" in piece)
        assertEquals(false, piece.contains("  "))
        assertEquals("קצר", findSnippet("קצר", "אחר"))
    }

    @Test
    fun `the hub keeps each tab's find and passes the lines asked for to the tab`() =
        runBlocking {
            val hub = BookFindHub()
            hub.publish("a", FindListing(1L, "ברוך", false, matches, null))
            assertEquals(
                3,
                hub.found.value["a"]
                    ?.matches
                    ?.foundLines()
                    ?.size,
            )
            val asked = async { hub.goTo.first() }
            yield()
            hub.goTo("a", 7)
            assertEquals("a" to 7, asked.await())
            hub.publish("a", null)
            assertNull(hub.found.value["a"])
        }

    @Test
    fun `a line found by meaning shows the passage the search highlights in it, bold`() =
        runBlocking {
            val engine = mockk<SearchEngine>()
            val text = "אמר רבי יוחנן כך"
            val passage = listOf(4..12)
            coEvery { engine.highlights(listOf(text), "שאלה") } returns listOf(TextHighlights(passage, byMeaning = true))
            every { engine.rangeSnippet(text, passage, any(), any()) } returns "אמר <b>רבי יוחנן</b> כך"
            assertEquals("אמר <b>רבי יוחנן</b> כך", meaningSnippet(engine, "<p>אמר   רבי יוחנן כך</p>", "שאלה"))
        }

    @Test
    fun `a line the search highlights nothing in shows its start`() =
        runBlocking {
            val engine = mockk<SearchEngine>()
            coEvery { engine.highlights(any(), any()) } returns listOf(TextHighlights(emptyList()))
            assertEquals("אמר רבי יוחנן", meaningSnippet(engine, "אמר רבי יוחנן", "שאלה"))
        }
}

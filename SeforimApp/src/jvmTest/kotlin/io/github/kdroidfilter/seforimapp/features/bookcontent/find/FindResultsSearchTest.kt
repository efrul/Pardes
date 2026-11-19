package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The results pane searched by its entries' titles, and each result's place with the level above it (request 62). */
class FindResultsSearchTest {
    private fun entry(
        id: Long,
        parent: Long?,
        text: String,
    ) = TocEntry(id = id, bookId = 5, parentId = parent, text = text, level = 0)

    @Test
    fun `an entry's place is its titles from the top, without the book's own title over them`() {
        val toc = listOf(entry(1, null, "רש\"י על בראשית"), entry(2, 1, "פרק כ"), entry(3, 2, "פסוק יז"))
        val paths = entryPaths(toc, "רש\"י על בראשית")
        assertEquals("פרק כ, פסוק יז", paths[3])
        assertEquals("פרק כ", paths[2])
        assertEquals("רש\"י על בראשית", paths[1], "a line of the book's own entry keeps it")
        assertEquals("בראשית, פרק כ", entryPaths(listOf(entry(1, null, "בראשית"), entry(2, 1, "פרק כ")), "אור החיים")[2])
    }

    @Test
    fun `an entry that is its own ancestor still has a place`() {
        val paths = entryPaths(listOf(entry(1, 2, "א"), entry(2, 1, "ב")), null)
        assertEquals(true, paths[1]?.endsWith("ב, א"))
    }

    // Lines 3, 7 and 9 under two chapters' verses, and a PDF book's two matches under a bookmark
    private val lines = listOf(FoundLine(3, 103, 1), FoundLine(7, 107, 2), FoundLine(9, 109, 1))
    private val rows =
        listOf(
            FindRow.Entry("פרק כ, פסוק יז"),
            FindRow.Found(lines[0]),
            FindRow.Found(lines[1]),
            FindRow.Entry("פרק כא, פסוק א"),
            FindRow.Found(lines[2]),
        )

    @Test
    fun `the search keeps the lines under the titles it matches, as the contents' search matches them`() {
        val result = filterFindRows(rows, "כא")
        assertEquals(listOf(rows[3], rows[4]), result.rows)
        assertEquals(listOf(9L), result.matchIds, "a line by its index")
        assertEquals(mapOf(0 to listOf(4..5)), result.highlights, "the title's words matched, by the entry's place")
        assertEquals(listOf(3L, 7L, 9L), filterFindRows(rows, "פסוק").matchIds)
        assertEquals(emptyList(), filterFindRows(rows, "סימן").rows)
        assertEquals(FindFilterResult(), filterFindRows(rows, " ,"), "no word typed")
    }

    @Test
    fun `Enter goes to the first line of the entry with the most whole words`() {
        // "כ" is a whole word of the first title and only the start of the second's כא
        assertEquals(3L, filterFindRows(rows, "כ").bestMatchId)
        assertEquals(9L, filterFindRows(rows, "א").bestMatchId)
    }

    @Test
    fun `a PDF book's matches are found by their bookmark, by their place in its find`() {
        val pdf =
            listOf(
                FindRow.Entry("עמוד 1"),
                FindRow.InPage(0, PdfFound(0, "א")),
                FindRow.Entry("פרק ב"),
                FindRow.InPage(1, PdfFound(4, "ב")),
            )
        assertEquals(listOf(1L), filterFindRows(pdf, "פרק").matchIds)
        assertNull(FindRow.Entry("פרק ב").matchId)
    }

    @Test
    fun `a piece cut after the line's own number keeps it first`() {
        val verse = "(יז) וַיִּתְפַּלֵּל אַבְרָהָם אֶל הָאֱלֹהִים " + "וַיִּרְפָּא ".repeat(20) + "אֶת אֲבִימֶלֶךְ וְאֶת אִשְׁתּוֹ"
        val piece = findSnippet(verse, "אבימלך ואת אשתו")
        assertEquals(true, piece.startsWith("(יז) …"), piece)
        assertEquals("(יז) וַיִּתְפַּלֵּל", findSnippet("(יז) וַיִּתְפַּלֵּל", "ויתפלל"), "not cut: as it is")
        assertEquals("…מילה", withOwnNumber("בלי מספר מילה", "…מילה"))
        assertEquals("(קכ\"ג) ...שם", withOwnNumber("(קכ\"ג) הלכה שם", "...שם"), "a geresh, and the search's dots")
    }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.features.bookcontent.find.foundLines
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookFindMatches
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The find on the daf (stage 3): the Gemara's matches with those of Rashi and Tosafot printed on it, as it is read. */
class DafFindCommentsTest {
    // Two amudim: the Gemara's lines 1 and 2 on the first and 3 on the second; Rashi's 11 and 12, Tosafot's 21 and 22
    private val printed =
        DafPrinted(
            comments = mapOf("T" to listOf(21L, 22L), "R" to listOf(11L, 12L)),
            places =
                mapOf(
                    1L to (0 to 10f),
                    2L to (0 to 40f),
                    3L to (1 to 5f),
                    11L to (0 to 50f),
                    12L to (1 to 20f),
                    21L to (0 to 5f),
                    22L to (1 to 30f),
                ),
        )
    private val comments =
        listOf(
            Line(11, 2, 0, "רש\"י: כלומר כך"),
            Line(12, 2, 1, "אין כאן"),
            Line(21, 3, 0, "תוס' כלומר <b>וכלומר</b>"),
            Line(22, 3, 1, "כלומר"),
        )

    // The Gemara's lines 1 and 3 matched once each
    private val gemara = BookFindMatches(intArrayOf(0, 2), longArrayOf(1, 3), intArrayOf(0, 1, 2))

    private fun merged(query: String) =
        runBlocking { withComments(gemara, printed, query, HashMap()) { ids -> comments.filter { it.id in ids } } }

    @Test
    fun `each amud's matches come as it is read, the Gemara's, then Rashi's, then Tosafot's`() {
        val found = merged("כלומר").foundLines()
        assertEquals(listOf(1L, 11L, 21L, 3L, 22L), found.map { it.lineId })
        assertEquals(listOf(0, 1, 2, 3, 4), found.map { it.lineIndex }, "their places in that order")
        assertEquals(2, found[2].count, "Tosafot's line matched twice")
        assertEquals(6, merged("כלומר").occurrences)
    }

    @Test
    fun `the Gemara's matches stay as they are for a query too short`() {
        assertEquals(gemara, merged("כ"))
    }

    @Test
    fun `the comments' text is read once`() {
        val asked = ArrayList<Long>()
        val texts = HashMap<Long, String>()
        runBlocking {
            repeat(2) {
                withComments(gemara, printed, "כלומר", texts) { ids ->
                    asked += ids
                    comments.filter { it.id in ids }
                }
            }
        }
        assertEquals(listOf(21L, 22L, 11L, 12L).sorted(), asked.sorted())
        assertTrue(texts.values.none { "<b>" in it }, "kept as the text shows it")
    }
}

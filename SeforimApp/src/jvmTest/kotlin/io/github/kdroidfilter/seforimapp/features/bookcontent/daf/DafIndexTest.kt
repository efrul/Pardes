package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DafIndex] over three amudim: Rashi's comments 5001 to 5004, the alignment having put 5003 a line early and its end on
 * the next amud; then a second book of his column, numbered lower (the Rashbam's, in Bava Batra); Tosafot's 9001 to 9003.
 */
class DafIndexTest {
    // A printed line of [column] whose words are of the library's lines [ids], [top] points down the page
    private fun line(
        column: String,
        top: Float,
        vararg ids: Long,
    ) = DafLine(
        c = column,
        b = listOf(100f, top, 300f, top + 8f),
        t = ids.joinToString(" ") { "מילה" },
        u = ids.mapIndexed { i, id -> listOf(id, i.toLong(), i.toLong()) },
    )

    private fun amud(
        name: String,
        vararg lines: DafLine,
    ) = DafAmud(name, page = listOf(600f, 850f), block = listOf(100f, 50f, 500f, 700f), lines = lines.toList())

    private val file =
        DafLayoutFile(
            format = 2,
            tractate = "Bava Batra",
            tractateHe = "בבא בתרא",
            amudim =
                listOf(
                    amud(
                        "2a",
                        line("G", 60f, 10),
                        line("G", 70f, 10, 11),
                        line("R", 60f, 5001, 5003),
                        line("R", 68f, 5002, 5003),
                        line("T", 60f, 9001, 9002),
                    ),
                    amud(
                        "2b",
                        line("G", 60f, 12),
                        line("G", 70f, 12),
                        line("R", 60f, 5003, 5004),
                        line("R", 68f, 3001),
                        line("R", 76f, 3002),
                    ),
                    amud("3a", line("G", 60f, 13), line("R", 60f, 3003), line("T", 60f, 9003)),
                ),
        )
    private val index = DafIndex(file, listOf("2a", "2b", "3a"))

    private fun read(
        from: Long,
        forward: Boolean = true,
    ) = generateSequence(from) { index.next(it, forward) }.toList()

    @Test
    fun `a column's comments are read in the library's order, each once, though a line lists one early`() {
        assertEquals(listOf(5001L, 5002L, 5003L, 5004L), read(5001).take(4))
    }

    @Test
    fun `a second book of the column is read where it is printed, after the first`() {
        assertEquals(listOf(5004L, 3001L, 3002L, 3003L), read(5004))
        assertEquals(listOf(3003L, 3002L, 3001L, 5004L, 5003L, 5002L, 5001L), read(3003, forward = false))
    }

    @Test
    fun `a column ends where its comments do, and the Gemara's lines are no comments`() {
        assertEquals(listOf(9001L, 9002L, 9003L), read(9001))
        assertNull(index.next(9003, forward = true))
        assertNull(index.next(5001, forward = false))
        assertNull(index.next(10, forward = true))
    }

    @Test
    fun `the Gemara's segment read at a place is the first that begins on the line there, or the one going on`() {
        assertEquals(10L, index.gemaraAt(0, 0f))
        assertEquals(11L, index.gemaraAt(0, 15f), "segment 10 goes on through the line, 11 begins on it")
        assertEquals(12L, index.gemaraAt(0, 25f), "past the amud's last line: the next amud's first")
        assertEquals(12L, index.gemaraAt(1, 15f), "no segment begins on the line: the one that goes on through it")
        assertEquals(13L, index.gemaraAt(2, 10f))
        assertNull(index.gemaraAt(2, 11f))
    }

    @Test
    fun `a view scrolled to a segment's place reads that segment again`() {
        for (id in listOf(10L, 11L, 12L, 13L)) {
            val (amud, top) = index.places.getValue(id)
            assertEquals(id, index.gemaraAt(amud, top))
        }
        assertEquals(10f, index.gemaraTop(0))
        assertNull(index.gemaraTop(5))
    }

    @Test
    fun `every line has the place it is printed at first`() {
        assertEquals(0 to 10f, index.places[10])
        assertEquals(0 to 20f, index.places[11])
        assertEquals(0 to 10f, index.places[5003])
        assertEquals(1 to 10f, index.places[5004])
        assertEquals(2 to 10f, index.places[9003])
    }
}

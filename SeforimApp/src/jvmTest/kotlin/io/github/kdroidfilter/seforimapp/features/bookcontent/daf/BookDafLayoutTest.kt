package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A book's daf as set: the text in the middle, its commentators around it, on pages, with words of a known width. */
class BookDafLayoutTest {
    // Every letter as wide, a square one wider than one of Rashi script, and a space
    private val widths =
        object : WordWidths {
            override fun of(
                word: String,
                square: Boolean,
                bold: Boolean,
            ) = word.length * if (square) 6f else 4f

            override fun space(square: Boolean) = if (square) 3f else 2f
        }

    private val width = BOOK_DAF_WIDTH
    private val textLeft = width * 0.25f
    private val textRight = width * 0.75f

    private fun words(
        count: Int,
        word: String = "מילה",
    ) = List(count) { word }

    private fun text(vararg counts: Int) = counts.mapIndexed { i, n -> DafSegment(lineId = 100L + i, words = words(n)) }

    private fun commentary(
        bookId: Long,
        onRight: Boolean,
        on: Long,
        count: Int,
    ) = DafCommentary(bookId, "מפרש", onRight, mapOf(on to listOf(DafSegment(bookId * 1000, words(count), bold = setOf(0, 1)))))

    private fun page(
        text: List<DafSegment>,
        vararg commentaries: DafCommentary,
    ): DafAmud = dafPages(text, commentaries.toList(), widths, "u").single()

    private fun DafAmud.of(column: String) = lines.filter { it.c == column }

    @Test
    fun `the text is in the middle and each side's commentary beside it`() {
        val amud = page(text(60), commentary(1, true, 100, 300), commentary(2, false, 100, 300))
        val besideText = amud.of(TEXT_COLUMN).maxOf { it.b[3] }
        assertTrue(amud.of(TEXT_COLUMN).all { it.b[0] >= textLeft && it.b[2] <= textRight })
        assertTrue(amud.of(RIGHT_COLUMN).filter { it.b[1] < besideText }.all { it.b[0] >= textRight })
        assertTrue(amud.of(LEFT_COLUMN).filter { it.b[1] < besideText }.all { it.b[2] <= textLeft })
    }

    @Test
    fun `a side that ends first leaves its place to the text`() {
        val amud = page(text(400), commentary(1, true, 100, 20), commentary(2, false, 100, 2000))
        val rightEnd = amud.of(RIGHT_COLUMN).maxOf { it.b[3] }
        val below = amud.of(TEXT_COLUMN).filter { it.b[1] > rightEnd + 10f }
        assertTrue(below.isNotEmpty())
        assertTrue(below.all { it.b[2] == width && it.b[0] >= textLeft }, "the text widens to the right, the left side still beside it")
    }

    @Test
    fun `the commentaries go under the text on their halves once it ends`() {
        val amud = page(text(20), commentary(1, true, 100, 600), commentary(2, false, 100, 600))
        val textEnd = amud.of(TEXT_COLUMN).maxOf { it.b[3] }
        val rightBelow = amud.of(RIGHT_COLUMN).filter { it.b[1] > textEnd + 10f }
        val leftBelow = amud.of(LEFT_COLUMN).filter { it.b[1] > textEnd + 10f }
        assertTrue(rightBelow.isNotEmpty() && leftBelow.isNotEmpty())
        assertTrue(rightBelow.dropLast(1).all { it.b[0] > width / 2 && it.b[0] < textRight })
        assertTrue(leftBelow.dropLast(1).all { it.b[2] < width / 2 && it.b[2] > textLeft })
    }

    @Test
    fun `a side alone takes the whole width under the text`() {
        val amud = page(text(20), commentary(1, true, 100, 800))
        val textEnd = amud.of(TEXT_COLUMN).maxOf { it.b[3] }
        assertEquals(0f, amud.of(TEXT_COLUMN).first().b[0], "the text takes the empty left side's place")
        val below = amud.of(RIGHT_COLUMN).filter { it.b[1] > textEnd + 10f }
        assertTrue(below.isNotEmpty())
        assertTrue(below.dropLast(1).all { it.b[0] == 0f && it.b[2] == width })
    }

    @Test
    fun `with no commentaries the text takes the whole width`() {
        val amud = page(text(80))
        assertTrue(amud.lines.all { it.c == TEXT_COLUMN })
        assertTrue(amud.lines.dropLast(1).all { it.b[0] == 0f && it.b[2] == width })
    }

    @Test
    fun `no two lines overlap`() {
        val commentaries = listOf(commentary(1, true, 100, 700), commentary(2, false, 101, 90), commentary(3, true, 102, 40))
        for (amud in dafPages(text(30, 10, 50), commentaries, widths, "u")) {
            val boxes = amud.lines.map { it.b }
            for (i in boxes.indices) {
                for (j in i + 1 until boxes.size) {
                    val (a, b) = boxes[i] to boxes[j]
                    val overlap = minOf(a[2], b[2]) > maxOf(a[0], b[0]) + 0.01f && minOf(a[3], b[3]) > maxOf(a[1], b[1]) + 0.01f
                    assertTrue(!overlap, "lines $i and $j overlap: $a $b")
                }
            }
        }
    }

    @Test
    fun `every word is set once, in order, tied to its line`() {
        val text = listOf(DafSegment(1, listOf("א", "ב", "ג")), DafSegment(2, listOf("ד", "ה")))
        val comment = DafSegment(9, listOf("ו", "ז", "ח"), bold = setOf(0))
        val amud = page(text, DafCommentary(5, "רש\"י", onRight = true, comments = mapOf(2L to listOf(comment))))
        val textWords = amud.of(TEXT_COLUMN).flatMap { it.t.split(' ') }
        assertEquals(listOf("א", "ב", "ג", "ד", "ה"), textWords)
        val ids =
            amud.of(TEXT_COLUMN).flatMap { line ->
                line.t
                    .split(' ')
                    .indices
                    .map { line.lineIdAt(it) }
            }
        assertEquals(listOf(1L, 1L, 1L, 2L, 2L), ids)
        val right = amud.of(RIGHT_COLUMN)
        assertEquals(listOf("ו", "ז", "ח"), right.flatMap { it.t.split(' ') })
        assertEquals(listOf(0), right.first().d, "the comment's dibbur in bold")
        assertEquals(listOf("רש\"י"), amud.of(NAME_COLUMN).map { it.t }, "its commentator's name above it")
    }

    @Test
    fun `a commentator's name is centred over its side's column`() {
        val amud = page(text(60), commentary(1, true, 100, 100))
        val name = amud.of(NAME_COLUMN).single().b
        val column = amud.of(RIGHT_COLUMN).first().b
        assertEquals((column[0] + column[2]) / 2, (name[0] + name[2]) / 2, 0.5f)
        assertTrue(name[3] <= column[1])
    }

    @Test
    fun `pages hold as many lines as fit and a heading is never last`() {
        val text =
            listOf(DafSegment(1, listOf("פרק", "א"), isHeading = true)) + (2L..40L).map { DafSegment(it, words(30)) } +
                DafSegment(41, listOf("פרק", "ב"), isHeading = true) + DafSegment(42, words(30))
        val pages = dafPages(text, emptyList(), widths, "u")
        assertTrue(pages.size > 1)
        assertTrue(pages.dropLast(1).all { it.block[3] <= BOOK_DAF_HEIGHT })
        assertEquals(text.map { it.lineId }, pages.flatMap { p -> p.lines.flatMap { it.lineIds } }.distinct())
        for (p in pages.dropLast(1)) {
            val last =
                p.lines
                    .last { it.c == TEXT_COLUMN }
                    .lineIds
                    .last()
            assertTrue(text.first { it.lineId == last }.isHeading.not(), "a page ends on a heading")
        }
        assertEquals(listOf("u/0", "u/1"), pages.take(2).map { it.amud })
    }

    @Test
    fun `verse by verse each verse has its targum beside it and its commentators under it in two columns`() {
        val text = text(12, 12)
        val targum = DafTargum("תרגום אונקלוס", mapOf(100L to listOf(DafSegment(500, words(10)))))
        val amud = dafPages(text, listOf(commentary(1, true, 100, 80)), widths, "u", DafStyle.Verses, targum).single()
        val firstVerse = amud.lines.filter { it.c == TEXT_COLUMN && 100L in it.lineIds }
        assertTrue(firstVerse.all { it.b[0] >= width * 0.37f && it.b[2] == width }, "the verse on the right")
        assertTrue(amud.of(TARGUM_COLUMN).all { it.b[2] < width * 0.38f }, "its targum on the left")
        assertEquals(
            listOf("תרגום", "אונקלוס"),
            amud
                .of(NAME_COLUMN)
                .first()
                .t
                .split(' '),
        )
        val verseEnd = maxOf(firstVerse.maxOf { it.b[3] }, amud.of(TARGUM_COLUMN).maxOf { it.b[3] })
        val right = amud.of(RIGHT_COLUMN)
        val left = amud.of(LEFT_COLUMN)
        assertTrue(right.all { it.b[1] > verseEnd && it.b[0] > width / 2 }, "the commentator under the verse, its first half on the right")
        assertTrue(left.all { it.b[2] < width / 2 }, "and the rest on the left")
        assertTrue(right.size - left.size in 0..1, "the two columns balanced")
        val secondVerse = amud.lines.filter { it.c == TEXT_COLUMN && 101L in it.lineIds }
        assertTrue(secondVerse.all { it.b[1] > right.maxOf { r -> r.b[3] } }, "the next verse after them")
        assertTrue(secondVerse.dropLast(1).all { it.b[0] == 0f }, "across, with no targum of its own")
    }
}

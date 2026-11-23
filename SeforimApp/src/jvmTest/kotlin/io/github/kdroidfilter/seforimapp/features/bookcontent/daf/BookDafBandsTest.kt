package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A book's daf set in bands, by the rules of docs/BOOK_DAF_RULES.md, with words of a known width: the checks its
 * "בדיקות לפני מסירה" asks of every setting.
 */
class BookDafBandsTest {
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

    private fun measures(
        style: BookDafStyle = BookDafStyle(),
        runOn: Boolean = false,
    ) = BandsMeasures(style, runOn)

    // Words that tell their place: the line's or comment's id and their own index
    private fun words(
        id: Long,
        count: Int,
    ) = List(count) { "$id.$it" }

    private fun text(vararg counts: Int) = counts.mapIndexed { i, n -> DafSegment(lineId = 100L + i, words = words(100L + i, n)) }

    // A commentator with one comment on each of the lines [on], of as many words
    private fun commentary(
        bookId: Long,
        vararg on: Pair<Long, Int>,
    ) = DafCommentary(
        bookId,
        "מפרש $bookId",
        onRight = true,
        comments =
            on.associate { (line, count) ->
                line to
                    listOf(DafSegment(bookId * 1000 + line, words(bookId * 1000 + line, count), setOf(0)))
            },
    )

    private fun pages(
        text: List<DafSegment>,
        commentaries: List<DafCommentary> = emptyList(),
        targum: DafTargum? = null,
        measures: BandsMeasures = measures(),
    ) = DafBandsSetter(widths, text, commentaries, targum, measures).pages()

    private fun SetPage.of(column: String) = lines.filter { it.c == column }

    private fun List<DafLine>.words() = flatMap { it.t.split(' ') }

    // The lines of a page under the commentator's name [name], up to the next name; none on a page without it
    private fun SetPage.band(name: String): List<DafLine> {
        val names = of(NAME_COLUMN)
        val head = names.firstOrNull { it.t == name } ?: return emptyList()
        val next = names.filter { it.b[1] > head.b[1] }.minOfOrNull { it.b[1] } ?: Float.MAX_VALUE
        return of(RIGHT_COLUMN).filter { it.b[1] > head.b[1] && it.b[1] < next }
    }

    @Test
    fun `every word is set once and in order, the text's and each commentator's`() {
        val text = text(40, 12, 80, 5, 30, 60)
        val rashi = commentary(1, 100L to 300, 101L to 40, 103L to 900, 105L to 20)
        val ramban = commentary(2, 100L to 4000, 104L to 700)
        val pages = pages(text, listOf(rashi, ramban))
        assertTrue(pages.size > 3)
        assertEquals(text.flatMap { it.words }, pages.flatMap { it.of(TEXT_COLUMN).words() })
        for (c in listOf(rashi, ramban)) {
            val all = text.flatMap { line -> c.comments[line.lineId].orEmpty().flatMap { it.words } }
            assertEquals(all, pages.flatMap { it.band(c.name).words() }, c.name)
        }
    }

    @Test
    fun `no comment is on a page before its line's`() {
        val text = text(40, 12, 80, 5, 30, 60)
        val commentaries = listOf(commentary(1, 100L to 300, 103L to 900), commentary(2, 100L to 4000, 104L to 700, 105L to 30))
        val pages = pages(text, commentaries)
        val pageOfLine = HashMap<Long, Int>()
        pages.forEachIndexed { i, page -> page.of(TEXT_COLUMN).flatMap { it.lineIds }.forEach { pageOfLine.putIfAbsent(it, i) } }
        pages.forEachIndexed { i, page ->
            for (comment in page.of(RIGHT_COLUMN).flatMap { it.lineIds }) {
                val line = comment % 1000
                assertTrue(pageOfLine.getValue(line) <= i, "the comment $comment on page $i, its line on page ${pageOfLine[line]}")
            }
        }
    }

    @Test
    fun `pages are one size, none overflows, and the last ends where its lines do`() {
        val measures = measures()
        val pages =
            pages(
                text(40, 12, 80, 5, 30, 60),
                listOf(commentary(1, 100L to 300, 103L to 900), commentary(2, 100L to 4000)),
                measures = measures,
            )
        assertTrue(pages.dropLast(1).all { it.height == measures.height })
        assertTrue(pages.all { page -> page.lines.all { it.b[3] <= page.height + 0.01f } })
        assertTrue(pages.last().height <= measures.height)
        assertTrue(pages.all { page -> page.lines.all { it.b[0] >= -0.01f && it.b[2] <= width + 0.01f } })
    }

    @Test
    fun `a line is free at every page's bottom`() {
        val measures = measures()
        val pages = pages(text(40, 12, 80), listOf(commentary(1, 100L to 5000)), measures = measures)
        for (page in pages.dropLast(1)) {
            assertTrue(page.lines.maxOf { it.b[3] } <= measures.height - measures.commentary.pitch + measures.commentary.box)
        }
    }

    @Test
    fun `no two lines overlap`() {
        val targum = DafTargum("תרגום", (100L..104L).associateWith { listOf(DafSegment(500 + it, words(500 + it, 25))) })
        val commentaries = listOf(commentary(1, 100L to 700, 102L to 90), commentary(2, 101L to 400), commentary(3, 100L to 40))
        for (page in pages(text(30, 10, 50, 20, 40), commentaries, targum, measures(runOn = true))) {
            val boxes = page.lines.map { it.b }
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
    fun `a page takes lines of the text while they and their comments fit`() {
        val pages = pages(text(10, 10, 10, 10), listOf(commentary(1, 100L to 30, 101L to 30, 102L to 30, 103L to 30)))
        assertEquals(1, pages.size, "four short lines and their comments are one page")
        val page = pages.single()
        assertTrue(page.of(TEXT_COLUMN).maxOf { it.b[3] } < page.of(NAME_COLUMN).single().b[1], "the text above its band")
    }

    @Test
    fun `every page has a new line of the text while a long commentary lags behind`() {
        val text = text(20, 20, 20, 20)
        val pages = pages(text, listOf(commentary(1, 100L to 6000)))
        val firstLines = pages.map { page -> page.of(TEXT_COLUMN).flatMap { it.lineIds }.toSet() }
        assertTrue(pages.size > 4)
        for (i in text.indices) assertEquals(setOf(100L + i), firstLines[i], "page $i")
        assertTrue(firstLines.drop(text.size).all { it.isEmpty() }, "then the commentary alone")
    }

    @Test
    fun `a chapter's commentators all at once are set to the last word, a page at a time`() {
        val text = text(20, 15, 30, 10)
        val commentaries = (1L..60L).map { commentary(it, 100L to 2_000, 102L to 500) }
        var asked = 0
        val pages = DafBandsSetter(widths, text, commentaries, null, measures()).pages { asked++ }
        assertEquals(pages.size, asked, "asked before each page, for a setting no longer wanted to be let go of")
        for (c in commentaries) {
            val all = text.flatMap { line -> c.comments[line.lineId].orEmpty().flatMap { it.words } }
            assertEquals(all, pages.flatMap { it.band(c.name).words() }, c.name)
        }
    }

    @Test
    fun `the first commentator takes its room first, the next what is left`() {
        val measures = measures()
        val pages = pages(text(20), listOf(commentary(1, 100L to 200), commentary(2, 100L to 3000)), measures = measures)
        val first = pages.first()
        val whole =
            commentary(1, 100L to 200)
                .comments
                .getValue(100L)
                .single()
                .words
        assertEquals(whole, first.band("מפרש 1").words(), "the short commentary whole on the first page")
        assertTrue(first.band("מפרש 2").isNotEmpty(), "the long one in what is left")
        assertTrue(first.band("מפרש 1").maxOf { it.b[3] } < first.band("מפרש 2").minOf { it.b[1] })
    }

    @Test
    fun `a band with room for fewer than two lines a column waits for the next page`() {
        val measures = measures()
        // The first commentator leaves less than a band's head and two lines
        val lines = ((measures.height - measures.text.pitch * 2 - measures.bandHead * 2) / measures.commentary.pitch).toInt()
        val perLine = (measures.columnWidth * 0.97f / 26f).toInt()
        val pages =
            pages(text(5), listOf(commentary(1, 100L to perLine * lines * 2 - perLine), commentary(2, 100L to 400)), measures = measures)
        assertTrue(pages[0].of(NAME_COLUMN).none { it.t == "מפרש 2" })
        assertTrue(pages[1].of(NAME_COLUMN).any { it.t == "מפרש 2" })
    }

    @Test
    fun `a band's columns are balanced, the odd line in the right one`() {
        val page = pages(text(10), listOf(commentary(1, 100L to 250))).single()
        val band = page.band("מפרש 1")
        val right = band.filter { it.b[2] > width / 2 + 1f }
        val left = band.filter { it.b[2] <= width / 2 + 1f }
        assertTrue(right.size - left.size in 0..1, "${right.size} and ${left.size}")
        assertEquals(band.words(), (right + left).words(), "the right column is read first")
        assertEquals(right.first().b[1], left.first().b[1], "both from one top")
    }

    @Test
    fun `three columns hold a band's lines in three`() {
        val page = pages(text(10), listOf(commentary(1, 100L to 240)), measures = measures(BookDafStyle(columns = 3))).single()
        val tops = page.band("מפרש 1").groupBy { it.b[1] }
        assertEquals(3, tops.values.first().size)
    }

    @Test
    fun `a band's last line is short only where a comment ends`() {
        val pages = pages(text(10, 10), listOf(commentary(1, 100L to 3000, 101L to 30)))
        val across = pages.first().band("מפרש 1")
        assertTrue(across.all { it.b[2] - it.b[0] > measures().columnWidth - 0.01f }, "a band cut in the middle of a comment is set across")
        val last = pages.last().band("מפרש 1").last()
        assertTrue(last.b[2] - last.b[0] < measures().columnWidth, "the comment's last line from the right")
    }

    @Test
    fun `the words that open a comment are named, for the text's square letters`() {
        val comment = DafSegment(900, words(900, 12), bold = setOf(0, 1, 5))
        val commentary = DafCommentary(1, "מפרש 1", onRight = true, comments = mapOf(100L to listOf(comment)))
        val page = pages(text(10), listOf(commentary)).single()
        val first = page.lines.indexOfFirst { it.c == RIGHT_COLUMN }
        assertEquals(mapOf(first to setOf(0, 1)), page.openings, "its first bold words, not those further on")
        assertEquals(listOf(0, 1, 5), page.lines[first].d.take(3), "all of them bold")
        val square = pages(text(10), listOf(commentary), measures = measures(BookDafStyle(squareCommentary = true))).single()
        assertTrue(square.openings.isEmpty(), "none where the commentaries are in square letters already")
    }

    @Test
    fun `where the text runs on, a petuchah closes its paragraph and the page's last line ends in the middle`() {
        val closing = DafSegment(100, words(100, 14) + PETUCHAH)
        val open = DafSegment(101, words(101, 14))
        val page = pages(listOf(closing, open), measures = measures(runOn = true)).single()
        val text = page.of(TEXT_COLUMN)
        val closed = text.last { 100L in it.lineIds }
        assertTrue(closed.b[2] == width && closed.b[0] > 0f, "the paragraph's last line from the right")
        val first = text.first { 101L in it.lineIds }
        assertTrue(first.b[1] > closed.b[1], "the next line of the text begins a paragraph")
        val last = text.last()
        assertEquals(width / 2, (last.b[0] + last.b[2]) / 2, 0.5f)
    }

    @Test
    fun `where it does not, every line of the text is a paragraph`() {
        val page = pages(text(4, 4)).single()
        val text = page.of(TEXT_COLUMN)
        assertEquals(2, text.size)
        assertTrue(text.all { it.b[2] == width && it.b[0] > 0f })
    }

    @Test
    fun `the targum is beside its lines, at the page's left`() {
        val targum =
            DafTargum(
                "תרגום",
                mapOf(
                    100L to listOf(DafSegment(500, words(500, 30))),
                    101L to listOf(DafSegment(501, words(501, 30))),
                ),
            )
        val measures = measures(runOn = true)
        val page = pages(text(30, 30), targum = targum, measures = measures).single()
        assertTrue(page.of(TARGUM_COLUMN).all { it.b[2] <= measures.sideWidth + 0.01f })
        assertTrue(page.of(TEXT_COLUMN).all { it.b[0] >= measures.sideWidth + measures.gap - 0.01f })
        assertEquals(words(500, 30) + words(501, 30), page.of(TARGUM_COLUMN).words())
        assertEquals(1, page.rules.size, "a rule between the text and its targum")
    }

    @Test
    fun `with no room for the targum the text takes the whole width`() {
        val targum = DafTargum("תרגום", mapOf(100L to listOf(DafSegment(500, words(500, 30)))))
        val page = pages(text(60), targum = targum, measures = measures(BookDafStyle(sideShare = 0f))).single()
        assertTrue(page.of(TARGUM_COLUMN).isEmpty())
        assertTrue(page.of(TEXT_COLUMN).dropLast(1).all { it.b[0] == 0f && it.b[2] == width })
    }

    @Test
    fun `a heading is in the middle and never a page's last line`() {
        val text =
            listOf(DafSegment(1, listOf("פרק", "א"), isHeading = true)) + (2L..30L).map { DafSegment(it, words(it, 40)) } +
                DafSegment(31, listOf("פרק", "ב"), isHeading = true) + DafSegment(32, words(32, 40))
        val pages = pages(text)
        assertTrue(pages.size > 1)
        val heading = pages.first().of(TEXT_COLUMN).first()
        assertEquals(width / 2, (heading.b[0] + heading.b[2]) / 2, 0.5f)
        for (page in pages.dropLast(1)) {
            val last =
                page
                    .of(TEXT_COLUMN)
                    .last()
                    .lineIds
                    .last()
            assertTrue(!text.first { it.lineId == last }.isHeading, "a page ends on a heading")
        }
    }

    @Test
    fun `a line of the text taller than a page makes its page longer, and loses no word`() {
        val measures = measures()
        val pages = pages(text(3000), listOf(commentary(1, 100L to 50)), measures = measures)
        assertTrue(pages.first().height > measures.height)
        assertEquals(3000, pages.flatMap { it.of(TEXT_COLUMN).words() }.size)
        assertEquals(50, pages.flatMap { it.band("מפרש 1").words() }.size)
    }

    @Test
    fun `larger letters make more pages`() {
        val text = text(40, 40, 40)
        val commentaries = listOf(commentary(1, 100L to 2000))
        val small = pages(text, commentaries, measures = measures(BookDafStyle(textSize = 16f, commentarySize = 10f)))
        val large = pages(text, commentaries, measures = measures(BookDafStyle(textSize = 28f, commentarySize = 15f)))
        assertTrue(large.size > small.size)
        assertEquals(small.flatMap { it.band("מפרש 1").words() }, large.flatMap { it.band("מפרש 1").words() })
    }
}

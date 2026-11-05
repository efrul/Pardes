package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A reader's review of the daf: a line's start moved a word on or back, as the reader said, and kept in its file. */
class DafReviewTest {
    private fun line(
        c: String,
        t: String,
        u: List<List<Long>> = emptyList(),
        d: List<Int> = emptyList(),
        big: Int = 0,
    ) = DafLine(c = c, b = listOf(0f, 0f, 1f, 1f), t = t, d = d, u = u, big = big)

    private val amud =
        DafAmud(
            amud = "2a",
            page = listOf(600f, 800f),
            block = listOf(0f, 0f, 600f, 800f),
            lines =
                listOf(
                    line("R", "אלא", big = 1),
                    line("R", "אמר רבי יוחנן", u = listOf(listOf(10, 0, 2))),
                    line("G", "מתני׳ מאימתי קורין"),
                    line("R", "מאי טעמא דהכא כתיב", u = listOf(listOf(10, 0, 0), listOf(11, 1, 3)), d = listOf(1)),
                    line("R", "ועוד דבר אחר"),
                ),
        )

    @Test
    fun `the line before and after are its column's, past another column's lines and big words`() {
        assertEquals(1, amud.lineBefore(3))
        assertEquals(null, amud.lineBefore(1))
        assertEquals(4, amud.lineAfter(3))
        assertEquals(null, amud.lineAfter(4))
        assertEquals(null, amud.lineBefore(0))
    }

    @Test
    fun `a line's first word goes to the line before, with its library line, the bold kept on its word`() {
        val moved = amud.withMarks(listOf(DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", cut = 1)))

        assertEquals("אמר רבי יוחנן מאי", moved.lines[1].t)
        assertEquals(listOf(listOf(10L, 0L, 3L)), moved.lines[1].u)
        assertEquals("טעמא דהכא כתיב", moved.lines[3].t)
        assertEquals(listOf(listOf(11L, 0L, 2L)), moved.lines[3].u)
        assertEquals(listOf(0), moved.lines[3].d)
    }

    @Test
    fun `the line before's last words come to the line, and a move that would empty a line is left out`() {
        val moved = amud.withMarks(listOf(DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", cut = -2)))
        assertEquals("אמר", moved.lines[1].t)
        assertEquals("רבי יוחנן מאי טעמא דהכא כתיב", moved.lines[3].t)
        assertEquals(listOf(listOf(10L, 0L, 2L), listOf(11L, 3L, 5L)), moved.lines[3].u)

        assertEquals(amud, amud.withMarks(listOf(DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", cut = -3))))
    }

    @Test
    fun `a mark of a line that now holds other words, or of another amud, moves nothing`() {
        assertEquals(amud, amud.withMarks(listOf(DafMark("2a", "R", 3, "words of another layout", cut = 1))))
        assertEquals(amud, amud.withMarks(listOf(DafMark("2b", "R", 3, "מאי טעמא דהכא כתיב", cut = 1))))
    }

    @Test
    fun `a review is kept in the layouts' feedback folder, a line's new mark in place of its old, an empty one none`() {
        val dir = Files.createTempDirectory("daf-review").toFile()
        val layout = DafLayoutFile(format = 2, tractate = "Bava Batra", tractateHe = "בבא בתרא", amudim = listOf(amud))
        val reviews = DafReviews(dir)
        reviews.mark(layout, DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", ok = false))
        reviews.mark(layout, DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", ok = true, cut = 1))
        reviews.mark(layout, DafMark("2a", "R", 1, "אמר רבי יוחנן", ok = true))

        val read = DafReviews(dir).of(layout)
        assertEquals(2, read.marks.size)
        assertEquals(true, read.marks.first { it.i == 3 }.ok)
        assertEquals(1, read.marks.first { it.i == 3 }.cut)
        assertTrue(dir.resolve("feedback/Bava Batra.json").isFile)

        reviews.mark(layout, DafMark("2a", "R", 1, "אמר רבי יוחנן"))
        assertEquals(listOf(3), DafReviews(dir).of(layout).marks.map { it.i })
        dir.deleteRecursively()
    }

    @Test
    fun `a mark is written to the file the review was read from, whatever tractate the file names`() {
        val dir = Files.createTempDirectory("daf-review").toFile()
        val layout = DafLayoutFile(format = 2, tractate = "Bava Batra", tractateHe = "בבא בתרא", amudim = listOf(amud))
        val feedback = dir.resolve("feedback").apply { mkdirs() }
        val mark = """{"amud": "2a", "c": "R", "i": 1, "t": "אמר רבי יוחנן", "ok": true}"""
        feedback.resolve("Bava Batra.json").writeText("""{"tractate": "BavaBatra", "tractateHe": "בבא בתרא", "marks": [$mark]}""")
        DafReviews(dir).mark(layout, DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", ok = false))

        assertEquals(listOf(1, 3), DafReviews(dir).of(layout).marks.map { it.i }.sorted())
        assertEquals(listOf("Bava Batra.json"), feedback.list()!!.toList())
        dir.deleteRecursively()
    }

    @Test
    fun `a review file that cannot be read is set aside, not written over`() {
        val dir = Files.createTempDirectory("daf-review").toFile()
        val layout = DafLayoutFile(format = 2, tractate = "Bava Batra", tractateHe = "בבא בתרא", amudim = listOf(amud))
        val feedback = dir.resolve("feedback").apply { mkdirs() }
        feedback.resolve("Bava Batra.json").writeText("{\"tractate\": \"Bava Batra\", \"marks\": [")
        val reviews = DafReviews(dir)

        assertTrue(reviews.of(layout).marks.isEmpty())
        val aside = feedback.list()!!.filter { it.startsWith("Bava Batra.unreadable-") }
        assertEquals(1, aside.size)
        assertEquals("{\"tractate\": \"Bava Batra\", \"marks\": [", feedback.resolve(aside[0]).readText())

        reviews.mark(layout, DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", ok = false))
        assertEquals(listOf(3), DafReviews(dir).of(layout).marks.map { it.i })
        assertEquals(2, feedback.list()!!.size)
        dir.deleteRecursively()
    }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `a review is of the selection's lines when the word is in it, with the words it holds, else of the word's line`() {
        assertEquals(listOf(ReviewedLine(3)), amud.reviewed(DafWord(3, 1), null))
        val range = DafRange(DafWord(1, 1), DafWord(4, 1))
        assertEquals(
            listOf(ReviewedLine(1, from = 1), ReviewedLine(3), ReviewedLine(4, to = 1)),
            amud.reviewed(DafWord(3, 0), range),
            "its column's lines, not the Gemara's between them",
        )
        assertEquals(listOf(ReviewedLine(4)), amud.reviewed(DafWord(4, 2), range), "a word outside the selection: its own line")
        val whole = DafRange(DafWord(1, 0), DafWord(3, 3))
        assertEquals(listOf(ReviewedLine(1), ReviewedLine(3)), amud.reviewed(DafWord(1, 0), whole), "whole lines name no words")
        assertEquals(emptyList(), amud.reviewed(DafWord(0, 0), null), "a big word is no line to review")
    }

    @Test
    fun `what is wrong with a passage is said of each of its lines in one write, and unsaid leaves no mark`() {
        val dir = Files.createTempDirectory("daf-review").toFile()
        val layout = DafLayoutFile(format = 2, tractate = "Bava Batra", tractateHe = "בבא בתרא", amudim = listOf(amud))
        val reviews = DafReviews(dir)
        val lines = listOf(ReviewedLine(1, from = 1), ReviewedLine(3))

        val said = reviews.say(layout, amud, lines, DafReason.LAYOUT, said = true)
        assertEquals(listOf(1, 3), said.marks.map { it.i })
        val first = said.marks.first()
        assertTrue(first.layout && !first.words && first.ok == false)
        assertEquals(1 to null, first.from to first.to)
        assertEquals("אמר רבי יוחנן", first.t)

        assertTrue(reviews.say(layout, amud, lines, DafReason.WORDS, said = true).marks.all { it.layout && it.words })
        val words = DafReviews(dir).say(layout, amud, lines, DafReason.LAYOUT, said = false)
        assertTrue(words.marks.all { !it.layout && it.words && it.ok == false }, "read from its file, the other reason kept")
        assertTrue(DafReviews(dir).say(layout, amud, lines, DafReason.WORDS, said = false).marks.isEmpty())
        assertTrue(DafReviews(dir).of(layout).marks.isEmpty())
        // A mark says which version of the tractate's layout it was made on
        assertNull(first.version)
        val fourth = DafReviews(dir).say(layout.copy(version = 4), amud, lines, DafReason.WORDS, said = true)
        assertEquals(listOf(4, 4), fourth.marks.map { it.version })
        dir.deleteRecursively()
    }

    @Test
    fun `a mark of before the two reasons is kept under them, and told apart`() {
        val moved = DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב", cut = 1)
        assertTrue(moved.isFormer)
        val both = moved.saying(DafReason.WORDS, said = true, from = null, to = 2)
        assertTrue(both.isFormer && both.words && both.ok == false)
        assertEquals(1 to 2, both.cut to both.to)
        val unsaid = both.saying(DafReason.WORDS, said = false, from = null, to = 2)
        assertTrue(unsaid.cut == 1 && unsaid.ok == null && unsaid.to == null && !unsaid.isEmpty, "its move is still made")

        assertTrue(!DafMark("2a", "R", 3, "מאי", ok = true).isFormer, "right is one of the menu's answers")
        assertTrue(DafMark("2a", "R", 3, "מאי", ok = false).isFormer, "wrong, with no reason given")
        assertTrue(!DafMark("2a", "R", 3, "מאי").saying(DafReason.LAYOUT, said = true, from = null, to = null).isFormer)
    }

    @Test
    fun `a passage said to be right has neither fault, and a fault said of it takes right away`() {
        val right = DafMark("2a", "R", 3, "מאי טעמא דהכא כתיב").saying(DafReason.RIGHT, said = true, from = 1, to = null)
        assertTrue(right.ok == true && !right.layout && !right.words)
        assertEquals(1 to null, right.from to right.to)
        assertEquals(true, right.saying(DafReason.WORDS, said = false, from = 1, to = null).ok, "a fault it never had, unsaid")

        val wrong = right.saying(DafReason.LAYOUT, said = true, from = 1, to = null)
        assertTrue(wrong.ok == false && wrong.layout)
        val both = wrong.saying(DafReason.WORDS, said = true, from = 1, to = null)
        val rightAgain = both.saying(DafReason.RIGHT, said = true, from = 1, to = null)
        assertTrue(rightAgain.ok == true && !rightAgain.layout && !rightAgain.words, "right takes both faults away")
        assertTrue(rightAgain.saying(DafReason.RIGHT, said = false, from = 1, to = null).isEmpty, "unsaid, it leaves no mark")

        assertTrue(DafReason.RIGHT.excludes(DafReason.WORDS) && DafReason.LAYOUT.excludes(DafReason.RIGHT))
        assertTrue(!DafReason.LAYOUT.excludes(DafReason.WORDS), "the two faults stand together")
        assertTrue(wrong.says(DafReason.LAYOUT) && !wrong.says(DafReason.RIGHT) && right.says(DafReason.RIGHT))

        // A line marked wrong before the two reasons, said to be right
        assertEquals(true, DafMark("2a", "R", 3, "מאי", ok = false).saying(DafReason.RIGHT, said = true, from = null, to = null).ok)
    }

    @Test
    fun `right is said of each line of a passage in one write, as a fault is`() {
        val dir = Files.createTempDirectory("daf-review").toFile()
        val layout = DafLayoutFile(format = 2, tractate = "Bava Batra", tractateHe = "בבא בתרא", amudim = listOf(amud), version = 3)
        val lines = listOf(ReviewedLine(1, from = 1), ReviewedLine(3))
        DafReviews(dir).say(layout, amud, lines, DafReason.WORDS, said = true)

        val right = DafReviews(dir).say(layout, amud, lines, DafReason.RIGHT, said = true)
        assertEquals(listOf(1, 3), right.marks.map { it.i })
        assertTrue(right.marks.all { it.ok == true && !it.words && it.version == 3 })
        assertEquals(1, right.marks.first().from)
        assertTrue(DafReviews(dir).say(layout, amud, lines, DafReason.RIGHT, said = false).marks.isEmpty())
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

        assertEquals(
            listOf(1, 3),
            DafReviews(dir)
                .of(layout)
                .marks
                .map { it.i }
                .sorted(),
        )
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

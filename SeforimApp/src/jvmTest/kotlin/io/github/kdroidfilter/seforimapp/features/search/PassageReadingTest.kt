package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassageReadingTest {
    // A book of 100 lines, its passage the lines 40 to 44
    private val book = (0 until 100).map { Line(id = 1000L + it, bookId = 7L, lineIndex = it, content = "line $it") }

    private fun TestScope.reading(
        passage: IntRange = 40..44,
        asked: MutableList<IntRange> = ArrayList(),
    ) = PassageReading(book.slice(passage), backgroundScope) { from, to ->
        asked += from..to
        book.filter { it.lineIndex in from..to }
    }

    private fun PassageReading.indexes() = lines.map { it.lineIndex }

    @Test
    fun `more of the book is read after the passage, then before it`() =
        runTest {
            val reading = reading()
            reading.more(before = false)
            runCurrent()
            assertEquals((40..74).toList(), reading.indexes())
            assertFalse(reading.grownBefore)

            reading.more(before = true)
            runCurrent()
            assertEquals((10..74).toList(), reading.indexes())
            assertTrue(reading.grownBefore)
        }

    @Test
    fun `nothing is read past the book's ends`() =
        runTest {
            val asked = ArrayList<IntRange>()
            val reading = reading(passage = 0..2, asked = asked)
            reading.more(before = true)
            runCurrent()
            assertEquals(emptyList(), asked)

            val last = reading(passage = 95..99, asked = asked)
            last.more(before = false)
            runCurrent()
            last.more(before = false)
            runCurrent()
            // Asked once: the book gave no line, and is not asked again
            assertEquals(listOf(100..129), asked)
            assertEquals((95..99).toList(), last.indexes())
        }

    @Test
    fun `one reading at a time`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val asked = ArrayList<IntRange>()
            val reading =
                PassageReading(book.slice(40..44), backgroundScope) { from, to ->
                    asked += from..to
                    gate.await()
                    book.filter { it.lineIndex in from..to }
                }
            reading.more(before = false)
            runCurrent()
            reading.more(before = true)
            runCurrent()
            assertEquals(listOf(45..74), asked)
            gate.complete(Unit)
            runCurrent()
            assertEquals((40..74).toList(), reading.indexes())
        }

    @Test
    fun `a selection's lines are counted from the line pressed, down or up`() =
        runTest {
            val lines =
                listOf("אָמַר <b>רָבָא</b>: הֶיזֵּק רְאִיָּה", "שְׁמֵיהּ הֶיזֵּק, וְכֵן הֲלָכָה", "תָּא שְׁמַע").mapIndexed { i, text ->
                    Line(id = 10L + i, bookId = 7L, lineIndex = i, content = text)
                }
            val reading = PassageReading(lines, backgroundScope) { _, _ -> emptyList() }

            fun ids(selected: String) = reading.linesOf(selected)?.let { it.first.id to it.second.id }

            // Dragged down from the first line, then up from the third: the same lines
            reading.pressedLineId = 10L
            assertEquals(10L to 11L, ids("הֶיזֵּק רְאִיָּה\nשְׁמֵיהּ הֶיזֵּק"))
            reading.pressedLineId = 12L
            assertEquals(10L to 12L, ids("רְאִיָּה\nשְׁמֵיהּ הֶיזֵּק, וְכֵן הֲלָכָה\nתָּא"))
            // A word of one line; a right click that selected a word of another line
            assertEquals(12L to 12L, ids("שְׁמַע"))
            reading.menuLineId = 11L
            assertEquals(11L to 11L, ids("הֲלָכָה"))
            // Held by neither line
            assertEquals(null, ids("רָבָא"))
        }

    @Test
    fun `the lines added before the passage are held back for the scroll, those after are not`() =
        runTest {
            val reading = reading()
            reading.laidOut(500)
            reading.more(before = false)
            runCurrent()
            reading.laidOut(3500)
            assertEquals(0, reading.heldBack)

            reading.more(before = true)
            runCurrent()
            reading.laidOut(6500)
            assertEquals(3000, reading.heldBack)
            // A later layout (the pane made narrower) is no line added
            reading.heldBack = 0
            reading.laidOut(7000)
            assertEquals(0, reading.heldBack)
        }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An amud's words tied to the library: what a click selects and what is marked, on a small page. */
class DafLinksTest {
    private val gemara = 100L
    private val first = 200L
    private val second = 201L
    private val rashi = 7L

    // A Gemara line, and a line of Rashi with the end of one comment and the start of the next
    private val amud =
        DafAmud(
            amud = "2a",
            page = listOf(600f, 850f),
            block = listOf(0f, 0f, 100f, 100f),
            lines =
                listOf(
                    DafLine("G", listOf(0f, 0f, 100f, 10f), "הקטר חלבים ואברים מצותן", u = listOf(listOf(gemara, 0, 3))),
                    DafLine(
                        "R",
                        listOf(0f, 20f, 100f, 28f),
                        "הקטר חלבים ואברים. של קרבנות מצותן. להעלות",
                        d = listOf(0, 1, 2, 5),
                        u = listOf(listOf(first, 0, 4), listOf(second, 5, 6)),
                    ),
                ),
        )
    private val links = DafAmudLinks(amud, mapOf(first to setOf(gemara), second to setOf(gemara)), mapOf(first to rashi, second to rashi))

    @Test
    fun eachDibburQuotesItsWordsOfTheGemara() {
        assertEquals((0..2).map { DafWord(0, it) }.toSet(), links.quoted[first])
        assertEquals(setOf(DafWord(0, 3)), links.quoted[second])
        assertEquals(listOf(first, second), links.commentsOn[gemara])
        assertEquals(setOf(second), links.passageAt(DafWord(0, 3)))
        assertEquals(setOf(first), links.passageAt(DafWord(1, 1)), "a comment's word stands for its comment")
    }

    @Test
    fun aClickSelectsThePassageOrTheSegment() {
        val calls = mutableListOf<String>()
        val onLine = { id: Long, withModifier: Boolean -> calls += "line $id $withModifier" }
        val onPassage = { id: Long, comments: Set<Long>, books: Set<Long> -> calls += "passage $id $comments $books" }
        clickOn(links, DafWord(0, 3), false, onLine, onPassage)
        clickOn(links, DafWord(1, 6), false, onLine, onPassage)
        clickOn(links, DafWord(0, 3), true, onLine, onPassage)
        assertEquals(
            listOf("passage $gemara [$second] [$rashi]", "passage $gemara [$second] [$rashi]", "line $gemara true"),
            calls,
        )
    }

    @Test
    fun thePassageIsMarkedStrongestThenTheSegmentThenItsComments() {
        val color = Color.Blue
        val highlight = dafHighlight(links, setOf(gemara), setOf(second), color)
        val passage = highlight.colorOf(DafWord(0, 3))
        val segment = highlight.colorOf(DafWord(0, 0))
        val related = highlight.colorOf(DafWord(1, 0))
        assertEquals(passage, highlight.colorOf(DafWord(1, 6)), "the selected comment as the words it quotes")
        assertTrue(passage!!.alpha > segment!!.alpha && segment.alpha > related!!.alpha)
        assertNull(dafHighlight(links, emptySet(), emptySet(), color).colorOf(DafWord(0, 0)))
    }
}

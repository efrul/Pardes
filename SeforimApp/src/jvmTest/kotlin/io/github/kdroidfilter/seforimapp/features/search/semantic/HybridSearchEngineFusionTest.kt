package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.coverage
import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.fusedScores
import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.queryWords
import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.verbatimKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [HybridSearchEngine.fusedScores]: RRF with the word-for-word weight, the short dense lines left out and the window. */
class HybridSearchEngineFusionTest {
    private val eps = 1e-12

    private fun rrf(rank: Int) = 1.0 / (60 + rank + 1)

    @Test
    fun `a lexical hit holding a query of three words word for word weighs three`() {
        val texts = mapOf(1L to "דבר אחר לגמרי בלי המילים האלה", 2L to "תנן <b>אין</b> מרחמין בדין")
        val scores = fusedScores(listOf(1L, 2L), emptyList(), 3.0, "אין מרחמין בדין", general = true) { texts[it] }
        assertEquals(rrf(0), scores.getValue(1L), eps)
        // Weighed three, and its window holds all three words
        assertEquals(3 * rrf(1) + 0.03, scores.getValue(2L), eps)
    }

    @Test
    fun `a query of two words is no sentence, though its words are in a row`() {
        val texts = mapOf(1L to "שורה אחרת", 2L to "אין מרחמין בדין")
        val scores = fusedScores(listOf(1L, 2L), emptyList(), 3.0, "מרחמין בדין", general = true) { texts[it] }
        assertEquals(rrf(1) + 0.03, scores.getValue(2L), eps)
    }

    @Test
    fun `short lines the dense side alone finds stay out of a search of several books, and its ranks close up`() {
        val long = "מילה ".repeat(12).trim()
        val texts = mapOf(10L to long, 20L to "כותרת של שלוש", 21L to long)
        val query = "זזז ששש"
        val several = fusedScores(listOf(10L), listOf(20L, 21L, 10L), 3.0, query, general = true) { texts[it] }
        assertFalse(20L in several)
        assertEquals(3 * rrf(0), several.getValue(21L), eps)
        assertEquals(rrf(0) + 3 * rrf(1), several.getValue(10L), eps)
        // In one book its short lines are its own
        val one = fusedScores(listOf(10L), listOf(20L, 21L, 10L), 3.0, query, general = false) { texts[it] }
        assertEquals(3 * rrf(0), one.getValue(20L), eps)
        assertEquals(rrf(0) + 3 * rrf(2), one.getValue(10L), eps)
        // A short line the words find too stays
        val shortBoth = fusedScores(listOf(20L), listOf(20L), 3.0, query, general = true) { texts[it] }
        assertEquals(rrf(0) + 3 * rrf(0), shortBoth.getValue(20L), eps)
    }

    @Test
    fun `without the library's lines the fusion is RRF alone`() {
        val scores = fusedScores(listOf(1L, 2L), listOf(3L, 1L), 3.0, "אין מרחמין בדין", general = true) { null }
        val expected = mapOf(1L to rrf(0) + 3 * rrf(1), 2L to rrf(1), 3L to 3 * rrf(0))
        assertEquals(expected.keys, scores.keys)
        expected.forEach { (id, score) -> assertEquals(score, scores.getValue(id), eps) }
    }

    @Test
    fun `a search the words find nothing for keeps the dense side's order`() {
        val texts = mapOf(20L to "כותרת של שלוש", 21L to "שבת קודש היום")
        val scores = fusedScores(emptyList(), listOf(20L, 21L), 3.0, "שבת קודש", general = true) { texts[it] }
        // Neither the short line left out nor the window's bonus
        assertEquals(3 * rrf(0), scores.getValue(20L), eps)
        assertEquals(3 * rrf(1), scores.getValue(21L), eps)
    }

    @Test
    fun `the window holds the query's words close together, with or without their prefixes`() {
        val wanted = queryWords("שבת קודש")
        assertEquals(listOf("שבת", "קודש"), wanted)
        assertEquals(1.0, coverage(verbatimKey("והשבת יום קודש הוא"), wanted), eps)
        // A window of six words: "קודש" seven words after "שבת" is outside it
        assertEquals(0.5, coverage(verbatimKey("שבת א ב ג ד ה ו קודש"), wanted), eps)
        assertEquals(0.0, coverage(verbatimKey("שבת"), queryWords("שבת")), eps)
        assertTrue(coverage("", wanted) == 0.0)
    }
}

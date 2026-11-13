package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals

/** The daf's amudim two side by side, as a Gemara opens: amud b on the right, the next daf's amud a on the left. */
class DafSpreadsTest {
    // Each row as its amudim, the left one's then the right one's (null: that side empty)
    private fun DafSpreads.named(order: List<String>): List<Pair<String?, String?>> =
        rows.map { row -> row.left?.let(order::get) to row.right?.let(order::get) }

    @Test
    fun `2a is alone on the left, then each amud b on the right of the next amud a`() {
        val order = listOf("2a", "2b", "3a", "3b", "4a")
        val spreads = DafSpreads(order)

        assertEquals(listOf("2a" to null, "3a" to "2b", "4a" to "3b"), spreads.named(order))
        assertEquals(listOf(0, 1, 1, 2, 2), order.indices.map(spreads::rowOf))
    }

    @Test
    fun `an amud b read first in its row, and alone on the right at the tractate's end`() {
        val order = listOf("2a", "2b", "3a", "3b")
        val spreads = DafSpreads(order)

        assertEquals(listOf("2a" to null, "3a" to "2b", null to "3b"), spreads.named(order))
        assertEquals(listOf(0, 1, 3), spreads.rows.map { it.first })
    }

    @Test
    fun `an amud the order has not leaves its side empty`() {
        // 5a is missing: 4b has no amud on its left, and 5b none on its right… but 6a
        val order = listOf("2a", "4b", "5b", "6a")
        val spreads = DafSpreads(order)

        assertEquals(listOf("2a" to null, null to "4b", "6a" to "5b"), spreads.named(order))
    }

    @Test
    fun `an amud of no daf's number has a row of its own`() {
        val order = listOf("2a", "2b", "x", "y")

        assertEquals(listOf("2a" to null, null to "2b", "x" to null, "y" to null), DafSpreads(order).named(order))
    }
}

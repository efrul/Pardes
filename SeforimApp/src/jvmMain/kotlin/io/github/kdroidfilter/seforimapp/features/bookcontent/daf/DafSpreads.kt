package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

// Pardes (request 47 of docs/DESKTOP_STAGE2.md): the daf's amudim two side by side, as a Gemara open, "עמוד א בצד שמאל
// עמוד ב בימין".

/** A row of amudim side by side: the places in the order of its amud on the [left] and of its amud on the [right]. */
internal class DafSpread(
    val left: Int?,
    val right: Int?,
) {
    /** The amud read first in the row: the right one, else the left. */
    val first: Int get() = right ?: left ?: 0
}

/**
 * The amudim of a tractate in [order] (`2a`, `2b`, `3a`…) two side by side, as a Gemara opens: each daf's amud b on
 * the right and the next daf's amud a on the left, 2a alone on the left as the tractate's first page. An amud the order
 * has not leaves its side empty, and one of no daf's number has a row of its own.
 */
internal class DafSpreads(
    order: List<String>,
) {
    val rows: List<DafSpread>
    private val rowOfAmud = IntArray(order.size)

    init {
        val built = ArrayList<DafSpread>()
        var spread: Int? = null
        var left: Int? = null
        var right: Int? = null
        order.forEachIndexed { i, amud ->
            val index = amudIndex(amud)
            // Amud b of a daf and amud a of the next share a spread: 2b and 3a the third, as 1b would 2a
            val at = if (index == Int.MAX_VALUE) null else (index + 1) / 2
            if (at == null || at != spread || (if (amud.endsWith('b')) right else left) != null) {
                if (left != null || right != null) built += DafSpread(left, right)
                left = null
                right = null
            }
            spread = at
            if (at != null && amud.endsWith('b')) right = i else left = i
            rowOfAmud[i] = built.size
        }
        if (left != null || right != null) built += DafSpread(left, right)
        rows = built
    }

    /** The row of the amud at [amud] in the order. */
    fun rowOf(amud: Int): Int = rowOfAmud.getOrElse(amud) { 0 }
}

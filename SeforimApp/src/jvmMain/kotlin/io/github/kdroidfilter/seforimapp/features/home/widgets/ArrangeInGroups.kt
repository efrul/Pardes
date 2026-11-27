package io.github.kdroidfilter.seforimapp.features.home.widgets

import kotlin.random.Random

// Pardes: the Home's "arrange" for more widgets than its search holds. The search (ArrangeLayout.kt) keeps two tables of
// an entry a subset of the widgets, so each widget more doubles its memory and triples its time: the 20 widgets of
// Zayit's and Pardes's gallery, all placed, were its most, and with Sefaria's two it ran out of memory. A Home seldom
// holds that many; when one does, its widgets are dealt into groups the search holds, each arranged as before.

/** The most widgets [arranged] lays out at once: all of the gallery's 20, as before Sefaria's two. */
internal const val MAX_ARRANGED_AT_ONCE = 20

/**
 * The widgets laid out again as [arranged] lays them, when they are more than [MAX_ARRANGED_AT_ONCE]: dealt in reading
 * order, or shuffled by [random], into the fewest groups of at most that many, as even as can be, each arranged on its
 * own and put under the one before it. The widgets come back in their own order, as [arranged] gives them.
 */
internal fun List<WidgetPlacement>.arrangedInGroups(
    pitch: CellPitch,
    random: Random? = null,
): List<WidgetPlacement> {
    val reading = sortedWith(compareBy({ it.cell.y }, { it.cell.x }))
    val dealt = random?.let(reading::shuffled) ?: reading
    val groups = (size + MAX_ARRANGED_AT_ONCE - 1) / MAX_ARRANGED_AT_ONCE
    var top = 0
    val placed =
        dealt.chunked((size + groups - 1) / groups).flatMap { group ->
            group
                .arranged(pitch)
                .map { it.copy(cell = it.cell.copy(y = it.cell.y + top)) }
                .also { top = it.bottom() }
        }
    val order = map { it.widget.id }
    return placed.sortedBy { order.indexOf(it.widget.id) }
}

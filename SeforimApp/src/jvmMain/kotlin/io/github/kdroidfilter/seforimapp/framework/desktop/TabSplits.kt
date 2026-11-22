package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.Serializable
import kotlin.math.abs

// Pardes (request 59 of docs/DESKTOP_STAGE3_PLAN.md: "כרטיסיות מחוברות כמו בכרום", the user's choice of 2026-11-18:
// Chrome's split view): two tabs of a window side by side, joined in its strip. A window has one such pair at most; it
// is shown while one of its two tabs is selected, the selected one the side the panes, bars and keys act on, and kept
// with the window in the session.

/**
 * Two tabs of a window side by side, [first] and [second] (their places are the window's: the one on the left in its
 * strip is drawn on the left), and the share of the window's width of the one on the left ([ratio]). Kept in the
 * session with its window (WindowSnapshot.split): new fields go last.
 */
@Immutable
@Serializable
data class TabSplit(
    val first: String,
    val second: String,
    val ratio: Float = HALF,
) {
    /** Whether [tabId] is one of the two. */
    operator fun contains(tabId: String?): Boolean = tabId == first || tabId == second

    /** The other one of the two than [tabId]. */
    fun partnerOf(tabId: String): String? =
        when (tabId) {
            first -> second
            second -> first
            else -> null
        }
}

/** The half of its window a tab of a split shown is drawn in: the left one or the right, and the left one's share. */
@Immutable
data class SplitHalf(
    val left: Boolean,
    val ratio: Float,
)

/** The split views of a desktop's windows, by the window's group: one pair a window at most. */
@Stable
class TabSplits {
    private val splits = mutableStateMapOf<String, TabSplit>()

    /** The pair of window [groupId], while both its tabs stand next to each other in [ids] (its order); null otherwise. */
    fun of(
        groupId: String,
        ids: List<String>,
    ): TabSplit? = splits[groupId]?.takeIf { it.adjacentIn(ids) }

    /** Window [groupId] shows [a] and [b] side by side, half and half, once they stand next to each other. */
    fun split(
        groupId: String,
        a: String,
        b: String,
    ) {
        if (a != b) splits[groupId] = TabSplit(a, b)
    }

    /** Window [groupId] shows its tabs one at a time again. */
    fun end(groupId: String) {
        splits.remove(groupId)
    }

    /** The left one's share of window [groupId]'s width, between a quarter and three quarters. */
    fun setRatio(
        groupId: String,
        ratio: Float,
    ) {
        val split = splits[groupId] ?: return
        splits[groupId] = split.copy(ratio = ratio.coerceIn(MIN_RATIO, MAX_RATIO))
    }

    /** The pair of window [groupId] for the session file, as it is. */
    fun saved(groupId: String): TabSplit? = splits[groupId]

    /** Window [groupId] restored with its pair [split]. */
    fun restore(
        groupId: String,
        split: TabSplit,
    ) {
        splits[groupId] = split
    }
}

/** Whether the two tabs stand next to each other in [ids]. */
internal fun TabSplit.adjacentIn(ids: List<String>): Boolean {
    val a = ids.indexOf(first)
    val b = ids.indexOf(second)
    return a >= 0 && b >= 0 && abs(a - b) == 1
}

/**
 * The half the tab [tabId] is drawn in while its window, its tabs [ids], shows [split] with [selected] selected: as the
 * strip lays them out, the window's earlier tab on the left (its first tab is at the strip's left end, beside the new
 * tab's button, from the right as from the left); null when the split is not shown (no pair, or the tab selected
 * outside it) or the tab is not one of the two.
 */
fun splitHalf(
    split: TabSplit?,
    ids: List<String>,
    selected: String?,
    tabId: String,
): SplitHalf? {
    if (split == null || selected !in split || tabId !in split) return null
    val earlier = if (ids.indexOf(split.first) <= ids.indexOf(split.second)) split.first else split.second
    return SplitHalf(left = tabId == earlier, ratio = split.ratio)
}

private const val HALF = 0.5f
private const val MIN_RATIO = 0.25f
private const val MAX_RATIO = 0.75f

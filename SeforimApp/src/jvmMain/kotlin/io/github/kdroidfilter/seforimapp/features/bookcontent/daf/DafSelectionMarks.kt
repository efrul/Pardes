package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import kotlinx.serialization.Serializable

// Pardes (request 102 of docs/DESKTOP_STAGE3_PLAN.md, the user's word on the testers' build: "אני רוצה שבמקום הדגשה כבדה
// של הטקסט כמו היום יוכלו להגדיר בהגדרות שיהיה פס צידי כמו בתצוגת טקסט, והאם בחירה של הספר הראשי תדגיש את המפרשים
// המקושרים והאם בהדגשה כמו היום או גם בפס צידי"): how a page, a tractate's amud or a book's own, marks the tab's
// selection, as the reader set it in Settings › צורת הדף.

/** A way a page marks lines: a color behind their words, as a highlighter, or a bar at their side, as the text view's. */
@Serializable
enum class SelectionMark { Highlight, Stripe }

/**
 * How a page marks the tab's selection: what is selected ([selection]: the text's segments, a comment clicked itself),
 * and the comments on the selected segments ([comments]; null: not at all). As it always was by default: both
 * highlighted.
 */
@Serializable
data class DafSelectionMarks(
    val selection: SelectionMark = SelectionMark.Highlight,
    val comments: SelectionMark? = SelectionMark.Highlight,
)

/** The pages' selection marks' settings (Settings › צורת הדף). */
object DafSelectionSettings : SettingsGroup("daf_selection") {
    val marks by json(DafSelectionMarks())
}

/** How strong the bar beside the comments on the selected segments is, to the selection's own. */
private const val RELATED_STRIPE_ALPHA = 0.45f

/**
 * The bars at the side of a page's lines, by the words on them: the selection's own (the selected segments, the comment
 * clicked itself) in [color], the comments on the selected segments lighter; each only where the reader has it marked
 * by a bar rather than a highlight ([how]). A line any word of which has a bar has one.
 */
internal fun dafStripe(
    links: DafAmudLinks?,
    selected: Set<Long>,
    passage: Set<Long>,
    color: Color,
    how: DafSelectionMarks,
): DafHighlight {
    val ofSelection = how.selection == SelectionMark.Stripe
    val ofComments = how.comments == SelectionMark.Stripe
    if (links == null || !(ofSelection || ofComments)) return DafHighlight.NONE
    if (selected.isEmpty() && passage.isEmpty()) return DafHighlight.NONE
    val related = if (ofComments) selected.flatMapTo(HashSet()) { links.commentsOn[it].orEmpty() } else emptySet()
    val relatedColor = color.copy(alpha = color.alpha * RELATED_STRIPE_ALPHA)
    return DafHighlight { word ->
        val id = links.lineIdOf(word) ?: return@DafHighlight null
        when {
            id in passage || id in selected -> color.takeIf { ofSelection }
            id in related -> relatedColor
            else -> null
        }
    }
}

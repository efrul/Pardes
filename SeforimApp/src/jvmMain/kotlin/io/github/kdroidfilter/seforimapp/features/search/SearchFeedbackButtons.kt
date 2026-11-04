package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.feedback.FeedbackGrade
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_feedback_exact
import seforimapp.seforimapp.generated.resources.search_feedback_not_relevant
import seforimapp.seforimapp.generated.resources.search_feedback_relevant

/**
 * Pardes: feedback mode's buttons under a search result (Settings › ניהול נתונים, the semantic search's card): exactly
 * this, relevant, not relevant. The [grade] given is marked; clicking it again takes it back ([onGrade] decides).
 */
@Composable
internal fun SearchFeedbackButtons(
    grade: FeedbackGrade?,
    onGrade: (FeedbackGrade) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (each in FeedbackGrade.entries) {
            FeedbackChip(stringResource(labelOf(each)), selected = grade == each, onClick = { onGrade(each) })
        }
    }
}

private fun labelOf(grade: FeedbackGrade): StringResource =
    when (grade) {
        FeedbackGrade.EXACT -> Res.string.search_feedback_exact
        FeedbackGrade.RELEVANT -> Res.string.search_feedback_relevant
        FeedbackGrade.NOT_RELEVANT -> Res.string.search_feedback_not_relevant
    }

@Composable
private fun FeedbackChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = label,
        fontSize = resultSp(11f),
        color = if (selected) accent else readingSecondary(),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier =
            Modifier
                .clip(shape)
                .border(
                    1.dp,
                    if (selected) {
                        accent
                    } else {
                        JewelTheme.globalColors.borders.normal
                            .copy(alpha = 0.6f)
                    },
                    shape,
                ).background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

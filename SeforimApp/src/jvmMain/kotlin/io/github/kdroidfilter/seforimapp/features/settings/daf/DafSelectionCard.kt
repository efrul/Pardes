package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafSelectionSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.SelectionMark
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_selection_comments
import seforimapp.seforimapp.generated.resources.daf_selection_description
import seforimapp.seforimapp.generated.resources.daf_selection_highlight
import seforimapp.seforimapp.generated.resources.daf_selection_none
import seforimapp.seforimapp.generated.resources.daf_selection_selected
import seforimapp.seforimapp.generated.resources.daf_selection_stripe
import seforimapp.seforimapp.generated.resources.daf_selection_title

/** The ways the comments on the selected segments are marked, as the card lists them: not at all first. */
private val COMMENTS_MARKS = listOf(null, SelectionMark.Highlight, SelectionMark.Stripe)

/**
 * Settings › צורת הדף: how a page marks the tab's selection (DafSelectionMarks, request 102 of
 * docs/DESKTOP_STAGE3_PLAN.md): what is selected, behind its words or by a bar at its lines' side as the text view's,
 * and the comments on it, either way or not at all. A tractate's daf and a book's own pages alike.
 */
@Composable
internal fun DafSelectionCard() {
    val appSettings = LocalAppGraph.current.appSettings
    val marks by appSettings.flow(DafSelectionSettings.marks).collectAsState()
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.daf_selection_title), fontSize = 15.sp)
        Text(
            text = stringResource(Res.string.daf_selection_description),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        val highlight = stringResource(Res.string.daf_selection_highlight)
        val stripe = stringResource(Res.string.daf_selection_stripe)
        StyleChoice(
            label = stringResource(Res.string.daf_selection_selected),
            options = listOf(highlight, stripe),
            selected = marks.selection.ordinal,
            onSelect = { appSettings[DafSelectionSettings.marks] = marks.copy(selection = SelectionMark.entries[it]) },
        )
        StyleChoice(
            label = stringResource(Res.string.daf_selection_comments),
            options = listOf(stringResource(Res.string.daf_selection_none), highlight, stripe),
            selected = COMMENTS_MARKS.indexOf(marks.comments),
            onSelect = { appSettings[DafSelectionSettings.marks] = marks.copy(comments = COMMENTS_MARKS[it]) },
        )
    }
}

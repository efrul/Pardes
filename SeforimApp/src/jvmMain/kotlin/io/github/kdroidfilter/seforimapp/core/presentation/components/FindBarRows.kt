package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

// Pardes (stage 3 of docs/DESKTOP_STAGE3_PLAN.md): the find bar in a narrow view, a tab beside another, kept its field's
// width and lost its actions past the view's edge, as the user found after the second testers' build ("לא רק בpdf
// תתקן").

/**
 * The find bar's rows inside its frame ([frameModifier]: its shadow, background, border and padding): in one row, as Zayit's, the
 * [field] (its icon, the field and its counter), a divider and the [actions]; in a view narrower than [ONE_ROW_WIDTH],
 * the actions in a second row under the field, at its end. The field narrows before the actions do in either.
 */
@Composable
internal fun FindBarRows(
    field: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    frameModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= ONE_ROW_WIDTH) {
            Row(frameModifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                field()
                Divider(orientation = Orientation.Vertical, modifier = Modifier.padding(horizontal = 8.dp).height(20.dp))
                actions()
            }
        } else {
            Column(frameModifier, horizontalAlignment = Alignment.End) {
                Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically, content = field)
                Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

// The bar's height, a row's
private val ROW_HEIGHT = 36.dp

/** The narrowest view the bar has one row in: its icon, a field of 120 dp, its counter, a divider and five actions. */
internal val ONE_ROW_WIDTH: Dp = 380.dp

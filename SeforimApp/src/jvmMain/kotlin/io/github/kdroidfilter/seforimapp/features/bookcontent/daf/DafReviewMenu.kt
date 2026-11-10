package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.menuStyle
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_review_open_folder
import java.awt.Desktop

/**
 * An entry of the text's menu that is a checkbox, [checked] when the menu opens: a click checks or unchecks it
 * ([onCheck]), and the menu stays open, for another of them to be checked too.
 */
class ContextMenuCheck(
    label: String,
    val checked: Boolean,
    val onCheck: (Boolean) -> Unit,
) : ContextMenuItem(label, {})

/** A [ContextMenuCheck] in the menu: its box and its label, the row lit under the pointer as the menu's items are. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContextMenuCheckRow(
    item: ContextMenuCheck,
    modifier: Modifier = Modifier,
) {
    var checked by remember(item) { mutableStateOf(item.checked) }
    var hovered by remember { mutableStateOf(false) }
    // A passive item of the menu is given no content color: the menu's own, as its other items
    val colors = JewelTheme.menuStyle.colors.itemColors
    val check: (Boolean) -> Unit = {
        checked = it
        item.onCheck(it)
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (hovered) colors.backgroundHovered else Color.Transparent)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .clickable { check(!checked) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = check)
        Text(text = item.label, color = colors.content)
    }
}

/** Opens the folder the daf's reviews are kept in (the layouts' feedback/), to look at them or to send one by hand. */
@Composable
fun DafReviewsFolderLink(modifier: Modifier = Modifier) {
    val reviews = rememberDafLayouts().reviews
    val scope = rememberCoroutineScope()
    Link(
        text = stringResource(Res.string.daf_review_open_folder),
        onClick = {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    reviews.folder.mkdirs()
                    Desktop.getDesktop().open(reviews.folder)
                }
            }
        },
        modifier = modifier,
    )
}

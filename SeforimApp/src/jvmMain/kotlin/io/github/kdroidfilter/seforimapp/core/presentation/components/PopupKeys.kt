package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.unit.toSize
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.revealItem
import org.jetbrains.jewel.foundation.theme.JewelTheme
import kotlin.math.roundToInt

// Pardes (request 69 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md: "צריך תמיכה מלאה במקשים,
// כל תיבה שנפתחת תתמך בזה... היסטוריה והערות מהשורה למעלה"): the title bar's popups (the tab search and its history,
// the favorites, the notes) answer the keys as the breadcrumb's popup does: up and down mark a row, Enter opens it,
// Escape closes the popup.

/** A row of a popup's list the keys can mark: its place among the list's items ([item], headers counted) and [open]. */
internal class PopupKeyRow(
    val item: Int,
    val open: () -> Unit,
)

/**
 * The row the keys mark in a popup's list, as BreadcrumbPopup marks its own: [index] among the [rows] that open
 * something, in their order, the first at first; the list's [listState], which keeps it in view.
 */
@Stable
internal class PopupKeys(
    val listState: LazyListState,
) {
    var index by mutableIntStateOf(0)
        private set

    /** The rows that open something, in their order; set as the list is built. */
    var rows: List<PopupKeyRow> = emptyList()

    /** Whether the row [row] (its place among [rows]) is the one marked. */
    fun marks(row: Int): Boolean = row == index

    internal fun move(by: Int) {
        if (rows.isNotEmpty()) index = (index + by).coerceIn(0, rows.lastIndex)
    }

    internal fun open() {
        rows.getOrNull(index)?.open?.invoke()
    }
}

/** A popup's keys, on the first row again when [inputs] change (what is typed in its field). */
@Composable
internal fun rememberPopupKeys(vararg inputs: Any?): PopupKeys {
    val listState = rememberLazyListState()
    val keys = remember(*inputs) { PopupKeys(listState) }
    LaunchedEffect(keys, keys.index) {
        val row = keys.rows.getOrNull(keys.index) ?: return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) listState.revealItem(row.item)
    }
    return keys
}

/** Up and down mark a row of [keys], Enter opens it, Escape closes the popup ([onEscape]), before its field takes them. */
internal fun Modifier.popupKeys(
    keys: PopupKeys,
    onEscape: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> keys.move(1)
            Key.DirectionUp -> keys.move(-1)
            Key.Enter, Key.NumPadEnter -> keys.open()
            Key.Escape -> onEscape()
            else -> return@onPreviewKeyEvent false
        }
        true
    }

/**
 * A dropdown's popup (DropdownButton): up and down move the focus from row to row (the first row from none), Escape
 * closes it ([onEscape]); Enter and Space click the row in focus, as a clickable row takes them (the home's catalog
 * dropdowns, request 69 of stage 3: "כמו למשל הטאבים של הקטגוריות במסך הבית").
 */
internal fun Modifier.dropdownKeys(
    focusManager: FocusManager,
    onEscape: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Next)
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Previous)
            Key.Escape -> onEscape()
            else -> return@onPreviewKeyEvent false
        }
        true
    }

/**
 * A dropdown row's background ([DropdownButton]'s rows): [hovered], Zayit's tint as the pointer comes, or the same tint at
 * once while the keys mark the row ([focused]), as the app's lists mark theirs: no fading trail under held arrows.
 */
@Composable
internal fun keyMarkedBackground(
    focused: Boolean,
    hovered: Color,
): Color =
    if (focused) {
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.12f)
    } else {
        hovered
    }

/**
 * The rows of a column scrolled by [scrollState] come into view at once when they ask (the row the keys move the focus
 * to), as the app's lists reveal theirs ([revealItem]), not by the scroll's animation: on the column, inside its scroll,
 * it answers its rows' requests itself.
 */
internal fun Modifier.revealAtOnce(scrollState: ScrollState): Modifier = this then RevealAtOnceElement(scrollState)

private data class RevealAtOnceElement(
    val scrollState: ScrollState,
) : ModifierNodeElement<RevealAtOnceNode>() {
    override fun create() = RevealAtOnceNode(scrollState)

    override fun update(node: RevealAtOnceNode) {
        node.scrollState = scrollState
    }
}

private class RevealAtOnceNode(
    var scrollState: ScrollState,
) : Modifier.Node(),
    BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        val bounds = boundsProvider() ?: Rect(Offset.Zero, childCoordinates.size.toSize())
        val top = requireLayoutCoordinates().localPositionOf(childCoordinates, bounds.topLeft).y
        val first = scrollState.value
        val target =
            when {
                top < first -> top
                top + bounds.height > first + scrollState.viewportSize -> top + bounds.height - scrollState.viewportSize
                else -> return
            }
        scrollState.scrollTo(target.roundToInt())
    }
}

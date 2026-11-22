package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.SplitHalf
import io.github.kdroidfilter.seforimapp.framework.desktop.TabSplit
import io.github.kdroidfilter.seforimapp.framework.desktop.splitHalf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.tab_split_beside
import seforimapp.seforimapp.generated.resources.tab_split_end
import seforimapp.seforimapp.generated.resources.tab_split_new
import java.awt.Cursor
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// Pardes (request 59 of docs/DESKTOP_STAGE3_PLAN.md, the user's choice of 2026-11-18: Chrome's split view): a
// window's two tabs side by side, their texts each in its half, a grip between them that moves the split, a press in
// the half not selected selecting it; in the strip, the two joined as one.

/**
 * Whether the tab drawn here is on screen: the tab selected, or the one beside it in a split shown. What is drawn only
 * while on screen (a PDF book's pages, a tractate's scan) asks this rather than `LocalTabSelected`, which the tab
 * beside the selected one is not: its focus and its popups stay the selected tab's.
 */
val LocalTabShown = compositionLocalOf { true }

/** The split of this window shown now: its pair, while one of the two is the tab selected; null otherwise. */
fun OpenWindow.shownSplit(): TabSplit? {
    val group = group() ?: return null
    return session.splits.of(groupId, group.ids)?.takeIf { group.selectedId in it }
}

/** The half of this window the tab [tabId] is drawn in; null for the whole of it. */
fun OpenWindow.splitHalfOf(tabId: String): SplitHalf? {
    val group = group() ?: return null
    return splitHalf(session.splits.of(groupId, group.ids), group.ids, group.selectedId, tabId)
}

/** Whether the tab [tabId] is shown beside the tab selected, the other half of the split shown. */
fun OpenWindow.shownBeside(tabId: String): Boolean = shownSplit()?.partnerOf(tabId) == group()?.selectedId

/**
 * The tab [tabId] shown beside the tab selected: moved next to it when it is not, the two split half and half, the
 * selected one still selected (Chrome's "Add tab to split view").
 */
fun OpenWindow.splitBeside(tabId: String) {
    val group = group() ?: return
    val selected = group.selectedId ?: return
    val at = group.ids.indexOf(selected)
    val from = group.ids.indexOf(tabId)
    if (at < 0 || from < 0 || at == from) return
    if (abs(at - from) > 1) session.workspace.reorder(tabId, if (from > at) at + 1 else at - 1)
    session.splits.split(groupId, selected, tabId)
}

/** A new tab opened beside the tab selected, the two split half and half, the new one selected (Chrome's "New split view"). */
fun OpenWindow.splitWithNewTab() {
    val selected = group()?.selectedId ?: return
    val tab = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())
    tabsViewModel.openTab(tab)
    session.splits.split(groupId, selected, tab.tabId)
}

/**
 * The items of a tab's menu ([tabId]) for its window's split: its end, for one of the two; for another tab, it shown
 * beside the tab selected; for the tab selected, it split with a new tab.
 */
@Composable
internal fun OpenWindow.splitTabActions(tabId: String): List<PardesTabAction> {
    val group = group() ?: return emptyList()
    val split = session.splits.of(groupId, group.ids)
    val action =
        when {
            split?.contains(tabId) == true ->
                PardesTabAction(stringResource(Res.string.tab_split_end), AllIconsKeys.Actions.Close) { session.splits.end(groupId) }
            tabId != group.selectedId ->
                PardesTabAction(stringResource(Res.string.tab_split_beside), AllIconsKeys.Actions.SplitVertically) { splitBeside(tabId) }
            else ->
                PardesTabAction(stringResource(Res.string.tab_split_new), AllIconsKeys.Actions.SplitVertically) { splitWithNewTab() }
        }
    return listOf(action)
}

/** Selects the tab [tabId] of this window. */
fun OpenWindow.selectTab(tabId: String) {
    val index =
        tabsViewModel.state.value.tabs
            .indexOfFirst { it.destination.tabId == tabId }
    if (index >= 0) tabsViewModel.onEvent(TabsEvents.OnSelect(index))
}

/**
 * A tab's place in its window: as it is (Zayit's layout after it shows it whole or not at all), or its half of a split
 * ([half]), the grip's width between the halves; a press in it selects it ([onPress]) and goes on to what it presses.
 */
fun Modifier.splitHalf(
    half: SplitHalf?,
    onPress: () -> Unit,
): Modifier =
    if (half == null) {
        this
    } else {
        layout { measurable, constraints ->
            val width = constraints.maxWidth
            val left = leftWidth(width, SPLIT_GRIP.roundToPx(), half.ratio)
            val own = if (half.left) left else (width - SPLIT_GRIP.roundToPx() - left).coerceAtLeast(0)
            val placeable = measurable.measure(constraints.copy(minWidth = own, maxWidth = own))
            layout(width, placeable.height) { placeable.place(if (half.left) 0 else left + SPLIT_GRIP.roundToPx(), 0) }
        }.pointerInput(half.left) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onPress()
                }
            }
        }
    }

/** The left half's width of a window [width] pixels wide with a grip [grip] pixels wide between, its share [ratio]. */
internal fun leftWidth(
    width: Int,
    grip: Int,
    ratio: Float,
): Int = ((width - grip).coerceAtLeast(0) * ratio).roundToInt()

/**
 * The grip between the halves of this window's split shown ([OpenWindow.shownSplit]), which moves it: the left
 * half's share follows the pointer, between a quarter and three quarters. Nothing while no split is shown.
 */
@Composable
fun BoxScope.TabSplitGrip(openWindow: OpenWindow) {
    val split = openWindow.shownSplit() ?: return
    var width by remember { mutableIntStateOf(0) }
    val ratio by rememberUpdatedState(split.ratio)
    val drag =
        rememberDraggableState { delta ->
            if (width > 0) openWindow.session.splits.setRatio(openWindow.groupId, ratio + delta / width)
        }
    Box(
        Modifier
            .fillMaxHeight()
            .layout { measurable, constraints ->
                width = constraints.maxWidth
                val grip = SPLIT_GRIP.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(grip, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(leftWidth(constraints.maxWidth, grip, split.ratio), 0)
                }
            }.pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(drag, Orientation.Horizontal),
    )
}

/**
 * The side a tab of the strip ([tabId]) is joined to its partner on, when it is one of its window's split (joined
 * while they stand next to each other, shown or not): its left edge or its right one; null for a tab on its own.
 */
internal fun OpenWindow.splitJoint(tabId: String): SplitJoint? {
    val group = group() ?: return null
    val split = session.splits.of(groupId, group.ids) ?: return null
    // Drawn as the one shown on the left, or on the right, whichever is selected
    val half = splitHalf(split, group.ids, split.first, tabId) ?: return null
    return if (half.left) SplitJoint.RIGHT else SplitJoint.LEFT
}

/** The edge a tab of a split is joined to its partner on, in the strip. */
internal enum class SplitJoint { LEFT, RIGHT }

/** The tab's shape in the strip: rounded but on its joint's edge, where it meets its partner. */
internal fun splitShape(
    joint: SplitJoint?,
    radius: Dp,
): Shape =
    when (joint) {
        null -> AbsoluteRoundedCornerShape(radius)
        SplitJoint.LEFT -> AbsoluteRoundedCornerShape(topLeft = 0.dp, topRight = radius, bottomRight = radius, bottomLeft = 0.dp)
        SplitJoint.RIGHT -> AbsoluteRoundedCornerShape(topLeft = radius, topRight = 0.dp, bottomRight = 0.dp, bottomLeft = radius)
    }

/** The tab's padding in the strip ([horizontal] and [vertical]): none on its joint's edge, to meet its partner. */
internal fun Modifier.splitPadding(
    joint: SplitJoint?,
    horizontal: Dp,
    vertical: Dp,
): Modifier =
    absolutePadding(
        left = if (joint == SplitJoint.LEFT) 0.dp else horizontal,
        right = if (joint == SplitJoint.RIGHT) 0.dp else horizontal,
        top = vertical,
        bottom = vertical,
    )

/** A tab of a split in the strip: its [background], tinted a little ([accent]) when it is none, and its outline. */
internal fun Modifier.splitFrame(
    joint: SplitJoint?,
    background: Color,
    accent: Color,
    shape: Shape,
): Modifier =
    if (joint == null) {
        background(background)
    } else {
        background(if (background.alpha == 0f) accent.copy(alpha = SPLIT_TINT) else background)
            .border(1.dp, accent.copy(alpha = SPLIT_OUTLINE), shape)
    }

/** Between the halves of a split, as the gap between the reader's cards. */
val SPLIT_GRIP: Dp = 6.dp

// A split's tab in the strip: the tint of one not selected, and the outline's
private const val SPLIT_TINT = 0.08f
private const val SPLIT_OUTLINE = 0.45f

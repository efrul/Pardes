package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
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
 * dropdowns, request 69 of stage 3: "כמו למשל הטאבים של הקטגוריות במסך הבית"). The keys mark the rows from then on
 * ([marking]).
 */
internal fun Modifier.dropdownKeys(
    focusManager: FocusManager,
    marking: DropdownMarking? = null,
    onEscape: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> {
                marking?.keysMoved()
                focusManager.moveFocus(FocusDirection.Next)
            }
            Key.DirectionUp -> {
                marking?.keysMoved()
                focusManager.moveFocus(FocusDirection.Previous)
            }
            Key.Escape -> onEscape()
            else -> return@onPreviewKeyEvent false
        }
        true
    }

/**
 * How a dropdown's rows are marked ([DropdownButton]), as a menu's are (the Tools menu, a tab's): one row at a time, the
 * one the keys moved to or the one the pointer moved over, which takes the focus ([rememberHoverFocus]), so that Enter
 * opens the row marked and the arrows go on from it (the user's word, 2026-11-20: "זה תקין? כשהחץ על אחד מהם?").
 */
@Stable
internal class DropdownMarking {
    /** The keys moved the focus last: the row under a still pointer is not marked, nor brought into view. */
    var byKeys by mutableStateOf(false)
        private set

    /** The pointer moved over the popup since the keys did: the row under it takes the focus. */
    var pointerMoved by mutableStateOf(false)
        private set

    private var pointer: Offset? = null

    fun keysMoved() {
        byKeys = true
        pointerMoved = false
    }

    /** The pointer at [at] in the popup: a move only when it is elsewhere, not a list scrolled under it again. */
    fun pointerAt(at: Offset) {
        if (at == pointer) return
        pointer = at
        byKeys = false
        pointerMoved = true
    }
}

/** The marking of the dropdown a row is in ([DropdownButton] provides it); null out of one. */
internal val LocalDropdownMarking = staticCompositionLocalOf<DropdownMarking?> { null }

/**
 * A dropdown row's focus as a menu row's: the row the pointer moves over (its hover in [interactionSource]) takes the
 * focus, once the pointer moved, and not as a list scrolls under a still pointer. Give it to the row's `focusRequester`.
 */
@Composable
internal fun rememberHoverFocus(interactionSource: InteractionSource): FocusRequester {
    val requester = remember { FocusRequester() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val moved = LocalDropdownMarking.current?.pointerMoved == true
    LaunchedEffect(hovered, moved) { if (hovered && moved) requester.requestFocus() }
    return requester
}

/**
 * A dropdown row's background ([DropdownButton]'s rows): the row in focus, the one marked ([DropdownMarking]), tinted at
 * once as the app's lists mark theirs (no fading trail under held arrows); [hovered], Zayit's tint as the pointer comes,
 * only while the keys do not mark the rows.
 */
@Composable
internal fun keyMarkedBackground(
    focused: Boolean,
    hovered: Color,
): Color =
    when {
        focused ->
            JewelTheme.globalColors.outlines.focused
                .copy(alpha = 0.12f)
        LocalDropdownMarking.current?.byKeys == true -> Color.Transparent
        else -> hovered
    }

/**
 * The rows of a column scrolled by [scrollState] come into view at once when they ask (the row the keys move the focus
 * to), as the app's lists reveal theirs ([revealItem]), not by the scroll's animation: on the column, inside its scroll,
 * it answers its rows' requests itself; a row the pointer gave the focus to ([marking]) stays where it is.
 */
internal fun Modifier.revealAtOnce(
    scrollState: ScrollState,
    marking: DropdownMarking? = null,
): Modifier = this then RevealAtOnceElement(scrollState, marking)

private data class RevealAtOnceElement(
    val scrollState: ScrollState,
    val marking: DropdownMarking?,
) : ModifierNodeElement<RevealAtOnceNode>() {
    override fun create() = RevealAtOnceNode(scrollState, marking)

    override fun update(node: RevealAtOnceNode) {
        node.scrollState = scrollState
        node.marking = marking
    }
}

private class RevealAtOnceNode(
    var scrollState: ScrollState,
    var marking: DropdownMarking?,
) : Modifier.Node(),
    BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        if (marking?.pointerMoved == true) return
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

package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.PopupPositionProvider
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.revealItem
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.theme.menuStyle
import kotlin.math.roundToInt

// Pardes (request 74 of the list after the public release, docs/DESKTOP_STAGE3_PLAN.md: "יש עדיין תפריטים שבהם העכבר
// והכפתורים לא עובדים כמו שעשית בתפריט לשונית וכלים... למה זה לא קוד משותף לכולם?"): every popup list of the app marks
// its rows by this file's one code, as a menu does (the Tools menu, a tab's): one row at a time, the one the keys moved
// to or the one the pointer moved over ([PopupMarking]), in one tint ([markedRowBackground]), brought into view at once
// ([reveal], [revealAtOnce]); and Jewel's menus marking one item ([SingleMarkMenus]).

/**
 * How a popup's rows are marked, as a menu's are (the Tools menu, a tab's): one row at a time, the one the keys moved to
 * or the one the pointer moved over ([MarkOnHover]), so that Enter opens the row marked and the arrows go on from
 * it (the user's word, 2026-11-20: "זה תקין? כשהחץ על אחד מהם?"). A dropdown's row takes the focus to be marked
 * ([rememberHoverFocus]); a popup whose field keeps the focus marks its row by index ([PopupKeys]).
 */
@Stable
internal class PopupMarking {
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

    /**
     * The pointer at [at] in the popup, read as IntelliJ's popups read it (ListPopupImpl.isMouseMoved): its first place
     * only noted (a popup opened under a still pointer), then a move only when it is elsewhere, not a list scrolled
     * under it again.
     */
    fun pointerAt(at: Offset) {
        val last = pointer
        pointer = at
        if (last == null || last == at) return
        byKeys = false
        pointerMoved = true
    }
}

/** The marking of the popup a row is in ([DropdownButton] and the popups of the search bar provide it); null out of one. */
internal val LocalPopupMarking = staticCompositionLocalOf<PopupMarking?> { null }

/** The pointer's moves over this popup told to [marking] ([PopupMarking.pointerAt]). */
internal fun Modifier.notePointer(marking: PopupMarking): Modifier =
    pointerInput(marking) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Move) marking.pointerAt(event.changes.first().position)
            }
        }
    }

/**
 * A popup's list of [listState] marks the item the pointer moves over ([onMark], by its index in the list), as a menu
 * marks its rows: told to [marking], which notes the pointer's first place only, and not a list scrolled under a still
 * pointer. On the list itself, whose items' offsets are its own.
 */
internal fun Modifier.markOnPointer(
    marking: PopupMarking,
    listState: LazyListState,
    onMark: (item: Int) -> Unit,
): Modifier = this then MarkOnPointerElement(marking, listState, onMark)

private data class MarkOnPointerElement(
    val marking: PopupMarking,
    val listState: LazyListState,
    val onMark: (item: Int) -> Unit,
) : ModifierNodeElement<MarkOnPointerNode>() {
    override fun create() = MarkOnPointerNode(marking, listState, onMark)

    override fun update(node: MarkOnPointerNode) {
        node.marking = marking
        node.listState = listState
        node.onMark = onMark
    }
}

private class MarkOnPointerNode(
    var marking: PopupMarking,
    var listState: LazyListState,
    var onMark: (item: Int) -> Unit,
) : Modifier.Node(),
    PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Main || pointerEvent.type != PointerEventType.Move) return
        val at = pointerEvent.changes.first().position
        marking.pointerAt(at)
        if (!marking.pointerMoved) return
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { at.y >= it.offset && at.y < it.offset + it.size }
            ?.let { onMark(it.index) }
    }

    override fun onCancelPointerInput() = Unit
}

/** As [markOnPointer], the list's items being [items], in order: the one under the pointer marked by [onMark]. */
internal fun <T> Modifier.markOnPointer(
    marking: PopupMarking,
    listState: LazyListState,
    items: List<T>,
    onMark: (T) -> Unit,
): Modifier = markOnPointer(marking, listState) { item -> items.getOrNull(item)?.let(onMark) }

/**
 * The row [index] of [listState]'s list brought into view at once ([revealItem]) when the keys marked it last: the
 * pointer's row is where the pointer is. Once the list is laid out.
 */
internal suspend fun PopupMarking.reveal(
    listState: LazyListState,
    index: Int,
) {
    if (!pointerMoved && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) listState.revealItem(index)
}

/**
 * A popup row marked by the pointer as a menu row is: [onMark] when the pointer moves over it (its hover in
 * [interactionSource]), once the pointer moved in the popup of [marking] (by default the one it is in), and not as a
 * list scrolls under a still pointer.
 */
@Composable
internal fun MarkOnHover(
    interactionSource: InteractionSource,
    marking: PopupMarking? = LocalPopupMarking.current,
    onMark: () -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val moved = marking?.pointerMoved == true
    val currentOnMark by rememberUpdatedState(onMark)
    LaunchedEffect(hovered, moved) { if (hovered && moved) currentOnMark() }
}

/**
 * A dropdown row's focus as a menu row's: the row the pointer moves over takes the focus ([MarkOnHover]). Give
 * it to the row's `focusRequester`.
 */
@Composable
internal fun rememberHoverFocus(interactionSource: InteractionSource): FocusRequester {
    val requester = remember { FocusRequester() }
    MarkOnHover(interactionSource) { requester.requestFocus() }
    return requester
}

/**
 * The background of a popup's row: the one tint of the row marked ([PopupMarking]), at once as the app's lists mark
 * theirs (no fading trail under held arrows), in every popup but Jewel's menus; none for the others, the pointer's row
 * being the marked one once it moved.
 */
@Composable
internal fun markedRowBackground(marked: Boolean): Color =
    if (marked) {
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

/**
 * A dropdown row's background ([DropdownButton]'s rows): the row in focus, the one marked ([markedRowBackground]);
 * [hovered], Zayit's tint as the pointer comes, only while the keys do not mark the rows.
 */
@Composable
internal fun keyMarkedBackground(
    focused: Boolean,
    hovered: Color,
): Color =
    when {
        focused -> markedRowBackground(marked = true)
        LocalPopupMarking.current?.byKeys == true -> Color.Transparent
        else -> hovered
    }

/**
 * The rows of a column scrolled by [scrollState] come into view at once when they ask (the row the keys move the focus
 * to), as the app's lists reveal theirs ([revealItem]), not by the scroll's animation: on the column, inside its scroll,
 * it answers its rows' requests itself; a row the pointer gave the focus to ([marking]) stays where it is.
 */
internal fun Modifier.revealAtOnce(
    scrollState: ScrollState,
    marking: PopupMarking? = null,
): Modifier = this then RevealAtOnceElement(scrollState, marking)

private data class RevealAtOnceElement(
    val scrollState: ScrollState,
    val marking: PopupMarking?,
) : ModifierNodeElement<RevealAtOnceNode>() {
    override fun create() = RevealAtOnceNode(scrollState, marking)

    override fun update(node: RevealAtOnceNode) {
        node.scrollState = scrollState
        node.marking = marking
    }
}

private class RevealAtOnceNode(
    var scrollState: ScrollState,
    var marking: PopupMarking?,
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

/**
 * Jewel's [PopupMenu] (the Tools menu, a tab's, a card's) marking one item at a time, as IntelliJ's own menus do: in
 * Jewel's Swing compatibility mode (the app's theme again, as Zayit's dialogs take it, `swingCompatMode` on) an item is
 * tinted only while in focus, which the pointer's hover gives it (Jewel's MenuItem), and not again under a still pointer
 * once the arrows moved on; Jewel's own mode tints the hovered item too, in the same colour
 * (FocusableComponentState.chooseValue), so that two items were marked (2026-11-20).
 */
@Composable
internal fun SingleMarkPopupMenu(
    onDismissRequest: (InputMode) -> Boolean,
    popupPositionProvider: PopupPositionProvider,
    style: MenuStyle = JewelTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    SingleMarkMenus {
        PopupMenu(onDismissRequest = onDismissRequest, popupPositionProvider = popupPositionProvider, style = style, content = content)
    }
}

/**
 * Jewel's menus drawn in [content] marking one item at a time ([SingleMarkPopupMenu]): the app's theme again, in Jewel's
 * Swing compatibility mode; the text's own menu too (BookContentScreen's), request 74 of stage 3.
 */
@Composable
internal fun SingleMarkMenus(content: @Composable () -> Unit) {
    IntUiTheme(theme = ThemeUtils.buildThemeDefinition(), styling = ThemeUtils.buildComponentStyling(), swingCompatMode = true) {
        content()
    }
}

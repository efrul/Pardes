package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.presentation.window.FocusStop
import io.github.kdroidfilter.seforimapp.core.presentation.window.focusStops
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Pardes (request 47 of docs/DESKTOP_STAGE3_PLAN.md: "מקשים בעץ הספרים, איך מקובל בעורכי קוד"): the keys of the book
// tree and of the table of contents as the IntelliJ project view and VS Code's explorer have them, right to left. The
// arrows up and down move a row of the keys' own, shown as the row selected while the tree has the keyboard's focus, as
// a pane's search shows its match (the user's word: "הפוקוס יראה כמו במקומות שהוא קיים, כמו בתוכן העניינים לאחר חיפוש
// בו"); the arrow a closed folder's chevron points to opens it, or goes on to the next row, and the other arrow closes
// it, or goes up to the parent (from the top level, to the nearest open folder above); Enter clicks the row; Home and
// End, Page Up and Page Down; Escape gives the focus back to the text. The arrows as IntelliJ's own code has them
// (TreeAction, read on 2026-11-20 at the user's word: "IntelliJ לפי התקן שלהם תעמיק בו ותיישם").

/** A row of a tree as its keys see it: its depth, and whether it is a folder, one that opens, and open. */
@Immutable
data class TreeNode(
    val level: Int,
    val folder: Boolean = false,
    val open: Boolean = false,
)

/** A row of a tree for its keys: its key in the tree's list, its [node], and its [click] (a folder's opens or closes it). */
class TreeKeyRow(
    val key: Any,
    val node: TreeNode,
    val click: () -> Unit,
)

/** What a key does in a tree. */
internal sealed interface TreeMove {
    /** The keys' row goes to the row [index]. */
    data class Focus(
        val index: Int,
    ) : TreeMove

    /** The row [index] is clicked: a folder opens or closes, a leaf opens. */
    data class Click(
        val index: Int,
    ) : TreeMove
}

/**
 * A key of a tree's own: up, down, into a folder and out of it, Enter, the ends, a page up and down, and the numeric
 * keypad's plus and minus, which open and close a folder as IntelliJ's trees do (request 69 of the list after the third
 * testers' build: "המקשים בעץ - תבדוק שזה באמת תומך כמו בג'טבריינס").
 */
internal enum class TreeKey { UP, DOWN, IN, OUT, ENTER, HOME, END, PAGE_UP, PAGE_DOWN, EXPAND, COLLAPSE }

/**
 * The tree's key [key] is, the view laid out from the right when [rtl] (where a closed folder's chevron points left,
 * so the arrow left goes in); null for any other key.
 */
internal fun treeKeyOf(
    key: Key,
    rtl: Boolean,
): TreeKey? =
    when (key) {
        Key.DirectionUp -> TreeKey.UP
        Key.DirectionDown -> TreeKey.DOWN
        Key.DirectionLeft -> if (rtl) TreeKey.IN else TreeKey.OUT
        Key.DirectionRight -> if (rtl) TreeKey.OUT else TreeKey.IN
        Key.Enter, Key.NumPadEnter -> TreeKey.ENTER
        Key.MoveHome -> TreeKey.HOME
        Key.MoveEnd -> TreeKey.END
        Key.PageUp -> TreeKey.PAGE_UP
        Key.PageDown -> TreeKey.PAGE_DOWN
        Key.NumPadAdd -> TreeKey.EXPAND
        Key.NumPadSubtract -> TreeKey.COLLAPSE
        else -> null
    }

/**
 * What [key] does in a tree of [nodes], the keys on the row [focused] (-1 before any: the first row, or the last for
 * End), [page] rows in view: a row up or down, the first or the last, a page; in, a closed folder opened and an open
 * one's first row; out, an open folder closed and any other row's parent; Enter, the row clicked; the keypad's plus a
 * closed folder opened, its minus an open one closed. Null when it does nothing: no rows, nothing further in or out.
 */
internal fun treeMove(
    nodes: List<TreeNode>,
    focused: Int,
    key: TreeKey,
    page: Int,
): TreeMove? {
    if (nodes.isEmpty()) return null
    val last = nodes.lastIndex
    if (focused !in nodes.indices) return TreeMove.Focus(if (key == TreeKey.END) last else 0)
    return rowMove(focused, last, key, page)?.let { TreeMove.Focus(it) } ?: rowAction(nodes, focused, key)
}

// Where the keys that only move along the rows go from the row [focused], [last] the last row: a row up or down, the
// first or the last, a page; null for the keys that act on the row
private fun rowMove(
    focused: Int,
    last: Int,
    key: TreeKey,
    page: Int,
): Int? =
    when (key) {
        TreeKey.UP -> (focused - 1).coerceAtLeast(0)
        TreeKey.DOWN -> (focused + 1).coerceAtMost(last)
        TreeKey.HOME -> 0
        TreeKey.END -> last
        TreeKey.PAGE_UP -> (focused - page).coerceAtLeast(0)
        TreeKey.PAGE_DOWN -> (focused + page).coerceAtMost(last)
        else -> null
    }

// What the keys that act on the row [focused] do: Enter clicks it; in, out, the keypad's plus and minus open or close
// its folder, or go on to the row they lead to; null when there is nothing further, and for the keys that only move
private fun rowAction(
    nodes: List<TreeNode>,
    focused: Int,
    key: TreeKey,
): TreeMove? {
    val node = nodes[focused]
    val closed = node.folder && !node.open
    val open = node.folder && node.open
    return when (key) {
        TreeKey.ENTER -> TreeMove.Click(focused)
        // IntelliJ's (TreeAction.selectChild): a closed folder opens, an open one or a leaf goes on to the next row
        TreeKey.IN -> if (closed) TreeMove.Click(focused) else TreeMove.Focus(focused + 1).takeIf { focused < nodes.lastIndex }
        // IntelliJ's (TreeAction.selectParent): an open folder closes, a row goes up to its parent, and a row at the top
        // level, which has none, to the nearest open folder above it, else to the first row
        TreeKey.OUT -> if (open) TreeMove.Click(focused) else outOf(nodes, focused)?.let { TreeMove.Focus(it) }
        TreeKey.EXPAND -> TreeMove.Click(focused).takeIf { closed }
        TreeKey.COLLAPSE -> TreeMove.Click(focused).takeIf { open }
        else -> null
    }
}

// Where the arrow out goes from the row [focused], no open folder: up to its parent, or from the top level, which has
// none, to the nearest open folder above it (IntelliJ's TreeAction.selectParent), else to the first row
private fun outOf(
    nodes: List<TreeNode>,
    focused: Int,
): Int? {
    val level = nodes[focused].level
    return (focused - 1 downTo 0).firstOrNull { nodes[it].level < level }
        ?: (focused - 1 downTo 0).firstOrNull { nodes[it].folder && nodes[it].open }
        ?: 0.takeIf { focused > 0 }
}

/**
 * Where the keys of a list of [count] rows go from the row [index] for Home, End, Page Up and Page Down, [page] rows a
 * view; null for any other key: the lists whose arrows are their own (the search results) take these from the tree.
 */
internal fun listMove(
    count: Int,
    index: Int,
    key: Key,
    page: Int,
): Int? {
    if (key !in PAGE_KEYS) return null
    val move = treeMove(List(count) { TreeNode(0) }, index, treeKeyOf(key, rtl = true) ?: return null, page)
    return (move as? TreeMove.Focus)?.index
}

private val PAGE_KEYS = setOf(Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown)

/**
 * The book tree's key of its row selected: the book's ([bookOverride], else the selected book's), else the category's
 * ([categoryOverride], else the selected category's); null with neither.
 */
fun NavigationState.treeSelectedKey(
    bookOverride: Long? = null,
    categoryOverride: Long? = null,
): String? =
    (bookOverride ?: selectedBook?.id)?.let { "book_$it" }
        ?: (categoryOverride ?: selectedCategory?.id)?.let { "category_$it" }

/**
 * The rows a page of [this] list moves: those in view but some kept in it, as IntelliJ's trees page (the view's height
 * less four rows, TreeAction.pageDown), at least one.
 */
internal fun LazyListState.pageRows(): Int = (layoutInfo.visibleItemsInfo.size - PAGE_KEPT_ROWS).coerceAtLeast(1)

// The rows a page keeps in view, IntelliJ's four, one of them the row only partly in view
private const val PAGE_KEPT_ROWS = 4

/**
 * A tree's keys: the row they are on and whether the tree has the keyboard's focus. [rememberTreeKeys] keeps it on the
 * row selected as that changes.
 */
@Stable
class TreeKeys internal constructor(
    @StructuredScope private val scope: CoroutineScope,
) {
    internal val focusRequester = FocusRequester()

    // The tree as a part the window's focus keys stop at (request 100): Shift with the arrow left or right, F6
    internal val stop = FocusStop { focusRequester.requestFocus() }

    /** The key of the row the keys are on, null before any. */
    var focused: Any? by mutableStateOf(null)
        internal set

    internal var hasFocus by mutableStateOf(false)
    internal var rtl = true
    internal var rows: List<TreeKeyRow> = emptyList()

    /**
     * The key of the row the tree shows as selected in place of its selection while it has the keyboard's focus: the
     * keys' row, as a pane's search shows its match; null otherwise.
     */
    val shown: Any? get() = focused.takeIf { hasFocus }

    // A key pressed in the tree: its move made, the row revealed in [listState]'s list
    internal fun onKey(
        event: KeyEvent,
        listState: LazyListState,
        onLeave: (() -> Unit)?,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isShiftPressed) return false
        if (event.key == Key.Escape) {
            onLeave?.invoke() ?: return false
            return true
        }
        val key = treeKeyOf(event.key, rtl) ?: return false
        // Ctrl/Cmd with Enter opens in a new tab, as with a click; with the other keys, they are the window's
        if ((event.isCtrlPressed || event.isMetaPressed) && key != TreeKey.ENTER) return false
        val rows = rows
        val move = treeMove(rows.map { it.node }, rows.indexOfFirst { it.key == focused }, key, listState.pageRows())
        when (move) {
            is TreeMove.Focus -> {
                focused = rows[move.index].key
                scope.launch { listState.revealItem(move.index) }
            }
            is TreeMove.Click -> {
                val row = rows[move.index]
                row.click()
                if (key == TreeKey.ENTER && !row.node.folder) onLeave?.invoke()
            }
            null -> Unit
        }
        return true
    }

    // A press at [y] pixels down the list: on a folder's row, the tree takes the keyboard's focus and the keys that row
    internal fun onPress(
        y: Float,
        listState: LazyListState,
    ) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return
        val row = rows.firstOrNull { it.key == item.key } ?: return
        if (!row.node.folder) return
        focused = row.key
        focusRequester.requestFocus()
    }
}

/**
 * A tree's keys, on the row [selected] (its key in the list) whenever that changes; the tree takes the keyboard's
 * focus once drawn when its pane was just opened ([PaneFocusRequests], by [LocalPaneFocusKey]).
 */
@Composable
fun rememberTreeKeys(selected: Any?): TreeKeys {
    val scope = rememberCoroutineScope()
    val keys = remember(scope) { TreeKeys(scope) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    SideEffect { keys.rtl = rtl }
    LaunchedEffect(keys, selected) { if (selected != null) keys.focused = selected }
    // Only in a reader's pane, which has the graph (a test of the tree alone has neither)
    val focusKey = LocalPaneFocusKey.current
    if (focusKey != null) {
        val stops = LocalOpenWindow.current.focusStops
        DisposableEffect(keys, stops) {
            stops.add(keys.stop)
            onDispose { stops.remove(keys.stop) }
        }
        val requests = LocalAppGraph.current.paneFocusRequests
        LaunchedEffect(keys, focusKey) {
            requests.await(focusKey)
            // Once the tree is laid out, its focus requester attached
            withFrameNanos { }
            runCatching { keys.focusRequester.requestFocus() }
        }
    }
    return keys
}

/** The pane a tree is drawn in, by its window and its own id ("<window>:<pane>"), for [PaneFocusRequests]; none outside one. */
val LocalPaneFocusKey = staticCompositionLocalOf<String?> { null }

/**
 * The panes asked to give their tree the keyboard's focus once drawn: a window's book tree or contents just opened by
 * the user (its button, Ctrl/Cmd+B), as a code editor's tree takes the focus as it opens; by "<window>:<pane>", each
 * request once.
 */
@Inject
@SingleIn(AppScope::class)
class PaneFocusRequests {
    private val pending = MutableStateFlow<Set<String>>(emptySet())

    /** The panes of these [keys] were just opened. */
    fun opened(keys: Collection<String>) {
        if (keys.isNotEmpty()) pending.update { it + keys }
    }

    // Waits for a request for [key], and takes it
    internal suspend fun await(key: String) {
        pending.first { key in it }
        pending.update { it - key }
    }
}

/**
 * The keys ([keys]) of a tree drawn in this box by [listState]'s list, its [rows] in the list's order ([treeMove]): the
 * keys' row revealed as it moves, a row clicked by Enter (with Ctrl/Cmd as a click with them: a new tab), and
 * [onLeave] for Escape and after Enter opened a leaf, to give the focus back to the text (none: Escape is not the
 * tree's). A press on a folder's row gives the tree the keyboard's focus, on that row; on a leaf's, the focus stays
 * where it is, for the text it opens.
 */
fun Modifier.treeKeys(
    keys: TreeKeys,
    listState: LazyListState,
    rows: List<TreeKeyRow>,
    onLeave: (() -> Unit)? = null,
): Modifier {
    keys.rows = rows
    return onGloballyPositioned { keys.stop.bounds = it.boundsInWindow() }
        .focusRequester(keys.focusRequester)
        .onFocusChanged {
            keys.hasFocus = it.hasFocus
            keys.stop.focused = it.hasFocus
        }.onPreviewKeyEvent { keys.onKey(it, listState, onLeave) }
        .onKeyEvent(keys.stop::key)
        .focusable()
        .pointerInput(keys, listState) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) event.changes.firstOrNull()?.let { keys.onPress(it.position.y, listState) }
                }
            }
        }
}

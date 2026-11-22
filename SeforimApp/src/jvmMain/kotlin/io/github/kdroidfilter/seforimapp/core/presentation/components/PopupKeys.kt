package io.github.kdroidfilter.seforimapp.core.presentation.components

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.pageRows

// Pardes (request 69 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md: "צריך תמיכה מלאה במקשים,
// כל תיבה שנפתחת תתמך בזה... היסטוריה והערות מהשורה למעלה"): the title bar's popups (the tab search and its history,
// the favorites, the notes) answer the keys as the breadcrumb's popup does: up and down mark a row, Enter opens it,
// Escape closes the popup. How the rows are marked, by the keys or the pointer, is PopupMarking.kt's.

/** A row of a popup's list the keys can mark: its place among the list's items ([item], headers counted) and [open]. */
internal class PopupKeyRow(
    val item: Int,
    val open: () -> Unit,
)

/**
 * The row marked in a popup's list whose field keeps the keyboard's focus: [index] among the [rows] that open something,
 * in their order, the first at first; the keys' row or the pointer's ([marking]), the list's [listState] keeping the
 * keys' in view.
 */
@Stable
internal class PopupKeys(
    val listState: LazyListState,
) {
    var index by mutableIntStateOf(0)
        private set

    /** Whether the keys or the pointer marked the row last. */
    val marking = PopupMarking()

    /** The rows that open something, in their order; set as the list is built. */
    var rows: List<PopupKeyRow> = emptyList()

    /** Whether the row [row] (its place among [rows]) is the one marked. */
    fun marks(row: Int): Boolean = row == index

    // Round from the last row to the first and back, as every popup of the app (2026-11-20: "תוודא שבכולם")
    internal fun move(by: Int) {
        marking.keysMoved()
        if (rows.isNotEmpty()) index = (index + by).mod(rows.size)
    }

    /** The pointer moved over the row [row]: it is the one marked, the keys going on from it. */
    internal fun point(row: Int) {
        if (row in rows.indices) index = row
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
        keys.rows.getOrNull(keys.index)?.let { keys.marking.reveal(listState, it.item) }
    }
    return keys
}

/**
 * Up and down mark a row of [keys], Page Up and Down a view of them, Enter opens it, Escape closes the popup
 * ([onEscape]), before its field takes them. The pointer marks its rows by [markOnPointer], on the popup's list.
 */
internal fun Modifier.popupKeys(
    keys: PopupKeys,
    onEscape: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> keys.move(1)
            Key.DirectionUp -> keys.move(-1)
            Key.PageDown, Key.PageUp -> keys.page(forward = event.key == Key.PageDown)
            Key.Enter, Key.NumPadEnter -> keys.open()
            Key.Escape -> onEscape()
            else -> return@onPreviewKeyEvent false
        }
        true
    }

// A view of rows down or up, as the trees and lists page, stopping at the list's ends (Pardes, request 77 of stage 3)
private fun PopupKeys.page(forward: Boolean) {
    if (rows.isEmpty()) return
    val by = listState.pageRows()
    move(((if (forward) index + by else index - by).coerceIn(0, rows.lastIndex)) - index)
}

/**
 * A dropdown's popup (DropdownButton): up and down move the focus from row to row (the first row from none), Escape
 * closes it ([onEscape]); Enter and Space click the row in focus, as a clickable row takes them (the home's catalog
 * dropdowns, request 69 of stage 3: "כמו למשל הטאבים של הקטגוריות במסך הבית"). The keys mark the rows from then on
 * ([marking]).
 */
internal fun Modifier.dropdownKeys(
    focusManager: FocusManager,
    marking: PopupMarking? = null,
    // Whether the popup's frame, no row, has the focus: the focus rounding the last row comes there first
    frameFocused: () -> Boolean = { false },
    onEscape: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown, Key.DirectionUp -> {
                marking?.keysMoved()
                val direction = if (event.key == Key.DirectionDown) FocusDirection.Next else FocusDirection.Previous
                focusManager.moveFocus(direction)
                // From the last row to the first and back at once, not by the frame, which marks nothing
                if (frameFocused()) focusManager.moveFocus(direction)
            }
            Key.Escape -> onEscape()
            else -> return@onPreviewKeyEvent false
        }
        true
    }

/** The list of [keys]' popup marks its row under the pointer ([markOnPointer]): rows that open nothing are passed over. */
internal fun Modifier.markOnPointer(keys: PopupKeys): Modifier =
    markOnPointer(keys.marking, keys.listState) { item ->
        keys.rows
            .indexOfFirst { it.item == item }
            .takeIf { it >= 0 }
            ?.let(keys::point)
    }

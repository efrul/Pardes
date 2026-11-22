package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.listMove
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

// Pardes (request 55 of docs/DESKTOP_STAGE3_PLAN.md): the results pane's list, as a book's find and a PDF book's lay it
// (FindResultsPane.kt), and its keys.

/**
 * The results' [count] over their [rows], as a book's and a PDF book's lists lay them: the list takes the keys ([onKey])
 * and the [focus], each row drawn by [row].
 */
@Composable
internal fun FoundList(
    count: String,
    rows: List<FindRow>,
    focus: FoundListFocus,
    onKey: (KeyEvent) -> Boolean,
    row: @Composable (FindRow) -> Unit,
) {
    Column {
        Text(count, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), color = JewelTheme.globalColors.text.info, fontSize = 12.sp)
        LaunchedEffect(focus.refocus) {
            if (focus.refocus) runCatching { focus.requester.requestFocus() }
            focus.onRefocused()
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focus.requester)
                .onPreviewKeyEvent(onKey)
                .focusable(),
            state = focus.listState,
        ) {
            items(rows.size) { index -> row(rows[index]) }
        }
    }
}

/** The row [index] brought into view when it is out of it; -1, none. */
internal suspend fun LazyListState.keepInView(index: Int) {
    if (index >= 0 && layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

/**
 * What a key does in a results' list of [count] rows, the row [at] marked (-1: none), [page] rows a view: Enter and
 * Escape give the focus back to the text ([onLeave]), as the book tree's, and the moves of [findListMove] go to the row
 * they reach ([onMove]); whether the key was taken.
 */
internal fun findListKey(
    event: KeyEvent,
    count: Int,
    at: Int,
    page: Int,
    onLeave: () -> Unit,
    onMove: (Int) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || count == 0) return false
    if (event.key in LEAVE_KEYS) {
        onLeave()
        return true
    }
    onMove(findListMove(event.key, count, at, page) ?: return false)
    return true
}

/** Where up, down, Home, End and a page up and down go in a list of [count] from [at] (-1 for none); null for another key. */
internal fun findListMove(
    key: Key,
    count: Int,
    at: Int,
    page: Int,
): Int? =
    when (key) {
        Key.DirectionDown -> (at + 1).coerceAtMost(count - 1)
        Key.DirectionUp -> (at - 1).coerceAtLeast(0)
        else -> listMove(count, at.coerceAtLeast(0), key, page)
    }

// The keys that give the focus back to the text, as the book tree's
private val LEAVE_KEYS = setOf(Key.Enter, Key.NumPadEnter, Key.Escape)

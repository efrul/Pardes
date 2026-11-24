package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.MarkOnHover
import io.github.kdroidfilter.seforimapp.core.presentation.components.PopupKeyRow
import io.github.kdroidfilter.seforimapp.core.presentation.components.PopupKeys
import io.github.kdroidfilter.seforimapp.core.presentation.components.PopupSearchField
import io.github.kdroidfilter.seforimapp.core.presentation.components.notePointer
import io.github.kdroidfilter.seforimapp.core.presentation.components.popupKeys
import io.github.kdroidfilter.seforimapp.core.presentation.components.rememberPopupKeys
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.tab_search_placeholder
import kotlin.math.ceil

// Pardes (request 83 of docs/DESKTOP_STAGE3_PLAN.md, the hidden tabs' list narrowed by what is typed, as IntelliJ's
// list of hidden tabs is): a field over the list of the tabs the strip has no room for. The list answers the keys as
// every popup list of the app does (PopupKeys.kt, the user's word of 2026-11-24: "אין תמיכת מקשים ל"עוד" בכרטיסיות"):
// up and down mark a row, Page Up and Down a view of them, Enter opens it, and the pointer marks the row it moves over.

/**
 * The list's width: a book's name with its place whole ("שולחן ערוך, אורח חיים - סימן רנג"), where a tab's own width,
 * Zayit's for this list, showed the tabs of one book alike.
 */
internal val HiddenTabsListWidth = 280.dp

/** What is typed over the hidden tabs' list. */
@Stable
internal class HiddenTabsFilter {
    var query by mutableStateOf("")
    val focus = FocusRequester()
}

@Composable
internal fun rememberHiddenTabsFilter(): HiddenTabsFilter = remember { HiddenTabsFilter() }

/**
 * Those of [tabs] one of whose names ([names]: its title, and the short one a narrow tab shows) holds what is typed, in
 * their order: all of them while nothing is.
 */
@Composable
internal fun <T> HiddenTabsFilter.matching(
    tabs: List<T>,
    names: @Composable (T) -> List<String>,
): List<T> {
    val labels = tabs.map { names(it) }
    return remember(tabs, labels, query) { matchingTabs(tabs, labels, query) }
}

/**
 * Those of [tabs] one of whose names, at the same place in [labels], holds [query], nikud, quote marks and letter case
 * aside (`שוע` finds "שו"ע"): all of them for a blank query.
 */
internal fun <T> matchingTabs(
    tabs: List<T>,
    labels: List<List<String>>,
    query: String,
): List<T> {
    val typed = query.searchKey()
    if (typed.isEmpty()) return tabs
    return tabs.filterIndexed { index, _ -> labels[index].any { it.searchKey().contains(typed, ignoreCase = true) } }
}

/**
 * The keys of the hidden tabs' list: the app's popup keys ([popup]) over Zayit's column of rows, which is no lazy list,
 * its scroll ([scroll]) keeping the keys' row in view. The rows are known by name, a tab's key or [HIDDEN_TABS_ADD_ROW].
 */
@Stable
internal class HiddenTabsKeys(
    val popup: PopupKeys,
    val scroll: ScrollState,
) {
    /** The rows' names in their order, the tabs first; set as the list is built. */
    var names: List<String> = emptyList()

    // How many of the rows are tabs, in the scrolled column: the + row stands under it.
    internal var tabRows = 0

    /** Whether the row [name] is the one marked. */
    fun marks(name: String): Boolean = popup.marks(names.indexOf(name))

    /** The pointer moved over the row [name]. */
    fun point(name: String) = popup.point(names.indexOf(name))

    // A view of rows down or up, as the app's lists page (TreeKeys' pageRows), stopping at the list's ends.
    internal fun page(forward: Boolean) {
        if (names.isEmpty()) return
        val by = pageRows(scroll.viewportSize, rowHeight())
        popup.move(((if (forward) popup.index + by else popup.index - by).coerceIn(0, names.lastIndex)) - popup.index)
    }

    // The tabs' rows are one height: the column's own, shared among them.
    private fun rowHeight(): Float = if (tabRows == 0) 0f else (scroll.maxValue + scroll.viewportSize).toFloat() / tabRows

    /** The marked row brought into view by the least scroll, when it is a tab's (the + row is always in view). */
    internal suspend fun reveal() {
        val target = revealScroll(popup.index, tabRows, rowHeight(), scroll.value, scroll.viewportSize) ?: return
        scroll.scrollTo(target)
    }
}

/** The name of the list's + row among its rows ([HiddenTabsKeys.names]), which no tab's key is. */
internal const val HIDDEN_TABS_ADD_ROW = "+"

/** The keys of the list a row is in; null out of one. */
internal val LocalHiddenTabsKeys = staticCompositionLocalOf<HiddenTabsKeys?> { null }

/**
 * The keys of this list, its rows being [tabs] in their order (each known by [name], opened by [onOpen]) and the + row
 * when the list has one ([onAdd]); on the first row again when what is typed changes. [scroll] is the scroll of the
 * column of the tabs' rows.
 */
@Composable
internal fun <T> HiddenTabsFilter.keys(
    scroll: ScrollState,
    tabs: List<T>,
    name: (T) -> String,
    onOpen: (T) -> Unit,
    onAdd: (() -> Unit)?,
): HiddenTabsKeys {
    val popup = rememberPopupKeys(query)
    val keys = remember(popup, scroll) { HiddenTabsKeys(popup, scroll) }
    keys.names = tabs.map(name) + listOfNotNull(HIDDEN_TABS_ADD_ROW.takeIf { onAdd != null })
    keys.tabRows = tabs.size
    popup.rows = tabs.mapIndexed { row, tab -> PopupKeyRow(row) { onOpen(tab) } } + listOfNotNull(onAdd?.let { PopupKeyRow(tabs.size, it) })
    LaunchedEffect(keys, popup.index) { if (popup.marking.byKeys) keys.reveal() }
    return keys
}

/** The rows a page moves in a list [viewport] high of rows [rowHeight] high: those in view but four, at least one. */
internal fun pageRows(
    viewport: Int,
    rowHeight: Float,
): Int = if (rowHeight <= 0f) 1 else ((viewport / rowHeight).toInt() - PAGE_KEPT_ROWS).coerceAtLeast(1)

// The rows a page keeps in view, as the app's lists and IntelliJ's trees (TreeKeys.kt)
private const val PAGE_KEPT_ROWS = 4

/**
 * Where a column scrolled to [scrolled], [viewport] high, goes to show its row [row] of [rows] rows [rowHeight] high, by
 * the least scroll; null when the row is in view already, or is none of the column's.
 */
internal fun revealScroll(
    row: Int,
    rows: Int,
    rowHeight: Float,
    scrolled: Int,
    viewport: Int,
): Int? {
    if (row !in 0 until rows) return null
    val top = row * rowHeight
    val bottom = top + rowHeight
    return when {
        top < scrolled -> top.toInt()
        bottom > scrolled + viewport -> ceil(bottom - viewport).toInt()
        else -> null
    }
}

/** The list's column, as Zayit's: its rows reach [keys] ([hiddenTabMarked]), and the pointer's moves over it are noted. */
@Composable
internal fun HiddenTabsColumn(
    keys: HiddenTabsKeys,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalHiddenTabsKeys provides keys) {
        Column(modifier.notePointer(keys.popup.marking), content = content)
    }
}

/**
 * Whether the list's row [name] is the one marked, by the keys or by the pointer moved over it ([interactionSource]),
 * one row at a time as a menu marks its own; out of the list, whether the pointer is over it. It stands for Zayit's
 * `hovered`: the marked row has its tint, and its close button.
 */
@Composable
internal fun hiddenTabMarked(
    name: String,
    interactionSource: InteractionSource,
): Boolean {
    val hovered by interactionSource.collectIsHoveredAsState()
    val keys = LocalHiddenTabsKeys.current ?: return hovered
    MarkOnHover(interactionSource, keys.popup.marking) { keys.point(name) }
    return keys.marks(name)
}

/** The list's popup's key handler: Escape closes it ([onClose]), which the list's field would keep from the popup. */
internal fun hiddenTabsEscape(onClose: () -> Unit): (KeyEvent) -> Boolean =
    { event ->
        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
        if (escape) onClose()
        escape
    }

/**
 * The list's field, which takes the keys once the list opens: those of [keys] go to the list's rows (Enter opens the
 * marked one, the first tab left until another is marked), the others type in the field.
 */
@Composable
internal fun HiddenTabsFilterField(
    filter: HiddenTabsFilter,
    keys: HiddenTabsKeys,
) {
    LaunchedEffect(filter) { filter.focus.requestFocus() }
    Box(
        Modifier
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)
            .onPreviewKeyEvent { event ->
                val page = event.type == KeyEventType.KeyDown && (event.key == Key.PageDown || event.key == Key.PageUp)
                if (page) keys.page(forward = event.key == Key.PageDown)
                page
                // Escape is the popup's own (hiddenTabsEscape), ahead of this
            }.popupKeys(keys.popup, onEscape = {}),
    ) {
        PopupSearchField(
            query = filter.query,
            onQueryChange = { filter.query = it },
            placeholder = stringResource(Res.string.tab_search_placeholder),
            focusRequester = filter.focus,
        )
    }
}

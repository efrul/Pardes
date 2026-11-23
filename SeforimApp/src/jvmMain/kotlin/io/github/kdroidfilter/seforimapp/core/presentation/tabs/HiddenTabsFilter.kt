package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.PopupSearchField
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.tab_search_placeholder

// Pardes (request 83 of docs/DESKTOP_STAGE3_PLAN.md, the hidden tabs' list narrowed by what is typed, as IntelliJ's
// list of hidden tabs is): a field over the list of the tabs the strip has no room for.

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

/** The list's popup's key handler: Escape closes it ([onClose]), which the list's field would keep from the popup. */
internal fun hiddenTabsEscape(onClose: () -> Unit): (KeyEvent) -> Boolean =
    { event ->
        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
        if (escape) onClose()
        escape
    }

/** The list's field, which takes the keys once the list opens; Enter goes to the first tab left ([onEnter]). */
@Composable
internal fun HiddenTabsFilterField(
    filter: HiddenTabsFilter,
    onEnter: () -> Unit,
) {
    LaunchedEffect(filter) { filter.focus.requestFocus() }
    Box(
        Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp).onPreviewKeyEvent { event ->
            val enter = event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)
            if (enter) onEnter()
            enter
        },
    ) {
        PopupSearchField(
            query = filter.query,
            onQueryChange = { filter.query = it },
            placeholder = stringResource(Res.string.tab_search_placeholder),
            focusRequester = filter.focus,
        )
    }
}

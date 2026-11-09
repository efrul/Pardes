package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.github.kdroidfilter.seforimapp.features.home.widgets.DefaultToolWindow
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph

/** Whether the dictionary is drawn in its own window, the Tools menu's; on the Home it is a card among others. */
internal val LocalDictionaryWindow = staticCompositionLocalOf { false }

/** The dictionary's own window: every tool's, where its field takes the focus (see [focusedInItsWindow]). */
@Composable
internal fun DictionaryToolWindow(
    state: HomeWidgetsState,
    onClose: () -> Unit,
) = CompositionLocalProvider(LocalDictionaryWindow provides true) { DefaultToolWindow(DictionaryWidget, state, onClose) }

/**
 * The focus on the dictionary's field in its own window, ready to type in: when the window opens, each time it is asked
 * for again, and back from an entry's page. On the Home the card takes no focus by itself.
 */
@Composable
internal fun Modifier.focusedInItsWindow(): Modifier {
    if (!LocalDictionaryWindow.current) return this
    val focus = remember { FocusRequester() }
    val reopened = LocalAppGraph.current.toolWindows.reopened[DictionaryWidget.id] ?: 0
    LaunchedEffect(reopened) { focus.requestFocus() }
    return focusRequester(focus)
}

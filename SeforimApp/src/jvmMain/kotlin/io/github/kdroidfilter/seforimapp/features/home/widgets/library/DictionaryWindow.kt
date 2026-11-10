package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.lettersOnly
import io.github.kdroidfilter.seforimapp.features.home.widgets.DefaultToolWindow
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.ToolWindows
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph

// A word, or an expression of a few (קל וחומר, מה נפשך): a longer selection is no entry of a dictionary
private const val MAX_WORDS = 4
private val SPACES = Regex("\\s+")

/** [selection] as the dictionary is asked for it, its words alone; null when it is empty or longer than an entry. */
internal fun lookupWord(selection: String): String? {
    val words = lettersOnly(selection).split(SPACES).filter { it.isNotEmpty() }
    return words.joinToString(" ").takeIf { words.size in 1..MAX_WORDS }
}

// The letters a word takes before it in a text (וה, בכל, מש) and the Aramaic ד; at most two are dropped
private const val PREFIXES = "והבכלמשד"
private const val MOST_PREFIXES = 2

/**
 * [query]'s entries, as [lookup]; a word no entry begins with is looked up as it stands in a text, without the letters
 * before it (`והלל`: הלל, `דאמר`: אמר): the fewest dropped that leave a word of the dictionaries.
 */
internal fun lookupAsRead(
    entries: List<DictionaryEntry>,
    query: String,
): List<DictionaryEntry> {
    val found = lookup(entries, query)
    if (found.isNotEmpty()) return found
    var word = normalizeWord(query)
    repeat(MOST_PREFIXES) {
        if (word.length < 2 || word[0] !in PREFIXES) return found
        word = word.drop(1)
        if (entries.any { it.key == word }) return lookup(entries, word)
    }
    return found
}

/** The dictionary's window opened on a word from outside it (the reader's menu), which waits here for its field. */
@Inject
@SingleIn(AppScope::class)
class DictionaryLookup(
    private val toolWindows: ToolWindows,
) {
    internal var word by mutableStateOf<String?>(null)

    /** Opens the dictionary's window, or brings it back, with [word] in its field. */
    fun open(word: String) {
        this.word = word
        toolWindows.open(DictionaryWidget)
    }
}

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

/** In the dictionary's own window, the word it was opened on from outside handed to [onWord], once. */
@Composable
internal fun LookupAskedFor(onWord: (String) -> Unit) {
    if (!LocalDictionaryWindow.current) return
    val lookup = LocalAppGraph.current.dictionaryLookup
    val currentOnWord by rememberUpdatedState(onWord)
    LaunchedEffect(lookup.word) {
        lookup.word?.let { currentOnWord(it) }
        lookup.word = null
    }
}

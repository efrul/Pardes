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
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.home.widgets.DefaultToolWindow
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.ToolWindows
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
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

// The letters a word takes before it in a text (וה, בכל, מש) and the Aramaic ד; at most two are dropped, down to a word
// of three letters (shorter, what is left of והלל is לל, the Aramaic night)
private const val PREFIXES = "והבכלמשד"
private const val MOST_PREFIXES = 2
private const val LEAST_WORD = 3

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
        if (word.length <= LEAST_WORD || word[0] !in PREFIXES) return found
        word = word.drop(1)
        if (entries.any { it.key == word }) return lookup(entries, word)
    }
    return found
}

/**
 * What of [selection] (as [lookupWord] gives it) the dictionaries' [entries] have: the selection itself, a word or an
 * expression (קל וחומר), else the first of its words they have, each found as the window finds it ([lookupAsRead]).
 * Null when they have none of it.
 */
internal fun entryIn(
    entries: List<DictionaryEntry>,
    selection: String,
): String? = (listOf(selection) + selection.split(' ')).firstOrNull { lookupAsRead(entries, it).isNotEmpty() }

/**
 * The dictionary's window opened on a word from outside it (the reader's menu), which waits here for its field; and
 * what the dictionaries have of a selection, for the menu to offer the window only for that.
 */
@Inject
@SingleIn(AppScope::class)
class DictionaryLookup(
    private val toolWindows: ToolWindows,
    private val appSettings: AppSettings,
) {
    internal var word by mutableStateOf<String?>(null)

    // The dictionaries searched and their entries, once read for the menus (read); null until then
    @Volatile
    private var known: Pair<List<Dictionary>, List<DictionaryEntry>>? = null

    /** Opens the dictionary's window, or brings it back, with [word] in its field. */
    fun open(word: String) {
        this.word = word
        toolWindows.open(DictionaryWidget)
    }

    /**
     * What of [selection] the dictionary's window would find ([entryIn]), or null when the dictionaries have none of
     * it. Until they are read ([read]) it is the selection itself, as the menus offered it before they asked.
     */
    fun entryOf(selection: String): String? {
        val (dictionaries, entries) = known ?: return selection
        return if (dictionaries == searched()) entryIn(entries, selection) else selection
    }

    /** Reads the dictionaries searched for [entryOf], once, and again when others are ticked; off the UI thread. */
    internal suspend fun read(graph: AppGraph) {
        val dictionaries = searched()
        if (known?.first == dictionaries) return
        val read = readDictionaries(graph, dictionaries)
        // One that failed to read is left out of the map: asked again at the next selection
        if (read.keys.containsAll(dictionaries)) known = dictionaries to dictionaries.flatMap { read.getValue(it) }
    }

    // The dictionaries ticked in the card's options, as the card takes them: every one when none is
    private fun searched(): List<Dictionary> {
        val ticked = appSettings[DictionarySettings.shown]
        return Dictionary.entries.filter { it in ticked }.ifEmpty { Dictionary.entries }
    }
}

/**
 * Has the dictionaries read for the menus ([DictionaryLookup.entryOf]) once [selection] is one the dictionary is
 * offered for, so that the menu opened on it knows what they have.
 */
@Composable
internal fun DictionaryReadFor(selection: String) {
    val graph = LocalAppGraph.current
    val offered = lookupWord(selection) != null
    LaunchedEffect(offered) { if (offered) graph.dictionaryLookup.read(graph) }
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

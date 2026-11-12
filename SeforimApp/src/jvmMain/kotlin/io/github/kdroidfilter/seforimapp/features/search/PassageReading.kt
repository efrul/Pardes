package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.buildCopyWithSourcePayload
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.deeplink.bookShareLink
import io.github.kdroidfilter.seforimapp.core.hasPunctuation
import io.github.kdroidfilter.seforimapp.core.lettersOnly
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.features.home.widgets.library.DictionaryLookup
import io.github.kdroidfilter.seforimapp.features.home.widgets.library.DictionaryReadFor
import io.github.kdroidfilter.seforimapp.features.home.widgets.library.lookupWord
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jsoup.Jsoup
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.context_menu_copy_letters_only
import seforimapp.seforimapp.generated.resources.context_menu_copy_link
import seforimapp.seforimapp.generated.resources.context_menu_copy_with_source
import seforimapp.seforimapp.generated.resources.context_menu_copy_without_nikud
import seforimapp.seforimapp.generated.resources.context_menu_open_in_dictionary
import seforimapp.seforimapp.generated.resources.context_menu_search_selected_text
import seforimapp.seforimapp.generated.resources.open_book
import java.util.UUID

/**
 * The passage beside the results, read as a book is: more of the book's lines when a scroll goes past one of its ends
 * ([more], [readPastEnds]), its text selected with the mouse under the book's own menu ([PassageText]), and a click on
 * a line that still opens the book there ([passageLine]).
 */
@Stable
internal class PassageReading(
    first: List<Line>,
    @param:StructuredScope @property:StructuredScope private val scope: CoroutineScope,
    // The book's lines from one index to another, both in
    private val bookLines: suspend (from: Int, to: Int) -> List<Line>,
) {
    /** The passage's lines: the result's own, then those read before and after them. */
    var lines: List<Line> by mutableStateOf(first)
        private set

    /** Whether lines were added before the passage's own: the view goes back to the found line by itself no longer. */
    var grownBefore: Boolean by mutableStateOf(false)
        private set

    // The text selected in the passage, as its menu's area reads it
    internal var selectedText = ""

    // The line a selection's press began on, and the line a right click was on (the book opened there)
    internal var pressedLineId: Long? = null
    internal var menuLineId: Long? = null

    // The height of the lines just added before those read, until the scroll takes it: drawn above the view meanwhile
    internal var heldBack by mutableIntStateOf(0)
    private var height = 0
    private var addedBefore = false
    private var loading: Job? = null
    private var noneBefore = false
    private var noneAfter = false

    /** A scroll the passage could not take, at one of its ends: more of the book that way. */
    val pastEnds =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y != 0f) more(before = available.y > 0f)
                return Offset.Zero
            }
        }

    /**
     * Reads [MORE_LINES] more lines of the book [before] the passage or after it, once at a time, until the book's
     * end or [MAX_LINES] lines: further, the book is to be opened.
     */
    fun more(before: Boolean) {
        if (loading?.isActive == true || lines.size >= MAX_LINES || (if (before) noneBefore else noneAfter)) return
        val edge = (if (before) lines.firstOrNull() else lines.lastOrNull())?.lineIndex ?: return
        val (from, to) = if (before) (edge - MORE_LINES).coerceAtLeast(0) to edge - 1 else edge + 1 to edge + MORE_LINES
        if (to < from) return
        loading =
            scope.launch {
                val more = runSuspendCatching { bookLines(from, to) }.getOrDefault(emptyList())
                when {
                    more.isEmpty() -> if (before) noneBefore = true else noneAfter = true
                    before -> {
                        addedBefore = true
                        grownBefore = true
                        lines = more + lines
                    }
                    else -> lines = lines + more
                }
            }
    }

    /**
     * The first and last lines of the text [selected] in the passage, for its source and its link: counted from the line
     * its press began on (or the right click's, which selects a word), down when the selection begins in that line and
     * up when it ends there. Null when neither line holds its text.
     */
    fun linesOf(selected: String): Pair<Line, Line>? {
        val parts = selected.lines().map(::lettersOnly).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return listOfNotNull(pressedLineId, menuLineId).firstNotNullOfOrNull { id ->
            val at = lines.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return@firstNotNullOfOrNull null
            val its = lettersOnly(Jsoup.parse(lines[at].content).text())
            val first =
                when {
                    parts.size == 1 -> at.takeIf { parts[0] in its }
                    its.endsWith(parts.first()) -> at
                    its.startsWith(parts.last()) -> at - parts.lastIndex
                    else -> null
                }
            val from = first?.let(lines::getOrNull)
            val to = first?.let { lines.getOrNull(it + parts.lastIndex) }
            if (from != null && to != null) from to to else null
        }
    }

    /** The lines laid out, [height] high: what those added before the passage took is held back for the scroll. */
    fun laidOut(height: Int) {
        if (addedBefore && this.height > 0) heldBack += (height - this.height).coerceAtLeast(0)
        addedBefore = false
        this.height = height
    }

    private companion object {
        const val MORE_LINES = 30
        const val MAX_LINES = 600
    }
}

/** The reading of [preview]'s passage, anew for each result shown. */
@Composable
internal fun rememberPassageReading(preview: SearchResultViewModel.PassagePreview): PassageReading {
    val repository = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()
    return remember(preview, repository) {
        PassageReading(preview.lines, scope) { from, to -> repository.getLines(preview.hit.bookId, from, to) }
    }
}

/** More of the book when a scroll goes past an end of the passage: a wheel turned there, or its text dragged. */
internal fun Modifier.readPastEnds(
    passage: PassageReading,
    scroll: ScrollState,
): Modifier =
    nestedScroll(passage.pastEnds).pointerInput(passage, scroll) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val turned =
                    event.changes
                        .firstOrNull()
                        ?.scrollDelta
                        ?.y ?: continue
                if (turned < 0f && scroll.value == 0) passage.more(before = true)
                if (turned > 0f && scroll.value >= scroll.maxValue) passage.more(before = false)
            }
        }
    }

/**
 * The passage's lines ([content]) as a text to select, under the menu of [preview]'s book: the copies, the link, the
 * search and the dictionary of the reader's menu, and the book opened at the line ([onOpenLine]); what needs the book
 * open (a note, a highlight, the find bar) is the reader's alone. Lines added before those read keep what is read in
 * its place: [scroll] takes their height once they are laid out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PassageText(
    passage: PassageReading,
    preview: SearchResultViewModel.PassagePreview,
    scroll: ScrollState,
    onOpenLine: (lineId: Long) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val base = LocalTextContextMenu.current
    val actions = rememberPassageActions(passage, preview, onOpenLine)
    val menu =
        remember(base, passage, actions) {
            object : TextContextMenu {
                @Composable
                override fun Area(
                    textManager: TextContextMenu.TextManager,
                    state: ContextMenuState,
                    content: @Composable () -> Unit,
                ) {
                    val selected = textManager.selectedText.text
                    DictionaryReadFor(selected)
                    SideEffect { passage.selectedText = selected }
                    ContextMenuDataProvider(items = { actions.items(textManager.selectedText.text) }) {
                        base.Area(textManager, state, content)
                    }
                }
            }
        }
    LaunchedEffect(passage, scroll) {
        snapshotFlow { passage.heldBack }.collect { held ->
            if (held > 0) {
                scroll.dispatchRawDelta(held.toFloat())
                passage.heldBack = 0
            }
        }
    }
    CompositionLocalProvider(LocalTextContextMenu provides menu) {
        SelectionContainer {
            Box(
                modifier.offset { IntOffset(0, -passage.heldBack) }.onSizeChanged { passage.laidOut(it.height) },
                contentAlignment = Alignment.TopCenter,
            ) { content() }
        }
    }
}

/**
 * A line of the passage: [onOpen] on a click of its own, as before its text could be selected. A press that selects
 * (a drag, a second click on a word, a click that lets a selection go) opens nothing; the line pressed is kept for
 * the selection's source, and a right click's as the line of the menu.
 */
@Composable
internal fun Modifier.passageLine(
    passage: PassageReading,
    lineId: Long,
    onOpen: () -> Unit,
): Modifier {
    val open by rememberUpdatedState(onOpen)
    return pointerInput(passage, lineId) {
        var released = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (currentEvent.buttons.isSecondaryPressed) passage.menuLineId = lineId
            if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            passage.pressedLineId = lineId
            val wait = viewConfiguration.doubleTapTimeoutMillis
            val selecting = passage.selectedText.isNotEmpty() || down.uptimeMillis - released < wait
            val (up, still) = release(down) ?: return@awaitEachGesture
            released = up.uptimeMillis
            if (selecting || !still) return@awaitEachGesture
            // A second click selects its word: the book opens when none comes
            val again = withTimeoutOrNull(wait) { awaitFirstDown(requireUnconsumed = false) }
            if (again == null) open() else release(again)?.let { released = it.first.uptimeMillis }
        }
    }
}

// The release of the press [down], and whether the pointer stayed where it pressed; null when the press was lost
private suspend fun AwaitPointerEventScope.release(down: PointerInputChange): Pair<PointerInputChange, Boolean>? {
    var still = true
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > CLICK_SLOP.toPx()) still = false
        if (!change.pressed) return change to still
    }
}

// The menu's labels, the reader's own
private class PassageLabels(
    val withoutNikud: String,
    val lettersAlone: String,
    val withSource: String,
    val link: String,
    val search: String,
    val dictionary: String,
    val openBook: String,
)

// What the menu offers for the text selected
private class PassageActions(
    private val passage: PassageReading,
    private val preview: SearchResultViewModel.PassagePreview,
    private val labels: PassageLabels,
    private val copy: (String) -> Unit,
    private val search: (String) -> Unit,
    private val dictionary: DictionaryLookup,
    private val openLine: (Long) -> Unit,
) {
    fun items(selected: String): List<ContextMenuItem> =
        buildList {
            val entity = preview.book
            if (selected.isNotBlank() && (HebrewTextUtils.containsNikud(selected) || HebrewTextUtils.containsTeamim(selected))) {
                add(option(AllIconsKeys.Actions.Copy, labels.withoutNikud) { copy(HebrewTextUtils.removeAllDiacritics(selected)) })
            }
            if (selected.isNotBlank() && hasPunctuation(selected)) {
                add(option(AllIconsKeys.Actions.Copy, labels.lettersAlone) { copy(lettersOnly(selected)) })
            }
            if (entity != null && selected.isNotBlank()) {
                add(
                    option(AllIconsKeys.Actions.Copy, labels.withSource) {
                        val root = entity.categories.firstOrNull()
                        copy(buildCopyWithSourcePayload(selected, entity.book, root, passage.lines, passage.linesOf(selected)))
                    },
                )
            }
            add(option(AllIconsKeys.ToolbarDecorator.AddLink, labels.link) { copy(bookShareLink(preview.hit.bookId, lineOf(selected))) })
            val query = selected.replace(SPACES, " ").trim()
            if (query.isNotEmpty()) add(option(AllIconsKeys.Actions.Find, labels.search) { search(query) })
            // Lit for what the dictionaries have of it, as in the reader
            lookupWord(selected)?.let { word ->
                val entry = dictionary.entryOf(word)
                add(option(AllIconsKeys.Toolwindows.Documentation, labels.dictionary, entry != null) { entry?.let(dictionary::open) })
            }
            add(option(AllIconsKeys.Actions.MenuOpen, labels.openBook) { openLine(passage.menuLineId ?: preview.hit.lineId) })
        }

    // The first line of the selection, else the line clicked, else the found one
    private fun lineOf(selected: String): Long = passage.linesOf(selected)?.first?.id ?: passage.menuLineId ?: preview.hit.lineId

    private fun option(
        icon: IconKey,
        label: String,
        enabled: Boolean = true,
        action: () -> Unit,
    ) = ContextMenuItemOption(icon = icon, enabled = enabled, label = label, action = action)
}

@Composable
private fun rememberPassageActions(
    passage: PassageReading,
    preview: SearchResultViewModel.PassagePreview,
    onOpenLine: (Long) -> Unit,
): PassageActions {
    val withoutNikud = stringResource(Res.string.context_menu_copy_without_nikud)
    val lettersAlone = stringResource(Res.string.context_menu_copy_letters_only)
    val withSource = stringResource(Res.string.context_menu_copy_with_source)
    val link = stringResource(Res.string.context_menu_copy_link)
    val search = stringResource(Res.string.context_menu_search_selected_text)
    val inDictionary = stringResource(Res.string.context_menu_open_in_dictionary)
    val openBook = stringResource(Res.string.open_book)
    val copy = rememberCopyToClipboard()
    val dictionary = LocalAppGraph.current.dictionaryLookup
    val tabs = LocalOpenWindow.current.tabsViewModel
    val openLine by rememberUpdatedState(onOpenLine)
    return remember(passage, preview, copy, dictionary, tabs, withoutNikud, openBook) {
        PassageActions(
            passage,
            preview,
            PassageLabels(withoutNikud, lettersAlone, withSource, link, search, inDictionary, openBook),
            copy,
            // In a tab of its own, as the reader's
            search = { query -> tabs.openTab(TabsDestination.Search(searchQuery = query, tabId = UUID.randomUUID().toString())) },
            dictionary = dictionary,
            openLine = { openLine(it) },
        )
    }
}

private val SPACES = Regex("\\s+")

// A press that moves further selects
private val CLICK_SLOP = 4.dp

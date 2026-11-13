package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.CurrentFindMatchColor
import io.github.kdroidfilter.seforimapp.core.presentation.text.SearchHighlightColor
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LocalLocatedSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LocatedSelection
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

// An amud's text tied to the library, as both dafs show it: a tractate's Vilna page (DafView) and another book's own
// pages (BookDafView). Its words' clicks, marks, selection and menu are the text's.

/** How an amud is drawn: [perPoint] pixels a point (laid out at [LAYOUT_SCALE]), in [gemara] and [rashi] letters. */
internal class DafLook(
    val perPoint: Float,
    val gemara: FontFamily,
    val rashi: FontFamily,
    val ink: Color,
    /** The marks' color: the tab's selection, the comments on it. */
    val color: Color,
)

/** What the tab marks on an amud: its selected segments ([marked]), its selected passage's comments, the find in page. */
internal class DafMarks(
    val marked: Set<Long>,
    val passage: Set<Long>,
    val find: DafFind,
)

/**
 * What an amud's clicks do, as the text's lines': select a segment, or a passage with its comments; a comment clicked
 * itself, focused for the arrows ([onFocusComment]); the line of a right-clicked word, for the text's menu; a word
 * clicked, selected in the text where it is in its line.
 */
internal class DafClicks(
    val onSelectLine: (Long, Boolean) -> Unit,
    val onSelectPassage: (Long, Set<Long>, Set<Long>) -> Unit,
    val onFocusComment: (Long?) -> Unit,
    val onContextLine: (Long) -> Unit,
    val onWordInText: (DafAnchor) -> Unit,
)

/** An amud's own selection of its words ([range], each change given to [onRange]); [onPress] comes on any press on it. */
internal class AmudSelection(
    val range: DafRange?,
    val onRange: (DafRange?) -> Unit,
    val onPress: () -> Unit,
)

/**
 * What a tractate's review adds to its amud (DafReview.kt): the lines it [tints] (by index), its actions in the menu on
 * a right-clicked word ([items], with the page's links), and the lines the alignment is not sure of drawn lighter.
 */
internal class AmudReview(
    val tints: Map<Int, Color>,
    val items: (DafWord, DafAmudLinks?) -> List<ContextMenuItem>,
    val markUnsure: Boolean,
)

/** The daf's layout scale, pixels a point: an amud is laid out once at it, and drawn at any width. */
internal const val LAYOUT_SCALE = 2f

/**
 * The amud [amud] of [book] laid out ([layout]; null while it is being laid out), [look]'s size, tied to the library
 * (its links, read once, with the passages Zayit's anchors mark in a book's lines, [passages]): the tab's [marks] on
 * its words, the user's highlights and notes, the find in page's matches; its words' [clicks] as the text's, a drag
 * selecting them ([selection]) for the text's own menu (copy, copy with source, search, find in page…), and a
 * tractate's [review] when it has one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LinkedAmud(
    book: Book,
    amud: DafAmud,
    layout: AmudLayout?,
    look: DafLook,
    marks: DafMarks,
    selection: AmudSelection,
    clicks: DafClicks,
    modifier: Modifier = Modifier,
    passages: Map<Long, List<Pair<IntRange, Set<Long>>>> = emptyMap(),
    review: AmudReview? = null,
) {
    val graph = LocalAppGraph.current
    val density = LocalDensity.current
    // The page's comments and the lines they are on, read once for the page
    val links by produceState<DafAmudLinks?>(null, book.id, amud, passages) {
        value = loadLinks(graph.repository, book.id, amud, passages)
    }
    val words = linkedWords(book.id, links, marks, review, look.color)
    // The selection located in the library, for the text's menu: its highlights, its note and its link
    val currentRange by rememberUpdatedState(selection.range)
    val currentLinks by rememberUpdatedState(links)
    val located: () -> LocatedSelection? =
        remember(amud, book.id) {
            {
                val r = currentRange
                val l = currentLinks
                if (r == null || l == null) null else locate(amud, r, l.anchors, book.id) { w -> l.segmentOf(w) }
            }
        }
    val copyToClipboard = rememberCopyToClipboard()
    val menuState = remember { ContextMenuState() }
    // The word a right-click was on, for the review's actions
    var contextWord by remember(amud) { mutableStateOf<DafWord?>(null) }
    val width = (amud.block[2] - amud.block[0]) * look.perPoint
    val height = (amud.block[3] - amud.block[1]) * look.perPoint
    with(density) {
        Box(modifier.width(width.toDp()).height(height.toDp())) {
            if (layout == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            // The text's own menu on the amud's selection, and the review's actions on the line right-clicked
            val text = remember(amud, selection.range) { selection.range?.let { amud.textOf(it) }.orEmpty() }
            val manager = remember(text, copyToClipboard) { dafTextManager(text, copyToClipboard) }
            ContextMenuDataProvider(items = { contextWord?.let { review?.items?.invoke(it, links) }.orEmpty() }) {
                CompositionLocalProvider(LocalLocatedSelection provides located) {
                    LocalTextContextMenu.current.Area(manager, menuState) {
                        DafAmudText(
                            layout = layout,
                            k = look.perPoint / LAYOUT_SCALE,
                            gemara = look.gemara,
                            rashi = look.rashi,
                            ink = look.ink,
                            highlight = words.highlight,
                            range = selection.range,
                            selectionColor = LocalTextSelectionColors.current.backgroundColor,
                            onRange = selection.onRange,
                            onWordClick = { word, withModifier ->
                                links?.let { l ->
                                    clickOn(l, word, withModifier, clicks.onSelectLine, clicks.onSelectPassage)
                                    clicks.onFocusComment(l.lineIdOf(word)?.takeUnless { l.isGemara(word) })
                                    // The word itself in the text, where it is in its line
                                    l.anchors[word]?.takeIf { it.bookId == book.id }?.let(clicks.onWordInText)
                                }
                            },
                            onContextWord = { word ->
                                contextWord = word
                                links?.segmentOf(word)?.let(clicks.onContextLine)
                            },
                            onMenu = { at -> menuState.status = ContextMenuState.Status.Open(Rect(at, Size.Zero)) },
                            // A press anywhere forgets the word of the last right-click: one off a word has none
                            onPress = {
                                contextWord = null
                                selection.onPress()
                            },
                            markUnsure = review?.markUnsure == true,
                            underlined = words.noted,
                            underlineColor = JewelTheme.globalColors.text.info,
                        )
                    }
                }
            }
        }
    }
}

/** An amud's words as marked: their colors behind them, and those a note is on. */
private class LinkedWords(
    val highlight: DafHighlight,
    val noted: Set<DafWord>,
)

/**
 * The marks on an amud's words: the find in page's matches strongest, then the tab's selection (in [color]), the
 * user's highlights and the [review]'s tints; and the words the main book's ([bookId]) notes are on. The highlights and
 * notes are kept in the library's text, and found on the words they are on (DafAnchors.kt): the highlights of the
 * page's books, the notes of the main book (the text's notes are its own).
 */
@Composable
private fun linkedWords(
    bookId: Long,
    links: DafAmudLinks?,
    marks: DafMarks,
    review: AmudReview?,
    color: Color,
): LinkedWords {
    val graph = LocalAppGraph.current
    val highlightStore = graph.highlightStore
    val noteStore = graph.noteStore
    val anchorBooks =
        remember(links) {
            links
                ?.anchors
                ?.values
                ?.mapTo(HashSet()) { it.bookId }
                .orEmpty()
        }
    LaunchedEffect(anchorBooks) { anchorBooks.forEach { highlightStore.loadBook(it) } }
    LaunchedEffect(bookId) { noteStore.loadBook(bookId) }
    val highlightsByBook by highlightStore.highlightsByBook.collectAsState()
    val notesByBook by noteStore.notesByBook.collectAsState()
    val userColors =
        remember(links, highlightsByBook) { links?.let { highlightedWords(it.anchors, highlightsByBook, USER_HIGHLIGHT_ALPHA) }.orEmpty() }
    val noted =
        remember(links, notesByBook, bookId) { links?.let { notedWords(it.anchors, notesByBook[bookId].orEmpty(), bookId) }.orEmpty() }
    // The find in page's matches, as the text view finds them in the library's lines, the current one strongest
    val find = marks.find
    val found =
        remember(links, find) {
            val l = links
            when {
                l == null -> emptyMap()
                find.query != null -> foundWords(l.anchors, l.texts, find.query, find.current, SearchHighlightColor, CurrentFindMatchColor)
                else -> wordsOfLines(l.anchors, find.lines, SearchHighlightColor)
            }
        }
    val tints = review?.tints.orEmpty()
    val highlight =
        remember(links, marks.marked, marks.passage, color, tints, userColors, found) {
            val selected = dafHighlight(links, marks.marked, marks.passage, color)
            if (tints.isEmpty() && userColors.isEmpty() && found.isEmpty()) {
                selected
            } else {
                DafHighlight { word -> found[word] ?: selected.colorOf(word) ?: userColors[word] ?: tints[word.line] }
            }
        }
    return LinkedWords(highlight, noted)
}

/** What the text's menu takes of an amud's selection: its words, copied as they are. */
@OptIn(ExperimentalFoundationApi::class)
private fun dafTextManager(
    text: String,
    copyToClipboard: (String) -> Unit,
): TextContextMenu.TextManager =
    object : TextContextMenu.TextManager {
        override val selectedText = AnnotatedString(text)
        override val cut: TextContextMenu.Action? = null
        override val copy: TextContextMenu.Action? =
            text.takeIf { it.isNotEmpty() }?.let {
                TextContextMenu.Action(
                    true,
                ) { copyToClipboard(it) }
            }
        override val paste: TextContextMenu.Action? = null
        override val selectAll: TextContextMenu.Action? = null

        // A right-click selects no word: the menu acts on the selection as it is, or on the line under the pointer
        override fun selectWordAtPositionIfNotAlreadySelected(offset: Offset) = Unit
    }

/**
 * The page's ties to the library: its comments' Gemara lines and books, in one read each, and the [passages] Zayit's
 * anchors mark. The Gemara lines are the comments' sources (the library's COMMENTARY links, read backward), one for
 * each link: getSourcesForLines gives each Gemara line once, for the first comment on it.
 */
internal suspend fun loadLinks(
    repository: SeforimRepository,
    bookId: Long,
    amud: DafAmud,
    passages: Map<Long, List<Pair<IntRange, Set<Long>>>> = emptyMap(),
): DafAmudLinks {
    val comments = DafAmudLinks.commentIdsOf(amud)
    val bases =
        runSuspendCatching { repository.getCommentarySummariesForLines(comments.toList(), includeSources = true) }
            .getOrDefault(emptyList())
            .map { it.link }
            .filter { it.connectionType == ConnectionType.SOURCE && it.targetBookId == bookId }
            .groupBy({ it.sourceLineId }, { it.targetLineId })
            .mapValues { (_, ids) -> ids.toSet() }
    // The page's library lines, the Gemara's and the comments', for the comments' books and the words' places in them
    val lines =
        runSuspendCatching {
            repository.getLinesByIds(
                amud.lines.flatMapTo(LinkedHashSet()) { it.lineIds },
            )
        }.getOrDefault(emptyList())
    val books = lines.filter { it.id in comments }.associate { it.id to it.bookId }
    return withContext(Dispatchers.Default) {
        val texts =
            lines.associate { line ->
                line.id to
                    LibraryLine(line.bookId, line.lineIndex, buildAnnotatedFromHtml(line.content, baseTextSize = 16f, boldScale = 1f).text)
            }
        DafAmudLinks(amud, bases, books, anchorsOf(amud, texts), texts.mapValues { it.value.text }, passages)
    }
}

/** How strong a user's highlight is behind the daf's words, as behind the text's (applyUserHighlights). */
private const val USER_HIGHLIGHT_ALPHA = 0.4f

/** How strong each mark is: the passage selected, the segments selected, the comments on them. */
private const val PASSAGE_ALPHA = 0.32f
private const val SELECTED_ALPHA = 0.18f
private const val RELATED_ALPHA = 0.08f

/**
 * The page's marks: the selected passage (its comments and the Gemara words they quote) strongest, then the selected
 * segments, then the comments on the selected segments, lightest. Nothing follows the pointer: the page reads as
 * the text does, where only a selection is marked.
 */
internal fun dafHighlight(
    links: DafAmudLinks?,
    selected: Set<Long>,
    passage: Set<Long>,
    color: Color,
): DafHighlight {
    if (links == null || (selected.isEmpty() && passage.isEmpty())) return DafHighlight.NONE
    val related = selected.flatMapTo(HashSet()) { links.commentsOn[it].orEmpty() }
    val quotedWords = passage.flatMapTo(HashSet()) { links.quoted[it].orEmpty() }
    val strong = color.copy(alpha = PASSAGE_ALPHA)
    val selectedColor = color.copy(alpha = SELECTED_ALPHA)
    val relatedColor = color.copy(alpha = RELATED_ALPHA)
    return DafHighlight { word ->
        val id = links.lineIdOf(word) ?: return@DafHighlight null
        when {
            id in passage || word in quotedWords -> strong
            id in selected -> selectedColor
            id in related -> relatedColor
            else -> null
        }
    }
}

/**
 * A click on [word]: a Gemara word inside words a dibbur quotes selects that passage, another one its segment (with a
 * modifier, added to the selection); a comment's word selects the comment, on its segment.
 */
internal fun clickOn(
    links: DafAmudLinks,
    word: DafWord,
    withModifier: Boolean,
    onSelectLine: (Long, Boolean) -> Unit,
    onSelectPassage: (Long, Set<Long>, Set<Long>) -> Unit,
) {
    val id = links.lineIdOf(word) ?: return
    if (links.isGemara(word)) {
        val passage = if (withModifier) emptySet() else links.passageAt(word)
        if (passage.isEmpty()) {
            onSelectLine(id, withModifier)
        } else {
            onSelectPassage(id, passage, passage.mapNotNullTo(HashSet()) { links.bookOf[it] })
        }
    } else {
        val base = links.segmentOf(word) ?: return
        onSelectPassage(base, setOf(id), setOfNotNull(links.bookOf[id]))
    }
}

/** The Gemara segment of [word]: its own line, or for a comment's word the segment it is on (the one on this page). */
internal fun DafAmudLinks.segmentOf(word: DafWord): Long? {
    val id = lineIdOf(word) ?: return null
    if (isGemara(word)) return id
    val bases = baseOf[id].orEmpty()
    return bases.firstOrNull { it in wordsOf } ?: bases.firstOrNull()
}

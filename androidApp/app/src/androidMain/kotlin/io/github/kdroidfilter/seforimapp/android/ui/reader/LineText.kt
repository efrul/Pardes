package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.data.UserHighlight
import io.github.kdroidfilter.seforimapp.android.data.UserNote
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/** Line height of the reader's text, as a multiple of the text size. */
const val READER_LINE_HEIGHT = 1.65f

/** A stored HTML text as displayed: styled, links, inline images (desktop buildLineAnnotation). */
@Immutable
class HtmlAnnotation(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

/** [html] with the diacritics [diacritics] hides removed, built for display at [textSize]. */
@Composable
fun rememberHtmlAnnotation(html: String, textSize: Float, diacritics: DiacriticsMode): HtmlAnnotation {
    val c = Zayit.colors
    return remember(html, textSize, diacritics, c.isDark, c.accent) {
        val inline = HashMap<String, InlineTextContent>()
        val text = buildAnnotatedFromHtml(
            diacritics.apply(html),
            textSize,
            footnoteMarkerColor = c.accent,
            inlineContent = inline,
            imageContentBuilder = readerImageBuilder(c.isDark),
        )
        HtmlAnnotation(text, inline)
    }
}

/**
 * Desktop's selection bar logic (views/SelectionBarLogic.kt): the primary line, and every line of a manual
 * selection, get the thick bar; the other lines of a TOC section a thin one.
 */
fun shouldUseThickBar(lineId: Long, primaryLineId: Long?, isTocEntrySelection: Boolean): Boolean = lineId == primaryLineId || !isTocEntrySelection

/** Desktop selectionBar modifier: a bar in the start slot, the line's full height, reaching the next selected line. */
fun Modifier.selectionBar(isSelected: Boolean, extendToNext: Boolean, color: Color, isPrimary: Boolean): Modifier = drawBehind {
    if (!isSelected) return@drawBehind
    val slot = SelectionBarWidth.toPx()
    val w = (if (isPrimary) 4.dp else 2.dp).toPx()
    val inSlot = (slot - w) / 2f
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - slot + inSlot else inSlot
    val extra = if (extendToNext) LineVerticalPadding.toPx() * 2 else 0f
    drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height + extra))
}

/** The selection bar's slot at the start of a line, the same whether thick or thin. */
val SelectionBarWidth: Dp = 4.dp

/** The id of a note being written, underlined before it is saved and given its id. */
const val DRAFT_NOTE_ID: Long = -1L

/** Vertical padding of each line item (desktop LineItemVerticalPaddingPerSide is 8dp; tighter on touch screens). */
val LineVerticalPadding: Dp = 5.dp

/**
 * Where the current find-in-page match sits, for the text to bring it into view (desktop BookContentView
 * FindMatchLocator): published by the line showing it as it is laid out, read after a scroll. Plain fields, not
 * state, so publishing never recomposes. A second one locates the passage a note is being written on.
 */
class FindMatchLocator {
    var list: LayoutCoordinates? = null
    private var lineId: Long = -1
    private var matchStart: Int = 0
    private var text: LayoutCoordinates? = null
    private var layout: TextLayoutResult? = null

    fun publishStart(lineId: Long, start: Int) {
        if (this.lineId != lineId) {
            text = null
            layout = null
        }
        this.lineId = lineId
        matchStart = start
    }

    fun publishText(lineId: Long, coordinates: LayoutCoordinates) {
        if (this.lineId != lineId) layout = null
        this.lineId = lineId
        text = coordinates
    }

    fun publishLayout(lineId: Long, result: TextLayoutResult) {
        if (this.lineId != lineId) text = null
        this.lineId = lineId
        layout = result
    }

    /** Drops what [lineId] published: its node may be reused for another line. */
    fun forget(lineId: Long) {
        if (this.lineId != lineId) return
        text = null
        layout = null
    }

    /** Top of the match in [lineId], in the list's coordinates; null until that line is laid out. */
    fun matchTop(lineId: Long): Float? = matchRow(lineId)?.start

    /** Top to bottom of the row the match starts on, in the list's coordinates; null until that line is laid out. */
    fun matchRow(lineId: Long): ClosedFloatingPointRange<Float>? {
        if (this.lineId != lineId) return null
        val list = list?.takeIf { it.isAttached } ?: return null
        val text = text?.takeIf { it.isAttached } ?: return null
        val layout = layout ?: return null
        val length = layout.layoutInput.text.length
        val box = if (length == 0) null else layout.getBoundingBox(matchStart.coerceIn(0, length - 1))
        val top = list.localPositionOf(text, Offset(0f, box?.top ?: 0f)).y
        return top..top + (box?.height ?: 0f)
    }
}

/**
 * One line of the book (desktop BookContentView LineItem): the user's highlights (remapped when diacritics are
 * hidden), the search's words, the find-in-page matches with the current one apart, dotted underlines under noted
 * passages, citation links and images. A tap selects the line (Ctrl+tap adds it); a long press selects text.
 * [findLocator] is given to the line holding the current find match, which tells it where the match is;
 * [noteLocator] to the line a note is being written on, which tells it where the note's passage starts.
 */
@Composable
fun LineItem(
    html: String,
    textSize: Float,
    fontFamily: FontFamily,
    diacritics: DiacriticsMode,
    highlights: List<UserHighlight>,
    notes: List<UserNote>,
    findQuery: String,
    currentMatchOrdinal: Int?,
    searchQuery: String?,
    searchRanges: suspend (String, String) -> List<IntRange>,
    onTap: (ctrl: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Zayit.colors.text,
    textAlign: TextAlign = TextAlign.Justify,
    findLocator: FindMatchLocator? = null,
    noteLocator: FindMatchLocator? = null,
    lineId: Long = -1L,
) {
    val c = Zayit.colors
    val annotation = rememberHtmlAnnotation(html, textSize, diacritics)
    val annotated = annotation.text
    val needsOriginal = diacritics != DiacriticsMode.All && (highlights.isNotEmpty() || notes.isNotEmpty())
    val originalPlain = remember(html, needsOriginal) { if (needsOriginal) buildAnnotatedFromHtml(html, textSize).text else null }
    val search by produceState<List<IntRange>?>(null, annotated.text, searchQuery) {
        value = searchQuery?.let { q -> searchRanges(annotated.text, q) }
    }
    val baseHl = c.accent.copy(alpha = 0.22f)
    val display = remember(annotated, highlights, originalPlain, diacritics, search, searchQuery, findQuery, currentMatchOrdinal, baseHl) {
        val withUser = applyUserHighlights(annotated, highlights, originalPlain, diacritics)
        // A smart find's line is one match: all its spans take the current colour when it is the current one
        val searchColor = if (searchQuery != null && currentMatchOrdinal != null) CurrentFindMatchColor else baseHl
        val withSearch = search?.takeIf { searchQuery != null }?.let { withUser.withBackground(it, searchColor) } ?: withUser
        highlightAnnotatedWithCurrent(withSearch, findQuery, baseHl, CurrentFindMatchColor, currentMatchOrdinal)
    }
    val style = TextStyle(fontFamily = fontFamily, fontSize = textSize.sp, lineHeight = (textSize * READER_LINE_HEIGHT).sp, color = color, textAlign = textAlign)
    val noteRanges = remember(notes, originalPlain, diacritics, display) { noteDisplayRanges(notes, originalPlain, diacritics, display.length) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val noteColor = c.textSecondary
    val tap by rememberUpdatedState(onTap)
    if (findLocator != null) {
        // A smart find's line is one match, from its first lit word; a literal find's current occurrence
        val matchStart = remember(annotated.text, search, searchQuery, findQuery, currentMatchOrdinal) {
            (if (searchQuery != null) search?.firstOrNull() else findAllMatchesOriginal(annotated.text, findQuery).getOrNull(currentMatchOrdinal ?: 0))?.first ?: 0
        }
        SideEffect { findLocator.publishStart(lineId, matchStart) }
        DisposableEffect(findLocator, lineId) { onDispose { findLocator.forget(lineId) } }
    }
    if (noteLocator != null) {
        // The draft's passage (not yet saved, so without an id), where its dotted underline starts
        val noteStart = remember(notes, originalPlain, diacritics, display.length) {
            noteDisplayRanges(notes.filter { it.id == DRAFT_NOTE_ID }, originalPlain, diacritics, display.length).firstOrNull()?.first ?: 0
        }
        SideEffect { noteLocator.publishStart(lineId, noteStart) }
        DisposableEffect(noteLocator, lineId) { onDispose { noteLocator.forget(lineId) } }
    }
    // The width the Text fills, which its lines are justified to (Justify.kt)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val justified = rememberJustifiedText(display, style, constraints.maxWidth, inline = annotation.inline.isNotEmpty())
        Text(
            text = justified.text,
            inlineContent = annotation.inline,
            modifier = modifier
                .then(if (findLocator != null) Modifier.onGloballyPositioned { findLocator.publishText(lineId, it) } else Modifier)
                .then(if (noteLocator != null) Modifier.onGloballyPositioned { noteLocator.publishText(lineId, it) } else Modifier)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // Desktop LineItem: a press released unconsumed is a click; a long press goes to the selection, a
                    // drag to the list and a link's tap to its link, which consume it.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // Ctrl/Cmd held on a hardware keyboard (desktop Ctrl+click adds the line to the selection)
                        val ctrl = ((currentEvent.motionEvent?.metaState ?: 0) and (android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_META_ON)) != 0
                        val up = waitForUpOrCancellation()
                        if (up != null && !up.isConsumed) tap(ctrl)
                    }
                }
                .drawBehind { layout?.let { drawNoteUnderlines(it, noteRanges, noteColor) } },
            onTextLayout = {
                layout = it
                justified.onLayout(it)
                findLocator?.publishLayout(lineId, it)
                noteLocator?.publishLayout(lineId, it)
            },
            style = style.copy(textAlign = justified.textAlign),
        )
    }
}

/** Desktop AltHeadingItem: an alternative structure's heading inline in the text ("בראשית", "עליה א"). */
@Composable
fun AltHeading(text: String, level: Int, textSize: Float, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    val size = when (level) {
        0 -> textSize * 1.25f
        1 -> textSize * 1.125f
        else -> textSize
    }
    Box(
        Modifier.fillMaxWidth().padding(top = if (level == 0) 4.dp else 0.dp, bottom = 4.dp).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                if (up != null && !up.isConsumed) click()
            }
        },
    ) {
        Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = Zayit.colors.text, fontFamily = Zayit.fonts.book)
    }
}

/** Where a marked passage ends: "סוף הלימוד", or "סוף הקריאה" for an aliya read, between two rules (desktop MarkedEnd). */
@Composable
fun MarkedEnd(label: String) {
    val color = Zayit.colors.accent
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
    }
}

/** A verse's targum under the verse read twice, in shnayim mikra (desktop ShnayimMikraTargum, 0.9× and secondary). */
@Composable
fun ShnayimMikraTargum(html: String, textSize: Float, diacritics: DiacriticsMode) {
    val size = textSize * 0.9f
    val annotation = rememberHtmlAnnotation(html, size, diacritics)
    Text(
        annotation.text,
        inlineContent = annotation.inline,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        style = TextStyle(fontFamily = Zayit.fonts.targum, fontSize = size.sp, lineHeight = (size * READER_LINE_HEIGHT).sp, color = Zayit.colors.textSecondary),
    )
}

@Composable
fun LinePlaceholder(textSize: Float) {
    Spacer(Modifier.fillMaxWidth().height((textSize * 1.9f).dp))
}

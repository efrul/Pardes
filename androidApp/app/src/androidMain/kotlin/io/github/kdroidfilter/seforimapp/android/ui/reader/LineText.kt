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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.data.UserHighlight
import io.github.kdroidfilter.seforimapp.android.data.UserNote
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/** Line height of the reader's text, as a multiple of the text size. */
const val READER_LINE_HEIGHT = 1.65f

/** A stored HTML text as displayed: styled, links, inline images (desktop buildLineAnnotation). */
@Immutable
class HtmlAnnotation(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

/**
 * [html] with the diacritics [diacritics] hides removed, built for display at [textSize]; the commentary anchors of
 * [anchorCommentators] become its passages (desktop 0.6.12, CommentSegments.kt), which leave the text as it is.
 */
@Composable
fun rememberHtmlAnnotation(html: String, textSize: Float, diacritics: DiacriticsMode, anchorCommentators: Set<String> = emptySet()): HtmlAnnotation {
    val c = Zayit.colors
    return remember(html, textSize, diacritics, c.isDark, c.accent, anchorCommentators) {
        buildHtmlAnnotation(html, textSize, diacritics, c.isDark, c.accent, anchorCommentators)
    }
}

/** What [rememberHtmlAnnotation] shows, built outside composition (a selected text scrolled away is read with it). */
fun buildHtmlAnnotation(
    html: String,
    textSize: Float,
    diacritics: DiacriticsMode,
    dark: Boolean,
    accent: Color,
    anchorCommentators: Set<String> = emptySet(),
): HtmlAnnotation {
    val inline = HashMap<String, InlineTextContent>()
    val text = buildAnnotatedFromHtml(
        diacritics.apply(html),
        textSize,
        footnoteMarkerColor = accent,
        inlineContent = inline,
        imageContentBuilder = readerImageBuilder(dark),
        anchorCommentators = anchorCommentators,
    )
    return HtmlAnnotation(text, inline)
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
 * [selectable] makes it one of a [ListSelection]'s texts.
 *
 * The passages commentaries explain (desktop 0.6.12): the anchors of [anchorCommentators] leading, by [anchorTargets],
 * to a commentary book open in the pane ([passageBooks]). A tap on one reports it to [onPassageTap] instead of [onTap];
 * the passage selected ([selectedAnchors]) is underlined as a tray, and with [showPassages] (the primary line: touch has
 * no pointer to hover the text with, as desktop shows them) the others faintly.
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
    selectable: SelectableText? = null,
    anchorCommentators: Set<String> = emptySet(),
    anchorTargets: Map<CommentAnchor, AnchorTarget> = emptyMap(),
    passageBooks: Set<Long> = emptySet(),
    selectedAnchors: Set<CommentAnchor>? = null,
    showPassages: Boolean = false,
    onPassageTap: ((SelectedPassage) -> Unit)? = null,
) {
    val c = Zayit.colors
    val annotation = rememberHtmlAnnotation(html, textSize, diacritics, anchorCommentators)
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
    // The passages of this line that lead to a commentary open in the pane (desktop LineItem's segments)
    val allSegments = remember(annotated) { annotated.commentSegments() }
    val tappable = onPassageTap != null
    val segments = remember(allSegments, anchorTargets, passageBooks, lineId, tappable) {
        if (!tappable) emptyList() else allSegments.filter { passageOf(lineId, it, anchorTargets, passageBooks) != null }
    }
    // The selected one stays drawn even if its commentator no longer answers
    val selectedSegment = selectedAnchors?.let { anchors -> allSegments.firstOrNull { it.anchors == anchors } }
    val passageHit = remember(lineId) { PassageHit() }
    val trays = remember(lineId) { PassageTrays() }
    val passageTap by rememberUpdatedState(onPassageTap)
    SideEffect {
        passageHit.segments = segments
        passageHit.passageOf = { passageOf(lineId, it, anchorTargets, passageBooks) }
        passageHit.layout = { layout }
    }
    val trayColor = c.accent
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
                .then(selectable?.modifier ?: Modifier)
                .then(if (findLocator != null) Modifier.onGloballyPositioned { findLocator.publishText(lineId, it) } else Modifier)
                .then(if (noteLocator != null) Modifier.onGloballyPositioned { noteLocator.publishText(lineId, it) } else Modifier)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // Desktop LineItem: a press released unconsumed is a click; a long press goes to the selection, a
                    // drag to the list and a link's tap to its link, which consume it.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Ctrl/Cmd held on a hardware keyboard (desktop Ctrl+click adds the line to the selection)
                        val ctrl = ((currentEvent.motionEvent?.metaState ?: 0) and (android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_META_ON)) != 0
                        val up = waitForUpOrCancellation()
                        if (up != null && !up.isConsumed) {
                            // One tap, one event: the passage it lands on, else the line (desktop LineItem)
                            val passage = if (ctrl) null else passageHit.passageAt(down.position, up.position, viewConfiguration.touchSlop)
                            val onPassage = passageTap
                            if (passage != null && onPassage != null) onPassage(passage) else tap(ctrl)
                        }
                    }
                }
                .drawBehind {
                    val laid = layout ?: return@drawBehind
                    if (showPassages) {
                        segments.forEach { segment ->
                            if (segment != selectedSegment) with(trays) { draw(laid, segment, trayColor.copy(alpha = PASSAGE_UNDERLINE_ALPHA)) }
                        }
                    }
                    selectedSegment?.let { with(trays) { draw(laid, it, trayColor.copy(alpha = SELECTED_UNDERLINE_ALPHA)) } }
                    drawNoteUnderlines(laid, noteRanges, noteColor)
                },
            onTextLayout = {
                layout = it
                justified.onLayout(it)
                selectable?.onLayout(it)
                findLocator?.publishLayout(lineId, it)
                noteLocator?.publishLayout(lineId, it)
            },
            style = style.copy(textAlign = justified.textAlign),
        )
    }
}

/** Where a point of a line's text falls among its passages: set by the line once its text is laid out (desktop PassageHit). */
private class PassageHit {
    var segments: List<CommentSegment> = emptyList()
    var layout: () -> TextLayoutResult? = { null }
    var passageOf: (CommentSegment) -> SelectedPassage? = { null }

    fun at(position: Offset): CommentSegment? {
        if (segments.isEmpty()) return null
        val result = layout() ?: return null
        // Below the text the nearest offset is its last character: not a passage under the finger
        if (position.y > result.size.height) return null
        return segments.at(result.getOffsetForPosition(position))
    }

    // A tap, not a drag (beyond [slop]), on a passage: the passage with the commentary lines it leads to
    fun passageAt(down: Offset, up: Offset, slop: Float): SelectedPassage? = if ((up - down).getDistance() > slop) null else at(up)?.let(passageOf)
}

// Desktop's tray alphas: the passages shown at rest are its hovered line's, the selected one its pointed one
private const val PASSAGE_UNDERLINE_ALPHA = 0.35f
private const val SELECTED_UNDERLINE_ALPHA = 0.9f
private val PassageUnderlineWidth = 2.dp

// Past the bottom of the text's last row, clear of its letters' tails and vowels (negative: below it)
private val PassageUnderlineLift = (-3).dp

// Each end pushed just past the passage's outer letters, so the tray holds them; the space between two passages keeps
// their trays apart
private val PassageUnderlineOutset = 1.dp

// The rims rising at a passage's two ends, so its underline holds it like a tray
private val PassageRimHeight = 9.dp

/** The trays of a line's passages, traced once per layout (and density): a frame only strokes them (desktop PassageTrays). */
private class PassageTrays {
    private var layout: TextLayoutResult? = null
    private var density = 0f
    private val paths = HashMap<CommentSegment, Path>()

    fun DrawScope.draw(layout: TextLayoutResult, passage: CommentSegment, color: Color) {
        if (layout !== this@PassageTrays.layout || density != this@PassageTrays.density) {
            paths.clear()
            this@PassageTrays.layout = layout
            this@PassageTrays.density = density
        }
        val tray = paths.getOrPut(passage) { trayOf(layout, passage) }
        drawPath(tray, color, style = Stroke(width = PassageUnderlineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * A passage underlined row by row as a tray: the line under it rises at its first and last character, just past them.
 * A row only continuing the passage has the line alone (desktop trayOf).
 */
private fun DrawScope.trayOf(layout: TextLayoutResult, passage: CommentSegment): Path {
    val tray = Path()
    val text = layout.layoutInput.text.text
    // Without the space that may close the passage
    val end = (passage.end.coerceAtMost(text.length) downTo passage.start + 1).firstOrNull { !text[it - 1].isWhitespace() } ?: return tray
    val outset = PassageUnderlineOutset.toPx()
    val rim = PassageRimHeight.toPx()
    layout.forEachRowSpan(passage.start, end) { row, from, to, xFrom, xTo ->
        if (from >= to) return@forEachRowSpan
        // Each end pushed outwards, whichever side it stands on
        val direction = if (xTo >= xFrom) 1f else -1f
        val a = xFrom - direction * outset
        val b = xTo + direction * outset
        // Only the text's last row has room below it; a row with another under it keeps the line on its own bottom
        val lift = if (row == layout.lineCount - 1) PassageUnderlineLift else 0.dp
        val y = layout.getLineBottom(row) - lift.toPx()
        tray.moveTo(a, if (from == passage.start) y - rim else y)
        tray.lineTo(a, y)
        tray.lineTo(b, y)
        if (to == end) tray.lineTo(b, y - rim)
    }
    return tray
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
fun ShnayimMikraTargum(html: String, textSize: Float, diacritics: DiacriticsMode, selectable: SelectableText? = null) {
    val size = shnayimMikraTargumSize(textSize)
    val annotation = rememberHtmlAnnotation(html, size, diacritics)
    Text(
        annotation.text,
        inlineContent = annotation.inline,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp).then(selectable?.modifier ?: Modifier),
        onTextLayout = { selectable?.onLayout(it) },
        style = TextStyle(fontFamily = Zayit.fonts.targum, fontSize = size.sp, lineHeight = (size * READER_LINE_HEIGHT).sp, color = Zayit.colors.textSecondary),
    )
}

/** The targum's size in shnayim mikra, 0.9× the text's. */
fun shnayimMikraTargumSize(textSize: Float): Float = textSize * 0.9f

@Composable
fun LinePlaceholder(textSize: Float) {
    Spacer(Modifier.fillMaxWidth().height((textSize * 1.9f).dp))
}

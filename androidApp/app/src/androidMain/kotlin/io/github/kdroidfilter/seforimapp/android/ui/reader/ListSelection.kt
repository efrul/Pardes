package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.toSize

/**
 * A place in a selectable list's texts: [unit] orders the texts (a line of the book and its part, a commentary) and
 * [offset] is a position in that unit's text as it is displayed.
 */
@Immutable
data class TextPoint(val unit: Long, val offset: Int) : Comparable<TextPoint> {
    override fun compareTo(other: TextPoint): Int = if (unit != other.unit) unit.compareTo(other.unit) else offset.compareTo(other.offset)
}

/**
 * A text selection over the texts of a lazy list (the book's lines, a pane's commentaries) that scrolling does not
 * break; [SelectableArea] draws its handles and runs its gestures.
 *
 * Compose's SelectionContainer around a LazyColumn knows only the texts composed at the moment. Once the line a
 * selection started on had scrolled away, dragging the other handle looked that line up among the ones on screen and
 * the app closed (MultiWidgetSelectionDelegate, NoSuchElementException "Cannot find value for key"); a copy left out the
 * lines scrolled past; and its handles, windows of their own, could not scroll the list, so a selection longer than the
 * screen took scrolling by hand between drags. This one keeps its ends as [TextPoint]s, reads the texts scrolled away
 * from the data ([textOf]), and scrolls the list while a handle or a long-press drag is held at the top or bottom edge.
 */
@Stable
class ListSelection {
    /** The selection's first point, before [end]; both null while nothing is selected. */
    var start by mutableStateOf<TextPoint?>(null)
        private set
    var end by mutableStateOf<TextPoint?>(null)
        private set

    /** A handle or a long press is being dragged: the bar waits until it is let go. */
    var dragging by mutableStateOf(false)
        internal set

    /** Counts the selections made: each one opens its own bar (the palette closed). */
    var generation by mutableIntStateOf(0)
        private set

    val hasSelection: Boolean
        get() {
            val s = start ?: return false
            val e = end ?: return false
            return s < e
        }

    /** The units the last [selectedText] read, kept after [clear] for the action the text was read for. */
    var lastUnits: LongRange? = null
        private set

    /** The text of a unit that is not composed (scrolled away), as its Text shows it; null where there is none. */
    internal var textOf: (Long) -> String? = { null }

    /** Scroll speed at the edge, px/s, negative toward the top; 0 when no drag is held at an edge. */
    internal var edgeSpeed by mutableFloatStateOf(0f)

    /** Applies the held drag again where the finger is, after the text scrolled under it. */
    internal var redrag: (() -> Unit)? = null

    /** What the magnifier shows while a drag is held, in the area; unspecified otherwise. */
    internal var magnifier by mutableStateOf(Offset.Unspecified)

    /** Where the start and end handles take a touch, in the area; null while one is not shown. */
    internal val handleRects = arrayOfNulls<Rect>(2)

    private val texts = HashMap<Long, SelectableText>()

    fun clear() {
        start = null
        end = null
        dragging = false
        edgeSpeed = 0f
        redrag = null
        magnifier = Offset.Unspecified
        handleRects.fill(null)
    }

    /** Selects from [a] to [b], in either order. */
    internal fun select(a: TextPoint, b: TextPoint) {
        if (b < a) {
            start = b
            end = a
        } else {
            start = a
            end = b
        }
    }

    /** A selection started anew (a long press, a mouse drag). */
    internal fun begin(a: TextPoint, b: TextPoint) {
        select(a, b)
        generation++
    }

    internal fun register(text: SelectableText) {
        texts[text.unit] = text
    }

    internal fun unregister(text: SelectableText) {
        if (texts[text.unit] === text) texts.remove(text.unit)
    }

    /** The part of [unit]'s text ([length] long) that is selected; null when none of it is. */
    internal fun rangeIn(unit: Long, length: Int): IntRange? {
        val s = start ?: return null
        val e = end ?: return null
        if (unit < s.unit || unit > e.unit) return null
        val from = if (unit == s.unit) s.offset.coerceIn(0, length) else 0
        val to = if (unit == e.unit) e.offset.coerceIn(0, length) else length
        return if (to > from) from until to else null
    }

    /** The selected text: the parts of several units joined by line breaks, as Compose's selection copied them. */
    fun selectedText(): String {
        val s = start ?: return ""
        val e = end ?: return ""
        lastUnits = s.unit..e.unit
        val out = StringBuilder()
        var unit = s.unit
        while (unit <= e.unit) {
            val text = texts[unit]?.text ?: textOf(unit)
            val range = text?.let { rangeIn(unit, it.length) }
            if (text != null && range != null) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(text, range.first, range.last + 1)
            }
            unit++
        }
        return out.toString()
    }

    /** The text laid out at [position] (in [area]), or with [exact] false the nearest one; null when none is. */
    internal fun pointAt(position: Offset, area: LayoutCoordinates, exact: Boolean): TextPoint? {
        var best: SelectableText? = null
        var bestDistance = Float.MAX_VALUE
        var bestLocal = Offset.Zero
        for (t in texts.values) {
            val coordinates = t.coordinates?.takeIf { it.isAttached } ?: continue
            if (t.layout == null) continue
            val local = coordinates.localPositionOf(area, position)
            val height = coordinates.size.height.toFloat()
            val distance = when {
                local.y < 0f -> -local.y
                local.y > height -> local.y - height
                else -> 0f
            }
            if (distance < bestDistance) {
                best = t
                bestDistance = distance
                bestLocal = local
            }
        }
        val text = best ?: return null
        if (exact && bestDistance > 0f) return null
        val layout = text.layout ?: return null
        val y = bestLocal.y.coerceIn(0f, (layout.size.height - 1).coerceAtLeast(0).toFloat())
        return TextPoint(text.unit, layout.getOffsetForPosition(Offset(bestLocal.x, y)))
    }

    /**
     * The word at [point], without the spaces and maqaf at its edges (Android's word boundary keeps the maqaf joining it
     * to the next word, which desktop leaves out); the one character there when it is no word, null on a space.
     */
    internal fun wordAt(point: TextPoint): Pair<TextPoint, TextPoint>? {
        val layout = texts[point.unit]?.layout ?: return null
        val text = layout.layoutInput.text.text
        if (text.isEmpty()) return null
        val boundary = layout.getWordBoundary(point.offset.coerceIn(0, text.length))
        var a = boundary.min
        var b = boundary.max
        while (a < b && text[a].isSelectionEdge()) a++
        while (b > a && text[b - 1].isSelectionEdge()) b--
        if (a >= b) {
            a = point.offset.coerceIn(0, text.length - 1)
            if (text[a].isWhitespace()) return null
            b = a + 1
        }
        return TextPoint(point.unit, a) to TextPoint(point.unit, b)
    }

    /** Where an end's handle hangs, in the area: [x] on its character's outer edge, under its row ([top] to [bottom]). */
    internal class Spot(val x: Float, val top: Float, val bottom: Float, val outwardRight: Boolean)

    /** The start's or the end's [Spot]; null while its text is not laid out (scrolled away). */
    internal fun spot(isStart: Boolean, area: LayoutCoordinates): Spot? {
        val point = (if (isStart) start else end) ?: return null
        val text = texts[point.unit] ?: return null
        val coordinates = text.coordinates?.takeIf { it.isAttached && area.isAttached } ?: return null
        val layout = text.layout ?: return null
        val length = layout.layoutInput.text.length
        if (length == 0) return null
        // The first selected character, or the last: its outer edge is the right one in Hebrew, the left one in Latin
        val char = (if (isStart) point.offset else point.offset - 1).coerceIn(0, length - 1)
        val box = layout.getBoundingBox(char)
        val outwardRight = isStart == (layout.getBidiRunDirection(char) == ResolvedTextDirection.Rtl)
        val row = layout.getLineForOffset(char)
        val top = area.localPositionOf(coordinates, Offset(if (outwardRight) box.right else box.left, layout.getLineTop(row)))
        return Spot(top.x, top.y, top.y + layout.getLineBottom(row) - layout.getLineTop(row), outwardRight)
    }

    /** The handle under [position]: 0 the start's, 1 the end's (the nearer when both are), -1 none. */
    internal fun handleAt(position: Offset): Int {
        if (!hasSelection) return -1
        var found = -1
        var nearest = Float.MAX_VALUE
        handleRects.forEachIndexed { i, rect ->
            if (rect != null && rect.contains(position)) {
                val d = (rect.center - position).getDistanceSquared()
                if (d < nearest) {
                    found = i
                    nearest = d
                }
            }
        }
        return found
    }

    /** The selection's part on screen, in the area: where the bar goes; null when none of it shows. */
    internal fun visibleBounds(area: LayoutCoordinates): Rect? {
        if (!area.isAttached) return null
        val screen = Rect(Offset.Zero, area.size.toSize())
        var bounds: Rect? = null
        for (t in texts.values) {
            val coordinates = t.coordinates?.takeIf { it.isAttached } ?: continue
            val layout = t.layout ?: continue
            val range = rangeIn(t.unit, layout.layoutInput.text.length) ?: continue
            val box = layout.getPathForRange(range.first, range.last + 1).getBounds()
            val topLeft = area.localPositionOf(coordinates, box.topLeft)
            val part = Rect(topLeft, box.size).intersect(screen)
            if (part.width <= 0f || part.height <= 0f) continue
            bounds = bounds?.union(part) ?: part
        }
        return bounds
    }

    private fun Rect.union(other: Rect) = Rect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
}

private const val MAQAF = '־'

private fun Char.isSelectionEdge() = isWhitespace() || this == MAQAF

/**
 * One of a [ListSelection]'s texts: [modifier] goes on its Text after its padding (where its layout starts), [onLayout]
 * in its onTextLayout. Draws the selected part behind the text.
 */
@Stable
class SelectableText internal constructor(private val selection: ListSelection, val unit: Long, private val color: Color) {
    internal var coordinates: LayoutCoordinates? = null
    internal var layout: TextLayoutResult? = null

    internal val text: String? get() = layout?.layoutInput?.text?.text

    // Coordinates are live: kept once, they tell where the text is after every scroll
    val modifier: Modifier = Modifier
        .onPlaced { coordinates = it }
        .drawBehind {
            val l = layout ?: return@drawBehind
            val range = selection.rangeIn(unit, l.layoutInput.text.length) ?: return@drawBehind
            drawPath(l.getPathForRange(range.first, range.last + 1), color)
        }

    fun onLayout(result: TextLayoutResult) {
        layout = result
    }
}

/** [unit]'s text in [selection], registered while it is composed; null without a selection. */
@Composable
fun rememberSelectableText(selection: ListSelection?, unit: Long): SelectableText? {
    if (selection == null) return null
    val color = LocalTextSelectionColors.current.backgroundColor
    val text = remember(selection, unit, color) { SelectableText(selection, unit, color) }
    DisposableEffect(text) {
        selection.register(text)
        onDispose { selection.unregister(text) }
    }
    return text
}

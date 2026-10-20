package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.shell.WindowBackScope
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The actions of desktop's text context menu (features/bookcontent/BookContentScreen.kt BookTextMenus), in its order.
 * [mainOnly] ones are not offered in the panes, as desktop.
 */
enum class SelectionAction(val label: String, val icon: ImageVector, val mainOnly: Boolean = false) {
    Copy("העתקה", Icons.Outlined.ContentCopy),
    CopyWithoutNikud("העתק ללא ניקוד", Icons.Outlined.ContentCopy),
    CopyWithSource("העתק עם מקור", Icons.Outlined.ContentCopy, mainOnly = true),
    CopyLink("העתק קישור לקטע זה", Icons.Outlined.Link, mainOnly = true),
    SearchDatabase("חפש במאגר", Icons.Outlined.Search),
    FindInPage("חפש בעמוד", Icons.Outlined.FindInPage),
    AddNote("הוסף הערה", Icons.Outlined.EditNote, mainOnly = true),
    ReportError("דווח על טעות", Icons.Outlined.ReportProblem, mainOnly = true),

    /** Panes: the commentary's own place, in a new tab (desktop Ctrl+click on a commentary). */
    OpenInPlace("פתח במקומו", Icons.Outlined.OpenInNew),

    /** Touch stand-in for Ctrl+click: taps add or remove lines. */
    MultiSelect("בחירה מרובה", Icons.Outlined.SelectAll, mainOnly = true),
}

/**
 * What a selectable area offers: [main] for the book's text, else a pane column; [canCopyWithoutNikud] decides
 * desktop's condition (the book has diacritics, they are shown and the selection holds some); [onAction] gets the
 * selected text; [onHighlight] a palette colour ([Color.Transparent] clears).
 */
@Stable
class SelectionMenuSpec(
    val main: Boolean,
    val canCopyWithoutNikud: (String) -> Boolean,
    val onAction: (SelectionAction, String) -> Unit,
    val onHighlight: (Color, String) -> Unit,
    val canOpenInPlace: Boolean = false,
)

/**
 * Copies reach the system clipboard as plain text. A selection's copy otherwise carries the text's spans (the letter
 * spacing that justifies it, Justify.kt, and the highlights), which a paste into a search field would show.
 */
internal class PlainTextClipboard(private val real: Clipboard) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = real.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) = real.setClipEntry(clipEntry?.plain())

    override val nativeClipboard get() = real.nativeClipboard
}

private fun ClipEntry.plain(): ClipEntry {
    val data = clipData
    val text = if (data.itemCount > 0) data.getItemAt(0).text else null
    return if (text == null) this else ClipEntry(ClipData.newPlainText(data.description.label, text.toString()))
}

/** A SelectionContainer whose copies are plain text ([PlainTextClipboard]), for justified text outside [SelectableArea]. */
@Composable
fun PlainCopySelectionContainer(content: @Composable () -> Unit) {
    val real = LocalClipboard.current
    val plain = remember(real) { PlainTextClipboard(real) }
    CompositionLocalProvider(LocalClipboard provides plain) { SelectionContainer { content() } }
}

/**
 * A list of texts the reader can select in, with desktop's text menu (BookContentScreen.kt BookTextMenus): a long press
 * selects a word and a drag from it more, the handles extend the selection, and once it is let go a floating bar offers
 * copy, highlight, note and "עוד" (desktop's whole menu in a sheet). The selection is [selection] ([ListSelection]): the
 * texts in [content] join it with [rememberSelectableText], [textOf] reads the ones scrolled away, and a drag held at
 * the top or bottom edge scrolls [list]. A change of [resetKey] (other texts shown) clears it.
 */
@Composable
fun SelectableArea(
    spec: SelectionMenuSpec,
    selection: ListSelection,
    list: LazyListState,
    textOf: (Long) -> String?,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var sheetText by remember { mutableStateOf<String?>(null) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    val area = remember { AreaHolder() }
    SideEffect { selection.textOf = textOf }
    LaunchedEffect(selection, resetKey) { selection.clear() }
    LaunchedEffect(selection, list) { edgeScroll(selection, list) }
    val selected by remember(selection) { derivedStateOf { selection.hasSelection } }
    BackHandler(enabled = selected) { selection.clear() }
    // The reader's Ctrl+C copies the selection made last, in its text or a pane
    val focus = LocalSelectionFocus.current
    LaunchedEffect(focus, selection) {
        snapshotFlow { selection.generation to selection.hasSelection }.collect { (_, has) -> if (has) focus?.current = selection }
    }
    DisposableEffect(focus, selection) { onDispose { if (focus?.current === selection) focus.current = null } }
    val haptics = LocalHapticFeedback.current
    val handleColor = LocalTextSelectionColors.current.handleColor
    Box(modifier.onSizeChanged { areaSize = it }) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { area.coordinates = it }
                .magnifier(sourceCenter = { selection.magnifier })
                .pointerInput(selection) { selectionGestures(selection, area, haptics) },
        ) {
            content()
            if (selected) {
                SelectionHandle(selection, isStart = true, list, area, handleColor)
                SelectionHandle(selection, isStart = false, list, area, handleColor)
            }
        }
        // Over the part on screen once the drag and the scrolling stop (Android hides its bar meanwhile)
        if (selected && !selection.dragging && !list.isScrollInProgress) {
            area.coordinates?.let(selection::visibleBounds)?.let { bounds ->
                key(selection.generation) {
                    SelectionBar(bounds, spec, areaSize, capture = selection::selectedText, dismiss = selection::clear) { sheetText = it }
                }
            }
        }
    }
    if (isTabInFront()) sheetText?.let { text -> SelectionSheet(text, spec, onDismiss = { sheetText = null }) }
}

/** Which selection a reader's Ctrl+C copies: the one made last in its text or one of its panes ([SelectableArea]). */
@Stable
class SelectionFocus {
    internal var current: ListSelection? = null

    /** That selection, while something is selected in it. */
    val selection: ListSelection? get() = current?.takeIf { it.hasSelection }
}

/** The reader's [SelectionFocus]; null outside a reader. */
val LocalSelectionFocus = staticCompositionLocalOf<SelectionFocus?> { null }

/** The area's coordinates, which the gestures and the handles convert positions with. */
private class AreaHolder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * One end's handle, Android's drop with its point on the end's outer edge under its row; hidden while that end is off
 * screen. It takes no touches itself: [selectionGestures] finds it by [ListSelection.handleRects].
 */
@Composable
private fun BoxScope.SelectionHandle(selection: ListSelection, isStart: Boolean, list: LazyListState, area: AreaHolder, color: Color) {
    val index = if (isStart) 0 else 1
    fun shown(): ListSelection.Spot? {
        val coordinates = area.coordinates ?: return null
        return selection.spot(isStart, coordinates)?.takeIf { it.bottom >= 0f && it.bottom <= coordinates.size.height }
    }
    Spacer(
        Modifier
            .align(AbsoluteAlignment.TopLeft)
            .layout { measurable, _ ->
                val size = HandleSize.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(size, size))
                layout(size, size) {
                    list.layoutInfo // read, so the handle moves with the text as the list scrolls and lays out
                    val spot = shown()
                    if (spot == null) {
                        selection.handleRects[index] = null
                        placeable.place(0, 0)
                    } else {
                        val x = if (spot.outwardRight) spot.x else spot.x - size
                        selection.handleRects[index] = Rect(x, spot.bottom, x + size, spot.bottom + size).inflate(HandleTouchMargin.toPx())
                        placeable.place(x.roundToInt(), spot.bottom.roundToInt())
                    }
                }
            }
            .drawBehind {
                list.layoutInfo
                val spot = shown() ?: return@drawBehind
                val r = size.width / 2f
                drawCircle(color, r, Offset(r, r))
                // The square corner is the point, toward the selected text
                drawRect(color, topLeft = Offset(if (spot.outwardRight) 0f else r, 0f), size = Size(r, r))
            },
    )
}

private val HandleSize = 24.dp

/** Room around a handle that still takes its drag. */
private val HandleTouchMargin = 12.dp

/** Long presses, taps, handle drags and the mouse, in the area's coordinates, taken before the list and its lines see them. */
private suspend fun PointerInputScope.selectionGestures(selection: ListSelection, area: AreaHolder, haptics: HapticFeedback) {
    val clicks = Clicks()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val handle = selection.handleAt(down.position)
        when {
            // One finger on a handle; with a second one it is a pinch, which the handle leaves to the text
            handle >= 0 && currentEvent.changes.count { it.pressed } == 1 -> {
                down.consume()
                dragHandle(selection, isStart = handle == 0, down, area, haptics)
            }
            down.type == PointerType.Mouse -> if (currentEvent.buttons.isPrimaryPressed) mouseSelect(selection, down, area, clicks)
            else -> touchSelect(selection, down, area, haptics, clicks)
        }
    }
}

/** Presses in a row on one spot, as Compose's ClicksCounter counts them: each within the double-tap time and slop of the last. */
private class Clicks {
    private var count = 0
    private var lastTime = 0L
    private var lastPosition = Offset.Zero

    fun press(down: PointerInputChange, configuration: ViewConfiguration): Int {
        val again = count > 0 && down.uptimeMillis - lastTime < configuration.doubleTapTimeoutMillis &&
            (down.position - lastPosition).getDistance() < configuration.touchSlop
        count = if (again) count + 1 else 1
        lastTime = down.uptimeMillis
        lastPosition = down.position
        return count
    }

    /** The press was no click (a long press, a drag, a scroll): the next one starts a new row. */
    fun reset() {
        count = 0
    }
}

private enum class PressEnd { Released, Moved, Taken }

/**
 * How [down]'s press ends: let go, moved past the touch slop (consumed when [takeMove], else left to the list's
 * scroll), or taken (a second finger for the pinch, or a handler under it).
 */
private suspend fun AwaitPointerEventScope.awaitPressEnd(down: PointerInputChange, takeMove: Boolean): PressEnd {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return PressEnd.Taken
        if (!change.pressed) return PressEnd.Released
        if (change.isConsumed || event.changes.count { it.pressed } > 1) return PressEnd.Taken
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
            if (takeMove) change.consume()
            return PressEnd.Moved
        }
    }
}

/**
 * A touch: held still, the word under it, then a drag extends the selection a word at a time either way. Taps: the
 * first ends a selection, a second selects the word, a third the whole text ([tapped]).
 */
private suspend fun AwaitPointerEventScope.touchSelect(selection: ListSelection, down: PointerInputChange, area: AreaHolder, haptics: HapticFeedback, clicks: Clicks) {
    val count = clicks.press(down, viewConfiguration)
    // A press that stopped a fling, or a pager settling, belongs to the list: it took the down in its own pass, after
    // this one saw it. Held still, it would otherwise select the word where the text stopped.
    if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.id == down.id && it.isConsumed }) {
        clicks.reset()
        return
    }
    when (withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPressEnd(down, takeMove = false) }) {
        PressEnd.Released -> return tapped(selection, down, area, count, extend = false)
        // A move is the list's scroll, a second finger its pinch
        PressEnd.Moved, PressEnd.Taken -> return clicks.reset()
        null -> clicks.reset()
    }
    val coordinates = area.coordinates ?: return
    val word = selection.pointAt(down.position, coordinates, exact = true)?.let(selection::wordAt) ?: return
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    selection.begin(word.first, word.second)
    holdDrag(selection, down, down.position, area, haptics, startMoves = false) { finger ->
        val c = area.coordinates
        val w = c?.let { selection.pointAt(finger.within(it), it, exact = false) }?.let { p -> selection.wordAt(p) ?: (p to p) }
        when {
            w == null -> false
            w.first < word.first -> {
                selection.select(w.first, word.second)
                true
            }
            else -> {
                selection.select(word.first, maxOf(w.second, word.second))
                false
            }
        }
    }
}

/**
 * A tap or click, the [count]th in a row: the first ends a selection and stays the line's tap (as desktop's click),
 * the second selects the word, the third the whole text; with [extend] (Shift), a click moves the nearer end to it.
 */
private fun tapped(selection: ListSelection, down: PointerInputChange, area: AreaHolder, count: Int, extend: Boolean) {
    val coordinates = area.coordinates
    val start = selection.start
    val end = selection.end
    if (extend && coordinates != null && start != null && end != null && selection.hasSelection) {
        val point = selection.pointAt(down.position, coordinates, exact = false) ?: return
        if (point < start) selection.select(point, end) else selection.select(start, point)
        return
    }
    val point = if (count > 1 && coordinates != null) selection.pointAt(down.position, coordinates, exact = true) else null
    val range = when {
        point == null -> null
        count == 2 -> selection.wordAt(point)
        else -> selection.unitAt(point)
    }
    if (range != null) selection.begin(range.first, range.second) else selection.clear()
}

/** A handle: the text under its point follows the finger, past the other end too (the two swap). */
private suspend fun AwaitPointerEventScope.dragHandle(selection: ListSelection, isStart: Boolean, down: PointerInputChange, area: AreaHolder, haptics: HapticFeedback) {
    val coordinates = area.coordinates ?: return
    val spot = selection.spot(isStart, coordinates) ?: return
    val fixed = (if (isStart) selection.end else selection.start) ?: return
    // From the finger to the handle's point, just above its row's bottom
    val toPoint = Offset(spot.x, spot.bottom - 1f) - down.position
    holdDrag(selection, down, down.position, area, haptics, startMoves = isStart) { finger ->
        val c = area.coordinates
        val p = c?.let { selection.pointAt((finger + toPoint).within(it), it, exact = false) }
        if (p != null && p != fixed) selection.select(fixed, p)
        (p ?: fixed) < fixed
    }
}

/**
 * A mouse, as desktop: a drag selects from where it was pressed; clicks as [tapped], Shift+click extending the
 * selection.
 */
private suspend fun AwaitPointerEventScope.mouseSelect(selection: ListSelection, down: PointerInputChange, area: AreaHolder, clicks: Clicks) {
    val count = clicks.press(down, viewConfiguration)
    val shift = currentEvent.keyboardModifiers.isShiftPressed
    when (awaitPressEnd(down, takeMove = true)) {
        PressEnd.Released -> tapped(selection, down, area, count, extend = shift)
        PressEnd.Taken -> clicks.reset()
        PressEnd.Moved -> {
            clicks.reset()
            val coordinates = area.coordinates ?: return
            val anchor = selection.pointAt(down.position, coordinates, exact = false) ?: return
            selection.begin(anchor, anchor)
            val from = currentEvent.changes.firstOrNull { it.id == down.id }?.position ?: down.position
            holdDrag(selection, down, from, area, haptics = null, startMoves = false) { finger ->
                val p = area.coordinates?.let { selection.pointAt(finger.within(it), it, exact = false) }
                if (p != null) selection.select(anchor, p)
                (p ?: anchor) < anchor
            }
        }
    }
}

/**
 * Follows [down]'s pointer until it is let go, its events kept from the list (no scroll) and the lines (no tap); a
 * second finger ends it, leaving the pinch to the text. [moveTo] applies the drag at a finger position: from [from]
 * on (at once when the pointer already moved there), and again each frame the list scrolls under a finger held at an
 * edge ([edgeScroll]). It tells whether the end it moved is the start, for the magnifier; [startMoves] says so before
 * any move.
 */
private suspend fun AwaitPointerEventScope.holdDrag(
    selection: ListSelection,
    down: PointerInputChange,
    from: Offset,
    area: AreaHolder,
    haptics: HapticFeedback?,
    startMoves: Boolean,
    moveTo: (Offset) -> Boolean,
) {
    var finger = from
    fun apply() {
        val start = selection.start
        val end = selection.end
        val movedStart = moveTo(finger)
        if (haptics != null && (selection.start != start || selection.end != end)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selection.magnifier = magnifierSource(selection, area, finger, movedStart)
    }
    val slop = viewConfiguration.touchSlop
    selection.dragging = true
    selection.redrag = ::apply
    try {
        // A press on a handle changes nothing until it moves
        if (from != down.position) apply() else selection.magnifier = magnifierSource(selection, area, finger, startMoves)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (event.changes.count { it.pressed } > 1) break
            event.changes.forEach { it.consume() }
            if (!change.pressed) break
            if (change.position != finger) {
                finger = change.position
                // Only a move toward an edge scrolls: a finger resting in a band (a long press on the first or last
                // row, or anywhere in half of a short pane) jitters, and a handle pulled away from the edge it starts
                // near asks for no scroll toward it
                val speed = edgeSpeed(finger.y, size.height)
                selection.edgeSpeed = when {
                    speed < 0f && finger.y < from.y - slop -> speed
                    speed > 0f && finger.y > from.y + slop -> speed
                    else -> 0f
                }
                apply()
            }
        }
    } finally {
        selection.dragging = false
        selection.edgeSpeed = 0f
        selection.redrag = null
        selection.magnifier = Offset.Unspecified
    }
}

/** The magnifier's source: the moved end's row, where the finger is along it; none while the finger is off the area. */
private fun magnifierSource(selection: ListSelection, area: AreaHolder, finger: Offset, isStart: Boolean): Offset {
    val coordinates = area.coordinates ?: return Offset.Unspecified
    if (finger.y < 0f || finger.y > coordinates.size.height) return Offset.Unspecified
    val spot = selection.spot(isStart, coordinates) ?: return Offset.Unspecified
    return Offset(finger.x, (spot.top + spot.bottom) / 2f)
}

private fun Offset.within(coordinates: LayoutCoordinates) =
    Offset(x.coerceIn(0f, coordinates.size.width.toFloat()), y.coerceIn(0f, coordinates.size.height.toFloat()))

/**
 * The edge scroll's speed for a finger at [y] in an area [height] px tall, px/s, negative toward the top: from
 * [EdgeMinSpeed] where its band starts to [EdgeMaxSpeed] at the edge and past it; 0 outside the bands.
 */
private fun Density.edgeSpeed(y: Float, height: Int): Float {
    val band = EdgeBand.toPx().coerceAtMost(height / 4f)
    val depth = when {
        y < band -> band - y
        y > height - band -> height - band - y
        else -> return 0f
    }
    val f = (abs(depth) / band).coerceAtMost(1f)
    val speed = EdgeMinSpeed.toPx() + (EdgeMaxSpeed.toPx() - EdgeMinSpeed.toPx()) * f * f
    return if (depth > 0f) -speed else speed
}

/** The band along the top and bottom edges where a held drag scrolls. */
private val EdgeBand = 40.dp

/** The edge scroll's speeds, per second. */
private val EdgeMinSpeed = 150.dp
private val EdgeMaxSpeed = 1500.dp

/** Longest step of one frame of the edge scroll (a frame that came late does not jump). */
private const val MAX_EDGE_STEP_S = 0.05f

/** Scrolls [list] while a drag is held at its top or bottom edge, the drag following the text that comes under it. */
private suspend fun edgeScroll(selection: ListSelection, list: LazyListState) {
    snapshotFlow { selection.edgeSpeed != 0f }.distinctUntilChanged().collectLatest { held ->
        if (!held) return@collectLatest
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            // The text the last frame laid out under the finger, then the next step
            selection.redrag?.invoke()
            list.scrollBy(selection.edgeSpeed * ((now - last) / 1e9f).coerceAtMost(MAX_EDGE_STEP_S))
            last = now
        }
    }
}

@Composable
private fun BoxScope.SelectionBar(bounds: Rect, spec: SelectionMenuSpec, area: IntSize, capture: () -> String, dismiss: () -> Unit, onMore: (String) -> Unit) {
    val c = Zayit.colors
    val density = LocalDensity.current
    var palette by remember { mutableStateOf(false) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(density) { 8.dp.roundToPx() }
    val x = (bounds.center.x - barSize.width / 2f).roundToInt().coerceIn(margin, (area.width - barSize.width - margin).coerceAtLeast(margin))
    val above = bounds.top - barSize.height - margin
    val y = (if (above >= margin) above else bounds.bottom + margin * 3).roundToInt().coerceIn(margin, (area.height - barSize.height - margin).coerceAtLeast(margin))

    fun act(block: (String) -> Unit) {
        val text = trimSelectionEdges(capture()).takeIf { it.isNotBlank() } ?: return
        dismiss()
        block(text)
    }
    // The palette's six swatches narrow to fit a Jelly Star's text (about 232dp) rather than losing the last ones
    val swatch = with(density) { ((area.width - 2 * margin - 4.dp.roundToPx()) / 6).toDp() }.coerceIn(32.dp, 44.dp)
    Row(
        Modifier
            // x and y are physical (LayoutCoordinates): placed from the left edge, not mirrored in RTL as offset() is
            .align(AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset(x, y) }
            .onSizeChanged { barSize = it }
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(c.panel)
            .border(1.dp, c.panelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 2.dp),
    ) {
        if (palette) {
            (HighlightPalette + Color.Transparent).forEach { color ->
                Box(
                    Modifier.size(swatch, 44.dp).clickable(role = Role.Button) { act { spec.onHighlight(color, it) } },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    if (color == Color.Transparent) {
                        Icon(Icons.Outlined.Close, "הסר סימון", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    } else {
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(color))
                    }
                }
            }
        } else {
            BarButton(Icons.Outlined.ContentCopy, "העתקה") { act { spec.onAction(SelectionAction.Copy, it) } }
            BarButton(Icons.Outlined.BorderColor, "סמן") { palette = true }
            if (spec.main) BarButton(Icons.Outlined.EditNote, "הוסף הערה") { act { spec.onAction(SelectionAction.AddNote, it) } }
            BarButton(Icons.Outlined.MoreHoriz, "עוד") { act(onMore) }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(icon, label, tint = Zayit.colors.text, modifier = Modifier.size(20.dp))
    }
}

private const val MAQAF = '\u05BE'

/** Characters of the selection the sheet's two-line preview is laid out from. */
private const val SHEET_PREVIEW_CHARS = 400

/**
 * A selection without the maqaf (־) at its edges: Android's word selection takes the one joining a long-pressed word
 * to its neighbour ("־הָאָדָם" from "אֶת־הָאָדָם"), which belongs to neither the quote nor the range; desktop's word
 * boundaries leave it out.
 */
internal fun trimSelectionEdges(text: String): String = text.trim { it.isWhitespace() || it == MAQAF }

/**
 * Desktop's whole text context menu, in its order, on the captured selection. Opened whole, as desktop shows every
 * action at once: half open, it left the last rows and the palette below the screen's edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionSheet(text: String, spec: SelectionMenuSpec, onDismiss: () -> Unit) {
    val c = Zayit.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panel) {
        WindowBackScope {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                // Its start only: a selection the edge scroll drew out may be long, and all of it would be laid out
                Text(
                    text.take(SHEET_PREVIEW_CHARS),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = c.textSecondary,
                    fontFamily = ZayitFonts.book,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
                SelectionAction.entries.forEach { action ->
                    val shown = when (action) {
                        SelectionAction.CopyWithoutNikud -> spec.canCopyWithoutNikud(text)
                        SelectionAction.OpenInPlace -> spec.canOpenInPlace
                        else -> spec.main || !action.mainOnly
                    }
                    if (shown) {
                        SheetRow(action.icon, action.label) {
                            onDismiss()
                            spec.onAction(action, text)
                        }
                    }
                }
                // "סמן" and its six swatches (about 310dp in a row); on a narrower phone (Jelly Star) the swatches take a
                // row of their own and shrink to share it, rather than the last ones being cut off
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    val stacked = maxWidth < 320.dp
                    val dot = if (stacked) ((maxWidth - 8.dp * 5) / 6).coerceIn(24.dp, 34.dp) else 34.dp
                    val swatches: @Composable () -> Unit = {
                        (HighlightPalette + Color.Transparent).forEach { col ->
                            val clear = col == Color.Transparent
                            Box(
                                Modifier.size(dot).clip(CircleShape).background(if (clear) c.selectedRow else col).clickable(role = Role.Button) {
                                    onDismiss()
                                    spec.onHighlight(col, text)
                                },
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) { if (clear) Icon(Icons.Outlined.Close, "הסר סימון", tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("סמן", color = c.text, fontSize = 16.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { swatches() }
                        }
                    } else {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("סמן", color = c.text, fontSize = 16.sp)
                            swatches()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SheetRow(icon: ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = if (enabled) c.textSecondary else c.textDisabled, modifier = Modifier.size(20.dp))
        Text(text, color = if (enabled) c.text else c.textDisabled, fontSize = 16.sp)
    }
}

package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * Port of desktop 0.6.10 features/home/widgets/HomeWidgetsGrid.kt. The grid itself (CellArea), its drag-over logic
 * (Launcher3's: drop outline, reorder alarm, the widgets in the way making room) and the resize frames are desktop's;
 * a touch moves a widget after a long press (in edit mode), and the lifted/leaving animations are left out. Below
 * COMPACT_GRID_WIDTH (every phone, 240 dp included) the widgets go one per row in reading order as on desktop, or two
 * side by side from TWO_COLUMNS_WIDTH (a phone on its side), the wide ones still alone on their row. Desktop can't
 * edit that page (its user widens the window); a phone never can, so here its edit mode is a list of its own: see
 * [CompactWidgets].
 */

/** How long a widget rests over an occupied area before the others make room (desktop's mouse-paced value). */
private const val REORDER_TIMEOUT_MS = 200L

/** From this width the compact page puts two widgets side by side. Android only. */
private val TWO_COLUMNS_WIDTH = 560.dp

/** A widget this many columns wide or more keeps a row of its own in two columns (the zmanim). */
private const val FULL_ROW_COLUMNS = 10

/**
 * The Home page: [header] (the catalog, the logo and the search) then the widgets, on a grid of cells as on Android's
 * home screen. [contentPadding] keeps the page clear of the system bars and the screen's edges.
 */
@Composable
fun HomeWidgetsGrid(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: @Composable ColumnScope.() -> Unit = {},
) {
    AutoScroll(state, scrollState)
    // A widget held: only the drag scrolls the page (AutoScroll), never a fling left from before
    val dragging = state.drag.movingId != null || state.drag.resizing != null
    BoxWithConstraints(modifier) {
        val side = ((maxWidth - MAX_GRID_WIDTH) / 2).coerceAtLeast(0.dp)
        Column(
            modifier =
                Modifier
                    .onGloballyPositioned { state.drag.viewport = it.boundsInRoot() }
                    .verticalScroll(scrollState, enabled = !dragging)
                    .padding(contentPadding)
                    // Top: room for the "−" badges, which stick out of the first row
                    .padding(start = side, top = 8.dp, end = side, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            header()
            // The search sections were 16 dp apart and 32 dp above the widgets: keep that rhythm
            Spacer(Modifier.height(4.dp))
            WidgetCells(state, widgets)
            // Lets the last widgets scroll above the gallery panel
            if (state.editingWidgets) Spacer(Modifier.height(state.drag.galleryHeight))
        }
    }
}

/** How close to the page's top or bottom a drag scrolls it, and how fast at most (dp a frame) right at the edge. */
internal val AUTO_SCROLL_BAND = 64.dp
private val AUTO_SCROLL_SPEED = 14.dp

/**
 * While a widget is dragged near the top or the bottom of the page, the page scrolls, the faster the nearer the edge.
 * Only once the finger has moved that way from where the drag started: one started in a band (a grabber just above
 * the gallery, a bar at the page's top) holds still until the finger goes on towards the edge.
 */
@Composable
private fun AutoScroll(
    state: HomeWidgetsState,
    scrollState: ScrollState,
) {
    val drag = state.drag
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val dragging = drag.movingId != null || drag.resizing != null
    LaunchedEffect(dragging, state.editingWidgets) {
        if (!dragging) return@LaunchedEffect
        val band = with(density) { AUTO_SCROLL_BAND.toPx() }
        val speed = with(density) { AUTO_SCROLL_SPEED.toPx() }
        val covered = if (state.editingWidgets) with(density) { drag.galleryHeight.toPx() } else 0f

        fun towards(): Float {
            val view = drag.viewport
            val y = drag.pointer.y
            val start = drag.origin.y
            // Where each band starts scrolling: its inner edge, or past where the finger started if that was inside it
            val up = minOf(view.top + band, start - slop)
            val down = maxOf(view.bottom - covered - band, start + slop)
            return when {
                y < up -> -(up - y) / band
                y > down -> (y - down) / band
                else -> 0f
            }.coerceIn(-1f, 1f)
        }
        // Frames only while it scrolls: out of the bands, or the page at its end, nothing keeps the clock ticking
        snapshotFlow { drag.pointer }.collectLatest {
            while (true) {
                val direction = towards()
                if (direction == 0f) break
                var scrolled = 0f
                withFrameNanos { scrolled = scrollState.dispatchRawDelta(direction * speed) }
                if (scrolled == 0f) break
            }
        }
    }
}

/** A section across the whole page, as the Home's search sections are ([gapAfter] adds to the page's own gap). */
@Composable
fun FullWidthSection(
    gapAfter: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(bottom = gapAfter)) { content() }
}

@Composable
private fun WidgetCells(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
) {
    // One widget built at a time (see [WhenNearWindow]), and those built so far, for the whole page whatever its layout
    val turns = remember { BuildTurns() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < COMPACT_GRID_WIDTH
        // The gallery follows this page's own test, not the Home's width (the page has margins)
        SideEffect { state.drag.compact = compact }
        if (compact) {
            CompactWidgets(state, widgets, maxWidth, turns)
        } else {
            CellArea(state, widgets, maxWidth, turns)
        }
    }
}

/** How far outside the window, in window heights, a widget is built ahead of being scrolled into view. */
private const val BUILD_AHEAD_WINDOWS = 1f

/**
 * The page's queue for [WhenNearWindow]: the widgets wait for [lock] to be built one at a time, and [built] keeps the
 * ones built so far by id. Each layout of the page (the grid, one column, two columns, the edit list) is a call site of
 * its own, whose slots start empty, so the page has to remember them: a widget it built in one layout comes back at
 * once in the next.
 */
private class BuildTurns {
    val lock = Mutex()

    // Not snapshot state: a slot reads it once, when it is created, so a widget built doesn't recompose every slot
    val built = HashSet<String>()
}

/**
 * The widgets any Home of this run has built, by id. Only the first Home of a run (a cold start, where a frame building
 * everything stalled for seconds) waits its turns for them: a Home built again later (a new tab, one the app had let
 * go) builds those near the window at once, their values kept by rememberOffMain's tags. Main thread only.
 */
private val builtWidgets = HashSet<String>()

/**
 * A slot of [WhenNearWindow]: whether it asked for its turn yet (layout callbacks only), whether it has had it, and
 * where it was last laid out (to tell, when its turn comes, whether it is still near the window).
 */
private class DeferredSlot(
    built: Boolean,
) {
    var asked = false
    var built by mutableStateOf(built)
    var coordinates: LayoutCoordinates? = null
}

/**
 * The widget [content] once its slot ([modifier], the size and place the page gives it whatever is inside) comes within
 * [BUILD_AHEAD_WINDOWS] of the window, then for good; until then an empty slot of that size.
 *
 * Why: a cold start composed, measured and drew every widget of the page in its first frame, those far below the
 * window too (a scrolling Column builds all its children, and Compose records the drawing of children out of view):
 * the Earth's labels, the solar system's label placement, the sky's stars, the Temple's animation, the zmanim cards.
 * On the emulator that one frame held the main thread ~9.5 s ("Skipped 571 frames"), so a launcher widget broadcast
 * timed out (10 s) and the app was killed; a phone pays the same work, only faster. Now the first frame draws the bars,
 * the logo, the search and empty slots; the widgets in and near the window come next, **one at a time**, a frame or two
 * apart in page order ([turns]), so input and broadcasts are served between them; the rest only when the page is
 * scrolled towards them.
 * A Home tab kept in the background is not placed, so it builds nothing until it is shown again.
 *
 * The slot's size never depends on its content (cells, or [HomeWidget.heightAt]), so nothing moves when it fills, and
 * once built a widget stays built: scrolling away and back keeps its state, as before. A widget the page built already,
 * shown by another layout (edit mode on a tablet's two columns, a window resized across the grid's width), skips the
 * queue: it comes back in the switch's own frame, as before, instead of the whole screen going blank and refilling.
 * A slot whose turn comes once it is no longer near (a fling queues every slot it passes) gives the turn up at once,
 * so the widgets the user stopped at aren't kept waiting behind the ones scrolled past.
 */
@Composable
private fun WhenNearWindow(
    id: String,
    turns: BuildTurns,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val slot = remember(id) { DeferredSlot(built = id in turns.built) }
    // Outside the branch below: its turn must not be cancelled (and the next widget let through) by the very
    // recomposition that builds this one
    val scope = rememberCoroutineScope()
    if (slot.built) {
        content(modifier)
    } else {
        Box(
            modifier.onGloballyPositioned { coordinates ->
                slot.coordinates = coordinates
                if (slot.asked || !coordinates.isNearWindow()) return@onGloballyPositioned
                if (id in builtWidgets) {
                    // Built before in this run: no turn to wait, nothing new to load
                    slot.built = true
                    slot.coordinates = null
                    turns.built.add(id)
                    return@onGloballyPositioned
                }
                slot.asked = true
                scope.launch {
                    turns.lock.withLock {
                        // Scrolled away while it waited: no frames spent on it, and it asks again from the next
                        // positioning that finds it near (the page scrolled back)
                        if (slot.coordinates?.isNearWindow() != true) {
                            slot.asked = false
                            return@withLock
                        }
                        slot.built = true
                        slot.coordinates = null
                        turns.built.add(id)
                        builtWidgets.add(id)
                        // Its frame (composed, measured, drawn), then the next one, before another widget is let in
                        withFrameNanos {}
                        withFrameNanos {}
                    }
                }
            },
        )
    }
}

/** Whether this slot lies within [BUILD_AHEAD_WINDOWS] window heights above or below the window (the root). */
private fun LayoutCoordinates.isNearWindow(): Boolean {
    if (!isAttached) return false
    val window = findRootCoordinates().size.height.toFloat()
    if (window <= 0f) return false
    val top = positionInRoot().y
    val bottom = top + size.height
    return top < window * (1f + BUILD_AHEAD_WINDOWS) && bottom > -window * BUILD_AHEAD_WINDOWS
}

/** The height a widget takes on the compact page, [width] wide, [rows] rows tall: its rows', or what its content needs. */
private fun WidgetPlacement.compactHeight(
    width: Dp,
    rows: Int = cell.h,
): Dp = maxOf(CellPitch(width).height(rows), widget.heightAt(width) ?: 0.dp)

/** The rows its content needs [width] wide, counted as [minRows] counts them; 0 when the widget doesn't say. */
private fun HomeWidget.contentRows(width: Dp): Int {
    val content = heightAt(width) ?: return 0
    return ceil((content.value + GRID_GAP.value) / CellPitch(width).y - 0.01f).toInt()
}

/**
 * Whether dragging its bottom on the compact page, [width] wide, changes its height: it has rows to give, and its
 * content doesn't make it taller than its most rows anyway (the zmanim, the molad and the tefila never do).
 */
private fun WidgetPlacement.rowsResizable(width: Dp): Boolean {
    val minRows = widget.minRows(cell.w, SAVED_PITCH)
    val maxRows = maxOf(widget.maxSpan.h, minRows)
    if (maxRows <= minRows) return false
    val content = widget.heightAt(width) ?: return true
    return content < CellPitch(width).height(maxRows)
}

/** The compact page's slots in edit mode: where each is laid out (root pixels), its height, and where one let go was. */
@Stable
private class CompactSlots {
    val tops = mutableStateMapOf<String, Float>()
    val heights = mutableStateMapOf<String, Int>()

    /** A widget let go: how far from its place it was, for it to glide there. */
    val letGo = mutableStateMapOf<String, Float>()

    /** The list's top, as the page scrolls. */
    var columnTop by mutableFloatStateOf(0f)
}

/**
 * Too narrow a window for the grid (every phone): the widgets one per row (or two), in reading order, as desktop's
 * compact mode. Desktop can't edit them there; a phone has nothing wider, so its edit mode (Android only) makes the
 * page a list:
 *  - each widget under a bar ([EditHeader]: a handle that moves it at once, its menu, its name, its "−"), nothing over
 *    the widget itself;
 *  - a press held on a widget picks it up too; the others make room once it is nearer their place than its own, and
 *    let go, the widgets flow onto the grid in that order ([HomeWidgetsLayout.moveTo]);
 *  - a widget that can change height has a strip under it; tapped (the widget, its strip, or its menu's resize), the
 *    strip becomes a grabber that drags its bottom, a row at a time ([HomeWidgetsLayout.resizeRows]). Only the
 *    one picked: the others' strips leave a swipe to the page's scroll.
 * Out of edit mode, a button at the end of the page opens it, in the page's flow instead of floating over a widget.
 */
@Composable
private fun CompactWidgets(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
    width: Dp,
    turns: BuildTurns,
) {
    val drag = state.drag
    val editing = state.editingWidgets
    val density = LocalDensity.current
    val shown = remember(widgets) { widgets.filter { it.widget.isSupported }.sortedWith(ReadingOrder) }
    // What a drop saved, shown until the saved layout comes back (as the grid's layout)
    var order by remember(shown) { mutableStateOf(shown) }
    val slots = remember { CompactSlots() }
    val gap = with(density) { GRID_GAP.toPx() }

    // Where the held widget goes among the others: the place whose top is nearest its own, so it stays where it is
    // until it has travelled half a neighbour's height, up or down, whatever their heights. Counted over the others
    // only, from where they would be without it, so it can't flicker between two places.
    fun heldIndex(list: List<WidgetPlacement>): Int? {
        val id = drag.movingId ?: return null
        if (slots.heights[id] == null) return null
        if (list.none { it.widget.id == id }) return null
        val heldTop = drag.pointer.y - drag.grab.y
        var top = slots.columnTop
        var index = 0
        for (placement in list) {
            if (placement.widget.id == id) continue
            val h = slots.heights[placement.widget.id] ?: 0
            // Past this one only once nearer the place after it (its top + h + gap) than its own (its top)
            if (heldTop < top + (h + gap) / 2f) return index
            top += h + gap
            index++
        }
        return index
    }

    fun withHeldAt(
        list: List<WidgetPlacement>,
        index: Int,
    ): List<WidgetPlacement> {
        val held = list.firstOrNull { it.widget.id == drag.movingId } ?: return list
        val others = list.filter { it !== held }
        return others.take(index) + held + others.drop(index)
    }

    // The rows a widget's dragged bottom gives it, a row (75 dp) at a time, within its limits. Its top moving (the page
    // scrolling under a still finger) counts too, so it keeps growing at the gallery's edge.
    fun liveRows(
        placement: WidgetPlacement,
        resize: Resize,
    ): Int {
        val widget = placement.widget
        val content = widget.contentRows(width)
        val anchor = resize.anchor
        val top = slots.tops[resize.id]
        val scrolled = if (anchor != null && top != null) top - anchor.y else 0f
        val moved = with(density) { (drag.pointer.y - resize.from.y - scrolled).toDp() }
        val cells = (moved.value / CellPitch(width).y).roundToInt()
        val rows = widget.resized(placement.cell.copy(h = maxOf(placement.cell.h, content)), ResizeEdge.BOTTOM, cells, SAVED_PITCH).h
        return maxOf(rows, content)
    }

    // The rows to save: its own, while its content is what sets its height either way (a tablet's grid keeps its area)
    fun savedRows(
        placement: WidgetPlacement,
        rows: Int,
    ): Int {
        val content = placement.widget.contentRows(width)
        return if (rows <= content && placement.cell.h <= content) placement.cell.h else rows
    }

    val heldAt by remember(shown, gap) { derivedStateOf { heldIndex(order) } }
    // Only a new row count recomposes the page, not each move of the finger
    val liveResize by remember(shown, width, density) {
        derivedStateOf {
            val resize = drag.resizing ?: return@derivedStateOf null
            val placement = order.firstOrNull { it.widget.id == resize.id } ?: return@derivedStateOf null
            resize.id to liveRows(placement, resize)
        }
    }

    // Gone (the window wide enough for the grid), its drop must not act on a list it no longer shows
    DisposableEffect(drag) {
        onDispose {
            drag.drop = {}
            drag.anchorOf = { null }
        }
    }
    SideEffect {
        // A move is held from the widget's slot (its bar and grabber included), not from its card
        drag.anchorOf = { id -> slots.tops[id]?.let { Offset(0f, it) } }
        drag.drop = drop@{
            val resize = drag.resizing
            if (resize != null) {
                val placement = order.firstOrNull { it.widget.id == resize.id } ?: return@drop
                val rows = savedRows(placement, liveRows(placement, resize))
                if (rows == placement.cell.h) return@drop
                order = order.map { if (it === placement) it.copy(cell = it.cell.copy(h = rows)) else it }
                state.layout.resizeRows(placement.widget, rows)
                return@drop
            }
            val id = drag.movingId ?: return@drop
            val index = heldIndex(order) ?: return@drop
            // From where the finger leaves it, into its place
            slots.letGo[id] = drag.pointer.y - drag.grab.y - (slots.tops[id] ?: 0f)
            val moved = withHeldAt(order, index)
            if (moved.map { it.widget.id } == order.map { it.widget.id }) return@drop
            order = moved
            state.layout.moveTo(moved.map { it.widget.id })
        }
    }

    Column(
        modifier =
            if (editing) {
                Modifier.onGloballyPositioned { coordinates ->
                    val top = coordinates.positionInRoot().y
                    if (slots.columnTop != top) slots.columnTop = top
                }
            } else {
                Modifier
            },
        verticalArrangement = Arrangement.spacedBy(GRID_GAP),
    ) {
        if (!editing && width >= TWO_COLUMNS_WIDTH) {
            TwoColumns(state, order, width, turns)
        } else {
            val display = heldAt?.let { withHeldAt(order, it) } ?: order
            display.forEach { placement ->
                key(placement.widget.id) {
                    val rows = liveResize?.takeIf { it.first == placement.widget.id }?.second ?: placement.cell.h
                    CompactSlot(placement, state, slots, width, rows, turns)
                }
            }
        }
        // In the page's flow, at its end: the grid's floating pencil would cover a phone's widgets
        if (!editing) EditWidgetsPill(state, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Two by two, in reading order; a wide widget alone on its row (a phone on its side, out of edit mode). */
@Composable
private fun TwoColumns(
    state: HomeWidgetsState,
    shown: List<WidgetPlacement>,
    width: Dp,
    turns: BuildTurns,
) {
    val rows = mutableListOf<List<WidgetPlacement>>()
    var pending: WidgetPlacement? = null
    for (p in shown) {
        if (p.cell.w >= FULL_ROW_COLUMNS) {
            pending?.let { rows += listOf(it) }
            pending = null
            rows += listOf(p)
        } else if (pending == null) {
            pending = p
        } else {
            rows += listOf(pending, p)
            pending = null
        }
    }
    pending?.let { rows += listOf(it) }
    val half = (width - GRID_GAP) / 2
    rows.forEach { row ->
        if (row.size == 1 && row[0].cell.w >= FULL_ROW_COLUMNS) {
            key(row[0].widget.id) {
                WhenNearWindow(row[0].widget.id, turns, Modifier.fillMaxWidth().height(row[0].compactHeight(width))) { slot ->
                    WidgetFrame(row[0], state, movable = false, modifier = slot)
                }
            }
        } else {
            val height = row.maxOf { it.compactHeight(half) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                row.forEach { p ->
                    key(p.widget.id) {
                        WhenNearWindow(p.widget.id, turns, Modifier.weight(1f).height(height)) { slot ->
                            WidgetFrame(p, state, movable = false, modifier = slot)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A widget of the one-column compact page, [rows] rows tall. In edit mode its bar above it and its grabber below; held,
 * it follows the finger (the page lays it out where it will land, its outline there), and let go, it glides there.
 * The same composition in and out of edit mode, so the widgets aren't built again on entering it.
 */
@Composable
private fun CompactSlot(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    slots: CompactSlots,
    width: Dp,
    rows: Int,
    turns: BuildTurns,
) {
    val drag = state.drag
    val id = placement.widget.id
    val editing = state.editingWidgets
    val held = drag.movingId == id
    val dragging = drag.movingId != null || drag.resizing != null
    val resizable = remember(placement, width) { placement.rowsResizable(width) }
    var menuOpen by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }
    LaunchedEffect(held) {
        if (held) return@LaunchedEffect
        val from = slots.letGo[id] ?: return@LaunchedEffect
        settle.snapTo(from)
        slots.letGo.remove(id)
        settle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    DisposableEffect(id) {
        onDispose {
            slots.tops.remove(id)
            slots.heights.remove(id)
            slots.letGo.remove(id)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (editing) {
                    // Before the translation below: where the slot is laid out, not where the finger holds it
                    Modifier
                        .onGloballyPositioned { coordinates ->
                            val top = coordinates.positionInRoot().y
                            if (slots.tops[id] != top) slots.tops[id] = top
                        }.onSizeChanged { if (slots.heights[id] != it.height) slots.heights[id] = it.height }
                } else {
                    Modifier
                },
            ).zIndex(if (held) 3f else 0f)
            .glide(enabled = dragging && !held),
    ) {
        if (held) DropOutline(Modifier.matchParentSize())
        Column(
            if (editing) {
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (held) {
                            // Under the finger, from where the slot is placed now: a new place among the others and
                            // the page scrolled count in the same frame (the slot's top as last reported is a frame
                            // late). Read here, not in composition: a move places this slot alone.
                            val top = coordinates?.positionInRoot()?.y
                            val offset = if (top == null) 0f else drag.pointer.y - drag.grab.y - top
                            placeable.placeWithLayer(0, 0) { translationY = offset }
                        } else {
                            // Let go: gliding into its place, redrawn alone
                            placeable.placeWithLayer(0, 0) { translationY = slots.letGo[id] ?: settle.value }
                        }
                    }
                }
            } else {
                Modifier
            },
        ) {
            if (editing) {
                EditHeader(placement, state, onMenu = { menuOpen = true })
                Spacer(Modifier.height(6.dp))
            }
            // The bar and the grabber around it are built at once (edit mode measures the slot), the widget near the window
            WhenNearWindow(id, turns, Modifier.fillMaxWidth().height(placement.compactHeight(width, rows))) { slot ->
                WidgetFrame(
                    placement = placement,
                    state = state,
                    movable = false,
                    reorderable = editing,
                    rowsResizable = resizable,
                    modifier = slot,
                )
            }
            if (editing && resizable) RowsGrabber(id, state)
        }
        if (menuOpen && isTabInFront()) WidgetMenu(placement, state, movable = false, resizable = resizable, onDismiss = { menuOpen = false })
    }
}

private class Glide {
    var target by mutableStateOf(IntOffset.Zero)
    var animation: Animatable<IntOffset, AnimationVector2D>? = null
}

/**
 * The compact page's slots while a widget is dragged: the others glide to their new places (the grid's [PlacementSpec]);
 * else, and the one held (the finger places it), they're put there at once.
 */
@Composable
private fun Modifier.glide(enabled: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val glide = remember { Glide() }
    return onPlaced { glide.target = it.positionInParent().round() }
        .offset {
            val target = glide.target
            val animation = glide.animation ?: Animatable(target, IntOffset.VectorConverter).also { glide.animation = it }
            if (!enabled) {
                if (animation.targetValue != target || animation.isRunning) scope.launch { animation.snapTo(target) }
                IntOffset.Zero
            } else {
                if (animation.targetValue != target) scope.launch { animation.animateTo(target, PlacementSpec) }
                animation.value - target
            }
        }
}

/**
 * The grid of cells, and Launcher3's Workspace.onDragOver for a widget moved:
 *  - the drop area is the nearest to the held widget's visual centre, whatever is there;
 *  - its outline shows there at once; occupied, an alarm is set (a new area resets it), and when it goes off the
 *    widgets in the way make room (CellLayout.performReorder);
 *  - let go, the widget goes to the area nearest then, the widgets in the way moved.
 * Every reorder is worked out from the layout as it was, never from the previous one. A resize frame's handle resizes
 * the widget cell by cell, the widgets in the way making room the same way.
 */
@Composable
private fun CellArea(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
    gridWidth: Dp,
    turns: BuildTurns,
) {
    val drag = state.drag
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pitch = CellPitch(gridWidth)
    // The widgets shown, and so the cells taken: one this platform can't show leaves its area free, kept in the save
    val hidden = widgets.filterNot { it.widget.isSupported }
    // The layout saved on a drop, shown until the saved one comes back
    var layout by remember(widgets) { mutableStateOf(widgets - hidden.toSet()) }

    /** A point in root pixels, in dp from the grid's top-start corner. */
    fun toGrid(point: Offset): Offset {
        val local = with(density) { (point - drag.gridOrigin).let { Offset(it.x.toDp().value, it.y.toDp().value) } }
        return if (rtl) local.copy(x = gridWidth.value - local.x) else local
    }

    /** The widget moved, and its visual centre. */
    fun held(): Pair<WidgetPlacement, Offset>? {
        val moved = layout.firstOrNull { it.widget.id == drag.movingId } ?: return null
        val size = with(density) { Offset(pitch.width(moved.cell.w).toPx(), pitch.height(moved.cell.h).toPx()) }
        return moved to toGrid(drag.pointer - drag.grab + size / 2f)
    }

    fun dropArea(
        held: WidgetPlacement,
        centre: Offset,
    ) = layout.nearestArea(centre, held.cell.span, pitch, except = held.widget.id, ignoreOccupied = true)

    // Launcher3's mReorderAlarm: each setting is a new one, null cancels it
    var alarm by remember { mutableStateOf<Any?>(null) }

    // When it goes off: the widgets in the way make room at the area nearest then
    fun onAlarm() {
        val (held, centre) = held() ?: return
        val area = dropArea(held, centre)
        drag.preview = layout.reorder(held.copy(cell = area), pitch)
        drag.outline = area
    }

    fun onDragOver() {
        drag.resizing?.let { resize ->
            resizeTo(resize, layout, pitch, density, rtl)
            return
        }
        val (held, centre) =
            held() ?: run {
                alarm = null
                drag.lastReorder = null
                drag.preview = null
                drag.outline = null
                return
            }
        val area = dropArea(held, centre)
        // Where it will land, at once, whatever is there: the others make room once it rests (the alarm)
        drag.outline = area
        if (layout.isVacant(area, except = held.widget.id)) {
            drag.preview = null
            drag.lastReorder = null
        } else if (area != drag.lastReorder) {
            drag.lastReorder = area
            alarm = Any()
        }
    }
    LaunchedEffect(alarm, layout, pitch, rtl) {
        if (alarm == null) return@LaunchedEffect
        delay(REORDER_TIMEOUT_MS)
        onAlarm()
    }
    LaunchedEffect(drag, layout, pitch, rtl) {
        snapshotFlow { listOf(drag.pointer, drag.gridOrigin, drag.movingId, drag.resizing) }.collect { onDragOver() }
    }
    // Gone (the window too narrow), its drop must not act on a layout it no longer shows
    DisposableEffect(drag) { onDispose { drag.drop = {} } }
    SideEffect {
        drag.drop = drop@{
            val final =
                if (drag.resizing != null) {
                    drag.preview
                } else {
                    held()?.let { (held, centre) -> layout.reorder(held.copy(cell = dropArea(held, centre)), pitch) }
                }
            alarm = null
            drag.preview = null
            drag.outline = null
            drag.lastReorder = null
            if (final == null) return@drop
            layout = final
            state.layout.save(final + hidden)
        }
    }

    val shown = drag.preview ?: layout
    val outline = drag.outline?.takeIf { drag.movingId != null }
    val rows = maxOf(shown.bottom(), outline?.bottom ?: 0)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (rows == 0) 0.dp else pitch.height(rows))
            .onGloballyPositioned { drag.gridOrigin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { state.selectedWidget = null } },
    ) {
        outline?.let { DropOutline(Modifier.onCell(it, pitch, gridWidth)) }
        shown.forEach { placement ->
            val id = placement.widget.id
            key(id) {
                // Its place on the grid (and its glide there) is the slot's, built or not
                val cellModifier =
                    Modifier.onCell(
                        cell = placement.cell,
                        pitch = pitch,
                        gridWidth = gridWidth,
                        held = drag.movingId == id,
                        resized = drag.resizing?.id == id,
                    ) {
                        // Where the finger holds it, in the grid's own (start-based) coordinates
                        val topLeft = drag.pointer - drag.grab - drag.gridOrigin
                        val width = pitch.width(placement.cell.w).roundToPx()
                        val x = if (rtl) gridWidth.roundToPx() - topLeft.x.roundToInt() - width else topLeft.x.roundToInt()
                        IntOffset(x, topLeft.y.roundToInt())
                    }
                WhenNearWindow(id, turns, cellModifier) { slot -> WidgetFrame(placement = placement, state = state, modifier = slot) }
            }
        }
    }
}

/** A resize frame's handle at the drag's pointer: the widget's area for it, cell by cell, the others making room. */
private fun resizeTo(
    resize: Resize,
    layout: List<WidgetPlacement>,
    pitch: CellPitch,
    density: Density,
    rtl: Boolean,
) {
    val drag = resize.drag
    val placement = layout.firstOrNull { it.widget.id == resize.id } ?: return
    val moved = with(density) { (drag.pointer - resize.from).let { Offset(it.x.toDp().value, it.y.toDp().value) } }
    // Launcher3's AppWidgetResizeFrame: the handle's travel, in cells, rounded; a corner moves both its sides, the
    // width first, as the rows a widget needs may depend on it
    val across = ((if (rtl) -moved.x else moved.x) / pitch.x).roundToInt()
    val down = (moved.y / pitch.y).roundToInt()
    val area =
        resize.edges.sortedBy { it == ResizeEdge.TOP || it == ResizeEdge.BOTTOM }.fold(placement.cell) { cell, edge ->
            val cells = if (edge == ResizeEdge.START || edge == ResizeEdge.END) across else down
            placement.widget.resized(cell, edge, cells, pitch)
        }
    drag.preview = layout.reorder(placement.copy(cell = area), pitch)
}

private val PlacementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/**
 * Puts a widget on [cell], gliding there when its area changes (the others making room, a drop); while [held], at
 * [heldAt], under the finger. A new [gridWidth] (the window resized) puts it there at once, as being [resized] does.
 */
@Composable
private fun Modifier.onCell(
    cell: CellRect,
    pitch: CellPitch,
    gridWidth: Dp,
    held: Boolean = false,
    resized: Boolean = false,
    heldAt: Density.() -> IntOffset = { IntOffset.Zero },
): Modifier {
    val density = LocalDensity.current
    val target = with(density) { IntOffset((cell.x * pitch.x).dp.roundToPx(), (cell.y * pitch.y).dp.roundToPx()) }
    val position = remember { Animatable(target, IntOffset.VectorConverter) }
    var placedFor by remember { mutableStateOf(gridWidth) }
    var letGo by remember { mutableStateOf<IntOffset?>(null) }
    LaunchedEffect(target, held, gridWidth) {
        if (held) return@LaunchedEffect
        if (gridWidth != placedFor || resized) {
            placedFor = gridWidth
            letGo = null
            position.snapTo(target)
            return@LaunchedEffect
        }
        // Let go: from where the finger left it, into its area
        letGo?.let { position.snapTo(it) }
        letGo = null
        position.animateTo(target, PlacementSpec)
    }
    val z =
        when {
            held || resized -> 3f
            position.isRunning -> 1f
            else -> 0f
        }
    return zIndex(z)
        .offset {
            when {
                held -> heldAt().also { letGo = it }
                resized -> target
                else -> position.value
            }
        }.size(pitch.width(cell.w), pitch.height(cell.h))
}

/** Where a dragged widget will land: a dashed, empty card of its size. */
@Composable
private fun DropOutline(modifier: Modifier = Modifier) {
    val color = Zayit.colors.accent
    Box(
        modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .drawBehind {
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                )
            },
    )
}

/**
 * A resize frame's handle being dragged: which widget, which sides (two for a corner), from where; on the compact page,
 * where the widget's slot was then ([anchor]), for the page scrolled since to count.
 */
internal class Resize(
    val drag: WidgetDrag,
    val id: String,
    val edges: List<ResizeEdge>,
    val from: Offset,
    val anchor: Offset? = null,
)

/**
 * A widget being dragged: moved ([movingId]) or resized ([resizing]), at [pointer] (root pixels); and the grid's
 * answer, Launcher3's drag-over state: the reordered layout it shows ([preview]) and where the widget will land
 * ([outline]).
 */
internal class WidgetDrag {
    var movingId by mutableStateOf<String?>(null)
    var resizing by mutableStateOf<Resize?>(null)
    var pointer by mutableStateOf(Offset.Zero)

    /** Where the drag started (root pixels): the page scrolls once the finger has moved on from there to an edge. */
    var origin = Offset.Zero

    /** Where the finger holds the moved widget, from its top-left corner. */
    var grab = Offset.Zero

    val bounds = mutableMapOf<String, Rect>()

    /** The grid's top-left corner, in root coordinates: it moves as the page scrolls, under a still finger too. */
    var gridOrigin by mutableStateOf(Offset.Zero)

    /** The scrolling page, in root coordinates, for the auto-scroll near its edges. */
    var viewport = Rect.Zero

    var preview by mutableStateOf<List<WidgetPlacement>?>(null)
    var outline by mutableStateOf<CellRect?>(null)

    /** The gallery panel's height in edit mode (desktop's 320 dp, less on a short phone): the page scrolls above it. */
    var galleryHeight by mutableStateOf(WIDGET_GALLERY_HEIGHT)

    /** Whether the page shows the compact list rather than the grid (its own width test); null until it's laid out. */
    var compact by mutableStateOf<Boolean?>(null)

    /** Where the compact page's slot of a widget is (root pixels), a move held from there; null: the widget's own bounds. */
    var anchorOf: (String) -> Offset? = { null }

    /** Launcher3's mLastReorderX/Y: the area last reordered for. */
    var lastReorder: CellRect? = null

    /** Lets the widget go where it shows, and saves the layout (set by the grid). */
    var drop: () -> Unit = {}

    fun startMove(
        id: String,
        pointer: Offset,
    ) {
        grab = pointer - (anchorOf(id) ?: bounds[id]?.topLeft ?: pointer)
        origin = pointer
        this.pointer = pointer
        movingId = id
    }

    fun startResize(
        id: String,
        edges: List<ResizeEdge>,
        pointer: Offset,
    ) {
        origin = pointer
        this.pointer = pointer
        resizing = Resize(this, id, edges, pointer, anchorOf(id))
    }

    fun end() {
        movingId = null
        resizing = null
    }
}

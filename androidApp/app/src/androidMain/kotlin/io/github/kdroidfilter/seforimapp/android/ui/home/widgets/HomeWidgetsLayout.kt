package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

// Port of desktop 0.6.10 features/home/widgets/HomeWidgetsLayout.kt, on [HomeWidgetStore] in place of AppSettings.

/** Further down than any layout a user makes (every widget stacked at its tallest is under 50 rows). */
private const val MAX_SAVED_ROW = 200

/** The full-width grid layouts are saved and checked at: their limits don't depend on the window. */
internal val SAVED_PITCH = CellPitch(MAX_GRID_WIDTH)

/**
 * Arranging works over every subset of the widgets shown (2^n of them, a few hundred MB past 14 on a phone): with more,
 * the layout stays as it is. Android only; desktop's users keep fewer widgets on a page.
 */
private const val MAX_ARRANGED_WIDGETS = 14

/**
 * "zmanim@0,0,13,4;earth@13,0,7,4": each widget's area, in cells. An old or broken save never breaks: unknown widgets
 * are dropped, areas brought within their widget's limits, and a widget overlapping one before it moved to the vacant
 * area nearest; a save of the format before areas keeps its widgets and their order.
 */
internal fun decodeLayout(raw: String?): List<WidgetPlacement> {
    if (raw == null) return defaultLayout()
    if (raw.isNotBlank() && '@' !in raw) return decodeOrderLayout(raw)
    val layout = mutableListOf<WidgetPlacement>()
    for (entry in raw.split(';').filter { it.isNotBlank() }) {
        val widget = availableHomeWidgets.firstOrNull { it.id == entry.substringBefore('@') } ?: continue
        if (layout.any { it.widget.id == widget.id }) continue
        val numbers = entry.substringAfter('@').split(',').map { it.trim().toIntOrNull() }
        if (numbers.size != 4 || numbers.any { it == null }) continue
        val (x, y, w, h) = numbers.map { it!! }
        // A row far beyond any real layout (a corrupted save) would have every search walk to it
        val cell = widget.clamp(CellRect(x, y.coerceAtMost(MAX_SAVED_ROW), w, h), SAVED_PITCH)
        val vacant =
            if (layout.isVacant(cell)) cell else layout.nearestArea(SAVED_PITCH.centre(cell), cell.span, SAVED_PITCH)
        layout += WidgetPlacement(widget, vacant)
    }
    return layout
}

/**
 * The format before widgets had areas, "zmanim:LARGE,earth:MEDIUM": the widgets in their order, each at its default
 * size in the first vacant area, as if added one after the other. The ones removed stay removed.
 */
internal fun decodeOrderLayout(
    raw: String,
    shown: (HomeWidget) -> Boolean = { it.isSupported },
): List<WidgetPlacement> {
    val widgets =
        raw
            .split(',')
            .mapNotNull { entry -> availableHomeWidgets.firstOrNull { it.id == entry.substringBefore(':').trim() } }
            .distinct()
    // The ones this platform shows first, so the ones it can't don't leave holes among them
    val (visible, hidden) = widgets.partition(shown)
    return (visible + hidden).fold(
        emptyList(),
    ) { layout, widget -> layout + WidgetPlacement(widget, layout.firstVacant(widget.defaultSpan)) }
}

internal fun encodeLayout(layout: List<WidgetPlacement>): String =
    layout.joinToString(";") { (widget, c) -> "${widget.id}@${c.x},${c.y},${c.w},${c.h}" }

/** The edits the Home offers on its layout, each saved at once. */
class HomeWidgetsLayout(
    internal val store: HomeWidgetStore,
) {
    init {
        availableHomeWidgets.forEach { it.applySettings(store) }
    }

    fun current(): List<WidgetPlacement> = decodeLayout(store.homeWidgetsLayoutFlow.value)

    fun save(layout: List<WidgetPlacement>) = store.setHomeWidgetsLayout(encodeLayout(layout))

    /** The widgets shown, and the ones this platform can't show: those take no cells here, but stay in the save. */
    private fun edit(change: (List<WidgetPlacement>) -> List<WidgetPlacement>) {
        val (shown, hidden) = current().partition { it.widget.isSupported }
        val changed = change(shown)
        save(changed + hidden.filter { h -> changed.none { it.widget.id == h.widget.id } })
    }

    /** At its default size, in the first vacant area (row after row), as Android adds a widget from its picker. */
    fun add(widget: HomeWidget) {
        if (current().any { it.widget.id == widget.id }) return
        edit { layout -> layout + WidgetPlacement(widget, layout.firstVacant(widget.defaultSpan)) }
    }

    /** Returns what was removed, for an undo to [restore] it. Its area stays empty: nothing moves into it. */
    fun remove(widget: HomeWidget): RemovedWidget? {
        val layout = current()
        val removed = layout.firstOrNull { it.widget.id == widget.id } ?: return null
        save(layout - removed)
        return RemovedWidget(removed)
    }

    /** Back on its area, or the vacant one nearest if another widget took it since. */
    fun restore(removed: RemovedWidget) {
        val placement = removed.placement
        if (current().any { it.widget.id == placement.widget.id }) return
        edit { layout ->
            val cell = placement.cell
            val vacant = if (layout.isVacant(cell)) cell else layout.nearestArea(SAVED_PITCH.centre(cell), cell.span, SAVED_PITCH)
            layout + placement.copy(cell = vacant)
        }
    }

    /** [widget] on [cell] (within its limits), the widgets in the way making room. */
    fun place(
        widget: HomeWidget,
        cell: CellRect,
    ) {
        val placement = WidgetPlacement(widget, widget.clamp(cell, SAVED_PITCH))
        // One this platform can't show takes no cells here: it just moves, pushing nothing
        if (!widget.isSupported) {
            save(current().map { if (it.widget.id == widget.id) placement else it })
            return
        }
        edit { it.reorder(placement, SAVED_PITCH) }
    }

    /**
     * Android only, the compact page's move: the widgets in [ids]' order, each its size kept, flowed onto the grid in that
     * order ([flowedInOrder]), so the page reads them so on a phone, and on a tablet too.
     */
    fun moveTo(ids: List<String>) =
        edit { shown ->
            shown
                .sortedWith(ReadingOrder)
                .sortedBy { placement -> ids.indexOf(placement.widget.id).let { if (it < 0) Int.MAX_VALUE else it } }
                .flowedInOrder()
        }

    /**
     * Android only, the compact page's resize: [widget] [rows] rows tall (within its limits). The others keep their
     * areas when the ones in the way can make room without changing the reading order (always, for a widget made
     * shorter), so a tablet's layout stays; else they flow in that order.
     */
    fun resizeRows(
        widget: HomeWidget,
        rows: Int,
    ) = edit { shown ->
        val reading = shown.sortedWith(ReadingOrder)
        val placement = reading.firstOrNull { it.widget.id == widget.id } ?: return@edit shown
        val resized = placement.copy(cell = widget.clamp(placement.cell.copy(h = rows), SAVED_PITCH))
        if (resized.cell == placement.cell) return@edit shown
        val reordered = shown.reorder(resized, SAVED_PITCH)
        if (reordered.sortedWith(ReadingOrder).map { it.widget.id } == reading.map { it.widget.id }) {
            reordered
        } else {
            reading.map { if (it.widget.id == widget.id) resized else it }.flowedInOrder()
        }
    }

    private var arrangements = 0
    private var arranging = false

    /** Laid out again, resized within their limits, with the fewest holes and rows; again, another way (see [rearranged]). */
    suspend fun arrange() {
        if (arranging) return
        arranging = true
        try {
            val shown = current().filter { it.widget.isSupported }
            if (shown.size > MAX_ARRANGED_WIDGETS) return
            val arranged = withContext(Dispatchers.Default) { shown.rearranged(SAVED_PITCH, arrangements++) }
            // A widget added, removed or moved, or the layout reset, while the search ran: that edit stands
            if (current().filter { it.widget.isSupported } == shown) edit { arranged }
        } finally {
            arranging = false
        }
    }

    fun reset() = store.setHomeWidgetsLayout(null)

    fun <T> flow(setting: Setting<T>): StateFlow<T> = store.flow(setting)

    /** Saved, and the widget it belongs to grown as its new content asks, the widgets in the way making room. */
    fun <T> set(
        setting: Setting<T>,
        value: T,
    ) {
        store[setting] = value
        availableHomeWidgets.firstOrNull { setting in it.settings?.all.orEmpty() }?.let(::settingsChanged)
    }

    fun resetSettings(widget: HomeWidget) {
        widget.settings?.let(store::reset)
        settingsChanged(widget)
    }

    private fun settingsChanged(widget: HomeWidget) {
        widget.applySettings(store)
        current().firstOrNull { it.widget.id == widget.id }?.let { place(widget, it.cell) }
    }
}

data class RemovedWidget(
    val placement: WidgetPlacement,
)

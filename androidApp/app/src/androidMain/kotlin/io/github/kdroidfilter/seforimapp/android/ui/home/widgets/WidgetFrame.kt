package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

// Port of desktop 0.6.10 features/home/widgets/WidgetFrame.kt for touch: a long press opens the widget's menu (desktop's
// right click), as a bottom sheet on a phone and a menu on a tablet's grid. In edit mode, on the grid, a "−" badge on
// its corner removes it, a long press then a drag moves it, and a tap selects it for its resize frame, a handle on each
// side and corner. The compact page of a phone (Android only) puts its controls around the widget instead, never over
// it: [EditHeader] above, [RowsGrabber] below.

internal const val HOME_WIDGETS_EDIT = "עריכת ווידג'טים"
internal const val HOME_WIDGETS_REMOVE = "הסרת הווידג'ט"
internal const val HOME_WIDGETS_RESIZE = "שינוי גודל"
internal const val HOME_WIDGETS_OPTIONS = "אפשרויות…"

// Desktop's home_widgets_gallery_hint, its part on moving
private const val HOME_WIDGETS_MOVE = "גררו ווידג'ט בדף כדי להזיז אותו"
private const val MORE_OPTIONS = "אפשרויות נוספות"

/**
 * A widget on the page. Not [movable] (the compact page of a phone), it is moved as a list's item when [reorderable]
 * (held, then dragged), and [rowsResizable] offers its menu's resize, which picks it for its grabber.
 */
@Composable
internal fun WidgetFrame(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    movable: Boolean = true,
    reorderable: Boolean = false,
    rowsResizable: Boolean = false,
) {
    val widget = placement.widget
    val drag = state.drag
    var menuOpen by remember { mutableStateOf(false) }
    val editing = state.editingWidgets
    val selected = movable && editing && state.selectedWidget == widget.id
    val resizable = if (movable) widget.minSpan != widget.maxSpan else rowsResizable
    val dragging = drag.movingId == widget.id
    // Lifted while moved; put down as it glides into its area
    val lift by animateFloatAsState(if (dragging) 1.03f else 1f, spring(stiffness = Spring.StiffnessMedium))
    val elevation by animateDpAsState(if (dragging) 16.dp else 0.dp, spring(stiffness = Spring.StiffnessMedium))
    // A removed widget must not stay a drop target where it used to be
    DisposableEffect(widget.id) { onDispose { drag.bounds.remove(widget.id) } }

    // Min constraints reach the widget, so it fills its cell
    Box(
        propagateMinConstraints = true,
        modifier =
            modifier
                .onGloballyPositioned { drag.bounds[widget.id] = Rect(it.positionInRoot(), it.size.toSize()) }
                .then(if (!editing) Modifier.longPressMenu { menuOpen = true } else Modifier)
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                }
                // Not clipped: the "−" badge and the resize handles stick out of the widget
                .shadow(elevation, WidgetShape, clip = false),
    ) {
        widget.Content(state, Modifier)
        // matchParentSize: the handles take the widget's size instead of passing it their min constraints
        Box(Modifier.matchParentSize()) {
            if (editing) {
                // Swallows taps meant for the widget: a tap selects it, a long press and move moves it (a quick swipe
                // still scrolls the page: the long press is cancelled by the scroll taking the move)
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (movable || reorderable) {
                                Modifier.trackedDrag(
                                    afterLongPress = true,
                                    onStart = {
                                        // Moved, it's the one selected: its resize frame shows as soon as it's put down
                                        if (movable) state.selectedWidget = widget.id
                                        drag.startMove(widget.id, it)
                                    },
                                    onMove = { drag.pointer = it },
                                    onEnd = {
                                        if (drag.movingId == widget.id) drag.drop()
                                        drag.end()
                                    },
                                    onCancel = drag::end,
                                )
                            } else {
                                Modifier
                            },
                        ).pointerInput(widget.id, movable, rowsResizable) {
                            detectTapGestures {
                                state.selectedWidget =
                                    when {
                                        movable -> widget.id
                                        // The compact page: a tap picks it for its grabber, a second one puts it down
                                        rowsResizable && state.selectedWidget != widget.id -> widget.id
                                        else -> null
                                    }
                            }
                        },
                )
                // The compact page's widget picked (a tap, its menu's resize): outlined, its grabber brought into view
                if (!movable && state.selectedWidget == widget.id) {
                    Box(Modifier.matchParentSize().border(2.dp, Zayit.colors.accent, WidgetShape))
                }
                // On its corner (centred on it, a finger's target); inside it while selected, clear of the resize
                // frame's corner handle, as desktop's (16 dp in rather than its 12: a finger's targets are larger).
                // Under the frame: where their targets meet, a touch goes to the handle, never to the "−". The
                // compact page has its "−" in the widget's bar instead.
                if (movable) {
                    val badge = if (selected) 6.dp else (-22).dp
                    RemoveBadge(
                        onClick = { state.removeWidget(widget) },
                        modifier = Modifier.align(Alignment.TopStart).offset(badge, badge),
                    )
                }
                if (selected) ResizeFrame(widget.id, drag)
            }
        }
        if (menuOpen && isTabInFront()) WidgetMenu(placement, state, movable, resizable, onDismiss = { menuOpen = false })
    }
}

/**
 * A long press anywhere on the widget, its own controls included, seen before them (the initial pass) and without
 * taking their taps or the page's scroll: only a press held still past the long-press timeout opens the menu, and
 * the rest of that gesture is kept from them.
 */
private fun Modifier.longPressMenu(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Released or moved before the timeout: a tap or a scroll, not the menu's
            val longPressed =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var done = false
                    while (!done) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        done = change == null || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    }
                    false
                } ?: true
            if (longPressed) {
                onLongPress()
                // The rest of the gesture is the menu's: no tap, no scroll under it
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }

@Composable
private fun RemoveBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    // A 44 dp target around desktop's 24 dp badge
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = HOME_WIDGETS_REMOVE },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .shadow(3.dp, CircleShape)
                .background(c.textSecondary, CircleShape)
                .border(1.dp, c.panel.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The macOS "−": a bar, drawn so no font can miss it
            Box(Modifier.size(width = 10.dp, height = 2.dp).background(c.panel, RoundedCornerShape(1.dp)))
        }
    }
}

/**
 * Android only: a compact page widget's bar in edit mode, above it so nothing covers the widget (desktop's badge on its
 * corner hid a phone widget's first words). From the start: a handle that moves it at once, its menu, its name, and
 * the "−" that removes it, at the far end.
 */
@Composable
internal fun EditHeader(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val widget = placement.widget
    val drag = state.drag
    val c = Zayit.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(c.panel.copy(alpha = 0.9f))
            .border(1.dp, c.panelBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drags at once, no long press: the handle wins over the page's scroll as soon as the finger moves
        Box(
            Modifier
                .size(48.dp)
                .trackedDrag(
                    afterLongPress = false,
                    onStart = { drag.startMove(widget.id, it) },
                    onMove = { drag.pointer = it },
                    onEnd = {
                        if (drag.movingId == widget.id) drag.drop()
                        drag.end()
                    },
                    onCancel = drag::end,
                ).semantics { contentDescription = HOME_WIDGETS_MOVE },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DragIndicator, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        }
        // The menu beside the handle, the name between it and the "−": a tap a little off the menu never removes
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onMenu)
                .semantics { contentDescription = MORE_OPTIONS },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MoreVert, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
        Text(
            widget.title,
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { state.removeWidget(widget) }
                .semantics { contentDescription = HOME_WIDGETS_REMOVE },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp).background(c.textSecondary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Remove, null, tint = c.panel, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Android only: the compact page's resize, a strip under a widget that can change height. Picked (a tap on the
 * widget or the strip, or the widget's menu's resize; Launcher3 too shows one widget's resize frame at a time), it
 * becomes a grabber that drags the widget's bottom a row at a time (the page works the rows out), and it's brought
 * into view above the gallery, clear of the band where a drag scrolls the page. The width is the page's, so the
 * bottom is the only side to drag. Not picked, a swipe on it scrolls the page.
 */
@Composable
internal fun RowsGrabber(
    id: String,
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val drag = state.drag
    val c = Zayit.colors
    val density = LocalDensity.current
    val requester = remember { BringIntoViewRequester() }
    val picked = state.selectedWidget == id
    LaunchedEffect(picked) {
        if (!picked || drag.movingId != null) return@LaunchedEffect
        // Laid out first: the widgets' bars have just come in above it
        withFrameNanos {}
        val band = with(density) { AUTO_SCROLL_BAND.toPx() }
        val height = with(density) { (GRABBER_HEIGHT + drag.galleryHeight).toPx() }
        requester.bringIntoView(Rect(0f, -band, 1f, height + band))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(GRABBER_HEIGHT)
            .bringIntoViewRequester(requester)
            .then(
                if (picked) {
                    Modifier.trackedDrag(
                        afterLongPress = false,
                        onStart = { drag.startResize(id, listOf(ResizeEdge.BOTTOM), it) },
                        onMove = { drag.pointer = it },
                        onEnd = {
                            if (drag.resizing != null) drag.drop()
                            drag.end()
                        },
                        onCancel = drag::end,
                    )
                } else {
                    Modifier.clickable(role = Role.Button) { state.selectedWidget = id }
                },
            ).semantics { contentDescription = HOME_WIDGETS_RESIZE },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = if (picked) 48.dp else 40.dp, height = 5.dp)
                .background(if (picked) c.accent else c.textSecondary.copy(alpha = 0.35f), RoundedCornerShape(50)),
        )
    }
}

private val GRABBER_HEIGHT = 48.dp

/** The widget's menu: its own items, then resize, remove and edit widgets (desktop's right-click menu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetMenu(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    movable: Boolean,
    resizable: Boolean,
    onDismiss: () -> Unit,
) {
    val widget = placement.widget
    val c = Zayit.colors
    val items =
        buildList {
            widget.menuItems(state).forEach { item ->
                add(Triple(item.label, if (item.checked == true) Icons.Outlined.Check else Icons.Outlined.Settings, item.onClick))
            }
            if (resizable) {
                add(
                    Triple(HOME_WIDGETS_RESIZE, Icons.Outlined.OpenInFull) {
                        // As on Android, its resize frame (the grid), its grabber (the compact page)
                        state.editingWidgets = true
                        state.selectedWidget = widget.id
                    },
                )
            }
            add(Triple(HOME_WIDGETS_REMOVE, Icons.Outlined.RemoveCircleOutline) { state.removeWidget(widget) })
            if (!state.editingWidgets) add(Triple(HOME_WIDGETS_EDIT, Icons.Outlined.Edit) { state.editingWidgets = true })
        }
    if (movable) {
        // A tablet's grid: a menu at the widget, as desktop's
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, containerColor = c.panel, offset = DpOffset(16.dp, (-48).dp)) {
            items.forEach { (label, icon, action) ->
                DropdownMenuItem(
                    text = { Text(label, color = c.text, fontSize = 15.sp) },
                    leadingIcon = { Icon(icon, null, tint = c.textSecondary) },
                    onClick = {
                        onDismiss()
                        action()
                    },
                )
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.panel) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
                Text(widget.title, color = c.text, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                HorizontalDivider(color = c.divider)
                items.forEach { (label, icon, action) -> SheetRow(label, icon) { onDismiss(); action() } }
            }
        }
    }
}

@Composable
internal fun SheetRow(label: String, icon: ImageVector?, onClick: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
            Box(Modifier.width(16.dp))
        }
        Text(label, color = c.text, fontSize = 16.sp)
    }
}

/**
 * Android's widget resize frame: an outline, and a handle on each side and each corner that drags that side, or both
 * a corner's, cell by cell (the grid works the size out, see [HomeWidgetsGrid]).
 */
@Composable
private fun BoxScope.ResizeFrame(
    id: String,
    drag: WidgetDrag,
) {
    val c = Zayit.colors
    Box(Modifier.matchParentSize().border(2.dp, c.accent, WidgetShape))
    val handles =
        listOf(
            listOf(ResizeEdge.START) to Alignment.CenterStart,
            listOf(ResizeEdge.END) to Alignment.CenterEnd,
            listOf(ResizeEdge.TOP) to Alignment.TopCenter,
            listOf(ResizeEdge.BOTTOM) to Alignment.BottomCenter,
            // The "−" badge goes inside the widget while the frame shows, clear of this one
            listOf(ResizeEdge.START, ResizeEdge.TOP) to Alignment.TopStart,
            listOf(ResizeEdge.END, ResizeEdge.TOP) to Alignment.TopEnd,
            listOf(ResizeEdge.START, ResizeEdge.BOTTOM) to Alignment.BottomStart,
            listOf(ResizeEdge.END, ResizeEdge.BOTTOM) to Alignment.BottomEnd,
        )
    for ((edges, alignment) in handles) {
        // Centred on its side or corner; a 48 dp target (a finger's) around a 20 dp handle, desktop's 18 dp one
        val outward =
            DpOffset(
                if (ResizeEdge.START in edges) (-24).dp else if (ResizeEdge.END in edges) 24.dp else 0.dp,
                if (ResizeEdge.TOP in edges) (-24).dp else if (ResizeEdge.BOTTOM in edges) 24.dp else 0.dp,
            )
        Box(
            Modifier
                .align(alignment)
                .offset(outward.x, outward.y)
                .size(48.dp)
                .trackedDrag(
                    afterLongPress = false,
                    onStart = { drag.startResize(id, edges, it) },
                    onMove = { drag.pointer = it },
                    onEnd = {
                        if (drag.resizing != null) drag.drop()
                        drag.end()
                    },
                    onCancel = drag::end,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).shadow(3.dp, CircleShape).background(c.panel, CircleShape).border(2.dp, c.accent, CircleShape))
        }
    }
}

/** A drag on this element, reported in root coordinates (where the grid is); after a long press for a widget moved. */
@Composable
internal fun Modifier.trackedDrag(
    afterLongPress: Boolean,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val holder = remember { CoordinatesHolder() }

    fun root(local: Offset) = holder.coordinates?.takeIf { it.isAttached }?.localToRoot(local)
    return onGloballyPositioned { holder.coordinates = it }
        .pointerInput(afterLongPress) { cancellableDrag(afterLongPress, ::root, onStart, onMove, onEnd, onCancel) }
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * detectDragGestures, [onCancel] included when the element goes away mid-drag (edit mode closed, the window narrowed):
 * detectDragGestures then just stops, calling neither its end nor its cancel.
 */
private suspend fun PointerInputScope.cancellableDrag(
    afterLongPress: Boolean,
    root: (Offset) -> Offset?,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    var dragging = false
    val start: (Offset) -> Unit = { position ->
        root(position)?.let {
            dragging = true
            onStart(it)
        }
    }
    try {
        if (afterLongPress) {
            detectDragGesturesAfterLongPress(
                onDragStart = start,
                onDrag = { change, _ ->
                    change.consume()
                    root(change.position)?.let(onMove)
                },
                onDragEnd = {
                    dragging = false
                    onEnd()
                },
                onDragCancel = {
                    dragging = false
                    onCancel()
                },
            )
        } else {
            detectDragGestures(
                onDragStart = start,
                onDrag = { change, _ ->
                    change.consume()
                    root(change.position)?.let(onMove)
                },
                onDragEnd = {
                    dragging = false
                    onEnd()
                },
                onDragCancel = {
                    dragging = false
                    onCancel()
                },
            )
        }
    } finally {
        if (dragging) onCancel()
    }
}

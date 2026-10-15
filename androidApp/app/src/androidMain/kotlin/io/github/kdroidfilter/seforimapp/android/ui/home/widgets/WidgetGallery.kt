package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Port of desktop 0.6.1 features/home/widgets/WidgetGallery.kt and WidgetOptionsDialog.kt for touch: the gallery panel
// over the bottom of the Home (a tap adds a widget in the first vacant area), the pencil that opens it, the undo bar,
// and a widget's options (a bottom sheet on a phone, a dialog of desktop's size on a tablet). Android only: the compact
// page opens the edit mode from a button in its flow (no pencil over its widgets), and on a short screen the gallery
// opens folded to its buttons, its widgets one tap away, so the page keeps room to be edited.

private const val PREVIEW_SCALE = 0.42f

/** The gallery shows widgets at their size on a full-width grid. */
private val GALLERY_PITCH = CellPitch(MAX_GRID_WIDTH)

/** Room the gallery panel takes at most at the bottom of the Home (desktop's), for the page to scroll its last widgets above it. */
val WIDGET_GALLERY_HEIGHT = 320.dp

private const val HOME_WIDGETS_DONE = "סיום"
private const val HOME_WIDGETS_ADD = "הוספה"
private const val HOME_WIDGETS_GALLERY_TITLE = "ווידג'טים"
private const val HOME_WIDGETS_ALL_PLACED = "כל הווידג'טים כבר בדף"
private const val HOME_WIDGETS_ARRANGE = "סידור חכם"
private const val HOME_WIDGETS_RESET = "שחזור ברירת המחדל"
private const val HOME_WIDGETS_UNDO = "ביטול"

// Desktop's home_widgets_gallery_hint, told for a finger: a tap adds, a long press drags, the handles resize
private const val GALLERY_HINT_GRID = "לחצו על ווידג'ט כדי להוסיף אותו · לחיצה ארוכה וגרירה מזיזה ווידג'ט בדף · לחצו עליו ומשכו את הידיות כדי לשנות את גודלו"
private const val GALLERY_HINT_COMPACT = "לחצו על ווידג'ט כדי להוסיף אותו · גררו ווידג'ט בדף כדי להזיז אותו · לחצו על ווידג'ט בדף ומשכו את הפס שמתחתיו כדי לשנות את גובהו"

/** Below this Home height the gallery opens folded (the Jelly Star's Home is about 300 dp tall). */
private val GALLERY_FOLD_HEIGHT = 520.dp

/** The gallery's row of buttons: "סיום" and the round ones. */
private val GALLERY_BAR_HEIGHT = 44.dp

/** Adding on a short screen, the gallery leaves the page at least this much above it (the page isn't edited then). */
private val GALLERY_ADDING_GAP = 64.dp

/**
 * The gallery floating over the bottom of the Home in edit mode, the pencil that opens it, the undo bar after a
 * removal, and a widget's options.
 */
@Composable
fun BoxScope.HomeWidgetsOverlay(
    state: HomeWidgetsState,
    placed: List<WidgetPlacement>,
) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        // The page's own test (its width less its margins); the Home's until the page is laid out
        val compact = state.drag.compact ?: (maxWidth < COMPACT_GRID_WIDTH)
        val tight = maxWidth < 300.dp
        // No room for the title beside the buttons: the Jelly Star's 240 dp
        val narrow = maxWidth < 360.dp
        val foldable = maxHeight < GALLERY_FOLD_HEIGHT
        var adding by rememberSaveable { mutableStateOf(false) }
        // Each edit opens folded again: the page is what a short screen edits first
        LaunchedEffect(state.editingWidgets) { if (!state.editingWidgets) adding = false }
        val open = !foldable || adding
        val margin = if (tight) 6.dp else 8.dp
        val inset = if (tight) 8.dp else 12.dp
        val galleryHeight =
            when {
                // Folded: its row of buttons
                !open -> GALLERY_BAR_HEIGHT + inset * 2 + 16.dp
                // Adding on a short screen: most of the Home, for a row of widgets under the buttons (the Jelly Star's
                // 0.55 left them a 25 dp strip)
                foldable -> maxOf(maxHeight * 0.8f, maxHeight - GALLERY_ADDING_GAP)
                // Desktop's 320 dp, or a little over half the screen
                else -> minOf(WIDGET_GALLERY_HEIGHT, maxHeight * 0.55f)
            }
        SideEffect { state.drag.galleryHeight = galleryHeight }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            if (state.editingWidgets) {
                WidgetGallery(
                    state = state,
                    placed = placed,
                    compact = compact,
                    narrow = narrow,
                    open = open,
                    onToggleAdd = if (foldable) ({ adding = !adding }) else null,
                    modifier = Modifier.align(Alignment.BottomCenter).height(galleryHeight).padding(horizontal = margin, vertical = 8.dp),
                    inset = inset,
                )
            }
            // On the grid only: the compact page has its button in its flow, where it covers no widget
            if (!state.editingWidgets && state.drag.movingId == null && !compact) {
                EditWidgetsButton(state, Modifier.align(Alignment.BottomEnd).padding(20.dp))
            }
            state.lastRemoved?.let { removed ->
                UndoBar(
                    removed = removed,
                    state = state,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.editingWidgets) galleryHeight + 8.dp else 24.dp),
                )
            }
        }
        // Over the page, not in its card: scrolled away, the card would close it
        if (isTabInFront()) state.optionsOpen?.let { widget -> WidgetOptions(widget, state, sheet = maxWidth < 600.dp, onClose = { state.optionsOpen = null }) }
    }
}

/**
 * The gallery: "סיום" first, labelled (an icon alone read as one more tool), then arrange and reset; [open], the hint
 * and the widgets to add. [onToggleAdd] (a short screen) shows or hides those widgets. [narrow], the title gives its
 * room to the buttons, the hint going above the widgets, scrolled with them (under the buttons it took the widgets'
 * room).
 */
@Composable
private fun WidgetGallery(
    state: HomeWidgetsState,
    placed: List<WidgetPlacement>,
    compact: Boolean,
    narrow: Boolean,
    open: Boolean,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    inset: Dp = 12.dp,
) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(16.dp)
    val scope = rememberCoroutineScope()
    // Fades away while a widget is dragged, to show the grid it goes to
    val alpha by animateFloatAsState(if (state.drag.movingId != null) 0.15f else 1f)
    val hint = if (compact) GALLERY_HINT_COMPACT else GALLERY_HINT_GRID
    val done = {
        state.editingWidgets = false
        state.selectedWidget = null
    }
    val buttons: @Composable () -> Unit = {
        if (onToggleAdd != null) {
            RoundIconButton(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.Add, HOME_WIDGETS_ADD, onToggleAdd, primary = open)
        }
        RoundIconButton(Icons.Outlined.AutoFixHigh, HOME_WIDGETS_ARRANGE, { scope.launch { state.layout.arrange() } })
        RoundIconButton(Icons.Outlined.RestartAlt, HOME_WIDGETS_RESET, { state.layout.reset() })
    }
    Column(
        modifier
            .widthIn(max = 960.dp)
            .fillMaxWidth()
            .alpha(alpha)
            .shadow(24.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.panelBorder, shape)
            .padding(inset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (narrow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DoneButton(done)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { buttons() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DoneButton(done)
                Column(Modifier.weight(1f)) {
                    Text(HOME_WIDGETS_GALLERY_TITLE, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (open) GalleryHint(hint)
                }
                buttons()
            }
        }
        if (open) GalleryWidgets(state, placed, hint = if (narrow) hint else null)
    }
}

@Composable
private fun GalleryHint(
    text: String,
    maxLines: Int = 3,
) {
    Text(text, fontSize = 12.sp, color = Zayit.colors.textSecondary, maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
}

/** "✓ סיום": the accent's, labelled, the edit mode's way out (Back too). */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(GALLERY_BAR_HEIGHT)
            .clip(shape)
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
        Text(HOME_WIDGETS_DONE, color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The widgets not on the page yet, at their size scaled down, a tap adding one; [hint] above them, scrolled with them. */
@Composable
private fun ColumnScope.GalleryWidgets(
    state: HomeWidgetsState,
    placed: List<WidgetPlacement>,
    hint: String? = null,
) {
    val c = Zayit.colors
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val available = maxWidth
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hint != null) GalleryHint(hint, maxLines = Int.MAX_VALUE)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                // The ones not on the page yet: one already there is moved or resized there, not added again
                val addable = availableHomeWidgets.filter { widget -> widget.isSupported && placed.none { it.widget.id == widget.id } }
                if (addable.isEmpty()) Text(HOME_WIDGETS_ALL_PLACED, color = c.textSecondary, fontSize = 14.sp)
                addable.forEach { widget -> key(widget.id) { GalleryItem(widget, state, available) } }
            }
        }
    }
}

@Composable
private fun GalleryItem(
    widget: HomeWidget,
    state: HomeWidgetsState,
    available: Dp,
) {
    val c = Zayit.colors
    val fullWidth = GALLERY_PITCH.width(widget.defaultSpan.w)
    // Desktop's scale, or smaller where a phone can't hold the preview
    val scale = minOf(PREVIEW_SCALE, (available.value - 4f) / fullWidth.value)
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { state.layout.add(widget) }.padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            WidgetPreview(widget, widget.defaultSpan, state, scale)
            // On top of the preview, so its own buttons never get the tap
            Box(Modifier.matchParentSize().clickable(role = Role.Button) { state.layout.add(widget) })
        }
        Text(widget.title, fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 13.sp)
    }
}

/** The widget's [HomeWidget.Preview] at its real size, scaled down by [scale]. */
@Composable
internal fun WidgetPreview(
    widget: HomeWidget,
    span: CellSpan,
    state: HomeWidgetsState,
    scale: Float,
) {
    Box(
        propagateMinConstraints = true,
        modifier =
            Modifier
                .clip(RoundedCornerShape(18.dp * scale))
                .layout { measurable, _ ->
                    val width = GALLERY_PITCH.width(span.w).roundToPx()
                    val height = GALLERY_PITCH.height(span.h).roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout((width * scale).roundToInt(), (height * scale).roundToInt()) {
                        placeable.placeWithLayer(0, 0) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                },
    ) {
        widget.Preview(state, Modifier)
    }
}

/**
 * Android only: the compact page's way into the edit mode, at the end of its widgets, in its flow (the grid's floating
 * pencil covered a phone's widgets). A widget's long press offers it too.
 */
@Composable
internal fun EditWidgetsPill(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.panelBorder, shape)
            .clickable(role = Role.Button) { state.editingWidgets = true }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Text(HOME_WIDGETS_EDIT, color = c.text, fontSize = 14.sp, maxLines = 1)
    }
}

/** A pencil floating in the Home's corner, always at hand, that opens the edit mode and its gallery (the grid's). */
@Composable
private fun EditWidgetsButton(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    RoundIconButton(
        icon = Icons.Outlined.Edit,
        label = HOME_WIDGETS_EDIT,
        onClick = { state.editingWidgets = true },
        modifier = modifier.shadow(8.dp, CircleShape),
        size = 44.dp,
    )
}

/** A round icon button whose [label] shows as a tooltip on a long press; [primary] fills it with the accent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: Dp = 40.dp,
) {
    val c = Zayit.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (primary) c.accent else c.panel)
                .border(1.dp, if (primary) c.accent else c.panelBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (primary) c.onAccent else c.text, modifier = Modifier.size(20.dp))
        }
    }
}

/** "X הוסר · ביטול", for a few seconds after a widget is removed. */
@Composable
private fun UndoBar(
    removed: RemovedWidget,
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    LaunchedEffect(removed) {
        delay(UNDO_DELAY_MS)
        state.forgetRemoved(removed)
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.panelBorder, shape)
            .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${removed.placement.widget.title} הוסר", color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(
            HOME_WIDGETS_UNDO,
            color = c.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = state::undoRemove).heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private const val UNDO_DELAY_MS = 6_000L

/**
 * [widget]'s options, as desktop's WidgetOptionsDialog: its [HomeWidget.Options], then a bar to go back to its
 * defaults or close. Each change is saved at once, and the card follows it behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetOptions(
    widget: HomeWidget,
    state: HomeWidgetsState,
    sheet: Boolean,
    onClose: () -> Unit,
) {
    val c = Zayit.colors
    val title = "${widget.title} · ${HOME_WIDGETS_OPTIONS.trimEnd('…')}"
    val body: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(bottom = 8.dp))
            Box(Modifier.weight(1f, fill = false).fillMaxWidth()) { widget.Options(state) }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = c.divider)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton(HOME_WIDGETS_RESET, { state.resetSettings(widget) })
                io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton(HOME_WIDGETS_DONE, onClose)
            }
        }
    }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panel) {
            body(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp))
        }
    } else {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val shape = RoundedCornerShape(14.dp)
            body(
                Modifier
                    .sizeIn(maxWidth = 460.dp, maxHeight = 560.dp)
                    .fillMaxWidth(0.9f)
                    .clip(shape)
                    .background(c.panel)
                    .border(1.dp, c.panelBorder, shape)
                    .padding(16.dp),
            )
        }
    }
}

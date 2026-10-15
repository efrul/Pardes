package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.shell.WindowBackScope
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

/** Hands the selected text over without touching the system clipboard, unless no action asked for it. */
private class CapturingClipboard(private val real: Clipboard) : Clipboard {
    private var pending: CompletableDeferred<String>? = null

    fun arm(): CompletableDeferred<String> = CompletableDeferred<String>().also { pending = it }

    /** The copy never came (timeout): later copies must reach the real clipboard again. */
    fun disarm(d: CompletableDeferred<String>) {
        if (pending === d) pending = null
    }

    override suspend fun getClipEntry(): ClipEntry? = real.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val p = pending
        if (p == null) {
            real.setClipEntry(clipEntry?.plain())
            return
        }
        pending = null
        val data = clipEntry?.clipData
        p.complete(if (data != null && data.itemCount > 0) data.getItemAt(0).text?.toString().orEmpty() else "")
    }

    override val nativeClipboard get() = real.nativeClipboard
}

/** A selection waiting for an action: where it is (in the area) and how to read its text. */
private class SelectionRequest(val bounds: (LayoutCoordinates) -> Rect?, val capture: suspend () -> String?, val dismiss: () -> Unit)

@Stable
private class SelectionHost(val clipboard: CapturingClipboard) {
    var request by mutableStateOf<SelectionRequest?>(null)
    var coordinates: LayoutCoordinates? = null

    /** The new context menu's toolbar (Compose foundation 1.9+): our bar, with desktop's items. */
    val provider = object : TextContextMenuProvider {
        override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
            val done = CompletableDeferred<Unit>()
            val session = object : TextContextMenuSession {
                override fun close() {
                    done.complete(Unit)
                }
            }
            val request = SelectionRequest(
                bounds = { coords -> runCatching { dataProvider.contentBounds(coords) }.getOrNull() },
                capture = {
                    val copy = dataProvider.data().components.filterIsInstance<TextContextMenuItem>().firstOrNull { it.key == TextContextMenuKeys.CopyKey }
                    if (copy == null) {
                        null
                    } else {
                        val captured = clipboard.arm()
                        copy.onClick(session)
                        withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { captured.await() }.also { clipboard.disarm(captured) }
                    }
                },
                dismiss = { done.complete(Unit) },
            )
            this@SelectionHost.request = request
            try {
                done.await()
            } finally {
                if (this@SelectionHost.request === request) this@SelectionHost.request = null
            }
        }
    }

    /** The older TextToolbar path, for when the new context menu is off. */
    val toolbar = object : TextToolbar {
        private var current: SelectionRequest? = null
        override val status: TextToolbarStatus get() = if (current != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
            val copy = onCopyRequested ?: return hide()
            val request = SelectionRequest(
                bounds = { coords ->
                    val root = coords.findRootCoordinates()
                    Rect(coords.localPositionOf(root, rect.topLeft), coords.localPositionOf(root, rect.bottomRight))
                },
                capture = {
                    val captured = clipboard.arm()
                    copy()
                    withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { captured.await() }.also { clipboard.disarm(captured) }
                },
                dismiss = { hide() },
            )
            current = request
            this@SelectionHost.request = request
        }

        override fun hide() {
            if (this@SelectionHost.request === current) this@SelectionHost.request = null
            current = null
        }
    }
}

private const val CAPTURE_TIMEOUT_MS = 1_000L

/**
 * A text area the reader can select in, as desktop's SelectionContainer under BookTextMenus: a long press selects,
 * handles extend, and a floating bar offers copy, highlight, note and "עוד" (desktop's whole menu in a sheet).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableArea(spec: SelectionMenuSpec, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val real = LocalClipboard.current
    val host = remember(real) { SelectionHost(CapturingClipboard(real)) }
    var sheetText by remember { mutableStateOf<String?>(null) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onGloballyPositioned { host.coordinates = it }.onSizeChanged { areaSize = it }) {
        CompositionLocalProvider(
            LocalClipboard provides host.clipboard,
            LocalTextContextMenuToolbarProvider provides host.provider,
            LocalTextToolbar provides host.toolbar,
        ) {
            SelectionContainer { content() }
        }
        host.request?.let { request -> SelectionBar(host, request, spec, areaSize) { sheetText = it } }
    }
    if (isTabInFront()) sheetText?.let { text -> SelectionSheet(text, spec, onDismiss = { sheetText = null }) }
}

@Composable
private fun BoxScope.SelectionBar(host: SelectionHost, request: SelectionRequest, spec: SelectionMenuSpec, area: IntSize, onMore: (String) -> Unit) {
    val c = Zayit.colors
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var palette by remember(request) { mutableStateOf(false) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val bounds = host.coordinates?.let(request.bounds)
    val margin = with(density) { 8.dp.roundToPx() }
    val x = ((bounds?.center?.x ?: (area.width / 2f)) - barSize.width / 2f).roundToInt().coerceIn(margin, (area.width - barSize.width - margin).coerceAtLeast(margin))
    val above = (bounds?.top ?: 0f) - barSize.height - margin
    val y = (if (above >= margin) above else (bounds?.bottom ?: 0f) + margin * 3).roundToInt().coerceIn(margin, (area.height - barSize.height - margin).coerceAtLeast(margin))

    fun act(block: (String) -> Unit) {
        scope.launch {
            val text = request.capture()?.let(::trimSelectionEdges)?.takeIf { it.isNotBlank() } ?: return@launch
            request.dismiss()
            block(text)
        }
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
                Text(
                    text,
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

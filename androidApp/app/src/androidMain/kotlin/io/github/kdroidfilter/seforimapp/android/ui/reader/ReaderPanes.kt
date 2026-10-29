package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.UserHighlight
import io.github.kdroidfilter.seforimapp.android.ui.components.EmptyHint
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.components.ZCheckRow
import io.github.kdroidfilter.seforimapp.android.ui.components.ZChip
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.shell.WindowBackScope
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the panes do outside themselves: the shell's tabs and the reader's shared actions. */
@Stable
class PaneActions(
    /** Opens a book in a new tab, at a line when given (desktop OpenCommentaryTarget / OpenBookByIdInNewTab). */
    val openBookAt: (bookId: Long, lineId: Long?) -> Unit,
    /** Builds the selection menu of a pane column: its highlights go to [bookOf]'s book (the commentary's own). */
    val paneSelection: (items: () -> List<CommentaryWithText>) -> SelectionMenuSpec,
)

/** A pane's header (desktop ui/components/PaneHeader.kt): its title, its actions, and hide. */
@Composable
fun PaneHeader(title: String, onClose: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Zayit.colors
    Row(Modifier.fillMaxWidth().height(44.dp).padding(start = 14.dp, end = 2.dp).then(modifier), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
        if (onClose != null) ZIconButton(Icons.Outlined.Close, "הסתר", onClose, tint = c.textSecondary)
    }
}

/**
 * Phone: the pane tabs above the pane under the text, only the kinds the book has, with desktop's titles. They keep
 * their roomy look when they all fit; else tighter steps (padding, gaps, then the text a size smaller) make them fit
 * from about 360 dp; narrower still (Jelly Star), the row scrolls, the selected tab kept whole in view and a fade at
 * the edge that hides tabs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhonePaneTabs(current: ReaderPane, panes: List<ReaderPane>, onSelect: (ReaderPane) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    Row(modifier.fillMaxWidth().height(44.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val baseStyle = LocalTextStyle.current
            val available = constraints.maxWidth
            // The first step every tab fits in, the selected one bold; the last one scrolls when none does
            val step = remember(panes, available, baseStyle, density) {
                PaneTabSteps.firstOrNull { st ->
                    fun width(weight: FontWeight) = panes.map { p ->
                        measurer.measure(p.title, baseStyle.copy(fontSize = st.fontSize, fontWeight = weight), maxLines = 1, softWrap = false).size.width
                    }
                    val regular = width(FontWeight.Normal)
                    val bold = width(FontWeight.Bold)
                    val boldExtra = regular.indices.maxOfOrNull { bold[it] - regular[it] } ?: 0
                    val chrome = with(density) { (st.padding * 2 * panes.size + st.gap * (panes.size - 1)).roundToPx() }
                    regular.sum() + boldExtra + chrome <= available
                } ?: PaneTabSteps.first().copy(padding = 10.dp)
            }
            val scroll = rememberScrollState()
            Row(
                Modifier.fillMaxHeight().horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(step.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                panes.forEach { p ->
                    key(p) {
                        val sel = p == current
                        val bringIntoView = remember { BringIntoViewRequester() }
                        LaunchedEffect(sel, step) {
                            if (sel) {
                                withFrameNanos { } // laid out first
                                bringIntoView.bringIntoView()
                            }
                        }
                        Box(
                            Modifier
                                .bringIntoViewRequester(bringIntoView)
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) c.selectedRow else Color.Transparent)
                                .clickable(role = Role.Tab) { onSelect(p) }
                                .padding(horizontal = step.padding, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                p.title,
                                fontSize = step.fontSize,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) c.text else c.textSecondary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            // Tabs hidden past an edge: that edge fades out
            if (scroll.canScrollBackward) PaneTabsEdgeFade(atStart = true, Modifier.align(Alignment.CenterStart))
            if (scroll.canScrollForward) PaneTabsEdgeFade(atStart = false, Modifier.align(Alignment.CenterEnd))
        }
        ZIconButton(Icons.Outlined.Close, "סגור", onClose, tint = c.textSecondary)
    }
}

/** A size of the pane tabs: the text, the padding inside a tab and the gap between two. */
private data class PaneTabStep(val fontSize: TextUnit, val padding: Dp, val gap: Dp)

private val PaneTabSteps = listOf(
    PaneTabStep(14.sp, 12.dp, 4.dp),
    PaneTabStep(14.sp, 8.dp, 2.dp),
    PaneTabStep(13.sp, 6.dp, 0.dp),
    PaneTabStep(12.sp, 5.dp, 0.dp),
)

/** The panel's colour fading in toward the [atStart] (else end) edge of the pane tabs. */
@Composable
private fun PaneTabsEdgeFade(atStart: Boolean, modifier: Modifier) {
    val panel = Zayit.colors.panel
    // drawBehind's x runs left to right whatever the layout direction: the start edge is on the right in RTL
    val edgeOnLeft = atStart != (LocalLayoutDirection.current == LayoutDirection.Rtl)
    val colors = if (edgeOnLeft) listOf(panel, panel.copy(alpha = 0f)) else listOf(panel.copy(alpha = 0f), panel)
    Box(modifier.fillMaxHeight().width(20.dp).background(Brush.horizontalGradient(colors)))
}

/** The links of a pane column, read [PAGE] at a time as it scrolls (desktop's paging sources): lazy text loading. */
@Stable
class PagedLinks(private val load: suspend (offset: Int, limit: Int) -> List<CommentaryWithText>) {
    val items = mutableStateListOf<CommentaryWithText>()
    var loading by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    suspend fun more() {
        if (loading || done) return
        loading = true
        try {
            // A read cancelled because its section or last item left the screen is not the end of the list: it was
            // marked done with no lines, and the section stayed a header with nothing under it
            val page = try {
                load(items.size, PAGE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            items.addAll(page)
            if (page.size < PAGE) done = true
        } finally {
            loading = false
        }
    }

    /**
     * The place of the first link among [targetLineIds], reading the next pages while it is not loaded yet; null when
     * the column ends without it (desktop 0.6.12 loadedIndexOf).
     */
    suspend fun indexOfFirst(targetLineIds: Set<Long>): Int? {
        while (true) {
            val index = items.indexOfFirst { it.link.targetLineId in targetLineIds }
            if (index >= 0) return index
            if (done) return null
            if (loading) snapshotFlow { loading }.first { !it } else more()
        }
    }

    companion object {
        const val PAGE = 64
    }
}

/** The selected text's commentaries, targumim or sources share the reader's text size × 0.875 (desktop). */
fun paneTextSize(textSize: Float) = textSize * 0.875f

/**
 * One link's text (desktop CommentaryItem / LinkItem): highlights of its own book, the find query, links, images.
 * [isInPassage]: led to by the passage selected in the text, it stands on a light tint of the accent, faded in (desktop
 * 0.6.12: nothing fades, as a commentary not linked to the passage may still be about it).
 */
@Composable
fun LinkItem(
    item: CommentaryWithText,
    textSize: Float,
    font: FontFamily,
    diacritics: DiacriticsMode,
    highlights: List<UserHighlight>,
    findQuery: String,
    selectable: SelectableText? = null,
    isInPassage: Boolean = false,
) {
    val c = Zayit.colors
    val passageTint by animateFloatAsState(if (isInPassage) 1f else 0f, tween(durationMillis = 250), label = "passage tint")
    val annotation = rememberHtmlAnnotation(item.targetText, textSize, diacritics)
    val needsOriginal = diacritics != DiacriticsMode.All && highlights.isNotEmpty()
    val original = remember(item.targetText, needsOriginal) { if (needsOriginal) buildAnnotatedFromHtml(item.targetText, textSize).text else null }
    val baseHl = c.accent.copy(alpha = 0.22f)
    val display = remember(annotation.text, highlights, original, diacritics, findQuery, baseHl) {
        highlightAnnotatedWithCurrent(applyUserHighlights(annotation.text, highlights, original, diacritics), findQuery, baseHl, baseHl)
    }
    val style = TextStyle(fontFamily = font, fontSize = textSize.sp, lineHeight = (textSize * READER_LINE_HEIGHT).sp, color = c.text, textAlign = TextAlign.Justify)
    val tint = c.accent
    // The width the Text fills, which its lines are justified to (Justify.kt)
    BoxWithConstraints(
        Modifier
            .drawBehind { if (passageTint > 0f) drawRoundRect(tint.copy(alpha = PASSAGE_TINT_ALPHA * passageTint), cornerRadius = CornerRadius(6.dp.toPx())) }
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        val justified = rememberJustifiedText(display, style, constraints.maxWidth, inline = annotation.inline.isNotEmpty())
        Text(
            justified.text,
            inlineContent = annotation.inline,
            modifier = Modifier.fillMaxWidth().then(selectable?.modifier ?: Modifier),
            onTextLayout = {
                justified.onLayout(it)
                selectable?.onLayout(it)
            },
            style = style.copy(textAlign = justified.textAlign),
        )
    }
}

/**
 * Desktop CommentatorHeader: the commentator's name, bold and centred; a tap opens its book where it comments.
 * [compact], for a short pane, keeps that tap on one tighter line at the text's size.
 */
@Composable
fun CommentatorHeader(name: String, textSize: Float, compact: Boolean = false, onClick: () -> Unit) {
    Text(
        name,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = if (compact) 3.dp else 8.dp, horizontal = 6.dp),
        fontSize = (if (compact) textSize else textSize * 1.1f).sp,
        fontWeight = FontWeight.Bold,
        color = Zayit.colors.text,
        textAlign = TextAlign.Center,
        maxLines = if (compact) 1 else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The user's highlights of the books [items] belong to, by (book, line) (desktop highlightsByBookLine). */
@Composable
private fun rememberLinkHighlights(items: List<CommentaryWithText>): Map<Long, Map<Long, List<UserHighlight>>> {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val books = remember(items.size) { items.map { it.link.targetBookId }.toSet() }
    LaunchedEffect(books) { books.forEach { app.userData.loadHighlights(it) } }
    val byBook by app.userData.highlightsByBook.collectAsState()
    return remember(byBook) { byBook.mapValues { (_, list) -> list.groupBy { it.lineId } } }
}

/**
 * One commentator's column (desktop CommentariesPagedList), its own scroll, selectable. [compactHeader] narrows the
 * name above it to one line where the room is short; it stays, being the tap that opens the commentator's book.
 */
@Composable
private fun CommentatorColumn(
    vm: ReaderViewModel,
    selection: LinkSelection,
    commentatorId: Long,
    name: String,
    textSize: Float,
    diacritics: DiacriticsMode,
    findQuery: String,
    actions: PaneActions,
    modifier: Modifier = Modifier,
    compactHeader: Boolean = false,
    passage: SelectedPassage? = null,
) {
    val scope = rememberCoroutineScope()
    val paged = remember(selection, commentatorId) { PagedLinks { o, l -> vm.linksPage(selection, ConnectionType.COMMENTARY, commentatorId, o, l) } }
    LaunchedEffect(paged) { paged.more() }
    val list = rememberLazyListState()
    // The passage selected in the text: the commentary lines it leads to stand out, and the column brings the first in
    // view once, reading further pages when needed, unless it is already wholly shown (desktop 0.6.12)
    LaunchedEffect(passage, paged) {
        val targets = passage?.takeIf { commentatorId in it.targetBookIds }?.targetLineIds ?: return@LaunchedEffect
        val index = paged.indexOfFirst(targets) ?: return@LaunchedEffect
        val layout = list.layoutInfo
        val shown = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val isWhollyShown = shown != null && shown.offset >= layout.viewportStartOffset && shown.offset + shown.size <= layout.viewportEndOffset
        if (!isWhollyShown) list.animateScrollToItem(index)
    }
    LaunchedEffect(list, paged) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= paged.items.size - 8 }.distinctUntilChanged().collect { if (it) paged.more() }
    }
    val highlights = rememberLinkHighlights(paged.items)
    val spec = remember(paged) { actions.paneSelection { paged.items.toList() } }
    // Units: the commentaries' places in the column
    val textSelection = remember(paged) { ListSelection() }
    val colors = Zayit.colors
    val textOf: (Long) -> String? = { unit ->
        paged.items.getOrNull(unit.toInt())?.let { buildHtmlAnnotation(it.targetText, textSize, diacritics, colors.isDark, colors.accent).text.text }
    }
    Column(modifier) {
        CommentatorHeader(name, textSize, compact = compactHeader) { scope.launch { actions.openBookAt(commentatorId, vm.commentaryTargetLine(selection, commentatorId)) } }
        SelectableArea(spec, textSelection, list, textOf, resetKey = diacritics, Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(paged.items, key = { i, it -> "${it.link.id}-$i" }) { i, item ->
                    val selectable = rememberSelectableText(textSelection, i.toLong())
                    LinkItem(
                        item,
                        textSize,
                        Zayit.fonts.commentary,
                        diacritics,
                        highlights[item.link.targetBookId]?.get(item.link.targetLineId).orEmpty(),
                        findQuery,
                        selectable,
                        isInPassage = passage != null && item.link.targetLineId in passage.targetLineIds,
                    )
                }
                if (paged.loading && paged.items.isEmpty()) item { LoadingBox(Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
    }
}

// Grid capacity (desktop views/CommentatorsGrid.kt), at the reference commentary size 14
/** A sources or targum section's span of [ListSelection] units: more than one line's links from one book ever are. */
private const val SECTION_UNITS = 10_000L

private const val MIN_CELL_WIDTH_AT_REF = 320f
private const val MIN_CELL_HEIGHT_AT_REF = 150f
private const val MAX_ROWS_PER_PAGE = 2
private const val REFERENCE_TEXT_SIZE = 14f
private const val MIN_TEXT_SCALE = 0.55f
private const val DOWNWARD_AMPLIFIER = 3f
private const val NARROW_PANE_WIDTH = 420f

internal data class GridCapacity(val cols: Int, val rows: Int, val perPage: Int)

/** Desktop computeCommentariesGridCapacity: cells of 320×150 dp at the reference size, up to 2 rows, the user's cap. */
internal fun computeGridCapacity(paneWidthDp: Float, paneHeightDp: Float, commentTextSize: Float, maxPerPage: Int): GridCapacity {
    val rawScale = commentTextSize / REFERENCE_TEXT_SIZE
    val scale = if (rawScale >= 1f) 1f else (1f - (1f - rawScale) * DOWNWARD_AMPLIFIER).coerceAtLeast(MIN_TEXT_SCALE)
    var cols = maxOf(1, (paneWidthDp / (MIN_CELL_WIDTH_AT_REF * scale)).toInt())
    var rows = maxOf(1, (paneHeightDp / (MIN_CELL_HEIGHT_AT_REF * scale)).toInt()).coerceAtMost(MAX_ROWS_PER_PAGE)
    if (cols * rows < 2 && paneWidthDp >= NARROW_PANE_WIDTH) if (paneWidthDp >= paneHeightDp) cols = 2 else rows = 2
    var perPage = cols * rows
    if (maxPerPage in 1 until perPage) {
        perPage = maxPerPage
        cols = cols.coerceAtMost(perPage)
        rows = ((perPage + cols - 1) / cols).coerceAtLeast(1)
    }
    return GridCapacity(cols, rows, perPage)
}

// Below this width the commentators list no longer fits beside one column (desktop CompactCommentariesPaneWidth)
private val CompactCommentariesWidth = 150.dp + 320.dp

/**
 * The commentaries of the selected line or section (desktop LineCommentsView + CommentatorsGrid): the commentators
 * linked there, by group, a checklist on the reading side and a grid of columns paged vertically (tablet), or one
 * commentator per page with chips (phone). Selections are per line with a sticky set per book, as desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentariesPane(vm: ReaderViewModel, state: ReaderUiState, textSize: Float, wide: Boolean, actions: PaneActions, modifier: Modifier = Modifier, header: @Composable (actions: @Composable RowScope.() -> Unit) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val prefs = remember { ReaderPrefs.get(app) }
    val listPref by prefs.commentatorsList.collectAsState()
    val selection = state.linkSelection
    val primary = state.primaryLineId
    val snapshot by produceState<LineConnectionsSnapshot?>(null, selection, vm.connections[primary]) {
        value = selection?.let { vm.connectionsOf(it) }
    }
    val groups = snapshot?.commentatorGroups.orEmpty()
    val available = remember(groups) { groups.flatMap { g -> g.commentators } }
    val selectedIds = if (primary != null) vm.selectedCommentators(primary, available.map { it.bookId }) else emptySet()
    val shown = remember(available, selectedIds) { available.filter { it.bookId in selectedIds } }
    var filterOpen by remember { mutableStateOf(false) }
    val pane = paneTextSize(textSize)
    val findQuery = state.find.activeQuery
    // The passage selected in the text, on a single selected line
    val passage = state.selectedPassage?.takeIf { selection is LinkSelection.Single && it.lineId == selection.lineId }

    fun toggle(id: Long) {
        val p = primary ?: return
        val next = if (id in selectedIds) selectedIds - id else selectedIds + id
        vm.updateSelectedCommentators(p, next.filter { i -> available.any { it.bookId == i } }.toSet(), available.map { it.bookId })
    }

    BoxWithConstraints(modifier) {
        val compact = maxWidth < CompactCommentariesWidth
        val paneWidth = maxWidth
        var compactListOpen by remember { mutableStateOf(false) }
        val listVisible = wide && if (compact) compactListOpen else listPref
        Column(Modifier.fillMaxSize()) {
            header {
                if (wide && groups.isNotEmpty()) {
                    ZIconButton(
                        Icons.AutoMirrored.Outlined.ViewSidebar,
                        if (listVisible) "הסתר סרגל מפרשים" else "הצג סרגל מפרשים",
                        { if (compact) compactListOpen = !compactListOpen else prefs.setCommentatorsList(!listPref) },
                        tint = Zayit.colors.textSecondary,
                        selected = listVisible,
                    )
                }
            }
            when {
                selection == null -> EmptyHint("בחר שורה כדי לצפות במפרשים שלה", Modifier.fillMaxSize())
                snapshot == null -> LoadingBox(Modifier.fillMaxSize())
                groups.isEmpty() -> EmptyHint("אין מפרשים זמינים לפסקה זו", Modifier.fillMaxSize())
                !wide -> PhoneCommentaries(vm, selection, shown, pane, state.diacritics, findQuery, actions, passage, onFilter = { filterOpen = true }, onJump = {})
                else -> Row(Modifier.fillMaxSize()) {
                    if (listVisible) {
                        Column(Modifier.then(if (compact) Modifier.fillMaxSize() else Modifier.width(maxOf(150.dp, paneWidth * 0.22f)).fillMaxHeight()).verticalScroll(rememberScrollState())) {
                            CommentatorChecklist(groups, selectedIds, ::toggle)
                        }
                        if (!compact) Box(Modifier.width(1.dp).fillMaxHeight().background(Zayit.colors.divider))
                    }
                    if (!(compact && listVisible)) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (shown.isEmpty()) {
                                EmptyHint("אנא בחר לפחות מפרש אחד מהרשימה", Modifier.fillMaxSize())
                            } else {
                                CommentatorsGrid(vm, selection, shown, pane, state.diacritics, findQuery, actions, passage)
                            }
                        }
                    }
                }
            }
        }
    }
    if (filterOpen && isTabInFront()) {
        ModalBottomSheet(onDismissRequest = { filterOpen = false }, sheetState = rememberModalBottomSheetState(), containerColor = Zayit.colors.panel) {
            WindowBackScope {
                Text("מפרשים", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Zayit.colors.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { CommentatorChecklist(groups, selectedIds, ::toggle) }
            }
        }
    }
}

/** Desktop CommentatorsList: group headers (when more than one group), dividers, checkboxes. */
@Composable
private fun CommentatorChecklist(groups: List<CommentatorGroup>, selected: Set<Long>, onToggle: (Long) -> Unit) {
    val c = Zayit.colors
    groups.forEachIndexed { i, group ->
        if (group.commentators.isEmpty()) return@forEachIndexed
        if (i > 0) HorizontalDivider(color = c.divider)
        if (groups.size > 1 && group.label.isNotBlank()) {
            Text(group.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp, end = 8.dp))
        }
        group.commentators.forEach { item -> ZCheckRow(item.name, item.bookId in selected, onToggle = { onToggle(item.bookId) }) }
    }
}

/** Tablet: desktop CommentatorsGridScaffold, cells rebalanced per page, a vertical pager driven by its dots. */
@Composable
private fun CommentatorsGrid(
    vm: ReaderViewModel,
    selection: LinkSelection,
    shown: List<CommentatorItem>,
    textSize: Float,
    diacritics: DiacriticsMode,
    findQuery: String,
    actions: PaneActions,
    passage: SelectedPassage?,
) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val max by app.prefs.maxCommentatorsPerPage.collectAsState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cap = computeGridCapacity(maxWidth.value, maxHeight.value, textSize, max)
        val pages = remember(shown, cap.perPage) { shown.chunked(cap.perPage) }
        val pager = rememberPagerState { pages.size }
        // The commentators of the page shown, for the text to offer their passages only (none once the grid goes)
        ReportVisibleCommentators(vm) { pages.getOrNull(pager.settledPage).orEmpty() }
        // The selected passage's commentator keeps its page shown, whatever repaginates (desktop's pager anchor)
        LaunchedEffect(passage, pages) {
            val page = passage?.let { p -> pages.indexOfFirst { names -> names.any { it.bookId in p.targetBookIds } } } ?: -1
            if (page >= 0 && page != pager.currentPage) pager.animateScrollToPage(page)
        }
        // A commentator just ticked: its page comes into view (desktop's anchor follows the added one)
        var previous by remember { mutableStateOf<List<Long>?>(null) }
        LaunchedEffect(shown) {
            val prev = previous
            val added = if (prev != null) shown.lastOrNull { it.bookId !in prev } else null
            previous = shown.map { it.bookId }
            if (added != null) pager.animateScrollToPage(shown.indexOf(added) / cap.perPage)
        }
        Row(Modifier.fillMaxSize()) {
            VerticalPager(pager, Modifier.weight(1f).fillMaxHeight(), userScrollEnabled = false, beyondViewportPageCount = 1) { page ->
                val names = pages.getOrNull(page).orEmpty()
                val rowsNeeded = ((names.size + cap.cols - 1) / cap.cols).coerceAtLeast(1)
                val perRow = ((names.size + rowsNeeded - 1) / rowsNeeded).coerceAtLeast(1)
                Column(Modifier.fillMaxSize()) {
                    names.chunked(perRow).forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEach { item ->
                                androidx.compose.runtime.key(item.bookId) {
                                    CommentatorColumn(
                                        vm,
                                        selection,
                                        item.bookId,
                                        item.name,
                                        textSize,
                                        diacritics,
                                        findQuery,
                                        actions,
                                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp),
                                        passage = passage,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Column(Modifier.fillMaxHeight().width(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    repeat(pages.size) { i ->
                        val active = pager.currentPage == i
                        Box(Modifier.size(24.dp).clickable(role = Role.Button) { scope.launch { pager.animateScrollToPage(i) } }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(if (active) 10.dp else 7.dp).clip(CircleShape).background(Zayit.colors.text.copy(alpha = if (active) 1f else 0.4f)))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tells the reader which commentators the pane shows on its current page ([visible]), as it changes, and none once
 * the pane goes (desktop 0.6.12 CommentariesVisibleChanged): only their passages answer in the text.
 */
@Composable
private fun ReportVisibleCommentators(vm: ReaderViewModel, visible: () -> List<CommentatorItem>) {
    val current by rememberUpdatedState(visible)
    LaunchedEffect(vm) {
        snapshotFlow { current().mapTo(HashSet()) { it.bookId } }.distinctUntilChanged().collect { vm.setVisibleCommentators(it) }
    }
    DisposableEffect(vm) { onDispose { vm.setVisibleCommentators(emptySet()) } }
}

private const val PASSAGE_TINT_ALPHA = 0.1f

/** Below this height a phone commentary page's title row (about 30dp) shrinks to one tight line (about 22dp). */
private val PhoneCommentaryHeaderRoom = 140.dp

/** Phone: one commentator per page, the chips row as the page indicator (tap jumps), "סנן" for the checklist. */
@Composable
private fun PhoneCommentaries(
    vm: ReaderViewModel,
    selection: LinkSelection,
    shown: List<CommentatorItem>,
    textSize: Float,
    diacritics: DiacriticsMode,
    findQuery: String,
    actions: PaneActions,
    passage: SelectedPassage?,
    onFilter: () -> Unit,
    onJump: (Int) -> Unit,
) {
    // The pager composes later than this, in the BoxWithConstraints' measure: its pages are read from the list of now,
    // which its page count already follows, not from the one of its last composition (a commentator added in the
    // filter, or a line with more of them, asked that one for a page past its end and took the app down)
    val current by rememberUpdatedState(shown)
    val pager = rememberPagerState { current.size }
    val scope = rememberCoroutineScope()
    // The commentator of the page shown, for the text to offer its passages only (none once the pane goes)
    ReportVisibleCommentators(vm) { listOfNotNull(current.getOrNull(pager.settledPage)) }
    // The selected passage's commentator stays the page shown
    LaunchedEffect(passage, shown) {
        val page = passage?.let { p -> shown.indexOfFirst { it.bookId in p.targetBookIds } } ?: -1
        if (page >= 0 && page != pager.currentPage) pager.animateScrollToPage(page)
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
            item { ZChip("סנן", selected = false, onClick = onFilter, leading = Icons.Outlined.FilterList) }
            items(shown.size) { i ->
                ZChip(shown[i].name, selected = pager.currentPage == i, onClick = {
                    onJump(i)
                    scope.launch { pager.animateScrollToPage(i) }
                })
            }
        }
        if (shown.isEmpty()) {
            EmptyHint("אנא בחר לפחות מפרש אחד מהרשימה", Modifier.fillMaxSize())
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // A short pane (Jelly Star) gives most of the commentator's title row to its text; the title stays, as
                // desktop's, the tap that opens its book at this line (the chips only turn the pages)
                val compactHeader = maxHeight < PhoneCommentaryHeaderRoom
                HorizontalPager(pager, Modifier.fillMaxSize(), key = { current.getOrNull(it)?.bookId ?: it }) { page ->
                    val item = current.getOrNull(page) ?: return@HorizontalPager
                    CommentatorColumn(vm, selection, item.bookId, item.name, textSize, diacritics, findQuery, actions, Modifier.fillMaxSize(), compactHeader = compactHeader, passage = passage)
                }
            }
        }
    }
}

/**
 * Targumim of the selected line (desktop LineTargumView): a section per book in SQL order, its header opening that
 * book at its first link in a new tab, the links read a page at a time. The sources are a places pane since desktop
 * 0.6.11 ([LinePlacesPane]).
 */
@Composable
fun LinksPane(vm: ReaderViewModel, state: ReaderUiState, textSize: Float, actions: PaneActions, modifier: Modifier = Modifier, header: @Composable () -> Unit = {}) {
    val selection = state.linkSelection
    val snapshot by produceState<LineConnectionsSnapshot?>(null, selection, vm.connections[state.primaryLineId]) {
        value = selection?.let { vm.connectionsOf(it) }
    }
    val sources = snapshot?.targumSources.orEmpty()
    val pane = paneTextSize(textSize)
    val font = Zayit.fonts.targum
    Column(modifier) {
        header()
        when {
            selection == null -> EmptyHint("בחר שורה כדי לצפות בתרגומים שלה", Modifier.fillMaxSize())
            snapshot == null -> LoadingBox(Modifier.fillMaxSize())
            sources.isEmpty() -> EmptyHint("אין תרגומים זמינים לשורה זו", Modifier.fillMaxSize())
            else -> {
                val sections = sources.entries.map { (name, id) ->
                    name to remember(selection, id) { PagedLinks { o, l -> vm.linksPage(selection, ConnectionType.TARGUM, id, o, l) } }
                }
                val ids = sources.values.toList()
                // One effect per section: a sibling added or dropped no longer cancels a surviving section's first page
                sections.forEach { (_, p) -> key(p) { LaunchedEffect(p) { p.more() } } }
                val all = sections.flatMap { it.second.items }
                val highlights = rememberLinkHighlights(all)
                val spec = remember(sections.map { it.second }) { actions.paneSelection { sections.flatMap { it.second.items } } }
                // Units: a section's place × SECTION_UNITS + the source's place in it, which its paging keeps
                val textSelection = remember(sections.map { it.second }) { ListSelection() }
                val list = rememberLazyListState()
                val colors = Zayit.colors
                val textOf: (Long) -> String? = { unit ->
                    sections.getOrNull((unit / SECTION_UNITS).toInt())?.second?.items?.getOrNull((unit % SECTION_UNITS).toInt())?.let {
                        buildHtmlAnnotation(it.targetText, pane, state.diacritics, colors.isDark, colors.accent).text.text
                    }
                }
                SelectableArea(spec, textSelection, list, textOf, resetKey = state.diacritics, Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
                        sections.forEachIndexed { s, (name, paged) ->
                            item(key = "h-${ids[s]}") {
                                CommentatorHeader(name, pane) { actions.openBookAt(ids[s], paged.items.firstOrNull()?.link?.targetLineId) }
                            }
                            itemsIndexed(paged.items, key = { i, it -> "${ids[s]}-${it.link.id}-$i" }) { i, item ->
                                val selectable = rememberSelectableText(textSelection, s * SECTION_UNITS + i)
                                LinkItem(item, pane, font, state.diacritics, highlights[item.link.targetBookId]?.get(item.link.targetLineId).orEmpty(), state.find.activeQuery, selectable)
                                if (i == paged.items.lastIndex && !paged.done) LaunchedEffect(paged.items.size) { paged.more() }
                            }
                        }
                    }
                }
            }
        }
    }
}

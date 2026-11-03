package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.SelectedPassage
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.Amud
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.TocNumber
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_amud_missing
import seforimapp.seforimapp.generated.resources.daf_import
import seforimapp.seforimapp.generated.resources.daf_import_not_layout
import seforimapp.seforimapp.generated.resources.daf_import_other_tractate
import seforimapp.seforimapp.generated.resources.daf_missing
import seforimapp.seforimapp.generated.resources.daf_missing_hint
import seforimapp.seforimapp.generated.resources.notorashihebrew
import java.io.File
import kotlin.math.abs

/** Whether [this] is a tractate of the Babylonian Talmud, whose page the daf view draws. */
internal val Book.isBavli: Boolean
    get() = categoryId in CatalogPresets.Ids.Categories.BAVLI_ZERAIM..CatalogPresets.Ids.Categories.BAVLI_TAHAROT

/** The amud (`35b`) that a table of contents' entry or a line's reference names (`דף לה:`, `ברכות לה:, א`), else null. */
internal fun amudOf(text: String): String? =
    text.split(' ', ',').firstNotNullOfOrNull { token ->
        TocNumber.of(token)?.takeIf { it.amud != null }?.let { "${it.value}${if (it.amud == Amud.B) 'b' else 'a'}" }
    }

/** The order of amudim: 2a, 2b, 3a… */
internal fun amudIndex(amud: String): Int =
    amud.dropLast(1).toIntOrNull()?.let { it * 2 + if (amud.endsWith('b')) 1 else 0 } ?: Int.MAX_VALUE

/** [amud] as cited: `לה:` for 35b, `לה.` for 35a. */
internal fun amudTitle(amud: String): String =
    amud.dropLast(1).toIntOrNull()?.let { ReferenceParser.hebrewNumeral(it) + if (amud.endsWith('b')) ":" else "." } ?: amud

/** The first line of every amud, from a book's table of contents. */
internal fun firstLines(toc: List<TocEntry>): Map<String, Long> {
    val lines = HashMap<String, Long>()
    for (entry in toc) {
        val lineId = entry.lineId ?: continue
        amudOf(entry.text)?.let { lines.putIfAbsent(it, lineId) }
    }
    return lines
}

/**
 * Where each Gemara line of the library is printed first: its amud's place in [order] and the top of its first printed
 * line (points, from the amud's block's top).
 */
internal fun placesOfLines(
    file: DafLayoutFile,
    order: List<String>,
): Map<Long, Pair<Int, Float>> {
    val places = HashMap<Long, Pair<Int, Float>>()
    val at = order.withIndex().associate { (i, amud) -> amud to i }
    for (amud in file.amudim) {
        val index = at[amud.amud] ?: continue
        for (line in amud.lines) {
            if (!line.isGemara) continue
            for (id in line.lineIds) places.putIfAbsent(id, index to line.b[1] - amud.block[1])
        }
    }
    return places
}

/**
 * A tab's tractate shown as its Vilna page in the text view ([book]), and what a click on a word selects: a segment
 * ([onSelectLine], with whether Ctrl or Cmd was held), or a passage ([onSelectPassage]: the segment, the comments, their
 * books).
 */
class DafMode(
    val book: Book,
    val onSelectLine: (lineId: Long, withModifier: Boolean) -> Unit,
    val onSelectPassage: (lineId: Long, commentIds: Set<Long>, commentBookIds: Set<Long>) -> Unit,
)

/**
 * What the reader's keys and its find bar ask of the daf: a page up or down, the tractate's first or last amud, a
 * line of the library brought into view. Set by [DafView] while it is shown.
 */
@Stable
class DafControl {
    internal var page: suspend (forward: Boolean) -> Unit = {}
    internal var edge: suspend (end: Boolean) -> Unit = {}
    internal var toLine: suspend (lineId: Long) -> Unit = {}

    suspend fun scrollPage(forward: Boolean) = page(forward)

    suspend fun scrollToEdge(end: Boolean) = edge(end)

    suspend fun scrollToLine(lineId: Long) = toLine(lineId)
}

private sealed interface DafLoad {
    data object Loading : DafLoad

    data object Missing : DafLoad

    data class Ready(
        val file: DafLayoutFile,
    ) : DafLoad
}

/** The page's widest at the default text size (and no wider than the view); the text's zoom widens or narrows it. */
private const val MAX_PAGE_WIDTH = 1100f

/** Laid out at this many pixels a point, drawn at any width: the text's size is the zoom's, its places scaled. */
private const val LAYOUT_SCALE = 2f

/** The amudim kept laid out, scrolled away from and back to. */
private const val KEPT_LAYOUTS = 12

private val TitleHeight = 36.dp
private val PagePadding = 24.dp

/**
 * A Talmud tractate as printed in the Vilna Shas, inside the text view's own frame (its zoom, keys, selection and
 * menu): every amud of the tractate's layout (its file in the library's pardes-daf folder) one below the other, each
 * at its own height as printed, with its name above it; with no layout for the tractate, a word on it and a button to
 * import one. The pages are as wide as the view at the text's default size, at most [MAX_PAGE_WIDTH] dp, and the
 * text's zoom ([textSize]) makes them wider than the view, a sideways scroll showing the rest.
 *
 * The page answers as the text does, its panes the text's: a click on a Gemara word selects its segment
 * ([onSelectLine]; with Ctrl or Cmd, adds it), or, inside words a comment's dibbur quotes, that passage
 * ([onSelectPassage]: the segment and those comments, which the commentaries pane brings in view); a click on a comment
 * selects it on its segment. A drag selects the text, to copy or for the text's menu, which a right-click on a word
 * opens on its line ([onContextLine]). The tab's selection ([selectedLineIds], [selectedPassage]) is marked on the
 * page, with the comments on the selected segments; not a table of contents' entry selected whole
 * ([isTocEntrySelection]), which would mark a whole page. The tab's line ([lineId]) is brought into view whenever it
 * changes or the tab asks ([scrollToLineTimestamp]), as the text does.
 */
@Suppress("ComposeUnstableCollections") // the tab's selection, as the text view takes it
@Composable
fun DafView(
    book: Book,
    listState: LazyListState,
    control: DafControl,
    textSize: Float,
    lineId: Long?,
    selectedLineIds: Set<Long>,
    isTocEntrySelection: Boolean,
    selectedPassage: SelectedPassage?,
    scrollToLineTimestamp: Long,
    onSelectLine: (lineId: Long, withModifier: Boolean) -> Unit,
    onSelectPassage: (lineId: Long, commentIds: Set<Long>, commentBookIds: Set<Long>) -> Unit,
    onContextLine: (lineId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    // Next to the seforim.db open now, as the semantic search's model (SEFORIMAPP_DATABASE_PATH can name another one
    // than the settings)
    val layouts =
        remember(graph) {
            val libraryDir =
                runCatching { File(graph.databasePathProvider.get()).absoluteFile.parentFile }.getOrNull()
                    ?: LibraryLocation.currentDir(graph.appSettings)
            DafLayouts.of(libraryDir)
        }
    // Bumped by an import, to read the folder again
    var imports by remember { mutableIntStateOf(0) }
    val load by produceState<DafLoad>(DafLoad.Loading, layouts, book.title, imports) {
        val file = withContext(Dispatchers.IO) { layouts.tractate(book.title) }
        value = file?.takeIf { it.amudim.isNotEmpty() }?.let { DafLoad.Ready(it) } ?: DafLoad.Missing
    }
    Box(modifier.fillMaxSize()) {
        when (val state = load) {
            DafLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            DafLoad.Missing ->
                MissingLayout(
                    book = book,
                    layouts = layouts,
                    onImport = { imports++ },
                    modifier = Modifier.align(Alignment.Center),
                )
            is DafLoad.Ready ->
                DafPages(
                    book = book,
                    file = state.file,
                    listState = listState,
                    control = control,
                    textSize = textSize,
                    lineId = lineId,
                    selectedLineIds = selectedLineIds,
                    isTocEntrySelection = isTocEntrySelection,
                    selectedPassage = selectedPassage,
                    scrollToLineTimestamp = scrollToLineTimestamp,
                    onSelectLine = onSelectLine,
                    onSelectPassage = onSelectPassage,
                    onContextLine = onContextLine,
                )
        }
    }
}

/** A font of the daf by its settings' code: Noto Rashi Hebrew a variable font, its bold its weight axis at 700. */
@Composable
private fun dafFamily(code: String): FontFamily {
    if (code != AppSettings.DEFAULT_DAF_COMMENTARY_FONT) return FontCatalog.familyFor(code)
    val normal = Font(Res.font.notorashihebrew, FontWeight.Normal)
    val bold = Font(Res.font.notorashihebrew, FontWeight.Bold)
    return remember(normal, bold) { FontFamily(normal, bold) }
}

@Suppress("ComposeUnstableCollections")
@Composable
private fun DafPages(
    book: Book,
    file: DafLayoutFile,
    listState: LazyListState,
    control: DafControl,
    textSize: Float,
    lineId: Long?,
    selectedLineIds: Set<Long>,
    isTocEntrySelection: Boolean,
    selectedPassage: SelectedPassage?,
    scrollToLineTimestamp: Long,
    onSelectLine: (Long, Boolean) -> Unit,
    onSelectPassage: (Long, Set<Long>, Set<Long>) -> Unit,
    onContextLine: (Long) -> Unit,
) {
    val graph = LocalAppGraph.current
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gemaraCode by graph.appSettings.dafGemaraFontCodeFlow.collectAsState()
    val commentaryCode by graph.appSettings.dafCommentaryFontCodeFlow.collectAsState()
    val gemara = dafFamily(gemaraCode)
    val rashi = dafFamily(commentaryCode)
    val ink = JewelTheme.globalColors.text.normal
    val focus = JewelTheme.globalColors.outlines.focused

    // The amudim in order, those of the table of contents the layout has not too (drawn as missing)
    val amudLines by produceState(emptyMap<String, Long>(), book.id) {
        value = runSuspendCatching { firstLines(graph.repository.getTocEntriesForBook(book.id)) }.getOrDefault(emptyMap())
    }
    val order = remember(file, amudLines) { (file.amudim.map { it.amud } + amudLines.keys).distinct().sortedBy(::amudIndex) }
    val byName = remember(file) { file.amudim.associateBy { it.amud } }
    val places by produceState(emptyMap<Long, Pair<Int, Float>>(), file, order) {
        value = withContext(Dispatchers.Default) { placesOfLines(file, order) }
    }
    // The tractate's usual block: one scale for all its amudim, each as tall as printed
    val blockWidth =
        remember(file) {
            file.amudim
                .map { it.block[2] - it.block[0] }
                .sorted()
                .let { it[it.size / 2] }
        }
    val layouts = remember(file, gemara, rashi, density) { LayoutCache(KEPT_LAYOUTS) }
    val measurer = remember(resolver, density) { TextMeasurer(resolver, density, LayoutDirection.Rtl, cacheSize = 0) }
    val marked = if (isTocEntrySelection) emptySet() else selectedLineIds
    val passage = selectedPassage?.targetLineIds.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = constraints.maxWidth.toFloat()
        val padding = with(density) { PagePadding.toPx() }
        val base = minOf(viewport - 2 * padding, with(density) { MAX_PAGE_WIDTH.dp.toPx() }).coerceAtLeast(1f)
        val pageWidth = base * textSize / AppSettings.DEFAULT_TEXT_SIZE
        val perPoint = pageWidth / blockWidth
        // As wide as the view, or as the page once the zoom makes it wider (a wider amud takes some of its margin)
        val contentWidth = maxOf(viewport, pageWidth + 2 * padding)
        val titleHeight = with(density) { TitleHeight.toPx() }
        val viewportHeight = constraints.maxHeight.toFloat()
        val currentPlaces by rememberUpdatedState(places)
        val currentPerPoint by rememberUpdatedState(perPoint)

        // Brings the line of the library [id] into view, a little below the view's top, unless it is in view
        suspend fun reveal(
            id: Long,
            always: Boolean,
        ) {
            val (index, top) = currentPlaces[id] ?: return
            val y = titleHeight + top * currentPerPoint
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val shown = item != null && item.offset + y >= 0 && item.offset + y + titleHeight <= listState.layoutInfo.viewportEndOffset
            if (always || !shown) listState.scrollToItem(index, (y - titleHeight).toInt().coerceAtLeast(0))
        }
        control.page = { forward -> listState.animateScrollBy((if (forward) 1 else -1) * viewportHeight * PAGE_STEP) }
        control.edge = { end -> listState.scrollToItem(if (end) (order.size - 1).coerceAtLeast(0) else 0) }
        control.toLine = { id -> reveal(id, always = false) }

        // The tab's line in view: once the places are known, then whenever it changes or the tab asks
        var revealed by remember(file) { mutableStateOf(false) }
        LaunchedEffect(lineId, scrollToLineTimestamp, places) {
            val id = lineId?.takeIf { it > 0 } ?: return@LaunchedEffect
            if (places.isEmpty()) return@LaunchedEffect
            if (id !in places) {
                // A line not printed in the layout (an amud it is missing): its amud, by the table of contents
                val amud =
                    runSuspendCatching {
                        graph.repository
                            .getTocEntryForLine(id)
                            ?.text
                            ?.let(::amudOf)
                            ?: graph.repository
                                .getLine(id)
                                ?.heRef
                                ?.let(::amudOf)
                    }.getOrNull()
                val index = amud?.let(order::indexOf) ?: -1
                if (index >= 0 && (!revealed || listState.layoutInfo.visibleItemsInfo.none { it.index == index })) {
                    listState.scrollToItem(index)
                }
            } else {
                reveal(id, always = !revealed)
            }
            revealed = true
        }

        val hScroll = rememberScrollState()
        Box(
            Modifier
                .fillMaxSize()
                .horizontalScroll(hScroll)
                .sidewaysPan(hScroll, rtl),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(with(density) { contentWidth.toDp() }).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(order, key = { _, amud -> amud }) { _, name ->
                    val amud = byName[name]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(TitleHeight), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                "${file.tractateHe} ${amudTitle(name)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = JewelTheme.globalColors.text.info,
                            )
                        }
                        if (amud == null) {
                            Text(
                                stringResource(Res.string.daf_amud_missing),
                                Modifier.padding(vertical = 48.dp),
                                color = JewelTheme.globalColors.text.info,
                            )
                        } else {
                            AmudItem(
                                book = book,
                                amud = amud,
                                perPoint = perPoint,
                                layouts = layouts,
                                measurer = measurer,
                                density = density,
                                gemara = gemara,
                                rashi = rashi,
                                ink = ink,
                                color = focus,
                                marked = marked,
                                passage = passage,
                                onSelectLine = onSelectLine,
                                onSelectPassage = onSelectPassage,
                                onContextLine = onContextLine,
                                modifier = Modifier.padding(horizontal = PagePadding).padding(bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A page up or down moves this much of the view. */
private const val PAGE_STEP = 0.9f

@Suppress("ComposeUnstableCollections")
@Composable
private fun AmudItem(
    book: Book,
    amud: DafAmud,
    perPoint: Float,
    layouts: LayoutCache,
    measurer: TextMeasurer,
    density: Density,
    gemara: FontFamily,
    rashi: FontFamily,
    ink: Color,
    color: Color,
    marked: Set<Long>,
    passage: Set<Long>,
    onSelectLine: (Long, Boolean) -> Unit,
    onSelectPassage: (Long, Set<Long>, Set<Long>) -> Unit,
    onContextLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    // Laid out once, off the UI thread (some 160 lines measured), and kept while scrolled away
    val layout by produceState(layouts[amud], amud, layouts) {
        if (value == null) {
            value =
                withContext(Dispatchers.Default) {
                    layoutAmud(amud, LAYOUT_SCALE, measurer, density, gemara, rashi, beforeEachLine = { ensureActive() })
                }.also { layouts[amud] = it }
        }
    }
    // The page's comments and the Gemara lines they are on, read once for the page
    val links by produceState<DafAmudLinks?>(null, book.id, amud) { value = loadLinks(graph.repository, book.id, amud) }
    val highlight = remember(links, marked, passage, color) { dafHighlight(links, marked, passage, color) }
    val width = (amud.block[2] - amud.block[0]) * perPoint
    val height = (amud.block[3] - amud.block[1]) * perPoint
    with(density) {
        Box(modifier.width(width.toDp()).height(height.toDp())) {
            val laidOut = layout
            if (laidOut == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                DafAmudText(
                    layout = laidOut,
                    k = perPoint / LAYOUT_SCALE,
                    gemara = gemara,
                    rashi = rashi,
                    ink = ink,
                    highlight = highlight,
                    onWordClick = { word, withModifier -> links?.let { clickOn(it, word, withModifier, onSelectLine, onSelectPassage) } },
                    onContextWord = { word -> links?.segmentOf(word)?.let(onContextLine) },
                )
            }
        }
    }
}

/** The last few amudim laid out, by amud. */
private class LayoutCache(
    private val kept: Int,
) {
    private val map =
        object : LinkedHashMap<DafAmud, AmudLayout>(kept + 1, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DafAmud, AmudLayout>?) = size > kept
        }

    @Synchronized
    operator fun get(amud: DafAmud): AmudLayout? = map[amud]

    @Synchronized
    operator fun set(
        amud: DafAmud,
        layout: AmudLayout,
    ) {
        map[amud] = layout
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}

/** A wheel notch sideways, in pixels. */
private const val WHEEL_PX = 48f

/**
 * Scrolls [state] sideways, as the commentaries' pages turn (pageSwipe): a touchpad's sideways swipe with two fingers
 * (a pan on Windows), a tilt wheel or Shift + wheel; not with Ctrl or Cmd held (the zoom's). In the initial pass, ahead
 * of the pages' list, which would take Shift + wheel as its own scroll down.
 */
private fun Modifier.sidewaysPan(
    state: ScrollState,
    rtl: Boolean,
): Modifier =
    pointerInput(state, rtl) {
        awaitPointerEventScope {
            var panX = 0f
            var panY = 0f
            // Right to left, the sideways scroll's 0 is the right edge: a move right is back toward it
            val sign = if (rtl) -1f else 1f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                if (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed) continue
                when (event.type) {
                    PointerEventType.PanStart -> {
                        panX = 0f
                        panY = 0f
                    }
                    // Two fingers on a touchpad come as a pan (Windows), in px; an up-and-down one is the list's scroll
                    PointerEventType.PanMove -> {
                        panX += change.panOffset.x
                        panY += change.panOffset.y
                        if (abs(panX) <= abs(panY)) continue
                        state.dispatchRawDelta(-sign * change.panOffset.x)
                        change.consume()
                    }
                    PointerEventType.Scroll -> {
                        val delta = change.scrollDelta
                        val by =
                            when {
                                // Shift + wheel (the platform may give it as either axis): on along the reading, as
                                // the wheel down goes on
                                event.keyboardModifiers.isShiftPressed -> if (delta.x != 0f) delta.x else delta.y
                                // A tilt wheel or a touchpad: toward the side it moves to
                                abs(delta.x) > abs(delta.y) -> sign * delta.x
                                else -> continue
                            }
                        if (by == 0f) continue
                        state.dispatchRawDelta(by * WHEEL_PX * density)
                        change.consume()
                    }
                    else -> Unit
                }
            }
        }
    }

@Composable
private fun MissingLayout(
    book: Book,
    layouts: DafLayouts,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // What became of the last import, when it did not bring this tractate
    var problem by remember(book.id) { mutableStateOf<ImportProblem?>(null) }
    Column(
        modifier.widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.daf_missing, book.title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.daf_missing_hint, DafLayouts.DIR_NAME),
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = {
            scope.launch {
                // Off the GTK event-loop thread, as the data screen's pickers
                val picked =
                    withContext(Dispatchers.IO) { FileKit.openFilePicker(type = FileKitType.File(extensions = setOf("json"))) }
                        ?: return@launch
                problem =
                    withContext(Dispatchers.IO) {
                        val layout = runSuspendCatching { layouts.import(File(picked.path)) }.getOrNull()
                        when {
                            layout == null -> ImportProblem.NotLayout
                            !layouts.has(book.title) -> ImportProblem.OtherTractate(layout.tractateHe)
                            else -> null
                        }
                    }
                onImport()
            }
        }) {
            Text(stringResource(Res.string.daf_import))
        }
        when (val p = problem) {
            ImportProblem.NotLayout -> Text(stringResource(Res.string.daf_import_not_layout), color = JewelTheme.globalColors.text.error)
            is ImportProblem.OtherTractate ->
                Text(stringResource(Res.string.daf_import_other_tractate, p.tractate), color = JewelTheme.globalColors.text.error)
            null -> Unit
        }
    }
}

private sealed interface ImportProblem {
    data object NotLayout : ImportProblem

    data class OtherTractate(
        val tractate: String,
    ) : ImportProblem
}

/**
 * The page's ties to the library: its comments' Gemara lines and books, in one read each. The Gemara lines are the
 * comments' sources (the library's COMMENTARY links, read backward), one for each link: getSourcesForLines gives each
 * Gemara line once, for the first comment on it.
 */
private suspend fun loadLinks(
    repository: SeforimRepository,
    bookId: Long,
    amud: DafAmud,
): DafAmudLinks {
    val comments = DafAmudLinks.commentIdsOf(amud)
    val bases =
        runSuspendCatching { repository.getCommentarySummariesForLines(comments.toList(), includeSources = true) }
            .getOrDefault(emptyList())
            .map { it.link }
            .filter { it.connectionType == ConnectionType.SOURCE && it.targetBookId == bookId }
            .groupBy({ it.sourceLineId }, { it.targetLineId })
            .mapValues { (_, ids) -> ids.toSet() }
    val books = runSuspendCatching { repository.getLinesByIds(comments) }.getOrDefault(emptyList()).associate { it.id to it.bookId }
    return withContext(Dispatchers.Default) { DafAmudLinks(amud, bases, books) }
}

/** How strong each mark is: the passage selected, the segments selected, the comments on them. */
private const val PASSAGE_ALPHA = 0.32f
private const val SELECTED_ALPHA = 0.18f
private const val RELATED_ALPHA = 0.08f

/**
 * The page's marks: the selected passage (its comments and the Gemara words they quote) strongest, then the selected
 * segments, then the comments on the selected segments, lightest. Nothing follows the pointer: the page reads as
 * the text does, where only a selection is marked.
 */
internal fun dafHighlight(
    links: DafAmudLinks?,
    selected: Set<Long>,
    passage: Set<Long>,
    color: Color,
): DafHighlight {
    if (links == null || (selected.isEmpty() && passage.isEmpty())) return DafHighlight.NONE
    val related = selected.flatMapTo(HashSet()) { links.commentsOn[it].orEmpty() }
    val quotedWords = passage.flatMapTo(HashSet()) { links.quoted[it].orEmpty() }
    val strong = color.copy(alpha = PASSAGE_ALPHA)
    val selectedColor = color.copy(alpha = SELECTED_ALPHA)
    val relatedColor = color.copy(alpha = RELATED_ALPHA)
    return DafHighlight { word ->
        val id = links.lineIdOf(word) ?: return@DafHighlight null
        when {
            id in passage || word in quotedWords -> strong
            id in selected -> selectedColor
            id in related -> relatedColor
            else -> null
        }
    }
}

/**
 * A click on [word]: a Gemara word inside words a dibbur quotes selects that passage, another one its segment (with a
 * modifier, added to the selection); a comment's word selects the comment, on its segment.
 */
internal fun clickOn(
    links: DafAmudLinks,
    word: DafWord,
    withModifier: Boolean,
    onSelectLine: (Long, Boolean) -> Unit,
    onSelectPassage: (Long, Set<Long>, Set<Long>) -> Unit,
) {
    val id = links.lineIdOf(word) ?: return
    if (links.isGemara(word)) {
        val passage = if (withModifier) emptySet() else links.passageAt(word)
        if (passage.isEmpty()) {
            onSelectLine(id, withModifier)
        } else {
            onSelectPassage(id, passage, passage.mapNotNullTo(HashSet()) { links.bookOf[it] })
        }
    } else {
        val base = links.segmentOf(word) ?: return
        onSelectPassage(base, setOf(id), setOfNotNull(links.bookOf[id]))
    }
}

/** The Gemara segment of [word]: its own line, or for a comment's word the segment it is on (the one on this page). */
internal fun DafAmudLinks.segmentOf(word: DafWord): Long? {
    val id = lineIdOf(word) ?: return null
    if (isGemara(word)) return id
    val bases = baseOf[id].orEmpty()
    return bases.firstOrNull { it in wordsOf } ?: bases.firstOrNull()
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.context_menu_copy_with_source
import seforimapp.seforimapp.generated.resources.context_menu_search_selected_text
import seforimapp.seforimapp.generated.resources.daf_amud_missing
import seforimapp.seforimapp.generated.resources.daf_import
import seforimapp.seforimapp.generated.resources.daf_import_not_layout
import seforimapp.seforimapp.generated.resources.daf_import_other_tractate
import seforimapp.seforimapp.generated.resources.daf_missing
import seforimapp.seforimapp.generated.resources.daf_missing_hint
import seforimapp.seforimapp.generated.resources.daf_next_amud
import seforimapp.seforimapp.generated.resources.daf_previous_amud
import seforimapp.seforimapp.generated.resources.notorashihebrew
import java.io.File

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

private sealed interface DafLoad {
    data object Loading : DafLoad

    data object Missing : DafLoad

    data class Ready(
        val file: DafLayoutFile,
    ) : DafLoad
}

/** The page's widest at the default text size; the zoom buttons make it wider or narrower, as far as the view goes. */
private const val MAX_PAGE_WIDTH = 1100f

/**
 * A Talmud tractate as printed in the Vilna Shas: the amud of the tab's line ([lineId]) drawn from the tractate's
 * layout (its file in the library's pardes-daf folder), with its name and the previous and next amudim above it.
 * Going to another amud selects its first line, so the text and the line panes follow; with no layout for the
 * tractate, a word on it and a button to import one.
 *
 * The page answers as the text does, its panes the text's: a click on a Gemara word selects its segment
 * ([onSelectLine]; with Ctrl or Cmd, adds it), or, inside words a comment's dibbur quotes, that passage
 * ([onSelectPassage]: the segment and those comments, which the commentaries pane brings in view); a click on a comment
 * selects it on its segment. The tab's selection ([selectedLineIds], [selectedPassage]) is marked on the page, with
 * the comments on the selected segments and what a click under the pointer would select; not a table of contents'
 * entry selected whole ([isTocEntrySelection], as going to an amud does), which would mark all of the page.
 */
@Suppress("ComposeUnstableCollections") // the tab's selection, as the text view takes it
@Composable
fun DafReaderView(
    book: Book,
    lineId: Long?,
    selectedLineIds: Set<Long>,
    isTocEntrySelection: Boolean,
    selectedPassage: SelectedPassage?,
    onEvent: (BookContentEvent) -> Unit,
    onSelectLine: (lineId: Long, withModifier: Boolean) -> Unit,
    onSelectPassage: (lineId: Long, commentIds: Set<Long>, commentBookIds: Set<Long>) -> Unit,
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
    val amudLines by produceState(emptyMap<String, Long>(), book.id) {
        value = runSuspendCatching { firstLines(graph.repository.getTocEntriesForBook(book.id)) }.getOrDefault(emptyMap())
    }
    // The amud shown: the tab's line's, once known
    var shown by remember(book.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(book.id, lineId) {
        val id = lineId?.takeIf { it > 0 } ?: return@LaunchedEffect
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
        if (amud != null) shown = amud
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
            is DafLoad.Ready -> {
                val file = state.file
                val order = remember(file, amudLines) { (file.amudim.map { it.amud } + amudLines.keys).distinct().sortedBy(::amudIndex) }
                val current = shown ?: file.amudim.first().amud
                val at = order.indexOf(current)
                val textSize by graph.appSettings.textSizeFlow.collectAsState()
                val amud = file.amudim.firstOrNull { it.amud == current }
                // The page's comments and the Gemara lines they are on, read once for the page
                val links by produceState<DafAmudLinks?>(null, book.id, amud) {
                    value = amud?.let { loadLinks(graph.repository, book.id, it) }
                }
                var hovered by remember(amud) { mutableStateOf<DafWord?>(null) }
                val focus = JewelTheme.globalColors.outlines.focused
                val marked = if (isTocEntrySelection) emptySet() else selectedLineIds
                val highlight =
                    remember(links, marked, selectedPassage, hovered, focus) {
                        dafHighlight(links, marked, selectedPassage?.targetLineIds.orEmpty(), hovered, focus)
                    }
                val copyToClipboard = rememberCopyToClipboard()
                val scope = rememberCoroutineScope()
                val copyLabel = stringResource(Res.string.context_menu_copy_with_source)
                val searchLabel = stringResource(Res.string.context_menu_search_selected_text)
                // A word's menu, as the text's: its comment or segment copied with its source, the word searched
                val menuItems: (DafWord) -> List<ContextMenuItem> = { word ->
                    val lineId = links?.lineIdOf(word)
                    val query = amud?.wordText(word)?.let(::searchWord)
                    buildList {
                        if (lineId != null) {
                            add(
                                ContextMenuItemOption(icon = AllIconsKeys.Actions.Copy, label = copyLabel) {
                                    scope.launch {
                                        runSuspendCatching { graph.repository.getLine(lineId) }.getOrNull()?.let {
                                            copyToClipboard(copyWithSource(it.content, it.heRef))
                                        }
                                    }
                                },
                            )
                        }
                        if (!query.isNullOrEmpty()) {
                            add(
                                ContextMenuItemOption(icon = AllIconsKeys.Actions.Find, label = searchLabel) {
                                    onEvent(BookContentEvent.SearchInDatabase(query))
                                },
                            )
                        }
                    }
                }
                AmudPage(
                    title = "${file.tractateHe} ${amudTitle(current)}",
                    amud = amud,
                    highlight = highlight,
                    hovered = hovered,
                    menuItems = menuItems,
                    onWordClick = { word, withModifier ->
                        links?.let { clickOn(it, word, withModifier, onSelectLine, onSelectPassage) }
                    },
                    onHover = { hovered = it },
                    previous = order.getOrNull(at - 1)?.takeIf { at > 0 },
                    next = order.getOrNull(at + 1)?.takeIf { at >= 0 },
                    maxWidth = (MAX_PAGE_WIDTH * textSize / AppSettings.DEFAULT_TEXT_SIZE).dp,
                    onGo = { target ->
                        shown = target
                        amudLines[target]?.let { onEvent(BookContentEvent.LoadAndSelectLine(it)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun AmudPage(
    title: String,
    amud: DafAmud?,
    previous: String?,
    next: String?,
    maxWidth: Dp,
    highlight: DafHighlight,
    hovered: DafWord?,
    menuItems: (DafWord) -> List<ContextMenuItem>,
    onWordClick: (DafWord, Boolean) -> Unit,
    onHover: (DafWord?) -> Unit,
    onGo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The page takes the arrows once clicked: the next amud on the side reading goes to
    val focus = remember { FocusRequester() }
    val currentHovered by rememberUpdatedState(hovered)
    val gemara = FontCatalog.familyFor("frankruehlclm")
    // A variable font: its bold is its weight axis at 700
    val rashiNormal = Font(Res.font.notorashihebrew, FontWeight.Normal)
    val rashiBold = Font(Res.font.notorashihebrew, FontWeight.Bold)
    val rashi = remember(rashiNormal, rashiBold) { FontFamily(rashiNormal, rashiBold) }
    val ink = JewelTheme.globalColors.text.normal
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The previous amud on the side reading comes from
            IconButton(onClick = { previous?.let(onGo) }, enabled = previous != null, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (rtl) AllIconsKeys.General.ChevronRight else AllIconsKeys.General.ChevronLeft,
                    stringResource(Res.string.daf_previous_amud),
                )
            }
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { next?.let(onGo) }, enabled = next != null, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (rtl) AllIconsKeys.General.ChevronLeft else AllIconsKeys.General.ChevronRight,
                    stringResource(Res.string.daf_next_amud),
                )
            }
        }
        // A new amud opens at its top
        val scroll = remember(amud) { ScrollState(0) }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val target =
                        when (event.key) {
                            Key.DirectionLeft -> if (rtl) next else previous
                            Key.DirectionRight -> if (rtl) previous else next
                            else -> return@onPreviewKeyEvent false
                        }
                    target?.let(onGo)
                    true
                }.verticalScroll(scroll),
        ) {
            if (amud == null) {
                Text(
                    stringResource(Res.string.daf_amud_missing),
                    Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                    color = JewelTheme.globalColors.text.info,
                )
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    // The menu of the word under the pointer when it opens
                    ContextMenuArea(items = { currentHovered?.let(menuItems).orEmpty() }) {
                        DafPage(
                            amud = amud,
                            gemara = gemara,
                            rashi = rashi,
                            ink = ink,
                            highlight = highlight,
                            onWordClick = { word, withModifier ->
                                focus.requestFocus()
                                onWordClick(word, withModifier)
                            },
                            onHover = onHover,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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

/** How strong each mark is: the passage selected, the segments selected, under the pointer, the comments on them. */
private const val PASSAGE_ALPHA = 0.32f
private const val SELECTED_ALPHA = 0.18f
private const val HOVER_ALPHA = 0.12f
private const val RELATED_ALPHA = 0.08f

/**
 * The page's marks: the selected passage (its comments and the Gemara words they quote) strongest, then the selected
 * segments, then what a click under the pointer would select ([hovered]), then the comments on the selected
 * segments, lightest.
 */
internal fun dafHighlight(
    links: DafAmudLinks?,
    selected: Set<Long>,
    passage: Set<Long>,
    hovered: DafWord?,
    color: Color,
): DafHighlight {
    if (links == null) return DafHighlight.NONE
    val related = selected.flatMapTo(HashSet()) { links.commentsOn[it].orEmpty() }
    val quotedWords = passage.flatMapTo(HashSet()) { links.quoted[it].orEmpty() }
    val hoverPassage = hovered?.let { links.passageAt(it) }.orEmpty()
    val hoverWords = hoverPassage.flatMapTo(HashSet()) { links.quoted[it].orEmpty() }
    val hoverLine = hovered?.let { links.lineIdOf(it) }
    val strong = color.copy(alpha = PASSAGE_ALPHA)
    val selectedColor = color.copy(alpha = SELECTED_ALPHA)
    val hoverColor = color.copy(alpha = HOVER_ALPHA)
    val relatedColor = color.copy(alpha = RELATED_ALPHA)
    return DafHighlight { word ->
        val id = links.lineIdOf(word) ?: return@DafHighlight null
        when {
            id in passage || word in quotedWords -> strong
            id in selected -> selectedColor
            id in hoverPassage || word in hoverWords || (hoverPassage.isEmpty() && id == hoverLine) -> hoverColor
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
        // Its segment, the one printed on this page when it is on two
        val bases = links.baseOf[id].orEmpty()
        val base = bases.firstOrNull { it in links.wordsOf } ?: bases.firstOrNull() ?: return
        onSelectPassage(base, setOf(id), setOfNotNull(links.bookOf[id]))
    }
}

/** The word [word] of this amud as printed, or null. */
internal fun DafAmud.wordText(word: DafWord): String? =
    lines
        .getOrNull(word.line)
        ?.t
        ?.split(' ')
        ?.filter { it.isNotEmpty() }
        ?.getOrNull(word.word)

/** A printed word to search for: without the print's period, colon or comma, its abbreviation marks kept. */
internal fun searchWord(word: String): String = word.trim { !it.isLetter() && it !in "״׳\"'" }

/** A library line copied with its source, as the text's "העתק עם מקור" does: its text, then (its place). */
internal fun copyWithSource(
    content: String,
    heRef: String?,
): String {
    val text =
        content
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    return if (heRef.isNullOrBlank()) text else "$text\n\n($heRef)"
}

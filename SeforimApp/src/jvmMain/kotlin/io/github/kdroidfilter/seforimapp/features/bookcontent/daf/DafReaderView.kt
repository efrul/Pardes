package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.Amud
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.TocNumber
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
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
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
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
 */
@Composable
fun DafReaderView(
    book: Book,
    lineId: Long?,
    onEvent: (BookContentEvent) -> Unit,
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
                AmudPage(
                    title = "${file.tractateHe} ${amudTitle(current)}",
                    amud = file.amudim.firstOrNull { it.amud == current },
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
    onGo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            if (amud == null) {
                Text(
                    stringResource(Res.string.daf_amud_missing),
                    Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                    color = JewelTheme.globalColors.text.info,
                )
            } else {
                DafPage(
                    amud = amud,
                    gemara = gemara,
                    rashi = rashi,
                    ink = ink,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = maxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                )
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

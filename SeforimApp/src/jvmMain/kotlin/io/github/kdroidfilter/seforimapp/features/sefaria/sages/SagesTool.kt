package io.github.kdroidfilter.seforimapp.features.sefaria.sages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.DefaultToolWindow
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WIDGET_LOADING_TAG
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetTitle
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.sefaria.AmbiguousSage
import io.github.kdroidfilter.seforimapp.features.sefaria.BackTitle
import io.github.kdroidfilter.seforimapp.features.sefaria.ChipFlow
import io.github.kdroidfilter.seforimapp.features.sefaria.ChipSection
import io.github.kdroidfilter.seforimapp.features.sefaria.LocalSefariaWindow
import io.github.kdroidfilter.seforimapp.features.sefaria.Sage
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaChip
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaHint
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaLine
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSages
import io.github.kdroidfilter.seforimapp.features.sefaria.SourceRow
import io.github.kdroidfilter.seforimapp.features.sefaria.generationLabel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_sages
import seforimapp.seforimapp.generated.resources.sefaria_loading
import seforimapp.seforimapp.generated.resources.sefaria_sage_all
import seforimapp.seforimapp.generated.resources.sefaria_sage_ambiguous
import seforimapp.seforimapp.generated.resources.sefaria_sage_family
import seforimapp.seforimapp.generated.resources.sefaria_sage_groups
import seforimapp.seforimapp.generated.resources.sefaria_sage_mentioned
import seforimapp.seforimapp.generated.resources.sefaria_sage_students
import seforimapp.seforimapp.generated.resources.sefaria_sage_teachers
import seforimapp.seforimapp.generated.resources.sefaria_sage_wiki
import seforimapp.seforimapp.generated.resources.sefaria_sages_credit
import seforimapp.seforimapp.generated.resources.sefaria_sages_none_found
import seforimapp.seforimapp.generated.resources.sefaria_sages_search_hint
import java.util.UUID

/**
 * The persons the Mishnah and the Talmud mention, from Sefaria's data: an index of them, the most mentioned first, and a
 * person's page (its generation, its teachers and students, its description) with every line that mentions it, by
 * tractate, each opening in the reader. A Home card and a tool window, as the dictionary's, its window opened on a person
 * by a name's link (SageLinks).
 */
internal object SagesTool : HomeWidget {
    override val id = "sefaria_sages"
    override val title = Res.string.home_widget_name_sages
    override val defaultSpan = CellSpan(6, 5)
    override val minSpan = CellSpan(4, 4)
    override val maxSpan = CellSpan(10, 10)
    override val toolWindowSize = DpSize(560.dp, 700.dp)
    override val toolWindowMinSize = DpSize(400.dp, 440.dp)
    override val toolSymbol = "person.2"

    @Composable
    override fun ToolWindow(
        state: HomeWidgetsState,
        onClose: () -> Unit,
    ) = CompositionLocalProvider(LocalSefariaWindow provides true) { DefaultToolWindow(this, state, onClose) }

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val windows = LocalAppGraph.current.sefariaWindows
        val sages by produceState<SefariaSages?>(null) { value = withContext(Dispatchers.Default) { SefariaSages.shipped } }
        var trail by remember { mutableStateOf(emptyList<Int>()) }
        if (LocalSefariaWindow.current) {
            LaunchedEffect(windows.sageAsks) { windows.sage?.let { trail = listOf(it) } }
        }
        val accent = rememberAccentColor(JewelTheme.isDark)
        PanelCard(modifier) {
            val loaded = sages
            // Still loading, as the other widgets' loading card: its tag is what the size test waits for
            if (loaded == null) return@PanelCard CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(WIDGET_LOADING_TAG))
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val page = trail.lastOrNull()
                when {
                    page != null ->
                        SagePage(loaded, page, accent, onBack = { trail = trail.dropLast(1) }, onSage = { trail = trail + it }) { line ->
                            state.openTab(
                                TabsDestination.BookContent(bookId = line.bookId, tabId = UUID.randomUUID().toString(), lineId = line.id),
                            )
                        }
                    else -> SagesIndex(loaded, accent) { trail = listOf(it) }
                }
            }
        }
    }
}

/** The persons, the most mentioned first, or those whose name holds what is typed. */
@Composable
private fun ColumnScope.SagesIndex(
    sages: SefariaSages,
    accent: Color,
    onSage: (Int) -> Unit,
) {
    val query = rememberTextFieldState()
    WidgetTitle(stringResource(Res.string.home_widget_name_sages)) {
        Text(stringResource(Res.string.sefaria_sages_credit), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
    }
    TextField(
        state = query,
        placeholder = { Text(stringResource(Res.string.sefaria_sages_search_hint)) },
        leadingIcon = { Icon(AllIconsKeys.Actions.Find, null, Modifier.padding(end = 6.dp)) },
        textStyle = TextStyle(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth(),
    )
    val typed = query.text.toString()
    val found = remember(sages, typed) { sages.search(typed).take(MAX_LISTED) }
    if (found.isEmpty()) return SefariaHint(stringResource(Res.string.sefaria_sages_none_found))
    LazyColumn(Modifier.fillMaxSize()) {
        items(found, key = { it.id }) { sage -> SageRow(sage, sages.placesOf(sage.id).size, accent) { onSage(sage.id) } }
    }
}

@Composable
private fun SageRow(
    sage: Sage,
    places: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    HoverBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(sage.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
            generationLabel(sage.generation)?.let { Text(it, fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1) }
            Text(places.toString(), fontSize = 11.sp, color = JewelTheme.globalColors.text.info, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A person's page: its generation and description, the persons around it, and the lines that mention it, a tractate's
 * at a time once picked. For a name Sefaria could not tell between persons, those persons.
 */
@Composable
private fun ColumnScope.SagePage(
    sages: SefariaSages,
    id: Int,
    accent: Color,
    onBack: () -> Unit,
    onSage: (Int) -> Unit,
    onOpenLine: (SefariaLine) -> Unit,
) {
    sages.ambiguous[id]?.let { name -> return AmbiguousPage(name, sages, accent, onBack, onSage) }
    val sage = sages.sages[id] ?: return BackTitle("", accent, onBack)
    val graph = LocalAppGraph.current
    val uriHandler = LocalUriHandler.current
    val heRefs = remember(sages, id) { sages.placesOf(id) }
    val lines by produceState<List<SefariaLine>?>(null, id) {
        val found = graph.sefariaLines.linesOf(heRefs)
        value = found.values.sortedWith(compareBy({ it.bookId }, { it.lineIndex }))
    }
    var tractate by remember(id) { mutableStateOf<String?>(null) }
    BackTitle(sage.name, accent, onBack) {
        if (sage.wiki.isNotEmpty()) {
            HoverBox(onClick = { uriHandler.openUri(sage.wiki) }) {
                Text(
                    stringResource(Res.string.sefaria_sage_wiki),
                    fontSize = 12.sp,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SageAbout(sage, sages, heRefs.size, accent, onSage) }
        val found = lines
        if (found == null) {
            item { Text(stringResource(Res.string.sefaria_loading), fontSize = 12.sp, color = JewelTheme.globalColors.text.info) }
        } else {
            val byBook = found.groupBy { it.bookTitle }
            item {
                ChipFlow {
                    SefariaChip(stringResource(Res.string.sefaria_sage_all), found.size, tractate == null, accent) { tractate = null }
                    byBook.entries.sortedByDescending { it.value.size }.take(TRACTATES).forEach { (book, bookLines) ->
                        SefariaChip(book, bookLines.size, tractate == book, accent) { tractate = book }
                    }
                }
            }
            val shown = tractate?.let(byBook::get) ?: found
            items(shown.take(MAX_PLACES), key = { it.id }) { line ->
                val name = sages.at(line.heRef).firstOrNull { it.sageId == id && it.text.isNotEmpty() }?.text ?: sage.name
                SourceRow(line, accent, around = name) { onOpenLine(line) }
            }
            item { Text(stringResource(Res.string.sefaria_sages_credit), fontSize = 11.sp, color = JewelTheme.globalColors.text.info) }
        }
    }
}

/** A name Sefaria could not tell between persons: those persons, to pick one. */
@Composable
private fun ColumnScope.AmbiguousPage(
    name: AmbiguousSage,
    sages: SefariaSages,
    accent: Color,
    onBack: () -> Unit,
    onSage: (Int) -> Unit,
) {
    BackTitle(name.name, accent, onBack)
    Text(stringResource(Res.string.sefaria_sage_ambiguous, name.options.size), fontSize = 14.sp)
    ChipFlow {
        name.options.mapNotNull(sages.sages::get).forEach { sage ->
            SefariaChip(sage.name, sages.placesOf(sage.id).size, false, accent) { onSage(sage.id) }
        }
    }
}

/** What a person's page says of it above its places: its generation, its description, the persons around it. */
@Composable
private fun SageAbout(
    sage: Sage,
    sages: SefariaSages,
    places: Int,
    accent: Color,
    onSage: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        generationLabel(sage.generation)?.let { Text(it, fontSize = 13.sp, color = JewelTheme.globalColors.text.info) }
        if (sage.description.isNotEmpty()) Text(sage.description, fontSize = 14.sp, lineHeight = 22.sp)
        PeopleChips(stringResource(Res.string.sefaria_sage_teachers), sages.teachers(sage.id), sages, accent, onSage)
        PeopleChips(stringResource(Res.string.sefaria_sage_students), sages.students(sage.id), sages, accent, onSage)
        PeopleChips(stringResource(Res.string.sefaria_sage_family), sages.family(sage.id), sages, accent, onSage)
        PeopleChips(stringResource(Res.string.sefaria_sage_groups), sages.groups(sage.id), sages, accent, onSage)
        Text(stringResource(Res.string.sefaria_sage_mentioned, places), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PeopleChips(
    title: String,
    people: List<Sage>,
    sages: SefariaSages,
    accent: Color,
    onSage: (Int) -> Unit,
) = ChipSection(title, people.size) {
    people.distinctBy { it.id }.forEach { sage -> SefariaChip(sage.name, sages.placesOf(sage.id).size, false, accent) { onSage(sage.id) } }
}

private const val MAX_LISTED = 300
private const val MAX_PLACES = 400
private const val TRACTATES = 12

package io.github.kdroidfilter.seforimapp.features.sefaria.topics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetTitle
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.sefaria.BackTitle
import io.github.kdroidfilter.seforimapp.features.sefaria.ChipFlow
import io.github.kdroidfilter.seforimapp.features.sefaria.ChipSection
import io.github.kdroidfilter.seforimapp.features.sefaria.LocalSefariaWindow
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaChip
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaHint
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaLine
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaTopics
import io.github.kdroidfilter.seforimapp.features.sefaria.SourceRow
import io.github.kdroidfilter.seforimapp.features.sefaria.Topic
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_topics
import seforimapp.seforimapp.generated.resources.sefaria_loading
import seforimapp.seforimapp.generated.resources.sefaria_topic_broader
import seforimapp.seforimapp.generated.resources.sefaria_topic_dependents
import seforimapp.seforimapp.generated.resources.sefaria_topic_more
import seforimapp.seforimapp.generated.resources.sefaria_topic_narrower
import seforimapp.seforimapp.generated.resources.sefaria_topic_related
import seforimapp.seforimapp.generated.resources.sefaria_topic_shown_under
import seforimapp.seforimapp.generated.resources.sefaria_topic_sources_in_books
import seforimapp.seforimapp.generated.resources.sefaria_topic_time
import seforimapp.seforimapp.generated.resources.sefaria_topics_credit
import seforimapp.seforimapp.generated.resources.sefaria_topics_index
import seforimapp.seforimapp.generated.resources.sefaria_topics_index_count
import seforimapp.seforimapp.generated.resources.sefaria_topics_none_found
import seforimapp.seforimapp.generated.resources.sefaria_topics_search_hint
import seforimapp.seforimapp.generated.resources.sefaria_topics_tree
import java.util.UUID

/**
 * Sefaria's topics: browsed in the tree Sefaria shows them in or by their first letter, or searched by name; a topic
 * opened shows its description, its sources by book (each opens in the reader) and the topics around it. A tool window
 * of the Tools menu (sefariaTools), never a widget of the Home, opened on a topic from the reader's pane or the search.
 */
internal val TopicsTool: HomeWidget = TopicsWindow()

// A class and not an object: the widget processor makes every HomeWidget object a widget of the Home
private class TopicsWindow : HomeWidget {
    override val id = "sefaria_topics"
    override val title = Res.string.home_widget_name_topics
    override val defaultSpan = CellSpan(6, 6)
    override val toolWindowSize = DpSize(560.dp, 680.dp)
    override val toolWindowMinSize = DpSize(400.dp, 440.dp)
    override val toolSymbol = "tag"

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
        val topics by produceState<SefariaTopics?>(null) { value = withContext(Dispatchers.Default) { SefariaTopics.shipped } }
        // The topics opened one from another, the last shown: back goes to the one before
        var trail by remember { mutableStateOf(emptyList<Int>()) }
        if (LocalSefariaWindow.current) {
            LaunchedEffect(windows.topicAsks) { windows.topic?.let { trail = listOf(it) } }
        }
        val accent = rememberAccentColor(JewelTheme.isDark)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val loaded = topics
                val page = trail.lastOrNull()
                when {
                    loaded == null -> SefariaHint(stringResource(Res.string.sefaria_loading))
                    page != null ->
                        TopicPage(loaded, page, accent, onBack = { trail = trail.dropLast(1) }, onTopic = { trail = trail + it }) { line ->
                            state.openTab(
                                TabsDestination.BookContent(bookId = line.bookId, tabId = UUID.randomUUID().toString(), lineId = line.id),
                            )
                        }
                    else -> TopicsBrowser(loaded, accent) { trail = listOf(it) }
                }
            }
        }
    }
}

/** The topics browsed: by name typed, else in the tree or by their first letter. */
@Composable
private fun ColumnScope.TopicsBrowser(
    topics: SefariaTopics,
    accent: Color,
    onTopic: (Int) -> Unit,
) {
    val query = rememberTextFieldState()
    var byLetter by remember { mutableStateOf(false) }
    var letter by remember { mutableStateOf('א') }
    // The branches opened, kept while the index or a search is shown
    val expanded = remember { mutableStateListOf<Int>() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetTitle(stringResource(Res.string.home_widget_name_topics)) {
            Text(stringResource(Res.string.sefaria_topics_credit), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
        }
        TextField(
            state = query,
            placeholder = { Text(stringResource(Res.string.sefaria_topics_search_hint)) },
            leadingIcon = { Icon(AllIconsKeys.Actions.Find, null, Modifier.padding(end = 6.dp)) },
            textStyle = TextStyle(fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val typed = query.text.toString()
    if (typed.isNotBlank()) {
        val found = remember(topics, typed) { topics.search(typed).take(MAX_FOUND) }
        if (found.isEmpty()) {
            SefariaHint(stringResource(Res.string.sefaria_topics_none_found))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    found,
                    key = { it.id },
                ) { topic -> TopicRow(topic, topics.sourceCount(topic.id), 0, null, accent) { onTopic(topic.id) } }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SefariaChip(stringResource(Res.string.sefaria_topics_tree), null, !byLetter, accent) { byLetter = false }
        SefariaChip(stringResource(Res.string.sefaria_topics_index), null, byLetter, accent) { byLetter = true }
    }
    if (byLetter) {
        ChipFlow { LETTERS.forEach { l -> SefariaChip(l.toString(), null, l == letter, accent) { letter = l } } }
        val shown = remember(topics, letter) { topics.startingWith(letter) }
        Text(
            stringResource(Res.string.sefaria_topics_index_count, shown.size, letter.toString()),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { topic -> TopicRow(topic, topics.sourceCount(topic.id), 0, null, accent) { onTopic(topic.id) } }
        }
    } else {
        TopicTree(topics, expanded, accent, onTopic)
    }
}

/** The topics' tree as rows: a branch opened shows what is under it, indented; one with no sources of its own opens. */
@Composable
private fun TopicTree(
    topics: SefariaTopics,
    expanded: SnapshotStateList<Int>,
    accent: Color,
    onTopic: (Int) -> Unit,
) {
    val rows =
        remember(topics, expanded.toList()) {
            buildList {
                fun addBranch(
                    topic: Topic,
                    depth: Int,
                ) {
                    add(topic to depth)
                    if (topic.id in expanded && depth < MAX_DEPTH) topics.children(topic.id).forEach { addBranch(it, depth + 1) }
                }
                topics.roots.mapNotNull(topics.topics::get).forEach { addBranch(it, 0) }
            }
        }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { (topic, depth) -> "${topic.id}-$depth" }) { (topic, depth) ->
            val branch = topics.children(topic.id).isNotEmpty()
            val open = topic.id in expanded
            val toggle = { if (open) expanded.remove(topic.id) else expanded.add(topic.id) }
            TopicRow(topic, topics.sourceCount(topic.id), depth, if (branch) open else null, accent, onToggle = { toggle() }) {
                if (branch && topics.sourceCount(topic.id) == 0) toggle() else onTopic(topic.id)
            }
        }
    }
}

/** A topic in a list: its name and its count of sources, indented [depth]; [open] is a branch's, null for a leaf. */
@Composable
private fun TopicRow(
    topic: Topic,
    sources: Int,
    depth: Int,
    open: Boolean?,
    accent: Color,
    onToggle: () -> Unit = {},
    onClick: () -> Unit,
) {
    HoverBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = (6 + depth * INDENT).dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (open != null) {
                IconButton(onClick = onToggle, modifier = Modifier.size(18.dp)) {
                    Icon(if (open) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronLeft, null)
                }
            }
            Text(
                topic.name,
                fontSize = 14.sp,
                fontWeight = if (depth == 0 && open != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (sources > 0) accent else JewelTheme.globalColors.text.normal,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (sources > 0) Text(sources.toString(), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
        }
    }
}

/** A topic's page: its description, its sources by book, and the topics around it. */
@Composable
private fun TopicPage(
    topics: SefariaTopics,
    id: Int,
    accent: Color,
    onBack: () -> Unit,
    onTopic: (Int) -> Unit,
    onOpenLine: (SefariaLine) -> Unit,
) {
    val topic = topics.topics[id] ?: return BackTitle("", accent, onBack)
    val graph = LocalAppGraph.current
    val heRefs = remember(topics, id) { topics.sourcesOf(id) }
    val lines by produceState<Map<String, SefariaLine>?>(null, id) { value = graph.sefariaLines.linesOf(heRefs) }
    val opened = remember(id) { mutableStateListOf<String>() }
    BackTitle(topic.name, accent, onBack)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (topic.description.isNotEmpty()) {
            item { Text(topic.description, fontSize = 14.sp, lineHeight = 22.sp, color = JewelTheme.globalColors.text.normal) }
        }
        val found = lines
        if (found == null) {
            item { Text(stringResource(Res.string.sefaria_loading), fontSize = 12.sp, color = JewelTheme.globalColors.text.info) }
        } else {
            val books = heRefs.mapNotNull(found::get).groupBy { it.bookTitle }
            item {
                Text(
                    stringResource(Res.string.sefaria_topic_sources_in_books, heRefs.size, books.size),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            books.forEach { (book, bookLines) ->
                item(key = "book-$book") {
                    BookSources(book, bookLines, all = book in opened, accent, onMore = { opened.add(book) }, onOpenLine)
                }
            }
        }
        item { TopicNeighbours(topics, id, accent, onTopic) }
    }
}

/** A book's sources of a topic: its first [PER_BOOK], or [all] of them once its "more" is clicked. */
@Composable
private fun BookSources(
    book: String,
    lines: List<SefariaLine>,
    all: Boolean,
    accent: Color,
    onMore: () -> Unit,
    onOpenLine: (SefariaLine) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Text("(${lines.size})", fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
        }
        (if (all) lines else lines.take(PER_BOOK)).forEach { line ->
            SourceRow(line, accent, showBook = false) { onOpenLine(line) }
        }
        if (!all && lines.size > PER_BOOK) {
            HoverBox(onClick = onMore) {
                Text(
                    stringResource(Res.string.sefaria_topic_more, lines.size - PER_BOOK),
                    fontSize = 12.sp,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The topics around topic [id], each kind of neighbour in its section, and the credit to Sefaria. */
@Composable
private fun TopicNeighbours(
    topics: SefariaTopics,
    id: Int,
    accent: Color,
    onTopic: (Int) -> Unit,
) {
    val neighbours = remember(topics, id) { topics.neighbours(id) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        NeighbourChips(stringResource(Res.string.sefaria_topic_shown_under), neighbours.shownUnder, topics, accent, onTopic)
        NeighbourChips(stringResource(Res.string.sefaria_topic_broader), neighbours.broader, topics, accent, onTopic)
        NeighbourChips(stringResource(Res.string.sefaria_topic_narrower), neighbours.narrower, topics, accent, onTopic)
        NeighbourChips(stringResource(Res.string.sefaria_topic_time), neighbours.contains + neighbours.around, topics, accent, onTopic)
        NeighbourChips(stringResource(Res.string.sefaria_topic_dependents), neighbours.dependents, topics, accent, onTopic)
        NeighbourChips(stringResource(Res.string.sefaria_topic_related), neighbours.related, topics, accent, onTopic)
        Text(stringResource(Res.string.sefaria_topics_credit), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
    }
}

@Composable
private fun NeighbourChips(
    title: String,
    shown: List<Topic>,
    topics: SefariaTopics,
    accent: Color,
    onTopic: (Int) -> Unit,
) = ChipSection(title, shown.size) {
    shown.distinctBy { it.id }.forEach { topic ->
        SefariaChip(topic.name, topics.sourceCount(topic.id).takeIf { it > 0 }, false, accent) { onTopic(topic.id) }
    }
}

// The index's letters, finals aside
private const val LETTERS = "אבגדהוזחטיכלמנסעפצקרשת"
private const val MAX_FOUND = 80
private const val MAX_DEPTH = 4
private const val PER_BOOK = 3
private const val INDENT = 16

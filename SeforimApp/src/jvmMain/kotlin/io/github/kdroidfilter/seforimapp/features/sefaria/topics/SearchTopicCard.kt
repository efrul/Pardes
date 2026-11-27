package io.github.kdroidfilter.seforimapp.features.sefaria.topics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.search.ResultLink
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaTopics
import io.github.kdroidfilter.seforimapp.features.sefaria.Topic
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Contextual_token
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.sefaria_topic_card_kind
import seforimapp.seforimapp.generated.resources.sefaria_topic_more
import seforimapp.seforimapp.generated.resources.sefaria_topic_open_page
import seforimapp.seforimapp.generated.resources.sefaria_topic_related
import seforimapp.seforimapp.generated.resources.sefaria_topic_sources_in_books
import seforimapp.seforimapp.generated.resources.sefaria_topics_credit

/**
 * The topic of Sefaria's a search's query names, as the area beside its results shows it (SearchArea): its
 * description, its count of sources, the books most of them are in, the topics around it and the way to its page.
 */
@Composable
internal fun ColumnScope.TopicDetails(
    topic: Topic,
    topics: SefariaTopics,
) {
    val graph = LocalAppGraph.current
    // Its sources read from the file, off the UI thread, then their books in the library
    val books by produceState<ImmutableList<Pair<String, Int>>>(persistentListOf(), topics, topic.id) {
        val lines = graph.sefariaLines.linesOf(withContext(Dispatchers.IO) { topics.sourcesOf(topic.id) })
        value =
            lines.values
                .groupingBy { it.bookTitle }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
                .toImmutableList()
    }
    val related =
        remember(topics, topic.id) {
            topics
                .neighbours(topic.id)
                .let { it.broader + it.related }
                .distinctBy { it.id }
                .take(RELATED)
        }
    val grey = readingSecondary()
    val accent = JewelTheme.globalColors.outlines.focused
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Contextual_token, contentDescription = null, tint = grey, modifier = Modifier.size(20.dp))
        Text(topic.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.normal)
        Text(stringResource(Res.string.sefaria_topic_card_kind), fontSize = 12.sp, color = grey)
    }
    if (topic.description.isNotEmpty()) {
        Text(
            topic.description,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = grey,
            maxLines = DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val windows = graph.sefariaWindows
    if (books.isNotEmpty()) TopicBooks(topics.sourceCount(topic.id), books) { book -> windows.openTopic(topic.id, book) }
    // Each topic around it a link to its own page
    if (related.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.sefaria_topic_related) + ":", fontSize = 12.sp, color = grey)
            related.forEach { ResultLink(it.name, accent, 12.sp) { windows.openTopic(it.id) } }
        }
    }
    // The way to its page, as Zayit's panel has it to an author's (DefaultButton, the accent's) and the sage's card
    DefaultButton(onClick = { windows.openTopic(topic.id) }) { Text(stringResource(Res.string.sefaria_topic_open_page)) }
    Text(stringResource(Res.string.sefaria_topics_credit), fontSize = 11.sp, color = grey)
}

/**
 * A topic's count of [sources] and the [books] most of them are in, each a link to the topic's page on its sources, as
 * the parts of Zayit's book details are ([onOpen] with the book); the others to the page ([onOpen] with none).
 */
@Composable
private fun ColumnScope.TopicBooks(
    sources: Int,
    books: ImmutableList<Pair<String, Int>>,
    onOpen: (book: String?) -> Unit,
) {
    val grey = readingSecondary()
    Text(stringResource(Res.string.sefaria_topic_sources_in_books, sources, books.size), fontSize = 13.sp, color = grey)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        books.take(TOP_BOOKS).forEach { (book, count) ->
            ResultLink("$book $count", JewelTheme.globalColors.outlines.focused, 13.sp) { onOpen(book) }
        }
        if (books.size > TOP_BOOKS) {
            ResultLink(stringResource(Res.string.sefaria_topic_more, books.size - TOP_BOOKS), grey, 13.sp) { onOpen(null) }
        }
    }
}

private const val DESCRIPTION_LINES = 4
private const val TOP_BOOKS = 5
private const val RELATED = 6

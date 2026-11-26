package io.github.kdroidfilter.seforimapp.features.sefaria.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaTopics
import io.github.kdroidfilter.seforimapp.features.sefaria.Topic
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Contextual_token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.sefaria_topic_card_kind
import seforimapp.seforimapp.generated.resources.sefaria_topic_open_page
import seforimapp.seforimapp.generated.resources.sefaria_topic_related
import seforimapp.seforimapp.generated.resources.sefaria_topic_sources_in_books
import seforimapp.seforimapp.generated.resources.sefaria_topics_credit

/**
 * Beside a search's results, the topic its [query] names (SefariaTopics.named), as the panel of a book or an author it
 * names: its description, its count of sources, the books most of them are in, the topics around it and the way to its
 * page. Under the book's panel when the query names both (שבת); nothing when it names no topic.
 */
@Composable
fun SearchTopicCard(
    query: String,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val found by produceState<Pair<SefariaTopics, Topic>?>(null, query) {
        value =
            withContext(Dispatchers.Default) {
                val topics = SefariaTopics.shipped
                topics.named(query.trim())?.takeIf { topics.sourceCount(it.id) > 0 }?.let { topics to it }
            }
    }
    val (topics, topic) = found ?: return
    val books by produceState(emptyList<Pair<String, Int>>(), topic.id) {
        val lines = graph.sefariaLines.linesOf(topics.sourcesOf(topic.id))
        value =
            lines.values
                .groupingBy { it.bookTitle }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
    }
    val related =
        remember(topics, topic.id) {
            topics
                .neighbours(topic.id)
                .let { it.broader + it.related }
                .distinctBy { it.id }
                .take(RELATED)
        }
    TopicCard(topic, topics.sourceCount(topic.id), books, related, onOpen = { graph.sefariaWindows.openTopic(topic.id) }, modifier)
}

/** The search's card of [topic]: its description, its [sources] in the [books] most of them are in, [related] topics. */
@Composable
private fun TopicCard(
    topic: Topic,
    sources: Int,
    books: List<Pair<String, Int>>,
    related: List<Topic>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grey = readingSecondary()
    val accent = JewelTheme.globalColors.outlines.focused
    Column(
        modifier
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        if (books.isNotEmpty()) {
            Text(stringResource(Res.string.sefaria_topic_sources_in_books, sources, books.size), fontSize = 13.sp, color = grey)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                books.take(TOP_BOOKS).forEach { (book, count) -> Text("$book $count", fontSize = 13.sp, color = accent, maxLines = 1) }
            }
        }
        if (related.isNotEmpty()) {
            Text(
                stringResource(Res.string.sefaria_topic_related) + ": " + related.joinToString(" · ") { it.name },
                fontSize = 12.sp,
                color = grey,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(onClick = onOpen) { Text(stringResource(Res.string.sefaria_topic_open_page)) }
        Text(stringResource(Res.string.sefaria_topics_credit), fontSize = 11.sp, color = grey)
    }
}

private const val DESCRIPTION_LINES = 4
private const val TOP_BOOKS = 5
private const val RELATED = 6

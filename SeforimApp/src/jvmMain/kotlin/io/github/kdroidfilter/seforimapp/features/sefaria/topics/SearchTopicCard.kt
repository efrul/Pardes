package io.github.kdroidfilter.seforimapp.features.sefaria.topics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSettings
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaTopics
import io.github.kdroidfilter.seforimapp.features.sefaria.Topic
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Contextual_token
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_card_topic
import seforimapp.seforimapp.generated.resources.search_card_topic_tooltip
import seforimapp.seforimapp.generated.resources.sefaria_topic_card_kind
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
    if (books.isNotEmpty()) {
        Text(
            stringResource(Res.string.sefaria_topic_sources_in_books, topics.sourceCount(topic.id), books.size),
            fontSize = 13.sp,
            color = grey,
        )
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
    OutlinedButton(onClick = { graph.sefariaWindows.openTopic(topic.id) }) { Text(stringResource(Res.string.sefaria_topic_open_page)) }
    Text(stringResource(Res.string.sefaria_topics_credit), fontSize = 11.sp, color = grey)
}

/** The search's end bar's button of the topic and the person of Sefaria's the query names (SearchPanelsSettings.kt). */
@Composable
fun SearchTopicCardButton() {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(SefariaSettings.searchTopicCard).collectAsState()
    val label = stringResource(Res.string.search_card_topic)
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.search_card_topic_tooltip),
        onClick = { appSettings[SefariaSettings.searchTopicCard] = !shown },
        isSelected = shown,
        icon = Contextual_token,
        iconDescription = label,
        label = label,
    )
}

private const val DESCRIPTION_LINES = 4
private const val TOP_BOOKS = 5
private const val RELATED = 6

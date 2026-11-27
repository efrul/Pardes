package io.github.kdroidfilter.seforimapp.features.search.area

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import io.github.kdroidfilter.seforimapp.features.search.ResultLink
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.features.sefaria.Sage
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSages
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaTopics
import io.github.kdroidfilter.seforimapp.features.sefaria.Topic
import io.github.kdroidfilter.seforimapp.features.sefaria.generationLabel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.BootstrapPersonLines
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
import seforimapp.seforimapp.generated.resources.search_area_kind_sage
import seforimapp.seforimapp.generated.resources.search_area_kind_topic
import seforimapp.seforimapp.generated.resources.search_area_sage_line
import seforimapp.seforimapp.generated.resources.search_area_sage_mentions
import seforimapp.seforimapp.generated.resources.search_area_sage_page
import seforimapp.seforimapp.generated.resources.search_area_sefaria_credit
import seforimapp.seforimapp.generated.resources.search_area_topic_line
import seforimapp.seforimapp.generated.resources.sefaria_sage_students
import seforimapp.seforimapp.generated.resources.sefaria_sage_teachers
import seforimapp.seforimapp.generated.resources.sefaria_topic_open_page
import java.text.NumberFormat

/**
 * A person of Sefaria's shown in full in the area beside a search's results (the design's ד): its generation and
 * description, its teachers and students, how many lines of the library mention it and in which books most, its
 * [topic] when it has one (the same id), and the way to its page in the persons' window.
 */
@Composable
internal fun ColumnScope.SageDetails(
    sage: Sage,
    topic: Topic?,
    sages: SefariaSages,
    topics: SefariaTopics,
) {
    val windows = LocalAppGraph.current.sefariaWindows
    val grey = readingSecondary()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(BootstrapPersonLines, contentDescription = null, tint = grey, modifier = Modifier.size(20.dp))
        Text(sage.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.normal)
        val kinds =
            listOfNotNull(
                stringResource(Res.string.search_area_kind_sage),
                topic?.let { stringResource(Res.string.search_area_kind_topic) },
            )
        Text("· " + kinds.joinToString(" · "), fontSize = 12.sp, color = grey)
    }
    generationLabel(sage.generation)?.let { Text(it, fontSize = 12.sp, color = grey) }
    if (sage.description.isNotEmpty()) {
        Text(
            sage.description,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = grey,
            maxLines = DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val teachers =
        remember(sages, sage.id) {
            sages
                .teachers(sage.id)
                .distinctBy { it.id }
                .take(PEOPLE)
                .toImmutableList()
        }
    val students =
        remember(sages, sage.id) {
            sages
                .students(sage.id)
                .distinctBy { it.id }
                .take(PEOPLE)
                .toImmutableList()
        }
    People(stringResource(Res.string.sefaria_sage_teachers), teachers, windows::openSage)
    People(stringResource(Res.string.sefaria_sage_students), students, windows::openSage)
    Mentions(sage, sages)
    if (topic != null) TopicLine(topic, topics)
    DefaultButton(onClick = { windows.openSage(sage.id) }) { Text(stringResource(Res.string.search_area_sage_page)) }
    Text(stringResource(Res.string.search_area_sefaria_credit), fontSize = 11.sp, color = grey)
}

// Persons around it, each a link to its page; nothing when there are none
@Composable
private fun People(
    title: String,
    people: ImmutableList<Sage>,
    onOpen: (Int) -> Unit,
) {
    if (people.isEmpty()) return
    val accent = JewelTheme.globalColors.outlines.focused
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$title:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = readingSecondary())
        people.forEach { person -> ResultLink(person.name, accent, 13.sp) { onOpen(person.id) } }
    }
}

// How many lines of the library mention it, and the books most of them are in, read off the UI thread
@Composable
private fun ColumnScope.Mentions(
    sage: Sage,
    sages: SefariaSages,
) {
    val lines = LocalAppGraph.current.sefariaLines
    val mentions = sages.placeCount(sage.id)
    if (mentions == 0) return
    val books by produceState<ImmutableList<Pair<String, Int>>>(persistentListOf(), sages, sage.id) {
        value =
            lines
                .linesOf(withContext(Dispatchers.IO) { sages.placesOf(sage.id) })
                .values
                .groupingBy { it.bookTitle }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(BARS)
                .map { it.key to it.value }
                .toImmutableList()
    }
    val count = remember(mentions) { NumberFormat.getIntegerInstance().format(mentions) }
    Text(
        stringResource(Res.string.search_area_sage_mentions, count),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = readingSecondary(),
    )
    if (books.isNotEmpty()) Bars(books)
}

/** Books and their counts as thin bars, the longest the most: one series, so no legend, the values written beside. */
@Composable
private fun Bars(rows: ImmutableList<Pair<String, Int>>) {
    val top = rows.maxOf { it.second }.toFloat()
    val grey = readingSecondary()
    val fill = JewelTheme.globalColors.outlines.focused
    Column(Modifier.widthIn(max = BARS_WIDTH), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (book, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    book,
                    fontSize = 13.sp,
                    color = JewelTheme.globalColors.text.normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(BAR_LABEL),
                )
                // The bar grows from the reading start, its data end rounded
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(count / top)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(fill),
                    )
                }
                Text(count.toString(), fontSize = 12.sp, color = grey, maxLines = 1, modifier = Modifier.width(BAR_VALUE))
            }
        }
    }
}

/** The person of Sefaria's an author is, under Zayit's panel of the author: its generation and the way to its page. */
@Composable
internal fun SageLine(sage: Sage) {
    val windows = LocalAppGraph.current.sefariaWindows
    val grey = readingSecondary()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(BootstrapPersonLines, contentDescription = null, tint = grey, modifier = Modifier.size(16.dp))
        Text(
            stringResource(Res.string.search_area_sage_line, generationLabel(sage.generation) ?: sage.name),
            fontSize = 13.sp,
            color = grey,
        )
        ResultLink(stringResource(Res.string.search_area_sage_page), JewelTheme.globalColors.outlines.focused, 13.sp) {
            windows.openSage(sage.id)
        }
    }
}

/** The topic of Sefaria's a person is, under its details: its count of sources and the way to its page. */
@Composable
internal fun TopicLine(
    topic: Topic,
    topics: SefariaTopics,
) {
    val windows = LocalAppGraph.current.sefariaWindows
    val grey = readingSecondary()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Contextual_token, contentDescription = null, tint = grey, modifier = Modifier.size(16.dp))
        Text(stringResource(Res.string.search_area_topic_line, topics.sourceCount(topic.id)), fontSize = 13.sp, color = grey)
        ResultLink(stringResource(Res.string.sefaria_topic_open_page), JewelTheme.globalColors.outlines.focused, 13.sp) {
            windows.openTopic(topic.id)
        }
    }
}

private const val DESCRIPTION_LINES = 3
private const val PEOPLE = 6
private const val BARS = 4
private val BARS_WIDTH = 360.dp
private val BAR_LABEL = 84.dp
private val BAR_VALUE = 32.dp

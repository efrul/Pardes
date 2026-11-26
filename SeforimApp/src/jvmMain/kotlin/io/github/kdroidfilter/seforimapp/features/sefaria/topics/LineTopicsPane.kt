package io.github.kdroidfilter.seforimapp.features.sefaria.topics

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.sefaria.ChipFlow
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaChip
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaHint
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaLine
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaTopics
import io.github.kdroidfilter.seforimapp.features.sefaria.SourceRow
import io.github.kdroidfilter.seforimapp.features.sefaria.Topic
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.sefaria_loading
import seforimapp.seforimapp.generated.resources.sefaria_topic_open
import seforimapp.seforimapp.generated.resources.sefaria_topics_credit
import seforimapp.seforimapp.generated.resources.sefaria_topics_empty_line
import seforimapp.seforimapp.generated.resources.sefaria_topics_line_count
import seforimapp.seforimapp.generated.resources.sefaria_topics_pane
import seforimapp.seforimapp.generated.resources.sefaria_topics_select_line

/**
 * The reader's pane of the topics its [line] is about, from Sefaria's topics: each as a pill with its count of sources,
 * the one picked (the first at first) read below, with two of its sources and the way to its page in the topics'
 * window. Beside the text as the references pane, one setting for every tab (SefariaSettings.topicsPane).
 */
@Composable
fun LineTopicsPane(
    line: Line?,
    onHide: () -> Unit,
    onOpenLine: (SefariaLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = rememberAccentColor(JewelTheme.isDark)
    val topics by produceState<SefariaTopics?>(null) { value = withContext(Dispatchers.Default) { SefariaTopics.shipped } }
    val hover = remember { MutableInteractionSource() }
    Column(modifier.fillMaxSize().hoverable(hover)) {
        PaneHeader(label = stringResource(Res.string.sefaria_topics_pane), onHide = onHide, interactionSource = hover)
        val loaded = topics
        val heRef = line?.heRef?.takeIf { it.isNotBlank() }
        val shown = remember(loaded, heRef) { if (loaded == null || heRef == null) emptyList() else loaded.of(heRef) }
        when {
            line == null -> SefariaHint(stringResource(Res.string.sefaria_topics_select_line))
            loaded == null -> SefariaHint(stringResource(Res.string.sefaria_loading))
            shown.isEmpty() -> SefariaHint(stringResource(Res.string.sefaria_topics_empty_line))
            else -> LineTopics(heRef.orEmpty(), shown, loaded, accent, onOpenLine, Modifier.weight(1f))
        }
    }
}

/** The topics of the line [heRef] as pills, the one picked (the first at first) read below with two of its sources. */
@Composable
private fun LineTopics(
    heRef: String,
    shown: List<Topic>,
    topics: SefariaTopics,
    accent: Color,
    onOpenLine: (SefariaLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    var picked by remember(heRef) { mutableStateOf(shown.first().id) }
    val topic = shown.firstOrNull { it.id == picked } ?: shown.first()
    val sources by produceState(emptyList<SefariaLine>(), topic.id) {
        val heRefs = topics.sourcesOf(topic.id).take(PREVIEW_SOURCES + 1)
        val found = graph.sefariaLines.linesOf(heRefs)
        value = heRefs.mapNotNull(found::get).filter { it.heRef != heRef }.take(PREVIEW_SOURCES)
    }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.sefaria_topics_line_count, heRef, shown.size),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        ChipFlow {
            shown.forEach { t -> SefariaChip(t.name, topics.sourceCount(t.id), t.id == topic.id, accent) { picked = t.id } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(topic.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            HoverBox(onClick = { graph.sefariaWindows.openTopic(topic.id) }) {
                Text(
                    stringResource(Res.string.sefaria_topic_open, topics.sourceCount(topic.id)),
                    fontSize = 13.sp,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (topic.description.isNotEmpty()) {
            Text(
                topic.description,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = JewelTheme.globalColors.text.info,
                maxLines = DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        sources.forEach { source -> SourceRow(source, accent, around = topic.name) { onOpenLine(source) } }
        Text(stringResource(Res.string.sefaria_topics_credit), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
    }
}

private const val PREVIEW_SOURCES = 2
private const val DESCRIPTION_LINES = 4

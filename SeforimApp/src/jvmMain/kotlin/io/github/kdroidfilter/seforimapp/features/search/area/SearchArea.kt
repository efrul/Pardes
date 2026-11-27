package io.github.kdroidfilter.seforimapp.features.search.area

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchEntity
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.features.sefaria.Sage
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSages
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSettings
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaTopics
import io.github.kdroidfilter.seforimapp.features.sefaria.Topic
import io.github.kdroidfilter.seforimapp.features.sefaria.generationLabel
import io.github.kdroidfilter.seforimapp.features.sefaria.topics.TopicDetails
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import io.github.kdroidfilter.seforimapp.icons.Book
import io.github.kdroidfilter.seforimapp.icons.BootstrapPersonLines
import io.github.kdroidfilter.seforimapp.icons.Contextual_token
import io.github.kdroidfilter.seforimapp.icons.WritingHand
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.author_books_count
import seforimapp.seforimapp.generated.resources.search_area_also
import seforimapp.seforimapp.generated.resources.search_area_kind_author
import seforimapp.seforimapp.generated.resources.search_area_kind_book
import seforimapp.seforimapp.generated.resources.search_area_kind_sage
import seforimapp.seforimapp.generated.resources.search_area_kind_topic
import seforimapp.seforimapp.generated.resources.search_area_sefaria_credit
import seforimapp.seforimapp.generated.resources.search_area_topic_sources

/**
 * Pardes: the area at the top of the passage's pane beside a search's results (the design's ד with ב, the user's
 * choice of 2026-11-27): what the query names, a book, an author, a person or a topic of Sefaria's, one person's faces
 * together (SearchFacets), the first in full and the others on its "also" line, where a click shows one in full; then
 * the sources the first results link to (docs/COMMON_SOURCES_PLAN.md), each with its text, shown in the preview by a
 * click ([onShowSource]), its count narrowing the results to those that link to it ([onNarrowResults]). A book or an author is shown
 * in Zayit's panel ([panel]), with the rest of the area inside it. Nothing when the query names nothing and no source is
 * common to its results.
 */
@Composable
fun SearchArea(
    query: String,
    entity: SearchEntity?,
    sources: ImmutableList<CommonSource>,
    onShowSource: (CommonSource) -> Unit,
    onNarrowResults: (CommonSource) -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable (entity: SearchEntity, extra: @Composable ColumnScope.() -> Unit) -> Unit,
) {
    val appSettings = LocalAppGraph.current.appSettings
    // The topic and the person of Sefaria's, shown or hidden from the search's end bar (SearchTopicCardButton)
    val sefariaShown by appSettings.flow(SefariaSettings.searchTopicCard).collectAsState()
    val named by produceState<SefariaNamed?>(null, query, sefariaShown) {
        value = if (sefariaShown) withContext(Dispatchers.Default) { SefariaNamed.of(query.trim()) } else null
    }
    val entities = remember(query, entity, named) { SearchFacets.entities(facetsOf(query, entity, named)).toImmutableList() }
    // The entity shown in full, picked on the "also" line; the first until one is
    var picked by remember(entities) { mutableStateOf<Facet?>(null) }
    val shown = entities.firstOrNull { it.main == picked } ?: entities.firstOrNull()
    val rest: @Composable ColumnScope.() -> Unit = {
        AlsoLine(entities.filter { it != shown }.toImmutableList(), entity, named) { picked = it.main }
        SourcesPart(sources, onShowSource, onNarrowResults, first = shown == null)
    }
    Box(modifier) {
        when {
            shown == null -> if (sources.isNotEmpty()) AreaCard(rest)
            entity != null && shown.main.kind in ZAYIT_KINDS ->
                panel(entity) {
                    PersonFacets(shown, named)
                    rest()
                }
            else ->
                AreaCard {
                    SefariaEntity(shown, named)
                    rest()
                }
        }
    }
}

/** The person and the topic of Sefaria's a query names, with the data they are read from. */
@Immutable
private class SefariaNamed(
    val sages: SefariaSages,
    val topics: SefariaTopics,
    val sage: Sage?,
    val topic: Topic?,
) {
    companion object {
        fun of(query: String): SefariaNamed {
            val sages = SefariaSages.shipped
            val topics = SefariaTopics.shipped
            return SefariaNamed(sages, topics, sages.named(query), topics.named(query)?.takeIf { topics.sourceCount(it.id) > 0 })
        }
    }
}

// What the query names: Zayit's book or author, then Sefaria's person and topic
private fun facetsOf(
    query: String,
    entity: SearchEntity?,
    named: SefariaNamed?,
): List<Facet> =
    buildList {
        when (entity) {
            is SearchEntity.BookEntity -> {
                val title = entity.book.title
                add(Facet(FacetKind.BOOK, entity.book.id, listOf(title), byTitle = title.searchKey() == query.searchKey()))
            }
            is SearchEntity.AuthorEntity -> {
                val details = entity.details
                add(Facet(FacetKind.AUTHOR, details.id, listOf(details.name, AuthorNames.display(details.name)) + details.aliases))
            }
            null -> Unit
        }
        named?.sage?.let { add(Facet(FacetKind.SAGE, it.id.toLong(), listOf(it.name) + it.others)) }
        named?.topic?.let { add(Facet(FacetKind.TOPIC, it.id.toLong(), listOf(it.name) + it.others)) }
    }

// A card of the passage's pane, as Zayit's panel of a book or an author
@Composable
private fun AreaCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// A person or a topic of Sefaria's shown in full
@Composable
private fun ColumnScope.SefariaEntity(
    shown: AreaEntity,
    named: SefariaNamed?,
) {
    if (named == null) return
    val sage = named.sage
    val topic = named.topic
    when {
        shown.main.kind == FacetKind.SAGE && sage != null ->
            SageDetails(sage, topic?.takeIf { shown.facet(FacetKind.TOPIC) != null }, named.sages, named.topics)
        topic != null -> TopicDetails(topic, named.topics)
    }
}

// The faces of Sefaria's of an author shown in Zayit's panel: a line each, with the way to its page
@Composable
private fun ColumnScope.PersonFacets(
    shown: AreaEntity,
    named: SefariaNamed?,
) {
    if (named == null) return
    val sage = named.sage?.takeIf { shown.facet(FacetKind.SAGE) != null }
    val topic = named.topic?.takeIf { shown.facet(FacetKind.TOPIC) != null }
    if (sage == null && topic == null) return
    sage?.let { SageLine(it) }
    topic?.let { TopicLine(it, named.topics) }
    Text(stringResource(Res.string.search_area_sefaria_credit), fontSize = 11.sp, color = readingSecondary())
}

/** The other entities the query names, a click on one showing it in full in place of the one shown. */
@Composable
private fun AlsoLine(
    others: ImmutableList<AreaEntity>,
    entity: SearchEntity?,
    named: SefariaNamed?,
    onPick: (AreaEntity) -> Unit,
) {
    if (others.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.search_area_also), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = readingSecondary())
        others.forEach { other -> AlsoPill(other.main, entity, named) { onPick(other) } }
    }
}

// One of them as a pill: its kind, its name and what it holds
@Composable
private fun AlsoPill(
    facet: Facet,
    entity: SearchEntity?,
    named: SefariaNamed?,
    onClick: () -> Unit,
) {
    val (icon, words) = pillOf(facet, entity, named) ?: return
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = readingSecondary(), modifier = Modifier.size(14.dp))
        Text(words.joinToString(" · "), fontSize = 13.sp, color = JewelTheme.globalColors.text.normal, maxLines = 1)
    }
}

@Composable
private fun pillOf(
    facet: Facet,
    entity: SearchEntity?,
    named: SefariaNamed?,
): Pair<ImageVector, List<String>>? =
    when (facet.kind) {
        FacetKind.BOOK ->
            (entity as? SearchEntity.BookEntity)?.let { Book to listOf(stringResource(Res.string.search_area_kind_book), it.book.title) }
        FacetKind.AUTHOR ->
            (entity as? SearchEntity.AuthorEntity)?.details?.let {
                val books = stringResource(Res.string.author_books_count, it.books.size)
                WritingHand to listOf(stringResource(Res.string.search_area_kind_author), AuthorNames.display(it.name), books)
            }
        FacetKind.SAGE ->
            named?.sage?.let {
                BootstrapPersonLines to
                    listOfNotNull(stringResource(Res.string.search_area_kind_sage), it.name, generationLabel(it.generation))
            }
        FacetKind.TOPIC ->
            named?.let { n ->
                n.topic?.let {
                    val count = stringResource(Res.string.search_area_topic_sources, n.topics.sourceCount(it.id))
                    Contextual_token to listOf(stringResource(Res.string.search_area_kind_topic), it.name, count)
                }
            }
    }

// The kinds Zayit's panel shows
private val ZAYIT_KINDS = setOf(FacetKind.BOOK, FacetKind.AUTHOR)

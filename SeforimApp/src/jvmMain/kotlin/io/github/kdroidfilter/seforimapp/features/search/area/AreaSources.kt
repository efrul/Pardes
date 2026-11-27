package io.github.kdroidfilter.seforimapp.features.search.area

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.features.search.ResultLink
import io.github.kdroidfilter.seforimapp.features.search.domain.CommonSources
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import io.github.kdroidfilter.seforimapp.icons.Link
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_area_linked
import seforimapp.seforimapp.generated.resources.search_area_linked_all
import seforimapp.seforimapp.generated.resources.search_area_source_books
import seforimapp.seforimapp.generated.resources.search_area_source_books_tip
import seforimapp.seforimapp.generated.resources.search_area_sources
import seforimapp.seforimapp.generated.resources.search_area_sources_page

/**
 * The sources the first results link to (docs/COMMON_SOURCES_PLAN.md), each its place, a link that shows it in the
 * preview beside the results ([onShow]), its text, and how many books of the first results link to it, a link to those
 * results ([onNarrow]); under a divider when the area shows something above them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.SourcesPart(
    sources: ImmutableList<CommonSource>,
    onShow: (CommonSource) -> Unit,
    onNarrow: (CommonSource) -> Unit,
    first: Boolean,
) {
    if (sources.isEmpty()) return
    val grey = readingSecondary()
    val accent = JewelTheme.globalColors.outlines.focused
    val fontCode by LocalAppGraph.current.appSettings.bookFontCodeFlow
        .collectAsState()
    val font = FontCatalog.familyFor(fontCode)
    if (!first) Divider(Orientation.Horizontal, Modifier.fillMaxWidth().padding(vertical = 2.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Link, contentDescription = null, tint = grey, modifier = Modifier.size(16.dp))
        Text(stringResource(Res.string.search_area_sources), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = grey)
        Text("· " + stringResource(Res.string.search_area_sources_page, CommonSources.PAGE), fontSize = 12.sp, color = grey)
    }
    sources.forEach { source ->
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.widthIn(min = SOURCE_PLACE)) {
                ResultLink(source.label, accent, 13.sp, FontWeight.SemiBold) { onShow(source) }
            }
            Text(
                source.text,
                fontFamily = font,
                fontSize = 14.sp,
                color = grey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Tooltip(tooltip = { Text(stringResource(Res.string.search_area_source_books_tip, source.books)) }) {
                ResultLink(stringResource(Res.string.search_area_source_books, source.books), accent, 12.sp) { onNarrow(source) }
            }
        }
    }
}

/**
 * Pardes: over the results while they are narrowed to those that link to a source of the area beside them
 * ([source], by its count): which source, how many results, and the way back to all of them.
 */
@Composable
fun LinkedResultsLine(
    source: CommonSource?,
    shown: Int,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (source == null) return
    val grey = readingSecondary()
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Link, contentDescription = null, tint = grey, modifier = Modifier.size(14.dp))
        Text(
            stringResource(Res.string.search_area_linked, source.label, shown),
            fontSize = 13.sp,
            color = grey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text("·", fontSize = 13.sp, color = grey)
        ResultLink(stringResource(Res.string.search_area_linked_all), JewelTheme.globalColors.outlines.focused, 13.sp, onClick = onShowAll)
    }
}

/** [this] source as a result of the list, to show it in the preview beside it. */
fun CommonSource.asResult(): SearchResult = SearchResult(bookId, bookTitle, lineId, lineIndex, text, 0.0)

/** The [results] that link to [source], in their order; all of them when there is none. */
fun linkedResults(
    results: ImmutableList<SearchResult>,
    source: CommonSource?,
): ImmutableList<SearchResult> {
    val from = source?.from?.toHashSet() ?: return results
    return results.filter { it.lineId in from }.toImmutableList()
}

private val SOURCE_PLACE = 92.dp

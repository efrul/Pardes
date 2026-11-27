package io.github.kdroidfilter.seforimapp.features.search.area

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.features.search.domain.CommonSources
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import io.github.kdroidfilter.seforimapp.icons.Link
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_area_sources
import seforimapp.seforimapp.generated.resources.search_area_sources_page

/**
 * The sources the first results link to (docs/COMMON_SOURCES_PLAN.md), each its place and its text, a click showing it
 * in the preview beside the results ([onShow]), as a click on a result does; under a divider when the area shows
 * something above them.
 */
@Composable
internal fun ColumnScope.SourcesPart(
    sources: ImmutableList<CommonSource>,
    onShow: (CommonSource) -> Unit,
    first: Boolean,
) {
    if (sources.isEmpty()) return
    val grey = readingSecondary()
    val fontCode by LocalAppGraph.current.appSettings.bookFontCodeFlow
        .collectAsState()
    val font = FontCatalog.familyFor(fontCode)
    if (!first) Divider(Orientation.Horizontal, Modifier.fillMaxWidth().padding(vertical = 2.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Link, contentDescription = null, tint = grey, modifier = Modifier.size(16.dp))
        Text(stringResource(Res.string.search_area_sources), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = grey)
        Text("· " + stringResource(Res.string.search_area_sources_page, CommonSources.PAGE), fontSize = 12.sp, color = grey)
    }
    sources.forEach { source -> SourceRow(source, font, grey) { onShow(source) } }
}

// One source: its place, underlined while the pointer is on it, over its text; a click anywhere on it shows it
@Composable
private fun SourceRow(
    source: CommonSource,
    font: FontFamily,
    grey: Color,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            source.label,
            color = JewelTheme.globalColors.outlines.focused,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (hovered) TextDecoration.Underline else null,
        )
        Text(source.text, fontFamily = font, fontSize = 14.sp, color = grey, maxLines = SOURCE_LINES, overflow = TextOverflow.Ellipsis)
    }
}

/** [this] source as a result of the list, to show it in the preview beside it. */
fun CommonSource.asResult(): SearchResult = SearchResult(bookId, bookTitle, lineId, lineIndex, text, 0.0)

// A source's text, at most
private const val SOURCE_LINES = 2

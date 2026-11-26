package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_common_source
import seforimapp.seforimapp.generated.resources.search_common_source_books
import seforimapp.seforimapp.generated.resources.search_common_source_books_tip
import seforimapp.seforimapp.generated.resources.search_common_sources

/**
 * Pardes: the line of common sources under the status line (docs/COMMON_SOURCES_PLAN.md): each source a link that
 * opens it, with how many books of the first results link to it. It slides in after the first page, so that the list
 * moves down smoothly, and out with the sources it had.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommonSourcesLine(
    sources: ImmutableList<CommonSource>,
    onOpen: (CommonSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The last sources shown, still drawn while the line slides out
    var last by remember { mutableStateOf(sources) }
    LaunchedEffect(sources) { if (sources.isNotEmpty()) last = sources }
    val shown = sources.ifEmpty { last }
    AnimatedVisibility(visible = sources.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        val grey = readingSecondary()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text(
                stringResource(if (shown.size == 1) Res.string.search_common_source else Res.string.search_common_sources),
                color = grey,
                fontSize = 13.sp,
            )
            shown.forEachIndexed { i, source ->
                Tooltip(tooltip = { Text(stringResource(Res.string.search_common_source_books_tip, source.books)) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ResultLink(source.label, JewelTheme.globalColors.outlines.focused, 13.sp) { onOpen(source) }
                        Text(stringResource(Res.string.search_common_source_books, source.books), color = grey, fontSize = 13.sp)
                        if (i < shown.lastIndex) Text("·", color = grey, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

package io.github.kdroidfilter.seforimapp.features.search.area

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.SimpleTabViewModelOwner
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.rememberSearchShellActions
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabSearchViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.search.EntityPanel
import io.github.kdroidfilter.seforimapp.features.search.SearchPanelsSettings
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchEntity
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.search.CommonSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_area_pane

// Pardes: the area beside a search's results in a pane of the dock, which the reader moves to any side, sizes and pulls
// out into a window of its own as any pane of the reader (the user's word on build 87, 2026-11-27: "שיוכלו לשלוט בגובה
// ומיקום חלונית הנושאים", in Zayit's design language): a column beside the passage at first (ReaderPane.SearchArea), one
// row of two columns once docked under the results. It is open while its search names something or links to sources,
// and its setting is on (SearchPanelsSettings, the end bar's button and the pane's own hide).

/**
 * The pane of the area beside the results of the search tab [destination] ([owner] its tab): its header, which is also
 * its grip, and the area (SearchArea), side by side when the pane is far wider than tall.
 */
@Composable
fun SearchAreaPane(
    owner: SimpleTabViewModelOwner,
    destination: TabsDestination.Search,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = tabSearchViewModel(owner, destination)
    val actions = rememberSearchShellActions(viewModel)
    val content = rememberAreaContent(viewModel)
    val hover = remember { MutableInteractionSource() }
    Column(modifier.fillMaxSize().hoverable(hover)) {
        PaneHeader(label = stringResource(Res.string.search_area_pane), onHide = onHide, interactionSource = hover)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            SearchArea(
                query = content.query,
                entity = content.entity,
                sources = content.sources,
                onShowSource = { source ->
                    val hit = source.asResult()
                    actions.onRequestBreadcrumb(hit)
                    actions.onPreviewLine(hit)
                },
                modifier = Modifier.fillMaxSize(),
                wide = maxWidth >= WIDE_MIN_WIDTH && maxWidth >= maxHeight * WIDE_RATIO,
            ) { entity, extra ->
                EntityPanel(
                    entity = entity,
                    onOpenBookAt = actions.onOpenBookAt,
                    onOpenAuthor = actions.onOpenAuthor,
                    modifier = Modifier.fillMaxWidth(),
                    extra = extra,
                )
            }
        }
    }
}

/**
 * Whether the search tab [destination] ([owner] its tab) has its area's pane open: asked for, and its search naming a
 * book, an author, a person or a topic, or linking to sources. A search that names nothing closes it once it has
 * settled, so that a search on its way to its results, or a source read late, does not close and open it again.
 */
@Composable
fun rememberSearchAreaShown(
    owner: SimpleTabViewModelOwner,
    destination: TabsDestination.Search,
): Boolean {
    val graph = LocalAppGraph.current
    val asked by graph.appSettings.flow(SearchPanelsSettings.area).collectAsState()
    if (!asked) return false
    val content = rememberAreaContent(tabSearchViewModel(owner, destination))
    val query = content.query.trim()
    val named by produceState(false, query) {
        value = query.isNotEmpty() && withContext(Dispatchers.Default) { SefariaNamed.of(query).any }
    }
    val has = content.entity != null || named || content.sources.isNotEmpty()
    val areas = graph.searchAreas
    LaunchedEffect(areas, destination.tabId, has, query, content.loading) {
        if (has) {
            areas.show(destination.tabId, true)
        } else if (!content.loading) {
            delay(SETTLE_MS)
            areas.show(destination.tabId, false)
        }
    }
    return areas.isShown(destination.tabId)
}

/** What the area of a search tab shows: its query, the book or author it names, the sources its first results link to. */
@Immutable
private data class AreaContent(
    val query: String,
    val entity: SearchEntity?,
    val sources: ImmutableList<CommonSource>,
    val loading: Boolean,
)

@Composable
private fun rememberAreaContent(viewModel: SearchResultViewModel): AreaContent {
    val state by viewModel.uiState.collectAsState()
    val entity by viewModel.entityFlow.collectAsState()
    val sources by viewModel.commonSourcesFlow.collectAsState()
    val preview by viewModel.previewFlow.collectAsState()
    val bookDetails by LocalAppGraph.current.appSettings.bookDetailsPaneFlow
        .collectAsState()
    // The query's book is already in the details pane when a result of it is shown, as Zayit had it
    val book = entity as? SearchEntity.BookEntity
    val duplicate = book != null && bookDetails && book.book.id == preview?.hit?.bookId
    return AreaContent(state.executedQuery, entity.takeUnless { duplicate }, sources, state.isLoading)
}

/**
 * Whether each search tab, by its id, has its area's pane open, kept while the app runs: a tab selected again opens it
 * at once, as it was, without waiting to read again what its search names.
 */
@Inject
@SingleIn(AppScope::class)
class SearchAreas {
    private val shown = mutableStateMapOf<String, Boolean>()

    internal fun isShown(tabId: String): Boolean = shown[tabId] == true

    internal fun show(
        tabId: String,
        open: Boolean,
    ) {
        shown[tabId] = open
    }
}

// Under the results the pane is a row: what the query names beside the sources, once it is this wide and far wider
// than tall
private val WIDE_MIN_WIDTH = 720.dp
private const val WIDE_RATIO = 1.8f

// How long a search that names nothing waits before its pane closes: a search on its way, or a source read late
private const val SETTLE_MS = 1_500L

package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.PdfBookTab
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_book_remove
import seforimapp.seforimapp.generated.resources.pdf_book_remove_hint
import seforimapp.seforimapp.generated.resources.pdf_book_removed
import java.util.UUID

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): the tab of a PDF book of the user's.

/** Brings the window's tab of the PDF book [pdfId] forward, or opens one, as the notes page's. */
fun openPdfBookTab(
    tabsViewModel: TabsViewModel,
    pdfId: Long,
) {
    val existing =
        tabsViewModel.state.value.tabs
            .indexOfFirst { (it.destination as? PdfBookTab)?.pdfId == pdfId }
    if (existing >= 0) {
        tabsViewModel.onEvent(TabsEvents.OnSelect(existing))
    } else {
        tabsViewModel.openTab(PdfBookTab(tabId = UUID.randomUUID().toString(), pdfId = pdfId))
    }
}

/**
 * The tab of the PDF book [pdfId]: its pages ([PdfBookView]) where it was last read, its place kept as it is read, and a
 * button that takes it out of the list. Only the tab in view holds its file: every tab stays composed (TabsContent),
 * and a PDF's pages take memory as its file does; back in view, it opens again where it was left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfBookTabContent(
    tabId: String,
    pdfId: Long,
) {
    val appGraph = LocalAppGraph.current
    val store = appGraph.pdfBookStore
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val scope = rememberCoroutineScope()
    var book by remember(pdfId) { mutableStateOf<PdfBook?>(null) }
    var read by remember(pdfId) { mutableStateOf(false) }
    LaunchedEffect(pdfId) {
        book = store.book(pdfId)
        read = true
    }
    val title = book?.title
    LaunchedEffect(tabId, title) {
        if (title != null) appGraph.tabTitleUpdateManager.updateTabTitle(tabId, title, TabType.BOOK)
    }
    val current = book
    when {
        !read || !LocalTabSelected.current -> Box(Modifier.fillMaxSize())
        current == null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.pdf_book_removed), Modifier.padding(16.dp))
            }
        else ->
            PdfBookView(
                file = current.file,
                place = current.place,
                onPlace = { place ->
                    book = current.copy(place = place)
                    store.keepPlace(pdfId, place)
                },
                actions = {
                    val remove = stringResource(Res.string.pdf_book_remove)
                    Tooltip({ Text(stringResource(Res.string.pdf_book_remove_hint)) }) {
                        IconActionButton(
                            key = AllIconsKeys.General.Delete,
                            onClick = {
                                scope.launch {
                                    store.remove(pdfId)
                                    tabsViewModel.closeTab(tabId)
                                }
                            },
                            contentDescription = remove,
                        )
                    }
                },
            )
    }
}

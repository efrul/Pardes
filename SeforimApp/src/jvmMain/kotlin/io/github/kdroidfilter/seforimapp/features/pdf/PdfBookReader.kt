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

// Pardes (requests 28, 44 and 45 of docs/DESKTOP_STAGE2.md): a PDF book of the user's read, in the reading screen as
// the library's books are, in the text's place, "בדומה להצגת גמרא סרוקה" (and in a tab of its own, as build 45 opened
// it).

/**
 * The PDF book [pdfId] in the tab [tabId]: its pages ([PdfBookView]) where it was last read, its place kept as it is
 * read, the zoom's buttons in its bar with [zoomButtons], and, for a book added alone by build 45 (in none of the user's
 * folders), a button that takes it out of the list and closes the tab. Only the tab in view holds its file: every tab
 * stays composed (TabsContent), and a PDF's pages take memory as its file does; back in view, it opens again where it
 * was left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PdfBookReader(
    tabId: String,
    pdfId: Long,
    modifier: Modifier = Modifier,
    zoomButtons: Boolean = false,
) {
    val store = LocalAppGraph.current.pdfBookStore
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val scope = rememberCoroutineScope()
    var book by remember(pdfId) { mutableStateOf<PdfBook?>(null) }
    var read by remember(pdfId) { mutableStateOf(false) }
    // A book of a folder goes with its folder (Settings › ניהול נתונים), not by itself
    var alone by remember(pdfId) { mutableStateOf(false) }
    LaunchedEffect(pdfId) {
        book = store.book(pdfId)
        alone = book?.let { store.isAlone(it) } == true
        read = true
    }
    val current = book
    when {
        !read || !LocalTabSelected.current -> Box(modifier.fillMaxSize())
        current == null ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                tabId = tabId,
                modifier = modifier,
                zoomButtons = zoomButtons,
                actions = {
                    if (!alone) return@PdfBookView
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

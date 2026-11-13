package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocView
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_book_toc_page

// Pardes (request 49 of docs/DESKTOP_STAGE2.md): a PDF book's contents in the reader's own contents pane, "סרגל תוכן
// עניינים רגיל כמו שיש לכל הספרים", in place of the field that went to a page.

/**
 * The contents of the PDF book [bookId] shown in the tab [tabId]: its pages, as the reader tells them
 * ([PdfBookStore.shown]), drawn as the library's books' contents are ([BookTocView]); the page at the reader's top is
 * the one selected, and a page clicked is gone to. Its PDF's own bookmarks are not read: the binding gives none.
 */
@Composable
fun PdfBookToc(
    tabId: String,
    bookId: Long,
    modifier: Modifier = Modifier,
) {
    val store = LocalAppGraph.current.pdfBookStore
    val shown by store.shown.collectAsState()
    val pages = shown[tabId]
    // "עמוד %1$d", filled for each page here rather than looked up a thousand times
    val label = stringResource(Res.string.pdf_book_toc_page)
    val entries =
        remember(bookId, pages?.count, label) {
            (1..(pages?.count ?: 0))
                .map { page -> TocEntry(id = page.toLong(), bookId = bookId, text = label.replace("%1\$d", "$page"), level = 0) }
                .toImmutableList()
        }
    if (pages == null) {
        Box(modifier)
        return
    }
    BookTocView(
        tocEntries = entries,
        expandedEntries = emptySet(),
        tocChildren = emptyMap(),
        scrollIndex = 0,
        scrollOffset = 0,
        onEntryClick = { entry -> store.goToPage(tabId, entry.id.toInt() - 1) },
        onEntryExpand = {},
        onScroll = { _, _ -> },
        modifier = modifier,
        selectedTocEntryId = pages.page + 1L,
    )
}

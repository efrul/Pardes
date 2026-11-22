package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.TocFilterResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.PaneSearchRunner
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.buildTocFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_book_toc_page

// Pardes (request 53 of docs/DESKTOP_STAGE2.md): a PDF book's contents searched as a book's are, "למה אין חיפוש בתוכן
// עניינים?": its bookmarks and its pages, both shown (request 48 of docs/DESKTOP_STAGE3_PLAN.md).

/**
 * The search of the contents pane in the PDF book open in a reader, run as Zayit's `TocUseCase` runs a book's
 * ([buildTocFilter] over the whole contents, [PaneSearchRunner] for the field): the book's bookmarks as the pane draws
 * them ([pdfOutlineToc]), and its pages after them ("12" finds "עמוד 12"). The field itself is Zayit's, opened and closed by the
 * reader's events as for any book; a match chosen closes it and goes to its page.
 */
internal class PdfTocSearch(
    private val pdfBooks: PdfBookStore,
    private val stateManager: BookContentStateManager,
) {
    private val runner =
        PaneSearchRunner<TocFilterResult> { transform ->
            stateManager.updateToc(save = false) { copy(search = search?.let(transform)) }
        }

    // The contents of the book searched last, kept while it is searched again
    private var contents: Pair<Long, PdfOutlineToc>? = null

    /** Searches [query] in the contents of the book open when it is a PDF book, and says whether it is one. */
    suspend fun search(query: String): Boolean {
        val bookId = openPdfBook() ?: return false
        // A query flushed by a closing field, or arriving after Escape
        if (stateManager.state.value.toc.search == null) return true
        runner.run(query) {
            val toc = contentsOf(bookId) ?: return@run TocFilterResult()
            val result = withContext(Dispatchers.Default) { buildTocFilter(toc.entries, query) }
            // Dropped if another book was opened meanwhile
            result.takeIf { openPdfBook() == bookId }
        }
        return true
    }

    /** Closes the search and goes to the page of the match [id], when the book open is a PDF book; says whether it is. */
    suspend fun open(id: Long): Boolean {
        val bookId = openPdfBook() ?: return false
        val state = stateManager.state.value
        // Closed already: a double click or a held Enter must not go twice
        if (state.toc.search == null) return true
        stateManager.updateToc(save = false) { copy(search = null) }
        contentsOf(bookId)?.pageOf(id)?.let { pdfBooks.goToPage(state.tabId, it) }
        return true
    }

    private fun openPdfBook(): Long? =
        stateManager.state.value.navigation.selectedBook
            ?.id
            ?.takeIf { pdfIdOf(it) != null }

    // The bookmarks as the pane draws them, then the pages the tab's reader shows; kept once the pages are known
    private suspend fun contentsOf(bookId: Long): PdfOutlineToc? {
        contents?.takeIf { it.first == bookId }?.let { return it.second }
        val book = pdfIdOf(bookId)?.let { pdfBooks.book(it) } ?: return null
        val label = getString(Res.string.pdf_book_toc_page)
        val pages = pdfBooks.shown.value[stateManager.state.value.tabId]?.count ?: 0
        val outline = PdfOutlines.read(book.file) + List(pages) { page -> PdfOutlineEntry(pageLabel(label, page), page) }
        return pdfOutlineToc(bookId, outline) { page -> pageLabel(label, page) }
            .also { if (pages > 0) contents = bookId to it }
    }
}

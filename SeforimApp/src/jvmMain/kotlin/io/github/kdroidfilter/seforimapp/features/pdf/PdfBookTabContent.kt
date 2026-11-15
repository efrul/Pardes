package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): the tab of a PDF book of the user's, as build 45 opened one; its
// books open in the reading screen since (request 45), and this tab is kept for the sessions that still hold one.

/**
 * The tab of the PDF book [pdfId]: its title the book's, and the book read ([PdfBookReader]), its bar going to a page
 * and zooming it, as the tab has no contents pane nor end bar.
 */
@Composable
fun PdfBookTabContent(
    tabId: String,
    pdfId: Long,
) {
    val appGraph = LocalAppGraph.current
    val title by produceState<String?>(null, pdfId) { value = appGraph.pdfBookStore.book(pdfId)?.title }
    LaunchedEffect(tabId, title) {
        title?.let { appGraph.tabTitleUpdateManager.updateTabTitle(tabId, it, TabType.BOOK) }
    }
    PdfBookReader(tabId, pdfId, inTab = true)
}

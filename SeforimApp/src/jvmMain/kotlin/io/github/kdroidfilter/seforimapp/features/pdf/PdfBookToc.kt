package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocView
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_book_toc_page

// Pardes (requests 49 and 52 of docs/DESKTOP_STAGE2.md): a PDF book's contents in the reader's own contents pane, "סרגל
// תוכן עניינים רגיל כמו שיש לכל הספרים", in place of the field that went to a page: the PDF's own bookmarks, and its
// pages under them, as a book's two divisions (request 48 of docs/DESKTOP_STAGE3_PLAN.md), or its pages alone.

/**
 * The contents of the PDF book [bookId] shown in the tab [tabId], drawn as the library's books' contents are
 * ([BookTocView]): its own bookmarks ([PdfOutlines]), read once its reader opened it, above its pages as a book's two
 * divisions are, or its pages alone when it has none, as the reader tells them ([PdfBookStore.shown]). The entry of the
 * page at the reader's top is the one selected in each, and an entry clicked goes to its page; one with entries under
 * it opens them, as in a book's contents.
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
    val opened = pages != null
    // The bookmarks read, with the book they are of: another book opened in the tab shows none of the last one's
    val read by produceState<Pair<Long, List<PdfOutlineEntry>>?>(null, bookId, opened) {
        if (opened) value = bookId to pdfIdOf(bookId)?.let { store.book(it) }?.let { PdfOutlines.read(it.file) }.orEmpty()
    }
    val bookmarks = read?.takeIf { it.first == bookId }?.second
    if (pages == null || bookmarks == null) {
        Box(modifier)
        return
    }
    val goTo = { page: Int -> store.goToPage(tabId, page) }
    if (bookmarks.isNotEmpty()) {
        val toc = remember(bookId, bookmarks, label) { pdfOutlineToc(bookId, bookmarks) { page -> pageLabel(label, page) } }
        Column(modifier) {
            PdfOutlineView(toc, pages.page, goTo, Modifier.weight(1f))
            Box(Modifier.padding(vertical = 8.dp)) { HorizontalDivider() }
            PdfPagesView(bookId, pages, label, goTo, Modifier.weight(1f))
        }
        return
    }
    PdfPagesView(bookId, pages, label, goTo, modifier)
}

// The pages, "עמוד N" each ([label]), the one at the reader's top selected
@Composable
private fun PdfPagesView(
    bookId: Long,
    pages: PdfPagesShown,
    label: String,
    goTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries =
        remember(bookId, pages.count, label) {
            (1..pages.count)
                .map { page -> TocEntry(id = page.toLong(), bookId = bookId, text = pageLabel(label, page - 1), level = 0) }
                .toImmutableList()
        }
    BookTocView(
        tocEntries = entries,
        expandedEntries = emptySet(),
        tocChildren = emptyMap(),
        scrollIndex = 0,
        scrollOffset = 0,
        onEntryClick = { entry -> goTo(entry.id.toInt() - 1) },
        onEntryExpand = {},
        onScroll = { _, _ -> },
        modifier = modifier,
        selectedTocEntryId = pages.page + 1L,
    )
}

// The bookmarks, opened as a book's contents open: the first if it holds others, and those above the entry read
@Composable
private fun PdfOutlineView(
    toc: PdfOutlineToc,
    page: Int,
    goTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = remember(toc, page) { toc.entryAt(page) }
    var expanded by remember(toc) {
        mutableStateOf(
            toc.roots
                .firstOrNull()
                ?.takeIf { it.hasChildren }
                ?.let { setOf(it.id) }
                .orEmpty(),
        )
    }
    LaunchedEffect(toc, selected) { selected?.let { expanded = expanded + toc.pathTo(it) } }
    BookTocView(
        tocEntries = toc.roots,
        expandedEntries = expanded,
        tocChildren = toc.children,
        scrollIndex = 0,
        scrollOffset = 0,
        onEntryClick = { entry -> toc.pageOf(entry.id)?.let(goTo) },
        onEntryExpand = { entry ->
            expanded = if (entry.id in expanded) expanded - entry.id - toc.below(entry.id) else expanded + entry.id
        },
        onScroll = { _, _ -> },
        modifier = modifier,
        selectedTocEntryId = selected,
    )
}

/** "עמוד N" ([label], "עמוד %1$d") for the page [page] (from 0), or a dash for none. */
internal fun pageLabel(
    label: String,
    page: Int?,
): String = page?.let { label.replace("%1\$d", "${it + 1}") } ?: "—"

/**
 * A PDF book's bookmarks as the contents pane draws them ([BookTocView]): the [roots], each entry's [children] by its
 * id, every entry in the book's order ([entries], as a book's whole contents are searched), and the page each one goes
 * to; the ids count the entries in the book's order, from 1.
 */
@Immutable
internal class PdfOutlineToc(
    val roots: ImmutableList<TocEntry>,
    val children: Map<Long, List<TocEntry>>,
    val entries: List<TocEntry>,
    private val pages: Map<Long, Int>,
    private val parents: Map<Long, Long>,
) {
    /** The page (from 0) the entry [id] goes to, or null when it goes to none. */
    fun pageOf(id: Long): Int? = pages[id]

    /**
     * The entry read at [page]: the one that starts nearest before it or on it, the last of those that start together.
     */
    fun entryAt(page: Int): Long? =
        pages.entries
            .filter { it.value <= page }
            .maxWithOrNull(compareBy({ it.value }, { it.key }))
            ?.key

    /** The entries above the entry [id], which open to show it. */
    fun pathTo(id: Long): Set<Long> = generateSequence(parents[id]) { parents[it] }.toSet()

    /** The entries under the entry [id], at every depth, which close with it. */
    fun below(id: Long): Set<Long> = children[id].orEmpty().flatMapTo(HashSet()) { below(it.id) + it.id }
}

/**
 * The bookmarks [outline] of the book [bookId] as its contents, an untitled entry named by [untitled] from its page.
 */
internal fun pdfOutlineToc(
    bookId: Long,
    outline: List<PdfOutlineEntry>,
    untitled: (Int?) -> String,
): PdfOutlineToc {
    val children = HashMap<Long, List<TocEntry>>()
    val pages = HashMap<Long, Int>()
    val parents = HashMap<Long, Long>()
    var last = 0L

    fun add(
        entries: List<PdfOutlineEntry>,
        parent: Long?,
        level: Int,
    ): List<TocEntry> =
        entries.mapIndexed { index, entry ->
            val id = ++last
            entry.page?.let { pages[id] = it }
            parent?.let { parents[id] = it }
            val under = add(entry.children, id, level + 1)
            if (under.isNotEmpty()) children[id] = under
            TocEntry(
                id = id,
                bookId = bookId,
                parentId = parent,
                text = entry.title.ifBlank { untitled(entry.page) },
                level = level,
                isLastChild = index == entries.lastIndex,
                hasChildren = under.isNotEmpty(),
            )
        }
    val roots = add(outline, parent = null, level = 0)
    val entries = (roots + children.values.flatten()).sortedBy { it.id }
    return PdfOutlineToc(roots.toImmutableList(), children, entries, pages, parents)
}

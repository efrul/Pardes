package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.CommentatorsDefault
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.CommentatorsList
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_daf_chooser

// Pardes (request 92 of docs/DESKTOP_STAGE3_PLAN.md: "המפרשים שיוצגו לא יקבעו לפי המפרשים שנבחרו במפרשים אלא יהיה
// בדומה למה שיש במפרשים שאפשר לפתוח חלונית צד לבחירה - יהיה גם בתצוגת ספר. בהתאם לסגנון ושפת העיצוב של זית"): a book's
// pages have commentators of their own (BookDafCommentators, saved for the book), checked in the commentaries pane's
// own list, which stands beside the pages as it stands beside the pane's texts (CommentatorsListAndTexts).

/** [commentators] are the book [bookId]'s on its pages from now on. */
internal fun AppSettings.setBookDafCommentators(
    bookId: Long,
    commentators: BookDafCommentators,
) {
    this[BookDafSettings.commentators] = this[BookDafSettings.commentators] + (bookId to commentators)
}

/**
 * [book]'s commentators on its pages, as the reader chose them there. The first time the book's pages are shown they
 * take those the commentaries pane shows ([commentators]) on the lines the tab is at ([lineId], among [units]), and are
 * the pages' own from then on, whatever the pane shows. Null until they are known.
 */
@Composable
internal fun rememberBookDafCommentators(
    book: Book,
    commentators: DafCommentators,
    units: List<DafUnit>,
    lineId: Long?,
): BookDafCommentators? {
    val graph = LocalAppGraph.current
    val all by graph.appSettings.flow(BookDafSettings.commentators).collectAsState()
    val own = all[book.id]
    val isChosen = own?.shown != null
    LaunchedEffect(book.id, isChosen, units) {
        if (isChosen || units.isEmpty()) return@LaunchedEffect
        val index =
            lineId
                ?.let { id ->
                    runSuspendCatching { graph.repository.getLine(id) }.getOrNull()
                }?.takeIf { it.bookId == book.id }
                ?.lineIndex
        val unit = units.firstOrNull { index != null && index >= it.first && index < it.end } ?: units.first()
        val first =
            runSuspendCatching { paneCommentators(graph.repository, book.id, unit, commentators.selection, commentators.connections) }
                .onFailure { warnln { "[BookDaf] the commentaries pane's commentators could not be read: $it" } }
                .getOrDefault(emptyList())
        graph.appSettings.setBookDafCommentators(book.id, (own ?: BookDafCommentators()).copy(shown = first))
    }
    return own?.takeIf { isChosen }
}

/**
 * The list of [book]'s commentators beside its pages: the commentaries pane's own list, of the commentators the units
 * laid out have ([laid]), those on the pages ([own]) checked. A check sets the pages again with the commentator in a
 * band after the others, or without it.
 */
@Suppress("ComposeUnstableCollections") // the units laid out, as the pages take them
@Composable
internal fun BookDafChooser(
    book: Book,
    own: BookDafCommentators,
    laid: Map<Int, LaidUnit>,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppGraph.current.appSettings
    val groups = groupsOf(laid.entries.sortedBy { it.key }.map { it.value.groups })
    val items = groups.flatMap { it.commentators }
    val shown = own.shown.orEmpty()
    // The pane's pin of a default is not the pages': theirs is saved as it is checked
    val default = remember { CommentatorsDefault() }
    Column(modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.book_daf_chooser),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f)) {
            CommentatorsList(
                groups = groups,
                selectedCommentators = items.filter { it.bookId in shown }.mapTo(HashSet()) { it.name },
                initialScrollIndex = 0,
                initialScrollOffset = 0,
                onScroll = { _, _ -> },
                detachedCommentators = emptyList(),
                default = default,
                onReattach = {},
                onSelectionChange = { names, checked ->
                    val ids = items.filter { it.name in names }.map { it.bookId }
                    settings.setBookDafCommentators(book.id, own.checked(ids, checked))
                },
                onAllChange = {},
                onGroupChange = { _, _ -> },
            )
        }
    }
}

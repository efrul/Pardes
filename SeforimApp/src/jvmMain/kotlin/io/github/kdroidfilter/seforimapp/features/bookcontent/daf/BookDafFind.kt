package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.features.bookcontent.find.foundLines
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookFindMatches
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.findInBook
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine

// Pardes (request 97 of docs/DESKTOP_STAGE3_PLAN.md: "כמו בגמרא - חיפוש בצורת הדף יחפש גם בכל המפרשים המוצגים"): the
// find on a book's own pages searches the commentators shown on them too, as the daf's searches Rashi and Tosafot
// (DafFindComments). A book's pages are laid out as they come into view, so its comments are not known beforehand as
// a tractate's are: each commentator's book is searched as the text is, and of its lines found those that are comments
// on this book are kept, by the library's links.

// The comments whose lines of the text are asked for at a time
private const val BASES_CHUNK = 500

// The find's shortest query, as the text's
private const val MIN_QUERY = 2

// A commentator's lines found by meaning, as many as the text's
private const val MEANING_LIMIT = 60

/**
 * While a book's pages are shown, the find on them searches their commentators ([own]: the one beside the text first,
 * then the bands' in their order), told to the reader through the daf's [control] as a tractate's comments are.
 */
@Composable
internal fun FindInCommentators(
    control: DafControl,
    bookId: Long,
    own: BookDafCommentators,
) {
    val books =
        remember(own) {
            val shown = own.shown.orEmpty()
            (listOfNotNull(own.beside?.takeIf { it in shown }) + shown).toSet()
        }
    DisposableEffect(control, bookId, books) {
        control.printed = DafPrinted(emptyMap(), emptyMap(), books, pagesOf = bookId)
        onDispose { control.printed = null }
    }
}

/**
 * The index in book [bookId] of its line [id], or of the line of it that the comment [id] is on; null for a line that
 * is neither: where the pages go for a match of the find, a comment's unit being laid out only then.
 */
internal suspend fun textIndexOf(
    repository: SeforimRepository,
    bookId: Long,
    id: Long,
): Int? {
    val line = repository.getLine(id) ?: return null
    return if (line.bookId == bookId) line.lineIndex else commentBases(repository, bookId, listOf(id))[id]
}

/**
 * The find's matches of [query] in a book's text ([text]) with those in the commentators shown on its pages
 * ([printed]: the book, and their books in their bands' order): by its words, each commentator's book searched as the
 * text is, or by meaning ([smart]: the comments found so are marked whole, [DafFound.comments]).
 */
internal suspend fun bookDafFound(
    text: BookFindMatches,
    query: String,
    smart: Boolean,
    printed: DafPrinted,
    repository: SeforimRepository,
    searchEngine: SearchEngine,
): DafFound {
    val bookId = printed.pagesOf
    if (bookId == null || normalizeQueryForHebrew(query).length < MIN_QUERY) return DafFound(text, text)
    val bases: suspend (List<Long>) -> Map<Long, Int> = { commentBases(repository, bookId, it) }
    if (smart) {
        val found =
            withBookComments(text, printed.books.toList(), bases) { book ->
                searchEngine.semanticFind(query, book, MEANING_LIMIT).map {
                    it to
                        1
                }
            }
        val own = text.lineIds.toSet()
        return DafFound(text, found, found.lineIds.filterNotTo(HashSet()) { it in own })
    }
    return DafFound(
        text,
        withBookComments(text, printed.books.toList(), bases) { book ->
            findInBook(searchEngine, repository::getLinesByIds, { start, end -> repository.getLines(book, start, end) }, book, query)
                .foundLines()
                .map { it.lineId to it.count }
        },
    )
}

/**
 * [text], the find's matches in a book's text, with its matches in the commentators [books] (in their bands' order),
 * as the book's pages are read: line by line of the text, each followed by the comments on it, a commentator after
 * another. [find] gives a commentator's lines found, in its book's order, each with its matches' count; [bases] the
 * line of the text each of them is a comment on (its index in the book), none for a line that is no comment on this
 * book. Their line indices are their places in that order, as the daf's ([withComments]).
 */
internal suspend fun withBookComments(
    text: BookFindMatches,
    books: List<Long>,
    bases: suspend (commentIds: List<Long>) -> Map<Long, Int>,
    find: suspend (bookId: Long) -> List<Pair<Long, Int>>,
): BookFindMatches {
    // A line found: its id, its matches, the line of the text it is read with, its commentator's turn (the text's first)
    class Found(
        val lineId: Long,
        val count: Int,
        val base: Int,
        val turn: Int,
    )
    val found = text.foundLines().mapTo(ArrayList()) { Found(it.lineId, it.count, it.lineIndex, -1) }
    val own = found.size
    books.forEachIndexed { turn, book ->
        val lines = find(book)
        val on = HashMap<Long, Int>()
        for (chunk in lines.map { it.first }.chunked(BASES_CHUNK)) on += bases(chunk)
        for ((id, count) in lines) on[id]?.let { found += Found(id, count, it, turn) }
    }
    if (found.size == own) return text
    // A stable sort: a commentator's comments on one line stay in its book's order
    found.sortWith(compareBy({ it.base }, { it.turn }))
    val firstOccurrence = IntArray(found.size + 1)
    found.forEachIndexed { i, line -> firstOccurrence[i + 1] = firstOccurrence[i] + line.count }
    return BookFindMatches(IntArray(found.size) { it }, LongArray(found.size) { found[it].lineId }, firstOccurrence)
}

/**
 * The line of book [bookId] each of [commentIds] is a comment on, by its index in the book (the first of them, on
 * several): the library's links to these lines from the book's. A line that is no comment on the book has none.
 */
internal suspend fun commentBases(
    repository: SeforimRepository,
    bookId: Long,
    commentIds: List<Long>,
): Map<Long, Int> =
    repository
        .getCommentarySummariesForLines(commentIds, setOf(bookId), includeSources = true)
        // The links read from their target's side: the book's line is their target here, with its index
        .filter { it.link.connectionType == ConnectionType.SOURCE }
        .groupBy({ it.link.sourceLineId }) { it.link.targetLineIndex }
        .mapValues { it.value.min() }

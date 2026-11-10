package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LibraryLinks
import io.github.kdroidfilter.seforimlibrary.core.models.Line

/**
 * The links Pardes adds to the texts it shows, from the library's own links: the verses the Talmud quotes
 * ([VerseLinks]) and the notes a book's marks lead to ([NoteLinks]). Handed to the reader by
 * addons/quotelinks/disabled, where Zayit's official builds plug their private package.
 */
object TextLinks : QuoteLinksFeature {
    /** The library's links, given once the app's graph is made (main.kt); no book is covered without them. */
    @Volatile
    var library: LibraryLinks? = null
        set(value) {
            field = value
            links = value?.let { listOf(VerseLinks(it), NoteLinks(it)) }.orEmpty()
        }

    @Volatile
    private var links: List<QuoteLinksFeature> = emptyList()

    override fun covers(bookId: Long): Boolean = links.any { it.covers(bookId) }

    override suspend fun decorate(
        bookId: Long,
        lines: List<Line>,
        linesByIds: suspend (Collection<Long>) -> List<Line>,
    ): List<Line> = links.firstOrNull { it.covers(bookId) }?.decorate(bookId, lines, linesByIds) ?: lines
}

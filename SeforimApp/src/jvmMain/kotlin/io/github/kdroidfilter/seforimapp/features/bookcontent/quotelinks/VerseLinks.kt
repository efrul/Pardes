package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LibraryLinks
import io.github.kdroidfilter.seforimlibrary.core.models.Line

/**
 * The verses the Talmud quotes as links to them, from the library's own links: where a line of the Bavli or the
 * Yerushalmi is linked to a verse, the verse's words in it are made a link ([withVerseLinks]). In place of Zayit's
 * private package, which Pardes may not use: handed to the reader by addons/quotelinks/disabled.
 */
object VerseLinks : QuoteLinksFeature {
    /** The library's links, given once the app's graph is made (main.kt); no book is covered without them. */
    @Volatile
    var library: LibraryLinks? = null

    // The Talmud's tractates, read once from the library
    @Volatile
    private var talmud: Set<Long>? = null

    override fun covers(bookId: Long): Boolean {
        val library = library ?: return false
        val books = talmud ?: library.booksOf(TALMUD).also { if (it.isNotEmpty()) talmud = it }
        return bookId in books
    }

    override suspend fun decorate(
        bookId: Long,
        lines: List<Line>,
        linesByIds: suspend (Collection<Long>) -> List<Line>,
    ): List<Line> {
        val library = library ?: return lines
        val verses = library.linesIn(TANAKH, lines.map { it.id })
        if (verses.isEmpty()) return lines
        return lines.map { line ->
            val linked = verses[line.id] ?: return@map line
            val html = withVerseLinks(line.content, linked.map { Verse(it.id, wordsOfVerse(it.content)) })
            if (html === line.content) line else line.copy(content = html)
        }
    }

    private val TANAKH =
        with(CatalogPresets.Ids.Categories) { listOf(TORAH, NEVIIM, KETUVIM) }

    private val TALMUD =
        with(CatalogPresets.Ids.Categories) {
            (BAVLI_ZERAIM..BAVLI_TAHAROT) + (YERUSHALMI_ZERAIM..YERUSHALMI_TAHAROT)
        }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LibraryLinks
import io.github.kdroidfilter.seforimlibrary.core.models.Line

/**
 * The verses the Talmud quotes as links to them, from the library's own links: where a line of the Bavli or the
 * Yerushalmi is linked to a verse, the verse's words in it are made a link ([withVerseLinks]), and so are they in the
 * lines after it that quote the verse again. In place of Zayit's private package, which Pardes may not use: handed to
 * the reader by addons/quotelinks/disabled.
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
        val first = lines.firstOrNull() ?: return lines
        // The lines before the page too: a verse is linked to the line that quotes it first, and quoted again after it
        val before = library.lineIdsBefore(first, NEAR_LINES)
        val order = before + lines.map { it.id }
        val linked = library.linesIn(TANAKH, order)
        if (linked.isEmpty()) return lines
        val verses = HashMap<Long, Verse>()

        fun versesOf(lineId: Long) = linked[lineId].orEmpty().map { verses.getOrPut(it.id) { Verse(it.id, wordsOfVerse(it.content)) } }
        return lines.mapIndexed { index, line ->
            val own = versesOf(line.id)
            val at = before.size + index
            val near =
                (1..minOf(NEAR_LINES, at))
                    .flatMap { back -> versesOf(order[at - back]) }
                    .filter { verse -> own.none { it.lineId == verse.lineId } }
                    .distinctBy { it.lineId }
            if (own.isEmpty() && near.isEmpty()) return@mapIndexed line
            val html = withVerseLinks(line.content, own, near)
            if (html === line.content) line else line.copy(content = html)
        }
    }

    // A verse is looked for in the ten lines after the one linked to it: further on it is quoted again in few lines
    private const val NEAR_LINES = 10

    private val TANAKH =
        with(CatalogPresets.Ids.Categories) { listOf(TORAH, NEVIIM, KETUVIM) }

    private val TALMUD =
        with(CatalogPresets.Ids.Categories) {
            (BAVLI_ZERAIM..BAVLI_TAHAROT) + (YERUSHALMI_ZERAIM..YERUSHALMI_TAHAROT)
        }
}

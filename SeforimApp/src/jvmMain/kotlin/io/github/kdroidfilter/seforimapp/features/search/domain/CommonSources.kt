package io.github.kdroidfilter.seforimapp.features.search.domain

/** A link of a result: the book line at its other end, and that line's book. */
data class LinkEnd(
    val pageLine: Long,
    val otherLine: Long,
    val otherBook: Long,
)

/** A source shown, how many books of the page link to it, and the page's lines that do ([from], in the page's order). */
data class CommonSourcePick(
    val lineId: Long,
    val bookId: Long,
    val books: Int,
    val from: List<Long> = emptyList(),
)

/**
 * Pardes: the line of common sources above the results (docs/COMMON_SOURCES_PLAN.md): the book lines that many of the
 * first results link to, though the search may not show them itself (דברים ו, ד for "שמע ישראל ה' אלהינו ה' אחד",
 * 27th in the list and linked from 9 of its books). The rule measured in pardes-semantic's eval-real/common_source.py.
 */
object CommonSources {
    /** The results read: the first page. */
    const val PAGE = 20

    /** The first results, never a source: they are seen already. */
    const val SKIP = 3

    /** The books of the page a source is linked from, at least, while a line is. */
    const val MIN_BOOKS = 3

    /**
     * The results a source is linked from, at least, when no line is linked from [MIN_BOOKS] books of the page; when none
     * is linked from as many, any line a result links to: a search in one book or in few has its sources too (the user's
     * word of 2026-11-27, a source whatever the search is limited to).
     */
    const val MIN_RESULTS = 2

    /** The sources shown, at most. */
    const val SHOWN = 3

    /**
     * The sources of [page] (the first [PAGE] results as (lineId, bookId), in the engine's order) from their [links],
     * both directions, lines of any book: those linked from [MIN_BOOKS] books, the most books first, then a base book
     * ([baseBooks]), then by [rank] (the place in the page), then by id, as measured; without one, those linked from
     * [MIN_RESULTS] results, else any line a result links to, the most books first, then a base book, then the most
     * results, then the earliest result linking to it. Those linked from half the first's books or more.
     */
    fun pick(
        page: List<Pair<Long, Long>>,
        links: List<LinkEnd>,
        baseBooks: Set<Long>,
        rank: (Long) -> Int = { line -> page.indexOfFirst { it.first == line }.let { if (it < 0) page.size else it } },
    ): List<CommonSourcePick> {
        val bookOf = page.toMap()
        val skip = page.take(SKIP).mapTo(HashSet()) { it.first }
        val books = HashMap<Pair<Long, Long>, MutableSet<Long>>()
        val lines = HashMap<Pair<Long, Long>, MutableSet<Long>>()
        for (end in links) {
            val pageBook = bookOf[end.pageLine]
            if (pageBook != null && end.otherLine !in skip && end.otherLine != end.pageLine) {
                books.getOrPut(end.otherLine to end.otherBook) { HashSet() } += pageBook
                lines.getOrPut(end.otherLine to end.otherBook) { HashSet() } += end.pageLine
            }
        }
        val place = page.withIndex().associate { (i, result) -> result.first to i }
        val found =
            books.map { (line, bs) -> CommonSourcePick(line.first, line.second, bs.size, lines.getValue(line).sortedBy(place::getValue)) }
        val wide = found.filter { it.books >= MIN_BOOKS }
        val top =
            if (wide.isNotEmpty()) {
                wide.sortedWith(compareBy({ -it.books }, { it.bookId !in baseBooks }, { rank(it.lineId) }, { it.lineId }))
            } else {
                found
                    .filter { it.from.size >= MIN_RESULTS }
                    .ifEmpty { found }
                    .sortedWith(
                        compareBy(
                            { -it.books },
                            { it.bookId !in baseBooks },
                            { -it.from.size },
                            { place.getValue(it.from.first()) },
                            { rank(it.lineId) },
                            { it.lineId },
                        ),
                    )
            }.take(SHOWN)
        return top.filter { 2 * it.books >= top.first().books }
    }
}

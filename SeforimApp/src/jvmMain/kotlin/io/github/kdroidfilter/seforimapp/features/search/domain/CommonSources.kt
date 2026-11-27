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

    /** The books of the page a source is linked from, at least. */
    const val MIN_BOOKS = 3

    /** The sources shown, at most. */
    const val SHOWN = 3

    /**
     * The sources of [page] (the first [PAGE] results as (lineId, bookId), in the engine's order) from their [links],
     * both directions: the most books first, then a base book, then by [rank] (the place in the page), then by id;
     * those linked from half the first's books or more. In a base books' search ([baseOnly]) only lines of [baseBooks].
     */
    fun pick(
        page: List<Pair<Long, Long>>,
        links: List<LinkEnd>,
        baseBooks: Set<Long>,
        baseOnly: Boolean,
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
        val top =
            books
                .filter { (line, bs) -> bs.size >= MIN_BOOKS && (!baseOnly || line.second in baseBooks) }
                .map { (line, bs) -> CommonSourcePick(line.first, line.second, bs.size, lines.getValue(line).sortedBy(place::getValue)) }
                .sortedWith(compareBy({ -it.books }, { it.bookId !in baseBooks }, { rank(it.lineId) }, { it.lineId }))
                .take(SHOWN)
        return top.filter { 2 * it.books >= top.first().books }
    }
}

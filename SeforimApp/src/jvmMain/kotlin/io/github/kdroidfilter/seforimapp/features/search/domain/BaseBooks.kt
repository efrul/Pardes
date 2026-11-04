package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory

/**
 * The books a category is searched in "ספרי יסוד בלבד": its texts without the commentaries on them.
 *
 * The library's base-book flag (isBaseBook) is a list curated for the great corpora alone (386 books: תנ״ך, משנה,
 * תלמוד, משנה תורה, the codes, the midrashim), so a category of other books (מהר״ל מפראג, with הרטמן's notes on each of
 * his books; ספר יצירה and its commentaries; the הגדה and its commentaries) had no base book and was never offered so.
 * The rule, for a category:
 * - one with curated base books keeps them, but for those that are commentaries by their place or title (below a
 *   category named מפרשים, or titled הערות…, ביאור…, פירוש…: הערות בובר על מדרש משלי, מאיר עין על סדר עולם רבה);
 * - another is told apart by the library's links: its commentaries are the books that comment (a commentary,
 *   super-commentary, targum, parshanut or dibur hamatchil link) on a book of the same category, and its base books are
 *   the others, but for those commentaries by place or title. The commentaries on books of another category don't count:
 *   the זהר comments on the Torah, and is the base book of קבלה › זהר. When they are too few (under
 *   [MIN_COMMENTARIES_PERCENT] of the books: one note among שו״ת's 405 books) the choice isn't offered.
 *
 * Shared with Pardes for Android, which compiles this file unchanged.
 */
object BaseBooks {
    /**
     * The library's pairs of a commentary and the book it comments on (bookIds), for [of]: 12,897 pairs in the October
     * 2026 library, read in under a second.
     */
    const val COMMENTARY_PAIRS_SQL =
        "SELECT DISTINCT l.targetBookId, l.sourceBookId FROM link l " +
            "JOIN connection_type ct ON ct.id = l.connectionTypeId " +
            "WHERE ct.name IN ('COMMENTARY', 'SUPER_COMMENTARY', 'TARGUM', 'PARSHANUT', 'DIBUR_HAMATCHIL') " +
            "AND l.sourceBookId != l.targetBookId"

    /** A category's books (its subcategories' too, in the library's order) and its base books. */
    class Books(
        val all: LongArray,
        /** Its base books; all of them when it isn't searched in its base books alone. */
        val base: LongArray,
    ) {
        /** It holds base books and others: in its base books alone it is another search. */
        val hasBaseBooksAndOthers: Boolean get() = base.isNotEmpty() && base.size < all.size
    }

    /**
     * Every category of [roots] and its books, by id. [commented] gives each commentary the books it comments on
     * ([COMMENTARY_PAIRS_SQL]); without it only the curated base books count.
     */
    fun of(
        roots: List<CatalogCategory>,
        commented: Map<Long, LongArray> = emptyMap(),
    ): Map<Long, Books> {
        val result = HashMap<Long, Books>()

        // The books under [category], at [depth]; [commentariesDepth] is the depth of the deepest category named מפרשים
        // above it (or itself), -1 for none
        fun walk(
            category: CatalogCategory,
            depth: Int,
            commentariesDepth: Int,
        ): List<BookInfo> {
            val placeDepth = if (category.title.trim() == COMMENTARIES_CATEGORY) depth else commentariesDepth
            val books = ArrayList<BookInfo>()
            category.books.forEach { books += BookInfo(it.id, it.isBaseBook, isCommentaryTitle(it.title), placeDepth) }
            category.subcategories.forEach { books += walk(it, depth + 1, placeDepth) }
            result[category.id] = books(books, depth, commented)
            return books
        }
        roots.forEach { walk(it, 0, -1) }
        return result
    }

    private class BookInfo(
        val id: Long,
        val curated: Boolean,
        val commentaryTitle: Boolean,
        // The depth of the deepest category named מפרשים on its path, -1 for none
        val commentariesDepth: Int,
    ) {
        // A commentary by its title, or by its place below the category searched (at [depth]); in a category named
        // מפרשים, its books are the texts searched
        fun isCommentaryByName(depth: Int): Boolean = commentaryTitle || commentariesDepth > depth
    }

    private fun books(
        books: List<BookInfo>,
        depth: Int,
        commented: Map<Long, LongArray>,
    ): Books {
        val all = LongArray(books.size) { books[it].id }
        val curated = books.filter { it.curated && !it.isCommentaryByName(depth) }
        if (curated.isNotEmpty()) return Books(all, curated.map { it.id }.toLongArray())

        if (commented.isEmpty()) return Books(all, all)
        val inside = HashSet<Long>(all.size * 2).apply { all.forEach { add(it) } }
        val commentaries = books.filterTo(HashSet()) { book -> commented[book.id]?.any { it in inside } == true }
        if (commentaries.isEmpty() || commentaries.size * 100 < books.size * MIN_COMMENTARIES_PERCENT) return Books(all, all)
        val base = books.filter { it !in commentaries && !it.isCommentaryByName(depth) }
        return Books(all, if (base.isEmpty()) all else base.map { it.id }.toLongArray())
    }

    /** Whether [title] names a commentary: הערות…, הגהות…, ביאור…, פירוש… */
    fun isCommentaryTitle(title: String): Boolean {
        val first = title.trim().substringBefore(' ')
        return first != title.trim() && first in COMMENTARY_TITLE_WORDS
    }

    /** Below this share of commentaries on its own books, a category isn't offered in its base books alone. */
    const val MIN_COMMENTARIES_PERCENT = 5

    private const val COMMENTARIES_CATEGORY = "מפרשים"

    private val COMMENTARY_TITLE_WORDS = setOf("הערות", "הגהות", "ביאור", "ביאורי", "ביאורים", "פירוש", "פירושי", "פירושים")
}

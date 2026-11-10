package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory

/**
 * The commentaries on one book that stand under one category named for their time: "ראשונים על בבא בתרא", the books
 * under תלמוד › בבלי › ראשונים that comment on בבא בתרא. The library has no category for them (each commentator has his
 * own, a tractate at a time), so they are made here, from the catalog and the library's commentary links, and searched
 * as a category is, under an id of their own ([idOf], above every category's of the library).
 */
object CommentaryGroups {
    /** One such group: as the bar shows it (its path the time's name, then the book's title) and its books. */
    class Group(
        val hit: CategoryHit,
        val bookIds: LongArray,
    )

    /**
     * The groups of the catalog [roots], in its order. [links] gives each commentary the books it comments on and how
     * many links it has to each: a commentary is on the book it has the most links to, and on any other that has a
     * tenth of its links (the טור הארוך on each of the Torah's books, the רי״ף on בבא מציעא and not on בבא בתרא, which
     * it mentions). A group is of a category named for a time above the commentary, in the root category of the book
     * commented on, and has two books at least.
     */
    fun of(
        roots: List<CatalogCategory>,
        links: Map<Long, Map<Long, Int>>,
    ): List<Group> {
        val titles = HashMap<Long, String>()
        val rootOf = HashMap<Long, Long>()

        fun index(
            category: CatalogCategory,
            root: Long,
        ) {
            category.books.forEach {
                titles[it.id] = it.title
                rootOf[it.id] = root
            }
            category.subcategories.forEach { index(it, root) }
        }
        roots.forEach { index(it, it.id) }

        // (the time's category, the book commented on) to the commentaries, as they come
        val found = LinkedHashMap<Pair<CatalogCategory, Long>, MutableList<Long>>()

        fun walk(
            category: CatalogCategory,
            root: Long,
            times: List<CatalogCategory>,
        ) {
            val above = if (category.title.trim() in TIMES) times + category else times
            if (above.isNotEmpty()) {
                for (book in category.books) {
                    val commented = links[book.id]?.let(::mainBooks).orEmpty().filter { rootOf[it] == root }
                    for (time in above) commented.forEach { found.getOrPut(time to it) { ArrayList() } += book.id }
                }
            }
            category.subcategories.forEach { walk(it, root, above) }
        }
        roots.forEach { walk(it, it.id, emptyList()) }

        return found.mapNotNull { (key, books) ->
            val (time, bookId) = key
            val title = titles[bookId] ?: return@mapNotNull null
            if (books.size < MIN_BOOKS) return@mapNotNull null
            Group(CategoryHit(idOf(time.id, bookId), listOf(time.title.trim(), title)), books.toLongArray())
        }
    }

    /**
     * The groups [query] names, best first: a word of it must begin the time's name and the others the book's title,
     * one at least (`ראשונים על בבא בתרא`, `אחרונים על ברכות`); `על` and `של` are skipped and a word's leading ה may be
     * dropped. The book named in full comes first (בבא בתרא before משנה בבא בתרא), then the shorter title.
     */
    fun match(
        query: String,
        groups: List<CategoryHit>,
        limit: Int,
    ): List<CategoryHit> {
        val words =
            query
                .searchKey()
                .split(' ')
                .filter { it.isNotEmpty() && it !in JOINING_WORDS }
        if (words.size < 2) return emptyList()
        return groups
            .mapNotNull { group -> rank(words, group)?.let { group to it } }
            .sortedWith(compareBy({ it.second.first }, { it.second.second }))
            .take(limit)
            .map { it.first }
    }

    /**
     * The id a group is searched under: of [categoryId], its time's category, and [bookId], the book commented on.
     * Ten million at least, where the library's categories are a few thousand: no category has it, and it is a
     * category's id wherever one is kept (a tab's scope, the history).
     */
    fun idOf(
        categoryId: Long,
        bookId: Long,
    ): Long = categoryId * BOOK_IDS + bookId

    // (the book's title isn't named in full, its length) of a group the words name; null of another
    private fun rank(
        words: List<String>,
        group: CategoryHit,
    ): Pair<Boolean, Int>? {
        val time = group.path.first().searchKey()
        val title = group.title.searchKey()
        val ofTime = words.filter { word -> word.forms().any { time.hasWordStartingWith(it) } }
        val ofTitle = words - ofTime.toSet()
        if (ofTime.isEmpty() || ofTitle.isEmpty()) return null
        if (!ofTitle.all { word -> word.forms().any { title.hasWordStartingWith(it) } }) return null
        return (ofTitle.joinToString(" ") != title) to title.length
    }

    // The books a commentary is on, among those it has links to
    private fun mainBooks(links: Map<Long, Int>): List<Long> {
        val most = links.values.maxOrNull() ?: return emptyList()
        val total = links.values.sum()
        return links.filter { (_, count) -> count == most || count * MAIN_SHARE >= total }.keys.toList()
    }

    // A typed word, and without its leading ה
    private fun String.forms(): List<String> = if (length > 2 && startsWith('ה')) listOf(this, drop(1)) else listOf(this)

    private fun String.hasWordStartingWith(prefix: String): Boolean = startsWith(prefix) || contains(" $prefix")

    private val TIMES = setOf("גאונים", "ראשונים", "אחרונים", "מחברי זמננו", "מפרשים")
    private val JOINING_WORDS = setOf("על", "של")

    private const val MIN_BOOKS = 2

    // A book is commented on with a tenth of the commentary's links
    private const val MAIN_SHARE = 10

    // Above every book id of the library, so that a category and a book give one id
    private const val BOOK_IDS = 10_000_000L
}

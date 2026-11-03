package io.github.kdroidfilter.seforimapp.framework.search.titles

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import java.sql.Connection
import java.sql.DriverManager
import kotlin.concurrent.thread

/**
 * Builds the [BookTitleIndex] of a library: the books from its catalog (catalog.pb, already in memory), in the tree's
 * order, with the names of the categories above them; their authors, the authors' other names and the books' acronyms
 * from seforim.db (three short queries, read only). Android compiles this file as-is and reads the database its own way
 * ([build] with its rows).
 */
object BookTitleIndexLoader {
    /** A row a query returned, its columns counted from 0. */
    interface Row {
        fun long(column: Int): Long

        fun string(column: Int): String?
    }

    fun load(
        catalog: PrecomputedCatalog,
        dbPath: String,
    ): BookTitleIndex = DriverManager.getConnection("jdbc:sqlite:$dbPath").use { load(catalog, it) }

    fun load(
        catalog: PrecomputedCatalog,
        db: Connection,
    ): BookTitleIndex =
        build(catalog) { sql, each ->
            db.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    val row =
                        object : Row {
                            override fun long(column: Int) = rs.getLong(column + 1)

                            override fun string(column: Int): String? = rs.getString(column + 1)
                        }
                    while (rs.next()) each(row)
                }
            }
        }

    /** The index of [catalog], reading the database by [query] (each row of an SQL query given to its callback). */
    fun build(
        catalog: PrecomputedCatalog,
        query: (sql: String, each: (Row) -> Unit) -> Unit,
    ): BookTitleIndex {
        val authorsOfBook = HashMap<Long, MutableList<Long>>()
        val names = HashMap<Long, String>()
        query(BOOK_AUTHORS) { row ->
            authorsOfBook.getOrPut(row.long(0)) { ArrayList() } += row.long(1)
            row.string(2)?.let { names[row.long(1)] = it }
        }
        val aliases = HashMap<Long, MutableList<String>>()
        query(ALIASES) { row -> row.string(1)?.let { aliases.getOrPut(row.long(0)) { ArrayList() } += it } }
        val acronyms = HashMap<Long, MutableList<String>>()
        query(ACRONYMS) { row -> row.string(1)?.let { acronyms.getOrPut(row.long(0)) { ArrayList() } += it } }
        val bookCounts =
            authorsOfBook.values
                .flatten()
                .groupingBy { it }
                .eachCount()
        val authors = names.map { (id, name) -> TitleAuthor(id, name, aliases[id].orEmpty(), bookCounts[id] ?: 0) }

        val books = ArrayList<TitleBook>(catalog.totalBooks)

        fun walk(
            category: CatalogCategory,
            path: List<String>,
        ) {
            val here = path + category.title
            category.books.sortedBy { it.order }.forEach { b ->
                books +=
                    TitleBook(
                        id = b.id,
                        title = b.title,
                        categoryId = b.categoryId,
                        order = b.order,
                        isBaseBook = b.isBaseBook,
                        path = here,
                        authorIds = authorsOfBook[b.id].orEmpty(),
                        acronyms = acronyms[b.id].orEmpty(),
                    )
            }
            category.subcategories.forEach { walk(it, here) }
        }
        catalog.rootCategories.forEach { walk(it, emptyList()) }
        return BookTitleIndex(books, authors)
    }

    private const val BOOK_AUTHORS = "SELECT ba.bookId, a.id, a.name FROM book_author ba JOIN author a ON a.id = ba.authorId"
    private const val ALIASES = "SELECT authorId, alias FROM author_alias"
    private const val ACRONYMS = "SELECT bookId, term FROM book_acronym"
}

/**
 * The [BookTitleIndex] of the installed library, built once on first use (a fraction of a second) or ahead of it by
 * [warmUp]. Null when it cannot be built (no catalog; [onError] is told why): the lookup index alone serves the
 * suggestions then.
 */
class BookTitleIndexHolder(
    private val onError: (Throwable) -> Unit = {},
    private val build: () -> BookTitleIndex?,
) {
    @Volatile
    private var index: BookTitleIndex? = null

    @Volatile
    private var failed = false

    fun get(): BookTitleIndex? = index ?: if (failed) null else synchronized(this) { index ?: buildOnce() }

    private fun buildOnce(): BookTitleIndex? {
        if (failed) return null
        val built = runCatching(build).onFailure(onError).getOrNull()
        if (built == null) failed = true else index = built
        return built
    }

    /** Builds the index off the caller's thread, so the first typed letter does not wait for it. */
    fun warmUp() {
        if (index == null && !failed) thread(name = "book-title-index", isDaemon = true) { get() }
    }
}

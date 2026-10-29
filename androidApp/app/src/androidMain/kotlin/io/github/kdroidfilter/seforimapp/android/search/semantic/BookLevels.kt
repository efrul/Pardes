// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/search/semantic/BookLevels.kt, same package
// and names. A diff against it stays small; each change is marked "Android:": the library is read through the app's own
// SqlDriver (LibraryStore's, as every other read of seforim.db here) instead of a JDBC connection of its own, which
// Android has no driver for: the same three queries for the levels and the same chunked one for the lines.
package io.github.kdroidfilter.seforimapp.features.search.semantic

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * The level of a book of the library, for the precedence among the lines that hold the query as a sentence
 * ([LevelPrecedence]): the earlier level first (semantic-model/eval-real/results-level-order.md). [MIKRA], [CHAZAL],
 * [GEONIM], [RISHONIM], [ACHARONIM], [CONTEMPORARY], the lower the earlier; [levelOf] tells a book's from the library
 * itself, as semantic-model/eval-real/level_order.py's book_levels does.
 */
internal object BookLevels {
    const val MIKRA = 0
    const val CHAZAL = 1
    const val GEONIM = 2
    const val RISHONIM = 3
    const val ACHARONIM = 4
    const val CONTEMPORARY = 5

    /** A book of no known era ranks with the Acharonim, the era of most such books. */
    const val UNKNOWN = ACHARONIM

    /** The library's author eras (Sefaria's codes) the levels name. */
    private val ERA = mapOf("GN" to GEONIM, "RI" to RISHONIM, "AH" to ACHARONIM, "CO" to CONTEMPORARY)

    /** The categories named for an era; a translation (Targum) counts as Chazal's. */
    private val ERA_OF_CATEGORY =
        mapOf("גאונים" to GEONIM, "ראשונים" to RISHONIM, "אחרונים" to ACHARONIM, "מחברי זמננו" to CONTEMPORARY, "תרגומים" to CHAZAL)

    private const val TANAKH = "תנ״ך"
    private val TANAKH_PARTS = setOf("תורה", "נביאים", "כתובים")

    /** Chazal's categories, but for the commentaries, aids, references and notes inside them. */
    private val CHAZAL_CATEGORIES = setOf("משנה", "תלמוד", "תוספתא", "מדרש")
    private val LATER = setOf("מפרשים", "ספרות עזר", "מראי מקומות", "כללי הש״ס", "מפרשים על המסכתות הקטנות")
    private val NOTES = listOf("הערות", "הגהות", "נוסחא", "שינויי נוסחאות")

    /** Compilations of Chazal's words rank by their compilers. */
    private val COMPILATIONS = listOf("ילקוט שמעוני" to RISHONIM, "עין יעקב" to ACHARONIM)

    /**
     * The level of the book [title] in the categories [path] (from the root), by its authors' [eras] first (the latest
     * of them), then the Tanakh's own books, a category named for an era (the deepest), a compilation, Chazal's
     * categories; [UNKNOWN] otherwise.
     */
    fun levelOf(
        title: String,
        path: List<String>,
        eras: Collection<String>,
    ): Int {
        val ofAuthors = eras.mapNotNull { ERA[it] }
        val named = path.mapNotNull { ERA_OF_CATEGORY[it] }
        val compilation = COMPILATIONS.firstOrNull { (name, _) -> title.startsWith(name) }
        return when {
            ofAuthors.isNotEmpty() -> ofAuthors.max()
            path.firstOrNull() == TANAKH && path.getOrNull(1) in TANAKH_PARTS -> MIKRA
            named.isNotEmpty() -> named.last()
            compilation != null -> compilation.second
            path.firstOrNull() in CHAZAL_CATEGORIES && LATER.none { it in path } && NOTES.none { title.startsWith(it) } -> CHAZAL
            else -> UNKNOWN
        }
    }

    /** {bookId: level} of every book of the library at [driver]. */
    // Android: through the app's SqlDriver, the same queries as desktop's JDBC ones
    fun read(driver: SqlDriver): Map<Long, Int> {
        val parent = HashMap<Long, Long>()
        val name = HashMap<Long, String>()
        driver.forEachRow("SELECT id, parentId, title FROM category") { row ->
            val id = row.getLong(0)!!
            val parentId = row.getLong(1)
            if (parentId != null && parentId != 0L) parent[id] = parentId
            name[id] = row.getString(2).orEmpty()
        }

        fun path(category: Long): List<String> {
            val out = ArrayList<String>()
            var at: Long? = category
            // A category tree has no cycle; the bound only guards a broken one
            while (at != null && out.size < parent.size + 1) {
                out += name[at] ?: break
                at = parent[at]
            }
            return out.asReversed()
        }

        val eras = HashMap<Long, MutableSet<String>>()
        driver.forEachRow("SELECT ba.bookId, a.era FROM book_author ba JOIN author a ON a.id = ba.authorId") { row ->
            val book = row.getLong(0)!!
            row.getString(1)?.let { eras.getOrPut(book) { HashSet() } += it }
        }
        val levels = HashMap<Long, Int>()
        driver.forEachRow("SELECT id, categoryId, title FROM book") { row ->
            val book = row.getLong(0)!!
            levels[book] = levelOf(row.getString(2).orEmpty(), path(row.getLong(1) ?: 0L), eras[book].orEmpty())
        }
        return levels
    }
}

/** A line's book and text (its HTML, as the library keeps it). */
internal class LineText(
    val bookId: Long,
    val text: String,
)

/**
 * The library's book levels ([BookLevels]) and lines, read from its database for the precedence ([LevelPrecedence]);
 * the levels are read once. Android: through [driver], the app's own connection to seforim.db (LibraryStore's), asked
 * for each read so that a library replaced since is the one read.
 */
internal class LibraryLines(
    private val driver: () -> SqlDriver,
) : AutoCloseable {
    private val lock = Any()
    private var levels: Map<Long, Int>? = null

    /** {bookId: level} of every book. */
    fun levels(): Map<Long, Int> = synchronized(lock) { levels ?: BookLevels.read(driver()).also { levels = it } }

    /** {lineId: its book and text} of [ids] (those the library has). */
    fun lines(ids: Collection<Long>): Map<Long, LineText> =
        synchronized(lock) {
            val out = HashMap<Long, LineText>(ids.size * 2)
            ids.distinct().chunked(MAX_PARAMETERS).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                val sql = "SELECT id, bookId, content FROM line WHERE id IN ($marks)"
                driver().forEachRow(sql, chunk.size, { chunk.forEachIndexed { i, id -> bindLong(i, id) } }) { row ->
                    out[row.getLong(0)!!] = LineText(row.getLong(1)!!, row.getString(2).orEmpty())
                }
            }
            out
        }

    // Android: the connection is the app's, closed with the library (LibraryStore.close)
    override fun close() {}

    companion object {
        /** Under SQLite's default bound on a statement's parameters (999 before 3.32). */
        private const val MAX_PARAMETERS = 900
    }
}

// Android: each row of [sql] (bound by [bind], with [parameters] parameters), through the driver
private fun SqlDriver.forEachRow(
    sql: String,
    parameters: Int = 0,
    bind: (SqlPreparedStatement.() -> Unit)? = null,
    row: (SqlCursor) -> Unit,
) {
    executeQuery(
        null,
        sql,
        { cursor ->
            while (cursor.next().value) row(cursor)
            QueryResult.Unit
        },
        parameters,
        bind,
    )
}

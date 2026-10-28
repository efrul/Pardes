package io.github.kdroidfilter.seforimapp.features.search.semantic

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

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

    /** {bookId: level} of every book of the library at [connection]. */
    fun read(connection: Connection): Map<Long, Int> {
        val parent = HashMap<Long, Long>()
        val name = HashMap<Long, String>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT id, parentId, title FROM category").use { rs ->
                while (rs.next()) {
                    val id = rs.getLong(1)
                    val parentId = rs.getLong(2)
                    if (!rs.wasNull() && parentId != 0L) parent[id] = parentId
                    name[id] = rs.getString(3)
                }
            }
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
        connection.createStatement().use { st ->
            st.executeQuery("SELECT ba.bookId, a.era FROM book_author ba JOIN author a ON a.id = ba.authorId").use { rs ->
                while (rs.next()) {
                    val book = rs.getLong(1)
                    rs.getString(2)?.let { eras.getOrPut(book) { HashSet() } += it }
                }
            }
        }
        val levels = HashMap<Long, Int>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT id, categoryId, title FROM book").use { rs ->
                while (rs.next()) {
                    val book = rs.getLong(1)
                    levels[book] = levelOf(rs.getString(3).orEmpty(), path(rs.getLong(2)), eras[book].orEmpty())
                }
            }
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
 * The library's book levels ([BookLevels]) and lines, read from its database at [db] for the precedence
 * ([LevelPrecedence]), on a read-only connection of its own that it opens when first asked; the levels are read once.
 */
internal class LibraryLines(
    private val db: Path,
) : AutoCloseable {
    private val lock = Any()
    private var connection: Connection? = null
    private var levels: Map<Long, Int>? = null

    // Read-only, as VectorIndexBuilder reads it: the app's own connection is the one that writes (the delta updates)
    private fun connection(): Connection =
        connection ?: DriverManager.getConnection("jdbc:sqlite:file:$db?mode=ro").also { connection = it }

    /** {bookId: level} of every book. */
    fun levels(): Map<Long, Int> = synchronized(lock) { levels ?: BookLevels.read(connection()).also { levels = it } }

    /** {lineId: its book and text} of [ids] (those the library has). */
    fun lines(ids: Collection<Long>): Map<Long, LineText> =
        synchronized(lock) {
            val out = HashMap<Long, LineText>(ids.size * 2)
            ids.distinct().chunked(MAX_PARAMETERS).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                connection().prepareStatement("SELECT id, bookId, content FROM line WHERE id IN ($marks)").use { ps ->
                    chunk.forEachIndexed { i, id -> ps.setLong(i + 1, id) }
                    ps.executeQuery().use { rs ->
                        while (rs.next()) out[rs.getLong(1)] = LineText(rs.getLong(2), rs.getString(3).orEmpty())
                    }
                }
            }
            out
        }

    override fun close() {
        synchronized(lock) {
            runCatching { connection?.close() }
            connection = null
        }
    }

    companion object {
        /** Under SQLite's default bound on a statement's parameters (999 before 3.32). */
        private const val MAX_PARAMETERS = 900

        /**
         * The library's database beside the text index [indexDir], as the app names them (`seforim.db` and
         * `seforim.db.lucene`, or `<db>.luceneindex`); null when it isn't there.
         */
        fun beside(indexDir: Path): LibraryLines? {
            val name = indexDir.fileName?.toString() ?: return null
            val db =
                when {
                    name.endsWith(".lucene") -> indexDir.resolveSibling(name.removeSuffix(".lucene"))
                    name.endsWith(".luceneindex") -> indexDir.resolveSibling(name.removeSuffix(".luceneindex"))
                    else -> return null
                }
            return if (Files.isRegularFile(db)) LibraryLines(db) else null
        }
    }
}

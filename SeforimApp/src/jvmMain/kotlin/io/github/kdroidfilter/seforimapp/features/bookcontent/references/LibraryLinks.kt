package io.github.kdroidfilter.seforimapp.features.bookcontent.references

import dev.zacsweers.metro.Inject
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.LineReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * The library's links of the kinds no pane lists ([KINDS]): a quotation, a Mishnah in the Talmud, a related passage and
 * the links of no kind, some 1.7 million of them. Read from the database on a read-only connection of its own (as
 * CommentaryLinks reads it): the repository has no query for them. Such a link is stored once, with no direction of its
 * own, so that a line's links are those that start from it and those that reach it.
 */
@Inject
class LibraryLinks(
    private val databasePathProvider: DatabasePathProvider,
) {
    /**
     * The lines the links of [lineIds] reach, as the repository's getReferencesForLines returns its own: a line once,
     * however many links reach it, the lines of [lineIds] left out. Empty when the database can't be read.
     */
    suspend fun of(lineIds: Collection<Long>): List<LineReference> = read(emptyList()) { connection -> linked(connection, lineIds) }

    /** Whether a line of [lineIds] has such a link: the links' indexes alone are read, no line. */
    suspend fun any(lineIds: Collection<Long>): Boolean = read(false) { connection -> anyLinked(connection, lineIds) }

    /**
     * The lines of the books right under [categories] that each of [lineIds] is linked to, by such a link or a
     * reference: {line: the lines it is linked to}, a line once. The verses a line of the Talmud quotes are read so.
     */
    suspend fun linesIn(
        categories: Collection<Long>,
        lineIds: Collection<Long>,
    ): Map<Long, List<Line>> = read(emptyMap()) { connection -> linkedIn(connection, categories, lineIds) }

    /** The [count] lines that come before [line] in its book, in its order (fewer at the book's start). */
    suspend fun lineIdsBefore(
        line: Line,
        count: Int,
    ): List<Long> = read(emptyList()) { connection -> idsBefore(connection, line, count) }

    /**
     * The lines of the book [bookId] that each of [lineIds] is linked to, each with the [chars] letters it starts with:
     * {line: (linked line, its start)}. A book's notes are told so, by the number each starts with.
     */
    suspend fun startsIn(
        bookId: Long,
        lineIds: Collection<Long>,
        chars: Int,
    ): Map<Long, List<Pair<Long, String>>> = read(emptyMap()) { connection -> linkedStarts(connection, bookId, lineIds, chars) }

    /**
     * The books that have a book of notes on them, each with it: {book: its notes}, the notes' book named for it
     * ([NOTES_ON]). A few rows, read where it is called; empty when the database can't be read.
     */
    fun notedBooks(): Map<Long, Long> =
        runCatching { readOnly(databasePathProvider.get()).use(::booksWithNotes) }
            .getOrElse {
                warnln { "[LibraryLinks] the library's books of notes could not be read: $it" }
                emptyMap()
            }

    /** The books right under [categories]; a few rows, read where it is called. Empty when the database can't be read. */
    fun booksOf(categories: Collection<Long>): Set<Long> =
        runCatching { readOnly(databasePathProvider.get()).use { connection -> booksIn(connection, categories) } }
            .getOrElse {
                warnln { "[LibraryLinks] the library's books could not be read: $it" }
                emptySet()
            }

    private suspend fun <T> read(
        otherwise: T,
        query: (Connection) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            runCatching { readOnly(databasePathProvider.get()).use(query) }
                .getOrElse {
                    warnln { "[LibraryLinks] the library's links could not be read: $it" }
                    otherwise
                }
        }

    companion object {
        /** The kinds read here: the ones neither the repository's cross-references nor its SOURCE view hold. */
        val KINDS =
            setOf(ConnectionType.QUOTATION, ConnectionType.MISHNAH_IN_TALMUD, ConnectionType.RELATED, ConnectionType.OTHER)

        private val KIND_IDS = "SELECT id FROM connection_type WHERE name IN (${KINDS.joinToString { "'${it.name}'" }})"

        /** What the library names a book's notes by: הערות על חברותא על ברכות, the notes on חברותא על ברכות. */
        const val NOTES_ON = "הערות על "

        // The library read only, by a property and not a file: URI, which a '#' or a '%' in the folder's name would break
        private fun readOnly(path: String): Connection =
            DriverManager.getConnection("jdbc:sqlite:$path", Properties().apply { setProperty("open_mode", "1") })

        /** [LibraryLinks.of] on [connection]. */
        internal fun linked(
            connection: Connection,
            lineIds: Collection<Long>,
        ): List<LineReference> {
            if (lineIds.isEmpty()) return emptyList()
            val ids = lineIds.joinToString(",")
            val asked = lineIds.toSet()
            val seen = HashSet<Long>()
            val found = ArrayList<LineReference>()
            // The kind first, by the indexes on it: a line's commentaries, far more than these, are never read
            val sql =
                """
                SELECT ct.name, k.sourceLineId, ol.id, ol.bookId, ol.lineIndex, ol.heRef, ol.content, b.title, b.categoryId, b.orderIndex
                FROM link k
                JOIN connection_type ct ON ct.id = k.connectionTypeId
                JOIN line ol ON ol.id = k.targetLineId
                JOIN book b ON b.id = ol.bookId
                WHERE k.sourceLineId IN ($ids) AND k.connectionTypeId IN ($KIND_IDS)
                UNION ALL
                SELECT ct.name, k.targetLineId, ol.id, ol.bookId, ol.lineIndex, ol.heRef, ol.content, b.title, b.categoryId, b.orderIndex
                FROM link k
                JOIN connection_type ct ON ct.id = k.connectionTypeId
                JOIN line ol ON ol.id = k.sourceLineId
                JOIN book b ON b.id = ol.bookId
                WHERE k.targetLineId IN ($ids) AND k.connectionTypeId IN ($KIND_IDS)
                """.trimIndent()
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    while (rows.next()) {
                        val lineId = rows.getLong(3)
                        if (lineId in asked || !seen.add(lineId)) continue
                        found +=
                            LineReference(
                                connectionType = ConnectionType.fromString(rows.getString(1)),
                                fromLineId = rows.getLong(2),
                                line =
                                    Line(
                                        id = lineId,
                                        bookId = rows.getLong(4),
                                        lineIndex = rows.getInt(5),
                                        content = rows.getString(7),
                                        heRef = rows.getString(6),
                                    ),
                                bookTitle = rows.getString(8),
                                categoryId = rows.getLong(9),
                                bookOrderIndex = rows.getInt(10),
                            )
                    }
                }
            }
            return found
        }

        /** [LibraryLinks.linesIn] on [connection]. */
        internal fun linkedIn(
            connection: Connection,
            categories: Collection<Long>,
            lineIds: Collection<Long>,
        ): Map<Long, List<Line>> {
            if (lineIds.isEmpty() || categories.isEmpty()) return emptyMap()
            val ids = lineIds.joinToString(",")
            val names = (KINDS + ConnectionType.REFERENCE).joinToString { "'${it.name}'" }
            val kinds = "SELECT id FROM connection_type WHERE name IN ($names)"
            val books = "SELECT id FROM book WHERE categoryId IN (${categories.joinToString(",")})"
            // The other line's book is told from the link itself, before its line is read, and by no index of its own
            val sql =
                """
                SELECT k.sourceLineId, ol.id, ol.bookId, ol.lineIndex, ol.heRef, ol.content
                FROM link k
                JOIN line ol ON ol.id = k.targetLineId
                WHERE k.sourceLineId IN ($ids) AND k.connectionTypeId IN ($kinds) AND +k.targetBookId IN ($books)
                UNION ALL
                SELECT k.targetLineId, ol.id, ol.bookId, ol.lineIndex, ol.heRef, ol.content
                FROM link k
                JOIN line ol ON ol.id = k.sourceLineId
                WHERE k.targetLineId IN ($ids) AND k.connectionTypeId IN ($kinds) AND +k.sourceBookId IN ($books)
                """.trimIndent()
            val found = LinkedHashMap<Long, MutableList<Line>>()
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    while (rows.next()) {
                        val lines = found.getOrPut(rows.getLong(1)) { ArrayList(2) }
                        val lineId = rows.getLong(2)
                        if (lines.any { it.id == lineId }) continue
                        lines +=
                            Line(
                                id = lineId,
                                bookId = rows.getLong(3),
                                lineIndex = rows.getInt(4),
                                content = rows.getString(6),
                                heRef = rows.getString(5),
                            )
                    }
                }
            }
            return found
        }

        /** [LibraryLinks.lineIdsBefore] on [connection]. */
        internal fun idsBefore(
            connection: Connection,
            line: Line,
            count: Int,
        ): List<Long> {
            if (line.lineIndex <= 0 || count <= 0) return emptyList()
            val sql = "SELECT id FROM line WHERE bookId = ? AND lineIndex >= ? AND lineIndex < ? ORDER BY lineIndex"
            return connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, line.bookId)
                statement.setInt(2, line.lineIndex - count)
                statement.setInt(3, line.lineIndex)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
            }
        }

        /** [LibraryLinks.startsIn] on [connection]. */
        internal fun linkedStarts(
            connection: Connection,
            bookId: Long,
            lineIds: Collection<Long>,
            chars: Int,
        ): Map<Long, List<Pair<Long, String>>> {
            if (lineIds.isEmpty()) return emptyMap()
            val sql =
                """
                SELECT k.sourceLineId, ol.id, substr(ol.content, 1, $chars)
                FROM link k
                JOIN line ol ON ol.id = k.targetLineId
                WHERE k.sourceLineId IN (${lineIds.joinToString(",")}) AND +k.targetBookId = $bookId
                """.trimIndent()
            val found = HashMap<Long, MutableList<Pair<Long, String>>>()
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    while (rows.next()) found.getOrPut(rows.getLong(1)) { ArrayList(2) } += rows.getLong(2) to rows.getString(3)
                }
            }
            return found
        }

        /** [LibraryLinks.notedBooks] on [connection]. */
        internal fun booksWithNotes(connection: Connection): Map<Long, Long> {
            val books = HashMap<Long, Long>()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT b.id, n.id FROM book b JOIN book n ON n.title = '$NOTES_ON' || b.title").use { rows ->
                    while (rows.next()) books[rows.getLong(1)] = rows.getLong(2)
                }
            }
            return books
        }

        /** [LibraryLinks.booksOf] on [connection]. */
        internal fun booksIn(
            connection: Connection,
            categories: Collection<Long>,
        ): Set<Long> {
            if (categories.isEmpty()) return emptySet()
            val books = HashSet<Long>()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM book WHERE categoryId IN (${categories.joinToString(",")})").use { rows ->
                    while (rows.next()) books += rows.getLong(1)
                }
            }
            return books
        }

        /** [LibraryLinks.any] on [connection]. */
        internal fun anyLinked(
            connection: Connection,
            lineIds: Collection<Long>,
        ): Boolean {
            if (lineIds.isEmpty()) return false
            val ids = lineIds.joinToString(",")
            // A link of a line to itself or to another of the lines is no place to list
            val sql =
                """
                SELECT EXISTS (
                    SELECT 1 FROM link k
                    WHERE k.sourceLineId IN ($ids) AND k.connectionTypeId IN ($KIND_IDS) AND k.targetLineId NOT IN ($ids)
                ) OR EXISTS (
                    SELECT 1 FROM link k
                    WHERE k.targetLineId IN ($ids) AND k.connectionTypeId IN ($KIND_IDS) AND k.sourceLineId NOT IN ($ids)
                )
                """.trimIndent()
            return connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows -> rows.next() && rows.getBoolean(1) }
            }
        }
    }
}

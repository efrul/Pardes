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

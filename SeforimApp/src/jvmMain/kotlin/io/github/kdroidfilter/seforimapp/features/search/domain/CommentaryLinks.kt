package io.github.kdroidfilter.seforimapp.features.search.domain

import dev.zacsweers.metro.Inject
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.logger.warnln
import java.sql.DriverManager

/**
 * The library's commentaries and the books they comment on ([BaseBooks.COMMENTARY_PAIRS_SQL]), read from its database
 * on a read-only connection of its own (as LibraryLines reads it): the repository has no query for them.
 */
@Inject
class CommentaryLinks(
    private val databasePathProvider: DatabasePathProvider,
) {
    /** {commentary: the books it comments on}; empty when the database can't be read. */
    fun read(): Map<Long, LongArray> =
        runCatching {
            val pairs = HashMap<Long, MutableList<Long>>()
            DriverManager.getConnection("jdbc:sqlite:file:${databasePathProvider.get()}?mode=ro").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(BaseBooks.COMMENTARY_PAIRS_SQL).use { rows ->
                        while (rows.next()) pairs.getOrPut(rows.getLong(1)) { ArrayList(2) } += rows.getLong(2)
                    }
                }
            }
            pairs.mapValues { it.value.toLongArray() }
        }.getOrElse {
            warnln { "[CommentaryLinks] the library's commentaries could not be read: $it" }
            emptyMap()
        }

    /**
     * {commentary: {a book it comments on: its links to it}}, the pairs of [read] with how much of the commentary is on
     * each book ([CommentaryGroups]); read as fast as they are. Empty when the database can't be read.
     */
    fun counted(): Map<Long, Map<Long, Int>> =
        runCatching {
            val counts = HashMap<Long, MutableMap<Long, Int>>()
            DriverManager.getConnection("jdbc:sqlite:file:${databasePathProvider.get()}?mode=ro").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(COUNTED_PAIRS_SQL).use { rows ->
                        while (rows.next()) counts.getOrPut(rows.getLong(1)) { HashMap(2) }[rows.getLong(2)] = rows.getInt(3)
                    }
                }
            }
            counts
        }.getOrElse {
            warnln { "[CommentaryLinks] the library's commentaries could not be counted: $it" }
            emptyMap()
        }

    private companion object {
        // BaseBooks.COMMENTARY_PAIRS_SQL's pairs, each with its links
        const val COUNTED_PAIRS_SQL =
            "SELECT l.targetBookId, l.sourceBookId, COUNT(*) FROM link l " +
                "JOIN connection_type ct ON ct.id = l.connectionTypeId " +
                "WHERE ct.name IN ('COMMENTARY', 'SUPER_COMMENTARY', 'TARGUM', 'PARSHANUT', 'DIBUR_HAMATCHIL') " +
                "AND l.sourceBookId != l.targetBookId GROUP BY l.targetBookId, l.sourceBookId"
    }
}

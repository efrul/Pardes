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
}

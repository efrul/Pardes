package io.github.kdroidfilter.seforimapp.features.sefaria

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/** A line of the library a Sefaria ref names: where it is, its HTML [content] and its book's [bookTitle]. */
@Immutable
data class SefariaLine(
    val id: Long,
    val bookId: Long,
    val lineIndex: Int,
    val heRef: String,
    val content: String,
    val bookTitle: String,
)

/**
 * The library's lines by their heRef, as Sefaria's data names them (SefariaData.kt). Read on a read-only connection of
 * its own, as LibraryLinks reads the library: the repository has no query by heRef.
 */
@Inject
class SefariaLines(
    private val databasePathProvider: DatabasePathProvider,
) {
    /** The lines of [heRefs] the library has, by heRef; empty when the library can't be read. */
    suspend fun linesOf(heRefs: Collection<String>): Map<String, SefariaLine> =
        read(emptyMap()) { connection -> byHeRef(connection, heRefs.distinct()) }

    /** The lines of book [bookId] from [first] to [last] (line indexes), in their order: a passage, to copy. */
    suspend fun range(
        bookId: Long,
        first: Int,
        last: Int,
    ): List<SefariaLine> = read(emptyList()) { connection -> between(connection, bookId, first, last) }

    private suspend fun <T> read(
        otherwise: T,
        query: (Connection) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            runCatching { readOnly(databasePathProvider.get()).use(query) }
                .getOrElse {
                    warnln { "[SefariaLines] the library's lines could not be read: $it" }
                    otherwise
                }
        }

    internal companion object {
        // SQLite's limit of a statement's variables is 999 in older builds
        private const val CHUNK = 500

        private const val COLUMNS =
            "SELECT l.id, l.bookId, l.lineIndex, l.heRef, l.content, b.title FROM line l JOIN book b ON b.id = l.bookId"
        private const val RANGE = "$COLUMNS WHERE l.bookId = ? AND l.lineIndex BETWEEN ? AND ? ORDER BY l.lineIndex"

        // The library read only, by a property and not a file: URI, which a '#' or a '%' in the folder's name would break
        private fun readOnly(path: String): Connection =
            DriverManager.getConnection("jdbc:sqlite:$path", Properties().apply { setProperty("open_mode", "1") })

        /** [SefariaLines.linesOf] on [connection]: a heRef the library has twice gives its first line. */
        fun byHeRef(
            connection: Connection,
            heRefs: List<String>,
        ): Map<String, SefariaLine> {
            val found = HashMap<String, SefariaLine>()
            heRefs.chunked(CHUNK).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                connection.prepareStatement("$COLUMNS WHERE l.heRef IN ($marks) ORDER BY l.id").use { statement ->
                    chunk.forEachIndexed { i, heRef -> statement.setString(i + 1, heRef) }
                    statement.executeQuery().use { rs ->
                        while (rs.next()) found.putIfAbsent(rs.getString(4), rs.line())
                    }
                }
            }
            return found
        }

        /** [SefariaLines.range] on [connection]. */
        fun between(
            connection: Connection,
            bookId: Long,
            first: Int,
            last: Int,
        ): List<SefariaLine> =
            connection.prepareStatement(RANGE).use { statement ->
                statement.setLong(1, bookId)
                statement.setInt(2, first)
                statement.setInt(3, last)
                statement.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.line()) } }
            }

        private fun java.sql.ResultSet.line() =
            SefariaLine(getLong(1), getLong(2), getInt(3), getString(4).orEmpty(), getString(5).orEmpty(), getString(6).orEmpty())
    }
}

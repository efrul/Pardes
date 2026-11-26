package io.github.kdroidfilter.seforimapp.framework.search

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.features.search.domain.CommonSourcePick
import io.github.kdroidfilter.seforimapp.features.search.domain.CommonSources
import io.github.kdroidfilter.seforimapp.features.search.domain.LinkEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/** A source of the line of common sources, as the screen shows it. */
@Immutable
data class CommonSource(
    val lineId: Long,
    val bookId: Long,
    val label: String,
    val books: Int,
)

/**
 * Pardes: the line of common sources read from the library (docs/COMMON_SOURCES_PLAN.md): the page's links from the
 * `link` table alone, both directions, on a read-only connection of its own, opened for each line and closed after it.
 * Not through SeforimRepository: its queries keep a part of the links only.
 */
class CommonSourcesQueries(
    private val path: () -> String,
    private val catalogBaseBooks: () -> Set<Long>?,
) {
    @Volatile
    private var baseBooks: Set<Long>? = null

    /** The line of [page] ((lineId, bookId), the engine's order); none for a page of fewer than three books. */
    suspend fun line(
        page: List<Pair<Long, Long>>,
        baseOnly: Boolean,
    ): List<CommonSource> =
        withContext(Dispatchers.IO) {
            if (page.distinctBy { it.second }.size < CommonSources.MIN_BOOKS) return@withContext emptyList()
            open(path()).use { connection ->
                val picks = CommonSources.pick(page, links(connection, page.map { it.first }), baseBooks(connection), baseOnly)
                labels(connection, picks)
            }
        }

    // SQLITE_OPEN_READONLY, and the path as a plain file name: a "?mode=ro" is read as part of it
    internal fun open(path: String): Connection =
        DriverManager.getConnection("jdbc:sqlite:$path", Properties().apply { setProperty("open_mode", "1") })

    private fun links(
        connection: Connection,
        lines: List<Long>,
    ): List<LinkEnd> {
        val marks = lines.joinToString(",") { "?" }
        val ends = ArrayList<LinkEnd>()
        for (sql in listOf(FROM_SOURCE_SQL, FROM_TARGET_SQL)) {
            connection.prepareStatement(sql.replace("?", marks)).use { statement ->
                lines.forEachIndexed { i, id -> statement.setLong(i + 1, id) }
                statement.executeQuery().use { rows ->
                    while (rows.next()) ends += LinkEnd(rows.getLong(1), rows.getLong(2), rows.getLong(3))
                }
            }
        }
        return ends
    }

    private fun baseBooks(connection: Connection): Set<Long> =
        baseBooks ?: (
            catalogBaseBooks() ?: connection.createStatement().use { statement ->
                statement.executeQuery(BASE_BOOKS_SQL).use { rows -> buildSet { while (rows.next()) add(rows.getLong(1)) } }
            }
        ).also { baseBooks = it }

    // Each pick with its place as the library names it (heRef, the book's title when it has none), in the picks' order
    private fun labels(
        connection: Connection,
        picks: List<CommonSourcePick>,
    ): List<CommonSource> {
        if (picks.isEmpty()) return emptyList()
        val names = HashMap<Long, String>()
        connection.prepareStatement(LABELS_SQL.replace("?", picks.joinToString(",") { "?" })).use { statement ->
            picks.forEachIndexed { i, pick -> statement.setLong(i + 1, pick.lineId) }
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val heRef = rows.getString(2)
                    names[rows.getLong(1)] = if (heRef.isNullOrBlank()) rows.getString(3) else heRef.replace(SPACES, " ").trim()
                }
            }
        }
        return picks.mapNotNull { pick -> names[pick.lineId]?.let { CommonSource(pick.lineId, pick.bookId, it, pick.books) } }
    }

    private companion object {
        const val FROM_SOURCE_SQL = "SELECT sourceLineId, targetLineId, targetBookId FROM link WHERE sourceLineId IN (?)"
        const val FROM_TARGET_SQL = "SELECT targetLineId, sourceLineId, sourceBookId FROM link WHERE targetLineId IN (?)"
        const val BASE_BOOKS_SQL = "SELECT id FROM book WHERE isBaseBook = 1"
        const val LABELS_SQL = "SELECT l.id, l.heRef, b.title FROM line l JOIN book b ON b.id = l.bookId WHERE l.id IN (?)"
        val SPACES = Regex("\\s+")
    }
}

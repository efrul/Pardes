package io.github.kdroidfilter.seforimapp.framework.search

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.features.search.domain.CommonSourcePick
import io.github.kdroidfilter.seforimapp.features.search.domain.CommonSources
import io.github.kdroidfilter.seforimapp.features.search.domain.LinkEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * A source the first results link to, as the area beside them shows it: its place, its [books] and its [text], where it
 * is to show it in the preview ([bookTitle], [lineIndex]), and the results that link to it ([from], in their order).
 */
@Immutable
data class CommonSource(
    val lineId: Long,
    val bookId: Long,
    val label: String,
    val books: Int,
    val text: String = "",
    val bookTitle: String = "",
    val lineIndex: Int = 0,
    val from: List<Long> = emptyList(),
)

/**
 * Pardes: the sources the first results link to, read from the library for the area beside them (docs/COMMON_SOURCES_PLAN.md):
 * the page's links from the `link` table alone, both directions, on a read-only connection of its own, opened for each
 * read and closed after it.
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

    // Each pick with its place as the library names it (heRef, the book's title when it has none), its text and where it
    // is, in the picks' order
    private fun labels(
        connection: Connection,
        picks: List<CommonSourcePick>,
    ): List<CommonSource> {
        if (picks.isEmpty()) return emptyList()
        val found = HashMap<Long, CommonSource>()
        connection.prepareStatement(LABELS_SQL.replace("?", picks.joinToString(",") { "?" })).use { statement ->
            picks.forEachIndexed { i, pick -> statement.setLong(i + 1, pick.lineId) }
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val heRef = rows.getString(2)
                    val title = rows.getString(3).orEmpty()
                    val label = if (heRef.isNullOrBlank()) title else heRef.replace(SPACES, " ").trim()
                    found[rows.getLong(1)] =
                        CommonSource(rows.getLong(1), 0, label, 0, plain(rows.getString(4).orEmpty()), title, rows.getInt(5))
                }
            }
        }
        return picks.mapNotNull { pick -> found[pick.lineId]?.copy(bookId = pick.bookId, books = pick.books, from = pick.from) }
    }

    // A line's text on one row: no tags, no cantillation, no section marks, no verse number before it
    private fun plain(html: String): String =
        Jsoup
            .parse(html)
            .text()
            .replace(MARKS, "")
            .replace(VERSE_NUMBER, "")
            .replace(SPACES, " ")
            .trim()

    private companion object {
        const val FROM_SOURCE_SQL = "SELECT sourceLineId, targetLineId, targetBookId FROM link WHERE sourceLineId IN (?)"
        const val FROM_TARGET_SQL = "SELECT targetLineId, sourceLineId, sourceBookId FROM link WHERE targetLineId IN (?)"
        const val BASE_BOOKS_SQL = "SELECT id FROM book WHERE isBaseBook = 1"
        const val LABELS_SQL =
            "SELECT l.id, l.heRef, b.title, l.content, l.lineIndex FROM line l JOIN book b ON b.id = l.bookId WHERE l.id IN (?)"
        val SPACES = Regex("\\s+")
        val MARKS = Regex("[\\u0591-\\u05AF\\u05C0]|\\{[ספ]\\}")
        val VERSE_NUMBER = Regex("^\\s*\\([^)]{1,4}\\)\\s*")
    }
}

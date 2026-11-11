package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.github.kdroidfilter.seforimapp.branding.BrandLibraries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.Properties

/*
 * The searches made in other apps on this computer, read from the apps' own files as they keep them and never written
 * to. The user may send them with Pardes's own ([OtherApp], [FeedbackChoice.others]); an app's files are read only
 * once the user said so, and until then only looked for ([ZayitSearches.found], [OtzariaSearches.found]). What each
 * app keeps, and where, is as efrul/semantic-query-telemetry reads it (its SeforimAppReader and OtzariaReader).
 */

/**
 * Zayit's searches: its user database's visit_history, the table Pardes has from it, a row a search with its scope, how
 * many times it was made and when last. [databases] are the copies Zayit may keep ([BrandLibraries]).
 */
class ZayitSearches(
    private val databases: () -> List<File> = BrandLibraries::otherAppUserDatabases,
) {
    /** Whether Zayit's data is on this computer. Only looks for the file. */
    fun found(): Boolean = databases().any { it.isFile }

    /** The searches made after [since] (epoch millis); a search kept in two copies comes once, as last made. */
    fun since(since: Long): List<SearchRow> {
        val byKey = LinkedHashMap<String, SearchRow>()
        databases().filter { it.isFile }.forEach { file ->
            runCatching { readOnly(file).use { searches(it, since) } }.getOrDefault(emptyList()).forEach { (key, row) ->
                if ((byKey[key]?.lastAt ?: Long.MIN_VALUE) < row.lastAt) byKey[key] = row
            }
        }
        return byKey.values.sortedBy { it.lastAt }
    }

    private fun searches(
        connection: Connection,
        since: Long,
    ): List<Pair<String, SearchRow>> {
        val columns = columnsOf(connection, "visit_history")
        if ("searchQuery" !in columns || "visitedAt" !in columns) return emptyList()

        // A Zayit before a column was added has none of it
        fun column(
            name: String,
            otherwise: String,
        ) = if (name in columns) name else "$otherwise AS $name"
        val sql =
            "SELECT key, searchQuery, visitedAt, ${column("visitCount", "1")}, ${column("searchCategoryId", "NULL")}, " +
                "${column("searchBookId", "NULL")}, ${column("searchTocId", "NULL")}, ${column("searchExtended", "0")} " +
                "FROM visit_history WHERE kind = 'search' AND searchQuery IS NOT NULL AND visitedAt > ? ORDER BY visitedAt"
        val found = ArrayList<Pair<String, SearchRow>>()
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, since)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val key = rows.getString("key").orEmpty()
                    val query = sendable(rows.getString("searchQuery")?.trim()) ?: continue
                    found +=
                        key to
                        SearchRow(
                            query = query,
                            categoryId = rows.getLong("searchCategoryId").takeUnless { rows.wasNull() },
                            bookId = rows.getLong("searchBookId").takeUnless { rows.wasNull() },
                            tocId = rows.getLong("searchTocId").takeUnless { rows.wasNull() },
                            extended = rows.getLong("searchExtended") != 0L,
                            count = rows.getLong("visitCount").coerceAtLeast(1),
                            lastAt = rows.getLong("visitedAt"),
                            source = OtherApp.ZAYIT.source,
                            scope = authorScope(key),
                        )
                }
            }
        }
        return found
    }

    // An author's scope lives only in the key: search:a<id>[x]:<query>
    private fun authorScope(key: String): String? {
        if (!key.startsWith("search:a")) return null
        val id = key.removePrefix("search:a").substringBefore(':').trimEnd('x')
        return id.toLongOrNull()?.let { "$AUTHOR_SCOPE $it" }
    }

    private companion object {
        // As the telemetry tool says an author's scope, which collect.py reads
        const val AUTHOR_SCOPE = "מחבר"
    }
}

/**
 * Otzaria's searches (otzaria/otzaria 0.9.98 and later), under its data folders ([roots]):
 * - its history: user_state.db, table lists, the row box 'history' key 'history', a JSON list of bookmarks; one with
 *   isSearch is a search, its words in book.title, Otzaria's title for it in ref, its scope in searchScopeFacets and
 *   searchFilterFacets. Otzaria keeps no time and no count for a search;
 * - its smart search's events, where the user agreed to them in Otzaria: search_feedback/\*.jsonl, a context line and
 *   then events (search, vote) with eventId, clientTime and searchSessionId. Otzaria sends them to its own server
 *   within minutes and deletes them, so few are here at any time. A vote is read only with the search of its session.
 */
class OtzariaSearches(
    private val roots: () -> List<File> = ::otzariaDataRoots,
) {
    /** A row with the key that tells it from Otzaria's others, as the ledger keeps it ([OtherAppLedger]). */
    data class Keyed<T>(
        val key: String,
        val row: T,
    )

    /** What Otzaria holds now. [history] is null when none of its history could be read: what was there is not gone. */
    data class Held(
        val history: List<Keyed<SearchRow>>?,
        val smart: List<Keyed<SearchRow>>,
        val votes: List<Keyed<GradeRow>>,
    )

    /** Whether Otzaria's data is on this computer. Only looks for the files. */
    fun found(): Boolean = databases().isNotEmpty() || roots().any { File(it, FEEDBACK_DIR).isDirectory }

    fun read(): Held {
        var history: LinkedHashMap<String, Keyed<SearchRow>>? = null
        databases().forEach { file ->
            val searches = runCatching { readOnly(file).use(::history) }.getOrNull() ?: return@forEach
            val all = history ?: LinkedHashMap<String, Keyed<SearchRow>>().also { history = it }
            searches.forEach { all.putIfAbsent(it.key, it) }
        }
        val sessions = LinkedHashMap<String, JsonObject>()
        val votes = ArrayList<JsonObject>()
        roots().map { File(it, FEEDBACK_DIR) }.filter { it.isDirectory }.forEach { events(it, sessions, votes) }
        return Held(history?.values?.toList(), smartSearches(sessions), grades(votes, sessions))
    }

    // user_state.db under a data folder, or beside the library Otzaria points at
    private fun databases(): List<File> =
        roots()
            .flatMap { root ->
                val besideLibrary = pointer(File(root, "library_path.txt"))?.let { File(it).parentFile }?.let { File(it, "databases") }
                listOfNotNull(File(root, "databases"), root, besideLibrary)
            }.map { File(it, "user_state.db") }
            .filter { it.isFile }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }

    // A path in one of Otzaria's pointer files (UTF-8, with a byte order mark)
    private fun pointer(file: File): String? {
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: return null
        return text.trim(BYTE_ORDER_MARK, ' ', '\t', '\r', '\n', '"').ifEmpty { null }
    }

    private fun history(connection: Connection): List<Keyed<SearchRow>>? {
        if ("payload_json" !in columnsOf(connection, "lists")) return null
        val found = ArrayList<Keyed<SearchRow>>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT payload_json FROM lists WHERE box = 'history' AND key = 'history'").use { rows ->
                while (rows.next()) {
                    val list = runCatching { json.parseToJsonElement(rows.getString(1).orEmpty()) }.getOrNull() as? JsonArray ?: continue
                    list.mapNotNullTo(found) { historySearch(it as? JsonObject) }
                }
            }
        }
        return found
    }

    private fun historySearch(entry: JsonObject?): Keyed<SearchRow>? {
        if (entry == null || entry.boolean("isSearch") != true) return null
        val words = sendable(entry.obj("book")?.text("title")) ?: return null
        val title = entry.text("ref")?.takeIf { it != words && it.length <= MAX_QUERY_CHARS }
        val scope = facets(entry["searchScopeFacets"])
        val filter = facets(entry["searchFilterFacets"])?.takeIf { it != scope }
        val where = listOfNotNull(scope, filter?.let { "$FILTER: $it" }).joinToString("; ").ifEmpty { null }?.take(MAX_SCOPE_CHARS)
        // Its time is when Pardes first found it, which the ledger says
        val row = SearchRow(query = words, lastAt = 0, source = OtherApp.OTZARIA.source, scope = where, ref = title, timeUnknown = true)
        return Keyed(listOf("history", words, where.orEmpty(), title.orEmpty()).joinToString(SEPARATOR), row)
    }

    // Otzaria's facets ("/תלמוד בבלי/סדר נזיקין") as text; null for the whole library
    private fun facets(facets: JsonElement?): String? =
        (facets as? JsonArray)
            ?.mapNotNull {
                (it as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.trim('/')
                    ?.replace("/", " › ")
                    ?.ifEmpty { null }
            }?.distinct()
            ?.joinToString(", ")
            ?.take(MAX_SCOPE_CHARS)
            ?.ifEmpty { null }

    private fun events(
        folder: File,
        sessions: MutableMap<String, JsonObject>,
        votes: MutableList<JsonObject>,
    ) {
        val seen = HashSet<String>()
        folder.listFiles { file -> file.isFile && file.extension == "jsonl" }.orEmpty().sortedBy { it.name }.forEach { file ->
            // Otzaria may be writing, sending or deleting it now
            val lines = runCatching { file.readLines() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val context = parsed(lines.first()) ?: return@forEach
            // Not Otzaria's, or a debug build's that it never sends
            if (context.text("app") != "otzaria" || context.boolean("localOnly") == true) return@forEach
            lines.drop(1).forEach { line ->
                // A last line still being written does not parse
                val event = parsed(line) ?: return@forEach
                val id = event.text("eventId") ?: return@forEach
                val session = event.text("searchSessionId") ?: return@forEach
                if (!seen.add(id)) return@forEach
                when (event.text("type")) {
                    "search" -> sessions[session] = event
                    "vote" -> votes += event
                }
            }
        }
    }

    private fun smartSearches(sessions: Map<String, JsonObject>): List<Keyed<SearchRow>> =
        sessions.mapNotNull { (session, event) ->
            val words = sendable(event.text("query")) ?: return@mapNotNull null
            val at = time(event.text("clientTime")) ?: return@mapNotNull null
            val scope = facets(event.obj("params")?.obj("scope")?.get("facets"))
            Keyed(
                "search$SEPARATOR$session",
                SearchRow(query = words, lastAt = at, source = OtherApp.OTZARIA.source, scope = scope, ref = SMART_SEARCH),
            )
        }

    private fun grades(
        votes: List<JsonObject>,
        sessions: Map<String, JsonObject>,
    ): List<Keyed<GradeRow>> {
        // The last vote on a result stands; one cleared takes it back
        val last = LinkedHashMap<String, JsonObject>()
        votes.sortedBy { time(it.text("clientTime")) ?: 0 }.forEach { vote ->
            val result = vote.obj("result")
            val of = listOf(vote.text("searchSessionId"), result?.text("title"), result?.text("reference"), result?.long("segment"))
            last[of.joinToString(SEPARATOR) { it?.toString().orEmpty() }] = vote
        }
        return last.mapNotNull { (of, vote) ->
            val grade =
                when (vote.text("vote")) {
                    "like" -> RELEVANT
                    "dislike" -> NOT_RELEVANT
                    else -> return@mapNotNull null
                }
            val session = vote.text("searchSessionId") ?: return@mapNotNull null
            // Without its search the vote's words are unknown: it is not sent
            val search = sessions[session] ?: return@mapNotNull null
            val words = sendable(search.text("query")) ?: return@mapNotNull null
            val at = time(vote.text("clientTime")) ?: return@mapNotNull null
            val result = vote.obj("result")
            Keyed(
                "vote$SEPARATOR$of$SEPARATOR$grade",
                GradeRow(
                    query = words,
                    search = "otzaria:smart:$session",
                    lineId = 0,
                    bookId = 0,
                    grade = grade,
                    // Otzaria counts a result's place from 1
                    rank = ((result?.long("rank") ?: 1) - 1).coerceAtLeast(0).toInt(),
                    model = SMART_MODEL,
                    gradedAt = at,
                    source = OtherApp.OTZARIA.source,
                    scope = facets(search.obj("params")?.obj("scope")?.get("facets")),
                    bookTitle = result?.text("title")?.take(MAX_TITLE_CHARS),
                    heRef = result?.text("reference")?.take(MAX_TITLE_CHARS),
                    docId = result?.long("segment")?.toString(),
                ),
            )
        }
    }

    private fun parsed(line: String): JsonObject? = runCatching { json.parseToJsonElement(line) }.getOrNull() as? JsonObject

    private fun time(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }?.takeIf { it > 0 }

    private companion object {
        const val FEEDBACK_DIR = "search_feedback"
        const val BYTE_ORDER_MARK = '\uFEFF'
        const val SEPARATOR = "\u001f"

        // As the telemetry tool says them, which collect.py reads
        const val FILTER = "סינון"
        const val SMART_SEARCH = "חיפוש חכם"
        const val SMART_MODEL = "otzaria-smart-search"

        // The grades' scale (SearchFeedback.sq): Otzaria's like and dislike
        const val RELEVANT = 2
        const val NOT_RELEVANT = 0

        // The service's limits for a scope and for a title (feedback-service/src/package.js)
        const val MAX_SCOPE_CHARS = 500
        const val MAX_TITLE_CHARS = 300

        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Otzaria's data folder on this computer, as Otzaria chooses it: `%APPDATA%\otzaria` on Windows, `otzaria` under
 * Application Support on a Mac and under `~/.local/share` on Linux. A portable copy keeps it beside itself, where
 * this does not look.
 */
fun otzariaDataRoots(): List<File> {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home").orEmpty()
    val root =
        when {
            os.contains("win") -> System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { File(it, "otzaria") }
            os.contains("mac") -> File(home, "Library/Application Support/otzaria")
            else -> File(home, ".local/share/otzaria")
        }
    return listOfNotNull(root)
}

// Another app's database, read only and never created; by a property and not a file: URI, which a '#' or a '%' in the
// folder's name would break (LibraryLinks)
private fun readOnly(file: File): Connection =
    DriverManager.getConnection("jdbc:sqlite:${file.path}", Properties().apply { setProperty("open_mode", "1") })

private fun columnsOf(
    connection: Connection,
    table: String,
): Set<String> =
    connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA table_info($table)").use { rows ->
            buildSet { while (rows.next()) add(rows.getString("name")) }
        }
    }

private fun JsonObject.text(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.takeIf {
            it.isString
        }?.content
        ?.trim()
        ?.ifEmpty { null }

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

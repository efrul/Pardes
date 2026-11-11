package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlinx.coroutines.runBlocking
import java.io.File
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The searches of the other apps on this computer: read from files as Zayit and Otzaria keep them, sent only when the
 * user says, each once.
 */
class OtherAppSearchesTest {
    private val root = createTempDirectory("other-apps").toFile()

    private fun sql(
        file: File,
        vararg statements: String,
    ) {
        file.parentFile.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection.createStatement().use { statement -> statements.forEach(statement::execute) }
        }
    }

    // ---- Zayit

    private val zayitDatabase = File(root, "zayit/databases/settings/user_settings.db")

    // Zayit's history table, as its VisitHistory.sq makes it
    private val visitHistory =
        "CREATE TABLE visit_history (key TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, bookId INTEGER, searchQuery TEXT, " +
            "title TEXT NOT NULL, visitedAt INTEGER NOT NULL, visitCount INTEGER NOT NULL DEFAULT 1, tocEntryId INTEGER, " +
            "lineId INTEGER, searchCategoryId INTEGER, searchBookId INTEGER, searchTocId INTEGER, " +
            "searchExtended INTEGER NOT NULL DEFAULT 0)"

    private fun visit(
        key: String,
        query: String?,
        at: Long,
        count: Int = 1,
        book: Long? = null,
        extended: Int = 0,
        kind: String = "search",
    ) = "INSERT INTO visit_history(key, kind, searchQuery, title, visitedAt, visitCount, searchBookId, searchExtended) " +
        "VALUES ('$key', '$kind', ${query?.let { "'$it'" } ?: "NULL"}, 'x', $at, $count, ${book ?: "NULL"}, $extended)"

    @Test
    fun `Zayit's searches are its history's, with their scope, their count and when last made`() {
        sql(
            zayitDatabase,
            visitHistory,
            visit("search:g:שנים אוחזין", "שנים אוחזין", at = 100, count = 3),
            visit("search:b7x:מאימתי", " מאימתי ", at = 200, book = 7, extended = 1),
            visit("search:a15:היזק ראיה", "היזק ראיה", at = 300),
            // A book opened is no search, and neither is a search without words
            visit("book:7", null, at = 400, kind = "book"),
            visit("search:g:", "  ", at = 500),
        )
        val zayit = ZayitSearches { listOf(zayitDatabase, File(root, "no such copy.db")) }

        assertTrue(zayit.found())
        val rows = zayit.since(0)
        assertEquals(listOf("שנים אוחזין", "מאימתי", "היזק ראיה"), rows.map { it.query })
        assertEquals(setOf("zayit"), rows.map { it.source }.toSet())
        assertEquals(listOf(3L, 1L, 1L), rows.map { it.count })
        assertEquals(listOf(100L, 200L, 300L), rows.map { it.lastAt })
        assertEquals(listOf(null, 7L, null), rows.map { it.bookId })
        assertEquals(listOf(false, true, false), rows.map { it.extended })
        // An author's scope is in the key alone
        assertEquals(listOf(null, null, "מחבר 15"), rows.map { it.scope })
        // What was made since the last package
        assertEquals(listOf("היזק ראיה"), zayit.since(200).map { it.query })
    }

    @Test
    fun `a search kept in two copies of Zayit's data comes once, as last made, and an old Zayit's table is read too`() {
        val store = File(root, "store/user_settings.db")
        sql(zayitDatabase, visitHistory, visit("search:g:שנים אוחזין", "שנים אוחזין", at = 100, count = 2))
        // Before the scope's columns and the count
        sql(
            store,
            "CREATE TABLE visit_history (key TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, searchQuery TEXT, visitedAt INTEGER NOT NULL)",
            "INSERT INTO visit_history VALUES ('search:g:שנים אוחזין', 'search', 'שנים אוחזין', 900)",
            "INSERT INTO visit_history VALUES ('search:g:אחר', 'search', 'אחר', 50)",
        )

        val rows = ZayitSearches { listOf(zayitDatabase, store) }.since(0)

        assertEquals(listOf("אחר" to 50L, "שנים אוחזין" to 900L), rows.map { it.query to it.lastAt })
        assertEquals(listOf(1L, 1L), rows.map { it.count })
    }

    @Test
    fun `no Zayit here, or a file that is no database of it, gives nothing`() {
        assertFalse(ZayitSearches { listOf(File(root, "nowhere.db")) }.found())
        assertEquals(emptyList(), ZayitSearches { listOf(File(root, "nowhere.db")) }.since(0))

        val other = File(root, "other.db").apply { writeText("not a database") }
        assertEquals(emptyList(), ZayitSearches { listOf(other) }.since(0))
        val empty = File(root, "empty/user_settings.db")
        sql(empty, "CREATE TABLE something (x INTEGER)")
        assertEquals(emptyList(), ZayitSearches { listOf(empty) }.since(0))
    }

    // ---- Otzaria

    private val otzariaRoot = File(root, "otzaria")
    private val otzariaDatabase = File(otzariaRoot, "databases/user_state.db")

    private fun history(vararg entries: String) {
        otzariaDatabase.delete()
        sql(
            otzariaDatabase,
            "CREATE TABLE lists (box TEXT, key TEXT, payload_json TEXT)",
            "INSERT INTO lists VALUES ('history', 'history', '[${entries.joinToString(",")}]')",
            // Other lists are the window's state, not searches
            "INSERT INTO lists VALUES ('tabs', 'tabs', '[{\"isSearch\":true,\"book\":{\"title\":\"לשונית\"}}]')",
        )
    }

    private fun searched(
        words: String,
        title: String = words,
        scope: String = "null",
    ) = """{"isSearch":true,"book":{"title":"$words"},"ref":"$title","searchScopeFacets":$scope}"""

    private fun events(
        name: String,
        vararg lines: String,
    ) = File(otzariaRoot, "search_feedback/$name").apply {
        parentFile.mkdirs()
        writeText(lines.joinToString("\n"))
    }

    private val otzaria = OtzariaSearches { listOf(otzariaRoot) }

    @Test
    fun `Otzaria's history gives its searches, with Otzaria's title and scope, and no time`() {
        history(
            searched("שור שנגח", title = "שור שנגח (מדויק)", scope = """["/תלמוד בבלי/סדר נזיקין","/משנה"]"""),
            searched("מאימתי"),
            """{"isSearch":false,"book":{"title":"ברכות"},"ref":"ברכות ב."}""",
            searched("  "),
        )

        assertTrue(otzaria.found())
        val rows =
            otzaria
                .read()
                .history
                .orEmpty()
                .map { it.row }
        assertEquals(listOf("שור שנגח", "מאימתי"), rows.map { it.query })
        assertEquals(listOf("שור שנגח (מדויק)", null), rows.map { it.ref })
        assertEquals(listOf("תלמוד בבלי › סדר נזיקין, משנה", null), rows.map { it.scope })
        assertEquals(setOf("otzaria" to true), rows.map { it.source to it.timeUnknown }.toSet())
    }

    @Test
    fun `Otzaria's smart search gives its searches and its votes, a vote only with the search of its session`() {
        events(
            "a.jsonl",
            """{"app":"otzaria","appVersion":"0.9.98"}""",
            """{"eventId":"e1","type":"search","searchSessionId":"s1","clientTime":"2026-11-01T10:00:00Z","query":"היזק ראיה",""" +
                """"params":{"scope":{"facets":["/תלמוד בבלי"]}}}""",
            """{"eventId":"e2","type":"vote","searchSessionId":"s1","clientTime":"2026-11-01T10:01:00Z","vote":"dislike",""" +
                """"result":{"title":"בבא בתרא","reference":"בבא בתרא ב.","segment":41,"rank":3}}""",
            // The vote changed: the last one stands
            """{"eventId":"e3","type":"vote","searchSessionId":"s1","clientTime":"2026-11-01T10:02:00Z","vote":"like",""" +
                """"result":{"title":"בבא בתרא","reference":"בבא בתרא ב.","segment":41,"rank":3}}""",
            // One taken back, one of a search not here, and the same event twice
            """{"eventId":"e4","type":"vote","searchSessionId":"s1","clientTime":"2026-11-01T10:03:00Z","vote":"cleared",""" +
                """"result":{"title":"בבא קמא","reference":"בבא קמא ב.","segment":7,"rank":1}}""",
            """{"eventId":"e5","type":"vote","searchSessionId":"gone","clientTime":"2026-11-01T10:04:00Z","vote":"like",""" +
                """"result":{"title":"שבת","reference":"שבת ב.","segment":9,"rank":1}}""",
            """{"eventId":"e1","type":"search","searchSessionId":"s1","clientTime":"2026-11-01T10:00:00Z","query":"היזק ראיה"}""",
            // A line Otzaria is still writing
            """{"eventId":"e6","type":"sea""",
        )
        // A debug build's events, which Otzaria never sends, and a file of something else
        events(
            "debug.jsonl",
            """{"app":"otzaria","localOnly":true}""",
            """{"eventId":"d1","type":"search","searchSessionId":"d","clientTime":"2026-11-01T10:00:00Z","query":"ניסוי"}""",
        )
        events(
            "other.jsonl",
            """{"app":"else"}""",
            """{"eventId":"o1","type":"search","searchSessionId":"o","clientTime":"2026-11-01T10:00:00Z","query":"אחר"}""",
        )

        assertTrue(otzaria.found())
        val held = otzaria.read()
        // No user_state.db here: the history was not read
        assertNull(held.history)
        val search = held.smart.single().row
        assertEquals(
            listOf("היזק ראיה", "otzaria", "תלמוד בבלי", "חיפוש חכם"),
            listOf(search.query, search.source, search.scope, search.ref),
        )
        assertEquals(1_793_527_200_000L, search.lastAt)
        val vote = held.votes.single().row
        assertEquals(
            listOf("היזק ראיה", "otzaria:smart:s1", "בבא בתרא", "בבא בתרא ב.", "41"),
            listOf(vote.query, vote.search, vote.bookTitle, vote.heRef, vote.docId),
        )
        // A like is relevant, at the place Otzaria counts from 1
        assertEquals(2 to 2, vote.grade to vote.rank)
        // None of the library's ids
        assertEquals(0L to 0L, vote.lineId to vote.bookId)
        assertEquals("otzaria" to "otzaria-smart-search", vote.source to vote.model)
    }

    @Test
    fun `no Otzaria here gives nothing`() {
        val none = OtzariaSearches { listOf(File(root, "nowhere")) }

        assertFalse(none.found())
        assertEquals(OtzariaSearches.Held(null, emptyList(), emptyList()), none.read())
    }

    // ---- What goes in a package

    private val database = feedbackDatabase()
    private var now = 1_000L

    private fun source() =
        FeedbackSource(database, ZayitSearches { listOf(zayitDatabase) }, otzaria, { now }) { File(root, "no daf reviews") }

    @Test
    fun `an app is read only when the user sends its searches`() {
        sql(zayitDatabase, visitHistory, visit("search:g:שנים אוחזין", "שנים אוחזין", at = 100))
        history(searched("מאימתי"))
        database.search("משלי", at = 50)
        val source = source()

        assertEquals(setOf(OtherApp.ZAYIT, OtherApp.OTZARIA), source.otherApps())
        val own = source.read(FeedbackSent(), others = emptySet())
        assertEquals(listOf("משלי"), own.searches.map { it.query })
        assertTrue(own.others.isEmpty())
        // Otzaria's history was not so much as counted
        assertEquals(emptyList(), database.searchFeedbackQueries.otherAppRows("otzaria").executeAsList())

        val withZayit = source.read(FeedbackSent(), others = setOf(OtherApp.ZAYIT))
        assertEquals(setOf(OtherApp.ZAYIT), withZayit.others.keys)
        assertEquals(
            listOf("שנים אוחזין"),
            withZayit.others
                .getValue(OtherApp.ZAYIT)
                .searches
                .map { it.query },
        )
    }

    @Test
    fun `Otzaria's searches go once each, at the time Pardes first found them, and again when made again`() {
        history(searched("מאימתי"), searched("שור שנגח"))
        val source = source()
        val both = setOf(OtherApp.OTZARIA)

        val first = source.read(FeedbackSent(), both).others.getValue(OtherApp.OTZARIA)
        assertEquals(listOf("מאימתי" to 1_000L, "שור שנגח" to 1_000L), first.searches.map { it.query to it.lastAt })
        // Read again before any package: still to send, found when they first were
        now = 2_000L
        assertEquals(
            listOf(1_000L, 1_000L),
            source
                .read(FeedbackSent(), both)
                .others
                .getValue(OtherApp.OTZARIA)
                .searches
                .map { it.lastAt },
        )

        source.sent(OtherApp.OTZARIA, first.keys)
        assertTrue(source.read(FeedbackSent(), both).others.isEmpty())
        // Everything from the start: what was sent too
        assertEquals(
            2,
            source
                .read(FeedbackSent(), both, everything = true)
                .others
                .getValue(OtherApp.OTZARIA)
                .searches.size,
        )

        // A new search, and one that left the history
        now = 3_000L
        history(searched("היזק ראיה"), searched("מאימתי"))
        val next = source.read(FeedbackSent(), both).others.getValue(OtherApp.OTZARIA)
        assertEquals(listOf("היזק ראיה" to 3_000L), next.searches.map { it.query to it.lastAt })
        source.sent(OtherApp.OTZARIA, next.keys)

        // Back in the history: it was made again
        now = 4_000L
        history(searched("שור שנגח"), searched("היזק ראיה"), searched("מאימתי"))
        assertEquals(
            listOf("שור שנגח" to 4_000L),
            source.read(FeedbackSent(), both).others.getValue(OtherApp.OTZARIA).searches.map {
                it.query to
                    it.lastAt
            },
        )
    }

    @Test
    fun `a history that cannot be read forgets nothing`() {
        history(searched("מאימתי"))
        val source = source()
        val otzariaOnly = setOf(OtherApp.OTZARIA)
        source.sent(
            OtherApp.OTZARIA,
            source
                .read(FeedbackSent(), otzariaOnly)
                .others
                .getValue(OtherApp.OTZARIA)
                .keys,
        )

        // Otzaria's database out of reach for a moment
        val away = File(otzariaRoot, "databases/away.db")
        otzariaDatabase.renameTo(away)
        assertTrue(source.read(FeedbackSent(), otzariaOnly).others.isEmpty())
        away.renameTo(otzariaDatabase)

        assertTrue(source.read(FeedbackSent(), otzariaOnly).others.isEmpty())
    }

    @Test
    fun `the package holds the apps the user sends, each row saying which, and says so when it may be published`() {
        sql(zayitDatabase, visitHistory, visit("search:g:שנים אוחזין", "שנים אוחזין", at = 100), visit("search:g:סוד", "סוד", at = 110))
        history(searched("מאימתי"))
        database.search("משלי", at = 50)
        val contents = source().read(FeedbackSent(), OtherApp.entries.toSet())

        val own = feedbackPackage(contents, FeedbackChoice(), "sender", "1.0.9", "v1")
        assertEquals(listOf("משלי" to null), own.searches.map { it.query to it.source })
        // As before the other apps: nothing of them, nor of publishing, in what is sent
        assertFalse(own.toJson().contains("publish") || own.toJson().contains("source"))

        val choice = FeedbackChoice(others = setOf(OtherApp.ZAYIT, OtherApp.OTZARIA), publish = true, leftOut = setOf("סוד"))
        assertEquals(listOf("משלי", "שנים אוחזין", "סוד", "מאימתי"), contents.queries(choice))
        val all = feedbackPackage(contents, choice, "sender", "1.0.9", "v1")
        assertEquals(listOf("משלי" to null, "שנים אוחזין" to "zayit", "מאימתי" to "otzaria"), all.searches.map { it.query to it.source })
        assertEquals(true, all.publish)
        assertTrue(all.toJson().contains(""""publish":true"""))
        // Sent up to the newest read, what the user left out too
        assertEquals(110L, contents.newest().zayit)
    }

    @Test
    fun `the sender sends an app's searches once the user chose it, and not twice`() =
        runBlocking {
            sql(zayitDatabase, visitHistory, visit("search:g:שנים אוחזין", "שנים אוחזין", at = 100))
            history(searched("מאימתי"))
            val settings = testAppSettings().apply { setDatabasePath(File(root, "library/seforim.db").path) }
            val service = ServiceDouble()
            val sender =
                FeedbackSender(settings, database, service.service).also {
                    it.zayit = ZayitSearches { listOf(zayitDatabase) }
                    it.otzaria = otzaria
                }
            database.search("משלי", at = 50)

            // Found, and not chosen: only Pardes's own
            assertEquals(setOf(OtherApp.ZAYIT, OtherApp.OTZARIA), sender.otherApps())
            assertEquals(emptySet(), sender.choice().others)
            assertEquals(1, sender.sendNew())
            assertEquals(
                listOf("משלי"),
                service.received
                    .single()
                    .searches
                    .map { it.query },
            )
            assertNull(service.received.single().publish)

            sender.keep(sender.choice().copy(others = setOf(OtherApp.ZAYIT, OtherApp.OTZARIA), publish = true))
            assertEquals(1, sender.sendNew())
            val sent = service.received.last()
            assertEquals(setOf("שנים אוחזין" to "zayit", "מאימתי" to "otzaria"), sent.searches.map { it.query to it.source }.toSet())
            assertEquals(true, sent.publish)

            // Nothing new in either app
            assertEquals(0, sender.sendNew())
            assertEquals(2, service.received.size)
        }
}

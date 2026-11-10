package io.github.kdroidfilter.seforimapp.features.settings.feedback

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafMark
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviewFile
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The feedback package: what the source reads since each kind's last package, and what the user's choice keeps. */
class FeedbackPackageTest {
    private val database = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))
    private val library = createTempDirectory("feedback-library").toFile()
    private val source = FeedbackSource(database) { FeedbackSource.dafFolderOf(library) }

    private fun grade(
        query: String,
        lineId: Long,
        grade: Long,
        at: Long,
    ) = database.searchFeedbackQueries.upsertGrade(
        searchKey = "search::$query",
        query = query,
        lineId = lineId,
        bookId = 7,
        grade = grade,
        rank = lineId - 10,
        searchCategoryId = null,
        searchBookId = null,
        searchTocId = null,
        searchExtended = false,
        model = "student@test",
        gradedAt = at,
    )

    private fun visit(
        key: String,
        kind: String,
        query: String?,
        at: Long,
    ) = database.visitHistoryQueries.upsertVisit(
        key = key,
        kind = kind,
        bookId = if (kind == "book") 7 else null,
        searchQuery = query,
        title = query ?: "ספר",
        visitedAt = at,
        tocEntryId = null,
        lineId = null,
        searchCategoryId = null,
        searchBookId = 7,
        searchTocId = null,
        searchExtended = false,
    )

    private fun review(vararg marks: DafMark) {
        val folder = FeedbackSource.dafFolderOf(library).also { it.mkdirs() }
        val file = DafReviewFile(tractate = "Berakhot", tractateHe = "ברכות", library = "v1", marks = marks.toList())
        File(folder, "Berakhot.json").writeText(Json.encodeToString(DafReviewFile.serializer(), file))
    }

    private fun mark(
        i: Int,
        at: String,
    ) = DafMark(amud = "2a", c = "g", i = i, t = "שורה $i", ok = false, at = at)

    @Test
    fun `the source reads what is newer than each kind's last package, and only searches of the history`() {
        grade("שנים אוחזין", 10, 3, at = 100)
        grade("שנים אוחזין", 11, 0, at = 200)
        visit("search:a", "search", "שנים אוחזין", at = 150)
        visit("search:b", "search", "מאימתי", at = 250)
        visit("search:c", "search", " ", at = 260)
        visit("book:7", "book", null, at = 270)
        review(mark(1, "2026-11-01T10:00:00Z"), mark(2, "2026-11-03T10:00:00Z"))

        val all = source.read(FeedbackSent())
        assertEquals(listOf(10L, 11L), all.grades.map { it.lineId })
        assertEquals(listOf("שנים אוחזין", "מאימתי"), all.searches.map { it.query })
        assertEquals(2, all.dafMarks)

        val since = Instant.parse("2026-11-02T00:00:00Z").toEpochMilli()
        val newer = source.read(FeedbackSent(grades = 100, searches = 150, daf = since))
        assertEquals(listOf(11L), newer.grades.map { it.lineId })
        assertEquals(listOf("מאימתי"), newer.searches.map { it.query })
        assertEquals(
            listOf(2),
            newer.daf
                .single()
                .marks
                .map { it.i },
        )
        assertEquals("ברכות", newer.daf.single().tractateHe)
    }

    @Test
    fun `a query longer than the service takes is left out, a passage pasted in is not`() {
        val passage = "א".repeat(1500)
        val tooLong = "ב".repeat(MAX_QUERY_CHARS + 1)
        grade(passage, 10, 3, at = 100)
        grade(tooLong, 11, 3, at = 110)
        visit("search:a", "search", passage, at = 150)
        visit("search:b", "search", tooLong, at = 160)

        val read = source.read(FeedbackSent())
        assertEquals(listOf(10L), read.grades.map { it.lineId })
        assertEquals(listOf(passage), read.searches.map { it.query })
    }

    @Test
    fun `a package sent up to its newest time is not read again, a mark's finer time too`() {
        grade("שנים אוחזין", 10, 3, at = 100)
        visit("search:a", "search", "שנים אוחזין", at = 150)
        review(mark(1, "2026-11-03T10:00:00.123456789Z"))
        val newest = source.read(FeedbackSent()).newest()
        assertEquals(FeedbackSent(grades = 100, searches = 150, daf = Instant.parse("2026-11-03T10:00:00.123Z").toEpochMilli()), newest)
        assertTrue(source.read(newest).isEmpty)
    }

    @Test
    fun `the package keeps the kinds chosen, and a query left out is in none of them`() {
        grade("שנים אוחזין", 10, 3, at = 100)
        grade("כל כתבי הקודש", 12, 2, at = 110)
        visit("search:a", "search", "שנים אוחזין", at = 150)
        visit("search:b", "search", "מאימתי", at = 250)
        review(mark(1, "2026-11-03T10:00:00Z"))
        val contents = source.read(FeedbackSent())
        val choice = FeedbackChoice(searches = true, leftOut = setOf("כל כתבי הקודש"))
        assertEquals(listOf("שנים אוחזין", "כל כתבי הקודש", "מאימתי"), contents.queries(choice))
        // The searches off: the queries of the grades; neither kind, none
        assertEquals(listOf("שנים אוחזין", "כל כתבי הקודש"), contents.queries(FeedbackChoice(searches = false)))
        assertEquals(emptyList(), contents.queries(FeedbackChoice(grades = false, searches = false)))

        val made = feedbackPackage(contents, choice, "sender-1", "1.0.7", "v1", Instant.parse("2026-11-05T12:00:00Z"))
        assertEquals(listOf("שנים אוחזין"), made.grades.map { it.query })
        assertEquals(listOf("שנים אוחזין", "מאימתי"), made.searches.map { it.query })
        assertEquals(
            1,
            made.daf
                .single()
                .marks.size,
        )
        val json = made.toJson()
        assertFalse("כל כתבי הקודש" in json)
        assertEquals(made, Json.decodeFromString(FeedbackPackage.serializer(), json))

        // A kind turned off sends nothing
        val defaults = feedbackPackage(contents, FeedbackChoice(searches = false, daf = false), "sender-1", null, null)
        assertEquals(2, defaults.grades.size)
        assertTrue(defaults.searches.isEmpty() && defaults.daf.isEmpty())
        assertFalse("\"searches\":[{" in defaults.toJson())
    }

    @Test
    fun `a daf mark is sent with what is wrong with its line, and of which of its words`() {
        review(
            mark(1, "2026-11-03T10:00:00Z").copy(layout = true, from = 2),
            mark(2, "2026-11-03T10:00:01Z").copy(words = true, to = 4),
            mark(3, "2026-11-03T10:00:02Z"),
        )
        val made = feedbackPackage(source.read(FeedbackSent()), FeedbackChoice(), "sender-1", "1.0.7", "v1")
        val json = made.toJson()
        assertTrue("\"layout\":true" in json && "\"from\":2" in json)
        assertTrue("\"words\":true" in json && "\"to\":4" in json)
        assertEquals(made, Json.decodeFromString(FeedbackPackage.serializer(), json))
        // A mark of before the two reasons says neither
        val marks = made.daf.single().marks
        assertEquals(listOf(true, false, false), marks.map { it.layout })
        assertEquals(listOf(false, true, false), marks.map { it.words })
    }

    @Test
    fun `a big package goes in parts under the limit, every row in one of them, in order`() {
        val grades = (1..400).map { GradeRow("שאילתה $it", "search::שאילתה $it", it.toLong(), 7, 2, 0, gradedAt = it.toLong()) }
        val searches = (1..300).map { SearchRow("חיפוש $it", lastAt = it.toLong()) }
        val marks = (1..500).map { mark(it, "2026-11-01T09:00:00Z") }
        val reviews =
            listOf(
                DafReviewFile(tractate = "Berakhot", tractateHe = "ברכות", library = "v1", marks = marks.take(300)),
                DafReviewFile(tractate = "Shabbat", tractateHe = "שבת", library = "v1", marks = marks.drop(300)),
            )
        val made =
            FeedbackPackage(
                sender = "sender-1",
                sentAt = "2026-11-05T12:00:00Z",
                grades = grades,
                searches = searches,
                daf = reviews,
            )
        val limit = 20_000
        val parts = made.parts(limit)
        assertTrue(parts.size > 3)
        for (part in parts) {
            assertTrue(part.toJson().toByteArray().size <= limit)
            assertEquals("sender-1", part.sender)
            assertEquals(made.sentAt, part.sentAt)
        }
        assertEquals(grades, parts.flatMap { it.grades })
        assertEquals(searches, parts.flatMap { it.searches })
        assertEquals(marks, parts.flatMap { it.daf }.flatMap { it.marks })
        // A tractate's review over the parts its marks need, each with its names
        val berakhot = parts.flatMap { it.daf }.filter { it.tractate == "Berakhot" }
        assertTrue(berakhot.size > 1 && berakhot.all { it.tractateHe == "ברכות" && it.library == "v1" })
        // Small enough: itself
        assertEquals(listOf(made), made.parts())
    }

    @Test
    fun `the package says what it is, and a grade says its search, its line, its place and its model`() {
        grade("שנים אוחזין", 11, 0, at = 200)
        val made = feedbackPackage(source.read(FeedbackSent()), FeedbackChoice(), "sender-1", "1.0.7", "v1")
        val json = Json.parseToJsonElement(made.toJson()).toString()
        for (part in listOf(
            "\"format\":1",
            "\"app\":\"Pardes\"",
            "\"sender\":\"sender-1\"",
            "\"library\":\"v1\"",
            "\"search\":\"search::שנים אוחזין\"",
            "\"lineId\":11",
            "\"grade\":0",
            "\"rank\":1",
            "\"model\":\"student@test\"",
            "\"gradedAt\":200",
        )) {
            assertTrue(part in json, "$part in $json")
        }
        // Nothing for what is not known
        assertFalse("categoryId" in json)
    }
}

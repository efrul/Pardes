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
        // The searches off, as at first: the queries of the grades; neither kind, none
        assertEquals(listOf("שנים אוחזין", "כל כתבי הקודש"), contents.queries(FeedbackChoice()))
        assertEquals(emptyList(), contents.queries(FeedbackChoice(grades = false)))

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

        // The searches are off at first, and a kind turned off sends nothing
        val defaults = feedbackPackage(contents, FeedbackChoice(daf = false), "sender-1", null, null)
        assertEquals(2, defaults.grades.size)
        assertTrue(defaults.searches.isEmpty() && defaults.daf.isEmpty())
        assertFalse("\"searches\":[{" in defaults.toJson())
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

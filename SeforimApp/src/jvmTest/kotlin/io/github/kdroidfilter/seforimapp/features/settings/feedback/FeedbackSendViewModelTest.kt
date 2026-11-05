package io.github.kdroidfilter.seforimapp.features.settings.feedback

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Saving the feedback: the package in a file, and the next one holding only what is newer. */
class FeedbackSendViewModelTest {
    private val database = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))
    private val settings =
        testAppSettings().apply { setDatabasePath(File(createTempDirectory("feedback-library").toFile(), "seforim.db").path) }
    private val folder = createTempDirectory("feedback-out").toFile()

    private fun grade(
        query: String,
        lineId: Long,
        at: Long,
    ) = database.searchFeedbackQueries.upsertGrade(
        "search::$query",
        query,
        lineId,
        7,
        2,
        0,
        null,
        null,
        null,
        false,
        null,
        at,
    )

    private fun search(
        query: String,
        at: Long,
    ) = database.visitHistoryQueries.upsertVisit("search:$query", "search", null, query, query, at, null, null, null, null, null, false)

    private suspend fun FeedbackSendViewModel.read(): FeedbackContents {
        withTimeout(10_000) { refresh().join() }
        return state.value.contents!!
    }

    private suspend fun FeedbackSendViewModel.saved(): File =
        withTimeout(10_000) { state.first { it.work !is FeedbackSendViewModel.Work.Saving && it.work != FeedbackSendViewModel.Work.Idle } }
            .work
            .let { (it as FeedbackSendViewModel.Work.Saved).file }

    @Test
    fun `a package holds what the user kept, and the next one only what came after it`() =
        runBlocking {
            grade("שנים אוחזין", 10, at = 100)
            grade("מה שלא נשלח", 11, at = 200)
            search("שנים אוחזין", at = 150)
            val viewModel = FeedbackSendViewModel(settings, database)
            assertEquals(2, viewModel.read().grades.size)

            viewModel.setQuery("מה שלא נשלח", kept = false)
            viewModel.saveTo(folder)
            val file = viewModel.saved()
            assertTrue(file.name.startsWith("pardes-feedback-${settings.getFeedbackSender().take(8)}-") && file.name.endsWith(".json"))
            assertEquals(listOf(file), folder.listFiles()!!.toList())
            val made = Json.decodeFromString(FeedbackPackage.serializer(), file.readText())
            assertEquals(listOf("שנים אוחזין"), made.grades.map { it.query })
            assertTrue(made.searches.isEmpty())
            assertEquals(settings.getFeedbackSender(), made.sender)

            // The grades are sent up to the newest read, the one left out too; the searches, off, are not
            assertEquals(200, settings.getFeedbackSentAt(FeedbackSendViewModel.GRADES))
            assertEquals(0, settings.getFeedbackSentAt(FeedbackSendViewModel.SEARCHES))
            assertTrue(settings.getFeedbackSentAt(FeedbackSendViewModel.LAST) > 0)

            grade("מאימתי", 12, at = 300)
            val next = viewModel.read()
            assertEquals(listOf("מאימתי"), next.grades.map { it.query })
            assertEquals(listOf("שנים אוחזין"), next.searches.map { it.query })

            // All again, on request
            viewModel.setAll(true)
            assertEquals(3, withTimeout(10_000) { viewModel.state.first { it.contents != null } }.contents!!.grades.size)
        }

    @Test
    fun `nothing kept, nothing saved`() =
        runBlocking {
            search("שנים אוחזין", at = 150)
            val viewModel = FeedbackSendViewModel(settings, database)
            // Only searches, and they are off at first
            assertEquals(1, viewModel.read().searches.size)
            viewModel.saveTo(folder)
            assertEquals(FeedbackSendViewModel.Work.Idle, viewModel.state.value.work)
            assertTrue(folder.listFiles()!!.isEmpty())
        }

    @Test
    fun `the sender's id stays until it is renewed`() {
        val first = settings.getFeedbackSender()
        assertEquals(first, settings.getFeedbackSender())
        val viewModel = FeedbackSendViewModel(settings, database)
        viewModel.renewSender()
        assertNotEquals(first, settings.getFeedbackSender())
        assertEquals(settings.getFeedbackSender(), viewModel.state.value.sender)
    }
}

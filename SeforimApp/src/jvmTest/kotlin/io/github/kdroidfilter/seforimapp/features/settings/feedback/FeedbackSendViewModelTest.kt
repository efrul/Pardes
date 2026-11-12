package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Sending and saving the feedback: the package sent or in a file, and the next one holding only what is newer. */
class FeedbackSendViewModelTest {
    private val database = feedbackDatabase()
    private val settings =
        testAppSettings().apply { setDatabasePath(File(createTempDirectory("feedback-library").toFile(), "seforim.db").path) }
    private val folder = createTempDirectory("feedback-out").toFile()
    private val service = ServiceDouble()

    private fun viewModel(double: ServiceDouble = ServiceDouble(url = null)): FeedbackSendViewModel {
        val sender = FeedbackSender(settings, database, double.service)
        return FeedbackSendViewModel(settings, sender, FeedbackAutoSender(settings, sender))
    }

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
            database.grade("שנים אוחזין", 10, at = 100)
            database.grade("מה שלא נשלח", 11, at = 200)
            database.search("שנים אוחזין", at = 150)
            val viewModel = viewModel()
            assertEquals(2, viewModel.read().grades.size)
            // Every kind is sent at first; the searches turned off here
            assertTrue(viewModel.state.value.choice.searches)
            viewModel.setSearches(false)

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
            assertEquals(200, settings.getFeedbackSentAt(FeedbackSender.GRADES))
            assertEquals(0, settings.getFeedbackSentAt(FeedbackSender.SEARCHES))
            assertTrue(settings.getFeedbackSentAt(FeedbackSender.LAST) > 0)

            database.grade("מאימתי", 12, at = 300)
            val next = viewModel.read()
            assertEquals(listOf("מאימתי"), next.grades.map { it.query })
            assertEquals(listOf("שנים אוחזין"), next.searches.map { it.query })

            // All again, on request
            viewModel.setAll(true)
            assertEquals(3, withTimeout(10_000) { viewModel.state.first { it.contents != null } }.contents!!.grades.size)
        }

    @Test
    fun `sent to the service in the kinds the user keeps, which the automatic sending keeps too`() =
        runBlocking {
            database.grade("שנים אוחזין", 10, at = 100)
            database.search("שנים אוחזין", at = 150)
            val viewModel = viewModel(service)
            assertTrue(viewModel.state.value.online)
            viewModel.read()
            viewModel.setSearches(true)
            assertTrue(settings.isFeedbackKindSent(FeedbackSender.SEARCHES, false))

            viewModel.send()
            eventually { viewModel.state.value.work is FeedbackSendViewModel.Work.Sent }
            val sent = service.received.single()
            assertEquals(listOf("שנים אוחזין"), sent.grades.map { it.query })
            assertEquals(listOf("שנים אוחזין"), sent.searches.map { it.query })
            assertEquals(150, settings.getFeedbackSentAt(FeedbackSender.SEARCHES))
            eventually {
                viewModel.state.value.contents
                    ?.isEmpty == true
            }

            // And deleted
            viewModel.deleteSent()
            eventually { viewModel.state.value.work == FeedbackSendViewModel.Work.Deleted(1) }
            assertEquals(listOf(settings.getFeedbackSender()), service.deleted.toList())
        }

    @Test
    fun `without a network nothing is marked sent, and the card says why`() =
        runBlocking {
            database.grade("שנים אוחזין", 10, at = 100)
            service.offline = true
            val viewModel = viewModel(service)
            viewModel.read()
            viewModel.send()
            eventually { viewModel.state.value.work is FeedbackSendViewModel.Work.Failed }
            assertTrue((viewModel.state.value.work as FeedbackSendViewModel.Work.Failed).problem is java.io.IOException)
            assertEquals(0, settings.getFeedbackSentAt(FeedbackSender.GRADES))
        }

    @Test
    fun `nothing kept, nothing saved`() =
        runBlocking {
            database.search("שנים אוחזין", at = 150)
            val viewModel = viewModel()
            // Only searches, turned off
            assertEquals(1, viewModel.read().searches.size)
            viewModel.setSearches(false)
            viewModel.saveTo(folder)
            assertEquals(FeedbackSendViewModel.Work.Idle, viewModel.state.value.work)
            assertTrue(folder.listFiles()!!.isEmpty())
        }

    @Test
    fun `the sender's id stays until it is renewed`() {
        val first = settings.getFeedbackSender()
        assertEquals(first, settings.getFeedbackSender())
        val viewModel = viewModel()
        viewModel.renewSender()
        assertNotEquals(first, settings.getFeedbackSender())
        assertEquals(settings.getFeedbackSender(), viewModel.state.value.sender)
    }

    @Test
    fun `the grade buttons are turned on and off from the page, and the settings keep it`() {
        val viewModel = viewModel()
        assertFalse(viewModel.state.value.buttons)
        viewModel.setButtons(true)
        assertTrue(viewModel.state.value.buttons && settings.isSearchFeedbackEnabled())
        assertTrue(viewModel().state.value.buttons, "read back when the page is opened again")
        viewModel.setButtons(false)
        assertFalse(settings.isSearchFeedbackEnabled())
    }
}

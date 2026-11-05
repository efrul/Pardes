package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The question at start, and the feedback sent by itself: at start, at a connection, and after no network. */
class FeedbackAutoSenderTest {
    private val database = feedbackDatabase()
    private val settings =
        testAppSettings().apply { setDatabasePath(File(createTempDirectory("feedback-library").toFile(), "seforim.db").path) }
    private val service = ServiceDouble()
    private val connections = MutableSharedFlow<Unit>()

    private fun autoSender(double: ServiceDouble = service): Pair<FeedbackSender, FeedbackAutoSender> {
        val sender = FeedbackSender(settings, database, double.service)
        return sender to FeedbackAutoSender(settings, sender).also { it.connections = connections }
    }

    // The network came back, once the sender listens
    private suspend fun connect() {
        withTimeout(10_000) { connections.subscriptionCount.first { it > 0 } }
        connections.emit(Unit)
    }

    private fun CoroutineScope.running(auto: FeedbackAutoSender) = launch(Dispatchers.Default) { auto.run { } }

    @Test
    fun `asks once, sends what is new when the user agrees, and again at each connection`() =
        runBlocking {
            database.grade("שנים אוחזין", 10, at = 100)
            val (_, auto) = autoSender()
            val job = running(auto)
            withTimeout(10_000) { auto.askVisible.first { it } }
            delay(200)
            assertTrue(service.received.isEmpty())

            auto.answer(true, FeedbackChoice(searches = true))
            eventually { service.received.size == 1 }
            assertEquals(listOf("שנים אוחזין"), service.received[0].grades.map { it.query })
            assertFalse(auto.askVisible.value)
            assertTrue(settings.isFeedbackAsked() && settings.isFeedbackAutoSend())
            assertTrue(settings.isFeedbackKindSent(FeedbackSender.SEARCHES, false))

            // Nothing new: a connection sends nothing
            connect()
            delay(300)
            assertEquals(1, service.received.size)
            // Something new: the next connection sends it
            database.grade("מאימתי", 12, at = 300)
            database.search("מאימתי", at = 310)
            connect()
            eventually { service.received.size == 2 }
            assertEquals(listOf("מאימתי"), service.received[1].grades.map { it.query })
            assertEquals(listOf("מאימתי"), service.received[1].searches.map { it.query })
            job.cancelAndJoin()
        }

    @Test
    fun `a no sends nothing, and the question is not asked again`() =
        runBlocking {
            database.grade("שנים אוחזין", 10, at = 100)
            val (_, auto) = autoSender()
            val job = running(auto)
            withTimeout(10_000) { auto.askVisible.first { it } }
            auto.answer(false, FeedbackChoice())
            connect()
            delay(300)
            assertTrue(service.received.isEmpty())
            assertTrue(settings.isFeedbackAsked())
            assertFalse(settings.isFeedbackAutoSend())
            job.cancelAndJoin()

            val (_, again) = autoSender()
            val second = running(again)
            delay(300)
            assertFalse(again.askVisible.value)
            second.cancelAndJoin()
        }

    @Test
    fun `closed without an answer, it is asked again at the next start`() =
        runBlocking {
            val (_, auto) = autoSender()
            val job = running(auto)
            withTimeout(10_000) { auto.askVisible.first { it } }
            auto.dismiss()
            job.cancelAndJoin()
            assertFalse(settings.isFeedbackAsked())

            val (_, again) = autoSender()
            val second = running(again)
            withTimeout(10_000) { again.askVisible.first { it } }
            second.cancelAndJoin()
        }

    @Test
    fun `without a network at start, sent at the next connection`() =
        runBlocking {
            settings.setFeedbackAsked(true)
            settings.setFeedbackAutoSend(true)
            database.grade("שנים אוחזין", 10, at = 100)
            service.offline = true
            val (sender, auto) = autoSender()
            val job = running(auto)
            eventually { sender.status.value is FeedbackSender.Status.Failed }
            assertEquals(0, settings.getFeedbackSentAt(FeedbackSender.GRADES))

            service.offline = false
            connect()
            eventually { service.received.size == 1 }
            assertEquals(100, settings.getFeedbackSentAt(FeedbackSender.GRADES))
            assertFalse(auto.askVisible.value)
            job.cancelAndJoin()
        }

    @Test
    fun `a build without the service neither asks nor sends`() =
        runBlocking {
            database.grade("שנים אוחזין", 10, at = 100)
            val offline = ServiceDouble(url = null)
            val (_, auto) = autoSender(offline)
            withTimeout(10_000) { auto.run { error("not asked") } }
            assertFalse(auto.askVisible.value)
            assertTrue(offline.received.isEmpty())
        }
}

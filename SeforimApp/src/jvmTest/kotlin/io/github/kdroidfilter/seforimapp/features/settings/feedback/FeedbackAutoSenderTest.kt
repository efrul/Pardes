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

    // [zayit]: the copies of Zayit's data on this computer, none unless said
    private fun autoSender(
        double: ServiceDouble = service,
        zayit: List<File> = emptyList(),
    ): Pair<FeedbackSender, FeedbackAutoSender> {
        val sender = FeedbackSender(settings, database, double.service).withOtherApps(zayit)
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

            assertTrue(auto.buttonsOffered())
            auto.answer(true, FeedbackChoice(searches = true), buttons = true)
            eventually { service.received.size == 1 }
            // The grade buttons on, as the user asked
            assertTrue(settings.isSearchFeedbackEnabled())
            assertFalse(auto.buttonsOffered())
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
            auto.answer(false, FeedbackChoice(), buttons = true)
            connect()
            delay(300)
            assertTrue(service.received.isEmpty())
            assertFalse(settings.isSearchFeedbackEnabled())
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
    fun `whom agreed before another app's searches were offered is asked once more, where the app is`() =
        runBlocking {
            settings.setFeedbackAsked(true)
            settings.setFeedbackAutoSend(true)
            val zayit = listOf(zayitDatabase(createTempDirectory("zayit").toFile(), "שנים אוחזין" to 100L))
            // No other app here: nothing to ask
            val (_, alone) = autoSender()
            val first = running(alone)
            delay(300)
            assertFalse(alone.askVisible.value)
            first.cancelAndJoin()

            val (_, auto) = autoSender(zayit = zayit)
            val job = running(auto)
            withTimeout(10_000) { auto.askVisible.first { it } }
            assertEquals(setOf(OtherApp.ZAYIT), auto.otherApps())
            // Offered checked, the app found here and publishing; nothing of Zayit's goes before the answer
            assertEquals(setOf(OtherApp.ZAYIT) to true, auto.choice().others to auto.choice().publish)
            delay(200)
            assertTrue(service.received.isEmpty())
            auto.answer(true, auto.choice())
            eventually { service.received.size == 1 }
            assertEquals(listOf("שנים אוחזין" to "zayit"), service.received[0].searches.map { it.query to it.source })
            assertEquals(true, service.received[0].publish)
            job.cancelAndJoin()

            val (_, again) = autoSender(zayit = zayit)
            val second = running(again)
            delay(300)
            assertFalse(again.askVisible.value)
            second.cancelAndJoin()
        }

    @Test
    fun `the other apps and publishing are offered checked, and what the user unchecks stays so`() =
        runBlocking {
            val zayit = listOf(zayitDatabase(createTempDirectory("zayit").toFile(), "שנים אוחזין" to 100L))
            database.search("משלי", at = 50)
            val (_, auto) = autoSender(zayit = zayit)
            val job = running(auto)
            withTimeout(10_000) { auto.askVisible.first { it } }
            assertEquals(FeedbackChoice(others = setOf(OtherApp.ZAYIT), publish = true), auto.choice())

            // Unchecked, both: only this app's, and not to publish
            auto.answer(true, auto.choice().copy(others = emptySet(), publish = false))
            eventually { service.received.size == 1 }
            assertEquals(listOf("משלי" to null), service.received[0].searches.map { it.query to it.source })
            assertEquals(null, service.received[0].publish)
            // The answer stands: they are not offered checked again
            assertEquals(FeedbackChoice(), auto.choice())
            job.cancelAndJoin()
        }

    @Test
    fun `whom said no is not asked about another app's searches, and nothing of them is sent`() =
        runBlocking {
            settings.setFeedbackAsked(true)
            settings.setFeedbackAutoSend(false)
            val zayit = listOf(zayitDatabase(createTempDirectory("zayit").toFile(), "שנים אוחזין" to 100L))
            val (_, auto) = autoSender(zayit = zayit)
            assertFalse(auto.isQuestionOpen())
            val job = running(auto)
            connect()
            delay(300)
            assertFalse(auto.askVisible.value)
            assertTrue(service.received.isEmpty())
            job.cancelAndJoin()
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

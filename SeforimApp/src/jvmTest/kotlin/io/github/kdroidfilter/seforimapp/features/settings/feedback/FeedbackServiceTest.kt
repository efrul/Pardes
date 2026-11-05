package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The service's client: a package sent and its id, a refusal and why, no network, and a build without the address. */
class FeedbackServiceTest {
    private val sender = "aaaaaaaa-1111-4111-8111-111111111111"
    private val json =
        feedbackPackage(
            FeedbackContents(searches = listOf(SearchRow("שנים אוחזין", lastAt = 1))),
            FeedbackChoice(searches = true),
            sender,
            "1.0.7",
            "v3",
            Instant.parse("2026-11-05T12:00:00Z"),
        ).toJson()

    @Test
    fun `sends a package and says its id, and deletes what a sender sent`() =
        runBlocking<Unit> {
            val double = ServiceDouble()
            assertEquals(1, double.service.send(json))
            assertEquals(2, double.service.send(json))
            assertEquals(listOf("שנים אוחזין"), double.received[0].searches.map { it.query })
            assertEquals(2, double.service.delete(sender))
            assertTrue(double.received.isEmpty())
        }

    @Test
    fun `a refusal says why, and no network is an IOException`() =
        runBlocking<Unit> {
            val double = ServiceDouble()
            double.refusal = HttpStatusCode.TooManyRequests
            val tooMany = assertFailsWith<FeedbackServiceProblem> { double.service.send(json) }
            assertTrue(tooMany.tooMany)
            assertEquals("refused", tooMany.reason)
            double.refusal = HttpStatusCode.BadRequest
            assertFalse(assertFailsWith<FeedbackServiceProblem> { double.service.send(json) }.tooMany)
            double.refusal = null
            double.offline = true
            assertFailsWith<IOException> { double.service.send(json) }
        }

    @Test
    fun `a build without the address has no service`() {
        assertFalse(ServiceDouble(url = null).service.available)
        assertFalse(ServiceDouble(url = "  ").service.available)
        assertTrue(ServiceDouble(url = "https://feedback.test/").service.available)
    }
}

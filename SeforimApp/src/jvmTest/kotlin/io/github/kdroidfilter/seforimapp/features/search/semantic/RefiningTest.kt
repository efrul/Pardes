package io.github.kdroidfilter.seforimapp.features.search.semantic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A session's refined snippets: worked out while it is open, stopped when it closes, the caller going on. */
class RefiningTest {
    @Test
    fun `an open session's work gives its result`() =
        runBlocking {
            assertEquals("refined", Refining().run("none") { "refined" })
        }

    @Test
    fun `closing the session stops its work running, and the caller gets nothing`() =
        runBlocking {
            val refining = Refining()
            val started = CompletableDeferred<Unit>()
            var stopped = false
            val result =
                async(Dispatchers.Default) {
                    refining.run("none") {
                        started.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            stopped = true
                        }
                    }
                }
            started.await()
            refining.close()

            assertEquals("none", withTimeout(5_000) { result.await() })
            assertTrue(stopped)
        }

    @Test
    fun `a closed session works out nothing more`() =
        runBlocking {
            val refining = Refining()
            refining.close()
            var ran = false

            assertEquals("none", refining.run("none") { "refined".also { ran = true } })
            assertFalse(ran)
        }
}

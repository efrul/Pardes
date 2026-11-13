package io.github.kdroidfilter.seforimapp.features.settings.downloads

import io.github.kdroidfilter.seforimapp.features.settings.downloads.StartOffers.Found
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination.Daf
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination.Data
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a start opens the settings on ([StartOffers]), and the release list read once for all ([ReleaseLists]). */
class StartOffersTest {
    @AfterTest
    fun tearDown() {
        ReleaseLists.forget()
        ReleaseLists.clock = { System.nanoTime() / 1_000_000 }
    }

    @Test
    fun `missing files open the semantic search's page at every start`() {
        assertEquals(listOf(Data), StartOffers.pagesFor(Found(missingPack = true), offeredPack = 9, offeredDaf = 9))
    }

    @Test
    fun `a newer pack than the one in use is offered once`() {
        val found = Found(installedPack = 2, newestPack = 3)

        assertEquals(listOf(Data), StartOffers.pagesFor(found, offeredPack = 0, offeredDaf = 0))
        assertEquals(emptyList(), StartOffers.pagesFor(found, offeredPack = 3, offeredDaf = 0), "offered already")
        assertEquals(emptyList(), StartOffers.pagesFor(Found(installedPack = 3, newestPack = 3), 0, 0), "the one in use")
        assertEquals(emptyList(), StartOffers.pagesFor(Found(installedPack = null, newestPack = 3), 0, 0), "a pack with no number")
    }

    @Test
    fun `a newer release of layouts for an installed tractate is offered once, after the semantic search's page`() {
        assertEquals(listOf(Daf), StartOffers.pagesFor(Found(newerDaf = 3), offeredPack = 0, offeredDaf = 2))
        assertEquals(emptyList(), StartOffers.pagesFor(Found(newerDaf = 3), offeredPack = 0, offeredDaf = 3))
        assertEquals(listOf(Data, Daf), StartOffers.pagesFor(Found(missingPack = true, newerDaf = 3), 0, 0))
        assertEquals(emptyList(), StartOffers.pagesFor(Found(), 0, 0))
    }

    @Test
    fun `a page's mark is taken once its card is shown`() {
        assertFalse(StartOffers.take(Daf))
    }

    @Test
    fun `a release list read lately is not read again, one that failed is`() =
        runBlocking {
            var now = 0L
            ReleaseLists.clock = { now }
            var reads = 0
            val fetch: suspend (String) -> String = { url -> reads++.let { "$url $reads" } }

            assertEquals("api 1", ReleaseLists.text("api", fetch))
            assertEquals("api 1", ReleaseLists.text("api", fetch), "the semantic packs' and the daf's readers share it")
            assertEquals("site 2", ReleaseLists.text("site", fetch))
            now += 11 * 60_000L
            assertEquals("api 3", ReleaseLists.text("api", fetch), "read again after ten minutes")

            val failing: suspend (String) -> String = { throw java.io.IOException("HTTP 403") }
            assertTrue(runCatching { ReleaseLists.text("other", failing) }.isFailure)
            assertEquals("other 4", ReleaseLists.text("other", fetch))
        }
}

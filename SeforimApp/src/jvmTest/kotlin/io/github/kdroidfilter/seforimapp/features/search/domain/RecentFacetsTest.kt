package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The facets of a search tab's last searches, each counted once, and a tab's books read from the whole search's. */
class RecentFacetsTest {
    private val whole = FacetsKey("מילה", 5, baseBookOnly = true)
    private val counts = SearchFacets(totalHits = 6, categoryCounts = mapOf(1L to 6), bookCounts = mapOf(10L to 1, 20L to 2, 30L to 3))

    @Test
    fun `a search's facets are counted once, the second asking awaiting the first`() {
        val facets = RecentFacets()
        var counted = 0
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val first =
            pool.submit<SearchFacets?> {
                facets.get(whole) {
                    counted++
                    started.countDown()
                    release.await()
                    counts
                }
            }
        started.await()
        val second = Executors.newSingleThreadExecutor().submit<SearchFacets?> { facets.get(whole) { counts.also { counted++ } } }
        release.countDown()

        assertEquals(counts, first.get(5, TimeUnit.SECONDS))
        assertEquals(counts, second.get(5, TimeUnit.SECONDS))
        assertEquals(counts, facets.get(whole) { error("counted again") })
        assertEquals(1, counted)
    }

    @Test
    fun `a tab's books are counted from the whole search's facets`() {
        val facets = RecentFacets()
        facets.get(whole) { counts }

        assertEquals(mapOf(10L to 1, 30L to 3), facets.bookCounts(whole.copy(bookIds = setOf(10L, 30L, 40L))))
        assertEquals(counts.bookCounts, facets.bookCounts(whole))
        // Another search, or the same in all the books and not the base ones alone, or narrowed otherwise: not known
        assertNull(facets.bookCounts(whole.copy(query = "אחרת")))
        assertNull(facets.bookCounts(whole.copy(baseBookOnly = false)))
        assertNull(facets.bookCounts(whole.copy(lineIds = setOf(1L))))
    }

    @Test
    fun `a search in some books holds none wider than them`() {
        val facets = RecentFacets()
        val some = whole.copy(bookIds = setOf(10L, 20L))
        facets.get(some) { counts }

        assertEquals(mapOf(10L to 1), facets.bookCounts(whole.copy(bookIds = setOf(10L))))
        assertNull(facets.bookCounts(whole))
        assertNull(facets.bookCounts(whole.copy(bookIds = setOf(10L, 30L))))
    }

    @Test
    fun `only the last searches are kept, and a count that failed is not`() {
        val facets = RecentFacets(size = 2)
        facets.get(whole) { counts }
        facets.get(whole.copy(query = "שנייה")) { counts }
        facets.get(whole.copy(query = "שלישית")) { counts }
        assertNull(facets.bookCounts(whole))

        assertFailsWith<IllegalStateException> { facets.get(whole) { error("the index failed") } }
        assertEquals(counts, facets.get(whole) { counts })
    }
}

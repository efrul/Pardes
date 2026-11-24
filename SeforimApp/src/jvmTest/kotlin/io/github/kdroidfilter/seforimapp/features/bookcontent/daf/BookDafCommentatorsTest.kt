package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals

// Pardes (requests 92 and 93 of docs/DESKTOP_STAGE3_PLAN.md): a book's commentators on its pages as the reader chooses
// them: a check in their list, a commentator's name dragged among the bands or beside the text, its own letters.
class BookDafCommentatorsTest {
    private val own = BookDafCommentators(shown = listOf(1L, 2L, 3L))

    // The names of three bands down a page, a hundred points apart, and of the commentator beside the text
    private fun head(
        bookId: Long,
        y: Float,
        isBeside: Boolean = false,
    ) = BandHead(bookId, 0f, y, 400f, y + 10f, isBeside = isBeside)

    private val heads = listOf(head(1, 200f), head(2, 300f), head(3, 400f))

    @Test
    fun `a commentator checked comes after those shown, and one unchecked leaves the pages and the text's side`() {
        assertEquals(listOf(1L, 2L, 3L, 9L), own.checked(listOf(9L, 2L), checked = true).shown)
        val beside = own.besideText(2L, on = true)
        assertEquals(BookDafCommentators(shown = listOf(1L, 3L)), beside.checked(listOf(2L), checked = false))
        assertEquals(listOf(4L), BookDafCommentators().checked(listOf(4L), checked = true).shown, "the first one chosen")
    }

    @Test
    fun `a commentator moves before another, or after them all, and is then no longer beside the text`() {
        assertEquals(listOf(3L, 1L, 2L), own.moved(3L, before = 1L).shown)
        assertEquals(listOf(2L, 3L, 1L), own.moved(1L, before = null).shown)
        assertEquals(listOf(1L, 2L, 3L), own.moved(2L, before = 3L).shown, "where it was")
        assertEquals(null, own.besideText(3L, on = true).moved(3L, before = 1L).beside)
    }

    @Test
    fun `a commentator's own letters are kept until they set nothing apart`() {
        val styled = own.styled(2L, BandStyle(columns = 1))
        assertEquals(mapOf(2L to BandStyle(columns = 1)), styled.bands)
        assertEquals(emptyMap(), styled.styled(2L, BandStyle()).bands)
    }

    @Test
    fun `a name dropped goes before the band below it, after them all at the bottom, or beside the text above them`() {
        assertEquals(HeadDrop.Before(2L), headDrop(heads, 250f, hasSide = true))
        assertEquals(HeadDrop.Before(1L), headDrop(heads, 195f, hasSide = true), "just above the first band: first among them")
        assertEquals(HeadDrop.Before(null), headDrop(heads, 500f, hasSide = true))
        assertEquals(HeadDrop.Beside, headDrop(heads, 100f, hasSide = true))
        assertEquals(HeadDrop.Before(1L), headDrop(heads, 100f, hasSide = false), "a page with no column beside its text")
        assertEquals(HeadDrop.Beside, headDrop(listOf(head(7, 20f, isBeside = true)), 300f, hasSide = true), "a page of text alone")
    }

    @Test
    fun `a drop sets the commentators' order by the bands on the page`() {
        assertEquals(listOf(3L, 1L, 2L), own.dropped(3L, HeadDrop.Before(1L), heads).shown)
        assertEquals(own, own.dropped(2L, HeadDrop.Before(2L), heads), "dropped where it is")
        assertEquals(3L, own.dropped(3L, HeadDrop.Beside, heads).beside)
        // After the page's last band: before the commentator that follows it among those shown, on a later page
        val more = BookDafCommentators(shown = listOf(1L, 2L, 3L, 4L))
        assertEquals(listOf(2L, 1L, 3L, 4L), more.dropped(1L, HeadDrop.Before(null), heads.take(2)).shown)
        assertEquals(listOf(1L, 2L, 3L, 4L), more.besideText(4L, on = true).dropped(4L, HeadDrop.Before(null), heads).shown)
    }
}

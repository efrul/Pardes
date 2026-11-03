package io.github.kdroidfilter.seforimapp.framework.search.titles

import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookTitleIndexTest {
    private val authors =
        listOf(
            TitleAuthor(1, "רבי ישראל מאיר הכהן", listOf("חפץ חיים"), 3),
            TitleAuthor(2, "יוסף קארו", emptyList(), 2),
            TitleAuthor(3, "משה פיינשטיין", emptyList(), 1),
        )

    private fun book(
        id: Long,
        title: String,
        path: List<String> = listOf("הלכה"),
        authors: List<Long> = emptyList(),
        acronyms: List<String> = emptyList(),
        base: Boolean = false,
    ) = TitleBook(id, title, 1, id.toFloat(), base, path, authors, acronyms)

    private val index =
        BookTitleIndex(
            listOf(
                book(1, "שולחן ערוך, אורח חיים", listOf("הלכה", "שולחן ערוך"), listOf(2), listOf("שו\"ע או\"ח"), base = true),
                book(2, "משנה ברורה", listOf("הלכה", "משנה ברורה"), listOf(1), listOf("משנ\"ב")),
                book(3, "ביאור הלכה", listOf("הלכה", "משנה ברורה"), listOf(1)),
                book(4, "הכוזרי", listOf("מחשבת ישראל"), acronyms = listOf("ספר הכוזרי")),
                book(5, "שמירת הלשון", listOf("מוסר"), listOf(1)),
                book(6, "בבא קמא", listOf("תלמוד"), base = true),
                book(7, "תוספתא בבא קמא", listOf("תוספתא")),
                book(8, "בבא בתרא", listOf("תלמוד"), base = true),
                book(9, "אגרות משה", listOf("שו\"ת"), listOf(3)),
            ),
            authors,
        )

    private fun first(query: String): String? =
        index
            .searchBooks(query, 10)
            .firstOrNull()
            ?.book
            ?.title

    @Test
    fun `a title is found as typed, begun, in another order, with or without the article`() {
        assertEquals("משנה ברורה", first("משנה ברורה"))
        assertEquals("משנה ברורה", first("משנה ברו"))
        assertEquals("שולחן ערוך, אורח חיים", first("אורח חיים שולחן ערוך"))
        assertEquals("הכוזרי", first("כוזרי"))
        assertEquals("הכוזרי", first("ספר הכוזרי"))
    }

    @Test
    fun `acronyms are read with their quotes or without`() {
        assertEquals("משנה ברורה", first("מ\"ב"))
        assertEquals("שולחן ערוך, אורח חיים", first("שו\"ע או\"ח"))
        assertEquals("שולחן ערוך, אורח חיים", first("שוע אוח"))
    }

    @Test
    fun `other spellings and typos are forgiven`() {
        assertEquals("שולחן ערוך, אורח חיים", first("שלחן ערוך"))
        assertEquals("שמירת הלשון", first("שמירת הלשן"))
        assertEquals("משנה ברורה", first("משנה ברורא"))
    }

    @Test
    fun `a book is found with its author`() {
        assertEquals("משנה ברורה", first("משנה ברורה חפץ חיים"))
        assertEquals("אגרות משה", first("אגרות משה פיינשטיין"))
    }

    @Test
    fun `one typed word is not read twice`() {
        val titles = index.searchBooks("בבא ב", 10).map { it.book.title }
        assertEquals("בבא בתרא", titles.first())
        assertTrue("תוספתא בבא קמא" !in titles, titles.toString())
    }

    @Test
    fun `authors are found by name or another name, without their title, spelled otherwise`() {
        fun firstAuthor(query: String) =
            index
                .searchAuthors(query, 5)
                .firstOrNull()
                ?.author
                ?.name
        assertEquals("רבי ישראל מאיר הכהן", firstAuthor("החפץ חיים"))
        assertEquals("חפץ חיים", index.searchAuthors("חפץ חיים", 5).first().alias)
        assertEquals("יוסף קארו", firstAuthor("הרב יוסף קארו"))
        assertEquals("יוסף קארו", firstAuthor("יוסף קרו"))
        assertEquals("משה פיינשטיין", firstAuthor("משה פינשטין"))
    }

    @Test
    fun `a book's authors are known`() {
        assertEquals(listOf("רבי ישראל מאיר הכהן"), index.authorsOf(2).map { it.name })
        assertTrue(index.searchBooks("משנה ברורה", 3).first().tier >= BookTitleIndex.TIER_BEGUN)
    }

    @Test
    fun `the authors of the books named come after those found by name, once each, room kept for them`() {
        val byName = listOf(1L, 2L, 3L, 4L, 5L)
        assertEquals(listOf(1L, 2L, 6L, 7L), AuthorNames.withAuthorsOfBooks(byName, listOf(6L, 1L, 7L, 8L), 4) { it })
        assertEquals(listOf(1L, 2L, 3L, 4L), AuthorNames.withAuthorsOfBooks(byName, listOf(1L), 4) { it })
    }

    @Test
    fun `edit distance counts a swap as one`() {
        assertEquals(1, BookTitleIndex.editDistance("כוזרי", "כוזירי", 2))
        assertEquals(1, BookTitleIndex.editDistance("ברורה", "בררוה", 2))
        assertEquals(3, BookTitleIndex.editDistance("אבגד", "הוזח", 2))
    }
}

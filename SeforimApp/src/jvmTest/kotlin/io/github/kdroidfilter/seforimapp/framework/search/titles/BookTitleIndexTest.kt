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
    fun `the base books of a category named whole come before the books that have its name in their title`() {
        val tanakh =
            BookTitleIndex(
                listOf(
                    book(1, "אור הישר (תנך)", listOf("תנ״ך", "אחרונים")),
                    book(2, "בראשית", listOf("תנ״ך", "תורה"), base = true),
                    book(3, "שמואל א", listOf("תנ״ך", "נביאים"), base = true),
                    book(4, "יהושע", listOf("תנ״ך", "נביאים"), base = true),
                    book(5, "רש\"י על בראשית", listOf("תנ״ך", "ראשונים", "רש״י", "תורה")),
                    book(6, "משנה תורה, הלכות שבת", listOf("הלכה", "משנה תורה", "ספר זמנים"), base = true),
                    book(7, "ביאור הלכה", listOf("הלכה", "שולחן ערוך", "מפרשים")),
                    book(8, "שולחן ערוך, אורח חיים", listOf("הלכה", "שולחן ערוך"), base = true),
                ),
                emptyList(),
            )

        fun titles(query: String) = tanakh.searchBooks(query, 10).map { it.book.title }
        // In the library's order, a longer title in its place; then the title that has the name, then the commentary
        assertEquals(listOf("בראשית", "שמואל א", "יהושע", "אור הישר (תנך)", "רש\"י על בראשית"), titles("תנך"))
        assertEquals(listOf("שמואל א", "יהושע"), titles("נביאים"))
        // A wide category's name: its own books and those right under it, not every base book below
        assertEquals(listOf("שולחן ערוך, אורח חיים", "ביאור הלכה", "משנה תורה, הלכות שבת"), titles("הלכה"))
    }

    @Test
    fun `when nothing is found, every word may be the start of a word`() {
        val tanakh =
            BookTitleIndex(
                listOf(
                    book(1, "שמואל א", listOf("תנ״ך", "נביאים"), base = true),
                    book(2, "רש\"י על שמואל א", listOf("תנ״ך", "ראשונים", "רש״י", "נביאים")),
                    book(3, "מדרש שמואל", listOf("מדרש", "אגדה"), base = true),
                ),
                emptyList(),
            )
        assertEquals(listOf("שמואל א", "רש\"י על שמואל א"), tanakh.searchBooks("נביא שמואל", 10).map { it.book.title })
        // Found as typed: no word is read as a beginning but the last
        assertEquals(listOf("מדרש שמואל"), tanakh.searchBooks("מדרש שמואל", 10).map { it.book.title })
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

package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentaryGroupsTest {
    private fun book(
        id: Long,
        title: String,
        categoryId: Long,
    ) = CatalogBook(id = id, title = title, categoryId = categoryId)

    private fun category(
        id: Long,
        title: String,
        books: List<CatalogBook> = emptyList(),
        subcategories: List<CatalogCategory> = emptyList(),
    ) = CatalogCategory(id = id, title = title, level = 0, books = books, subcategories = subcategories)

    // תלמוד › בבלי: two tractates, their ראשונים (each commentator in a category of his own) and אחרונים; and משנה
    private val talmud =
        category(
            1,
            "תלמוד",
            subcategories =
                listOf(
                    category(
                        2,
                        "בבלי",
                        subcategories =
                            listOf(
                                category(3, "סדר נזיקין", books = listOf(book(100, "בבא מציעא", 3), book(101, "בבא בתרא", 3))),
                                category(
                                    4,
                                    "ראשונים",
                                    subcategories =
                                        listOf(
                                            category(5, "רשב״ם", books = listOf(book(200, "רשב\"ם על בבא בתרא", 5))),
                                            category(6, "רמב״ן", books = listOf(book(201, "חידושי רמב\"ן על בבא בתרא", 6))),
                                            category(
                                                7,
                                                "רי״ף",
                                                books = listOf(book(202, "רי\"ף בבא מציעא", 7), book(203, "רי\"ף בבא בתרא", 7)),
                                            ),
                                        ),
                                ),
                                category(8, "אחרונים", books = listOf(book(300, "רש\"ש על בבא בתרא", 8))),
                            ),
                    ),
                ),
        )
    private val mishnah =
        category(
            20,
            "משנה",
            subcategories =
                listOf(
                    category(21, "סדר נזיקין", books = listOf(book(400, "משנה בבא בתרא", 21))),
                    category(
                        22,
                        "ראשונים",
                        books = listOf(book(401, "ברטנורא על משנה בבא בתרא", 22), book(402, "רמב\"ם על משנה בבא בתרא", 22)),
                    ),
                ),
        )

    // Each commentary and its links: the רי״ף on בבא מציעא mentions בבא בתרא in a few of its lines
    private val links =
        mapOf(
            200L to mapOf(101L to 900),
            201L to mapOf(101L to 400),
            202L to mapOf(100L to 800, 101L to 12),
            203L to mapOf(101L to 700, 100L to 9),
            300L to mapOf(101L to 50),
            401L to mapOf(400L to 60),
            // A commentary on the Mishnah that the Talmud's tractate is linked to as well
            402L to mapOf(400L to 60, 101L to 30),
        )

    private val groups = CommentaryGroups.of(listOf(talmud, mishnah), links)

    @Test
    fun aGroupIsTheCommentariesOnABookUnderACategoryNamedForATime() {
        val rishonim = groups.single { it.hit.path == listOf("ראשונים", "בבא בתרא") }
        // Not the רי״ף on בבא מציעא: a tenth of its links are not on בבא בתרא
        assertEquals(listOf(200L, 201L, 203L), rishonim.bookIds.toList())
        assertEquals("ראשונים › בבא בתרא", rishonim.hit.label)
        assertEquals(CommentaryGroups.idOf(4, 101), rishonim.hit.id)
        assertEquals(40_000_101L, rishonim.hit.id)
        // One אחרון on it, one ראשון on בבא מציעא: no group of one book
        assertTrue(groups.none { it.hit.path.first() == "אחרונים" })
        assertTrue(groups.none { it.hit.title == "בבא מציעא" })
        // The Mishnah's commentators are a group on the Mishnah's tractate, not on the Talmud's, of another root
        assertEquals(listOf(401L, 402L), groups.single { it.hit.title == "משנה בבא בתרא" }.bookIds.toList())
        assertEquals(2, groups.size)
    }

    @Test
    fun aTextNamesAGroupByItsTimeAndItsBook() {
        val hits = groups.map { it.hit }

        fun named(query: String) = CommentaryGroups.match(query, hits, limit = 5).map { it.label }
        // The book named in full first
        assertEquals(listOf("ראשונים › בבא בתרא", "ראשונים › משנה בבא בתרא"), named("ראשונים על בבא בתרא"))
        assertEquals(listOf("ראשונים › משנה בבא בתרא"), named("ראשונים על משנה בבא בתרא"))
        assertEquals(listOf("ראשונים › בבא בתרא", "ראשונים › משנה בבא בתרא"), named("הראשונים על בבא בת"))
        assertEquals(listOf("ראשונים › בבא בתרא", "ראשונים › משנה בבא בתרא"), named("בבא בתרא ראשונים"))
        // The book alone, or the time alone, names no group: the book and the category are offered as they are
        assertTrue(named("בבא בתרא").isEmpty())
        assertTrue(named("ראשונים").isEmpty())
        assertTrue(named("ראשונים על ברכות").isEmpty())
        assertTrue(named("אחרונים על בבא בתרא").isEmpty())
        assertEquals(1, CommentaryGroups.match("ראשונים על בבא בתרא", hits, limit = 1).size)
    }
}

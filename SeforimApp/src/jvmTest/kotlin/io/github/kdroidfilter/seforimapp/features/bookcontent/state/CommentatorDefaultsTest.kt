package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.domain.LibraryCategories
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The reader's default commentators, kept by names ([CommentatorDefault], [KeptCommentatorDefault], [CommentatorDefaults]). */
class CommentatorDefaultsTest {
    private val rishonim = "ראשונים על התנ״ך"

    private fun category(
        id: Long,
        title: String,
        parent: Long?,
        books: List<CatalogBook> = emptyList(),
        subcategories: List<CatalogCategory> = emptyList(),
    ) = CatalogCategory(id, title, if (parent == null) 0 else 1, parent, books, subcategories)

    private fun book(
        id: Long,
        title: String,
        categoryId: Long,
    ) = CatalogBook(id = id, title = title, categoryId = categoryId, isBaseBook = id < 100)

    // תנ״ך: תורה (בראשית, שמות) and נביאים (יהושע), each with its commentaries; הלכה: טור
    private val catalog =
        PrecomputedCatalog(
            rootCategories =
                listOf(
                    category(
                        1L,
                        "תנ״ך",
                        null,
                        subcategories =
                            listOf(
                                category(2L, "תורה", 1L, listOf(book(10L, "בראשית", 2L), book(11L, "שמות", 2L))),
                                category(3L, "נביאים", 1L, listOf(book(12L, "יהושע", 3L))),
                                category(
                                    4L,
                                    rishonim,
                                    1L,
                                    listOf(
                                        book(100L, "רש״י על בראשית", 4L),
                                        book(101L, "רמב״ן על בראשית", 4L),
                                        book(110L, "רש״י על שמות", 4L),
                                        book(111L, "רמב״ן על שמות", 4L),
                                        book(120L, "רש״י על יהושע", 4L),
                                    ),
                                ),
                            ),
                    ),
                    category(5L, "הלכה", null, listOf(book(20L, "טור", 5L), book(200L, "בית יוסף", 5L))),
                ),
        )

    // {commentary: {the book it comments on: its links}}
    private val links =
        mapOf(
            100L to mapOf(10L to 5),
            101L to mapOf(10L to 3),
            110L to mapOf(11L to 4),
            111L to mapOf(11L to 2),
            120L to mapOf(12L to 2),
            200L to mapOf(20L to 9),
        )

    private val catalogCache = mockk<CatalogCache> { every { getCatalog() } returns catalog }
    private val library = LibraryCategories(catalogCache, mockk(), mockk { every { counted() } returns links })

    private fun defaults() =
        CommentatorDefaults(UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)), library)

    private fun kept(
        path: List<String>,
        title: String,
        at: Long,
        vararg names: String,
    ) = KeptCommentatorDefault(path, title, CommentatorDefault(names = names.toSet()), at)

    @Test
    fun `a selection is kept by names, and read on a book by the ids it has for them`() {
        val onBereshit = CommentatorSelection(remembered = setOf(100L, 101L), groups = setOf(rishonim), excluded = setOf(999L))
        val names = mapOf(100L to "רש״י", 101L to "רמב״ן")

        val default = CommentatorDefault.of(onBereshit) { names[it] }

        assertEquals(CommentatorDefault(groups = setOf(rishonim), names = setOf("רש״י", "רמב״ן")), default)
        // יהושע has no רמב״ן: left out
        assertEquals(CommentatorSelection(remembered = setOf(120L), groups = setOf(rishonim)), default.selection(mapOf("רש״י" to 120L)))
    }

    @Test
    fun `a book takes its own default, else its category's last, else one under the category above`() {
        val torah = listOf("תנ״ך", "תורה")
        val all =
            listOf(
                kept(torah, "בראשית", 1, "רש״י"),
                kept(torah, "שמות", 5, "רמב״ן"),
                kept(listOf("תלמוד", "בבלי", "סדר נשים"), "קידושין", 3, "תוספות"),
            )

        fun names(
            path: List<String>,
            title: String,
        ) = KeptCommentatorDefault.forBook(all, path, title)?.default?.names

        assertEquals(setOf("רש״י"), names(torah, "בראשית"))
        // The last one set in the category
        assertEquals(setOf("רמב״ן"), names(torah, "ויקרא"))
        assertEquals(setOf("רמב״ן"), names(listOf("תנ״ך", "נביאים"), "יהושע"))
        // Another seder of the Bavli, but not the Yerushalmi, nor another top category
        assertEquals(setOf("תוספות"), names(listOf("תלמוד", "בבלי", "סדר נזיקין"), "בבא בתרא"))
        assertNull(names(listOf("תלמוד", "ירושלמי", "סדר נשים"), "קידושין"))
        assertNull(names(listOf("הלכה"), "טור"))
        assertNull(KeptCommentatorDefault.forBook(emptyList(), torah, "בראשית"))
    }

    @Test
    fun `a default set on a book is that of the books beside it, by their own commentators`() =
        runBlocking {
            val defaults = defaults()
            assertNull(defaults.of(10L))

            defaults.set(10L, CommentatorSelection(remembered = setOf(100L, 101L)))

            assertEquals(CommentatorSelection(remembered = setOf(100L, 101L)), defaults.of(10L))
            assertEquals(CommentatorSelection(remembered = setOf(110L, 111L)), defaults.of(11L))
            // Under the category above; no רמב״ן there
            assertEquals(CommentatorSelection(remembered = setOf(120L)), defaults.of(12L))
            // Another top category has the library's own
            assertNull(defaults.of(20L))
            assertEquals(1L, defaults.revision.value)
        }

    @Test
    fun `the list is told whether a default applies and whether it is what the book shows`() =
        runBlocking {
            val defaults = defaults()
            val rashi = CommentatorSelection(remembered = setOf(100L))
            // A default set on בראשית is that of the books of the category above תורה
            assertEquals(CommentatorDefaultState(sharedIn = "תנ״ך"), defaults.stateOf(10L, rashi))
            // A top category's book shares one with the books beside it; a book the catalog lacks has none
            assertEquals(CommentatorDefaultState(sharedIn = "הלכה"), defaults.stateOf(20L, rashi))
            assertEquals(CommentatorDefaultState(), defaults.stateOf(999L, rashi))

            defaults.set(10L, rashi)

            val shown = CommentatorDefaultState("תנ״ך", isSet = true, isShown = true)
            assertEquals(shown, defaults.stateOf(10L, rashi))
            assertEquals(shown.copy(isShown = false), defaults.stateOf(10L, rashi.allChecked()))
            // שמות shows its own רש״י by it
            assertEquals(shown, defaults.stateOf(11L, CommentatorSelection(setOf(110L))))
        }

    @Test
    fun `a default of no commentator is one, until it is let go`() =
        runBlocking {
            val defaults = defaults()

            defaults.set(10L, CommentatorSelection())
            assertEquals(CommentatorSelection(), defaults.of(11L))

            // Let go from a book that has it from another
            defaults.clear(11L)
            assertNull(defaults.of(10L))
            assertEquals(2L, defaults.revision.value)
        }

    @Test
    fun `a book's own default stays when one beside it lets its own go`() =
        runBlocking {
            val defaults = defaults()
            defaults.set(10L, CommentatorSelection(remembered = setOf(100L)))
            defaults.set(11L, CommentatorSelection(all = true))

            defaults.clear(11L)

            assertEquals(CommentatorSelection(remembered = setOf(100L)), defaults.of(10L))
            // שמות now has בראשית's
            assertEquals(CommentatorSelection(remembered = setOf(110L)), defaults.of(11L))
        }
}

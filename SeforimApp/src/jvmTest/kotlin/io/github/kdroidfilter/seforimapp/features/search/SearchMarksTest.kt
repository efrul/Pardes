package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Pardes (request 97 of docs/DESKTOP_STAGE3_PLAN.md): a search's marks, the tabs picked above its results and the
// checks of its tree of books alike ("סימון לבד יהיה כמו בסימון טאב של קטגוריה - רק יסנן ויאפשר מיד חיפוש מחדש - קוד
// משותף"); the whole library's tree it shows ("שיוצג תמיד העץ המלא, גם מה שלא נמצא בו"); and the tree shown in a tab
// alone ("סגור ברירת מחדל גם אם לשונית קודמת הוא פתוח").
class SearchMarksTest {
    private fun book(
        id: Long,
        category: Long,
    ) = CatalogBook(id = id, title = "ספר $id", categoryId = category)

    private fun category(
        id: Long,
        parent: Long?,
        books: List<Long> = emptyList(),
        under: List<CatalogCategory> = emptyList(),
    ) = CatalogCategory(id, "קטגוריה $id", level = 0, parentId = parent, books = books.map { book(it, id) }, subcategories = under)

    // תנ"ך (1: a book of its own, תורה with two books and its commentaries, נביאים) and תלמוד (2: בבלי)
    private val roots =
        listOf(
            category(
                1,
                null,
                books = listOf(101),
                under =
                    listOf(
                        category(11, 1, books = listOf(111, 112), under = listOf(category(12, 11, books = listOf(121)))),
                        category(13, 1, books = listOf(131)),
                    ),
            ),
            category(2, null, under = listOf(category(21, 2, books = listOf(211, 212)))),
        )
    private val shape = LibraryShape(roots)
    private val none = SearchMarks()

    @Test
    fun `a category is marked with all those under it, and is where the text is searched again`() {
        val marks = none.category(1, true, shape)
        assertEquals(setOf(1L, 11L, 12L, 13L), marks.categories)
        assertEquals(SearchScope.Category(1), shape.scopeOf(marks), "a tab's subcategories are picked with it")
        assertNull(shape.scopeOf(none))
        assertEquals(none, marks.category(1, false, shape))
    }

    @Test
    fun `several marks are searched together, in the library's order`() {
        val marks = none.category(21, true, shape).category(13, true, shape).book(101, true, shape)
        assertEquals(SearchScope.Multi(bookIds = listOf(101L), categoryIds = listOf(13L, 21L)), shape.scopeOf(marks))
        assertEquals(SearchScope.Book(211), shape.scopeOf(none.book(211, true, shape)), "one book alone is the bar's book")
    }

    @Test
    fun `a category unmarked under a marked one opens it`() {
        val marks = none.category(1, true, shape).category(12, false, shape)
        assertEquals(setOf(13L), marks.categories, "the others under it stay marked")
        assertEquals(setOf(101L, 111L, 112L), marks.books, "its own books, and those of the categories between, one by one")
        assertEquals(SearchScope.Multi(bookIds = listOf(101L, 111L, 112L), categoryIds = listOf(13L)), shape.scopeOf(marks))
    }

    @Test
    fun `a book unmarked under a marked category leaves the category's others`() {
        val marks = none.category(11, true, shape).book(111, false, shape)
        assertEquals(setOf(12L), marks.categories)
        assertEquals(setOf(112L), marks.books)
        assertEquals(none, none.book(211, true, shape).book(211, false, shape))
    }

    @Test
    fun `a book under a marked category is marked with it`() {
        val marks = none.category(11, true, shape)
        assertEquals(marks, marks.book(111, true, shape))
        assertTrue(
            none
                .book(111, true, shape)
                .category(11, true, shape)
                .books
                .isEmpty(),
            "the category takes its book in",
        )
    }

    @Test
    fun `without a catalog a mark is its category or its book alone`() {
        val marks = none.category(5, true, null).book(7, true, null)
        assertEquals(SearchMarks(setOf(5L), setOf(7L)), marks)
        assertEquals(SearchScope.Multi(bookIds = listOf(7L), categoryIds = listOf(5L)), marks.scope())
        assertEquals(SearchScope.Category(5), marks.book(7, false, null).scope())
    }

    @Test
    fun `the tree shows the whole library, with what was found where it was`() {
        fun found(
            id: Long,
            count: Int,
            children: List<SearchResultViewModel.SearchTreeCategory> = emptyList(),
            books: Map<Long, Int> = emptyMap(),
        ) = SearchResultViewModel.SearchTreeCategory(
            category =
                io.github.kdroidfilter.seforimlibrary.core.models
                    .Category(id = id, title = "קטגוריה $id"),
            count = count,
            children = children,
            books = books.map { (bookId, n) -> SearchResultViewModel.SearchTreeBook(shapeBook(bookId), n) },
        )
        val tree = libraryTree(roots, listOf(found(1, 5, children = listOf(found(11, 5, books = mapOf(112L to 5))))))
        assertEquals(listOf(1L, 2L), tree.map { it.category.id }, "every top category, in the library's order")
        val torah = tree[0].children[0]
        assertEquals(5, tree[0].count)
        assertEquals(listOf(111L to 0, 112L to 5), torah.books.map { it.book.id to it.count })
        assertEquals(0, torah.children.single().count, "a category nothing was found in is listed too")
        assertEquals(
            listOf(211L, 212L),
            tree[1]
                .children
                .single()
                .books
                .map { it.book.id },
        )
        assertEquals(2, libraryTree(roots, emptyList()).size, "and with nothing found at all")
    }

    private fun shapeBook(id: Long) =
        io.github.kdroidfilter.seforimlibrary.core.models
            .Book(id = id, categoryId = 0, sourceId = 0, title = "ספר $id")

    @Test
    fun `the bar follows the marks of a search in the library, in categories or in several books`() {
        assertTrue(SearchScope.Global.barFollowsMarks())
        assertTrue(SearchScope.Category(1).barFollowsMarks())
        assertTrue(SearchScope.Multi(bookIds = listOf(101L), categoryIds = listOf(2L)).barFollowsMarks())
        assertFalse(SearchScope.Book(101).barFollowsMarks(), "inside one book the bar is the reader's own choice")
        assertFalse(SearchScope.Author(7).barFollowsMarks())
    }

    @Test
    fun `the categories a search ran in are its marks, when it ran in categories alone`() {
        assertEquals(emptyList(), SearchScope.Global.categoriesAlone())
        assertEquals(listOf(2L), SearchScope.Category(2).categoriesAlone())
        assertEquals(listOf(13L, 21L), SearchScope.Multi(bookIds = emptyList(), categoryIds = listOf(13L, 21L)).categoriesAlone())
        assertNull(SearchScope.Multi(bookIds = listOf(101L), categoryIds = listOf(21L)).categoriesAlone(), "books cannot be marked")
        assertNull(SearchScope.Book(101).categoriesAlone())
    }

    @Test
    fun `a category searched in its base books alone stays so while it is marked`() {
        val wide = SearchScope.Multi(bookIds = emptyList(), categoryIds = listOf(1L, 2L), baseOnlyCategoryIds = listOf(2L))
        val both = SearchScope.Multi(bookIds = emptyList(), categoryIds = listOf(2L, 13L))
        assertEquals(both.copy(baseOnlyCategoryIds = listOf(2L)), both.baseOnlyAs(wide))
        assertEquals(SearchScope.Category(2, baseBooksOnly = true), SearchScope.Category(2).baseOnlyAs(wide))
        assertEquals(SearchScope.Category(1), SearchScope.Category(1).baseOnlyAs(SearchScope.Category(1)))
        assertEquals(SearchScope.Category(1, baseBooksOnly = true), SearchScope.Category(1).baseOnlyAs(SearchScope.Category(1, true)))
        assertEquals(SearchScope.Book(101), SearchScope.Book(101).baseOnlyAs(wide))
    }

    @Test
    fun `a search's tree is shown in its own tab, closed until opened there`() {
        val trees = SearchTrees()
        assertFalse(trees.isShown("a"))
        trees.toggle("a")
        assertTrue(trees.isShown("a"))
        assertFalse(trees.isShown("b"), "another tab's is closed")
        trees.toggle("a")
        assertFalse(trees.isShown("a"))
    }
}

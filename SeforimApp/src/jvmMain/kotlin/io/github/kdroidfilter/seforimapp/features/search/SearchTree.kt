package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.toBook
import io.github.kdroidfilter.seforimlibrary.core.models.toCategory

// Pardes (request 97 of docs/DESKTOP_STAGE3_PLAN.md: "שיכולו לפתוח את עץ הספרים בחיפוש, ברירת מחדל סגור - יפתח לפי
// בחירה. בחירה שם תהיה כמו בטאבים - תסמן בשורת החיפוש ויוכלו לחפש שוב"): a search's tree of books, Zayit's own pane
// (SearchCategoryTreePanel), which its category tabs took the place of. It is closed until the reader opens it, by the
// book tree's own button in the start bar, which a search's results now have (the user's word on the testers' build's
// button beside the search bar: "למה אייקון פתיחת העץ לא במקום הרגיל שלו?"), or by its key. It shows the whole library,
// with what the search found in each category and book, and what is checked in it is marked as a tab picked is.

/**
 * Which search tabs show their tree of books, by the tab's id: a tab's own, closed until the reader opens it there,
 * whatever another tab has (the user's word on the testers' build, where one setting showed it in every tab: "עץ
 * הספרים יהיה סגור ברירת מחדל גם אם לשונית קודמת הוא פתוח"). Kept while the app runs.
 */
@Inject
@SingleIn(AppScope::class)
class SearchTrees {
    private val shown = mutableStateMapOf<String, Boolean>()

    /** Whether the search tab [tabId] shows its tree. */
    fun isShown(tabId: String): Boolean = shown[tabId] == true

    /** The tree of the search tab [tabId] shown, or hidden when it was. */
    fun toggle(tabId: String) {
        shown[tabId] = !isShown(tabId)
    }
}

/**
 * What a search has marked, to show alone and to be searched again in: its [categories], a category marked with all
 * those under it, and its [books]. The tabs picked above the results and the checks of the tree of books are both
 * these, and go one way (the user's word on the testers' build: "סימון לבד יהיה כמו בסימון טאב של קטגוריה - רק יסנן
 * ויאפשר מיד חיפוש מחדש - קוד משותף").
 */
internal data class SearchMarks(
    val categories: Set<Long> = emptySet(),
    val books: Set<Long> = emptySet(),
) {
    /** These with the category [id] marked or no longer, as the library's [shape] has it; alone, where it is not known. */
    fun category(
        id: Long,
        marked: Boolean,
        shape: LibraryShape?,
    ): SearchMarks =
        when {
            shape == null -> copy(categories = if (marked) categories + id else categories - id)
            marked -> shape.with(this, id)
            else -> shape.without(this, id)
        }

    /** These with the book [id] marked or no longer. */
    fun book(
        id: Long,
        marked: Boolean,
        shape: LibraryShape?,
    ): SearchMarks =
        when {
            shape == null -> copy(books = if (marked) books + id else books - id)
            marked -> shape.withBook(this, id)
            else -> shape.withoutBook(this, id)
        }

    /** Where these search, with nothing known of the library: every category and book marked. */
    fun scope(): SearchScope? = scopeOf(categories.toList(), books.toList())
}

/**
 * The categories a search of [this] scope runs in when they are all it runs in: none for the whole library, one, or
 * several together; null for any other scope (a book, an author's books, books among the categories). There the marks
 * and the bar are one: what is marked is where the bar searches.
 */
internal fun SearchScope.categoriesAlone(): List<Long>? =
    when (this) {
        SearchScope.Global -> emptyList()
        is SearchScope.Category -> listOf(categoryId)
        is SearchScope.Multi -> categoryIds.takeIf { bookIds.isEmpty() }
        else -> null
    }

/**
 * Whether the bar of a search of [this] scope shows the search's marks: the whole library's, categories', or several
 * books and categories'. Inside one book, or an author's books, the bar stays the reader's own choice.
 */
internal fun SearchScope.barFollowsMarks(): Boolean =
    this == SearchScope.Global || this is SearchScope.Category || this is SearchScope.Multi

/**
 * [this], where a search's marks search, with each of its categories that [wide], the search's own scope, has in its
 * base books alone searched so again: the status line's choice stays with a category as long as it is marked.
 */
internal fun SearchScope.baseOnlyAs(wide: SearchScope): SearchScope {
    val baseOnly =
        when (wide) {
            is SearchScope.Category -> listOfNotNull(wide.categoryId.takeIf { wide.baseBooksOnly })
            is SearchScope.Multi -> wide.baseOnlyCategoryIds
            else -> emptyList()
        }
    return when (this) {
        is SearchScope.Category -> copy(baseBooksOnly = categoryId in baseOnly)
        is SearchScope.Multi -> copy(baseOnlyCategoryIds = categoryIds.filter { it in baseOnly })
        else -> this
    }
}

/** Where [categories] and [books] search: a category, a book, several together; null with none. */
internal fun scopeOf(
    categories: List<Long>,
    books: List<Long>,
): SearchScope? =
    when {
        categories.isEmpty() && books.isEmpty() -> null
        books.isEmpty() && categories.size == 1 -> SearchScope.Category(categories.single())
        categories.isEmpty() && books.size == 1 -> SearchScope.Book(books.single())
        else -> SearchScope.Multi(bookIds = books, categoryIds = categories)
    }

/**
 * The library's shape, for a search's marks and its tree of books: its catalog's top categories ([roots], each with
 * all it holds), each category's parent, the categories right under it and its own books, and each book's category, in
 * the library's order.
 */
internal class LibraryShape(
    val roots: List<CatalogCategory>,
) {
    private val parents = HashMap<Long, Long?>()
    private val children = HashMap<Long, List<Long>>()
    private val ownBooks = HashMap<Long, List<Long>>()
    private val categoryOf = HashMap<Long, Long>()
    private val places = HashMap<Long, Int>()

    init {
        fun walk(category: CatalogCategory) {
            places[category.id] = places.size
            parents[category.id] = category.parentId
            children[category.id] = category.subcategories.map { it.id }
            ownBooks[category.id] = category.books.map { it.id }
            category.books.forEach { book ->
                categoryOf[book.id] = category.id
                places[-book.id] = places.size
            }
            category.subcategories.forEach(::walk)
        }
        roots.forEach(::walk)
    }

    // The category [id] and all those under it
    private fun under(id: Long): List<Long> = listOf(id) + children[id].orEmpty().flatMap(::under)

    // The categories above [id], the nearest first
    private fun above(id: Long): List<Long> = generateSequence(parents[id]) { parents[it] }.toList()

    /** [marks] with the category [id], and so every category under it; the books marked under it are its own already. */
    fun with(
        marks: SearchMarks,
        id: Long,
    ): SearchMarks {
        val all = under(id)
        return SearchMarks(marks.categories + all, marks.books - all.flatMapTo(HashSet()) { ownBooks[it].orEmpty() })
    }

    /**
     * [marks] without the category [id] and what is under it. Under a marked category, that one is marked no longer
     * either: its other categories stay marked, and its own books are marked one by one.
     */
    fun without(
        marks: SearchMarks,
        id: Long,
    ): SearchMarks {
        val gone = under(id)
        val opened = above(id).filter { it in marks.categories }
        return SearchMarks(
            categories = marks.categories - gone.toSet() - opened.toSet(),
            books = marks.books - gone.flatMapTo(HashSet()) { ownBooks[it].orEmpty() } + opened.flatMap { ownBooks[it].orEmpty() },
        )
    }

    /** [marks] with the book [id], unless a category it is under is marked. */
    fun withBook(
        marks: SearchMarks,
        id: Long,
    ): SearchMarks = if (categoryOf[id] in marks.categories) marks else marks.copy(books = marks.books + id)

    /** [marks] without the book [id]: marked with its category, that one's other books are marked one by one. */
    fun withoutBook(
        marks: SearchMarks,
        id: Long,
    ): SearchMarks {
        val category = categoryOf[id]
        val opened = (listOfNotNull(category) + category?.let(::above).orEmpty()).filter { it in marks.categories }
        return SearchMarks(marks.categories - opened.toSet(), marks.books + opened.flatMap { ownBooks[it].orEmpty() } - id)
    }

    /**
     * Where [marks] search: their categories that are under none of them, and their books under none, each in the
     * library's order; null with nothing marked.
     */
    fun scopeOf(marks: SearchMarks): SearchScope? =
        scopeOf(
            categories = marks.categories.filter { parents[it] !in marks.categories }.sortedBy { places[it] ?: Int.MAX_VALUE },
            books = marks.books.filter { categoryOf[it] !in marks.categories }.sortedBy { places[-it] ?: Int.MAX_VALUE },
        )
}

/**
 * The library's whole tree as a search's tree of books shows it ([roots], its catalog's): every category and every
 * book, in the library's order, each with what the search found in it ([found], the results' own tree) and none where
 * it found nothing. So that what was not found in can be checked too, and searched again in (the user's word on the
 * testers' build: "שיוצג תמיד העץ המלא, גם מה שלא נמצא בו כדי שיוכלו לסמן ולחפש בו שוב").
 */
internal fun libraryTree(
    roots: List<CatalogCategory>,
    found: List<SearchResultViewModel.SearchTreeCategory>,
): List<SearchResultViewModel.SearchTreeCategory> {
    val inCategory = HashMap<Long, Int>()
    val inBook = HashMap<Long, Int>()

    fun count(node: SearchResultViewModel.SearchTreeCategory) {
        inCategory[node.category.id] = node.count
        node.books.forEach { inBook[it.book.id] = it.count }
        node.children.forEach(::count)
    }
    found.forEach(::count)

    fun node(category: CatalogCategory): SearchResultViewModel.SearchTreeCategory =
        SearchResultViewModel.SearchTreeCategory(
            category = category.toCategory(),
            count = inCategory[category.id] ?: 0,
            children = category.subcategories.map(::node),
            books = category.books.map { SearchResultViewModel.SearchTreeBook(it.toBook(), inBook[it.id] ?: 0) },
        )
    return roots.map(::node)
}

/**
 * A search tab's tree of books for its bars and its keys: whether it is [shown], and the tab's events ([onEvent]) with
 * the book tree's toggle turned to it.
 */
internal class SearchTreeToggle(
    val shown: Boolean,
    val onEvent: (BookContentEvent) -> Unit,
)

/**
 * The tree of books of the search tab [tabId], whose events are [onEvent]: the book tree's own button and key (the
 * start bar's "ספרים", Ctrl/Cmd+B) and its pane's close show it and hide it, where a book's tab shows the library's
 * tree.
 */
@Composable
internal fun rememberSearchTreeToggle(
    tabId: String,
    onEvent: (BookContentEvent) -> Unit,
): SearchTreeToggle {
    val trees = LocalAppGraph.current.searchTrees
    val current by rememberUpdatedState(onEvent)
    val events =
        remember(trees, tabId) {
            { event: BookContentEvent -> if (event == BookContentEvent.ToggleBookTree) trees.toggle(tabId) else current(event) }
        }
    return SearchTreeToggle(trees.isShown(tabId), events)
}

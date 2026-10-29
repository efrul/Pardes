package io.github.kdroidfilter.seforimapp.features.search.domain

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimlibrary.core.models.Book

/** One thing the bar searches in, picked from its suggestions (Tab): a book, or a category with its subcategories. */
@Immutable
sealed interface ScopeItem {
    /** Its name on the bar's chip. */
    val label: String

    data class BookItem(
        val book: Book,
    ) : ScopeItem {
        override val label: String get() = book.title
    }

    data class CategoryItem(
        val category: CategoryHit,
    ) : ScopeItem {
        override val label: String get() = category.label
    }
}

/**
 * The books and categories picked in the bar, each once, in the order picked: together they are where the text is
 * searched. One book alone is the bar's book, with its table of contents to pick from.
 */
object ScopeItems {
    fun add(
        items: List<ScopeItem>,
        item: ScopeItem,
    ): List<ScopeItem> = if (items.any { it.sameAs(item) }) items else items + item

    fun remove(
        items: List<ScopeItem>,
        item: ScopeItem,
    ): List<ScopeItem> = items.filterNot { it.sameAs(item) }

    /** Where [items] search: everywhere, one book, one category, or all of them together. */
    fun scopeOf(items: List<ScopeItem>): SearchScope {
        val single = items.singleOrNull()
        return when {
            items.isEmpty() -> SearchScope.Global
            single is ScopeItem.BookItem -> SearchScope.Book(single.book.id)
            single is ScopeItem.CategoryItem -> SearchScope.Category(single.category.id)
            else ->
                SearchScope.Multi(
                    bookIds = items.filterIsInstance<ScopeItem.BookItem>().map { it.book.id },
                    categoryIds = items.filterIsInstance<ScopeItem.CategoryItem>().map { it.category.id },
                )
        }
    }

    // The same book or category, whatever else its object holds
    private fun ScopeItem.sameAs(other: ScopeItem): Boolean =
        when (this) {
            is ScopeItem.BookItem -> other is ScopeItem.BookItem && other.book.id == book.id
            is ScopeItem.CategoryItem -> other is ScopeItem.CategoryItem && other.category.id == category.id
        }
}

package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Where a search runs: the whole library, one category's or author's books, several books and categories, or one book or TOC entry. */
@Serializable
@Immutable
sealed interface SearchScope {
    @Serializable
    data object Global : SearchScope

    /**
     * All the books of one category and its subcategories, base books or not (`תלמוד`, `רש"י על התורה`), or
     * [baseBooksOnly] its base books alone: the texts without their commentaries (תלמוד's tractates).
     */
    @Serializable
    data class Category(
        val categoryId: Long,
        // Appended last: the session is ProtoBuf, numbered by declaration order
        val baseBooksOnly: Boolean = false,
    ) : SearchScope

    @Serializable
    data class Book(
        val bookId: Long,
    ) : SearchScope

    @Serializable
    data class Toc(
        val bookId: Long,
        val tocId: Long,
    ) : SearchScope

    /** All the books of one author, base books or not. */
    @Serializable
    data class Author(
        val authorId: Long,
    ) : SearchScope

    /**
     * Several books and categories together (each category with its subcategories), base books or not; those of
     * [categoryIds] also in [baseOnlyCategoryIds] in their base books alone.
     */
    @Serializable
    data class Multi(
        val bookIds: List<Long>,
        val categoryIds: List<Long>,
        // Appended last: the session is ProtoBuf, numbered by declaration order
        val baseOnlyCategoryIds: List<Long> = emptyList(),
    ) : SearchScope
}

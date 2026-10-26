package io.github.kdroidfilter.seforimapp.android.data

import kotlinx.serialization.Serializable

/**
 * Where a search runs: the whole library, one author's books, or one book or TOC entry.
 *
 * Mirrors desktop 0.6.10 `navigation/src/commonMain/kotlin/io/github/kdroidfilter/seforim/tabs/SearchScope.kt`, whose
 * only change since 0.6.1 is [Author] (0.6.5 1723de06). The navigation module is not compiled into the Android app.
 */
@Serializable
sealed interface SearchScope {
    @Serializable
    data object Global : SearchScope

    /** Read back only, from history rows saved when a search could be scoped to a category: it runs everywhere now. */
    @Serializable
    data class Category(val categoryId: Long) : SearchScope

    @Serializable
    data class Book(val bookId: Long) : SearchScope

    @Serializable
    data class Toc(val bookId: Long, val tocId: Long) : SearchScope

    /** All the books of one author, base books or not. */
    @Serializable
    data class Author(val authorId: Long) : SearchScope
}

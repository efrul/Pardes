package io.github.kdroidfilter.seforimapp.android.data

import kotlinx.serialization.Serializable

/**
 * Where a search runs: the whole library, or one book or TOC entry.
 *
 * Mirrors desktop 0.6.1 `navigation/src/commonMain/kotlin/io/github/kdroidfilter/seforim/tabs/SearchScope.kt`
 * (the navigation module is not compiled into the Android app).
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
}

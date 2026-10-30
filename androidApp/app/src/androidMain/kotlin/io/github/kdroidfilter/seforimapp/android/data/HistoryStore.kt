package io.github.kdroidfilter.seforimapp.android.data

import android.os.Build
import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.db.Visit_history
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** One deduplicated history entry (Chrome-like: last visit wins, count accumulates). */
@Stable
data class VisitEntry(
    val key: String,
    val kind: VisitKind,
    val bookId: Long?,
    val searchQuery: String?,
    val title: String,
    val visitedAt: Long,
    val visitCount: Long,
    val tocEntryId: Long? = null,
    val lineId: Long? = null,
    val searchScope: SearchScope = SearchScope.Global,
    val searchGlobalExtended: Boolean = false,
)

enum class VisitKind { BOOK, SEARCH }

/**
 * Persistent full visit history (the chrome://history equivalent): every opened book and every executed search,
 * unbounded, in the `visit_history` table of user_settings.db, so a desktop database imported with
 * `push-desktop-library --with-notes` shows its history here. Rows are deduplicated by target key; a revisit bumps
 * `visitedAt`/`visitCount`. Reads are on-demand SQL queries; [revision] bumps on every write so UI can re-run its
 * current query.
 *
 * Verbatim port of desktop 0.6.10 `core/history/HistoryStore.kt`: 0.6.1's with the 0.6.5 author scope (1723de06: the
 * author's id is in the key only), its only change since.
 */
class HistoryStore(database: UserSettingsDb) {
    private val queries = database.visitHistoryQueries
    private val legacyQueries = database.upsertLegacyQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /**
     * Records a book visit at an optional TOC position (Chrome records precise URLs, we record book + chapter): one
     * history row per (book, tocEntry), with the line to reopen at.
     */
    suspend fun recordBookVisit(
        bookId: Long,
        title: String,
        timestamp: Long,
        tocEntryId: Long? = null,
        lineId: Long? = null,
    ): Unit = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext
        upsertVisit(
            key = if (tocEntryId != null) "book:$bookId:$tocEntryId" else "book:$bookId",
            kind = KIND_BOOK,
            bookId = bookId,
            searchQuery = null,
            title = title,
            visitedAt = timestamp,
            tocEntryId = tocEntryId,
            lineId = lineId,
            searchCategoryId = null,
            searchBookId = null,
            searchTocId = null,
            searchExtended = false,
        )
        _revision.update { it + 1 }
    }

    /**
     * Records an executed search with the [scope] it ran in: one history row per (query, scope, extended), titled
     * with [scopeLabel] (the scope's name) so a scoped search reads apart from the same query elsewhere.
     */
    suspend fun recordSearchVisit(
        query: String,
        scope: SearchScope,
        globalExtended: Boolean,
        scopeLabel: String?,
        timestamp: Long,
    ): Unit = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext
        upsertVisit(
            key = searchKey(q, scope, globalExtended),
            kind = KIND_SEARCH,
            bookId = null,
            searchQuery = q,
            title = if (scopeLabel.isNullOrBlank()) q else "$q — $scopeLabel",
            visitedAt = timestamp,
            tocEntryId = null,
            lineId = null,
            searchCategoryId = (scope as? SearchScope.Category)?.categoryId,
            searchBookId = when (scope) {
                is SearchScope.Book -> scope.bookId
                is SearchScope.Toc -> scope.bookId
                else -> null
            },
            searchTocId = (scope as? SearchScope.Toc)?.tocId,
            searchExtended = globalExtended,
        )
        _revision.update { it + 1 }
    }

    /**
     * Desktop's upsertVisit. UPSERT needs SQLite 3.24: Android 8-10 (3.18 to 3.22) make the same write in two statements
     * (UpsertLegacy.sq).
     */
    private fun upsertVisit(
        key: String,
        kind: String,
        bookId: Long?,
        searchQuery: String?,
        title: String,
        visitedAt: Long,
        tocEntryId: Long?,
        lineId: Long?,
        searchCategoryId: Long?,
        searchBookId: Long?,
        searchTocId: Long?,
        searchExtended: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            queries.upsertVisit(
                key = key,
                kind = kind,
                bookId = bookId,
                searchQuery = searchQuery,
                title = title,
                visitedAt = visitedAt,
                tocEntryId = tocEntryId,
                lineId = lineId,
                searchCategoryId = searchCategoryId,
                searchBookId = searchBookId,
                searchTocId = searchTocId,
                searchExtended = searchExtended,
            )
        } else {
            legacyQueries.upsertVisitLegacy(
                key = key,
                kind = kind,
                bookId = bookId,
                searchQuery = searchQuery,
                title = title,
                visitedAt = visitedAt,
                tocEntryId = tocEntryId,
                lineId = lineId,
                searchCategoryId = searchCategoryId,
                searchBookId = searchBookId,
                searchTocId = searchTocId,
                searchExtended = searchExtended,
            )
        }
    }

    /** Most recent entries, or entries whose title matches [query] when non-blank. */
    suspend fun query(query: String, limit: Int): List<VisitEntry> = withContext(Dispatchers.IO) {
        val rows = if (query.isBlank()) {
            queries.selectRecent(limit.toLong()).executeAsList()
        } else {
            queries.searchByTitle(query = query.trim(), limitCount = limit.toLong()).executeAsList()
        }
        rows.map { it.toEntry() }
    }

    suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        queries.deleteByKey(key)
        _revision.update { it + 1 }
    }

    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        queries.deleteAll()
        _revision.update { it + 1 }
    }

    private fun Visit_history.toEntry(): VisitEntry = VisitEntry(
        key = key,
        kind = if (kind == KIND_SEARCH) VisitKind.SEARCH else VisitKind.BOOK,
        bookId = bookId,
        searchQuery = searchQuery,
        title = title,
        visitedAt = visitedAt,
        visitCount = visitCount,
        tocEntryId = tocEntryId,
        lineId = lineId,
        searchScope = when {
            searchTocId != null && searchBookId != null -> SearchScope.Toc(bookId = searchBookId, tocId = searchTocId)
            searchBookId != null -> SearchScope.Book(searchBookId)
            searchCategoryId != null -> SearchScope.Category(searchCategoryId)
            // No column for an author or several books and categories: they are in the key only
            else -> authorIdInKey(key)?.let(SearchScope::Author) ?: multiScopeInKey(key) ?: SearchScope.Global
        },
        searchGlobalExtended = searchExtended,
    )

    private fun searchKey(query: String, scope: SearchScope, globalExtended: Boolean): String {
        val scopeKey = when (scope) {
            SearchScope.Global -> "g"
            is SearchScope.Category -> "c${scope.categoryId}"
            is SearchScope.Multi -> "$MULTI_KEY${scope.bookIds.joinToString(".")}_${scope.categoryIds.joinToString(".")}"
            is SearchScope.Book -> "b${scope.bookId}"
            is SearchScope.Toc -> "t${scope.tocId}"
            is SearchScope.Author -> "$AUTHOR_KEY${scope.authorId}"
        } + if (globalExtended) "x" else ""
        return "search:$scopeKey:$query"
    }

    // `search:a150:…` (or `a150x`) → 150
    private fun authorIdInKey(key: String): Long? =
        key
            .removePrefix("$KIND_SEARCH:$AUTHOR_KEY")
            .takeIf { it != key }
            ?.takeWhile(Char::isDigit)
            ?.toLongOrNull()

    // `search:m12.34_56:…` (or `m12.34_56x`) → books 12 and 34, category 56 (Pardes desktop HistoryStore)
    private fun multiScopeInKey(key: String): SearchScope.Multi? {
        val scopeKey = key.removePrefix("$KIND_SEARCH:").takeIf { it != key }?.substringBefore(':')?.takeIf { it.startsWith(MULTI_KEY) } ?: return null
        val body = scopeKey.removePrefix(MULTI_KEY).removeSuffix("x")
        val bookIds = body.substringBefore('_').split('.').mapNotNull(String::toLongOrNull)
        val categoryIds = body.substringAfter('_', "").split('.').mapNotNull(String::toLongOrNull)
        return SearchScope.Multi(bookIds, categoryIds).takeIf { bookIds.isNotEmpty() || categoryIds.isNotEmpty() }
    }

    private companion object {
        const val KIND_BOOK = "book"
        const val KIND_SEARCH = "search"
        const val AUTHOR_KEY = "a"
        const val MULTI_KEY = "m"
    }
}

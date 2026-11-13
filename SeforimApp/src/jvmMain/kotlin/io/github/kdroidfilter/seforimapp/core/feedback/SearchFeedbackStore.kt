package io.github.kdroidfilter.seforimapp.core.feedback

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A grade given to one result of a search, on the evaluations' scale ([SearchFeedbackStore]). */
enum class FeedbackGrade(
    val value: Int,
) {
    /** Exactly what was looked for: the source. */
    EXACT(3),

    /** Answers the search. */
    RELEVANT(2),

    /** Has nothing to do with it. */
    NOT_RELEVANT(0),
    ;

    companion object {
        fun of(value: Int): FeedbackGrade? = entries.firstOrNull { it.value == value }
    }
}

/** One result graded: the search it was a result of (keyed as the history keys it), its line, its place. */
data class SearchFeedback(
    val searchKey: String,
    val query: String,
    val lineId: Long,
    val bookId: Long,
    val grade: FeedbackGrade,
    /** The result's place among the search's results, from 0, as the engine gave them. */
    val rank: Int,
    val scope: SearchScope,
    val extended: Boolean,
    /** The semantic model in use (pardes-model.json's id), null without one. */
    val model: String?,
    val gradedAt: Long,
)

/**
 * Pardes: the grades the user gives search results in feedback mode (Settings › משוב), kept in the user database beside
 * the visit history, for pardes-semantic/feedback/ to turn into training and evaluation data. A search is keyed as
 * [io.github.kdroidfilter.seforimapp.core.history.HistoryStore.searchKey] keys it; each line of its results holds at
 * most one grade.
 */
class SearchFeedbackStore(
    database: UserSettingsDb,
) {
    private val queries = database.searchFeedbackQueries

    /** The grades given to the results of [searchKey]: line to grade. */
    suspend fun grades(searchKey: String): Map<Long, FeedbackGrade> =
        withContext(Dispatchers.IO) {
            queries
                .gradesForSearch(searchKey)
                .executeAsList()
                .mapNotNull { row -> FeedbackGrade.of(row.grade.toInt())?.let { row.lineId to it } }
                .toMap()
        }

    suspend fun save(feedback: SearchFeedback): Unit =
        withContext(Dispatchers.IO) {
            val scope = feedback.scope
            queries.upsertGrade(
                searchKey = feedback.searchKey,
                query = feedback.query,
                lineId = feedback.lineId,
                bookId = feedback.bookId,
                grade = feedback.grade.value.toLong(),
                rank = feedback.rank.toLong(),
                searchCategoryId = (scope as? SearchScope.Category)?.categoryId,
                searchBookId =
                    when (scope) {
                        is SearchScope.Book -> scope.bookId
                        is SearchScope.Toc -> scope.bookId
                        else -> null
                    },
                searchTocId = (scope as? SearchScope.Toc)?.tocId,
                searchExtended = feedback.extended,
                model = feedback.model,
                gradedAt = feedback.gradedAt,
            )
        }

    /** Takes back the grade of [lineId] in [searchKey]. */
    suspend fun clear(
        searchKey: String,
        lineId: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteGrade(searchKey, lineId)
        }
}

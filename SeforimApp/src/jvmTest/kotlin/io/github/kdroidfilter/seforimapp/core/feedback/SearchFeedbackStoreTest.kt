package io.github.kdroidfilter.seforimapp.core.feedback

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchFeedbackStoreTest {
    private val database = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))
    private val store = SearchFeedbackStore(database)
    private val history = HistoryStore(database)

    private fun feedback(
        key: String,
        lineId: Long,
        grade: FeedbackGrade,
        scope: SearchScope = SearchScope.Global,
    ) = SearchFeedback(key, "שנים אוחזין", lineId, bookId = 7, grade, rank = 0, scope, extended = false, model = "m", gradedAt = 1)

    @Test
    fun `a search's grades come back by its key, each line's last one`() =
        runBlocking {
            val key = history.searchKey("שנים אוחזין", SearchScope.Global, globalExtended = false)
            store.save(feedback(key, 10, FeedbackGrade.EXACT))
            store.save(feedback(key, 11, FeedbackGrade.NOT_RELEVANT))
            store.save(feedback(key, 11, FeedbackGrade.RELEVANT))
            assertEquals(mapOf(10L to FeedbackGrade.EXACT, 11L to FeedbackGrade.RELEVANT), store.grades(key))
            assertEquals(2L, store.count.value)
        }

    @Test
    fun `the same query in another scope is another search, and a grade taken back is gone`() =
        runBlocking {
            val global = history.searchKey("שנים אוחזין", SearchScope.Global, globalExtended = false)
            val inBook = history.searchKey("שנים אוחזין", SearchScope.Book(7), globalExtended = false)
            store.save(feedback(global, 10, FeedbackGrade.RELEVANT))
            store.save(feedback(inBook, 10, FeedbackGrade.NOT_RELEVANT, SearchScope.Book(7)))
            assertEquals(mapOf(10L to FeedbackGrade.RELEVANT), store.grades(global))
            store.clear(inBook, 10)
            assertEquals(emptyMap(), store.grades(inBook))
            assertEquals(1L, store.refreshCount())
        }
}

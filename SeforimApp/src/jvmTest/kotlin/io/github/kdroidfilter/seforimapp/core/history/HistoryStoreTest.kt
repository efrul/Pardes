package io.github.kdroidfilter.seforimapp.core.history

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryStoreTest {
    @Test
    fun `a search scoped to an author reopens in their books`() =
        runBlocking {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)
            val store = HistoryStore(UserSettingsDb(driver))
            store.recordSearchVisit("גט", SearchScope.Author(150L), globalExtended = false, scopeLabel = "רשב\"א", timestamp = 2)
            store.recordSearchVisit("גט", SearchScope.Author(150L), globalExtended = true, scopeLabel = "רשב\"א", timestamp = 1)
            store.recordSearchVisit("גט", SearchScope.Global, globalExtended = false, scopeLabel = null, timestamp = 0)
            assertEquals(
                listOf(SearchScope.Author(150L), SearchScope.Author(150L), SearchScope.Global),
                store.query("", limit = 10).map { it.searchScope },
            )
        }

    @Test
    fun `a search in a category, or in several books and categories, reopens there`() =
        runBlocking {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)
            val store = HistoryStore(UserSettingsDb(driver))
            val several = SearchScope.Multi(bookIds = listOf(12L, 34L), categoryIds = listOf(1142L))
            val label = "ברכות, שבת, רש״י › תורה"
            store.recordSearchVisit("אור", several, globalExtended = false, scopeLabel = label, timestamp = 2)
            store.recordSearchVisit("אור", SearchScope.Category(12L), globalExtended = false, scopeLabel = "תלמוד", timestamp = 1)
            val categoryOnly = SearchScope.Multi(emptyList(), listOf(7L))
            store.recordSearchVisit("אור", categoryOnly, globalExtended = true, scopeLabel = null, timestamp = 0)
            assertEquals(
                listOf(several, SearchScope.Category(12L), categoryOnly),
                store.query("", limit = 10).map { it.searchScope },
            )
        }
}

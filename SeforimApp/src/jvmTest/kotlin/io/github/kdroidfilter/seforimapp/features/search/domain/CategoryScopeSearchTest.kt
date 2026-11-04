package io.github.kdroidfilter.seforimapp.features.search.domain

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.core.feedback.SearchFeedbackStore
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** A search in a category's base books alone (תלמוד's tractates) over the dev database; skipped when it is absent. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryScopeSearchTest : SearchIntegrationTestBase() {
    private val store = TabPersistedStateStore()

    @BeforeTest
    override fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        super.setup()
    }

    @AfterTest
    override fun tearDown() {
        super.tearDown()
        Dispatchers.resetMain()
    }

    @Test
    fun `a category in its base books alone finds in its texts only, not their commentaries`() =
        runBlocking {
            skipIfNoIndex()
            val repository = requireRepository()
            // No catalog here: the categories' books come from the database
            val categories = LibraryCategories(mockk { every { getCatalog() } returns null }, repository, mockk())
            val tractates = categories.bookIds(TALMUD, baseBooksOnly = true).toSet()
            assertTrue(tractates.isNotEmpty())

            val desktopManager = mockk<DesktopManager>(relaxed = true)
            every { desktopManager.tabExistsFlow(any()) } returns flowOf(true)
            store.update(TAB) {
                it.copy(search = SearchPersistedState(query = "קל וחומר").withScope(SearchScope.Category(TALMUD, baseBooksOnly = true)))
            }
            val vm =
                SearchResultViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(StateKeys.TAB_ID to TAB)),
                    persistedStore = store,
                    repository = repository,
                    lucene = requireSearchEngine(),
                    titleUpdateManager = TabTitleUpdateManager(),
                    desktopManager = desktopManager,
                    historyStore =
                        HistoryStore(
                            UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)),
                        ),
                    feedbackStore =
                        SearchFeedbackStore(
                            UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)),
                        ),
                    appSettings = testAppSettings(),
                    lookup = mockk(relaxed = true),
                    libraryCategories = categories,
                )
            val done = withTimeout(60_000) { vm.uiState.first { !it.isLoading && it.results.isNotEmpty() } }
            assertTrue(
                done.results.all { it.bookId in tractates },
                done.results
                    .map { it.bookTitle }
                    .distinct()
                    .toString(),
            )

            // Those searches run off this thread: end them before tearDown closes the index under them
            vm.viewModelScope.coroutineContext.job
                .cancelAndJoin()
        }

    private companion object {
        const val TALMUD = 12L
        const val TAB = "category-scope-test"
    }
}

package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.core.feedback.SearchFeedbackStore
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.search.domain.CommentaryLinks
import io.github.kdroidfilter.seforimapp.features.search.domain.LibraryCategories
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.search.PardesLexicon
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimapp.framework.search.withSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.vinceglb.filekit.FileKit
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

private val FLOW_QUERIES =
    listOf(
        "קל וחומר",
        "המפקיד",
        "ארבעה אבות נזיקין",
        "תפילין",
        "שכח ולא התפלל ערבית",
    )

// A scope the bar passes, as the app keeps it, and whether its tabs and the library's order are tried too
private class FlowScope(
    val label: String,
    val scope: SearchScope,
    val catalog: Boolean = false,
)

private val FLOW_SCOPES =
    listOf(
        FlowScope("כל הספרייה", SearchScope.Global, catalog = true),
        FlowScope("בבא בתרא", SearchScope.Book(124)),
        FlowScope("שלוש הבבות", SearchScope.Multi(bookIds = listOf(122, 123, 124), categoryIds = emptyList())),
        FlowScope("סדר נזיקין", SearchScope.Category(17)),
        FlowScope("תלמוד", SearchScope.Category(12), catalog = true),
        FlowScope("הלכה", SearchScope.Category(44), catalog = true),
        FlowScope("אחרונים על הבבלי", SearchScope.Category(117)),
        FlowScope("ראשונים ואחרונים על הבבלי", SearchScope.Multi(bookIds = emptyList(), categoryIds = listOf(212, 117))),
    )

// One step's times from its start, in ms: its first results shown, its tabs, its end (isLoading off)
private class Step(
    val first: Double,
    val tabs: Double,
    val done: Double,
    val hits: Long,
)

/**
 * Where the wait is in a search the app runs in a scope, as the reader feels it (request 37 of
 * `docs/DESKTOP_STAGE2.md`): SearchResultViewModel over the engines the app builds (AppCoreBindings'
 * `provideSearchEngine`, LibraryCategories over catalog.pb), timed from the search's start to its first results, its
 * tabs and its end; then, in the whole library and the scopes with tabs, a tab picked and all of them again; and the
 * library's order, with a tab in it. `SEFORIMAPP_DATABASE_PATH` names seforim.db, with its full-text index, catalog.pb
 * and the semantic pack beside it; the first argument is the rounds (the first, cold, apart); `BENCH_QUERY` one query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun main(args: Array<String>) {
    val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH") ?: error("SEFORIMAPP_DATABASE_PATH")
    val rounds = args.firstOrNull()?.toInt() ?: 3
    // The view models' Main: one thread, as the window's
    Dispatchers.setMain(Executors.newSingleThreadExecutor { Thread(it, "main").apply { isDaemon = true } }.asCoroutineDispatcher())
    // The app's folders, which the database path and the catalog ask for, in a folder of their own
    val root = createTempDirectory("scope-flow-bench").toFile()
    FileKit.init(appId = "scope-flow-bench", filesDir = File(root, "files"), cacheDir = File(root, "cache"))
    val appSettings = testAppSettings().apply { setDatabasePath(dbPath) }
    val paths = DatabasePathProvider(appSettings)
    val repository = SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath"))
    val engine = appEngine(repository, dbPath)
    val categories = LibraryCategories(CatalogCache(paths), repository, CommentaryLinks(paths))
    runBlocking {
        val start = System.nanoTime()
        categories.warmUp()
        println("categories ${"%.0f".format(millis(start))} ms")
        engine.warmUp()
        println("engine warm-up ${"%.0f".format(millis(start))} ms")
        val times = LinkedHashMap<String, MutableList<Step>>()
        repeat(rounds) { round ->
            for (query in System.getenv("BENCH_QUERY")?.let { listOf(it) } ?: FLOW_QUERIES) {
                for (scope in FLOW_SCOPES) {
                    // Each step as it ends, for a run cut short
                    suspend fun record(
                        step: String,
                        measure: suspend () -> Step,
                    ) {
                        val took = measure()
                        times.getOrPut("$query|${scope.label}|$step") { ArrayList() } += took
                        println(
                            "r%d %s | %s | %s: first %.0f tabs %.0f done %.0f hits %d"
                                .format(round, query, scope.label, step, took.first, took.tabs, took.done, took.hits),
                        )
                    }
                    val flow = SearchFlow(repository, engine, appSettings, categories, query, scope.scope)
                    try {
                        record("search") { flow.search() }
                        val tabs =
                            flow.vm.searchTreeFlow.value
                                .sortedByDescending { it.count }
                                .take(2)
                        if (tabs.size == 2) {
                            for ((i, tab) in tabs.withIndex()) {
                                println("tab ${i + 1}: ${tab.category.title}, ${tab.count} hits")
                                record("tab ${i + 1}") { flow.filter { flow.vm.showOnlyCategory(tab.category.id) } }
                                record("all ${i + 1}") { flow.filter { flow.vm.showOnlyCategory(null) } }
                            }
                        }
                        if (scope.catalog) {
                            record("catalog") { flow.catalogOrder() }
                            flow.vm.searchTreeFlow.value.maxByOrNull { it.count }?.let { tab ->
                                record("catalog tab") { flow.filter { flow.vm.showOnlyCategory(tab.category.id) } }
                            }
                        }
                    } finally {
                        flow.close()
                    }
                }
            }
        }
        print(times)
    }
    // Not engine.close(): the last pages' passages may still be worked out, and closing the model under them crashes
    // the JVM; the app keeps its engine to the end too. The engines' own threads would keep the JVM up
    exitProcess(0)
}

// The app's engine, as AppCoreBindings.provideSearchEngine builds it
private fun appEngine(
    repository: SeforimRepository,
    dbPath: String,
): SearchEngine {
    val indexPath = Paths.get("$dbPath.lucene")
    val dictionaryPath = PardesLexicon.dictionaryFor(indexPath.resolveSibling("lexical.db"), indexPath)
    val lexical = LuceneSearchEngine(indexPath, RepositorySnippetSourceProvider(repository), dictionaryPath = dictionaryPath)
    return withSemanticSearch(lexical, indexPath, repository)
}

// A search tab, new: its view model runs the search it is opened with, as a tab restored or opened from the bar does
private class SearchFlow(
    repository: SeforimRepository,
    engine: SearchEngine,
    appSettings: AppSettings,
    categories: LibraryCategories,
    private val query: String,
    private val scope: SearchScope,
) {
    private val tab = "bench-${UUID.randomUUID()}"
    private val store = TabPersistedStateStore()
    private val desktopManager = mockk<DesktopManager>(relaxed = true).also { every { it.tabExistsFlow(any()) } returns flowOf(true) }
    private val made = System.nanoTime()
    val vm: SearchResultViewModel

    init {
        store.update(tab) { it.copy(search = SearchPersistedState(query = query).withScope(scope)) }
        vm =
            SearchResultViewModel(
                savedStateHandle = SavedStateHandle(mapOf(StateKeys.TAB_ID to tab)),
                persistedStore = store,
                repository = repository,
                engine = engine,
                titleUpdateManager = TabTitleUpdateManager(),
                desktopManager = desktopManager,
                historyStore = HistoryStore(memoryDb()),
                feedbackStore = SearchFeedbackStore(memoryDb()),
                appSettings = appSettings,
                lookup = mockk(relaxed = true),
                libraryCategories = categories,
            )
        vm.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(true))
        // What the screen collects, which the view model's shared flows run for
        vm.viewModelScope.launch { vm.visibleResultsFlow.collect {} }
        vm.viewModelScope.launch { vm.tabCategoriesFlow.collect {} }
    }

    // The search the tab was opened with, from the view model's creation
    suspend fun search(): Step = watch(made, tabsToo = true, before = null) {}

    // A tab picked or let go: the list found filtered, or a search in it
    suspend fun filter(action: () -> Unit): Step {
        delay(PAUSE_MS)
        return watch(System.nanoTime(), tabsToo = false, before = vm.uiState.value.results, action)
    }

    // The results in the library's order: the search again
    suspend fun catalogOrder(): Step {
        delay(PAUSE_MS)
        return watch(System.nanoTime(), tabsToo = true, before = vm.uiState.value.results) {
            vm.onEvent(SearchResultViewModel.SearchResultEvents.SetCatalogOrder(true))
        }
    }

    private suspend fun watch(
        start: Long,
        tabsToo: Boolean,
        before: List<SearchResult>?,
        action: () -> Unit,
    ): Step =
        coroutineScope {
            var first = Double.NaN
            var tabs = Double.NaN
            val treeBefore = vm.searchTreeFlow.value
            val beforeLines = before?.map { it.lineId }
            val tree =
                if (!tabsToo) {
                    null
                } else {
                    launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        vm.searchTreeFlow.first { it.isNotEmpty() && it !== treeBefore }
                        tabs = millis(start)
                    }
                }
            val end =
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    var loading = false
                    vm.uiState.first { state ->
                        if (state.isLoading) loading = true
                        // Other lines than before: the last step's snippets refined late change the list, not its lines
                        val changed = state.results.isNotEmpty() && (before == null || state.results.map { it.lineId } != beforeLines)
                        if (first.isNaN() && changed) first = millis(start)
                        !state.isLoading && (loading || changed)
                    }
                }
            action()
            val state = withTimeoutOrNull(TIMEOUT_MS) { end.await() }
            val done = if (state == null) Double.NaN else millis(start)
            if (tree != null && withTimeoutOrNull(TREE_WAIT_MS) { tree.join() } == null) tree.cancel()
            end.cancel()
            Step(first, tabs, done, state?.progressTotal ?: -1)
        }

    // Its searches stopped and its session closed, as a tab closed
    fun close() {
        vm.cancelSearch()
        vm.viewModelScope.cancel()
    }

    private fun memoryDb() = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))
}

// Per query, scope and step: the cold first round, then the median of the others
private fun print(times: Map<String, List<Step>>) {
    var lastQuery = ""
    for ((key, steps) in times) {
        val (query, scope, step) = key.split('|')
        if (query != lastQuery) println("--- $query").also { lastQuery = query }
        val warm = steps.drop(1).ifEmpty { steps }
        println(
            "  %-28s %-12s hits=%8d | first %s | tabs %s | done %s".format(
                scope,
                step,
                steps.last().hits,
                stats(steps.first().first, warm.map { it.first }),
                stats(steps.first().tabs, warm.map { it.tabs }),
                stats(steps.first().done, warm.map { it.done }),
            ),
        )
    }
}

private fun stats(
    cold: Double,
    warm: List<Double>,
): String {
    val known = warm.filterNot { it.isNaN() }.sorted()
    val median = if (known.isEmpty()) Double.NaN else known[known.size / 2]
    return "%6.0f / %6.0f".format(cold, median)
}

private fun millis(start: Long): Double = (System.nanoTime() - start) / 1e6

private const val TIMEOUT_MS = 120_000L
private const val TREE_WAIT_MS = 5_000L

// How long a reader looks at the results before the next click (BENCH_PAUSE_MS): the passages of the page shown are
// still being worked out then
private val PAUSE_MS = System.getenv("BENCH_PAUSE_MS")?.toLong() ?: 300L

package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.search.semantic.WordsEngine
import io.github.kdroidfilter.seforimapp.framework.search.LongQuerySearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import io.github.kdroidfilter.seforimlibrary.search.TextHighlights
import kotlinx.serialization.Serializable

// Pardes (request 54 of docs/DESKTOP_STAGE3_PLAN.md): a search exact, general or hybrid, as the user decided on
// 2026-11-18: "מדויק מרכאות, כללי - חיפוש רחב אבל לא סמנטי, היברידי - כמו היום".

/** How a search reads its text. Kept by its place in a tab's saved state: new modes go last. */
@Serializable
enum class SearchMode {
    /** As one exact phrase, its words as written, as when it is typed in quotes. */
    EXACT,

    /** By its words and their forms, without meaning: the search of the words alone. */
    GENERAL,

    /** By its words and by meaning together, fused: the search as it was. */
    HYBRID,
}

/** The search mode chosen last, which the next searches take; none until one is chosen. */
object SearchModeSettings : SettingsGroup("search_mode") {
    val last by enumOrNull<SearchMode>()
}

/**
 * The mode a search takes when its tab has none: the one chosen last, else hybrid where the search by meaning is
 * installed ([meaning]) and general where it is not.
 */
fun AppSettings.defaultSearchMode(meaning: Boolean): SearchMode =
    this[SearchModeSettings.last] ?: if (meaning) SearchMode.HYBRID else SearchMode.GENERAL

/**
 * [engine] searched in the mode [mode] says at each search: exact, its search of the words (`WordsEngine.words`, the
 * engine itself when it has no other) with the text as exact phrases; general, that search as it is; hybrid, [engine]
 * itself. Its words for a list in the library's order (CatalogOrderEngine) are the mode's too.
 */
class SearchModeEngine(
    private val engine: SearchEngine,
    private val mode: () -> SearchMode,
) : SearchEngine by engine,
    WordsEngine {
    private val lexical = (engine as? WordsEngine)?.words ?: engine
    private val quoted = QuotedEngine(lexical)

    override val words: SearchEngine get() = if (mode() == SearchMode.EXACT) quoted else lexical

    // The engine of the mode now
    private fun current(): SearchEngine =
        when (mode()) {
            SearchMode.EXACT -> quoted
            SearchMode.GENERAL -> lexical
            SearchMode.HYBRID -> engine
        }

    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchSession? = current().openSession(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchFacets? = current().computeFacets(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)

    override fun buildSnippet(
        rawText: String,
        query: String,
        near: Int,
    ): String = current().buildSnippet(rawText, query, near)

    override fun highlightRanges(
        text: String,
        query: String,
    ): List<IntRange> = current().highlightRanges(text, query)

    override fun highlightsByMeaning(query: String): Boolean = current().highlightsByMeaning(query)

    override suspend fun highlights(
        texts: List<String>,
        query: String,
    ): List<TextHighlights> = current().highlights(texts, query)

    override suspend fun passagesByMeaning(
        query: String,
        texts: List<String>,
    ): List<String?>? = current().passagesByMeaning(query, texts)

    override suspend fun attachSnippets(
        hits: List<LineHit>,
        query: String,
        near: Int,
    ): List<LineHit> = current().attachSnippets(hits, query, near)
}

/** [lexical] with every text searched, marked and cut as exact phrases ([LongQuerySearchEngine.asQuote]). */
private class QuotedEngine(
    private val lexical: SearchEngine,
) : SearchEngine by lexical {
    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchSession? = lexical.openSession(quoted(query), near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchFacets? = lexical.computeFacets(quoted(query), near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)

    override fun buildSnippet(
        rawText: String,
        query: String,
        near: Int,
    ): String = lexical.buildSnippet(rawText, quoted(query), near)

    override fun highlightRanges(
        text: String,
        query: String,
    ): List<IntRange> = lexical.highlightRanges(text, quoted(query))

    override fun highlightsByMeaning(query: String): Boolean = false

    override suspend fun highlights(
        texts: List<String>,
        query: String,
    ): List<TextHighlights> = lexical.highlights(texts, quoted(query))

    override suspend fun attachSnippets(
        hits: List<LineHit>,
        query: String,
        near: Int,
    ): List<LineHit> = lexical.attachSnippets(hits, quoted(query), near)

    private fun quoted(query: String): String = LongQuerySearchEngine.asQuote(query)
}

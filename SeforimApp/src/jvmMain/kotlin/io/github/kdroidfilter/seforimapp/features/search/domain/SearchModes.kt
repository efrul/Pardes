package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.search.semantic.WordsEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import io.github.kdroidfilter.seforimlibrary.search.TextHighlights
import kotlinx.serialization.Serializable

// Pardes (request 54 of docs/DESKTOP_STAGE3_PLAN.md): a search by its words alone or by meaning too, as the user
// decided: "כללי - חיפוש רחב אבל לא סמנטי, היברידי - כמו היום" (2026-11-18), and "תוריד את מדויק" (2026-11-19).

/** How a search reads its text. Kept by its place in a tab's saved state: new modes go last. */
@Serializable
enum class SearchMode {
    /**
     * No longer offered (the user's word of 2026-11-19: "תוריד את מדויק"): kept for its place, which a testers'
     * build saved tabs with; such a tab searches as [GENERAL].
     */
    EXACT,

    /** By its words and their forms, without meaning: the search of the words alone. */
    GENERAL,

    /** By its words and by meaning together, fused: the search as it was. */
    HYBRID,
}

/** The mode the home page's toggle chose for the next search, which that search takes once; none until one is chosen. */
object SearchModeSettings : SettingsGroup("search_mode") {
    val last by enumOrNull<SearchMode>()
}

/**
 * The mode a search takes when its tab has none: the one the home page's toggle chose for it, else hybrid where the
 * search by meaning is installed ([meaning]), the default the user asked for on 2026-11-19 ("ברירת מחדל תהיה עם
 * סמנטי"), and general where it is not.
 */
fun AppSettings.defaultSearchMode(meaning: Boolean): SearchMode =
    this[SearchModeSettings.last]?.takeIf { meaning || it != SearchMode.HYBRID }
        ?: if (meaning) SearchMode.HYBRID else SearchMode.GENERAL

/** The mode of a new search ([defaultSearchMode]), the home page's choice taken by it: the searches after it, by meaning again. */
fun AppSettings.takeSearchMode(meaning: Boolean): SearchMode = defaultSearchMode(meaning).also { reset(SearchModeSettings) }

/**
 * [engine] searched in the mode [mode] says at each search: general, its search of the words (`WordsEngine.words`, the
 * engine itself when it has no other); hybrid, [engine] itself. Its words for a list in the library's order
 * (CatalogOrderEngine) are its search of the words.
 */
class SearchModeEngine(
    private val engine: SearchEngine,
    private val mode: () -> SearchMode,
) : SearchEngine by engine,
    WordsEngine {
    private val lexical = (engine as? WordsEngine)?.words ?: engine

    override val words: SearchEngine get() = lexical

    // The engine of the mode now
    private fun current(): SearchEngine = if (mode() == SearchMode.HYBRID) engine else lexical

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

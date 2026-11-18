package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.semantic.WordsEngine
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A search exact, general or hybrid (request 54): which engine is asked, and the text it is asked. */
class SearchModesTest {
    // An engine that writes down the texts it is asked to search
    private open class Recording : SearchEngine {
        val asked = mutableListOf<String>()

        override fun openSession(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchSession? {
            asked += query
            return null
        }

        override fun computeFacets(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
        ): SearchFacets? {
            asked += "facets $query"
            return null
        }

        override fun searchBooksByTitlePrefix(
            query: String,
            limit: Int,
        ): List<Long> = emptyList()

        override fun buildSnippet(
            rawText: String,
            query: String,
            near: Int,
        ): String = rawText

        override fun highlightsByMeaning(query: String): Boolean = true

        override fun close() = Unit
    }

    // The hybrid engine: its own search, and its search of the words apart
    private class Hybrid(
        override val words: Recording,
    ) : Recording(),
        WordsEngine

    private val lexical = Recording()
    private val hybrid = Hybrid(lexical)

    private fun search(
        mode: SearchMode,
        query: String,
    ) {
        val engine = SearchModeEngine(hybrid) { mode }
        engine.openSession(query)
        engine.computeFacets(query)
    }

    @Test
    fun `hybrid asks the engine itself, general its search of the words, each the text as typed`() {
        search(SearchMode.HYBRID, "ברכות השחר")
        assertEquals(listOf("ברכות השחר", "facets ברכות השחר"), hybrid.asked)
        assertTrue(lexical.asked.isEmpty())

        search(SearchMode.GENERAL, "ברכות השחר")
        assertEquals(listOf("ברכות השחר", "facets ברכות השחר"), lexical.asked)
    }

    @Test
    fun `exact asks the search of the words the text as an exact phrase, a quote typed kept, and marks no meaning`() {
        search(SearchMode.EXACT, "ברכות השחר")
        search(SearchMode.EXACT, "\"ברכות\" השחר")
        assertEquals(
            listOf("\"ברכות השחר\"", "facets \"ברכות השחר\"", "\"ברכות\" \"השחר\"", "facets \"ברכות\" \"השחר\""),
            lexical.asked,
        )
        assertTrue(hybrid.asked.isEmpty())
        assertFalse(SearchModeEngine(hybrid) { SearchMode.EXACT }.highlightsByMeaning("ברכות"))
        assertTrue(SearchModeEngine(hybrid) { SearchMode.HYBRID }.highlightsByMeaning("ברכות"))
    }

    @Test
    fun `the words a list in the library's order reads are the mode's`() {
        val mode = arrayOf(SearchMode.EXACT)
        val engine = SearchModeEngine(hybrid) { mode[0] }
        engine.words.openSession("ברכות השחר")
        mode[0] = SearchMode.GENERAL
        engine.words.openSession("ברכות השחר")
        assertEquals(listOf("\"ברכות השחר\"", "ברכות השחר"), lexical.asked)
    }

    @Test
    fun `the next searches take the mode chosen last, else hybrid where the search by meaning is installed`() {
        val settings = testAppSettings()
        assertEquals(SearchMode.HYBRID, settings.defaultSearchMode(meaning = true))
        assertEquals(SearchMode.GENERAL, settings.defaultSearchMode(meaning = false))
        settings[SearchModeSettings.last] = SearchMode.EXACT
        assertEquals(SearchMode.EXACT, settings.defaultSearchMode(meaning = true))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `a tab's mode is kept with its search, and one saved before it has none`() {
        val saved = ProtoBuf.encodeToByteArray(SearchPersistedState(query = "ברכות", searchMode = SearchMode.EXACT))
        assertEquals(SearchMode.EXACT, ProtoBuf.decodeFromByteArray<SearchPersistedState>(saved).searchMode)
        val before = ProtoBuf.encodeToByteArray(Before(query = "ברכות"))
        assertNull(ProtoBuf.decodeFromByteArray<SearchPersistedState>(before).searchMode)
    }

    // A search's state as saved before the mode, by its first field (the others are numbered after it)
    @Serializable
    private data class Before(
        val query: String = "",
    )
}

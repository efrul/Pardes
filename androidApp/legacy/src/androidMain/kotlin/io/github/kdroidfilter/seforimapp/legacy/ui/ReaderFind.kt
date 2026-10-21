package io.github.kdroidfilter.seforimapp.legacy.ui

import android.util.Log
import io.github.kdroidfilter.seforim.htmlparser.plainTextFromHtml
import io.github.kdroidfilter.seforimapp.android.ui.reader.BookFindMatches
import io.github.kdroidfilter.seforimapp.android.ui.reader.findInBook
import io.github.kdroidfilter.seforimapp.android.ui.reader.findSearchMatchesInBook
import io.github.kdroidfilter.seforimapp.android.ui.reader.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.android.ui.reader.queryNarrows
import io.github.kdroidfilter.seforimapp.legacy.data.LegacySearch
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reader's find in the page, as the main app's (ReaderViewModel's find and FindState): the query typed, from two
 * letters, is found in the whole book once the typing pauses ([DEBOUNCE_MS]), by the main app's BookFindMatches (the
 * index's candidate lines at LegacySearch.Level.Full, else the book read through); a query that holds the one before
 * narrows its results, and ▲ ▼ step from the match reached, else from the line at the top, around the book.
 *
 * Opened by a search result, the bar holds the search's query ([searchQuery]) and is [smart], as desktop's: it counts
 * the lines the search finds in the book, lit with the search's own highlighting, one match each. Without the index
 * (the scan's results) those are the lines the scan's pattern finds, all its words in a line or the phrase
 * ([searchPhrase]), lit with that pattern. Typing another query is the literal find again.
 *
 * [onChange] tells the reader what to draw again; everything here runs on the main thread, the finding off it.
 */
class ReaderFind(
    private val scope: CoroutineScope,
    private val search: LegacySearch,
    private val repository: SeforimRepository,
    private val bookId: Long,
    val searchQuery: String?,
    private val searchPhrase: Boolean,
    private val onChange: (Change) -> Unit,
) {
    sealed interface Change {
        /** The counter: results, the match reached, or the bar opened or closed. */
        data object Counter : Change

        /** What the text and the panes light changed: every line drawn again. */
        data object Lit : Change

        /** The search's highlighting of line [lineId] is known ([searchRanges]). */
        data class LitLine(val lineId: Long) : Change

        /** ▲ ▼ reached [match] (from [previous]): both lines drawn again, and the match brought into view. */
        data class Step(val match: BookFindMatches.Match, val previous: BookFindMatches.Match?) : Change
    }

    var open = false
        private set
    var query = ""
        private set

    /** The results of [resultsQuery] (null while counting), in [resultsSmart]'s mode. */
    private var results: BookFindMatches? = null
    private var resultsQuery = ""
    private var resultsSmart = false

    /** The match ▲ ▼ reached. */
    var current: BookFindMatches.Match? = null
        private set

    /** The bar holds the search's query as it came: the search's lines and highlighting (see the class). */
    var smart = false
        private set

    /** Times the bar was opened asking for the cursor (the main app's focusRequest): Back then closes it. */
    private var asked = 0

    /** The query lit in the text (literal find) and in the panes (both modes), once the typing has paused; "" for none. */
    var textQuery = ""
        private set
    var paneQuery = ""
        private set

    private var findJob: Job? = null
    private var stepJob: Job? = null

    // The smart find's highlighting: the engine, once asked for (null without the index), and each line's ranges
    private var engineAsked = false
    private var engineKnown = false
    private var engine: SearchEngine? = null
    private val ranges = HashMap<Long, List<IntRange>>()
    private val pending = HashSet<Long>()

    /** Counts [forgetRanges]: a reading begun before it is of the old text. */
    private var marksDrawn = 0
    private val scanPattern: Regex? by lazy { searchQuery?.let { LineText.highlighter(it, phrase = searchPhrase) } }

    /** The results shown: those of the query as typed, from two letters. */
    fun shownResults(): BookFindMatches? = results?.takeIf { query.trim().length >= 2 && resultsQuery == query }

    /** The counter's text ("3/861", or the count before a match is reached), null while counting. */
    fun counterText(): String? {
        val shown = shownResults() ?: return null
        val count = shown.occurrences
        return current?.takeIf { it in shown }?.let { "${it.position}/$count" } ?: count.toString()
    }

    /** The smart find's lines once known for the query shown: only they are lit (null while they are counted). */
    fun smartLines(): BookFindMatches? = results?.takeIf { open && smart && resultsSmart && resultsQuery == query }

    /** Whether the text lights the search's highlighting (the bar open, holding the search's query). */
    val lightsSearch: Boolean get() = open && smart

    /**
     * Back closes the bar when the user opened it, or typed in it; a search result's bar left as it came leaves Back to
     * the book, which goes back to the results (the main app's findBack).
     */
    fun backCloses(): Boolean = open && (asked > 0 || query != searchQuery)

    /** Opens the bar, with [query] when given (a search result's); [focus]: asked for by the user (the cursor in it). */
    fun open(query: String? = null, focus: Boolean = true) {
        open = true
        if (query != null) this.query = query
        if (focus) asked++
        recompute()
    }

    fun close() {
        findJob?.cancel()
        stepJob?.cancel()
        open = false
        current = null
        setLit("", "")
        onChange(Change.Counter)
    }

    fun setQuery(query: String) {
        if (query == this.query) return
        this.query = query
        current = null
        recompute()
    }

    private fun recompute() {
        // Smart while the bar holds the search's query as it came (desktop: smart mode set with the seeded query)
        smart = searchQuery != null && query == searchQuery
        findJob?.cancel()
        if (!open || query.trim().length < 2) {
            results = null
            resultsQuery = ""
            setLit("", "")
            onChange(Change.Counter)
            return
        }
        if (smart) {
            askEngine()
            setLit("", query)
        }
        // Results of the other mode don't answer this one, nor narrow it
        val previous = results?.takeIf { resultsQuery.isNotEmpty() && resultsSmart == smart }
        if (previous != null && (resultsQuery == query || !smart && normalizeQueryForHebrew(resultsQuery) == normalizeQueryForHebrew(query))) {
            resultsQuery = query
            if (!smart) setLit(query, query)
            onChange(Change.Counter)
            return
        }
        val narrowing = previous?.takeIf { !smart && queryNarrows(query, resultsQuery) }
        val q = query
        val isSmart = smart
        results = null
        onChange(Change.Counter)
        findJob = scope.launch {
            // Let the typing settle before reading the book (and drawing the lines again with what is typed)
            if (!isSmart) {
                delay(DEBOUNCE_MS)
                setLit(q, q)
            }
            val matches = try {
                val engine = withContext(Dispatchers.IO) { runCatching { search.service()?.engine() }.getOrNull() }
                when {
                    isSmart && engine != null -> findSearchMatchesInBook(engine, repository::getLinesByIds, bookId, q)
                    isSmart -> findScanMatches()
                    else -> findInBook(engine, repository::getLinesByIds, { a, b -> repository.getLines(bookId, a, b) }, bookId, q, narrowing)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "find in book", e)
                BookFindMatches.Empty
            }
            if (query != q || smart != isSmart) return@launch
            results = matches
            resultsQuery = q
            resultsSmart = isSmart
            onChange(Change.Counter)
            // The smart find lights only the lines it counts, once they are known
            if (isSmart) onChange(Change.Lit)
        }
    }

    private fun setLit(text: String, pane: String) {
        if (text == textQuery && pane == paneQuery) return
        textQuery = text
        paneQuery = pane
        onChange(Change.Lit)
    }

    /**
     * ▲ ▼, Enter: the next match from the one reached, else from [topLine] (the line at the top of the text); waits for
     * the results of the query as typed.
     */
    fun step(forward: Boolean, topLine: () -> Int) {
        stepJob?.cancel()
        stepJob = scope.launch {
            var waited = 0L
            while ((results == null || resultsQuery != query) && open && waited < WAIT_MS) {
                delay(WAIT_STEP_MS)
                waited += WAIT_STEP_MS
            }
            val matches = results?.takeIf { open && resultsQuery == query } ?: return@launch
            val top = topLine()
            val match = current?.takeIf { it in matches }?.let { matches.next(it, forward) }
                ?: matches.nextAfterLine(if (forward) top - 1 else top, forward)
                ?: return@launch
            val previous = current
            current = match
            onChange(Change.Step(match, previous))
            onChange(Change.Counter)
        }
    }

    /**
     * The search's highlighting of [text], line [lineId]'s displayed text, in the smart find: the scan's pattern at once,
     * the engine's once read (null until then: [Change.LitLine] tells when it is).
     */
    fun searchRanges(lineId: Long, text: String): List<IntRange>? {
        val q = searchQuery ?: return emptyList()
        if (!engineKnown) return null
        val engine = engine ?: return scanPattern?.findAll(text)?.map { it.range }?.toList().orEmpty()
        ranges[lineId]?.let { return it }
        if (pending.add(lineId)) {
            val drawn = marksDrawn
            scope.launch {
                val found = withContext(Dispatchers.IO) { runCatching { engine.highlightRanges(text, q) }.getOrDefault(emptyList()) }
                if (drawn != marksDrawn) return@launch
                pending.remove(lineId)
                ranges[lineId] = found
                onChange(Change.LitLine(lineId))
            }
        }
        return null
    }

    /** The lines are drawn with other marks: the search's highlighting of each is read again for its new text. */
    fun forgetRanges() {
        marksDrawn++
        ranges.clear()
        pending.clear()
    }

    private fun askEngine() {
        if (engineAsked) return
        engineAsked = true
        scope.launch {
            engine = withContext(Dispatchers.IO) { runCatching { search.service()?.engine() }.getOrNull() }
            engineKnown = true
            onChange(Change.Lit)
        }
    }

    /** The scan's results in this book: the lines its pattern finds, each word or the phrase, one match each. */
    private suspend fun findScanMatches(): BookFindMatches = withContext(Dispatchers.Default) {
        val q = searchQuery ?: return@withContext BookFindMatches.Empty
        val tests = (if (searchPhrase) listOfNotNull(LineText.highlighter(q, phrase = true)) else LineText.wordPatterns(q))
            .ifEmpty { return@withContext BookFindMatches.Empty }
        val found = ArrayList<Line>()
        var start = 0
        do {
            val lines = repository.getLines(bookId, start, start + SCAN_CHUNK - 1)
            for (line in lines) {
                ensureActive()
                val text = plainTextFromHtml(line.content)
                if (tests.all { it.containsMatchIn(text) }) found += line
            }
            start += SCAN_CHUNK
        } while (lines.size == SCAN_CHUNK)
        BookFindMatches.ofLines(found)
    }

    private companion object {
        const val TAG = "Pardes"

        /** The main app's FIND_DEBOUNCE_MS and FIND_WAIT_MS. */
        const val DEBOUNCE_MS = 150L
        const val WAIT_MS = 30_000L
        const val WAIT_STEP_MS = 50L

        /** Lines read per query of the scan's find (the main app's FIND_CHUNK). */
        const val SCAN_CHUNK = 500
    }
}

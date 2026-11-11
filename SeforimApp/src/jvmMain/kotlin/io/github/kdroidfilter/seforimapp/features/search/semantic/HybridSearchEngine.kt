package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchQueryParser
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import io.github.kdroidfilter.seforimlibrary.search.SnippetSources
import io.github.kdroidfilter.seforimlibrary.search.WARMUP_PAGE_SIZE
import io.github.kdroidfilter.seforimlibrary.search.WARMUP_QUERIES
import io.github.kdroidfilter.seforimlibrary.search.WARMUP_ROUNDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Independent Hybrid Search Engine for Pardes Desktop:
 * Combines lexical BM25 (LuceneSearchEngine) with independent ONNX dense vector search (IndependentSemanticSearch)
 * using Reciprocal Rank Fusion: RRF(doc) = Σ 1/(K + rank), the dense side weighed per model, a lexical hit holding the
 * query word for word weighing more, short lines of the dense side alone left out and a bonus for the query's words
 * close together ([fusedScores]); then the lexical side's first hits that hold the query as a quote kept on the first
 * page ([keepLeaders], [holdsQuote]), and the earlier level first among the lines that hold it as a sentence
 * ([LevelPrecedence], [BookLevels]).
 *
 * Gracefully and safely degrades to pure lexical search if the model or vector files are not found.
 */
class HybridSearchEngine internal constructor(
    private val lexical: SearchEngine,
    private val indexDir: Path,
    private val resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
    // Whether the model and its vector index are there (a cheap check)
    private val semanticConfigured: Boolean,
    // The library's book levels and lines, for the precedence of the earlier level; without them, the fused order
    private val library: LibraryLines? = LibraryLines.beside(indexDir),
    // What loads the model and its vector index
    private val openSemantic: (Path) -> IndependentSemanticSearch?,
) : SearchEngine,
    WordsEngine {
    /** The search of the words alone, every line that has them: what a list in the library's order reads. */
    override val words: SearchEngine get() = lexical

    constructor(
        lexical: SearchEngine,
        indexDir: Path,
        resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
    ) : this(
        lexical,
        indexDir,
        resolveLine,
        IndependentSemanticSearch.isAvailable(indexDir),
        openSemantic = { IndependentSemanticSearch.open(it) },
    )

    @Volatile private var semantic: IndependentSemanticSearch? = null

    @Volatile private var semanticTried = false
    private val semanticMutex = Mutex()

    private suspend fun ensureSemantic(): IndependentSemanticSearch? {
        if (semanticTried) return semantic
        semanticMutex.withLock {
            if (!semanticTried) {
                semantic =
                    if (semanticConfigured) {
                        withContext(Dispatchers.IO) {
                            // An Error too: ONNX Runtime's native library throws UnsatisfiedLinkError where it can't load
                            // (Windows without the Visual C++ runtime); the search then goes on with its words alone
                            runSuspendCatching { openSemantic(indexDir) }
                                .onSuccess {
                                    if (it != null) {
                                        infoln { "[HybridSearchEngine] Dense semantic search loaded successfully." }
                                    } else {
                                        warnln { "[HybridSearchEngine] Model unavailable; falling back to lexical search." }
                                    }
                                }.onFailure { warnln(it) { "[HybridSearchEngine] Error loading dense search; lexical only." } }
                                .getOrNull()
                        }
                    } else {
                        null
                    }
                semanticTried = true
            }
        }
        return semantic
    }

    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchSession? {
        if (query.isBlank()) return null

        // Quoted phrase or specific filters unsupported by dense index fall back directly to lexical
        if (!semanticConfigured || categoryFilter != null || lineIds != null || SearchQueryParser.parse(query).hasExactPhrases) {
            return lexical.openSession(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
        }

        return HybridSession(query, near, bookIds ?: bookFilter?.let { listOf(it) }, baseBookOnly)
    }

    override fun searchBooksByTitlePrefix(
        query: String,
        limit: Int,
    ): List<Long> = lexical.searchBooksByTitlePrefix(query, limit)

    override fun buildSnippet(
        rawText: String,
        query: String,
        near: Int,
    ): String = lexical.buildSnippet(rawText, query, near)

    override fun highlightRanges(
        text: String,
        query: String,
    ): List<IntRange> = lexical.highlightRanges(text, query)

    override fun rangeSnippet(
        text: String,
        ranges: List<IntRange>,
        lineStart: Int,
        lineEnd: Int,
    ): String = lexical.rangeSnippet(text, ranges, lineStart, lineEnd)

    // A quoted phrase asks for those exact words: they alone are highlighted, as they alone are found. A model that
    // failed to load highlights nothing by meaning either
    override fun highlightsByMeaning(query: String): Boolean =
        semanticConfigured && !(semanticTried && semantic == null) && !SearchQueryParser.parse(query).hasExactPhrases

    override suspend fun passagesByMeaning(
        query: String,
        texts: List<String>,
    ): List<String?>? {
        val semantic = ensureSemantic() ?: return null
        return withContext(Dispatchers.Default) {
            runSuspendCatching { semantic.bestPassages(query, texts) { lexical.highlightRanges(it, query) } }
                .onFailure { warnln(it) { "[HybridSearchEngine] Passages by meaning failed; the query's words instead." } }
                .getOrNull()
        }
    }

    override fun findInBookCandidates(
        query: String,
        bookId: Long,
    ): LongArray? = lexical.findInBookCandidates(query, bookId)

    override suspend fun denseReady(): Boolean = ensureSemantic() != null

    override suspend fun attachSnippets(
        hits: List<LineHit>,
        query: String,
        near: Int,
    ): List<LineHit> = lexical.attachSnippets(hits, query, near)

    override suspend fun warmUp() {
        lexical.warmUp()
        ensureSemantic() ?: return
        // The fused path too, as the first searches take it: query embeddings, the vector graph, passages by meaning
        repeat(WARMUP_ROUNDS) {
            for (query in WARMUP_QUERIES) {
                openSession(query, near = 5, baseBookOnly = true)?.use { session ->
                    session.nextPage(WARMUP_PAGE_SIZE)?.let { session.refinedSnippets(it.hits) }
                }
            }
        }
    }

    override suspend fun semanticFind(
        query: String,
        bookId: Long,
        limit: Int,
    ): List<Long> {
        if (query.isBlank()) return emptyList()
        return fuse(query, near = SMART_FIND_NEAR, bookIds = listOf(bookId), baseOnly = false)
            .take(limit)
            .map { it.lineId }
    }

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchFacets? = lexical.computeFacets(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)

    override fun close() {
        runCatching { semantic?.close() }
        runCatching { library?.close() }
        runCatching { lexical.close() }
    }

    private class FusedEntry(
        val lineId: Long,
        val score: Float,
        val lexical: LineHit?,
        // The line's book: the lexical hit's, else the dense hit's (-1 when the vector index doesn't store it)
        val bookId: Long = lexical?.bookId ?: -1L,
    )

    private suspend fun fuse(
        query: String,
        near: Int,
        bookIds: Collection<Long>?,
        baseOnly: Boolean,
        // How many lines the lexical side finds in all, of which the fusion takes the first CANDIDATES, and whether the
        // dense side answered: a list without its lines is the lexical side's ([HybridSession])
        onLexical: ((total: Long, denseAnswered: Boolean) -> Unit)? = null,
    ): List<FusedEntry> =
        coroutineScope {
            val started = System.nanoTime()
            var denseMillis = 0L
            // Whether the dense side's walk came back short, in a trap of its graph: it then starts again where the
            // words' first hits are (IndependentSemanticSearch.search)
            var short = false
            val dense =
                async(Dispatchers.Default) {
                    val sem = ensureSemantic() ?: return@async null
                    runSuspendCatching { sem.search(query, CANDIDATES, baseOnly, bookIds, onShort = { short = true }) }
                        .onFailure { warnln(it) { "[HybridSearchEngine] Dense KNN failed; using lexical results." } }
                        .getOrNull()
                        .also { denseMillis = millisSince(started) }
                }

            // A lexical failure must not sink the search either: a long query's dictionary expansions can pass Lucene's
            // 1024 clauses (IndexSearcher.TooManyNestedClauses), and the dense side alone still answers it
            var lexicalTotal = 0L
            val lexHits =
                runSuspendCatching {
                    lexical
                        .openSession(query, near = near, bookIds = bookIds, baseBookOnly = baseOnly)
                        ?.use { it.nextPage(CANDIDATES, snippets = false) }
                        ?.also { lexicalTotal = it.totalHits }
                        ?.hits
                        .orEmpty()
                }.getOrElse { e ->
                    warnln(e) { "[HybridSearchEngine] Lexical search failed; using dense results." }
                    emptyList()
                }
            val wordsMillis = millisSince(started)

            var denseHits = dense.await()
            if (short) {
                val seeds = lexHits.take(DENSE_SEEDS).map { it.lineId }
                denseHits =
                    withContext(Dispatchers.Default) {
                        runSuspendCatching { semantic?.search(query, CANDIDATES, baseOnly, bookIds, seeds) }
                            .onFailure { warnln(it) { "[HybridSearchEngine] Dense KNN failed; using lexical results." } }
                            .getOrNull()
                            .also { denseMillis = millisSince(started) }
                    } ?: denseHits
            }
            if (denseHits.isNullOrEmpty()) {
                onLexical?.invoke(lexicalTotal, false)
                return@coroutineScope lexHits.map { FusedEntry(it.lineId, it.score, it) }
            }
            onLexical?.invoke(lexicalTotal, true)

            // The candidates' lines, read once: for the fusion's weights and for the precedence of the earlier level
            val linesStarted = System.nanoTime()
            val lines = library?.candidateLines(lexHits.map { it.lineId } + denseHits.map { it.lineId })
            val linesMillis = millisSince(linesStarted)
            val orderStarted = System.nanoTime()
            val rrf =
                fusedScores(
                    lexical = lexHits.map { it.lineId },
                    dense = denseHits.map { it.lineId },
                    denseWeight = semantic?.hybridWeight ?: 1.0,
                    query = query,
                    // A search of several books: one book's short lines are its own (a chosen book is read whole)
                    general = bookIds?.size != 1,
                ) { lines?.get(it)?.text }

            val lexById = lexHits.associateBy { it.lineId }
            val denseBooks = denseHits.associate { it.lineId to it.bookId }
            val order = rrf.entries.sortedWith(compareByDescending<Map.Entry<Long, Double>> { it.value }.thenBy { it.key }).map { it.key }
            // The lexical side's first hits that hold the query as a quote keep a place on the first page; the others rank
            // as fused. On real searches the lexical first hit was mostly a long line the query's words are scattered in:
            // kept whatever it held, it cost the first page more than it gave
            val leaders = LinkedHashMap<Long, Int>()
            if (quoteKey(query) != null) {
                lexHits.take(LEXICAL_PLACES.size).forEachIndexed { j, hit ->
                    val text = lines?.get(hit.lineId)?.text ?: runSuspendCatching { resolveLine(hit.lineId, query)?.rawText }.getOrNull()
                    if (text != null && holdsQuote(text, query)) leaders[hit.lineId] = LEXICAL_PLACES[j]
                }
            }
            // The order is the list's from here, not the scores': a kept lexical hit keeps its own (lower) score
            val page = keepLeaders(order, leaders)
            // Within one book every line has the same level: no line to put first
            (if (bookIds?.size == 1) page else withPrecedence(page, query, lines))
                .map { lineId ->
                    val lexicalHit = lexById[lineId]
                    FusedEntry(lineId, rrf.getValue(lineId).toFloat(), lexicalHit, lexicalHit?.bookId ?: denseBooks[lineId] ?: -1L)
                }.also {
                    // The two sides run at once: the list waits for the slower one, then for the lines
                    debugln {
                        "[HybridSearchEngine] ${bookIds?.size ?: "all"} books, fused in ${millisSince(started)} ms: " +
                            "words $wordsMillis, meaning $denseMillis, the candidates' lines $linesMillis, " +
                            "their order ${millisSince(orderStarted)}"
                    }
                }
        }

    /**
     * [page] with the earlier level first among its lines that hold [query] as a sentence ([LevelPrecedence]), as the
     * library's book levels and lines tell ([known], when the fusion has read them already); [page] as it is when they
     * can't be read.
     */
    private suspend fun withPrecedence(
        page: List<Long>,
        query: String,
        known: Map<Long, LineText>? = null,
    ): List<Long> {
        val library = library ?: return page
        if (quoteKey(query) == null) return page
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                val levels = library.levels()
                val lines = known ?: library.lines(page)
                LevelPrecedence.order(page, query, { lines[it]?.text }) { id ->
                    lines[id]?.let { levels[it.bookId] } ?: BookLevels.UNKNOWN
                }
            }.onFailure { warnln(it) { "[HybridSearchEngine] The library's levels unavailable; the fused order as is." } }
                .getOrDefault(page)
        }
    }

    /**
     * The fused list, as Zayit's: the lexical side's first CANDIDATES and the dense side's, some 300 lines at most, its
     * total their number, the list's length. A quoted query or a table of contents' section never comes here
     * ([openSession]): the lexical side's whole list, as in Zayit. The books of the lines it found by meaning alone,
     * which the facets don't count, are its [meaningBookCounts]: the category tabs count them too.
     *
     * A search the dense side doesn't answer (its model failed to load, no vector in the scope) is the lexical side's
     * whole list: its candidates, then its results past them, in their order and each once, its total theirs, as a search
     * without the dense side. Zayit takes the lexical candidates alone for its fused list there, the first 150.
     */
    private inner class HybridSession(
        private val query: String,
        private val near: Int,
        private val bookIds: Collection<Long>?,
        private val baseOnly: Boolean,
    ) : SearchSession,
        MeaningFacets,
        SessionViews {
        private var fused: List<FusedEntry>? = null
        private var offset = 0
        private var lexicalTotal = 0L
        private var lexicalOnly = false

        // Without the dense side, the lexical results after its candidates, and the lines shown so far
        private var rest: SearchSession? = null
        private val shown = HashSet<Long>()

        override suspend fun nextPage(
            limit: Int,
            snippets: Boolean,
        ): SearchPage? {
            val all =
                fused ?: fuse(query, near, bookIds, baseOnly) { total, denseAnswered ->
                    lexicalTotal = total
                    lexicalOnly = !denseAnswered
                }.also { list ->
                    fused = list
                    list.mapTo(shown) { it.lineId }
                }
            // Past the candidates only without the dense side: a fused list is the whole list
            val more = lexicalOnly && lexicalTotal > CANDIDATES
            val total = if (more) lexicalTotal else all.size.toLong()
            while (offset < all.size) {
                val end = minOf(offset + limit, all.size)
                val entries = all.subList(offset, end)
                offset = end
                val page = resolvePage(entries, snippets)
                if (page.isNotEmpty() || offset >= all.size) {
                    return SearchPage(hits = page, totalHits = total, isLastPage = offset >= all.size && !more)
                }
            }
            return if (more) restPage(limit, snippets, total) else null
        }

        private suspend fun restPage(
            limit: Int,
            snippets: Boolean,
            total: Long,
        ): SearchPage? {
            val session =
                rest ?: lexical
                    .openSession(query, near = near, bookIds = bookIds, baseBookOnly = baseOnly)
                    // Its first CANDIDATES are the fused list's lexical side
                    ?.also { opened ->
                        opened.nextPage(CANDIDATES, snippets = false)
                        rest = opened
                    } ?: return null
            while (true) {
                val page = session.nextPage(limit, snippets = false) ?: return null
                val entries = page.hits.filter { shown.add(it.lineId) }.map { FusedEntry(it.lineId, it.score, it) }
                val hits = resolvePage(entries, snippets)
                if (hits.isNotEmpty() || page.isLastPage) return SearchPage(hits = hits, totalHits = total, isLastPage = page.isLastPage)
            }
        }

        private suspend fun resolvePage(
            entries: List<FusedEntry>,
            snippets: Boolean,
        ): List<LineHit> {
            val lexicalHits = entries.mapNotNull { it.lexical }
            val withSnippets = if (snippets) lexical.attachSnippets(lexicalHits, query, near) else lexicalHits
            val lexicalById = withSnippets.associateBy { it.lineId }

            return entries.mapNotNull { entry ->
                val hit = lexicalById[entry.lineId] ?: denseHit(entry.lineId) ?: return@mapNotNull null
                // No word in bold: the passage closest in meaning is highlighted once ready (refinedSnippets), the one
                // rule every view highlights a found line with (SearchEngine.highlights)
                val snippet = if (snippets) lexical.rangeSnippet(hit.rawText, emptyList(), hit.lineStart, hit.lineEnd) else hit.snippet
                hit.copy(score = entry.score, snippet = snippet)
            }
        }

        private suspend fun denseHit(lineId: Long): LineHit? {
            val hit = resolveLine(lineId, query) ?: return null
            val text = SnippetSources.clean(hit.rawText)
            return hit.copy(rawText = text, lineStart = 0, lineEnd = text.length)
        }

        override suspend fun view(bookIds: Set<Long>?): SearchSession? {
            val all = fused ?: return null
            // Without the dense side the list goes on past its candidates, in a session of its own
            if (lexicalOnly && lexicalTotal > CANDIDATES) return null
            return View(if (bookIds == null) all else all.filter { it.bookId in bookIds })
        }

        /** Some of this session's list ([entries], in its order), paged from its first: no search, nothing to close. */
        private inner class View(
            private val entries: List<FusedEntry>,
        ) : SearchSession,
            MeaningFacets {
            private var at = 0

            override suspend fun nextPage(
                limit: Int,
                snippets: Boolean,
            ): SearchPage? {
                while (at < entries.size) {
                    val end = minOf(at + limit, entries.size)
                    val page = resolvePage(entries.subList(at, end), snippets)
                    at = end
                    if (page.isNotEmpty() || at >= entries.size) {
                        return SearchPage(hits = page, totalHits = entries.size.toLong(), isLastPage = at >= entries.size)
                    }
                }
                return null
            }

            override suspend fun meaningBookCounts(): Map<Long, Int> = this@HybridSession.meaningBookCounts()

            override suspend fun refinedSnippets(hits: List<LineHit>): Map<Long, String> = this@HybridSession.refinedSnippets(hits)

            override fun close() = Unit
        }

        override suspend fun meaningBookCounts(): Map<Long, Int> {
            val meaningOnly = fused.orEmpty().filter { it.lexical == null && it.bookId > 0 }
            if (meaningOnly.isEmpty()) return emptyMap()
            // Past the fused list's CANDIDATES the words find more lines, which their facets count already: those of these
            // lines the words find too are not the meaning search's own
            val alsoWords = if (lexicalTotal > CANDIDATES) wordHitsAmong(meaningOnly.map { it.lineId }) else emptySet()
            return meaningOnly.filterNot { it.lineId in alsoWords }.groupingBy { it.bookId }.eachCount()
        }

        // Which of [lineIds] the words find, where this session searches; none when the lexical side fails
        private suspend fun wordHitsAmong(lineIds: List<Long>): Set<Long> =
            runSuspendCatching {
                lexical
                    .openSession(query, near = near, bookIds = bookIds, lineIds = lineIds, baseBookOnly = baseOnly)
                    ?.use { it.nextPage(lineIds.size, snippets = false) }
                    ?.hits
                    .orEmpty()
                    .mapTo(HashSet()) { it.lineId }
            }.getOrDefault(emptySet())

        override suspend fun refinedSnippets(hits: List<LineHit>): Map<Long, String> {
            val shown = hits.filter { it.rawText.isNotBlank() && it.lineEnd > it.lineStart }
            if (shown.isEmpty()) return emptyMap()
            // The found line alone, not the neighbors its snippet shows around it
            val highlights = highlights(shown.map { it.rawText.substring(it.lineStart, it.lineEnd) }, query)
            return buildMap {
                shown.forEachIndexed { i, hit ->
                    val ranges = highlights[i].ranges.map { (it.first + hit.lineStart)..(it.last + hit.lineStart) }
                    if (ranges.isNotEmpty()) put(hit.lineId, lexical.rangeSnippet(hit.rawText, ranges, hit.lineStart, hit.lineEnd))
                }
            }
        }

        override fun close() {
            rest?.close()
        }
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000

        private fun millisSince(start: Long) = (System.nanoTime() - start) / NANOS_PER_MILLI

        private const val RRF_K = 60
        private const val SMART_FIND_NEAR = 5
        private const val CANDIDATES = 150

        /** The words' first hits the dense side's walk starts again at, when it came back short: 16 were enough, 4 not. */
        private const val DENSE_SEEDS = 64

        /** The places (0-based) the lexical side's first two hits are moved up to at worst: 2 and 4. */
        private val LEXICAL_PLACES = intArrayOf(1, 3)

        /** Fewer query words than this are no quote: a line holding one word is merely a line that has it. */
        private const val MIN_QUOTE_WORDS = 2

        /** Words beyond the query's own the window holding all its words may span ([holdsQuote]). */
        private const val QUOTE_SLACK = 2

        /** The one-letter prefixes a word may carry in a quote and not in its source, or the reverse ([stem]). */
        internal const val PREFIXES = "והבכלמש"

        /** From this many words a query is a sentence a lexical hit can hold word for word ([fusedScores]). */
        private const val VERBATIM_WORDS = 3

        /** What a lexical hit holding the query word for word weighs in the RRF, the others 1 ([fusedScores]). */
        private const val VERBATIM_WEIGHT = 3.0

        /** A line only the dense side finds, of this many words or fewer, stays out of a search of several books. */
        private const val SHORT_LINE_WORDS = 8

        /** What a line holding all the query's words in its best window gains ([fusedScores], [coverage]). */
        private const val WINDOW_BONUS = 0.03

        /**
         * The fused scores of the [lexical] and [dense] candidates (each in its side's order): RRF (k = [RRF_K], the dense
         * side weighing [denseWeight]) with three changes, measured on the feedback's graded searches, 157 source
         * searches, eval-100 and eval-real (docs/fusion-compare: SOURCE_TEST.md, EXPANSIONS.md, IMPLEMENTATION.md;
         * "p12wl" there, with [keepLeaders] and [LevelPrecedence] after it):
         * - a lexical hit that holds a query of [VERBATIM_WORDS] words or more word for word ([verbatimKey]) weighs
         *   [VERBATIM_WEIGHT]: a line the words find and the model doesn't is otherwise below the first 121 dense hits;
         * - in a search of several books ([general]) a line only the dense side finds, of [SHORT_LINE_WORDS] words or
         *   fewer, is left out (a heading, a line of a list: the model ranks such lines close to anything), and the dense
         *   ranks are counted without it;
         * - every line gains [WINDOW_BONUS] times the square of the share of the query's words its best window holds
         *   ([coverage]).
         * A line whose text [textOf] doesn't give takes none of them: without the library's lines this is RRF alone. So is
         * a search the words find nothing for: the dense side's order, as measured.
         */
        internal fun fusedScores(
            lexical: List<Long>,
            dense: List<Long>,
            denseWeight: Double,
            query: String,
            general: Boolean,
            textOf: (Long) -> String?,
        ): Map<Long, Double> {
            if (lexical.isEmpty()) {
                val alone = HashMap<Long, Double>()
                dense.forEachIndexed { rank, id -> alone.merge(id, denseWeight / (RRF_K + rank + 1), Double::plus) }
                return alone
            }
            val queryKey = verbatimKey(query)
            val sentence = queryKey.isNotEmpty() && wordCount(queryKey) >= VERBATIM_WORDS
            val keys = HashMap<Long, String>()

            fun keyOf(id: Long): String? = keys[id] ?: textOf(id)?.let(::verbatimKey)?.also { keys[id] = it }

            val scores = HashMap<Long, Double>()
            lexical.forEachIndexed { rank, id ->
                val weight = if (sentence && keyOf(id)?.contains(queryKey) == true) VERBATIM_WEIGHT else 1.0
                scores.merge(id, weight / (RRF_K + rank + 1), Double::plus)
            }
            val found = lexical.toHashSet()
            dense
                .filterNot { id -> general && id !in found && keyOf(id)?.let { wordCount(it) <= SHORT_LINE_WORDS } == true }
                .forEachIndexed { rank, id -> scores.merge(id, denseWeight / (RRF_K + rank + 1), Double::plus) }
            val wanted = queryWords(query)
            for (id in scores.keys.toList()) {
                val share = keyOf(id)?.let { coverage(it, wanted) } ?: 0.0
                if (share > 0.0) scores[id] = scores.getValue(id) + WINDOW_BONUS * share * share
            }
            return scores
        }

        /** The words of a [verbatimKey]. */
        private fun wordCount(key: String): Int = if (key.isEmpty()) 0 else key.count { it == ' ' } + 1

        /** The query's distinct words of two letters or more, as [stem]s, in their order ([coverage]). */
        internal fun queryWords(query: String): List<String> =
            verbatimKey(query)
                .split(' ')
                .filter { it.length > 1 }
                .map(::stem)
                .distinct()

        /**
         * The share of [wanted] (the query's words, [queryWords]) that the best window of the line [lineKey] (its
         * [verbatimKey]) holds: a window of twice the query's words, at least four more than them, starting at a word of
         * the query. 0 for a query of fewer than two words.
         */
        internal fun coverage(
            lineKey: String,
            wanted: List<String>,
        ): Double {
            val n = wanted.size
            if (n < 2 || lineKey.isEmpty()) return 0.0
            val want = wanted.toHashSet()
            val line = lineKey.split(' ').map(::stem)
            val window = maxOf(2 * n, n + 4)
            var best = 0
            for (i in line.indices) {
                if (line[i] in want) {
                    best = maxOf(best, line.subList(i, minOf(i + window, line.size)).filterTo(HashSet()) { it in want }.size)
                    if (best == n) break
                }
            }
            return best.toDouble() / n
        }

        /**
         * [fused] with each of [leaders] (lexical hits, in order, each with its place) moved up to that place, or left
         * where it is when the fusion ranks it higher; a leader whose place an earlier one took comes right after it.
         * With the dense side weighing 3, a line only the words find scores 1/61 at best, below each of the first 121
         * dense hits: an exact quote the model misses would never reach the first page (pardes-semantic/eval-100).
         */
        internal fun <T> keepLeaders(
            fused: List<T>,
            leaders: Map<T, Int>,
        ): List<T> {
            val placeOf = LinkedHashMap<T, Int>()
            for ((id, wanted) in leaders) {
                val at = fused.indexOf(id)
                var place = if (at in 0..wanted) at else wanted
                while (place in placeOf.values) place++
                placeOf[id] = place
            }
            val out = fused.filterNot { it in placeOf }.toMutableList()
            // From the first place down, so that no insertion moves a leader already placed
            placeOf.entries.sortedBy { it.value }.forEach { (id, place) -> out.add(minOf(place, out.size), id) }
            return out
        }

        /**
         * Whether [text] (a line, HTML) holds [query] as a quote, the way one writes it from memory: all its words, each
         * with or without one-letter prefixes ([stem]), within a window of the query's length plus [QUOTE_SLACK] words,
         * in any order ("דרך ארץ קדמה לתורה" in "קדמה דרך ארץ את התורה"); not words scattered over a long line. A query
         * of one word is no quote ([quoteKey]).
         */
        internal fun holdsQuote(
            text: String,
            query: String,
        ): Boolean {
            val words = quoteKey(query)?.split(' ')?.map(::stem) ?: return false
            val wanted = words.toSet()
            val line = verbatimKey(text).split(' ').map(::stem)
            val window = words.size + QUOTE_SLACK
            return line.indices.any { i ->
                line[i] in wanted && wanted.all { it in line.subList(i, minOf(i + window, line.size)) }
            }
        }

        /** [word] (a [verbatimKey] word) without up to two one-letter [PREFIXES], each off a word of more than 3 letters. */
        internal fun stem(word: String): String {
            var w = word
            repeat(2) { if (w.length > 3 && w[0] in PREFIXES) w = w.substring(1) }
            return w
        }

        /** [query] as [verbatimKey], or null when it has fewer than [MIN_QUOTE_WORDS] words. */
        internal fun quoteKey(query: String): String? =
            verbatimKey(query).takeIf { key -> key.isNotEmpty() && key.count { it == ' ' } + 1 >= MIN_QUOTE_WORDS }

        /**
         * [s] (text or a line's HTML) as its letters and digits, words split by single spaces, to tell whether a line
         * holds a query word for word: markup removed (a tag inside a word, as `<big>בְּ</big>רֵאשִׁית`, leaves it
         * whole), niqqud, teamim, gershayim and quotes removed, maqaf and punctuation as spaces, final letters as their
         * base forms, Latin letters in lower case.
         */
        internal fun verbatimKey(s: String): String {
            val sb = StringBuilder(s.length)
            var space = true
            var i = 0
            while (i < s.length) {
                val c = s[i]
                val tagEnd = if (c == '<') s.indexOf('>', i + 1) else -1
                val entityEnd = if (c == '&') entityEnd(s, i) else -1
                // A tag that ends a line parts words, as an entity does; any other tag is left out
                val parts =
                    when {
                        tagEnd >= 0 -> isBlockTag(s, i + 1, tagEnd)
                        entityEnd >= 0 -> true
                        HebrewTextUtils.isNikudOrTeamim(c) || c == '\u05F3' || c == '\u05F4' || c == '"' || c == '\'' -> false
                        c.isLetterOrDigit() -> {
                            sb.append(HebrewTextUtils.baseLetter(c.lowercaseChar()))
                            space = false
                            false
                        }
                        else -> true
                    }
                if (parts && !space) {
                    sb.append(' ')
                    space = true
                }
                i = maxOf(i, tagEnd, entityEnd) + 1
            }
            return sb.toString().trimEnd()
        }

        /** Whether the tag inside [s] from [from] to [to] (between its `<` and `>`) ends a line: `br`, `/p`, `/div`. */
        private fun isBlockTag(
            s: String,
            from: Int,
            to: Int,
        ): Boolean {
            if (s.regionMatches(from, "br", 0, 2, ignoreCase = true)) {
                var i = from + 2
                while (i < to && (s[i] == ' ' || s[i] in '\t'..'\r')) i++
                if (i < to && s[i] == '/') i++
                return i == to
            }
            return (to - from == 2 && s.regionMatches(from, "/p", 0, 2, ignoreCase = true)) ||
                (to - from == 4 && s.regionMatches(from, "/div", 0, 4, ignoreCase = true))
        }

        /** The index of the `;` that ends the entity at [at] in [s] (`&amp;`, `&#160;`), or -1 when none starts there. */
        private fun entityEnd(
            s: String,
            at: Int,
        ): Int {
            var i = at + 1
            if (i < s.length && s[i] == '#') i++
            val start = i
            while (i < s.length && (s[i] in 'a'..'z' || s[i] in 'A'..'Z' || s[i] in '0'..'9' || s[i] == '_')) i++
            return if (i > start && i < s.length && s[i] == ';') i else -1
        }
    }
}

/**
 * The library's lines of [ids] (their book and text), for the fusion's weights ([HybridSearchEngine.fusedScores]) and the
 * precedence; null when the library can't be read: the fusion is then RRF alone.
 */
private suspend fun LibraryLines.candidateLines(ids: List<Long>): Map<Long, LineText>? =
    withContext(Dispatchers.IO) {
        runSuspendCatching { lines(ids) }
            .onFailure { warnln(it) { "[HybridSearchEngine] The library's lines unavailable; RRF alone." } }
            .getOrNull()
    }

package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
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
 * using Reciprocal Rank Fusion: RRF(doc) = Σ 1/(K + rank), the dense side weighed per model, with the lexical side's
 * first hits that hold the query as a quote kept on the first page ([keepLeaders], [holdsQuote]), and the earlier level
 * first among the lines that hold it as a sentence ([LevelPrecedence], [BookLevels]).
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
) : SearchEngine {
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
    )

    private suspend fun fuse(
        query: String,
        near: Int,
        bookIds: Collection<Long>?,
        baseOnly: Boolean,
    ): List<FusedEntry> =
        coroutineScope {
            val dense =
                async(Dispatchers.Default) {
                    val sem = ensureSemantic() ?: return@async null
                    runSuspendCatching { sem.search(query, CANDIDATES, baseOnly, bookIds) }
                        .onFailure { warnln(it) { "[HybridSearchEngine] Dense KNN failed; using lexical results." } }
                        .getOrNull()
                }

            // A lexical failure must not sink the search either: a long query's dictionary expansions can pass Lucene's
            // 1024 clauses (IndexSearcher.TooManyNestedClauses), and the dense side alone still answers it
            val lexHits =
                runSuspendCatching {
                    lexical
                        .openSession(query, near = near, bookIds = bookIds, baseBookOnly = baseOnly)
                        ?.use { it.nextPage(CANDIDATES, snippets = false)?.hits }
                        .orEmpty()
                }.getOrElse { e ->
                    warnln(e) { "[HybridSearchEngine] Lexical search failed; using dense results." }
                    emptyList()
                }

            val denseHits = dense.await()
            if (denseHits.isNullOrEmpty()) {
                return@coroutineScope lexHits.map { FusedEntry(it.lineId, it.score, it) }
            }

            val rrf = HashMap<Long, Double>()
            lexHits.forEachIndexed { rank, hit ->
                rrf.merge(hit.lineId, 1.0 / (RRF_K + rank + 1), Double::plus)
            }
            val denseWeight = semantic?.hybridWeight ?: 1.0
            denseHits.forEachIndexed { rank, hit ->
                rrf.merge(hit.lineId, denseWeight / (RRF_K + rank + 1), Double::plus)
            }

            val lexById = lexHits.associateBy { it.lineId }
            val order = rrf.entries.sortedByDescending { it.value }.map { it.key }
            // The lexical side's first hits that hold the query as a quote keep a place on the first page; the others rank
            // as fused. On real searches the lexical first hit was mostly a long line the query's words are scattered in:
            // kept whatever it held, it cost the first page more than it gave
            val leaders = LinkedHashMap<Long, Int>()
            if (quoteKey(query) != null) {
                lexHits.take(LEXICAL_PLACES.size).forEachIndexed { j, hit ->
                    val text = runSuspendCatching { resolveLine(hit.lineId, query)?.rawText }.getOrNull()
                    if (text != null && holdsQuote(text, query)) leaders[hit.lineId] = LEXICAL_PLACES[j]
                }
            }
            // The order is the list's from here, not the scores': a kept lexical hit keeps its own (lower) score
            val page = keepLeaders(order, leaders)
            // Within one book every line has the same level: no line to put first
            (if (bookIds?.size == 1) page else withPrecedence(page, query))
                .map { lineId -> FusedEntry(lineId, rrf.getValue(lineId).toFloat(), lexById[lineId]) }
        }

    /**
     * [page] with the earlier level first among its lines that hold [query] as a sentence ([LevelPrecedence]), as the
     * library's book levels and lines tell; [page] as it is when they can't be read.
     */
    private suspend fun withPrecedence(
        page: List<Long>,
        query: String,
    ): List<Long> {
        val library = library ?: return page
        if (quoteKey(query) == null) return page
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                val levels = library.levels()
                val lines = library.lines(page)
                LevelPrecedence.order(page, query, { lines[it]?.text }) { id ->
                    lines[id]?.let { levels[it.bookId] } ?: BookLevels.UNKNOWN
                }
            }.onFailure { warnln(it) { "[HybridSearchEngine] The library's levels unavailable; the fused order as is." } }
                .getOrDefault(page)
        }
    }

    private inner class HybridSession(
        private val query: String,
        private val near: Int,
        private val bookIds: Collection<Long>?,
        private val baseOnly: Boolean,
    ) : SearchSession {
        private var fused: List<FusedEntry>? = null
        private var offset = 0

        override suspend fun nextPage(
            limit: Int,
            snippets: Boolean,
        ): SearchPage? {
            val all = fused ?: fuse(query, near, bookIds, baseOnly).also { fused = it }
            while (offset < all.size) {
                val end = minOf(offset + limit, all.size)
                val entries = all.subList(offset, end)
                offset = end
                val page = resolvePage(entries, snippets)
                if (page.isNotEmpty() || offset >= all.size) {
                    return SearchPage(hits = page, totalHits = all.size.toLong(), isLastPage = offset >= all.size)
                }
            }
            return null
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

        override fun close() {}
    }

    companion object {
        private const val RRF_K = 60
        private const val SMART_FIND_NEAR = 5
        private const val CANDIDATES = 150

        /** The places (0-based) the lexical side's first two hits are moved up to at worst: 2 and 4. */
        private val LEXICAL_PLACES = intArrayOf(1, 3)

        /** Fewer query words than this are no quote: a line holding one word is merely a line that has it. */
        private const val MIN_QUOTE_WORDS = 2

        /** Words beyond the query's own the window holding all its words may span ([holdsQuote]). */
        private const val QUOTE_SLACK = 2

        /** The one-letter prefixes a word may carry in a quote and not in its source, or the reverse ([stem]). */
        internal const val PREFIXES = "והבכלמש"

        /**
         * [fused] with each of [leaders] (lexical hits, in order, each with its place) moved up to that place, or left
         * where it is when the fusion ranks it higher; a leader whose place an earlier one took comes right after it.
         * With the dense side weighing 3, a line only the words find scores 1/61 at best, below each of the first 121
         * dense hits: an exact quote the model misses would never reach the first page (semantic-model/eval-100).
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

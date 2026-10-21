// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.util.Log
import io.github.kdroidfilter.seforim.htmlparser.plainTextFromHtml
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The matches of the find-in-page query in a book, in reading order: each matching line's index and id, and a prefix
 * sum of its match count. Verbatim port of desktop 0.6.1 features/bookcontent/usecases/BookFindMatches.kt.
 */
class BookFindMatches internal constructor(
    private val lineIndices: IntArray,
    internal val lineIds: LongArray,
    // firstOccurrence[i] = matches before the line i; one more entry holding the total
    private val firstOccurrence: IntArray,
) {
    /** Matches in the whole book, several in a line counting as several. */
    val occurrences: Int get() = firstOccurrence.last()

    data class Match(
        val lineIndex: Int,
        val lineId: Long,
        /** Rank of the match within its line, from 0. */
        val ordinal: Int,
        /** Rank of the match within the book, from 1, as shown to the user. */
        val position: Int,
    )

    /** The match after [current], or before it when not [forward], wrapping around the book. */
    fun next(current: Match, forward: Boolean): Match? {
        val i = lineIndices.binarySearch(current.lineIndex)
        if (i < 0) return nextAfterLine(current.lineIndex, forward)
        val ordinal = current.ordinal + if (forward) 1 else -1
        return when {
            ordinal in 0 until countIn(i) -> matchAt(i, ordinal)
            forward -> matchAt(if (i + 1 < lineIndices.size) i + 1 else 0, 0)
            else -> (if (i > 0) i - 1 else lineIndices.lastIndex).let { matchAt(it, countIn(it) - 1) }
        }
    }

    /** The first match of the first line after [fromLineIndex], or when not [forward] the last match before it, wrapping. */
    fun nextAfterLine(fromLineIndex: Int, forward: Boolean): Match? {
        if (lineIndices.isEmpty()) return null
        val pos = lineIndices.binarySearch(fromLineIndex)
        return if (forward) {
            matchAt((if (pos >= 0) pos + 1 else -pos - 1).takeIf { it < lineIndices.size } ?: 0, 0)
        } else {
            val i = (if (pos >= 0) pos - 1 else -pos - 2).takeIf { it >= 0 } ?: lineIndices.lastIndex
            matchAt(i, countIn(i) - 1)
        }
    }

    /** Whether [match] is one of these matches: a match kept from older results may no longer be. */
    operator fun contains(match: Match): Boolean {
        val i = lineIndices.binarySearch(match.lineIndex)
        return i >= 0 && lineIds[i] == match.lineId && match.ordinal in 0 until countIn(i) && match.position == firstOccurrence[i] + match.ordinal + 1
    }

    /** Whether the line [lineId] at [lineIndex] holds matches. */
    fun hasLine(lineIndex: Int, lineId: Long): Boolean {
        val i = lineIndices.binarySearch(lineIndex)
        return i >= 0 && lineIds[i] == lineId
    }

    private fun countIn(i: Int) = firstOccurrence[i + 1] - firstOccurrence[i]

    private fun matchAt(i: Int, ordinal: Int) = Match(lineIndices[i], lineIds[i], ordinal, firstOccurrence[i] + ordinal + 1)

    companion object {
        val Empty = BookFindMatches(IntArray(0), LongArray(0), IntArray(1))

        /** Whole-line matches (the smart mode's), one occurrence each. */
        fun ofLines(lines: List<Line>): BookFindMatches {
            val sorted = lines.distinctBy { it.id }.sortedBy { it.lineIndex }
            return BookFindMatches(
                lineIndices = IntArray(sorted.size) { sorted[it].lineIndex },
                lineIds = LongArray(sorted.size) { sorted[it].id },
                firstOccurrence = IntArray(sorted.size + 1) { it },
            )
        }
    }
}

/**
 * Finds [query] in the whole book [bookId], loaded lines or not (desktop findInBook): Lucene narrows the book down to
 * its candidate lines, read [FIND_CHUNK] at a time and matched exactly as the highlight does; the book is scanned when
 * the index can't answer. Never interrupted: the engine shares one NIOFS reader (see SearchService.countFacets).
 *
 * @param narrowing matches of a query [query] contains: only their lines can hold [query]
 */
suspend fun findInBook(
    searchEngine: SearchEngine?,
    loadLines: suspend (List<Long>) -> List<Line>,
    loadRange: suspend (start: Int, endInclusive: Int) -> List<Line>,
    bookId: Long,
    query: String,
    narrowing: BookFindMatches? = null,
): BookFindMatches = withContext(Dispatchers.Default) {
    val candidates = narrowing?.lineIds ?: try {
        searchEngine?.let { engine -> withContext(Dispatchers.IO) { engine.findInBookCandidates(query, bookId) } }
    } catch (e: IOException) {
        Log.d("Zayit", "find-in-book: index unavailable, scanning the book: $e")
        null
    } catch (e: RuntimeException) {
        Log.d("Zayit", "find-in-book: index query failed, scanning the book: $e")
        null
    }
    val normalizedQuery = normalizeQueryForHebrew(query)
    val found = ArrayList<Triple<Int, Long, Int>>()

    fun confirm(lines: List<Line>) {
        for (line in lines) {
            ensureActive()
            val count = findAllMatchesNormalized(plainTextFromHtml(line.content), normalizedQuery).size
            if (count > 0) found += Triple(line.lineIndex, line.id, count)
        }
    }
    if (candidates != null) {
        for (chunk in candidates.asList().chunked(FIND_CHUNK)) confirm(loadLines(chunk))
    } else {
        var start = 0
        do {
            val lines = loadRange(start, start + FIND_CHUNK - 1)
            confirm(lines)
            start += FIND_CHUNK
        } while (lines.size == FIND_CHUNK)
    }
    found.sortBy { it.first }
    val firstOccurrence = IntArray(found.size + 1)
    found.forEachIndexed { i, (_, _, count) -> firstOccurrence[i + 1] = firstOccurrence[i] + count }
    BookFindMatches(IntArray(found.size) { found[it].first }, LongArray(found.size) { found[it].second }, firstOccurrence)
}

/**
 * Desktop's smart find on a search result (BookContentView semanticFind + [BookFindMatches.ofLines]), with the
 * lexical engine, as Android has no embedding add-on: the lines of [bookId] the search for [query] finds, one match
 * each, lit with the search's own highlighting ([SearchEngine.highlightRanges]), all their spans at once. A literal
 * find would miss what the search highlights: the words apart ("תהו ... ובהו"), other spellings ("תוהו ובוהו") and
 * inflections. Only the lines that highlighting lights count, so the counter and ▲ ▼ go over exactly what is lit.
 */
suspend fun findSearchMatchesInBook(
    searchEngine: SearchEngine,
    loadLines: suspend (List<Long>) -> List<Line>,
    bookId: Long,
    query: String,
): BookFindMatches = withContext(Dispatchers.Default) {
    val ids = LinkedHashSet<Long>()
    withContext(Dispatchers.IO) {
        // Paged without snippets (ids only). Cancelled between pages, never interrupted (see findInBook)
        searchEngine.openSession(query, SEARCH_NEAR, bookFilter = bookId)?.use { session ->
            while (true) {
                ensureActive()
                val page = session.nextPage(FIND_CHUNK, snippets = false) ?: break
                page.hits.forEach { if (it.bookId == bookId) ids += it.lineId }
                if (page.isLastPage) break
            }
        }
    }
    val lit = ArrayList<Line>()
    for (chunk in ids.toList().chunked(FIND_CHUNK)) {
        for (line in loadLines(chunk)) {
            ensureActive()
            if (searchEngine.highlightRanges(plainTextFromHtml(line.content), query).isNotEmpty()) lit += line
        }
    }
    BookFindMatches.ofLines(lit)
}

/** Whether every line holding [query] also holds [previous], so the matches of [previous] can narrow its search. */
fun queryNarrows(query: String, previous: String): Boolean {
    val prev = normalizeQueryForHebrew(previous)
    return prev.length >= 2 && normalizeQueryForHebrew(query).contains(prev)
}

// Lines read per DB query: bounds memory, and stays under SQLite's bound-parameter limit
private const val FIND_CHUNK = 500

// The search screen's proximity (desktop ExecuteSearchUseCase.DEFAULT_NEAR): the lines it listed for this book
private const val SEARCH_NEAR = 5

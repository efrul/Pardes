package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.github.kdroidfilter.seforim.htmlparser.plainTextFromHtml
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesNormalized
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.features.bookcontent.find.foundLines
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookFindMatches
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Pardes (stage 3 of docs/DESKTOP_STAGE3_PLAN.md): the find on the daf searches Rashi and Tosafot too, as the user asked
// on 2026-11-19 ("במצב צורת הדף החיפוש יהיה גם על רש"י ותוספות"). The daf drew their matches already, as it draws every
// line printed on it; the count and the steps of the find went through the Gemara alone.

/**
 * What the find on the daf searches besides the Gemara: the tractate's comments of each commentary column, as read
 * ([comments], R Rashi and T Tosafot, [DafIndex.comments]), where each line of the library is printed first
 * ([places], [DafIndex.places]: the amud's place in the order, and its top), and the commentaries' [books] (the
 * results pane reads their contents, whose entries are the Gemara's: דף ב., דף ב:).
 */
@Immutable
class DafPrinted(
    val comments: Map<String, List<Long>>,
    val places: Map<Long, Pair<Int, Float>>,
    val books: Set<Long> = emptySet(),
)

/**
 * The find's matches in the Gemara ([gemara]), those with the comments printed on the daf ([matches]), and the comments
 * found ([comments]: by meaning, the daf marks them whole, as it marks the Gemara's lines found so).
 */
@Immutable
class DafFound(
    val gemara: BookFindMatches,
    val matches: BookFindMatches,
    val comments: Set<Long> = emptySet(),
)

/**
 * The find's matches of [query] in the Gemara ([gemara]) with those in the comments [printed] on the daf, worked out
 * off the UI thread: by its words ([withComments], the lines read from [repository], their text once a tractate), or
 * by meaning ([smart], the user's question of 2026-11-19 "למה?": [withMeaningComments], each commentary's lines that
 * [searchEngine] finds, as the Gemara's). Null when the daf is not shown ([printed] null), and until they are.
 */
@Composable
fun rememberDafFound(
    gemara: BookFindMatches,
    query: String,
    smart: Boolean,
    printed: DafPrinted?,
    repository: SeforimRepository,
    searchEngine: SearchEngine,
): State<DafFound?> {
    // Read by one search at a time, but a search dropped for the next may still be writing
    val texts = remember(printed) { ConcurrentHashMap<Long, String>() }
    return produceState<DafFound?>(null, gemara, query, smart, printed) {
        value =
            if (printed == null) {
                null
            } else {
                runSuspendCatching {
                    if (smart) {
                        withMeaningComments(gemara, printed, query) { book -> searchEngine.semanticFind(query, book, MEANING_LIMIT) }
                    } else {
                        DafFound(gemara, withComments(gemara, printed, query, texts, repository::getLinesByIds))
                    }
                }.getOrNull()
            }
    }
}

/**
 * [gemara], the find's matches of [query] in the tractate's Gemara, with its matches in the comments [printed] on the
 * daf, as the daf is read: amud by amud, on each the Gemara's, then Rashi's, then Tosafot's, each from its top down
 * (a Gemara line the daf does not print, at the end). Their line indices are their places in that order: the daf goes
 * to a match by its line, and the results pane to a line by its index. [texts] keeps the comments' text as read.
 */
internal suspend fun withComments(
    gemara: BookFindMatches,
    printed: DafPrinted,
    query: String,
    texts: MutableMap<Long, String>,
    loadLines: suspend (List<Long>) -> List<Line>,
): BookFindMatches {
    val q = normalizeQueryForHebrew(query)
    if (q.length < MIN_QUERY) return gemara
    val ids =
        printed.comments.values
            .flatten()
            .distinct()
    for (chunk in ids.filterNot(texts::containsKey).chunked(CHUNK)) {
        val lines = loadLines(chunk)
        withContext(Dispatchers.Default) { for (line in lines) texts[line.id] = plainTextFromHtml(line.content) }
    }
    val counts =
        withContext(Dispatchers.Default) {
            ids.mapNotNull { id -> texts[id]?.let { findAllMatchesNormalized(it, q).size }?.takeIf { it > 0 }?.let { id to it } }
        }
    return inDafOrder(gemara, counts, printed)
}

/**
 * The Gemara's lines found by meaning ([gemara]) with each commentary's lines on the daf that [findByMeaning] finds
 * (the search by meaning of one book, as the Gemara's: its words fused with the lines closest in meaning), in the
 * daf's order as [withComments] puts them, each once.
 */
internal suspend fun withMeaningComments(
    gemara: BookFindMatches,
    printed: DafPrinted,
    query: String,
    findByMeaning: suspend (bookId: Long) -> List<Long>,
): DafFound {
    if (normalizeQueryForHebrew(query).length < MIN_QUERY) return DafFound(gemara, gemara)
    val printedComments =
        printed.comments.values
            .flatten()
            .toSet()
    val found =
        printed.books
            .flatMap { findByMeaning(it) }
            .filter { it in printedComments }
            .distinct()
    return DafFound(gemara, inDafOrder(gemara, found.map { it to 1 }, printed), found.toSet())
}

// The Gemara's matches and the comments' ([comments], each with its count), as the daf is read: amud by amud, the
// Gemara's, then Rashi's, then Tosafot's, each from its top down; their line indices their places in that order
private fun inDafOrder(
    gemara: BookFindMatches,
    comments: List<Pair<Long, Int>>,
    printed: DafPrinted,
): BookFindMatches {
    val column =
        printed.comments.entries
            .flatMap { (name, ids) -> ids.map { it to columnRank(name) } }
            .toMap()
    val lines = gemara.foundLines().map { Found(it.lineId, it.count, 0, it.lineIndex) }
    val found =
        (lines + comments.map { (id, count) -> Found(id, count, column[id] ?: COMMENT, Int.MAX_VALUE) }).sortedWith(
            compareBy<Found>({ printed.places[it.lineId]?.first ?: Int.MAX_VALUE }, { it.column })
                .thenBy { printed.places[it.lineId]?.second ?: Float.MAX_VALUE }
                .thenBy { it.lineIndex },
        )
    val firstOccurrence = IntArray(found.size + 1)
    found.forEachIndexed { i, line -> firstOccurrence[i + 1] = firstOccurrence[i] + line.count }
    return BookFindMatches(
        lineIndices = IntArray(found.size) { it },
        lineIds = LongArray(found.size) { found[it].lineId },
        firstOccurrence = firstOccurrence,
    )
}

// A line found: its id, its matches, its column's rank (the Gemara first) and its place in the Gemara
private class Found(
    val lineId: Long,
    val count: Int,
    val column: Int,
    val lineIndex: Int,
)

// Rashi before Tosafot, as an amud is read; another commentary after them
private fun columnRank(column: String): Int =
    when (column) {
        "R" -> 1
        "T" -> 2
        else -> COMMENT
    }

private const val COMMENT = 3

/** The Gemara's column in a layout's lines and books ([DafLine.c], [DafLibrary.books]), as [DafLine.isGemara] reads it. */
internal const val GEMARA_COLUMN = "G"

// The find's shortest query, as the text's
private const val MIN_QUERY = 2

// The comments read at a time, as the find reads the Gemara's lines
private const val CHUNK = 500

// A commentary's lines found by meaning, as many as the Gemara's (BookContentView's SMART_FIND_LIMIT)
private const val MEANING_LIMIT = 60

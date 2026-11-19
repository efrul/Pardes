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

/** The find's matches in the Gemara ([gemara]), and those with the comments printed on the daf ([matches]). */
@Immutable
class DafFound(
    val gemara: BookFindMatches,
    val matches: BookFindMatches,
)

/**
 * The find's matches of [query] in the Gemara ([gemara]) with those in the comments [printed] on the daf
 * ([withComments], their lines read from [repository]), worked out off the UI thread; null when the daf is not shown ([printed] null), for the smart mode
 * ([query] null), and until they are. The comments' text is read once a tractate.
 */
@Composable
fun rememberDafFound(
    gemara: BookFindMatches,
    query: String?,
    printed: DafPrinted?,
    repository: SeforimRepository,
): State<DafFound?> {
    // Read by one search at a time, but a search dropped for the next may still be writing
    val texts = remember(printed) { ConcurrentHashMap<Long, String>() }
    return produceState<DafFound?>(null, gemara, query, printed) {
        value =
            if (printed == null || query == null) {
                null
            } else {
                runSuspendCatching { DafFound(gemara, withComments(gemara, printed, query, texts, repository::getLinesByIds)) }.getOrNull()
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
    val column =
        printed.comments.entries
            .flatMap { (name, comments) -> comments.map { it to columnRank(name) } }
            .toMap()
    val found =
        withContext(Dispatchers.Default) {
            val lines = gemara.foundLines().map { Found(it.lineId, it.count, 0, it.lineIndex) }
            val comments =
                ids.mapNotNull { id ->
                    val count = texts[id]?.let { findAllMatchesNormalized(it, q).size } ?: 0
                    if (count > 0) Found(id, count, column[id] ?: COMMENT, Int.MAX_VALUE) else null
                }
            (lines + comments).sortedWith(
                compareBy<Found>({ printed.places[it.lineId]?.first ?: Int.MAX_VALUE }, { it.column })
                    .thenBy { printed.places[it.lineId]?.second ?: Float.MAX_VALUE }
                    .thenBy { it.lineIndex },
            )
        }
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

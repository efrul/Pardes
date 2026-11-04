package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.seforimapp.core.annotations.UserHighlight
import io.github.kdroidfilter.seforimapp.core.annotations.UserNote
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LocatedRange
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LocatedSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NoteDraftAnchor

// Where each printed word of the daf is in the library's own text, found when an amud is shown, against the library
// installed: the layout files keep the words as printed and the library line each is of, not where in it, and the
// print's words are not always the library's (the Gemara's library edition spells its abbreviations out, א״ר for
// אָמַר רַבִּי). The user's highlights and notes are kept in the library's text, so they are drawn on the daf, and made
// from it, through these.

/**
 * Where a printed word is in the library: the characters [start] until [end] of the line [lineId] (of book [bookId],
 * the [lineIndex]th of it), in the line's plain text with diacritics, the offsets highlights and notes are kept in. A
 * printed abbreviation stands for all the words it abbreviates.
 */
data class DafAnchor(
    val bookId: Long,
    val lineId: Long,
    val lineIndex: Int,
    val start: Int,
    val end: Int,
)

/** A library line as the anchors read it: its book, its index in the book, and its plain text with diacritics. */
internal class LibraryLine(
    val bookId: Long,
    val lineIndex: Int,
    val text: String,
)

/** A word of a library line: its characters [start] until [end] in the line's text, and its letters ([dafKey]). */
internal class LibraryWord(
    val start: Int,
    val end: Int,
    val key: String,
)

/** The words of a library line's [text], parted by spaces and maqafs; a piece with no letters (a dash) is none. */
internal fun libraryWords(text: String): List<LibraryWord> {
    val out = ArrayList<LibraryWord>()
    var i = 0
    while (i < text.length) {
        if (isBreak(text[i])) {
            i++
            continue
        }
        val start = i
        while (i < text.length && !isBreak(text[i])) i++
        val key = dafKey(text.substring(start, i))
        if (key.isNotEmpty()) out += LibraryWord(start, i, key)
    }
    return out
}

private fun isBreak(c: Char): Boolean = c.isWhitespace() || c == MAQAF

private const val MAQAF = '־'

/** The most library words one printed abbreviation stands for (אע״פ for אַף עַל פִּי). */
private const val MOST_ABBREVIATED = 4

/** A printed word the line has not, a line's word not printed (inside the part printed here), another word in place. */
private const val PRINTED_ONLY = -0.5f
private const val LIBRARY_ONLY = -0.5f
private const val OTHER_WORD = -0.3f

/**
 * For each of [printed] (a library line's words as printed here, in order), the library words of [library] it stands
 * for, or null: the best alignment of the two in order, each printed word with a word alike ([alike]), an
 * abbreviation with the words it abbreviates ([abbreviates]), another word in its place, or none. The line may begin
 * before the words printed here or end after them (on the amud before or after): its words there are free to skip.
 */
internal fun alignWords(
    printed: List<String>,
    library: List<LibraryWord>,
): List<IntRange?> {
    val m = printed.size
    val n = library.size
    if (m == 0 || n == 0) return List(m) { null }
    val keys = printed.map(::dafKey)
    val abbreviations = printed.map { word -> word.any { it in ABBREVIATION_MARKS } }
    val none = Float.NEGATIVE_INFINITY
    // score[i][j]: the first i printed words aligned with library words before j; move[i][j]: how it got there (0 a
    // library word skipped, -1 a printed word skipped, k > 0 the last printed word for k library words)
    val score = Array(m + 1) { FloatArray(n + 1) { none } }
    val move = Array(m + 1) { IntArray(n + 1) }
    for (j in 0..n) score[0][j] = 0f
    for (i in 0..m) {
        for (j in 0..n) {
            val here = score[i][j]
            if (here == none) continue
            if (j < n && i > 0) {
                val skipped = here + LIBRARY_ONLY
                if (skipped > score[i][j + 1]) {
                    score[i][j + 1] = skipped
                    move[i][j + 1] = 0
                }
            }
            if (i == m) continue
            val alone = here + PRINTED_ONLY
            if (alone > score[i + 1][j]) {
                score[i + 1][j] = alone
                move[i + 1][j] = -1
            }
            // A printed piece with no letters (a parenthesis alone) is no word of the line's
            if (keys[i].isEmpty()) continue
            for (k in 1..MOST_ABBREVIATED) {
                if (j + k > n) break
                val gain =
                    when {
                        k == 1 && !abbreviations[i] -> alike(keys[i], library[j].key).takeIf { it > 0f } ?: OTHER_WORD
                        abbreviations[i] && abbreviates(keys[i], (j until j + k).map { library[it].key }) -> 1f
                        k == 1 -> alike(keys[i], library[j].key).takeIf { it > 0f } ?: OTHER_WORD
                        else -> continue
                    }
                val value = here + gain
                if (value > score[i + 1][j + k]) {
                    score[i + 1][j + k] = value
                    move[i + 1][j + k] = k
                }
            }
        }
    }
    // The line's words after the last printed here are free too
    var j = (0..n).maxBy { score[m][it] }
    var i = m
    val out = arrayOfNulls<IntRange>(m)
    while (i > 0) {
        when (val k = move[i][j]) {
            0 -> j--
            -1 -> i--
            else -> {
                out[i - 1] = j - k until j
                i--
                j -= k
            }
        }
    }
    return out.toList()
}

/**
 * Each printed word of [amud] in its library line ([lines], by id), for the words whose line the library has: each
 * library line's words on the amud, in the order they are read, aligned with the line's words ([alignWords]).
 */
internal fun anchorsOf(
    amud: DafAmud,
    lines: Map<Long, LibraryLine>,
): Map<DafWord, DafAnchor> {
    val printed = LinkedHashMap<Long, MutableList<Pair<DafWord, String>>>()
    amud.lines.forEachIndexed { li, line ->
        line.t.split(' ').filter { it.isNotEmpty() }.forEachIndexed { wi, text ->
            val id = line.lineIdAt(wi) ?: return@forEachIndexed
            printed.getOrPut(id) { mutableListOf() } += DafWord(li, wi) to text
        }
    }
    val out = HashMap<DafWord, DafAnchor>()
    for ((id, words) in printed) {
        val line = lines[id] ?: continue
        val library = libraryWords(line.text)
        alignWords(words.map { it.second }, library).forEachIndexed { k, span ->
            if (span == null || span.isEmpty()) return@forEachIndexed
            out[words[k].first] = DafAnchor(line.bookId, id, line.lineIndex, library[span.first].start, library[span.last].end)
        }
    }
    return out
}

/**
 * The color of each word of the amud a user highlight is on ([highlights], by book: HighlightStore's), at [alpha]:
 * the first highlight on its line that overlaps its characters.
 */
internal fun highlightedWords(
    anchors: Map<DafWord, DafAnchor>,
    highlights: Map<Long, List<UserHighlight>>,
    alpha: Float,
): Map<DafWord, Color> {
    if (highlights.isEmpty()) return emptyMap()
    val byLine = HashMap<Long, List<UserHighlight>>()
    for ((_, list) in highlights) for ((lineId, ofLine) in list.groupBy { it.lineId }) byLine[lineId] = ofLine
    val out = HashMap<DafWord, Color>()
    for ((word, a) in anchors) {
        val on = byLine[a.lineId]?.firstOrNull { it.startOffset < a.end && it.endOffset > a.start } ?: continue
        out[word] = on.color.copy(alpha = alpha)
    }
    return out
}

/** The words of the amud a note of book [bookId] ([notes], NoteStore's) is on. */
internal fun notedWords(
    anchors: Map<DafWord, DafAnchor>,
    notes: List<UserNote>,
    bookId: Long,
): Set<DafWord> {
    if (notes.isEmpty()) return emptySet()
    val byLine = notes.groupBy { it.lineId }
    return anchors
        .filterTo(HashMap()) { (_, a) ->
            a.bookId == bookId && byLine[a.lineId]?.any { it.startOffset < a.end && it.endOffset > a.start } == true
        }.keys
}

/**
 * [range] of [amud] located in the library, for the text's menu: its words of the column it began in, each library
 * line's part of them (in that line's book, so a comment's highlight is its commentary's, as in the comments pane),
 * the note the main book's ([bookId]) first line of them anchors (notes are the main book's, as in the text), and the
 * Gemara segment a link to it goes to ([segmentOf]). Null when none of its words is found in the library.
 */
internal fun locate(
    amud: DafAmud,
    range: DafRange,
    anchors: Map<DafWord, DafAnchor>,
    bookId: Long,
    segmentOf: (DafWord) -> Long?,
): LocatedSelection? {
    val column = amud.lines.getOrNull(range.anchor.line)?.c ?: return null
    val words =
        (range.first.line..range.last.line).flatMap { li ->
            val line = amud.lines.getOrNull(li)?.takeIf { it.c == column } ?: return@flatMap emptyList()
            val count = line.t.split(' ').count { it.isNotEmpty() }
            val from = if (li == range.first.line) range.first.word else 0
            val to = if (li == range.last.line) range.last.word else count - 1
            (from..minOf(to, count - 1)).map { DafWord(li, it) }
        }
    val found = words.mapNotNull { w -> anchors[w]?.let { w to it } }
    if (found.isEmpty()) return null
    val parts = found.groupBy { (_, a) -> a.bookId to a.lineId }
    val ranges =
        parts.map { (key, ofLine) ->
            LocatedRange(key.first, key.second, ofLine.minOf { it.second.start }, ofLine.maxOf { it.second.end })
        }
    val note =
        parts.entries.firstOrNull { it.key.first == bookId }?.let { (key, ofLine) ->
            val texts =
                ofLine.map { (w, _) ->
                    amud.lines[w.line]
                        .t
                        .split(' ')
                        .filter { it.isNotEmpty() }[w.word]
                }
            NoteDraftAnchor(key.second, ofLine.minOf { it.second.start }, ofLine.maxOf { it.second.end }, texts.joinToString(" "))
        }
    return LocatedSelection(ranges = ranges, note = note, lineId = segmentOf(found.first().first))
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.ui.text.font.FontWeight
import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.CommentAnchorIndex
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.commentSegments
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.listedCommentators
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): what a book's daf sets, read from the library. A book is laid out a
// unit at a time (a part of its table of contents), each with its lines, the comments on them of the commentators the
// commentaries pane shows for each line, and the words each comment explains where Zayit's anchors put it
// (CommentAnchorIndex: Shulchan Aruch's comments, as the text's passages have them).

/** A part of a book its daf lays out at once: its lines [first] until [end] (indices, [end] excluded). */
internal data class DafUnit(
    val first: Int,
    val end: Int,
)

/**
 * A unit as its daf sets it: its lines and their references ([refs], by id), its commentators' comments on them, the
 * passages their anchors mark, and (verse by verse) the targum beside its verses.
 */
internal class DafUnitText(
    val text: List<DafSegment>,
    val refs: Map<Long, String>,
    val commentaries: List<DafCommentary>,
    /** Each line's passages its anchors begin: the words (indices in its [DafSegment.words]) and the comments on them. */
    val passages: Map<Long, List<Pair<IntRange, Set<Long>>>>,
    val targum: DafTargum? = null,
)

/** The most lines a unit has: a longer part of the table of contents is laid out in pieces of this many. */
internal const val MOST_UNIT_LINES = 40

/**
 * The units of a book of [totalLines] lines whose table of contents' entries begin at the lines [starts] (indices):
 * from each entry to the next, a longer one in pieces of [MOST_UNIT_LINES]; an entry of a line alone (a heading right
 * before another, as a book's title is) goes with the next.
 */
internal fun dafUnits(
    starts: List<Int>,
    totalLines: Int,
): List<DafUnit> {
    val bounds = (starts.filter { it in 0 until totalLines } + 0 + totalLines).distinct().sorted()
    val parts = bounds.zipWithNext().filter { (a, b) -> b > a }
    val merged = ArrayList<DafUnit>()
    var pending: Int? = null
    for ((a, b) in parts) {
        val first = pending ?: a
        if (b - a <= 1 && b < totalLines) {
            pending = first
            continue
        }
        pending = null
        var from = first
        while (from < b) {
            val to = minOf(b, from + MOST_UNIT_LINES)
            merged += DafUnit(from, to)
            from = to
        }
    }
    pending?.let { merged += DafUnit(it, totalLines) }
    return merged
}

/** The words of a library line as a book's daf prints them, those of [bold] in bold, and its anchors' passages. */
internal class DafWords(
    val words: List<String>,
    val bold: Set<Int>,
    /** Each passage the line's anchors mark: its words' indices, and the anchors that begin it. */
    val passages: List<Pair<IntRange, Set<CommentAnchor>>> = emptyList(),
)

/**
 * The words of a library line's [html] as the text shows them (its text parted by spaces), without what [diacritics]
 * hides; bold those most of whose letters are; the passages the anchors of [anchorCommentators] begin.
 */
internal fun dafWords(
    html: String,
    diacritics: DiacriticsMode,
    anchorCommentators: Set<String> = emptySet(),
): DafWords {
    val text = buildAnnotatedFromHtml(html, baseTextSize = 16f, boldScale = 1f, anchorCommentators = anchorCommentators)
    val bold = BooleanArray(text.length)
    for (span in text.spanStyles) if (span.item.fontWeight == FontWeight.Bold) for (i in span.start until span.end) bold[i] = true
    val words = ArrayList<String>()
    val boldWords = HashSet<Int>()
    // Each word's first character in the text, for the passages
    val starts = ArrayList<Int>()
    for (span in WORD.findAll(text.text)) {
        val word = span.value.filterNot(diacritics::hides)
        if (word.isEmpty()) continue
        if (span.range.count { bold[it] } * 2 > span.value.length) boldWords += words.size
        words += word
        starts += span.range.first
    }
    val passages =
        text.commentSegments().mapNotNull { segment ->
            val first = starts.indexOfFirst { it >= segment.start }
            val end = starts.indexOfFirst { it >= segment.end }.let { if (it < 0) words.size else it }
            if (first < 0 || first >= end) null else (first until end) to segment.anchors
        }
    return DafWords(words, boldWords, passages)
}

private val WORD = Regex("\\S+")

/** Whether a library line is a heading (a chapter's, a siman's), as the text shows it. */
internal fun isHeading(html: String): Boolean = HEADING.containsMatchIn(html)

private val HEADING = Regex("^\\s*<h[1-6]")

/** The most comments read at once; a unit with more is read in as many reads as needed. */
private const val COMMENTS_PAGE = 2_000

/**
 * The unit [unit] of book [bookId] as its daf sets it: its lines, and the comments on each of the commentators shown on
 * it ([selection] of those its [connections] list, which say their names too), each comment with the first of the
 * unit's lines it is on; their sides by their place in the [look]'s ranks (the first on the right), which take in the
 * ones not met yet, in the order the commentaries pane lists them. The words are without what the look's diacritics
 * hide, and the passages are those the book's anchors put the comments on.
 */
internal suspend fun loadDafUnit(
    repository: SeforimRepository,
    bookId: Long,
    unit: DafUnit,
    selection: CommentatorSelection,
    connections: suspend (List<Long>) -> Map<Long, LineConnectionsSnapshot>,
    look: DafUnitLook,
): DafUnitText {
    val lines = repository.getLines(bookId, unit.first, unit.end - 1)
    val ids = lines.map { it.id }
    val listed = connections(ids)
    val shown = ids.associateWith { id -> selection.shownOf(listed[id]?.commentatorGroups.orEmpty().listedCommentators()) }
    val names = HashMap<Long, String>()
    for (id in ids) {
        listed[id]?.commentatorGroups.orEmpty().forEach { group ->
            group.commentators.forEach { item ->
                if (item.bookId in shown.getValue(id)) {
                    names.putIfAbsent(item.bookId, item.name)
                    look.ranks.putIfAbsent(item.bookId, look.ranks.size)
                }
            }
        }
    }
    val comments =
        linksOf(repository, ids, names.keys, ConnectionType.COMMENTARY).filter {
            it.link.targetBookId in
                shown[it.link.sourceLineId].orEmpty()
        }
    val text = lines.map { it.asSegment(look.diacritics, look.anchors.commentators) }
    return DafUnitText(
        text = text.map { it.first },
        refs = lines.mapNotNull { line -> line.heRef?.takeIf { it.isNotBlank() }?.let { line.id to it } }.toMap(),
        commentaries = commentaries(lines, comments, names, look),
        passages = passagesOf(lines, text.associate { it.first.lineId to it.second }, comments, look.anchors),
        targum = if (look.style == DafStyle.Verses) targumOf(linksOf(repository, ids, emptySet(), ConnectionType.TARGUM), look) else null,
    )
}

/** The targum a book is read with, of its verses' [targums]: Onkelos where it is, else the one on most of them. */
private fun targumOf(
    targums: List<CommentaryWithText>,
    look: DafUnitLook,
): DafTargum? {
    val byBook = targums.groupBy { it.link.targetBookId }
    val chosen =
        byBook.values.maxByOrNull { links -> (if (ONKELOS in links.first().targetBookTitle) targums.size else 0) + links.size }
            ?: return null
    val verses =
        chosen
            .sortedBy { it.link.targetLineIndex }
            .groupBy({
                it.link.sourceLineId
            }) { t -> dafWords(t.targetText, look.diacritics).let { DafSegment(t.link.targetLineId, it.words, it.bold) } }
    return DafTargum(
        chosen
            .first()
            .targetBookTitle
            .substringBefore(" על ")
            .trim(),
        verses,
    )
}

private const val ONKELOS = "אונקלוס"

/**
 * What a unit's daf takes besides the library: the words' [diacritics], the book's [anchors], the commentators' sides,
 * and the book's [style] (verse by verse, its verses' targum too).
 */
internal class DafUnitLook(
    val diacritics: DiacriticsMode,
    val anchors: CommentAnchorIndex,
    /** Each commentator's place among those met on the book's pages, kept from unit to unit: its side. */
    val ranks: MutableMap<Long, Int>,
    val style: DafStyle = DafStyle.Around,
)

private fun Line.asSegment(
    diacritics: DiacriticsMode,
    anchorCommentators: Set<String>,
): Pair<DafSegment, DafWords> {
    val words = dafWords(content, diacritics, anchorCommentators)
    val heading = isHeading(content)
    val bold = if (heading) words.words.indices.toSet() else words.bold
    return DafSegment(id, words.words, bold, heading) to words
}

// The links of [type] from the lines [ids] to the books [books] (to any book when none), with their lines' text, as many
// reads as they take
private suspend fun linksOf(
    repository: SeforimRepository,
    ids: List<Long>,
    books: Set<Long>,
    type: ConnectionType,
): List<CommentaryWithText> {
    if (books.isEmpty() && type == ConnectionType.COMMENTARY) return emptyList()
    val all = ArrayList<CommentaryWithText>()
    while (true) {
        currentCoroutineContext().ensureActive()
        val page =
            repository.getCommentariesForLineRange(
                lineIds = ids,
                activeCommentatorIds = books,
                connectionTypes = setOf(type),
                offset = all.size,
                limit = COMMENTS_PAGE,
            )
        all += page
        if (page.size < COMMENTS_PAGE) return all
    }
}

// Each commentator's comments, each with the first of the unit's lines it is on, in the order they are read
private fun commentaries(
    lines: List<Line>,
    comments: List<CommentaryWithText>,
    names: Map<Long, String>,
    look: DafUnitLook,
): List<DafCommentary> {
    val order = lines.withIndex().associate { (i, line) -> line.id to i }
    val first = HashMap<Long, CommentaryWithText>()
    for (c in comments) {
        val held = first[c.link.targetLineId]
        if (held == null || order.getValue(c.link.sourceLineId) < order.getValue(held.link.sourceLineId)) first[c.link.targetLineId] = c
    }
    return first.values
        .groupBy { it.link.targetBookId }
        .map { (bookId, ofBook) ->
            val onLines =
                ofBook
                    .sortedWith(compareBy({ order.getValue(it.link.sourceLineId) }, { it.link.targetLineIndex }))
                    .groupBy({ it.link.sourceLineId }) { c ->
                        val words = dafWords(c.targetText, look.diacritics)
                        DafSegment(c.link.targetLineId, words.words, words.bold)
                    }
            val rank = look.ranks[bookId] ?: look.ranks.size
            DafCommentary(bookId, names[bookId].orEmpty(), onRight = rank % 2 == 0, comments = onLines)
        }.sortedBy { look.ranks[it.bookId] ?: Int.MAX_VALUE }
}

// The passages each line's anchors begin, and the comments shown that they lead to
private fun passagesOf(
    lines: List<Line>,
    words: Map<Long, DafWords>,
    comments: List<CommentaryWithText>,
    anchors: CommentAnchorIndex,
): Map<Long, List<Pair<IntRange, Set<Long>>>> {
    if (anchors.commentators.isEmpty()) return emptyMap()
    val shown = comments.mapTo(HashSet()) { it.link.targetLineId }
    return lines
        .associate { line ->
            val targets = anchors.of(line.id)
            line.id to
                words[line.id]
                    ?.passages
                    .orEmpty()
                    .mapNotNull { (range, marks) ->
                        val ids = marks.flatMapTo(HashSet()) { targets[it]?.lineIds.orEmpty() }.filterTo(HashSet()) { it in shown }
                        if (ids.isEmpty()) null else range to ids
                    }
        }.filterValues { it.isNotEmpty() }
}

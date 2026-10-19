package io.github.kdroidfilter.seforimapp.android.data

import android.os.Build
import android.util.Log
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlin.math.min

/**
 * Id lists bound into one `IN ?` kept within the bound-parameter limit of the SQLite that runs the statement.
 *
 * SQLite before 3.32 binds at most 999 parameters per statement (SQLITE_MAX_VARIABLE_NUMBER), 3.32 and later 32766, and
 * Android keeps that default: Android 8.0 to 11 have 3.18 to 3.28, Android 12 and later 3.32 or newer. A longer list
 * fails with "too many SQL variables", which the readers' runCatching turned into an empty pane or an empty passage.
 * Every parameter of the statement counts, not only the list: the other IN lists, LIMIT and OFFSET.
 *
 * On Android 12 and later nothing here splits a list: each caller runs its query once, as before. A library split on a
 * FAT32 card, and every library below Android 9, is read by requery's SQLite 3.49; below Android 12 its lists are split
 * too, which costs a few statements more and gives the same rows.
 */
object SqlVariableLimit {
    /** SQLITE_MAX_VARIABLE_NUMBER before SQLite 3.32. */
    private const val LEGACY_MAX_VARIABLES = 999

    /** How many ids one statement may bind beside its [otherParameters], or null when the platform's SQLite needs no split. */
    fun idsPerStatement(otherParameters: Int): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) null else LEGACY_MAX_VARIABLES - otherParameters

    /**
     * [read] over [ids] for a lookup with no ORDER BY and no paging (`SELECT * FROM line WHERE id IN ?`): one statement
     * when the list fits, else the rows of each part one after the other.
     */
    suspend fun <T> readInChunks(ids: Collection<Long>, otherParameters: Int = 0, read: suspend (Collection<Long>) -> List<T>): List<T> {
        val perStatement = idsPerStatement(otherParameters)
        if (perStatement == null || ids.size <= perStatement) return read(ids)
        // IN reads an id once, a repeat in two parts would come back twice. Sorted, the rows come by id, as one statement
        // gives them (SQLite walks an IN list in ascending order)
        return ids.distinct().sorted().chunked(perStatement).flatMap { read(it) }
    }

    /**
     * One page of SeforimRepository.getCommentariesForLineRange for one target book and one connection type (the
     * reader's panes), [read] running it on part of [lineIds] from its first row: below Android 12 a section of a
     * thousand lines and more, which the Targum and Sources panes take whole for a heading, needs more than one
     * statement.
     *
     * The page is the one a single statement gives. Every query takes one book and the lines of one book, so its ORDER
     * BY comes down to the line index of the line shown ([CommentaryWithText.link]'s targetLineIndex):
     *  - forward links: `targetBookOrderIndex` (the book's orderIndex, copied onto each link), then `targetLineIndex`;
     *  - SOURCE: `isDeclaredBase`, which the generator derives from the pair of books (Sefaria's base_text_titles, 0
     *    otherwise; no pair of the desktop library has both), the book's isBaseBook and orderIndex, then the line index.
     *
     * Each part is read to one row past the page's end. Below the smallest last key of the parts that filled their limit,
     * every part's rows are all there, so the merge sorts those, cuts the page out and reads further only when the page
     * reaches past that key (links of one line split between reads). A [distinct] query gives one row per line shown, its
     * MIN() over the links to that line: the rows of one line from several parts become one. Within a key a plain query
     * leaves the order open (several selected lines linking the same line); the merge takes them by link id, as SQLite's
     * plan for a long list gives them (it walks the book's links by id, then sorts), so the pages neither repeat nor skip
     * a link.
     */
    suspend fun linksPage(
        lineIds: List<Long>,
        type: ConnectionType,
        distinct: Boolean,
        offset: Int,
        limit: Int,
        read: suspend (lineIds: List<Long>, offset: Int, limit: Int) -> List<CommentaryWithText>,
    ): List<CommentaryWithText> {
        // The SOURCE queries bind the lines, the book, LIMIT and OFFSET; the forward ones the type's name as well
        val perStatement = idsPerStatement(if (type == ConnectionType.SOURCE) 3 else 4)
        if (perStatement == null || lineIds.size <= perStatement || offset < 0 || limit <= 0) return read(lineIds, offset, limit)
        // IN reads a line once: without repeats the parts' links are apart (a link has one line of the selection)
        val unique = lineIds.distinct()
        if (unique.size <= perStatement) return read(unique, offset, limit)
        val parts = evenParts(unique, perStatement)
        val end = (offset.toLong() + limit).coerceAtMost(Int.MAX_VALUE - 1L).toInt()
        val limits = IntArray(parts.size) { end + 1 }
        val fetched = arrayOfNulls<List<CommentaryWithText>>(parts.size)
        while (true) {
            for (i in parts.indices) {
                if (fetched[i] != null) continue
                val part = read(parts[i], 0, limits[i])
                // Never seen: the ORDER BY terms left out above differ within one call, and the merge is off
                if (part.zipWithNext().any { (a, b) -> a.key > b.key }) Log.w(TAG, "Links of part $i of ${parts.size} not in line order")
                fetched[i] = part
            }
            val rows = fetched.map { it!! }
            // A part that filled its limit may have more rows at its last key, never below it
            val full = rows.indices.filter { rows[it].size >= limits[it] }
            val cutoff = full.minOfOrNull { rows[it].last().key }
            val merged = merge(if (cutoff == null) rows.flatten() else rows.flatMap { part -> part.filter { it.key < cutoff } }, distinct)
            if (cutoff == null || merged.size >= end) return merged.subList(min(offset, merged.size), min(end, merged.size)).toList()
            // The page reaches into the links of a line that some parts only began: read those further
            for (i in full) {
                if (rows[i].last().key != cutoff) continue
                limits[i] = if (limits[i] > Int.MAX_VALUE / 2) Int.MAX_VALUE else limits[i] * 2
                fetched[i] = null
            }
        }
    }

    private val CommentaryWithText.key: Int get() = link.targetLineIndex

    private val LINK_ORDER = compareBy<CommentaryWithText>({ it.key }, { it.link.id })

    private fun merge(rows: List<CommentaryWithText>, distinct: Boolean): List<CommentaryWithText> {
        if (!distinct) return rows.sortedWith(LINK_ORDER)
        // The distinct queries group by the line shown; a line's index is its own, so no two groups share a key
        return rows.groupBy { it.link.targetLineId }.values.map { group -> group.reduce { a, b -> minOfGroup(a, b) } }.sortedBy { it.key }
    }

    /**
     * One line's distinct row from two parts: the numbers are MIN() in the queries, or the same in every row of one call
     * (the book asked for, the reader's book).
     */
    private fun minOfGroup(a: CommentaryWithText, b: CommentaryWithText): CommentaryWithText = a.copy(
        link = a.link.copy(
            id = min(a.link.id, b.link.id),
            sourceBookId = min(a.link.sourceBookId, b.link.sourceBookId),
            targetBookId = min(a.link.targetBookId, b.link.targetBookId),
            sourceLineId = min(a.link.sourceLineId, b.link.sourceLineId),
            targetLineIndex = min(a.link.targetLineIndex, b.link.targetLineIndex),
        ),
    )

    /**
     * [ids] in the fewest parts of at most [perStatement], their sizes a line apart: never a part of one line, which
     * getCommentariesForLineRange would read without DISTINCT.
     */
    private fun evenParts(ids: List<Long>, perStatement: Int): List<List<Long>> {
        val count = (ids.size + perStatement - 1) / perStatement
        val base = ids.size / count
        val extra = ids.size % count
        var from = 0
        return List(count) { i ->
            val size = base + if (i < extra) 1 else 0
            ids.subList(from, from + size).also { from += size }
        }
    }

    private const val TAG = "Zayit"
}

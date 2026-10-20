package io.github.kdroidfilter.seforimapp.android.ui.reader

import io.github.kdroidfilter.seforimapp.android.data.SqlVariableLimit
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * What the reader's panes read their links through (desktop CommentariesUseCase and its paging sources), moved out of
 * ReaderViewModel unchanged: the lines a selection stands for (a heading's section), and one page of their links to one
 * book. Plain Kotlin, so the Android 4.4 edition's reader reads the same lines and pages.
 */
class ReaderLinks(private val repository: SeforimRepository) {
    private val sectionCache = ConcurrentHashMap<Long, List<Long>>()

    /** Up to 128 lines of a section around [lineId] (desktop's sliding window). */
    suspend fun sectionWindow(tocId: Long, lineId: Long): List<Long> {
        val all = sectionCache[tocId] ?: runCatching { repository.getLineIdsForTocEntry(tocId) }.getOrElse { emptyList() }.also { sectionCache[tocId] = it }
        if (all.size <= MAX_SECTION) return all
        val idx = all.indexOf(lineId).coerceAtLeast(0)
        val start = (idx - MAX_SECTION / 2).coerceAtLeast(0)
        return all.subList(start, min(all.size, start + MAX_SECTION))
    }

    /** The lines a link selection stands for: a heading's section (the first 256 for commentaries), else the line. */
    suspend fun sectionOrLine(lineId: Long, windowed: Boolean): List<Long> {
        val heading = runCatching { repository.getHeadingTocEntryByLineId(lineId) }.getOrNull() ?: return listOf(lineId)
        val lines = (sectionCache[heading.id] ?: repository.getLineIdsForTocEntry(heading.id).also { sectionCache[heading.id] = it }).filter { it != lineId }
        if (!windowed || lines.size <= MAX_COMMENT_WINDOW) return lines.ifEmpty { listOf(lineId) }
        return lines.take(MAX_COMMENT_WINDOW)
    }

    /** One page of the links of [ids] to [targetBookId] of [type]; none when the read fails. */
    suspend fun linksPage(ids: List<Long>, type: ConnectionType, targetBookId: Long, offset: Int, limit: Int): List<CommentaryWithText> {
        val distinct = type != ConnectionType.COMMENTARY && ids.size > 1
        return runCatching {
            // A whole section (a heading's targum and sources) can pass the 999 bound parameters of SQLite below 3.32
            SqlVariableLimit.linksPage(ids, type, distinct, offset, limit) { lineIds, o, l ->
                repository.getCommentariesForLineRange(
                    lineIds = lineIds,
                    activeCommentatorIds = setOf(targetBookId),
                    connectionTypes = setOf(type),
                    offset = o,
                    limit = l,
                    distinctByTargetLine = distinct,
                )
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    companion object {
        const val MAX_SECTION = 128
        const val MAX_COMMENT_WINDOW = 256

        /** A line's commentators changed from [previous] to [selected]: the book's sticky set moves the same way (desktop). */
        fun stickyAfter(sticky: Set<Long>, previous: Set<Long>, selected: Set<Long>): Set<Long> = sticky + (selected - previous) - (previous - selected)
    }
}

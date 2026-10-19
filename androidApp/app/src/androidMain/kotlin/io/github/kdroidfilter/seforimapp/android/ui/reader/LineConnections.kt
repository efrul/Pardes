package io.github.kdroidfilter.seforimapp.android.ui.reader

import io.github.kdroidfilter.seforimapp.android.data.CommentatorGrouping
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentarySummary
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/** One commentator of a line, under its desktop name ("רש״י", not "רש״י על בראשית"). */
data class CommentatorItem(val name: String, val bookId: Long)

data class CommentatorGroup(val label: String, val commentators: List<CommentatorItem>)

/** What a line links to: its commentators by group, its targumim and its sources (name → book id, in SQL order). */
data class LineConnectionsSnapshot(
    val commentatorGroups: List<CommentatorGroup> = emptyList(),
    val targumSources: Map<String, Long> = emptyMap(),
    val sources: Map<String, Long> = emptyMap(),
) {
    val hasCommentaries: Boolean get() = commentatorGroups.any { it.commentators.isNotEmpty() }
}

/**
 * The connections of the book's lines, as desktop 0.6.1 CommentariesUseCase computes them (computeLineConnections,
 * resolveBaseLineResolution, filterTargumConnections, buildLineConnectionsSnapshot, buildSourceMap): a heading line
 * stands for up to 128 lines of its TOC section (its targum then only the default one), commentators are grouped and
 * ranked by [CommentatorGrouping] under their sanitized names. Cached per line (LRU of 512).
 */
class LineConnections(private val repository: SeforimRepository, private val currentBookTitle: () -> String) {
    private val bookCache = ConcurrentHashMap<Long, Book>()
    private val defaultTargumCache = ConcurrentHashMap<Long, List<Long>>()
    private val grouping = CommentatorGrouping(repository)
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Long, LineConnectionsSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LineConnectionsSnapshot>?): Boolean = size > MAX_CACHED
    }

    private data class Resolution(val baseLineIds: List<Long>, val headingTocEntryId: Long? = null, val headingBookId: Long? = null)

    fun cached(lineId: Long): LineConnectionsSnapshot? = synchronized(cache) { cache[lineId] }

    suspend fun of(lineId: Long): LineConnectionsSnapshot = load(listOf(lineId))[lineId] ?: LineConnectionsSnapshot()

    /** The snapshots of [lineIds], reading only the missing ones; one load at a time, off the main thread. */
    suspend fun load(lineIds: List<Long>): Map<Long, LineConnectionsSnapshot> {
        if (lineIds.isEmpty()) return emptyMap()
        return mutex.withLock { withContext(Dispatchers.Default) { compute(lineIds) } }
    }

    private suspend fun compute(lineIds: List<Long>): Map<Long, LineConnectionsSnapshot> {
        val cached = LinkedHashMap<Long, LineConnectionsSnapshot>()
        val missing = ArrayList<Long>()
        synchronized(cache) { lineIds.distinct().forEach { id -> cache[id]?.let { cached[id] = it } ?: missing.add(id) } }
        if (missing.isEmpty()) return cached

        fun store(loaded: Map<Long, LineConnectionsSnapshot>): Map<Long, LineConnectionsSnapshot> {
            synchronized(cache) { cache.putAll(loaded) }
            return cached + loaded
        }

        val tocLines = HashMap<Long, List<Long>>()
        val headings = HashMap<Long, TocEntry?>()
        val resolutions = missing.associateWith { resolve(it, tocLines, headings) }
        val allBaseIds = resolutions.values.flatMap { it.baseLineIds }.distinct()
        if (allBaseIds.isEmpty()) return store(missing.associateWith { LineConnectionsSnapshot() })
        val all = allBaseIds.chunked(MAX_IDS_PER_QUERY).flatMap { repository.getCommentarySummariesForLines(it, includeSources = true) }
        if (all.isEmpty()) return store(missing.associateWith { LineConnectionsSnapshot() })
        val bySource = all.groupBy { it.link.sourceLineId }
        val title = currentBookTitle().trim()
        val defaultTargum = resolutions.values.mapNotNull { it.headingBookId }.distinct().associateWith { defaultTargums(it).firstOrNull() }
        return store(
            resolutions.mapValues { (_, r) ->
                val aggregated = r.baseLineIds.flatMap { bySource[it].orEmpty() }
                val targum = r.headingBookId?.let { defaultTargum[it] }
                val filtered = if (r.headingTocEntryId == null || targum == null) {
                    aggregated
                } else {
                    aggregated.filter { it.link.connectionType != ConnectionType.TARGUM || it.link.targetBookId == targum }
                }
                snapshot(filtered, title)
            },
        )
    }

    /** The connections of several lines merged as desktop's multi-line providers do (union, first name wins). */
    suspend fun union(lineIds: List<Long>): LineConnectionsSnapshot {
        val snaps = load(lineIds).let { m -> lineIds.mapNotNull { m[it] } }
        val seen = HashSet<Long>()
        val groups = ArrayList<CommentatorGroup>()
        for (snap in snaps) for (group in snap.commentatorGroups) {
            val fresh = group.commentators.filter { seen.add(it.bookId) }
            if (fresh.isEmpty()) continue
            val i = groups.indexOfFirst { it.label == group.label }
            if (i >= 0) groups[i] = groups[i].copy(commentators = groups[i].commentators + fresh) else groups += group.copy(commentators = fresh)
        }
        fun merge(pick: (LineConnectionsSnapshot) -> Map<String, Long>) = LinkedHashMap<String, Long>().also { m -> snaps.forEach { s -> pick(s).forEach { (k, v) -> m.putIfAbsent(k, v) } } }
        return LineConnectionsSnapshot(groups, merge { it.targumSources }, merge { it.sources })
    }

    /** The base lines a selected line stands for: its section's (max 128 around it) when it heads one. */
    suspend fun baseLineIds(lineId: Long): List<Long> = resolve(lineId, HashMap(), HashMap()).baseLineIds

    private suspend fun resolve(lineId: Long, tocLines: MutableMap<Long, List<Long>>, headings: MutableMap<Long, TocEntry?>): Resolution {
        val heading = headings.getOrPut(lineId) { runCatching { repository.getHeadingTocEntryByLineId(lineId) }.getOrNull() } ?: return Resolution(listOf(lineId))
        val lines = tocLines.getOrPut(heading.id) { repository.getLineIdsForTocEntry(heading.id) }
        val idx = lines.indexOf(lineId)
        val trimmed = if (idx >= 0) {
            val start = max(0, idx - MAX_BASE_LINES / 2)
            lines.subList(start, min(lines.size, start + MAX_BASE_LINES))
        } else {
            lines.take(MAX_BASE_LINES)
        }
        return Resolution(trimmed.filter { it != lineId }, heading.id, heading.bookId)
    }

    private suspend fun defaultTargums(bookId: Long): List<Long> =
        defaultTargumCache[bookId] ?: runCatching { repository.getDefaultTargumIdsForBook(bookId) }.getOrElse { emptyList() }.also { defaultTargumCache[bookId] = it }

    private suspend fun book(id: Long): Book? = bookCache[id] ?: runCatching { repository.getBook(id) }.getOrNull()?.also { bookCache[id] = it }

    private suspend fun snapshot(connections: List<CommentarySummary>, title: String): LineConnectionsSnapshot {
        if (connections.isEmpty()) return LineConnectionsSnapshot()
        val commentaries = connections.filter { it.link.connectionType == ConnectionType.COMMENTARY }
        val groups = if (commentaries.isEmpty()) {
            emptyList()
        } else {
            val names = LinkedHashMap<Long, String>()
            commentaries.forEach { c -> names.getOrPut(c.link.targetBookId) { sanitizeCommentatorName(c.targetBookTitle, title) } }
            val entries = names.map { (id, name) -> Triple(id, name, book(id)) }
            grouping.group(entries.map { it.third to it.second }).map { (label, idx) ->
                CommentatorGroup(label, idx.map { CommentatorItem(entries[it].second, entries[it].first) })
            }.filter { it.commentators.isNotEmpty() }
        }
        return LineConnectionsSnapshot(
            commentatorGroups = groups,
            targumSources = sourceMap(connections.filter { it.link.connectionType == ConnectionType.TARGUM }, title),
            sources = sourceMap(connections.filter { it.link.connectionType == ConnectionType.SOURCE }, title),
        )
    }

    private fun sourceMap(links: List<CommentarySummary>, title: String): Map<String, Long> =
        LinkedHashMap<String, Long>().also { map -> links.forEach { map.putIfAbsent(sanitizeCommentatorName(it.targetBookTitle, title), it.link.targetBookId) } }

    private companion object {
        const val MAX_CACHED = 512
        const val MAX_BASE_LINES = 128

        // Bound parameters per query: SQLite below 3.32 (Android 8.0 to 11 have 3.18 to 3.28) takes at most 999
        const val MAX_IDS_PER_QUERY = 400
    }
}

// Punctuation and spaces the title may be written with between its words ("שיר השירים", "שיר-השירים")
private const val TITLE_GAP = "[\\s,.\\-־–—:;'\"׳״]*"
private val TITLE_SEPARATORS = Regex("[\\s,.\\-־–—:;'\"׳״!?()\\[\\]{}]+")

/**
 * Drops " על <current book>" (or " על ספר <current book>") from a commentator's name: shown beside the book,
 * "רש״י על בראשית" reads "רש״י"; "X על שמות רבה" is about another book and stays (desktop sanitizeCommentatorName).
 */
internal fun sanitizeCommentatorName(raw: String, currentBookTitle: String): String {
    val words = currentBookTitle.trim().split(TITLE_SEPARATORS).filter { it.isNotBlank() }
    if (words.isEmpty()) return raw
    val title = words.joinToString(TITLE_GAP) { Regex.escape(it) }
    val pattern = Regex(" על(?:\\s+ספר)?[\\s:'\"׳״]+$title['\"׳״]?(?=\\s*(?:$|[,.;:!?()\\[\\]{}\\-־–—]))")
    val stripped = raw.replace(pattern, "")
    if (stripped == raw) return raw.trim()
    return stripped.trim().trimEnd(',', ';', ':', '.', '!', '?', '-', '־', '–', '—').trim()
}

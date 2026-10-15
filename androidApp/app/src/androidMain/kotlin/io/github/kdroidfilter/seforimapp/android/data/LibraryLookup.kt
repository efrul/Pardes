package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds books and TOC sections for the siddur fallback and the Shnayim Mikra reader, by title (exact, then loosely: spelling
 * variants and quotes) and by heading. The luach tools open their places with LuachData.resolvePlace, as desktop.
 */
object LibraryLookup {
    private val bookIds = ConcurrentHashMap<String, Long>()

    suspend fun findBook(repo: SeforimRepository, titles: List<String>): Book? = withContext(Dispatchers.IO) {
        for (t in titles) {
            bookIds[t]?.let { id -> runCatching { repo.getBookCore(id) }.getOrNull()?.let { return@withContext it } }
            val exact = runCatching { repo.getBookByTitle(t) }.getOrNull()
            if (exact != null) {
                bookIds[t] = exact.id
                return@withContext exact
            }
        }
        // Spelling variants ("ביכורים" / "בכורים", quotes): compare without matres lectionis and punctuation.
        for (t in titles) {
            val key = loose(t)
            if (key.isEmpty()) continue
            val pattern = "%" + key.replace(" ", "").toList().joinToString("%") + "%"
            val candidates = runCatching { repo.findBooksByTitleLikeCore(pattern, 200) }.getOrDefault(emptyList())
            val hit = candidates.firstOrNull { loose(it.title) == key }
            if (hit != null) {
                bookIds[t] = hit.id
                return@withContext hit
            }
        }
        null
    }

    /** Walks [path] down the TOC: exact text first, then prefix, then substring, each inside the previous match. */
    fun findTocEntry(toc: List<TocEntry>, path: List<String>): TocEntry? {
        if (toc.isEmpty()) return null
        val children = toc.groupBy { it.parentId }
        val order = compareBy<TocEntry>({ it.lineId ?: Long.MAX_VALUE }, { it.id })
        val all = toc.sortedWith(order)
        var current: TocEntry? = null
        for (step in path) {
            val key = HebrewText.normalize(step)
            val pool = current?.let { descendants(it, children).sortedWith(order) } ?: all
            val norm = pool.map { it to HebrewText.normalize(it.text) }
            val hit = norm.firstOrNull { it.second == key }?.first
                ?: norm.firstOrNull { it.second.startsWith(key) }?.first
                ?: norm.firstOrNull { it.second.contains(key) }?.first
                ?: return current
            current = hit
        }
        return current
    }

    private fun descendants(root: TocEntry, children: Map<Long?, List<TocEntry>>): List<TocEntry> {
        val out = ArrayList<TocEntry>()
        val stack = ArrayDeque<TocEntry>()
        children[root.id]?.let { stack.addAll(it) }
        while (stack.isNotEmpty()) {
            val e = stack.removeFirst()
            out += e
            children[e.id]?.let { stack.addAll(it) }
        }
        return out
    }

    private fun loose(s: String): String =
        HebrewText.normalize(s).filterNot { it == 'י' || it == 'ו' || it == ',' || it == '.' || it == '-' }.trim()
}

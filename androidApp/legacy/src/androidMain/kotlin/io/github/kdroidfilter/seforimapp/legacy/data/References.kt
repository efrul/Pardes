package io.github.kdroidfilter.seforimapp.legacy.data

import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceSource
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.TocNode
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * The references' source (the main app's RepositoryReferenceSource, desktop's): books by title or acronym, and their
 * contents, the last books' kept, as a reference is resolved again at each word typed. This edition's SQLite (3.49)
 * binds 32,766 parameters, so a section's lines are read in one query.
 */
class References(private val repository: SeforimRepository) : ReferenceSource {
    private val tocs = Lru<List<TocNode>>()
    private val altTocs = Lru<List<List<TocNode>>>()

    override suspend fun booksNamed(normalizedName: String): List<Book> {
        // A LIKE pattern without wildcards: every book carrying exactly this title, whatever its source
        val byTitle = repository.findBooksByTitleLikeCore(normalizedName, limit = MAX_BOOKS)
        val byAcronym = repository.findBookIdsByAcronym(normalizedName).mapNotNull { repository.getBookCore(it) }
        return (byTitle + byAcronym).distinctBy { it.id }
    }

    override suspend fun titleStartsWith(normalizedPrefix: String): Boolean =
        repository.findBooksByTitleLikeCore("$normalizedPrefix%", limit = 1).isNotEmpty()

    override suspend fun toc(bookId: Long): List<TocNode> =
        tocs.getOrPut(bookId) { repository.getBookToc(bookId).map { TocNode(it.id, it.parentId, it.text, it.lineId) } }

    override suspend fun altTocs(bookId: Long): List<List<TocNode>> =
        altTocs.getOrPut(bookId) {
            repository.getAltTocStructuresForBook(bookId).map { structure ->
                repository.getAltTocEntriesForStructure(structure.id).map { TocNode(it.id, it.parentId, it.text, it.lineId) }
            }
        }

    override suspend fun linesOf(tocEntryId: Long): List<Line> =
        repository.getLinesByIds(repository.getLineIdsForTocEntry(tocEntryId)).sortedBy { it.lineIndex }

    /** The last books' contents, by book. */
    private class Lru<V> {
        private val map = object : LinkedHashMap<Long, V>(MAX_CACHED_BOOKS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, V>?): Boolean = size > MAX_CACHED_BOOKS
        }

        suspend fun getOrPut(key: Long, load: suspend () -> V): V {
            synchronized(map) { map[key] }?.let { return it }
            val value = load()
            synchronized(map) { map[key] = value }
            return value
        }
    }

    private companion object {
        const val MAX_BOOKS = 10
        const val MAX_CACHED_BOOKS = 12
    }
}

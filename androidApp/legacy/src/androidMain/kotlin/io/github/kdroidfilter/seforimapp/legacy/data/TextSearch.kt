package io.github.kdroidfilter.seforimapp.legacy.data

import io.github.kdroidfilter.seforimapp.legacy.db.SqliteException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A line the search found. */
data class Hit(val bookId: Long, val bookTitle: String, val lineId: Long, val lineIndex: Int, val content: String)

/**
 * The text search of this edition without the desktop's Lucene index: when the library has none, or the phone cannot
 * hold or open it (LegacySearch's levels Lookup and Scan), the lines of the books in scope are read one book after the
 * other and tested in C (pardes_sqlite.c), points, cantillation and punctuation aside: pardes_match for the query as a phrase,
 * pardes_match_all for its words anywhere in the line. A book is a few milliseconds; the whole library (7 million lines)
 * takes minutes on an old phone, so the scope is usually a book or a category.
 */
class TextSearch(private val opened: Library.Opened) {
    /**
     * Searches [books] in order for [query] and reports each book's hits with [onHits], and its place with [onBook]
     * (before the book is read). Stops after [limit] hits, or when the calling coroutine is cancelled: the book being
     * read is stopped by [cancel] (sqlite3_interrupt), from any thread.
     */
    suspend fun run(
        query: String,
        books: List<Entry>,
        limit: Int,
        allWords: Boolean,
        onBook: suspend (index: Int, book: Entry) -> Unit,
        onHits: suspend (List<Hit>) -> Unit,
    ): Int {
        var found = 0
        val test = if (allWords) "pardes_match_all" else "pardes_match"
        for ((i, book) in books.withIndex()) {
            currentCoroutineContext().ensureActive()
            onBook(i, book)
            val hits = try {
                opened.query(
                    "SELECT id, lineIndex, content FROM line WHERE bookId = ? AND $test(content, ?) ORDER BY lineIndex LIMIT ?",
                    { bindLong(0, book.id); bindString(1, query); bindLong(2, (limit - found).toLong()) },
                ) { Hit(book.id, book.title, it.getLong(0)!!, it.getLong(1)!!.toInt(), it.getString(2) ?: "") }
            } catch (e: SqliteException) {
                if (e.interrupted) {
                    currentCoroutineContext().ensureActive()
                    continue
                }
                throw e
            }
            if (hits.isNotEmpty()) {
                found += hits.size
                onHits(hits)
            }
            if (found >= limit) break
        }
        return found
    }

    /** Stops the book being read now (the search's Stop). */
    fun cancel() = opened.driver.interrupt()
}

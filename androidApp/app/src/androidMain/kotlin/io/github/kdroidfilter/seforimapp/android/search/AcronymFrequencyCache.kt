// Android variant of the Zayit 0.6.1 file SeforimApp/src/jvmMain/.../framework/search/AcronymFrequencyCache.kt (tag
// v0.6.1): the desktop file reads seforim.db through JDBC (sqlite-jdbc, absent on Android) at the path its
// DatabasePathProvider gives. Same queries and API; it reads the installed library with the platform SQLite, read-only
// (or, for a database split on a FAT32 card, with the app's own SQLite: SplitDatabase).
package io.github.kdroidfilter.seforimapp.framework.search

import co.touchlab.kermit.Logger
import io.github.kdroidfilter.seforimapp.android.data.SplitDatabase
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for acronym frequency data from seforim.db (book_acronym table).
 * Provides fast lookup of how many books use each acronym.
 */
class AcronymFrequencyCache(private val databaseFile: File) {
    private val db: SplitDatabase.LibraryReader by lazy {
        logger.i { "Loading acronym data from seforim.db: $databaseFile" }
        SplitDatabase.openReader(databaseFile)
    }

    // Concurrent maps: suggestions for successive keystrokes may still be running on several threads.
    private val frequencyCache = ConcurrentHashMap<String, Int>()
    private val bookTitlesCache = ConcurrentHashMap<String, List<String>>()
    private val titleToAcronymsCache = ConcurrentHashMap<String, List<String>>()

    init {
        // Pre-load common acronyms into cache
        preloadCache()
    }

    /** Get the frequency (number of books) for an acronym. */
    fun getFrequency(acronym: String): Int = frequencyCache.getOrPut(acronym) { queryFrequency(acronym) }

    /** Get all book titles that use this acronym. */
    fun getBookTitles(acronym: String): List<String> = bookTitlesCache.getOrPut(acronym) { queryBookTitles(acronym) }

    /** Check if an acronym is unique (used by only one book). */
    fun isUnique(acronym: String): Boolean = getFrequency(acronym) == 1

    /** Get all acronyms for a given book title. */
    fun getAcronymsForBook(bookTitle: String): List<String> = titleToAcronymsCache.getOrPut(bookTitle) { queryAcronymsForTitle(bookTitle) }

    private fun queryFrequency(acronym: String): Int =
        db.rawQuery("SELECT COUNT(DISTINCT bookId) AS frequency FROM book_acronym WHERE term = ?", arrayOf(acronym))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun queryBookTitles(acronym: String): List<String> =
        strings("SELECT DISTINCT b.title FROM book_acronym ba JOIN book b ON ba.bookId = b.id WHERE ba.term = ?", acronym)

    private fun queryAcronymsForTitle(bookTitle: String): List<String> =
        strings("SELECT DISTINCT ba.term FROM book b JOIN book_acronym ba ON b.id = ba.bookId WHERE b.title = ?", bookTitle)

    private fun strings(sql: String, arg: String): List<String> =
        db.rawQuery(sql, arrayOf(arg)).use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let(::add) } }

    private fun preloadCache() {
        // Pre-load top 100 most common acronyms
        runCatching {
            db.rawQuery(
                "SELECT term, COUNT(DISTINCT bookId) AS frequency FROM book_acronym GROUP BY term ORDER BY frequency DESC LIMIT 100",
            ).use { c -> while (c.moveToNext()) c.getString(0)?.let { frequencyCache[it] = c.getInt(1) } }
        }.onFailure { logger.w(it) { "Could not pre-load acronyms" } }
        logger.i { "Pre-loaded ${frequencyCache.size} acronyms into cache from seforim.db" }
    }

    fun close() {
        runCatching { db.close() }
    }

    private companion object {
        val logger = Logger.withTag("AcronymFrequencyCache")
    }
}

// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.search

import android.util.Log
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.BookEdition
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

// From SearchEntityFinder.kt (desktop BookDetailsLoader.kt): no Lucene, no kosherkotlin.

/** A part of a book and the line it starts at. */
data class BookPart(val title: String, val lineId: Long)

/** What a search names, shown in a panel beside its results, as Google's knowledge panel. */
sealed interface SearchEntity {
    data class BookEntity(
        val book: Book,
        // Its categories, from the root (הלכה › שולחן ערוך)
        val categories: List<String>,
        // Its authors, with their era and years
        val authors: List<AuthorDetails>,
        // Its main parts (הלכות לשון הרע, הלכות רכילות; פרק א for a tractate), to open directly
        val parts: List<BookPart>,
        // Its editions, with their source and license
        val editions: List<BookEdition>,
        // Whether its text comes from the Otzaria library (every source but Sefaria), to credit it
        val fromOtzaria: Boolean,
    ) : SearchEntity

    data class AuthorEntity(val details: AuthorDetails) : SearchEntity
}

/**
 * A book's details as the book-details pane shows them (categories, authors with their years, main parts, editions
 * with their license), for the search's panel. Desktop BookDetailsLoader.
 */
class BookDetailsLoader(private val repository: SeforimRepository) {
    private val cache = ConcurrentHashMap<Long, SearchEntity.BookEntity>()

    /** A book with its categories, authors and main parts, once per book; null (logged) when it fails. */
    suspend fun load(bookId: Long): SearchEntity.BookEntity? = cache[bookId] ?: try {
        describe(bookId)?.also { cache[bookId] = it }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.w("Zayit", "Book details of $bookId failed to load", t)
        null
    }

    private suspend fun describe(bookId: Long): SearchEntity.BookEntity? {
        val book = repository.getBook(bookId) ?: return null
        val categories = ArrayList<String>()
        var categoryId: Long? = book.categoryId
        while (categoryId != null && categories.size < MAX_DEPTH) {
            val category = repository.getCategory(categoryId) ?: break
            categories.add(0, category.title)
            categoryId = category.parentId
        }
        val authors = book.authors.mapNotNull { repository.getAuthorDetails(it.id) }
        val fromOtzaria = repository.getSourceById(book.sourceId)?.name.let { it != null && it != SEFARIA_SOURCE }
        return SearchEntity.BookEntity(
            book = book,
            categories = categories,
            authors = authors,
            parts = mainParts(book),
            editions = repository.getBookEditions(bookId),
            fromOtzaria = fromOtzaria,
        )
    }

    /**
     * The book's main parts: its top TOC entries below a root that only repeats the title, when few enough to mean
     * something (הלכות לשון הרע…), else the first alternative TOC that has few (a tractate's chapters rather than its
     * folios); none when every list is long (סימן א, ב, ג…).
     */
    private suspend fun mainParts(book: Book): List<BookPart> {
        var level = repository.getBookRootToc(book.id)
        while (level.size == 1 && level[0].hasChildren) level = repository.getTocChildren(level[0].id)
        if (level.size <= MAX_PARTS) return level.mapNotNull { e -> e.lineId?.let { BookPart(e.text, it) } }
        for (structure in repository.getAltTocStructuresForBook(book.id)) {
            var alt = repository.getAltRootToc(structure.id)
            while (alt.size == 1 && alt[0].hasChildren) alt = repository.getAltTocChildren(alt[0].id)
            if (alt.size in 2..MAX_ALT_PARTS) return alt.mapNotNull { e -> e.lineId?.let { BookPart(e.text, it) } }
        }
        return emptyList()
    }

    private companion object {
        const val MAX_DEPTH = 12
        const val MAX_PARTS = 12
        const val MAX_ALT_PARTS = 20
        const val SEFARIA_SOURCE = "Sefaria"
    }
}

/** A license code in plain Hebrew where it has a name, else as Creative Commons writes it (desktop licenseLabel). */
fun licenseLabel(code: String): String = when (code) {
    "Public Domain" -> "נחלת הכלל"
    "unknown" -> "רישיון לא ידוע"
    else -> if (code.startsWith("CC-")) "CC " + code.removePrefix("CC-") else code
}

/** The credit of a book that came through Otzaria (desktop BookDetails), and where it leads. */
const val OTZARIA_CREDIT = "התכנים שלפניכם נאספו, מוינו, סוננו ונערכו בעמל רב ובמסירות על ידי צוות פרויקט 'אוצריא' (otzaria.org)."
const val OTZARIA_URL = "https://otzaria.org"

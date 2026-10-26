package io.github.kdroidfilter.seforimapp.android.search

import android.util.Log
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.BookEdition
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

// Ports of desktop 0.6.10 features/search/domain: SearchKey.kt, SearchEntityFinder.kt and BookDetailsLoader.kt, plus
// features/author/AuthorScreen.kt's AUTHOR_ERAS and authorYears (shared by the knowledge panel and the author page).

private val NIKUD = Regex("[֑-ׇ]")
private val QUOTES = Regex("[\"'״׳]")
private val SPACES = Regex("\\s+")

/** A name as typed, to compare: without nikud, quotes and extra spaces (`שו"ע` = `שוע`). Desktop SearchKey.kt. */
internal fun String.searchKey(): String =
    // The maqaf joins words (שולחן־ערוך): a space, before the nikud range (which holds it) goes
    replace('־', ' ')
        .replace(NIKUD, "")
        .replace(QUOTES, "")
        .replace(SPACES, " ")
        .trim()

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
 * Finds the book or author a query names exactly: a book's title first (חפץ חיים is a book before its author's alias),
 * then an author's name or alias (רמב"ם, חפץ חיים), then a book's whole acronym (שוע). Null when the query names
 * nothing, the usual case for a text search. Desktop SearchEntityFinder.
 */
class SearchEntityFinder(
    private val lookup: LuceneLookupSearchService,
    private val repository: SeforimRepository,
    private val books: BookDetailsLoader,
) {
    suspend fun find(query: String): SearchEntity? {
        val key = query.searchKey()
        if (key.isEmpty()) return null
        val hits = lookup.suggestBooks(query, BOOK_CANDIDATES)
        hits.firstOrNull { it.title.searchKey() == key }?.let { return books.load(it.id) }
        authorNamed(query, key)?.let { return SearchEntity.AuthorEntity(it) }
        return hits.firstOrNull { it.exactAcronym }?.let { books.load(it.id) }
    }

    private suspend fun authorNamed(query: String, key: String): AuthorDetails? {
        for (hit in lookup.suggestAuthors(query, AUTHOR_CANDIDATES)) {
            val details = repository.getAuthorDetails(hit.id) ?: continue
            val names = listOf(details.name, AuthorNames.display(details.name)) + details.aliases
            if (names.any { it.searchKey() == key }) return details
        }
        return null
    }

    private companion object {
        const val BOOK_CANDIDATES = 5
        const val AUTHOR_CANDIDATES = 3
    }
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

/** Sefaria era codes, as named in Hebrew (desktop features/author AUTHOR_ERAS). */
val AUTHOR_ERAS: Map<String, String> =
    mapOf(
        "T" to "תנאים",
        "A" to "אמוראים",
        "GN" to "גאונים",
        "RI" to "ראשונים",
        "AH" to "אחרונים",
        "CO" to "בני זמננו",
    )

private val hebrewYears = HebrewDateFormatter().apply {
    isHebrewFormat = true
    isUseGershGershayim = true
}

private const val HEBREW_YEAR_OFFSET = 3760

/**
 * `ה׳תקכ״א–ה׳תקצ״ז (1761–1837)`, `~1089` for an approximate year; null when nothing is known. Sefaria gives civil years
 * only: the Hebrew one is the year that covers most of it (+3760), so it may be one off. Desktop authorYears.
 */
fun authorYears(details: AuthorDetails): String? {
    fun span(year: (Int) -> String): String? {
        fun one(value: Int?, approx: Boolean) = value?.let { (if (approx) "~" else "") + year(it) }
        val birth = one(details.birthYear, details.birthYearApprox)
        val death = one(details.deathYear, details.deathYearApprox)
        return when {
            birth != null && death != null -> "$birth–$death"
            death != null -> "–$death"
            birth != null -> "$birth–"
            else -> null
        }
    }
    val civil = span { it.toString() } ?: return null
    val hebrew = span { synchronized(hebrewYears) { hebrewYears.formatHebrewNumber(it + HEBREW_YEAR_OFFSET) } }
    return "$hebrew ($civil)"
}

/** The biographies link every person they name to a search: shown as plain text, only books stay links. */
fun plainPersonLinks(markdown: String): String = markdown.replace(Regex("""\[([^\]]+)]\(zayit://search/[^)]*\)"""), "$1")

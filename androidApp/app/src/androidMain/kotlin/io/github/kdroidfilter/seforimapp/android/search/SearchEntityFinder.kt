package io.github.kdroidfilter.seforimapp.android.search

import com.kdroid.gematria.converter.toHebrewNumeral
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

// Ports of desktop 0.6.1 features/search/domain: SearchKey.kt and SearchEntityFinder.kt (BookDetailsLoader.kt is in its
// own file), plus features/author/AuthorScreen.kt's AUTHOR_ERAS and authorYears (shared by the knowledge panel and the
// author page).

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
    val hebrew = span { (it + HEBREW_YEAR_OFFSET).toHebrewNumeral(true) }
    return "$hebrew ($civil)"
}

/** The biographies link every person they name to a search: shown as plain text, only books stay links. */
fun plainPersonLinks(markdown: String): String = markdown.replace(Regex("""\[([^\]]+)]\(zayit://search/[^)]*\)"""), "$1")

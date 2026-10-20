package io.github.kdroidfilter.seforimapp.android.ui.search

import io.github.kdroidfilter.seforimapp.android.search.searchKey
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ResolvedReference
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimlibrary.core.models.Book

// The bar's view logic (moved from UnifiedSearchBar.kt, shared with the Android 4.4 edition).

// Desktop 0.6.1 strings (composeResources/values/strings.xml).
internal const val SEARCH_TEXT_ROW = "חיפוש במאגר: %1\$s"
internal const val SEARCH_TEXT_ROW_IN_BOOK = "חיפוש בתוך %2\$s: %1\$s"
internal const val SEARCH_TEXT_ROW_IN_AUTHOR = "חיפוש בספרי %2\$s: %1\$s"
internal const val SEARCH_IN_BOOK_PLACEHOLDER = "מה לחפש בספר זה? פרק, סימן או כל מילה"
internal const val ROW_ACTION_OPEN = "פתיחה"
internal const val ROW_ACTION_SEARCH_INSIDE = "חיפוש בתוך"
internal const val ROW_ACTION_SEARCH = "חיפוש"
internal const val ROW_ACTION_SEARCH_BASE = "בספרי יסוד"
internal const val ROW_ACTION_SEARCH_ALL = "בכל הספרים"
internal const val AUTOCOMPLETE_NO_RESULTS = "לא נמצאו תוצאות"
internal const val AUTOCOMPLETE_LOADING = "טוען…"
internal const val AUTHOR_BOOKS_COUNT = "%1\$d ספרים"
internal const val SEARCH_CLEAR = "ניקוי"
internal const val SEARCH_ICON_DESCRIPTION = "חיפוש"
internal const val REMOVE_SELECTED_BOOK = "הסר ספר שנבחר"
internal const val BREADCRUMB_SEPARATOR = " > "

internal val REFERENCE_HINTS = listOf(
    "שו\"ע אורח חיים סימן רסח",
    "רמב\"ם הלכות שבת פרק כ",
    "משנה ברורה סימן רלה סעיף קטן ד",
    "ברכות דף ב עמוד א",
    "תהילים פרק כג פסוק ד",
)
internal val TEXT_HINTS = listOf(
    "הכל בידי שמים חוץ מיראת שמים",
    "ואהבת לרעך כמוך",
    "שמע ישראל ה׳ אלקינו ה׳ אחד",
    "מי שטרח בערב שבת יאכל בשבת",
    "אין עוד מלבדו",
)

// The one bar takes references and texts alike: both hints, in turn
internal val HINTS = REFERENCE_HINTS.zip(TEXT_HINTS).flatMap { (reference, text) -> listOf(reference, text) }

/** A row of the book-stage list; see [catBookRows] for their order. */
internal sealed interface CatBookRow {
    data class Jump(val reference: ResolvedReference) : CatBookRow

    data object TextSearch : CatBookRow

    data class Author(val hit: AuthorHit) : CatBookRow

    data class BookRow(val suggestion: BookSuggestionDto) : CatBookRow
}

/**
 * The book-stage rows in order: the references, then the books and authors named exactly as typed (`חולין` opens
 * Chullin on Enter; `שוע` puts the שולחן ערוך parts first), the text search, then the other authors and books.
 * A title being typed past its first word (`בבא ק`) is no text search: with no book named exactly,
 * the first book whose title it begins comes before the text search too (desktop 0.6.2 fix 51463d23).
 */
internal fun catBookRows(
    query: String,
    jumps: List<ResolvedReference>,
    textSearch: Boolean,
    authors: List<AuthorHit>,
    books: List<BookSuggestionDto>,
): List<CatBookRow> {
    val key = query.searchKey()
    val (exactAuthors, otherAuthors) = authors.partition { author ->
        listOfNotNull(author.name, AuthorNames.display(author.name), author.alias).any { it.searchKey() == key }
    }
    val (namedBooks, otherBooks) = books.partition { it.exactAcronym || it.book.title.searchKey() == key }
    val typedTitle =
        otherBooks.firstOrNull()?.takeIf { book ->
            val title =
                book.book.title
                    .searchKey()
                    .replace(",", "")
            namedBooks.isEmpty() && ' ' in key && title.startsWith(key)
        }
    val exactBooks = namedBooks + listOfNotNull(typedTitle)
    return buildList {
        jumps.mapTo(this) { CatBookRow.Jump(it) }
        exactBooks.mapTo(this) { CatBookRow.BookRow(it) }
        exactAuthors.mapTo(this) { CatBookRow.Author(it) }
        if (textSearch) add(CatBookRow.TextSearch)
        otherAuthors.mapTo(this) { CatBookRow.Author(it) }
        otherBooks.filter { it !== typedTitle }.mapTo(this) { CatBookRow.BookRow(it) }
    }
}

/**
 * The TOC suggestions to show (and let the keyboard select), each with its display breadcrumb. Entries whose
 * breadcrumb collapses to nothing once the book prefix is stripped are dropped. Desktop tocSuggestionsForDisplay.
 */
internal fun tocSuggestionsForDisplay(selectedBook: Book?, tocSuggestions: List<TocSuggestionDto>): List<Pair<TocSuggestionDto, List<String>>> =
    tocSuggestions.mapNotNull { ts ->
        val parts = stripBookPrefixFromTocPath(selectedBook, dedupAdjacent(ts.path))
        if (parts.isNotEmpty()) ts to parts else null
    }

/** Strips the selected book's title if it redundantly opens a TOC path. Desktop stripBookPrefixFromTocPath. */
internal fun stripBookPrefixFromTocPath(selectedBook: Book?, parts: List<String>): List<String> {
    if (selectedBook == null || parts.isEmpty()) return parts
    val bookTitle = selectedBook.title.trim()
    val first = parts.first().trim()
    if (first == bookTitle) return parts.drop(1)
    if (first.length > bookTitle.length && first.startsWith(bookTitle)) {
        val ch = first[bookTitle.length]
        if (ch == ',' || ch == ' ' || ch == ':' || ch == '-' || ch == '—') {
            val remainder = first.substring(bookTitle.length + 1).trim().trimStart(',', ' ', ':', '-', '—').trim()
            if (remainder.isNotEmpty()) return listOf(remainder) + parts.drop(1)
        }
    }
    return parts
}

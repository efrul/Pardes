package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph

// Pardes (request 97 of docs/DESKTOP_STAGE3_PLAN.md: "שיכולו לפתוח את עץ הספרים בחיפוש, ברירת מחדל סגור - יפתח לפי
// בחירה. בחירה שם תהיה כמו בטאבים - תסמן בשורת החיפוש ויוכלו לחפש שוב"): a search's tree of the books it found,
// Zayit's own pane (SearchCategoryTreePanel), which its category tabs took the place of. It is closed until the reader
// opens it, by the book tree's own button in the start bar, which a search's results now have (the user's word on the
// testers' build's button beside the search bar: "למה אייקון פתיחת העץ לא במקום הרגיל שלו?"), or by its key; and what
// is checked in it is in the bar too, as a tab picked is.

/** Whether a search's results show their tree of books in a pane: one setting, every tab, off until asked for. */
object SearchTreeSettings : SettingsGroup("search_tree") {
    val shown by boolean(false)
}

/**
 * What the reader checked in a search's tree of books, in the order checked: the categories and the books its bar
 * shows, to search again in. The tabs picked above the results are its categories, and no book ([only]).
 */
internal class SearchTreePicks {
    private val categories = LinkedHashSet<Long>()
    private val books = LinkedHashSet<Long>()

    /** The category [id] checked, or no longer: where the bar searches then ([scope]). */
    fun category(
        id: Long,
        checked: Boolean,
    ): SearchScope? {
        if (checked) categories += id else categories -= id
        return scope()
    }

    /** The book [id] checked, or no longer: where the bar searches then ([scope]). */
    fun book(
        id: Long,
        checked: Boolean,
    ): SearchScope? {
        if (checked) books += id else books -= id
        return scope()
    }

    /** These categories alone are checked: the tabs picked, or none at a new search. */
    fun only(tabs: Collection<Long> = emptyList()) {
        categories.clear()
        categories += tabs
        books.clear()
    }

    /** Where the bar searches with what is checked: a category, a book, several together; null with nothing checked. */
    fun scope(): SearchScope? =
        when {
            categories.isEmpty() && books.isEmpty() -> null
            books.isEmpty() && categories.size == 1 -> SearchScope.Category(categories.single())
            categories.isEmpty() && books.size == 1 -> SearchScope.Book(books.single())
            else -> SearchScope.Multi(bookIds = books.toList(), categoryIds = categories.toList())
        }
}

/**
 * A search tab's tree of books for its bars and its keys: whether it is [shown], and the tab's events ([onEvent]) with
 * the book tree's toggle turned to it.
 */
internal class SearchTreeToggle(
    val shown: Boolean,
    val onEvent: (BookContentEvent) -> Unit,
)

/**
 * The tree of books of a search tab whose events are [onEvent]: the book tree's own button and key (the start bar's
 * "ספרים", Ctrl/Cmd+B) and its pane's close show it and hide it, where a book's tab shows the library's tree.
 */
@Composable
internal fun rememberSearchTreeToggle(onEvent: (BookContentEvent) -> Unit): SearchTreeToggle {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(SearchTreeSettings.shown).collectAsState()
    val current by rememberUpdatedState(onEvent)
    val events =
        remember(appSettings) {
            { event: BookContentEvent ->
                if (event == BookContentEvent.ToggleBookTree) {
                    appSettings[SearchTreeSettings.shown] = !appSettings[SearchTreeSettings.shown]
                } else {
                    current(event)
                }
            }
        }
    return SearchTreeToggle(shown, events)
}

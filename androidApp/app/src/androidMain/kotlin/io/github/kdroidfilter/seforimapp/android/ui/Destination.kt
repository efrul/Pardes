package io.github.kdroidfilter.seforimapp.android.ui

import io.github.kdroidfilter.seforimapp.android.data.SearchScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** What a tab shows: the desktop TabsDestination (Home, BookContent, Search, History, Favorites). */
@Serializable
sealed interface Destination {
    @Serializable
    @SerialName("home")
    data object Home : Destination

    /**
     * [findQuery]: set when a search result is opened, so the reader highlights the searched words (desktop seeds its find bar).
     * [endLineId]: the last line of a passage to mark (the day's limud, an aliya); [shnayimMikra]: open in that mode;
     * [openNotes]: open the notes pane on [lineId] (desktop TabsDestination.BookContent's endLineId, shnayimMikra, openNotes).
     */
    @Serializable
    @SerialName("book")
    data class Book(
        val bookId: Long,
        val lineId: Long? = null,
        val title: String = "",
        val findQuery: String? = null,
        val endLineId: Long? = null,
        val shnayimMikra: Boolean = false,
        val openNotes: Boolean = false,
    ) : Destination

    /**
     * [id] tells searches apart: a tab keeps its search view model, so a new search opened in it must re-run. [scope]
     * is where a fresh tab runs it (a search reopened from history runs where it first ran), [globalExtended] searches
     * all books rather than the base ones (desktop TabsDestination.Search).
     */
    @Serializable
    @SerialName("search")
    data class Search(
        val query: String,
        val id: String = UUID.randomUUID().toString(),
        val scope: SearchScope = SearchScope.Global,
        val globalExtended: Boolean = false,
    ) : Destination

    /** Full visit-history page (desktop TabsDestination.History, the chrome://history equivalent). */
    @Serializable
    @SerialName("history")
    data object History : Destination

    /** Favorites page (desktop TabsDestination.Favorites, the chrome://bookmarks equivalent). */
    @Serializable
    @SerialName("favorites")
    data object Favorites : Destination

    /** An author's page (desktop features/author), opened from search suggestions, the knowledge panel and book details. */
    @Serializable
    @SerialName("author")
    data class Author(val authorId: Long, val name: String = "") : Destination
}

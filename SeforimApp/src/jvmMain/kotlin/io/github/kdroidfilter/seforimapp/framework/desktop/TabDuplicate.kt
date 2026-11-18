package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState

/**
 * Pardes (request 59 of docs/DESKTOP_STAGE3_PLAN.md): the destination of a copy of a tab ([destination], with its saved
 * [state]), which then opens where the tab is, from the state copied with it: a book tab at the book read last (the
 * destination keeps the one it was opened with) with no line of its own, which would win over the state's place; a
 * search tab with no scope of its own, which would replace the state's.
 */
internal fun duplicateDestination(
    destination: TabsDestination,
    state: TabPersistedState,
): TabsDestination =
    when (destination) {
        is TabsDestination.BookContent ->
            destination.copy(
                bookId = state.bookContent.selectedBookId.takeIf { it > 0 } ?: destination.bookId,
                lineId = null,
                endLineId = null,
                openNotes = false,
                shnayimMikra = false,
            )
        is TabsDestination.Search -> destination.copy(scope = SearchScope.Global, globalExtended = false)
        else -> destination
    }

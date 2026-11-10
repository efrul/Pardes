package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.SearchSession

/**
 * A search session whose results can be shown in part with no new search: the lines of some books, in the order of its
 * list. The results' category tabs filter so the list a search by meaning made, which another search in the category
 * would not give again.
 */
interface SessionViews {
    /**
     * This session's results in [bookIds] alone, or all of them for null, from the first, in their order. Null when
     * its list is not all it finds (it is not loaded yet, or goes on past what it holds): the category is then searched.
     */
    suspend fun view(bookIds: Set<Long>?): SearchSession?
}

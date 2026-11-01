package io.github.kdroidfilter.seforimapp.android.data

/**
 * Pardes desktop's CommentatorSelection (SeforimApp …/features/bookcontent/state/CommentatorSelection.kt), as it is:
 * the commentators a book's lines show, remembered for the book (the commentators list's checkboxes).
 *
 * A list ([remembered]): a line shows those of them it has, so a commentator it is the first to have is not shown.
 * Or, once "כל המפרשים" is checked, all of them ([all]): every line shows all of its commentators, the ones that
 * appear only further on included, but those unchecked since ([excluded]).
 *
 * Desktop's ContentState extensions are not here: the reader keeps one selection, its book's (ReaderUiState), saved
 * by AppPrefs.
 */
data class CommentatorSelection(
    val remembered: Set<Long> = emptySet(),
    val all: Boolean = false,
    val excluded: Set<Long> = emptySet(),
) {
    /** The commentators shown on a line whose list is [available]. */
    fun shownOf(available: Collection<Long>): Set<Long> =
        if (all) {
            available.filterTo(LinkedHashSet()) { it !in excluded }
        } else {
            available.filterTo(LinkedHashSet()) { it in remembered }
        }

    /**
     * A line's list showed [previous] checked and now has [selected]: what was checked or unchecked there is for the
     * book's other lines too. The caller bounds [previous] to the commentators that list showed, so that one it did
     * not show is never taken for unchecked.
     */
    fun changed(
        previous: Set<Long>,
        selected: Set<Long>,
    ): CommentatorSelection {
        val checked = selected - previous
        val unchecked = previous - selected
        if (checked.isEmpty() && unchecked.isEmpty()) return this
        return copy(
            remembered = remembered + checked - unchecked,
            excluded = if (all) excluded + unchecked - checked else excluded,
        )
    }

    /** "כל המפרשים" checked on a line whose list is [available]: from now on every line shows all of its commentators. */
    fun allChecked(available: Collection<Long>): CommentatorSelection =
        CommentatorSelection(remembered = remembered + available, all = true)

    /**
     * "כל המפרשים" unchecked on a line whose list is [available]. After all of them were shown, no line shows any. A
     * line whose own were all checked by chance (the list mode) has only its own unchecked, as unchecking them one by
     * one does.
     */
    fun allUnchecked(available: Collection<Long>): CommentatorSelection =
        if (all) CommentatorSelection() else copy(remembered = remembered - available.toSet())
}

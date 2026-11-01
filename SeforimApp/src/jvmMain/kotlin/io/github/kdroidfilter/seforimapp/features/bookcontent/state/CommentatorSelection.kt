package io.github.kdroidfilter.seforimapp.features.bookcontent.state

/**
 * The commentators a book's lines show, remembered for the book (the commentaries pane's checkboxes).
 *
 * A list ([remembered]): a line shows those of them it has, so a commentator it is the first to have is not shown.
 * Or, once "כל המפרשים" is checked, all of them ([all]): every line shows all of its commentators, the ones that
 * appear only further on included, but those unchecked since ([excluded]).
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

/** The commentator selection of [bookId] in this tab. */
fun ContentState.commentatorSelection(bookId: Long): CommentatorSelection =
    CommentatorSelection(
        remembered = selectedCommentatorsByBook[bookId].orEmpty(),
        all = bookId in allCommentatorsBooks,
        excluded = excludedCommentatorsByBook[bookId].orEmpty(),
    )

/** This tab's content with [selection] as the commentator selection of [bookId]. */
fun ContentState.withCommentatorSelection(
    bookId: Long,
    selection: CommentatorSelection,
): ContentState =
    copy(
        selectedCommentatorsByBook = selectedCommentatorsByBook.withEntry(bookId, selection.remembered),
        allCommentatorsBooks = if (selection.all) allCommentatorsBooks + bookId else allCommentatorsBooks - bookId,
        excludedCommentatorsByBook = excludedCommentatorsByBook.withEntry(bookId, selection.excluded),
    )

private fun Map<Long, Set<Long>>.withEntry(
    key: Long,
    value: Set<Long>,
): Map<Long, Set<Long>> = if (value.isEmpty()) this - key else this + (key to value)

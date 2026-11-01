package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import androidx.compose.runtime.Immutable

/**
 * The commentators a book's lines show, remembered for the book (the commentaries pane's checkboxes).
 *
 * Commentators checked one by one ([remembered]): a line shows those of them it has, so a commentator it is the first
 * to have is not shown. A group's checkbox, as "ראשונים על התנ״ך", checks the whole group ([groups]): every line shows
 * all of that group's commentators, those that appear only further on included. "כל המפרשים" checks all of them
 * ([all]): every line shows all of its commentators, but the groups unchecked since ([hiddenGroups]). A commentator
 * unchecked while its group or all of them are checked stays hidden on the other lines ([excluded]).
 *
 * A line's list is given as its commentators with their group (the label of their heading in the list, which depends
 * on the commentator alone), as [listedCommentators] makes it.
 */
@Immutable
data class CommentatorSelection(
    val remembered: Set<Long> = emptySet(),
    val all: Boolean = false,
    val groups: Set<String> = emptySet(),
    val hiddenGroups: Set<String> = emptySet(),
    val excluded: Set<Long> = emptySet(),
) {
    /** Whether whole groups are checked, so that every line shows commentators no line before had. */
    val showsGroups: Boolean get() = all || groups.isNotEmpty()

    /** Nothing checked: no line shows any commentator. */
    val isEmpty: Boolean get() = !showsGroups && remembered.isEmpty()

    /** Whether a line shows all of [group]'s commentators; a commentator of no known group only with all of them. */
    fun groupShown(group: String?): Boolean = if (group == null) all else group in groups || (all && group !in hiddenGroups)

    /** The commentators shown on a line whose list is [listed]. */
    fun shownOf(listed: Map<Long, String>): Set<Long> =
        listed.entries
            .filter { (id, group) -> id !in excluded && (id in remembered || groupShown(group)) }
            .mapTo(LinkedHashSet()) { it.key }

    /**
     * A line's list ([listed]) showed [previous] checked and now has [selected]: what was checked or unchecked there
     * is for the book's other lines too. The caller bounds [previous] to the commentators that list showed, so that one
     * it did not show is never taken for unchecked.
     */
    fun changed(
        previous: Set<Long>,
        selected: Set<Long>,
        listed: Map<Long, String>,
    ): CommentatorSelection {
        val checked = selected - previous
        val unchecked = previous - selected
        if (checked.isEmpty() && unchecked.isEmpty()) return this
        return copy(
            remembered = remembered + checked - unchecked,
            // Unchecked while its group, or all of them, are checked: hidden on the other lines too
            excluded = excluded - checked + unchecked.filter { groupShown(listed[it]) },
        )
    }

    /** "כל המפרשים" checked: from now on every line shows all of its commentators. */
    fun allChecked(): CommentatorSelection = CommentatorSelection(remembered = remembered, all = true)

    /** "כל המפרשים" unchecked: no line shows any commentator. */
    fun allUnchecked(): CommentatorSelection = CommentatorSelection()

    /**
     * [group]'s checkbox checked: from now on every line shows all of its commentators. [members] are those of it the
     * caller knows (the line's, and those of other lines remembered or unchecked), which are shown again.
     */
    fun groupChecked(
        group: String,
        members: Collection<Long>,
    ): CommentatorSelection = copy(groups = groups + group, hiddenGroups = hiddenGroups - group, excluded = excluded - members.toSet())

    /** [group]'s checkbox unchecked: no line shows its commentators ([members], as for [groupChecked]). */
    fun groupUnchecked(
        group: String,
        members: Collection<Long>,
    ): CommentatorSelection {
        val ids = members.toSet()
        return copy(
            remembered = remembered - ids,
            groups = groups - group,
            hiddenGroups = if (all) hiddenGroups + group else hiddenGroups,
            excluded = excluded - ids,
        )
    }
}

/** A line's commentators list ([CommentatorGroup]s), as [CommentatorSelection] reads it: each commentator's group. */
fun List<CommentatorGroup>.listedCommentators(): Map<Long, String> =
    LinkedHashMap<Long, String>().also { listed ->
        forEach { group -> group.commentators.forEach { listed.putIfAbsent(it.bookId, group.label) } }
    }

/** The commentator selection of [bookId] in this tab. */
fun ContentState.commentatorSelection(bookId: Long): CommentatorSelection =
    CommentatorSelection(
        remembered = selectedCommentatorsByBook[bookId].orEmpty(),
        all = bookId in allCommentatorsBooks,
        groups = commentatorGroupsByBook[bookId].orEmpty(),
        hiddenGroups = hiddenCommentatorGroupsByBook[bookId].orEmpty(),
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
        commentatorGroupsByBook = commentatorGroupsByBook.withEntry(bookId, selection.groups),
        hiddenCommentatorGroupsByBook = hiddenCommentatorGroupsByBook.withEntry(bookId, selection.hiddenGroups),
        excludedCommentatorsByBook = excludedCommentatorsByBook.withEntry(bookId, selection.excluded),
    )

/** [selectedCommentatorsByLine] with [lineId]'s selection [ids], none dropping it. */
fun Map<Long, Set<Long>>.withLine(
    lineId: Long,
    ids: Set<Long>,
): Map<Long, Set<Long>> = withEntry(lineId, ids)

private fun <T> Map<Long, Set<T>>.withEntry(
    key: Long,
    value: Set<T>,
): Map<Long, Set<T>> = if (value.isEmpty()) this - key else this + (key to value)

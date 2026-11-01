package io.github.kdroidfilter.seforimapp.android.data

import androidx.compose.runtime.Immutable

/**
 * Pardes desktop's CommentatorSelection (SeforimApp …/features/bookcontent/state/CommentatorSelection.kt), as it is:
 * the commentators a book's lines show, remembered for the book (the commentators list's checkboxes).
 *
 * Commentators checked one by one ([remembered]): a line shows those of them it has, so a commentator it is the first
 * to have is not shown. A group's checkbox, as "ראשונים על התנ״ך", checks the whole group ([groups]): every line shows
 * all of that group's commentators, those that appear only further on included. "כל המפרשים" checks all of them
 * ([all]): every line shows all of its commentators, but the groups unchecked since ([hiddenGroups]). A commentator
 * unchecked while its group or all of them are checked stays hidden on the other lines ([excluded]).
 *
 * A line's list is given as its commentators with their group (the label of their heading in the list, which depends
 * on the commentator alone): ui/reader `listedCommentators`. Desktop's ContentState extensions are not here: the
 * reader keeps one selection, its book's (ReaderUiState), saved by AppPrefs.
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

package io.github.kdroidfilter.seforimapp.features.bookcontent.state

/**
 * The commentators a tab shows in panes of their own, out of the commentaries pane, in the order they were split
 * off. Each has its own pane (ReaderPane.Commentator, a satellite keyed by its name), so it stays where it was put
 * while others come and go. Commentators are kept by name (as the commentaries pane lists them), so Rashi stays in
 * his pane from Bereshit to Shemot, where he is another book.
 *
 * Desktop 1.0.5 kept the same list by slot, an empty name standing for a free one: such a list reads here as its
 * names, and the plain list of names written here reads there as the first slots.
 */
object DetachedCommentators {
    /** How many commentators a tab can show in panes of their own: more than any screen holds side by side. */
    const val MAX = 8

    /** The names of [saved], in order, without free slots (1.0.5's empty names) or repeats. */
    fun names(saved: List<String>): List<String> = saved.filter { it.isNotBlank() }.distinct()

    /** [saved] with [name] at its end; unchanged when it is already there or [MAX] are. */
    fun detach(
        saved: List<String>,
        name: String,
    ): List<String> {
        val names = names(saved)
        if (name.isBlank() || name in names || names.size >= MAX) return names
        return names + name
    }

    /** [saved] without [name]: its pane is closed and it is back in the commentaries pane. */
    fun reattach(
        saved: List<String>,
        name: String,
    ): List<String> = names(saved) - name

    /** Whether another commentator can be given a pane of its own. */
    fun canDetach(saved: List<String>): Boolean = names(saved).size < MAX
}

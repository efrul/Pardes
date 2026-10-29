package io.github.kdroidfilter.seforimapp.features.bookcontent.state

/**
 * The commentators a tab shows in panes of their own, out of the commentaries pane: each in its slot, so it
 * stays in the pane (and the place) it was given while others come and go. An empty name is a free slot; the
 * list ends with a used one. Commentators are kept by name (as the commentaries pane lists them), so Rashi
 * stays in his pane from Bereshit to Shemot, where he is another book.
 */
object DetachedCommentators {
    /** The panes there are for split-off commentators in each window ([ReaderPane]'s commentator slots). */
    const val MAX = 4

    /** [slots] with [name] in the first free slot; unchanged when it already has one or none is free. */
    fun detach(
        slots: List<String>,
        name: String,
    ): List<String> {
        if (name.isBlank() || name in slots) return slots
        val free = slots.indexOf("")
        return when {
            free >= 0 -> slots.toMutableList().apply { this[free] = name }
            slots.size < MAX -> slots + name
            else -> slots
        }
    }

    /** [slots] without [name], its slot freed. */
    fun reattach(
        slots: List<String>,
        name: String,
    ): List<String> = slots.map { if (it == name) "" else it }.trimmed()

    /** [slots] with [slot] freed, as when its pane is closed from the dock. */
    fun clear(
        slots: List<String>,
        slot: Int,
    ): List<String> = slots.mapIndexed { i, name -> if (i == slot) "" else name }.trimmed()

    /** Whether another commentator can be given a pane of its own. */
    fun canDetach(slots: List<String>): Boolean = slots.count { it.isNotEmpty() } < MAX

    private fun List<String>.trimmed(): List<String> = dropLastWhile { it.isEmpty() }
}

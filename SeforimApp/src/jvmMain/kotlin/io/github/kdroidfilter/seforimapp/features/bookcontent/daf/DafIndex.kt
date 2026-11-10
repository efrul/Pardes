package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

/**
 * A tractate's printed lines of the library, the Gemara's and the commentaries': where each is printed first
 * ([places]), which of the Gemara's is read at a place ([gemaraAt]), and each commentary column's comments in the
 * order they are read ([next]), which the arrows step through when a comment is selected. [order] is the amudim as the
 * daf lists them.
 */
internal class DafIndex(
    file: DafLayoutFile,
    order: List<String>,
) {
    /**
     * Where each line of the library is printed first: its amud's place in the order, and the top of its first printed
     * line (points, from the amud's block's top).
     */
    val places: Map<Long, Pair<Int, Float>>

    // Each commentary column's comments as read, and each comment's column and place there
    private val columns = HashMap<String, MutableList<Long>>()
    private val placeIn = HashMap<Long, Pair<String, Int>>()

    // Each amud's printed lines of the Gemara, from its top down: the top of each and the segment read at it, the first
    // that begins on the line, or the one that goes on through it when none does
    private val gemara = HashMap<Int, List<Pair<Float, Long>>>()

    init {
        val found = HashMap<Long, Pair<Int, Float>>()
        val at = order.withIndex().associate { (i, amud) -> amud to i }
        for (amud in file.amudim.sortedBy { at[it.amud] ?: Int.MAX_VALUE }) {
            val index = at[amud.amud] ?: continue
            for (line in amud.lines) {
                for (id in line.lineIds) found.putIfAbsent(id, index to line.b[1] - amud.block[1])
            }
            gemara[index] =
                amud.lines
                    .filter { it.isGemara && it.lineIds.isNotEmpty() }
                    .map { line ->
                        val top = line.b[1] - amud.block[1]
                        top to (line.lineIds.firstOrNull { found[it] == index to top } ?: line.lineIds.first())
                    }.sortedBy { it.first }
            for ((column, lines) in amud.lines.filterNot { it.isGemara }.groupBy { it.c }) {
                val read = columns.getOrPut(column) { ArrayList() }
                // A comment that goes on from the amud before is read there
                for (id in asRead(lines.flatMap { it.lineIds }.distinct().filterNot(placeIn::containsKey))) {
                    placeIn[id] = column to read.size
                    read += id
                }
            }
        }
        places = found
    }

    /**
     * The Gemara's segment read at the top of a view scrolled [top] points down the amud at [amud] in the order (from
     * its block's top): that of the first line printed there or below, on a later amud when that one has none below. A
     * view scrolled to a segment's own place ([places]) reads that segment again, or one that begins on the same line.
     */
    fun gemaraAt(
        amud: Int,
        top: Float,
    ): Long? {
        gemara[amud]?.firstOrNull { it.first >= top }?.let { return it.second }
        val below = gemara.entries.filter { it.key > amud && it.value.isNotEmpty() }.minByOrNull { it.key }
        return below?.value?.first()?.second
    }

    /** The top of the Gemara's first printed line of the amud at [amud] in the order (points, from its block's top). */
    fun gemaraTop(amud: Int): Float? = gemara[amud]?.firstOrNull()?.first

    /**
     * The comment read after [commentId] in its column, or before it ([forward] false); null at the column's end, and
     * for a line that is no comment of the tractate's pages.
     */
    fun next(
        commentId: Long,
        forward: Boolean,
    ): Long? {
        val (column, place) = placeIn[commentId] ?: return null
        return columns.getValue(column).getOrNull(if (forward) place + 1 else place - 1)
    }

    private companion object {
        /** A book's lines are numbered one after the other: two comments of an amud this far apart are two books'. */
        const val BOOK_GAP = 1000L

        /**
         * [printed] (an amud's comments of one column, as its lines list them) as they are read: each book's in the
         * library's order, which the lines' order follows but for a comment the alignment put a line early or late (a
         * third of the amudim have one), the books in the order they are printed (Bava Batra 29a: Rashi's last
         * comments, then the Rashbam's first, whose lines the library numbers lower).
         */
        fun asRead(printed: List<Long>): List<Long> {
            val books = ArrayList<MutableList<Long>>()
            for (id in printed.sorted()) {
                if (books.isEmpty() || id - books.last().last() > BOOK_GAP) books += mutableListOf(id) else books.last() += id
            }
            val place = printed.withIndex().associate { (i, id) -> id to i }
            return books.sortedBy { book -> book.minOf(place::getValue) }.flatten()
        }
    }
}

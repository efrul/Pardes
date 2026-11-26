package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

// Pardes (request 98 of docs/DESKTOP_STAGE3_PLAN.md: "הפירוש לא היה מותאם לספר עצמו, כל אחד נהיה בעמוד אחר לגמרי", the
// Maharal's Be'er HaGolah with Rabbi Hartman's notes, five times its text): the text of a unit in the pieces a page of
// bands takes whole. A line of the library is one piece, as the rules' first has a verse ("פסוק אינו נחתך בין
// עמודים"); a paragraph a commentary is anchored inside (a note's mark among its words), or one of many printed lines,
// is its printed lines, so that a page ends where the comments that fit it end, as a footnoted book's does.

/** A paragraph of no more printed lines than these stays whole, unless a commentary is anchored inside it. */
private const val WHOLE_ROWS = 8

/**
 * A piece of the text: a whole line of the library, or one printed line of a paragraph that may be cut. Its line
 * ([segment], its place in the unit; [lineId]), the index among the line's words of its first ([from]), and whether it
 * [opens] the line and [closes] it.
 */
internal class TextPiece(
    val segment: Int,
    val lineId: Long,
    val from: Int,
    val words: List<SetWord>,
    val opens: Boolean,
    val closes: Boolean,
    val isHeading: Boolean,
)

/**
 * The pieces of a unit's [text]. [rows] breaks a paragraph's words into its printed lines, null where no paragraph is
 * cut; [anchors] has each anchored comment's place in its line (the first of the words its anchor puts it on), and
 * [anchored] the lines such a comment is on.
 */
internal class TextPieces(
    text: List<DafSegment>,
    private val anchors: Map<Long, Int>,
    anchored: Set<Long>,
    rows: ((List<SetWord>) -> List<IntRange>)?,
) {
    val list: List<TextPiece>
    private val firstOf = HashMap<Long, Int>()
    private val lastOf = HashMap<Long, Int>()

    init {
        val pieces = ArrayList<TextPiece>()
        text.forEachIndexed { i, segment ->
            val words = setWords(segment)
            firstOf.putIfAbsent(segment.lineId, pieces.size)
            val lines = rows?.takeUnless { segment.isHeading || words.isEmpty() }?.invoke(words).orEmpty()
            if (lines.size < 2 || (lines.size <= WHOLE_ROWS && segment.lineId !in anchored)) {
                pieces += TextPiece(i, segment.lineId, 0, words, opens = true, closes = true, segment.isHeading)
            } else {
                lines.forEachIndexed { row, range ->
                    val part = words.subList(range.first, range.last + 1)
                    pieces += TextPiece(i, segment.lineId, range.first, part, row == 0, row == lines.lastIndex, isHeading = false)
                }
            }
            lastOf[segment.lineId] = pieces.lastIndex
        }
        list = pieces
    }

    /**
     * The piece the comment [commentId] on the line [lineId] is set with, no page before which has it: its anchor's;
     * without one the line's last ([atEnd]: a band's comment is read after its paragraph), or its first (what stands
     * beside the text begins with it).
     */
    fun of(
        lineId: Long,
        commentId: Long,
        atEnd: Boolean,
    ): Int {
        val first = firstOf[lineId] ?: return 0
        val last = lastOf.getValue(lineId)
        val word = anchors[commentId] ?: return if (atEnd) last else first
        var piece = first
        while (piece < last && list[piece + 1].from <= word) piece++
        return piece
    }
}

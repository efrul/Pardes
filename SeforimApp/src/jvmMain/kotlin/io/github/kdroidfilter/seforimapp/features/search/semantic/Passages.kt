package io.github.kdroidfilter.seforimapp.features.search.semantic

/**
 * A found line cut into the passages a search by meaning highlights one of ([IndependentSemanticSearch.bestPassages]):
 * its sentences and clauses, each a verbatim part of the line, so that the views find it again in the text
 * (SearchEngine.highlights). Cut after . : ; ? ! and sof pasuq, then after the commas of a long one, then by words when
 * still long; a short piece is joined to the next one.
 */
internal object Passages {
    /** Fewer words than this make no passage of their own: joined to the next one (or to the last one, at the end). */
    private const val MIN_WORDS = 4

    /** More words than this are cut further: at commas, then into even pieces of about [MAX_WORDS] words at most. */
    private const val MAX_WORDS = 30

    /** The name of an HTML entity, `#` and digits included, is no longer than this. */
    private const val MAX_ENTITY = 10

    private val SENTENCE_ENDS = setOf('.', ':', ';', '?', '!', '׃')
    private val CLAUSE_ENDS = setOf(',', '،')

    /** The passages of [text], as ranges of it in order, without the spaces around them; one when it is one clause. */
    fun split(text: String): List<IntRange> {
        val sentences = cutAfter(text, 0 until text.length, SENTENCE_ENDS)
        val pieces =
            sentences.flatMap { sentence ->
                if (words(text, sentence) <= MAX_WORDS) {
                    listOf(sentence)
                } else {
                    cutAfter(text, sentence, CLAUSE_ENDS).flatMap { clause ->
                        if (words(text, clause) <= MAX_WORDS) listOf(clause) else byWords(text, clause)
                    }
                }
            }
        return joinShort(text, pieces).mapNotNull { trim(text, it) }
    }

    // [range] of [text] cut after each of [ends], not after the ';' of an HTML entity: a results snippet's text is
    // escaped (&amp; &nbsp; &lt;, SnippetSources.clean)
    private fun cutAfter(
        text: String,
        range: IntRange,
        ends: Set<Char>,
    ): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = range.first
        for (i in range) {
            if (text[i] in ends && !closesEntity(text, i)) {
                out += start..i
                start = i + 1
            }
        }
        if (start <= range.last) out += start..range.last
        return out
    }

    private fun closesEntity(
        text: String,
        i: Int,
    ): Boolean {
        if (text[i] != ';') return false
        var j = i - 1
        while (j >= 0 && i - j <= MAX_ENTITY && isEntityName(text[j])) j--
        return j in 0 until i - 1 && text[j] == '&'
    }

    private fun isEntityName(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '#'

    // [range] of [text] cut at the spaces between its words into even pieces of MAX_WORDS words at most, but for fewer
    // than MIN_WORDS left over, which stay with the rest: 34 words into two of 17 (not 30, ending mid-phrase, and 4),
    // 33 words whole
    private fun byWords(
        text: String,
        range: IntRange,
    ): List<IntRange> {
        val starts = range.filter { i -> !text[i].isWhitespace() && (i == range.first || text[i - 1].isWhitespace()) }
        val pieces = (starts.size - MIN_WORDS + MAX_WORDS) / MAX_WORDS
        if (pieces <= 1) return listOf(range)
        return (0 until pieces).map { p ->
            val from = if (p == 0) range.first else starts[p * starts.size / pieces]
            val to = if (p == pieces - 1) range.last else starts[(p + 1) * starts.size / pieces] - 1
            from..to
        }
    }

    private fun joinShort(
        text: String,
        pieces: List<IntRange>,
    ): List<IntRange> {
        val out = ArrayList<IntRange>()
        var pending: IntRange? = null
        for (piece in pieces) {
            val current = pending?.let { it.first..piece.last } ?: piece
            if (words(text, current) < MIN_WORDS) {
                pending = current
            } else {
                out += current
                pending = null
            }
        }
        if (pending != null) {
            if (out.isEmpty()) out += pending else out[out.lastIndex] = out.last().first..pending.last
        }
        return out
    }

    private fun trim(
        text: String,
        range: IntRange,
    ): IntRange? {
        var first = range.first
        var last = range.last
        while (first <= last && text[first].isWhitespace()) first++
        while (last >= first && text[last].isWhitespace()) last--
        return if (first <= last) first..last else null
    }

    private fun words(
        text: String,
        range: IntRange,
    ): Int {
        var count = 0
        var inWord = false
        for (i in range) {
            val word = text[i].isLetterOrDigit()
            if (word && !inWord) count++
            inWord = word || (inWord && !text[i].isWhitespace())
        }
        return count
    }
}

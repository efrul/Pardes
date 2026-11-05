package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

// Where a commentary's dibbur hamatchil is in the Gemara: the library ties each comment to its Gemara segment, a
// whole one, and this finds, among the segment's printed words, the ones the comment's bold words quote. Plain
// comparison of words, done when a page is shown: nothing is stored for it.

/** A word as compared: its Hebrew letters only, final forms as the others (`מצותן`, `מצותן:` and `מצותנ` alike). */
internal fun dafKey(word: String): String =
    buildString(word.length) {
        for (c in word) {
            if (c in 'א'..'ת') append(FINALS[c] ?: c)
        }
    }

private val FINALS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

/** The letters a word takes before it (`ו`אברים, `ה`נאכלים, `ש`יעלה): a lemma may quote it with them or without. */
private const val PREFIXES = "והשבכלמד"

/** A dibbur's words that stand for words left out (`מאימתי וכו'`): what comes after them may be further on. */
private val ELLIPSES = setOf("וכו", "כו", "וגו", "וגומר")

/** The print's marks of the Mishnah and the Gemara, which a dibbur may start with (`גמ' גודא`), the segment not. */
private val MARKERS = setOf("גמ", "גמרא", "מתני", "מתניתין")

/** An abbreviation as printed: its marks (א״כ, כר׳, ק"ש). */
internal val ABBREVIATION_MARKS = setOf('״', '"', '׳', '\'')

private const val PREFIXED = 0.9f
private const val SPELLED_OTHERWISE = 0.85f
private const val ONE_LETTER = 0.75f

/** The letters a word is spelled with or without (כתיב מלא and חסר): קפיד and קפדי, מצותן and מצוותן. */
private const val PLENE = "וי"

/** A word of the lemma the print has not, or the other way, or another word in its place: what each costs. */
private const val SKIP = -0.5f
private const val OTHER_WORD = -0.3f

/** An abbreviation printed for two or three of the lemma's words (א״כ for אם כן), each of them as a word alike. */
private const val ABBREVIATED = 0.9f
private const val MAX_ABBREVIATED = 3

/** A lone word must be the same word; a phrase, most of it (its score to its number of words). */
private const val ONE_WORD_SCORE = 0.9f
private const val PHRASE_SCORE = 0.7f

/** How alike a lemma's word and a word of the Gemara are, from 0 to 1 (both [dafKey]s). */
internal fun alike(
    lemma: String,
    text: String,
): Float {
    if (lemma == text) return 1f
    val (longer, shorter) = if (text.length > lemma.length) text to lemma else lemma to text
    // One or two prefix letters more on one side
    val extra = longer.length - shorter.length
    if (shorter.length >= 2 && extra in 1..2 && longer.endsWith(shorter) && longer.take(extra).all { it in PREFIXES }) {
        return PREFIXED
    }
    // Spelled with a yod or a vav more, less or elsewhere (תרתי לא קפדי in the dibbur, קפיד in the Gemara)
    if (shorter.length >= 2) {
        val a = lemma.filterNot { it in PLENE }
        if (a.length >= 2 && a == text.filterNot { it in PLENE }) return SPELLED_OTHERWISE
    }
    // A letter more or less, or another: misread or spelled otherwise
    if (shorter.length >= 3 && levenshtein(lemma, text) == 1) return ONE_LETTER
    return 0f
}

private fun levenshtein(
    a: String,
    b: String,
): Int {
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        val t = prev
        prev = cur
        cur = t
    }
    return prev[b.length]
}

/** Whether the abbreviation's letters [abbreviation] are, in order, the starts of each of [words] (א״כ: אם, כן). */
internal fun abbreviates(
    abbreviation: String,
    words: List<String>,
): Boolean {
    fun from(
        i: Int,
        rest: String,
    ): Boolean {
        if (i == words.size) return rest.isEmpty()
        val most = minOf(words[i].length, rest.length - (words.size - i - 1))
        return (1..most).any { n -> rest.regionMatches(0, words[i], 0, n) && from(i + 1, rest.substring(n)) }
    }
    return abbreviation.length >= words.size && from(0, abbreviation)
}

/** A word compared: its letters, and whether it is printed as an abbreviation. */
private class TextWord(
    val key: String,
    val isAbbreviation: Boolean,
)

private fun textWord(word: String) = TextWord(dafKey(word), word.any { it in ABBREVIATION_MARKS })

/**
 * The best place of [part] (a lemma's words, as keys) in [text] from [from] on, aligned word by word: the lemma whole,
 * the text from any word to any word. Returns the range of the text's words, or null when it scores too low.
 */
private fun bestPlace(
    part: List<TextWord>,
    text: List<TextWord>,
    from: Int,
): IntRange? {
    val n = part.size
    val m = text.size
    if (n == 0 || m == 0 || from >= m) return null
    val none = Float.NEGATIVE_INFINITY
    // score[i][j]: part's first i words aligned, the last text word used text[j - 1]; start[i][j]: where it began
    val score = Array(n + 1) { FloatArray(m + 1) { none } }
    val start = Array(n + 1) { IntArray(m + 1) { -1 } }
    for (j in from..m) {
        score[0][j] = 0f
        start[0][j] = j
    }
    for (i in 1..n) {
        for (j in from..m) {
            var best = none
            var begin = -1

            fun offer(
                value: Float,
                at: Int,
            ) {
                if (value > best) {
                    best = value
                    begin = at
                }
            }
            // A lemma word the print has not
            if (score[i - 1][j] > none) offer(score[i - 1][j] + SKIP, start[i - 1][j])
            if (j > from) {
                val word = text[j - 1]
                // The word, alike or another one in its place
                if (score[i - 1][j - 1] > none) {
                    val a = alike(part[i - 1].key, word.key)
                    offer(score[i - 1][j - 1] + if (a > 0f) a else OTHER_WORD, start[i - 1][j - 1])
                }
                // The lemma's abbreviation for several of the text's words (ה״ק for הכי קאמר), as one word alike
                if (part[i - 1].isAbbreviation) {
                    for (k in 2..minOf(MAX_ABBREVIATED, j - from)) {
                        val words = (j - k until j).map { text[it].key }
                        if (score[i - 1][j - k] > none && abbreviates(part[i - 1].key, words)) {
                            offer(score[i - 1][j - k] + 1f, start[i - 1][j - k])
                        }
                    }
                }
                // A word the lemma has not, inside the quote (after its first word: i > 1 forbade one between its
                // first and second words)
                if (i >= 1 && score[i][j - 1] > none) offer(score[i][j - 1] + SKIP, start[i][j - 1])
                // An abbreviation printed for several of the lemma's words
                if (word.isAbbreviation) {
                    for (k in 2..minOf(MAX_ABBREVIATED, i)) {
                        if (score[i - k][j - 1] > none && abbreviates(word.key, part.subList(i - k, i).map { it.key })) {
                            offer(score[i - k][j - 1] + k * ABBREVIATED, start[i - k][j - 1])
                        }
                    }
                }
            }
            score[i][j] = best
            start[i][j] = begin
        }
    }
    var endAt = -1
    for (j in from + 1..m) if (endAt < 0 || score[n][j] > score[n][endAt]) endAt = j
    if (endAt < 0 || score[n][endAt] == none) return null
    val needed = if (n == 1) ONE_WORD_SCORE else PHRASE_SCORE
    if (score[n][endAt] / n < needed) return null
    val first = start[n][endAt]
    return if (first in 0 until endAt) first until endAt else null
}

/** [lemma]'s place in [text] from [from] on: from its first part's first word to its last part's last one. */
private fun placeOf(
    lemma: List<TextWord>,
    text: List<TextWord>,
    from: Int,
): IntRange? {
    // Split at its ellipses: "מאימתי וכו' בערבין" is מאימתי, then בערבין further on
    val parts = mutableListOf(mutableListOf<TextWord>())
    for (word in lemma) if (word.key in ELLIPSES) parts += mutableListOf<TextWord>() else parts.last() += word
    val kept = parts.filter { it.isNotEmpty() }
    if (kept.isEmpty()) return null
    val head = bestPlace(kept.first(), text, from) ?: return null
    var end = head.last
    for (part in kept.drop(1)) {
        val at = bestPlace(part, text, end + 1) ?: break
        end = at.last
    }
    return head.first..end
}

/**
 * For each of one commentator's comments on a Gemara segment, in the order he wrote them, the range of [segment]'s
 * words (as printed) its dibbur hamatchil ([lemmas], each a comment's bold words) quotes, or null when not found. A
 * commentator goes down the Gemara in order, so each is looked for from where the one before it was found, then
 * anywhere in the segment: Rashi's comments on a phrase come before his comments on its words (הקטר חלבים ואברים,
 * then חלבים, then אברים), and a word the segment says twice (מצותן) is the one after the comment before.
 */
internal fun matchLemmas(
    segment: List<String>,
    lemmas: List<List<String>>,
): List<IntRange?> {
    val text = segment.map(::textWord)
    var from = 0
    return lemmas.map { lemma ->
        val words = lemma.map(::textWord).filter { it.key.isNotEmpty() }.dropWhile { it.key in MARKERS }
        val place = placeOf(words, text, from) ?: placeOf(words, text, 0)
        if (place != null) from = place.first
        place
    }
}

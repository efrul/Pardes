package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.PREFIXES
import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.quoteKey
import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.stem
import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine.Companion.verbatimKey

/**
 * The precedence of the earlier level ([BookLevels]) among the lines of [HybridSearchEngine]'s fused page that hold the
 * query as a sentence ([order]).
 */
internal object LevelPrecedence {
    /** The first places the lines holding the query as a sentence trade by level ([byLevel]). */
    private const val LEVEL_PLACES = 20

    /** From this many words on a query held as a sentence is quoted; fewer are more a subject ([order]). */
    private const val SENTENCE_WORDS = 3

    /**
     * [page] (the fused order) with the earlier level ([levelOf], [BookLevels]) first among its lines that hold
     * [query] as a sentence ([textOf], [holdsPhrase]), as semantic-model/eval-real/results-level-order.md measured it:
     * - a query of [SENTENCE_WORDS] words or more: its holders, spelling apart, trade the places holders take in the
     *   first [LEVEL_PLACES] ([byLevel]), and the earliest takes the first place too when its level is earlier than
     *   that of the line there: the Gemara before the Acharonim quoting it, the verse before the Midrash;
     * - a query of two words, more a subject than a sentence ("דין תפילה" is held by "מדין תפלה"): only a verse of
     *   the Tanakh holding the two words as written takes the first place ("בראשית ברא", "מיכאל שרכם").
     */
    fun <T> order(
        page: List<T>,
        query: String,
        textOf: (T) -> String?,
        levelOf: (T) -> Int,
    ): List<T> {
        val words = quoteKey(query)?.split(' ') ?: return page
        val quoted = words.size >= SENTENCE_WORDS
        val sentence = Sentence(words, spelling = quoted)
        val holders =
            page.filterTo(LinkedHashSet()) { id ->
                (quoted || levelOf(id) == BookLevels.MIKRA) && textOf(id)?.let(sentence::heldBy) == true
            }
        if (holders.isEmpty()) return page
        val place = HashMap<T, Int>(page.size * 2).apply { page.forEachIndexed { i, id -> putIfAbsent(id, i) } }
        val earliest = compareBy<T>({ levelOf(it) }, { place.getValue(it) })
        val out = byLevel(page, holders, earliest)
        val first = holders.minWith(earliest)
        return if (out.first() !in holders && levelOf(first) < levelOf(out.first())) {
            listOf(first) + out.filter { it != first }
        } else {
            out
        }
    }

    /**
     * [page] with [holders] on the places holders take in its first [LEVEL_PLACES], the [earliest] first (by level,
     * then place); the holders there that no longer fit come right after them, the rest in its order.
     */
    fun <T> byLevel(
        page: List<T>,
        holders: Set<T>,
        earliest: Comparator<T>,
    ): List<T> {
        val top = page.take(LEVEL_PLACES).toMutableList()
        val slots = top.indices.filter { top[it] in holders }
        if (slots.isEmpty()) return page
        val chosen = holders.sortedWith(earliest).take(slots.size)
        val left = top.filter { it in holders && it !in chosen }
        slots.forEachIndexed { k, i -> top[i] = chosen[k] }
        return top + left + page.drop(LEVEL_PLACES).filterNot { it in chosen }
    }

    /** Whether [text] (a line, HTML) holds [query] as a sentence ([Sentence]), with [spelling] spelling apart. */
    fun holdsPhrase(
        text: String,
        query: String,
        spelling: Boolean,
    ): Boolean = quoteKey(query)?.let { Sentence(it.split(' '), spelling).heldBy(text) } == true

    /** Whether a line's [word] may stand for [form]: as itself, or without one or two one-letter [PREFIXES]. */
    fun isFormOf(
        word: String,
        form: String,
    ): Boolean =
        form.isNotEmpty() &&
            word.endsWith(form) &&
            when (word.length - form.length) {
                0 -> true
                1 -> word[0] in PREFIXES
                2 -> word[0] in PREFIXES && word[1] in PREFIXES
                else -> false
            }

    /** Whether a form of a line's [word] ([isFormOf]) has the skeleton [target] ([skeleton]). */
    fun hasSkeleton(
        word: String,
        target: String,
    ): Boolean =
        skeletonIs(word, 0, target) ||
            (
                word.length > 1 &&
                    word[0] in PREFIXES &&
                    (skeletonIs(word, 1, target) || (word.length > 2 && word[1] in PREFIXES && skeletonIs(word, 2, target)))
            )

    /** Whether [word] from [from] on has the skeleton [target] ([skeleton]), told without making it. */
    private fun skeletonIs(
        word: String,
        from: Int,
        target: String,
    ): Boolean {
        if (from == word.length) return target.isEmpty()
        if (target.isEmpty() || word[from] != target[0]) return false
        val finalNun = word.length - from >= 2 && word.endsWith("ינ")
        var at = 1
        for (k in from + 1 until word.length) {
            val c = if (finalNun && k == word.length - 1) 'מ' else word[k]
            if (c == 'ו' || c == 'י') continue
            if (at == target.length || target[at] != c) return false
            at++
        }
        return at == target.length
    }

    /**
     * [word] (a [verbatimKey] word, final letters as their base forms) without the ו and י after its first letter, a
     * final ין as ים first: the same word in full and defective spelling (תרדוף, תרדף), the Mishnah's plural as today's.
     */
    fun skeleton(word: String): String {
        val w = if (word.endsWith("ינ")) word.dropLast(1) + "מ" else word
        return w.take(1) + w.drop(1).filterNot { it == 'ו' || it == 'י' }
    }

    /** The divine name as a query writes it, and as the text does: ה (from ה') for יהוה, אלוק and אלק for אלה. */
    fun names(word: String): Set<String> {
        if (word == "ה") return setOf("ה", "יהוה", "יי", "השמ")
        for (short in listOf("אלוק", "אלק")) {
            if (word.startsWith(short) && word.length > short.length) return setOf(word, "אלה" + word.substring(short.length))
        }
        return if (word == "אלוק" || word == "אלק") setOf(word, "אלוה") else setOf(word)
    }

    /**
     * A query's [words] ([quoteKey]'s), to tell whether a line holds them as a sentence: in a row and in order, a line's
     * word standing for itself or itself without one or two one-letter prefixes ([isFormOf]); with [spelling], a word
     * also matches by its [skeleton] (full and defective spelling, the Mishnah's plural), the divine name as the query
     * writes it ([names]) against the text's. Made once for a page's lines, which are matched word by word as they are:
     * no form or skeleton of a line's word is made.
     */
    private class Sentence(
        words: List<String>,
        private val spelling: Boolean,
    ) {
        private val wanted = words.map { listOf(it, stem(it)).distinct() }

        // The skeleton of the query's word as written, not of its stem: a stem may have lost a root letter taken for a
        // prefix (מגדל, גדל), and would then match another word's skeleton (גדול)
        private val skeletons = words.map { w -> names(w).map(::skeleton).distinct() }

        /** Whether [text] (a line, HTML) holds the words. */
        fun heldBy(text: String): Boolean {
            val line = verbatimKey(text).split(' ')
            return (0..line.size - wanted.size).any { i -> wanted.indices.all { j -> matches(line[i + j], j) } }
        }

        private fun matches(
            word: String,
            j: Int,
        ): Boolean = wanted[j].any { isFormOf(word, it) } || (spelling && skeletons[j].any { hasSkeleton(word, it) })
    }
}

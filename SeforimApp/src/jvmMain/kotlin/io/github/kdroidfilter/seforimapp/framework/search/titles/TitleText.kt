package io.github.kdroidfilter.seforimapp.framework.search.titles

/**
 * How book titles, category names and author names are read for the title search, and how a query is read against them.
 * The same on both sides, so they meet: nikud, quote marks (`שו"ע`, `ש''ע`, `שו״ע`) and punctuation go, a maqaf or a hyphen
 * splits words, final letters are folded (`שולחן` and a typed `שולחנ` meet).
 *
 * Two spellings of a word meet as in Klei Kodesh's Kitvei Hakodesh (https://kleikodesh.github.io/), whose rules these are:
 * with or without the definite article (`הכוזרי`, `כוזרי`), and written with or without a vowel letter between two
 * consonants (`נידה`, `נדה`; `שלחן`, `שולחן`). A query's acronyms are read by [TitleAbbreviations].
 */
internal object TitleText {
    private const val HE = 'ה'

    /** A word read of a text: its letters, as compared, without marks. */
    fun words(text: String): List<String> = rawWords(text).mapNotNull { clean(it).ifEmpty { null } }

    /** The words of [text] as typed, quote marks kept (they tell an acronym), split where [words] splits. */
    fun rawWords(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (c in text) {
            if (c.isWhitespace() || c in SEPARATORS) {
                if (sb.isNotEmpty()) out += sb.toString()
                sb.setLength(0)
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    /** [raw] without nikud, marks and punctuation, lower case, final letters folded. */
    fun clean(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            if (c !in '\u0591'..'\u05C7' && c.isLetterOrDigit()) sb.append(foldFinal(c.lowercaseChar()))
        }
        return sb.toString()
    }

    fun hasQuote(raw: String): Boolean = raw.any { it in QUOTES }

    private fun foldFinal(c: Char): Char =
        when (c) {
            'ך' -> 'כ'
            'ם' -> 'מ'
            'ן' -> 'נ'
            'ף' -> 'פ'
            'ץ' -> 'צ'
            else -> c
        }

    /** [word] without a leading definite article, or null when it has none (or too little is left). */
    fun withoutHe(word: String): String? = if (word.length >= 3 && word[0] == HE) word.substring(1) else null

    /** The forms of [word] it is looked up by: itself, with the article and without it. */
    fun articleForms(word: String): List<String> = listOfNotNull(word, HE + word, withoutHe(word))

    private fun isHebrewLetter(c: Char): Boolean = c in 'א'..'ת'

    /**
     * [word] without the vowel letters (ו, י) that stand between two Hebrew letters, and where they stood, as
     * "position in the skeleton" times 2 plus 1 for a yod: `שולחן` is שלחן with a vav at 1.
     */
    class Skeleton(
        val letters: String,
        val vowels: IntArray,
    )

    fun skeleton(word: String): Skeleton {
        val sb = StringBuilder(word.length)
        var vowels = IntArray(0)
        for (i in word.indices) {
            val c = word[i]
            val mater =
                (c == 'ו' || c == 'י') &&
                    i > 0 &&
                    i < word.length - 1 &&
                    isHebrewLetter(word[i - 1]) &&
                    isHebrewLetter(word[i + 1])
            if (mater) vowels += sb.length * 2 + (if (c == 'י') 1 else 0) else sb.append(c)
        }
        return Skeleton(sb.toString(), vowels)
    }

    /** Two spellings of one word: the same skeleton, and the vowel letters of one all in the other (נדה, נידה). */
    fun spellingVariants(
        a: Skeleton,
        b: Skeleton,
    ): Boolean {
        if (a.letters != b.letters) return false
        val (small, large) = if (a.vowels.size <= b.vowels.size) a to b else b to a
        return small.vowels.all { it in large.vowels }
    }

    /**
     * One word of a query: what it may be read as, each reading the words that must all be found. A plain word has one
     * reading, itself; an acronym its expansions, and itself too when typed without quotes (`במדבר` is the book before
     * it is במדבר רבה). [typed] is the reading that is the words as typed, for the ranking.
     */
    class QueryToken(
        val readings: List<List<String>>,
        val typed: List<String>,
    ) {
        val isPlain: Boolean get() = readings.size == 1 && readings[0].size == 1
    }

    /** The query's words, acronyms read as [TitleAbbreviations] reads them, longest key first (`שו"ע הרב`). */
    fun queryTokens(query: String): List<QueryToken> {
        val raws = rawWords(query).filter { clean(it).isNotEmpty() }
        val cleaned = raws.map(::clean)
        val out = ArrayList<QueryToken>()
        var i = 0
        while (i < raws.size) {
            val match = abbreviationAt(raws, cleaned, i)
            if (match == null) {
                out += QueryToken(listOf(listOf(cleaned[i])), listOf(cleaned[i]))
                i++
                continue
            }
            val (readings, consumed) = match
            val typed = cleaned.subList(i, i + consumed).toList()
            // Unquoted, the words stand for themselves too: `במדבר` the book, `בן איש חי` the title
            val all = if (consumed == 1 && hasQuote(raws[i])) readings else (readings + listOf(typed)).distinct()
            out += QueryToken(all, if (consumed == 1 && hasQuote(raws[i])) readings[0] else typed)
            i += consumed
        }
        return out
    }

    private fun abbreviationAt(
        raws: List<String>,
        cleaned: List<String>,
        start: Int,
    ): Pair<List<List<String>>, Int>? {
        val maxWindow = minOf(MAX_KEY_WORDS, raws.size - start)
        for (w in maxWindow downTo 1) {
            val key = cleaned.subList(start, start + w).joinToString(" ")
            KEYS[key]?.let { return it to w }
            val first = withoutHe(cleaned[start]) ?: continue
            val keyWithoutHe = (listOf(first) + cleaned.subList(start + 1, start + w)).joinToString(" ")
            KEYS[keyWithoutHe]?.let { return it to w }
        }
        return null
    }

    // The map with its words read as everything else is (final letters folded); and the Name, written `ה'` in titles
    private val KEYS: Map<String, List<List<String>>> =
        TitleAbbreviations.MAP.entries.associate { (key, readings) ->
            words(key).joinToString(" ") to readings.map { reading -> reading.flatMap(::words) }
        } + ("השמ" to listOf(listOf("ה")))

    private val MAX_KEY_WORDS = KEYS.keys.maxOf { key -> key.count { it == ' ' } + 1 }

    private const val QUOTES = "\"'״׳“”‘’`"

    // Maqaf, hyphens, slashes and the punctuation between words; quote marks stay in a word (they tell an acronym)
    private const val SEPARATORS = "\u05BE-–—/\\,.:;()[]{}|!?"
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

/**
 * A word of a line of the Talmud: its [letters] as they are compared with a verse's, where it stands in the line's
 * HTML ([start] until [end], its geresh with it: ה׳), and whether a quote opens before it or closes after it (״ …״).
 * A word inside a link is no word here.
 */
internal class LineWord(
    val letters: String,
    val start: Int,
    val end: Int,
    val opens: Boolean,
    val closes: Boolean,
) {
    /** Quoted by itself: ״לָכֶם״. */
    val quotedAlone: Boolean get() = opens && closes
}

/** A verse a line is linked to: the line of the Tanakh, and its words as they are compared. */
internal class Verse(
    val lineId: Long,
    val words: List<String>,
)

/**
 * A word as it is compared: its letters without their nikud and teamim, and without the letters ו and י, which a quote
 * in the Talmud spells as it pleases (בשכבך for ובשכבך, אותו for אתו). The Name is one word however it is written
 * (ה׳, יְיָ, אלקים for אלהים).
 */
internal fun comparedLetters(word: String): String {
    val letters = word.filter { it in ALEF..TAV }
    if (letters in NAMES) return NAME
    val spelled = letters.replace("אלק", "אלה")
    return spelled.filter { it != 'ו' && it != 'י' }.ifEmpty { spelled }
}

/** The words of a verse of the library: its number, its notes and its section marks ({פ}) left out. */
internal fun wordsOfVerse(html: String): List<String> =
    html
        .replace(VERSE_NOTES, " ")
        .replace(TAG, "")
        .replace(ENTITY, " ")
        .replace(MAQAF, ' ')
        .replace(VERSE_NUMBER, "")
        .split(' ', '\n', '\t')
        .map(::comparedLetters)
        .filter { it.isNotEmpty() }

/** The words of a line of the library, each where it stands in its HTML. */
internal fun wordsOfLine(html: String): List<LineWord> {
    val words = ArrayList<LineWord>()
    var i = 0
    var inLink = false
    while (i < html.length) {
        val c = html[i]
        when {
            c == '<' -> {
                val close = html.indexOf('>', i).takeIf { it >= 0 } ?: html.lastIndex
                if (html.startsWith("<a", i) && html.getOrNull(i + 2)?.isLetter() != true) inLink = true
                if (html.startsWith("</a>", i)) inLink = false
                i = close + 1
            }
            c == '&' -> i = ENTITY.matchAt(html, i)?.let { it.range.last + 1 } ?: (i + 1)
            c in ALEF..TAV -> {
                var end = i
                while (end < html.length && (html[end] in ALEF..TAV || html[end].isMark())) end++
                val letters = comparedLetters(html.substring(i, end))
                val closes = html.getOrNull(end).isQuoteMark()
                if (html.getOrNull(end)?.let { it in GERESH } == true) end++
                if (!inLink && letters.isNotEmpty()) words += LineWord(letters, i, end, html.getOrNull(i - 1).isQuoteMark(), closes)
                i = end
            }
            else -> i++
        }
    }
    return words
}

/**
 * The words of [line] that quote [verse]: its longest run of the verse's words in their order that is a quote
 * ([quoteIn]), among the words [free] to be linked. Null when there is none.
 */
internal fun quoteOf(
    line: List<LineWord>,
    verse: List<String>,
    free: BooleanArray,
): IntRange? {
    val left = free.copyOf()
    while (true) {
        val run = longestRun(line, verse, left) ?: return null
        quoteIn(line, run)?.let { return it }
        run.forEach { left[it] = false }
    }
}

/** The longest run of [verse]'s words in their order among the [free] words of [line], of [MIN_WORDS] words or more. */
private fun longestRun(
    line: List<LineWord>,
    verse: List<String>,
    free: BooleanArray,
): IntRange? {
    var best = 0
    var bestEnd = 0
    var runs = IntArray(verse.size + 1)
    for (l in 1..line.size) {
        val next = IntArray(verse.size + 1)
        if (free[l - 1]) {
            for (v in 1..verse.size) {
                if (line[l - 1].letters != verse[v - 1]) continue
                next[v] = runs[v - 1] + 1
                if (next[v] > best) {
                    best = next[v]
                    bestEnd = l
                }
            }
        }
        runs = next
    }
    return ((bestEnd - best) until bestEnd).takeIf { best >= MIN_WORDS }
}

/**
 * The quote in [run], words of a verse in their order: [MIN_WORDS] words or more, of no fewer than [MIN_LETTERS]
 * letters. In a line that marks its quotes, the Talmud's own words may be a verse's too: the words before the quote
 * opens or after it closes are left out (אָמַר לָהֶם: ״לְכוּ אִכְלוּ מַשְׁמַנִּים״), and two words alone are a quote only where one
 * opens or closes (צאת הכוכבים is the Talmud's).
 */
private fun quoteIn(
    line: List<LineWord>,
    run: IntRange,
): IntRange? {
    var quote = run
    val opening = quote.firstOrNull { line[it].opens }
    if (opening != null && (quote.first until opening).none { line[it].closes }) quote = opening..quote.last
    val closing = quote.lastOrNull { line[it].closes }
    if (closing != null && (closing + 1..quote.last).none { line[it].opens }) quote = quote.first..closing
    if (quote.count() < MIN_WORDS || quote.sumOf { line[it].letters.length } < MIN_LETTERS) return null
    val marked = quote.count() > MIN_WORDS || line[quote.first].opens || line[quote.last].closes
    return quote.takeIf { marked || line.none { it.opens || it.closes } }
}

/**
 * [html], a line of the Talmud, with the words that quote each of [verses] made links to it
 * (`<a class="ref" href="seforim://line/<id>">`, as the library's own citations): every run of two words or more of a
 * verse, the longer first, and a word quoted by itself that one verse alone has. The same [html] when nothing is quoted.
 */
internal fun withVerseLinks(
    html: String,
    verses: List<Verse>,
): String {
    val line = wordsOfLine(html)
    if (line.isEmpty() || verses.isEmpty()) return html
    val free = BooleanArray(line.size) { true }
    val links = ArrayList<Pair<IntRange, Long>>()
    // Each verse's longest quote first, then what else of it the line quotes
    var left = verses
    repeat(MAX_QUOTES) {
        val found =
            left
                .mapNotNull { verse -> quoteOf(line, verse.words, free)?.let { it to verse } }
                .sortedByDescending { it.first.count() }
        for ((run, verse) in found) {
            if (run.any { !free[it] }) continue
            run.forEach { free[it] = false }
            links += run to verse.lineId
        }
        left = found.map { it.second }
    }
    for ((index, word) in line.withIndex()) {
        if (!free[index] || !word.quotedAlone || word.letters.length < MIN_ALONE_LETTERS) continue
        val verse = verses.singleOrNull { word.letters in it.words } ?: continue
        links += (index..index) to verse.lineId
    }
    if (links.isEmpty()) return html
    return buildString(html.length + links.size * LINK_CHARS) {
        var at = 0
        for ((run, verseId) in links.sortedBy { it.first.first }) {
            val start = line[run.first].start
            val end = line[run.last].end
            append(html, at, start)
            appendLinked(html, start, end, verseId)
            at = end
        }
        append(html, at, html.length)
    }
}

/**
 * [html] from [start] to [end] as a link to [verseId]. A link crosses no tag and no quote mark (a run of a verse's
 * words may be two quotes of it, one after the other): each stretch of words between them is a link of its own.
 */
private fun StringBuilder.appendLinked(
    html: String,
    start: Int,
    end: Int,
    verseId: Long,
) {
    var at = start
    while (at < end) {
        var stop = at
        while (stop < end && html[stop] != '<' && !html[stop].isQuoteMark()) stop++
        // The punctuation between two quotes is no link
        val first = (at until stop).firstOrNull { html[it] in ALEF..TAV }
        if (first == null) {
            append(html, at, stop)
        } else {
            val last = (at until stop).last { html[it] in ALEF..TAV || html[it].isMark() || html[it] in GERESH } + 1
            append(html, at, first)
            append("<a class=\"ref\" href=\"").append(VERSE_LINK).append(verseId).append("\">")
            append(html, first, last).append("</a>")
            append(html, last, stop)
        }
        if (stop >= end) break
        val after = if (html[stop] == '<') (html.indexOf('>', stop).takeIf { it in stop until end } ?: (end - 1)) + 1 else stop + 1
        append(html, stop, after)
        at = after
    }
}

private fun Char.isMark(): Boolean = this in FIRST_MARK..LAST_MARK && this != MAQAF

private fun Char?.isQuoteMark(): Boolean = this == GERSHAYIM || this == '"' || this == '\u201C' || this == '\u201D'

private const val ALEF = 'א'
private const val TAV = 'ת'

// The teamim and the nikud, among them the maqaf, which parts two words
private const val FIRST_MARK = '\u0591'
private const val LAST_MARK = '\u05C7'
private const val MAQAF = '\u05BE'
private const val GERSHAYIM = '\u05F4'
private const val GERESH = "\u05F3'"

private const val NAME = "יהוה"
private val NAMES = setOf("ה", "יי", "ייי", NAME)

private val TAG = Regex("<[^>]*>")
private val ENTITY = Regex("&#?[A-Za-z0-9]+;")

// A verse's notes (a ketiv's reading, a variant) and its section mark, which are not its words
private val VERSE_NOTES = Regex("""<i class="footnote">.*?</i>|<sup[^>]*>.*?</sup>|<span class="mam-spi[^"]*">.*?</span>""")
private val VERSE_NUMBER = Regex("""^\s*\([^)]*\)""")

private const val VERSE_LINK = "seforim://line/"
private const val LINK_CHARS = 48

// A quote is two words or more, of five letters or more as they are compared (לא כי is none)
private const val MIN_WORDS = 2
private const val MIN_LETTERS = 5
private const val MIN_ALONE_LETTERS = 3
private const val MAX_QUOTES = 3

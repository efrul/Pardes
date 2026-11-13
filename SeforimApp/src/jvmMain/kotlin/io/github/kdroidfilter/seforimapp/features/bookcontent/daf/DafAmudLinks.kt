package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

/**
 * One amud's printed words tied to the library, for its clicks and marks: which library line each word is of (the
 * layout's [DafLine.u]), which Gemara line each comment on the page is on ([baseOf], the library's COMMENTARY links,
 * read once for the page), and which of the Gemara's printed words each comment's dibbur hamatchil quotes
 * ([matchLemmas], as the library knows only whole lines), or which its anchor puts it on ([passages], a book's daf's).
 */
internal class DafAmudLinks(
    private val amud: DafAmud,
    /** Each comment on the page and the Gemara lines it is on. */
    val baseOf: Map<Long, Set<Long>>,
    /** Each comment's book (its commentator). */
    val bookOf: Map<Long, Long>,
    /** Where each printed word is in its library line (DafAnchors.kt), for the user's highlights and notes. */
    val anchors: Map<DafWord, DafAnchor> = emptyMap(),
    /** Each library line's plain text with diacritics, the anchors' offsets', for the find in page's matches. */
    val texts: Map<Long, String> = emptyMap(),
    /**
     * The passages Zayit's anchors mark in a book's lines (BookDafSource): each line's words, as indices among its words
     * on the page, and the comments on them, which quote those words whatever their dibbur.
     */
    passages: Map<Long, List<Pair<IntRange, Set<Long>>>> = emptyMap(),
) {
    /** The comments on each Gemara line of the page, in the order they are printed. */
    val commentsOn: Map<Long, List<Long>>

    /** The Gemara words each comment's dibbur quotes, on this page. */
    val quoted: Map<Long, Set<DafWord>>

    /** The comments whose dibbur quotes each Gemara word. */
    val quoting: Map<DafWord, Set<Long>>

    /** The words of each library line on the page. */
    val wordsOf: Map<Long, List<DafWord>>

    init {
        val words = LinkedHashMap<Long, MutableList<DafWord>>()
        // Each comment's lemma: its bold words, the first of its words up to the first one not bold
        val lemma = LinkedHashMap<Long, MutableList<String>>()
        val lemmaDone = HashSet<Long>()
        amud.lines.forEachIndexed { li, line ->
            val texts = line.t.split(' ').filter { it.isNotEmpty() }
            val bold = line.d.toSet()
            texts.forEachIndexed { wi, text ->
                val id = line.lineIdAt(wi) ?: return@forEachIndexed
                words.getOrPut(id) { mutableListOf() } += DafWord(li, wi)
                if (!line.isGemara && id !in lemmaDone) {
                    if (wi in bold) lemma.getOrPut(id) { mutableListOf() } += text else lemmaDone += id
                }
            }
        }
        wordsOf = words
        // The comments printed on the page, by the Gemara line they are on, in print order
        val on = LinkedHashMap<Long, MutableList<Long>>()
        for ((comment, bases) in baseOf) {
            if (comment !in words) continue
            for (base in bases) on.getOrPut(base) { mutableListOf() } += comment
        }
        val printOrder = words.keys.withIndex().associate { (i, id) -> id to i }
        commentsOn = on.mapValues { (_, comments) -> comments.sortedBy { printOrder[it] ?: Int.MAX_VALUE } }
        // The comments an anchor puts on words, on those; each commentator's other comments on a segment, in the order
        // he wrote them, in its words on the page
        val quotedBy = HashMap<Long, MutableSet<DafWord>>()
        for ((base, marked) in passages) {
            val segment = words[base] ?: continue
            for ((range, comments) in marked) {
                val at = range.mapNotNull { segment.getOrNull(it) }
                if (at.isNotEmpty()) for (comment in comments) quotedBy.getOrPut(comment) { mutableSetOf() } += at
            }
        }
        val anchored = quotedBy.keys.toSet()
        for ((base, comments) in commentsOn) {
            val segment = words[base] ?: continue
            val texts =
                segment.map { w ->
                    amud.lines[w.line]
                        .t
                        .split(' ')
                        .filter { it.isNotEmpty() }[w.word]
                }
            for ((_, ofOne) in comments.filterNot { it in anchored }.groupBy { bookOf[it] }) {
                val found = matchLemmas(texts, ofOne.map { lemma[it].orEmpty() })
                ofOne.zip(found).forEach { (comment, range) ->
                    if (range != null) quotedBy.getOrPut(comment) { mutableSetOf() } += range.map { segment[it] }
                }
            }
        }
        quoted = quotedBy
        val byWord = HashMap<DafWord, MutableSet<Long>>()
        for ((comment, ws) in quotedBy) for (w in ws) byWord.getOrPut(w) { mutableSetOf() } += comment
        quoting = byWord
    }

    /** The library line of [word], or null. */
    fun lineIdOf(word: DafWord): Long? = amud.lines.getOrNull(word.line)?.lineIdAt(word.word)

    /** Whether [word] is the Gemara's. */
    fun isGemara(word: DafWord): Boolean = amud.lines.getOrNull(word.line)?.isGemara == true

    /** The library lines of the page's comments (Rashi, Tosafot, ...): what [baseOf] and [bookOf] are read for. */
    companion object {
        fun commentIdsOf(amud: DafAmud): Set<Long> = amud.lines.filterNot { it.isGemara }.flatMapTo(LinkedHashSet()) { it.lineIds }
    }
}

/**
 * What a click or the pointer on [word] stands for: a comment's word, that comment; a Gemara word its dibbur
 * quotes, the comments quoting it; another Gemara word, none (its segment alone).
 */
internal fun DafAmudLinks.passageAt(word: DafWord): Set<Long> {
    val id = lineIdOf(word) ?: return emptySet()
    return if (isGemara(word)) quoting[word].orEmpty() else setOf(id)
}

package io.github.kdroidfilter.seforimapp.framework.search.titles

import io.github.kdroidfilter.seforimapp.framework.search.titles.TitleText.QueryToken
import java.util.BitSet

/** A book as the title search reads it: its title, the names of the categories above it, its authors and acronyms. */
class TitleBook(
    val id: Long,
    val title: String,
    val categoryId: Long,
    val order: Float,
    val isBaseBook: Boolean,
    val path: List<String>,
    val authorIds: List<Long>,
    val acronyms: List<String>,
)

/** An author as the search reads them: the name, the other names they are known by, and how many books they wrote. */
class TitleAuthor(
    val id: Long,
    val name: String,
    val aliases: List<String>,
    val bookCount: Int,
)

/**
 * The library's books and authors found by name as people type them, in memory: built once from the catalog, then a
 * query costs a few lookups in a sorted word list (well under a millisecond for most, a few for a one-letter word).
 *
 * The rules are Klei Kodesh's (Kitvei Hakodesh, https://kleikodesh.github.io/): every typed word must be found, as typed
 * or as another spelling of it ([TitleText]), in the title, in the name of a category above the book or in an author's
 * name, the last word also as the start of a word (it is still being typed); an acronym as what it stands for. Words
 * may come in any order (`ערוך שולחן`, `משנה ברורה חפץ חיים`). When nothing is found so, one typo per word is forgiven
 * (two in a long word), and when nothing is found still, every word may be the start of a word (`נביא שמואל`: שמואל א,
 * of the נביאים). Ranked: the title typed whole, then a title the query begins, then the base books of a category the
 * query names whole (`תנך`: בראשית, before אור הישר (תנך)), then the most words found in the title, a base book, a
 * shorter title, and the library's order.
 */
@Suppress("TooManyFunctions") // The matching and the ranking share the index's arrays
class BookTitleIndex(
    books: List<TitleBook>,
    authors: List<TitleAuthor>,
) {
    private val books: Array<TitleBook> = books.toTypedArray()
    private val titleWords: Array<List<String>> = Array(books.size) { TitleText.words(books[it].title) }

    // Each acronym as one word (שוע), and its words in any order (`רמבם הלכות שבת` for `הלכות שבת רמבם`)
    private val acronymKeys: Array<Set<String>> =
        Array(books.size) { i -> books[i].acronyms.flatMap { acronymKeys(TitleText.words(it)) }.toSet() }
    private val authorsById: Map<Long, TitleAuthor> = authors.associateBy { it.id }

    // The names of the categories above the books, each as its words
    private val categoryWords: Map<String, List<String>> = books.flatMapTo(HashSet()) { it.path }.associateWith(TitleText::words)
    private val authorNames: List<Pair<TitleAuthor, List<String>>> =
        authors.flatMap { a -> (listOf(a.name) + a.aliases).map { a to TitleText.words(it) } }.filter { it.second.isNotEmpty() }
    private val authorVocabulary: List<String> = authorNames.flatMapTo(HashSet()) { it.second }.toList()

    // Every word of every field, sorted (prefix lookups are a range), and which books carry it in their title or elsewhere
    private val vocabulary: Array<String>
    private val inTitle: Array<IntArray>
    private val elsewhere: Array<IntArray>
    private val bySkeleton: Map<String, IntArray>

    // Each book's words, every field
    private val allWords: Array<List<String>>

    init {
        val title = HashMap<String, MutableSet<Int>>()
        val words = ArrayList<List<String>>(this.books.size)
        val other = HashMap<String, MutableSet<Int>>()
        for (i in this.books.indices) {
            val book = this.books[i]
            titleWords[i].forEach { title.getOrPut(it) { LinkedHashSet() } += i }
            val rest =
                book.path.flatMap(TitleText::words) +
                    book.authorIds.flatMap { id ->
                        authorsById[id]?.let { a -> (listOf(a.name) + a.aliases).flatMap(TitleText::words) }.orEmpty()
                    } +
                    acronymKeys[i].filter { ' ' !in it }
            rest.forEach { other.getOrPut(it) { LinkedHashSet() } += i }
            words += titleWords[i] + rest
        }
        allWords = words.toTypedArray()
        vocabulary = (title.keys + other.keys).toSortedSet().toTypedArray()
        inTitle = Array(vocabulary.size) { title[vocabulary[it]]?.toIntArray()?.apply { sort() } ?: EMPTY }
        elsewhere = Array(vocabulary.size) { other[vocabulary[it]]?.toIntArray()?.apply { sort() } ?: EMPTY }
        bySkeleton =
            vocabulary.indices
                .groupBy { TitleText.skeleton(vocabulary[it]).letters }
                .mapValues { it.value.toIntArray() }
    }

    val bookCount: Int get() = books.size

    /** A book found, with what the ranking saw. */
    class BookMatch(
        val book: TitleBook,
        // [TIER_WHOLE]: the title (or an acronym of the book) typed whole; [TIER_BEGUN]: a title the query begins; 0 otherwise
        val tier: Int,
        val titleHits: Int,
        val exactAcronym: Boolean,
        // In the library's order (the catalog's tree, depth first), as the books were given
        internal val position: Int,
        // A base book of a category the query names whole, found by that name alone
        internal val ofNamedCategory: Boolean = false,
    )

    /** The books named by [query], best first, at most [limit]. */
    fun searchBooks(
        query: String,
        limit: Int,
    ): List<BookMatch> {
        val tokens = TitleText.queryTokens(query)
        if (tokens.isEmpty()) return emptyList()
        val found = matchTokens(tokens, fuzzy = false).takeIf { m -> !m.all.isEmpty } ?: matchTokens(tokens, fuzzy = true)
        val matches = matched(found, tokens).ifEmpty { matched(matchTokens(tokens, fuzzy = false, begun = true), tokens) }
        val order =
            compareByDescending<BookMatch> { it.tier }
                .thenByDescending { it.ofNamedCategory }
                // A named category's books in the library's order, whatever their titles
                .thenByDescending { if (it.ofNamedCategory) 0 else it.titleHits }
                .thenByDescending { it.book.isBaseBook }
                .thenBy { if (it.ofNamedCategory) 0 else titleWords[it.position].size }
                .thenBy { it.position }
        return matches.sortedWith(order).take(limit)
    }

    // The books [found], each with what the ranking sees
    private fun matched(
        found: Found,
        tokens: List<QueryToken>,
    ): List<BookMatch> {
        if (found.all.isEmpty) return emptyList()
        val typedKeys = acronymKeys(tokens.flatMap { it.typed })
        val required = tokens.filterNot { isFiller(it, tokens) }
        val named = categoryWords.filterValues { titleWhole(it, required) }.keys
        val matches = ArrayList<BookMatch>(found.all.cardinality())
        var i = found.all.nextSetBit(0)
        while (i >= 0) {
            val hits = found.title.count { it[i] }
            val acronym = typedKeys.any { it in acronymKeys[i] }
            val tier =
                when {
                    acronym || titleWhole(titleWords[i], required) -> TIER_WHOLE
                    titleBegins(titleWords[i], tokens) -> TIER_BEGUN
                    else -> 0
                }
            if (tier > 0 || acronym || wordsApart(i, tokens, found)) {
                matches += BookMatch(books[i], tier, hits, acronym, i, tier == 0 && ofNamedCategory(books[i], named))
            }
            i = found.all.nextSetBit(i + 1)
        }
        return matches
    }

    // A base book of one of the categories [named], its own or of a category right under it (תנ״ך: בראשית, of its תורה)
    private fun ofNamedCategory(
        book: TitleBook,
        named: Set<String>,
    ): Boolean = named.isNotEmpty() && book.isBaseBook && book.path.takeLast(NAMED_CATEGORY_DEPTH).any { it in named }

    private val positionById: Map<Long, Int> = this.books.indices.associateBy { this.books[it].id }

    /** An author found by name, and whether the query is the name typed whole (2) or its beginning (1). */
    class AuthorMatch(
        val author: TitleAuthor,
        // The other name the query was typed as, null when the author's own
        val alias: String?,
        val tier: Int,
    )

    /**
     * The authors named by [query], best first: every word found in their name or in one of their other names (`חפץ חיים`,
     * `הגר"א`), as for the books; a leading title (`הרב`, `רבי`) is not needed.
     */
    fun searchAuthors(
        query: String,
        limit: Int,
    ): List<AuthorMatch> {
        val tokens = TitleText.queryTokens(query).dropHonorific()
        if (tokens.isEmpty()) return emptyList()
        val exact = matchAuthors(tokens, fuzzy = false)
        val found = exact.ifEmpty { matchAuthors(tokens, fuzzy = true) }
        return found
            .groupBy { it.author.id }
            .map { (_, byName) -> byName.maxBy { it.tier * 2 + (if (it.alias == null) 1 else 0) } }
            // The name typed whole, then those with the most books: a family name is typed more often than a first one
            .sortedWith(
                compareByDescending<AuthorMatch> { it.tier == 2 }
                    .thenByDescending { it.author.bookCount }
                    .thenByDescending { it.tier },
            ).take(limit)
    }

    /** The authors of the book [bookId] the index knows. */
    fun authorsOf(bookId: Long): List<TitleAuthor> =
        positionById[bookId]?.let { books[it].authorIds.mapNotNull(authorsById::get) }.orEmpty()

    private fun matchAuthors(
        tokens: List<QueryToken>,
        fuzzy: Boolean,
    ): List<AuthorMatch> {
        // Per typed word, the words of the names it matches: each word of the names compared once, not once a name
        val last = tokens.lastIndex
        val matching =
            tokens.mapIndexed { i, token ->
                token.readings.map { reading ->
                    reading.map { q -> authorVocabulary.filterTo(HashSet()) { w -> wordMatches(w, q, i == last, fuzzy) } }
                }
            }
        return authorNames.mapNotNull { (author, words) ->
            val all = matching.all { readings -> readings.any { reading -> reading.all { set -> words.any(set::contains) } } }
            if (!all) return@mapNotNull null
            val tier =
                when {
                    wordCount(tokens) == words.size && titleBegins(words, tokens) -> 2
                    titleBegins(words, tokens) -> 1
                    else -> 0
                }
            val alias = if (TitleText.words(author.name) == words) null else aliasText(author, words)
            AuthorMatch(author, alias, tier)
        }
    }

    private fun aliasText(
        author: TitleAuthor,
        words: List<String>,
    ): String? = author.aliases.firstOrNull { TitleText.words(it) == words }

    private fun List<QueryToken>.dropHonorific(): List<QueryToken> {
        val first = firstOrNull() ?: return this
        return if (size > 1 && first.isPlain && first.typed[0] in HONORIFICS) drop(1) else this
    }

    private fun wordCount(tokens: List<QueryToken>): Int = tokens.sumOf { it.typed.size }

    // The title's words are the query's, whole and no other, in any order (`אורח חיים שולחן ערוך`)
    private fun titleWhole(
        words: List<String>,
        tokens: List<QueryToken>,
    ): Boolean {
        val typed = tokens.flatMap { it.typed }
        return typed.size == words.size && assignable(words, typed, lastPrefix = false, fuzzy = false)
    }

    // Each typed word matches a word of its own among [words]: the last as begun ([lastPrefix]), or each of them ([begun])
    private fun assignable(
        words: List<String>,
        typed: List<String>,
        lastPrefix: Boolean,
        fuzzy: Boolean,
        begun: Boolean = false,
    ): Boolean {
        val used = BooleanArray(words.size)

        fun assign(i: Int): Boolean {
            if (i == typed.size) return true
            for (j in words.indices) {
                if (used[j] || !wordMatches(words[j], typed[i], begun || (lastPrefix && i == typed.lastIndex), fuzzy)) continue
                used[j] = true
                if (assign(i + 1)) return true
                used[j] = false
            }
            return false
        }
        return assign(0)
    }

    // The title's words begin with the query's, in order, the last as typed so far
    private fun titleBegins(
        words: List<String>,
        tokens: List<QueryToken>,
    ): Boolean {
        val typed = tokens.flatMap { it.typed }
        if (typed.size > words.size) return false
        return typed.indices.all { j -> wordMatches(words[j], typed[j], j == typed.lastIndex, fuzzy = false) }
    }

    // One word of a name against one typed: as typed, with or without the article, another spelling; or begun by it
    private fun wordMatches(
        word: String,
        typed: String,
        prefix: Boolean,
        fuzzy: Boolean,
    ): Boolean {
        val forms = TitleText.articleForms(typed)
        if (forms.any { it == word || (prefix && word.startsWith(it)) }) return true
        if (TitleText.spellingVariants(TitleText.skeleton(word), TitleText.skeleton(typed))) return true
        return fuzzy && typoMatch(word, typed, prefix)
    }

    private class Found(
        val all: BitSet,
        // Per token, the books that have it in their title
        val title: List<BitSet>,
        val fuzzy: Boolean,
        // Every typed word read as the start of a word, not the last alone
        val begun: Boolean,
    )

    // Every typed word found in a word of its own: the lookup read one word twice (`בבא ב` found תוספתא בבא קמא)
    private fun wordsApart(
        book: Int,
        tokens: List<QueryToken>,
        found: Found,
    ): Boolean {
        val typed = tokens.filter { it.isPlain && !isFiller(it, tokens) }.map { it.typed[0] }
        if (typed.size < 2) return true
        val lastPrefix = tokens.last().let { it.isPlain && !isFiller(it, tokens) }
        return assignable(allWords[book], typed, lastPrefix, found.fuzzy, found.begun)
    }

    private fun matchTokens(
        tokens: List<QueryToken>,
        fuzzy: Boolean,
        begun: Boolean = false,
    ): Found {
        val all = BitSet(books.size).apply { set(0, books.size) }
        val titles = ArrayList<BitSet>(tokens.size)
        for ((i, token) in tokens.withIndex()) {
            val any = BitSet(books.size)
            val title = BitSet(books.size)
            for (reading in token.readings) {
                // A reading's words all found: AND
                val found = reading.map { word -> wordBooks(word, prefix = begun || i == tokens.lastIndex, fuzzy = fuzzy) }
                found.map { it.first }.reduceOrNull { x, y -> x.apply { and(y) } }?.let(any::or)
                found.map { it.second }.reduceOrNull { x, y -> x.apply { and(y) } }?.let(title::or)
            }
            // `ספר`, `מסכת`: counted when in the title, never needed (`ספר הכוזרי`, `מסכת ברכות`)
            if (!isFiller(token, tokens)) all.and(any)
            titles += title
            if (all.isEmpty) break
        }
        return Found(all, titles, fuzzy, begun)
    }

    // The books with a word [typed] may stand for: anywhere, and in their title
    private fun wordBooks(
        typed: String,
        prefix: Boolean,
        fuzzy: Boolean,
    ): Pair<BitSet, BitSet> {
        val any = BitSet(books.size)
        val title = BitSet(books.size)
        for (id in wordIds(typed, prefix, fuzzy)) {
            inTitle[id].forEach {
                any.set(it)
                title.set(it)
            }
            elsewhere[id].forEach(any::set)
        }
        return any to title
    }

    // The words of the index [typed] may stand for
    private fun wordIds(
        typed: String,
        prefix: Boolean,
        fuzzy: Boolean,
    ): IntArray {
        val ids = LinkedHashSet<Int>()
        val forms = TitleText.articleForms(typed)
        for (form in forms) {
            if (prefix) {
                var at = lowerBound(form)
                while (at < vocabulary.size && vocabulary[at].startsWith(form)) ids += at++
            } else {
                val at = lowerBound(form)
                if (at < vocabulary.size && vocabulary[at] == form) ids += at
            }
        }
        val skeleton = TitleText.skeleton(typed)
        bySkeleton[skeleton.letters]?.forEach { id ->
            if (TitleText.spellingVariants(skeleton, TitleText.skeleton(vocabulary[id]))) ids += id
        }
        if (fuzzy && typed.length >= MIN_TYPO_LENGTH) {
            for (id in vocabulary.indices) if (typoMatch(vocabulary[id], typed, prefix)) ids += id
        }
        return ids.toIntArray()
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = vocabulary.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (vocabulary[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    // [word] is [typed] with a typo (one, or two in a long word): a letter missing, extra, wrong or two swapped
    private fun typoMatch(
        word: String,
        typed: String,
        prefix: Boolean,
    ): Boolean {
        if (typed.length < MIN_TYPO_LENGTH) return typed.length == MIN_TYPO_LENGTH - 1 && vowelLetterApart(word, typed)
        val max = if (typed.length >= LONG_WORD) 2 else 1
        if (editDistance(word, typed, max) <= max) return true
        if (!prefix || word.length <= typed.length) return false
        // Still being typed: against the word's beginning, one letter longer or shorter
        return (typed.length - 1..typed.length + 1).any { n ->
            n in 1..word.length && editDistance(word.substring(0, n), typed, max) <= max
        }
    }

    private fun isFiller(
        token: QueryToken,
        tokens: List<QueryToken>,
    ): Boolean = tokens.size > 1 && token.isPlain && token.typed[0] in FILLERS

    private fun acronymKeys(words: List<String>): List<String> =
        if (words.size < 2) listOf(words.joinToString("")) else listOf(words.joinToString(""), words.sorted().joinToString(" "))

    // A short word with a vowel letter more or less (`קרו` for קארו): one of א ו י, put in or left out
    private fun vowelLetterApart(
        word: String,
        typed: String,
    ): Boolean {
        val (short, long) = if (word.length < typed.length) word to typed else typed to word
        if (long.length != short.length + 1) return false
        return long.indices.any { i -> long[i] in VOWEL_LETTERS && long.removeRange(i, i + 1) == short }
    }

    companion object {
        const val TIER_WHOLE = 3
        const val TIER_BEGUN = 2

        // A category's books are its own and those of the categories right under it; deeper, the name is too wide (הלכה)
        private const val NAMED_CATEGORY_DEPTH = 2
        private val EMPTY = IntArray(0)
        private const val MIN_TYPO_LENGTH = 4
        private const val VOWEL_LETTERS = "אוי"
        private val FILLERS = setOf("ספר", "מסכת", "מס", "מסכתא")
        private const val LONG_WORD = 7
        private val HONORIFICS =
            setOf("רבי", "הרב", "רב", "ר", "רבנו", "רבינו", "הגאונ", "גאונ", "מרנ", "החכמ", "חכמ", "האדמור", "אדמור", "מורנו", "רבנ")

        // The two letters before [i] in [a] are those before [j] in [b], swapped
        private fun swapped(
            a: String,
            b: String,
            i: Int,
            j: Int,
        ): Boolean = i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]

        /** Optimal string alignment distance (a swap of two letters is one edit), or [max] + 1 when above it. */
        internal fun editDistance(
            a: String,
            b: String,
            max: Int,
        ): Int {
            if (kotlin.math.abs(a.length - b.length) > max) return max + 1
            var prev2 = IntArray(b.length + 1)
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (swapped(a, b, i, j)) v = minOf(v, prev2[j - 2] + 1)
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > max) return max + 1
                val t = prev2
                prev2 = prev
                prev = cur
                cur = t
            }
            return prev[b.length]
        }
    }
}

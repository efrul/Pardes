// Otzaria's lexical search, ported to Pardes's text index, to measure it in place of Pardes's (OTZARIA_LEXICAL.md):
//
//   OtzariaLexicalKt seforim.db.lucene lexical.db searches.tsv OUT_DIR
//
// Writes OUT_DIR/<mode>.jsonl, one line per search: {"id", "total", "fallback", "hits": [[lineId, score], ...]} (the
// first 1000), in the search's scope (searches.tsv: id, query, kind, scopeId, as FeedbackHistory.kt reads it): book, a
// category (its books and those of the categories under it), all, or auto, the scope the app searches with none
// chosen: the base books, and all the books when nothing is found there. The modes follow otzaria_search_engine
// (github.com/Otzaria/otzaria_search_engine, 8ef29fd, MIT), read and rewritten here, not run:
//   smart  the lexical half of Otzaria's smart (hybrid) search, kSmartSearchLexicalMode = fuzzy with
//          kSmartSearchFuzzyMaxDistance = 0 (build_lexical_fuzzy_query): every word, anywhere in the line, as typed or
//          as one of its dictionary forms (the same lexical.db, the first 32 forms); ranked by tiers that sum: the typed
//          form 1000 plus its BM25, a dictionary form 30, and 2000 for the typed words as a phrase (slop 1);
//   exact  the regular search with no word option: the typed words in a row (distance 0), by relevance (BM25);
//   gram   the same with "קידומות דקדוקיות" and "סיומות דקדוקיות" on every word (hebrew_query.rs: affix_arms,
//          suffix_sides);
//   all    the same with "כתיב מלא/חסר" too (generate_spelling_variations: every inner ו/י optional, ו/י added, up
//          to 16 forms).
// And one mode of Otzaria's pieces put together here, not Otzaria's:
//   smartspell  smart, with a word's כתיב מלא/חסר forms (up to 32, as Otzaria's advanced search makes them) as a third
//          tier (10, as ADVANCED_TIER_BOOSTS puts spelling variants), and the phrase bonus (2000) for the words in a row
//          in any of those forms (slop 1).
// Otzaria's index keeps final letters and its regular search shows its results in the library's order unless relevance
// is chosen; Pardes's index stores final letters as their base form (the patterns are mapped to it) and the test ranks
// by relevance, as the hybrid does.

import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.search.MagicDictionaryIndex
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper
import org.apache.lucene.queries.spans.SpanNearQuery
import org.apache.lucene.queries.spans.SpanQuery
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.ConstantScoreQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.PhraseQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.RegexpQuery
import org.apache.lucene.search.TermInSetQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import org.apache.lucene.util.automaton.RegExp
import java.io.File
import java.nio.file.Paths

private val GRAM_PREFIX_A = listOf("ו", "מ", "דא", "כש", "כ", "ב", "ש", "ל", "ה", "ד")
private val GRAM_PREFIX_B = listOf("כש", "כ", "ב", "ש", "ל", "ה", "ד")
private val GRAM_SUFFIXES = listOf(
    "ותי", "ותיך", "ותיו", "ותיה", "ותינו", "ותיכם", "ותיכן", "ותיהם", "ותיהן", "יות", "יי", "יך", "יו", "יה", "יא", "תא",
    "ינו", "יכם", "יכן", "יהם", "יהן", "י", "ך", "ו", "ה", "נו", "כם", "כן", "ם", "ן", "ים", "ות",
)
private val VERB_SUFFIXES = listOf("תי", "ת", "תם", "תן")
private val HE_STEM = listOf("ות", "ת")
private const val MIN_GRAM_ROOT_LETTERS = 3
private const val MAX_GRAMMATICAL_SPELLING_BRANCHES = 16
private const val MAX_SPELLING_BRANCHES = 32
private const val KEEP = 1000
private val FINAL_OF = mapOf('כ' to 'ך', 'מ' to 'ם', 'נ' to 'ן', 'פ' to 'ף', 'צ' to 'ץ')
private val BASE_OF = FINAL_OF.entries.associate { (b, f) -> f to b }

private fun group(alts: List<String>) = "(" + alts.joinToString("|") + ")"
private fun optional(alts: List<String>) = group(alts) + "?"
private fun alternation(parts: List<String>) = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    else -> "(" + parts.joinToString("|") + ")"
}

/** Lucene RegExp escaping of a literal (Hebrew letters pass; `"` would open a quoted string). */
private fun escape(s: String) = buildString { for (c in s) { if (c in "\\.?+*|{}[]()\"~@#&<>") append('\\'); append(c) } }

/** hebrew_query.rs generate_spelling_variations, line for line. */
fun spellingVariations(word: String, limit: Int): List<String> {
    if (word.isEmpty()) return listOf("")
    if (limit == 0) return emptyList()
    val chars = word.toCharArray()
    val n = chars.size
    val letters = chars.count { it != '\'' && it != '"' }
    data class Edit(val del: Int = -1, val gap: Int = -1, val letter: Char = ' ')
    val edits = mutableListOf<Edit>()
    for (i in 0 until n) {
        val c = chars[i]
        when (c) {
            'י', 'ו' -> {
                val runStart = i == 0 || chars[i - 1] != c
                if (runStart) {
                    var j = i
                    var at = -1
                    while (j < n && chars[j] == c) {
                        if (j > 0 && j + 1 < n) { at = j; break }
                        j++
                    }
                    if (at >= 0 && !(at + 2 == n && chars[n - 1] == 'ה')) edits += Edit(del = at)
                }
            }
            '\'', '"' -> edits += Edit(del = i)
        }
    }
    if (letters >= 4) {
        for (gap in 1 until n) { edits += Edit(gap = gap, letter = 'ו'); edits += Edit(gap = gap, letter = 'י') }
    } else if (letters == 3 && n == letters) {
        edits += Edit(gap = 1, letter = 'ו')
    }
    val e = edits.take(24)
    val m = e.size
    fun valid(idx: IntArray): Boolean {
        var yod = 0; var vav = 0; var deletedLetters = 0
        val deleted = BooleanArray(64)
        val gaps = mutableListOf<Int>(); val removed = mutableListOf<Char>(); val added = mutableListOf<Char>()
        for (i in idx) {
            val ed = e[i]
            if (ed.del >= 0) {
                deleted[ed.del] = true; removed += chars[ed.del]
                if (chars[ed.del] == 'י') yod++ else if (chars[ed.del] == 'ו') vav++
                if (chars[ed.del] != '\'' && chars[ed.del] != '"') deletedLetters++
            } else {
                gaps += ed.gap; added += ed.letter
                if (ed.letter == 'י') yod++ else vav++
            }
        }
        if (yod > 3 || vav > 3) return false
        if (removed.any { it in added }) return false
        if (letters - deletedLetters < 3) return false
        return gaps.zipWithNext().all { (g1, g2) -> g1 != g2 && !(g1 until g2).all { deleted[it] } }
    }
    fun apply(idx: IntArray): String {
        val deleted = BooleanArray(64)
        val inserts = mutableListOf<Pair<Int, Char>>()
        for (i in idx) { val ed = e[i]; if (ed.del >= 0) deleted[ed.del] = true else inserts += ed.gap to ed.letter }
        return buildString {
            for (i in 0..n) {
                for ((g, l) in inserts) if (g == i) append(l)
                if (i < n && !deleted[i]) append(chars[i])
            }
        }
    }
    if (n >= 64) return listOf(word)
    val out = LinkedHashSet<String>().apply { add(word) }
    val maxK = minOf(m, 8)
    sizes@ for (k in 1..maxK) {
        if (out.size >= limit) break
        val idx = IntArray(k) { it }
        while (true) {
            if (valid(idx)) { out += apply(idx); if (out.size >= limit) break@sizes }
            var i = k
            while (true) {
                if (i == 0) continue@sizes
                i--
                if (idx[i] < m - k + i) { idx[i]++; for (j in i + 1 until k) idx[j] = idx[j - 1] + 1; break }
            }
        }
    }
    return out.toList()
}

/** hebrew_query.rs affix_arms + suffix_sides for the grammatical options: (lead, core, trail). */
private fun affixArms(root: String, gramPrefix: Boolean, gramSuffix: Boolean): List<Triple<String, String, String>> {
    val letters = root.length
    if (letters == 0) return emptyList()
    val abbreviation = root.contains('\'') || root.contains('"')
    val suffix = gramSuffix && (abbreviation || letters >= MIN_GRAM_ROOT_LETTERS)
    val lead = if (gramPrefix && (abbreviation || letters >= MIN_GRAM_ROOT_LETTERS)) {
        optional(GRAM_PREFIX_A) + optional(GRAM_PREFIX_B) + optional(listOf("ה"))
    } else ""
    if (!suffix) return listOf(Triple(lead, root, ""))
    val suffixes = GRAM_SUFFIXES.toMutableList()
    val last = root.last()
    if (letters in 3..4 && !abbreviation && last != 'ה' && last != 'ת') suffixes += VERB_SUFFIXES
    BASE_OF[last]?.let { base ->
        return listOf(Triple(lead, root, ""), Triple(lead, root.dropLast(1) + base, group(suffixes)))
    }
    if (last == 'ה') return listOf(Triple(lead, root, ""), Triple(lead, root.dropLast(1), group(HE_STEM) + optional(suffixes)))
    return listOf(Triple(lead, root, optional(suffixes)))
}

/** hebrew_query.rs word_to_pattern, in Lucene's RegExp syntax and the index's letters (finals as base forms). */
fun wordPattern(root: String, gramPrefix: Boolean, gramSuffix: Boolean, spelling: Boolean): String {
    val forms = if (spelling) {
        spellingVariations(root, if (gramPrefix || gramSuffix) MAX_GRAMMATICAL_SPELLING_BRANCHES else MAX_SPELLING_BRANCHES)
    } else listOf(root)
    val branches = LinkedHashSet<String>()
    for (form in forms) for ((lead, core, trail) in affixArms(form, gramPrefix, gramSuffix)) branches += lead + escape(core) + trail
    return alternation(branches.toList()).map { BASE_OF[it] ?: it }.joinToString("")
}

private fun tokens(analyzer: StandardAnalyzer, text: String): List<String> {
    val out = mutableListOf<String>()
    analyzer.tokenStream("text", HebrewTextUtils.normalizeHebrew(text)).use { ts ->
        val term = ts.addAttribute(CharTermAttribute::class.java)
        ts.reset()
        while (ts.incrementToken()) out += term.toString()
        ts.end()
    }
    return out
}

/** A token as typed: its last letter in final form, as Otzaria's query words keep it. */
private fun typed(token: String) = token.dropLast(1) + (FINAL_OF[token.last()] ?: token.last())

fun main(args: Array<String>) {
    val (indexDir, lexical, searchesTsv, outDir) = args
    IndexSearcher.setMaxClauseCount(1 shl 20)
    val reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)))
    val searcher = IndexSearcher(reader)
    val analyzer = StandardAnalyzer()
    val dict = MagicDictionaryIndex.load(HebrewTextUtils::normalizeHebrew, Paths.get(lexical)) ?: error("no dictionary at $lexical")
    val line = TermQuery(Term("type", "line"))
    val base = IntPoint.newExactQuery("is_base_book", 1)
    fun scopeOf(kind: String, scopeId: Int?): Query? = when (kind) {
        "book" -> IntPoint.newExactQuery("book_id", scopeId!!)
        "cat" -> BooleanQuery.Builder()
            .add(IntPoint.newExactQuery("category_id", scopeId!!), Occur.SHOULD)
            .add(IntPoint.newExactQuery("ancestor_category_ids", scopeId), Occur.SHOULD).build()
        "all" -> null
        else -> base
    }

    fun forms(token: String): List<String> {
        val ex = dict.expansionsFor(listOf(token))
        val ordered = LinkedHashSet<String>()
        ordered += token
        ex.flatMap { it.base }.forEach { ordered += it }
        ex.flatMap { it.surface + it.variants }.forEach { ordered += it }
        return ordered.map(HebrewTextUtils::normalizeHebrew).filter { it.isNotEmpty() && ' ' !in it }.distinct().take(32)
    }

    fun smart(words: List<String>): Query {
        val b = BooleanQuery.Builder()
        for (t in words) {
            val tier = BooleanQuery.Builder()
                .add(BoostQuery(ConstantScoreQuery(TermQuery(Term("text", t))), 1000f), Occur.SHOULD)
                .add(TermQuery(Term("text", t)), Occur.SHOULD)
            val others = forms(t).filter { it != t }
            if (others.isNotEmpty()) {
                tier.add(BoostQuery(ConstantScoreQuery(TermInSetQuery("text", others.map { BytesRef(it) })), 30f), Occur.SHOULD)
            }
            b.add(tier.build(), Occur.MUST)
        }
        if (words.size >= 2) b.add(BoostQuery(ConstantScoreQuery(PhraseQuery(1, "text", *words.toTypedArray())), 2000f), Occur.SHOULD)
        return b.build()
    }

    fun spellingQuery(w: String) = RegexpQuery(Term("text", wordPattern(typed(w), false, false, true)), RegExp.NONE, 1_000_000)

    fun smartSpell(words: List<String>): Query {
        val b = BooleanQuery.Builder()
        for (t in words) {
            val tier = BooleanQuery.Builder()
                .add(BoostQuery(ConstantScoreQuery(TermQuery(Term("text", t))), 1000f), Occur.SHOULD)
                .add(TermQuery(Term("text", t)), Occur.SHOULD)
                .add(BoostQuery(ConstantScoreQuery(spellingQuery(t)), 10f), Occur.SHOULD)
            val others = forms(t).filter { it != t }
            if (others.isNotEmpty()) {
                tier.add(BoostQuery(ConstantScoreQuery(TermInSetQuery("text", others.map { BytesRef(it) })), 30f), Occur.SHOULD)
            }
            b.add(tier.build(), Occur.MUST)
        }
        if (words.size >= 2) {
            val spans = words.map { w ->
                SpanMultiTermQueryWrapper(spellingQuery(w)).apply { setRewriteMethod(SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite(1024)) }
            }
            b.add(BoostQuery(ConstantScoreQuery(SpanNearQuery(spans.toTypedArray<SpanQuery>(), 1, true)), 2000f), Occur.SHOULD)
        }
        return b.build()
    }

    fun advanced(words: List<String>, gram: Boolean, spelling: Boolean): Query {
        if (!gram && !spelling) return if (words.size == 1) TermQuery(Term("text", words[0])) else PhraseQuery(0, "text", *words.toTypedArray())
        val spans = words.map { w ->
            val q = RegexpQuery(Term("text", wordPattern(typed(w), gram, gram, spelling)), RegExp.NONE, 1_000_000)
            SpanMultiTermQueryWrapper(q).apply { setRewriteMethod(SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite(1024)) }
        }
        return if (spans.size == 1) spans[0] else SpanNearQuery(spans.toTypedArray<SpanQuery>(), 0, true)
    }

    val modes: Map<String, (List<String>) -> Query> = linkedMapOf(
        "smart" to ::smart,
        "exact" to { w -> advanced(w, gram = false, spelling = false) },
        "gram" to { w -> advanced(w, gram = true, spelling = false) },
        "all" to { w -> advanced(w, gram = true, spelling = true) },
        "smartspell" to ::smartSpell,
    )
    File(outDir).mkdirs()
    val writers = modes.keys.associateWith { File(outDir, "$it.jsonl").bufferedWriter() }
    val rows = File(searchesTsv).readLines().drop(1).map { it.split('\t') }
    for (r in rows) {
        val id = r[0]
        val words = tokens(analyzer, r[1])
        val kind = r.getOrElse(2) { "auto" }
        val scope = scopeOf(kind, r.getOrNull(3)?.toIntOrNull())
        for ((mode, build) in modes) {
            val q = build(words)
            fun run(scope: Query?): Pair<Int, List<Pair<Long, Float>>> {
                val b = BooleanQuery.Builder().add(q, Occur.MUST).add(line, Occur.FILTER)
                if (scope != null) b.add(scope, Occur.FILTER)
                val full = b.build()
                val top = searcher.search(full, KEEP)
                val stored = searcher.storedFields()
                return searcher.count(full) to top.scoreDocs.map { stored.document(it.doc).getField("line_id").numericValue().toLong() to it.score }
            }
            var (total, hits) = run(scope)
            val fallback = total == 0 && kind == "auto"
            if (fallback) run(null).let { total = it.first; hits = it.second }
            writers.getValue(mode).apply {
                write("{\"id\":$id,\"total\":$total,\"fallback\":$fallback,\"hits\":[" + hits.joinToString(",") { "[${it.first},${it.second}]" } + "]}\n")
            }
        }
        if (id.toInt() % 20 == 0) System.err.println("$id ${words.joinToString(" ")}")
    }
    writers.values.forEach { it.close() }
    reader.close()
}

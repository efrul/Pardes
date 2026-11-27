package io.github.kdroidfilter.seforimapp.features.sefaria

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import io.github.kdroidfilter.seforimapp.framework.search.titles.BookTitleIndex
import io.github.kdroidfilter.seforimapp.framework.search.titles.TitleAuthor
import io.github.kdroidfilter.seforimapp.framework.search.titles.TitleText
import java.util.zip.GZIPInputStream

// Sefaria's topics, the persons its texts mention and its passages, as scripts/sefaria_content.py packs them under
// resources/sefaria/ (their rows are described there): read on first use, off the UI thread, and kept.

/** A topic of Sefaria's with a Hebrew [name]: its [others] names and its Hebrew [description], empty when it has none. */
@Immutable
data class Topic(
    val id: Int,
    val name: String,
    val others: List<String>,
    val description: String,
)

/** How Sefaria relates one topic to another (its intra-topic link types, as the R rows name them). */
enum class TopicRelation(
    val code: String,
) {
    UNDER("under"),
    IS_A("isa"),
    RELATED("related"),
    SIMILAR("similar"),
    CONTAINS("contains"),
    PRECEDES("precedes"),
    DEPENDS("depends"),
}

/** A topic's neighbours on its page, each list in Sefaria's order: what it is a kind of, its kinds, what it is related to. */
@Immutable
data class TopicNeighbours(
    val shownUnder: List<Topic> = emptyList(),
    val broader: List<Topic> = emptyList(),
    val narrower: List<Topic> = emptyList(),
    val contains: List<Topic> = emptyList(),
    val dependents: List<Topic> = emptyList(),
    val around: List<Topic> = emptyList(),
    val related: List<Topic> = emptyList(),
)

/**
 * Sefaria's topics: their [topics], the [roots] of the tree they are shown in, the lines each is about ([sourcesOf], in
 * Sefaria's order) and the topics of a line ([of]).
 */
class SefariaTopics(
    val topics: Map<Int, Topic>,
    val roots: List<Int>,
    private val out: Map<Int, List<Pair<TopicRelation, Int>>>,
    private val into: Map<Int, List<Pair<TopicRelation, Int>>>,
    private val byLine: Map<String, IntArray>,
    private val sources: Map<Int, List<String>>,
) {
    // A topic by its names as typed (searchKey), for the search's panel
    private val byName: Map<String, Topic> by lazy {
        buildMap {
            topics.values.sortedByDescending { sourceCount(it.id) }.forEach { topic ->
                (listOf(topic.name) + topic.others).forEach { putIfAbsent(it.searchKey(), topic) }
            }
        }
    }

    /** The topics the line [heRef] is about, the ones with the most sources first. */
    fun of(heRef: String): List<Topic> =
        byLine[heRef]
            ?.asList()
            ?.mapNotNull { topics[it] }
            ?.sortedByDescending { sourceCount(it.id) }
            .orEmpty()

    /** The first lines of topic [id]'s sources, in Sefaria's order (the curated first, then by its rank). */
    fun sourcesOf(id: Int): List<String> = sources[id].orEmpty()

    fun sourceCount(id: Int): Int = sources[id]?.size ?: 0

    /** The topics shown under [id] in the tree, the ones with the most sources first. */
    fun children(id: Int): List<Topic> = into.related(id, TopicRelation.UNDER).sortedByDescending { sourceCount(it.id) }

    /** Topic [id]'s neighbours, for its page. */
    fun neighbours(id: Int): TopicNeighbours =
        TopicNeighbours(
            shownUnder = out.related(id, TopicRelation.UNDER),
            broader = out.related(id, TopicRelation.IS_A),
            narrower = into.related(id, TopicRelation.IS_A),
            contains = out.related(id, TopicRelation.CONTAINS) + into.related(id, TopicRelation.CONTAINS),
            dependents = into.related(id, TopicRelation.DEPENDS),
            around = into.related(id, TopicRelation.PRECEDES) + out.related(id, TopicRelation.PRECEDES),
            related =
                (
                    out.related(id, TopicRelation.RELATED) + into.related(id, TopicRelation.RELATED) +
                        out.related(id, TopicRelation.SIMILAR) + into.related(id, TopicRelation.SIMILAR)
                ).distinct(),
        )

    /**
     * The topic named [query]: its name or another of its names typed whole, as typed or in another spelling, with or
     * without the article, its words in any order; the one with the most sources first. Never a typo: a search that names
     * no topic (שבט) shows none.
     */
    fun named(query: String): Topic? {
        val key = query.searchKey().takeIf { it.isNotEmpty() } ?: return null
        return byName[key] ?: names.searchAuthors(query, NAMED_CANDIDATES).firstNotNullOfOrNull { match ->
            topics[match.author.id.toInt()]?.takeIf { topic -> (listOf(topic.name) + topic.others).any { sameWords(it, query) } }
        }
    }

    /** The topics with sources whose name starts with [letter], by their name: a page of the index. */
    fun startingWith(letter: Char): List<Topic> =
        topics.values
            .filter { sourceCount(it.id) > 0 && it.name.searchKey().firstOrNull() == letter }
            .sortedBy { it.name.searchKey() }

    // The topics with sources found by name as the bar finds books and authors (BookTitleIndex): in another spelling,
    // with a typo, the words in another order, with or without the article, a leading רבי or הרב not needed
    private val names: BookTitleIndex by lazy {
        BookTitleIndex(
            emptyList(),
            topics.values.filter { sourceCount(it.id) > 0 }.map { TitleAuthor(it.id.toLong(), it.name, it.others, sourceCount(it.id)) },
        )
    }

    /** The topics with sources named by [query], as the bar finds authors: its name typed whole first, then the most sourced. */
    fun search(
        query: String,
        limit: Int,
    ): List<Topic> = names.searchAuthors(query, limit).mapNotNull { topics[it.author.id.toInt()] }

    private fun Map<Int, List<Pair<TopicRelation, Int>>>.related(
        id: Int,
        kind: TopicRelation,
    ): List<Topic> = this[id].orEmpty().filter { it.first == kind }.mapNotNull { topics[it.second] }

    companion object {
        private const val RESOURCE = "/sefaria/topics.tsv.gz"
        private const val NAMED_CANDIDATES = 5

        /** The topics packed in the app; empty when the resource can't be read. */
        val shipped: SefariaTopics by lazy { readResource(RESOURCE, ::parse) ?: parse(emptySequence()) }

        fun parse(rows: Sequence<String>): SefariaTopics {
            val topics = HashMap<Int, Topic>()
            val roots = ArrayList<Pair<Int, Int>>()
            val out = HashMap<Int, MutableList<Pair<TopicRelation, Int>>>()
            val into = HashMap<Int, MutableList<Pair<TopicRelation, Int>>>()
            val byLine = HashMap<String, MutableList<Int>>()
            val sources = HashMap<Int, MutableList<String>>()
            val kinds = TopicRelation.entries.associateBy { it.code }
            val refs = HashMap<String, String>()
            for (row in rows) {
                val f = row.split('\t')
                when (f[0]) {
                    "T" -> topics[f[1].toInt()] = Topic(f[1].toInt(), f[2], f.names(3), f.getOrElse(4) { "" })
                    "O" -> roots += f[1].toInt() to f[2].toInt()
                    "R" ->
                        kinds[f[2]]?.let { kind ->
                            out.getOrPut(f[1].toInt()) { mutableListOf() } += kind to f[3].toInt()
                            into.getOrPut(f[3].toInt()) { mutableListOf() } += kind to f[1].toInt()
                        }
                    "A" -> {
                        // A line of several topics is one string
                        val heRef = refs.getOrPut(f[2]) { f[2] }
                        val topic = f[1].toInt()
                        byLine.getOrPut(heRef) { mutableListOf() } += topic
                        if (f[4] == "1") sources.getOrPut(topic) { mutableListOf() } += heRef
                    }
                }
            }
            return SefariaTopics(
                topics,
                roots.sortedBy { it.second }.map { it.first }.filter { it in topics },
                out,
                into,
                byLine.mapValues { it.value.distinct().toIntArray() },
                sources,
            )
        }
    }
}

/**
 * A person Sefaria finds mentioned in the library's lines: its Hebrew [name], its [others] names, its [description],
 * its [generation] as Sefaria codes it (T3: the Tannaim's third, A5/A6: the Amoraim's fifth or sixth, Z: the Zugot) and
 * its Hebrew Wikipedia page ([wiki]).
 */
@Immutable
data class Sage(
    val id: Int,
    val name: String,
    val others: List<String>,
    val description: String,
    val generation: String,
    val wiki: String,
)

/** A name Sefaria could not tell between persons ([options]): רבן גמליאל, of Yavneh, the elder or Rabbi's son. */
@Immutable
data class AmbiguousSage(
    val id: Int,
    val name: String,
    val options: List<Int>,
)

/** A mention of [sageId] in a line, its name as the line writes it ([text]): empty when found only in a translation. */
@Immutable
data class Mention(
    val sageId: Int,
    val text: String,
)

/** How Sefaria relates one person to another. */
enum class SageRelation(
    val code: String,
) {
    TAUGHT("taught"),
    CHILD("child"),
    MEMBER("member"),
}

/** The persons the library's lines mention: [sages], [ambiguous] names, the mentions of a line ([at]) and a person's lines. */
class SefariaSages(
    val sages: Map<Int, Sage>,
    val ambiguous: Map<Int, AmbiguousSage>,
    private val out: Map<Int, List<Pair<SageRelation, Int>>>,
    private val into: Map<Int, List<Pair<SageRelation, Int>>>,
    private val byLine: Map<String, List<Mention>>,
    private val places: Map<Int, List<String>>,
) {
    /** The mentions in the line [heRef]. */
    fun at(heRef: String): List<Mention> = byLine[heRef].orEmpty()

    /** The lines that mention [id], a line once. */
    fun placesOf(id: Int): List<String> = places[id].orEmpty()

    // A person by its names as typed (searchKey), for the search's area, as the topics' byName
    private val byName: Map<String, Sage> by lazy {
        buildMap {
            sages.values.sortedByDescending { placesOf(it.id).size }.forEach { sage ->
                (listOf(sage.name) + sage.others).forEach { putIfAbsent(it.searchKey(), sage) }
            }
        }
    }

    /** The person named [query] (its name or another of its names, as typed), the most mentioned first. */
    fun named(query: String): Sage? = query.searchKey().takeIf { it.isNotEmpty() }?.let(byName::get)

    /** Those [id] taught, those who taught [id], [id]'s parents and children, and the groups [id] belongs to. */
    fun students(id: Int): List<Sage> = out.related(id, SageRelation.TAUGHT)

    fun teachers(id: Int): List<Sage> = into.related(id, SageRelation.TAUGHT)

    fun family(id: Int): List<Sage> = out.related(id, SageRelation.CHILD) + into.related(id, SageRelation.CHILD)

    fun groups(id: Int): List<Sage> = out.related(id, SageRelation.MEMBER)

    // The persons found by name as the bar finds authors (BookTitleIndex): another spelling, a typo, the words in another
    // order, a leading רבי or הרב not needed
    private val names: BookTitleIndex by lazy {
        BookTitleIndex(emptyList(), sages.values.map { TitleAuthor(it.id.toLong(), it.name, it.others, placesOf(it.id).size) })
    }

    /** The persons named by [query], as the bar finds authors; all of them when it is blank. The most mentioned first. */
    fun search(
        query: String,
        limit: Int,
    ): List<Sage> =
        if (query.isBlank()) {
            sages.values.sortedByDescending { placesOf(it.id).size }.take(limit)
        } else {
            names.searchAuthors(query, limit).mapNotNull { sages[it.author.id.toInt()] }
        }

    private fun Map<Int, List<Pair<SageRelation, Int>>>.related(
        id: Int,
        kind: SageRelation,
    ): List<Sage> = this[id].orEmpty().filter { it.first == kind }.mapNotNull { sages[it.second] }

    companion object {
        private const val RESOURCE = "/sefaria/sages.tsv.gz"

        val shipped: SefariaSages by lazy { readResource(RESOURCE, ::parse) ?: parse(emptySequence()) }

        fun parse(rows: Sequence<String>): SefariaSages {
            val sages = HashMap<Int, Sage>()
            val ambiguous = HashMap<Int, AmbiguousSage>()
            val out = HashMap<Int, MutableList<Pair<SageRelation, Int>>>()
            val into = HashMap<Int, MutableList<Pair<SageRelation, Int>>>()
            val byLine = HashMap<String, MutableList<Mention>>()
            val places = HashMap<Int, MutableList<String>>()
            val kinds = SageRelation.entries.associateBy { it.code }
            for (row in rows) {
                val f = row.split('\t')
                when (f[0]) {
                    "P" ->
                        sages[f[1].toInt()] =
                            Sage(f[1].toInt(), f[2], f.names(3), f.getOrElse(4) { "" }, f.getOrElse(5) { "" }, f.getOrElse(6) { "" })
                    "W" ->
                        ambiguous[f[1].toInt()] =
                            AmbiguousSage(f[1].toInt(), f[2], f.getOrElse(3) { "" }.split('|').mapNotNull(String::toIntOrNull))
                    "R" ->
                        kinds[f[2]]?.let { kind ->
                            out.getOrPut(f[1].toInt()) { mutableListOf() } += kind to f[3].toInt()
                            into.getOrPut(f[3].toInt()) { mutableListOf() } += kind to f[1].toInt()
                        }
                    "M" -> {
                        val sage = f[2].toInt()
                        byLine.getOrPut(f[1]) { mutableListOf() } += Mention(sage, f.getOrElse(3) { "" })
                        val lines = places.getOrPut(sage) { mutableListOf() }
                        if (lines.lastOrNull() != f[1]) lines += f[1]
                    }
                }
            }
            return SefariaSages(sages, ambiguous, out, into, byLine, places)
        }
    }
}

/** A passage of Sefaria's: [kind] S a sugya, M a Mishnah in the Gemara, B a story of the Tanakh; [lines] in their order. */
@Immutable
data class Sugya(
    val kind: Char,
    val source: String,
    val lines: List<String>,
)

/** Sefaria's passages, by each of their lines. */
class SefariaSugyot(
    private val byLine: Map<String, Sugya>,
) {
    /** The passage the line [heRef] is in: a sugya rather than the Mishnah it starts with. */
    fun of(heRef: String): Sugya? = byLine[heRef]

    companion object {
        private const val RESOURCE = "/sefaria/sugyot.tsv.gz"

        private val read = lazy { readResource(RESOURCE, ::parse) ?: parse(emptySequence()) }

        val shipped: SefariaSugyot by read

        /** The passages once read, null before: for a menu, which must not wait for them. */
        fun ifRead(): SefariaSugyot? = if (read.isInitialized()) shipped else null

        fun parse(rows: Sequence<String>): SefariaSugyot {
            val byLine = HashMap<String, Sugya>()
            for (row in rows) {
                val f = row.split('\t')
                if (f[0] != "S" || f.size < 4) continue
                val sugya = Sugya(f[1].firstOrNull() ?: 'S', f[2], f[3].split('|'))
                // A sugya over the Mishnah it shares lines with
                sugya.lines.forEach { line -> if (sugya.kind == 'S' || line !in byLine) byLine[line] = sugya }
            }
            return SefariaSugyot(byLine)
        }
    }
}

/** Krupnik's dictionary of the Talmud: each headword (and each other headword) with its text, in Sefaria's order. */
object KrupnikDictionary {
    private const val RESOURCE = "/sefaria/krupnik.tsv.gz"

    val shipped: List<Pair<String, String>> by lazy { readResource(RESOURCE, ::parse).orEmpty() }

    fun parse(rows: Sequence<String>): List<Pair<String, String>> =
        rows
            .map { it.split('\t') }
            .filter { it[0] == "K" && it.size >= 4 }
            .flatMap { f -> (listOf(f[1]) + f.names(2)).map { it to f[3] } }
            .toList()
}

private fun List<String>.names(index: Int): List<String> = getOrElse(index) { "" }.split('|').filter { it.isNotEmpty() }

// The words of [name] are those of [query], each as typed, in another spelling or with the article, in any order
private fun sameWords(
    name: String,
    query: String,
): Boolean {
    val left = TitleText.words(name).toMutableList()
    val typed = TitleText.words(query)
    if (left.size != typed.size) return false
    return typed.all { q ->
        val word =
            left.firstOrNull { w ->
                q in TitleText.articleForms(w) ||
                    TitleText.spellingVariants(TitleText.skeleton(w), TitleText.skeleton(q))
            }
        word != null && left.remove(word)
    }
}

private fun <T> readResource(
    path: String,
    parse: (Sequence<String>) -> T,
): T? =
    SefariaTopics::class.java.getResourceAsStream(path)?.use { stream ->
        GZIPInputStream(stream).bufferedReader().useLines(parse)
    }

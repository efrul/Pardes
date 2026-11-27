package io.github.kdroidfilter.seforimapp.features.sefaria

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import io.github.kdroidfilter.seforimapp.features.sefaria.pack.SefariaPack
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.search.titles.BookTitleIndex
import io.github.kdroidfilter.seforimapp.framework.search.titles.TitleAuthor
import io.github.kdroidfilter.seforimapp.framework.search.titles.TitleText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

// Sefaria's topics, the persons its texts mention and its passages, read from their file beside the library
// (SefariaDb, SefariaPack: its tables are built from the rows scripts/sefaria_content.py writes under sefaria-data/):
// the topics and the persons held once read, a line's topics and mentions read by its heRef, off the UI thread.

/**
 * Pardes: Sefaria's data of the library's folder, its file opened on first use and kept until it is put in place again,
 * replaced or taken out ([close], [changed]); none when the folder has no file of it (SefariaPack offers it).
 */
object SefariaData {
    /** The app's settings, given once the app's graph is made (main.kt): where the library is. */
    @Volatile
    var appSettings: AppSettings? = null

    private val _revision = MutableStateFlow(0)

    /** Bumped when the file is put in place, replaced or taken out: the screens that show its data read it again. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var opened: Opened? = null

    // Where the data's file is: beside the library; a test puts its own
    @Volatile
    internal var locate: () -> File? = { appSettings?.let { fileIn(LibraryLocation.currentDir(it)) } }

    /** The data of one file: its persons and topics read once, a line's read by its heRef. */
    class Opened internal constructor(
        internal val file: File,
        internal val db: SefariaDb,
    ) {
        val topics: SefariaTopics by lazy { SefariaTopics.read(db) }
        val sages: SefariaSages by lazy { SefariaSages.read(db) }
        val sugyot: SefariaSugyot = SefariaSugyot(db)

        /** The pack the file is (SefariaPack), 0 when it doesn't say. */
        val pack: Int get() = db.pack
    }

    /** The data file in the library's folder [dir]. */
    fun fileIn(dir: File): File = File(File(dir, SefariaPack.DIR_NAME), SefariaPack.DB_NAME)

    /** The data in use, its file opened if it wasn't; null when the library's folder has none. Reads the disk: off the UI thread. */
    @Synchronized
    fun current(): Opened? {
        val file = locate() ?: return null
        opened?.let { if (it.file == file && file.isFile) return it }
        close()
        return SefariaDb.open(file)?.let { Opened(file, it) }.also { opened = it }
    }

    /** The data once opened, null before: for a menu, which must not wait for it. */
    fun ifOpen(): Opened? = opened

    /** Closes the file, before it is replaced or taken out: the next [current] opens it again. */
    @Synchronized
    fun close() {
        opened?.db?.close()
        opened = null
    }

    /** Tells the screens that show the data to read it again: its file was put in place, replaced or taken out. */
    fun changed() {
        _revision.update { it + 1 }
    }
}

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
class SefariaTopics internal constructor(
    val topics: Map<Int, Topic>,
    val roots: List<Int>,
    private val out: Map<Int, List<Pair<TopicRelation, Int>>>,
    private val into: Map<Int, List<Pair<TopicRelation, Int>>>,
    private val counts: Map<Int, Int>,
    private val db: SefariaDb,
) {
    // A topic by its names as typed (searchKey), for the search's panel
    private val byName: Map<String, Topic> by lazy {
        buildMap {
            topics.values.sortedByDescending { sourceCount(it.id) }.forEach { topic ->
                (listOf(topic.name) + topic.others).forEach { putIfAbsent(it.searchKey(), topic) }
            }
        }
    }

    /** The topics the line [heRef] is about, the ones with the most sources first. Reads the file. */
    fun of(heRef: String): List<Topic> =
        db
            .rows(LINE_TOPICS, heRef) { it.getInt(1) }
            .distinct()
            .mapNotNull { topics[it] }
            .sortedByDescending { sourceCount(it.id) }

    /** The first lines of topic [id]'s sources, in Sefaria's order (the curated first, then by its rank). Reads the file. */
    fun sourcesOf(id: Int): List<String> = db.rows(TOPIC_SOURCES, id) { it.getString(1) }

    fun sourceCount(id: Int): Int = counts[id] ?: 0

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
        private const val NAMED_CANDIDATES = 5

        // A line's topics, every one of its rows (a source of its own or a line of a source's range), in Sefaria's order
        private const val LINE_TOPICS = "SELECT t.topic FROM topic_line t JOIN line l ON l.id = t.line WHERE l.heref = ? ORDER BY t.ord"

        // A topic's sources, in Sefaria's order
        private const val TOPIC_SOURCES =
            "SELECT l.heref FROM topic_line t JOIN line l ON l.id = t.line WHERE t.topic = ? AND t.source = 1 ORDER BY t.ord"

        /** The topics of [db]: their table, the tree's roots and their relations read whole, their lines left in the file. */
        internal fun read(db: SefariaDb): SefariaTopics {
            val counts = HashMap<Int, Int>()
            val topics =
                db
                    .rows("SELECT id, name, others, description, sources FROM topic") { row ->
                        counts[row.getInt(1)] = row.getInt(5)
                        Topic(row.getInt(1), row.getString(2), row.getString(3).names(), row.getString(4))
                    }.associateBy { it.id }
            val kinds = TopicRelation.entries.associateBy { it.code }
            val out = HashMap<Int, MutableList<Pair<TopicRelation, Int>>>()
            val into = HashMap<Int, MutableList<Pair<TopicRelation, Int>>>()
            db.rows("SELECT src, kind, dst FROM topic_relation ORDER BY ord") { row ->
                kinds[row.getString(2)]?.let { kind ->
                    out.getOrPut(row.getInt(1)) { mutableListOf() } += kind to row.getInt(3)
                    into.getOrPut(row.getInt(3)) { mutableListOf() } += kind to row.getInt(1)
                }
            }
            val roots = db.rows("SELECT topic FROM topic_root ORDER BY ord") { it.getInt(1) }.filter { it in topics }
            return SefariaTopics(topics, roots, out, into, counts, db)
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
class SefariaSages internal constructor(
    val sages: Map<Int, Sage>,
    val ambiguous: Map<Int, AmbiguousSage>,
    private val out: Map<Int, List<Pair<SageRelation, Int>>>,
    private val into: Map<Int, List<Pair<SageRelation, Int>>>,
    private val counts: Map<Int, Int>,
    private val db: SefariaDb,
) {
    /** The mentions in the line [heRef]. Reads the file. */
    fun at(heRef: String): List<Mention> = db.rows(LINE_MENTIONS, heRef) { Mention(it.getInt(1), it.getString(2)) }

    /** The lines that mention [id], a line once, in their order. Reads the file. */
    fun placesOf(id: Int): List<String> = db.rows(SAGE_PLACES, id) { it.getString(1) }

    /** How many lines mention [id]. */
    fun placeCount(id: Int): Int = counts[id] ?: 0

    // A person by its names as typed (searchKey), for the search's area, as the topics' byName
    private val byName: Map<String, Sage> by lazy {
        buildMap {
            sages.values.sortedByDescending { placeCount(it.id) }.forEach { sage ->
                (listOf(sage.name) + sage.others).forEach { putIfAbsent(it.searchKey(), sage) }
            }
        }
    }

    /**
     * The person named [query], for the search's area as the topics' [SefariaTopics.named]: its name or another of its
     * names typed whole, as typed or in another spelling, with or without the article, its words in any order; the most
     * mentioned first. Never a typo.
     */
    fun named(query: String): Sage? {
        val key = query.searchKey().takeIf { it.isNotEmpty() } ?: return null
        return byName[key] ?: names.searchAuthors(query, NAMED_CANDIDATES).firstNotNullOfOrNull { match ->
            sages[match.author.id.toInt()]?.takeIf { sage -> (listOf(sage.name) + sage.others).any { sameWords(it, query) } }
        }
    }

    /** Those [id] taught, those who taught [id], [id]'s parents and children, and the groups [id] belongs to. */
    fun students(id: Int): List<Sage> = out.related(id, SageRelation.TAUGHT)

    fun teachers(id: Int): List<Sage> = into.related(id, SageRelation.TAUGHT)

    fun family(id: Int): List<Sage> = out.related(id, SageRelation.CHILD) + into.related(id, SageRelation.CHILD)

    fun groups(id: Int): List<Sage> = out.related(id, SageRelation.MEMBER)

    // The persons found by name as the bar finds authors (BookTitleIndex): another spelling, a typo, the words in another
    // order, a leading רבי or הרב not needed
    private val names: BookTitleIndex by lazy {
        BookTitleIndex(emptyList(), sages.values.map { TitleAuthor(it.id.toLong(), it.name, it.others, placeCount(it.id)) })
    }

    /** The persons named by [query], as the bar finds authors; all of them when it is blank. The most mentioned first. */
    fun search(
        query: String,
        limit: Int,
    ): List<Sage> =
        if (query.isBlank()) {
            sages.values.sortedByDescending { placeCount(it.id) }.take(limit)
        } else {
            names.searchAuthors(query, limit).mapNotNull { sages[it.author.id.toInt()] }
        }

    private fun Map<Int, List<Pair<SageRelation, Int>>>.related(
        id: Int,
        kind: SageRelation,
    ): List<Sage> = this[id].orEmpty().filter { it.first == kind }.mapNotNull { sages[it.second] }

    companion object {
        private const val NAMED_CANDIDATES = 5

        private const val LINE_MENTIONS =
            "SELECT m.sage, m.text FROM mention m JOIN line l ON l.id = m.line WHERE l.heref = ? ORDER BY m.ord"

        // A line once, where its first mention is
        private const val SAGE_PLACES =
            "SELECT l.heref FROM mention m JOIN line l ON l.id = m.line WHERE m.sage = ? GROUP BY m.line ORDER BY MIN(m.ord)"

        /** The persons of [db]: their table, the ambiguous names and their relations read whole, their lines left in the file. */
        internal fun read(db: SefariaDb): SefariaSages {
            val counts = HashMap<Int, Int>()
            val sages =
                db
                    .rows("SELECT id, name, others, description, generation, wiki, places FROM sage") { row ->
                        counts[row.getInt(1)] = row.getInt(7)
                        Sage(
                            row.getInt(1),
                            row.getString(2),
                            row.getString(3).names(),
                            row.getString(4),
                            row.getString(5),
                            row.getString(6),
                        )
                    }.associateBy { it.id }
            val ambiguous =
                db
                    .rows("SELECT id, name, options FROM ambiguous") { row ->
                        AmbiguousSage(row.getInt(1), row.getString(2), row.getString(3).split('|').mapNotNull(String::toIntOrNull))
                    }.associateBy { it.id }
            val kinds = SageRelation.entries.associateBy { it.code }
            val out = HashMap<Int, MutableList<Pair<SageRelation, Int>>>()
            val into = HashMap<Int, MutableList<Pair<SageRelation, Int>>>()
            db.rows("SELECT src, kind, dst FROM sage_relation ORDER BY ord") { row ->
                kinds[row.getString(2)]?.let { kind ->
                    out.getOrPut(row.getInt(1)) { mutableListOf() } += kind to row.getInt(3)
                    into.getOrPut(row.getInt(3)) { mutableListOf() } += kind to row.getInt(1)
                }
            }
            return SefariaSages(sages, ambiguous, out, into, counts, db)
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

/** Sefaria's passages, by each of their lines, in [db]. */
class SefariaSugyot internal constructor(
    private val db: SefariaDb,
) {
    /** The passage the line [heRef] is in: a sugya rather than the Mishnah it starts with. Reads the file. */
    fun of(heRef: String): Sugya? =
        db
            .rows(LINE_SUGYA, heRef) { row -> Sugya(row.getString(1).firstOrNull() ?: 'S', row.getString(2), row.getString(3).split('|')) }
            .firstOrNull()

    private companion object {
        const val LINE_SUGYA =
            "SELECT s.kind, s.source, s.lines FROM sugya_line x JOIN line l ON l.id = x.line JOIN sugya s ON s.id = x.sugya WHERE l.heref = ?"
    }
}

/** Krupnik's dictionary of the Talmud: each headword (and each other headword) with its text, in Sefaria's order. */
object KrupnikDictionary {
    /** The dictionary's entries in the data in use; none without it. Reads the file: off the UI thread. */
    fun entries(): List<Pair<String, String>> = SefariaData.current()?.let { read(it.db) }.orEmpty()

    internal fun read(db: SefariaDb): List<Pair<String, String>> =
        db.rows("SELECT headword, text FROM krupnik ORDER BY ord") { it.getString(1) to it.getString(2) }
}

// The names a row joins by `|`
private fun String.names(): List<String> = split('|').filter { it.isNotEmpty() }

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

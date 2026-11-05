package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import java.util.zip.GZIPInputStream

// Klei Kodesh's dictionary data (Based on Klei Kodesh - https://kleikodesh.github.io/), packed by
// scripts/kleikodesh_dictionary.py into resources/kleikodesh/dictionary.tsv.gz (459 KB): their index of the headwords of
// books our library has, their Aramaic-Hebrew dictionary, Hebrew Wikipedia's acronyms, and their synonyms and spellings
// of words.

/** Where a dictionary's entries come from. */
internal enum class DictionarySource {
    /** Its book, parsed by the card ([parse]). */
    BOOK,

    /** Klei Kodesh's index of its books' lines, each checked against our library's line. */
    INDEX,

    /** Klei Kodesh's data alone: a word and its text, of no book. */
    TEXT,
}

/** A related word: [SYNONYM] of the same meaning, or [SPELLING], the same word otherwise spelled or inflected. */
internal enum class RelationKind { SYNONYM, SPELLING }

internal data class RelatedWord(
    val word: String,
    val kind: RelationKind,
)

/** A line of a book of an [DictionarySource.INDEX] dictionary, as Klei Kodesh's index names it. */
internal data class IndexedHeadword(
    val lineIndex: Int,
    val headword: String,
)

/** Klei Kodesh's data, as read from its pack. */
internal class KleiKodeshPack(
    /** Each indexed dictionary's books, by title, and their headwords. */
    val books: Map<Dictionary, Map<String, List<IndexedHeadword>>>,
    /** Each text dictionary's words and their texts. */
    val texts: Map<Dictionary, List<Pair<String, String>>>,
    private val links: Map<String, List<RelatedWord>>,
) {
    /** The words related to [word], its synonyms first, without itself. */
    fun related(
        word: String,
        most: Int = RELATED_LIMIT,
    ): List<RelatedWord> {
        val key = normalizeWord(word)
        if (key.isEmpty()) return emptyList()
        return links[key]
            .orEmpty()
            .filter { normalizeWord(it.word) != key }
            .distinctBy { normalizeWord(it.word) }
            .sortedBy { it.kind }
            .take(most)
    }

    companion object {
        private const val RELATED_LIMIT = 16
        private const val RESOURCE = "/kleikodesh/dictionary.tsv.gz"

        val empty = KleiKodeshPack(emptyMap(), emptyMap(), emptyMap())

        /** The pack shipped with the app; read once (some 80,000 short rows). */
        val shipped: KleiKodeshPack by lazy {
            KleiKodeshPack::class.java.getResourceAsStream(RESOURCE)?.use { stream ->
                GZIPInputStream(stream).bufferedReader().useLines(::parse)
            } ?: empty
        }

        /** [rows] of a pack (see scripts/kleikodesh_dictionary.py); a row of a dictionary or kind unknown is left out. */
        fun parse(rows: Sequence<String>): KleiKodeshPack {
            val bookOf = mutableListOf<Pair<Dictionary, String>?>()
            val books = mutableMapOf<Dictionary, MutableMap<String, MutableList<IndexedHeadword>>>()
            val texts = mutableMapOf<Dictionary, MutableList<Pair<String, String>>>()
            val links = mutableMapOf<String, MutableList<RelatedWord>>()

            fun link(
                from: String,
                to: String,
                kind: RelationKind,
            ) {
                links.getOrPut(normalizeWord(from)) { mutableListOf() }.add(RelatedWord(to, kind))
            }
            for (row in rows) {
                val f = row.split('\t')
                when (f[0]) {
                    "B" -> bookOf += Dictionary.of(f.getOrNull(1), DictionarySource.INDEX)?.let { it to f[2] }
                    "E" ->
                        bookOf.getOrNull(f[1].toInt())?.let { (dictionary, title) ->
                            books
                                .getOrPut(dictionary) { mutableMapOf() }
                                .getOrPut(title) { mutableListOf() }
                                .add(IndexedHeadword(f[2].toInt(), f[3]))
                        }
                    "S" -> Dictionary.of(f[1], DictionarySource.TEXT)?.let { texts.getOrPut(it) { mutableListOf() }.add(f[2] to f[3]) }
                    "L" ->
                        when (f[1]) {
                            // A synonym both ways; a form leads to its word, not the word to all its forms
                            "1" -> {
                                link(f[2], f[3], RelationKind.SYNONYM)
                                link(f[3], f[2], RelationKind.SYNONYM)
                            }
                            "2" -> link(f[2], f[3], RelationKind.SPELLING)
                        }
                }
            }
            return KleiKodeshPack(books, texts, links)
        }
    }
}

private val TAG = Regex("<[^>]+>")
private val NOT_LETTER = Regex("[^א-ת]")

// The headword as the line marks it, bold or as a heading: <b>צמד.</b>, <strong><big>אב </big></strong>, <h2>אב, המוליד</h2>
private val MARKED = Regex("""^\s*<(b|strong|h\d)>(.+?)</\1>""")

// A note's number (<sup>11</sup>), and the direction marks some books start their lines with
private val NOTE = Regex("""<sup>.*?</sup>""")
private val DIRECTION = Regex("""[\u200E\u200F]""")
private val LEAD = Regex("""^[\s.:,;\-–—]+""")

private val SPACES = Regex("\\s+")

private fun String.plain() =
    replace(DIRECTION, "")
        .replace(NOTE, " ")
        .replace(TAG, " ")
        .replace("&nbsp;", " ")
        .replace(SPACES, " ")
        .trim()

/** Letters alone, final letters as the others: what an index headword and a line are compared by. */
internal fun letters(text: String) = normalizeWord(text.replace(TAG, " ").replace(NOT_LETTER, ""))

/** Most lines an entry spread over its following lines reads in the card. */
private const val MAX_ENTRY_LINES = 60
private const val GLOSS_LENGTH = 160

/**
 * [headwords] of [bookId] (titled [bookTitle]) as entries of this dictionary, from [lines], the book's lines read
 * around them: an entry whose line in our library doesn't hold its headword is left out (the index was built on
 * another library), and counted in [Checked.dropped].
 */
internal fun Dictionary.indexedEntries(
    bookId: Long,
    bookTitle: String,
    headwords: List<IndexedHeadword>,
    lines: Map<Int, Line>,
): Checked {
    val sorted = headwords.sortedBy { it.lineIndex }
    // A book of a series (מצודת ציון על איוב): the book it is on, ahead of the explanation
    val on = bookTitle.substringAfter(" על ", "").takeIf { it.isNotEmpty() }
    var dropped = 0
    val entries =
        sorted.mapIndexedNotNull { i, head ->
            val line = lines[head.lineIndex]
            val key = letters(head.headword)
            if (line == null || key.isEmpty() || key !in letters(line.content)) {
                dropped++
                return@mapIndexedNotNull null
            }
            val marked = MARKED.find(line.content)?.takeIf { letters(it.groupValues[2]) == key }
            val word =
                marked
                    ?.groupValues
                    ?.get(2)
                    ?.plain()
                    ?.trimEnd('.', ':', ',') ?: head.headword
            val rest =
                (marked?.let { line.content.substring(it.range.last + 1) } ?: line.content)
                    .plain()
                    .let { if (marked == null && it.startsWith(word)) it.substring(word.length) else it }
                    .replace(LEAD, "")
            // A heading: its explanation on the lines after it, up to the next entry
            val next = sorted.getOrNull(i + 1)?.lineIndex ?: Int.MAX_VALUE
            val last =
                if (rest.isNotEmpty()) {
                    head.lineIndex
                } else {
                    generateSequence(head.lineIndex + 1) { it + 1 }
                        .take(MAX_ENTRY_LINES)
                        .takeWhile { it < next && it in lines }
                        .toList()
                        // Not the heading of the next one (<h3>ערך ב</h3>)
                        .dropLastWhile {
                            lines
                                .getValue(it)
                                .content
                                .trimStart()
                                .startsWith("<h")
                        }.lastOrNull() ?: head.lineIndex
                }
            val explanation =
                rest.ifEmpty {
                    lines[head.lineIndex + 1]
                        ?.takeIf { last > head.lineIndex }
                        ?.content
                        ?.plain()
                        .orEmpty()
                }
            val gloss = listOfNotNull(on, explanation.ifEmpty { null }).joinToString(" · ")
            DictionaryEntry(word, gloss.take(GLOSS_LENGTH), this, bookId, line.id, head.lineIndex, last)
        }
    return Checked(entries, dropped)
}

/** Entries checked against the library, and how many of the index's were not found there. */
internal data class Checked(
    val entries: List<DictionaryEntry>,
    val dropped: Int,
)

/** The line indexes to read for [headwords]: each one's, and up to [MAX_ENTRY_LINES] after it for a heading. */
internal fun linesToRead(headwords: List<IndexedHeadword>): List<IntRange> {
    val sorted = headwords.map { it.lineIndex }.distinct().sorted()
    val ranges = mutableListOf<IntRange>()
    sorted.forEachIndexed { i, index ->
        val end = minOf(index + MAX_ENTRY_LINES, (sorted.getOrNull(i + 1) ?: Int.MAX_VALUE) - 1).coerceAtLeast(index)
        val last = ranges.lastOrNull()
        if (last != null && index <= last.last + 1) ranges[ranges.size - 1] = last.first..maxOf(last.last, end) else ranges += index..end
    }
    return ranges
}

/** A text dictionary's words as entries, of no book: [idBase] - n, below every line id, keeps them apart in a list. */
internal fun Dictionary.textEntries(
    texts: List<Pair<String, String>>,
    idBase: Long,
): List<DictionaryEntry> =
    texts.mapIndexed { i, (word, text) ->
        DictionaryEntry(word, text.take(GLOSS_LENGTH), this, bookId = -1, lineId = idBase - i, first = 0, text = text)
    }

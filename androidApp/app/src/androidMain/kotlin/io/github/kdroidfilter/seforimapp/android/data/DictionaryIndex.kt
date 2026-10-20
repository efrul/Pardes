package io.github.kdroidfilter.seforimapp.android.data

import android.util.Log
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Port of the data part of desktop 0.6.7 features/home/widgets/library/DictionaryWidget.kt: the library's dictionaries,
// each parsed with its own rule, normalised and looked up as desktop does. Android adds a disk cache of the parsed
// entries (v2), so a phone reads each dictionary book once per library, not once per launch.

/** A dictionary of the library, by its book's [bookTitle]; each one marks its words in its own way (see [parse]). */
internal enum class Dictionary(
    val bookTitle: String,
    val label: String,
) {
    ARUKH("ספר הערוך", "הערוך"),
    HAFLAAH("הפלאה שבערכין על ספר הערוך", "הפלאה שבערכין"),
    LAAZEI_RASHI("אוצר לעזי רש\"י", "לעזי רש״י"),
    SHORASHIM("ספר השרשים לרדק", "השרשים לרד״ק"),
}

/** A word of [dictionary], the start of its explanation, and its lines in the book, [first] to [last]. */
internal data class DictionaryEntry(
    val word: String,
    val gloss: String,
    val dictionary: Dictionary,
    val bookId: Long,
    val lineId: Long,
    val first: Int,
    val last: Int = first,
) {
    val key = normalizeWord(word.replace(CORRECTED, ""))
}

private const val RESULT_LIMIT = 50
private const val GLOSS_LENGTH = 160
private const val PAGE_LINES = 300

private val TAG = Regex("<[^>]+>")

// הערוך: <b><big>אגמא</big></b> [German]. explanation
private val ARUKH_ENTRY = Regex("""^<b><big>(.+?)</big></b>\s*(.*)""")

// Its words come with a German translation in brackets, of no use to its readers today
private val GERMAN = Regex("""^\[[^]]*]\.?\s*""")

// הפלאה שבערכין: <b>אבלא</b> explanation
private val BOLD_ENTRY = Regex("""^<b>(.+?)</b>\s*(.*)""")

// לעזי רש"י: 17 / (ברכות כד:) / <b>סנטר</b> מינטו"ן / menton / <b>סנטר</b> notes
private val LAAZ_ENTRY = Regex("""^\d+ / \(([^)]*)\) / <b>(.+?)</b>([^/]*)/([^/]*)/\s*<b>(.+?)</b>""")

// A few have their fields otherwise divided: the rest of the line explains them
private val LAAZ_LOOSE = Regex("""^\d+ / \(([^)]*)\) / <b>(.+?)</b>(.*)""")

// A word the editor set right: (סנטרו) [סנטר] is looked up as סנטר
private val CORRECTED = Regex("""\([^)]*\)|[\[\]]""")

private val ROOT = Regex("""^<h3>(.+?)</h3>""")

private fun String.plain() = replace(TAG, "").replace("&nbsp;", " ").trim()

// The end of a letter (נשלם אות הבית), and the רד״ק's list of Aramaic words by book (בספר דניאל)
private val NOT_A_WORD = Regex("""^(נשלם|סליק|בספר )""")

/** From the first letter's heading (אות האל״ף) on: the preface's bold lines and headings aren't words. */
private fun List<Line>.fromFirstLetter() = dropWhile { !(it.content.startsWith("<h2") && it.content.plain().startsWith("אות")) }

/** [lines] of [bookId], the book of this dictionary, as its entries; its headings and other lines left out. */
internal fun Dictionary.parse(
    bookId: Long,
    lines: List<Line>,
): List<DictionaryEntry> {
    fun entry(
        line: Line,
        word: String,
        gloss: String,
    ) = DictionaryEntry(word.plain().trimEnd('.', ':', ','), gloss.plain().take(GLOSS_LENGTH), this, bookId, line.id, line.lineIndex)

    val entries =
        when (this) {
            Dictionary.ARUKH ->
                lines.mapNotNull { line ->
                    ARUKH_ENTRY.find(line.content)?.destructured?.let { (word, rest) ->
                        entry(line, word, rest.plain().replace(GERMAN, ""))
                    }
                }
            Dictionary.HAFLAAH ->
                lines.fromFirstLetter().mapNotNull { line ->
                    BOLD_ENTRY.find(line.content)?.destructured?.let { (word, rest) -> entry(line, word, rest) }
                }
            Dictionary.LAAZEI_RASHI ->
                lines.mapNotNull { line ->
                    LAAZ_ENTRY.find(line.content)?.destructured?.let { (ref, word, laaz, french, meaning) ->
                        entry(line, word, "${meaning.plain()} · ${laaz.plain()} (${french.plain()}) · $ref")
                    }
                        ?: LAAZ_LOOSE.find(line.content)?.destructured?.let { (ref, word, rest) ->
                            entry(line, word, "${rest.plain()} · $ref")
                        }
                }
            // A root's heading, then its explanation on the lines up to the next heading
            Dictionary.SHORASHIM -> {
                val book = lines.fromFirstLetter()
                book.mapIndexedNotNull { i, line ->
                    val root = ROOT.find(line.content)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
                    val body = book.subList(i + 1, book.size).takeWhile { !it.content.startsWith("<h") }
                    entry(line, root, body.firstOrNull()?.content.orEmpty()).copy(last = body.lastOrNull()?.lineIndex ?: line.lineIndex)
                }
            }
        }
    return entries.filterNot { it.word.isEmpty() || NOT_A_WORD.containsMatchIn(it.word) }
}

// Nikud, teamim, geresh and quotes: אַגְמָא and אגמא are one word
private val MARKS = Regex("""[֑-ׇ'"׳״\s]""")

// A final letter is the same letter, so a word half typed (אגמ) finds the whole one (אגם)
private val FINALS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

internal fun normalizeWord(word: String) = word.replace(MARKS, "").map { FINALS[it] ?: it }.joinToString("")

/** The entries whose word starts with [query], the word itself first; the dictionaries' order otherwise. */
internal fun lookup(
    entries: List<DictionaryEntry>,
    query: String,
): List<DictionaryEntry> {
    val q = normalizeWord(query)
    if (q.isEmpty()) return emptyList()
    return entries.filter { it.key.startsWith(q) }.sortedBy { it.key != q }.take(RESULT_LIMIT)
}

/**
 * The dictionaries read so far (desktop's cache, ~16,000 short entries in all): each one read once for the app's life
 * (from its disk cache when the library is the same), an empty list when the library has no such book.
 */
internal object DictionaryIndex {
    private val cache = ConcurrentHashMap<Dictionary, List<DictionaryEntry>>()

    // Two screens showing the dictionary read each one once: the second one waits, then finds it in the cache
    private val reading = Mutex()

    /** Whether [dictionary] was read and the library has no such book. */
    fun isMissing(dictionary: Dictionary): Boolean = cache[dictionary]?.isEmpty() == true

    /** [dictionaries] as read so far, or null while one of them is still to be read. */
    fun cached(dictionaries: List<Dictionary>): Map<Dictionary, List<DictionaryEntry>>? = dictionaries.associateWith { cache[it] ?: return null }

    /** [dictionaries]' entries, each read once; one that fails to read is left out of the map, to be tried again. */
    suspend fun read(
        repository: SeforimRepository,
        cacheDir: File,
        dbFile: File,
        dictionaries: List<Dictionary>,
    ): Map<Dictionary, List<DictionaryEntry>> = reading.withLock {
        val stamp = "${dbFile.length()}-${dbFile.lastModified()}"
        dictionaries.mapNotNull { dictionary ->
            cache[dictionary]?.let { return@mapNotNull dictionary to it }
            runCatching {
                withContext(Dispatchers.IO) {
                    val book = repository.getBookByTitle(dictionary.bookTitle) ?: return@withContext emptyList()
                    val file = File(cacheDir, "dict_${dictionary.name}_v2.tsv")
                    readCache(file, stamp, dictionary, book.id) ?: run {
                        // Short pages: a book opened meanwhile waits for one page, not for the whole dictionary
                        val lines = (0 until book.totalLines step PAGE_LINES).flatMap { start -> repository.getLines(book.id, start, start + PAGE_LINES - 1) }
                        dictionary.parse(book.id, lines).also { writeCache(file, stamp, it) }
                    }
                }
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w("DictionaryIndex", dictionary.name, it)
            }.getOrNull()?.also { cache[dictionary] = it }?.let { dictionary to it }
        }.toMap()
    }

    private fun readCache(file: File, stamp: String, dictionary: Dictionary, bookId: Long): List<DictionaryEntry>? = runCatching {
        if (!file.isFile) return null
        val rows = file.readLines()
        if (rows.firstOrNull() != stamp) return null
        rows.drop(1).mapNotNull { row ->
            val p = row.split('\t')
            if (p.size < 5) return@mapNotNull null
            DictionaryEntry(p[0], p[1], dictionary, bookId, p[2].toLong(), p[3].toInt(), p[4].toInt())
        }
    }.getOrNull()

    private fun writeCache(file: File, stamp: String, entries: List<DictionaryEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.bufferedWriter().use { w ->
                w.write(stamp)
                w.newLine()
                fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
                for (e in entries) {
                    w.write("${clean(e.word)}\t${clean(e.gloss)}\t${e.lineId}\t${e.first}\t${e.last}")
                    w.newLine()
                }
            }
        }
    }
}

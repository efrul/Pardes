package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * A reader's review of a tractate's daf (its layout file's lines), for pardes-rashi-ocr: which lines are right, which
 * are not, and where a line's first word really is. Kept in the layouts' folder, under feedback/, one file a tractate,
 * which the reader sends; pardes-rashi-ocr's feedback.py measures the layouts by it and puts its moves in them.
 */
@Serializable
data class DafReviewFile(
    val format: Int = FORMAT,
    val tractate: String,
    val tractateHe: String,
    /** The library release of the layout reviewed (its file's [DafLibrary.release]). */
    val library: String? = null,
    val marks: List<DafMark> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
    }
}

/**
 * The review of one line: the [i]th of its [amud]'s lines (the layout file's), of column [c], whose words were [t]
 * when it was reviewed (a mark whose line has other words now, the layout made again, is not applied). [ok] whether its
 * words are the printed line's (null: not said), and [cut] how many words the line's start moves in the column's text:
 * 1 its first word is the line before's last, -1 the line before's last word is its first. [at] when it was marked.
 */
@Serializable
data class DafMark(
    val amud: String,
    val c: String,
    val i: Int,
    val t: String,
    val ok: Boolean? = null,
    val cut: Int = 0,
    val at: String = Instant.now().toString(),
) {
    val isEmpty: Boolean get() = ok == null && cut == 0
}

/** The line of [amud] at [index] in its column's reading order, before it (null for its column's first or a big word). */
internal fun DafAmud.lineBefore(index: Int): Int? {
    val line = lines.getOrNull(index)?.takeIf { !it.isBig } ?: return null
    return (index - 1 downTo 0).firstOrNull { lines[it].c == line.c && !lines[it].isBig }
}

/** The line of [amud] after the one at [index] in its column (null for its column's last). */
internal fun DafAmud.lineAfter(index: Int): Int? {
    val line = lines.getOrNull(index)?.takeIf { !it.isBig } ?: return null
    return (index + 1 until lines.size).firstOrNull { lines[it].c == line.c && !lines[it].isBig }
}

/**
 * The amud with its review's moves made ([marks], of this amud, each on its line as it was): a line's start moved by
 * [DafMark.cut] words, those words taken from the line before or given to it, with their bold and their library lines.
 * A move that would empty a line, a mark of a line now holding other words, or a big word's line, is left out.
 */
internal fun DafAmud.withMarks(marks: Collection<DafMark>): DafAmud {
    val moves = marks.filter { it.amud == amud && it.cut != 0 && lines.getOrNull(it.i)?.t == it.t }
    if (moves.isEmpty()) return this
    val out = lines.toMutableList()
    for (mark in moves.sortedBy { it.i }) {
        val before = lineBefore(mark.i) ?: continue
        val (earlier, later) = moveCut(out[before], out[mark.i], mark.cut) ?: continue
        out[before] = earlier
        out[mark.i] = later
    }
    return copy(lines = out)
}

/** A line's words with their bold and library lines, one by one. */
private data class LineWord(
    val text: String,
    val bold: Boolean,
    val lineId: Long?,
)

private fun DafLine.words(): List<LineWord> {
    val texts = t.split(' ').filter { it.isNotEmpty() }
    return texts.mapIndexed { n, w -> LineWord(w, n in d, lineIdAt(n)) }
}

private fun DafLine.withWords(words: List<LineWord>): DafLine {
    val spans = mutableListOf<List<Long>>()
    words.forEachIndexed { n, w ->
        val id = w.lineId ?: return@forEachIndexed
        val last = spans.lastOrNull()
        if (last != null && last[0] == id && last[2] == n - 1L) {
            spans[spans.lastIndex] = listOf(id, last[1], n.toLong())
        } else {
            spans += listOf(id, n.toLong(), n.toLong())
        }
    }
    return copy(
        t = words.joinToString(" ") { it.text },
        d = words.indices.filter { words[it].bold },
        u = if (u.isEmpty() && spans.isEmpty()) u else spans,
    )
}

/** Lines [earlier] and [later] with the cut between them moved [cut] words on (back when negative), or null if not possible. */
private fun moveCut(
    earlier: DafLine,
    later: DafLine,
    cut: Int,
): Pair<DafLine, DafLine>? {
    val a = earlier.words()
    val b = later.words()
    return when {
        cut > 0 && cut < b.size -> earlier.withWords(a + b.take(cut)) to later.withWords(b.drop(cut))
        cut < 0 && -cut < a.size -> earlier.withWords(a.dropLast(-cut)) to later.withWords(a.takeLast(-cut) + b)
        else -> null
    }
}

/**
 * The reviews of the layouts in [dir] (the layouts' folder): its feedback/ folder, a file a tractate, named as its
 * layout file is (the tractate's English name). Read once when a tractate's daf is shown, written at every mark.
 */
class DafReviews(
    private val dir: File,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    private val read = HashMap<String, DafReviewFile>()

    /** The folder of the reviews. */
    val folder: File get() = File(dir, FOLDER)

    /**
     * The review of [layout]'s tractate, or an empty one. A file that cannot be read is set aside under another name
     * (`<name>.unreadable-<time>.json`), so that the next mark does not write over the reader's former marks.
     */
    @Synchronized
    fun of(layout: DafLayoutFile): DafReviewFile =
        read.getOrPut(layout.tractate) {
            fileOf(layout.tractate).takeIf { it.isFile }?.let { file ->
                runCatching { json.decodeFromString(DafReviewFile.serializer(), file.readText()) }
                    .onFailure { file.renameTo(File(file.parentFile, "${file.nameWithoutExtension}.unreadable-${System.currentTimeMillis()}.json")) }
                    .getOrNull()
            } ?: DafReviewFile(tractate = layout.tractate, tractateHe = layout.tractateHe, library = layout.library?.release)
        }

    /** [layout]'s review with [mark] in place of its line's former mark (none, if [mark] says nothing), written. */
    @Synchronized
    fun mark(
        layout: DafLayoutFile,
        mark: DafMark,
    ): DafReviewFile {
        val review = of(layout)
        val others = review.marks.filterNot { it.amud == mark.amud && it.i == mark.i }
        val next = review.copy(marks = if (mark.isEmpty) others else others + mark)
        write(layout.tractate, next)
        read[layout.tractate] = next
        return next
    }

    /** [review] written to the file [of] reads for [tractate] (the layout's name: the review's own may differ). */
    private fun write(
        tractate: String,
        review: DafReviewFile,
    ) {
        val target = fileOf(tractate)
        target.parentFile.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")
        part.writeText(json.encodeToString(DafReviewFile.serializer(), review))
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun fileOf(tractate: String): File = File(folder, "${tractate.replace(UNSAFE_NAME, "_")}.json")

    companion object {
        const val FOLDER = "feedback"
        private val UNSAFE_NAME = Regex("[^A-Za-z0-9 _-]")
    }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LibraryLinks
import io.github.kdroidfilter.seforimlibrary.core.models.Line

/**
 * The marks of a book's notes as links to them: in a book that has a book of notes on it (חברותא על ברכות and הערות
 * על חברותא על ברכות), a line's mark `(3)` leads to the note the library links the line to that starts with `(3)`.
 */
internal class NoteLinks(
    private val library: LibraryLinks,
) : QuoteLinksFeature {
    // The books with notes, each with its notes' book, read once from the library
    @Volatile
    private var noted: Map<Long, Long>? = null

    override fun covers(bookId: Long): Boolean = bookId in notedBooks()

    override suspend fun decorate(
        bookId: Long,
        lines: List<Line>,
        linesByIds: suspend (Collection<Long>) -> List<Line>,
    ): List<Line> {
        val notes = notedBooks()[bookId] ?: return lines
        val marked = lines.filter { MARK_START in it.content }
        if (marked.isEmpty()) return lines
        val starts = library.startsIn(notes, marked.map { it.id }, NOTE_START_CHARS)
        return lines.map { line ->
            val linked = starts[line.id] ?: return@map line
            val html = withNoteLinks(line.content, linked.mapNotNull { (id, start) -> noteNumber(start)?.let { it to id } }.toMap())
            if (html === line.content) line else line.copy(content = html)
        }
    }

    private fun notedBooks(): Map<Long, Long> = noted ?: library.notedBooks().also { if (it.isNotEmpty()) noted = it }
}

/** The number a note starts with, after its tags: 3 of `<b>(3)</b> המהרש"א כתב`. Null for a line that starts with none. */
internal fun noteNumber(start: String): Int? {
    val number = NOTE_NUMBER.find(start) ?: return null
    return number.groupValues[1].toIntOrNull()
}

/**
 * [html] with each mark of a note (`<small>(3)</small>`) made a link to its note, among [notes] ({number: the note's
 * line}), as the library's own citations. A mark with no note is left; the same [html] when no mark has one.
 */
internal fun withNoteLinks(
    html: String,
    notes: Map<Int, Long>,
): String {
    if (notes.isEmpty()) return html
    var linked = false
    val out =
        MARK.replace(html) { mark ->
            val note = mark.groupValues[1].toIntOrNull()?.let(notes::get) ?: return@replace mark.value
            linked = true
            "<a class=\"ref\" href=\"seforim://line/$note\">${mark.value}</a>"
        }
    return if (linked) out else html
}

private const val MARK_START = "<small>("
private val MARK = Regex("""<small>\s*\((\d+)\)\s*</small>""")
private val NOTE_NUMBER = Regex("""^\s*(?:<[^>]+>\s*)*\((\d+)\)""")

// A note's number is in its first letters, after a tag or two
private const val NOTE_START_CHARS = 48

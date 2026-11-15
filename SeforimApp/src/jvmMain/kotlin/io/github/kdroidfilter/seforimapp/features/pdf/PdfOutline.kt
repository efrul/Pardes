package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE

// Pardes (request 52 of docs/DESKTOP_STAGE2.md): a PDF book's own contents, the bookmarks PDF readers list, in the
// reader's contents pane ("אין תמיכה בתוכן עניינים", the user's word on the pages that request 49 listed there).

/**
 * An entry of a PDF book's own contents: its [title], the [page] it leads to (from 0; null when none in the book), and
 * the entries under it.
 */
@Immutable
data class PdfOutlineEntry(
    val title: String,
    val page: Int?,
    val children: List<PdfOutlineEntry> = emptyList(),
)

/**
 * Reads a PDF file's own contents (its outline) with the PDFium that Nucleus's binding ships and loads: the binding
 * reads no bookmarks, so the few functions of PDFium's fpdf_doc.h that do are called here ([PdfiumCalls]), on a document
 * of their own that reads from the file only what the outline needs. The binding loads PDFium when it first opens a
 * PDF, so a book's outline is read once its reader opened it.
 */
object PdfOutlines {
    /** The outline of [file], empty when it has none or cannot be read. */
    suspend fun read(file: File): List<PdfOutlineEntry> =
        withContext(PdfiumCalls.dispatcher) { runCatching { PdfiumOutline.read(file) }.getOrDefault(emptyList()) }
}

// The bookmarks of a PDF, read by PDFium's functions (PdfiumCalls)
private object PdfiumOutline {
    // A malformed file's bookmarks may loop, or be many more than any book's contents
    const val MAX_ENTRIES = 20_000
    const val MAX_DEPTH = 64

    // PDFACTION_GOTO of fpdf_doc.h: a destination in the document itself
    const val ACTION_GOTO = 1L

    // A title's terminating NUL, in UTF-16
    const val TITLE_END = 2L
    const val TITLE_BUFFER = 512L

    // A title's line breaks and runs of spaces, one space in the pane
    private val spaces = Regex("\\s+")

    fun read(file: File): List<PdfOutlineEntry> {
        val f = PdfiumCalls.functions() ?: return emptyList()
        // Done once by the binding before it opens a book; again here, for a file read before any (PDFium skips it
        // then)
        f.init.invokeExact()
        Arena.ofConfined().use { arena ->
            val doc = f.load.invokeExact(arena.allocateFrom(file.absolutePath), MemorySegment.NULL) as MemorySegment
            if (doc.address() == 0L) return emptyList()
            try {
                return Walk(f, doc, f.pageCount.invokeExact(doc) as Int, arena).children(MemorySegment.NULL, depth = 0)
            } finally {
                f.close.invokeExact(doc)
            }
        }
    }

    // One document's outline, its bookmarks seen by their address (each one's dictionary in the document)
    private class Walk(
        private val f: PdfiumCalls.Functions,
        private val doc: MemorySegment,
        private val pages: Int,
        private val arena: Arena,
    ) {
        private val seen = HashSet<Long>()
        private var buffer = arena.allocate(TITLE_BUFFER)

        fun children(
            parent: MemorySegment,
            depth: Int,
        ): List<PdfOutlineEntry> {
            if (depth >= MAX_DEPTH) return emptyList()
            val entries = ArrayList<PdfOutlineEntry>()
            var bookmark = f.firstChild.invokeExact(doc, parent) as MemorySegment
            while (bookmark.address() != 0L && seen.size < MAX_ENTRIES && seen.add(bookmark.address())) {
                entries += PdfOutlineEntry(title(bookmark), page(bookmark), children(bookmark, depth + 1))
                bookmark = f.nextSibling.invokeExact(doc, bookmark) as MemorySegment
            }
            return entries
        }

        private fun title(bookmark: MemorySegment): String {
            val size = titleInto(bookmark, MemorySegment.NULL, 0L)
            if (size <= TITLE_END) return ""
            if (size > buffer.byteSize()) buffer = arena.allocate(size)
            titleInto(bookmark, buffer, size)
            val text = String(buffer.asSlice(0L, size - TITLE_END).toArray(JAVA_BYTE), Charsets.UTF_16LE)
            return text.replace(spaces, " ").trim()
        }

        private fun titleInto(
            bookmark: MemorySegment,
            into: MemorySegment,
            size: Long,
        ): Long =
            if (PdfiumCalls.longIsInt) {
                Integer.toUnsignedLong(f.title.invokeExact(bookmark, into, size.toInt()) as Int)
            } else {
                f.title.invokeExact(bookmark, into, size) as Long
            }

        // FPDFBookmark_GetDest takes the destination of the bookmark's action when it has none of its own, another
        // file's too: one that acts goes to a page of this book only by GoTo
        private fun page(bookmark: MemorySegment): Int? {
            val action = f.action.invokeExact(bookmark) as MemorySegment
            if (action.address() != 0L && actionType(action) != ACTION_GOTO) return null
            val dest = (f.dest.invokeExact(doc, bookmark) as MemorySegment).takeIf { it.address() != 0L } ?: return null
            return (f.destPage.invokeExact(doc, dest) as Int).takeIf { it in 0 until pages }
        }

        private fun actionType(action: MemorySegment): Long =
            if (PdfiumCalls.longIsInt) (f.actionType.invokeExact(action) as Int).toLong() else f.actionType.invokeExact(action) as Long
    }
}

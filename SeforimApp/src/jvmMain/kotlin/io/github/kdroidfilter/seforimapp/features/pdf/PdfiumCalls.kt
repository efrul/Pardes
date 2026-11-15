package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.nucleusframework.pdfium.rememberPdfReaderState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandle

// Pardes (requests 52 and 56 of docs/DESKTOP_STAGE2.md): PDFium's own functions, for what Nucleus's binding does not do:
// a PDF's bookmarks, and a page drawn into pixels of Pardes's own.

/**
 * Whether PDFium is loaded for Pardes's calls ([PdfiumCalls]): when the binding has not loaded it yet, it is asked to,
 * by opening an empty PDF of one page as it opens a book (its loader is internal to it). Null while it is asked, then
 * whether it was loaded.
 */
@Composable
internal fun rememberPdfiumLoaded(): Boolean? {
    val reader = rememberPdfReaderState()
    val loaded by produceState(PdfiumCalls.functions()?.let { true }, reader) {
        if (value == true) return@produceState
        runSuspendCatching { reader.open(PdfiumCalls.EMPTY_PDF) }
        value = PdfiumCalls.functions() != null
    }
    return loaded
}

/**
 * PDFium's functions that Pardes calls itself, through Java's Foreign Function API, in the library that Nucleus's
 * binding ships and loads (once it has opened a PDF, [rememberPdfiumLoaded]): the binding reads no bookmarks, and draws
 * a page only into its own composable. Every call is made on [dispatcher], one at a time, on documents of Pardes's own,
 * as the binding calls PDFium on each of its own threads with a document of each.
 */
internal object PdfiumCalls {
    /** The thread PDFium's calls are made on, one at a time. */
    val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val linker = Linker.nativeLinker()

    /** C's long, which the functions that give a length or a kind take and give: 32 bits on Windows, 64 elsewhere. */
    val cLong = linker.canonicalLayouts().getValue("long") as ValueLayout

    /** Whether C's long is Java's int here, as each call is made with its exact types. */
    val longIsInt = cLong.carrier() == Int::class.javaPrimitiveType

    // Found once PDFium is loaded; looked for again until then
    @Volatile private var found: Functions? = null

    /** The functions, once the binding has loaded PDFium ([rememberPdfiumLoaded]); null until then. */
    fun functions(): Functions? = found ?: runCatching { Functions(lookup()) }.getOrNull()?.also { found = it }

    // The libraries System.load loaded for this class loader, the binding's among them, else PDFium by its name, which
    // the system gives as the one already loaded
    private fun lookup(): SymbolLookup =
        SymbolLookup.loaderLookup().takeIf { it.find("FPDF_LoadDocument").isPresent }
            ?: SymbolLookup.libraryLookup(System.mapLibraryName("pdfium"), Arena.global())

    /** PDFium's functions called here, by fpdfview.h and fpdf_doc.h. */
    class Functions(
        private val lookup: SymbolLookup,
    ) {
        private fun function(
            name: String,
            descriptor: FunctionDescriptor,
        ): MethodHandle = linker.downcallHandle(lookup.find(name).orElseThrow(), descriptor)

        val init = function("FPDF_InitLibrary", FunctionDescriptor.ofVoid())
        val load = function("FPDF_LoadDocument", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
        val close = function("FPDF_CloseDocument", FunctionDescriptor.ofVoid(ADDRESS))
        val pageCount = function("FPDF_GetPageCount", FunctionDescriptor.of(JAVA_INT, ADDRESS))

        // A page, and its drawing into a bitmap whose pixels are Pardes's (FPDFBitmap_CreateEx)
        val loadPage = function("FPDF_LoadPage", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT))
        val closePage = function("FPDF_ClosePage", FunctionDescriptor.ofVoid(ADDRESS))
        val pageWidth = function("FPDF_GetPageWidthF", FunctionDescriptor.of(JAVA_FLOAT, ADDRESS))
        val pageHeight = function("FPDF_GetPageHeightF", FunctionDescriptor.of(JAVA_FLOAT, ADDRESS))
        val bitmap =
            function("FPDFBitmap_CreateEx", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT))
        val render =
            function(
                "FPDF_RenderPageBitmap",
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
            )
        val destroyBitmap = function("FPDFBitmap_Destroy", FunctionDescriptor.ofVoid(ADDRESS))

        // The bookmarks
        val firstChild = function("FPDFBookmark_GetFirstChild", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
        val nextSibling = function("FPDFBookmark_GetNextSibling", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
        val title = function("FPDFBookmark_GetTitle", FunctionDescriptor.of(cLong, ADDRESS, ADDRESS, cLong))
        val dest = function("FPDFBookmark_GetDest", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
        val action = function("FPDFBookmark_GetAction", FunctionDescriptor.of(ADDRESS, ADDRESS))
        val actionType = function("FPDFAction_GetType", FunctionDescriptor.of(cLong, ADDRESS))
        val destPage = function("FPDFDest_GetDestPageIndex", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
    }

    /** A PDF of one empty page, with its cross-reference table. */
    val EMPTY_PDF: ByteArray =
        run {
            val objects =
                listOf(
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] >>",
                )
            val out = StringBuilder("%PDF-1.4\n")
            val offsets = objects.mapIndexed { i, body -> out.length.also { out.append("${i + 1} 0 obj\n$body\nendobj\n") } }
            val xref = out.length
            out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { out.append("%010d 00000 n \n".format(it)) }
            out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
            out.toString().toByteArray(Charsets.US_ASCII)
        }
}

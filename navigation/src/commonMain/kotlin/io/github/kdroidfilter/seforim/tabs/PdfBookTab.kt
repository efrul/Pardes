package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pardes (request 28 of docs/DESKTOP_STAGE2.md): a PDF book of the user's, [pdfId] in the user DB's pdf_book, read in a
 * tab of its own. A destination of Pardes's own, beside [TabsDestination]'s in its file: a sealed interface's subclass
 * may live in another file of its package, and Zayit's file stays as it is. Its serial name is fixed, as the session
 * keeps the tab by it.
 */
@Serializable
@SerialName("PdfBook")
@Immutable
data class PdfBookTab(
    override val tabId: String,
    val pdfId: Long,
) : TabsDestination

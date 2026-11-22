package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

/**
 * Pardes (request 56 of docs/DESKTOP_STAGE3_PLAN.md): [base], with each text copied to it cut of the spaces at its
 * ends. A selection begun at the gap between two words takes that space, which the paste then shows before the text.
 * Compose's own copy (Ctrl+C, the menu's "העתקה") writes through the window's clipboard, as the app's copies do.
 */
private class EdgeTrimmedClipboard(
    private val base: Clipboard,
) : Clipboard by base {
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        base.setClipEntry(clipEntry?.let(::edgeTrimmed))
    }
}

/** [entry] with its text cut of the spaces at its ends; [entry] itself when it holds no text, or nothing to cut. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun edgeTrimmed(entry: ClipEntry): ClipEntry {
    val text = textOf(entry) ?: return entry
    val trimmed = text.trim()
    return if (trimmed.length == text.length) entry else ClipEntry(StringSelection(trimmed))
}

// The plain text [entry] holds, if it holds one
@OptIn(ExperimentalComposeUiApi::class)
private fun textOf(entry: ClipEntry): String? {
    val transferable = entry.nativeClipEntry as? Transferable ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    return runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
}

/** The window's clipboard ([LocalClipboard]) with its copies cut of the spaces at their ends, for the window's content. */
@Composable
fun rememberEdgeTrimmedClipboard(): Clipboard {
    val base = LocalClipboard.current
    return remember(base) { EdgeTrimmedClipboard(base) }
}

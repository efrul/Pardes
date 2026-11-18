package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A text copied to the window's clipboard, cut of the spaces at its ends (request 56). */
@OptIn(ExperimentalComposeUiApi::class)
class EdgeTrimmedClipboardTest {
    private fun textOf(entry: ClipEntry): String =
        (entry.nativeClipEntry as Transferable).getTransferData(DataFlavor.stringFlavor) as String

    @Test
    fun `a selection begun at a word's gap loses the space before it`() {
        val copied = edgeTrimmed(ClipEntry(StringSelection(" בִּלְבַד אָמְרוּ")))

        assertEquals("בִּלְבַד אָמְרוּ", textOf(copied))
    }

    @Test
    fun `spaces and line breaks at both ends go, those inside stay`() {
        val copied = edgeTrimmed(ClipEntry(StringSelection("\n מַתְנִי׳ מֵאֵימָתַי\nקוֹרִין \n")))

        assertEquals("מַתְנִי׳ מֵאֵימָתַי\nקוֹרִין", textOf(copied))
    }

    @Test
    fun `a text with nothing to cut is the same entry`() {
        val entry = ClipEntry(StringSelection("ברכות ב."))

        assertSame(entry, edgeTrimmed(entry))
    }
}

package io.github.kdroidfilter.seforimapp.features.siddur

import io.github.kdroidfilter.seforimapp.siddur.BookSection
import io.github.kdroidfilter.seforimapp.siddur.SiddurLine
import io.github.kdroidfilter.seforimapp.siddur.SiddurPart
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SiddurScrollTest {
    @Test
    fun togglingOmittedTextRetainsTheSameLineAndPixelOffset() {
        val filtered = items(SiddurLine("לשם יחוד"), SiddurLine("יתגדל ויתקדש"))
        val all = items(SiddurLine("ברכי נפשי", skipped = true), SiddurLine("לשם יחוד"), SiddurLine("יתגדל ויתקדש"))

        assertEquals(2 to 16, retainedSiddurPosition(filtered, all, index = 1, offset = 16))
        assertEquals(1 to 16, retainedSiddurPosition(all, filtered, index = 2, offset = 16))
        assertEquals(1 to 0, retainedSiddurPosition(all, filtered, index = 1, offset = 12))
    }

    private fun items(vararg lines: SiddurLine): List<BookItem> =
        bookItems(listOf(BookSection(SiddurPart.MAARIV, LocalDate(2026, 11, 8), true, emptySet(), lines.toList())))
}

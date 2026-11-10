package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NoteLinksTest {
    // A line of חברותא על ברכות, with the marks of two notes
    private val line =
        "<b>משעה שהכהנים נכנסים לאכול בתרומתן.</b> והוא זמן צאת הכוכבים. <small>(3)</small> " +
            "זמן זה הוא גם תחילת הזמן של מצות קריאת שמע של ערבית. <small>(4)</small>"

    @Test
    fun aNotesNumberIsWhatItStartsWith() {
        assertEquals(3, noteNumber("<b>(3)</b> <b>המהרש''א</b> [ע\"ב תוד\"ה משעה] כתב"))
        assertEquals(12, noteNumber(" (12) אבל קודם לכן"))
        assertNull(noteNumber("<b>המהרש''א</b> כתב (3)"))
        assertNull(noteNumber(""))
    }

    @Test
    fun aMarkIsALinkToItsNote() {
        assertEquals(
            "<b>משעה שהכהנים נכנסים לאכול בתרומתן.</b> והוא זמן צאת הכוכבים. " +
                "<a class=\"ref\" href=\"seforim://line/7647144\"><small>(3)</small></a> " +
                "זמן זה הוא גם תחילת הזמן של מצות קריאת שמע של ערבית. " +
                "<a class=\"ref\" href=\"seforim://line/7647145\"><small>(4)</small></a>",
            withNoteLinks(line, mapOf(3 to 7647144L, 4 to 7647145L)),
        )
    }

    @Test
    fun aMarkWithNoNoteIsLeft() {
        val one = withNoteLinks(line, mapOf(4 to 7647145L))
        assertEquals(1, Regex("<a ").findAll(one).count())
        assertEquals(true, "<small>(3)</small> " in one && "\"><small>(4)</small></a>" in one)
        assertSame(line, withNoteLinks(line, mapOf(9 to 1L)))
        assertSame(line, withNoteLinks(line, emptyMap()))
        // A number in the line's own words is no mark
        val words = "כמבואר להלן (3) בדברי הגמרא"
        assertSame(words, withNoteLinks(words, mapOf(3 to 1L)))
    }
}

package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import io.github.kdroidfilter.seforimapp.features.home.widgets.ToolWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DictionaryLookupTest {
    @Test
    fun `a selected word is asked for as its letters alone`() {
        assertEquals("אגמא", lookupWord("אַגְמָא,"))
        assertEquals("קל וחומר", lookupWord(" קַל\nוָחוֹמֶר: "))
        assertEquals("רש\"י", lookupWord("(רש\"י)"))
    }

    @Test
    fun `no word, or more words than an entry has, is not looked up`() {
        assertNull(lookupWord("  "))
        assertNull(lookupWord("—"))
        assertEquals("א ב ג ד", lookupWord("א ב ג ד"))
        assertNull(lookupWord("א ב ג ד ה"))
    }

    @Test
    fun `a word of a text is found without the letters before it`() {
        val words = listOf("הלל", "הללויה", "אמר", "משה", "שה")
        val entries = words.mapIndexed { i, word -> DictionaryEntry(word, "", Dictionary.ARUKH, 1, i.toLong(), i) }

        fun found(query: String) = lookupAsRead(entries, query).map { it.word }
        assertEquals(listOf("הלל", "הללויה"), found("והלל"))
        assertEquals(listOf("אמר"), found("דאמר"))
        assertEquals(listOf("אמר"), found("וּכְאָמַר"))
        // A word of the dictionaries is itself, whatever its first letter, and so is one being typed
        assertEquals(listOf("משה"), found("משה"))
        assertEquals(listOf("הלל", "הללויה"), found("הל"))
        // No word left, or more letters before it than a word takes: nothing found, as before
        assertEquals(emptyList(), found("וגבר"))
        assertEquals(emptyList(), found("ואמ"))
        assertEquals(emptyList(), found("וכשאמר"))
    }

    @Test
    fun `a lookup opens the dictionary's window, and brings it back for the next word`() {
        val toolWindows = ToolWindows()
        val lookup = DictionaryLookup(toolWindows)
        lookup.open("אגמא")
        assertEquals("אגמא", lookup.word)
        assertEquals(listOf<Any>(DictionaryWidget), toolWindows.open.toList())
        lookup.open("בדק")
        assertEquals("בדק", lookup.word)
        assertEquals(1, toolWindows.reopened[DictionaryWidget.id])
    }
}

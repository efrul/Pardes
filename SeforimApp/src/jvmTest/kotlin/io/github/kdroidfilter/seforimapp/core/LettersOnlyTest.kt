package io.github.kdroidfilter.seforimapp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LettersOnlyTest {
    @Test
    fun `drops the nikud, the teamim and the punctuation`() {
        assertEquals("בראשית ברא אלהים את השמים ואת הארץ", lettersOnly("בְּרֵאשִׁ֖ית בָּרָ֣א אֱלֹהִ֑ים אֵ֥ת הַשָּׁמַ֖יִם וְאֵ֥ת הָאָֽרֶץ׃"))
        assertEquals("אמר רבא מאי קרא שנאמר", lettersOnly("אמר רבא: מאי קרא? (שנאמר)..."))
    }

    @Test
    fun `puts a space where a mark stood between two words`() {
        assertEquals("על פני תהום", lettersOnly("עַל־פְּנֵ֣י תְה֑וֹם"))
        assertEquals("רב הונא רב חסדא", lettersOnly("רב הונא—רב חסדא"))
        assertEquals("דף ב עמוד א", lettersOnly("דף ב/עמוד א"))
    }

    @Test
    fun `keeps a geresh or gershayim that is part of a word`() {
        assertEquals("פירש רש\"י ד\"ה וגו'", lettersOnly("פירש רש\"י, ד\"ה: וגו'."))
        assertEquals("ר׳ יוחנן אמר רש״י", lettersOnly("ר׳ יוחנן אמר: רש״י"))
    }

    @Test
    fun `drops the quotation marks around words`() {
        assertEquals("אמר שלום לו", lettersOnly("אמר \"שלום\" לו"))
    }

    @Test
    fun `keeps the lines and the numbers`() {
        assertEquals("סימן 12 סעיף 3\nשורה שנייה", lettersOnly("סימן 12, סעיף 3.\n  שורה   שנייה!  "))
    }

    @Test
    fun `tells a text of words alone from one with marks`() {
        assertFalse(hasMoreThanLetters("אמר רבא מאי קרא\nשנאמר 12"))
        assertTrue(hasMoreThanLetters("אמר רבא: מאי קרא"))
        assertTrue(hasMoreThanLetters("בְּרֵאשִׁית"))
    }
}

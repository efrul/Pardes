package io.github.kdroidfilter.seforimapp.siddur

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SukkotPrayersTest {
    private val prefs = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = true, beitAvel = false)

    @Test
    fun `shehecheyanu appears only on the first lulav day or by choice`() {
        val outline = Outline(
            SourceBook(
                listOf("סוכות", "נטילת לולב", "על נטילת לולב", "בפעם הראשון שמברך על הלולב:", "שהחיינו"),
                listOf(SourceHeading(0, 1, "סוכות"), SourceHeading(1, 2, "נטילת לולב")),
            ),
        )
        val flags = setOf("sukkot")
        assertTrue(outline.lulavBlessings(LocalDate(2026, 9, 27), prefs, flags, null).any { it.html == "שהחיינו" })
        assertFalse(outline.lulavBlessings(LocalDate(2026, 10, 1), prefs, flags, null).any { it.html == "שהחיינו" })
        assertTrue(
            outline.lulavBlessings(LocalDate(2026, 10, 1), prefs, flags + "first_lulav", null).any { it.html == "שהחיינו" },
        )
    }

    @Test
    fun `the last cue splits the final hodu from the preceding verses`() {
        val hallel = listOf(SiddurLine("בָּרוּךְ הַבָּא. אֵלִי אַתָּה וְאוֹדֶךָּ׃ הוֹד֣וּ לַיהֹוָ֣ה כִּי־ט֑וֹב"))
        val lines = hallel.withLulavCues(prefs, setOf("sukkot"))
        assertEquals(LulavInstruction.SHAKE_TWICE, lines[1].lulavInstruction)
        assertTrue(lines[2].html.startsWith("הוֹד"))
        assertEquals(hallel, hallel.withLulavCues(prefs, setOf("sukkot", "sat")))
    }

    @Test
    fun `chol hamoed aliyot follow location and nusach`() {
        val date = LocalDate(2026, 10, 1)
        fun verses(location: SiddurPrefs) = readingsFor(date, SiddurPart.SHACHARIT, location)
            .single { it.kind == ReadingKind.TORAH }.passages.map { it.first.verse to it.last.verse }

        assertEquals(List(4) { 29 to 31 }, verses(prefs))
        assertEquals(listOf(26 to 28, 29 to 31, 32 to 34, 26 to 31), verses(prefs.copy(inIsrael = false)))
        assertEquals(
            listOf(26 to 28, 29 to 31, 29 to 31, 26 to 31),
            verses(prefs.copy(inIsrael = false, nusach = Nusach.EDOT_HAMIZRACH)),
        )
    }
}

package io.github.kdroidfilter.seforimapp.features.search.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategorySuggestionsTest {
    // Categories of the library (ids as in the October 2026 release)
    private val talmud = CategoryHit(12, listOf("תלמוד"))
    private val bavli = CategoryHit(13, listOf("תלמוד", "בבלי"))
    private val bavliAcharonim = CategoryHit(117, listOf("תלמוד", "בבלי", "אחרונים"))
    private val mishnaAcharonim = CategoryHit(448, listOf("משנה", "אחרונים"))
    private val torah = CategoryHit(2, listOf("תנ״ך", "תורה"))
    private val rashiTanakh = CategoryHit(1140, listOf("תנ״ך", "ראשונים", "רש״י"))
    private val rashiTorah = CategoryHit(1142, listOf("תנ״ך", "ראשונים", "רש״י", "תורה"))
    private val shulchanAruch = CategoryHit(62, listOf("הלכה", "שולחן ערוך"))
    private val shulchanAruchHarav = CategoryHit(1036, listOf("הלכה", "שולחן ערוך הרב"))
    private val shulchanAruchCommentaries = CategoryHit(560, listOf("הלכה", "שולחן ערוך", "מפרשים"))
    private val all =
        listOf(
            talmud,
            bavli,
            bavliAcharonim,
            mishnaAcharonim,
            torah,
            rashiTanakh,
            rashiTorah,
            shulchanAruch,
            shulchanAruchHarav,
            shulchanAruchCommentaries,
        )

    private fun match(query: String) = CategorySuggestions.match(query, all, limit = 4)

    @Test
    fun `a category typed by its title comes first`() {
        assertEquals(talmud, match("תלמוד").first())
        assertEquals(listOf(shulchanAruch, shulchanAruchHarav), match("שולחן ערוך"))
    }

    @Test
    fun `a commentator on a part of the Tanakh is its category under the commentator`() {
        assertEquals(rashiTorah, match("רש\"י על התורה").first())
        assertEquals(rashiTanakh, match("רשי").first())
    }

    @Test
    fun `shas stands for the Bavli`() {
        assertEquals(listOf(bavliAcharonim), match("אחרונים על הש\"ס"))
    }

    @Test
    fun `the words name the category, its own title at least`() {
        assertEquals(listOf(shulchanAruchCommentaries), match("מפרשים שולחן ערוך"))
        // תורה alone is in רש״י's path, but no title of it is רש״י's
        assertTrue(match("תורה").none { it == rashiTanakh })
        assertTrue(match("ש").isEmpty())
    }
}

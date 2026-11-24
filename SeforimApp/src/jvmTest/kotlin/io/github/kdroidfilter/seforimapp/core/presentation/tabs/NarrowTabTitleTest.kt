package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabType
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A tab too narrow for its book's title shows the book's acronym and its place (request 83 of stage 3). */
@OptIn(ExperimentalTestApi::class)
class NarrowTabTitleTest {
    @Test
    fun `the acronym then the place, as a reference is written`() {
        assertEquals("שו\"ע או\"ח רנג", narrowTabTitle("שולחן ערוך, אורח חיים - סימן רנג", "שו\"ע או\"ח"))
        assertEquals("משנ\"ב שח", narrowTabTitle("משנה ברורה - סימן שח", "משנ\"ב"))
        assertEquals("משנ\"ת שבת ג", narrowTabTitle("משנה תורה, הלכות שבת - פרק ג", "משנ\"ת שבת"))
    }

    @Test
    fun `a book without an acronym keeps its name before the place`() {
        // A pinned tab's label is the book's title then.
        assertEquals("ברכות ב.", narrowTabTitle("ברכות - דף ב.", "ברכות"))
        assertEquals("בבא בתרא קעו:", narrowTabTitle("בבא בתרא - דף קעו:", "בבא בתרא"))
        assertEquals("בראשית א", narrowTabTitle("בראשית - פרק א", "בראשית"))
    }

    @Test
    fun `two tabs of one book differ by their place`() {
        val first = narrowTabTitle("שולחן ערוך, אורח חיים - סימן רנג", "שו\"ע או\"ח")
        val second = narrowTabTitle("שולחן ערוך, אורח חיים - סימן שח", "שו\"ע או\"ח")
        assertEquals(listOf("שו\"ע או\"ח רנג", "שו\"ע או\"ח שח"), listOf(first, second))
    }

    @Test
    fun `a heading in words stays whole`() {
        assertEquals("שו\"ע או\"ח הקדמה", narrowTabTitle("שולחן ערוך, אורח חיים - הקדמה", "שו\"ע או\"ח"))
        assertEquals("משנ\"ת הלכות שבת", narrowTabTitle("משנה תורה - הלכות שבת", "משנ\"ת"))
        assertEquals("בר\"ר פרק שלישי", narrowTabTitle("בראשית רבה - פרק שלישי", "בר\"ר"))
        // A unit's word alone is the heading itself.
        assertEquals("סה\"ח שער", narrowTabTitle("ספר החינוך - שער", "סה\"ח"))
    }

    @Test
    fun `a book still at its opening shows its acronym alone`() {
        assertEquals("משנ\"ב", narrowTabTitle("משנה ברורה", "משנ\"ב"))
    }

    @Test
    fun `the place is what follows the last joint`() {
        assertEquals("אוצ\"מ ג", narrowTabTitle("אוצר מדרשים - חלק א - פרק ג", "אוצ\"מ"))
    }

    @Test
    fun `a title that fits its tab stays, and one that does not gives way to the short form`() =
        runDesktopComposeUiTest(width = 600, height = 80) {
            val book = TabItem(id = 1, title = "שולחן ערוך, אורח חיים - סימן רנג", tabType = TabType.BOOK, shortTitle = "או\"ח")
            var width by mutableStateOf(400.dp)
            setContent { IntUiTheme(isDark = false) { Row { TabContentBox(Modifier.width(width)) { Text(book.stripTitle()) } } } }
            onNodeWithText("שולחן ערוך, אורח חיים - סימן רנג").assertExists()
            width = 120.dp
            waitForIdle()
            onNodeWithText("או\"ח רנג").assertExists()
            width = 400.dp
            waitForIdle()
            onNodeWithText("שולחן ערוך, אורח חיים - סימן רנג").assertExists()
        }

    @Test
    fun `a search's tab keeps its title however narrow`() =
        runDesktopComposeUiTest(width = 600, height = 80) {
            val search = TabItem(id = 2, title = "כירה שהסיקוה בקש ובגבבא", tabType = TabType.SEARCH)
            setContent { IntUiTheme(isDark = false) { Row { TabContentBox(Modifier.width(60.dp)) { Text(search.stripTitle()) } } } }
            onNodeWithText("כירה שהסיקוה בקש ובגבבא").assertExists()
        }

    @Test
    fun `a narrow tab that is not selected shows its close button under the pointer only`() {
        assertFalse(closeShown(112.dp, hovered = false))
        assertTrue(closeShown(112.dp, hovered = true))
        assertTrue(closeShown(140.dp, hovered = false))
        assertTrue(closeShown(180.dp, hovered = false))
    }

    @Test
    fun `a tab that is not a book's keeps its title`() {
        assertNull(narrowTabTitle("בין השמשות", ""))
        assertNull(narrowTabTitle("דף הבית - פרדס", " "))
    }
}

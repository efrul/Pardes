package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.testAppSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the reader's side panes run the window's whole height (Settings › תצוגה): off by default, the bottom pane
 * under the text and the side panes as before; kept, carried by a backup and reset with the other settings.
 */
class SidePanesFullHeightTest {
    @Test
    fun `off by default, kept once turned on, and reset with the settings`() {
        val properties = Properties()
        val settings = AppSettings(PropertiesSettings(properties))
        assertFalse(settings.sidePanesFullHeightFlow.value)

        settings.setSidePanesFullHeight(true)
        assertTrue(settings.sidePanesFullHeightFlow.value)
        assertTrue(AppSettings(PropertiesSettings(properties)).isSidePanesFullHeight())

        settings.clearAll()
        assertFalse(settings.sidePanesFullHeightFlow.value)
        assertFalse(settings.isSidePanesFullHeight())
    }

    @Test
    fun `a backup carries it to another computer`() {
        val settings = testAppSettings()
        settings.setSidePanesFullHeight(true)

        val other = PropertiesSettings(Properties())
        AppSettings.importPortablePreferences(other, settings.exportPortablePreferences())

        assertTrue(AppSettings(other).isSidePanesFullHeight())
    }
}

package io.github.kdroidfilter.seforimapp.features.siddur

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.siddur.IsraeliPrayerCustom
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The open siddur is experimental: the app has none until its switch is turned on, and has it again at the next start. */
class OpenSiddurSwitchTest {
    @Test
    fun `no siddur until the switch is turned on, and none once it is turned off`() {
        val settings = AppSettings(PropertiesSettings(Properties()))

        assertFalse(settings.isOpenSiddurEnabled())
        assertNull(installedSiddur)

        settings.setOpenSiddurEnabled(true)
        assertNotNull(installedSiddur)

        settings.setOpenSiddurEnabled(false)
        assertNull(installedSiddur)
    }

    @Test
    fun `the switch is kept for the next start, and travels with the preferences`() {
        val stored = Properties()
        AppSettings(PropertiesSettings(stored)).setOpenSiddurEnabled(true)
        // Another start that never turned it on
        AppSettings(PropertiesSettings(Properties()))
        assertNull(installedSiddur)

        val restarted = AppSettings(PropertiesSettings(stored))
        assertTrue(restarted.isOpenSiddurEnabled())
        assertNotNull(installedSiddur)
        assertEquals("true", restarted.exportPortablePreferences()["open_siddur"])

        restarted.clearAll()
        assertNull(installedSiddur)
    }

    @Test
    fun `Iyar's days are ordinary weekdays until a custom is picked, and the custom is kept by name`() {
        val settings = AppSettings(PropertiesSettings(Properties()))
        assertEquals(IsraeliPrayerCustom.ORDINARY_WEEKDAY, israeliPrayerCustomOf(settings.getSiddurIsraeliPrayerCustom()))

        settings.setSiddurIsraeliPrayerCustom(IsraeliPrayerCustom.DAYTIME_HALLEL.name)
        assertEquals(IsraeliPrayerCustom.DAYTIME_HALLEL, israeliPrayerCustomOf(settings.siddurIsraeliPrayerCustomFlow.value))
        // A custom this version does not know
        assertEquals(IsraeliPrayerCustom.ORDINARY_WEEKDAY, israeliPrayerCustomOf("gone"))
    }
}

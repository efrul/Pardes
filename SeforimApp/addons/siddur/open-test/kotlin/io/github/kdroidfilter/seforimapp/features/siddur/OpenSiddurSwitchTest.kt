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
    fun `the custom of Iyar's days is kept by name, and an unknown one reads as the first`() {
        val settings = AppSettings(PropertiesSettings(Properties()))
        assertEquals(IsraeliPrayerCustom.NO_TACHANUN, israeliPrayerCustomOf(settings.getSiddurIsraeliPrayerCustom()))

        settings.setSiddurIsraeliPrayerCustom(IsraeliPrayerCustom.DAYTIME_HALLEL.name)
        assertEquals(IsraeliPrayerCustom.DAYTIME_HALLEL, israeliPrayerCustomOf(settings.siddurIsraeliPrayerCustomFlow.value))
        assertEquals(IsraeliPrayerCustom.NO_TACHANUN, israeliPrayerCustomOf("gone"))
    }
}

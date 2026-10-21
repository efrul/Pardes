package io.github.kdroidfilter.seforimapp.branding

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

/**
 * Pardes for desktop's identity (branding/pardes): the values the app reads where upstream has Zayit's
 * (branding/zayit has those). Pardes keeps its data and preferences apart from an installed Zayit's.
 */
object Brand {
    /** The data folder's id (FileKit): the library, notes, settings and sessions live under it. */
    const val APP_ID = "io.github.efrul.pardes"

    /** The app's preferences (java.util.prefs): its own node, where Zayit uses the root. */
    fun settings(): Settings = PreferencesSettings(Preferences.userRoot().node("io/github/efrul/pardes"))
}

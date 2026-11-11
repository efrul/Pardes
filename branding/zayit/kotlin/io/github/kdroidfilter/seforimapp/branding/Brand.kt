package io.github.kdroidfilter.seforimapp.branding

import com.russhwolf.settings.Settings

/** Upstream Zayit's identity (branding/zayit, -Pseforimapp.brand=zayit): the values upstream's own code has. */
object Brand {
    /** The data folder's id (FileKit). */
    const val APP_ID = "io.github.kdroidfilter.seforimapp"

    /** The app's preferences: java.util.prefs' user root. */
    fun settings(): Settings = Settings()

    /** No open smart siddur: upstream's siddur is its own (addons/siddur/enabled) or none. */
    const val OPEN_SIDDUR = false
}

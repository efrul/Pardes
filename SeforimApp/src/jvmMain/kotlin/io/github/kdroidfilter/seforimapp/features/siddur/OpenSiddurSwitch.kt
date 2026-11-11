package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings

/**
 * The open smart siddur's switch. The siddur built on SeforimOpenSiddur's engine (addons/siddur/open, siddur-open) is
 * experimental, so it is shown only once turned on in Settings › כללי; until then [installedSiddur] is null there and
 * the app shows no siddur, as a build without one.
 *
 * [installedSiddur] is read in composition and where there is no graph to take the settings from, so [shown] mirrors
 * the preference ([AppSettings.isOpenSiddurEnabled]) as a snapshot state: what shows the siddur follows the switch at
 * once. [AppSettings] alone writes it.
 */
object OpenSiddurSwitch {
    var shown: Boolean by mutableStateOf(false)
        internal set
}

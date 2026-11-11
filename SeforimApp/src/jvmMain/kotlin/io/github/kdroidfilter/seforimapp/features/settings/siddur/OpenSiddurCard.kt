package io.github.kdroidfilter.seforimapp.features.settings.siddur

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.branding.Brand
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingCard
import io.github.kdroidfilter.seforimapp.features.siddur.OpenSiddurSwitch
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_open_siddur
import seforimapp.seforimapp.generated.resources.settings_open_siddur_description

/**
 * Settings › כללי: the experimental smart siddur's switch ([OpenSiddurSwitch]), off until the user turns it on. Its
 * description credits the engine and its sources. Nothing in a build that carries no open siddur.
 */
@Composable
internal fun OpenSiddurCard() {
    if (!Brand.OPEN_SIDDUR) return
    val appSettings = LocalAppGraph.current.appSettings
    SettingCard(
        title = Res.string.settings_open_siddur,
        description = Res.string.settings_open_siddur_description,
        checked = OpenSiddurSwitch.shown,
        onCheckedChange = appSettings::setOpenSiddurEnabled,
    )
}

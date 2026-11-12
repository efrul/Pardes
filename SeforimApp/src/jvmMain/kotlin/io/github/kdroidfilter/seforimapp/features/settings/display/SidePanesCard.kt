package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingCard
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_side_panes_full_height
import seforimapp.seforimapp.generated.resources.settings_side_panes_full_height_description

/**
 * Settings › תצוגה: whether the reader's side panes run the window's whole height and the bottom one sits under the text
 * alone (SidePanesFullHeightOrder), or the bottom one runs under the text and the side panes, as Zayit has it (off).
 */
@Composable
internal fun SidePanesCard() {
    val appSettings = LocalAppGraph.current.appSettings
    val fullHeight by appSettings.sidePanesFullHeightFlow.collectAsState()
    SettingCard(
        title = Res.string.settings_side_panes_full_height,
        description = Res.string.settings_side_panes_full_height_description,
        checked = fullHeight,
        onCheckedChange = appSettings::setSidePanesFullHeight,
    )
}

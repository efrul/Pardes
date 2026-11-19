package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingCard
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_show_home_logo
import seforimapp.seforimapp.generated.resources.settings_show_home_logo_description

// Pardes (request 67 of docs/DESKTOP_STAGE3_PLAN.md): "אפשרות בהגדרות תצוגה להסתיר את הכיתוב הגדול של פרדס בדף הבית".

/** Whether the Home shows the app's big name above its search bar (HomeView's logo): one setting, every window. */
object HomeLogoSettings : SettingsGroup("home_logo") {
    val shown by boolean(true)
}

/** Settings › תצוגה: the Home's big name shown or hidden, after the wallpaper's card, its sibling. */
@Composable
internal fun HomeLogoCard() {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(HomeLogoSettings.shown).collectAsState()
    SettingCard(
        title = Res.string.settings_show_home_logo,
        description = Res.string.settings_show_home_logo_description,
        checked = shown,
        onCheckedChange = { appSettings[HomeLogoSettings.shown] = it },
    )
}

package io.github.kdroidfilter.seforimapp.features.settings.display

import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup

// Pardes (request 67 of docs/DESKTOP_STAGE3_PLAN.md): the setting of HomeLogoCard.kt's card.

/** Whether the Home shows the app's big name above its search bar (HomeView's logo): one setting, every window. */
object HomeLogoSettings : SettingsGroup("home_logo") {
    val shown by boolean(true)
}

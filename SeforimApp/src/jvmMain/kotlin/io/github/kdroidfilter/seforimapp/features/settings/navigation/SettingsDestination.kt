package io.github.kdroidfilter.seforimapp.features.settings.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface SettingsDestination {
    @Serializable
    data object General : SettingsDestination

    @Serializable
    data object Profile : SettingsDestination

    @Serializable
    data object Fonts : SettingsDestination

    @Serializable
    data object Display : SettingsDestination

    @Serializable
    data object About : SettingsDestination

    @Serializable
    data object Conditions : SettingsDestination

    @Serializable
    data object Data : SettingsDestination

    // The daf view's settings, in a page of their own
    @Serializable
    data object Daf : SettingsDestination
}

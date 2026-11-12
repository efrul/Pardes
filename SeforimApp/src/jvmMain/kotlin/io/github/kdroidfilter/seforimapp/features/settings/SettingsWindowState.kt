package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination

// Window-level settings state: only controls visibility of the Settings window.
data class SettingsWindowState(
    val isVisible: Boolean = false,
    val initialDestination: SettingsDestination? = null,
    // Window that was focused when the settings were opened
    val ownerWindowId: String? = null,
    // Pardes: how many times the open settings were asked for again; their window comes to the front at each one
    val reopened: Int = 0,
)

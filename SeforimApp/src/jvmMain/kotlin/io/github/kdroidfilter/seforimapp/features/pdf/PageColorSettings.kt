package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup

// Pardes (request 42 of docs/DESKTOP_STAGE3_PLAN.md): the setting of PageColors.kt's button.

/** The colours of pages drawn as images in the dark theme. */
object PageColorSettings : SettingsGroup("page_colors") {
    /** Whether pages keep their original colours in the dark theme, rather than its text on its panel; in every tab. */
    val original by boolean(false)
}

package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup

// Pardes (request 63 of the list after the third testers' build, docs/DESKTOP_STAGE3_PLAN.md): the setting of
// PageFit.kt's button.

/** Whether a whole page is fitted in the view ("עמוד שלם"), for every tab: a daf, a tractate's scan, a PDF book. */
object PageFitSettings : SettingsGroup("page_fit") {
    val whole by boolean(false)
}

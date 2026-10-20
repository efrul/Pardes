package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.CONDITIONS_MARKDOWN

/**
 * Desktop AboutSettingsScreen, on its own like desktop's AccentMarkdownView: a section on Pardes, then its thanks.
 * Desktop's files/ABOUT.md is left out: it is Zayit's author on Zayit, in his first person and inviting readers to
 * contact him, which in an app he did not make would read as his voice. Its thanks are kept, in this app's words.
 */
@Composable
internal fun AboutSettings(wide: Boolean) {
    SettingsColumn(wide) { MarkdownText(ABOUT_MD) }
}

/**
 * Desktop ConditionsSettingsScreen: CONDITIONS_MARKDOWN, the same text the onboarding's licence step shows. Its AGPL
 * link opens the licence bundled in the APK.
 */
@Composable
internal fun TermsSettings(wide: Boolean) {
    BundledLicenceLinks { SettingsColumn(wide) { MarkdownText(CONDITIONS_MARKDOWN.trimIndent()) } }
}

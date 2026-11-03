package io.github.kdroidfilter.seforimapp.features.settings.fonts

sealed interface FontsSettingsEvents {
    data class SetBookFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetCommentaryFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetTargumFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetSourceFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetDafGemaraFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetDafCommentaryFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetDafMarkUnsure(
        val enabled: Boolean,
    ) : FontsSettingsEvents

    data object ResetToDefaults : FontsSettingsEvents
}

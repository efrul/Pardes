package io.github.kdroidfilter.seforimapp.features.settings.fonts

/** What the last font added came to: added, with Hebrew letters or without, or a file that is not a font. */
internal sealed interface FontAddResult {
    data class Added(
        val name: String,
        val hebrew: Boolean,
    ) : FontAddResult

    data class Failed(
        val reason: String,
    ) : FontAddResult
}

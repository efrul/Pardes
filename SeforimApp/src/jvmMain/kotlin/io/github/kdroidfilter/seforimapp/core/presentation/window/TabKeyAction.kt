package io.github.kdroidfilter.seforimapp.core.presentation.window

/** What a tab shortcut does ([tabKeyAction]): select the tab at [index], or move the selected tab there. */
internal sealed interface TabKeyAction {
    val index: Int

    data class Select(
        override val index: Int,
    ) : TabKeyAction

    data class Move(
        override val index: Int,
    ) : TabKeyAction
}

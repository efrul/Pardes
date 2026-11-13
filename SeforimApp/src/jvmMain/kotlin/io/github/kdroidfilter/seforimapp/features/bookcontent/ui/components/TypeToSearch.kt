package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow

/**
 * Typing over a side pane: with the pointer over the table of contents or the book tree, a letter or digit typed that no
 * text field took (the window's own key handler, MainAppWindow) opens the pane's search with it and gives the search the
 * focus, without a click on its button first. The keys of shortcuts (Ctrl, Alt, Meta), the arrows and the others are
 * left as they were. Only a typed character counts (KeyEventType.Unknown, what a text field inserts), so a key's
 * character is the keyboard layout's own, Hebrew included.
 */
object TypeToSearch {
    // The pane under the pointer: its window and what it does with the text typed
    private var target: Pair<String, (String) -> Unit>? = null

    /** [event], typed in the window [windowId], given to the pane under the pointer there; whether it took it. */
    fun type(
        event: KeyEvent,
        windowId: String,
    ): Boolean {
        if (event.type != KeyEventType.Unknown) return false
        return route(textOf(event.utf16CodePoint, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed), windowId)
    }

    /** The text a typed character puts in a search: a letter or digit with no Ctrl, Alt or Meta; null otherwise. */
    internal fun textOf(
        codePoint: Int,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean,
    ): String? = if (ctrl || alt || meta || !Character.isLetterOrDigit(codePoint)) null else String(Character.toChars(codePoint))

    /** [text] given to the pane under the pointer in the window [windowId], if any; whether it was. */
    internal fun route(
        text: String?,
        windowId: String,
    ): Boolean {
        val (window, onType) = target ?: return false
        if (text == null || window != windowId) return false
        onType(text)
        return true
    }

    /** While a pane of the window [windowId] is under the pointer, text typed there goes to [onType]; null: none is. */
    internal fun point(
        windowId: String,
        onType: ((String) -> Unit)?,
    ) {
        target = onType?.let { windowId to it }
    }
}

/** While the pointer is over the pane whose hover [interactionSource] tells, text typed in its window goes to [onType]. */
@Composable
fun TypeToSearchTarget(
    interactionSource: InteractionSource,
    onType: (String) -> Unit,
) {
    val windowId = LocalOpenWindow.current.id
    val hovered by interactionSource.collectIsHoveredAsState()
    val currentOnType by rememberUpdatedState(onType)
    DisposableEffect(hovered, windowId) {
        if (hovered) TypeToSearch.point(windowId) { currentOnType(it) }
        onDispose { if (hovered) TypeToSearch.point(windowId, null) }
    }
}

/** [text] typed into the pane's search, which takes the focus; the search opened first ([open]) unless [isOpen]. */
fun PaneSearchFocus.type(
    text: String,
    isOpen: Boolean,
    open: () -> Unit,
) {
    typed += text
    requested = true
    if (!isOpen) open()
}

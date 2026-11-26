package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import java.util.concurrent.ConcurrentHashMap

// Pardes (request 94 of docs/DESKTOP_STAGE3_PLAN.md: "מקש קיצור למעבר בין תצוגת ספר לרגיל"): Ctrl or Cmd + D shows the
// tab's book as its daf (a tractate's page, another book's own pages) and back as its text. The key is the end bar's
// daf button's: what a click on it does, for the books that have it.

/** The daf button's click of each reader tab, by the tab's id, set while the button is shown: a book with no daf has no key. */
@Inject
@SingleIn(AppScope::class)
class DafKeys {
    private val clicks = ConcurrentHashMap<String, () -> Unit>()

    internal fun set(
        tabId: String,
        click: () -> Unit,
    ) {
        clicks[tabId] = click
    }

    internal fun clear(
        tabId: String,
        click: () -> Unit,
    ) {
        clicks.remove(tabId, click)
    }

    /** Whether [event] is the daf's key, taken by the daf button of the tab [tabId]. */
    fun press(
        event: KeyEvent,
        tabId: String?,
    ): Boolean = isDafKey(event) && click(tabId)

    /** The click of the daf button of the tab [tabId]; false with none shown. */
    internal fun click(tabId: String?): Boolean {
        val click = clicks[tabId ?: return false] ?: return false
        click()
        return true
    }
}

/** Ctrl or Cmd + D pressed, with nothing else held. */
internal fun isDafKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key == Key.D &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        !event.isShiftPressed &&
        !event.isAltPressed

/** The daf button of the tab [tabId] is shown: its [onClick] is the daf's key's too. */
@Composable
internal fun DafKey(
    tabId: String,
    onClick: () -> Unit,
) {
    val keys = LocalAppGraph.current.dafKeys
    val current by rememberUpdatedState(onClick)
    DisposableEffect(tabId, keys) {
        val click = { current() }
        keys.set(tabId, click)
        onDispose { keys.clear(tabId, click) }
    }
}

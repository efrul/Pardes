@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.SatellitePlacement
import dev.nucleusframework.window.tao.SatelliteWorkspace
import dev.nucleusframework.window.tao.WindowAnchor
import dev.nucleusframework.window.tao.WindowPositioner
import dev.nucleusframework.window.tao.satelliteTearOffHandle
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The commentators of one window that can be torn off its commentaries pane: those whose column header is on screen
 * there. [WindowPanes] declares a satellite for each (closed until it is torn off), since a tear-off drags a
 * registered satellite; a header is the grip ([tearOffHandle]) and has a button that opens it in a window of its own
 * ([openInWindow]).
 */
@Stable
class CommentatorTearOffs(
    private val workspace: SatelliteWorkspace,
    private val groupId: String,
    @StructuredScope private val scope: CoroutineScope,
) {
    /** How many headers show each commentator now; a commentator leaves only a moment after its last header. */
    private val shown = mutableStateMapOf<String, Int>()

    /** The commentators whose header is on screen in this window. */
    val names: Set<String> get() = shown.keys

    /** A header of [name] came on screen. */
    fun show(name: String) {
        shown[name] = (shown[name] ?: 0) + 1
    }

    /**
     * A header of [name] left the screen. Forgotten a moment later rather than at once: a commentator torn off leaves
     * the commentaries pane in the same frame it joins the tab's split-off commentators, and its satellite must not
     * leave the composition in between, which would take down the window it just opened in.
     */
    fun hide(name: String) {
        scope.launch {
            delay(FORGET_DELAY_MS)
            val left = (shown[name] ?: 0) - 1
            if (left > 0) shown[name] = left else shown.remove(name)
        }
    }

    /** The satellite [name]'s pane is in this window. */
    fun satelliteId(name: String): String = ReaderPane.Commentator(name).idIn(groupId)

    /** Makes an element [name]'s grip: dragged, it opens [name]'s pane where it is dropped. */
    fun tearOffHandle(name: String): Modifier = Modifier.satelliteTearOffHandle(workspace, satelliteId(name))

    /**
     * Opens [name]'s pane in a window of its own, as the panes' "open in a separate window" does: over the middle of
     * the window, or where it floated last.
     */
    fun openInWindow(name: String) {
        val id = satelliteId(name)
        workspace.undock(id, FloatingInWindowMiddle)
        workspace.open(id)
    }

    private companion object {
        const val FORGET_DELAY_MS = 1_000L

        val FloatingInWindowMiddle =
            SatellitePlacement.Floating(
                positioner = WindowPositioner(parentAnchor = WindowAnchor.Center, childAnchor = WindowAnchor.Center),
                size = DpSize(360.dp, 520.dp),
            )
    }
}

/** The tear-offs of the window whose pane this is; null outside a reader pane. */
val LocalCommentatorTearOffs = staticCompositionLocalOf<CommentatorTearOffs?> { null }

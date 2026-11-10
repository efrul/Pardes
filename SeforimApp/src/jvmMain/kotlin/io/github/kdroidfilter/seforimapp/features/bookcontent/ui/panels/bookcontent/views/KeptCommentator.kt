package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * The commentator the reader is on in the commentaries' pages, kept from line to line and from passage to passage: a
 * line he does not comment on shows another, and the next one he comments on shows him again. He is the reader's own
 * choice (a page turned, a commentator ticked; the first passage clicked, until then), never the pane's (a page left
 * because its commentators are not on the line, a commentator who comes back with a line, the commentator of the next
 * passage).
 */
@Stable
internal class KeptCommentator(
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** His name, as the pages list him; null until the reader chooses one. */
    var name: String? by mutableStateOf(null)
        private set

    // When the reader last turned the page himself: where it settles then is his choice
    private var turnedAt = Long.MIN_VALUE

    // The line the commentators were last listed for
    private var listedFor: Any? = null

    /** The reader chose [commentator]. */
    fun chose(commentator: String) {
        name = commentator
    }

    /** The reader turns the page: the commentator it settles on ([settledOn]) is his choice. */
    fun turns() {
        turnedAt = now()
    }

    /**
     * The commentator the pages follow once settled on the page that [first] opens, among those [shown]: [first] when the
     * reader turned to it, his choice from now on. When the pane moved by itself: the commentator of the passage
     * selected ([focus]), else the reader's when the line has him, else [first].
     */
    fun settledOn(
        first: String,
        shown: List<String>,
        focus: String?,
    ): String {
        val turned = now() - turnedAt in 0 until TURN_MS
        turnedAt = Long.MIN_VALUE
        if (turned) {
            name = first
            return first
        }
        return focus?.takeIf { it in shown } ?: among(shown) ?: first
    }

    /** The reader's commentator when he is among [shown] (a line's, a passage's). */
    fun among(shown: List<String>): String? = name?.takeIf { it in shown }

    /** The commentators are listed for [line] now. */
    fun listedOn(line: Any?) {
        listedFor = line
    }

    /**
     * Whether the commentators listed changed on the [line] they were listed for (the reader ticked one), and not with
     * the line: a commentator who comes back with a line is no choice of the reader's.
     */
    fun tickedOn(line: Any?): Boolean = (line == listedFor).also { listedFor = line }

    private companion object {
        // A page turned settles well within this; a turn that never settled is forgotten after it
        const val TURN_MS = 3_000L
    }
}

/**
 * Keeps the pages on the reader's commentator: the commentator the pages follow ([onFollow]) is [kept]'s whenever the
 * [line]'s commentators ([shown]) have him and no passage is selected (its commentator, [focus], is followed then, and
 * is the reader's first choice when he has made none).
 */
@Composable
internal fun KeepCommentator(
    kept: KeptCommentator,
    shown: List<String>,
    line: Any?,
    focus: String?,
    onFollow: (String) -> Unit,
) {
    val follow by rememberUpdatedState(onFollow)
    LaunchedEffect(line) { kept.listedOn(line) }
    LaunchedEffect(focus) { if (focus != null && kept.name == null) kept.chose(focus) }
    LaunchedEffect(shown, kept.name, focus) { if (focus == null) kept.among(shown)?.let { follow(it) } }
}

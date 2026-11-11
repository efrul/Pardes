package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorDefaultState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorDefaults
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorSelection
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.commentators_default_on
import seforimapp.seforimapp.generated.resources.commentators_default_on_hint
import seforimapp.seforimapp.generated.resources.commentators_default_replace
import seforimapp.seforimapp.generated.resources.commentators_default_set
import seforimapp.seforimapp.generated.resources.commentators_default_set_hint

/** The reader's default commentators ([CommentatorDefaults]) as the commentators list offers them. */
@Stable
class CommentatorsDefault(
    val state: CommentatorDefaultState = CommentatorDefaultState(),
    val onSet: () -> Unit = {},
    val onClear: () -> Unit = {},
)

/**
 * The reader's default for the book [bookId], whose commentators are [selection] now: read again when the book, its
 * commentators or a default changes. Setting and letting go are the tab's events.
 */
@Composable
fun rememberCommentatorsDefault(
    bookId: Long?,
    selection: CommentatorSelection,
    onEvent: (BookContentEvent) -> Unit,
): CommentatorsDefault {
    val defaults = LocalAppGraph.current.commentatorDefaults
    val revision by defaults.revision.collectAsState()
    val state by produceState(CommentatorDefaultState(), bookId, selection, revision) {
        value = bookId?.let { runSuspendCatching { defaults.stateOf(it, selection) }.getOrNull() } ?: CommentatorDefaultState()
    }
    val currentOnEvent by rememberUpdatedState(onEvent)
    return remember(state) {
        CommentatorsDefault(
            state = state,
            onSet = { currentOnEvent(BookContentEvent.CommentatorsDefaultSet) },
            onClear = { currentOnEvent(BookContentEvent.CommentatorsDefaultCleared) },
        )
    }
}

/**
 * The pin at the end of "כל המפרשים", as the star of the favorites: a click keeps the commentators checked as the
 * reader's default for the book and the books beside it; pinned, in the accent's colour, while they are what the book
 * shows, and a click then lets the default go. Its tooltip says what a click does and which books share it. Nothing
 * for a book that can have no default.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentatorsDefaultPin(
    default: CommentatorsDefault,
    modifier: Modifier = Modifier,
) {
    val state = default.state
    val sharedIn = state.sharedIn ?: return
    val title =
        stringResource(
            when {
                state.isShown -> Res.string.commentators_default_on
                state.isSet -> Res.string.commentators_default_replace
                else -> Res.string.commentators_default_set
            },
        )
    val hint =
        stringResource(
            if (state.isShown) Res.string.commentators_default_on_hint else Res.string.commentators_default_set_hint,
            sharedIn,
        )
    Tooltip(
        tooltip = {
            Column(modifier = Modifier.widthIn(max = TOOLTIP_WIDTH), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 13.sp)
                Text(hint, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
            }
        },
    ) {
        val accent = JewelTheme.globalColors.outlines.focused
        IconButton(onClick = if (state.isShown) default.onClear else default.onSet, modifier = modifier.size(PIN_BUTTON)) {
            // Pinned: the accent's colour on a wash of it, as a chosen tab of the search
            Box(
                modifier =
                    Modifier
                        .size(PIN_BUTTON)
                        .background(if (state.isShown) accent.copy(alpha = PINNED_WASH) else Color.Transparent, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    key = if (state.isShown) AllIconsKeys.General.PinSelected else AllIconsKeys.General.Pin,
                    contentDescription = title,
                    modifier = Modifier.size(16.dp),
                    tint = if (state.isShown) accent else Color.Unspecified,
                )
            }
        }
    }
}

private val TOOLTIP_WIDTH = 260.dp
private val PIN_BUTTON = 24.dp
private const val PINNED_WASH = 0.16f

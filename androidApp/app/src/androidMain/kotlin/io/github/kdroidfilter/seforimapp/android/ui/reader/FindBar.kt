package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimapp.icons.MaterialSymbolsMagicButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val LINE_BREAKS = Regex("[\r\n]+")

/** Longest wait for the window's focus (a menu's popup closing) before the find field asks for the cursor anyway. */
private const val WINDOW_FOCUS_WAIT_MS = 1_500L

/** Requests for the cursor, a couple of frames apart, before the find field gives up on it. */
private const val FOCUS_ATTEMPTS = 4

/**
 * The find-in-page bar (desktop core/presentation/components/FindInPageBar.kt): the field, the "3/861" counter (red
 * when nothing matches, hidden while computing), ▲ ▼ and ✕. Enter steps to the next match, Shift+Enter back, Esc
 * closes; typing again replaces the query, selected when the bar opens. Seeded by a search result, the bar counts
 * the lines the search finds in the book, as desktop's smart mode does (see [FindState.smart]). [onToggleSmart] shows
 * desktop's toggle of that mode ("חיפוש חכם לפי משמעות"), as Pardes desktop shows it with its semantic search; null
 * leaves it out, as desktop's community build does.
 */
@Composable
fun FindBar(
    find: FindState,
    onQuery: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onToggleSmart: (() -> Unit)? = null,
) {
    val c = Zayit.colors
    val focus = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(find.query, TextRange(0, find.query.length))) }
    LaunchedEffect(find.query) {
        if (find.query != field.text) field = TextFieldValue(find.query, TextRange(0, find.query.length))
    }
    // 0: opened without asking for the cursor (a search result's seeded bar). Otherwise the field takes the focus and
    // the keyboard from whatever held them (a note being written), and checks it did: one request on the bar's first
    // frame left the cursor, and the first letters typed, in the note.
    LaunchedEffect(find.focusRequest) {
        if (find.focusRequest == 0) return@LaunchedEffect
        field = field.copy(selection = TextRange(0, field.text.length))
        // Opened from a menu ("עוד", ⋮), the menu's popup holds the window's focus until it is gone, and the system
        // hands the keyboard only to a field of the focused window: the request waits for it
        withTimeoutOrNull(WINDOW_FOCUS_WAIT_MS) { snapshotFlow { windowInfo.isWindowFocused }.first { it } }
        repeat(FOCUS_ATTEMPTS) {
            runCatching { focus.requestFocus() }
            repeat(2) { withFrameNanos { } } // the focus change and its callbacks land with the next layout
            if (fieldFocused) {
                // The keyboard for this field, also when Back had hidden it while the note kept the focus
                keyboard?.show()
                return@LaunchedEffect
            }
        }
    }
    val results = find.results?.takeIf { find.query.trim().length >= 2 && find.resultsQuery == find.query }
    val count = results?.occurrences
    val shape = RoundedCornerShape(10.dp)
    Row(
        // The bar takes the touches that miss its field and buttons (the phone band's padding, the gaps): they no longer
        // fall through to the text under it, a tap selecting a hidden line, a long press its text
        Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }.then(modifier)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.panelBorder, shape)
            .heightIn(min = 48.dp)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Under 300dp (Jelly Star) the icon, the divider and the buttons' margins give the field its room: else the
        // counter and the three buttons left it about nothing once a query matched
        if (!compact) {
            Icon(Icons.Outlined.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (field.text.isEmpty()) Text("הקלד כדי לחפש בעמוד...", color = c.textDisabled, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                field,
                { value ->
                    // A single line: pasted line breaks ("העתק עם מקור" ends with "\n\n(<source>)") become spaces, so
                    // the whole query is shown and searched, as desktop's single-line field shows them
                    val flat = value.text.replace(LINE_BREAKS, " ")
                    field = if (flat == value.text) value else TextFieldValue(flat, TextRange(flat.length))
                    onQuery(flat)
                },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = ZayitFonts.ui),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { fieldFocused = it.isFocused }.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (e.isShiftPressed) onPrevious() else onNext()
                            true
                        }
                        Key.Escape -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                },
            )
        }
        if (count != null && !(compact && field.text.isEmpty())) {
            Spacer(Modifier.width(6.dp))
            Text(
                find.current?.takeIf { it in results }?.let { "${it.position}/$count" } ?: count.toString(),
                color = if (count == 0) c.error else c.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        val button = if (compact) Modifier.size(36.dp) else Modifier
        if (!compact) Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(c.divider))
        if (onToggleSmart != null) {
            ZIconButton(MaterialSymbolsMagicButton, "חיפוש חכם לפי משמעות", onToggleSmart, button, tint = if (find.smart) c.accent else c.textSecondary, selected = find.smart)
        }
        // Up/down, not left/right: unambiguous in a right-to-left layout
        ZIconButton(Icons.Outlined.KeyboardArrowUp, "התוצאה הקודמת", onPrevious, button, tint = if (count == 0) c.textDisabled else c.text)
        ZIconButton(Icons.Outlined.KeyboardArrowDown, "התוצאה הבאה", onNext, button, tint = if (count == 0) c.textDisabled else c.text)
        ZIconButton(Icons.Outlined.Close, "סגירת החיפוש", onClose, button, tint = c.textSecondary)
    }
}

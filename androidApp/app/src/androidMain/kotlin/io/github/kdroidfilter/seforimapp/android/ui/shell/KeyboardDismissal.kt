package io.github.kdroidfilter.seforimapp.android.ui.shell

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long the second pass waits for the window to get its focus back (a cold start under the emulator is slow). */
private const val WINDOW_FOCUS_WAIT_MS = 5_000L

/**
 * Drops a focused text field and hides the soft keyboard each time [request] changes: something from outside the app
 * (a zayit:// link, a launcher shortcut, a launcher widget's tap) is about to open a page or a tab.
 *
 * Why: opened from the ⋮ menu, a page comes after the menu's popup window took the window focus, and the keyboard and
 * the Home bar's cursor handle went with it. A widget tap has no popup: the Home bar, focused when the app went to the
 * background, kept Gboard and its gold handle over the tool page. A hide asked while the window has no focus is
 * dropped, so the keyboard is hidden again once the focus is back. The field is let go only at the request: the app's
 * fields (value-based BasicTextField) keep their input session open while the window is in the background, so the
 * focused one is seen and dropped then, and cannot take the focus back. A field focused after it is one the page
 * opened focused itself (a new Home tab's bar with a hardware keyboard, as desktop's new tab): it keeps the focus.
 *
 * Only a text field lets go: the reader's focus for hardware keys (not an editor) stays, as through the menu.
 * Compose it before what opens the request (ZayitApp's link handling, MainActivity's widget launch): effects start in
 * composition order, so the field lets go before the page opens.
 */
@Composable
fun DismissKeyboardOnRequest(request: Int, window: Window) {
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    val view = LocalView.current
    LaunchedEffect(request) {
        if (request == 0) return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // The Compose view answers true while a text field holds the focus (its input session is open)
        if (view.onCheckIsTextEditor()) focusManager.clearFocus(force = true)
        controller.hide(WindowInsetsCompat.Type.ime())
        val focused = withTimeoutOrNull(WINDOW_FOCUS_WAIT_MS) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
        }
        if (focused != null) {
            // A frame on: after what the system shows by itself as the window gets its focus back
            withFrameNanos { }
            // A text editor now is the opened page's own (see above): its focus and keyboard are left alone
            if (!view.onCheckIsTextEditor()) controller.hide(WindowInsetsCompat.Type.ime())
        }
    }
}

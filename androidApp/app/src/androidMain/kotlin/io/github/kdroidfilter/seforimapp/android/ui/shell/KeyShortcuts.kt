package io.github.kdroidfilter.seforimapp.android.ui.shell

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Overlay

/**
 * Hardware-keyboard shortcuts (tablets, DeX, Chromebooks). MainActivity forwards every key press here before the
 * views see it, so the shortcuts work whatever has the focus; ZayitApp installs the [handler] while it is shown.
 */
class KeyShortcuts {
    var handler: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean = event.action == KeyEvent.ACTION_DOWN && handler?.invoke(event) == true
}

/** What the shortcuts do that lives in ZayitApp's own state. */
class ShellKeyActions(
    val toggleTabSearch: () -> Unit,
    val toggleFullscreen: () -> Unit,
)

/**
 * The desktop window shortcuts (0.6.1 core/presentation/window/MainAppWindow.kt 350-470) and the zoom and find keys
 * of core/presentation/utils/WindowUtils.kt processKeyShortcuts, without the macOS variants. Ctrl+N makes a new
 * workspace, as Ctrl+Alt+N (one window on Android). Returns true when the key was used.
 */
fun handleShellShortcut(event: KeyEvent, appVm: AppViewModel, prefs: AppPrefs, actions: ShellKeyActions): Boolean {
    val ctrl = event.isCtrlPressed
    val shift = event.isShiftPressed
    val alt = event.isAltPressed
    val code = event.keyCode
    when {
        ctrl && shift && code == KeyEvent.KEYCODE_W -> appVm.closeAll()
        ctrl && shift && code == KeyEvent.KEYCODE_T -> appVm.reopenClosedTab()
        ctrl && code == KeyEvent.KEYCODE_W -> appVm.closeSelectedTab(repeat = event.repeatCount > 0)
        ctrl && code == KeyEvent.KEYCODE_TAB -> appVm.selectRelative(if (shift) -1 else 1)
        ctrl && shift && code == KeyEvent.KEYCODE_A -> actions.toggleTabSearch()
        ctrl && !shift && code == KeyEvent.KEYCODE_H -> appVm.showPage(Destination.History)
        ctrl && shift && code == KeyEvent.KEYCODE_O -> appVm.showPage(Destination.Favorites)
        ctrl && alt && code == KeyEvent.KEYCODE_DPAD_RIGHT -> appVm.switchWorkspaceRelative(1)
        ctrl && alt && code == KeyEvent.KEYCODE_DPAD_LEFT -> appVm.switchWorkspaceRelative(-1)
        ctrl && code == KeyEvent.KEYCODE_N -> appVm.createWorkspace()
        ctrl && code == KeyEvent.KEYCODE_T -> appVm.newHomeTab()
        alt && code == KeyEvent.KEYCODE_MOVE_HOME -> appVm.goHome()
        ctrl && code == KeyEvent.KEYCODE_COMMA -> if (appVm.state.value.overlays.lastOrNull() != Overlay.Settings) appVm.push(Overlay.Settings)
        code == KeyEvent.KEYCODE_F11 && !ctrl && !alt -> actions.toggleFullscreen()
        // Find-in-page of the current tab (desktop showFindBar): left alone where the tab has none.
        ctrl && code == KeyEvent.KEYCODE_F -> return appVm.requestFind()
        ctrl && code in ZOOM_IN -> prefs.setTextSize(prefs.textSize.value + TEXT_SIZE_INCREMENT)
        ctrl && code in ZOOM_OUT -> prefs.setTextSize(prefs.textSize.value - TEXT_SIZE_INCREMENT)
        else -> return false
    }
    return true
}

private val ZOOM_IN = setOf(KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_EQUALS)
private val ZOOM_OUT = setOf(KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT)

/** Desktop AppSettings.TEXT_SIZE_INCREMENT. */
private const val TEXT_SIZE_INCREMENT = 2f

/** The shortcuts as the system's keyboard-shortcuts helper (Meta+/) lists them, with desktop's labels. */
fun shellKeyboardShortcuts(): KeyboardShortcutGroup {
    val ctrl = KeyEvent.META_CTRL_ON
    val shift = KeyEvent.META_SHIFT_ON
    fun item(label: String, key: Int, meta: Int) = KeyboardShortcutInfo(label, key, meta)
    return KeyboardShortcutGroup(
        "פרדס",
        listOf(
            item("לשונית חדשה", KeyEvent.KEYCODE_T, ctrl),
            item("סגור לשונית", KeyEvent.KEYCODE_W, ctrl),
            item("סגור את כל הלשוניות", KeyEvent.KEYCODE_W, ctrl or shift),
            item("פתח מחדש לשונית שנסגרה", KeyEvent.KEYCODE_T, ctrl or shift),
            item("חיפוש בלשוניות ובהיסטוריה", KeyEvent.KEYCODE_A, ctrl or shift),
            item("הצג את כל ההיסטוריה", KeyEvent.KEYCODE_H, ctrl),
            item("הצג את כל המועדפים", KeyEvent.KEYCODE_O, ctrl or shift),
            item("חפש בעמוד", KeyEvent.KEYCODE_F, ctrl),
            item("הגדל טקסט", KeyEvent.KEYCODE_EQUALS, ctrl),
            item("הקטן טקסט", KeyEvent.KEYCODE_MINUS, ctrl),
            item("עבור לדף הבית", KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_ALT_ON),
            item("פתח הגדרות", KeyEvent.KEYCODE_COMMA, ctrl),
            item("מרחב חדש", KeyEvent.KEYCODE_N, ctrl or KeyEvent.META_ALT_ON),
        ),
    )
}

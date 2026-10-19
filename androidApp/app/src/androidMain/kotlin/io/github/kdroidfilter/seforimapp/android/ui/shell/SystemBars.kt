package io.github.kdroidfilter.seforimapp.android.ui.shell

import android.os.Build
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/** What is drawn under the status bar and under the navigation bar. */
data class SystemBarBackgrounds(val status: Color, val navigation: Color)

/** The backgrounds the screens shown declared, the last one declared winning. */
@Stable
class SystemBarsRegistry internal constructor() {
    internal val entries = mutableStateListOf<Pair<Any, SystemBarBackgrounds>>()

    val current: SystemBarBackgrounds? get() = entries.lastOrNull()?.second
}

val LocalSystemBars = staticCompositionLocalOf<SystemBarsRegistry?> { null }

/**
 * Declares what this screen draws under the system bars, while it is shown: their icons are drawn dark over a light
 * [status] or [navigation] background and light over a dark one (see [SystemBarsHost]).
 */
@Composable
fun SystemBarsOver(status: Color, navigation: Color) {
    val registry = LocalSystemBars.current ?: return
    val token = remember { Any() }
    val backgrounds = SystemBarBackgrounds(status, navigation)
    SideEffect {
        val i = registry.entries.indexOfFirst { it.first === token }
        if (i < 0) {
            registry.entries.add(token to backgrounds)
        } else if (registry.entries[i].second != backgrounds) {
            registry.entries[i] = token to backgrounds
        }
    }
    DisposableEffect(registry, token) { onDispose { registry.entries.removeAll { it.first === token } } }
}

/**
 * The system bars' icons follow the background of the screen shown ([SystemBarsOver]; the theme's ground meanwhile),
 * not the theme alone. They are set again whenever the window gets the focus back or the activity resumes: the system
 * may show the theme's appearance meanwhile (the cold start's splash window, a permission dialog, a SystemUI restart).
 * Setting the same appearance again costs nothing (the window is only laid out again when it changes).
 */
@Composable
fun SystemBarsHost(window: Window, content: @Composable () -> Unit) {
    val registry = remember { SystemBarsRegistry() }
    val c = Zayit.colors
    val shown = registry.current ?: SystemBarBackgrounds(c.ground, c.ground)
    val lightStatus = shown.status.luminance() > 0.5f
    val lightNavigation = shown.navigation.luminance() > 0.5f
    val applyBars by rememberUpdatedState {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightStatus
            isAppearanceLightNavigationBars = lightNavigation
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) window.navigationBarColor = navigationBarScrim(lightNavigation)
    }
    SideEffect { applyBars() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) applyBars() }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus) applyBars() }
        val tree = window.decorView.viewTreeObserver
        lifecycle.addObserver(observer)
        tree.addOnWindowFocusChangeListener(focus)
        // After the first frame too: the window is attached and shown by then
        window.decorView.post { applyBars() }
        onDispose {
            lifecycle.removeObserver(observer)
            window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focus)
        }
    }
    CompositionLocalProvider(LocalSystemBars provides registry, content = content)
}

/**
 * [SystemBarsOver] for a full-screen Dialog drawn under the system bars (decorFitsSystemWindows = false), called in
 * its content: such a dialog has a window of its own, which draws the bars' icons while it is shown, and
 * [SystemBarsHost] only sets the activity's. Left alone, the dialog's window takes them from the theme, which follows
 * the system's night mode rather than the app's.
 */
@Composable
fun DialogSystemBarsOver(status: Color, navigation: Color) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val lightStatus = status.luminance() > 0.5f
    val lightNavigation = navigation.luminance() > 0.5f
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightStatus
            isAppearanceLightNavigationBars = lightNavigation
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) window.navigationBarColor = navigationBarScrim(lightNavigation)
    }
}

/**
 * The navigation bar's colour below Android 10, where the bar is not transparent: enableEdgeToEdge paints it with the
 * scrim of the system's night mode, not the app's, so with the app dark on a light system its light icons vanished on
 * the light scrim. Here the scrim follows the background shown, as the icons do: androidx.activity's own two scrims
 * (EdgeToEdge.kt DefaultLightScrim and DefaultDarkScrim). From Android 10 on enableEdgeToEdge leaves the bar
 * transparent, and the system keeps the icons readable.
 */
private fun navigationBarScrim(light: Boolean): Int =
    if (light) android.graphics.Color.argb(0xe6, 0xff, 0xff, 0xff) else android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

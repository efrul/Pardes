package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import io.github.kdroidfilter.seforimapp.legacy.PardesApp
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import java.util.Locale

/**
 * What every screen shares: the theme of the settings (light or dark), a Hebrew right-to-left layout whatever the
 * phone's language (the main app's UI is Hebrew only), a keypad's Menu key opening the title bar's ⋮ menu, and a
 * coroutine scope that ends with the screen.
 */
abstract class BaseActivity : Activity() {
    protected val app: PardesApp get() = PardesApp.of(this)
    protected val prefs: Prefs get() = app.prefs
    protected lateinit var colors: Colors
    protected val scope: CoroutineScope = MainScope()

    /** The screen's title bar, whose ⋮ menu the Menu key opens. */
    protected var topBar: TopBar? = null
    private var darkAtCreate = false

    /**
     * Hebrew for the framework's own texts and widgets too (the text field's handles, the keyboard's direction), and
     * right to left: Configuration.setLocale sets the layout direction (API 17).
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(HEBREW)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        darkAtCreate = prefs.dark
        colors = if (darkAtCreate) Palette.dark else Palette.light
        setTheme(if (darkAtCreate) R.style.Theme_Pardes_Dark else R.style.Theme_Pardes)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(colors.ground))
        // The status bar takes the title bar's tone (the main app's SystemBarsOver) where its icons can be dark
        // (Android 6.0); before that it stays dark under white icons, and Android 4.4 cannot colour it at all.
        if (Build.VERSION.SDK_INT >= 23) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.barTone
            if (!colors.dark) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        } else if (Build.VERSION.SDK_INT >= 21) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.statusBarColor = 0xFF1E1F22.toInt()
        }
    }

    override fun setContentView(view: View) {
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        super.setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        // The theme was changed in Settings while this screen was behind it.
        if (prefs.dark != darkAtCreate) recreate()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // A keypad phone's left soft key: the ⋮ menu of the title bar.
        if (keyCode == KeyEvent.KEYCODE_MENU && topBar?.showMenu() == true) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    protected fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        @Suppress("DEPRECATION")
        val HEBREW: Locale = Locale("iw", "IL")
    }
}

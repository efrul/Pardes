package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * What every screen shares: the theme of the settings (light or dark), a Hebrew right-to-left layout whatever the
 * phone's language (the main app's UI is Hebrew only), a keypad's Menu key opening the title bar's ⋮ menu, a
 * coroutine scope that ends with the screen, and a screen built again on its library when that was closed behind it.
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

    /**
     * Set in onResume when the screen is about to be built again or to give way to the Home (the theme or its library
     * changed while it was behind another screen): its own onResume then does nothing more.
     */
    protected var rebuilding = false
        private set

    /**
     * The library this screen reads, once it has opened it; null for a screen that reads none. An install from files
     * closes the library before it writes (LegacyInstaller.releaseLibrary), and one that fails or is cancelled leaves it
     * closed: a screen left under the install screen would read through that closed connection (a book's blank lines).
     */
    protected open val heldLibrary: Library.Opened? get() = null

    /** Between onResume and onPause. */
    private var resumed = false

    override fun onResume() {
        super.onResume()
        resumed = true
        if (rebuilding) return
        // The theme was changed in Settings while this screen was behind it.
        if (prefs.dark != darkAtCreate) {
            rebuilding = true
            recreate()
            return
        }
        val held = heldLibrary
        if (held != null && held !== app.library.current) {
            rebuilding = true
            reopenLibrary(held)
        }
    }

    /**
     * [held] was closed behind this screen: the library is opened again off the main thread, and the screen goes on with
     * it ([onLibraryReopened]) when it is the same database. Otherwise (none left, another one, or an install running)
     * the Home says what there is, as after Settings' "חפש את הספרייה מחדש".
     */
    private fun reopenLibrary(held: Library.Opened) {
        scope.launch {
            val library = withContext(Dispatchers.IO) { runCatching { app.library.open() }.getOrNull() }
            if (isFinishing) return@launch
            // Left meanwhile: the next onResume looks again.
            if (!resumed) {
                rebuilding = false
                return@launch
            }
            if (library != null && library.file == held.file) {
                rebuilding = false
                onLibraryReopened(library)
            } else {
                startActivity(Intent(this@BaseActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_RELOAD, true))
                finish()
            }
        }
    }

    /**
     * Goes on with [library], the database this screen read, opened again: by default the screen is built again, its
     * place kept in its saved state; a screen whose state is only ids in the library takes it as it is.
     */
    protected open fun onLibraryReopened(library: Library.Opened) = recreate()

    override fun onPause() {
        resumed = false
        super.onPause()
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

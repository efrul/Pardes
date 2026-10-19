package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
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
 * phone's language (the main app's UI is Hebrew only), and a coroutine scope that ends with the screen.
 */
abstract class BaseActivity : Activity() {
    protected val app: PardesApp get() = PardesApp.of(this)
    protected val prefs: Prefs get() = app.prefs
    protected lateinit var colors: Colors
    protected val scope: CoroutineScope = MainScope()
    private var darkAtCreate = false

    /**
     * Hebrew for the framework's own texts and widgets too (dialog buttons, menus, the text field's handles), and
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    protected fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        val HEBREW: Locale = Locale("iw", "IL")
    }
}

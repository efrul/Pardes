package io.github.kdroidfilter.seforimapp.legacy

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs

/** The app's singletons: the library and the preferences. */
class PardesApp : Application() {
    lateinit var library: Library
        private set
    lateinit var prefs: Prefs
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Below Android 5.0 a second classes.dex is loaded by the app itself (a no-op when the release has one dex).
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        library = Library(this)
    }

    companion object {
        fun of(context: Context): PardesApp = context.applicationContext as PardesApp
    }
}

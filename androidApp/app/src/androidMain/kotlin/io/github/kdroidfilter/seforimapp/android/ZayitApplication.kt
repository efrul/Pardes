package io.github.kdroidfilter.seforimapp.android

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.data.ReadingHistory
import io.github.kdroidfilter.seforimapp.android.data.UserDataStore
import io.github.kdroidfilter.seforimapp.android.search.SearchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** App-wide singletons: the Android counterpart of desktop's AppGraph (framework/di, Metro). */
class ZayitApplication : Application() {
    lateinit var libraryStore: LibraryStore
        private set
    lateinit var prefs: AppPrefs
        private set

    /** Work that must outlive a screen (saving the session, writing history), never the main thread. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** user_settings.db: notes, highlights, `userData.history` and `userData.favorites`. Opened off the main thread first. */
    val userData: UserDataStore by lazy { UserDataStore(this) }

    /** The reader's bridge to the history and favorites (desktop recording rules). */
    val history: ReadingHistory by lazy { ReadingHistory(this) }

    /** The desktop Lucene search on the library's prebuilt indexes; opened on first use, off the main thread. */
    val search: SearchService by lazy { SearchService(libraryStore) }

    private val _mediaChanges = MutableStateFlow(0)

    /** Counts memory cards put in or taken out, for the screens that list or wait for a card. */
    val mediaChanges: StateFlow<Int> = _mediaChanges.asStateFlow()

    /** Settings asked for the library update flow (an update from files, or a full download) over the running app. */
    val manualLibraryUpdate = MutableStateFlow(false)

    private var startedActivities = 0

    /** An activity of the app is on screen. */
    val isInForeground: Boolean get() = startedActivities > 0

    override fun onCreate() {
        super.onCreate()
        libraryStore = LibraryStore(this)
        prefs = AppPrefs(this)
        registerActivityLifecycleCallbacks(ForegroundTracker())
        registerCardReceiver()
    }

    /**
     * Starts the app again from its launcher activity (a fresh process: every handle on the library is reopened), or,
     * with no activity on screen, only ends the process, as Android may not let a background app open an activity.
     */
    fun restart() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (isInForeground && launch != null) startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }

    /** Set once a card's removal has started the app again: the same removal's later broadcasts do nothing more. */
    private val restartingForCard = AtomicBoolean(false)

    /**
     * Android's media broadcasts for memory cards. Any of them re-reads the places the library can be in. When the card
     * this process opened the library from is being taken out, the library is closed first (Android ends a process that
     * keeps files open on a card it unmounts) and the app starts again, once, on the screen that asks for the card. A
     * process that never opened the library there (one showing that screen, or the onboarding) keeps running.
     */
    private fun registerCardReceiver() {
        val filter = IntentFilter().apply {
            CARD_GONE_ACTIONS.forEach(::addAction)
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addDataScheme("file")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val volume = intent.data?.path?.let(::File)
                val holdsLibrary = volume != null && intent.action in CARD_GONE_ACTIONS &&
                    libraryStore.openedFrom?.path?.startsWith(volume.path + "/") == true
                libraryStore.refreshLocations()
                _mediaChanges.update { it + 1 }
                if (!holdsLibrary || !restartingForCard.compareAndSet(false, true)) return
                val pending = goAsync()
                appScope.launch {
                    runCatching { search.close() }
                    runCatching { libraryStore.close() }
                    pending.finish()
                    restart()
                }
            }
        }
        // System broadcasts reach a receiver that is not exported.
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private inner class ForegroundTracker : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        val CARD_GONE_ACTIONS = setOf(
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
            Intent.ACTION_MEDIA_REMOVED,
        )
    }
}

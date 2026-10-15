package io.github.kdroidfilter.seforimapp.android

import android.app.Application
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.data.ReadingHistory
import io.github.kdroidfilter.seforimapp.android.data.UserDataStore
import io.github.kdroidfilter.seforimapp.android.search.SearchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    override fun onCreate() {
        super.onCreate()
        libraryStore = LibraryStore(this)
        prefs = AppPrefs(this)
    }
}

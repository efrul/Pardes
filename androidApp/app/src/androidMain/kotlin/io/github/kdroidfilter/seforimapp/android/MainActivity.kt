package io.github.kdroidfilter.seforimapp.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.android.appwidget.WidgetLaunch
import io.github.kdroidfilter.seforimapp.android.appwidget.ZayitWidgets
import io.github.kdroidfilter.seforimapp.android.appwidget.open
import io.github.kdroidfilter.seforimapp.android.appwidget.widgetLaunchOf
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.data.SavedSession
import io.github.kdroidfilter.seforimapp.android.data.SessionStore
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateService
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.database.DatabaseUpdateReason
import io.github.kdroidfilter.seforimapp.android.ui.database.DatabaseUpdateScreen
import io.github.kdroidfilter.seforimapp.android.ui.database.LibraryCardMissingScreen
import io.github.kdroidfilter.seforimapp.android.ui.database.databaseUpdateReason
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.OnboardingFlow
import io.github.kdroidfilter.seforimapp.android.ui.shell.DismissKeyboardOnRequest
import io.github.kdroidfilter.seforimapp.android.ui.shell.KeyShortcuts
import io.github.kdroidfilter.seforimapp.android.ui.shell.LauncherShortcuts
import io.github.kdroidfilter.seforimapp.android.ui.shell.ShellEntry
import io.github.kdroidfilter.seforimapp.android.ui.shell.SystemBarsHost
import io.github.kdroidfilter.seforimapp.android.ui.shell.ZayitApp
import io.github.kdroidfilter.seforimapp.android.ui.shell.shellKeyboardShortcuts
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitTheme
import io.github.kdroidfilter.seforimapp.android.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Put on an intent once its link or widget tap was opened. */
private const val EXTRA_HANDLED = "io.github.kdroidfilter.seforimapp.android.extra.HANDLED"

/** Where the last request opened is kept with its task's id (MainActivity.handledBefore). */
private const val LAUNCH_PREFS = "zayit_launch"
private const val KEY_HANDLED_REQUEST = "handled_request"

/** What the startup check found (desktop main.kt computeStartupState). */
private sealed interface Startup {
    data class Ready(val session: SavedSession?) : Startup

    data class Update(val reason: DatabaseUpdateReason) : Startup

    /**
     * Android: the library is on a memory card that is not in the device ([label] is the card's name). [mediaSeen]: the
     * card events counted (ZayitApplication.mediaChanges) before the volumes were read; a later one checks again.
     */
    data class CardMissing(val label: String, val mediaSeen: Int) : Startup
}

/**
 * Desktop 0.6.1 main.kt: onboarding on first run; once it is done, the library check (DatabaseVersionManager) that
 * sends a missing, outdated or unreadable library to the database update flow; then the saved session and the tabs.
 * Also the Android entry points: zayit:// links and launcher shortcuts (singleTask, so a running app gets them in
 * [onNewIntent]), the app update's notification, hardware-keyboard shortcuts, and saving the session when the app
 * leaves the screen.
 */
class MainActivity : ComponentActivity() {
    /** A link or launcher shortcut waiting to be opened by the shell. */
    private var pendingEntry by mutableStateOf<ShellEntry?>(null)

    /** A launcher widget's tap waiting for the library to be open (appwidget/WidgetLaunch). */
    private var pendingWidget by mutableStateOf<WidgetLaunch?>(null)

    /**
     * Counts the links, launcher shortcuts and widget taps taken in: each first drops the focused field and the
     * keyboard ([DismissKeyboardOnRequest]).
     */
    private var outsideRequests by mutableIntStateOf(0)
    private val shortcuts = KeyShortcuts()
    private val appVm: AppViewModel by viewModels()

    /**
     * LibraryStore.closeCount when the startup check last opened the library for the tabs: their ViewModels keep that
     * repository, so a close since then (an install's prepareTarget) leaves them a closed one.
     */
    @Volatile
    private var closeCountAtReady = -1

    /** The app is starting afresh: Settings' update flow stays on screen until it has. */
    private var restarting = false

    private fun entryOf(intent: Intent): ShellEntry? = when (intent.action) {
        LauncherShortcuts.ACTION_NEW_TAB -> ShellEntry.NewTab
        else -> intent.dataString?.let { ShellEntry.Link(it) }
    }

    /**
     * Takes what [intent] asks to open (a zayit:// link, a launcher shortcut, a widget tap), once. [launch]: it is the
     * intent that created the activity, which Android hands over again whenever it restarts the task.
     */
    private fun take(intent: Intent?, launch: Boolean) {
        if (intent == null || intent.getBooleanExtra(EXTRA_HANDLED, false)) return
        // Reopened from Recents after the process died: the intent that first launched the app is not a new request
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        // The app update's notification ("ready to install"): the update dialog, over whatever the shell shows
        if (intent.action == AppUpdater.ACTION_SHOW) {
            intent.putExtra(EXTRA_HANDLED, true)
            AppUpdater.get(this).openDialog()
            return
        }
        val entry = entryOf(intent)
        val widget = widgetLaunchOf(intent)
        if (entry == null && widget == null) return
        val again = handledBefore(intent, launch)
        // Within this process (getIntent() read again by a recreated activity) the intent itself says so
        intent.putExtra(EXTRA_HANDLED, true)
        if (again) return
        if (entry != null) pendingEntry = entry
        if (widget != null) pendingWidget = widget
        outsideRequests++
    }

    /**
     * Whether [intent], creating the activity ([launch]), is this task's last request, opened by a process that has
     * since died. Reusing this singleTask activity makes the newest link or widget intent its task's base intent, and a
     * restart of the task hands that over as the launching intent; Recents does not always say so (an ANR kill restored
     * from Recents reopened a widget's passage: 7 tabs became 8). So every request opened is kept on disk with the
     * task's id: a restart of the same task finds it, while the same link or widget row later opening a new task
     * (another id), or reaching the running activity ([onNewIntent]), still opens. Task ids are unique within a boot
     * only (the counter starts again after a reboot), so the key holds the boot too: the restart guarded against is
     * within one boot, and a widget's intent, the same on every tap, must still open in a new task the next day.
     */
    private fun handledBefore(intent: Intent, launch: Boolean): Boolean {
        // Flags and the tapped widget's bounds differ between a delivery and a restart; what to open does not
        val request = Intent(intent).setFlags(0).apply {
            sourceBounds = null
            removeExtra(EXTRA_HANDLED)
        }
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
        val key = "$boot $taskId " + request.toUri(0)
        val prefs = getSharedPreferences(LAUNCH_PREFS, MODE_PRIVATE)
        if (launch && prefs.getString(KEY_HANDLED_REQUEST, null) == key) return true
        // apply, not commit: commit wrote and fsynced the file on the main thread in onCreate, on the cold start of
        // every link and widget tap. The death this guards against (an ANR kill) comes 5 to 10 s into a stalled main
        // thread, and apply's write runs on its own thread (QueuedWork, ~100 ms later), which a stalled main thread
        // does not hold up; the value is in memory at once for the next take()
        prefs.edit().putString(KEY_HANDLED_REQUEST, key).apply()
        return false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent, launch = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A rotation or a restored process must not open the launching link again.
        if (savedInstanceState == null) take(intent, launch = true)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ZayitApplication
        setContent {
            val themeMode by app.prefs.themeMode.collectAsState()
            val accent by app.prefs.accent.collectAsState()
            val onboardingDone by app.prefs.onboardingFinished.collectAsState()
            ZayitTheme(themeMode = themeMode, accent = accent) {
                // Status / navigation bar icons follow the background of the screen shown (in the app's theme, not
                // only the system's), and are set again when the system may have reset them
                SystemBarsHost(window) { MainContent(app, onboardingDone) }
            }
        }
    }

    @Composable
    private fun MainContent(app: ZayitApplication, onboardingDone: Boolean) {
        if (!onboardingDone) {
            OnboardingFlow(onFinished = { app.prefs.setOnboardingFinished(true) })
        } else {
            var attempt by remember { mutableIntStateOf(0) }
            val startup by produceState<Startup?>(null, attempt) {
                value = null
                val restore = !appVm.sessionApplied && app.prefs.persistSession.value
                value = withContext(Dispatchers.IO) { checkStartup(app, restore) }
                // The card and update flows own the screen now and return to the app when done: Settings' request is
                // not reopened after them.
                if (value !is Startup.Ready) app.manualLibraryUpdate.value = false
                (value as? Startup.Ready)?.let { appVm.restoreFromDisk(it.session) }
            }
            val manualUpdate by app.manualLibraryUpdate.collectAsState()
            if (manualUpdate && startup is Startup.Ready) {
                // Android: Settings' update from files (or download). A new library starts the app afresh; leaving the
                // flow checks the library again (an install that failed may have removed it) and returns to the tabs,
                // or, when the install closed the library under them (prepareTarget), starts the app afresh too.
                DatabaseUpdateScreen(
                    DatabaseUpdateReason.Manual,
                    onDone = { app.restart() },
                    onExit = {
                        if (app.libraryStore.closeCount != closeCountAtReady) {
                            restartSavingSession(app)
                        } else {
                            app.manualLibraryUpdate.value = false
                            attempt++
                        }
                    },
                )
                return
            }
            when (val s = startup) {
                null -> LoadingBox(Modifier.fillMaxSize().background(Zayit.colors.ground))
                is Startup.Ready -> {
                    // First: its effect starts before ZayitApp's link handling and the widget launch below
                    DismissKeyboardOnRequest(outsideRequests, window)
                    ZayitApp(entry = pendingEntry, onEntryHandled = { pendingEntry = null }, shortcuts = shortcuts, appVm = appVm)
                    // After the session is restored, or restoreFromDisk would replace what the tap opened
                    val widget = pendingWidget
                    LaunchedEffect(widget) {
                        if (widget != null) {
                            widget.open(this@MainActivity, app, appVm)
                            // The same tap again while a passage was being looked up is the one just opened
                            if (pendingWidget == widget) pendingWidget = null
                        }
                    }
                }
                is Startup.Update -> DatabaseUpdateScreen(s.reason, onDone = { attempt++ }, onExit = { finish() })
                is Startup.CardMissing -> LibraryCardMissingScreen(
                    s.label,
                    s.mediaSeen,
                    onRetry = { attempt++ },
                    onUsePhone = {
                        app.libraryStore.setPreferred(app.libraryStore.locations.first())
                        attempt++
                    },
                    onExit = { finish() },
                )
            }
        }
    }

    /**
     * Blocking: the user database (may first import a desktop one pushed by adb), the library check, opening the
     * multi-GB library (schema checks, WAL recovery), then the saved session with its missing titles.
     */
    private suspend fun checkStartup(app: ZayitApplication, restoreSession: Boolean): Startup {
        runCatching { app.userData }
        runCatching { app.libraryStore.deleteObsoleteFiles() }
        // What a killed install or move left under `.part` names (gigabytes the space checks would count as taken).
        runCatching { LibraryInstaller.get(app).discardStaleDownload() }
        // Counted before the volumes are read: a card put in from here on makes the card screen check again.
        val mediaSeen = app.mediaChanges.value
        // Android: a library kept on a memory card that is not in the device is asked for, not reinstalled.
        app.libraryStore.missingCard()?.let { return Startup.CardMissing(it, mediaSeen) }
        runCatching { app.libraryStore.rememberCurrentLocation() }
        // Desktop computeStartupState: no library → Missing, release_info.txt missing or too old → Outdated; then the
        // installer's check (as before) for a database without its tables, and opening it.
        val reason = runCatching { databaseUpdateReason(app) }.getOrDefault(DatabaseUpdateReason.Broken)
            ?: DatabaseUpdateReason.Broken.takeIf { runCatching { LibraryInstaller.get(app).checkInstalledLibrary() != null }.getOrDefault(true) }
        if (reason != null) return Startup.Update(reason)
        // Read before the repository is opened, so that no close after it goes unnoticed ([closeCountAtReady]).
        val closeCount = app.libraryStore.closeCount
        val repository = runCatching { app.libraryStore.repository() }.getOrElse { return Startup.Update(DatabaseUpdateReason.Broken) }
        val session = if (restoreSession) SessionStore.load(app)?.let { runCatching { SessionStore.enrichTitles(it, repository) }.getOrDefault(it) } else null
        // Desktop main.kt's DbDeltaRecoveryBootstrap spot: a delta whose database part committed before the app stopped
        // gets its search index and catalog finished in the background (nothing to roll back on Android).
        runCatching { DbDeltaUpdateService.get(app).resumeIfPending() }
        closeCountAtReady = closeCount
        return Startup.Ready(session)
    }

    /**
     * Starts the app afresh (ZayitApplication.restart) after the library was closed under the tabs, the session saved
     * first. AppViewModel.saveNow writes it on another thread, which the restart's exit would not wait for.
     */
    private fun restartSavingSession(app: ZayitApplication) {
        if (restarting) return
        restarting = true
        val session = if (appVm.sessionApplied && app.prefs.persistSession.value) {
            runCatching { SessionStore.encode(appVm.buildSession()) }.getOrNull()
        } else {
            null
        }
        app.appScope.launch(Dispatchers.IO) {
            session?.let { SessionStore.write(app, it) }
            app.restart()
        }
    }

    override fun onStop() {
        super.onStop()
        // Android may kill the process in the background without a word: save the tabs now (desktop saves on quit).
        if (appVm.sessionApplied) appVm.saveNow()
        // The launcher shows next: its widgets drawn fresh, their alarm set again (also after a force stop)
        ZayitWidgets.refreshAll(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = shortcuts.dispatch(event) || super.dispatchKeyEvent(event)

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data.add(shellKeyboardShortcuts())
    }
}

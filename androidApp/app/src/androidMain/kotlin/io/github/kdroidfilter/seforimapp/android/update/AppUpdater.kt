package io.github.kdroidfilter.seforimapp.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.HttpStatusException
import io.github.kdroidfilter.seforimapp.android.data.InstallToken
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.data.LibraryMover
import io.github.kdroidfilter.seforimapp.android.data.ResumingHttpInputStream
import io.github.kdroidfilter.seforimapp.android.data.SessionStore
import io.github.kdroidfilter.seforimapp.android.data.USER_AGENT
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateService
import io.github.kdroidfilter.seforimapp.android.readAtMost
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Desktop 0.6.1 `framework/update/AppUpdateService.kt`'s UpdateUiState, as the touch flow shows it: the version found
 * ([release], with what is new in it), the download and its progress, the APK checked and waiting ([ready]), Android's
 * "install unknown apps" switch still off ([needsPermission]), and the install itself.
 */
data class AppUpdateState(
    /** Active step, or null. */
    val phase: Phase? = null,
    /** A newer version the last check found; kept while it downloads, waits and installs. */
    val release: AppRelease? = null,
    /** What changed since the installed version (CHANGELOG.md's sections), when the site could be read. */
    val changes: String? = null,
    /** The last check found nothing newer. */
    val upToDate: Boolean = false,
    /** The APK of [release] is downloaded and checked: [AppUpdater.install] hands it to Android. */
    val ready: Boolean = false,
    /** Android must first let Pardes install apps (Settings › "install unknown apps"). */
    val needsPermission: Boolean = false,
    /** Installing: Android asked the user to confirm, and has had no answer yet. */
    val confirming: Boolean = false,
    /** The APK of [release] cannot replace this app (another signer, another app, not newer): nothing to offer. */
    val rejected: Boolean = false,
    /** A note that is not an error ("ההתקנה בוטלה"). */
    val message: String? = null,
    val errorMessage: String? = null,
    /** 0..1 while downloading, null when it cannot be measured. */
    val progress: Float? = null,
    /** "8.00 מ״ב / 18.0 מ״ב", or the reconnecting note. */
    val detail: String? = null,
) {
    enum class Phase { Checking, Downloading, Verifying, Installing }

    /** The version offered ("0.4.0"), or null. */
    val version: String? get() = release?.version

    val apkSize: Long get() = release?.apkSize ?: 0L

    /** Desktop showTitleBarIcon: the title bar's "עדכון זמין" while a newer version can be installed. */
    val showBadge: Boolean get() = release != null && !rejected
}

/**
 * Desktop 0.6.1 `framework/update/AppUpdateService.kt` (Nucleus: a check at startup, the title-bar badge, the dialog,
 * the settings banner's re-check), for an APK that Android installs: one process-wide object like
 * [DbDeltaUpdateService], so a download outlives the screen that started it and goes on in [AppUpdateService].
 *
 * It finds the newest version as the download site does ([AppReleases]), downloads its APK into the app's cache with
 * the library downloads' HTTP stack (user CAs, Range resume, reconnects), checks it (GitHub's sha256, then that
 * Android reads it as Pardes signed with the installed app's own certificate, of that version and not older), and
 * hands it to Android's PackageInstaller. Pardes holds UPDATE_PACKAGES_WITHOUT_USER_ACTION, so once the user has let it
 * install apps, Android replaces it without asking (it may still ask: then the confirmation is opened, or waits in a
 * notification). The process ends there; the new version says "פרדס עודכן" ([AppUpdatedReceiver], and the shell).
 *
 * Development builds are signed with the public debug key, which no release carries, so they look for no update. For
 * testing, a development build reads `files/app-update-test-feed.json` (releases.json's shape) instead of GitHub when
 * one is there, as desktop reads SEFORIMAPP_UPDATE_FEED_URL; its APK may then be a `file:` URL inside the app's files.
 */
class AppUpdater private constructor(private val app: ZayitApplication) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _dialogVisible = MutableStateFlow(false)

    /** Desktop dialogVisible: the update dialog, opened from the badge, the menu, a notification or the snackbar. */
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private val _announcement = MutableStateFlow<String?>(null)

    /**
     * A version the check at startup found and the user has not been told of: the shell says so once it can, then
     * calls [markAnnounced] (held as state, so a shell that was not on screen at the time still says it).
     */
    val announcement: StateFlow<String?> = _announcement.asStateFlow()

    private val visibleViews = AtomicInteger(0)

    /** The dialog or the settings banner is on screen: the end of a download shows there, not as a notification. */
    val uiVisible: Boolean get() = visibleViews.get() > 0

    /**
     * The shell's session as text (null when it is not kept), written before the app is replaced: an update that needs
     * no confirmation ends the process with no onStop. Set by the shell (ZayitApp), read on the main thread.
     */
    @Volatile
    var sessionText: (() -> String?)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val startupChecked = AtomicBoolean(false)
    private val shellStarted = AtomicBoolean(false)

    @Volatile
    private var token: InstallToken? = null

    @Volatile
    private var activeStream: ResumingHttpInputStream? = null

    /** The checked APK of the release offered. */
    @Volatile
    private var readyFile: File? = null

    /** The user asked for "download and install": the install follows the download while they look at it. */
    @Volatile
    private var installWhenReady = false

    /** The PackageInstaller session this process committed, or -1: statuses of any other are not this install's. */
    @Volatile
    private var sessionId = -1

    /** Android's confirmation for [sessionId] (STATUS_PENDING_USER_ACTION's intent), to open again. */
    @Volatile
    private var confirmIntent: Intent? = null

    /** While a confirmation waits: abandons the session if a library install, move or update starts meanwhile. */
    private var libraryWatch: Job? = null

    /** The session [watchLibrary] abandoned: its ABORTED status says why, not "cancelled". */
    @Volatile
    private var abandonedForLibrary = -1

    /** The version installed (PackageInfo.versionName). */
    val installedVersion: String by lazy { AppPackages.installed(app)?.versionName.orEmpty() }

    /** Signed with Pardes' release key: published releases can be installed over it. */
    val isReleaseBuild: Boolean by lazy { AppPackages.isReleaseBuild(app) }

    private val testFeed: File? get() = File(app.filesDir, TEST_FEED_NAME).takeIf { !isReleaseBuild && it.isFile }

    /** Newer versions can be looked for and installed: a release build, or a development build with a test feed. */
    val canUpdate: Boolean get() = isReleaseBuild || testFeed != null

    val isBusy: Boolean get() = _state.value.phase != null

    /**
     * Android is installing the update (not waiting for the user's confirmation): the process may end any moment, so a
     * library install, move or update does not start now (LibraryInstaller, LibraryMover, DbDeltaUpdateService).
     */
    val isInstalling: Boolean get() = _state.value.let { it.phase == AppUpdateState.Phase.Installing && !it.confirming }

    private val dir: File get() = File(app.cacheDir, DIR_NAME)

    /**
     * Opens the dialog. With no version known (a notification's tap started a new process), the APK downloaded before
     * is taken up again, or, when checking at startup is on, the version is looked for.
     */
    fun openDialog() {
        _dialogVisible.value = true
        if (_state.value.release == null && !isBusy && canUpdate) {
            if (app.prefs.appUpdateCheck.value) check() else scope.launch { restoreReady() }
        }
    }

    fun closeDialog() {
        _dialogVisible.value = false
    }

    /** Counts a view that shows the update (see [uiVisible]); [visible] false when it leaves the screen. */
    fun trackVisible(visible: Boolean) {
        if (visible) visibleViews.incrementAndGet() else visibleViews.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    // ------------------------------------------------------------------ startup

    /**
     * Blocking, once the shell is up: drops the APKs of versions no longer newer (and what a killed download or
     * install left), takes up an APK downloaded and checked before, then returns the version this app was just updated
     * to by this updater, once, or null.
     */
    fun onShellStart(): String? {
        val firstInProcess = shellStarted.compareAndSet(false, true)
        // Android 11 restarted the app for the permission the install asked for (awaitPermissionAcrossRestart). Only a
        // new process takes it up, and at once: the shell coming back in this one (a recreated activity) leaves the wait
        // as it is, and a later process must not find it
        val awaited = firstInProcess && prefs.getBoolean(KEY_AWAITING_PERMISSION, false)
        if (awaited) prefs.edit().remove(KEY_AWAITING_PERMISSION).apply()
        // The shell comes back after the library update flow too: not while this process downloads or installs
        if (!isBusy) {
            runCatching {
                dir.listFiles().orEmpty().forEach { file ->
                    val tag = file.name.removeSuffix(PART_SUFFIX).removePrefix("pardes-").removeSuffix(".apk")
                    val version = AppReleases.versionOfTag(tag)?.first
                    if (version == null || AppReleases.compareVersions(version, installedVersion) <= 0) file.deleteRecursively()
                }
            }
            if (sessionId == -1) abandonSessions(committedToo = false)
            restoreReady()
            if (awaited) resumeInstall()
        } else if (awaited) {
            // A check runs (the "ready" notification's tap opened the dialog first): the install goes on once it has
            // found the APK again, unless the dialog was closed meanwhile
            scope.launch {
                if (_state.first { it.phase == null }.ready && _dialogVisible.value) resumeInstall()
            }
        }
        val installing = prefs.getString(KEY_INSTALLING, null)
        val updated = (prefs.getString(KEY_UPDATED, null) ?: installing)?.takeIf { AppReleases.compareVersions(it, installedVersion) == 0 }
        // Kept only while an install of a newer version may still be confirmed (its session not yet answered)
        val stale = installing != null && (AppReleases.compareVersions(installing, installedVersion) <= 0 || !hasCommittedSession())
        if (stale || prefs.contains(KEY_UPDATED)) prefs.edit().apply { if (stale) remove(KEY_INSTALLING) }.remove(KEY_UPDATED).apply()
        if (updated != null) AppUpdateNotifications.cancelResult(app)
        return updated
    }

    /** Desktop checkOnStartup: once per process, quietly (a failure only shows in the settings banner). */
    fun checkOnStartup() {
        if (!app.prefs.appUpdateCheck.value || !canUpdate || !startupChecked.compareAndSet(false, true)) return
        check(announce = true)
    }

    /** The shell said the version [announcement] holds: not again. */
    fun markAnnounced(version: String) {
        prefs.edit().putString(KEY_ANNOUNCED, version).apply()
        _announcement.compareAndSet(version, null)
    }

    // ------------------------------------------------------------------ check

    /** Desktop recheck: looks for a newer version, unless a step runs or a checked APK waits. */
    fun check() = check(announce = false)

    private fun check(announce: Boolean) {
        val s = _state.value
        if (isBusy || !canUpdate || s.ready || s.needsPermission) return
        val current = InstallToken().also { token = it }
        _state.value = AppUpdateState(phase = AppUpdateState.Phase.Checking)
        scope.launch {
            // An APK downloaded and checked before (the process ended since): offered as it is, with no network
            if (restoreReady(current)) return@launch
            val result = runCatching { findNewer(current) }
            publish(
                current,
                result.fold(
                    onSuccess = { found -> found?.let { AppUpdateState(release = it.first, changes = it.second) } ?: AppUpdateState(upToDate = true) },
                    onFailure = { AppUpdateState(errorMessage = AppUpdateStrings.checkFailed) },
                ),
            )
            val release = result.getOrNull()?.first
            if (announce && release != null && token === current && prefs.getString(KEY_ANNOUNCED, null) != release.version) {
                _announcement.value = release.version
            }
        }
    }

    /** Blocking: the newest version, when it is newer than this one, with what changed since this one. */
    private fun findNewer(current: InstallToken): Pair<AppRelease, String?>? {
        // A release found unusable before (another signer, a conflict Android reported) is not offered again
        val rejected = prefs.getString(KEY_REJECTED, null)
        val newest = AppReleases.newest(releases(current).filter { it.tag != rejected }) ?: return null
        if (AppReleases.compareVersions(newest.version, installedVersion) <= 0) return null
        return newest to changesFor(newest, current)
    }

    private fun changesFor(release: AppRelease, current: InstallToken): String? = runCatching {
        val changelog = testFeed?.let { File(app.filesDir, TEST_CHANGELOG_NAME).takeIf(File::isFile)?.readText() }
            ?: getText(AppReleases.CHANGELOG_URL, ACCEPT_ANY, current, MAX_CHANGELOG_BYTES)
        AppReleases.changesSince(changelog, installedVersion, release.version)
    }.getOrNull()

    /** The GitHub API's list, else releases.json (as the site reads them); a development build's test feed instead. */
    private fun releases(current: InstallToken): List<AppRelease> {
        testFeed?.let { return AppReleases.parse(it.readText()) }
        return runCatching { AppReleases.parse(getText(AppReleases.API_URL, ACCEPT_GITHUB_JSON, current, MAX_LIST_BYTES)) }
            .getOrElse { AppReleases.parse(getText(AppReleases.SNAPSHOT_URL, ACCEPT_ANY, current, MAX_LIST_BYTES)) }
    }

    /**
     * A small text (a release list, the changelog), with short timeouts: a filtering proxy may hold a request to
     * api.github.com open instead of refusing it, and releases.json is asked next. One retry on a dropped connection.
     */
    private fun getText(url: String, accept: String, current: InstallToken, maxBytes: Int): String {
        var attempt = 0
        while (true) {
            current.check()
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TEXT_TIMEOUT_MS
                connection.readTimeout = TEXT_TIMEOUT_MS
                connection.setRequestProperty("Accept", accept)
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val code = connection.responseCode
                if (code !in 200..299) throw HttpStatusException(code)
                val bytes = connection.inputStream.use { it.readAtMost(maxBytes + 1) }
                if (bytes.size > maxBytes) throw IOException("Too large: $url")
                return String(bytes, Charsets.UTF_8)
            } catch (e: HttpStatusException) {
                throw e
            } catch (e: IOException) {
                if (++attempt > 1) throw e
                current.sleep(RETRY_DELAY_MS)
            } finally {
                connection.disconnect()
            }
        }
    }

    // ------------------------------------------------------------------ download

    /**
     * Desktop startDownload: the release's APK into the cache, checked. [installAfter]: "הורד והתקן", the install
     * follows while the dialog or the banner is on screen (otherwise it waits for "התקן עכשיו").
     */
    fun download(installAfter: Boolean) {
        val s = _state.value
        val release = s.release ?: return
        if (isBusy || s.rejected) return
        libraryBusy()?.let { reason ->
            _state.value = offered(release).copy(errorMessage = reason)
            return
        }
        // The download, then the session's copy of it; written so that no size from the release list can overflow it
        val free = runCatching { dir.apply { mkdirs() }.let { StatFs(it.path).availableBytes } }.getOrDefault(Long.MAX_VALUE)
        if (free < SPACE_MARGIN_BYTES || release.apkSize > (free - SPACE_MARGIN_BYTES) / 2) {
            val need = release.apkSize * 2 + SPACE_MARGIN_BYTES
            _state.value = offered(release).copy(errorMessage = AppUpdateStrings.noSpace.format(formatBytes(need), formatBytes(free)))
            return
        }
        installWhenReady = installAfter
        val current = InstallToken().also { token = it }
        _state.value = offered(release).copy(phase = AppUpdateState.Phase.Downloading, progress = 0f, detail = progressText(0L, release.apkSize))
        AppUpdateService.start(app)
        scope.launch { runDownload(release, current) }
    }

    private fun runDownload(release: AppRelease, current: InstallToken) {
        val wakeLock = acquireWakeLock()
        try {
            val apk = File(dir, apkName(release))
            var lastEmitNs = 0L
            DeltaDownloader(
                openStream = { url, size, startAt -> openApk(url, size, startAt, current) },
                onProgress = { done, total ->
                    val now = System.nanoTime()
                    if (done == total || now - lastEmitNs >= PROGRESS_EVERY_NS) {
                        lastEmitNs = now
                        update(current) { it.copy(progress = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else null, detail = progressText(done, total)) }
                    }
                },
                checkCancelled = current::check,
            ).download(release.apkUrl, apk.toPath(), release.apkSha256, release.apkSize)
            update(current) { it.copy(phase = AppUpdateState.Phase.Verifying, progress = null, detail = null) }
            val problem = problemWith(apk, release)
            if (problem != null) {
                apk.delete()
                reject(release)
                publish(current, offered(release).copy(errorMessage = problem, rejected = true))
                return
            }
            readyFile = apk
            saveReady(release, _state.value.changes)
            publish(current, offered(release).copy(ready = true))
            if (installWhenReady && uiVisible && app.isInForeground && token === current) scope.launch(Dispatchers.Main) { install() }
        } catch (e: Throwable) {
            publish(
                current,
                when {
                    current.cancelled.get() -> offered(release)
                    e.message.orEmpty().startsWith("Sha256 mismatch") -> offered(release).copy(errorMessage = AppUpdateStrings.corrupt)
                    else -> offered(release).copy(errorMessage = AppUpdateStrings.downloadFailed.format(e.message ?: e.javaClass.simpleName))
                },
            )
        } finally {
            activeStream = null
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    /** The APK's bytes from [startAt]: GitHub's download (redirected to its file host), or a test feed's local file. */
    private fun openApk(url: String, size: Long, startAt: Long, current: InstallToken): InputStream {
        if (url.startsWith("file:")) {
            val file = File(URI(url)).canonicalFile
            if (testFeed == null || !file.path.startsWith(app.filesDir.canonicalPath + File.separator)) throw IOException("Not allowed: $url")
            return FileInputStream(file).apply { skipFully(startAt) }
        }
        return ResumingHttpInputStream(url, size, current, LibraryInstaller.get(app)::networkAvailable, startAt) { lost ->
            update(current) { it.copy(detail = if (lost) AndroidStrings.reconnecting else progressText((it.progress ?: 0f).times(size).toLong(), size)) }
        }.also { activeStream = it }
    }

    /** Stops a download (its `.part` stays, so "הורד והתקן" resumes it). */
    fun cancel() {
        if (_state.value.phase != AppUpdateState.Phase.Downloading) return
        val current = token ?: return
        current.cancelled.set(true)
        activeStream?.abort()
    }

    // ------------------------------------------------------------------ install

    /**
     * Desktop installAndRestart: hands the checked APK to Android. Android first needs Pardes allowed to install apps
     * (asked for here, and the install goes on when the user comes back: [onResume]); the session is saved; then a
     * PackageInstaller session, whose answer reaches [AppUpdateInstallReceiver]. Call on the main thread.
     */
    fun install() {
        val s = _state.value
        val release = s.release ?: return
        if (isBusy || s.rejected) return
        val apk = readyFile?.takeIf { s.ready && it.isFile } ?: return download(installAfter = true)
        libraryBusy()?.let { reason ->
            _state.value = s.copy(errorMessage = reason, message = null)
            return
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            awaitPermissionAcrossRestart(true)
            _state.value = s.copy(needsPermission = true, errorMessage = null, message = null)
            return
        }
        // Allowed without a restart (on already, or Android did not end the app): no install waits for the next process
        awaitPermissionAcrossRestart(false)
        val session = runCatching { sessionText?.invoke() }.getOrNull()
        val current = InstallToken().also { token = it }
        _state.value = s.copy(phase = AppUpdateState.Phase.Installing, needsPermission = false, confirming = false, errorMessage = null, message = null)
        scope.launch {
            session?.let { SessionStore.write(app, it) }
            // The file may have changed in the cache since it was checked.
            val problem = problemWith(apk, release)
            if (problem != null) {
                apk.delete()
                forgetReady()
                reject(release)
                publish(current, offered(release).copy(errorMessage = problem, rejected = true))
                return@launch
            }
            runCatching { commit(apk, release) }.onFailure { e ->
                publish(current, offered(release).copy(ready = true, errorMessage = AppUpdateStrings.installFailed.format(e.message ?: e.javaClass.simpleName)))
            }
        }
    }

    /** "המשך בהתקנה": Android's confirmation for the waiting session, again (the user left it, or it waited in a notification). */
    fun continueInstall(context: Context) {
        val s = _state.value
        val confirm = confirmIntent
        if (s.phase != AppUpdateState.Phase.Installing || !s.confirming || confirm == null) return
        // Its approval would end the process in the middle of the library work
        libraryBusy()?.let { reason ->
            _state.value = s.copy(errorMessage = reason)
            return
        }
        _state.value = s.copy(errorMessage = null)
        AppUpdateNotifications.cancelResult(app)
        runCatching { context.startActivity(Intent(confirm)) }
    }

    /** "ביטול" while a confirmation waits: the session is abandoned (its status brings the APK back as ready). */
    fun cancelInstall() {
        val id = sessionId
        if (_state.value.phase != AppUpdateState.Phase.Installing || id == -1) return
        runCatching { app.packageManager.packageInstaller.abandonSession(id) }
        if (!hasSession(id)) installEnded(cancelled = true, error = null)
    }

    /** "ביטול" beside "פתח הגדרות": the checked APK waits again. */
    fun clearPermission() {
        val s = _state.value
        if (s.needsPermission) {
            awaitPermissionAcrossRestart(false)
            _state.value = s.copy(needsPermission = false)
        }
    }

    /**
     * Android 11 ends the app as "install unknown apps" is switched on for it (it remounts the app's storage; Android
     * 12 and later keep it running), so the install that asked for it cannot go on in [onResume]: remembered, it goes
     * on in the next process instead, with the APK checked before and the dialog open ([onShellStart]).
     */
    private fun awaitPermissionAcrossRestart(waiting: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !waiting && !prefs.contains(KEY_AWAITING_PERMISSION)) return
        prefs.edit().apply { if (waiting) putBoolean(KEY_AWAITING_PERMISSION, true) else remove(KEY_AWAITING_PERMISSION) }.apply()
    }

    /** The install that waited across Android 11's restart goes on, with the dialog open, once allowed. */
    private fun resumeInstall() {
        if (_state.value.ready && app.packageManager.canRequestPackageInstalls()) {
            _dialogVisible.value = true
            scope.launch(Dispatchers.Main) { install() }
        }
    }

    /** "פתח הגדרות": Android's "install unknown apps" page for Pardes. */
    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))

    /**
     * The app is back on screen: from the "install unknown apps" page (the install goes on once allowed), or after an
     * install whose session ended with no answer reaching this process (the APK waits again).
     */
    fun onResume() {
        val s = _state.value
        if (s.needsPermission && app.packageManager.canRequestPackageInstalls()) {
            install()
        } else if (s.phase == AppUpdateState.Phase.Installing && sessionId != -1) {
            val id = sessionId
            scope.launch(Dispatchers.Main) {
                // A status on its way comes first (it says why); a session still there waits (installing, or confirming)
                delay(STATUS_GRACE_MS)
                if (sessionId == id && _state.value.phase == AppUpdateState.Phase.Installing && !hasSession(id)) installEnded(cancelled = false, error = null)
            }
        }
    }

    /** Blocking: writes [apk] into a PackageInstaller session and commits it. */
    private fun commit(apk: File, release: AppRelease) {
        val installer = app.packageManager.packageInstaller
        // Any earlier attempt, even one waiting for its confirmation, gives way: one session for the update at a time
        abandonSessions(committedToo = true)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Honoured as Pardes updates itself and holds UPDATE_PACKAGES_WITHOUT_USER_ACTION, once it may install apps.
            // Android still asks at times (another installer owns updates, a silent update in the last 30 s, a policy).
            // API 31: Android 11 always asks.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite(SESSION_APK_NAME, 0, apk.length()).use { out ->
                    FileInputStream(apk).use { it.copyTo(out, BUFFER_SIZE) }
                    session.fsync(out)
                }
                // Read by the new version (AppUpdatedReceiver, onShellStart): written before the process can end.
                prefs.edit().putString(KEY_INSTALLING, release.version).commit()
                sessionId = id
                val status = Intent(app, AppUpdateInstallReceiver::class.java).setAction(AppUpdateInstallReceiver.ACTION_STATUS).setPackage(app.packageName)
                // Mutable: the installer adds its status to the intent. Explicit, to a receiver that is not exported.
                val pending = PendingIntent.getBroadcast(app, id, status, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
        } catch (e: Throwable) {
            runCatching { installer.abandonSession(id) }
            if (sessionId == id) sessionId = -1
            prefs.edit().remove(KEY_INSTALLING).apply()
            throw e
        }
    }

    /**
     * PackageInstaller's answer (AppUpdateInstallReceiver, main thread). Only this process's session counts: a status
     * reaching a new process (the old one ended meanwhile, by the update itself on success) is left to the shell's
     * "פרדס עודכן" and to [onShellStart].
     */
    internal fun onInstallStatus(id: Int, status: Int, detail: String?, confirm: Intent?) {
        if (id == -1 || id != sessionId) {
            // The new version's process gets the status of the session that installed it
            if (status == PackageInstaller.STATUS_SUCCESS && sessionId == -1) AppUpdatedReceiver.announce(app)
            return
        }
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirm == null) {
                    runCatching { app.packageManager.packageInstaller.abandonSession(id) }
                    installEnded(cancelled = false, error = AppUpdateStrings.installFailed.format("(PENDING_USER_ACTION)"))
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent = confirm
                _state.value = _state.value.copy(phase = AppUpdateState.Phase.Installing, confirming = true)
                watchLibrary(id)
                // Opened at once only while the app is on screen (Android lets a visible app open it); otherwise it
                // waits in a notification that opens the dialog, whose "המשך בהתקנה" opens it
                val opened = app.isInForeground && runCatching { app.startActivity(Intent(confirm)) }.isSuccess
                if (!opened) AppUpdateNotifications.confirm(app)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // The process normally ends before this arrives
                sessionId = -1
                confirmIntent = null
                readyFile = null
                forgetReady()
                _state.value = AppUpdateState()
            }
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                // A cancel or Back in Android's confirmation, an abandoned session, or the device's verifier
                when {
                    abandonedForLibrary == id -> installEnded(cancelled = false, error = AppUpdateStrings.withdrawnForLibrary)
                    detail.orEmpty().contains("VERIFICATION", ignoreCase = true) -> installEnded(cancelled = false, error = AppUpdateStrings.installBlocked)
                    else -> installEnded(cancelled = true, error = null)
                }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                // A file Android cannot install: downloaded again on the next try
                readyFile?.delete()
                readyFile = null
                forgetReady()
                installEnded(cancelled = false, error = installError(status, detail))
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                // Android found what the checks could not (a provider or permission another app holds): not offered again
                _state.value.release?.let(::reject)
                readyFile?.delete()
                readyFile = null
                forgetReady()
                installEnded(cancelled = false, error = installError(status, detail), rejected = true)
            }
            else -> installEnded(cancelled = false, error = installError(status, detail))
        }
    }

    /** The install's session is over without replacing the app: the APK waits again (unless it cannot be used). */
    private fun installEnded(cancelled: Boolean, error: String?, rejected: Boolean = false) {
        sessionId = -1
        confirmIntent = null
        libraryWatch?.cancel()
        prefs.edit().remove(KEY_INSTALLING).apply()
        AppUpdateNotifications.cancelResult(app)
        val release = _state.value.release
        val base = release?.let { offered(it).copy(ready = !rejected && readyFile?.isFile == true, rejected = rejected) } ?: AppUpdateState()
        _state.value = base.copy(message = if (cancelled) AppUpdateStrings.cancelled else null, errorMessage = error)
    }

    /**
     * While Android's confirmation waits, a library install, move or update may start; approving the update would end
     * it half way, so the waiting session is abandoned when one does.
     */
    private fun watchLibrary(id: Int) {
        libraryWatch?.cancel()
        libraryWatch = scope.launch {
            combine(LibraryInstaller.get(app).state, LibraryMover.get(app).state, DbDeltaUpdateService.get(app).state) { _, _, _ -> libraryBusy() != null }
                .first { it }
            if (sessionId == id) {
                abandonedForLibrary = id
                runCatching { app.packageManager.packageInstaller.abandonSession(id) }
                // Its ABORTED status also comes; this is for a session already gone
                launch(Dispatchers.Main) { if (sessionId == id && !hasSession(id)) installEnded(cancelled = false, error = AppUpdateStrings.withdrawnForLibrary) }
            }
        }
    }

    /** Clears a note or an error the user has read. */
    fun clearMessage() {
        val s = _state.value
        if (s.phase == null) _state.value = s.copy(message = null, errorMessage = null, upToDate = false)
    }

    // ------------------------------------------------------------------ the APK kept between processes

    /** Remembers the checked APK's release, so a new process offers it as ready without the network. */
    private fun saveReady(release: AppRelease, changes: String?) {
        val record = buildJsonObject {
            put("version", release.version)
            put("build", release.build)
            put("tag", release.tag)
            release.pageUrl?.let { put("page", it) }
            put("name", release.apkName)
            put("url", release.apkUrl)
            put("size", release.apkSize)
            put("sha256", release.apkSha256)
            changes?.let { put("changes", it) }
        }
        prefs.edit().putString(KEY_READY, record.toString()).apply()
    }

    private fun forgetReady() {
        prefs.edit().remove(KEY_READY).apply()
    }

    /** [release] cannot replace this app: a later check offers another release, not this one again. */
    private fun reject(release: AppRelease) {
        prefs.edit().putString(KEY_REJECTED, release.tag).apply()
    }

    /**
     * Blocking: the APK checked by an earlier process, still newer and still passing the checks, as the state (ready);
     * true when there is one. [current]: a check's run, which this then ends.
     */
    private fun restoreReady(current: InstallToken? = null): Boolean {
        // A development build whose test feed is gone: what the feed brought goes too
        if (!canUpdate) {
            if (prefs.contains(KEY_READY)) forgetReady()
            runCatching { dir.deleteRecursively() }
            return false
        }
        val record = runCatching { Json.parseToJsonElement(prefs.getString(KEY_READY, null) ?: return false).jsonObject }.getOrNull()
        fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        val release = record?.let { r ->
            runCatching {
                AppRelease(
                    version = r.text("version")!!,
                    build = (r["build"] as JsonPrimitive).intOrNull!!,
                    tag = r.text("tag")!!,
                    pageUrl = r.text("page"),
                    apkName = r.text("name")!!,
                    apkUrl = r.text("url")!!,
                    apkSize = (r["size"] as JsonPrimitive).longOrNull!!,
                    apkSha256 = r.text("sha256")!!,
                )
            }.getOrNull()
        }
        val apk = release?.let { File(dir, apkName(it)) }
        val usable = release != null && apk != null && apk.isFile && release.tag != prefs.getString(KEY_REJECTED, null) &&
            AppReleases.compareVersions(release.version, installedVersion) > 0 && problemWith(apk, release) == null
        if (!usable) {
            forgetReady()
            return false
        }
        readyFile = apk
        val state = AppUpdateState(release = release, changes = record.text("changes"), ready = true)
        if (current != null) publish(current, state) else if (!isBusy && _state.value.release == null) _state.value = state
        return true
    }

    // ------------------------------------------------------------------ helpers

    /** The APK cannot replace this app: why, or null when it can (checked again right before the install). */
    private fun problemWith(apk: File, release: AppRelease): String? {
        val installed = AppPackages.installed(app) ?: return AppUpdateStrings.notReadable
        val archive = AppPackages.archive(app, apk) ?: return AppUpdateStrings.notReadable
        return when {
            archive.packageName != app.packageName -> AppUpdateStrings.notPardes
            !AppPackages.sameSigner(installed, archive) -> AppUpdateStrings.otherSigner
            AppReleases.compareVersions(archive.versionName, release.version) != 0 -> AppUpdateStrings.wrongVersion.format(release.version)
            AppReleases.compareVersions(archive.versionName, installed.versionName) <= 0 || archive.versionCode < installed.versionCode -> AppUpdateStrings.older
            archive.minSdk > Build.VERSION.SDK_INT -> AppUpdateStrings.incompatible
            else -> null
        }
    }

    private fun installError(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT -> AppUpdateStrings.installConflict.format(detail?.substringBefore(':')?.takeIf { it.isNotBlank() } ?: "($status)")
        PackageInstaller.STATUS_FAILURE_STORAGE -> AppUpdateStrings.installStorage
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> AppUpdateStrings.installIncompatible
        PackageInstaller.STATUS_FAILURE_BLOCKED -> AppUpdateStrings.installBlocked
        PackageInstaller.STATUS_FAILURE_INVALID -> AppUpdateStrings.installInvalid
        else -> AppUpdateStrings.installFailed.format(detail?.takeIf { it.isNotBlank() } ?: "($status)")
    }

    /** Installing ends the process: never while the library is being installed, moved or updated. */
    private fun libraryBusy(): String? =
        AppUpdateStrings.libraryBusy.takeIf { LibraryInstaller.get(app).isBusy || LibraryMover.get(app).isBusy || DbDeltaUpdateService.get(app).isBusy }

    /** This app's install sessions: those never committed (a process that ended while writing one), or all of them. */
    private fun abandonSessions(committedToo: Boolean) {
        val installer = app.packageManager.packageInstaller
        runCatching {
            installer.mySessions.filter { !it.isStaged && (committedToo || !it.isCommitted) }.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        }
    }

    private fun hasSession(id: Int): Boolean = runCatching { app.packageManager.packageInstaller.getSessionInfo(id) != null }.getOrDefault(false)

    private fun hasCommittedSession(): Boolean = runCatching { app.packageManager.packageInstaller.mySessions.any { it.isCommitted } }.getOrDefault(false)

    private fun offered(release: AppRelease) = AppUpdateState(release = release, changes = _state.value.changes)

    private fun progressText(done: Long, total: Long) =
        if (total > 0) DesktopStrings.onboardingDownloadProgress.format(formatBytes(done), formatBytes(total)) else formatBytes(done)

    private fun publish(current: InstallToken, state: AppUpdateState) {
        if (token === current) _state.value = state
    }

    private fun update(current: InstallToken, change: (AppUpdateState) -> AppUpdateState) {
        if (token === current) _state.value = change(_state.value)
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        app.getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pardes:AppUpdate")?.apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }.getOrNull()

    /** The version this updater installed, for [AppUpdatedReceiver]: set before the commit, read by the new version. */
    internal fun installingVersion(): String? = prefs.getString(KEY_INSTALLING, null)

    internal fun markUpdated(version: String) {
        prefs.edit().remove(KEY_INSTALLING).remove(KEY_READY).putString(KEY_UPDATED, version).apply()
    }

    companion object {
        /** An Android intent action: opens the update dialog (the app update's notifications). */
        const val ACTION_SHOW = "io.github.kdroidfilter.seforimapp.android.action.SHOW_APP_UPDATE"

        /** In the app's files: a development build's release list for testing (releases.json's shape). */
        const val TEST_FEED_NAME = "app-update-test-feed.json"

        /** Beside it, optionally: the changelog its versions are described in (CHANGELOG.md's format). */
        const val TEST_CHANGELOG_NAME = "app-update-test-changelog.md"

        private const val PREFS_NAME = "app_update"
        private const val KEY_INSTALLING = "installing_version"
        private const val KEY_UPDATED = "updated_version"
        private const val KEY_ANNOUNCED = "announced_version"
        private const val KEY_READY = "ready_release"
        private const val KEY_REJECTED = "rejected_tag"
        private const val KEY_AWAITING_PERMISSION = "awaiting_install_permission"
        private const val DIR_NAME = "app-update"
        private const val PART_SUFFIX = ".part"
        private const val SESSION_APK_NAME = "base.apk"
        private const val ACCEPT_GITHUB_JSON = "application/vnd.github+json"
        private const val ACCEPT_ANY = "*/*"
        private const val TEXT_TIMEOUT_MS = 15_000
        private const val MAX_LIST_BYTES = 4 shl 20

        /** CHANGELOG.md is ~10 KB; only its newest sections are shown. */
        private const val MAX_CHANGELOG_BYTES = 256 shl 10
        private const val RETRY_DELAY_MS = 2_000L
        private const val STATUS_GRACE_MS = 2_000L
        private const val PROGRESS_EVERY_NS = 250_000_000L
        private const val BUFFER_SIZE = 1 shl 16

        /** Beyond twice the APK (the download, then the session's copy). */
        private const val SPACE_MARGIN_BYTES = 32L shl 20
        private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60 * 1000

        /** The APK's name in the cache: the tag is checked digits, dots and dashes. */
        private fun apkName(release: AppRelease) = "pardes-${release.tag}.apk"

        @Volatile
        private var instance: AppUpdater? = null

        fun get(app: ZayitApplication): AppUpdater =
            instance ?: synchronized(this) { instance ?: AppUpdater(app).also { instance = it } }

        fun get(context: Context): AppUpdater = get(context.applicationContext as ZayitApplication)
    }
}

/** InputStream.skipNBytes is API 34: skips [n] bytes or fails (a resumed copy of a local file). */
private fun InputStream.skipFully(n: Long) {
    var left = n
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) {
            left -= skipped
        } else if (read() >= 0) {
            left--
        } else {
            throw IOException("Unexpected end of file")
        }
    }
}

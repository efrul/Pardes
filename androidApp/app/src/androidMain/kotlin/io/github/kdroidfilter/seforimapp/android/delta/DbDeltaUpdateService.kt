package io.github.kdroidfilter.seforimapp.android.delta

import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.DatabaseVersion
import io.github.kdroidfilter.seforimapp.android.data.HttpStatusException
import io.github.kdroidfilter.seforimapp.android.data.InstallToken
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.data.LibraryMover
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.data.ResumingHttpInputStream
import io.github.kdroidfilter.seforimapp.android.data.SplitDatabase
import io.github.kdroidfilter.seforimapp.android.readAtMost
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.releaseReadGrants
import io.github.kdroidfilter.seforimapp.android.update.AppUpdater
import io.github.kdroidfilter.seforimlibrary.common.patch.LogicalContentHasher
import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaApplierClient
import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaDownloader
import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaEntry
import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaManifest
import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaUpdaterClient
import io.github.kdroidfilter.seforimlibrary.deltaupdater.FullBundleEntry
import io.github.kdroidfilter.seforimlibrary.deltaupdater.NotEnoughSpaceException
import io.github.kdroidfilter.seforimlibrary.deltaupdater.ReleaseMeta
import io.github.kdroidfilter.seforimlibrary.deltaupdater.UpdateOrchestrator
import io.github.kdroidfilter.seforimlibrary.deltaupdater.UpdatePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop 0.6.1 `features/settings/dbupdate/DbDeltaUpdateState.kt`, plus what the touch flow shows: the update found
 * by a check ([offer], with its size, waiting for the user), the progress of the step, and whether it can be stopped.
 */
data class DbDeltaUpdateState(
    /** Active op or `null` when idle. */
    val phase: Phase? = null,
    /** Most recent message (download progress, apply phase, error, success). */
    val message: String = "",
    /** Filled once the apply succeeds: number of deltas applied in the last run. */
    val lastAppliedCount: Int? = null,
    /** Filled when the server reports the local version too old for incremental updates. */
    val needsFullBundle: Boolean = false,
    /** Set when an exception escaped — the UI can show a "retry" affordance. */
    val errorMessage: String? = null,
    /** Android: the last check found nothing newer. */
    val upToDate: Boolean = false,
    /** Android: an update was found and waits for "הורד עכשיו" (or an unfinished one for "המשך"). */
    val offer: Offer? = null,
    /** Android: 0..1 for the current step, null when it cannot be measured. */
    val progress: Float? = null,
    /** Android: "12 מ״ב / 55 מ״ב", lines indexed… */
    val detail: String? = null,
    /** Android: the run can still be stopped without leaving anything half done (before the SQLite COMMIT). */
    val cancellable: Boolean = false,
) {
    enum class Phase { CheckingForUpdates, Downloading, Applying, UpdatingIndex }

    data class Offer(val toVersion: Int, val bytes: Long, val steps: Int, val resume: Boolean)
}

/** Desktop `DbDeltaUpdateEvents`, with the touch flow's separate apply and cancel. */
sealed interface DbDeltaUpdateEvents {
    data object CheckClicked : DbDeltaUpdateEvents

    data object ApplyClicked : DbDeltaUpdateEvents

    data object CancelClicked : DbDeltaUpdateEvents

    /** Dismiss the current message / error after the user has acknowledged it. */
    data object ClearMessage : DbDeltaUpdateEvents
}

/** Desktop's section strings are English literals; these are their Hebrew, with 0.6.1 strings.xml wording where it has one. */
internal object DeltaStrings {
    const val title = "עדכונים מצטברים למסד הנתונים" // "Database delta updates"
    const val idle = "לחצו על הכפתור כדי לבדוק אם יש בשרת עדכון מצטבר למסד הנתונים." // "Idle. Press the button below…"
    const val check = "בדוק עדכונים" // "Check for updates"
    const val checkAgain = "בדוק שוב" // update_check_action
    const val working = "עובד…" // "Working…"
    const val dismiss = "סגור" // "Dismiss"
    const val checking = "בודק עדכונים…" // update_checking
    const val checkFailed = "לא ניתן לבדוק עדכונים — ודא שקיים חיבור לאינטרנט" // update_check_failed
    const val upToDate = "מסד הנתונים מעודכן לגרסה האחרונה." // "Database is up to date." (update_up_to_date)
    const val appliedOne = "עדכון מצטבר אחד הוחל. מסד הנתונים עודכן בהצלחה." // "Applied 1 delta(s)."
    const val appliedMany = "הוחלו %1\$d עדכונים מצטברים. מסד הנתונים עודכן בהצלחה."
    const val needsFull = "מסד הנתונים המקומי ישן מדי לעדכון מצטבר — יש להוריד את המאגר המלא." // "…too old for an incremental update…"
    const val failed = "העדכון נכשל: %1\$s" // "Update failed: …"
    const val available = "עדכון זמין" // update_dialog_title
    const val availableDetail = "עדכון מצטבר של מסד הנתונים: %1\$s להורדה, במקום הורדת המאגר המלא."
    const val resumeDetail = "עדכון קודם של מסד הנתונים לא הושלם. ניתן להמשיך מהמקום שבו נעצר."
    const val download = "הורד עכשיו" // update_download_action
    const val resume = "המשך" // db_update_continue
    const val cancel = "ביטול"
    const val preparing = "מתכונן להורדה..." // db_update_preparing_download
    const val downloading = "מוריד את העדכון…"
    const val extracting = "מחלץ את העדכון…"
    const val verifying = "בודק את מסד הנתונים הקיים…"
    const val applying = "מחיל את העדכון על מסד הנתונים…"
    const val verifyingResult = "מאמת את מסד הנתונים המעודכן…"
    const val indexing = "מעדכן את אינדקס החיפוש…"
    const val indexedLines = "%1\$d שורות עודכנו"
    const val catalog = "מעדכן את הקטלוג…"
    const val noSpace = "אין מספיק שטח פנוי להחלת העדכון: נדרשים %1\$s, פנויים %2\$s. פנו מקום ונסו שוב."
    const val backgroundHint = "ניתן לצאת מהיישום: העדכון ימשיך ברקע."

    // Android: an update from files brought to the device (no network).
    const val fromFiles = "עדכון מקבצים"
    const val fromFilesHint =
        "ללא אינטרנט: הורידו במחשב מעמוד ההפצה של SeforimLibrary את שני קובצי העדכון מהגרסה המותקנת, " +
            "patch-v…‎.db.zst ו-patch-v…‎.db.zst.manifest.json, העבירו אותם למכשיר ובחרו את שניהם, או את התיקייה שבה הם נמצאים. " +
            "כך אפשר גם לעדכן מכל קובצי ‎.part של המאגר המלא."
    const val splitUnsupported =
        "הספרייה שמורה בחלקים בכרטיס זיכרון FAT32, ועדכון מצטבר עדיין אינו זמין עבורה. " +
            "אפשר לעדכן אותה בעדכון מלא (הורדה, או קובצי ‎.part של המאגר המלא), או לפרמט את הכרטיס ל-exFAT ולהעביר אליו את הספרייה מחדש."
    const val fromFolder = "עדכון מתיקייה"
    const val folderEmpty = "בתיקייה שנבחרה אין קובצי עדכון או קובצי מאגר."
    const val folderIsLibrary =
        "בתיקייה שנבחרה יש ספרייה שלמה שאינה ארוזה (seforim.db לצד אינדקסי החיפוש, lexical.db או catalog.pb), " +
            "ועדכון מתיקייה היה לוקח ממנה רק את מסד הנתונים, בלי החיפוש והקטלוג. כדי להעביר ספרייה כזו מהמחשב הריצו את " +
            "סקריפט ההעתקה (push-desktop-library), או עדכנו מקובצי ‎.part של המאגר המלא."
    const val readingFiles = "בודק את הקבצים שנבחרו…"
    const val copying = "מעתיק את העדכון…"
    const val noManifest = "לא נבחר קובץ ‎.manifest.json של עדכון מצטבר."
    const val missingPatch = "חסר הקובץ %1\$s, שהעדכון צריך. בחרו אותו יחד עם קובץ ה-manifest."
    const val badChain = "קובצי העדכון שנבחרו אינם מתחברים לשרשרת אחת."
    const val localVersion = "עודכן מקבצים לגרסה %1\$d"
}

/**
 * Desktop 0.6.1 `framework/update/DbDeltaUpdateService.kt` (the app's facade over SeforimLibrary's DeltaUpdaterClient)
 * together with `features/settings/dbupdate/DbDeltaUpdateViewModel.kt` (the state the settings section renders), as one
 * process-wide object like [LibraryInstaller], so a run survives the screen and goes on in [LibraryDeltaService].
 *
 * Where desktop polls `release_meta.json` (still asked first, at the same URL), the releases of SeforimLibrary on
 * GitHub are the fallback: they carry the patch fan (`patch-v<from>-v<to>.db.zst` + its manifest) and no
 * release_meta.json, and that URL answers 404. The latest release is found as [LibraryInstaller] finds it (GitHub API,
 * then github.com's latest-release redirect, then release-manifest.json), with its HTTP stack (user CAs, Range-safe
 * identity encoding, reconnects). The installed version is the release whose tag carries release_info.txt's timestamp
 * (`v3-20261012193827` → 3): release 20261012193827's schema_meta says db_version 1. Desktop's schema_meta reading is
 * the fallback.
 *
 * A finished delta writes the release's timestamp into release_info.txt (the content hash proved the database is that
 * release's), so the startup version check follows; desktop leaves the file stale.
 */
class DbDeltaUpdateService private constructor(private val app: ZayitApplication) {
    private val _state = MutableStateFlow(DbDeltaUpdateState())
    val state: StateFlow<DbDeltaUpdateState> = _state.asStateFlow()

    /** Set while a screen shows the section; the service then skips the "finished" notification. */
    @Volatile
    var uiVisible: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var token: InstallToken? = null

    @Volatile
    private var activeStream: ResumingHttpInputStream? = null

    /** The chain the last check found, waiting for the user. */
    @Volatile
    private var plan: Plan? = null

    private val installer get() = LibraryInstaller.get(app)
    private val store get() = app.libraryStore

    val isBusy: Boolean get() = _state.value.phase != null

    fun onEvent(event: DbDeltaUpdateEvents) {
        when (event) {
            DbDeltaUpdateEvents.CheckClicked -> check()
            DbDeltaUpdateEvents.ApplyClicked -> apply()
            DbDeltaUpdateEvents.CancelClicked -> cancel()
            DbDeltaUpdateEvents.ClearMessage -> if (!isBusy) _state.value = _state.value.copy(message = "", errorMessage = null, needsFullBundle = false, upToDate = false, lastAppliedCount = null)
        }
    }

    /**
     * [local]: the delta came on files picked on the device (Android only): their content URIs by file name. The
     * manifests are read from them, the patches copied from them instead of downloaded.
     */
    private class Plan(val chain: List<DeltaEntry>, val releaseTag: String?, val bytes: Long, val resume: Boolean, val local: Map<String, Uri>? = null)

    private sealed interface Found {
        data object UpToDate : Found

        data object NeedsFullBundle : Found

        class Ready(val plan: Plan) : Found
    }

    /** Desktop's "Check for updates", stopping at the offer: on a phone the size is shown before anything is downloaded. */
    fun check() {
        if (isBusy || installer.isBusy || LibraryMover.get(app).isBusy || AppUpdater.get(app).isInstalling) return
        if (refuseSplit()) return
        val current = InstallToken().also { token = it }
        _state.value = DbDeltaUpdateState(phase = DbDeltaUpdateState.Phase.CheckingForUpdates, message = DeltaStrings.checking)
        job = scope.launch {
            val result = runCatching { findPlan(current) }
            if (token !== current) return@launch
            _state.value = result.fold(
                onSuccess = { found -> stateOf(found) },
                onFailure = { e -> if (current.cancelled.get()) DbDeltaUpdateState() else DbDeltaUpdateState(errorMessage = DeltaStrings.checkFailed + detailOf(e)) },
            )
        }
    }

    /** For the database update flow: the delta from the installed version, or null (no network, none published). */
    suspend fun findOffer(): DbDeltaUpdateState.Offer? = withContext(Dispatchers.IO) {
        if (isBusy) return@withContext _state.value.offer
        if (SplitDatabase.isSplit(store.databaseFile)) return@withContext null
        val found = runCatching { findPlan(InstallToken()) }.getOrNull()
        (found as? Found.Ready)?.let { stateOf(it).offer }
    }

    private fun stateOf(found: Found): DbDeltaUpdateState = when (found) {
        Found.UpToDate -> DbDeltaUpdateState(message = DeltaStrings.upToDate, upToDate = true).also { plan = null }
        Found.NeedsFullBundle -> DbDeltaUpdateState(message = DeltaStrings.needsFull, needsFullBundle = true).also { plan = null }
        is Found.Ready -> {
            plan = found.plan
            offerState(found.plan)
        }
    }

    private fun offerState(p: Plan) = DbDeltaUpdateState(
        message = if (p.resume) DeltaStrings.resumeDetail else DeltaStrings.availableDetail.format(formatBytes(p.bytes)),
        offer = DbDeltaUpdateState.Offer(p.chain.last().toVersion, p.bytes, p.chain.size, p.resume),
    )

    /** Downloads and applies the chain the check found (desktop's applyChain step of checkAndApply). */
    fun apply() {
        val p = plan ?: return check()
        if (isBusy || installer.isBusy || LibraryMover.get(app).isBusy || AppUpdater.get(app).isInstalling) return
        if (refuseSplit()) return
        val current = InstallToken().also { token = it }
        _state.value = DbDeltaUpdateState(phase = DbDeltaUpdateState.Phase.Downloading, message = DeltaStrings.preparing, cancellable = true)
        LibraryDeltaService.start(app)
        job = scope.launch { run(p, current) }
    }

    /**
     * Android only: applies a delta brought on files (picked with the system picker): one or more
     * `patch-v<from>-v<to>.db.zst.manifest.json` with their `patch-…db.zst`. The steps are chained by content hash from
     * the installed database's (each manifest's from-hash is the previous one's to-hash, [localPlan]), and the same
     * checks as a download guard them: each patch's size and sha256, then the database's content hash before and after.
     * A release tag is not in these files, so release_info.txt keeps its timestamp and the version reached is recorded
     * beside it ([LOCAL_VERSION_FILE]). The persistable read grants the picker's caller took on [uris] are given back
     * when the run ends, unless a cancel keeps the run for "המשך".
     */
    fun applyFromFiles(uris: List<Uri>) {
        if (uris.isEmpty() || isBusy || installer.isBusy || LibraryMover.get(app).isBusy || AppUpdater.get(app).isInstalling || refuseSplit()) {
            app.releaseReadGrants(uris) // not run: nothing will read them
            return
        }
        val current = InstallToken().also { token = it }
        _state.value = DbDeltaUpdateState(phase = DbDeltaUpdateState.Phase.CheckingForUpdates, message = DeltaStrings.readingFiles)
        job = scope.launch {
            val p = runCatching { localPlan(uris, current) }.getOrElse { e ->
                app.releaseReadGrants(uris)
                publish(current, if (current.cancelled.get()) DbDeltaUpdateState() else DbDeltaUpdateState(errorMessage = DeltaStrings.failed.format(e.message ?: e.javaClass.simpleName)))
                return@launch
            }
            if (p == null) {
                app.releaseReadGrants(uris)
                plan = null
                publish(current, DbDeltaUpdateState(message = DeltaStrings.upToDate, upToDate = true))
                return@launch
            }
            plan = p
            publish(current, DbDeltaUpdateState(phase = DbDeltaUpdateState.Phase.Downloading, message = DeltaStrings.copying, cancellable = true))
            LibraryDeltaService.start(app)
            run(p, current)
        }
    }

    /**
     * The chain of the picked deltas, or null when the database is already where the newest of them leads. One manifest
     * is the chain (its from-hash is checked when it applies). From several (a folder that keeps the patches applied
     * before, two patches of one release's fan) the chain starts at the installed database, by its content hash
     * computed once (minutes on 8 GB: shown, and cancellable), and each step is the one that starts where the last
     * ends, the furthest first (the fan's direct patch over its halves); manifests off that path are left out.
     */
    private fun localPlan(uris: List<Uri>, current: InstallToken): Plan? {
        val resolver = app.contentResolver
        val byName = uris.associateBy { uri ->
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
        }
        val manifests = byName.filterKeys { it.startsWith("patch-") && it.endsWith(MANIFEST_SUFFIX) }.map { (_, uri) ->
            uri to UpdateOrchestrator.DEFAULT_JSON.decodeFromString(DeltaManifest.serializer(), readText(uri))
        }
        if (manifests.isEmpty()) throw IOException(DeltaStrings.noManifest)
        val chain = mutableListOf<Pair<Uri, DeltaManifest>>()
        if (manifests.size == 1) {
            chain += manifests.single()
        } else {
            var at = installedContentHash(current)
            if (at == manifests.maxBy { it.second.toVersion }.second.toContentHash) return null
            while (true) {
                val next = manifests.filter { it !in chain && it.second.fromContentHash == at }.maxByOrNull { it.second.toVersion } ?: break
                chain += next
                at = next.second.toContentHash
            }
            if (chain.isEmpty()) throw IOException(DeltaStrings.badChain)
        }
        val entries = chain.map { (uri, manifest) ->
            manifest.patchFiles.forEach { if (it.file !in byName) throw IOException(DeltaStrings.missingPatch.format(it.file)) }
            DeltaEntry(manifest.fromVersion, manifest.toVersion, uri.toString(), manifest.patchFiles.sumOf { it.size })
        }
        return Plan(entries, releaseTag = null, bytes = entries.sumOf { it.totalSize }, resume = false, local = byName)
    }

    private fun readText(uri: Uri): String = app.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readAtMost(MAX_MANIFEST_BYTES + 1)
        if (bytes.size > MAX_MANIFEST_BYTES) throw IOException("Not a manifest: $uri")
        String(bytes, Charsets.UTF_8)
    } ?: throw IOException("Cannot read $uri")

    /**
     * The installed database's logical content hash, as [DeltaApplierClient] computes it before applying, with its
     * progress in the state. Minutes on a phone: the foreground service keeps the process meanwhile, and a cancel stops it.
     */
    private fun installedContentHash(current: InstallToken): String {
        publish(current, DbDeltaUpdateState(phase = DbDeltaUpdateState.Phase.Applying, message = DeltaStrings.verifying, progress = 0f, cancellable = true))
        LibraryDeltaService.start(app)
        return openLibraryDatabase(store.databaseFile).use { db ->
            LogicalContentHasher(
                progress = { done, total ->
                    if (due(done == total)) update(current) { it.copy(progress = (done.toDouble() / total).toFloat().coerceIn(0f, 1f)) }
                },
                checkCancelled = current::check,
            ).compute(db)
        }
    }

    /**
     * A database split in chunks on a FAT32 card (SplitDatabase) takes no incremental update yet: the delta code opens the
     * database with Android's SQLite, which cannot read the chunks. Says so, and true.
     */
    private fun refuseSplit(): Boolean {
        if (!SplitDatabase.isSplit(store.databaseFile)) return false
        _state.value = DbDeltaUpdateState(errorMessage = DeltaStrings.splitUnsupported)
        return true
    }

    /** Stops a check, or a run before its SQLite COMMIT (the transaction rolls back; downloads stay for a resume). */
    fun cancel() {
        val current = token ?: return
        if (_state.value.phase != DbDeltaUpdateState.Phase.CheckingForUpdates && !_state.value.cancellable) return
        current.cancelled.set(true)
        activeStream?.abort()
    }

    /**
     * At startup: a delta whose database part was committed before the app stopped gets its search index and catalog
     * finished in the background (desktop rolls back with its backup at boot; there is no backup here).
     */
    fun resumeIfPending() {
        if (isBusy || installer.isBusy || LibraryMover.get(app).isBusy || !store.databaseFile.isFile) return
        if (SplitDatabase.isSplit(store.databaseFile)) return
        val pending = DeltaApplierClient().pending(store.databaseFile.toPath()) ?: return
        if (pending.stage != DeltaApplierClient.Stage.Index || pending.manifestUrl == null) return
        plan = Plan(listOf(DeltaEntry(pending.fromVersion, pending.toVersion, pending.manifestUrl, 0L)), pending.releaseTag, 0L, resume = true)
        apply()
    }

    // ------------------------------------------------------------------ run

    private fun publish(current: InstallToken, state: DbDeltaUpdateState) {
        if (token === current) _state.value = state
    }

    private fun update(current: InstallToken, change: (DbDeltaUpdateState) -> DbDeltaUpdateState) {
        if (token === current) _state.value = change(_state.value)
    }

    private fun run(p: Plan, current: InstallToken) {
        val wakeLock = acquireWakeLock()
        try {
            client(current, p.local).applyChain(
                p.chain,
                progress = { _, _, status -> onStatus(current, status, local = p.local != null) },
                releaseTagFor = { entry -> p.releaseTag?.takeIf { tagNumber(it) == entry.toVersion } },
                onDeltaApplied = { entry ->
                    val tag = p.releaseTag?.takeIf { tagNumber(it) == entry.toVersion }
                    // A delta from files resumed after a restart has no local map: its manifest URL still says so.
                    if (tag != null) {
                        writeReleaseInfo(tag)
                    } else if (p.local != null || entry.manifestUrl.startsWith(CONTENT_SCHEME)) {
                        writeLocalVersion(entry.toVersion)
                    }
                },
            )
            closeSearch()
            plan = null
            releaseGrants(p)
            val n = p.chain.size
            publish(current, DbDeltaUpdateState(message = if (n == 1) DeltaStrings.appliedOne else DeltaStrings.appliedMany.format(n), lastAppliedCount = n))
        } catch (e: Throwable) {
            closeSearch()
            // A cancel keeps the plan, and the files it reads, for "המשך".
            if (!current.cancelled.get()) releaseGrants(p)
            publish(
                current,
                when {
                    current.cancelled.get() -> offerState(p)
                    e is NotEnoughSpaceException -> DbDeltaUpdateState(errorMessage = DeltaStrings.noSpace.format(formatBytes(e.required), formatBytes(e.available)))
                    else -> DbDeltaUpdateState(errorMessage = DeltaStrings.failed.format(e.message ?: e.javaClass.simpleName))
                },
            )
        } finally {
            activeStream = null
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    /** Desktop DbDeltaUpdateViewModel's mapping of the orchestrator's statuses to phases, with the Hebrew messages. */
    private fun onStatus(current: InstallToken, status: String, local: Boolean = false) {
        update(current) { s ->
            when {
                status == "downloading manifest" -> s.copy(
                    phase = DbDeltaUpdateState.Phase.Downloading,
                    message = if (local) DeltaStrings.copying else DeltaStrings.preparing,
                    progress = null,
                    detail = null,
                )
                "download" in status -> s.copy(phase = DbDeltaUpdateState.Phase.Downloading, message = if (local) DeltaStrings.copying else DeltaStrings.downloading)
                status.startsWith("decompressing") -> s.copy(message = DeltaStrings.extracting, progress = 0f, detail = null)
                "sqlite" in status -> s.copy(phase = DbDeltaUpdateState.Phase.Applying, message = DeltaStrings.verifying, progress = 0f, detail = null)
                "lucene" in status -> s.copy(phase = DbDeltaUpdateState.Phase.UpdatingIndex, message = DeltaStrings.indexing, progress = null, detail = null, cancellable = false)
                "catalog" in status -> s.copy(phase = DbDeltaUpdateState.Phase.UpdatingIndex, message = DeltaStrings.catalog, progress = null, detail = null, cancellable = false)
                else -> s
            }
        }
        // Lucene opens its own writer on the index: let the app's searcher go (it reopens on the next search).
        if ("lucene" in status) closeSearch()
    }

    private fun closeSearch() {
        runCatching { runBlocking { app.search.close() } }
    }

    /** Gives back the read grants of the picked files a run read: its patches and manifests (a resumed run's manifest). */
    private fun releaseGrants(p: Plan) {
        val uris = p.local?.values.orEmpty() + p.chain.map { it.manifestUrl }.filter { it.startsWith(CONTENT_SCHEME) }.map(Uri::parse)
        if (uris.isNotEmpty()) app.releaseReadGrants(uris)
    }

    private var lastEmitNs = 0L

    /** At most ~4 updates a second from the byte and row counters. */
    private fun due(force: Boolean): Boolean {
        val now = System.nanoTime()
        if (!force && now - lastEmitNs < 250_000_000L) return false
        lastEmitNs = now
        return true
    }

    private fun client(current: InstallToken, local: Map<String, Uri>? = null): DeltaUpdaterClient {
        val db = store.databaseFile
        var indexed = 0
        return DeltaUpdaterClient(
            seforimDb = db.toPath(),
            catalogPb = store.catalogFile.toPath(),
            workDir = File(store.libraryDir, WORK_DIR).toPath(),
            releaseMetaUrl = RELEASE_META_URL,
            indexSinks = LuceneDeltaSinks.provider(store.luceneDir, db) {
                indexed++
                if (due(false)) update(current) { it.copy(detail = DeltaStrings.indexedLines.format(indexed)) }
            },
            localVersionProvider = { localVersion(current, null) },
            downloader = DeltaDownloader(
                openStream = { url, size, startAt ->
                    if (url.startsWith(CONTENT_SCHEME)) {
                        // A patch picked on the device: copied into the delta cache like a download, checked the same way.
                        (app.contentResolver.openInputStream(Uri.parse(url)) ?: throw IOException("Cannot read $url")).apply { skipFully(startAt) }
                    } else {
                        ResumingHttpInputStream(url, size, current, installer::networkAvailable, startAt) { lost ->
                            update(current) { it.copy(detail = if (lost) AndroidStrings.reconnecting else it.detail) }
                        }.also { activeStream = it }
                    }
                },
                onProgress = { done, total ->
                    if (due(done == total)) {
                        update(current) {
                            it.copy(
                                progress = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else null,
                                detail = if (total > 0) DesktopStrings.onboardingDownloadProgress.format(formatBytes(done), formatBytes(total)) else formatBytes(done),
                            )
                        }
                    }
                },
                checkCancelled = current::check,
            ),
            httpGet = { url -> if (url.startsWith(CONTENT_SCHEME)) readText(Uri.parse(url)) else installer.httpGetText(url, LibraryInstaller.ACCEPT_ANY, current) },
            isNotFound = { it is HttpStatusException && it.code == 404 },
            urlForPatchFile = { entry, file -> local?.get(file)?.toString() ?: (entry.manifestUrl.substringBeforeLast('/') + "/" + file) },
            hashProgress = { done, total, after ->
                if (due(done == total)) {
                    val fraction = (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
                    update(current) {
                        when {
                            !after && done >= total -> it.copy(message = DeltaStrings.applying, progress = null)
                            after -> it.copy(message = DeltaStrings.verifyingResult, progress = fraction)
                            else -> it.copy(message = DeltaStrings.verifying, progress = fraction)
                        }
                    }
                }
            },
            onDecompress = { written, total ->
                if (due(written == total)) update(current) { it.copy(progress = if (total > 0) (written.toDouble() / total).toFloat().coerceIn(0f, 1f) else null) }
            },
            checkCancelled = current::check,
        )
    }

    // ------------------------------------------------------------------ discovery

    /** Blocking: what can be done from the installed library, by desktop's release_meta.json, else the GitHub release. */
    private fun findPlan(current: InstallToken): Found {
        if (!store.databaseFile.isFile) throw IllegalStateException("No library")
        val client = client(current)
        client.pendingChain()?.let { (chain, tag) -> return Found.Ready(Plan(chain, tag, 0L, resume = true)) }
        val latest = runCatching { installer.fetchLatestRelease(current) }.onFailure { if (current.cancelled.get()) throw it }.getOrNull()
        val local = localVersion(current, latest?.tag)
        // Desktop's index first. Unlike desktop, any failure there (not only a 404: a filter's block page, a DNS
        // error) falls back to the GitHub release rather than failing the check.
        val desktopMeta = runCatching { client.fetchReleaseMeta() }.onFailure { if (current.cancelled.get()) throw it }.getOrNull()
        val meta: ReleaseMeta
        val tag: String?
        if (desktopMeta != null) {
            meta = desktopMeta
            tag = tagInUrl(desktopMeta.fullBundle.url) ?: latest?.tag?.takeIf { tagNumber(it) == desktopMeta.latestVersion }
        } else {
            val release = latest ?: throw IOException("No release found")
            meta = releaseMeta(client, release, local)
            tag = release.tag
        }
        return when (val path = DeltaUpdaterClient.choosePath(local, meta)) {
            UpdatePath.UpToDate -> Found.UpToDate
            is UpdatePath.FullBundle -> Found.NeedsFullBundle
            is UpdatePath.Chain -> Found.Ready(Plan(path.deltas, tag, path.deltas.sumOf { it.totalSize }, resume = false))
        }
    }

    /**
     * A release_meta.json for a GitHub release `v<N>-<timestamp>`: the full bundle (its parts' size) and the direct
     * patch from [local] when the release has one (the workflow names it `patch-v<local>-v<N>.db.zst` and puts its
     * manifest next to it).
     */
    private fun releaseMeta(client: DeltaUpdaterClient, release: LibraryInstaller.Release, local: Int): ReleaseMeta {
        val n = tagNumber(release.tag) ?: return ReleaseMeta(latestVersion = local, fullBundle = FullBundleEntry(local, "", "", 0L), deltas = emptyList())
        val base = LibraryInstaller.RELEASE_DOWNLOAD_BASE + release.tag + "/"
        val bundleSize = release.assets.filter { it.name.startsWith("$BUNDLE_NAME.part") }.sumOf { it.size.coerceAtLeast(0L) }
            .takeIf { it > 0 } ?: LibraryInstaller.ESTIMATED_BUNDLE_BYTES
        val full = FullBundleEntry(n, "$base$BUNDLE_NAME.manifest.json", "", bundleSize)
        if (local !in 1 until n) return ReleaseMeta(n, full, emptyList())
        val name = "patch-v$local-v$n.db.zst.manifest.json"
        val url = release.assets.firstOrNull { it.name == name }?.url ?: (base + name)
        val manifest = try {
            client.fetchManifest(DeltaEntry(local, n, url, 0L))
        } catch (e: HttpStatusException) {
            if (e.code == 404) null else throw e
        }
        val deltas = listOfNotNull(manifest?.let { m -> DeltaEntry(local, n, url, m.patchFiles.sumOf { it.size }) })
        return ReleaseMeta(n, full, deltas)
    }

    /**
     * The installed release's version: the tag whose timestamp release_info.txt holds (the latest release's, or one of
     * release-manifest.json's list), else desktop's provider (schema_meta.db_version, 0 when absent).
     */
    private fun localVersion(current: InstallToken, latestTag: String?): Int {
        val stamp = DatabaseVersion.current(store)
        localUpdateVersion(stamp)?.let { return it }
        if (stamp != null) {
            latestTag?.takeIf { LibraryInstaller.versionFromTag(it) == stamp }?.let(::tagNumber)?.let { return it }
            runCatching { releaseTags(current) }.getOrNull()
                ?.firstOrNull { LibraryInstaller.versionFromTag(it) == stamp }
                ?.let(::tagNumber)
                ?.let { return it }
        }
        return runCatching {
            openLibraryDatabase(store.databaseFile).use { db ->
                db.rawQuery("SELECT value FROM schema_meta WHERE key='db_version'", null).use { c -> if (c.moveToFirst()) c.getString(0)?.toIntOrNull() else null }
            }
        }.getOrNull() ?: 0
    }

    /** Every tag release-manifest.json lists (its latest and its release list). */
    private fun releaseTags(current: InstallToken): List<String> {
        val root = Json.parseToJsonElement(installer.httpGetText(LibraryInstaller.RELEASE_MANIFEST_URL, LibraryInstaller.ACCEPT_ANY, current)).jsonObject
        fun JsonObject.tag() = (this["tagName"] as? JsonPrimitive)?.contentOrNull
        return listOfNotNull(root["latest"]?.jsonObject?.tag()) + root["releases"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.tag() }
    }

    /**
     * The version a delta from files reached (Android only), while release_info.txt still holds the [stamp] it had
     * then: a later download or install writes a new stamp, and the record no longer counts.
     */
    private fun localUpdateVersion(stamp: String?): Int? = runCatching {
        val lines = File(store.libraryDir, LOCAL_VERSION_FILE).readLines()
        lines[0].trim().toInt().takeIf { lines.getOrNull(1)?.trim().orEmpty() == stamp.orEmpty() }
    }.getOrNull()

    /** The version a delta from files reached, for the settings to show beside release_info.txt's date, or null. Blocking. */
    fun localUpdateVersion(): Int? = localUpdateVersion(DatabaseVersion.current(store))

    private fun writeLocalVersion(version: Int) {
        val target = File(store.libraryDir, LOCAL_VERSION_FILE)
        val tmp = File(target.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write("$version\n${DatabaseVersion.current(store).orEmpty()}\n".toByteArray())
            out.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** release_info.txt = the release's timestamp, written aside and renamed (the startup check reads it). */
    private fun writeReleaseInfo(tag: String) {
        val stamp = LibraryInstaller.versionFromTag(tag) ?: return
        File(store.libraryDir, LOCAL_VERSION_FILE).delete()
        val target = store.releaseInfoFile
        val tmp = File(target.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(stamp.toByteArray())
            out.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        app.getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pardes:LibraryDelta")?.apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }.getOrNull()

    private fun detailOf(e: Throwable): String = if (e is IOException) "" else e.message?.let { " ($it)" }.orEmpty()

    companion object {
        /** Desktop AppCoreBindings.provideDbDeltaUpdateService's default (SEFORIMAPP_RELEASE_META_URL overrides it there). */
        const val RELEASE_META_URL = "https://kdroidfilter.github.io/SefariaExport/release_meta.json"

        /** Desktop's work folder next to seforim.db. */
        const val WORK_DIR = "delta-cache"

        private const val BUNDLE_NAME = "seforim_bundle.tar.zst"
        private const val MANIFEST_SUFFIX = ".manifest.json"
        private const val CONTENT_SCHEME = "content:"
        private const val MAX_MANIFEST_BYTES = 4 shl 20

        /** Beside release_info.txt: the version a delta from files reached, and the release_info.txt stamp it applies to. */
        const val LOCAL_VERSION_FILE = LibraryStore.LOCAL_VERSION_NAME
        private const val WAKE_LOCK_TIMEOUT_MS = 3L * 60 * 60 * 1000
        private val TAG = Regex("""^v(\d+)-\d{14}$""")

        /** "v3-20261012193827" → 3 (the release workflow's db_version). */
        fun tagNumber(tag: String): Int? = TAG.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()

        /** ".../releases/download/v3-20261012193827/seforim_bundle.tar.zst" → "v3-20261012193827". */
        private fun tagInUrl(url: String): String? = url.split('/').firstOrNull { TAG.matches(it) }

        @Volatile
        private var instance: DbDeltaUpdateService? = null

        fun get(app: ZayitApplication): DbDeltaUpdateService =
            instance ?: synchronized(this) { instance ?: DbDeltaUpdateService(app).also { instance = it } }
    }
}

/** InputStream.skipNBytes is API 34: skips [n] bytes or fails (a resumed copy of a picked patch). */
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

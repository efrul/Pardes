package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import android.os.StatFs
import android.system.Os
import android.provider.OpenableColumns
import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.releaseReadGrants
import io.github.kdroidfilter.seforimapp.android.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp

/** Why an installation stopped; each value maps to the desktop message the onboarding shows. */
enum class InstallError {
    Generic,
    InsufficientSpace,
    IncompleteBundle,
    Incompatible,

    /** Android: the memory card's filesystem (FAT32) cannot hold a file that big ([LibraryLocation.maxFileBytes]). */
    FileTooLarge,
}

/** Progress of the library installation, observed by the onboarding and by [LibraryInstallService]. */
sealed interface InstallState {
    data object Idle : InstallState

    /** Looking up the latest release (online), or checking the picked files (offline), and the free space. */
    data class Preparing(val online: Boolean) : InstallState

    /** Online: the bundle streams from GitHub and is unpacked on the fly. */
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSec: Long,
        val reconnecting: Boolean = false,
    ) : InstallState {
        val progress: Float
            get() = if (totalBytes != null && totalBytes > 0) (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f
    }

    /** Offline: unpacking the picked files ([progress] counts compressed bytes read). */
    data class Extracting(val progress: Float, val online: Boolean) : InstallState

    /** Checking that the new database opens and is recent enough (desktop VersionVerificationScreen). */
    data object Verifying : InstallState

    data object Done : InstallState

    data class Failed(val error: InstallError, val detail: String?, val online: Boolean) : InstallState
}

/** The flow a run belongs to: the first-run onboarding, or desktop's DatabaseUpdateWindow (ui/database). */
enum class InstallPurpose { Onboarding, Update }

/**
 * Desktop `framework/database/DatabaseVersionManager.kt`: the library's version is the yyyyMMddHHmmss timestamp in
 * release_info.txt next to the database. A library without that file, or with a value that is not 14 digits, is not
 * compatible.
 */
object DatabaseVersion {
    /**
     * Desktop 0.6.1 MINIMUM_REQUIRED_VERSION. The 0.6.1 dao compiled into the app reads the author columns and tables
     * these releases added: older ones fail every getBook().
     */
    const val MINIMUM_REQUIRED_VERSION = "20261011170830"

    /** getCurrentDatabaseVersion: release_info.txt's trimmed content, or null. Blocking. */
    fun current(store: LibraryStore): String? = runCatching { store.releaseInfoFile.takeIf { it.exists() }?.readText()?.trim() }.getOrNull()

    /** isDatabaseVersionCompatible for a version read from release_info.txt (null: no file). */
    fun isCompatible(version: String?): Boolean {
        if (version == null) return false
        if (version.length != 14 || MINIMUM_REQUIRED_VERSION.length != 14) return false
        return runCatching { version.toLong() >= MINIMUM_REQUIRED_VERSION.toLong() }.getOrDefault(false)
    }

    /** formatVersionForDisplay: "dd/MM/yyyy HH:mm:ss", or the value as it is when it is not 14 characters. */
    fun formatForDisplay(version: String): String =
        if (version.length == 14) {
            "${version.substring(6, 8)}/${version.substring(4, 6)}/${version.substring(0, 4)} " +
                "${version.substring(8, 10)}:${version.substring(10, 12)}:${version.substring(12, 14)}"
        } else {
            version
        }
}

/**
 * First-run installation of the library, shared by the onboarding screens and [LibraryInstallService].
 * There is one instance per process, so a running install survives rotation and the activity being closed.
 *
 * Desktop (DownloadUseCase / ExtractUseCase) downloads `seforim_bundle.tar.zst.partNN` from the latest
 * SeforimLibrary release and then unpacks it. Here the bundle is streamed instead:
 * HTTP → sha256 check of each part → zstd → tar, and the same files as desktop are written (see
 * [LibraryStore]): the Lucene indexes that search reads, seforim.db, lexical.db, catalog.pb and
 * release_info.txt. Nothing compressed is stored, so the device only needs room for the unpacked library.
 * Each item is written under a `.part` name and renamed when the whole bundle has been read and checked,
 * the database last, so an interrupted install never leaves a half index or database that looks valid.
 * A dropped connection resumes from the last byte received with an HTTP Range request.
 */
class LibraryInstaller private constructor(private val context: Context) {
    private val store: LibraryStore = (context as? ZayitApplication)?.libraryStore ?: LibraryStore(context)

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /** Set while the onboarding or the database update is on screen; the service then skips the "finished" notification. */
    @Volatile
    var uiVisible: Boolean = false

    /** Which flow started the run: the notification uses its wording (desktop onboarding or DatabaseUpdateWindow). */
    @Volatile
    var purpose: InstallPurpose = InstallPurpose.Onboarding

    /** Whether the last run was a download (true) or a local import, for a screen that reopens on a running install. */
    val lastRunOnline: Boolean get() = lastRequest?.online ?: true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var token: InstallToken? = null

    @Volatile
    private var activeDownload: ResumingHttpInputStream? = null
    private var lastRequest: Request? = null

    val isBusy: Boolean
        get() = when (_state.value) {
            is InstallState.Preparing, is InstallState.Downloading, is InstallState.Extracting, InstallState.Verifying -> true
            else -> false
        }

    /** Downloads and installs the latest release. */
    fun startOnline() = begin(Request.Online)

    /** Installs from files picked with the system file picker (bundle parts, a .tar.zst, or a seforim.db). */
    fun importUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) begin(Request.Uris(uris))
    }

    /** Installs from bundle files found in the app's own folders ([findLocalBundle]). */
    fun importFiles(files: List<File>) {
        if (files.isNotEmpty()) begin(Request.Files(files))
    }

    /** Runs the last request again after a failure. */
    fun retry() {
        lastRequest?.let { begin(it) }
    }

    fun cancel() {
        val current = token ?: return
        current.cancelled.set(true)
        activeDownload?.abort()
        token = null
        _state.value = InstallState.Idle
    }

    /** Forgets a finished or failed attempt (e.g. "התחל התקנה מחדש"). */
    fun reset() {
        if (!isBusy) _state.value = InstallState.Idle
    }

    /** Free space where the next install writes ([LibraryStore.targetLocation]: the phone, or the chosen memory card). */
    fun diskSpace(): DiskSpace = targetDir?.let { dir ->
        runCatching { StatFs(dir.path).let { DiskSpace(totalBytes = it.totalBytes, availableBytes = it.availableBytes) } }.getOrNull()
    } ?: store.targetLocation().space()

    fun isMeteredNetwork(): Boolean =
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true }.getOrDefault(false)

    /** Size of a library already on the device (database and indexes), or null. Blocking: may adopt a database dropped by adb. */
    fun installedLibrarySize(): Long? = if (store.isInstalled()) store.installedBytes() else null

    /**
     * Checks the installed library with desktop's DatabaseVersionManager rules (a library without release_info.txt is
     * not compatible), then that its database has the library tables; null when it can be used. Blocking.
     */
    fun checkInstalledLibrary(): InstallError? {
        if (!store.isInstalled()) return InstallError.Incompatible
        return checkDatabase(store.databaseFile, DatabaseVersion.current(store))
    }

    /**
     * Drops what a killed install, move or transfer left half written, so the disk step counts that space as free and
     * no hidden copy keeps gigabytes. Only staged (`.part`) names go, never a library's own; nothing while an install or
     * a move runs.
     */
    fun discardStaleDownload() {
        if (isBusy || LibraryMover.get(context).isBusy) return
        val stagedDb = LibraryStore.DB_NAME + LibraryStore.PARTIAL_SUFFIX
        libraryDirs().forEach { dir ->
            LibraryStore.LIBRARY_ITEMS.forEach { File(dir, it + LibraryStore.PARTIAL_SUFFIX).deleteRecursively() }
            // The mover's staged write-ahead log, the staged database's side files and its overflow chunks (a FAT32 card).
            File(dir, LibraryStore.DB_NAME + "-wal" + LibraryStore.PARTIAL_SUFFIX).delete()
            DB_SIDECARS.forEach { File(dir, stagedDb + it).delete() }
            SplitDatabase.deleteOverflow(dir, stagedDb)
        }
    }

    /** Every place LibraryStore may keep the library in (the phone and the memory cards). */
    private fun libraryDirs(): List<File> = (store.candidateDirs + store.libraryDir).distinct()

    /**
     * Bundle files (parts, manifest or a single .tar.zst) copied into the app's own folders by adb or a file manager,
     * on the phone or on a memory card (`Android/data/<app>/files/`, which a computer can write to straight).
     */
    fun findLocalBundle(): List<File> {
        val dirs = (store.locations.map { it.dir.parentFile } + store.libraryDir + context.filesDir).filterNotNull().distinct()
        return dirs.flatMap { dir ->
            dir.listFiles().orEmpty().filter { file ->
                file.isFile && (BundleNames.partNumber(file.name) != null || BundleNames.isManifest(file.name) || file.name.endsWith(".tar.zst", ignoreCase = true))
            }
        }
    }

    private sealed class Request(val online: Boolean) {
        data object Online : Request(true)

        class Uris(val uris: List<Uri>) : Request(false)

        class Files(val files: List<File>) : Request(false)
    }

    private fun begin(request: Request) {
        // An app update being installed ends the process: no install starts under it
        if (isBusy || LibraryMover.get(context).isBusy || AppUpdater.get(context).isInstalling) return
        lastRequest = request
        val previous = job
        val current = InstallToken()
        token = current
        _state.value = InstallState.Preparing(request.online)
        LibraryInstallService.start(context)
        job = scope.launch {
            // A cancelled run may still be unwinding; never let two runs write the same files.
            previous?.join()
            execute(request, current)
        }
    }

    /** State changes from a run that has since been cancelled or replaced are dropped. */
    private fun publish(current: InstallToken, state: InstallState) {
        if (token === current) _state.value = state
    }

    private fun execute(request: Request, current: InstallToken) {
        targetDir = null
        val wakeLock = acquireWakeLock()
        try {
            when (request) {
                Request.Online -> installOnline(current)
                is Request.Uris -> importOffline(request.uris.map(::pickedFromUri), current)
                is Request.Files -> importOffline(request.files.map(::pickedFromFile), current)
            }
            // The read grants kept on the picked files, so that the run could outlive the screen, are not needed any
            // more. A failed run keeps them for its retry.
            if (request is Request.Uris) context.releaseReadGrants(request.uris)
            publish(current, InstallState.Done)
        } catch (e: Throwable) {
            runCatching { discardStaged() }
            if (current.cancelled.get()) {
                publish(current, InstallState.Idle)
            } else {
                publish(current, failureOf(e, request.online))
            }
        } finally {
            activeDownload = null
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    // ---------------------------------------------------------------- online

    private fun installOnline(current: InstallToken) {
        val release = fetchLatestRelease(current)
        // Checked before anything is deleted: a release older than the app needs (the manifest fallback can name one)
        // would leave the user with no library at all. A tag of an unknown form passes only when there is nothing to lose.
        val releaseVersion = versionFromTag(release.tag)
        if (releaseVersion != null && !DatabaseVersion.isCompatible(releaseVersion)) throw InstallException(InstallError.Incompatible)
        if (releaseVersion == null && store.isInstalled()) throw InstallException(InstallError.Incompatible, "Unknown release ${release.tag}")
        val parts = planDownload(release, current)
        val total = parts.sumOf { it.size.coerceAtLeast(0L) }
        val knownTotal = total.takeIf { it > 0L && parts.all { part -> part.size >= 0L } }
        prepareTarget(if (knownTotal != null) estimateLibraryBytes(knownTotal) else ESTIMATED_LIBRARY_BYTES)

        val meter = TransferSpeedMeter()
        var position = 0L
        var reconnecting = false
        fun report(force: Boolean) {
            val speed = meter.update(position)
            if (force || meter.due()) {
                publish(current, InstallState.Downloading(position, knownTotal, if (reconnecting) 0L else speed, reconnecting))
            }
        }

        val sources = parts.map { part ->
            BundlePartSource(part.name, part.size, part.sha256) {
                ResumingHttpInputStream(part.url, part.size, current, ::networkAvailable) { lost ->
                    reconnecting = lost
                    report(force = true)
                }.also { activeDownload = it }
            }
        }
        publish(current, InstallState.Downloading(0L, knownTotal, 0L))
        val version = ConcatenatedBundleParts(sources, current) { read ->
            position = read
            report(force = false)
        }.use { input -> unpackBundle(input, current) }
        publish(current, InstallState.Downloading(position, position, 0L))
        finishInstall(current, version ?: versionFromTag(release.tag), cleanup = emptyList())
    }

    internal class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

    internal class Release(val tag: String, val assets: List<ReleaseAsset>)

    private class RemotePart(val name: String, val url: String, val size: Long, val sha256: String?)

    /**
     * Same source as desktop (the GitHub API). When the API fails (60 requests an hour without a token), the latest
     * release is found through github.com's own `releases/latest` redirect and its bundle manifest, and only then
     * through the repo's release-manifest.json, which can lag behind.
     */
    internal fun fetchLatestRelease(current: InstallToken): Release {
        val fromApi = runCatching { parseApiRelease(httpGetText(LATEST_RELEASE_API, ACCEPT_GITHUB_JSON, current)) }
            .onFailure { if (current.cancelled.get()) throw it }
            .getOrNull()
        if (fromApi != null && fromApi.assets.isNotEmpty()) return fromApi
        val fromRedirect = runCatching { releaseFromRedirect(current) }
            .onFailure { if (current.cancelled.get()) throw it }
            .getOrNull()
        if (fromRedirect != null) return fromRedirect
        return parseReleaseManifest(httpGetText(RELEASE_MANIFEST_URL, ACCEPT_ANY, current))
    }

    /** The tag github.com redirects `releases/latest` to, and the parts its bundle manifest lists (no API call). */
    private fun releaseFromRedirect(current: InstallToken): Release? {
        current.check()
        val connection = URI.create(LATEST_RELEASE_PAGE).toURL().openConnection() as HttpURLConnection
        val tag = try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.responseCode
            connection.getHeaderField("Location")?.substringAfter("/releases/tag/", "")?.substringBefore('?')?.takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        } ?: return null
        val base = "$RELEASE_DOWNLOAD_BASE$tag/"
        val manifestName = BundleNames.manifestNameFor(BundleNames.BUNDLE_NAME)
        val manifest = parseBundleManifest(httpGetText(base + manifestName, ACCEPT_ANY, current)) ?: return null
        val assets = manifest.parts.map { ReleaseAsset(it.file, base + it.file, it.size, it.sha256) } +
            ReleaseAsset(manifestName, base + manifestName, -1L, null)
        return Release(tag, assets)
    }

    /** The bundle files to stream, in order, as desktop's DownloadUseCase picks them. */
    private fun planDownload(release: Release, current: InstallToken): List<RemotePart> {
        val byName = release.assets.associateBy { it.name }
        val manifest = byName[BundleNames.manifestNameFor(BundleNames.BUNDLE_NAME)]?.let { asset ->
            runCatching { parseBundleManifest(httpGetText(asset.url, ACCEPT_ANY, current)) }
                .onFailure { if (current.cancelled.get()) throw it }
                .getOrNull()
        }
        if (manifest != null) {
            return manifest.parts.map { part ->
                val asset = byName[part.file] ?: throw InstallException(InstallError.Generic, "Release is missing ${part.file}")
                RemotePart(part.file, asset.url, part.size, part.sha256)
            }
        }
        val legacy = BundleNames.legacyPartNames(byName.keys).map { byName.getValue(it) }
        if (legacy.isNotEmpty()) {
            BundleNames.requireComplete(legacy.map { it.size })
            return legacy.map { RemotePart(it.name, it.url, it.size, it.sha256) }
        }
        val single = release.assets.firstOrNull { it.name.endsWith(".tar.zst", ignoreCase = true) }
            ?: throw InstallException(InstallError.Generic, "No bundle assets found in latest release")
        return listOf(RemotePart(single.name, single.url, single.size, single.sha256))
    }

    // ---------------------------------------------------------------- offline

    private class PickedFile(val name: String, val size: Long, val localFile: File?, val open: () -> InputStream)

    private fun pickedFromUri(uri: Uri): PickedFile {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val displayName = name
        return PickedFile(displayName, size, null) {
            resolver.openInputStream(uri) ?: throw IOException("Cannot open $displayName")
        }
    }

    private fun pickedFromFile(file: File): PickedFile = PickedFile(file.name, file.length(), file) { FileInputStream(file) }

    private fun importOffline(picked: List<PickedFile>, current: InstallToken) {
        // A release_info.txt picked with the files gives the version of a database that comes without its bundle.
        val info = picked.firstOrNull { it.name.equals(RELEASE_INFO, ignoreCase = true) }
        val pickedVersion = info?.let { file -> runCatching { file.open().use { String(it.readBytes(), Charsets.UTF_8).trim() } }.getOrNull() }
        val files = picked - setOfNotNull(info)
        val parts = files.filter { BundleNames.partNumber(it.name) != null }
        val manifestFile = files.firstOrNull { BundleNames.isManifest(it.name) }
        val single = files.singleOrNull()
        val local = files.mapNotNull { it.localFile }
        // A lone database is only known by its release_info.txt: refuse it before the current library is deleted, as
        // desktop's version check would after the copy.
        fun requireVersion() {
            if (!DatabaseVersion.isCompatible(pickedVersion)) throw InstallException(InstallError.Incompatible)
        }
        when {
            parts.isNotEmpty() -> extractBundle(resolveOfflineParts(parts, manifestFile), current, cleanup = local, pickedVersion = pickedVersion)
            single != null && single.name.endsWith(".tar.zst", ignoreCase = true) ->
                extractBundle(listOf(BundlePartSource(single.name, single.size, null, single.open)), current, cleanup = local, pickedVersion = pickedVersion)
            single != null && single.name.endsWith(".zst", ignoreCase = true) -> {
                requireVersion()
                importZstDatabase(single, current, cleanup = local, version = pickedVersion)
            }
            single != null -> {
                requireVersion()
                importRawDatabase(single, current, cleanup = local, version = pickedVersion)
            }
            else -> throw InstallException(InstallError.IncompleteBundle)
        }
    }

    /** Orders the picked parts like desktop's BundleParts.resolve: by the manifest when given, else by number. */
    private fun resolveOfflineParts(parts: List<PickedFile>, manifestFile: PickedFile?): List<BundlePartSource> {
        val manifest = manifestFile?.let { file ->
            runCatching { parseBundleManifest(file.open().use { String(it.readBytes(), Charsets.UTF_8) }) }.getOrNull()
        }
        if (manifest != null) {
            val byName = parts.associateBy { it.name }
            return manifest.parts.map { part ->
                val file = byName[part.file] ?: throw InstallException(InstallError.IncompleteBundle, part.file)
                if (file.size >= 0 && file.size != part.size) throw InstallException(InstallError.IncompleteBundle, part.file)
                BundlePartSource(part.file, part.size, part.sha256, file.open)
            }
        }
        val byNumber = parts.associateBy { BundleNames.partNumber(it.name) ?: 0 }
        val ordered = BundleNames.requireContiguous(byNumber.keys).map { byNumber.getValue(it) }
        if (ordered.all { it.size >= 0 }) BundleNames.requireComplete(ordered.map { it.size })
        return ordered.map { BundlePartSource(it.name, it.size, null, it.open) }
    }

    private fun extractBundle(parts: List<BundlePartSource>, current: InstallToken, cleanup: List<File>, pickedVersion: String? = null) {
        val total = parts.sumOf { it.size.coerceAtLeast(0L) }
        val meter = TransferSpeedMeter()
        publish(current, InstallState.Extracting(0f, online = false))
        // Prepared on the first item written: a bundle that starts with release_info.txt (packed on a computer by a
        // tool, not desktop's order) is refused for an old release before anything is deleted.
        val version = ConcatenatedBundleParts(parts, current) { read ->
            if (total > 0 && meter.due()) publish(current, InstallState.Extracting((read.toDouble() / total).toFloat().coerceIn(0f, 1f), online = false))
        }.use { input ->
            // The bundle's own library_size.txt, when it comes first, rather than an estimate from the compression of
            // the official releases (a bundle compressed less, or a smaller library, would need far more than it takes).
            unpackBundle(input, current) { declared -> prepareTarget(declared ?: if (total > 0) estimateLibraryBytes(total) else ESTIMATED_LIBRARY_BYTES) }
        }
        publish(current, InstallState.Extracting(1f, online = false))
        finishInstall(current, version ?: pickedVersion, cleanup)
    }

    /** A database compressed on its own (`seforim.db.zst`), as desktop's extractDbZst accepts. */
    private fun importZstDatabase(file: PickedFile, current: InstallToken, cleanup: List<File>, version: String?) {
        prepareTarget(ESTIMATED_DB_BYTES)
        val meter = TransferSpeedMeter()
        publish(current, InstallState.Extracting(0f, online = false))
        val source = BundlePartSource(file.name, file.size, null, file.open)
        ConcatenatedBundleParts(listOf(source), current) { read ->
            if (file.size > 0 && meter.due()) publish(current, InstallState.Extracting((read.toDouble() / file.size).toFloat().coerceIn(0f, 1f), online = false))
        }.use { input ->
            ZstdInputStream(BufferedInputStream(input, BUFFER_SIZE)).use { zstd ->
                writeFile(zstd, -1L, tempDbFile, current, chunked = splitDatabase) {}
            }
        }
        finishInstall(current, version = version, cleanup = cleanup)
    }

    /** A ready seforim.db: checked by its SQLite header, then copied. */
    private fun importRawDatabase(file: PickedFile, current: InstallToken, cleanup: List<File>, version: String?) {
        val header = ByteArray(SQLITE_HEADER.size)
        val headerRead = file.open().use { input -> readFully(input, header) }
        if (headerRead < header.size || !header.contentEquals(SQLITE_HEADER)) {
            throw InstallException(InstallError.Generic, "Unsupported file type: ${file.name}")
        }
        prepareTarget(if (file.size > 0) file.size else ESTIMATED_DB_BYTES)
        val meter = TransferSpeedMeter()
        publish(current, InstallState.Extracting(0f, online = false))
        file.open().use { input ->
            writeFile(input, file.size, tempDbFile, current, chunked = splitDatabase) { written ->
                if (file.size > 0 && meter.due()) publish(current, InstallState.Extracting((written.toDouble() / file.size).toFloat().coerceIn(0f, 1f), online = false))
            }
        }
        finishInstall(current, version = version, cleanup = cleanup)
    }

    // ---------------------------------------------------------------- shared steps

    /** Library folder of the current run, fixed once the previous library is removed. */
    @Volatile
    private var targetDir: File? = null

    /** Where [name] is written until the install is complete (`seforim.db.part`, `seforim.db.lucene.part`, …). */
    private fun staged(name: String): File = File(targetDir ?: store.libraryDir, name + LibraryStore.PARTIAL_SUFFIX)

    private val tempDbFile: File get() = staged(LibraryStore.DB_NAME)

    private fun discardStaged() {
        LibraryStore.LIBRARY_ITEMS.forEach { name -> staged(name).deleteRecursively() }
        tempDbFile.parentFile?.let { SplitDatabase.deleteOverflow(it, tempDbFile.name) }
        DB_SIDECARS.forEach { File(tempDbFile.path + it).delete() }
    }

    /**
     * Makes room for the new library and checks the free space, like desktop's DatabasePreparationUseCase, which deletes
     * the previous library first. Android keeps it when the new one fits beside it: the new library is written under
     * `.part` names and replaces the old one only once it has been read to the end and checked, so a damaged part or a
     * release too old for the app fails with the working library still in place (an update from files, say).
     */
    private fun prepareTarget(neededBytes: Long) {
        targetDir = null
        store.refreshLocations()
        // Every handle on the library goes before anything is measured or deleted: search keeps seforim.db (its acronym
        // cache) and the Lucene indexes open, and a deleted file that is still open keeps its space.
        runCatching { runBlocking { (context as? ZayitApplication)?.search?.close() } }
        store.close()
        val location = store.targetLocation()
        // A FAT32 card takes no file of 4 GB: the database goes there in chunks (SplitDatabase).
        splitDatabase = location.isFat
        val target = location.dir.apply { mkdirs() }
        // LibraryStore may hold the library on the phone (external or internal app storage) or on a memory card: clear
        // them all of what an interrupted run left, and of the library itself unless there is room to keep it.
        val partial = LibraryStore.LIBRARY_ITEMS.map { it + LibraryStore.PARTIAL_SUFFIX } +
            DB_SIDECARS.map { LibraryStore.DB_NAME + LibraryStore.PARTIAL_SUFFIX + it }
        deleteInLibraryDirs(partial)
        // Only a library on the same volume frees room for the new one: one elsewhere (the phone, when the card is the
        // target) stays until the new one is installed (finishInstall), and is kept if this run fails. It is deleted only
        // when that makes the room: with too little space either way, nothing is deleted and the run stops here.
        val needed = neededBytes + SPACE_HEADROOM_BYTES
        val free = runCatching { StatFs(target.path).availableBytes }.getOrDefault(0L)
        if (free < needed) {
            val sameVolume = libraryDirs().filter { sameVolume(it, target) }
            if (free + sameVolume.sumOf(LibraryStore::libraryBytes) < needed) throw InstallException(InstallError.InsufficientSpace)
            deleteInLibraryDirs(LIBRARY_NAMES, sameVolume)
        }
        targetDir = target
        if (diskSpace().availableBytes < needed) throw InstallException(InstallError.InsufficientSpace)
    }

    /** Both folders are on one filesystem (the phone's internal and external app folders are; a card is not). */
    private fun sameVolume(a: File, b: File): Boolean = runCatching { Os.stat(a.path).st_dev == Os.stat(b.path).st_dev }.getOrDefault(false)

    private fun deleteInLibraryDirs(names: List<String>, dirs: List<File> = libraryDirs()) {
        for (dir in dirs) {
            // A split database's overflow chunks go with it (and with its staged copy).
            names.filter { it == LibraryStore.DB_NAME || it == LibraryStore.DB_NAME + LibraryStore.PARTIAL_SUFFIX }.forEach { SplitDatabase.deleteOverflow(dir, it) }
            for (name in names) {
                val file = File(dir, name)
                if (file.exists() && !file.deleteRecursively()) throw InstallException(InstallError.Generic, "Cannot delete ${file.name}")
            }
        }
    }

    /** The run writes the database in chunks (a FAT32 card). */
    @Volatile
    private var splitDatabase = false

    /**
     * Reads the tar.zst bundle to its end, writing the library items under their staged names ([staged]).
     * Desktop's tar holds `seforim.db.lookup.lucene/…`, `seforim.db.lucene/…`, `seforim.db`, `lexical.db`,
     * `catalog.pb` and `release_info.txt`, in that order. Returns the release_info.txt version when there is one.
     * [prepare], when given, runs before the first item is written; a release_info.txt read before then that is too old
     * for the app stops the install with nothing deleted, and a library_size.txt read before then gives [prepare] the
     * library's real size (bundles packed on a computer by a tool; desktop's order has neither first).
     */
    private fun unpackBundle(compressed: InputStream, current: InstallToken, prepare: ((declaredBytes: Long?) -> Unit)? = null): String? {
        var version: String? = null
        var foundDb = false
        var prepared = prepare == null
        var declaredBytes: Long? = null
        fun ensurePrepared() {
            if (prepared) return
            prepared = true
            prepare?.invoke(declaredBytes)
        }
        val buffer = ByteArray(BUFFER_SIZE)
        val buffered = BufferedInputStream(compressed, BUFFER_SIZE)
        val zstd = ZstdInputStream(buffered)
        val tar = TarArchiveInputStream(zstd)
        try {
            while (true) {
                current.check()
                val entry = tar.nextEntry ?: break
                val name = entry.name.removePrefix("./").trimEnd('/')
                val top = name.substringBefore('/')
                val inside = name.substringAfter('/', missingDelimiterValue = "")
                if (entry.isFile && name == RELEASE_INFO) {
                    version = String(tar.readBytes(), Charsets.UTF_8).trim()
                    if (!prepared && !DatabaseVersion.isCompatible(version)) throw InstallException(InstallError.Incompatible)
                    continue
                }
                if (entry.isFile && name == LIBRARY_SIZE) {
                    val text = String(tar.readBytes(), Charsets.UTF_8)
                    if (!prepared) declaredBytes = librarySize(text)
                    continue
                }
                if (top in INDEX_DIRS || (entry.isFile && (name == LibraryStore.DB_NAME || name in BUNDLE_FILES))) ensurePrepared()
                when {
                    entry.isFile && name == LibraryStore.DB_NAME -> {
                        // Exact size from the tar header: refuse before writing gigabytes that cannot fit.
                        if (diskSpace().availableBytes < entry.size + LATE_SPACE_MARGIN_BYTES) throw InstallException(InstallError.InsufficientSpace)
                        writeFile(tar, entry.size, tempDbFile, current, chunked = splitDatabase) {}
                        foundDb = true
                    }
                    entry.isFile && name in BUNDLE_FILES -> writeFile(tar, entry.size, staged(name), current) {}
                    top in INDEX_DIRS && entry.isDirectory -> indexPath(top, inside)?.mkdirs()
                    top in INDEX_DIRS && entry.isFile && inside.isNotEmpty() -> {
                        val target = indexPath(top, inside)
                        if (target == null) {
                            drain(tar, buffer, current)
                        } else {
                            target.parentFile?.mkdirs()
                            writeFile(tar, entry.size, target, current) {}
                        }
                    }
                    else -> drain(tar, buffer, current)
                }
            }
            // Read the end of the archive so the last part is consumed and its checksum verified.
            drain(zstd, buffer, current)
            drain(buffered, buffer, current)
        } finally {
            runCatching { tar.close() }
        }
        if (!foundDb) throw InstallException(InstallError.Generic, "No .db file found in archive")
        return version
    }

    /** A file or folder inside a staged index; null for a name that would leave it (`..`). */
    private fun indexPath(index: String, inside: String): File? {
        val segments = inside.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) return null
        return segments.fold(staged(index)) { dir, segment -> File(dir, segment) }
    }

    /** [chunked]: the database on a FAT32 card, written as SQLite multiplex chunks ([SplitDatabase]). */
    private fun writeFile(input: InputStream, size: Long, target: File, current: InstallToken, chunked: Boolean = false, onWritten: (Long) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        val stream: OutputStream = if (chunked) ChunkedOutputStream(target) else FileOutputStream(target)
        stream.use { out ->
            var written = 0L
            while (true) {
                current.check()
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                written += read
                onWritten(written)
            }
            if (size >= 0 && written != size) throw InstallException(InstallError.IncompleteBundle, "${target.name}: $written of $size bytes")
            out.flush()
            // ChunkedOutputStream syncs each chunk as it closes it.
            (out as? FileOutputStream)?.fd?.sync()
        }
    }

    private fun finishInstall(current: InstallToken, version: String?, cleanup: List<File>) {
        publish(current, InstallState.Verifying)
        val problem = checkDatabase(tempDbFile, version)
        // The database is renamed away from the -wal/-shm the check may have opened next to the staged name.
        DB_SIDECARS.forEach { File(tempDbFile.path + it).delete() }
        if (problem != null) {
            discardStaged()
            throw InstallException(problem)
        }
        current.check()
        val dir = tempDbFile.parentFile ?: store.libraryDir
        if (version != null) staged(RELEASE_INFO).writeText(version)
        // A previous library kept until now (prepareTarget) stops counting as installed before any of its items is
        // replaced: its database goes first, chunks and all. An interruption from here on leaves no library (a reinstall),
        // never the old database with the new indexes and version, nor its first chunk read with the new overflow chunks
        // through the multiplex VFS. The new database has been checked above.
        val oldDb = File(dir, LibraryStore.DB_NAME)
        if (oldDb.exists() && !oldDb.delete()) throw IOException("Cannot delete ${oldDb.name}")
        SplitDatabase.deleteOverflow(dir, LibraryStore.DB_NAME)
        // What else belonged to it: its write-ahead log must never meet the new database, nor its delta work or the
        // version a delta from files had reached.
        deleteInLibraryDirs(DB_SIDECARS.map { LibraryStore.DB_NAME + it } + DELTA_ITEMS + LibraryStore.LOCAL_VERSION_NAME, listOf(dir))
        // LIBRARY_ITEMS ends with the database: the library only counts as installed once everything else is in place. A
        // file is renamed over the old one (atomic); a folder (an index) replaces the old folder.
        for (name in LibraryStore.LIBRARY_ITEMS) {
            val source = staged(name)
            val target = File(dir, name)
            if (!source.exists()) {
                // Not in the new library (a bare seforim.db brings no index or catalog): the old one's would not match it.
                target.deleteRecursively()
                continue
            }
            // A split database's overflow chunks take their names just before its first chunk does.
            if (name == LibraryStore.DB_NAME) SplitDatabase.renameOverflow(source, target)
            if (target.isDirectory) target.deleteRecursively()
            if (!source.renameTo(target)) throw IOException("Cannot move ${source.name} to ${target.name}")
        }
        // The place the library is in now is the chosen one: an install made while the chosen card was out is not hidden
        // by that card's older library once it is back.
        runCatching { store.locations.firstOrNull { it.dir == dir }?.let(store::setPreferred) }
        // A previous library in another place is not the library any more. One on a memory card this run did not write to
        // is left alone: the card screen's "install on the phone" promises to keep it, whether the card is back or not.
        runCatching {
            val chosen = store.preferredKey
            val otherCards = store.locations.filter { it.removable && it.key != chosen }.map { it.dir }
            deleteInLibraryDirs(LIBRARY_NAMES, libraryDirs().filter { it != dir && it !in otherCards })
        }
        // Bundle files that sat in the app's own folders are not needed any more (desktop deletes its parts too).
        cleanup.forEach { runCatching { it.delete() } }
    }

    /** null when [db]'s release is recent enough (desktop isDatabaseVersionCompatible) and it opens with the library tables. */
    private fun checkDatabase(db: File, version: String?): InstallError? {
        if (!DatabaseVersion.isCompatible(version)) return InstallError.Incompatible
        val hasTables = runCatching {
            openForCheck(db).use { sql ->
                sql.rawQuery(REQUIRED_TABLES_QUERY).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == REQUIRED_TABLE_COUNT }
            }
        }.getOrDefault(false)
        return if (hasTables) null else InstallError.Incompatible
    }

    /** Android's SQLite, or the bundled one for a database in chunks ([SplitDatabase.openReader]). */
    private fun openForCheck(db: File): SplitDatabase.LibraryReader = try {
        SplitDatabase.openReader(db)
    } catch (e: Exception) {
        // A WAL-mode database needs its -shm file, which a read-only connection cannot create.
        SplitDatabase.openReader(db, writable = true)
    }

    private fun failureOf(e: Throwable, online: Boolean): InstallState.Failed = when {
        e is InstallException -> InstallState.Failed(e.error, e.detail, online)
        isDiskFull(e) -> InstallState.Failed(InstallError.InsufficientSpace, null, online)
        isFileTooLarge(e) -> InstallState.Failed(InstallError.FileTooLarge, null, online)
        else -> InstallState.Failed(InstallError.Generic, e.message ?: e.toString(), online)
    }

    /** EFBIG: a FAT32 card refused to grow a file past 4 GB. */
    private fun isFileTooLarge(e: Throwable): Boolean {
        val message = e.message.orEmpty()
        return message.contains("EFBIG") || message.contains("File too large", ignoreCase = true)
    }

    private fun isDiskFull(e: Throwable): Boolean {
        val message = e.message.orEmpty()
        return message.contains("ENOSPC") || message.contains("No space left", ignoreCase = true)
    }

    internal fun networkAvailable(): Boolean =
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null }.getOrDefault(true)

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pardes:LibraryInstall")
            ?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }.getOrNull()

    // ---------------------------------------------------------------- JSON

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun sha256OfDigest(digest: String?): String? = digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")

    /** `GET /repos/{owner}/{repo}/releases/latest`. */
    private fun parseApiRelease(text: String): Release {
        val root = json.parseToJsonElement(text).jsonObject
        val assets = root["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            ReleaseAsset(
                name = asset.string("name") ?: return@mapNotNull null,
                url = asset.string("browser_download_url") ?: return@mapNotNull null,
                size = asset.long("size") ?: -1L,
                sha256 = sha256OfDigest(asset.string("digest")),
            )
        }
        return Release(root.string("tag_name").orEmpty(), assets)
    }

    /** `release-manifest.json` on SeforimLibrary's master branch (written by its release workflow). */
    private fun parseReleaseManifest(text: String): Release {
        val latest = json.parseToJsonElement(text).jsonObject["latest"]?.jsonObject
            ?: throw InstallException(InstallError.Generic, "No release found")
        val assets = latest["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            ReleaseAsset(
                name = asset.string("name") ?: return@mapNotNull null,
                url = asset.string("url") ?: return@mapNotNull null,
                size = asset.long("size") ?: -1L,
                sha256 = sha256OfDigest(asset.string("digest")),
            )
        }
        return Release(latest.string("tagName").orEmpty(), assets)
    }

    private class ManifestPart(val file: String, val size: Long, val sha256: String)

    private class BundleManifest(val parts: List<ManifestPart>)

    /** `seforim_bundle.tar.zst.manifest.json` (format 1), or null if malformed or of another format. */
    private fun parseBundleManifest(text: String): BundleManifest? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root.int("formatVersion") != 1) return@runCatching null
        val parts = root["parts"]?.jsonArray.orEmpty().map { element ->
            val part = element.jsonObject
            ManifestPart(
                file = part.string("file") ?: error("part without file"),
                size = part.long("size") ?: error("part without size"),
                sha256 = part.string("sha256") ?: error("part without sha256"),
            )
        }
        if (parts.isEmpty()) null else BundleManifest(parts)
    }.getOrNull()

    internal fun httpGetText(url: String, accept: String, current: InstallToken): String {
        var failures = 0
        while (true) {
            current.check()
            try {
                val connection = openHttp(url, 0L, accept)
                try {
                    return connection.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
                } finally {
                    connection.disconnect()
                }
            } catch (e: HttpStatusException) {
                throw e
            } catch (e: IOException) {
                current.check()
                if (++failures > TEXT_RETRIES) throw e
                current.sleep(RETRY_DELAY_MS * failures)
            }
        }
    }

    data class DiskSpace(val totalBytes: Long, val availableBytes: Long)

    companion object {
        /** seforim.db in release v3-20261012193827 (8.02 GB), before the real size is read from the tar header. */
        const val ESTIMATED_DB_BYTES = 8_019_492_864L

        /**
         * The rest of that release, as listed from its tar headers: seforim.db.lucene (4.95 GB),
         * seforim.db.lookup.lucene (1.4 MB), lexical.db (81 MB), catalog.pb (0.8 MB) and release_info.txt.
         */
        const val ESTIMATED_INDEX_BYTES = 5_035_598_272L

        /** The unpacked library of that release (13.06 GB, 71 tar entries): what stays on the device. */
        const val ESTIMATED_LIBRARY_BYTES = ESTIMATED_DB_BYTES + ESTIMATED_INDEX_BYTES

        /** Compressed bundle (all parts) of the same release (5.54 GB). */
        const val ESTIMATED_BUNDLE_BYTES = 5_544_091_138L

        /** Kept free beyond the library: SQLite journal/WAL files and the rest of the app. */
        const val SPACE_HEADROOM_BYTES = 1_073_741_824L

        /** What the disk-space step asks for. No temporary space: the compressed bundle is never stored. */
        const val REQUIRED_SPACE_BYTES = ESTIMATED_LIBRARY_BYTES + SPACE_HEADROOM_BYTES

        /** [DatabaseVersion.MINIMUM_REQUIRED_VERSION] as a number. */
        const val MIN_DB_VERSION = 20261011170830L

        private const val LATE_SPACE_MARGIN_BYTES = 268_435_456L
        private const val RELEASE_INFO = LibraryStore.RELEASE_INFO_NAME

        /** Optional, right after release_info.txt in a bundle packed by a tool: the library's size ([librarySize]). */
        private const val LIBRARY_SIZE = "library_size.txt"
        private val INDEX_DIRS = setOf(LibraryStore.TEXT_INDEX_NAME, LibraryStore.LOOKUP_INDEX_NAME)
        private val BUNDLE_FILES = setOf(LibraryStore.LEXICAL_NAME, LibraryStore.CATALOG_NAME)
        private val DB_SIDECARS = listOf("-wal", "-shm", "-journal")

        /** The delta updater's work folder and its unfinished-delta marker (desktop DatabaseCleanupUseCase: delta-cache). */
        private val DELTA_ITEMS = listOf("delta-cache", LibraryStore.DB_NAME + ".applying")

        /** Everything a library consists of in its folder: its items, the database's side files and the delta work. */
        private val LIBRARY_NAMES = LibraryStore.LIBRARY_ITEMS + DB_SIDECARS.map { LibraryStore.DB_NAME + it } + DELTA_ITEMS

        /** A file an offline install takes: bundle parts, their manifest, a .tar.zst, a (.zst) database, release_info.txt. */
        fun isInstallFileName(name: String): Boolean =
            BundleNames.partNumber(name) != null || BundleNames.isManifest(name) || name.endsWith(".zst", ignoreCase = true) ||
                name == LibraryStore.DB_NAME || name == LibraryStore.RELEASE_INFO_NAME

        /** A file of an incremental update: `patch-v<from>-v<to>.db.zst` or its `.manifest.json`. */
        fun isDeltaFileName(name: String): Boolean =
            name.startsWith("patch-") && (name.endsWith(".db.zst") || name.endsWith(".db.zst.manifest.json"))
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/kdroidFilter/SeforimLibrary/releases/latest"
        internal const val RELEASE_MANIFEST_URL = "https://raw.githubusercontent.com/kdroidFilter/SeforimLibrary/master/release-manifest.json"
        private const val LATEST_RELEASE_PAGE = "https://github.com/kdroidFilter/SeforimLibrary/releases/latest"
        internal const val RELEASE_DOWNLOAD_BASE = "https://github.com/kdroidFilter/SeforimLibrary/releases/download/"
        private const val ACCEPT_GITHUB_JSON = "application/vnd.github+json"
        internal const val ACCEPT_ANY = "*/*"
        private const val BUFFER_SIZE = 1 shl 20
        private const val TEXT_RETRIES = 3
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
        // author_bio and book_edition: the 0.6.1 schema, for a database that comes without its release_info.txt
        private const val REQUIRED_TABLES_QUERY =
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('book', 'line', 'category', 'author_bio', 'book_edition')"
        private const val REQUIRED_TABLE_COUNT = 5
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /** Compressed bundle size → expected unpacked library size, scaled from the release measured above. */
        fun estimateLibraryBytes(bundleBytes: Long): Long =
            maxOf(ESTIMATED_LIBRARY_BYTES, (bundleBytes.toDouble() * ESTIMATED_LIBRARY_BYTES / ESTIMATED_BUNDLE_BYTES).toLong())

        /**
         * A bundle's library_size.txt: `key=value` lines, `library=<bytes>` the whole library unpacked (and
         * `seforim.db=<bytes>`, read by nothing yet). null when it has no sensible `library` line.
         */
        internal fun librarySize(text: String): Long? =
            text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("library=") }
                ?.substringAfter('=')?.trim()?.toLongOrNull()?.takeIf { it > 0 }

        /** "v3-20261012193827" → "20261012193827" (the release_info.txt format). */
        internal fun versionFromTag(tag: String): String? =
            tag.substringAfterLast('-').takeIf { it.length == 14 && it.all(Char::isDigit) }

        @Volatile
        private var instance: LibraryInstaller? = null

        fun get(context: Context): LibraryInstaller =
            instance ?: synchronized(this) {
                instance ?: LibraryInstaller(context.applicationContext).also { instance = it }
            }
    }
}

// -------------------------------------------------------------------- streams

private class InstallException(val error: InstallError, val detail: String? = null) : RuntimeException(detail ?: error.name)

/** Thrown out of blocking reads when the user cancels (not an IOException, so retry loops let it through). */
internal class InstallCancelledException : RuntimeException("Cancelled")

/** 4xx answers: retrying will not help. */
internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

internal class InstallToken {
    val cancelled = AtomicBoolean(false)

    fun check() {
        if (cancelled.get()) throw InstallCancelledException()
    }

    fun sleep(ms: Long) {
        var left = ms
        while (left > 0) {
            check()
            val step = minOf(left, 200L)
            Thread.sleep(step)
            left -= step
        }
        check()
    }
}

/** Names this app as what it is, Pardes (based on Zayit), with no contact URL: the original project does not maintain it. */
internal const val USER_AGENT = "Pardes-Android (based on Zayit)"
internal const val OCTET_STREAM = "application/octet-stream"
private const val RETRY_DELAY_MS = 2_000L

/** Consecutive failed attempts (with the network up) before a download gives up. */
private const val DOWNLOAD_RETRIES = 8

/** How long a download waits for the device to get a network back before failing. */
private const val NETWORK_WAIT_MS = 30L * 60 * 1000

private val CONTENT_RANGE = Regex("""bytes (\d+)-""")

internal fun openHttp(url: String, fromByte: Long, accept: String): HttpURLConnection {
    val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 30_000
    connection.readTimeout = 60_000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("Accept", accept)
    // Android's HttpURLConnection would otherwise ask for gzip and unzip transparently, which breaks Range offsets.
    connection.setRequestProperty("Accept-Encoding", "identity")
    connection.setRequestProperty("User-Agent", USER_AGENT)
    if (fromByte > 0) connection.setRequestProperty("Range", "bytes=$fromByte-")
    val code = try {
        connection.responseCode
    } catch (e: IOException) {
        connection.disconnect()
        throw e
    }
    if (code !in 200..299) {
        connection.disconnect()
        if (code >= 500 || code == 408 || code == 429) throw IOException("HTTP $code")
        throw HttpStatusException(code)
    }
    return connection
}

private fun readFully(input: InputStream, into: ByteArray): Int {
    var total = 0
    while (total < into.size) {
        val read = input.read(into, total, into.size - total)
        if (read < 0) break
        total += read
    }
    return total
}

private fun drain(input: InputStream, buffer: ByteArray, current: InstallToken) {
    while (true) {
        current.check()
        if (input.read(buffer) < 0) return
    }
}

/**
 * Body of [url] (a release asset of [size] bytes), requested again from the last byte received
 * (HTTP Range) whenever the connection drops, so a multi-GB transfer survives network changes.
 * [startAt] skips what an earlier attempt already saved (the delta updater's `.part` files).
 */
internal class ResumingHttpInputStream(
    private val url: String,
    private val size: Long,
    private val token: InstallToken,
    private val networkAvailable: () -> Boolean,
    startAt: Long = 0L,
    private val onConnectionLost: (Boolean) -> Unit,
) : InputStream() {
    @Volatile
    private var connection: HttpURLConnection? = null
    private var body: InputStream? = null
    private var position = startAt
    private var failures = 0
    private var lost = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            token.check()
            if (size in 0..position) return -1
            try {
                val input = body ?: connect()
                val read = input.read(b, off, len)
                if (read < 0) {
                    if (size < 0) return -1
                    throw IOException("Connection closed at $position of $size bytes")
                }
                position += read
                failures = 0
                if (lost) {
                    lost = false
                    onConnectionLost(false)
                }
                return read
            } catch (e: IOException) {
                token.check()
                if (e is HttpStatusException) throw e
                closeConnection()
                if (!lost) {
                    lost = true
                    onConnectionLost(true)
                }
                waitForNetwork()
                if (++failures > DOWNLOAD_RETRIES) throw e
                token.sleep(RETRY_DELAY_MS * failures)
            }
        }
    }

    private fun connect(): InputStream {
        val opened = openHttp(url, position, OCTET_STREAM)
        connection = opened
        // Where the body starts: the requested offset, 0 if the server ignored Range, or what a proxy chose.
        val offset = if (opened.responseCode == HttpURLConnection.HTTP_PARTIAL) {
            val range = opened.getHeaderField("Content-Range") ?: throw IOException("206 without Content-Range")
            CONTENT_RANGE.find(range)?.groupValues?.get(1)?.toLongOrNull() ?: throw IOException("Unparseable Content-Range: $range")
        } else {
            0L
        }
        if (offset > position) throw IOException("Server resumed at byte $offset instead of $position")
        val input = opened.inputStream
        skipFully(input, position - offset)
        body = input
        return input
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        val buffer = ByteArray(64 * 1024)
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (read < 0) throw IOException("Connection closed while skipping")
            left -= read
        }
    }

    private fun waitForNetwork() {
        var waited = 0L
        while (!networkAvailable() && waited < NETWORK_WAIT_MS) {
            token.sleep(2_000L)
            waited += 2_000L
        }
    }

    /** Called from another thread on cancel: unblocks a pending read. */
    fun abort() {
        runCatching { connection?.disconnect() }
    }

    private fun closeConnection() {
        runCatching { body?.close() }
        runCatching { connection?.disconnect() }
        body = null
        connection = null
    }

    override fun close() = closeConnection()
}

/** One file of the bundle, opened when the previous one has been read to its end. */
private class BundlePartSource(val name: String, val size: Long, val sha256: String?, val open: () -> InputStream)

/** The parts read back to back, each checked for its size and sha256 as it ends. */
private class ConcatenatedBundleParts(
    private val parts: List<BundlePartSource>,
    private val token: InstallToken,
    private val onPosition: (Long) -> Unit,
) : InputStream() {
    private var index = 0
    private var current: InputStream? = null
    private var digest: MessageDigest? = null
    private var readInPart = 0L
    private var position = 0L

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (index < parts.size) {
            token.check()
            val part = parts[index]
            val input = current ?: openPart(part)
            val read = input.read(b, off, len)
            if (read > 0) {
                digest?.update(b, off, read)
                readInPart += read
                position += read
                if (part.size >= 0 && readInPart > part.size) {
                    throw InstallException(InstallError.IncompleteBundle, "${part.name} is larger than ${part.size} bytes")
                }
                onPosition(position)
                return read
            }
            if (read < 0) endPart(part)
        }
        return -1
    }

    private fun openPart(part: BundlePartSource): InputStream {
        val opened = part.open()
        current = opened
        readInPart = 0L
        digest = if (part.sha256 != null) MessageDigest.getInstance("SHA-256") else null
        return opened
    }

    private fun endPart(part: BundlePartSource) {
        runCatching { current?.close() }
        current = null
        index++
        if (part.size >= 0 && readInPart != part.size) {
            throw InstallException(InstallError.IncompleteBundle, "${part.name}: $readInPart of ${part.size} bytes")
        }
        val actual = digest?.digest()?.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
        if (actual != null && !actual.equals(part.sha256, ignoreCase = true)) {
            throw InstallException(InstallError.Generic, "Corrupted download of ${part.name} (sha256 mismatch)")
        }
    }

    override fun close() {
        runCatching { current?.close() }
        current = null
        index = parts.size
    }
}

/** Download speed as a ~3 s exponential moving average (desktop DownloadUseCase), and a ~4 Hz UI throttle. */
internal class TransferSpeedMeter {
    private var lastBytes = 0L
    private var lastNs = System.nanoTime()
    private var ema = 0.0
    private var lastEmitNs = 0L

    fun update(bytes: Long): Long {
        val now = System.nanoTime()
        val dtNs = now - lastNs
        if (dtNs > 0) {
            val instant = (bytes - lastBytes).coerceAtLeast(0L).toDouble() * 1_000_000_000.0 / dtNs.toDouble()
            val alpha = 1.0 - exp(-(dtNs / 1_000_000_000.0) / 3.0)
            ema = if (ema == 0.0) instant else ema + alpha * (instant - ema)
            lastBytes = bytes
            lastNs = now
        }
        return ema.toLong().coerceAtLeast(0L)
    }

    fun due(): Boolean {
        val now = System.nanoTime()
        if (now - lastEmitNs < 250_000_000L) return false
        lastEmitNs = now
        return true
    }
}

/** Naming of the split bundle, as desktop's BundleParts: `<base>.tar.zst.partNN` + `<base>.tar.zst.manifest.json`. */
private object BundleNames {
    const val BUNDLE_NAME = "seforim_bundle.tar.zst"
    private val PART_NAME = Regex("""^(.+\.tar\.zst)\.part(\d+)$""", RegexOption.IGNORE_CASE)

    fun partNumber(name: String): Int? = PART_NAME.matchEntire(name)?.groupValues?.get(2)?.toIntOrNull()

    fun isManifest(name: String): Boolean = name.endsWith(".tar.zst.manifest.json", ignoreCase = true)

    fun manifestNameFor(bundleName: String): String = "$bundleName.manifest.json"

    /** Parts of [bundleName] among release asset names, in order (releases without a manifest). */
    fun legacyPartNames(names: Collection<String>, bundleName: String = BUNDLE_NAME): List<String> {
        val byNumber = names.mapNotNull { name ->
            val match = PART_NAME.matchEntire(name) ?: return@mapNotNull null
            if (!match.groupValues[1].equals(bundleName, ignoreCase = true)) return@mapNotNull null
            match.groupValues[2].toIntOrNull()?.let { it to name }
        }.toMap()
        if (byNumber.isEmpty()) return emptyList()
        return requireContiguous(byNumber.keys).map { byNumber.getValue(it) }
    }

    /** Sorted part numbers, failing if they do not run 1..N without a gap. */
    fun requireContiguous(numbers: Collection<Int>): List<Int> {
        if (numbers.isEmpty()) throw InstallException(InstallError.IncompleteBundle, "No bundle part found")
        val sorted = numbers.sorted()
        val missing = (1..sorted.last()).firstOrNull { it !in numbers }
        if (missing != null) throw InstallException(InstallError.IncompleteBundle, "Missing bundle part %02d".format(missing))
        return sorted
    }

    /** Without a manifest: every part but the last has the same size and the last one is smaller. */
    fun requireComplete(sizes: List<Long>) {
        if (sizes.size < 2) throw InstallException(InstallError.IncompleteBundle, "Only one bundle part found")
        val full = sizes.first()
        if (!sizes.dropLast(1).all { it == full }) throw InstallException(InstallError.IncompleteBundle, "Bundle parts have inconsistent sizes")
        if (sizes.last() >= full) throw InstallException(InstallError.IncompleteBundle, "The last bundle part is missing")
    }
}

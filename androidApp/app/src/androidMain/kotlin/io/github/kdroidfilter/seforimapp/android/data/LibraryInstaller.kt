package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import android.os.StatFs
import android.system.Os
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.releaseReadGrants
import io.github.kdroidfilter.seforimapp.android.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** The flow a run belongs to: the first-run onboarding, or desktop's DatabaseUpdateWindow (ui/database). */
enum class InstallPurpose { Onboarding, Update }

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
class LibraryInstaller private constructor(private val context: Context) : LibraryInstallerBase() {
    private val store: LibraryStore = (context as? ZayitApplication)?.libraryStore ?: LibraryStore(context)

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
    private var activeDownload: ResumingHttpInputStream? = null
    private var lastRequest: Request? = null

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

    override fun abortTransfer() {
        activeDownload?.abort()
    }

    /** Free space where the next install writes ([LibraryStore.targetLocation]: the phone, or the chosen memory card). */
    fun diskSpace(): DiskSpace = targetDir?.let { dir ->
        runCatching { StatFs(dir.path).let { DiskSpace(totalBytes = it.totalBytes, availableBytes = it.availableBytes) } }.getOrNull()
    } ?: store.targetLocation().space()

    override fun availableBytes(): Long = diskSpace().availableBytes

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
            DatabaseChunks.deleteOverflow(dir, stagedDb)
        }
    }

    /** Every place LibraryStore may keep the library in (the phone and the memory cards). */
    override fun libraryDirs(): List<File> = (store.candidateDirs + store.libraryDir).distinct()

    override val libraryDir: File get() = store.libraryDir

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

    private fun execute(request: Request, current: InstallToken) {
        targetDir = null
        val wakeLock = acquireWakeLock()
        try {
            when (request) {
                Request.Online -> installOnline(current)
                is Request.Uris -> importOffline(request.uris.map { pickedFromUri(context.contentResolver, it) }, current)
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

    // ---------------------------------------------------------------- where the library is kept

    override fun releaseLibrary(): TargetFolder {
        store.refreshLocations()
        // Every handle on the library goes before anything is measured or deleted: search keeps seforim.db (its acronym
        // cache) and the Lucene indexes open, and a deleted file that is still open keeps its space.
        runCatching { runBlocking { (context as? ZayitApplication)?.search?.close() } }
        store.close()
        val location = store.targetLocation()
        return TargetFolder(location.dir, location.isFat)
    }

    override fun sameVolume(a: File, b: File): Boolean = runCatching { Os.stat(a.path).st_dev == Os.stat(b.path).st_dev }.getOrDefault(false)

    override fun onInstalled(dir: File) {
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
        // The release's sizes, which the screens show (LibraryInstallerBase holds them for the Android 4.4 edition too).
        const val ESTIMATED_DB_BYTES = LibraryInstallerBase.ESTIMATED_DB_BYTES
        const val ESTIMATED_INDEX_BYTES = LibraryInstallerBase.ESTIMATED_INDEX_BYTES
        const val ESTIMATED_LIBRARY_BYTES = LibraryInstallerBase.ESTIMATED_LIBRARY_BYTES
        const val ESTIMATED_BUNDLE_BYTES = LibraryInstallerBase.ESTIMATED_BUNDLE_BYTES
        const val SPACE_HEADROOM_BYTES = LibraryInstallerBase.SPACE_HEADROOM_BYTES

        /** What the disk-space step asks for. No temporary space: the compressed bundle is never stored. */
        const val REQUIRED_SPACE_BYTES = ESTIMATED_LIBRARY_BYTES + SPACE_HEADROOM_BYTES

        /** [DatabaseVersion.MINIMUM_REQUIRED_VERSION] as a number. */
        const val MIN_DB_VERSION = 20261011170830L

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
        private const val TEXT_RETRIES = 3
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000

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

/** 4xx answers: retrying will not help. */
internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

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

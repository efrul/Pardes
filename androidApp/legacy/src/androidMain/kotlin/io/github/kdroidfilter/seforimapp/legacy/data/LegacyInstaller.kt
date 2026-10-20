package io.github.kdroidfilter.seforimapp.legacy.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.StatFs
import com.github.luben.zstd.Zstd
import io.github.kdroidfilter.seforimapp.android.data.DatabaseChunks
import io.github.kdroidfilter.seforimapp.android.data.DatabaseVersion
import io.github.kdroidfilter.seforimapp.android.data.InstallError
import io.github.kdroidfilter.seforimapp.android.data.InstallException
import io.github.kdroidfilter.seforimapp.android.data.InstallState
import io.github.kdroidfilter.seforimapp.android.data.InstallToken
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstallerBase
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.data.SplitDatabase
import io.github.kdroidfilter.seforimapp.android.data.TransferSpeedMeter
import io.github.kdroidfilter.seforimapp.legacy.PardesApp
import io.github.kdroidfilter.seforimapp.legacy.ui.InstallService
import io.github.kdroidfilter.seforimapp.legacy.ui.InstallTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * The main app's install from files (LibraryInstallerBase, shared with it word for word: bundle parts with their
 * manifest, a single .tar.zst, a seforim.db.zst or a seforim.db with its release_info.txt, written under staged names
 * and renamed at the end, after the free-space and compatibility checks), on this edition's library: its folder on the
 * phone or a memory card ([Library.locations]). It also takes a folder that holds a library, the desktop's databases
 * folder copied to the phone: moved into the library folder when it is on the same volume (no room needed, and nothing
 * copied), copied otherwise. One per process ([PardesApp.installer]); [InstallService] keeps the process in the
 * foreground while it runs, so that it goes on when the screen is left.
 */
class LegacyInstaller(private val app: PardesApp) : LibraryInstallerBase() {
    private val library: Library get() = app.library
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastRequest: Request? = null

    private val _running = MutableStateFlow(false)

    /** From a run's start until it has ended and let go of the library ([Library.installing]). */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Set while the install screen is shown: the service then posts no "finished" notification. */
    @Volatile
    var uiVisible: Boolean = false

    /** The run replaces a library (Settings › ספרייה) rather than installing the first: the notification's wording. */
    @Volatile
    var replacing: Boolean = false
        private set

    /** What the run is reading or writing now (a bundle's entry, a folder's file), for the progress screen. */
    @Volatile
    var item: String? = null
        private set

    /** The run unpacks zstd (a bundle or a .zst), rather than moving or copying a library folder. */
    @Volatile
    var compressed: Boolean = false
        private set

    /** The bytes the run reads (the picked files, or the folder's library), when known: the progress's total. */
    @Volatile
    var inputBytes: Long = 0L
        private set

    /** The place the run installs to: the screen's choice, else where the library is ([targetLocation]). */
    @Volatile
    private var chosen: Library.Location? = null

    /** A folder's items moved in so far, each with its staged name: a failed run puts them back ([discardStaged]). */
    private val moved = ArrayList<Pair<File, File>>()

    /** What a run takes, and where the library goes (null: where it is now, else the phone). */
    private sealed class Request(val target: Library.Location?, val replacing: Boolean) {
        class Files(val files: List<File>, target: Library.Location?, replacing: Boolean) : Request(target, replacing)

        class Uris(val uris: List<Uri>, target: Library.Location?, replacing: Boolean) : Request(target, replacing)

        class Folder(val dir: File, target: Library.Location?, replacing: Boolean) : Request(target, replacing)
    }

    /** Installs from files on the phone: bundle parts with their manifest, a .tar.zst, a seforim.db.zst (with release_info.txt). */
    fun installFiles(files: List<File>, target: Library.Location?, replacing: Boolean) {
        if (files.isNotEmpty()) begin(Request.Files(files, target, replacing))
    }

    /** Installs from documents picked with the system picker (content URIs, read through their grants). */
    fun installUris(uris: List<Uri>, target: Library.Location?, replacing: Boolean) {
        if (uris.isNotEmpty()) begin(Request.Uris(uris, target, replacing))
    }

    /** Installs the library in [dir] (seforim.db and the files beside it). */
    fun installFolder(dir: File, target: Library.Location?, replacing: Boolean) = begin(Request.Folder(dir, target, replacing))

    /** Runs the last request again after a failure. */
    fun retry() {
        lastRequest?.let { begin(it) }
    }

    private fun begin(request: Request) {
        if (isBusy) return
        lastRequest = request
        replacing = request.replacing
        compressed = request !is Request.Folder
        val previous = job
        val current = InstallToken()
        token = current
        item = null
        inputBytes = when (request) {
            is Request.Files -> request.files.sumOf { it.length() }
            else -> 0L
        }
        // The Home and the search leave the library alone from now on: the run may replace it.
        library.installing = true
        _running.value = true
        _state.value = InstallState.Preparing(false)
        InstallService.start(app)
        job = scope.launch {
            // A cancelled run may still be unwinding; never let two runs write the same files.
            previous?.join()
            execute(request, current)
        }
    }

    private fun execute(request: Request, current: InstallToken) {
        targetDir = null
        chosen = request.target
        val wakeLock = acquireWakeLock()
        try {
            when (request) {
                is Request.Files -> importOffline(request.files.map(::picked), current)
                is Request.Uris -> {
                    val picked = request.uris.map { pickedFromUri(app.contentResolver, it) }
                    inputBytes = picked.sumOf { it.size.coerceAtLeast(0L) }
                    importOffline(picked, current)
                }
                is Request.Folder -> importFolder(request.dir, current)
            }
            synchronized(moved) { moved.clear() }
            // The grants kept so that the run could outlive the screen are not needed any more (a failed run keeps them
            // for its retry).
            if (request is Request.Uris) releaseGrants(request.uris)
            // The new library opens at once, and its search in the background, before the screen says it is ready.
            letGo(current)
            runCatching { library.open() }
            app.search.prepare()
            publish(current, InstallState.Done)
        } catch (e: Throwable) {
            runCatching { discardStaged() }
            letGo(current)
            if (current.cancelled.get()) publish(current, InstallState.Idle) else publish(current, failureOf(e, online = false))
        } finally {
            item = null
            letGo(current)
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    /** The run has ended: the library may open again (unless a newer run has started meanwhile). */
    private fun letGo(current: InstallToken) {
        if (token === current || token == null) {
            library.installing = false
            _running.value = false
        }
    }

    /**
     * A file the user picked in the app's own folders goes once the library is installed, as the main app's install
     * deletes the bundle it found there; one elsewhere (Download) is the user's, and stays.
     */
    private fun picked(file: File): PickedFile =
        PickedFile(file.name, file.length(), file.takeIf { inOwnFolders(it) }) { FileInputStream(file) }

    private fun inOwnFolders(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrDefault(file.path)
        val own = runCatching { app.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList()) + app.filesDir
        return own.filterNotNull().any { dir -> path.startsWith(runCatching { dir.canonicalPath }.getOrDefault(dir.path) + "/") }
    }

    private fun releaseGrants(uris: List<Uri>) {
        uris.forEach { runCatching { app.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }

    // ---------------------------------------------------------------- a folder that holds a library

    /**
     * Installs the library in [source]: checked where it is (its release_info.txt against the minimum version, its
     * database's tables), then moved into the library folder item by item under the staged names when both are on one
     * volume, else copied there (the free space and a FAT32 card's 4 GB limit checked first, the database written in
     * chunks there), and put in place as an install's are.
     */
    private fun importFolder(source: File, current: InstallToken) {
        val db = File(source, LibraryStore.DB_NAME)
        if (!db.isFile) throw InstallException(InstallError.IncompleteBundle)
        val canonical = canonical(source)
        if (library.locations().any { canonical(it.dir) == canonical }) throw InstallException(InstallError.Generic, InstallTexts.ALREADY_LIBRARY)
        item = source.name
        val version = runCatching { File(source, LibraryStore.RELEASE_INFO_NAME).readText().trim() }.getOrNull()
        // As a picked database's: an old release (or none) is refused before anything is moved or deleted.
        if (!DatabaseVersion.isCompatible(version)) throw InstallException(InstallError.Incompatible)
        // A write-ahead log that still holds pages goes into the database first: an install never carries one along.
        if (File(source, LibraryStore.DB_NAME + "-wal").length() > 0L) {
            runCatching { SplitDatabase.openReader(db, writable = true).use { it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)").close() } }
        }
        // Checked where it is: a database moved in and found wrong would go with the staged items.
        checkDatabase(db, version)?.let { throw InstallException(it) }
        val items = FOLDER_ITEMS.map { File(source, it) }.filter(File::exists)
        val total = items.sumOf(::bytesOf)
        inputBytes = total
        publish(current, InstallState.Extracting(0f, online = false))
        if (!move(items, current)) copy(items, total, current)
        publish(current, InstallState.Extracting(1f, online = false))
        finishInstall(current, version, cleanup = emptyList())
    }

    /**
     * Moves [items] into the library folder under their staged names, when it is on their volume and Android lets the
     * app rename them there (on Android 4.4 a memory card is written only in the app's own folder). False, with nothing
     * moved, when it is not: they are copied then.
     */
    private fun move(items: List<File>, current: InstallToken): Boolean {
        targetDir = null
        val location = releaseLibrary()
        val target = location.dir.apply { mkdirs() }
        if (!sameVolume(items.first(), target)) return false
        deleteInLibraryDirs(STAGED_NAMES)
        targetDir = target
        splitDatabase = location.isFat
        for (item in items) {
            current.check()
            onItem(item.name)
            val staged = staged(item.name)
            if (!item.renameTo(staged)) {
                if (synchronized(moved) { moved.isEmpty() }) {
                    targetDir = null
                    return false
                }
                throw IOException("Cannot move ${item.name}")
            }
            synchronized(moved) { moved += item to staged }
            // A database in chunks: its overflow chunks follow it, under the staged database's chunk names.
            if (item.name == LibraryStore.DB_NAME) {
                DatabaseChunks.overflowChunks(item).forEachIndexed { i, chunk ->
                    val to = File(target, DatabaseChunks.chunkName(staged.name, i + 1))
                    if (!chunk.renameTo(to)) throw IOException("Cannot move ${chunk.name}")
                    synchronized(moved) { moved += chunk to to }
                }
            }
        }
        return true
    }

    /** Copies [items] ([total] bytes) into the library folder under their staged names, the database last. */
    private fun copy(items: List<File>, total: Long, current: InstallToken) {
        // FAT32 takes no file of 4 GB or more, but the database's, which goes there in chunks (the main app's move plan).
        val max = targetLocation().maxFileBytes
        if (max != null && items.any { item -> item.name != LibraryStore.DB_NAME && filesOf(item).any { it.length() > max } }) {
            throw InstallException(InstallError.FileTooLarge)
        }
        prepareTarget(total)
        val meter = TransferSpeedMeter()
        var copied = 0L
        fun copyFile(input: InputStream, size: Long, to: File, chunked: Boolean = false) {
            val before = copied
            input.use {
                writeFile(it, size, to, current, chunked) { written ->
                    copied = before + written
                    if (total > 0 && meter.due()) publish(current, InstallState.Extracting((copied.toDouble() / total).toFloat().coerceIn(0f, 1f), online = false))
                }
            }
        }
        for (item in items) {
            when {
                item.isDirectory -> item.walkTopDown().filter(File::isFile).forEach { file ->
                    val path = file.relativeTo(item).path
                    onItem(item.name + "/" + path)
                    val to = File(staged(item.name), path).apply { parentFile?.mkdirs() }
                    copyFile(FileInputStream(file), file.length(), to)
                }
                item.name == LibraryStore.DB_NAME -> {
                    onItem(item.name)
                    copyFile(DatabaseChunks.openInput(item), DatabaseChunks.size(item), tempDbFile, chunked = splitDatabase)
                }
                else -> {
                    onItem(item.name)
                    copyFile(FileInputStream(item), item.length(), staged(item.name))
                }
            }
        }
    }

    /** A library item's files: a folder's, the database's chunks, or the file itself. */
    private fun filesOf(item: File): List<File> = when {
        item.isDirectory -> item.walkTopDown().filter(File::isFile).toList()
        item.name == LibraryStore.DB_NAME -> DatabaseChunks.files(item)
        else -> listOf(item)
    }

    private fun bytesOf(item: File): Long = filesOf(item).sumOf { it.length() }

    override fun onItem(name: String) {
        item = name
    }

    /** A folder moved in goes back where it was: a failed run never deletes the user's library. */
    override fun discardStaged() {
        synchronized(moved) {
            moved.asReversed().forEach { (from, staged) -> if (staged.exists()) staged.renameTo(from) }
            moved.clear()
        }
        super.discardStaged()
    }

    /**
     * The size a seforim.db.zst unpacks to, from its zstd frame header, which zstd writes when it compresses a file:
     * a small library's database then needs its own room, not the full release's 8 GB that the main app reserves.
     */
    override fun zstDatabaseBytes(file: PickedFile): Long {
        val header = ByteArray(ZSTD_FRAME_HEADER_MAX)
        val read = runCatching {
            file.open().use { input ->
                var total = 0
                while (total < header.size) {
                    val n = input.read(header, total, header.size - total)
                    if (n < 0) break
                    total += n
                }
                total
            }
        }.getOrDefault(0)
        val size = runCatching { Zstd.getFrameContentSize(header, 0, read) }.getOrDefault(-1L)
        return if (size > 0) size else super.zstDatabaseBytes(file)
    }

    // ---------------------------------------------------------------- where the library is kept

    /** Where the run installs: the place chosen, else the one the library is in now, else the phone. */
    fun targetLocation(): Library.Location =
        chosen ?: library.currentLocation() ?: library.locations().first()

    override val libraryDir: File get() = targetLocation().dir

    override fun libraryDirs(): List<File> = library.locations().map { it.dir }

    override fun releaseLibrary(): TargetFolder {
        // Every handle on the library goes before anything is measured or deleted, as in the main app: the search keeps
        // the Lucene indexes, lexical.db and the database's acronyms open; then the database itself.
        runCatching { runBlocking { app.search.close() } }
        library.close()
        val location = targetLocation()
        return TargetFolder(location.dir, location.isFat)
    }

    /**
     * By the filesystem behind each folder ([Library.volumeOf]): this edition reads no inode device (Os.stat, Android
     * 5.0), and a phone's shared storage is a FUSE view of /data, a memory card one of its own filesystem.
     */
    override fun sameVolume(a: File, b: File): Boolean {
        val volume = library.volumeOf(a) ?: return false
        return volume == library.volumeOf(b)
    }

    override fun availableBytes(): Long =
        targetDir?.let { dir -> runCatching { StatFs(dir.path).availableBytes }.getOrNull() } ?: targetLocation().space().first

    override fun onInstalled(dir: File) {
        library.remember(dir)
        // A previous library in another place is not the library any more; one on another memory card is left alone,
        // as the main app leaves it.
        runCatching {
            val cards = library.locations().filter { it.isCard }.map { it.dir }
            deleteInLibraryDirs(LibraryInstallerBase.LIBRARY_NAMES, libraryDirs().filter { it != dir && it !in cards })
        }
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pardes:LibraryInstall")
            ?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }.getOrNull()

    private fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private companion object {
        /** A library folder's items, the database last; the main app's record of a delta is not one. */
        val FOLDER_ITEMS = LibraryStore.LIBRARY_ITEMS - LibraryStore.LOCAL_VERSION_NAME

        /** What an interrupted run leaves: the staged items and the staged database's side files. */
        val STAGED_NAMES = LibraryStore.LIBRARY_ITEMS.map { it + LibraryStore.PARTIAL_SUFFIX } +
            LibraryInstallerBase.DB_SIDECARS.map { LibraryStore.DB_NAME + LibraryStore.PARTIAL_SUFFIX + it }

        /** ZSTD_FRAMEHEADERSIZE_MAX: the content size is in the first 18 bytes. */
        const val ZSTD_FRAME_HEADER_MAX = 18
        const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
    }
}

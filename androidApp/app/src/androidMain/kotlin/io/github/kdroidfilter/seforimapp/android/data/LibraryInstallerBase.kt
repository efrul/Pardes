package io.github.kdroidfilter.seforimapp.android.data

import android.content.ContentResolver
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * The installation of a library from its files, as the main app's [LibraryInstaller] runs it and the Android 4.4
 * edition shares it: the state the screens and the service observe, the offline import (bundle parts with their
 * manifest, a single .tar.zst, a seforim.db.zst or a seforim.db with its release_info.txt), the tar.zst streaming, the
 * free-space check, the compatibility check and the staged names renamed at the end. Each app says where its library
 * is, how to close it and what follows an install ([releaseLibrary], [libraryDirs], [sameVolume], [onInstalled]).
 *
 * Every item is written under a `.part` name and renamed when the whole bundle has been read and checked, the database
 * last, so an interrupted install never leaves a half index or database that looks valid.
 */
abstract class LibraryInstallerBase {
    protected val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    @Volatile
    protected var token: InstallToken? = null

    val isBusy: Boolean
        get() = when (_state.value) {
            is InstallState.Preparing, is InstallState.Downloading, is InstallState.Extracting, InstallState.Verifying -> true
            else -> false
        }

    fun cancel() {
        val current = token ?: return
        current.cancelled.set(true)
        abortTransfer()
        token = null
        _state.value = InstallState.Idle
    }

    /** Unblocks a read the run waits on when it is cancelled (the main app's download). */
    protected open fun abortTransfer() {}

    /** Forgets a finished or failed attempt (e.g. "התחל התקנה מחדש"). */
    fun reset() {
        if (!isBusy) _state.value = InstallState.Idle
    }

    /** State changes from a run that has since been cancelled or replaced are dropped. */
    protected fun publish(current: InstallToken, state: InstallState) {
        if (token === current) _state.value = state
    }

    // ---------------------------------------------------------------- where each app keeps its library

    /** Where the library is, or will be installed, while no run has chosen its folder ([prepareTarget]). */
    protected abstract val libraryDir: File

    /** Every place the app may keep the library in (an install clears them all of what an interrupted run left). */
    protected abstract fun libraryDirs(): List<File>

    /**
     * Closes every handle on the library (its search indexes and database: a deleted file that is still open keeps its
     * space) and tells where the new library goes.
     */
    protected abstract fun releaseLibrary(): TargetFolder

    /** Both folders are on one filesystem (the phone's internal and external app folders are; a card is not). */
    protected abstract fun sameVolume(a: File, b: File): Boolean

    /** Free bytes where the run writes. */
    protected abstract fun availableBytes(): Long

    /** The new library is in place in [dir]: the app records it there, and drops a previous one kept elsewhere. */
    protected abstract fun onInstalled(dir: File)

    /** Where a new library goes, and whether its filesystem is FAT32 (the database is then written in chunks). */
    protected class TargetFolder(val dir: File, val isFat: Boolean)

    // ---------------------------------------------------------------- offline

    protected class PickedFile(val name: String, val size: Long, val localFile: File?, val open: () -> InputStream)

    protected fun pickedFromUri(resolver: ContentResolver, uri: Uri): PickedFile {
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

    protected fun pickedFromFile(file: File): PickedFile = PickedFile(file.name, file.length(), file) { FileInputStream(file) }

    protected fun importOffline(picked: List<PickedFile>, current: InstallToken) {
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

    /**
     * The room a database compressed on its own ([file], `seforim.db.zst`) is taken to need unpacked: the full
     * release's database, as the main app reserves it.
     */
    protected open fun zstDatabaseBytes(file: PickedFile): Long = ESTIMATED_DB_BYTES

    /** A database compressed on its own (`seforim.db.zst`), as desktop's extractDbZst accepts. */
    private fun importZstDatabase(file: PickedFile, current: InstallToken, cleanup: List<File>, version: String?) {
        prepareTarget(zstDatabaseBytes(file))
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
    protected var targetDir: File? = null

    /** Where [name] is written until the install is complete (`seforim.db.part`, `seforim.db.lucene.part`, …). */
    protected fun staged(name: String): File = File(targetDir ?: libraryDir, name + LibraryStore.PARTIAL_SUFFIX)

    protected val tempDbFile: File get() = staged(LibraryStore.DB_NAME)

    protected open fun discardStaged() {
        LibraryStore.LIBRARY_ITEMS.forEach { name -> staged(name).deleteRecursively() }
        tempDbFile.parentFile?.let { DatabaseChunks.deleteOverflow(it, tempDbFile.name) }
        DB_SIDECARS.forEach { File(tempDbFile.path + it).delete() }
    }

    /**
     * Makes room for the new library and checks the free space, like desktop's DatabasePreparationUseCase, which deletes
     * the previous library first. Android keeps it when the new one fits beside it: the new library is written under
     * `.part` names and replaces the old one only once it has been read to the end and checked, so a damaged part or a
     * release too old for the app fails with the working library still in place (an update from files, say).
     */
    protected fun prepareTarget(neededBytes: Long) {
        targetDir = null
        val location = releaseLibrary()
        // A FAT32 card takes no file of 4 GB: the database goes there in chunks (DatabaseChunks).
        splitDatabase = location.isFat
        val target = location.dir.apply { mkdirs() }
        // The library may be kept on the phone (external or internal app storage) or on a memory card: clear them all
        // of what an interrupted run left, and of the library itself unless there is room to keep it.
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
        if (availableBytes() < needed) throw InstallException(InstallError.InsufficientSpace)
    }

    protected fun deleteInLibraryDirs(names: List<String>, dirs: List<File> = libraryDirs()) {
        for (dir in dirs) {
            // A split database's overflow chunks go with it (and with its staged copy).
            names.filter { it == LibraryStore.DB_NAME || it == LibraryStore.DB_NAME + LibraryStore.PARTIAL_SUFFIX }.forEach { DatabaseChunks.deleteOverflow(dir, it) }
            for (name in names) {
                val file = File(dir, name)
                if (file.exists() && !file.deleteRecursively()) throw InstallException(InstallError.Generic, "Cannot delete ${file.name}")
            }
        }
    }

    /** The run reached [name], an entry of the bundle (the Android 4.4 edition names it under its progress). */
    protected open fun onItem(name: String) {}

    /** The run writes the database in chunks (a FAT32 card). */
    @Volatile
    protected var splitDatabase = false

    /**
     * Reads the tar.zst bundle to its end, writing the library items under their staged names ([staged]).
     * Desktop's tar holds `seforim.db.lookup.lucene/…`, `seforim.db.lucene/…`, `seforim.db`, `lexical.db`,
     * `catalog.pb` and `release_info.txt`, in that order. Returns the release_info.txt version when there is one.
     * [prepare], when given, runs before the first item is written; a release_info.txt read before then that is too old
     * for the app stops the install with nothing deleted, and a library_size.txt read before then gives [prepare] the
     * library's real size (bundles packed on a computer by a tool; desktop's order has neither first).
     */
    protected fun unpackBundle(compressed: InputStream, current: InstallToken, prepare: ((declaredBytes: Long?) -> Unit)? = null): String? {
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
                onItem(name)
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
                        if (availableBytes() < entry.size + LATE_SPACE_MARGIN_BYTES) throw InstallException(InstallError.InsufficientSpace)
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

    /** [chunked]: the database on a FAT32 card, written as SQLite multiplex chunks ([DatabaseChunks]). */
    protected fun writeFile(input: InputStream, size: Long, target: File, current: InstallToken, chunked: Boolean = false, onWritten: (Long) -> Unit) {
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

    protected fun finishInstall(current: InstallToken, version: String?, cleanup: List<File>) {
        publish(current, InstallState.Verifying)
        val problem = checkDatabase(tempDbFile, version)
        // The database is renamed away from the -wal/-shm the check may have opened next to the staged name.
        DB_SIDECARS.forEach { File(tempDbFile.path + it).delete() }
        if (problem != null) {
            discardStaged()
            throw InstallException(problem)
        }
        current.check()
        val dir = tempDbFile.parentFile ?: libraryDir
        if (version != null) staged(RELEASE_INFO).writeText(version)
        // A previous library kept until now (prepareTarget) stops counting as installed before any of its items is
        // replaced: its database goes first, chunks and all. An interruption from here on leaves no library (a reinstall),
        // never the old database with the new indexes and version, nor its first chunk read with the new overflow chunks
        // through the multiplex VFS. The new database has been checked above.
        val oldDb = File(dir, LibraryStore.DB_NAME)
        if (oldDb.exists() && !oldDb.delete()) throw IOException("Cannot delete ${oldDb.name}")
        DatabaseChunks.deleteOverflow(dir, LibraryStore.DB_NAME)
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
            if (name == LibraryStore.DB_NAME) DatabaseChunks.renameOverflow(source, target)
            if (target.isDirectory) target.deleteRecursively()
            if (!source.renameTo(target)) throw IOException("Cannot move ${source.name} to ${target.name}")
        }
        onInstalled(dir)
        // Bundle files that sat in the app's own folders are not needed any more (desktop deletes its parts too).
        cleanup.forEach { runCatching { it.delete() } }
    }

    /** null when [db]'s release is recent enough (desktop isDatabaseVersionCompatible) and it opens with the library tables. */
    protected fun checkDatabase(db: File, version: String?): InstallError? {
        if (!DatabaseVersion.isCompatible(version)) return InstallError.Incompatible
        val hasTables = runCatching {
            openForCheck(db).use { sql ->
                sql.rawQuery(REQUIRED_TABLES_QUERY).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == REQUIRED_TABLE_COUNT }
            }
        }.getOrDefault(false)
        return if (hasTables) null else InstallError.Incompatible
    }

    /** Android's SQLite, or the bundled one for a database in chunks and below Android 9 ([SplitDatabase.openReader]). */
    private fun openForCheck(db: File): SplitDatabase.LibraryReader = try {
        SplitDatabase.openReader(db)
    } catch (e: Exception) {
        // A WAL-mode database needs its -shm file, which a read-only connection cannot create.
        SplitDatabase.openReader(db, writable = true)
    }

    protected fun failureOf(e: Throwable, online: Boolean): InstallState.Failed = when {
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

    // ---------------------------------------------------------------- JSON

    protected val json = Json { ignoreUnknownKeys = true }

    protected fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    protected fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    protected class ManifestPart(val file: String, val size: Long, val sha256: String)

    protected class BundleManifest(val parts: List<ManifestPart>)

    /** `seforim_bundle.tar.zst.manifest.json` (format 1), or null if malformed or of another format. */
    protected fun parseBundleManifest(text: String): BundleManifest? = runCatching {
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

        private const val LATE_SPACE_MARGIN_BYTES = 268_435_456L
        private const val RELEASE_INFO = LibraryStore.RELEASE_INFO_NAME

        /** Optional, right after release_info.txt in a bundle packed by a tool: the library's size ([librarySize]). */
        private const val LIBRARY_SIZE = "library_size.txt"
        private val INDEX_DIRS = setOf(LibraryStore.TEXT_INDEX_NAME, LibraryStore.LOOKUP_INDEX_NAME)
        private val BUNDLE_FILES = setOf(LibraryStore.LEXICAL_NAME, LibraryStore.CATALOG_NAME)
        internal val DB_SIDECARS = listOf("-wal", "-shm", "-journal")

        /** The delta updater's work folder and its unfinished-delta marker (desktop DatabaseCleanupUseCase: delta-cache). */
        private val DELTA_ITEMS = listOf("delta-cache", LibraryStore.DB_NAME + ".applying")

        /** Everything a library consists of in its folder: its items, the database's side files and the delta work. */
        internal val LIBRARY_NAMES = LibraryStore.LIBRARY_ITEMS + DB_SIDECARS.map { LibraryStore.DB_NAME + it } + DELTA_ITEMS

        private const val BUFFER_SIZE = 1 shl 20

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
    }
}

// -------------------------------------------------------------------- streams

internal class InstallException(val error: InstallError, val detail: String? = null) : RuntimeException(detail ?: error.name)

/** Thrown out of blocking reads when the user cancels (not an IOException, so retry loops let it through). */
internal class InstallCancelledException : RuntimeException("Cancelled")

/** One run's cancellation, checked between reads (public: [LibraryInstallerBase]'s steps take it). */
class InstallToken {
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

/** One file of the bundle, opened when the previous one has been read to its end. */
internal class BundlePartSource(val name: String, val size: Long, val sha256: String?, val open: () -> InputStream)

/** The parts read back to back, each checked for its size and sha256 as it ends. */
internal class ConcatenatedBundleParts(
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
internal object BundleNames {
    const val BUNDLE_NAME = "seforim_bundle.tar.zst"
    private val PART_NAME = Regex("""^(.+\.tar\.zst)\.part(\d+)$""", RegexOption.IGNORE_CASE)

    fun partNumber(name: String): Int? = PART_NAME.matchEntire(name)?.groupValues?.get(2)?.toIntOrNull()

    /** The bundle a part belongs to (`seforim_bundle.tar.zst` for `seforim_bundle.tar.zst.part01`), or null. */
    fun bundleOf(name: String): String? = PART_NAME.matchEntire(name)?.groupValues?.get(1)

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

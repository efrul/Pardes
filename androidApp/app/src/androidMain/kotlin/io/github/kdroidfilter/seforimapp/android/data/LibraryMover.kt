package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.os.PowerManager
import android.util.Log
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/** Progress of moving the library between the phone and a memory card ([LibraryMover]). */
sealed interface MoveState {
    data object Idle : MoveState

    data class Copying(val copiedBytes: Long, val totalBytes: Long, val speedBytesPerSec: Long, val target: String) : MoveState {
        val progress: Float get() = if (totalBytes > 0) (copiedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f
    }

    /** Checking the copy, then putting it in place and removing the old one. */
    data class Finishing(val target: String) : MoveState

    /** Moved; the app starts again on the new copy. */
    data class Done(val target: String) : MoveState

    data class Failed(val message: String) : MoveState
}

/** What a move would take, worked out before it starts ([LibraryMover.plan]). */
data class MovePlan(
    val source: File,
    val target: LibraryLocation,
    val totalBytes: Long,
    val availableBytes: Long,
    /** The biggest single file, against a FAT32 card's limit. */
    val largestFileBytes: Long,
    /** The target already holds another library (not the one in use): the move replaces it. */
    val replacesLibrary: Boolean = false,
    /** The database's size (all its chunks): on a FAT32 card it is split only past one chunk. */
    val databaseBytes: Long = 0L,
) {
    /** The database will be kept in chunks there (SplitDatabase): a FAT32 card and a database over one chunk. */
    val splits: Boolean get() = target.isFat && databaseBytes > SplitDatabase.CHUNK_BYTES

    val fits: Boolean get() = availableBytes >= totalBytes + LibraryMover.SPACE_HEADROOM_BYTES
    val fileTooLarge: Boolean get() = target.maxFileBytes?.let { largestFileBytes > it } == true
    val possible: Boolean get() = fits && !fileTooLarge
}

/**
 * Android only: moves the installed library to another [LibraryLocation] (the phone's storage or a memory card).
 * Every item is copied under a `.part` name, flushed and checked (size, and a checksum of each file's end, read back
 * with the copy's cached pages dropped where Android allows: this shows the copy complete, and may show up a card that
 * lies about its size, though not surely), then given its real name, the database last, with the new place chosen first; only
 * then is the old copy deleted, the database first. An interrupted move therefore always leaves one complete library:
 * the old one until the new database has its name, the new one after. The app reads on from the old files while the
 * copy runs (nothing writes to the library: an update cannot start meanwhile), and starts again on the new ones.
 * [LibraryMoveService] keeps the process in the foreground for the many minutes a card takes.
 */
class LibraryMover private constructor(private val app: ZayitApplication) {
    private val store = app.libraryStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<MoveState>(MoveState.Idle)
    val state: StateFlow<MoveState> = _state.asStateFlow()

    @Volatile
    private var cancelled = AtomicBoolean(false)

    /** The running move has cleared the target of leftovers: a failure from then on also deletes what it staged there. */
    @Volatile
    private var staging = false

    val isBusy: Boolean get() = _state.value.let { it is MoveState.Copying || it is MoveState.Finishing || it is MoveState.Done }

    /** Why nothing can be moved now (an install or an update running or unfinished), or null. */
    fun blockedReason(): String? = when {
        LibraryInstaller.get(app).isBusy -> MoveStrings.blockedInstall
        DbDeltaUpdateService.get(app).isBusy -> MoveStrings.blockedUpdate
        File(store.libraryDir, LibraryStore.DB_NAME + APPLYING_SUFFIX).exists() -> MoveStrings.blockedUpdate
        else -> null
    }

    /** The move of the installed library to [target], or null when there is no library or it is already there. Blocking. */
    fun plan(target: LibraryLocation): MovePlan? {
        val source = store.libraryDir
        if (!LibraryStore.hasDatabase(source) || source == target.dir) return null
        val database = File(source, LibraryStore.DB_NAME)
        val others = items(source).filter { it != database }.flatMap { it.walkTopDown().filter(File::isFile).toList() }
        val databaseBytes = SplitDatabase.size(database)
        val available = target.space().availableBytes + leftoverBytes(target.dir)
        // The database goes to a FAT32 card in chunks (SplitDatabase), elsewhere as one file.
        val largest = maxOf(others.maxOfOrNull { it.length() } ?: 0L, if (target.isFat) 0L else databaseBytes)
        return MovePlan(source, target, others.sumOf { it.length() } + databaseBytes, available, largest, LibraryStore.hasDatabase(target.dir), databaseBytes)
    }

    fun start(target: LibraryLocation) {
        if (isBusy) return
        val token = AtomicBoolean(false)
        cancelled = token
        _state.value = MoveState.Copying(0L, 0L, 0L, target.label)
        LibraryMoveService.start(app)
        scope.launch {
            val wakeLock = acquireWakeLock()
            try {
                staging = false
                move(target, token)
                _state.value = MoveState.Done(target.label)
                // The screen (or the notification) says so first; then every handle on the old files goes with the process.
                delay(RESTART_DELAY_MS)
                app.restart()
            } catch (e: Throwable) {
                // A move stopped before its copy began leaves the target as it was (it may hold another library).
                if (staging) runCatching { discardStaged(target.dir) }
                _state.value = if (token.get()) MoveState.Idle else MoveState.Failed(message(e))
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
            }
        }
    }

    fun cancel() {
        // Too late once the copy is being put in place: that part is quick and must not stop halfway.
        if (_state.value is MoveState.Copying) cancelled.set(true)
    }

    /** Forgets a failed move. */
    fun reset() {
        if (_state.value is MoveState.Failed) _state.value = MoveState.Idle
    }

    private fun move(target: LibraryLocation, token: AtomicBoolean) {
        blockedReason()?.let { throw MoveException(it) }
        store.refreshLocations()
        val plan = plan(target) ?: throw MoveException(MoveStrings.nothingToMove)
        if (plan.fileTooLarge) throw MoveException(MoveStrings.fileTooLarge)
        if (!plan.fits) throw MoveException(MoveStrings.notEnoughSpace)
        // Only now, with nothing left to stop the move before its copy, does the target lose what it held.
        staging = true
        discardStaged(target.dir)
        val sourceItems = items(plan.source)
        val meter = TransferSpeedMeter()
        var copied = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        fun copy(input: InputStream, out: OutputStream) {
            while (true) {
                if (token.get()) throw MoveCancelledException()
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                copied += read
                if (meter.due()) _state.value = MoveState.Copying(copied, plan.totalBytes, meter.update(copied), target.label)
            }
            out.flush()
            // ChunkedOutputStream syncs each chunk as it closes it.
            (out as? FileOutputStream)?.fd?.sync()
        }
        fun copyFile(from: File, to: File) {
            to.parentFile?.mkdirs()
            FileInputStream(from).use { input -> FileOutputStream(to).use { out -> copy(input, out) } }
        }
        // The indexes and small files first, the database last (LIBRARY_ITEMS order), as an install writes them. The
        // database is read across its chunks and written in chunks to a FAT32 card, as one file elsewhere.
        for (item in sourceItems) {
            val staged = staged(target.dir, item.name)
            when {
                item.isDirectory -> item.walkTopDown().filter(File::isFile).forEach { file -> copyFile(file, File(staged, file.relativeTo(item).path)) }
                item.name == LibraryStore.DB_NAME -> SplitDatabase.openInput(item).use { input ->
                    (if (target.isFat) ChunkedOutputStream(staged) else FileOutputStream(staged)).use { out -> copy(input, out) }
                }
                else -> copyFile(item, staged)
            }
        }
        _state.value = MoveState.Finishing(target.label)
        for (item in sourceItems) verify(item, staged(target.dir, item.name))

        // The new place is chosen before the new database gets its name: from then on it is the library. The open
        // repository is left alone (screens may be reading): it reads on from the old files, deleted but open, until
        // the app starts again a moment later.
        store.setPreferred(target)
        for (item in sourceItems) {
            val final = File(target.dir, item.name)
            // A split database's overflow chunks take their names just before its first chunk does.
            if (item.name == LibraryStore.DB_NAME) SplitDatabase.renameOverflow(staged(target.dir, item.name), final)
            final.deleteRecursively()
            if (!staged(target.dir, item.name).renameTo(final)) throw IOException("Cannot rename ${item.name}")
        }
        store.refreshLocations()
        // The old copy: the database first, so it never looks like a library again.
        (listOf(File(plan.source, LibraryStore.DB_NAME)) + DB_SIDECARS.map { File(plan.source, LibraryStore.DB_NAME + it) }).forEach { it.delete() }
        SplitDatabase.deleteOverflow(plan.source, LibraryStore.DB_NAME)
        (sourceItems + LEFTOVERS.map { File(plan.source, it) }).forEach { runCatching { it.deleteRecursively() } }
    }

    /** The library's items in [dir], database last, with a write-ahead log that still holds pages (it goes with the database). */
    private fun items(dir: File): List<File> {
        val wal = File(dir, LibraryStore.DB_NAME + WAL).takeIf { it.isFile && it.length() > 0 }
        val main = LibraryStore.LIBRARY_ITEMS.map { File(dir, it) }.filter(File::exists)
        return main.dropLast(1) + listOfNotNull(wal) + main.takeLast(1).filter { it.name == LibraryStore.DB_NAME }
    }

    /**
     * Same size, and the same checksum over the file's last MiB (where a card that lies about its size loses data). The
     * copy was just written, so its pages are still in RAM: they are dropped first ([PageCache]), so that its end is read
     * back from the storage where Android allows it. When they cannot be dropped the move stops, since the old library
     * would otherwise be deleted on a read from RAM. Through FUSE the lower file system may still serve its own cached
     * pages, and the check then only shows the copy complete.
     */
    private fun verify(source: File, copy: File) {
        if (source.isDirectory) {
            source.walkTopDown().filter(File::isFile).forEach { verify(it, File(copy, it.relativeTo(source).path)) }
            return
        }
        // The database counts across its chunks (the copy may be split where the original is not, or the reverse).
        if (!copy.isFile || SplitDatabase.size(copy) != SplitDatabase.size(source)) throw MoveException(MoveStrings.verifyFailed)
        if (!SplitDatabase.files(copy).all(PageCache::drop)) throw MoveException(MoveStrings.verifyUnavailable)
        if (tailChecksum(source) != tailChecksum(copy)) throw MoveException(MoveStrings.verifyFailed)
    }

    /** CRC32 of the last MiB of [file], read across a split database's chunks (only a database has any). */
    private fun tailChecksum(file: File): Long {
        val tail = ArrayDeque<ByteArray>()
        var need = VERIFY_TAIL_BYTES
        for (part in SplitDatabase.files(file).asReversed()) {
            if (need <= 0) break
            RandomAccessFile(part, "r").use { raf ->
                val n = minOf(need, raf.length())
                raf.seek(raf.length() - n)
                tail.addFirst(ByteArray(n.toInt()).also(raf::readFully))
                need -= n
            }
        }
        return CRC32().apply { tail.forEach { update(it) } }.value
    }

    private fun staged(dir: File, name: String) = File(dir, name + LibraryStore.PARTIAL_SUFFIX)

    /**
     * What the target folder already holds of a library: what an interrupted move or install left, or an old library
     * that is not the one in use (left on a card that was out when the library was installed on the phone). It is
     * counted as free space and deleted before the copy.
     */
    private fun leftoverBytes(dir: File): Long = targetLeftovers(dir).sumOf { item -> item.walkTopDown().filter(File::isFile).sumOf { it.length() } }

    private fun targetLeftovers(dir: File): List<File> {
        val names = LibraryStore.LIBRARY_ITEMS + DB_SIDECARS.map { LibraryStore.DB_NAME + it } + LEFTOVERS + (LibraryStore.DB_NAME + APPLYING_SUFFIX)
        val chunks = SplitDatabase.allOverflowChunks(dir, LibraryStore.DB_NAME) + SplitDatabase.allOverflowChunks(dir, LibraryStore.DB_NAME + LibraryStore.PARTIAL_SUFFIX)
        return (names.map { File(dir, it) } + names.map { staged(dir, it) }).filter(File::exists) + chunks
    }

    /** Clears [dir] of library leftovers; never the library in use. */
    private fun discardStaged(dir: File) {
        if (dir == store.libraryDir) return
        targetLeftovers(dir).forEach { it.deleteRecursively() }
    }

    private fun message(e: Throwable): String = when {
        e is MoveException -> e.message.orEmpty()
        e.message.orEmpty().contains("ENOSPC") || e.message.orEmpty().contains("No space left", ignoreCase = true) -> MoveStrings.notEnoughSpace
        e.message.orEmpty().contains("EFBIG") || e.message.orEmpty().contains("File too large", ignoreCase = true) -> MoveStrings.fileTooLarge
        else -> MoveStrings.failed.format(e.message ?: e.toString())
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        app.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pardes:LibraryMove")
            ?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }.getOrNull()

    /**
     * Deletes the library kept in [location] when it is not the one in use (a copy left there, or brought from a
     * computer), the database first. Blocking.
     */
    fun deleteOtherLibrary(location: LibraryLocation) {
        if (isBusy || location.dir == store.libraryDir) return
        File(location.dir, LibraryStore.DB_NAME).delete()
        targetLeftovers(location.dir).forEach { runCatching { it.deleteRecursively() } }
    }

    private class MoveException(message: String) : IOException(message)

    private class MoveCancelledException : IOException("cancelled")

    companion object {
        /** Room left free on the target beyond the library itself. */
        const val SPACE_HEADROOM_BYTES = 268_435_456L
        private const val BUFFER_SIZE = 1 shl 20
        private const val VERIFY_TAIL_BYTES = 1L shl 20
        private const val RESTART_DELAY_MS = 2_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
        private const val WAL = "-wal"
        private const val APPLYING_SUFFIX = ".applying"
        private val DB_SIDECARS = listOf("-wal", "-shm", "-journal")

        /** What else the old place may hold that belongs to the library there: the delta cache. */
        private val LEFTOVERS = listOf("delta-cache")

        @Volatile
        private var instance: LibraryMover? = null

        fun get(context: Context): LibraryMover =
            instance ?: synchronized(this) {
                instance ?: LibraryMover(context.applicationContext as ZayitApplication).also { instance = it }
            }
    }
}

/**
 * Drops a file's pages from the page cache (posix_fadvise DONTNEED, by cache.c in libpardes_multiplex.so), so that a
 * read of a copy just written and flushed reaches the storage instead of RAM. When the library cannot be loaded or the
 * call fails, [drop] says so and the caller stops; through FUSE, the lower file system may serve its own cached pages
 * either way.
 */
internal object PageCache {
    private const val TAG = "PageCache"

    private val loaded: Boolean = runCatching { System.loadLibrary("pardes_multiplex") }
        .onFailure { Log.w(TAG, "No native library: cached pages cannot be dropped", it) }
        .isSuccess

    /** Drops [file]'s cached pages; it must have been flushed (only clean pages go). False when they were not dropped. */
    fun drop(file: File): Boolean {
        if (!loaded) return false
        return runCatching { nativeDropCache(file.path) }
            .onSuccess { error -> if (error != 0) Log.w(TAG, "${file.path}: cached pages not dropped (errno $error)") }
            .onFailure { Log.w(TAG, "${file.path}: cached pages not dropped", it) }
            .getOrNull() == 0
    }

    /** 0, or an errno value. */
    private external fun nativeDropCache(path: String): Int
}

/** Texts of the move (Android only). */
internal object MoveStrings {
    const val blockedInstall = "התקנת הספרייה פועלת כעת. אפשר להעביר את הספרייה כשתסתיים."
    const val blockedUpdate = "עדכון הספרייה פועל כעת או לא הושלם. אפשר להעביר את הספרייה אחרי שיסתיים."
    const val nothingToMove = "לא נמצאה ספרייה להעברה."
    const val notEnoughSpace = "אין די מקום פנוי ביעד."
    const val fileTooLarge = "כרטיס הזיכרון מפורמט ב-FAT32, שאינו מקבל קבצים של 4GB ומעלה, וקובץ בספרייה גדול מכך. אפשר לפרמט את הכרטיס ל-exFAT במחשב."
    const val verifyFailed = "בדיקת העותק החדש נכשלה: ייתכן שכרטיס הזיכרון פגום. הספרייה נשארה במקומה."
    const val verifyUnavailable = "לא ניתן לבדוק את העותק החדש. הספרייה נשארה במקומה."
    const val failed = "ההעברה נכשלה: %1\$s. הספרייה נשארה במקומה."
}

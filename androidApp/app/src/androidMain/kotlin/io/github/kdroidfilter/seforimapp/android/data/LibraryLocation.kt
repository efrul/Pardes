package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

/**
 * A place the library can be kept in: the phone's own storage, or a removable memory card (an SD card Android mounts
 * as portable storage). Either way it is the app's own folder on that volume, `<volume>/Android/data/<app>/files/library`,
 * which needs no storage permission. Android only: desktop has a single databases folder.
 */
data class LibraryLocation(
    /** [PHONE], or the card's volume UUID (its folder under /storage). */
    val key: String,
    /** The library folder on this volume. */
    val dir: File,
    val removable: Boolean,
    /** "זיכרון הטלפון", or the system's name for the card ("כרטיס SD של SanDisk"). */
    val label: String,
    /** The volume's Linux filesystem (vfat, exfat, f2fs…), or null when it cannot be read. */
    val fileSystem: String?,
) {
    /** FAT32 (vfat) takes no file of 4 GB or more; null when the volume has no such limit (or it is not known). */
    val maxFileBytes: Long? get() = if (fileSystem in FAT_TYPES) FAT_MAX_FILE_BYTES else null

    val isFat: Boolean get() = maxFileBytes != null

    fun space(): LibraryInstaller.DiskSpace = runCatching {
        dir.mkdirs()
        val stat = StatFs(dir.path)
        LibraryInstaller.DiskSpace(totalBytes = stat.totalBytes, availableBytes = stat.availableBytes)
    }.getOrDefault(LibraryInstaller.DiskSpace(0L, 0L))

    companion object {
        const val PHONE = "phone"
        const val PHONE_LABEL = "זיכרון הטלפון"

        /** The largest file FAT32 holds: 4 GiB less one byte. */
        const val FAT_MAX_FILE_BYTES = 4_294_967_295L

        private val FAT_TYPES = setOf("vfat", "msdos", "fat")

        /**
         * The phone (the app's external files on primary storage, else its internal files) and every card mounted now.
         * A card counts only when Android reports it removable and mounted read-write: an adopted card ("internal
         * storage") is part of the phone, and a card mounted read-only cannot take a library.
         */
        fun read(context: Context, internalDir: File): List<LibraryLocation> {
            val external = runCatching { context.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList())
            val mounts = readMounts()
            val phoneDir = external.firstOrNull()?.let { File(it, LIBRARY_FOLDER) } ?: internalDir
            val phone = LibraryLocation(PHONE, phoneDir, removable = false, label = PHONE_LABEL, fileSystem = mounts["/data"])
            val storage = context.getSystemService(StorageManager::class.java)
            val cards = external.drop(1).filterNotNull().mapNotNull { files ->
                val volume = runCatching { storage?.getStorageVolume(files) }.getOrNull() ?: return@mapNotNull null
                val uuid = volume.uuid ?: return@mapNotNull null
                if (!volume.isRemovable || volume.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
                LibraryLocation(
                    key = uuid,
                    dir = File(files, LIBRARY_FOLDER),
                    removable = true,
                    label = runCatching { volume.getDescription(context) }.getOrNull()?.takeIf { it.isNotBlank() } ?: CARD_LABEL,
                    // vold mounts the card's own filesystem at /mnt/media_rw/<uuid>; /storage/<uuid> is the FUSE view of it.
                    fileSystem = mounts["/mnt/media_rw/$uuid"] ?: mounts["/storage/$uuid"]?.takeUnless { it in VIEW_TYPES },
                )
            }
            return listOf(phone) + cards
        }

        const val CARD_LABEL = "כרטיס הזיכרון"
        const val LIBRARY_FOLDER = "library"
        private val VIEW_TYPES = setOf("fuse", "sdcardfs", "esdfs")

        /** Mount point → filesystem type, from this process's /proc/self/mounts. */
        private fun readMounts(): Map<String, String> = runCatching {
            File("/proc/self/mounts").readLines().mapNotNull { line ->
                val fields = line.split(' ')
                if (fields.size >= 3) fields[1].replace("\\040", " ") to fields[2] else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}

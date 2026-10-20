// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * The files of a library database kept in chunks, for a FAT32 memory card, which takes no file of 4 GB or more while
 * seforim.db is 8 GB. The chunks are those of SQLite's multiplex VFS (src/test_multiplex.c): a plain split of the file
 * into pieces of [CHUNK_BYTES], the first keeping the database's name and the next ones named `seforim.db001`,
 * `seforim.db002`… They are written by the install and the move ([ChunkedOutputStream]) and read through the multiplex
 * VFS ([SplitDatabase], which opens them). A database in one file has no overflow chunks.
 */
object DatabaseChunks {
    /** SQLITE_MULTIPLEX_CHUNK_SIZE: 2 GiB less 64 KiB. The multiplex VFS also finds it from the first chunk's size. */
    const val CHUNK_BYTES = 2_147_418_112L

    private val CHUNK_NUMBER = Regex("""\d{3}""")

    /** Chunk [index] of the database [base]: the name itself, then base001, base002… (multiplexFilename). */
    fun chunkName(base: String, index: Int): String = if (index == 0) base else base + String.format(Locale.ROOT, "%03d", index)

    /** [db]'s overflow chunks that exist, in order (none for a database in one file). */
    fun overflowChunks(db: File): List<File> {
        val dir = db.parentFile ?: return emptyList()
        return generateSequence(1) { it + 1 }.map { File(dir, chunkName(db.name, it)) }.takeWhile { it.isFile }.toList()
    }

    /** [db] and its overflow chunks. */
    fun files(db: File): List<File> = listOf(db) + overflowChunks(db)

    fun isSplit(db: File): Boolean = File(db.path + "001").isFile

    /** The database's size: all its chunks. */
    fun size(db: File): Long = files(db).sumOf { it.length() }

    /** Every overflow chunk of [base] in [dir], a gap or a stale one included. */
    fun allOverflowChunks(dir: File, base: String): List<File> =
        dir.listFiles { file -> file.isFile && file.name.startsWith(base) && CHUNK_NUMBER.matches(file.name.removePrefix(base)) }.orEmpty().toList()

    fun deleteOverflow(dir: File, base: String) {
        allOverflowChunks(dir, base).forEach { it.delete() }
    }

    /** Renames [from]'s overflow chunks to [to]'s (the database itself is left to the caller, as it goes last). */
    fun renameOverflow(from: File, to: File) {
        val dir = to.parentFile ?: return
        deleteOverflow(dir, to.name)
        overflowChunks(from).forEachIndexed { i, chunk ->
            if (!chunk.renameTo(File(dir, chunkName(to.name, i + 1)))) throw IOException("Cannot rename ${chunk.name}")
        }
    }

    /** The concatenation of [db]'s chunks. */
    fun openInput(db: File): InputStream = java.io.SequenceInputStream(java.util.Collections.enumeration(files(db).map { java.io.FileInputStream(it) }))
}

/**
 * Writes one database as SQLite multiplex chunks of [DatabaseChunks.CHUNK_BYTES] next to [base] ([base] itself, then
 * base001…), flushing each to disk as it is closed. A database smaller than one chunk stays a single file.
 */
class ChunkedOutputStream(private val base: File) : OutputStream() {
    private var index = 0
    private var inChunk = 0L
    private var closed = false
    private var out: FileOutputStream = FileOutputStream(base)

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var left = len
        while (left > 0) {
            if (inChunk == DatabaseChunks.CHUNK_BYTES) next()
            val n = minOf(left.toLong(), DatabaseChunks.CHUNK_BYTES - inChunk).toInt()
            out.write(b, offset, n)
            inChunk += n
            offset += n
            left -= n
        }
    }

    private fun next() {
        finish(out)
        index++
        inChunk = 0
        out = FileOutputStream(File(base.parentFile, DatabaseChunks.chunkName(base.name, index)))
    }

    private fun finish(stream: FileOutputStream) {
        stream.flush()
        stream.fd.sync()
        stream.close()
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        closed = true
        finish(out)
    }
}

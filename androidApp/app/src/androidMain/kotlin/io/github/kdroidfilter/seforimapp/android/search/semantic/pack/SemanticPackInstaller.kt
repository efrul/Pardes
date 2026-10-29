// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/search/semantic/pack/SemanticPackInstaller.kt,
// same package and names, with the same checks (each file against the manifest, the model's sha256s, the index of the
// model and of the library installed) and the same staging beside the library, swapped in at the next start
// (PendingSemanticPack). What differs, each marked "Android:":
//  - the bundle is read as it downloads, part by part (sha256 of each as it ends), straight into the staging folder, as
//    Android's library installer reads the library's bundle: a phone has no room for 2 GB of parts beside the 2.7 GB
//    they unpack to, which desktop keeps in .pardes-semantic-download (a stopped download starts again here);
//  - files come from the system's file picker (content URIs, several picked at once: the manifest with its parts, or
//    the two zips), not from a path whose folder holds the rest; the folders already unzipped are not offered (the
//    transfer scripts copy them from a computer);
//  - HexFormat, InputStream.transferTo and OutputStream.nullOutputStream are API 33 and later: written out here.
package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import android.os.StatFs
import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.android.data.InstallCancelledException
import io.github.kdroidfilter.seforimapp.android.data.InstallToken
import io.github.kdroidfilter.seforimapp.android.data.OCTET_STREAM
import io.github.kdroidfilter.seforimapp.android.data.ResumingHttpInputStream
import io.github.kdroidfilter.seforimapp.android.data.openHttp
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleManifest
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleParts
import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelSpec
import io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Why a pack can't be installed, for the user. */
sealed class PackProblem(
    message: String,
) : Exception(message) {
    /** [name] is none of a pack's files. */
    class NotAPack(
        val name: String,
    ) : PackProblem("$name is not a file of a semantic pack")

    /** [name] is not among the files picked, or not in the release. */
    class Missing(
        val name: String,
    ) : PackProblem("$name is missing")

    /** [name] does not have the size or the checksum its manifest gives. */
    class Corrupted(
        val name: String,
    ) : PackProblem("$name is corrupted")

    /** The model and the index don't belong together, or this app can't read them. */
    class Unusable(
        val reason: String,
    ) : PackProblem(reason)

    /** The index was embedded on the library release [pack], the library is [installed]: its line ids are another's. */
    class OtherLibrary(
        val pack: String,
        val installed: String,
    ) : PackProblem("A pack of the library $pack, the library is $installed")

    /** Not enough room beside the library. */
    class NoSpace(
        val dir: String,
        val needed: Long,
        val free: Long,
    ) : PackProblem("$needed bytes needed in $dir, $free free")
}

/**
 * Installs a semantic pack beside the library at [libraryDir], in two steps. Here, while the app runs, the pack is
 * downloaded ([online]) or taken from files the user picked ([resolve]), unpacked into [stagingDir], beside the library
 * on its volume ([stage]), and checked: the model reads, the index is the model's and of the library installed. At the
 * next start, before anything opens the search, [PendingSemanticPack] puts the two folders in place of the old ones:
 * the search keeps its model and index open while the app runs.
 */
// Android: internal, as the app's download helpers it reads with (InstallToken, ResumingHttpInputStream) are
internal class SemanticPackInstaller(
    private val libraryDir: File,
    /** Android: whether the device has a network now (a dropped download waits for one, as the library's). */
    private val networkAvailable: () -> Boolean = { true },
) {
    val stagingDir = stagingDirOf(libraryDir)

    /** Android: a file a pack is read from, picked or downloaded: its name, size (-1 when unknown), and how to open it. */
    class PackFile(
        val name: String,
        val size: Long,
        val open: () -> InputStream,
    )

    /** A file of the bundle, with its manifest's size and sha256 when known; [open] reads it until [InstallToken] stops. */
    class Part(
        val name: String,
        val size: Long,
        val sha256: String?,
        val open: (InstallToken) -> InputStream,
    )

    /** What a pack to install is made of. */
    sealed interface Source {
        /** The bundle's files in order, and from its manifest the bundle's sha256 and what it holds. */
        data class Bundle(
            val parts: List<Part>,
            val sha256: String?,
            val info: PackInfo?,
        ) : Source {
            val bytes: Long get() = parts.sumOf { it.size.coerceAtLeast(0L) }
        }

        /** The two zips of a pack's release, to install by hand. */
        data class Zips(
            val model: PackFile,
            val index: PackFile,
        ) : Source
    }

    /** A pack unpacked and checked in [stagingDir]: what it holds, the model's id, the index's lines and library. */
    data class Staged(
        val info: PackInfo?,
        val model: String,
        val lines: Long?,
        val library: String?,
    )

    /**
     * The pack [picked] are the files of: the bundle (its manifest with its parts, the parts alone, or the bundle whole),
     * or the two zips (each told by what it holds, whatever the browser named it). Android: the files are all picked
     * together, as the system's picker gives no file's folder.
     */
    fun resolve(picked: List<PackFile>): Source {
        if (picked.isEmpty()) throw PackProblem.NotAPack("")
        picked.firstOrNull { !SemanticPack.isBundleFile(it.name) && !it.name.endsWith(".zip", ignoreCase = true) }
            ?.let { throw PackProblem.NotAPack(it.name) }
        val zips = picked.filter { it.name.endsWith(".zip", ignoreCase = true) }
        return if (zips.isNotEmpty()) zipsOf(zips) else bundleOf(picked)
    }

    /**
     * The bundle of [available], read from its release as it is unpacked ([stage]), each file checked against the
     * manifest. Android: nothing is kept between tries (desktop keeps the whole parts of a stopped download).
     */
    suspend fun online(available: SemanticPackReleases.Available): Source.Bundle =
        withContext(Dispatchers.IO) {
            val manifestAsset = available.assets[SemanticPack.MANIFEST_NAME] ?: throw PackProblem.Missing(SemanticPack.MANIFEST_NAME)
            val text = downloadText(manifestAsset.url)
            val manifest = BundleManifest.parseOrNull(text) ?: throw PackProblem.Unusable("Unknown manifest format")
            val parts =
                manifest.parts.map { part ->
                    val asset = available.assets[part.file] ?: throw PackProblem.Missing(part.file)
                    Part(part.file, part.size, part.sha256) { token -> ResumingHttpInputStream(asset.url, part.size, token, networkAvailable) {} }
                }
            Source.Bundle(parts, manifest.sha256, PackInfo.parseOrNull(text))
        }

    /**
     * Unpacks [source] into [stagingDir] and checks it ([Staged]); on any failure nothing is left there. A pack already
     * waiting for the next start is dropped first ([markerDir]'s request): its folder is the one this fills. [onBytes]
     * reports the bytes of [source] read so far and their total.
     */
    suspend fun stage(
        source: Source,
        markerDir: File,
        onBytes: (read: Long, total: Long) -> Unit = { _, _ -> },
    ): Staged =
        withContext(Dispatchers.IO) {
            val job = coroutineContext.job
            PendingSemanticPack.clear(markerDir)
            discardStaged()
            val token = InstallToken()
            val reads = ArrayList<CheckedParts>()
            coroutineScope {
                // Android: a cancelled job also stops the blocking reads (a download waiting on its socket), which a
                // coroutine's cancellation alone does not reach
                val watch =
                    launch {
                        try {
                            awaitCancellation()
                        } finally {
                            token.cancelled.set(true)
                            reads.forEach { it.abort() }
                        }
                    }
                try {
                    requireSpace(unpackedBytes(source))
                    stagingDir.mkdirs()
                    when (source) {
                        is Source.Bundle -> untar(source, job, CheckedParts(source.parts, token).also(reads::add), onBytes)
                        is Source.Zips -> unzip(listOf(source.model, source.index), job, onBytes)
                    }
                    // What the pack holds: its manifest's, or the copy its zips carry (semantic-release.yml)
                    val info = (source as? Source.Bundle)?.info ?: stagedInfo()
                    verify(info, job).also { staged ->
                        staged.info?.let { File(stagingDir, "${SemanticPack.MODEL_DIR}/${SemanticPack.INFO_NAME}").writeText(it.toJson()) }
                        // The folders known as this pack's wherever they are, should the next start be stopped halfway
                        SemanticPack.FOLDERS.forEach { File(File(stagingDir, it), STAGED_MARK).writeText("") }
                    }
                } catch (e: InstallCancelledException) {
                    discardStaged()
                    throw CancellationException("Cancelled").apply { initCause(e) }
                } catch (e: Throwable) {
                    discardStaged()
                    throw e
                } finally {
                    watch.cancel()
                }
            }
        }

    /** Schedules the [staged] pack for the next start ([PendingSemanticPack]), which puts it in place. */
    fun schedule(
        staged: Staged,
        markerDir: File,
    ) = PendingSemanticPack.schedule(PendingSemanticPack.Request(libraryDir, staged.library), markerDir)

    fun discardStaged() {
        stagingDir.deleteRecursively()
    }

    // The bundle among [picked]: by its manifest when picked, else the bundle whole or its parts by number
    private fun bundleOf(picked: List<PackFile>): Source.Bundle {
        val manifestFile = picked.firstOrNull { it.name.equals(SemanticPack.MANIFEST_NAME, ignoreCase = true) }
        val text = manifestFile?.let { file -> reading({ PackProblem.Corrupted(file.name) }) { file.open().use { String(it.readBytes(), Charsets.UTF_8) } } }
        val manifest = text?.let { BundleManifest.parseOrNull(it) }
        if (manifestFile != null && manifest == null) throw PackProblem.Unusable("Unknown manifest format")
        if (manifest != null) {
            val byName = picked.associateBy { it.name.lowercase() }
            val parts =
                manifest.parts.map { part ->
                    val file = byName[part.file.lowercase()] ?: throw PackProblem.Missing(part.file)
                    if (file.size >= 0 && file.size != part.size) throw PackProblem.Corrupted(part.file)
                    Part(part.file, part.size, part.sha256) { file.open() }
                }
            return Source.Bundle(parts, manifest.sha256, PackInfo.parseOrNull(text))
        }
        // Without its manifest: the bundle whole, or its parts by name, complete as BundleParts can tell
        picked.firstOrNull { it.name.equals(SemanticPack.BUNDLE_NAME, ignoreCase = true) }?.let { whole ->
            return Source.Bundle(listOf(Part(whole.name, whole.size, null) { whole.open() }), null, null)
        }
        val byName = picked.associateBy { it.name }
        // A gap, or a missing last part: the manifest would say which
        val parts =
            runCatching {
                BundleParts.legacyPartNames(byName.keys, SemanticPack.BUNDLE_NAME).map { byName.getValue(it) }.also { parts ->
                    if (parts.all { it.size >= 0 }) BundleParts.requireComplete(parts.map { it.size })
                }
            }.getOrElse { throw PackProblem.Missing(SemanticPack.MANIFEST_NAME) }
        return Source.Bundle(parts.map { file -> Part(file.name, file.size, null) { file.open() } }, null, null)
    }

    // The two zips among [zips], each told by the folder it holds
    private fun zipsOf(zips: List<PackFile>): Source.Zips {
        val byFolder = HashMap<String, PackFile>()
        for (zip in zips) {
            val folder = zipFolder(zip) ?: throw PackProblem.NotAPack(zip.name)
            byFolder.putIfAbsent(folder, zip)
        }
        val model = byFolder[SemanticPack.MODEL_DIR] ?: throw PackProblem.Missing(SemanticPack.MODEL_ZIP)
        val index = byFolder[SemanticPack.INDEX_DIR] ?: throw PackProblem.Missing(SemanticPack.INDEX_ZIP)
        return Source.Zips(model, index)
    }

    /** The pack folder [zip] holds, or null when it is not one of a pack's zips. Android: told from its first entry. */
    private fun zipFolder(zip: PackFile): String? =
        runCatching {
            ZipInputStream(BufferedInputStream(zip.open(), 64 * 1024)).use { input ->
                input.nextEntry?.name?.removePrefix("./")?.substringBefore('/')
            }
        }.getOrNull()?.takeIf { it in SemanticPack.FOLDERS }

    /** What [source] unpacks to, at most, for its size: the bundle's and the zips' estimated. */
    private fun unpackedBytes(source: Source): Long =
        when (source) {
            is Source.Bundle -> unpackedEstimate(source.bytes)
            is Source.Zips -> unpackedEstimate(source.model.size.coerceAtLeast(0L) + source.index.size.coerceAtLeast(0L))
        }

    private fun requireSpace(needed: Long) {
        val free = freeBytes(libraryDir)
        if (free in 0 until needed + MARGIN_BYTES) throw PackProblem.NoSpace(libraryDir.path, needed + MARGIN_BYTES, free)
    }

    private fun untar(
        bundle: Source.Bundle,
        job: Job,
        joined: CheckedParts,
        onBytes: (Long, Long) -> Unit,
    ) {
        val total = bundle.bytes.coerceAtLeast(1L)
        // Hashing the bytes read for unpacking costs no extra reading
        val digest = bundle.sha256?.let { MessageDigest.getInstance("SHA-256") }
        joined.digest = digest
        joined.onRead = { read -> onBytes(read, total) }
        // A failure reading the bundle is its file's (the part being read), not this disk's
        val damaged = { PackProblem.Corrupted(joined.currentName()) }
        joined.use { counted ->
            TarArchiveInputStream(ZstdInputStream(BufferedInputStream(counted, BUFFER))).use { tar ->
                while (true) {
                    val entry = reading(damaged) { tar.nextEntry } ?: break
                    val out = target(entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        write(tar, out, job, damaged)
                    }
                }
                // The tar ends before the compressed stream does: its end is read too, for the last part's checksum
                reading(damaged) { drain(counted, job) }
                if (digest != null && !hex(digest.digest()).equals(bundle.sha256, ignoreCase = true)) {
                    throw PackProblem.Corrupted(SemanticPack.MANIFEST_NAME)
                }
            }
        }
    }

    private fun unzip(
        zips: List<PackFile>,
        job: Job,
        onBytes: (Long, Long) -> Unit,
    ) {
        val total = zips.sumOf { it.size.coerceAtLeast(0L) }.coerceAtLeast(1L)
        var done = 0L
        for (zip in zips) {
            // A stream, which checks each file's CRC
            val damaged = { PackProblem.Corrupted(zip.name) }
            val before = done
            val counted = CountingInputStream(reading(damaged) { zip.open() }) { read -> onBytes(before + read, total) }
            ZipInputStream(BufferedInputStream(counted, BUFFER)).use { input ->
                while (true) {
                    val entry = reading(damaged) { input.nextEntry } ?: break
                    val out = target(entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        write(input, out, job, damaged)
                    }
                }
            }
            done += zip.size.coerceAtLeast(counted.count)
        }
    }

    /** Where the pack's file [name] goes in [stagingDir]; a name outside the pack's two folders is refused. */
    private fun target(name: String): File {
        val clean = name.removePrefix("./")
        val file = File(stagingDir, clean).canonicalFile
        val inside = file.path.startsWith(stagingDir.canonicalPath + File.separator)
        if (!inside || clean.substringBefore('/') !in SemanticPack.FOLDERS) throw PackProblem.NotAPack(name)
        return file
    }

    /** Copies [input] to [out]; a failure reading [input] is the pack's file [damaged] names, not this disk's. */
    private fun write(
        input: InputStream,
        out: File,
        job: Job,
        damaged: () -> PackProblem,
    ) {
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { output ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                job.ensureActive()
                val read = reading(damaged) { input.read(buffer) }
                if (read < 0) break
                output.write(buffer, 0, read)
            }
            output.fd.sync()
        }
    }

    /** [block], a read of the pack's files: an IOException there is a damaged file, [damaged]. */
    private inline fun <T> reading(
        damaged: () -> PackProblem,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: IOException) {
            throw damaged().apply { initCause(e) }
        }

    private fun stagedInfo(): PackInfo? {
        val file = File(stagingDir, "${SemanticPack.MODEL_DIR}/${SemanticPack.INFO_NAME}")
        return if (file.isFile) PackInfo.parseOrNull(file.readText()) else null
    }

    /** The staged folders checked as IndependentSemanticSearch opens them, and against the library installed. */
    private fun verify(
        info: PackInfo?,
        job: Job,
    ): Staged {
        val modelDir = File(stagingDir, SemanticPack.MODEL_DIR)
        val spec =
            runCatching { OpenModelSpec.read(modelDir.toPath()) }
                .getOrElse { throw PackProblem.Unusable(it.message ?: it.toString()) }
                ?: throw PackProblem.Missing("${SemanticPack.MODEL_DIR}/${OpenModelSpec.FILE_NAME}")
        // The checksums export_onnx.py gives the model's files, when it does
        val described = JSONObject(File(modelDir, OpenModelSpec.FILE_NAME).readText())
        for ((key, file) in listOf("onnxSha256" to spec.onnx.toFile(), "tokenizerSha256" to spec.tokenizer.toFile())) {
            val expected = described.optString(key).ifEmpty { null } ?: continue
            if (!sha256(file, job).equals(expected, ignoreCase = true)) throw PackProblem.Corrupted(file.name)
        }
        val index =
            runCatching { VectorIndexBuilder.readInfo(File(stagingDir, SemanticPack.INDEX_DIR).toPath()) }
                .getOrElse { throw PackProblem.Corrupted(SemanticPack.INDEX_DIR) }
                ?: throw PackProblem.Missing(SemanticPack.INDEX_DIR)
        if (index.model != spec.id || index.dimension != spec.dimension) {
            throw PackProblem.Unusable("The index is of ${index.model} (${index.dimension}), the model is ${spec.id} (${spec.dimension})")
        }
        if (info != null && info.model != spec.id) throw PackProblem.Unusable("The manifest names ${info.model}, the model is ${spec.id}")
        val installed = InstalledPack.libraryVersion(libraryDir)
        val packLibraries = listOfNotNull(info?.library, index.dbRelease)
        if (installed != null) {
            packLibraries.firstOrNull { !SemanticPack.sameLibrary(it, installed) }?.let { throw PackProblem.OtherLibrary(it, installed) }
        }
        return Staged(info, spec.id, index.lines, packLibraries.firstOrNull())
    }

    private fun downloadText(url: String): String {
        val connection = openHttp(url, 0, "$OCTET_STREAM, application/json")
        return try {
            connection.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Android: the bundle's [parts] read back to back, each checked for its size and sha256 as it ends (a damaged one
     * named by [currentName]), the bytes hashed into [digest] too; [onRead] gets the bytes read so far.
     */
    private class CheckedParts(
        private val parts: List<Part>,
        private val token: InstallToken,
    ) : InputStream() {
        var digest: MessageDigest? = null
        var onRead: (Long) -> Unit = {}

        @Volatile
        private var current: InputStream? = null
        private var index = 0
        private var partDigest: MessageDigest? = null
        private var readInPart = 0L
        private var position = 0L

        fun currentName(): String = parts.getOrNull(index)?.name ?: parts.lastOrNull()?.name.orEmpty()

        /** Called from another thread on cancel: unblocks a download waiting on its socket. */
        fun abort() {
            (current as? ResumingHttpInputStream)?.abort()
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (len == 0) return 0
            while (index < parts.size) {
                token.check()
                val part = parts[index]
                val input = current ?: open(part)
                val read = input.read(b, off, len)
                if (read > 0) {
                    partDigest?.update(b, off, read)
                    digest?.update(b, off, read)
                    readInPart += read
                    position += read
                    if (part.size >= 0 && readInPart > part.size) throw PackProblem.Corrupted(part.name)
                    onRead(position)
                    return read
                }
                if (read < 0) end(part)
            }
            return -1
        }

        private fun open(part: Part): InputStream {
            val opened = part.open(token)
            current = opened
            readInPart = 0L
            partDigest = part.sha256?.let { MessageDigest.getInstance("SHA-256") }
            return opened
        }

        private fun end(part: Part) {
            runCatching { current?.close() }
            current = null
            if (part.size >= 0 && readInPart != part.size) throw PackProblem.Corrupted(part.name)
            val actual = partDigest?.digest()?.let(::hex)
            if (actual != null && !actual.equals(part.sha256, ignoreCase = true)) throw PackProblem.Corrupted(part.name)
            index++
        }

        override fun close() {
            runCatching { current?.close() }
            current = null
            index = parts.size
        }
    }

    private class CountingInputStream(
        private val input: InputStream,
        private val onRead: (Long) -> Unit,
    ) : InputStream() {
        var count = 0L
            private set

        override fun read(): Int = input.read().also { if (it >= 0) onRead(++count) }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int =
            input.read(b, off, len).also {
                if (it > 0) {
                    count += it
                    onRead(count)
                }
            }

        override fun close() = input.close()
    }

    companion object {
        /** Beside the library, on its volume, so the pack goes in place by a rename. */
        const val STAGING_DIR_NAME = ".pardes-semantic-staged"

        /** In each staged folder until [PendingSemanticPack] has put both in place. */
        const val STAGED_MARK = ".pardes-staged"

        /** Room left on the library's volume beyond the pack, as a library move leaves. */
        private const val MARGIN_BYTES = 200L shl 20

        /** What a bundle unpacks to, at most, for its size: the int8 vectors compress little (pack 2: 1.32). */
        private const val UNPACK_RATIO = 1.5

        private const val BUFFER = 1 shl 20

        fun stagingDirOf(libraryDir: File) = File(libraryDir, STAGING_DIR_NAME)

        fun unpackedEstimate(bundleBytes: Long): Long = (bundleBytes * UNPACK_RATIO).toLong()

        /** Android: the bytes free for the app on [dir]'s volume, or -1 when they can't be read (desktop's LibraryLocation.freeBytes). */
        fun freeBytes(dir: File): Long =
            runCatching {
                dir.mkdirs()
                StatFs(dir.path).availableBytes
            }.getOrDefault(-1L)

        private fun drain(
            input: InputStream,
            job: Job,
        ) {
            val buffer = ByteArray(BUFFER)
            while (true) {
                job.ensureActive()
                if (input.read(buffer) < 0) return
            }
        }

        private fun sha256(
            file: File,
            job: Job,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    job.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return hex(digest.digest())
        }

        // Android: HexFormat is API 34
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

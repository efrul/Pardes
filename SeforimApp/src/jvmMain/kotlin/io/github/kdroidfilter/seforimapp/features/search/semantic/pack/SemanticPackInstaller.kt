package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.features.onboarding.download.HttpFileDownloader
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleManifest
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleParts
import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelSpec
import io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.HexFormat
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Why a pack can't be installed, for the user. */
sealed class PackProblem(
    message: String,
) : Exception(message) {
    /** [name] is none of a pack's files. */
    class NotAPack(
        val name: String,
    ) : PackProblem("$name is not a file of a semantic pack")

    /** [name] is not beside the file picked, or not in the release. */
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

    /** Not enough room beside the library. */
    class NoSpace(
        val dir: String,
        val needed: Long,
        val free: Long,
    ) : PackProblem("$needed bytes needed in $dir, $free free")
}

/**
 * Installs a semantic pack beside the library at [libraryDir], in two steps. Here, while the app runs, the pack is
 * downloaded ([download]) or found among files the user downloaded ([resolve]), unpacked into [stagingDir], beside the
 * library on its drive ([stage]), and checked: the model reads, and the index is the model's.
 * At the next start, before anything opens the search, [PendingSemanticPack] puts the two folders in place of the old
 * ones: the search keeps its model and index open while the app runs, and Windows renames no open file.
 */
class SemanticPackInstaller(
    private val libraryDir: File,
) {
    private val http = HttpFileDownloader()
    val downloadDir = File(libraryDir, DOWNLOAD_DIR_NAME)
    val stagingDir = stagingDirOf(libraryDir)

    /** What a pack to install is made of. */
    sealed interface Source {
        /** The bundle's files in order, and from its manifest the bundle's sha256, each file's, and what it holds. */
        data class Bundle(
            val files: List<File>,
            val sha256: String?,
            val info: PackInfo?,
            val checksums: List<String>? = null,
        ) : Source

        /** The two zips of a pack's release, to install by hand. */
        data class Zips(
            val model: File,
            val index: File,
        ) : Source

        /** The two folders, already unzipped. */
        data class Folders(
            val model: File,
            val index: File,
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
     * The pack [picked] is a file of, with the rest of it beside it: the bundle (its manifest, one of its parts, or the
     * bundle whole), one of the two zips (the other found by what it holds, whatever the browser named it), or the
     * model's `pardes-model.json` in the unzipped folders.
     */
    fun resolve(picked: File): Source {
        val file = picked.absoluteFile
        val dir = file.parentFile ?: throw PackProblem.NotAPack(file.name)
        return when {
            SemanticPack.isBundleFile(file.name) -> bundleIn(dir)
            file.name.endsWith(".zip", ignoreCase = true) -> zipsBeside(file)
            file.name.equals(OpenModelSpec.FILE_NAME, ignoreCase = true) -> foldersFrom(dir)
            else -> throw PackProblem.NotAPack(file.name)
        }
    }

    /**
     * Downloads the bundle of [available] into [downloadDir], each file checked against the manifest. A file a stopped
     * download left whole is kept.
     */
    suspend fun download(
        available: SemanticPackReleases.Available,
        onBytes: (read: Long, total: Long) -> Unit,
    ): Source.Bundle =
        withContext(Dispatchers.IO) {
            val job = coroutineContext.job
            val manifestAsset = available.assets[SemanticPack.MANIFEST_NAME] ?: throw PackProblem.Missing(SemanticPack.MANIFEST_NAME)
            val text = http.downloadText(manifestAsset.url)
            val manifest = BundleManifest.parseOrNull(text) ?: throw PackProblem.Unusable("Unknown manifest format")
            downloadDir.mkdirs()
            val files = manifest.parts.map { File(downloadDir, it.file) }
            val whole =
                manifest.parts.zip(files).filter { (part, file) ->
                    file.length() == part.size && sha256(file, job).equals(part.sha256, ignoreCase = true)
                }
            requireSpace(manifest.size - whole.sumOf { it.first.size } + unpackedEstimate(manifest.size))
            var done = 0L
            for ((part, file) in manifest.parts.zip(files)) {
                if (whole.none { it.second == file }) {
                    val asset = available.assets[part.file] ?: throw PackProblem.Missing(part.file)
                    http.download(asset.url, file, part.size, part.sha256) { read ->
                        job.ensureActive()
                        onBytes(done + read, manifest.size)
                    }
                }
                done += part.size
                onBytes(done, manifest.size)
            }
            File(downloadDir, SemanticPack.MANIFEST_NAME).writeText(text)
            Source.Bundle(files, manifest.sha256, PackInfo.parseOrNull(text), manifest.parts.map { it.sha256 })
        }

    /**
     * Unpacks [source] into [stagingDir] and checks it ([Staged]); on any failure nothing is left there. A pack already
     * waiting for the next start is dropped first: its folder is the one this fills.
     */
    suspend fun stage(
        source: Source,
        onProgress: (Float) -> Unit = {},
    ): Staged =
        withContext(Dispatchers.IO) {
            val job = coroutineContext.job
            PendingSemanticPack.clear()
            discardStaged()
            try {
                requireSpace(unpackedBytes(source))
                stagingDir.mkdirs()
                when (source) {
                    is Source.Bundle -> untar(source, job, onProgress)
                    is Source.Zips -> unzip(listOf(source.model, source.index), job, onProgress)
                    is Source.Folders -> copyFolders(source, job, onProgress)
                }
                // What the pack holds: its manifest's, or the copy its zips carry (semantic-release.yml)
                val info = (source as? Source.Bundle)?.info ?: stagedInfo()
                verify(info, job).also { staged ->
                    staged.info?.let { File(stagingDir, "${SemanticPack.MODEL_DIR}/${SemanticPack.INFO_NAME}").writeText(it.toJson()) }
                    // The folders known as this pack's wherever they are, should the next start be stopped halfway
                    SemanticPack.FOLDERS.forEach { File(File(stagingDir, it), STAGED_MARK).writeText("") }
                    onProgress(1f)
                }
            } catch (e: Throwable) {
                discardStaged()
                throw e
            }
        }

    /** Schedules the pack [stage] left in [stagingDir] for the next start ([PendingSemanticPack]), which puts it in place. */
    fun schedule() = PendingSemanticPack.schedule(PendingSemanticPack.Request(libraryDir))

    fun discardStaged() {
        stagingDir.deleteRecursively()
    }

    fun discardDownload() {
        downloadDir.deleteRecursively()
    }

    private fun bundleIn(dir: File): Source.Bundle {
        val text = File(dir, SemanticPack.MANIFEST_NAME).takeIf { it.isFile }?.readText()
        val manifest = text?.let { BundleManifest.parseOrNull(it) }
        if (manifest != null) {
            val files =
                manifest.parts.map { part ->
                    File(dir, part.file).also { file ->
                        if (!file.isFile) throw PackProblem.Missing(part.file)
                        if (file.length() != part.size) throw PackProblem.Corrupted(part.file)
                    }
                }
            return Source.Bundle(files, manifest.sha256, PackInfo.parseOrNull(text), manifest.parts.map { it.sha256 })
        }
        // Without its manifest: the bundle whole, or its parts by name, complete as BundleParts can tell
        File(dir, SemanticPack.BUNDLE_NAME).takeIf { it.isFile }?.let { return Source.Bundle(listOf(it), null, null) }
        val names = dir.list().orEmpty().toList()
        // A gap, or a missing last part: the manifest would say which
        val parts =
            runCatching {
                BundleParts.legacyPartNames(names, SemanticPack.BUNDLE_NAME).map { File(dir, it) }.also { parts ->
                    BundleParts.requireComplete(parts.map { it.length() })
                }
            }.getOrElse { throw PackProblem.Missing(SemanticPack.MANIFEST_NAME) }
        return Source.Bundle(parts, null, null)
    }

    private fun zipsBeside(picked: File): Source.Zips {
        val folder = zipFolder(picked) ?: throw PackProblem.NotAPack(picked.name)
        val other = SemanticPack.FOLDERS.single { it != folder }
        val otherZip = if (other == SemanticPack.MODEL_DIR) SemanticPack.MODEL_ZIP else SemanticPack.INDEX_ZIP
        // The other zip beside it, as named or as a browser renamed a second download ("pardes.vec.lucene (1).zip"): the
        // newest that holds the other folder
        val sibling =
            picked.parentFile
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it != picked && it.name.startsWith(other, ignoreCase = true) && it.name.endsWith(".zip", true) }
                .sortedByDescending { it.lastModified() }
                .firstOrNull { zipFolder(it) == other }
                ?: throw PackProblem.Missing(otherZip)
        return if (folder == SemanticPack.MODEL_DIR) Source.Zips(picked, sibling) else Source.Zips(sibling, picked)
    }

    /** The pack folder [zip] holds, or null when it is not one of a pack's zips. */
    private fun zipFolder(zip: File): String? =
        runCatching {
            ZipFile(zip).use { z ->
                z
                    .entries()
                    .asSequence()
                    .map { it.name.removePrefix("./").substringBefore('/') }
                    .toSet()
            }
        }.getOrNull()?.singleOrNull()?.takeIf { it in SemanticPack.FOLDERS }

    private fun foldersFrom(modelDir: File): Source.Folders {
        val index = File(modelDir.parentFile, SemanticPack.INDEX_DIR)
        if (!index.isDirectory) throw PackProblem.Missing(SemanticPack.INDEX_DIR)
        if (modelDir.canonicalFile == File(libraryDir, SemanticPack.MODEL_DIR).canonicalFile) {
            throw PackProblem.Unusable("These are the folders already installed")
        }
        return Source.Folders(modelDir, index)
    }

    /** What [source] unpacks to: the zips' and the folders' sizes, the bundle's estimated. */
    private fun unpackedBytes(source: Source): Long =
        when (source) {
            is Source.Bundle -> unpackedEstimate(source.files.sumOf { it.length() })
            is Source.Zips -> zipBytes(source.model) + zipBytes(source.index)
            is Source.Folders -> listOf(source.model, source.index).sumOf { dir -> dir.walk().filter { it.isFile }.sumOf { it.length() } }
        }

    private fun requireSpace(needed: Long) {
        val free = LibraryLocation.freeBytes(libraryDir)
        if (free in 0 until needed + MARGIN_BYTES) throw PackProblem.NoSpace(libraryDir.path, needed + MARGIN_BYTES, free)
    }

    private fun untar(
        bundle: Source.Bundle,
        job: Job,
        onProgress: (Float) -> Unit,
    ) {
        val total = bundle.files.sumOf { it.length() }.coerceAtLeast(1L)
        val digest = bundle.sha256?.let { MessageDigest.getInstance("SHA-256") }
        val joined = SequenceInputStream(Collections.enumeration(bundle.files.map { BufferedInputStream(FileInputStream(it), BUFFER) }))
        val damaged = { damagedFile(bundle, job) }
        // Hashing the bytes read for unpacking costs no extra reading
        CountingInputStream(digest?.let { DigestInputStream(joined, it) } ?: joined).use { counted ->
            TarArchiveInputStream(ZstdInputStream(counted)).use { tar ->
                while (true) {
                    val entry = reading(damaged) { tar.nextEntry } ?: break
                    val out = target(entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        write(tar, out, job, damaged) { onProgress(counted.count.toFloat() / total) }
                    }
                }
                if (digest != null) {
                    // The tar ends before the compressed stream does: its end is hashed too
                    reading(damaged) { counted.transferTo(OutputStream.nullOutputStream()) }
                    if (!HexFormat.of().formatHex(digest.digest()).equals(bundle.sha256, ignoreCase = true)) throw damaged()
                }
            }
        }
    }

    /** The bundle's file that is not its manifest's, the first one when that can't be told. */
    private fun damagedFile(
        bundle: Source.Bundle,
        job: Job,
    ): PackProblem {
        val checksums = bundle.checksums?.takeIf { it.size == bundle.files.size }
        val damaged = checksums?.let { sums -> bundle.files.indices.firstOrNull { !sha256(bundle.files[it], job).equals(sums[it], true) } }
        return PackProblem.Corrupted(bundle.files[damaged ?: 0].name)
    }

    private fun unzip(
        zips: List<File>,
        job: Job,
        onProgress: (Float) -> Unit,
    ) {
        val total = zips.sumOf(::zipBytes).coerceAtLeast(1L)
        var done = 0L
        for (zip in zips) {
            // A stream, which checks each file's CRC (ZipFile's entries don't)
            val damaged = { PackProblem.Corrupted(zip.name) }
            ZipInputStream(BufferedInputStream(FileInputStream(zip), BUFFER)).use { input ->
                while (true) {
                    val entry = reading(damaged) { input.nextEntry } ?: break
                    val out = target(entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        val before = done
                        write(input, out, job, damaged) { written -> onProgress((before + written).toFloat() / total) }
                        done += out.length()
                    }
                }
            }
        }
    }

    private fun copyFolders(
        folders: Source.Folders,
        job: Job,
        onProgress: (Float) -> Unit,
    ) {
        val pairs = listOf(folders.model to SemanticPack.MODEL_DIR, folders.index to SemanticPack.INDEX_DIR)
        val total = pairs.sumOf { (dir, _) -> dir.walk().filter { it.isFile }.sumOf { it.length() } }.coerceAtLeast(1L)
        var done = 0L
        for ((dir, name) in pairs) {
            dir.walk().filter { it.isFile && it.name != LUCENE_LOCK && it.name != STAGED_MARK }.forEach { file ->
                val before = done
                val out = target("$name/${file.relativeTo(dir).invariantSeparatorsPath}")
                file.inputStream().use { input ->
                    write(input, out, job, { PackProblem.Corrupted(file.name) }) { written ->
                        onProgress((before + written).toFloat() / total)
                    }
                }
                done += file.length()
            }
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
        onWritten: (Long) -> Unit,
    ) {
        out.parentFile.mkdirs()
        out.outputStream().use { output ->
            val buffer = ByteArray(BUFFER)
            var written = 0L
            while (true) {
                job.ensureActive()
                val read = reading(damaged) { input.read(buffer) }
                if (read < 0) break
                output.write(buffer, 0, read)
                written += read
                onWritten(written)
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

    /** The staged folders checked as IndependentSemanticSearch opens them. */
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
        // Not checked against the library installed: a pack works on every library release (SemanticPackReleases.newest)
        return Staged(info, spec.id, index.lines, info?.library ?: index.dbRelease)
    }

    private class CountingInputStream(
        input: InputStream,
    ) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        /** Beside the library, on its drive, so the pack goes in place by a rename. */
        const val DOWNLOAD_DIR_NAME = ".pardes-semantic-download"
        const val STAGING_DIR_NAME = ".pardes-semantic-staged"

        /** In each staged folder until [PendingSemanticPack] has put both in place. */
        const val STAGED_MARK = ".pardes-staged"

        /** Room left on the library's drive beyond the pack, as a library move leaves. */
        private const val MARGIN_BYTES = 200L shl 20

        /** What a bundle unpacks to, at most, for its size: the int8 vectors compress little (pack 2: 1.32). */
        private const val UNPACK_RATIO = 1.5

        private const val BUFFER = 1 shl 20
        private const val LUCENE_LOCK = "write.lock"

        fun stagingDirOf(libraryDir: File) = File(libraryDir, STAGING_DIR_NAME)

        fun unpackedEstimate(bundleBytes: Long): Long = (bundleBytes * UNPACK_RATIO).toLong()

        /** What [zip] unpacks to, as its central directory says. */
        private fun zipBytes(zip: File): Long =
            ZipFile(zip).use { z ->
                z
                    .entries()
                    .asSequence()
                    .sumOf { it.size.coerceAtLeast(0L) }
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
            return HexFormat.of().formatHex(digest.digest())
        }
    }
}

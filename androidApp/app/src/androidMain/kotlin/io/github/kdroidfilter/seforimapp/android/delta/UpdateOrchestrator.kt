package io.github.kdroidfilter.seforimlibrary.deltaupdater

import co.touchlab.kermit.Logger
import com.github.luben.zstd.ZstdInputStream
import kotlinx.serialization.json.Json
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Android variant of SeforimLibrary (7ddf045) `delta-updater/.../UpdateOrchestrator.kt`: turns an
 * [UpdatePath.Chain] into applied deltas, with desktop's steps and progress statuses ("downloading manifest",
 * "downloading patch files", "decompressing …", "applying sqlite delta", "updating lucene", "updating catalog",
 * "done"). For each delta:
 *   1. download the patch files into a stable `delta-v<from>-v<to>` folder (resumable), verify, decompress, verify;
 *   2. apply the SQLite patch via [DeltaApplierClient] (one transaction, hash-checked);
 *   3. update Lucene via [LuceneUpdater] and the caller's sinks — committed only after a complete pass;
 *   4. update `catalog.pb` via [CatalogUpdater];
 *   5. [onDeltaApplied] (the app records the new release), then [DeltaApplierClient.finalizeApply].
 *
 * Failures go forward rather than back (see [DeltaApplierClient]): before the COMMIT nothing has changed; after it
 * the marker says [DeltaApplierClient.Stage.Index] and the next run of the same chain skips straight to Lucene and the
 * catalog, which are idempotent (delete-then-add per line id, atomic file swap). The delta folder is kept until the
 * delta is finished, so neither the download nor the decompressed patch is fetched twice; it also keeps the manifest
 * ([MANIFEST_FILE]), so a delta whose patch is on the device can be finished offline.
 */
class UpdateOrchestrator(
    private val applier: DeltaApplierClient,
    private val downloader: DeltaDownloader,
    private val luceneUpdater: LuceneUpdater,
    private val catalogUpdater: CatalogUpdater,
    private val workDir: Path,
    private val logger: Logger = Logger.withTag("UpdateOrchestrator"),
    private val onDecompress: (written: Long, total: Long) -> Unit = { _, _ -> },
    private val checkCancelled: () -> Unit = {},
) {

    fun applyChain(
        seforimDb: Path,
        catalogPb: Path,
        chain: List<DeltaEntry>,
        fetchManifest: (DeltaEntry) -> DeltaManifest,
        baseUrlForEntry: (DeltaEntry, String) -> String,
        luceneSinks: () -> LuceneUpdater.SinkSession,
        progress: (current: Int, total: Int, status: String) -> Unit = { _, _, _ -> },
        releaseTagFor: (DeltaEntry) -> String? = { null },
        onDeltaApplied: (DeltaEntry) -> Unit = {},
    ) {
        for ((idx, entry) in chain.withIndex()) {
            val step = idx + 1
            checkCancelled()
            progress(step, chain.size, "downloading manifest")
            val deltaDir = workDir.resolve("delta-v${entry.fromVersion}-v${entry.toVersion}")
            Files.createDirectories(deltaDir)
            // Kept with the patch, so a delta interrupted after its download can be finished without the network.
            val cachedManifest = deltaDir.resolve(MANIFEST_FILE)
            val manifest = try {
                fetchManifest(entry).also { Files.write(cachedManifest, DEFAULT_JSON.encodeToString(DeltaManifest.serializer(), it).toByteArray()) }
            } catch (e: Exception) {
                checkCancelled()
                if (!Files.exists(cachedManifest)) throw e
                logger.w(e) { "Manifest of ${entry.fromVersion} → ${entry.toVersion} unreachable; using the copy saved with the patch" }
                DEFAULT_JSON.decodeFromString(DeltaManifest.serializer(), String(Files.readAllBytes(cachedManifest), Charsets.UTF_8))
            }
            val pending = applier.pending(seforimDb)
            val committed = pending != null && pending.fromVersion == entry.fromVersion && pending.toVersion == entry.toVersion &&
                pending.stage == DeltaApplierClient.Stage.Index

            val patchFiles = manifest.patchFiles.map { f ->
                check(f.compression == "zstd") { "unsupported patch compression '${f.compression}' for ${f.file}" }
                val decompressed = deltaDir.resolve(decompressedName(f.file))
                if (Files.exists(decompressed) && Files.size(decompressed) == f.uncompressedSize &&
                    (committed || sha256(decompressed) == f.uncompressedSha256)
                ) {
                    return@map decompressed
                }
                progress(step, chain.size, "downloading patch files")
                val downloaded = downloader.download(
                    url = baseUrlForEntry(entry, f.file),
                    dest = deltaDir.resolve(f.file),
                    expectedSha256 = f.sha256,
                    expectedSize = f.size,
                )
                checkCancelled()
                progress(step, chain.size, "decompressing ${f.file}")
                decompressZstd(downloaded, f.uncompressedSha256, f.uncompressedSize)
            }
            val mainPatch = patchFiles.firstOrNull { it.fileName.toString().contains("global") } ?: patchFiles.first()

            if (!committed) {
                checkCancelled()
                progress(step, chain.size, "applying sqlite delta")
                applier.apply(seforimDb = seforimDb, patchDb = mainPatch, manifest = manifest, entry = entry, releaseTag = releaseTagFor(entry))
            }

            // From here the SQLite delta is committed; the rest is idempotent and resumable (no rollback).
            progress(step, chain.size, "updating lucene")
            luceneSinks().use { session ->
                // seforimDb for book-metadata-only changes (desktop): their lines are re-indexed too.
                luceneUpdater.applyTo(mainPatch, session.delete, session.upsert, liveDbPath = seforimDb)
                session.commit()
            }

            progress(step, chain.size, "updating catalog")
            manifest.catalogBlobName?.let { catalogUpdater.update(mainPatch, catalogPb, it) }

            onDeltaApplied(entry)
            applier.finalizeApply(seforimDb)
            logger.i { "Delta ${entry.fromVersion} → ${entry.toVersion} applied" }
            runCatching { deltaDir.toFile().deleteRecursively() }
        }
        progress(chain.size, chain.size, "done")
    }

    private fun decompressedName(name: String): String = if (name.endsWith(".zst")) name.removeSuffix(".zst") else "$name.decompressed"

    /**
     * Decompresses a zstd patch file next to itself (desktop naming), verifying the size and sha256 of the output, then
     * deletes the compressed copy. Written as `.part` first, so a half-written output is never taken for the patch.
     */
    private fun decompressZstd(compressed: Path, expectedSha256: String, expectedSize: Long): Path {
        val out = compressed.resolveSibling(decompressedName(compressed.fileName.toString()))
        val partial = out.resolveSibling("${out.fileName}.part")
        val md = MessageDigest.getInstance("SHA-256")
        var written = 0L
        Files.newInputStream(compressed).use { rawIn ->
            ZstdInputStream(rawIn.buffered(1 shl 20)).use { zstdIn ->
                FileOutputStream(partial.toFile()).use { fileOut ->
                    val buf = ByteArray(1 shl 20)
                    while (true) {
                        checkCancelled()
                        val n = zstdIn.read(buf)
                        if (n < 0) break
                        if (n == 0) continue
                        md.update(buf, 0, n)
                        fileOut.write(buf, 0, n)
                        written += n
                        onDecompress(written, expectedSize)
                    }
                    fileOut.fd.sync()
                }
            }
        }
        val actualSha = md.digest().joinToString("") { b -> "%02x".format(b) }
        if (written != expectedSize || actualSha != expectedSha256) {
            runCatching { Files.deleteIfExists(partial) }
            check(written == expectedSize) { "decompressed ${compressed.fileName} size mismatch: got $written, expected $expectedSize" }
            error("decompressed ${compressed.fileName} sha256 mismatch: got $actualSha, expected $expectedSha256")
        }
        Files.move(partial, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        // Free space — the compressed copy is no longer needed once the decompressed file passes verification.
        runCatching { Files.deleteIfExists(compressed) }
        return out
    }

    private fun sha256(path: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    companion object {
        /** The delta's manifest, saved in its folder (Android). */
        const val MANIFEST_FILE = "manifest.json"

        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

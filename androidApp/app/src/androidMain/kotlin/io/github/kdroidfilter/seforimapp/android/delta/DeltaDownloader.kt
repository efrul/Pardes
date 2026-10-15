package io.github.kdroidfilter.seforimlibrary.deltaupdater

import co.touchlab.kermit.Logger
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Android variant of SeforimLibrary (381fbea) `delta-updater/.../DeltaDownloader.kt`, same contract:
 *  - each file is downloaded next to its target as `<file>.part`; a target already carrying the right sha256 is reused;
 *  - a later attempt resumes from the `.part` (HTTP Range), feeding the saved bytes into the sha256 first;
 *  - a short transfer keeps the `.part` for the next attempt, a sha256 mismatch deletes it.
 *
 * The bytes come from [openStream] (url, size, first byte) instead of a bare HttpURLConnection: the app passes the
 * library installer's ResumingHttpInputStream, which asks for `Accept-Encoding: identity` (Android's client otherwise
 * gzips and breaks Range offsets), reconnects from the last byte when the network drops, skips what a server that
 * ignored Range resends, and trusts the user CAs of network_security_config. [onProgress] gets (bytes, total).
 */
class DeltaDownloader(
    private val logger: Logger = Logger.withTag("DeltaDownloader"),
    private val openStream: (url: String, size: Long, startAt: Long) -> InputStream,
    private val onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    private val checkCancelled: () -> Unit = {},
) {

    fun download(url: String, dest: Path, expectedSha256: String, expectedSize: Long?): Path {
        Files.createDirectories(dest.toAbsolutePath().parent)
        if (Files.exists(dest) && sha256(dest) == expectedSha256.lowercase()) {
            logger.i { "Skipping ${dest.fileName} — sha256 already matches" }
            expectedSize?.let { onProgress(it, it) }
            return dest
        }
        val partial = dest.resolveSibling("${dest.fileName}.part")
        var existing = if (Files.exists(partial)) Files.size(partial) else 0L
        if (expectedSize != null && existing > expectedSize) {
            Files.deleteIfExists(partial)
            existing = 0L
        }
        if (existing > 0) logger.i { "Resuming ${dest.fileName} from byte $existing" }
        val md = MessageDigest.getInstance("SHA-256")
        if (existing > 0) {
            // The already-downloaded bytes go into the digest first.
            Files.newInputStream(partial).use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
        }
        val total = expectedSize ?: -1L
        var written = existing
        onProgress(written, total)
        FileOutputStream(partial.toFile(), existing > 0).use { out ->
            openStream(url, total, existing).use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    checkCancelled()
                    val read = input.read(buf)
                    if (read < 0) break
                    if (read == 0) continue
                    out.write(buf, 0, read)
                    md.update(buf, 0, read)
                    written += read
                    onProgress(written, total)
                }
            }
            out.flush()
            out.fd.sync()
        }
        // "Server hung up early" (keep .part for resume) vs "all bytes received but sha mismatched" (delete .part).
        if (expectedSize != null && written < expectedSize) {
            throw IOException(
                "Truncated download on $url: got $written / $expectedSize bytes — " +
                    ".part kept for resume on next attempt.",
            )
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (actual.lowercase() != expectedSha256.lowercase()) {
            Files.deleteIfExists(partial)
            throw IOException("Sha256 mismatch on $url: expected $expectedSha256, got $actual")
        }
        Files.move(partial, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        logger.i { "Downloaded $url → $dest (${Files.size(dest)} bytes)" }
        return dest
    }

    private fun sha256(path: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BUFFER_SIZE: Int = 1 shl 16
    }
}

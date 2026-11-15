package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlinx.serialization.json.Json
import java.io.File

// Pardes (request 56 of docs/DESKTOP_STAGE2.md): where a tractate's scan cut into images is kept, beside the library.

/**
 * Where the scans' cuts are kept ([DafScanPages]): `pardes-scans` beside the library ([root]), a folder a scan named
 * after its file and a hash of its path (two scans of a tractate may be on the computer), each with its pages
 * (`<page>.png`) and its index (`cut.json`, a [ScanCut]).
 */
internal class ScanCutFolder(
    private val root: () -> File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The folder of every cut. */
    fun all(): File = root()

    /** The folder of [scan]'s cut. */
    fun of(scan: File): File = File(all(), "${scan.nameWithoutExtension.replace(UNSAFE, "_")}-%08x".format(scan.absolutePath.hashCode()))

    /** The image of [scan]'s page [page]. */
    fun page(
        scan: File,
        page: Int,
    ): File = File(of(scan), "$page.png")

    /** [scan]'s cut as its index keeps it, if it is of the file as it is now; a page whose image is gone, not cut. */
    fun read(scan: File): ScanCut? =
        runCatching { json.decodeFromString(ScanCut.serializer(), File(of(scan), INDEX).readText()) }
            .getOrNull()
            ?.takeIf { it.format == ScanCut.FORMAT && it.isOf(scan) && it.heights.size == it.pages }
            ?.let { cut -> cut.copy(heights = cut.heights.mapIndexed { page, h -> if (h > 0 && !page(scan, page).isFile) 0 else h }) }

    /** Keeps [cut] as [scan]'s index. */
    fun write(
        scan: File,
        cut: ScanCut,
    ) {
        runCatching { writeAtomically(File(of(scan), INDEX), json.encodeToString(ScanCut.serializer(), cut).toByteArray()) }
    }

    companion object {
        private const val INDEX = "cut.json"
        private val UNSAFE = Regex("[^\\p{L}\\p{N}_-]")

        /** [bytes] written beside [file], then renamed: a file cut halfway is never read. */
        fun writeAtomically(
            file: File,
            bytes: ByteArray,
        ) {
            file.parentFile.mkdirs()
            val part = File(file.parentFile, "${file.name}.part")
            part.writeBytes(bytes)
            if (!part.renameTo(file)) {
                file.delete()
                part.renameTo(file)
            }
        }
    }
}

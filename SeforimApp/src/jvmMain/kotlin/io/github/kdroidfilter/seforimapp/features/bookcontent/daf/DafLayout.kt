package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The Vilna page (צורת הדף) of a tractate: for every amud, its lines of Gemara, Rashi and Tosafot, each with its
 * words as printed and its box on the page, in PDF points (the page's top left at 0,0). Made by pardes-rashi-ocr's
 * export_layout.py from Otzaria's Talmud scans (the lines' places) and the texts of the library (their words). In
 * format 2, every line also names the library's lines its words belong to ([DafLine.u]), and [library] the library
 * release those ids are of.
 */
@Serializable
data class DafLayoutFile(
    val format: Int,
    val tractate: String,
    val tractateHe: String,
    val amudim: List<DafAmud>,
    val library: DafLibrary? = null,
)

/** The library a format-2 file's line ids are of: its [release] (release_info.txt) and its books, by column. */
@Serializable
data class DafLibrary(
    val release: String? = null,
    val books: Map<String, List<Long>> = emptyMap(),
)

/** One amud: [page] its size, [block] the box of its Gemara, Rashi and Tosafot (the margins are not drawn). */
@Serializable
data class DafAmud(
    val amud: String,
    val page: List<Float>,
    val block: List<Float>,
    val lines: List<DafLine>,
)

/**
 * A printed line: [c] its column (G the Gemara, R Rashi, T Tosafot), [b] its box (x0, y0, x1, y1), [t] its words as
 * read, right to left, [s] 1 when the alignment is sure of its first and last words, [d] the indices of the words of
 * [t] that are a dibbur hamatchil, bold as printed, and [u] (format 2) the library's lines its words belong to:
 * `[line id, first word, last word]`, the words counted in [t] from 0. A line often holds the end of one comment and
 * the start of the next; a Gemara line, of one segment or two. [big] is 1 for a column's first word printed big, two
 * lines tall (each amud's Rashi and Tosafot start with one, as a chapter does): a line of its own, in its box.
 */
@Serializable
data class DafLine(
    val c: String,
    val b: List<Float>,
    val t: String,
    val s: Int = 1,
    val d: List<Int> = emptyList(),
    val u: List<List<Long>> = emptyList(),
    val big: Int = 0,
) {
    val isGemara: Boolean get() = c == "G"

    val isBig: Boolean get() = big == 1

    /** The library line the word [index] of [t] belongs to, or null (a format-1 file, or a word of none). */
    fun lineIdAt(index: Int): Long? = u.firstOrNull { it.size == SPAN_SIZE && index.toLong() in it[1]..it[2] }?.get(0)

    /** The library lines of this line's words, in the order they are printed. */
    val lineIds: List<Long> get() = u.mapNotNull { it.firstOrNull() }.distinct()

    private companion object {
        const val SPAN_SIZE = 3
    }
}

/**
 * The layout files of a folder, one per tractate whatever its file's name: each file's header names its tractate, in
 * English (Sefaria's) and in Hebrew. The folder is looked at again when a file is put in or taken out, and the last
 * few tractates read are kept.
 */
class DafLayouts(
    private val dir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var scannedAt = Long.MIN_VALUE
    private var files: Map<String, File> = emptyMap()
    private val read =
        object : LinkedHashMap<File, Pair<Long, DafLayoutFile>>(KEPT + 1, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, Pair<Long, DafLayoutFile>>?) = size > KEPT
        }

    /** Whether there is a layout for the tractate named [name], in English or in Hebrew. */
    @Synchronized
    fun has(name: String): Boolean = fileFor(name) != null

    /** The tractate named [name], in English or in Hebrew, or null when no file is for it or it cannot be read. */
    @Synchronized
    fun tractate(name: String): DafLayoutFile? {
        val file = fileFor(name) ?: return null
        val modified = file.lastModified()
        read[file]?.takeIf { it.first == modified }?.let { return it.second }
        return parse(file)?.also { read[file] = modified to it }
    }

    /**
     * Copies [source] into the folder when it is a layout file, under its tractate's English name, in place of the
     * tractate's former file; the tractate, or null when [source] is not a layout file.
     */
    @Synchronized
    fun import(source: File): DafLayoutFile? {
        val layout = parse(source) ?: return null
        // The tractate's former file, under another name, would be found before the new one
        val former = fileFor(layout.tractate) ?: fileFor(layout.tractateHe)
        dir.mkdirs()
        val target = File(dir, "${layout.tractate.replace(UNSAFE_NAME, "_")}.json").absoluteFile
        val part = File(dir, "${target.name}.part")
        source.copyTo(part, overwrite = true)
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        former?.absoluteFile?.takeIf { it != target }?.delete()
        scannedAt = Long.MIN_VALUE
        return layout
    }

    private fun fileFor(name: String): File? {
        val modified = dir.lastModified()
        if (modified != scannedAt) {
            scannedAt = modified
            files = scan()
        }
        return files[key(name)]
    }

    /** Every tractate's file, by its names: read from the head of each file, or the whole file when not there. */
    private fun scan(): Map<String, File> {
        val found = HashMap<String, File>()
        val jsons = dir.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty().sortedBy { it.name }
        for (file in jsons) {
            val names = headerNames(file) ?: parse(file)?.let { listOf(it.tractate, it.tractateHe) } ?: continue
            for (name in names) found.putIfAbsent(key(name), file)
        }
        return found
    }

    private fun headerNames(file: File): List<String>? {
        val head =
            runCatching {
                file.inputStream().use { input -> String(input.readNBytes(HEAD_BYTES), Charsets.UTF_8) }
            }.getOrNull() ?: return null
        val english = TRACTATE.find(head)?.groupValues?.get(1) ?: return null
        val hebrew = TRACTATE_HE.find(head)?.groupValues?.get(1) ?: return null
        return runCatching { listOf(english, hebrew).map { json.decodeFromString<String>("\"$it\"") } }.getOrNull()
    }

    private fun parse(file: File): DafLayoutFile? =
        runCatching { json.decodeFromString(DafLayoutFile.serializer(), file.readText()) }
            .getOrNull()
            ?.takeIf { it.format in FORMATS }

    companion object {
        /** The folder of the layouts, next to the library. */
        const val DIR_NAME = "pardes-daf"

        /** The formats this app reads (export_layout.py's): 2 adds the library's lines to 1. */
        val FORMATS = 1..2

        private const val KEPT = 3
        private const val LOAD_FACTOR = 0.75f
        private const val HEAD_BYTES = 4096
        private val TRACTATE = Regex("\"tractate\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val TRACTATE_HE = Regex("\"tractateHe\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val UNSAFE_NAME = Regex("[^A-Za-z0-9 _-]")
        private val shared = ConcurrentHashMap<File, DafLayouts>()

        /** The layouts of the library in [libraryDir] (its [DIR_NAME] folder), one instance a folder. */
        fun of(libraryDir: File): DafLayouts = File(libraryDir, DIR_NAME).absoluteFile.let { shared.getOrPut(it) { DafLayouts(it) } }

        /** Names compare without quotes, nikud, punctuation, extra spaces or case (`עבודה זרה`, `Avodah Zarah`). */
        private fun key(name: String): String = ReferenceParser.normalizeName(name).lowercase()
    }
}

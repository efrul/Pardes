package io.github.kdroidfilter.seforimapp.core.presentation.typography

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.FontMgr
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A font the user added ([UserFonts]): its code in the settings, its family's name, and whether its file is there. */
data class UserFont(
    val code: String,
    val name: String,
    val present: Boolean = true,
)

/**
 * The fonts the user added from a file (TTF or OTF), copied into the app's data folder (fonts/) and offered in every list
 * of fonts beside the catalog's ([FontCatalog]). Each is the code `user:<file name>` in the settings; a code whose file
 * is gone is shown in the default font ([familyFor] null, FontCatalog's own fallback) and listed as missing. The files
 * go into the user's backup ([writeTo]) and come back with it ([restoreFrom]).
 */
class UserFonts(
    private val dir: File,
) {
    private val _fonts = MutableStateFlow(read())

    /** The fonts in the folder, by name. */
    val fonts: StateFlow<List<UserFont>> = _fonts.asStateFlow()

    private val families = ConcurrentHashMap<String, FontFamily>()

    /** What [add] made of a file: the font added, and whether it has Hebrew letters. */
    data class Added(
        val font: UserFont,
        val hebrew: Boolean,
    )

    /** [source] copied into the folder as a font, under a name of its own; throws when it is not a font that loads. */
    fun add(source: File): Added {
        require(source.extension.lowercase() in EXTENSIONS) { "Not a TTF or OTF file" }
        val typeface = FontMgr.default.makeFromFile(source.path) ?: throw IllegalArgumentException("Not a font")
        val hebrew = typeface.getUTF32Glyph(ALEF) != 0.toShort()
        dir.mkdirs()
        // Another font of the same file name keeps its own: -2, -3…
        val target = generateSequence(1) { it + 1 }.map { n -> File(dir, nameFor(source, n)) }.first { !it.exists() }
        source.copyTo(target)
        _fonts.value = read()
        return Added(UserFont(CODE_PREFIX + target.name, typeface.familyName.ifBlank { target.nameWithoutExtension }), hebrew)
    }

    /** The font of [code] taken out of the folder; a setting that still names it shows the default font. */
    fun remove(code: String) {
        fileOf(code)?.delete()
        families.remove(code)
        _fonts.value = read()
    }

    /** The family of a user font's [code]; null for a code that is not one, or whose file is gone. */
    fun familyFor(code: String): FontFamily? {
        val file = fileOf(code)?.takeIf { it.isFile } ?: return null
        return families.getOrPut(code) { FontFamily(Font(file, FontWeight.Normal)) }
    }

    /** [code]'s font as the lists show it: one of the folder's, or one a setting names whose file is gone. */
    fun fontOf(code: String): UserFont? =
        if (!isUserCode(code)) {
            null
        } else {
            _fonts.value.firstOrNull { it.code == code }
                ?: UserFont(code, code.removePrefix(CODE_PREFIX), present = false)
        }

    /** The folder's fonts written into the backup archive [zip] (fonts/<file name>). */
    fun writeTo(zip: ZipOutputStream) {
        files().forEach { file ->
            zip.putNextEntry(ZipEntry(ARCHIVE_DIR + file.name))
            file.inputStream().use { it.copyTo(zip) }
        }
    }

    /** The fonts of a backup [archive] put back in the folder, beside those it has. */
    fun restoreFrom(archive: ByteArray) {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name.removePrefix(ARCHIVE_DIR)
                if (entry.name.startsWith(ARCHIVE_DIR) && '/' !in name && File(name).extension.lowercase() in EXTENSIONS) {
                    dir.mkdirs()
                    File(dir, name).outputStream().use { zip.copyTo(it) }
                }
            }
        }
        _fonts.value = read()
    }

    private fun nameFor(
        source: File,
        n: Int,
    ): String = if (n == 1) source.name else "${source.nameWithoutExtension}-$n.${source.extension}"

    private fun fileOf(code: String): File? = if (isUserCode(code)) File(dir, code.removePrefix(CODE_PREFIX)) else null

    private fun files(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }.orEmpty().sortedBy { it.name }

    private fun read(): List<UserFont> =
        files()
            .map { file ->
                val name = runCatching { FontMgr.default.makeFromFile(file.path)?.familyName }.getOrNull()
                UserFont(CODE_PREFIX + file.name, name?.ifBlank { null } ?: file.nameWithoutExtension)
            }.sortedBy { it.name }

    companion object {
        const val CODE_PREFIX = "user:"
        private const val ARCHIVE_DIR = "fonts/"
        private val EXTENSIONS = setOf("ttf", "otf")
        private const val ALEF = 0x05D0

        /** Whether a font [code] of the settings is a font the user added. */
        fun isUserCode(code: String): Boolean = code.startsWith(CODE_PREFIX)

        /** The app's own, in its data folder. */
        val shared: UserFonts by lazy { UserFonts(File(FileKit.filesDir.path, "fonts")) }

        /** [shared], or null where the app's data folder is not known (FileKit not set up: a test). */
        fun sharedOrNull(): UserFonts? = runCatching { shared }.getOrNull()
    }
}

package io.github.kdroidfilter.seforimapp.core.presentation.typography

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fonts the user adds from a file ([UserFonts]): added, listed, offered, taken out, and carried by the backup. */
class UserFontsTest {
    private val root = createTempDirectory("user-fonts").toFile()
    private val fonts = File("src/commonMain/composeResources/font")

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a font file is added under a name of its own, listed, and offered by its code`() {
        val userFonts = UserFonts(File(root, "fonts"))

        val added = userFonts.add(File(fonts, "notorashihebrew.ttf"))
        assertTrue(added.hebrew)
        assertEquals("user:notorashihebrew.ttf", added.font.code)
        val again = userFonts.add(File(fonts, "notorashihebrew.ttf"))
        assertEquals("user:notorashihebrew-2.ttf", again.font.code, "the same file twice is two files")

        assertEquals(2, userFonts.fonts.value.size)
        assertNotNull(userFonts.familyFor(added.font.code))
        assertNull(userFonts.familyFor("notoserifhebrew"), "the catalog's own codes are not the user's")
    }

    @Test
    fun `a font without Hebrew letters is added and said to be so, and a file that is not a font is refused`() {
        val userFonts = UserFonts(File(root, "fonts"))

        assertFalse(userFonts.add(File(fonts, "CaladingsCLM.otf")).hebrew)
        val notFont = File(root, "notes.ttf").apply { writeText("not a font") }
        assertFailsWith<IllegalArgumentException> { userFonts.add(notFont) }
        assertFailsWith<IllegalArgumentException> { userFonts.add(File(root, "notes.txt").apply { writeText("x") }) }
    }

    @Test
    fun `a font taken out, or whose file is gone, is listed as missing and shown in the default font`() {
        val userFonts = UserFonts(File(root, "fonts"))
        val code = userFonts.add(File(fonts, "notorashihebrew.ttf")).font.code

        userFonts.remove(code)

        assertTrue(userFonts.fonts.value.isEmpty())
        assertNull(userFonts.familyFor(code))
        assertEquals(UserFont(code, "notorashihebrew.ttf", present = false), userFonts.fontOf(code))
        assertNull(userFonts.fontOf("cardo"))
    }

    @Test
    fun `the fonts go into a backup and come back from it`() {
        val mine = UserFonts(File(root, "mine"))
        mine.add(File(fonts, "notorashihebrew.ttf"))
        val archive = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use(mine::writeTo) }.toByteArray()

        val other = UserFonts(File(root, "other"))
        other.restoreFrom(archive)

        assertEquals(mine.fonts.value, other.fonts.value)
        assertEquals(File(fonts, "notorashihebrew.ttf").length(), File(root, "other/notorashihebrew.ttf").length())
    }
}

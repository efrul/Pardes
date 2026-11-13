package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The tractates' scans: Otzaria's, found in the library its data folder points at, and the files the user links. */
class DafScansTest {
    private val root = createTempDirectory("daf-scans").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    // Otzaria's data folder, its pointer file (UTF-8 with a byte order mark, as Otzaria writes it) naming [library]
    private fun dataFolder(library: File): File =
        File(root, "data").apply {
            mkdirs()
            File(this, "library_path.txt").writeText(BYTE_ORDER_MARK + library.path + "\r\n")
        }

    private fun scan(path: String): File = File(root, path).apply { parentFile.mkdirs() }.apply { writeText("%PDF") }

    @Test
    fun `a tractate's scan is found in its order's folder of the library Otzaria points at`() {
        val berakhot = scan("library/אוצריא/תלמוד בבלי/סדר זרעים/ברכות.pdf")
        val shabbat = scan("library/אוצריא/תלמוד בבלי/סדר מועד/שבת.pdf")
        scan("library/אוצריא/תלמוד בבלי/סדר זרעים/ברכות.txt")

        val bavli = OtzariaScans.bavliFolders(listOf(dataFolder(File(root, "library"))))

        assertEquals(listOf(File(root, "library/אוצריא/תלמוד בבלי")), bavli)
        assertEquals(berakhot, OtzariaScans.find("ברכות", bavli))
        assertEquals(shabbat, OtzariaScans.find("שבת", bavli))
        assertNull(OtzariaScans.find("עירובין", bavli), "a tractate without a scan")
    }

    @Test
    fun `the pointer may name the library's own אוצריא folder, and a scan may sit right in the Bavli's`() {
        val berakhot = scan("library/אוצריא/תלמוד בבלי/ברכות.pdf")

        val bavli = OtzariaScans.bavliFolders(listOf(dataFolder(File(root, "library/אוצריא"))))

        assertEquals(berakhot, OtzariaScans.find("ברכות", bavli))
    }

    @Test
    fun `without Otzaria there is no scan`() {
        assertEquals(emptyList(), OtzariaScans.bavliFolders(listOf(File(root, "nothing"))))
        assertNull(OtzariaScans.find("ברכות", emptyList()))
    }

    @Test
    fun `a file linked to a tractate is kept, and let go`() =
        runBlocking {
            val scans = DafScans(UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)))
            val file = scan("mine/ברכות וילנא.pdf")

            scans.link("ברכות", file)
            assertEquals(file.absoluteFile, scans.linked("ברכות"))
            assertNull(scans.linked("שבת"))

            scans.unlink("ברכות")
            assertNull(scans.linked("ברכות"))
        }

    private companion object {
        val BYTE_ORDER_MARK = Char(0xFEFF).toString()
    }
}

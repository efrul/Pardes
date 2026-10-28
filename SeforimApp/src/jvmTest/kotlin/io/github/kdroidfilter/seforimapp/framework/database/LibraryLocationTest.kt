package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.branding.BrandInfo
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.vinceglb.filekit.FileKit
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryLocationTest {
    private val root = createTempDirectory().toFile()
    private val appSettings = testAppSettings()

    @BeforeTest
    fun setUp() {
        // This app's own data folder in a temporary directory, never the user's
        FileKit.init(appId = TEST_APP_ID, filesDir = File(root, "files"), cacheDir = File(root, "cache"))
    }

    @AfterTest
    fun tearDown() {
        FileKit.init(TEST_APP_ID)
        root.deleteRecursively()
    }

    @Test
    fun `a chosen folder holds the library in a folder of its own`() {
        assertEquals(LibraryLocation.defaultDir(), LibraryLocation.destination(LibraryLocation.defaultDir()))
        val chosen = File(root, "D")
        assertEquals(File(chosen, BrandInfo.LIBRARY_FOLDER_NAME).absoluteFile, LibraryLocation.destination(chosen))
        val named = File(chosen, BrandInfo.LIBRARY_FOLDER_NAME)
        assertEquals(named.absoluteFile, LibraryLocation.destination(named))
    }

    @Test
    fun `a move on the same drive renames, points the app at the new folder and empties the old one`() {
        val from = libraryIn(LibraryLocation.defaultDir())
        val to = LibraryLocation.destination(File(root, "D"))
        var last = 0L to 0L

        val result = LibraryLocation.move(from, to, keep = false, appSettings, progress = { d, t -> last = d to t }, stopped = { false })

        assertTrue(result.sameDrive)
        assertFalse(result.keptCopy)
        assertTrue(LibraryLocation.exists(to))
        assertEquals(DB_TEXT, File(to, "seforim.db").readText())
        assertTrue(File(to, "seforim.db.lucene/segments_1").isFile)
        assertFalse(File(from, "seforim.db").exists())
        assertFalse(File(from, "seforim.db-shm").exists())
        assertEquals(File(to, "seforim.db").absolutePath, appSettings.getDatabasePath())
        assertEquals(to, LibraryLocation.installDir(appSettings))
        assertEquals(to, LibraryLocation.currentDir(appSettings))
        assertEquals(last.second, last.first)
    }

    @Test
    fun `the semantic search's model and vector index go with the library, moved or copied`() {
        val from = libraryIn(LibraryLocation.defaultDir()).withSemanticSearch()
        val moved = LibraryLocation.destination(File(root, "D"))
        LibraryLocation.move(from, moved, keep = false, appSettings, progress = { _, _ -> }, stopped = { false })
        assertTrue(File(moved, "pardes-model/model.int8.onnx").isFile)
        assertTrue(File(moved, "pardes.vec.lucene/segments_1").isFile)
        assertFalse(File(from, "pardes-model").exists())

        val copied = LibraryLocation.destination(File(root, "E"))
        LibraryLocation.move(moved, copied, keep = true, appSettings, progress = { _, _ -> }, stopped = { false })
        assertEquals("model", File(copied, "pardes-model/model.int8.onnx").readText())
        assertEquals("vectors", File(copied, "pardes.vec.lucene/segments_1").readText())
        assertTrue(File(moved, "pardes.vec.lucene/segments_1").isFile)
    }

    @Test
    fun `a kept copy can be switched back to, and deleted once not in use`() {
        val from = libraryIn(LibraryLocation.defaultDir())
        val to = LibraryLocation.destination(File(root, "D"))

        val result = LibraryLocation.move(from, to, keep = true, appSettings, progress = { _, _ -> }, stopped = { false })

        assertTrue(result.keptCopy)
        assertEquals(LibraryLocation.size(from), LibraryLocation.size(to))
        assertEquals(listOf(from), LibraryLocation.copies(appSettings))
        assertTrue(LibraryLocation.isKeptCopy(from))

        LibraryLocation.switchTo(from, appSettings)
        assertEquals(from, LibraryLocation.currentDir(appSettings))
        assertNull(appSettings.getLibraryInstallDir())
        assertEquals(listOf(to), LibraryLocation.copies(appSettings))

        assertEquals(listOf(from.path), LibraryLocation.deleteCopy(from, appSettings))
        assertEquals(emptyList(), LibraryLocation.deleteCopy(to, appSettings))
        assertFalse(to.exists())
        assertTrue(LibraryLocation.copies(appSettings).isEmpty())
    }

    @Test
    fun `a stopped copy leaves nothing behind and changes nothing`() {
        val from = libraryIn(LibraryLocation.defaultDir())
        val to = LibraryLocation.destination(File(root, "D"))

        assertFailsWith<LibraryLocation.MoveStopped> {
            LibraryLocation.move(from, to, keep = true, appSettings, progress = { _, _ -> }, stopped = { true })
        }

        assertFalse(to.exists())
        assertTrue(LibraryLocation.exists(from))
        assertNull(appSettings.getDatabasePath())
        assertTrue(LibraryLocation.copies(appSettings).isEmpty())
    }

    @Test
    fun `a move that cannot be is refused before anything is touched`() {
        val from = libraryIn(LibraryLocation.defaultDir())
        val occupied = libraryIn(File(root, "E/${BrandInfo.LIBRARY_FOLDER_NAME}"))

        assertEquals(LibraryLocation.Problem.AlreadyThere, LibraryLocation.check(from, from, keep = false))
        assertEquals(LibraryLocation.Problem.IntoItself, LibraryLocation.check(from, File(from, "inner"), keep = false))
        assertIs<LibraryLocation.Problem.LibraryThere>(LibraryLocation.check(from, occupied, keep = false))
        assertEquals(LibraryLocation.Problem.NoLibrary, LibraryLocation.check(File(root, "empty"), occupied, keep = false))
        assertNull(LibraryLocation.check(from, LibraryLocation.destination(File(root, "D")), keep = false))
    }

    @Test
    fun `a move the app was killed in is undone, or finished when the app already points at the new folder`() {
        val from = libraryIn(LibraryLocation.defaultDir())
        val to = File(root, "D/${BrandInfo.LIBRARY_FOLDER_NAME}").apply { mkdirs() }
        // A rename cut short: seforim.db went, the rest did not
        File(from, "seforim.db").renameTo(File(to, "seforim.db"))
        File(from, "catalog.pb").copyTo(File(to, "catalog.pb"))

        assertFalse(LibraryLocation.undo(from, to, keep = false, appSettings))
        assertTrue(LibraryLocation.exists(from))
        assertEquals(DB_TEXT, File(from, "seforim.db").readText())
        assertFalse(to.exists())

        val copied = libraryIn(to)
        appSettings.setDatabasePath(File(copied, "seforim.db").path)
        assertTrue(LibraryLocation.undo(from, to, keep = false, appSettings))
        assertFalse(File(from, "seforim.db").exists())
        assertTrue(LibraryLocation.exists(to))
    }

    @Test
    fun `an install folder holding a library is refused`() {
        val chosen = File(root, "D")
        assertNull(LibraryLocation.chooseInstallDir(chosen, appSettings))
        assertEquals(LibraryLocation.destination(chosen), LibraryLocation.installDir(appSettings))

        libraryIn(File(root, "E/${BrandInfo.LIBRARY_FOLDER_NAME}"))
        assertIs<LibraryLocation.Problem.LibraryThere>(LibraryLocation.chooseInstallDir(File(root, "E"), appSettings))
        assertEquals(LibraryLocation.destination(chosen), LibraryLocation.installDir(appSettings))

        assertNull(LibraryLocation.chooseInstallDir(LibraryLocation.defaultDir(), appSettings))
        assertNull(appSettings.getLibraryInstallDir())
    }

    private fun libraryIn(dir: File): File =
        dir.apply {
            mkdirs()
            File(this, "seforim.db").writeText(DB_TEXT)
            File(this, "seforim.db-shm").writeText("shm")
            File(this, "catalog.pb").writeText("catalog")
            File(this, "release_info.txt").writeText(VERSION)
            File(this, "seforim.db.lucene").mkdirs()
            File(this, "seforim.db.lucene/segments_1").writeText("index")
        }

    private fun File.withSemanticSearch(): File =
        apply {
            File(this, "pardes-model").mkdirs()
            File(this, "pardes-model/model.int8.onnx").writeText("model")
            File(this, "pardes.vec.lucene").mkdirs()
            File(this, "pardes.vec.lucene/segments_1").writeText("vectors")
        }

    private companion object {
        const val TEST_APP_ID = "seforimapp-tests"
        const val VERSION = "20261012193827"
        const val DB_TEXT = "the library's database"
    }
}

package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForeignLibrariesTest {
    private val root = createTempDirectory().toFile()
    private val appSettings = testAppSettings()
    private val databasePathProvider = DatabasePathProvider(appSettings)

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
    fun `a library used in place is foreign, our own folder and other folders are not`() {
        val other = libraryIn(File(root, "other app/databases"))
        assertFalse(ForeignLibraries.isForeign(other))

        useInPlace(other)

        assertEquals(File(other, "seforim.db").absolutePath, appSettings.getDatabasePath())
        assertTrue(ForeignLibraries.isForeign(other))
        assertTrue(ForeignLibraries.isForeign(File(other, "../databases")))
        assertFalse(ForeignLibraries.isForeign(File(FileKit.databasesDir.path)))
        assertFalse(ForeignLibraries.isForeign(File(root, "elsewhere")))
        assertFalse(ForeignLibraries.isForeign(null))
    }

    @Test
    fun `a full install's cleanup leaves a library used in place alone and empties our own folder`() {
        val other = libraryIn(File(root, "other app/databases"))
        val own = libraryIn(File(FileKit.databasesDir.path))
        useInPlace(other)

        val result = runBlocking { DatabaseCleanupUseCase(databasePathProvider, appSettings).cleanupDatabaseFiles() }

        assertIs<DatabaseCleanupUseCase.CleanupResult.Success>(result)
        assertTrue(File(other, "seforim.db").exists())
        assertTrue(File(other, "release_info.txt").exists())
        assertFalse(File(own, "seforim.db").exists())
        assertFalse(File(own, "release_info.txt").exists())
        assertNull(appSettings.getDatabasePath())
    }

    @Test
    fun `a folder the user chooses holds a library to use, in it or in the library's own folder inside it`() {
        val chosen = wholeLibraryIn(File(root, "drive/books"))

        val found = assertIs<ForeignLibraries.InFolder.Library>(ForeignLibraries.inFolder(chosen))
        assertEquals(File(chosen, "seforim.db"), found.library.database)
        assertEquals(VERSION, found.library.version)

        // Where an install to a chosen folder puts the library
        val outer = File(root, "drive2")
        val inside = wholeLibraryIn(LibraryLocation.destination(outer))
        val inOuter = assertIs<ForeignLibraries.InFolder.Library>(ForeignLibraries.inFolder(outer))
        assertEquals(File(inside, "seforim.db"), inOuter.library.database)

        ForeignLibraries.useInPlace(found.library, appSettings, databasePathProvider)
        assertEquals(File(chosen, "seforim.db").absolutePath, appSettings.getDatabasePath())
        assertTrue(ForeignLibraries.isForeign(chosen))
    }

    @Test
    fun `a folder with no library this app can open says why`() {
        assertEquals(ForeignLibraries.InFolder.NoLibrary, ForeignLibraries.inFolder(File(root, "empty").apply { mkdirs() }))

        // A copy of seforim.db alone
        assertEquals(ForeignLibraries.InFolder.NoSearchIndex, ForeignLibraries.inFolder(libraryIn(File(root, "one file"))))

        val pieces = wholeLibraryIn(File(root, "phone card")).also { File(it, "seforim.db001").writeText("") }
        assertEquals(ForeignLibraries.InFolder.InPieces, ForeignLibraries.inFolder(pieces))

        val unknown = wholeLibraryIn(File(root, "no release")).also { File(it, "release_info.txt").delete() }
        assertEquals(ForeignLibraries.InFolder.UnknownRelease, ForeignLibraries.inFolder(unknown))

        val old = wholeLibraryIn(File(root, "old")).also { File(it, "release_info.txt").writeText(OLD_VERSION) }
        assertEquals(ForeignLibraries.InFolder.TooOld(OLD_VERSION), ForeignLibraries.inFolder(old))
    }

    private fun useInPlace(dir: File) =
        ForeignLibraries.useInPlace(
            ForeignLibraries.ForeignLibrary(File(dir, "seforim.db"), VERSION, "other app"),
            appSettings,
            databasePathProvider,
        )

    private fun libraryIn(dir: File): File =
        dir.apply {
            mkdirs()
            File(this, "seforim.db").writeText("")
            File(this, "release_info.txt").writeText(VERSION)
        }

    // A library with its search index, as an install leaves it
    private fun wholeLibraryIn(dir: File): File = libraryIn(dir).also { File(it, "seforim.db.lucene").mkdirs() }

    private companion object {
        const val TEST_APP_ID = "seforimapp-tests"
        const val VERSION = "20261012193827"
        const val OLD_VERSION = "20250101000000"
    }
}

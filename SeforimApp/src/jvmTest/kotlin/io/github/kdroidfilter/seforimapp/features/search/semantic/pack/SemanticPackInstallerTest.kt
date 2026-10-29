package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticPackInstallerTest {
    private val root = createTempDirectory().toFile()
    private val library = PackFixtures.library(File(root, "library"))
    private val installer = SemanticPackInstaller(library)
    private val source = PackFixtures.folders(File(root, "source"))

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
    fun `a bundle in parts with its manifest is unpacked, checked and marked as the pack's`() {
        val downloads = PackFixtures.bundle(source, File(root, "downloads"))
        val bundle = assertIs<SemanticPackInstaller.Source.Bundle>(installer.resolve(File(downloads, SemanticPack.MANIFEST_NAME)))
        assertEquals(2, bundle.files.size)
        assertEquals(2, bundle.info?.pack)

        val staged = runBlocking { installer.stage(bundle) }

        assertEquals(SemanticPackInstaller.Staged(bundle.info, PackFixtures.MODEL, 2, PackFixtures.LIBRARY_TAG), staged)
        val unpacked = PackFixtures.contents(installer.stagingDir)
        for ((path, hash) in PackFixtures.contents(source)) assertEquals(hash, unpacked[path], path)
        assertEquals(bundle.info, PackInfo.parseOrNull(File(installer.stagingDir, "pardes-model/${SemanticPack.INFO_NAME}").readText()))
        for (name in SemanticPack.FOLDERS) assertTrue(File(installer.stagingDir, "$name/${SemanticPackInstaller.STAGED_MARK}").isFile)
        // The user's files stay as they are
        assertTrue(bundle.files.all { it.isFile })
    }

    @Test
    fun `any part finds the rest, and the bundle whole is read too`() {
        val downloads = PackFixtures.bundle(source, File(root, "downloads"), parts = 3)
        val fromPart = installer.resolve(File(downloads, "${SemanticPack.BUNDLE_NAME}.part02"))
        assertEquals(installer.resolve(File(downloads, SemanticPack.MANIFEST_NAME)), fromPart)
        assertEquals(3, (fromPart as SemanticPackInstaller.Source.Bundle).files.size)

        val whole = PackFixtures.bundle(source, File(root, "whole"), parts = 1)
        val single = assertIs<SemanticPackInstaller.Source.Bundle>(installer.resolve(File(whole, SemanticPack.BUNDLE_NAME)))
        assertEquals(listOf(File(whole, SemanticPack.BUNDLE_NAME).absoluteFile), single.files)
        runBlocking { installer.stage(single) }
        assertTrue(File(installer.stagingDir, "pardes.vec.lucene").isDirectory)
    }

    @Test
    fun `a damaged part is refused and nothing stays staged`() {
        val downloads = PackFixtures.bundle(source, File(root, "downloads"))
        val part = File(downloads, "${SemanticPack.BUNDLE_NAME}.part02")
        val bytes = part.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        part.writeBytes(bytes)

        val problem = assertFailsWith<PackProblem.Corrupted> { runBlocking { installer.stage(installer.resolve(part)) } }
        assertEquals(part.name, problem.name)
        assertFalse(installer.stagingDir.exists())
    }

    @Test
    fun `a part missing from beside the manifest is named`() {
        val downloads = PackFixtures.bundle(source, File(root, "downloads"))
        File(downloads, "${SemanticPack.BUNDLE_NAME}.part02").delete()

        val missing = assertFailsWith<PackProblem.Missing> { installer.resolve(File(downloads, SemanticPack.MANIFEST_NAME)) }
        assertEquals("${SemanticPack.BUNDLE_NAME}.part02", missing.name)
    }

    @Test
    fun `zips a browser renamed are found by what they hold`() {
        val downloads = File(root, "downloads")
        val model = PackFixtures.zip(source, SemanticPack.MODEL_DIR, downloads, "pardes-model (1).zip")
        val index = PackFixtures.zip(source, SemanticPack.INDEX_DIR, downloads, "pardes.vec.lucene (2).zip")
        // Another zip beside them, whose name starts the same
        PackFixtures.zip(source, SemanticPack.MODEL_DIR, downloads, "pardes.vec.lucene notes.zip")
        File(downloads, "pardes.vec.lucene notes.zip").setLastModified(System.currentTimeMillis() + 60_000)

        val zips = installer.resolve(model)
        assertEquals(SemanticPackInstaller.Source.Zips(model.absoluteFile, index), zips)
        assertEquals(zips, installer.resolve(index))

        val staged = runBlocking { installer.stage(zips) }
        assertEquals(null, staged.info)
        assertEquals(PackFixtures.LIBRARY, staged.library)
        val unpacked = PackFixtures.contents(installer.stagingDir)
        for ((path, hash) in PackFixtures.contents(source)) assertEquals(hash, unpacked[path], path)
    }

    @Test
    fun `the zips' copy of the manifest says which pack they are`() {
        val withInfo = File(root, "with-info").also { source.copyRecursively(it) }
        val info = PackInfo(3, PackFixtures.MODEL, PackFixtures.DIMENSION, PackFixtures.LIBRARY_TAG, 2)
        File(withInfo, "pardes-model/${SemanticPack.INFO_NAME}").writeText(info.toJson())
        val downloads = File(root, "downloads")
        val model = PackFixtures.zip(withInfo, SemanticPack.MODEL_DIR, downloads, SemanticPack.MODEL_ZIP)
        PackFixtures.zip(withInfo, SemanticPack.INDEX_DIR, downloads, SemanticPack.INDEX_ZIP)

        assertEquals(info, runBlocking { installer.stage(installer.resolve(model)) }.info)
    }

    @Test
    fun `a zip without the other beside it is named`() {
        val model = PackFixtures.zip(source, SemanticPack.MODEL_DIR, File(root, "downloads"), SemanticPack.MODEL_ZIP)

        assertEquals(SemanticPack.INDEX_ZIP, assertFailsWith<PackProblem.Missing> { installer.resolve(model) }.name)
    }

    @Test
    fun `unzipped folders are copied, the originals left`() {
        val folders = installer.resolve(File(source, "pardes-model/pardes-model.json"))
        assertIs<SemanticPackInstaller.Source.Folders>(folders)

        runBlocking { installer.stage(folders) }

        assertTrue(File(source, "pardes-model/model.int8.onnx").isFile)
        assertEquals(PackFixtures.contents(source).keys, PackFixtures.contents(installer.stagingDir).keys - marks())
    }

    @Test
    fun `a pack built on another library release is staged`() {
        // SeforimLibrary keeps a line's id from release to release: the pack works on this library too
        PackFixtures.library(library, release = "20261111000000")
        val downloads = PackFixtures.bundle(source, File(root, "downloads"))

        val staged = runBlocking { installer.stage(installer.resolve(File(downloads, SemanticPack.MANIFEST_NAME))) }

        assertEquals(PackFixtures.LIBRARY_TAG, staged.library)
        // The pack as the bundle has it, with what its manifest says about it
        assertEquals(
            PackFixtures.contents(source).keys + "${SemanticPack.MODEL_DIR}/${SemanticPack.INFO_NAME}",
            PackFixtures.contents(installer.stagingDir).keys - marks(),
        )
    }

    @Test
    fun `an index of another model is refused`() {
        val other = PackFixtures.folders(File(root, "other"), indexModel = "student@other/pardes-clean-1")

        assertFailsWith<PackProblem.Unusable> {
            runBlocking { installer.stage(installer.resolve(File(other, "pardes-model/pardes-model.json"))) }
        }
    }

    @Test
    fun `a file outside the pack's folders is refused`() {
        for (name in listOf("../escaped.txt", "other/file.txt")) {
            val downloads = PackFixtures.bundle(source, File(root, "downloads-${name.length}"), extra = listOf(name))

            val problem =
                assertFailsWith<PackProblem.NotAPack> {
                    runBlocking { installer.stage(installer.resolve(File(downloads, SemanticPack.MANIFEST_NAME))) }
                }
            assertEquals(name, problem.name)
            assertFalse(File(root, "escaped.txt").exists())
            assertFalse(File(library, "escaped.txt").exists())
        }
    }

    @Test
    fun `a file of no pack is refused`() {
        val notes = File(root, "notes.txt").apply { writeText("notes") }

        assertEquals("notes.txt", assertFailsWith<PackProblem.NotAPack> { installer.resolve(notes) }.name)
    }

    @Test
    fun `staging schedules the pack for the next start`() {
        val downloads = PackFixtures.bundle(source, File(root, "downloads"))
        runBlocking { installer.stage(installer.resolve(File(downloads, SemanticPack.MANIFEST_NAME))) }

        installer.schedule()

        assertEquals(PendingSemanticPack.Request(library), PendingSemanticPack.read())
        // Staging again drops what was scheduled: its folder is the one being filled
        runBlocking { installer.stage(installer.resolve(File(downloads, SemanticPack.MANIFEST_NAME))) }
        assertEquals(null, PendingSemanticPack.read())
    }

    @Test
    fun `the installed pack is read as the search opens it`() {
        assertEquals(InstalledPack.None, InstalledPack.read(library))

        PackFixtures.folders(library)
        assertEquals(InstalledPack.InUse(null, PackFixtures.MODEL, 2, PackFixtures.LIBRARY), InstalledPack.read(library))

        File(library, "pardes-model/${SemanticPack.INFO_NAME}")
            .writeText(PackInfo(2, PackFixtures.MODEL, PackFixtures.DIMENSION, PackFixtures.LIBRARY_TAG, 2).toJson())
        assertEquals(2, (InstalledPack.read(library) as InstalledPack.InUse).pack)

        // Another release of the library keeps the pack in use: its lines keep their ids
        PackFixtures.library(library, release = "20261111000000")
        assertEquals(2, (InstalledPack.read(library) as InstalledPack.InUse).pack)

        File(library, SemanticPack.INDEX_DIR).deleteRecursively()
        assertEquals(InstalledPack.NoIndex, InstalledPack.read(library))
    }

    private fun marks() = SemanticPack.FOLDERS.map { "$it/${SemanticPackInstaller.STAGED_MARK}" }.toSet()

    private companion object {
        const val TEST_APP_ID = "seforimapp-tests"
    }
}

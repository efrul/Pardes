package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class PendingSemanticPackTest {
    private val root = createTempDirectory().toFile()
    private val markers = File(root, "app").apply { mkdirs() }
    private val library = PackFixtures.library(File(root, "library"))
    private val staging = SemanticPackInstaller.stagingDirOf(library)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `the staged pack takes the old one's place and the old one is deleted`() {
        PackFixtures.folders(library, tag = "old")
        stage()
        val expected = newContents()
        PendingSemanticPack.schedule(PendingSemanticPack.Request(library), markers)

        assertEquals(PendingSemanticPack.Outcome.Installed, PendingSemanticPack.runOnce(markers))

        assertEquals(expected, PackFixtures.contents(library).filterKeys { it.substringBefore('/') in SemanticPack.FOLDERS })
        assertFalse(staging.exists())
        for (name in SemanticPack.FOLDERS) assertFalse(File(library, "$name.old").exists())
        assertNull(PendingSemanticPack.read(markers))
        assertNull(PendingSemanticPack.runOnce(markers))
    }

    @Test
    fun `a first pack goes in where there was none`() {
        stage()
        val expected = newContents()

        val outcome = PendingSemanticPack.apply(PendingSemanticPack.Request(library))

        assertEquals(PendingSemanticPack.Outcome.Installed, outcome)
        assertEquals(expected, PackFixtures.contents(library).filterKeys { it.substringBefore('/') in SemanticPack.FOLDERS })
    }

    @Test
    fun `a failed move puts both old folders back`() {
        PackFixtures.folders(library, tag = "old")
        val old = oldContents()
        stage()
        var moves = 0
        // The fourth rename, the new index's into place, fails: the model is already the new one
        val failing: (File, File) -> Unit = { from, to ->
            if (++moves == 4) throw IOException("locked")
            Files.move(from.toPath(), to.toPath())
        }

        val outcome = PendingSemanticPack.apply(PendingSemanticPack.Request(library), failing)

        assertEquals(PendingSemanticPack.Outcome.Failed("locked"), outcome)
        assertEquals(old, oldContents())
        for (name in SemanticPack.FOLDERS) assertFalse(File(library, "$name.old").exists())
        assertFalse(staging.exists())
    }

    @Test
    fun `a fresh pack whose second folder can't go in leaves no first one`() {
        stage()
        var moves = 0
        val failing: (File, File) -> Unit = { from, to ->
            if (++moves == 2) throw IOException("locked")
            Files.move(from.toPath(), to.toPath())
        }

        assertIs<PendingSemanticPack.Outcome.Failed>(PendingSemanticPack.apply(PendingSemanticPack.Request(library), failing))

        for (name in SemanticPack.FOLDERS) assertFalse(File(library, name).exists(), name)
    }

    @Test
    fun `a start stopped halfway is ended by the next one`() {
        PackFixtures.folders(library, tag = "old")
        stage()
        val expected = newContents()
        // The model's new folder already in, its old one aside; the index not yet
        Files.move(File(library, SemanticPack.MODEL_DIR).toPath(), File(library, "${SemanticPack.MODEL_DIR}.old").toPath())
        Files.move(File(staging, SemanticPack.MODEL_DIR).toPath(), File(library, SemanticPack.MODEL_DIR).toPath())
        PendingSemanticPack.schedule(PendingSemanticPack.Request(library), markers)

        assertEquals(PendingSemanticPack.Outcome.Installed, PendingSemanticPack.runOnce(markers))

        assertEquals(expected, PackFixtures.contents(library).filterKeys { it.substringBefore('/') in SemanticPack.FOLDERS })
        assertFalse(File(library, "${SemanticPack.MODEL_DIR}.old").exists())
    }

    @Test
    fun `a pack goes in over another release of the library, and not where the library is gone`() {
        PackFixtures.folders(library, tag = "old")
        stage()
        val expected = newContents()
        // A request Pardes 1.0.0 wrote, with the library's release on a second line
        File(markers, "pending-semantic-pack.txt").writeText("${library.absolutePath}\n${PackFixtures.LIBRARY}")
        PackFixtures.library(library, release = "20261111000000")

        assertEquals(PendingSemanticPack.Request(library), PendingSemanticPack.read(markers))
        assertEquals(PendingSemanticPack.Outcome.Installed, PendingSemanticPack.runOnce(markers))
        assertEquals(expected, PackFixtures.contents(library).filterKeys { it.substringBefore('/') in SemanticPack.FOLDERS })

        stage()
        File(library, "seforim.db").delete()
        assertIs<PendingSemanticPack.Outcome.Dropped>(PendingSemanticPack.apply(PendingSemanticPack.Request(library)))
        assertFalse(staging.exists())
    }

    @Test
    fun `a failure is told once`() {
        PackFixtures.folders(library, tag = "old")
        val old = oldContents()
        stage()
        PendingSemanticPack.schedule(PendingSemanticPack.Request(library), markers)
        // A link to nowhere where the old index was: not there to move aside, but in the way of the new one
        File(library, SemanticPack.INDEX_DIR).deleteRecursively()
        Files.createSymbolicLink(File(library, SemanticPack.INDEX_DIR).toPath(), File(root, "nowhere").toPath())

        val outcome = assertIs<PendingSemanticPack.Outcome.Failed>(PendingSemanticPack.runOnce(markers))

        assertEquals(outcome.message, PendingSemanticPack.takeFailure(markers))
        assertNull(PendingSemanticPack.takeFailure(markers))
        assertNull(PendingSemanticPack.read(markers))
        assertEquals(old.filterKeys { it.startsWith(SemanticPack.MODEL_DIR) }, oldContents())
        assertFalse(staging.exists())
    }

    /** The new pack staged as SemanticPackInstaller leaves it: both folders, each marked as the pack's. */
    private fun stage() {
        PackFixtures.folders(staging, tag = "new")
        for (name in SemanticPack.FOLDERS) File(staging, "$name/${SemanticPackInstaller.STAGED_MARK}").writeText("")
    }

    private fun newContents(): Map<String, String> =
        PackFixtures.contents(staging).filterKeys { !it.endsWith(SemanticPackInstaller.STAGED_MARK) }

    private fun oldContents(): Map<String, String> =
        SemanticPack.FOLDERS
            .flatMap { name -> PackFixtures.contents(File(library, name)).map { (path, hash) -> "$name/$path" to hash } }
            .toMap()
}

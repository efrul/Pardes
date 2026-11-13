package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `a failed move puts both old folders back and keeps the new pack staged`() {
        PackFixtures.folders(library, tag = "old")
        val old = oldContents()
        stage()
        val staged = newContents()
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
        assertStaged(staged)
    }

    @Test
    fun `a fresh pack whose second folder can't go in leaves no first one`() {
        stage()
        val staged = newContents()
        var moves = 0
        val failing: (File, File) -> Unit = { from, to ->
            if (++moves == 2) throw IOException("locked")
            Files.move(from.toPath(), to.toPath())
        }

        assertIs<PendingSemanticPack.Outcome.Failed>(PendingSemanticPack.apply(PendingSemanticPack.Request(library), failing))

        for (name in SemanticPack.FOLDERS) assertFalse(File(library, name).exists(), name)
        assertStaged(staged)
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
    fun `a failure is told once, and the pack is tried again at the next start`() {
        PackFixtures.folders(library, tag = "old")
        val old = oldContents()
        stage()
        val expected = newContents()
        PendingSemanticPack.schedule(PendingSemanticPack.Request(library), markers)
        blockIndex()

        val outcome = assertIs<PendingSemanticPack.Outcome.Failed>(PendingSemanticPack.runOnce(markers))

        assertEquals(outcome.message, PendingSemanticPack.takeFailure(markers))
        assertNull(PendingSemanticPack.takeFailure(markers))
        assertEquals(PendingSemanticPack.Request(library), PendingSemanticPack.read(markers))
        assertEquals(old.filterKeys { it.startsWith(SemanticPack.MODEL_DIR) }, oldContents())
        assertStaged(expected)

        // What was in the way is gone by the next start
        Files.delete(File(library, SemanticPack.INDEX_DIR).toPath())
        assertEquals(PendingSemanticPack.Outcome.Installed, PendingSemanticPack.runOnce(markers))
        assertEquals(expected, PackFixtures.contents(library).filterKeys { it.substringBefore('/') in SemanticPack.FOLDERS })
        assertNull(PendingSemanticPack.read(markers))
    }

    @Test
    fun `a pack that fails every start is dropped at the last one`() {
        PackFixtures.folders(library, tag = "old")
        stage()
        val expected = newContents()
        PendingSemanticPack.schedule(PendingSemanticPack.Request(library), markers)
        blockIndex()

        repeat(PendingSemanticPack.MAX_ATTEMPTS - 1) {
            assertIs<PendingSemanticPack.Outcome.Failed>(PendingSemanticPack.runOnce(markers))
            assertEquals(PendingSemanticPack.Request(library), PendingSemanticPack.read(markers))
            assertStaged(expected)
        }
        assertIs<PendingSemanticPack.Outcome.Failed>(PendingSemanticPack.runOnce(markers))

        assertNull(PendingSemanticPack.read(markers))
        assertFalse(staging.exists())
        assertTrue(PendingSemanticPack.takeFailure(markers) != null)
        // A new pack scheduled starts its own count
        stage()
        PendingSemanticPack.schedule(PendingSemanticPack.Request(library), markers)
        assertIs<PendingSemanticPack.Outcome.Failed>(PendingSemanticPack.runOnce(markers))
        assertEquals(PendingSemanticPack.Request(library), PendingSemanticPack.read(markers))
    }

    @Test
    fun `a folder in use is waited for, a name taken is not`() {
        val from = File(root, "from")
        val to = File(root, "to")
        val pauses = mutableListOf<Long>()
        val pause: (Long) -> Unit = { pauses += it }
        PendingSemanticPack.renameWaiting(from, to, failing(2) { AccessDeniedException(from.path) }, pause)
        assertEquals(2, pauses.size)

        pauses.clear()
        assertFailsWith<FileAlreadyExistsException> {
            PendingSemanticPack.renameWaiting(from, to, failing(Int.MAX_VALUE) { FileAlreadyExistsException(to.path) }, pause)
        }
        assertTrue(pauses.isEmpty())

        // In use for good: given up after ten seconds of pauses
        assertFailsWith<AccessDeniedException> {
            PendingSemanticPack.renameWaiting(from, to, failing(Int.MAX_VALUE) { AccessDeniedException(from.path) }, pause)
        }
        assertEquals(10_000L, pauses.sum())
    }

    /** A move that throws [failure] its first [times] times, then moves nothing. */
    private fun failing(
        times: Int,
        failure: () -> Exception,
    ): (File, File) -> Unit {
        var left = times
        return { _, _ -> if (left-- > 0) throw failure() }
    }

    /** A link to nowhere where the old index was: not there to move aside, but in the way of the new one. */
    private fun blockIndex() {
        File(library, SemanticPack.INDEX_DIR).deleteRecursively()
        Files.createSymbolicLink(File(library, SemanticPack.INDEX_DIR).toPath(), File(root, "nowhere").toPath())
    }

    /** The new pack is still staged as it was, both folders marked as the pack's, for the next start. */
    private fun assertStaged(expected: Map<String, String>) {
        assertEquals(expected, newContents())
        for (name in SemanticPack.FOLDERS) assertTrue(File(staging, "$name/${SemanticPackInstaller.STAGED_MARK}").isFile, name)
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

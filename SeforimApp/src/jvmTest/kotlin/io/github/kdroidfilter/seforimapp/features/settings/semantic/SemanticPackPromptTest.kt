package io.github.kdroidfilter.seforimapp.features.settings.semantic

import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PackFixtures
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PendingSemanticPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPack
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A start opens the settings on the semantic search card when the library's semantic search is off. */
class SemanticPackPromptTest {
    private val root = Files.createTempDirectory("semantic-prompt").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a library without a pack opens the card`() {
        assertTrue(SemanticPackPrompt.needsPack(PackFixtures.library(File(root, "lib")), pending = null))
    }

    @Test
    fun `a pack the search can't use opens the card`() {
        val lib = PackFixtures.folders(PackFixtures.library(File(root, "lib")))
        File(lib, SemanticPack.INDEX_DIR).deleteRecursively()
        assertTrue(SemanticPackPrompt.needsPack(lib, pending = null))
    }

    @Test
    fun `no library, a pack in use or one waiting for the next start does not`() {
        assertFalse(SemanticPackPrompt.needsPack(File(root, "none"), pending = null))
        assertFalse(SemanticPackPrompt.needsPack(PackFixtures.folders(PackFixtures.library(File(root, "in-use"))), pending = null))
        val waiting = PackFixtures.library(File(root, "waiting"))
        assertFalse(SemanticPackPrompt.needsPack(waiting, pending = PendingSemanticPack.Request(waiting)))
    }

    @Test
    fun `the card takes the focus once`() {
        SemanticPackPrompt.opened()
        assertTrue(SemanticPackPrompt.takeFocus())
        assertFalse(SemanticPackPrompt.takeFocus())
    }
}

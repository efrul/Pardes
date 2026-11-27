package io.github.kdroidfilter.seforimapp.features.sefaria.pack

import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaData
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaPackBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sefaria's pack: the release chosen, its file downloaded, checked and put beside the library, read, and taken out. */
class SefariaPackTest {
    private val dir = Files.createTempDirectory("pardes-library").toFile()

    @BeforeTest
    fun inTheFolder() {
        SefariaData.close()
        SefariaData.locate = { SefariaData.fileIn(dir) }
    }

    @AfterTest
    fun cleanUp() {
        SefariaPackBuilder.hideData()
        dir.deleteRecursively()
    }

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        manifest: Boolean = true,
    ) = SemanticPackReleases.Release(
        tag = tag,
        prerelease = prerelease,
        assets =
            listOfNotNull(
                SemanticPackReleases.Asset(SefariaPack.FILE_NAME, 10, "https://x/$tag/data"),
                if (manifest) SemanticPackReleases.Asset(SefariaPack.MANIFEST_NAME, 10, "https://x/$tag/manifest") else null,
            ),
    )

    @Test
    fun `the newest published pack with a manifest is chosen`() {
        val chosen =
            SefariaPack.select(
                listOf(
                    release("sefaria-3", prerelease = true),
                    release("sefaria-2"),
                    release("sefaria-4", manifest = false),
                    release("daf-9"),
                    release("sefaria-1"),
                ),
            )
        assertEquals(2, assertNotNull(chosen).number)
        assertNull(SefariaPack.select(listOf(release("semantic-2-v3"), release("daf-1"))))
        assertNull(SefariaManifest.parseOrNull("""{"format":2,"pack":1,"file":"a","size":1,"sha256":"a","dbSize":1,"dbSha256":"b"}"""))
    }

    // A pack of [number] with a topic, served from memory: its manifest and its file gzipped
    private fun downloads(
        number: Int,
        scope: TestScope,
    ): SefariaDownloads {
        val db = File(dir.parentFile, "${dir.name}-pack-$number.db").apply { deleteOnExit() }
        SefariaPackBuilder.build(db, pack = number, topics = sequenceOf("T\t1\tתפילה\t\t", "A\t1\tברכות ב., א\t1\t1"))
        val gz = File(dir.parentFile, "${dir.name}-pack-$number.db.gz").apply { deleteOnExit() }
        GZIPOutputStream(gz.outputStream()).use { zip -> db.inputStream().use { it.copyTo(zip) } }
        val manifest =
            """{"format":1,"pack":$number,"file":"${SefariaPack.FILE_NAME}","size":${gz.length()},"sha256":"${DafDownloads.sha256(
                gz,
            )}",""" +
                """"dbSize":${db.length()},"dbSha256":"${DafDownloads.sha256(db)}","dump":"2026-11-25"}"""
        return SefariaDownloads(
            dir = { dir },
            releases = { listOf(release("sefaria-$number")) },
            fetchText = { url ->
                assertEquals("https://x/sefaria-$number/manifest", url)
                manifest
            },
            fetchFile = { url, dest, size, sha256, onBytes ->
                assertEquals("https://x/sefaria-$number/data", url)
                assertEquals(gz.length(), size)
                assertEquals(DafDownloads.sha256(gz), sha256)
                gz.copyTo(dest, overwrite = true)
                onBytes(size)
            },
            scope = scope,
            io = StandardTestDispatcher(scope.testScheduler),
        )
    }

    @Test
    fun `a pack is downloaded, put beside the library, read, replaced by a newer one and taken out`() {
        val scope = TestScope()
        val first = downloads(1, scope)
        first.refresh()
        scope.advanceUntilIdle()
        assertNull(first.state.value.installed)
        assertTrue(first.state.value.newer)
        val revision = SefariaData.revision.value
        first.download()
        scope.advanceUntilIdle()
        assertNull(first.state.value.problem)
        assertEquals(1, first.state.value.installed)
        assertEquals(1, SefariaPack.installedIn(dir))
        assertTrue(SefariaData.revision.value > revision, "the screens are told to read the data again")
        assertEquals(listOf("תפילה"), assertNotNull(SefariaData.current()).topics.of("ברכות ב., א").map { it.name })
        assertFalse(File(dir, SefariaPack.DOWNLOAD_DIR).exists(), "the download's folder goes once the pack is in place")

        // A newer pack is offered and takes the old one's place, though the old one is open
        val second = downloads(2, scope)
        second.refresh()
        scope.advanceUntilIdle()
        assertTrue(second.state.value.newer)
        second.download()
        scope.advanceUntilIdle()
        assertEquals(2, SefariaPack.installedIn(dir))
        assertEquals(2, assertNotNull(SefariaData.current()).pack)
        assertFalse(second.state.value.newer)

        second.remove()
        scope.advanceUntilIdle()
        assertNull(SefariaPack.installedIn(dir))
        assertNull(SefariaData.current())
        assertFalse(File(dir, SefariaPack.DIR_NAME).exists())
    }

    @Test
    fun `a file that is not the manifest's is not put in place`() {
        val scope = TestScope()
        val downloads = downloads(1, scope)
        val broken =
            SefariaDownloads(
                dir = { dir },
                releases = { listOf(release("sefaria-1")) },
                fetchText = {
                    """{"format":1,"pack":1,"file":"${SefariaPack.FILE_NAME}","size":4,"sha256":"x","dbSize":4,"dbSha256":"x"}"""
                },
                fetchFile = { _, dest, _, _, _ -> GZIPOutputStream(dest.outputStream()).use { it.write("nope".toByteArray()) } },
                scope = scope,
                io = StandardTestDispatcher(scope.testScheduler),
            )
        broken.refresh()
        scope.advanceUntilIdle()
        broken.download()
        scope.advanceUntilIdle()
        assertNotNull(broken.state.value.problem)
        assertNull(SefariaPack.installedIn(dir))
        assertFalse(File(dir, SefariaPack.DOWNLOAD_DIR).exists())
        assertNull(downloads.state.value.installed)
    }
}

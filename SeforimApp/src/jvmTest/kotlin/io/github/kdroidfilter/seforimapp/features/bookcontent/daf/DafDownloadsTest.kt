package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The released layouts: the release chosen, the manifest read, a tractate downloaded, checked and put in the folder. */
class DafDownloadsTest {
    private val fixture = File(assertNotNull(javaClass.classLoader.getResource("daf/berakhot-35b.json")).toURI())
    private val dir = Files.createTempDirectory("pardes-daf").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sha256(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        manifest: Boolean = true,
    ) = DafReleases.Release(
        tag = tag,
        prerelease = prerelease,
        assets =
            listOfNotNull(
                DafReleases.Asset("Berakhot.json.gz", 10, "https://x/$tag/Berakhot.json.gz"),
                if (manifest) DafReleases.Asset(DafReleases.MANIFEST_NAME, 10, "https://x/$tag/manifest") else null,
            ),
    )

    @Test
    fun theNewestPublishedReleaseWithAManifestIsChosen() {
        val chosen =
            DafReleases.select(
                listOf(
                    release("daf-3", prerelease = true),
                    release("daf-2"),
                    release("daf-4", manifest = false),
                    release("desktop-1.0.7-32"),
                    release("daf-1"),
                ),
            )
        assertEquals("daf-2", assertNotNull(chosen).tag)
        assertEquals(2, chosen.number)
        assertNull(DafReleases.select(listOf(release("semantic-2-v3"))))
    }

    @Test
    fun aTractateIsFoundInTheManifestByEitherName() {
        val manifest =
            DafManifest(
                format = 1,
                daf = 1,
                tractates = listOf(DafManifest.Entry("Berakhot", "ברכות", "Berakhot.json.gz", 1, "a", "b")),
            )
        assertEquals("Berakhot", manifest.entryFor("בְּרָכוֹת")?.tractate)
        assertEquals("Berakhot", manifest.entryFor("berakhot")?.tractate)
        assertNull(manifest.entryFor("שבת"))
        assertNull(DafManifest.parseOrNull("""{"format": 2, "daf": 1, "tractates": []}"""), "a format this app does not know")
    }

    /**
     * A release of the fixture as Berakhot, served from memory; [layout] the file served, [manifestJson] its sha256,
     * [version] the tractate's in the manifest (none: a release of before the versions).
     */
    private fun downloads(
        layout: ByteArray = fixture.readBytes(),
        manifestJson: ByteArray = layout,
        version: Int? = null,
        scope: TestScope,
    ): DafDownloads {
        val gz = gzip(layout)
        val versioned = version?.let { ""","version":$it""" }.orEmpty()
        val manifest =
            """{"format":1,"daf":1,"tractates":[{"tractate":"Berakhot","tractateHe":"ברכות","file":"Berakhot.json.gz",""" +
                """"size":${gz.size},"sha256":"${sha256(gz)}","jsonSha256":"${sha256(manifestJson)}","amudim":1$versioned}]}"""
        val releases =
            DafReleases { url ->
                assertEquals(DafReleases.API_URL, url)
                """[{"tag_name":"daf-1","assets":[
                    {"name":"pardes-daf.manifest.json","size":${manifest.length},"browser_download_url":"https://x/manifest"},
                    {"name":"Berakhot.json.gz","size":${gz.size},"browser_download_url":"https://x/Berakhot.json.gz"}]}]"""
            }
        return DafDownloads(
            layouts = DafLayouts(dir),
            dir = dir,
            releases = releases,
            fetchText = { manifest },
            fetchFile = { url, dest, size, sha256, onBytes ->
                assertEquals("https://x/Berakhot.json.gz", url)
                assertEquals(gz.size.toLong(), size)
                assertEquals(sha256(gz), sha256)
                dest.writeBytes(gz)
                onBytes(size)
            },
            scope = scope,
            io = StandardTestDispatcher(scope.testScheduler),
        )
    }

    @Test
    fun aTractateDownloadedIsCheckedAndPutInTheFolder() =
        runBlocking<Unit> {
            val test = TestScope(StandardTestDispatcher())
            val downloads = downloads(scope = test)
            downloads.refresh()
            test.advanceUntilIdle()
            val checked = downloads.state.value
            assertEquals(DafDownloads.Installed.MISSING, checked.installedOf(assertNotNull(checked.manifest).tractates.single()))
            downloads.download(checked.toGet)
            test.advanceUntilIdle()
            val done = downloads.state.value
            assertNull(done.problem)
            assertEquals(1, done.revision)
            assertTrue(done.toGet.isEmpty())
            val layouts = DafLayouts(dir)
            assertEquals("35b", assertNotNull(layouts.tractate("ברכות")).amudim.single().amud)
            // Nothing left of the download beside the layout
            assertEquals(listOf("Berakhot.json"), dir.listFiles().orEmpty().map { it.name })
        }

    @Test
    fun aLayoutThatIsNotTheManifestsIsNotPutInTheFolder() =
        runBlocking<Unit> {
            val test = TestScope(StandardTestDispatcher())
            val downloads = downloads(manifestJson = "another".toByteArray(), scope = test)
            downloads.refresh()
            test.advanceUntilIdle()
            downloads.download(downloads.state.value.toGet)
            test.advanceUntilIdle()
            val state = downloads.state.value
            assertNotNull(state.problem)
            assertEquals(0, state.revision)
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".json") || it.name.endsWith(".download") })
        }

    @Test
    fun aFileOfTheFolderOtherThanTheReleasesIsToBeUpdated() =
        runBlocking<Unit> {
            fixture.copyTo(File(dir, "Berakhot.json"))
            val test = TestScope(StandardTestDispatcher())
            // The release's layout differs from the folder's: a newer one
            val newer = fixture.readText().replaceFirst("\"format\"", "\"note\": 1, \"format\"").toByteArray()
            val downloads = downloads(layout = newer, scope = test)
            downloads.refresh()
            test.advanceUntilIdle()
            val state = downloads.state.value
            assertEquals(DafDownloads.Installed.OTHER, state.installedOf(state.toGet.single()))
            downloads.download(state.toGet)
            test.advanceUntilIdle()
            assertEquals(
                DafDownloads.Installed.CURRENT,
                downloads.state.value.installedOf(assertNotNull(downloads.state.value.manifest).tractates.single()),
            )
            assertEquals(String(newer), File(dir, "Berakhot.json").readText())
        }

    // The fixture as the releases write a tractate's version of its own: in its file's head
    private fun versioned(version: Int) = fixture.readText().replaceFirst("\"amudim\"", "\"version\":$version,\"amudim\"")

    @Test
    fun aTractatesVersionIsItsFilesAndANewerOneIsOffered() =
        runBlocking<Unit> {
            val file = File(dir, "Berakhot.json")
            fixture.copyTo(file)
            assertNull(DafLayouts(dir).versionOf("Berakhot"), "a file of before the versions")
            val test = TestScope(StandardTestDispatcher())
            val downloads = downloads(layout = versioned(3).toByteArray(), version = 3, scope = test)
            downloads.refresh()
            test.advanceUntilIdle()
            assertNull(downloads.state.value.versions["Berakhot"])
            val offered = downloads.state.value.newerFor("ברכות")
            assertEquals(3, offered?.version, "a file with no version is older than any")

            // The folder's file an older version: the release's is offered, and downloaded it is the folder's
            file.writeText(versioned(2))
            downloads.recount()
            test.advanceUntilIdle()
            val older = downloads.state.value
            assertEquals(2, older.versions["Berakhot"])
            val newer = assertNotNull(older.newerFor("Berakhot"))
            downloads.download(listOf(newer))
            test.advanceUntilIdle()
            val done = downloads.state.value
            assertNull(done.problem)
            assertEquals(3, done.versions["Berakhot"])
            assertNull(done.newerFor("ברכות"))
            val layouts = DafLayouts(dir)
            assertEquals(3, layouts.versionOf("ברכות"))
            assertEquals(3, assertNotNull(layouts.tractate("Berakhot")).version)

            // A later file than the release's (one brought by hand) is another file, but nothing newer is offered
            file.writeText(versioned(4))
            downloads.recount()
            test.advanceUntilIdle()
            val later = downloads.state.value
            assertEquals(DafDownloads.Installed.OTHER, later.installedOf(newer))
            assertNull(later.newerFor("ברכות"))
        }

    @Test
    fun aReleaseOfBeforeTheVersionsOffersNoNewerOne() =
        runBlocking<Unit> {
            File(dir, "Berakhot.json").writeText(versioned(1))
            val test = TestScope(StandardTestDispatcher())
            val downloads = downloads(scope = test)
            downloads.refresh()
            test.advanceUntilIdle()
            val state = downloads.state.value
            assertEquals(DafDownloads.Installed.OTHER, state.installedOf(state.toGet.single()))
            assertNull(state.newerFor("ברכות"))
        }
}

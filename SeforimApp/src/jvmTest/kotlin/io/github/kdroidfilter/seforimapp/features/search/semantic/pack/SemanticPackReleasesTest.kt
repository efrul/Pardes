package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SemanticPackReleasesTest {
    @Test
    fun `the newest published pack is chosen, whatever library release it was built on`() {
        val releases =
            listOf(
                release("semantic-3-v3-20261012193827", draft = true),
                release("semantic-4-v3-20261012193827", prerelease = true),
                release("semantic-5-v4-20261201000000"),
                release("android-0.5.0-8"),
                release("semantic-2-v3-20261012193827"),
                release("semantic-1-v3-20261012193827"),
                // Its manifest not uploaded (yet)
                release("semantic-6-v3-20261012193827", manifest = false),
            )

        val chosen = SemanticPackReleases.select(releases, "20261012193827")

        assertEquals("semantic-5-v4-20261201000000", chosen?.tag)
        assertEquals(5, chosen?.pack)
        assertEquals(PART_BYTES * 2, chosen?.bundleBytes)
        assertEquals(chosen, SemanticPackReleases.select(releases, null))
        assertNull(SemanticPackReleases.select(releases.filter { it.tag.startsWith("android") }, "20261012193827"))
    }

    @Test
    fun `of a pack's builds, the installed library's is chosen, else the newest library's`() {
        val releases =
            listOf(
                release("semantic-5-v4-20261201000000"),
                release("semantic-5-v3-20261012193827"),
                release("semantic-5-v2-20261011223929"),
            )

        assertEquals("semantic-5-v3-20261012193827", SemanticPackReleases.select(releases, "20261012193827")?.tag)
        assertEquals("semantic-5-v4-20261201000000", SemanticPackReleases.select(releases, "20251116085948")?.tag)
        assertEquals("semantic-5-v4-20261201000000", SemanticPackReleases.select(releases, null)?.tag)
    }

    @Test
    fun `the API's list is read, and the site's when the API can't be`() {
        val api =
            """[{"tag_name":"semantic-2-v3-20261012193827","draft":false,"prerelease":false,"html_url":"page",""" +
                """"author":{"login":"efrul"},"assets":[{"name":"${SemanticPack.MANIFEST_NAME}","size":900,""" +
                """"browser_download_url":"https://example/m","download_count":3}]}]"""
        val site = """{"generated_at":"2026-10-28T00:00:00Z","releases":$api}"""

        val fromApi = runBlocking { SemanticPackReleases { url -> if (url == SemanticPackReleases.API_URL) api else error(url) }.list() }
        val fromSite =
            runBlocking {
                SemanticPackReleases { url -> if (url == SemanticPackReleases.SITE_URL) site else throw IOException("HTTP 403") }.list()
            }

        assertEquals(fromApi, fromSite)
        val assets = fromApi.single().assets
        assertEquals(listOf("https://example/m"), assets.map { it.url })
        assertFailsWith<IOException> { runBlocking { SemanticPackReleases { throw IOException("offline") }.list() } }
    }

    @Test
    fun `a library release is the same in a tag and in release_info`() {
        assertEquals(SemanticPack.Tag(2, "v3-20261012193827"), SemanticPack.parseTag("semantic-2-v3-20261012193827"))
        assertNull(SemanticPack.parseTag("desktop-1.0.0-3"))
        assertEquals(true, SemanticPack.sameLibrary("v3-20261012193827", "20261012193827\n"))
        assertEquals(false, SemanticPack.sameLibrary("v3-20261012193827", "20261111000000"))
    }

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        manifest: Boolean = true,
    ) = SemanticPackReleases.Release(
        tag = tag,
        draft = draft,
        prerelease = prerelease,
        assets =
            listOfNotNull(
                SemanticPackReleases.Asset("${SemanticPack.BUNDLE_NAME}.part01", PART_BYTES, "u1"),
                SemanticPackReleases.Asset("${SemanticPack.BUNDLE_NAME}.part02", PART_BYTES, "u2"),
                SemanticPackReleases.Asset(SemanticPack.MODEL_ZIP, PART_BYTES, "u3"),
                if (manifest) SemanticPackReleases.Asset(SemanticPack.MANIFEST_NAME, 900, "u4") else null,
            ),
    )

    private companion object {
        const val PART_BYTES = 1_000L
    }
}

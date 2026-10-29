// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/search/semantic/pack/SemanticPackReleases.kt,
// same package and names. Changes, marked "Android:": the lists are read with Android's HttpURLConnection and the app's
// user agent (desktop's HttpsConnectionFactory and BrandInfo), and logged with android.util.Log.
package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import android.util.Log
import io.github.kdroidfilter.seforimapp.android.data.USER_AGENT
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The semantic packs released in [SemanticPack.REPOSITORY]: the GitHub API's release list, or when it can't be reached
 * (60 calls an hour per IP, often shared behind a filtering proxy, and some filters block api.github.com) the copy the
 * site writes at every deploy (`semantic.json`, .github/workflows/android-site.yml), in the API's shape.
 */
class SemanticPackReleases(
    private val fetchText: suspend (url: String) -> String = ::get,
) {
    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val page: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    data class Asset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val url: String,
    )

    @Serializable
    private data class SiteList(
        val releases: List<Release> = emptyList(),
    )

    /** A pack the app can download: its release, and its files by name. */
    data class Available(
        val tag: String,
        val pack: Int,
        val library: String,
        val page: String?,
        val assets: Map<String, Asset>,
    ) {
        /** The bundle's size, the files the app downloads (the manifest's, before it is read). */
        val bundleBytes: Long
            get() = assets.values.filter { SemanticPack.isBundleFile(it.name) && it.name != SemanticPack.MANIFEST_NAME }.sumOf { it.size }
    }

    /** The newest pack for the library release [library] (release_info.txt), or null when there is none yet. */
    suspend fun newestFor(library: String): Available? = select(list(), library)

    /** The releases of the repository, newest first; throws IOException when neither list can be read. */
    suspend fun list(): List<Release> =
        runSuspendCatching { json.decodeFromString(ListSerializer(Release.serializer()), fetchText(API_URL)) }
            .getOrElse { apiError ->
                Log.w(TAG, "$API_URL: ${apiError.message}; trying $SITE_URL")
                runSuspendCatching { json.decodeFromString(SiteList.serializer(), fetchText(SITE_URL)).releases }
                    .getOrElse { throw IOException("Neither $API_URL nor $SITE_URL could be read: ${it.message}", apiError) }
            }

    companion object {
        private const val TAG = "PardesSemantic"
        const val API_URL = "https://api.github.com/repos/${SemanticPack.REPOSITORY}/releases?per_page=100"
        const val SITE_URL = "https://efrul.github.io/Pardes/semantic.json"
        const val RELEASES_PAGE = "https://github.com/${SemanticPack.REPOSITORY}/releases"

        private val json = Json { ignoreUnknownKeys = true }

        /** Of [releases], the published pack of [library] with the highest number that has its manifest. */
        fun select(
            releases: List<Release>,
            library: String,
        ): Available? =
            releases
                .asSequence()
                .filter { !it.draft && !it.prerelease }
                .mapNotNull { release ->
                    SemanticPack.parseTag(release.tag)?.let { tag ->
                        Available(release.tag, tag.pack, tag.library, release.page, release.assets.associateBy { it.name })
                    }
                }.filter { SemanticPack.sameLibrary(it.library, library) && SemanticPack.MANIFEST_NAME in it.assets }
                .maxByOrNull { it.pack }

        // Android: HttpURLConnection, with the app's user agent
        private suspend fun get(url: String): String =
            withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                try {
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
                    connection.inputStream.use { it.readBytes().decodeToString() }
                } finally {
                    connection.disconnect()
                }
            }
    }
}

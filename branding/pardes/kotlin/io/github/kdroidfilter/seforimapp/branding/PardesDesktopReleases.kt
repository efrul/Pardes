package io.github.kdroidfilter.seforimapp.branding

import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.updater.exception.NetworkException
import dev.nucleusframework.updater.provider.UpdateProvider
import io.github.kdroidfilter.seforimapp.logger.infoln
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Pardes for desktop's updates for Nucleus's updater: the `desktop-*` releases of efrul/Pardes, as the desktop release
 * workflow publishes them (tag `desktop-<x.y.z>-<run>[-r<attempt>]`, Nucleus's latest.yml and latest-linux.yml beside
 * the installers).
 *
 * Nucleus's GitHubProvider follows a repository's Latest release, and efrul/Pardes's Latest is Pardes for Android's
 * (`android-*` tags; desktop releases are published with `--latest=false`). So this provider reads the release list and
 * takes the newest desktop release that is neither a draft nor a pre-release (pre-releases only when Nucleus allows
 * them: an installed pre-release or a channel other than "latest") and that carries this platform's yml. It serves that
 * release's files as GitHubProvider serves the Latest's: `https://github.com/efrul/Pardes/releases/download/<tag>/<file>`.
 *
 * Written against nucleus.updater-runtime 3.0.0-dev-202610200221, whose NucleusUpdater calls [resolveMetadataUrl] with
 * the installed version, then [getDownloadUrl] with the yml's URL for each file the yml lists, and the interface's
 * defaults for the block map, the `.asc` signature and the (empty) auth headers.
 */
class PardesDesktopReleases : UpdateProvider {
    override fun resolveMetadataUrl(
        channel: String,
        platform: Platform,
        httpClient: HttpClient,
    ): String = metadataUrl(channel, platform, httpClient, allowPrerelease = !channel.equals(STABLE_CHANNEL, ignoreCase = true))

    override fun resolveMetadataUrl(
        channel: String,
        platform: Platform,
        httpClient: HttpClient,
        currentVersion: String,
        allowPrerelease: Boolean,
    ): String = metadataUrl(channel, platform, httpClient, allowPrerelease)

    /** A file the yml lists, in the yml's own release. */
    override fun getDownloadUrl(
        fileName: String,
        version: String,
        metadataUrl: String,
    ): String = "${metadataUrl.substringBeforeLast('/')}/${assetName(fileName)}"

    /** Not used: a desktop release's tag (it has the workflow's run number) is found only in the release list. */
    override fun getUpdateMetadataUrl(
        channel: String,
        platform: Platform,
    ): String = throw UnsupportedOperationException(NEEDS_RELEASE_LIST)

    /** Not used: a file's release is the yml's, which the other [getDownloadUrl] is given. */
    override fun getDownloadUrl(
        fileName: String,
        version: String,
    ): String = throw UnsupportedOperationException(NEEDS_RELEASE_LIST)

    private fun metadataUrl(
        channel: String,
        platform: Platform,
        httpClient: HttpClient,
        allowPrerelease: Boolean,
    ): String {
        val file = metadataFileName(channel, platform)
        val release =
            releases(httpClient)
                .mapNotNull { desktopRelease(it, file, allowPrerelease) }
                .maxOrNull()
                ?: throw NetworkException("No published Pardes for desktop release with $file in $REPO on GitHub.")
        infoln { "[update] newest Pardes for desktop release: ${release.tag}" }
        return "$DOWNLOAD_URL${release.tag}/$file"
    }

    /** The newest releases of the repository, as the GitHub API lists them (drafts only for its maintainers). */
    private fun releases(httpClient: HttpClient): List<JsonObject> {
        val request =
            HttpRequest
                .newBuilder(URI.create(API_URL))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .timeout(TIMEOUT)
                .GET()
                .build()
        val response =
            try {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (e: IOException) {
                // The message reaches the log (AppUpdateService logs the exception's message only)
                throw NetworkException("GitHub release list of $REPO unreachable (api.github.com)", e)
            }
        val status = response.statusCode()
        if (status != HTTP_OK) {
            // 60 calls an hour per IP without a token, and an IP is often shared behind a filtering proxy
            val limit = if (response.headers().firstValue("x-ratelimit-remaining").orElse(null) == "0") " (rate limit)" else ""
            throw NetworkException("GitHub release list of $REPO failed: HTTP $status$limit")
        }
        val list =
            try {
                Json.parseToJsonElement(response.body()).jsonArray
            } catch (e: IllegalArgumentException) {
                // What parseToJsonElement throws (SerializationException) is one, and so is jsonArray's for a non-list
                throw NetworkException("GitHub release list of $REPO is not a JSON list", e)
            }
        return list.filterIsInstance<JsonObject>()
    }

    /** [release] when it is a published desktop release that carries [file]; null for any other. */
    private fun desktopRelease(
        release: JsonObject,
        file: String,
        allowPrerelease: Boolean,
    ): DesktopRelease? {
        val published = !release.flag("draft") && (allowPrerelease || !release.flag("prerelease"))
        val tag = release.text("tag_name")?.takeIf { published && release.hasAsset(file) } ?: return null
        val numbers = TAG.matchEntire(tag)?.groupValues?.drop(1) ?: return null
        return DesktopRelease(tag, numbers.map { it.toLongOrNull() ?: 0L })
    }

    private fun JsonObject.hasAsset(name: String): Boolean =
        (this["assets"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.text("name") == name }

    private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /**
     * A desktop release's tag and its numbers: major, minor, patch, the workflow's run, its attempt (0 for none). The
     * newest is the highest version, then the latest run of it (two builds of one version), then the latest attempt.
     */
    private class DesktopRelease(
        val tag: String,
        val numbers: List<Long>,
    ) : Comparable<DesktopRelease> {
        override fun compareTo(other: DesktopRelease): Int =
            numbers.zip(other.numbers).map { (a, b) -> a.compareTo(b) }.firstOrNull { it != 0 } ?: 0
    }

    private companion object {
        const val REPO = "efrul/Pardes"
        const val API_URL = "https://api.github.com/repos/$REPO/releases?per_page=100"
        const val API_VERSION = "2022-11-28"
        const val DOWNLOAD_URL = "https://github.com/$REPO/releases/download/"
        const val STABLE_CHANNEL = "latest"
        const val HTTP_OK = 200
        const val NEEDS_RELEASE_LIST = "Pardes for desktop's releases are found in the release list: use resolveMetadataUrl"
        val TIMEOUT: Duration = Duration.ofSeconds(30)

        /** `desktop-<x.y.z>-<run>[-r<attempt>]`, the desktop release workflow's tags. */
        val TAG = Regex("""^desktop-(\d+)\.(\d+)\.(\d+)-(\d+)(?:-r(\d+))?$""")

        /** The yml Nucleus's generate-update-yml writes for [platform], as Nucleus's GitHub provider names it. */
        fun metadataFileName(
            channel: String,
            platform: Platform,
        ): String =
            when (platform) {
                Platform.MacOS -> "$channel-mac.yml"
                Platform.Linux -> "$channel-linux.yml"
                else -> "$channel.yml"
            }

        /** An asset's name from the yml's `url`, as Nucleus's GitHub provider derives it. */
        fun assetName(fileUrl: String): String = fileUrl.substringAfterLast('/').replace(' ', '-')
    }
}

package io.github.kdroidfilter.seforimapp.android.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

/**
 * A published Pardes version that can be installed over this one: a GitHub release of efrul/Pardes as
 * android-release.yml makes it (tag `android-<versionName>-<run>[-r<attempt>]`, asset `pardes-<tag>.apk`).
 */
data class AppRelease(
    /** versionName, from the tag ("0.3.0"). */
    val version: String,
    /** The release workflow's run number, from the tag: orders two builds of one version. */
    val build: Int,
    val tag: String,
    val pageUrl: String?,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lower-case hex, from GitHub's `sha256:` digest of the asset. */
    val apkSha256: String,
)

/**
 * Where Pardes finds its newer versions, read as the download site does (androidApp/site/index.html): the GitHub API's
 * release list first, then, when that fails (60 calls an hour per IP, often shared behind filtering proxies, and some
 * filters block api.github.com), releases.json, which android-site.yml writes in the same shape at every deploy and
 * deploys again after every release. Both list the newest first; the newest version that is not a pre-release is
 * offered. A version is its versionName (the tag's), compared number by number: versionCode is raised by hand with it
 * and is not in the tag. What is new comes from the site's changelog.md (androidApp/CHANGELOG.md).
 */
internal object AppReleases {
    const val REPO = "efrul/Pardes"
    const val API_URL = "https://api.github.com/repos/$REPO/releases?per_page=30"
    const val SNAPSHOT_URL = "https://efrul.github.io/Pardes/releases.json"
    const val CHANGELOG_URL = "https://efrul.github.io/Pardes/changelog.md"
    const val CHANGELOG_PAGE = "https://efrul.github.io/Pardes/#changelog"
    const val SITE_URL = "https://efrul.github.io/Pardes/"

    /** Larger than any Pardes APK will be. */
    private const val MAX_APK_BYTES = 256L shl 20

    /** What the dialog shows of the changelog at most. */
    private const val MAX_CHANGE_LINES = 300
    private const val MAX_CHANGE_CHARS = 32_000
    private const val MAX_LINE_CHARS = 1_000

    private val TAG = Regex("""^android-(\d+(?:\.\d+)*)-(\d+)(?:-r\d+)?$""")
    private val APK = Regex("""^pardes-.*\.apk$""", RegexOption.IGNORE_CASE)
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val json = Json { ignoreUnknownKeys = true }

    /** "android-0.3.0-12" or "android-0.3.0-12-r2" → ("0.3.0", 12); null for any other tag. */
    fun versionOfTag(tag: String): Pair<String, Int>? =
        TAG.matchEntire(tag)?.let { m -> m.groupValues[2].toIntOrNull()?.let { m.groupValues[1] to it } }

    /**
     * Dot-separated numbers compared as numbers, a missing part as 0 ("0.3" = "0.3.0" < "0.10.0"), as the site's
     * newer(). A release's versionName is digits and dots only (a tag with "-beta" in it matches no release); in the
     * installed versionName anything after the numbers is ignored.
     */
    fun compareVersions(a: String, b: String): Int {
        val x = numbers(a)
        val y = numbers(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0L }.compareTo(y.getOrElse(i) { 0L })
            if (d != 0) return d
        }
        return 0
    }

    private fun numbers(version: String): List<Long> =
        version.trim().takeWhile { it.isDigit() || it == '.' }.split('.').map { it.toLongOrNull() ?: 0L }

    /**
     * The releases in the API's list (a JSON array) or in releases.json (`{generated_at, releases: [...]}`) that can be
     * installed: not drafts or pre-releases, with an android- tag and a `pardes-*.apk` asset that has a size and a
     * sha256 digest. Throws on text that is neither (a filter's block page, say).
     */
    fun parse(text: String): List<AppRelease> {
        val root = json.parseToJsonElement(text)
        val list = when (root) {
            is JsonArray -> root
            is JsonObject -> root["releases"] as? JsonArray ?: throw IllegalArgumentException("No releases list")
            else -> throw IllegalArgumentException("Not a release list")
        }
        return list.mapNotNull { (it as? JsonObject)?.let(::releaseOf) }
    }

    private fun releaseOf(release: JsonObject): AppRelease? {
        if (release.boolean("draft") == true || release.boolean("prerelease") == true) return null
        val tag = release.string("tag_name") ?: return null
        val (version, build) = versionOfTag(tag) ?: return null
        val apks = release["assets"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.filter { asset -> asset.string("name")?.let(APK::matches) == true }
        // The workflow's own name first, should a release ever carry another APK beside it
        val apk = apks.firstOrNull { it.string("name") == "pardes-$tag.apk" } ?: apks.firstOrNull() ?: return null
        val sha = apk.string("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()?.takeIf(SHA256::matches) ?: return null
        // Bounded: the download stops at this size, and the free-space check doubles it (the APK is ~25 MB)
        val size = apk.long("size")?.takeIf { it in 1..MAX_APK_BYTES } ?: return null
        return AppRelease(
            version = version,
            build = build,
            tag = tag,
            pageUrl = release.string("html_url"),
            apkName = apk.string("name") ?: return null,
            apkUrl = apk.string("browser_download_url")?.takeIf { it.startsWith("https://") || it.startsWith("file:") } ?: return null,
            apkSize = size,
            apkSha256 = sha,
        )
    }

    /**
     * Desktop needsDbWarning (a MINOR or MAJOR update, not a PATCH): from [installed] to [offered] the first or second
     * number changes, and the new version may ask for a newer library.
     */
    fun newerLibraryMayBeNeeded(installed: String, offered: String): Boolean {
        val x = numbers(installed)
        val y = numbers(offered)
        return x.getOrElse(0) { 0L } != y.getOrElse(0) { 0L } || x.getOrElse(1) { 0L } != y.getOrElse(1) { 0L }
    }

    /** The newest of [releases]: the highest version, then the latest build of it. */
    fun newest(releases: List<AppRelease>): AppRelease? =
        releases.maxWithOrNull { a, b -> compareVersions(a.version, b.version).takeIf { it != 0 } ?: a.build.compareTo(b.build) }

    /**
     * What changed after [installed] up to [offered], from CHANGELOG.md: its `## <version> · <date>` sections in that
     * range, as they stand there (newest first), or null when it has none.
     */
    fun changesSince(changelog: String, installed: String, offered: String): String? {
        val out = StringBuilder()
        var keep = false
        var lines = 0
        for (line in changelog.lineSequence()) {
            // Bounded: the dialog lays every line out at once (the site's page has all of it)
            if (lines >= MAX_CHANGE_LINES || out.length >= MAX_CHANGE_CHARS) break
            val trimmed = line.trim()
            if (trimmed.startsWith("## ")) {
                val version = trimmed.removePrefix("## ").substringBefore('·').trim()
                keep = version.firstOrNull()?.isDigit() == true &&
                    compareVersions(version, installed) > 0 && compareVersions(version, offered) <= 0
            } else if (trimmed.startsWith("# ") || trimmed.startsWith("<")) {
                // The title and the page's HTML lines (<div dir="rtl">) belong to no version
                continue
            }
            if (keep) {
                out.appendLine(line.trimEnd().take(MAX_LINE_CHARS))
                lines++
            }
        }
        return out.toString().trim().ifEmpty { null }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

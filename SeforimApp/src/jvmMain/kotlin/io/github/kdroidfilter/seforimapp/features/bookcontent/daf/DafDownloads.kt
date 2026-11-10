package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.branding.BrandInfo
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.onboarding.download.HttpFileDownloader
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPack
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimapp.network.HttpsConnectionFactory
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * The layouts as released for download (.github/workflows/daf-release.yml, a daf-<n> release of Pardes's repository):
 * its manifest names every tractate's file, gzipped, with its sha256 and the sha256 of the layout it holds, and the
 * tractate's own version (one more in each release its layout changed in; none in a release of before the versions).
 */
@Serializable
data class DafManifest(
    val format: Int,
    val daf: Int,
    val tractates: List<Entry>,
) {
    @Serializable
    data class Entry(
        val tractate: String,
        val tractateHe: String,
        /** The release's file, the layout gzipped, its [size] and [sha256]. */
        val file: String,
        val size: Long,
        val sha256: String,
        /** The layout's own sha256, unzipped: what a tractate's file in the folder is compared with. */
        val jsonSha256: String,
        val amudim: Int = 0,
        val version: Int? = null,
    )

    /** The tractate named [name], in English or in Hebrew. */
    fun entryFor(name: String): Entry? {
        val key = DafLayouts.key(name)
        return tractates.find { DafLayouts.key(it.tractate) == key || DafLayouts.key(it.tractateHe) == key }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseOrNull(text: String): DafManifest? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.format == FORMAT }

        const val FORMAT = 1
    }
}

/** The daf-<n> releases of Pardes's repository: the GitHub API's list, or the site's copy (daf.json) when it fails. */
class DafReleases(
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

    /** A release of layouts: its number, its page and its files by name. */
    data class Available(
        val tag: String,
        val number: Int,
        val page: String?,
        val assets: Map<String, Asset>,
    )

    /** The newest release of layouts, or null when none is out. Throws IOException when neither list can be read. */
    suspend fun newest(): Available? = select(list())

    suspend fun list(): List<Release> =
        runSuspendCatching { json.decodeFromString(ListSerializer(Release.serializer()), fetchText(API_URL)) }
            .getOrElse { apiError ->
                warnln { "[DafReleases] $API_URL: ${apiError.message}; trying $SITE_URL" }
                runSuspendCatching { json.decodeFromString(SiteList.serializer(), fetchText(SITE_URL)).releases }
                    .getOrElse { throw IOException("Neither $API_URL nor $SITE_URL could be read: ${it.message}", apiError) }
            }

    companion object {
        const val API_URL = "https://api.github.com/repos/${SemanticPack.REPOSITORY}/releases?per_page=100"
        const val SITE_URL = "https://efrul.github.io/Pardes/daf.json"
        const val RELEASES_PAGE = "https://github.com/${SemanticPack.REPOSITORY}/releases"
        const val MANIFEST_NAME = "pardes-daf.manifest.json"

        private val TAG = Regex("""daf-(\d+)""")
        private val json = Json { ignoreUnknownKeys = true }

        /** Of [releases], the published one of the highest number with a manifest. */
        fun select(releases: List<Release>): Available? =
            releases
                .asSequence()
                .filter { !it.draft && !it.prerelease }
                .mapNotNull { release ->
                    TAG.matchEntire(release.tag)?.let { match ->
                        Available(release.tag, match.groupValues[1].toInt(), release.page, release.assets.associateBy { it.name })
                    }
                }.filter { MANIFEST_NAME in it.assets }
                .maxByOrNull { it.number }

        private suspend fun get(url: String): String =
            withContext(Dispatchers.IO) {
                val connection =
                    HttpsConnectionFactory.openConnection(url) {
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                        setRequestProperty("User-Agent", BrandInfo.USER_AGENT)
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
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

/**
 * Downloading the released layouts into a library's pardes-daf folder ([layouts]): the newest release and its
 * manifest, what of it is in the folder, and each tractate downloaded, checked and imported as a picked file is
 * ([DafLayouts.import]). One for a folder ([of]), whose work goes on wherever it was started from: the daf view's
 * screen of a tractate without its layout, or Settings › ניהול נתונים.
 */
class DafDownloads(
    private val layouts: DafLayouts,
    private val dir: File,
    private val releases: DafReleases = DafReleases(),
    private val fetchText: suspend (url: String) -> String = { HttpFileDownloader().downloadText(it) },
    private val fetchFile: suspend (url: String, dest: File, size: Long, sha256: String, onBytes: (Long) -> Unit) -> Unit =
        { url, dest, size, sha256, onBytes -> HttpFileDownloader().download(url, dest, size, sha256, onBytes) },
    // The app's lifetime: a download goes on when the screen that started it closes
    @StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    // Where files are read and written: a test's own dispatcher, to wait for them
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** A tractate of the release, as the folder has it. */
    enum class Installed { MISSING, CURRENT, OTHER }

    /** A download under way: the tractate now, its place among [count], and the bytes of all of them. */
    data class Work(
        val tractateHe: String,
        val index: Int,
        val count: Int,
        val done: Long,
        val total: Long,
    )

    data class State(
        val checking: Boolean = false,
        /** Whether the releases were read: [release] null after it means none is out. */
        val checked: Boolean = false,
        val release: DafReleases.Available? = null,
        val manifest: DafManifest? = null,
        val installed: Map<String, Installed> = emptyMap(),
        /** The version of each tractate's file in the folder, where it has one. */
        val versions: Map<String, Int> = emptyMap(),
        val work: Work? = null,
        /** Why the last check or download stopped. */
        val problem: String? = null,
        /** Bumped by every tractate put in the folder, for the views to read it again. */
        val revision: Int = 0,
    ) {
        fun installedOf(entry: DafManifest.Entry): Installed = installed[entry.tractate] ?: Installed.MISSING

        /** The release's tractates not as it has them: missing, or another file of theirs. */
        val toGet: List<DafManifest.Entry>
            get() = manifest?.tractates.orEmpty().filter { installedOf(it) != Installed.CURRENT }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    /** Reads the newest release and its manifest again, unless they were read and [force] is false. */
    fun refresh(force: Boolean = false) {
        val now = _state.value
        if (now.checking || now.work != null || (now.checked && now.problem == null && !force)) return
        _state.update { it.copy(checking = true, problem = null) }
        scope.launch {
            val result =
                runSuspendCatching {
                    val release = releases.newest()
                    val manifest =
                        release?.let { r ->
                            val asset = r.assets.getValue(DafReleases.MANIFEST_NAME)
                            DafManifest.parseOrNull(fetchText(asset.url)) ?: throw IOException("Unknown manifest format")
                        }
                    Triple(release, manifest, withContext(io) { installedOf(manifest) to versionsOf(manifest) })
                }
            _state.update { s ->
                result.fold(
                    { (release, manifest, folder) ->
                        s.copy(
                            checking = false,
                            checked = true,
                            release = release,
                            manifest = manifest,
                            installed = folder.first,
                            versions = folder.second,
                        )
                    },
                    { s.copy(checking = false, checked = true, problem = it.message ?: it.toString()) },
                )
            }
        }
    }

    /** Compares the folder with the manifest read again: a file may have been imported or taken out since. */
    fun recount() {
        val manifest = _state.value.manifest ?: return
        if (_state.value.work != null) return
        scope.launch {
            // A file of the folder that can't be read leaves the count as it was
            val folder =
                runSuspendCatching { withContext(io) { installedOf(manifest) to versionsOf(manifest) } }.getOrNull() ?: return@launch
            _state.update { it.copy(installed = folder.first, versions = folder.second) }
        }
    }

    /** Downloads [entries] one after the other; a tractate that fails stops the rest, and is said. */
    fun download(entries: List<DafManifest.Entry>) {
        val release = _state.value.release ?: return
        if (entries.isEmpty() || _state.value.work != null) return
        val total = entries.sumOf { it.size }
        _state.update { it.copy(problem = null, work = Work(entries.first().tractateHe, 0, entries.size, 0, total)) }
        job =
            scope.launch {
                var done = 0L
                try {
                    entries.forEachIndexed { index, entry ->
                        val asset = release.assets[entry.file] ?: throw IOException("${entry.file} is not in ${release.tag}")
                        install(entry, asset) { read ->
                            _state.update { it.copy(work = Work(entry.tractateHe, index, entries.size, done + read, total)) }
                        }
                        done += entry.size
                        _state.update { s ->
                            s.copy(
                                installed = s.installed + (entry.tractate to Installed.CURRENT),
                                versions = entry.version?.let { s.versions + (entry.tractate to it) } ?: (s.versions - entry.tractate),
                                revision = s.revision + 1,
                            )
                        }
                    }
                    _state.update { it.copy(work = null) }
                } catch (e: CancellationException) {
                    _state.update { it.copy(work = null) }
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(work = null, problem = e.message ?: e.toString()) }
                }
            }
    }

    fun cancel() {
        job?.cancel()
    }

    /** [entry] downloaded beside the folder, checked, unzipped, checked again and imported. */
    private suspend fun install(
        entry: DafManifest.Entry,
        asset: DafReleases.Asset,
        onBytes: (Long) -> Unit,
    ) {
        dir.mkdirs()
        // Not .json: the folder's reading takes only those
        val gz = File(dir, "${entry.file}.download")
        val json = File(dir, "${entry.tractate.replace(UNSAFE, "_")}.json.download")
        try {
            fetchFile(asset.url, gz, entry.size, entry.sha256, onBytes)
            withContext(io) {
                GZIPInputStream(gz.inputStream()).use { input -> json.outputStream().use { input.copyTo(it) } }
                check(sha256(json).equals(entry.jsonSha256, ignoreCase = true)) { "${entry.file}: the layout is not the manifest's" }
                checkNotNull(layouts.import(json)) { "${entry.file} is not a layout file" }
            }
        } finally {
            gz.delete()
            json.delete()
        }
    }

    private fun installedOf(manifest: DafManifest?): Map<String, Installed> =
        manifest?.tractates.orEmpty().associate { entry ->
            val file = layouts.fileFor(entry.tractate) ?: layouts.fileFor(entry.tractateHe)
            entry.tractate to
                when {
                    file == null -> Installed.MISSING
                    sha256(file).equals(entry.jsonSha256, ignoreCase = true) -> Installed.CURRENT
                    else -> Installed.OTHER
                }
        }

    private fun versionsOf(manifest: DafManifest?): Map<String, Int> =
        buildMap {
            for (entry in manifest?.tractates.orEmpty()) {
                (layouts.versionOf(entry.tractate) ?: layouts.versionOf(entry.tractateHe))?.let { put(entry.tractate, it) }
            }
        }

    companion object {
        private val UNSAFE = Regex("[^A-Za-z0-9 _-]")
        private val shared = ConcurrentHashMap<File, DafDownloads>()

        /** The downloads into [layouts]' folder, one for it. */
        fun of(layouts: DafLayouts): DafDownloads = shared.getOrPut(layouts.dir) { DafDownloads(layouts, layouts.dir) }

        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return HexFormat.of().formatHex(digest.digest())
        }
    }
}

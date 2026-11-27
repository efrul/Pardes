package io.github.kdroidfilter.seforimapp.features.sefaria.pack

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.onboarding.download.HttpFileDownloader
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaData
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaDb
import io.github.kdroidfilter.seforimapp.features.settings.downloads.StartOffers
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

// Pardes: Sefaria's data as a pack of its own, the user's word of 2026-11-27 ("עשה זאת ויציע אוטומטית ויזהה עדכונים
// לאותו קובץ"): released in Pardes's repository as sefaria-<n> (.github/workflows/sefaria-release.yml), the app
// downloading it into the library's folder, offering it at start while it is missing and a newer one once it is out
// (StartOffers), from Settings › ניהול נתונים (SefariaPackCard), as the daf view's layouts are (DafDownloads).

/**
 * The pack: its release's manifest ([SefariaManifest]) names its one file, the data's SQLite file gzipped, with its
 * sha256 and the file's own; the app puts the file in [DIR_NAME] beside the library, which a library's full update
 * leaves (DatabaseCleanupUseCase deletes the folder's `*.db`, not this folder) and a library's move carries
 * (LibraryLocation.NAMES).
 */
object SefariaPack {
    const val DIR_NAME = "pardes-sefaria"
    const val DB_NAME = "sefaria.db"
    const val MANIFEST_NAME = "pardes-sefaria.manifest.json"

    /** The release's file: the data's SQLite file gzipped. */
    const val FILE_NAME = "pardes-sefaria.db.gz"

    /** Where a download waits beside the library until it is checked and put in place. */
    const val DOWNLOAD_DIR = ".pardes-sefaria-download"

    private val TAG = Regex("""sefaria-(\d+)""")

    /** A release of the pack: its number, its page and its files by name. */
    data class Available(
        val tag: String,
        val number: Int,
        val page: String?,
        val assets: Map<String, SemanticPackReleases.Asset>,
    )

    /** Of [releases], the published one of the highest number with a manifest; null when none is out. */
    fun select(releases: List<SemanticPackReleases.Release>): Available? =
        releases
            .asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { release ->
                TAG.matchEntire(release.tag)?.let { match ->
                    Available(release.tag, match.groupValues[1].toInt(), release.page, release.assets.associateBy { it.name })
                }
            }.filter { MANIFEST_NAME in it.assets }
            .maxByOrNull { it.number }

    /** The pack the library's folder [dir] has, its number (0 when its file doesn't say); null when it has none. */
    fun installedIn(dir: File): Int? = SefariaDb.open(SefariaData.fileIn(dir))?.use { it.pack }
}

/** A pack's manifest: its number, its file (the data gzipped), its size and sha256, and the data file's own. */
@Serializable
data class SefariaManifest(
    val format: Int,
    val pack: Int,
    val file: String,
    val size: Long,
    val sha256: String,
    val dbSize: Long,
    val dbSha256: String,
    /** The day of Sefaria's data, as its export names it. */
    val dump: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseOrNull(text: String): SefariaManifest? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.format == SefariaDb.FORMAT }
    }
}

/** Whether a start offers the pack while it is missing, and the newest pack a start offered. */
object SefariaPackSettings : SettingsGroup("sefaria_pack") {
    val prompt by boolean(true)

    // The number of the newest pack offered at start, as a float (the groups' codecs): offered once
    val offered by float(0f)
}

/**
 * A start that offers Sefaria's data opens Settings › ניהול נתונים on its card (StartOffers), as the semantic search's
 * (SemanticPackPrompt): set as the settings open, taken once by the card to come into view.
 */
object SefariaPackPrompt {
    private val focus = AtomicBoolean(false)

    fun takeFocus(): Boolean = focus.getAndSet(false).also { StartOffers.take(SettingsDestination.Data) }

    internal fun opened() = focus.set(true)
}

/**
 * Downloading the pack into the library's folder ([dir], read when asked): the newest release and its manifest, the
 * pack the folder has, its file downloaded, checked, unzipped, checked again and put in place, the data in use closed
 * and read again ([SefariaData]). One for the app ([shared]): a download goes on when the card that started it closes.
 */
class SefariaDownloads(
    private val dir: () -> File?,
    private val releases: suspend () -> List<SemanticPackReleases.Release> = { SemanticPackReleases().list() },
    private val fetchText: suspend (url: String) -> String = { HttpFileDownloader().downloadText(it) },
    private val fetchFile: suspend (url: String, dest: File, size: Long, sha256: String, onBytes: (Long) -> Unit) -> Unit =
        { url, dest, size, sha256, onBytes -> HttpFileDownloader().download(url, dest, size, sha256, onBytes) },
    // The app's lifetime: a download goes on when the screen that started it closes
    @StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    // Where files are read and written: a test's own dispatcher, to wait for them
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** A download under way, its bytes. */
    data class Work(
        val done: Long,
        val total: Long,
    )

    data class State(
        val checking: Boolean = false,
        /** Whether the releases were read: [release] null after it means none is out. */
        val checked: Boolean = false,
        val release: SefariaPack.Available? = null,
        val manifest: SefariaManifest? = null,
        /** The pack the folder has; null when it has none. */
        val installed: Int? = null,
        val work: Work? = null,
        /** Why the last check, download or removal stopped. */
        val problem: String? = null,
    ) {
        /** Whether a newer pack than the folder's is out (or one is, and the folder has none). */
        val newer: Boolean get() = manifest != null && (installed == null || manifest.pack > installed)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    /** Reads what the folder has, and the newest release and its manifest unless they were read and [force] is false. */
    fun refresh(force: Boolean = false) {
        val now = _state.value
        if (now.checking || now.work != null) return
        scope.launch { recount() }
        if (now.checked && now.problem == null && !force) return
        _state.update { it.copy(checking = true, problem = null) }
        scope.launch {
            val result =
                runSuspendCatching {
                    val release = SefariaPack.select(releases())
                    val manifest =
                        release?.let { r ->
                            val asset = r.assets.getValue(SefariaPack.MANIFEST_NAME)
                            SefariaManifest.parseOrNull(fetchText(asset.url)) ?: throw IOException("Unknown manifest format")
                        }
                    release to manifest
                }
            _state.update { s ->
                result.fold(
                    { (release, manifest) -> s.copy(checking = false, checked = true, release = release, manifest = manifest) },
                    { s.copy(checking = false, checked = true, problem = it.message ?: it.toString()) },
                )
            }
        }
    }

    /** Reads again the pack the folder has: one may have been put in place or taken out since. */
    suspend fun recount() {
        val installed = withContext(io) { dir()?.let(SefariaPack::installedIn) }
        _state.update { it.copy(installed = installed) }
    }

    /** Downloads the newest pack and puts it in place of the folder's; a failure is said, the folder's left as it was. */
    fun download() {
        val now = _state.value
        val release = now.release ?: return
        val manifest = now.manifest ?: return
        val folder = dir() ?: return
        if (now.work != null) return
        _state.update { it.copy(problem = null, work = Work(0, manifest.size)) }
        job =
            scope.launch {
                try {
                    val asset = release.assets[manifest.file] ?: throw IOException("${manifest.file} is not in ${release.tag}")
                    install(folder, manifest, asset) { read -> _state.update { it.copy(work = Work(read, manifest.size)) } }
                    _state.update { it.copy(work = null, installed = manifest.pack) }
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

    /** Takes the folder's pack out: its features show nothing until one is downloaded again. */
    fun remove() {
        val folder = dir() ?: return
        if (_state.value.work != null) return
        scope.launch {
            val removed =
                withContext(io) {
                    SefariaData.close()
                    File(folder, SefariaPack.DIR_NAME).deleteRecursively()
                }
            SefariaData.changed()
            _state.update { it.copy(installed = null, problem = if (removed) null else "${SefariaPack.DIR_NAME} not removed") }
        }
    }

    /** The pack's file downloaded beside the folder, checked, unzipped, checked again and put in place of the folder's. */
    private suspend fun install(
        folder: File,
        manifest: SefariaManifest,
        asset: SemanticPackReleases.Asset,
        onBytes: (Long) -> Unit,
    ) {
        val work = File(folder, SefariaPack.DOWNLOAD_DIR).apply { mkdirs() }
        val gz = File(work, manifest.file)
        val db = File(work, SefariaPack.DB_NAME)
        try {
            fetchFile(asset.url, gz, manifest.size, manifest.sha256, onBytes)
            withContext(io) {
                GZIPInputStream(gz.inputStream()).use { input -> db.outputStream().use { input.copyTo(it) } }
                check(
                    DafDownloads.sha256(db).equals(manifest.dbSha256, ignoreCase = true),
                ) { "${manifest.file}: the data is not the manifest's" }
                checkNotNull(SefariaDb.open(db)?.use { it.pack }) { "${manifest.file} is not Sefaria's data of format ${SefariaDb.FORMAT}" }
                // The data in use closed first: Windows renames no open file
                SefariaData.close()
                val target = File(folder, SefariaPack.DIR_NAME).apply { mkdirs() }
                Files.move(db.toPath(), File(target, SefariaPack.DB_NAME).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            SefariaData.changed()
        } finally {
            work.deleteRecursively()
        }
    }

    companion object {
        /** The downloads into the library's folder (SefariaData's, read when asked), one for the app. */
        val shared: SefariaDownloads by lazy {
            SefariaDownloads(dir = { SefariaData.appSettings?.let(LibraryLocation::currentDir) })
        }
    }
}

package io.github.kdroidfilter.seforimapp.android.search

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PendingSemanticPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackInstaller
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.settings.semantic.SemanticPackPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Port of Pardes desktop's features/settings/semantic/SemanticPackViewModel: Pardes's semantic search in Settings ›
 * ניהול נתונים, the pack beside the library ([InstalledPack]), the newest pack ([SemanticPackReleases.newest]), and
 * installing one, downloaded or from files the user picked ([SemanticPackInstaller]); it goes in at the next start
 * ([PendingSemanticPack]). Android: one per process rather than per screen, as [LibraryInstaller] is, so that a
 * download of 2 GB goes on when the screen is left, with [SemanticPackService] keeping the process in the foreground
 * meanwhile.
 */
class SemanticPackManager private constructor(private val app: ZayitApplication) {
    sealed interface Available {
        data object Checking : Available

        data class Pack(
            val pack: SemanticPackReleases.Available,
        ) : Available

        /** No pack released yet. */
        data object NoneYet : Available

        /** Neither the GitHub API nor the site's list could be read. */
        data object Unreachable : Available
    }

    sealed interface Work {
        data object Idle : Work

        data class Downloading(
            val read: Long,
            val total: Long,
        ) : Work

        data class Unpacking(
            val progress: Float,
        ) : Work

        /** Unpacked and checked, waiting for the next start. */
        data object Ready : Work

        data class Failed(
            val problem: Throwable,
        ) : Work
    }

    data class State(
        val hasLibrary: Boolean = true,
        val installed: InstalledPack = InstalledPack.None,
        val available: Available = Available.Checking,
        val work: Work = Work.Idle,
        /** Why the last start did not put the pack in place, or null. */
        val startFailure: String? = null,
        /** Whether a start without a pack in use opens the settings on the card ([SemanticPackPrompt]). */
        val promptAtStart: Boolean = true,
    ) {
        val busy: Boolean get() = work is Work.Downloading || work is Work.Unpacking
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val releases = SemanticPackReleases()
    private val _state = MutableStateFlow(State(promptAtStart = app.prefs.semanticPackPrompt.value))
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var job: Job? = null

    /** Android: the request is kept in the app's own files. */
    private val markerDir get() = app.filesDir

    init {
        scope.launch {
            val ready = PendingSemanticPack.read(markerDir) != null
            _state.update { it.copy(startFailure = PendingSemanticPack.takeFailure(markerDir), work = if (ready) Work.Ready else it.work) }
        }
    }

    fun refresh() {
        scope.launch {
            val store = app.libraryStore
            val dir = store.libraryDir
            val hasLibrary = LibraryStore.hasDatabase(dir)
            _state.update { it.copy(hasLibrary = hasLibrary, installed = InstalledPack.read(dir)) }
            // Without a library the card asks for it, and offers no pack
            if (!hasLibrary) return@launch
            val available =
                runSuspendCatching { releases.newest(InstalledPack.libraryVersion(dir)) }
                    .map { pack -> pack?.let { Available.Pack(it) } ?: Available.NoneYet }
                    .getOrElse {
                        Log.w(TAG, "No pack list: ${it.message}")
                        Available.Unreachable
                    }
            _state.update { it.copy(available = available) }
        }
    }

    /** Downloads the newest pack and unpacks it, ready for the next start. */
    fun install() {
        val pack = (_state.value.available as? Available.Pack)?.pack ?: return
        launchInstall(Work.Downloading(0, pack.bundleBytes)) { installer ->
            // Android: read as it downloads, straight into the staging folder (SemanticPackInstaller.online)
            val bundle = installer.online(pack)
            stage(installer, bundle) { read, total -> Work.Downloading(read, total) }
        }
    }

    /** Installs the pack the files [uris] are (picked together), from the user's own files (left as they are). */
    fun import(uris: List<Uri>) =
        launchInstall(Work.Unpacking(0f)) { installer ->
            val source = installer.resolve(uris.map(::picked))
            stage(installer, source) { read, total -> Work.Unpacking(read.toFloat() / total.coerceAtLeast(1L)) }
        }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(work = Work.Idle) }
    }

    fun dismiss() = _state.update { it.copy(work = if (it.work is Work.Failed) Work.Idle else it.work, startFailure = null) }

    /** Starts the app afresh, which puts the staged pack in place before the search opens. */
    fun restart() = app.restart()

    fun setPromptAtStart(enabled: Boolean) {
        app.prefs.setSemanticPackPrompt(enabled)
        _state.update { it.copy(promptAtStart = enabled) }
    }

    private fun launchInstall(
        initial: Work,
        block: suspend (SemanticPackInstaller) -> Unit,
    ) {
        if (job?.isActive == true) return
        // Before the service starts, as LibraryInstaller does: the service stops at a state without work (a previous
        // failure, Idle)
        _state.update { it.copy(work = initial, startFailure = null) }
        SemanticPackService.start(app)
        job =
            scope
                .launch {
                    val installer = SemanticPackInstaller(app.libraryStore.libraryDir) { LibraryInstaller.get(app).networkAvailable() }
                    runSuspendCatching { block(installer) }.onFailure { problem ->
                        // A read that cancel() broke off is no failure
                        if (!isActive) return@onFailure
                        Log.w(TAG, "Semantic pack not installed: ${problem.message}", problem)
                        _state.update { it.copy(work = Work.Failed(problem)) }
                    }
                    refresh()
                }.apply {
                    // A progress report that came in as it was stopped
                    invokeOnCompletion { cause -> if (cause is CancellationException) _state.update { it.copy(work = Work.Idle) } }
                }
    }

    private suspend fun stage(
        installer: SemanticPackInstaller,
        source: SemanticPackInstaller.Source,
        progress: (read: Long, total: Long) -> Work,
    ) {
        installer.stage(source, markerDir) { read, total -> _state.update { it.copy(work = progress(read, total)) } }
        installer.schedule(markerDir)
        _state.update { it.copy(work = Work.Ready) }
    }

    /** A file the system's picker gave: its name and size from the provider, read when the pack is unpacked. */
    private fun picked(uri: Uri): SemanticPackInstaller.PackFile {
        val resolver = app.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> cursor.getString(i)?.let { name = it } }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
                }
            }
        }
        val shown = name
        return SemanticPackInstaller.PackFile(shown, size) { resolver.openInputStream(uri) ?: throw IOException("Cannot open $shown") }
    }

    companion object {
        private const val TAG = "PardesSemantic"

        @Volatile
        private var instance: SemanticPackManager? = null

        fun get(context: Context): SemanticPackManager =
            instance ?: synchronized(this) {
                instance ?: SemanticPackManager(context.applicationContext as ZayitApplication).also { instance = it }
            }
    }
}

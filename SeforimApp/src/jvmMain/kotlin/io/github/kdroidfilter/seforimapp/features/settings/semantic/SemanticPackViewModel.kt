package io.github.kdroidfilter.seforimapp.features.settings.semantic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.core.runtime.AppRestarter.restartApplication
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PendingSemanticPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackInstaller
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Settings › ניהול נתונים: Pardes's semantic search, the pack beside the library ([InstalledPack]), the newest pack for
 * the library installed ([SemanticPackReleases]), and installing one, downloaded or from files the user downloaded
 * ([SemanticPackInstaller]); it goes in at the next start ([PendingSemanticPack]).
 */
@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class SemanticPackViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {
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
    ) {
        val busy: Boolean get() = work is Work.Downloading || work is Work.Unpacking
    }

    private val releases = SemanticPackReleases()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val ready = PendingSemanticPack.read() != null
            _state.update { it.copy(startFailure = PendingSemanticPack.takeFailure(), work = if (ready) Work.Ready else it.work) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = LibraryLocation.currentDir(appSettings)
            val hasLibrary = LibraryLocation.exists(dir)
            _state.update { it.copy(hasLibrary = hasLibrary, installed = InstalledPack.read(dir)) }
            // Without a library the card asks for it, and offers no pack
            if (!hasLibrary) return@launch
            val available =
                runSuspendCatching { releases.newest(LibraryLocation.version(dir)) }
                    .map { pack -> pack?.let { Available.Pack(it) } ?: Available.NoneYet }
                    .getOrElse {
                        warnln { "[SemanticPackViewModel] No pack list: ${it.message}" }
                        Available.Unreachable
                    }
            _state.update { it.copy(available = available) }
        }
    }

    /** Downloads the newest pack for the library and unpacks it, ready for the next start. */
    fun install() {
        val pack = (_state.value.available as? Available.Pack)?.pack ?: return
        launchInstall { installer ->
            _state.update { it.copy(work = Work.Downloading(0, pack.bundleBytes)) }
            // A download that failed keeps its whole files for the next try; one stopped does not
            val bundle =
                try {
                    installer.download(pack) { read, total -> _state.update { it.copy(work = Work.Downloading(read, total)) } }
                } catch (e: CancellationException) {
                    installer.discardDownload()
                    throw e
                }
            try {
                stage(installer, bundle)
            } finally {
                installer.discardDownload()
            }
        }
    }

    /** Installs the pack [picked] is a file of, from the user's own files (left as they are). */
    fun import(picked: File) = launchInstall { installer -> stage(installer, installer.resolve(picked)) }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(work = Work.Idle) }
    }

    fun dismiss() = _state.update { it.copy(work = if (it.work is Work.Failed) Work.Idle else it.work, startFailure = null) }

    fun restart() = restartApplication()

    private fun launchInstall(block: suspend (SemanticPackInstaller) -> Unit) {
        if (job?.isActive == true) return
        job =
            viewModelScope
                .launch(Dispatchers.IO) {
                    val installer = SemanticPackInstaller(LibraryLocation.currentDir(appSettings))
                    runSuspendCatching { block(installer) }.onFailure { problem ->
                        warnln(problem) { "[SemanticPackViewModel] Semantic pack not installed: ${problem.message}" }
                        _state.update { it.copy(work = Work.Failed(problem)) }
                    }
                }.apply {
                    // A progress report that came in as it was stopped
                    invokeOnCompletion { cause -> if (cause is CancellationException) _state.update { it.copy(work = Work.Idle) } }
                }
    }

    private suspend fun stage(
        installer: SemanticPackInstaller,
        source: SemanticPackInstaller.Source,
    ) {
        _state.update { it.copy(work = Work.Unpacking(0f), startFailure = null) }
        installer.stage(source) { progress -> _state.update { it.copy(work = Work.Unpacking(progress)) } }
        installer.schedule()
        _state.update { it.copy(work = Work.Ready) }
    }
}

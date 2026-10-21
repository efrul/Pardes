package io.github.kdroidfilter.seforimapp.features.settings.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.core.runtime.AppRestarter.restartApplication
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.branding.BrandLibraries
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.ForeignLibraries
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.database.PendingLibraryMove
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Settings › ניהול נתונים: where the library is, moving it to another folder or drive (run at the next start,
 * [PendingLibraryMove]), and the copies kept by earlier moves ([LibraryLocation]).
 */
@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class LibraryLocationViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {
    /** A folder holding a library, as the card shows it. */
    data class Place(
        val dir: String,
        val drive: String,
        val bytes: Long,
        val freeBytes: Long,
    )

    data class State(
        val current: Place? = null,
        val exists: Boolean = true,
        val isDefault: Boolean = true,
        /** The other app whose library is used in place, or null. */
        val foreignOwner: String? = null,
        val copies: List<Place> = emptyList(),
        /** The folder the library would move to, while the user confirms. */
        val proposed: String? = null,
        val keepCopy: Boolean = false,
        val problem: LibraryLocation.Problem? = null,
        val notDeleted: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = LibraryLocation.currentDir(appSettings)
            val foreign = ForeignLibraries.isForeign(dir)
            _state.update {
                it.copy(
                    current = place(dir),
                    exists = LibraryLocation.exists(dir),
                    isDefault = dir.absoluteFile == LibraryLocation.defaultDir(),
                    foreignOwner = if (foreign) BrandLibraries.OTHER_APP_NAME else null,
                    copies = LibraryLocation.copies(appSettings).map(::place),
                )
            }
        }
    }

    /** The user picked [folder]: the library would go to its own folder there, checked at once. */
    fun choose(folder: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val to = LibraryLocation.destination(folder)
            _state.update { it.copy(proposed = to.path, problem = check(to, it.keepCopy), notDeleted = emptyList()) }
        }
    }

    fun backToDefault() = choose(LibraryLocation.defaultDir())

    fun setKeepCopy(keep: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { s -> s.copy(keepCopy = keep, problem = s.proposed?.let { check(File(it), keep) }) }
        }
    }

    fun cancel() = _state.update { it.copy(proposed = null, problem = null) }

    /** Schedules the move and restarts: it runs before anything opens the library. */
    fun confirmMove() {
        val s = _state.value
        val to = s.proposed ?: return
        if (s.problem != null) return
        PendingLibraryMove.schedule(PendingLibraryMove.Request(LibraryLocation.currentDir(appSettings), File(to), s.keepCopy))
        restartApplication()
    }

    /** Opens a kept copy instead of the current library (nothing is copied), after a restart. */
    fun switchTo(dir: String) {
        runCatching { LibraryLocation.switchTo(File(dir), appSettings) }
            .onSuccess { restartApplication() }
            .onFailure { _state.update { s -> s.copy(problem = LibraryLocation.Problem.NoLibrary) } }
    }

    fun deleteCopy(dir: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val failed = LibraryLocation.deleteCopy(File(dir), appSettings)
            _state.update { it.copy(notDeleted = failed) }
            refresh()
        }
    }

    private fun check(
        to: File,
        keep: Boolean,
    ): LibraryLocation.Problem? {
        val from = LibraryLocation.currentDir(appSettings)
        return LibraryLocation.check(from, to, keep || ForeignLibraries.isForeign(from))
    }

    private fun place(dir: File) =
        Place(
            dir = dir.path,
            drive = LibraryLocation.driveOf(dir),
            bytes = LibraryLocation.size(dir),
            freeBytes = LibraryLocation.freeBytes(dir),
        )
}

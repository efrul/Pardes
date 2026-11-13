package io.github.kdroidfilter.seforimapp.features.settings.downloads

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafLayouts
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.newerFor
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.features.settings.semantic.SemanticPackPrompt
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * What a start has to offer for download, the settings opened on it (main.kt): the semantic search's files when none
 * are in use (at every start, until they are or the user unticks it on their card, [SemanticPackPrompt]) or a newer
 * version of them than the one in use, and a newer version of an installed tractate's daf layout. The settings open on
 * the first page that has one, its card marked and brought into view; another page that has one is marked in the
 * sidebar ([marked]). A newer version is offered once: the version offered is kept (AppSettings), and a start after the
 * settings were closed on it does not open them again for it, until the next one. The releases are read once for both
 * ([ReleaseLists]), and only while the app checks for updates by itself (Settings › כללי).
 */
object StartOffers {
    private val FOCUS_WAIT = 5.seconds

    // How long the releases may take at start: past it, the start offers what it knows without them
    private val NETWORK_WAIT = 20.seconds

    private val _marked = MutableStateFlow<Set<SettingsDestination>>(emptySet())

    /** The settings' pages that have something new this start, until their card was shown. */
    val marked: StateFlow<Set<SettingsDestination>> = _marked.asStateFlow()

    /** What the start found: the semantic search's files missing, a newer pack or daf release than the one in use. */
    data class Found(
        val missingPack: Boolean = false,
        val installedPack: Int? = null,
        val newestPack: Int? = null,
        // The newest release of layouts, when it has a newer version of a tractate the folder has
        val newerDaf: Int? = null,
    )

    /**
     * Opens the settings with [open] on the first page of what this start offers: once the session is [restored], over a
     * window of [desktopManager] (the focused one, unless none is within a few seconds), and after an update dialog shown
     * at start ([updateDialogVisible]).
     */
    suspend fun openAtStart(
        appSettings: AppSettings,
        restored: Flow<Boolean>,
        desktopManager: DesktopManager,
        updateDialogVisible: Flow<Boolean>,
        open: (SettingsDestination) -> Unit,
    ) {
        val found = withContext(Dispatchers.IO) { find(appSettings) }
        val pages = pagesFor(found, appSettings.getOfferedSemanticPack(), appSettings.getOfferedDafRelease())
        if (pages.isEmpty()) return
        restored.first { it }
        desktopManager.windows.first { it.isNotEmpty() }
        withTimeoutOrNull(FOCUS_WAIT) { desktopManager.focusedWindowId.first { it.isNotEmpty() } }
        updateDialogVisible.first { !it }
        found.newestPack?.takeIf { SettingsDestination.Data in pages && !found.missingPack }?.let(appSettings::setOfferedSemanticPack)
        found.newerDaf?.takeIf { SettingsDestination.Daf in pages }?.let(appSettings::setOfferedDafRelease)
        if (SettingsDestination.Data in pages) SemanticPackPrompt.opened()
        _marked.value = pages.toSet()
        open(pages.first())
    }

    /**
     * The pages [found] opens the settings on, in order: the semantic search's (ניהול נתונים) when its files are missing,
     * or a newer pack than the one in use is out that was not offered yet ([offeredPack]); the daf view's when a newer
     * release of layouts is out for an installed tractate, not offered yet ([offeredDaf]).
     */
    internal fun pagesFor(
        found: Found,
        offeredPack: Int,
        offeredDaf: Int,
    ): List<SettingsDestination> =
        buildList {
            val installed = found.installedPack
            val newest = found.newestPack
            val newerPack = installed != null && newest != null && newest > installed && newest > offeredPack
            if (found.missingPack || newerPack) add(SettingsDestination.Data)
            if (found.newerDaf != null && found.newerDaf > offeredDaf) add(SettingsDestination.Daf)
        }

    /** Takes [page]'s mark once its card is shown; whether it had one. */
    fun take(page: SettingsDestination): Boolean {
        val had = page in _marked.value
        _marked.update { it - page }
        return had
    }

    // What this start has to offer: the library's folder read, and the releases while the app checks for updates
    private suspend fun find(appSettings: AppSettings): Found {
        val missingPack = SemanticPackPrompt.shouldOpen(appSettings)
        if (!appSettings.isAutoUpdateCheckEnabled()) return Found(missingPack = missingPack)
        val dir = LibraryLocation.currentDir(appSettings)
        if (!LibraryLocation.exists(dir)) return Found(missingPack = missingPack)
        val installed = (InstalledPack.read(dir) as? InstalledPack.InUse)?.pack
        return withTimeoutOrNull(NETWORK_WAIT) {
            val newestPack =
                if (installed == null) {
                    null
                } else {
                    runSuspendCatching { SemanticPackReleases().newest(LibraryLocation.version(dir))?.pack }
                        .onFailure { warnln { "[StartOffers] Semantic packs not read: ${it.message}" } }
                        .getOrNull()
                }
            Found(missingPack, installed, newestPack, newerDaf(dir))
        } ?: Found(missingPack = missingPack, installedPack = installed)
    }

    // The newest release of layouts when it has a newer version of a tractate the library's folder has, else null
    private suspend fun newerDaf(dir: File): Int? {
        val layouts = DafLayouts.of(dir)
        if (!layouts.hasAny()) return null
        val downloads = DafDownloads.of(layouts)
        downloads.refresh()
        val state = downloads.state.first { it.checked && !it.checking }
        val release = state.release ?: return null
        val tractates = state.manifest?.tractates.orEmpty()
        return release.number.takeIf { tractates.any { state.newerFor(it.tractate) != null } }
    }
}

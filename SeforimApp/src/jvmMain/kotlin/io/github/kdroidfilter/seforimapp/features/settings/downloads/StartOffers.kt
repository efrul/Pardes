package io.github.kdroidfilter.seforimapp.features.settings.downloads

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafLayouts
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.newerFor
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.sefaria.pack.SefariaPack
import io.github.kdroidfilter.seforimapp.features.sefaria.pack.SefariaPackPrompt
import io.github.kdroidfilter.seforimapp.features.sefaria.pack.SefariaPackSettings
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
 * ([ReleaseLists]), and only while the app checks for updates by itself (Settings › כללי). Pardes: so does Sefaria's
 * data (SefariaPack) on the same page, its own card: while it is missing and a pack is out (until the user unticks it),
 * and a newer pack once.
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
        // Pardes: Sefaria's data missing from the library's folder while a pack is out, the pack it has, the newest one out
        val missingSefaria: Boolean = false,
        val installedSefaria: Int? = null,
        val newestSefaria: Int? = null,
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
        val offeredSefaria = appSettings[SefariaPackSettings.offered].toInt()
        val pages = pagesFor(found, appSettings.getOfferedSemanticPack(), appSettings.getOfferedDafRelease(), offeredSefaria)
        if (pages.isEmpty()) return
        restored.first { it }
        desktopManager.windows.first { it.isNotEmpty() }
        withTimeoutOrNull(FOCUS_WAIT) { desktopManager.focusedWindowId.first { it.isNotEmpty() } }
        updateDialogVisible.first { !it }
        found.newestPack?.takeIf { SettingsDestination.Data in pages && !found.missingPack }?.let(appSettings::setOfferedSemanticPack)
        found.newerDaf?.takeIf { SettingsDestination.Daf in pages }?.let(appSettings::setOfferedDafRelease)
        // Each card of the page that has something new comes into view, Sefaria's offered once
        if (found.missingPack || newerPack(found, appSettings.getOfferedSemanticPack())) SemanticPackPrompt.opened()
        if (found.missingSefaria || newerSefaria(found, offeredSefaria)) {
            found.newestSefaria?.takeIf { !found.missingSefaria }?.let { appSettings[SefariaPackSettings.offered] = it.toFloat() }
            SefariaPackPrompt.opened()
        }
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
        offeredSefaria: Int = 0,
    ): List<SettingsDestination> =
        buildList {
            val sefaria = found.missingSefaria || newerSefaria(found, offeredSefaria)
            if (found.missingPack || newerPack(found, offeredPack) || sefaria) add(SettingsDestination.Data)
            if (found.newerDaf != null && found.newerDaf > offeredDaf) add(SettingsDestination.Daf)
        }

    // A newer semantic pack than the one in use, not offered yet
    private fun newerPack(
        found: Found,
        offered: Int,
    ): Boolean {
        val installed = found.installedPack
        val newest = found.newestPack
        return installed != null && newest != null && newest > installed && newest > offered
    }

    // Pardes: a newer pack of Sefaria's data than the folder's, not offered yet
    private fun newerSefaria(
        found: Found,
        offered: Int,
    ): Boolean {
        val installed = found.installedSefaria
        val newest = found.newestSefaria
        return installed != null && newest != null && newest > installed && newest > offered
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
        val dir = LibraryLocation.currentDir(appSettings)
        // Pardes: Sefaria's data, read from the folder: the pack it has, or none while the start offers one
        val library = LibraryLocation.exists(dir)
        val installedSefaria = if (library) SefariaPack.installedIn(dir) else null
        val wantSefaria = library && installedSefaria == null && appSettings[SefariaPackSettings.prompt]
        val offline = Found(missingPack = missingPack, installedSefaria = installedSefaria)
        if (!appSettings.isAutoUpdateCheckEnabled() || !library) return offline
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
            // The releases read once for both (ReleaseLists); the missing data offered only when a pack is out to download
            val newestSefaria =
                if (installedSefaria != null || wantSefaria) {
                    runSuspendCatching { SefariaPack.select(SefariaPack.releases())?.number }
                        .onFailure { warnln { "[StartOffers] Sefaria's packs not read: ${it.message}" } }
                        .getOrNull()
                } else {
                    null
                }
            val missingSefaria = wantSefaria && newestSefaria != null
            Found(missingPack, installed, newestPack, newerDaf(dir), missingSefaria, installedSefaria, newestSefaria)
        } ?: offline.copy(installedPack = installed)
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

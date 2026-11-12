package io.github.kdroidfilter.seforimapp.features.settings.semantic

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PendingSemanticPack
import io.github.kdroidfilter.seforimapp.features.settings.downloads.StartOffers
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A start whose library has no semantic pack in use opens Settings › ניהול נתונים on the semantic search card
 * (StartOffers), which then says what it is and how to install it: at every start, until a pack is in use or the user
 * unticks it on the card. So does a newer pack than the one in use, once.
 */
object SemanticPackPrompt {
    // Set as the settings open for the card, taken once by the card to come into view
    private val focus = AtomicBoolean(false)

    /** Whether this start opens the settings on the card. It reads the library's folder: off the UI thread. */
    fun shouldOpen(appSettings: AppSettings): Boolean =
        appSettings.isSemanticPackPromptEnabled() &&
            needsPack(LibraryLocation.currentDir(appSettings), PendingSemanticPack.read())

    /** The library at [libraryDir] is there and its semantic search is off, with no pack [pending] for the next start. */
    internal fun needsPack(
        libraryDir: File,
        pending: PendingSemanticPack.Request?,
    ): Boolean = LibraryLocation.exists(libraryDir) && pending == null && InstalledPack.read(libraryDir) !is InstalledPack.InUse

    /** Whether the settings were opened for the card, once; the page's mark in the sidebar goes with it. */
    fun takeFocus(): Boolean = focus.getAndSet(false).also { StartOffers.take(SettingsDestination.Data) }

    internal fun opened() = focus.set(true)
}

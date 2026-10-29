package io.github.kdroidfilter.seforimapp.features.settings.semantic

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PendingSemanticPack
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * A start whose library has no semantic pack in use opens Settings › ניהול נתונים on the semantic search card
 * (main.kt), which then says what it is and how to install it: at every start, until a pack is in use or the user
 * unticks it on the card.
 */
object SemanticPackPrompt {
    private val FOCUS_WAIT = 5.seconds

    // Set as the settings open for the card, taken once by the card to come into view
    private val focus = AtomicBoolean(false)

    /**
     * Opens the settings on the card with [open] when [shouldOpen]: once the session is [restored], over a window of
     * [desktopManager] (the focused one, unless none is within a few seconds), and after an update dialog shown at
     * start ([updateDialogVisible]).
     */
    suspend fun openAtStart(
        appSettings: AppSettings,
        restored: Flow<Boolean>,
        desktopManager: DesktopManager,
        updateDialogVisible: Flow<Boolean>,
        open: () -> Unit,
    ) {
        if (!withContext(Dispatchers.IO) { shouldOpen(appSettings) }) return
        restored.first { it }
        desktopManager.windows.first { it.isNotEmpty() }
        withTimeoutOrNull(FOCUS_WAIT) { desktopManager.focusedWindowId.first { it.isNotEmpty() } }
        updateDialogVisible.first { !it }
        opened()
        open()
    }

    /** Whether this start opens the settings on the card. It reads the library's folder: off the UI thread. */
    fun shouldOpen(appSettings: AppSettings): Boolean =
        appSettings.isSemanticPackPromptEnabled() &&
            needsPack(LibraryLocation.currentDir(appSettings), PendingSemanticPack.read())

    /** The library at [libraryDir] is there and its semantic search is off, with no pack [pending] for the next start. */
    internal fun needsPack(
        libraryDir: File,
        pending: PendingSemanticPack.Request?,
    ): Boolean = LibraryLocation.exists(libraryDir) && pending == null && InstalledPack.read(libraryDir) !is InstalledPack.InUse

    /** Whether the settings were opened for the card, once. */
    fun takeFocus(): Boolean = focus.getAndSet(false)

    internal fun opened() = focus.set(true)
}

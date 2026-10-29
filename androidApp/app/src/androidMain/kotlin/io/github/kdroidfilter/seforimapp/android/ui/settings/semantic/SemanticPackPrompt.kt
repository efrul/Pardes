// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/settings/semantic/SemanticPackPrompt.kt, same
// package and names, so that desktop's SemanticPackPromptTest runs on the port unchanged. Changes, marked "Android:":
//  - MainActivity runs it once the start is Ready, its session in by then; the settings are an overlay of the one window,
//    so there is no window to wait for, and a start that came for a page of its own (a link, a launcher shortcut, a
//    widget) is left alone;
//  - the library's folder is LibraryStore's, the preference AppPrefs', and a device that cannot run the model shows no
//    card (SemanticSearchSupport), so it opens nothing.
package io.github.kdroidfilter.seforimapp.features.settings.semantic

import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.search.SemanticSearchSupport
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PendingSemanticPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A start whose library has no semantic pack in use opens Settings › ניהול נתונים on the semantic search card
 * (MainActivity), which then says what it is and how to install it: at every start, until a pack is in use or the user
 * unticks it on the card.
 */
object SemanticPackPrompt {
    // Set as the settings open for the card, taken once by the card to come into view
    private val focus = AtomicBoolean(false)

    /**
     * Opens the settings on the card with [open] when [shouldOpen], after an update dialog shown at start
     * ([updateDialogVisible]). Android: the session is in by the time MainActivity runs it, and there is one window.
     */
    suspend fun openAtStart(
        prefs: AppPrefs,
        libraryDir: File,
        markerDir: File,
        updateDialogVisible: Flow<Boolean>,
        open: () -> Unit,
    ) {
        if (!withContext(Dispatchers.IO) { shouldOpen(prefs, libraryDir, markerDir) }) return
        updateDialogVisible.first { !it }
        opened()
        open()
    }

    /** Whether this start opens the settings on the card. It reads the library's folder: off the UI thread. */
    fun shouldOpen(
        prefs: AppPrefs,
        libraryDir: File,
        markerDir: File,
    ): Boolean =
        // Android: the card is there only where the model can run
        SemanticSearchSupport.supported &&
            prefs.semanticPackPrompt.value &&
            needsPack(libraryDir, PendingSemanticPack.read(markerDir))

    /** The library at [libraryDir] is there and its semantic search is off, with no pack [pending] for the next start. */
    internal fun needsPack(
        libraryDir: File,
        pending: PendingSemanticPack.Request?,
    ): Boolean = LibraryStore.hasDatabase(libraryDir) && pending == null && InstalledPack.read(libraryDir) !is InstalledPack.InUse

    /** Whether the settings were opened for the card, once. */
    fun takeFocus(): Boolean = focus.getAndSet(false)

    internal fun opened() = focus.set(true)
}

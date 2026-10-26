package io.github.kdroidfilter.seforimapp.android.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.DatabaseVersion
import io.github.kdroidfilter.seforimapp.android.data.InstallPurpose
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateEvents
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateService
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateState
import io.github.kdroidfilter.seforimapp.android.delta.DeltaStrings
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.releaseReadGrants
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.rememberWithNotificationPermission
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.takeReadGrants
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop 0.6.10 `features/settings/dbupdate/DbDeltaUpdateSection.kt` in Settings › ניהול נתונים: the status text
 * (idle, checking, downloading, applying, done, error, up to date, too old), a progress bar while a phase runs, the
 * "check for updates" button (disabled while busy) and "dismiss" for a message. Desktop's English literals are in
 * Hebrew ([DeltaStrings]). For touch the check stops at the update it found, with its download size and the
 * metered-network warning, and runs only on "הורד עכשיו"; a run can be cancelled until the database part commits.
 * The state lives in [DbDeltaUpdateService], so the section reopens on a running update.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DbDeltaUpdateSection() {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    val service = remember(app) { DbDeltaUpdateService.get(app) }
    val state by service.state.collectAsState()
    val busy = state.phase != null
    val hasMessage = state.message.isNotEmpty() || state.errorMessage != null
    TrackSectionVisibility(service)
    val version by produceState<String?>(null, state.lastAppliedCount) {
        value = withContext(Dispatchers.IO) { DatabaseVersion.current(app.libraryStore).orEmpty() }
    }
    // Android: the version an update from files reached (release_info.txt keeps its date then).
    val localVersion by produceState<Int?>(null, state.lastAppliedCount) {
        value = withContext(Dispatchers.IO) { runCatching { service.localUpdateVersion() }.getOrNull() }
    }
    val scope = rememberCoroutineScope()
    // Why a picked folder was not used (nothing to update from, or a library unpacked as on a computer), or null.
    var folderError by remember { mutableStateOf<String?>(null) }
    // Android: an update from files picked on the device. A delta's patch and manifest go to the service here; the
    // parts of a full release (or a seforim.db) to the installer, in the database update flow over the app.
    fun updateFrom(files: List<Pair<Uri, String>>) {
        val delta = files.filter { LibraryInstaller.isDeltaFileName(it.second) }
        // A patch without its manifest goes here too, to be told so: as an install it would read as a .zst database.
        if (delta.isNotEmpty()) {
            // The other picked files are not read: their grants go back now (a picked folder's stays for the patches).
            (files - delta.toSet()).forEach { runCatching { context.contentResolver.releasePersistableUriPermission(it.first, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            service.applyFromFiles(delta.map { it.first })
        } else {
            val installer = LibraryInstaller.get(app)
            installer.purpose = InstallPurpose.Update
            installer.reset()
            installer.importUris(files.map { it.first })
            app.manualLibraryUpdate.value = true
        }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // The services read the files in the background, possibly after the activity is gone.
        context.takeReadGrants(uris)
        scope.launch { updateFrom(withContext(Dispatchers.IO) { uris.map { it to displayName(context, it) } }) }
    }
    // A whole folder (the parts of a release are many files): its update files are taken, the rest ignored.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        // The folder's grant covers the files in it.
        context.takeReadGrants(listOf(tree))
        scope.launch {
            val entries = withContext(Dispatchers.IO) { folderEntries(context, tree) }
            val files = entries.filter { !it.isDirectory && (LibraryInstaller.isDeltaFileName(it.name) || LibraryInstaller.isInstallFileName(it.name)) }
            val names = entries.map { it.name }.toSet()
            // A library unpacked as on a computer (the database beside its indexes, dictionary or catalog): the install
            // would take the database alone and drop the rest, so the folder is refused (the copy script moves it all).
            val unpackedLibrary = files.none { LibraryInstaller.isDeltaFileName(it.name) } &&
                LibraryStore.DB_NAME in names && UNPACKED_LIBRARY_ITEMS.any { it in names }
            val error = when {
                files.isEmpty() -> DeltaStrings.folderEmpty
                unpackedLibrary -> DeltaStrings.folderIsLibrary
                else -> null
            }
            folderError = error
            if (error != null) context.releaseReadGrants(listOf(tree)) else updateFrom(files.map { it.uri to it.name })
        }
    }
    val pick = rememberWithNotificationPermission {
        // An earlier folder's refusal does not concern these files (it would show again under their result).
        folderError = null
        pickFiles.launch(arrayOf("*/*"))
    }
    // Bundle files already in the app's own folders (copied there from a computer, onto the card in a card reader,
    // say): Android's file picker does not show Android/data.
    val localBundle by produceState(emptyList<File>(), busy) {
        value = withContext(Dispatchers.IO) { runCatching { LibraryInstaller.get(app).findLocalBundle() }.getOrDefault(emptyList()) }
    }
    val useLocal = rememberWithNotificationPermission {
        folderError = null
        val installer = LibraryInstaller.get(app)
        installer.purpose = InstallPurpose.Update
        installer.reset()
        installer.importFiles(localBundle)
        app.manualLibraryUpdate.value = true
    }
    val pickDir = rememberWithNotificationPermission {
        folderError = null
        pickFolder.launch(null)
    }
    val metered by produceState(false, state.offer) {
        value = state.offer != null && withContext(Dispatchers.IO) { LibraryInstaller.get(app).isMeteredNetwork() }
    }
    val start = rememberWithNotificationPermission { service.onEvent(DbDeltaUpdateEvents.ApplyClicked) }
    val c = Zayit.colors

    SettingsCard(title = DeltaStrings.title) {
        version?.let { v ->
            Text(
                DesktopStrings.dbUpdateCurrentVersion.format(v.takeIf { it.isNotEmpty() }?.let(DatabaseVersion::formatForDisplay) ?: DesktopStrings.dbUpdateVersionUnknown) +
                    localVersion?.let { " · " + DeltaStrings.localVersion.format(it) }.orEmpty(),
                fontSize = 13.sp,
                color = c.textSecondary,
            )
        }
        val offer = state.offer
        val text = when {
            state.errorMessage != null -> null
            state.needsFullBundle || state.lastAppliedCount != null -> state.message
            busy -> state.message.ifEmpty { state.phase.toString() }
            offer != null -> null
            state.message.isNotEmpty() -> state.message
            else -> DeltaStrings.idle
        }
        when {
            state.errorMessage != null -> ErrorBanner(state.errorMessage!!)
            state.lastAppliedCount != null || state.upToDate -> InfoBanner(state.message, success = true)
            offer != null -> InfoBanner(if (offer.resume) state.message else "${DeltaStrings.available}: ${state.message}")
            text != null -> Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = c.text)
        }
        if (offer != null && metered && !offer.resume) InfoBanner(AndroidStrings.meteredWarning.format(formatBytes(offer.bytes)))
        if (busy) {
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            }
            state.detail?.let { Text(it, fontSize = 13.sp, color = c.textSecondary) }
            if (state.phase != DbDeltaUpdateState.Phase.CheckingForUpdates) Text(DeltaStrings.backgroundHint, fontSize = 12.sp, color = c.textSecondary)
        }
        // Wraps on a 240dp phone instead of squeezing the buttons.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                busy -> AccentButton(DeltaStrings.working, {}, enabled = false)
                offer != null -> AccentButton(if (offer.resume) DeltaStrings.resume else DeltaStrings.download, start)
                else -> AccentButton(if (state.upToDate) DeltaStrings.checkAgain else DeltaStrings.check, { service.onEvent(DbDeltaUpdateEvents.CheckClicked) })
            }
            if (busy && (state.cancellable || state.phase == DbDeltaUpdateState.Phase.CheckingForUpdates)) {
                OutlineButton(DeltaStrings.cancel, { service.onEvent(DbDeltaUpdateEvents.CancelClicked) })
            }
            if (hasMessage && !busy && offer == null) OutlineButton(DeltaStrings.dismiss, { service.onEvent(DbDeltaUpdateEvents.ClearMessage) })
            if (!busy) OutlineButton(DeltaStrings.fromFiles, pick)
            if (!busy) OutlineButton(DeltaStrings.fromFolder, pickDir)
            if (!busy && localBundle.isNotEmpty()) OutlineButton(AndroidStrings.useLocalBundle, useLocal)
        }
        if (!busy && localBundle.isNotEmpty()) Text(AndroidStrings.localBundleFound, fontSize = 13.sp, color = c.textSecondary)
        folderError?.takeIf { !busy }?.let { ErrorBanner(it) }
        if (!busy) Text(DeltaStrings.fromFilesHint, fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
    }
}

/** While the section is on screen the update's end is shown here, not as a notification (as LibraryInstaller.uiVisible). */
@Composable
private fun TrackSectionVisibility(service: DbDeltaUpdateService) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, service) {
        service.uiVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> service.uiVisible = true
                Lifecycle.Event.ON_STOP -> service.uiVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            service.uiVisible = false
        }
    }
}

/** A picked document's file name (what the release page called it), or its URI's last segment. Blocking. */
private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

/** What an unpacked library holds beside seforim.db (an install from a folder would take the database alone). */
private val UNPACKED_LIBRARY_ITEMS = setOf(LibraryStore.TEXT_INDEX_NAME, LibraryStore.LOOKUP_INDEX_NAME, LibraryStore.LEXICAL_NAME, LibraryStore.CATALOG_NAME)

/** Something directly in a picked folder: a file, or a folder ([isDirectory]). */
private class FolderEntry(val uri: Uri, val name: String, val isDirectory: Boolean)

/** What is directly in a picked folder (a document tree), files and folders, with their names. Blocking. */
private fun folderEntries(context: Context, tree: Uri): List<FolderEntry> = runCatching {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    context.contentResolver.query(children, columns, null, null, null)?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                add(FolderEntry(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
            }
        }
    }.orEmpty()
}.getOrDefault(emptyList())

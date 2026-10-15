package io.github.kdroidfilter.seforimapp.android.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.SessionStore
import io.github.kdroidfilter.seforimapp.android.data.UserDataBackup
import io.github.kdroidfilter.seforimapp.android.delta.DbDeltaUpdateService
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jobs that must outlive the settings screen (export, import or reset followed by a restart). */
private val maintenanceScope = MainScope()

/**
 * Desktop 0.6.1 DataSettingsScreen + DataSettingsViewModel: "ייצוא הנתונים שלי", "ייבוא נתונים" and "איפוס האפליקציה".
 * Backups are desktop's archive ([UserDataBackup]), so they move between the phone and the computer; a restore is
 * staged and applied when the app restarts. The Google Drive card is left out, as desktop does when it has no OAuth
 * client: desktop's Drive backup uses a desktop loopback OAuth client that an Android app cannot use.
 * On top: desktop's DbDeltaUpdateSection (incremental library updates, [DbDeltaUpdateSection]).
 */
@Composable
internal fun DataSettings(wide: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var exportedName by rememberSaveable { mutableStateOf<String?>(null) }
    var exportFailed by rememberSaveable { mutableStateOf(false) }
    var importSucceeded by remember { mutableStateOf(false) }
    var importFailed by rememberSaveable { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    // A library update in progress: an import or a reset would restart the app in the middle of it
    val deltaState by remember(app) { DbDeltaUpdateService.get(app) }.state.collectAsState()
    val deltaBusy = deltaState.phase != null
    var pendingExportName by rememberSaveable { mutableStateOf("") }

    // Desktop picks a folder and names the file; Android's document picker takes the same name.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        exportFailed = false
        exportedName = null
        val name = pendingExportName
        maintenanceScope.launch {
            val ok = exportUserData(context.applicationContext, app, uri)
            exporting = false
            if (ok) exportedName = name else exportFailed = true
        }
    }
    // Desktop filters on .zip and .db; providers label them inconsistently, so every file is offered and checked.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        importFailed = false
        importSucceeded = false
        maintenanceScope.launch {
            val staged = stageImport(context.applicationContext, uri)
            importing = false
            importFailed = !staged
            if (staged) {
                importSucceeded = true
                delay(RESTART_DELAY_MS)
                restartApp(app)
            }
        }
    }

    SettingsColumn(wide) {
        // Desktop 0.6.1 DbDeltaUpdateSection (incremental library updates).
        DbDeltaUpdateSection()
        SettingsCard {
            ActionRow("ייצוא הנתונים שלי", "שמירת גיבוי של ההערות, ההדגשות וההגדרות.") {
                OutlineButton(
                    if (exporting) "מייצא…" else "ייצוא",
                    {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                        val name = "pardes_backup_$timestamp.${UserDataBackup.BACKUP_EXTENSION}"
                        pendingExportName = name
                        exportLauncher.launch(name)
                    },
                    enabled = !exporting && !importing,
                )
            }
        }
        SettingsCard {
            ActionRow("ייבוא נתונים", "שחזור הנתונים מקובץ גיבוי קיים.") {
                OutlineButton(if (importing) "מייבא…" else "ייבוא", { importLauncher.launch(arrayOf("*/*")) }, enabled = !exporting && !importing && !deltaBusy)
            }
        }
        exportedName?.let { InfoBanner("הגיבוי נשמר בהצלחה: $it", success = true) }
        if (importSucceeded) InfoBanner("הנתונים יובאו בהצלחה. האפליקציה מופעלת מחדש…", success = true)
        if (exportFailed) ErrorBanner("ייצוא הנתונים נכשל")
        if (importFailed) ErrorBanner("ייבוא הנתונים נכשל")

        ResetCard(resetDone, enabled = !deltaBusy) {
            resetDone = true
            maintenanceScope.launch {
                resetApp(context.applicationContext, app)
                restartApp(app)
            }
        }
    }
}

/** Desktop ResetCard: the action, then an inline warning with "כן, אפס" / "ביטול" instead of a dialog. */
@Composable
private fun ResetCard(resetDone: Boolean, enabled: Boolean, onReset: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsCard {
        ActionRow("איפוס האפליקציה", "איפוס מלא של האפליקציה למצב ההתחלתי. פעולה זו תמחק את כל הנתונים שלך.") {
            if (!confirming) OutlineButton("איפוס האפליקציה", { confirming = true }, enabled = !resetDone && enabled)
        }
        if (confirming && enabled) {
            ErrorBanner("פעולה זו תמחק את כל הנתונים שלך: מסד הנתונים, אינדקס החיפוש, הסימניות וכל ההגדרות. האפליקציה תופעל מחדש כמו בהתקנה ראשונה.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentButton("כן, אפס", {
                    confirming = false
                    onReset()
                })
                OutlineButton("ביטול", { confirming = false })
            }
        }
        if (resetDone) InfoBanner("האיפוס הושלם. האפליקציה מופעלת מחדש...", success = true, modifier = Modifier.fillMaxWidth())
    }
}

/** Desktop exportToFile: the backup archive, written to the picked document. */
private suspend fun exportUserData(context: Context, app: ZayitApplication, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        // The user database is created on first use; a backup made before any note still carries the preferences.
        if (!context.getDatabasePath(UserDataBackup.USER_DB).isFile) app.userData.notesForBook(-1L)
        val archive = UserDataBackup.capture(context)
        // "wt" truncates a file being overwritten; some providers (cloud drives) accept only "w".
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w") ?: error("cannot write")
        out.use { it.write(archive) }
    }.isSuccess
}

/** Desktop importFromFile: validates the archive (or a bare .db from older exports) and stages it for the next launch. */
private suspend fun stageImport(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        // A backup is a few MB; a library file picked by mistake (seforim.db is a SQLite file too) must not be read into memory.
        val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
        require(size == null || size <= MAX_BACKUP_BYTES) { "Not a backup: $size bytes" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("cannot read")
        UserDataBackup.stageRestore(context, bytes)
    }.isSuccess
}

/**
 * Desktop resetApp: everything goes, as desktop empties its databases folder and clears its settings. Here that is
 * the library (both places it may be in), what an install or a transfer left in the app's folders, the user database
 * (notes, highlights, desktop tables) with a staged restore, downloaded fonts, and every preferences file: settings,
 * history and favourites, the tools' settings; and the launcher's shortcuts to the books read last (desktop's jump list).
 */
private suspend fun resetApp(context: Context, app: ZayitApplication) = withContext(Dispatchers.IO) {
    runCatching { context.getSystemService(ShortcutManager::class.java)?.removeAllDynamicShortcuts() }
    runCatching { app.userData.clearAll() }
    runCatching { app.history.clearHistory() }
    runCatching { app.libraryStore.close() }
    app.libraryStore.candidateDirs.forEach { runCatching { it.deleteRecursively() } }
    listOfNotNull(
        context.getExternalFilesDir(null),
        context.getDatabasePath(UserDataBackup.USER_DB).parentFile,
        context.filesDir,
        context.cacheDir,
    ).forEach { dir -> dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } } }
    app.prefs.resetAll()
    File(context.dataDir, "shared_prefs").listFiles()?.forEach { file ->
        runCatching { context.deleteSharedPreferences(file.name.removeSuffix(".xml")) }
    }
    // Last: resetAll() turned "שמור מצב" back on, so a session save meanwhile may have written it again
    SessionStore.delete(context)
}

private const val RESTART_DELAY_MS = 1_500L
private const val MAX_BACKUP_BYTES = 256L * 1024 * 1024

private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}

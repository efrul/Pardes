package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.DatabaseVersion
import io.github.kdroidfilter.seforimapp.android.data.LibraryLocation
import io.github.kdroidfilter.seforimapp.android.data.LibraryMover
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.data.MovePlan
import io.github.kdroidfilter.seforimapp.android.data.MoveState
import io.github.kdroidfilter.seforimapp.android.data.MoveUiStrings
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytesPerSec
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.locationIcon
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.rememberWithNotificationPermission
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Another library found in a place that is not the one in use (copied there from a computer, or left behind). */
private data class OtherLibrary(val location: LibraryLocation, val bytes: Long, val version: String?)

/** Where the library is, and the moves it can make (Android only), as worked out off the main thread. */
private data class LocationInfo(
    val current: LibraryLocation?,
    val libraryBytes: Long,
    val moves: List<MovePlan>,
    val blocked: String?,
    val others: List<OtherLibrary> = emptyList(),
)

/**
 * Android only, in Settings › ניהול נתונים: where the library is kept (the phone or a memory card), and moving it to
 * the other place ([LibraryMover]): the free space there, an inline confirmation, the copy's progress with cancel, and
 * its end, after which the app starts again. Lists the places again when a card goes in or out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryLocationSection() {
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    val mover = remember(app) { LibraryMover.get(app) }
    val state by mover.state.collectAsState()
    val mediaChanges by app.mediaChanges.collectAsState()
    val idle = state is MoveState.Idle || state is MoveState.Failed
    var refresh by remember { mutableIntStateOf(0) }
    val info by produceState<LocationInfo?>(null, mediaChanges, idle, refresh) {
        value = withContext(Dispatchers.IO) {
            val store = app.libraryStore
            store.refreshLocations()
            val current = store.currentLocation()
            val moves = if (current == null) emptyList() else store.locations.filter { it.key != current.key }.mapNotNull(mover::plan)
            val others = store.locations.filter { it.key != current?.key && LibraryStore.hasDatabase(it.dir) }.map { loc ->
                OtherLibrary(loc, LibraryStore.libraryBytes(loc.dir), runCatching { File(loc.dir, LibraryStore.RELEASE_INFO_NAME).readText().trim() }.getOrNull())
            }
            LocationInfo(current, if (current != null) store.installedBytes() else 0L, moves, mover.blockedReason(), others)
        }
    }
    var confirmKey by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val c = Zayit.colors

    SettingsCard(title = LocationStrings.title) {
        val i = info
        when {
            i == null -> Unit
            i.current == null -> Text(LocationStrings.noLibrary, fontSize = 14.sp, color = c.textSecondary)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(locationIcon(i.current), contentDescription = null, tint = c.text, modifier = Modifier.size(24.dp))
                Text(LocationStrings.current.format(i.current.label, formatBytes(i.libraryBytes)), fontSize = 14.sp, lineHeight = 20.sp, color = c.text)
            }
        }
        when (val s = state) {
            is MoveState.Copying -> {
                Text(MoveUiStrings.movingTo.format(s.target), fontSize = 14.sp, color = c.text)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
                if (s.totalBytes > 0) {
                    val sizes = DesktopStrings.onboardingDownloadProgress.format(formatBytes(s.copiedBytes), formatBytes(s.totalBytes))
                    Text(if (s.speedBytesPerSec > 0) "$sizes · ${formatBytesPerSec(s.speedBytesPerSec)}" else sizes, fontSize = 13.sp, color = c.textSecondary)
                }
                Text(LocationStrings.backgroundHint, fontSize = 12.sp, color = c.textSecondary)
                OutlineButton(LocationStrings.cancel, mover::cancel)
            }
            is MoveState.Finishing -> {
                Text(MoveUiStrings.finishing, fontSize = 14.sp, color = c.text)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            }
            is MoveState.Done -> InfoBanner(LocationStrings.done.format(s.target), success = true)
            else -> {
                (s as? MoveState.Failed)?.let { failed ->
                    ErrorBanner(failed.message)
                    OutlineButton(LocationStrings.dismiss, mover::reset)
                }
                val i = info
                if (i != null && i.current != null) {
                    if (i.moves.isEmpty()) Text(LocationStrings.insertCard, fontSize = 13.sp, lineHeight = 19.sp, color = c.textSecondary)
                    i.blocked?.let { Text(it, fontSize = 13.sp, color = c.textSecondary) }
                    i.moves.forEach { plan -> MoveOption(plan, enabled = i.blocked == null, confirming = confirmKey == plan.target.key, onConfirm = { confirmKey = it }, mover = mover) }
                }
                if (i != null) i.others.forEach { other ->
                    OtherLibraryRow(
                        other,
                        enabled = i.blocked == null,
                        onSwitch = {
                            app.libraryStore.setPreferred(other.location)
                            app.restart()
                        },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { mover.deleteOtherLibrary(other.location) }
                                refresh++
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One place the library can go: its free space against the library's size, and "העבר" with its inline confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveOption(plan: MovePlan, enabled: Boolean, confirming: Boolean, onConfirm: (String?) -> Unit, mover: LibraryMover) {
    val c = Zayit.colors
    val start = rememberWithNotificationPermission {
        onConfirm(null)
        mover.start(plan.target)
    }
    val detail = LocationStrings.space.format(formatBytes(plan.availableBytes), formatBytes(plan.totalBytes)) +
        when {
            plan.fileTooLarge -> "\n" + LocationStrings.fat
            !plan.fits -> "\n" + LocationStrings.notEnough
            plan.splits -> "\n" + LocationStrings.fatSplit
            else -> ""
        }
    ActionRow(LocationStrings.moveTo.format(plan.target.label), detail) {
        if (!confirming) OutlineButton(LocationStrings.move, { onConfirm(plan.target.key) }, enabled = enabled && plan.possible)
    }
    if (confirming && enabled && plan.possible) {
        InfoBanner(LocationStrings.confirm + if (plan.replacesLibrary) LocationStrings.replaces else "", Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton(LocationStrings.startMove, start)
            OutlineButton(LocationStrings.cancel, { onConfirm(null) })
        }
    }
}

/**
 * Another library in a place that is not the one in use: "עבור אליה" makes it the library (the app starts again on it,
 * the current one stays where it is), "מחק" removes it after an inline confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OtherLibraryRow(other: OtherLibrary, enabled: Boolean, onSwitch: () -> Unit, onDelete: () -> Unit) {
    var confirming by rememberSaveable(other.location.key) { mutableStateOf(false) }
    val version = other.version?.takeIf { it.isNotEmpty() }?.let(DatabaseVersion::formatForDisplay) ?: DesktopStrings.dbUpdateVersionUnknown
    ActionRow(LocationStrings.otherTitle.format(other.location.label), LocationStrings.otherDetail.format(formatBytes(other.bytes), version)) {}
    if (confirming) InfoBanner(LocationStrings.deleteConfirm.format(other.location.label), Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (confirming) {
            AccentButton(LocationStrings.delete, {
                confirming = false
                onDelete()
            })
            OutlineButton(LocationStrings.cancel, { confirming = false })
        } else {
            OutlineButton(LocationStrings.switchTo, onSwitch, enabled = enabled)
            OutlineButton(LocationStrings.delete, { confirming = true }, enabled = enabled)
        }
    }
}

/** Texts of the library location section (Android only). */
internal object LocationStrings {
    const val title = "מיקום הספרייה"
    const val current = "הספרייה שמורה ב%1\$s (%2\$s)."
    const val noLibrary = "לא נמצאה ספרייה מותקנת."
    const val insertCard = "כדי לשמור את הספרייה בכרטיס זיכרון, הכניסו כרטיס SD למכשיר."
    const val moveTo = "העברה אל %1\$s"
    const val space = "פנויים %1\$s · הספרייה תופסת %2\$s"
    const val fat = "הכרטיס מפורמט ב-FAT32, שאינו מקבל קבצים של 4GB ומעלה. אפשר לפרמט אותו ל-exFAT במחשב."
    const val notEnough = "אין די מקום פנוי."
    const val fatSplit = "הכרטיס מפורמט ב-FAT32: מסד הנתונים יישמר בו בכמה קבצים, ועדכון מצטבר לא יהיה זמין (רק עדכון מלא)."
    const val move = "העבר"
    const val confirm =
        "ההעתקה עשויה להימשך זמן רב (בכרטיס איטי גם חצי שעה). אפשר להמשיך להשתמש בפרדס בינתיים; בסיום פרדס יופעל מחדש, והעותק הישן יימחק."
    const val startMove = "התחל העברה"
    const val cancel = "ביטול"
    const val dismiss = "סגור"
    const val backgroundHint = "ניתן לצאת מהיישום: ההעברה תמשיך ברקע."
    const val done = "הספרייה הועברה אל %1\$s. פרדס מופעל מחדש…"
    const val replaces = "\nהספרייה הישנה שכבר נמצאת שם תימחק ותוחלף."
    const val otherTitle = "ספרייה נוספת ב%1\$s"
    const val otherDetail = "%1\$s · גרסה %2\$s. אינה בשימוש."
    const val switchTo = "עבור אליה"
    const val delete = "מחק"
    const val deleteConfirm = "הספרייה שב%1\$s תימחק לצמיתות."
}

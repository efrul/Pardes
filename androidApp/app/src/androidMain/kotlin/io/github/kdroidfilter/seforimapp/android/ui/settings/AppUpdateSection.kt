package io.github.kdroidfilter.seforimapp.android.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.rememberWithNotificationPermission
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.update.AppReleases
import io.github.kdroidfilter.seforimapp.android.update.AppUpdateState
import io.github.kdroidfilter.seforimapp.android.update.AppUpdateStrings
import io.github.kdroidfilter.seforimapp.android.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the banner and the dialog offer to do next, for one state; [primary] in the accent, [secondary] beside it. */
private class UpdateActions(val primary: Pair<String, () -> Unit>?, val secondary: Pair<String, () -> Unit>?)

/**
 * The step after [state]: download and install, install, the "install unknown apps" page, Android's confirmation
 * again, or check (again); and stopping the download or the waiting install. A version that cannot be installed
 * ([AppUpdateState.rejected]) and the steps that run on their own offer nothing.
 */
@Composable
private fun rememberUpdateActions(state: AppUpdateState, updater: AppUpdater, context: Context): UpdateActions {
    val start = rememberWithNotificationPermission { updater.download(installAfter = true) }
    val openSettings: () -> Unit = { runCatching { context.startActivity(updater.permissionIntent()) } }
    return when {
        state.phase == AppUpdateState.Phase.Downloading -> UpdateActions(null, AppUpdateStrings.stopDownload to updater::cancel)
        state.phase == AppUpdateState.Phase.Installing && state.confirming ->
            UpdateActions(AppUpdateStrings.continueInstall to { updater.continueInstall(context) }, AppUpdateStrings.cancel to updater::cancelInstall)
        state.phase != null -> UpdateActions(null, null)
        state.needsPermission -> UpdateActions(AppUpdateStrings.openSettings to openSettings, AppUpdateStrings.cancel to updater::clearPermission)
        state.rejected -> UpdateActions(null, null)
        state.ready -> UpdateActions(AppUpdateStrings.install to updater::install, null)
        state.release != null -> UpdateActions(AppUpdateStrings.downloadAndInstall to start, null)
        else -> UpdateActions(null, (if (state.upToDate || state.errorMessage != null) AppUpdateStrings.checkAgain else AppUpdateStrings.check) to updater::check)
    }
}

/**
 * Desktop GeneralSettingsScreen's UpdateStatusBanner, at the top of the app header card, for the states that call for an
 * action (desktop 0.6.8 971f7dd6): a newer version (to download and install, and what is new in it), the download, the
 * APK checked and waiting, Android's "install unknown apps" switch, or why it failed. Up to date, checking and not yet
 * checked are [AppUpdateStatusLine], under the version. A development build says why it does not update itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppUpdateBanner() {
    val context = LocalContext.current
    val updater = remember(context) { AppUpdater.get(context) }
    val state by updater.state.collectAsState()
    val uri = LocalUriHandler.current
    val c = Zayit.colors
    TrackUpdateView(updater)
    if (!updater.canUpdate) {
        // A line, not a banner: a development build shows it every time
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(AppUpdateStrings.devBuild, fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
            LinkText(AppUpdateStrings.site, { uri.openSafely(AppReleases.SITE_URL) })
        }
        return
    }
    // Quiet: the line under the version says it
    if (state.quiet) return
    UpdateStatus(state, updater, metered = rememberMetered(state))
    val actions = rememberUpdateActions(state, updater, context)
    // Wraps on a 240dp phone instead of squeezing the buttons.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.primary?.let { (label, action) -> AccentButton(label, action) }
        // Desktop's banner links "בדוק שוב": the accent is kept for a version to install
        actions.secondary?.let { (label, action) -> OutlineButton(label, action) }
        if (state.release != null && state.phase == null && !state.rejected) OutlineButton(AppUpdateStrings.whatsNew, updater::openDialog)
    }
    if (state.phase == AppUpdateState.Phase.Downloading) Text(AndroidStrings.backgroundHint, fontSize = 12.sp, color = c.textSecondary)
}

/** Up to date, checking, or not yet checked: nothing to do, so no banner (desktop 0.6.8 971f7dd6). */
private val AppUpdateState.quiet: Boolean
    get() = errorMessage == null && message == null && release == null && !needsPermission && (phase == null || phase == AppUpdateState.Phase.Checking)

/**
 * Desktop GeneralSettingsScreen's UpdateStatusLine (0.6.8 971f7dd6), under the version: checking, or up to date with
 * "בדוק שוב", as one quiet line; not yet checked, the check alone. Nothing in the other states, which [AppUpdateBanner]
 * shows, nor in a development build.
 */
@Composable
internal fun AppUpdateStatusLine() {
    val context = LocalContext.current
    val updater = remember(context) { AppUpdater.get(context) }
    val state by updater.state.collectAsState()
    val c = Zayit.colors
    if (!updater.canUpdate || !state.quiet) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            state.phase == AppUpdateState.Phase.Checking -> {
                CircularProgressIndicator(Modifier.size(14.dp), color = c.accent, strokeWidth = 2.dp)
                Text(AppUpdateStrings.checking, fontSize = 13.sp, color = c.textSecondary)
            }
            state.upToDate -> {
                Icon(Icons.Outlined.CheckCircle, null, tint = c.success, modifier = Modifier.size(14.dp))
                Text(AppUpdateStrings.upToDate, fontSize = 13.sp, color = c.textSecondary)
                LinkText(AppUpdateStrings.checkAgain, updater::check)
            }
            else -> LinkText(AppUpdateStrings.check, updater::check)
        }
    }
}

/**
 * Desktop features/update/UpdateDialog.kt: "עדכון זמין", "גרסה X של פרדס זמינה.", the warning that the database may
 * need updating after it, the download's progress, and "מאוחר יותר" (which only closes it: a download goes on), with
 * what is new since the installed version (the site's changelog) and the steps Android adds. Opened from the title
 * bar's badge, its menu, a notification or the snackbar.
 */
@Composable
internal fun AppUpdateDialog() {
    val context = LocalContext.current
    val updater = remember(context) { AppUpdater.get(context) }
    val state by updater.state.collectAsState()
    val uri = LocalUriHandler.current
    val c = Zayit.colors
    TrackUpdateView(updater)
    val actions = rememberUpdateActions(state, updater, context)
    val release = state.release
    val scroll = rememberScrollState()
    // A new step shows at the top (a long "what's new" may have been scrolled)
    LaunchedEffect(state.phase, state.ready, state.needsPermission, state.confirming, state.errorMessage, state.message) { scroll.animateScrollTo(0) }
    AlertDialog(
        onDismissRequest = updater::closeDialog,
        containerColor = c.panel,
        title = {
            Text(if (release != null && !state.rejected) AppUpdateStrings.available else AppUpdateStrings.title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (release != null) {
                    Text(AppUpdateStrings.dialogMessage.format(release.version), color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(AppUpdateStrings.size.format(formatBytes(release.apkSize)), color = c.textSecondary, fontSize = 13.sp)
                }
                UpdateStatus(state, updater, metered = rememberMetered(state), inDialog = true)
                if (state.phase == AppUpdateState.Phase.Downloading) Text(AndroidStrings.backgroundHint, fontSize = 12.sp, color = c.textSecondary)
                if (release != null && !state.rejected) {
                    Text(AppUpdateStrings.installHint, color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    Text(AppUpdateStrings.whatsNew, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    state.changes?.let { ChangesText(it) }
                    LinkText(AppUpdateStrings.whatsNewLink, { uri.openSafely(AppReleases.CHANGELOG_PAGE) })
                }
            }
        },
        confirmButton = {
            actions.primary?.let { (label, action) -> TextButton(onClick = action) { Text(label, color = c.accent, fontWeight = FontWeight.Bold) } }
        },
        dismissButton = {
            Row {
                // Stopping the download or the waiting install is not closing the dialog: told apart from it
                actions.secondary?.let { (label, action) -> TextButton(onClick = action) { Text(label, color = c.text) } }
                TextButton(onClick = updater::closeDialog) {
                    Text(if (release != null && !state.rejected) AppUpdateStrings.later else AppUpdateStrings.dismiss, color = c.text)
                }
            }
        },
    )
}

/** The banner's and the dialog's status lines: one banner for the state, the progress, and the warnings. */
@Composable
private fun UpdateStatus(state: AppUpdateState, updater: AppUpdater, metered: Boolean, inDialog: Boolean = false) {
    val c = Zayit.colors
    val version = state.version.orEmpty()
    state.message?.let { InfoBanner(it) }
    when {
        state.errorMessage != null -> ErrorBanner(state.errorMessage)
        state.phase == AppUpdateState.Phase.Checking -> InfoBanner(AppUpdateStrings.checking)
        state.phase == AppUpdateState.Phase.Downloading -> InfoBanner(
            state.progress?.let { AppUpdateStrings.downloadingPercent.format((it * 100).toInt()) } ?: AppUpdateStrings.downloading,
        )
        state.phase == AppUpdateState.Phase.Verifying -> InfoBanner(AppUpdateStrings.verifying)
        state.phase == AppUpdateState.Phase.Installing -> InfoBanner(if (state.confirming) AppUpdateStrings.confirming else AppUpdateStrings.installing)
        state.needsPermission -> InfoBanner(AppUpdateStrings.permission)
        state.ready -> InfoBanner(AppUpdateStrings.ready.format(version))
        state.release != null -> if (!inDialog) InfoBanner(AppUpdateStrings.availableBanner.format(version))
        state.upToDate -> InfoBanner(AppUpdateStrings.upToDate, success = true)
        state.message == null -> Text(AppUpdateStrings.notChecked, fontSize = 14.sp, lineHeight = 20.sp, color = c.text)
    }
    val offered = state.release != null && !state.rejected && state.phase == null
    // Desktop needsDbWarning: a MINOR or MAJOR version may ask for a newer library
    if (offered && inDialog && AppReleases.newerLibraryMayBeNeeded(updater.installedVersion, version)) InfoBanner(AppUpdateStrings.dbWarning)
    if (offered && !state.ready && state.errorMessage == null && metered) InfoBanner(AndroidStrings.meteredWarning.format(formatBytes(state.apkSize)))
    when (state.phase) {
        AppUpdateState.Phase.Downloading -> {
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            }
            state.detail?.let { Text(it, fontSize = 13.sp, color = c.textSecondary) }
        }
        AppUpdateState.Phase.Verifying, AppUpdateState.Phase.Checking ->
            if (inDialog || state.phase != AppUpdateState.Phase.Checking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
            }
        AppUpdateState.Phase.Installing -> if (!state.confirming) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.divider)
        null -> Unit
    }
}

/** The network is metered (cellular, a hotspot): read when a version is offered, for the size warning. */
@Composable
private fun rememberMetered(state: AppUpdateState): Boolean {
    val context = LocalContext.current
    val metered by produceState(false, state.release, state.ready) {
        value = state.release != null && !state.ready && withContext(Dispatchers.IO) { LibraryInstaller.get(context).isMeteredNetwork() }
    }
    return metered
}

/**
 * While the banner or the dialog is on screen the download's end shows there (and the install may follow), not as a
 * notification; and coming back to it (from Android's "install unknown apps" page, or its install confirmation) lets
 * the update go on.
 */
@Composable
private fun TrackUpdateView(updater: AppUpdater) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, updater) {
        var counted = false
        fun count(visible: Boolean) {
            if (visible != counted) {
                counted = visible
                updater.trackVisible(visible)
            }
        }
        count(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> count(true)
                Lifecycle.Event.ON_STOP -> count(false)
                Lifecycle.Event.ON_RESUME -> updater.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            count(false)
        }
    }
}

/** One line of the changelog, laid out once. */
private sealed interface ChangeLine {
    class Version(val version: String, val date: String) : ChangeLine

    class Heading(val text: String) : ChangeLine

    class Item(val text: AnnotatedString) : ChangeLine

    class Paragraph(val text: AnnotatedString) : ChangeLine
}

/**
 * The changelog's sections as androidApp/CHANGELOG.md writes them and the site renders them: `## <version> · <date>`
 * as "גרסה <version>" with its date, `###` headings, `- ` items, with `code`, **bold** and [links](https://…) inline.
 */
@Composable
private fun ChangesText(markdown: String) {
    val c = Zayit.colors
    val uri = LocalUriHandler.current
    val lines = remember(markdown, c.link, uri) {
        val linkStyle = SpanStyle(color = c.link)
        markdown.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            when {
                line.startsWith("## ") -> line.removePrefix("## ").split('·').let { ChangeLine.Version(it[0].trim(), it.getOrNull(1)?.trim().orEmpty()) }
                line.startsWith("#") -> ChangeLine.Heading(line.trimStart('#').trim())
                line.startsWith("- ") || line.startsWith("* ") -> ChangeLine.Item(inlineChanges(line.drop(2), linkStyle, uri))
                else -> ChangeLine.Paragraph(inlineChanges(line, linkStyle, uri))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            when (line) {
                is ChangeLine.Version -> Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppUpdateStrings.versionHeading.format(line.version), color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    // An ISO date, kept left to right in the Hebrew line
                    if (line.date.isNotEmpty()) Text("⁦${line.date}⁩", color = c.textSecondary, fontSize = 13.sp)
                }
                is ChangeLine.Heading -> Text(line.text, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                is ChangeLine.Item -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                    Text(line.text, color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
                }
                is ChangeLine.Paragraph -> Text(line.text, color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

private val changesLink = Regex("""\[([^\]\[]{1,200})]\((https?://[^\s)]{1,500})\)""")

/**
 * **bold**, `code` and [text](https://…), as the site's changelog renders them. Code is isolated left to right (the
 * site's `unicode-bidi: isolate`), so `.part` does not read "part." in a Hebrew line; in monospace unless it is Hebrew.
 */
private fun inlineChanges(text: String, linkStyle: SpanStyle, uri: UriHandler): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val code = SpanStyle(fontFamily = FontFamily.Monospace)
    var strong = false
    fun plain(chunk: String) {
        // Text between links: its ** turn bold on and off
        chunk.split("**").forEachIndexed { i, piece ->
            if (i > 0) strong = !strong
            if (strong) withStyle(bold) { append(piece) } else append(piece)
        }
    }
    text.split('`').forEachIndexed { i, part ->
        if (i % 2 == 1) {
            val hebrew = part.any { it in '֐'..'׿' }
            append('⁦')
            if (hebrew) append(part) else withStyle(code) { append(part) }
            append('⁩')
            return@forEachIndexed
        }
        var at = 0
        for (link in changesLink.findAll(part)) {
            plain(part.substring(at, link.range.first))
            val url = link.groupValues[2]
            val listener = object : LinkInteractionListener {
                override fun onClick(link: LinkAnnotation) = uri.openSafely(url)
            }
            withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle), listener)) {
                val label = link.groupValues[1].removePrefix("**").removeSuffix("**")
                if (strong || label != link.groupValues[1]) withStyle(bold) { append(label) } else append(label)
            }
            at = link.range.last + 1
        }
        plain(part.substring(at))
    }
}

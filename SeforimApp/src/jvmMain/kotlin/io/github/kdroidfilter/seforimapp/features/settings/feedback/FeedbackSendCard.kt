package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.feedback_consent_publish
import seforimapp.seforimapp.generated.resources.feedback_consent_publish_hint
import seforimapp.seforimapp.generated.resources.feedback_send_all
import seforimapp.seforimapp.generated.resources.feedback_send_auto
import seforimapp.seforimapp.generated.resources.feedback_send_auto_hint
import seforimapp.seforimapp.generated.resources.feedback_send_daf
import seforimapp.seforimapp.generated.resources.feedback_send_delete
import seforimapp.seforimapp.generated.resources.feedback_send_delete_no
import seforimapp.seforimapp.generated.resources.feedback_send_delete_warning
import seforimapp.seforimapp.generated.resources.feedback_send_delete_yes
import seforimapp.seforimapp.generated.resources.feedback_send_deleted
import seforimapp.seforimapp.generated.resources.feedback_send_description
import seforimapp.seforimapp.generated.resources.feedback_send_failed
import seforimapp.seforimapp.generated.resources.feedback_send_grades
import seforimapp.seforimapp.generated.resources.feedback_send_nothing
import seforimapp.seforimapp.generated.resources.feedback_send_nothing_new
import seforimapp.seforimapp.generated.resources.feedback_send_now
import seforimapp.seforimapp.generated.resources.feedback_send_offline
import seforimapp.seforimapp.generated.resources.feedback_send_offline_auto
import seforimapp.seforimapp.generated.resources.feedback_send_open_folder
import seforimapp.seforimapp.generated.resources.feedback_send_otzaria
import seforimapp.seforimapp.generated.resources.feedback_send_privacy
import seforimapp.seforimapp.generated.resources.feedback_send_privacy_online
import seforimapp.seforimapp.generated.resources.feedback_send_reading
import seforimapp.seforimapp.generated.resources.feedback_send_refused
import seforimapp.seforimapp.generated.resources.feedback_send_renew
import seforimapp.seforimapp.generated.resources.feedback_send_review
import seforimapp.seforimapp.generated.resources.feedback_send_review_hint
import seforimapp.seforimapp.generated.resources.feedback_send_save
import seforimapp.seforimapp.generated.resources.feedback_send_saved
import seforimapp.seforimapp.generated.resources.feedback_send_saving
import seforimapp.seforimapp.generated.resources.feedback_send_searches
import seforimapp.seforimapp.generated.resources.feedback_send_searches_hint
import seforimapp.seforimapp.generated.resources.feedback_send_sender
import seforimapp.seforimapp.generated.resources.feedback_send_sending
import seforimapp.seforimapp.generated.resources.feedback_send_sent
import seforimapp.seforimapp.generated.resources.feedback_send_title
import seforimapp.seforimapp.generated.resources.feedback_send_too_many
import seforimapp.seforimapp.generated.resources.feedback_send_zayit
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings › ניהול נתונים: sending the feedback given, the search results' grades (feedback mode), the searches made,
 * and the daf view's line review. The user sees what there is, keeps what they want of it, and sends it to Pardes's
 * service or saves it to a file; turns the automatic sending (FeedbackAutoSender) on or off, and deletes what was sent.
 */
@Composable
fun FeedbackSendCard() {
    val viewModel: FeedbackSendViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Each time the card is shown: the user may have given feedback since
    LaunchedEffect(viewModel) { viewModel.refresh() }
    FeedbackSendCardContent(
        state = state,
        actions =
            FeedbackSendActions(
                grades = viewModel::setGrades,
                searches = viewModel::setSearches,
                daf = viewModel::setDaf,
                otherApp = viewModel::setOtherApp,
                publish = viewModel::setPublish,
                query = viewModel::setQuery,
                all = viewModel::setAll,
                renewSender = viewModel::renewSender,
                auto = viewModel::setAuto,
                send = viewModel::send,
                deleteSent = viewModel::deleteSent,
                save = {
                    scope.launch {
                        // Off the GTK event-loop thread, as the data screen's other pickers (see pickDatabaseParts)
                        val folder = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }
                        folder?.let { viewModel.saveTo(File(it.path)) }
                    }
                },
                openFolder = { file ->
                    scope.launch(Dispatchers.IO) { runCatching { Desktop.getDesktop().open(file.absoluteFile.parentFile) } }
                },
            ),
    )
}

/** What the card's checkboxes and buttons do. */
internal class FeedbackSendActions(
    val grades: (Boolean) -> Unit = {},
    val searches: (Boolean) -> Unit = {},
    val daf: (Boolean) -> Unit = {},
    // Another app's searches sent too, or not
    val otherApp: (OtherApp, Boolean) -> Unit = { _, _ -> },
    // Whether what is sent may be shown to everyone
    val publish: (Boolean) -> Unit = {},
    // A query kept in the package or left out of it
    val query: (String, Boolean) -> Unit = { _, _ -> },
    val all: (Boolean) -> Unit = {},
    val renewSender: () -> Unit = {},
    // The automatic sending on or off
    val auto: (Boolean) -> Unit = {},
    val send: () -> Unit = {},
    val deleteSent: () -> Unit = {},
    val save: () -> Unit = {},
    val openFolder: (File) -> Unit = {},
)

@Composable
internal fun FeedbackSendCardContent(
    state: FeedbackSendViewModel.State,
    actions: FeedbackSendActions,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.feedback_send_title), fontSize = 15.sp)
        Hint(stringResource(Res.string.feedback_send_description))
        val contents = state.contents
        if (contents == null) {
            Text(text = stringResource(Res.string.feedback_send_reading), fontSize = 13.sp)
        } else {
            Kinds(contents, state.choice, state.otherApps, actions)
            if (contents.isEmpty) Text(text = nothingText(state), fontSize = 13.sp)
        }
        // Once a package was made: the next one holds what is newer, unless the user wants all again
        if (state.lastSent > 0 && contents != null) {
            CheckboxRow(
                text = stringResource(Res.string.feedback_send_all),
                checked = state.all,
                onCheckedChange = actions.all,
                enabled = !state.busy,
            )
        }
        if (state.online) Publish(state.choice.publish, actions)
        if (state.online) Auto(state.auto, actions)
        Privacy(state.sender, state.online, actions)
        WorkStatus(state)
        Buttons(state, actions)
        if (state.online && state.lastSent > 0) DeleteSent(state.busy, actions)
    }
}

/** The automatic sending: what is new, at every start and connection of the network, of the kinds checked above. */
@Composable
private fun Auto(
    on: Boolean,
    actions: FeedbackSendActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckboxRow(text = stringResource(Res.string.feedback_send_auto), checked = on, onCheckedChange = actions.auto)
        Hint(stringResource(Res.string.feedback_send_auto_hint))
    }
}

/** Whether the service may show to everyone what is sent, by the card and by itself: its words, never the sender. */
@Composable
private fun Publish(
    on: Boolean,
    actions: FeedbackSendActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckboxRow(text = stringResource(Res.string.feedback_consent_publish), checked = on, onCheckedChange = actions.publish)
        Hint(stringResource(Res.string.feedback_consent_publish_hint))
    }
}

/** Deleting from the service what this sender sent, once the user says yes. */
@Composable
private fun DeleteSent(
    busy: Boolean,
    actions: FeedbackSendActions,
) {
    var asking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!asking) {
            Link(text = stringResource(Res.string.feedback_send_delete), onClick = { asking = true }, enabled = !busy)
        } else {
            InlineWarningBanner(text = stringResource(Res.string.feedback_send_delete_warning), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(onClick = {
                    asking = false
                    actions.deleteSent()
                }) { Text(stringResource(Res.string.feedback_send_delete_yes)) }
                OutlinedButton(onClick = { asking = false }) { Text(stringResource(Res.string.feedback_send_delete_no)) }
            }
        }
    }
}

/** Each kind, whether it is sent (by the card and by itself) and how much of it is new, and the queries to look over. */
@Composable
private fun Kinds(
    contents: FeedbackContents,
    choice: FeedbackChoice,
    otherApps: Set<OtherApp>,
    actions: FeedbackSendActions,
) {
    // What each kind would send, the queries left out gone, whether it is on or not
    val offered = remember(contents, choice.leftOut) { contents.kept(FeedbackChoice(leftOut = choice.leftOut, searches = true)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckboxRow(
            text = stringResource(Res.string.feedback_send_grades, offered.grades.size, offered.grades.distinctBy { it.search }.size),
            checked = choice.grades,
            onCheckedChange = actions.grades,
        )
        CheckboxRow(
            text = stringResource(Res.string.feedback_send_searches, offered.searches.size),
            checked = choice.searches,
            onCheckedChange = actions.searches,
        )
        Hint(stringResource(Res.string.feedback_send_searches_hint))
        CheckboxRow(
            text = stringResource(Res.string.feedback_send_daf, contents.dafMarks, contents.daf.size),
            checked = choice.daf,
            onCheckedChange = actions.daf,
        )
        // The other apps on this computer: read, and so counted, only once checked
        otherApps.forEach { app ->
            val rows = contents.others[app]?.without(choice.leftOut) ?: OtherAppRows()
            CheckboxRow(
                text =
                    if (app in choice.others) {
                        stringResource(app.countLabel, rows.searches.size, rows.grades.size)
                    } else {
                        stringResource(app.label)
                    },
                checked = app in choice.others,
                onCheckedChange = { actions.otherApp(app, it) },
            )
            Hint(stringResource(app.hint))
        }
        Review(contents, choice, actions)
    }
}

private val OtherApp.countLabel: StringResource
    get() =
        when (this) {
            OtherApp.ZAYIT -> Res.string.feedback_send_zayit
            OtherApp.OTZARIA -> Res.string.feedback_send_otzaria
        }

/** The queries of the kinds sent, each with its checkbox: one left out goes in neither its grades nor its searches. */
@Composable
private fun Review(
    contents: FeedbackContents,
    choice: FeedbackChoice,
    actions: FeedbackSendActions,
) {
    val queries = remember(contents, choice.grades, choice.searches, choice.others) { contents.queries(choice) }
    if (queries.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val kept = queries.count { it !in choice.leftOut }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Link(text = stringResource(Res.string.feedback_send_review, kept, queries.size), onClick = { open = !open })
        if (open) {
            Hint(stringResource(Res.string.feedback_send_review_hint))
            QueryList(queries, choice.leftOut, actions)
        }
    }
}

@Composable
private fun QueryList(
    queries: List<String>,
    leftOut: Set<String>,
    actions: FeedbackSendActions,
) {
    val listState = rememberLazyListState()
    VerticallyScrollableContainer(
        scrollState = listState as ScrollableState,
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
    ) {
        // Room for the scrollbar, so that it never covers the rows
        LazyColumn(state = listState, modifier = Modifier.padding(end = 11.dp)) {
            items(queries, key = { it }) { query ->
                CheckboxRow(
                    text = query,
                    checked = query !in leftOut,
                    onCheckedChange = { actions.query(query, it) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** What is never sent, what is besides the feedback, and the sender's id, which the user can copy or renew. */
@Composable
private fun Privacy(
    sender: String,
    online: Boolean,
    actions: FeedbackSendActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Hint(stringResource(Res.string.feedback_send_privacy))
        if (online) Hint(stringResource(Res.string.feedback_send_privacy_online))
        if (sender.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer {
                    Text(
                        text = stringResource(Res.string.feedback_send_sender, isolatedPath(sender)),
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.info,
                    )
                }
                Link(text = stringResource(Res.string.feedback_send_renew), onClick = actions.renewSender)
            }
        }
    }
}

@Composable
private fun WorkStatus(state: FeedbackSendViewModel.State) {
    val work = state.work
    val wide = Modifier.fillMaxWidth()
    when (work) {
        is FeedbackSendViewModel.Work.Saved ->
            InlineSuccessBanner(text = stringResource(Res.string.feedback_send_saved, isolatedPath(work.file.name)), modifier = wide)
        is FeedbackSendViewModel.Work.Sending -> Hint(stringResource(Res.string.feedback_send_sending, work.part, work.parts))
        is FeedbackSendViewModel.Work.Sent ->
            InlineSuccessBanner(text = stringResource(Res.string.feedback_send_sent, dateOf(work.at)), modifier = wide)
        is FeedbackSendViewModel.Work.Deleted ->
            InlineSuccessBanner(text = stringResource(Res.string.feedback_send_deleted, work.packages), modifier = wide)
        is FeedbackSendViewModel.Work.Failed -> InlineErrorBanner(text = problemText(work.problem, state.auto), modifier = wide)
        FeedbackSendViewModel.Work.Idle, FeedbackSendViewModel.Work.Saving, FeedbackSendViewModel.Work.Deleting -> Unit
    }
}

/** Why the feedback was not sent, saved or deleted; without a network, whether it will be sent by itself ([auto]). */
@Composable
private fun problemText(
    problem: Throwable,
    auto: Boolean,
): String =
    when {
        problem is IOException ->
            stringResource(if (auto) Res.string.feedback_send_offline_auto else Res.string.feedback_send_offline)
        problem is FeedbackServiceProblem && problem.tooMany -> stringResource(Res.string.feedback_send_too_many)
        problem is FeedbackServiceProblem -> stringResource(Res.string.feedback_send_refused, isolatedPath(problem.reason))
        else -> stringResource(Res.string.feedback_send_failed, isolatedPath(problem.message ?: problem.toString()))
    }

@Composable
private fun Buttons(
    state: FeedbackSendViewModel.State,
    actions: FeedbackSendActions,
) {
    val contents = state.contents
    val any = contents != null && !contents.kept(state.choice).isEmpty
    val saving = state.work == FeedbackSendViewModel.Work.Saving
    val saveLabel = stringResource(if (saving) Res.string.feedback_send_saving else Res.string.feedback_send_save)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.online) {
            DefaultButton(onClick = actions.send, enabled = any && !state.busy) { Text(stringResource(Res.string.feedback_send_now)) }
            OutlinedButton(onClick = actions.save, enabled = any && !state.busy) { Text(saveLabel) }
        } else {
            DefaultButton(onClick = actions.save, enabled = any && !state.busy) { Text(saveLabel) }
        }
        (state.work as? FeedbackSendViewModel.Work.Saved)?.let { saved ->
            OutlinedButton(onClick = { actions.openFolder(saved.file) }) { Text(stringResource(Res.string.feedback_send_open_folder)) }
        }
    }
}

@Composable
private fun nothingText(state: FeedbackSendViewModel.State): String =
    if (state.lastSent > 0 && !state.all) {
        stringResource(Res.string.feedback_send_nothing_new, dateOf(state.lastSent))
    } else {
        stringResource(Res.string.feedback_send_nothing)
    }

@Composable
private fun Hint(text: String) {
    Text(text = text, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
}

private val dateFormat = DateTimeFormatter.ofPattern("d.M.yyyy, HH:mm").withZone(ZoneId.systemDefault())

private fun dateOf(millis: Long): String = dateFormat.format(Instant.ofEpochMilli(millis))

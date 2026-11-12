package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviewsFolderLink
import io.github.kdroidfilter.seforimapp.features.settings.ui.ExpandableCard
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingCard
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.feedback_consent_publish
import seforimapp.seforimapp.generated.resources.feedback_consent_publish_hint
import seforimapp.seforimapp.generated.resources.feedback_send_auto
import seforimapp.seforimapp.generated.resources.feedback_send_auto_hint
import seforimapp.seforimapp.generated.resources.feedback_send_privacy_title
import seforimapp.seforimapp.generated.resources.feedback_send_what
import seforimapp.seforimapp.generated.resources.search_feedback_setting
import seforimapp.seforimapp.generated.resources.search_feedback_setting_hint
import java.awt.Desktop
import java.io.File

/**
 * Settings › משוב: everything about the feedback in one page ([FeedbackSettingsContent]), and the folder of the daf
 * view's reviews. The user sees what there is to send, keeps what they want of it, and sends it to Pardes's service or
 * saves it to a file; turns the automatic sending (FeedbackAutoSender) and the search results' grade buttons on or off,
 * and deletes what was sent.
 */
@Composable
fun FeedbackSettingsScreen() {
    val viewModel: FeedbackSendViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Each time the page is shown: the user may have given feedback since
    LaunchedEffect(viewModel) { viewModel.refresh() }
    val actions =
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
            buttons = viewModel::setButtons,
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
        )
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeedbackSettingsContent(state, actions)
            DafReviewsFolderLink()
        }
    }
}

/**
 * The feedback page for [state]: sending what waits ([FeedbackSendCard]), then a row each, closed until its arrow is
 * clicked: what is sent, the automatic sending and publishing (with the service alone), the search results' grade
 * buttons, and privacy.
 */
@Composable
internal fun FeedbackSettingsContent(
    state: FeedbackSendViewModel.State,
    actions: FeedbackSendActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeedbackSendCard(state, actions)
        ExpandableCard(title = stringResource(Res.string.feedback_send_what)) { FeedbackKinds(state, actions) }
        if (state.online) {
            SettingCard(
                title = Res.string.feedback_send_auto,
                description = Res.string.feedback_send_auto_hint,
                checked = state.auto,
                onCheckedChange = actions.auto,
            )
            SettingCard(
                title = Res.string.feedback_consent_publish,
                description = Res.string.feedback_consent_publish_hint,
                checked = state.choice.publish,
                onCheckedChange = actions.publish,
            )
        }
        SettingCard(
            title = Res.string.search_feedback_setting,
            description = Res.string.search_feedback_setting_hint,
            checked = state.buttons,
            onCheckedChange = actions.buttons,
        )
        ExpandableCard(title = stringResource(Res.string.feedback_send_privacy_title)) { FeedbackPrivacy(state, actions) }
    }
}

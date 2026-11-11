package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviewsFolderLink
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_daf_mark_unsure
import seforimapp.seforimapp.generated.resources.settings_daf_mark_unsure_info
import seforimapp.seforimapp.generated.resources.settings_daf_review
import seforimapp.seforimapp.generated.resources.settings_daf_review_info

/**
 * Settings › צורת הדף: the daf view's settings in one place, which stood among the fonts' and the data's: the marks of
 * its unsure lines, its review, and its layouts ([DafLayoutsCard]). Its two fonts stay with the other fonts (Settings ›
 * גופנים). The state is the fonts' view model's, where the daf's switches are kept with its fonts.
 */
@Composable
fun DafSettingsScreen() {
    val viewModel: FontsSettingsViewModel = metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    DafSettingsView(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun DafSettingsView(
    state: FontsSettingsState,
    onEvent: (FontsSettingsEvents) -> Unit,
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckboxRow(
                    text = stringResource(Res.string.settings_daf_mark_unsure),
                    checked = state.dafMarkUnsure,
                    onCheckedChange = { onEvent(FontsSettingsEvents.SetDafMarkUnsure(it)) },
                )
                Text(
                    text = stringResource(Res.string.settings_daf_mark_unsure_info),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckboxRow(
                    text = stringResource(Res.string.settings_daf_review),
                    checked = state.dafReview,
                    onCheckedChange = { onEvent(FontsSettingsEvents.SetDafReview(it)) },
                )
                Text(
                    text = stringResource(Res.string.settings_daf_review_info),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
                if (state.dafReview) DafReviewsFolderLink()
            }

            DafLayoutsCard()
        }
    }
}

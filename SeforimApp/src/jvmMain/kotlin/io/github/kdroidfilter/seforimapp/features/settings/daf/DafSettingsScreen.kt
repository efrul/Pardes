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
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviewsFolderLink
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel
import io.github.kdroidfilter.seforimapp.features.settings.ui.FontSettingCard
import kotlinx.collections.immutable.toImmutableList
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
import seforimapp.seforimapp.generated.resources.settings_font_daf_commentary_label
import seforimapp.seforimapp.generated.resources.settings_font_daf_gemara_label

/**
 * Settings › צורת הדף: the daf view's settings in one place, which stood among the fonts' and the data's: its two
 * fonts, the marks of its unsure lines, its review, and its layouts ([DafLayoutsCard]). Their state is the fonts' view
 * model's, where the daf's are kept with the fonts.
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
    val options = FontCatalog.options
    val optionLabels = options.map { stringResource(it.label) }.toImmutableList()

    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FontSettingCard(
                label = Res.string.settings_font_daf_gemara_label,
                fontCode = state.dafGemaraFontCode,
                options = options,
                optionLabels = optionLabels,
                onFontChange = { code -> onEvent(FontsSettingsEvents.SetDafGemaraFont(code)) },
            )

            FontSettingCard(
                label = Res.string.settings_font_daf_commentary_label,
                fontCode = state.dafCommentaryFontCode,
                options = options,
                optionLabels = optionLabels,
                onFontChange = { code -> onEvent(FontsSettingsEvents.SetDafCommentaryFont(code)) },
            )

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

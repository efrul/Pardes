package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedDialog
import dev.nucleusframework.window.jewel.JewelDialogTitleBar
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.feedback_consent_agree
import seforimapp.seforimapp.generated.resources.feedback_consent_daf
import seforimapp.seforimapp.generated.resources.feedback_consent_daf_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_decline
import seforimapp.seforimapp.generated.resources.feedback_consent_explain
import seforimapp.seforimapp.generated.resources.feedback_consent_grades
import seforimapp.seforimapp.generated.resources.feedback_consent_grades_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_privacy
import seforimapp.seforimapp.generated.resources.feedback_consent_question
import seforimapp.seforimapp.generated.resources.feedback_consent_searches
import seforimapp.seforimapp.generated.resources.feedback_consent_searches_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_title

/**
 * The question, once at start (FeedbackAutoSender.run), whether to send feedback by itself and which kinds. Closed
 * without an answer, it is asked again at the next start.
 */
@Composable
fun NucleusApplicationScope.FeedbackConsentDialog(autoSender: FeedbackAutoSender) {
    IntUiTheme(
        theme = ThemeUtils.buildThemeDefinition(),
        styling = ThemeUtils.buildComponentStyling(),
    ) {
        val dialogState =
            rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(540.dp, 520.dp))
        JewelDecoratedDialog(
            onCloseRequest = autoSender::dismiss,
            title = stringResource(Res.string.feedback_consent_title),
            state = dialogState,
            visible = true,
            resizable = false,
        ) {
            JewelDialogTitleBar(
                modifier = Modifier.newFullscreenControls(),
                gradientStartColor = ThemeUtils.titleBarGradientColor(),
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) {
                Text(stringResource(Res.string.feedback_consent_title))
            }
            FeedbackConsentContent(initial = remember { autoSender.choice() }, onAnswer = autoSender::answer)
        }
    }
}

/** The question's content: the kinds, checked as [initial], and the answer, given to [onAnswer] with the kinds checked. */
@Composable
internal fun FeedbackConsentContent(
    initial: FeedbackChoice,
    onAnswer: (Boolean, FeedbackChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var choice by remember { mutableStateOf(initial) }
    val info = JewelTheme.globalColors.text.info
    Column(
        modifier =
            modifier
                .trackActivation()
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.feedback_consent_question), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.feedback_consent_explain), fontSize = 13.sp)
        Kind(Res.string.feedback_consent_grades, Res.string.feedback_consent_grades_hint, choice.grades) {
            choice = choice.copy(grades = it)
        }
        Kind(Res.string.feedback_consent_searches, Res.string.feedback_consent_searches_hint, choice.searches) {
            choice = choice.copy(searches = it)
        }
        Kind(Res.string.feedback_consent_daf, Res.string.feedback_consent_daf_hint, choice.daf) {
            choice = choice.copy(daf = it)
        }
        Text(stringResource(Res.string.feedback_consent_privacy), fontSize = 12.sp, color = info)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = { onAnswer(false, choice) }) { Text(stringResource(Res.string.feedback_consent_decline)) }
            // Agreeing to send nothing is saying no
            DefaultButton(onClick = { onAnswer(true, choice) }, enabled = choice.grades || choice.searches || choice.daf) {
                Text(stringResource(Res.string.feedback_consent_agree))
            }
        }
    }
}

@Composable
private fun Kind(
    label: org.jetbrains.compose.resources.StringResource,
    hint: org.jetbrains.compose.resources.StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column {
        CheckboxRow(text = stringResource(label), checked = checked, onCheckedChange = onChange)
        Text(
            stringResource(hint),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.padding(start = 28.dp),
        )
    }
}

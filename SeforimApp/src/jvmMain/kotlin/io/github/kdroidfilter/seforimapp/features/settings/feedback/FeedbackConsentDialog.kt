package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.feedback_consent_agree
import seforimapp.seforimapp.generated.resources.feedback_consent_buttons
import seforimapp.seforimapp.generated.resources.feedback_consent_buttons_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_daf
import seforimapp.seforimapp.generated.resources.feedback_consent_daf_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_decline
import seforimapp.seforimapp.generated.resources.feedback_consent_explain
import seforimapp.seforimapp.generated.resources.feedback_consent_grades
import seforimapp.seforimapp.generated.resources.feedback_consent_grades_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_otzaria
import seforimapp.seforimapp.generated.resources.feedback_consent_otzaria_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_privacy
import seforimapp.seforimapp.generated.resources.feedback_consent_publish
import seforimapp.seforimapp.generated.resources.feedback_consent_publish_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_question
import seforimapp.seforimapp.generated.resources.feedback_consent_searches
import seforimapp.seforimapp.generated.resources.feedback_consent_searches_hint
import seforimapp.seforimapp.generated.resources.feedback_consent_title
import seforimapp.seforimapp.generated.resources.feedback_consent_zayit
import seforimapp.seforimapp.generated.resources.feedback_consent_zayit_hint

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
            rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(560.dp, 600.dp))
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
            FeedbackConsentContent(
                initial = remember { autoSender.choice() },
                onAnswer = autoSender::answer,
                offerButtons = remember { autoSender.buttonsOffered() },
                otherApps = remember { autoSender.otherApps() },
            )
        }
    }
}

/**
 * The question's content: the kinds, checked as [initial], and, when [offerButtons] (the user has them off), the grade
 * buttons under the search results, checked; then, unchecked until the user says, the searches made in the
 * [otherApps] found on this computer, and whether what is sent may be shown to everyone. The answer goes to [onAnswer]
 * with what is checked and the buttons. The kinds scroll, in a window too short for them; the buttons stay.
 */
@Composable
internal fun FeedbackConsentContent(
    initial: FeedbackChoice,
    onAnswer: (agree: Boolean, choice: FeedbackChoice, buttons: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    offerButtons: Boolean = true,
    otherApps: Set<OtherApp> = emptySet(),
) {
    var choice by remember { mutableStateOf(initial) }
    var buttons by remember { mutableStateOf(offerButtons) }
    val info = JewelTheme.globalColors.text.info
    val scroll = rememberScrollState()
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
        VerticallyScrollableContainer(scrollState = scroll, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The container scrolls its content itself; room for the scrollbar, so that it never covers the rows
            Column(Modifier.padding(end = 11.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kind(Res.string.feedback_consent_grades, Res.string.feedback_consent_grades_hint, choice.grades) {
                    choice = choice.copy(grades = it)
                }
                // Grades come from the buttons: offered with them, under them
                if (offerButtons) {
                    Column(Modifier.padding(start = 28.dp)) {
                        CheckboxRow(
                            text = stringResource(Res.string.feedback_consent_buttons),
                            checked = buttons && choice.grades,
                            onCheckedChange = { buttons = it },
                            enabled = choice.grades,
                        )
                        Text(
                            stringResource(Res.string.feedback_consent_buttons_hint),
                            fontSize = 12.sp,
                            color = info,
                            modifier = Modifier.padding(start = 28.dp),
                        )
                    }
                }
                Kind(Res.string.feedback_consent_searches, Res.string.feedback_consent_searches_hint, choice.searches) {
                    choice = choice.copy(searches = it)
                }
                Kind(Res.string.feedback_consent_daf, Res.string.feedback_consent_daf_hint, choice.daf) {
                    choice = choice.copy(daf = it)
                }
                // The other apps here, read only once checked
                otherApps.forEach { app ->
                    Kind(app.label, app.hint, app in choice.others) { on ->
                        choice = choice.copy(others = if (on) choice.others + app else choice.others - app)
                    }
                }
                Kind(Res.string.feedback_consent_publish, Res.string.feedback_consent_publish_hint, choice.publish) {
                    choice = choice.copy(publish = it)
                }
                Text(stringResource(Res.string.feedback_consent_privacy), fontSize = 12.sp, color = info)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = { onAnswer(false, choice, false) }) { Text(stringResource(Res.string.feedback_consent_decline)) }
            // Agreeing to send nothing is saying no
            DefaultButton(
                onClick = { onAnswer(true, choice, offerButtons && buttons && choice.grades) },
                enabled = choice.grades || choice.searches || choice.daf || choice.others.isNotEmpty(),
            ) {
                Text(stringResource(Res.string.feedback_consent_agree))
            }
        }
    }
}

/** The question's and the card's row for another app's searches, and what it says of them. */
internal val OtherApp.label: StringResource
    get() =
        when (this) {
            OtherApp.ZAYIT -> Res.string.feedback_consent_zayit
            OtherApp.OTZARIA -> Res.string.feedback_consent_otzaria
        }

internal val OtherApp.hint: StringResource
    get() =
        when (this) {
            OtherApp.ZAYIT -> Res.string.feedback_consent_zayit_hint
            OtherApp.OTZARIA -> Res.string.feedback_consent_otzaria_hint
        }

@Composable
private fun Kind(
    label: StringResource,
    hint: StringResource,
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

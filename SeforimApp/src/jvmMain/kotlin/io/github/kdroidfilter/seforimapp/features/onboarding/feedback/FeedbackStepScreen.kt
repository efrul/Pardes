package io.github.kdroidfilter.seforimapp.features.onboarding.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.features.settings.feedback.FeedbackChoice
import io.github.kdroidfilter.seforimapp.features.settings.feedback.OtherApp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Share
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.inlineBannerStyle
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.feedback_consent_buttons
import seforimapp.seforimapp.generated.resources.feedback_consent_publish
import seforimapp.seforimapp.generated.resources.next_button
import seforimapp.seforimapp.generated.resources.onboarding_feedback_description
import seforimapp.seforimapp.generated.resources.onboarding_feedback_otzaria
import seforimapp.seforimapp.generated.resources.onboarding_feedback_publish_hint
import seforimapp.seforimapp.generated.resources.onboarding_feedback_send
import seforimapp.seforimapp.generated.resources.onboarding_feedback_sources
import seforimapp.seforimapp.generated.resources.onboarding_feedback_title
import seforimapp.seforimapp.generated.resources.onboarding_feedback_zayit

/**
 * The onboarding's step before its end, in a build that has the feedback service: the question FeedbackAutoSender asks
 * at a first start, answered here instead. Whether to send the search data by itself, from which apps on this
 * computer (this one, and Zayit and Otzaria where they are), and whether what is sent may be shown to everyone. All
 * of it is checked at first, as the user decided on 2026-11-11, and the step says so: what is unchecked is not sent,
 * and going on with the sending unchecked is a no.
 */
@Composable
fun FeedbackStepScreen(
    navController: NavController,
    progressBarState: ProgressBarState = LocalAppGraph.current.onboardingProgressBarState,
) {
    val autoSender = LocalAppGraph.current.feedbackAutoSender
    LaunchedEffect(Unit) { progressBarState.setProgress(0.95f) }
    FeedbackStepView(
        otherApps = remember { autoSender.otherApps() },
        offerButtons = remember { autoSender.buttonsOffered() },
        onNext = { answer ->
            autoSender.answer(answer.agree, answer.choice, answer.buttons)
            navController.navigate(OnBoardingDestination.FinishScreen)
        },
    )
}

/** What the step's boxes say: FeedbackAutoSender's answer. */
internal data class FeedbackStepAnswer(
    val agree: Boolean,
    val choice: FeedbackChoice,
    val buttons: Boolean,
)

/**
 * The answer of the boxes: [send] with an app to send from ([own] for this one, [others]) is a yes to this app's three
 * kinds and to those apps' searches; [publish] and the grade [buttons] only count with it, the buttons with [own].
 */
internal fun feedbackStepAnswer(
    send: Boolean,
    own: Boolean,
    others: Set<OtherApp>,
    publish: Boolean,
    buttons: Boolean,
): FeedbackStepAnswer {
    val agree = send && (own || others.isNotEmpty())
    val choice = FeedbackChoice(grades = own, searches = own, daf = own, others = others, publish = publish)
    return if (agree) FeedbackStepAnswer(true, choice, buttons && own) else FeedbackStepAnswer(false, FeedbackChoice(), false)
}

/** The step, laid out as the steps beside it: what it is about in a banner, the choices, and a picture beside them. */
@Composable
internal fun FeedbackStepView(
    otherApps: Set<OtherApp>,
    offerButtons: Boolean,
    onNext: (FeedbackStepAnswer) -> Unit,
) {
    // Checked at first, every one
    var send by remember { mutableStateOf(true) }
    var own by remember { mutableStateOf(true) }
    var others by remember { mutableStateOf(otherApps) }
    var publish by remember { mutableStateOf(true) }
    var buttons by remember { mutableStateOf(offerButtons) }

    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_feedback_title),
        bottomAction = {
            DefaultButton(onClick = { onNext(feedbackStepAnswer(send, own, others, publish, buttons)) }) {
                Text(stringResource(Res.string.next_button))
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InlineInformationBanner(
                style = JewelTheme.inlineBannerStyle.information,
                text = stringResource(Res.string.onboarding_feedback_description),
            )
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().weight(CHOICES_WEIGHT),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    CheckboxRow(text = stringResource(Res.string.onboarding_feedback_send), checked = send, onCheckedChange = { send = it })
                    // From which apps: asked only where another one is on this computer
                    if (otherApps.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(start = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(Res.string.onboarding_feedback_sources))
                            CheckboxRow(
                                text = stringResource(Res.string.app_name),
                                checked = own,
                                onCheckedChange = { own = it },
                                enabled = send,
                            )
                            otherApps.forEach { app ->
                                CheckboxRow(
                                    text = stringResource(app.shownName),
                                    checked = app in others,
                                    onCheckedChange = { others = if (it) others + app else others - app },
                                    enabled = send,
                                )
                            }
                        }
                    }
                    if (offerButtons) {
                        CheckboxRow(
                            text = stringResource(Res.string.feedback_consent_buttons),
                            checked = buttons && send && own,
                            onCheckedChange = { buttons = it },
                            enabled = send && own,
                            modifier = Modifier.padding(start = 28.dp),
                        )
                    }
                    CheckboxRow(
                        text = stringResource(Res.string.feedback_consent_publish),
                        checked = publish && send,
                        onCheckedChange = { publish = it },
                        enabled = send,
                    )
                    Text(
                        text = stringResource(Res.string.onboarding_feedback_publish_hint),
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.info,
                        modifier = Modifier.padding(start = 28.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                    Icon(Share, null, modifier = Modifier.fillMaxSize().padding(16.dp), tint = JewelTheme.globalColors.text.normal)
                }
            }
        }
    }
}

// The choices' share of the step's width beside its picture's 1: their rows are long
private const val CHOICES_WEIGHT = 1.6f

private val OtherApp.shownName: StringResource
    get() =
        when (this) {
            OtherApp.ZAYIT -> Res.string.onboarding_feedback_zayit
            OtherApp.OTZARIA -> Res.string.onboarding_feedback_otzaria
        }

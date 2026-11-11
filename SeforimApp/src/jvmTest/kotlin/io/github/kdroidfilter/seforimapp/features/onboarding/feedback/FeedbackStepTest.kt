package io.github.kdroidfilter.seforimapp.features.onboarding.feedback

import io.github.kdroidfilter.seforimapp.features.settings.feedback.FeedbackChoice
import io.github.kdroidfilter.seforimapp.features.settings.feedback.OtherApp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The onboarding's feedback step: what its boxes answer the question with. */
class FeedbackStepTest {
    private val no = FeedbackStepAnswer(false, FeedbackChoice(), false)

    @Test
    fun `nothing checked is a no, whatever stands under it`() {
        assertEquals(no, feedbackStepAnswer(send = false, own = true, others = setOf(OtherApp.ZAYIT), publish = true, buttons = true))
        // Sending, from nowhere
        assertEquals(no, feedbackStepAnswer(send = true, own = false, others = emptySet(), publish = true, buttons = true))
    }

    @Test
    fun `sending from this app is its three kinds, with the grade buttons as checked`() {
        val answer = feedbackStepAnswer(send = true, own = true, others = emptySet(), publish = false, buttons = true)

        assertEquals(FeedbackStepAnswer(true, FeedbackChoice(grades = true, searches = true, daf = true), true), answer)
        assertEquals(false, feedbackStepAnswer(send = true, own = true, others = emptySet(), publish = false, buttons = false).buttons)
    }

    @Test
    fun `the apps checked are sent from, this one or not, and published only when checked`() {
        val others = setOf(OtherApp.ZAYIT, OtherApp.OTZARIA)

        val all = feedbackStepAnswer(send = true, own = true, others = others, publish = true, buttons = false)
        assertEquals(FeedbackChoice(others = others, publish = true), all.choice)

        // Another app's searches alone: none of this app's kinds, and no buttons for grades that are not sent
        val zayitOnly = feedbackStepAnswer(send = true, own = false, others = setOf(OtherApp.ZAYIT), publish = false, buttons = true)
        assertEquals(
            FeedbackStepAnswer(true, FeedbackChoice(grades = false, searches = false, daf = false, others = setOf(OtherApp.ZAYIT)), false),
            zayitOnly,
        )
    }
}

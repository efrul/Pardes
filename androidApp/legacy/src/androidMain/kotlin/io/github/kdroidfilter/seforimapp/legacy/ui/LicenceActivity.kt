package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.CONDITIONS_MARKDOWN
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings

/**
 * The main app's onboarding licence step (LicenceStep), asked once before the first Home: the terms (CONDITIONS_MARKDOWN,
 * its AGPL link opening the licence in the app), the box "אני מאשר את תנאי הרישיון ושימוש" under them, centred, and
 * "המשך", which waits for it. Back leaves the app, as on the main app's first step. A link that started the app
 * (zayit://) is handed to the Home once the terms are accepted.
 *
 * There is one licence step and nothing under the Home it leads to: the Home (singleTask) finishes once it has started
 * the step, so the launcher's icon or a link while the step is shown starts a Home over it, which brings this step
 * back instead of a second one ([start]), and the Home comes on a task of its own ([accept]), so that Back from it
 * leaves the app.
 */
class LicenceActivity : BaseActivity() {
    private var accepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accepted = savedInstanceState?.getBoolean(STATE_ACCEPTED) ?: false
        // The latest link, which may have come through onNewIntent: Android starts a step again after ending the app with
        // the intent that first started it
        savedInstanceState?.getString(STATE_LINK)?.let { link -> intent = Intent(intent).setData(Uri.parse(link)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.panel)
        }
        val bar = TopBar(this, colors).apply { title.text = DesktopStrings.onboardingTitleBar }
        root.addView(bar, lp(MATCH, WRAP))
        val side = dp(if (resources.configuration.screenWidthDp < 360) 16 else 24)
        val step = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, dp(16), side, dp(12))
        }
        // StepScaffold: the step's title, 24sp and centred, over its scrolling content and its button.
        step.addView(label(DesktopStrings.licenseScreenTitle, 24f, colors.text).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }, lp(MATCH, WRAP))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(MarkdownText.view(this, colors, CONDITIONS_MARKDOWN), lp(MATCH, WRAP))
        val next = accentButton(DesktopStrings.nextButton, colors) { accept() }
        val check = checkRow(DesktopStrings.licenseAcceptCheckbox, colors, accepted) { checked ->
            accepted = checked
            next.setAccentEnabled(colors, checked)
        }
        // Desktop puts the box, centred, after the last paragraph.
        column.addView(FrameLayout(this).apply {
            addView(check, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL))
            setPadding(0, dp(16), 0, 0)
        }, lp(MATCH, WRAP))
        step.addView(ScrollView(this).apply { addView(column) }, lp(MATCH, 0, 1f))
        next.setAccentEnabled(colors, accepted)
        step.addView(FrameLayout(this).apply {
            setPadding(0, dp(12), 0, dp(4))
            addView(next, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL))
        }, lp(MATCH, WRAP))
        root.addView(step, lp(MATCH, 0, 1f))
        setContentView(root)
    }

    /** The step brought back by the launcher's icon or a link ([start]): a link is kept for the Home, the box as it was. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ACCEPTED, accepted)
        intent?.data?.let { outState.putString(STATE_LINK, it.toString()) }
    }

    private fun accept() {
        if (!accepted) return
        prefs.licenceAccepted = true
        startActivity(
            Intent(this, MainActivity::class.java).setData(intent?.data)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    companion object {
        private const val STATE_ACCEPTED = "accepted"
        private const val STATE_LINK = "link"

        /**
         * The licence step in [from]'s place, with the link that started it ([Intent.getData]), if any: the one already
         * shown when there is one, the screens over it closed.
         */
        fun start(from: Context, intent: Intent?) {
            from.startActivity(
                Intent(from, LicenceActivity::class.java).setData(intent?.data)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}

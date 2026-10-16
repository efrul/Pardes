package io.github.kdroidfilter.seforimapp.android.ui.database

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SdCardAlert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.BodyText
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.InstallerWindow
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.StepIllustration
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.StepScaffold

/**
 * Android only: the library is kept on a memory card ([label], the card's name) that is not in the device. The app
 * waits for it rather than offering to download the library again: it opens by itself once a card is put in
 * ("נסה שוב" checks again by hand). The library can also be installed anew on the phone, after a warning, for a card
 * that is lost or broken; [onUsePhone] then leads to the usual missing-library flow. [mediaSeen] is the count of card
 * events ([ZayitApplication.mediaChanges]) read before the check that found the card missing.
 */
@Composable
fun LibraryCardMissingScreen(label: String, mediaSeen: Int, onRetry: () -> Unit, onUsePhone: () -> Unit, onExit: () -> Unit) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val mediaChanges by app.mediaChanges.collectAsState()
    // A card went in (or any card event) since that check, before this screen was shown too: check again.
    LaunchedEffect(mediaChanges) {
        if (mediaChanges != mediaSeen) onRetry()
    }
    var confirming by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (confirming) confirming = false else onExit() }
    InstallerWindow(title = CardStrings.windowTitle, icon = Icons.Outlined.SdCardAlert, canGoBack = false, onBack = {}, progress = 0f) {
        StepScaffold(
            title = CardStrings.missingTitle,
            bottomAction = {
                if (!confirming) {
                    // Equal halves: on a narrow phone "נסה שוב" otherwise wraps onto two lines.
                    OutlineButton(CardStrings.usePhone, { confirming = true }, Modifier.weight(1f))
                    AccentButton(CardStrings.retry, onRetry, Modifier.weight(1f))
                }
            },
        ) {
            StepIllustration(Icons.Outlined.SdCardAlert, wideSize = 96.dp, phoneSize = 80.dp, modifier = Modifier.padding(top = 8.dp))
            BodyText(CardStrings.missingBody.format(label), Modifier.fillMaxWidth(0.9f))
            if (confirming) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoBanner(CardStrings.usePhoneWarning, Modifier.fillMaxWidth())
                    AccentButton(CardStrings.usePhoneConfirm, onUsePhone)
                    OutlineButton(CardStrings.cancel, { confirming = false })
                }
            }
        }
    }
}

/** Texts about a library kept on a memory card (Android only). */
internal object CardStrings {
    const val windowTitle = "פרדס"
    const val missingTitle = "כרטיס הזיכרון אינו זמין"
    const val missingBody = "הספרייה שמורה בכרטיס הזיכרון (%1\$s), שאינו מחובר כעת. הכניסו את הכרטיס למכשיר, והספרייה תיפתח מעצמה."
    const val retry = "נסה שוב"
    const val usePhone = "התקנה בטלפון"
    const val usePhoneWarning =
        "הספרייה תותקן מחדש בזיכרון הטלפון (הורדה, או קבצים מהמכשיר). הספרייה שבכרטיס לא תימחק, ואפשר יהיה להשתמש בה שוב כשהכרטיס יחזור."
    const val usePhoneConfirm = "המשך להתקנה בטלפון"
    const val cancel = "ביטול"
}

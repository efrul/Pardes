package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/**
 * Settings › צורת הדף: the daf view's layouts ([DafLayoutsCard]). Its two fonts stay with the other fonts (Settings ›
 * גופנים), its marks are shown from the button beside the daf, and the folder of the reader's reviews is opened from
 * Settings › משוב.
 */
@Composable
fun DafSettingsScreen() {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DafLayoutsCard()
        }
    }
}

package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.LimudPanel
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.LimudSettings
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.LimudWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberOpenInLibrary
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState

/**
 * "לימוד יומי": the Limud widget alone, as desktop's tool window of features/home/widgets/luach/LuachWidgets.kt
 * (LimudWidget, 380 dp wide, as tall as its lines): the limudim picked in its options (its gear), each opening its
 * place in a new tab. A page on a phone, with the day to show above it.
 */
@Composable
fun DailyStudyTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    val open = rememberOpenInLibrary(state)
    val shown = state[LimudSettings.shown]
    ToolPage(ToolKind.DailyStudy.title, onBack) { wide ->
        if (inToolPanel()) {
            LimudPanel(state, shown, open, Modifier.fillMaxSize())
            return@ToolPage
        }
        ToolColumn(maxWidth = if (wide) 760.dp else 560.dp) {
            ToolDayBar(state)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                LimudPanel(state, shown, open, Modifier.fillMaxWidth().height(LimudWidget.heightAt(maxWidth)))
            }
        }
    }
}

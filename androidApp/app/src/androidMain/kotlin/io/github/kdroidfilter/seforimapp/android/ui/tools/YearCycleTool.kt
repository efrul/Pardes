package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.SolarSystemPage
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState

/**
 * "מעגל השנה": the solar system's full page, desktop's SolarSystemWindow (features/home/widgets/solarsystem, 1200 × 720):
 * the Hebrew year's festivals on the Earth's orbit, played at the speed of its options (the gear), with the Earth and
 * the sky of the moment beside it on a tablet, under it on a phone. A 2D drawing of desktop's 3D scene (Filament).
 */
@Composable
fun YearCycleTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    ToolPage(
        ToolKind.YearCycle.title,
        onBack,
        actions = { GearButton { state.optionsOpen = SolarSystemWidget } },
    ) { wide ->
        SolarSystemPage(state, wide, Modifier.fillMaxSize().padding(10.dp))
    }
}

package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchMode
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchModeSettings
import io.github.kdroidfilter.seforimapp.features.search.domain.defaultSearchMode
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.theme.textFieldStyle
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_mode_exact
import seforimapp.seforimapp.generated.resources.search_mode_exact_tooltip
import seforimapp.seforimapp.generated.resources.search_mode_general
import seforimapp.seforimapp.generated.resources.search_mode_general_tooltip
import seforimapp.seforimapp.generated.resources.search_mode_hybrid
import seforimapp.seforimapp.generated.resources.search_mode_hybrid_missing
import seforimapp.seforimapp.generated.resources.search_mode_hybrid_tooltip
import kotlin.math.max
import kotlin.math.min

// Pardes (request 54 of docs/DESKTOP_STAGE3_PLAN.md): the three modes of a search beside the search bar, "מדויק |
// כללי | היברידי", the design the user approved on 2026-11-18 (variant ב: in the bar's row, on the home page and over
// the results), drawn as the bar is drawn (the user's word the same day: "תדייק עיצובית").

/**
 * The search modes as one pill as high as the search bar beside it, on the bar's background within its border, the
 * three modes in it, the [selected] one marked as the results' category tabs mark theirs; [onSelect] is given the one
 * chosen, each explained in its tooltip. Hybrid needs the search by meaning: without it, it is dimmed, its tooltip says
 * where it is installed, and it is not chosen.
 */
@Composable
fun SearchModeSwitch(
    selected: SearchMode,
    onSelect: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meaning = rememberMeaningInstalled()
    val field = JewelTheme.textFieldStyle.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(BAR_HEIGHT)
            .clip(shape)
            .background(field.background)
            .border(1.dp, field.border, shape)
            .padding(PILL_INSET)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchMode.entries.forEach { mode ->
            val available = mode != SearchMode.HYBRID || meaning
            ModePart(mode, selected = mode == selected, available = available) { onSelect(mode) }
        }
    }
}

/** The mode the next searches take ([SearchModeSettings.last]), chosen before a search: the home page's bar. */
@Composable
fun NextSearchModeSwitch(modifier: Modifier = Modifier) {
    val appSettings = LocalAppGraph.current.appSettings
    val last by appSettings.flow(SearchModeSettings.last).collectAsState()
    val meaning = rememberMeaningInstalled()
    SearchModeSwitch(
        selected = last ?: appSettings.defaultSearchMode(meaning),
        onSelect = { appSettings[SearchModeSettings.last] = it },
        modifier = modifier,
    )
}

/**
 * The home page's search bar ([bar], one element, as wide as it takes) centered as Zayit centers it, under the logo,
 * and the next searches' mode beside it at its end, outside its width; on a page too narrow for both, the mode under
 * the bar.
 */
@Composable
fun BarWithSearchMode(
    modifier: Modifier = Modifier,
    bar: @Composable () -> Unit,
) {
    Layout(
        content = {
            bar()
            NextSearchModeSwitch()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = MODE_GAP.roundToPx()
        val switch = measurables[1].measure(Constraints())
        val width = constraints.maxWidth
        val beside = width - 2 * (switch.width + gap) >= MIN_BAR_WIDTH.roundToPx()
        val room = if (beside) width - 2 * (switch.width + gap) else width
        val field = measurables[0].measure(Constraints(maxWidth = room.coerceAtLeast(0), maxHeight = constraints.maxHeight))
        val barX = (width - field.width) / 2
        val height = if (beside) max(field.height, switch.height) else field.height + gap + switch.height
        layout(width, min(height, constraints.maxHeight)) {
            field.placeRelative(barX, 0)
            if (beside) {
                switch.placeRelative(barX + field.width + gap, 0)
            } else {
                switch.placeRelative((width - switch.width) / 2, field.height + gap)
            }
        }
    }
}

// A mode in the pill: its name, marked when chosen, and what it does in its tooltip; dimmed when it is not there
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModePart(
    mode: SearchMode,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
) {
    val (name, tip) =
        when (mode) {
            SearchMode.EXACT -> Res.string.search_mode_exact to Res.string.search_mode_exact_tooltip
            SearchMode.GENERAL -> Res.string.search_mode_general to Res.string.search_mode_general_tooltip
            SearchMode.HYBRID ->
                Res.string.search_mode_hybrid to
                    (if (available) Res.string.search_mode_hybrid_tooltip else Res.string.search_mode_hybrid_missing)
        }
    val accent = JewelTheme.globalColors.outlines.focused
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    val fill =
        when {
            selected -> accent.copy(alpha = 0.15f)
            hovered && available ->
                JewelTheme.globalColors.text.normal
                    .copy(alpha = 0.06f)
            else -> Color.Transparent
        }
    Tooltip(tooltip = { Text(stringResource(tip)) }) {
        Box(
            Modifier
                .height(BAR_HEIGHT - PILL_INSET * 2)
                .clip(shape)
                .background(fill)
                .border(1.dp, if (selected) accent else Color.Transparent, shape)
                .hoverable(hover)
                .selectable(selected = selected, enabled = available, role = Role.RadioButton, onClick = onClick)
                .pointerHoverIcon(if (available) PointerIcon.Hand else PointerIcon.Default)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(name),
                fontSize = 13.sp,
                maxLines = 1,
                color =
                    when {
                        selected -> accent
                        available -> JewelTheme.globalColors.text.normal
                        else -> JewelTheme.globalColors.text.disabled
                    },
            )
        }
    }
}

// Whether the search by meaning is installed (its pack in the library's folder), read once off the UI thread
@Composable
private fun rememberMeaningInstalled(): Boolean {
    val appSettings = LocalAppGraph.current.appSettings
    val installed by produceState(true, appSettings) {
        value = withContext(Dispatchers.IO) { InstalledPack.read(LibraryLocation.currentDir(appSettings)) is InstalledPack.InUse }
    }
    return installed
}

// The search bar's height (Zayit's SearchBar), the pill's own, and the room around a mode inside it
private val BAR_HEIGHT = 40.dp
private val PILL_INSET = 3.dp

// Between the bar and the modes beside it, and the bar's narrowest beside them before they go under it
private val MODE_GAP = 10.dp
private val MIN_BAR_WIDTH = 360.dp

package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.ALIYOS
import io.github.kdroidfilter.seforimapp.android.data.MikraWeek
import io.github.kdroidfilter.seforimapp.android.data.aliyaPlace
import io.github.kdroidfilter.seforimapp.android.data.aliyaRange
import io.github.kdroidfilter.seforimapp.android.data.aliyosOf
import io.github.kdroidfilter.seforimapp.android.data.mikraWeek
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.launch

// Port of the widget part of desktop 0.6.1 features/home/widgets/luach/ShnayimMikraWidget.kt: the week's parsha by
// aliyos, each one ticked once read (in the user DB, shared with desktop) and opened in the Chumash.

/** The week's ticks, followed as they're written (a tick here, in the tool, or in a desktop DB imported). */
@Composable
internal fun rememberMikraDone(
    state: HomeWidgetsState,
    week: MikraWeek,
): Set<Int> {
    val store = state.app.userData.shnayimMikra
    var weekShown by remember { mutableStateOf(week) }
    val revision by store.revision.collectAsState()
    val done by produceState(emptySet<Int>(), week, revision) {
        // Another week's ticks aren't shown while this one's load
        if (week != weekShown) value = emptySet()
        weekShown = week
        value = runCatching { store.read(week.year, week.parsha.name) }.getOrDefault(emptySet())
    }
    return done
}

/** The week's parsha by aliyos: a tick marks one read, a tap opens it in the Chumash. */
internal object ShnayimMikraWidget : HomeWidget {
    override val id = "shnayim_mikra"
    override val title = "שנים מקרא ואחד תרגום"
    override val defaultSpan = CellSpan(4, 4)
    override val minSpan = CellSpan(4, 4)
    override val maxSpan = CellSpan(6, 4)
    override val tool = ToolKind.ShnayimMikra

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val c = Zayit.colors
        val day = state.shownDate
        val week = remember(day, state.inIsrael) { mikraWeek(day, state.inIsrael) }
        val done = rememberMikraDone(state, week)
        val scope = rememberCoroutineScope()
        val aliyos = remember(week) { aliyosOf(week.parsha) }
        val open = rememberOpenInLibrary(state, shnayimMikra = true)
        val store = state.app.userData.shnayimMikra
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                    "שנים מקרא · ${week.name}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp).height(22.dp),
                )
                FitColumn(Modifier.fillMaxWidth().weight(1f), spacing = 2.dp, spread = true) {
                    aliyos.forEachIndexed { i, (from, to) ->
                        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = i in done,
                                onCheckedChange = { read -> scope.launch { runCatching { store.setRead(week.year, week.parsha.name, i, read) } } },
                                colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
                                modifier = Modifier.size(32.dp),
                            )
                            HoverBox(onClick = { open(aliyaPlace(week.parsha, i)) }, modifier = Modifier.weight(1f).height(32.dp)) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        ALIYOS[i],
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (i in done) c.accent else c.text,
                                        maxLines = 1,
                                        modifier = Modifier.width(48.dp),
                                    )
                                    Text(aliyaRange(from, to), fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.ChevronLeft, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

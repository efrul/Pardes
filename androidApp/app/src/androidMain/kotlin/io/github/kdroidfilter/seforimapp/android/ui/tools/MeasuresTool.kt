package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.AMMA_OPINIONS
import io.github.kdroidfilter.seforimapp.android.data.ETZBAOS_IN_AMMA
import io.github.kdroidfilter.seforimapp.android.data.LengthUnit
import io.github.kdroidfilter.seforimapp.android.data.amount
import io.github.kdroidfilter.seforimapp.android.data.length
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.MeasuresCard
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.MeasuresWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/**
 * "ממיר מידות": desktop's Measures widget alone, as its tool window (features/home/widgets/measures/MeasuresWidget.kt,
 * 400 × 400): an amount converted between the Torah's lengths, with its sources, and what it is today by the opinions
 * picked in its options (home_widget.measures.shown). The page adds a table of the units (Android).
 */
@Composable
fun MeasuresTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    ToolPage(ToolKind.Measures.title, onBack) { wide ->
        if (inToolPanel()) {
            MeasuresCard(state, Modifier.fillMaxSize())
            return@ToolPage
        }
        ToolColumn(maxWidth = if (wide) 1100.dp else 560.dp) {
            AdaptiveColumns(
                wide,
                first = {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        MeasuresCard(state, Modifier.fillMaxWidth().height(MeasuresWidget.heightAt(maxWidth)))
                    }
                },
                second = { UnitsTable() },
            )
        }
    }
}

/** Every unit in etzbaos, and in today's measure by each opinion of the amma. */
@Composable
private fun UnitsTable() {
    val c = Zayit.colors
    ToolCard("טבלת המידות") {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("מידה", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("אצבעות", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            AMMA_OPINIONS.forEach { Text(it.label, color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
        }
        LengthUnit.entries.forEachIndexed { i, unit ->
            if (i > 0) HorizontalDivider(color = c.divider)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(unit.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(amount(unit.etzbaos), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                AMMA_OPINIONS.forEach { opinion ->
                    Text(length(unit.etzbaos * opinion.cm / ETZBAOS_IN_AMMA), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            "אמה = 6 טפחים · טפח = 4 אצבעות · מיל = 2,000 אמה · פרסה = 4 מילין",
            color = c.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

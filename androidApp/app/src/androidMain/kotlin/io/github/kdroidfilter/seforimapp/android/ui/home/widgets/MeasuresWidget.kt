package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.ALL_MEASURE_OPINIONS
import io.github.kdroidfilter.seforimapp.android.data.AMMA_OPINIONS
import io.github.kdroidfilter.seforimapp.android.data.AMOS_IN_MIL
import io.github.kdroidfilter.seforimapp.android.data.ETZBAOS_IN_AMMA
import io.github.kdroidfilter.seforimapp.android.data.LengthUnit
import io.github.kdroidfilter.seforimapp.android.data.MEASURES_TODAY_TITLE
import io.github.kdroidfilter.seforimapp.android.data.MEASURES_WALK_TITLE
import io.github.kdroidfilter.seforimapp.android.data.MIL_OPINIONS
import io.github.kdroidfilter.seforimapp.android.data.Source
import io.github.kdroidfilter.seforimapp.android.data.conversionSources
import io.github.kdroidfilter.seforimapp.android.data.duration
import io.github.kdroidfilter.seforimapp.android.data.length
import io.github.kdroidfilter.seforimapp.android.data.quantity
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.launch

// Port of the widget part of desktop 0.6.1 features/home/widgets/measures/MeasuresWidget.kt: a length in one of the
// Torah's measures converted into another, how long it is today and how long it takes to walk after the opinions
// picked; each figure links to the line of the library it comes from.

// Every block a fixed height: the card's is worked out from the opinions shown (heightAt). Android's touch targets
// are taller than desktop's: the input and the opinion rows.
private val PADDING = 8.dp
private val TITLE_HEIGHT = 22.dp
private val INPUT_HEIGHT = 40.dp
private val RESULT_HEIGHT = 28.dp
private val LINK_HEIGHT = 22.dp
private val SECTION_TITLE_HEIGHT = 16.dp
private val OPINION_ROW_HEIGHT = 24.dp
private val BLOCK_GAP = 8.dp
private val ROW_GAP = 2.dp

internal object MeasuresWidget : HomeWidget {
    override val id = "measures"
    override val title = "ממיר מידות"
    override val defaultSpan = CellSpan(7, 4)
    override val minSpan = CellSpan(7, 2)
    override val tool = ToolKind.Measures

    // Its height is the one its opinions need (heightAt), no more: only its width is the user's
    override val maxSpan = CellSpan(10, 2)

    override val settings get() = MeasuresSettings

    // The opinions shown, as saved: read by the grid for the card's height as well as by the card
    private var shown by mutableStateOf(MeasuresSettings.shown.default)

    override fun applySettings(store: HomeWidgetStore) {
        shown = store[MeasuresSettings.shown]
    }

    /** Tall enough for every opinion picked, never one hidden for want of room. */
    override fun heightAt(width: Dp): Dp {
        var height = PADDING * 2 + TITLE_HEIGHT + BLOCK_GAP + INPUT_HEIGHT + BLOCK_GAP + RESULT_HEIGHT + LINK_HEIGHT
        // A kind with no opinion shown is left out, its title too
        for (rows in listOf(AMMA_OPINIONS.count { it.id in shown }, MIL_OPINIONS.count { it.id in shown })) {
            if (rows > 0) height += BLOCK_GAP + SECTION_TITLE_HEIGHT + (ROW_GAP + OPINION_ROW_HEIGHT) * rows
        }
        return height
    }

    @Composable
    override fun menuItems(state: HomeWidgetsState) = listOf(WidgetMenuItem(HOME_WIDGETS_OPTIONS) { state.optionsOpen = this })

    @Composable
    override fun Options(state: HomeWidgetsState) = MeasuresOptions(state)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = MeasuresCard(state, modifier)
}

internal object MeasuresSettings : WidgetSettings(MeasuresWidget.id) {
    /** The ids of the opinions shown: every one until the user picks. */
    val shown by stringSet(ALL_MEASURE_OPINIONS)
}

/** The converter: an amount, its unit and the unit it goes to, the result, and the opinions of today's figures. */
@Composable
internal fun MeasuresCard(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    var typedText by rememberSaveable { mutableStateOf("1") }
    var from by rememberSaveable { mutableStateOf(LengthUnit.AMMA) }
    var to by rememberSaveable { mutableStateOf(LengthUnit.TEFACH) }
    val shown = state[MeasuresSettings.shown]
    val amount = typedText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val etzbaos = amount?.let { it * from.etzbaos }
    val ammaOpinions = AMMA_OPINIONS.filter { it.id in shown }
    val milOpinions = MIL_OPINIONS.filter { it.id in shown }
    val open = rememberOpenSource(state)
    PanelCard(modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = PADDING),
            // Taller than its content (heightAt) by a row's rounding: spread over its blocks
            verticalArrangement = Arrangement.spacedBy(BLOCK_GAP, Alignment.CenterVertically),
        ) {
            Row(Modifier.fillMaxWidth().height(TITLE_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                Text("ממיר מידות", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
                // Its options at hand, as in its menu
                TitleIconButton(Icons.Outlined.Settings, HOME_WIDGETS_OPTIONS) { state.optionsOpen = MeasuresWidget }
            }
            Row(Modifier.fillMaxWidth().height(INPUT_HEIGHT), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(8.dp)
                BasicTextField(
                    typedText,
                    { typedText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(56.dp).clip(shape).background(c.panel).border(1.dp, c.panelBorder, shape).padding(horizontal = 6.dp, vertical = 9.dp),
                )
                UnitPicker(from, { from = it }, Modifier.weight(1f))
                HoverBox(onClick = {
                    val was = from
                    from = to
                    to = was
                }) {
                    Text("⇄", fontSize = 18.sp, color = c.accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
                UnitPicker(to, { to = it }, Modifier.weight(1f))
            }
            Column {
                Text(
                    etzbaos?.let { "= ${quantity(it / to.etzbaos, to)}" } ?: "–",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(RESULT_HEIGHT).padding(horizontal = 4.dp),
                )
                Row(Modifier.height(LINK_HEIGHT)) {
                    conversionSources(from, to).forEach { SourceLink(it, open) }
                }
            }
            if (ammaOpinions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    SectionTitle(MEASURES_TODAY_TITLE)
                    ammaOpinions.forEach { opinion ->
                        OpinionRow(opinion.label, etzbaos?.let { length(it / ETZBAOS_IN_AMMA * opinion.cm) }, opinion.source, open)
                    }
                }
            }
            if (milOpinions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    SectionTitle(MEASURES_WALK_TITLE)
                    milOpinions.forEach { opinion ->
                        OpinionRow(opinion.label, etzbaos?.let { duration(it / ETZBAOS_IN_AMMA / AMOS_IN_MIL * opinion.minutes * 60) }, opinion.source, open)
                    }
                }
            }
        }
    }
}

/** A box for each opinion, by kind, with its figure and source; each tick shows or hides it at once. */
@Composable
internal fun MeasuresOptions(state: HomeWidgetsState) {
    val c = Zayit.colors
    val shown = state[MeasuresSettings.shown]
    val toggle = { id: String, on: Boolean -> state[MeasuresSettings.shown] = if (on) shown + id else shown - id }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(MEASURES_TODAY_TITLE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        AMMA_OPINIONS.forEach { OptionCheckRow(it.label, "אמה ${length(it.cm)} · ${it.source.ref}", it.id in shown) { on -> toggle(it.id, on) } }
        Text(MEASURES_WALK_TITLE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        MIL_OPINIONS.forEach { OptionCheckRow(it.label, it.source.ref, it.id in shown) { on -> toggle(it.id, on) } }
    }
}

@Composable
private fun UnitPicker(
    unit: LengthUnit,
    onPick: (LengthUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(shape).background(c.panel).border(1.dp, c.panelBorder, shape).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(unit.title, color = c.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(open && isTabInFront(), { open = false }, containerColor = c.panel) {
            LengthUnit.entries.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u.title, color = c.text, fontWeight = if (u == unit) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onPick(u)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Zayit.colors.textSecondary, maxLines = 1, modifier = Modifier.height(SECTION_TITLE_HEIGHT))
}

/** What the amount is after an opinion, with its source. */
@Composable
private fun OpinionRow(
    label: String,
    value: String?,
    source: Source,
    open: (Source) -> Unit,
) {
    val c = Zayit.colors
    Row(Modifier.fillMaxWidth().height(OPINION_ROW_HEIGHT), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = c.text, maxLines = 1)
        SourceLink(source, open, Modifier.weight(1f))
        Text(value ?: "–", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent, maxLines = 1)
    }
}

/** [source]'s reference, opening it in a new tab. */
@Composable
private fun SourceLink(
    source: Source,
    open: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    HoverBox(onClick = { open(source) }, modifier = modifier) {
        Text(
            source.ref,
            fontSize = 11.sp,
            // Its own line, not the body style's 24 sp: in the 22 dp row under the result that line was cut above its
            // underline
            lineHeight = 15.sp,
            color = Zayit.colors.textSecondary,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
        )
    }
}

/** Opens [Source] in a new tab, on its line (by its index in the book, as desktop). */
@Composable
private fun rememberOpenSource(state: HomeWidgetsState): (Source) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(state, scope, context) {
        { source ->
            scope.launch {
                // Read on tap: the books DB is opened when first needed, never by showing the card
                val repository = state.app.libraryStore.repository()
                val book = runCatching { repository.getBookByTitle(source.bookTitle) }.getOrNull()
                if (book == null) {
                    Toast.makeText(context, "הספר לא נמצא בספרייה", Toast.LENGTH_SHORT).show()
                } else {
                    val line = runCatching { repository.getLineByIndex(book.id, source.lineIndex) }.getOrNull()
                    state.openBook(book, line?.id)
                }
            }
        }
    }
}

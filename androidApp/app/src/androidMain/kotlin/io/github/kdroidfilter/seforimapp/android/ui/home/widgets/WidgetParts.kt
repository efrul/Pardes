package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.delay

// The parts the widgets are made of, after desktop 0.6.7 features/home/widgets/WidgetCard.kt and WidgetParts.kt: the
// card frames, a title, rows lit on press, lists as long as the card holds.

val WidgetShape = RoundedCornerShape(18.dp)

/** The frame of the picture widgets (the Earth, the sky, the solar system, the Temple): rounded, bordered, black behind. */
@Composable
fun WidgetCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.clip(WidgetShape).background(Color.Black).border(1.5.dp, Zayit.colors.panelBorder, WidgetShape),
        content = content,
    )
}

/**
 * A card whose content is still being computed off the UI thread (see rememberOffMain). The spinner redraws the window
 * at every frame, and a computation that failed leaves its value null for good: after [LOADING_SPINNER_MS] the card
 * holds still and says so (the content still replaces it if it comes).
 */
@Composable
fun WidgetCardLoading(modifier: Modifier = Modifier) {
    var waiting by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(LOADING_SPINNER_MS)
        waiting = false
    }
    PanelCard(modifier) {
        if (waiting) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Zayit.colors.accent, strokeWidth = 3.dp)
        } else {
            Text(
                "לא ניתן להציג כעת",
                fontSize = 13.sp,
                color = Zayit.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        }
    }
}

private const val LOADING_SPINNER_MS = 10_000L

/** The frame of the text widgets (the calendar, the luach ones): the panel's colour, see-through. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Zayit.colors
    Box(
        modifier
            .clip(WidgetShape)
            .background(c.panel.copy(alpha = if (c.isDark) 0.25f else 0.35f), WidgetShape)
            .border(1.5.dp, c.panelBorder, WidgetShape),
        content = content,
    )
}

/** A text widget's title, with an optional action at the end ("show all", its options). */
@Composable
fun WidgetTitle(
    title: String,
    modifier: Modifier = Modifier,
    end: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Zayit.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        end()
    }
}

/**
 * Its children from the top, as many as fit whole; the others aren't shown. [spread], the room left is shared between
 * the ones shown, above, between and below them.
 */
@Composable
fun FitColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    spread: Boolean = false,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        var y = 0
        val placed =
            buildList {
                for (measurable in measurables) {
                    val placeable = measurable.measure(loose)
                    if (y + placeable.height > constraints.maxHeight) break
                    add(y to placeable)
                    y += placeable.height + gap
                }
            }
        val used = placed.lastOrNull()?.let { (top, placeable) -> top + placeable.height } ?: 0
        val share = if (spread && constraints.hasBoundedHeight) (constraints.maxHeight - used) / (placed.size + 1) else 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else used
        layout(constraints.maxWidth, height) {
            placed.forEachIndexed { i, (top, placeable) -> placeable.placeRelative(0, top + share * (i + 1)) }
        }
    }
}

/** A rounded area that answers a tap ([onClick]); [tinted] shows its area at rest (desktop HoverBox, lit on hover). */
@Composable
fun HoverBox(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (tinted) Zayit.colors.text.copy(alpha = 0.05f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) { content() }
}

/** The accent colour of the app (desktop rememberAccentColor). */
val accentColor: Color @Composable get() = Zayit.colors.accent

/** Desktop's left-to-right isolate around times and dates in Hebrew text: "⁦18:42⁩". */
fun String.ltr() = "⁦$this⁩"

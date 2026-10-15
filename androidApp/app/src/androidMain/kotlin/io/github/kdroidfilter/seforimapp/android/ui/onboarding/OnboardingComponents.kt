package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.components.SearchableOptions
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** True when the flow is shown as the centered desktop-like window (tablets), false on phones. */
internal val LocalOnboardingWide = staticCompositionLocalOf { false }

/**
 * Desktop OnBoardingScaffold: big centered title, a scrolling column of content spaced by 16dp, and the
 * step's button(s) pinned at the bottom.
 */
@Composable
internal fun StepScaffold(
    title: String,
    modifier: Modifier = Modifier,
    bottomAction: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zayit.colors
    val wide = LocalOnboardingWide.current
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            color = c.text,
            fontSize = if (wide) 28.sp else 24.sp,
            lineHeight = if (wide) 34.sp else 30.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        if (bottomAction != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = bottomAction,
            )
        }
    }
}

/** Plain body text of the onboarding (desktop default text style). */
@Composable
internal fun BodyText(text: String, modifier: Modifier = Modifier, fontSize: Int = 15, textAlign: TextAlign = TextAlign.Center) {
    Text(
        text,
        modifier = modifier,
        color = Zayit.colors.text,
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 7).sp,
        textAlign = textAlign,
    )
}

/** A desktop line icon (tinted with the text colour), sized for the window class. */
@Composable
internal fun StepIllustration(icon: ImageVector, wideSize: Dp, phoneSize: Dp, modifier: Modifier = Modifier) {
    val size = if (LocalOnboardingWide.current) wideSize else phoneSize
    Icon(icon, contentDescription = null, tint = Zayit.colors.text, modifier = modifier.size(size))
}

/** Thin animated bar under the title bar (desktop AnimatedHorizontalProgressBar, RTL aware). */
@Composable
internal fun StepProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "OnboardingProgress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawWithContent {
                drawRect(color = c.panelBorder)
                val width = size.width * animated
                val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
                drawRoundRect(
                    color = c.info,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                )
            },
    )
}

/** Desktop DefaultErrorBanner: red-tinted box with an optional link action ("נסה שוב", "בדיקה מחדש"). */
@Composable
internal fun ErrorBanner(text: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val c = Zayit.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.error.copy(alpha = if (c.isDark) 0.16f else 0.07f))
            .border(1.dp, c.error.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(c.error), contentAlignment = Alignment.Center) {
            Text("!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
            if (actionLabel != null) {
                Text(
                    actionLabel,
                    color = c.link,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** Labelled single-line field in the desktop TextField look (accent border while focused). */
@Composable
internal fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    val c = Zayit.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = c.text, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = ZayitFonts.ui),
            cursorBrush = SolidColor(c.accent),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(shape)
                        .background(c.panel)
                        .border(if (focused) 2.dp else 1.dp, if (focused) c.accent else c.panelBorder, shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

/** Labelled drop-down list (desktop ListComboBox); [searchable] adds desktop's type-to-filter (SpeedSearchArea). */
@Composable
internal fun LabeledDropdown(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchable: Boolean = false,
) {
    val c = Zayit.colors
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = if (enabled) c.text else c.textDisabled, fontSize = 14.sp)
        // The list is as wide as its field, like the desktop combobox.
        BoxWithConstraints {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(c.panel)
                    .border(if (expanded) 2.dp else 1.dp, if (expanded) c.accent else c.panelBorder, shape)
                    .clickable(enabled = enabled && items.isNotEmpty(), role = Role.Button) { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    items.getOrNull(selectedIndex).orEmpty(),
                    color = if (enabled) c.text else c.textDisabled,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = label, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
            if (searchable) {
                SearchableOptions(expanded, { expanded = false }, label, items, selectedIndex, onSelect, maxWidth)
            } else {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = c.panel,
                    modifier = Modifier.width(maxWidth).heightIn(max = 320.dp),
                ) {
                    items.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    item,
                                    color = c.text,
                                    fontSize = 15.sp,
                                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                expanded = false
                                onSelect(index)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One slice of the disk-space pie, with its legend text. */
internal class PieSlice(val value: Float, val color: Color, val label: String)

/** Desktop pie (koalaplot) and legend: used / library / free after install. */
@Composable
internal fun DiskPie(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val wide = LocalOnboardingWide.current
    val chart: @Composable () -> Unit = {
        Canvas(Modifier.size(if (wide) 220.dp else 180.dp)) {
            val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: return@Canvas
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.value / total
                if (sweep > 0f) drawArc(color = slice.color, startAngle = start, sweepAngle = sweep, useCenter = true)
                start += sweep
            }
        }
    }
    val legend: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).background(slice.color))
                    BodyText(slice.label, fontSize = 14, textAlign = TextAlign.Start)
                }
            }
        }
    }
    if (wide) {
        // RTL row: the chart sits on the right as in the desktop screenshot, the legend on its left.
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
            chart()
            legend()
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            chart()
            legend()
        }
    }
}

/** Colours of koalaplot's generateHueColorPalette(4), as the desktop pie shows them. */
internal object PieColors {
    val used = Color(0xFFBF4040)
    val library = Color(0xFF80BF40)
    val temporary = Color(0xFF40BFBF)
    val freeAfter = Color(0xFF8040BF)
}

private class ConfettiPiece(random: Random) {
    val delay = random.nextFloat() * 4f
    val angle = random.nextFloat() * 2f * PI.toFloat()
    val speed = 0.25f + random.nextFloat() * 0.45f
    val spin = (random.nextFloat() - 0.5f) * 4f
    val width = 6f + random.nextFloat() * 8f
    val height = 4f + random.nextFloat() * 6f
    val round = random.nextFloat() < 0.4f
    val color = ConfettiColors[random.nextInt(ConfettiColors.size)]
}

/** Konfetti's default colours, used by desktop's ConfettiKit. */
private val ConfettiColors = listOf(Color(0xFFFCE18A), Color(0xFFFF726D), Color(0xFFF4306D), Color(0xFFB48DEF))

/** Bursts of confetti for ~5 s from the middle of the area, like the desktop finish screen. */
@Composable
internal fun Confetti(modifier: Modifier = Modifier) {
    val pieces = remember { Random(7).let { random -> List(150) { ConfettiPiece(random) } } }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (elapsed < 8f) {
            elapsed = (withFrameNanos { it } - start) / 1_000_000_000f
        }
    }
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val origin = Offset(size.width / 2, size.height * 0.4f)
        pieces.forEach { piece ->
            val t = elapsed - piece.delay
            if (t < 0f || t > 3f) return@forEach
            val x = origin.x + cos(piece.angle) * piece.speed * unit * t
            val y = origin.y + sin(piece.angle) * piece.speed * unit * t + 0.35f * unit * t * t
            val alpha = if (t > 2.5f) (3f - t) * 2f else 1f
            val w = piece.width * density / 2
            val h = piece.height * density / 2
            rotate(degrees = piece.spin * t * 180f, pivot = Offset(x, y)) {
                if (piece.round) {
                    drawCircle(color = piece.color, radius = h, center = Offset(x, y), alpha = alpha)
                } else {
                    drawRect(color = piece.color, topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h), alpha = alpha)
                }
            }
        }
    }
}

private val InlineMarkdown = Regex("""\*\*(.+?)\*\*|\[(.+?)]\((\S+?)\)""")

/** `**bold**` and `[label](url)` of the licence text, links in the accent colour as on desktop. */
private fun AnnotatedString.Builder.appendInline(text: String, link: Color) {
    var index = 0
    for (match in InlineMarkdown.findAll(text)) {
        append(text.substring(index, match.range.first))
        val bold = match.groupValues[1]
        if (bold.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(bold, link) }
        } else {
            val url = match.groupValues[3]
            withLink(LinkAnnotation.Url(url, TextLinkStyles(style = SpanStyle(color = link, fontWeight = FontWeight.Bold)))) {
                appendInline(match.groupValues[2], link)
            }
        }
        index = match.range.last + 1
    }
    append(text.substring(index))
}

/** The small Markdown subset of CONDITIONS.md: `## ` headings with a rule under them, and paragraphs. */
@Composable
internal fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    val blocks = remember(markdown) {
        markdown.trim().split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            if (block.startsWith("#")) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(block.trimStart('#').trim(), color = c.text, fontSize = 20.sp, lineHeight = 28.sp, textAlign = TextAlign.Start)
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = c.divider)
                }
            } else {
                val paragraph = block.lines().joinToString(" ") { it.trim() }
                Text(
                    buildAnnotatedString { appendInline(paragraph, c.link) },
                    color = c.text,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Metrics row of the desktop DownloadProgressDetails: an icon and a value. */
@Composable
internal fun MetricRow(icon: ImageVector, text: String) {
    val c = Zayit.colors
    // Desktop: the icon, then the value in a 175dp column aligned to its far end (left in RTL).
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(16.dp))
        Text(text, color = c.text, fontSize = 15.sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(175.dp))
    }
}

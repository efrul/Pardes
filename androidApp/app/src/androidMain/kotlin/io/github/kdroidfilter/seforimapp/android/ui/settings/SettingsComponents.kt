package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.components.SearchableOptions
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts

internal val CardShape = RoundedCornerShape(10.dp)

/** Scrollable page body (desktop: VerticallyScrollableContainer + Column(padding 16, spacedBy 12)). */
@Composable
internal fun SettingsColumn(wide: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val insets = if (wide) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(insets).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Bordered card of the settings pages (desktop SectionCard / SettingCard frame). */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Zayit.colors
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(c.panel).border(1.dp, c.panelBorder, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Desktop SectionCard title: 15sp.
        if (title != null) Text(title, fontSize = 15.sp, color = c.text)
        content()
    }
}

/** Desktop SettingCard: a checkbox with its title, and a chevron that unfolds the description. */
@Composable
internal fun SettingCheckCard(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Zayit.colors
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(CardShape).background(c.panel).border(1.dp, c.panelBorder, CardShape)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(12.dp),
                colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
            )
            Text(title, fontSize = 15.sp, lineHeight = 20.sp, color = c.text, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            ZIconButton(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "הסבר", { expanded = !expanded }, tint = c.textSecondary)
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                description,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 14.dp),
            )
        }
    }
}

/** A label with its control beside it (desktop) or under it when the card is too narrow (small phones). */
@Composable
internal fun LabeledControl(label: String, controlWidth: Dp = 170.dp, control: @Composable (Modifier) -> Unit) {
    val c = Zayit.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < controlWidth * 2) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontSize = 15.sp, color = c.text)
                control(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, fontSize = 15.sp, color = c.text, modifier = Modifier.weight(1f))
                control(Modifier.width(controlWidth))
            }
        }
    }
}

/** Title + description with an action button (desktop DataActionCard); the button wraps under on narrow cards. */
@Composable
internal fun ActionRow(title: String, description: String, action: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTexts(title, description, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { action() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTexts(title, description, Modifier.weight(1f))
                action()
            }
        }
    }
}

@Composable
private fun ActionTexts(title: String, description: String, modifier: Modifier) {
    val c = Zayit.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 15.sp, color = c.text)
        Text(description, fontSize = 13.sp, lineHeight = 19.sp, color = c.textSecondary)
    }
}

/** Desktop ListComboBox: the current value with a chevron, opening a menu of the options. */
@Composable
internal fun ZDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    optionFonts: List<FontFamily>? = null,
    // Long lists (the region): the open list filters as the user types, like desktop's SpeedSearchArea.
    searchTitle: String? = null,
) {
    val c = Zayit.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BoxWithConstraints(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(c.panel)
                .border(1.dp, if (open) c.accent else c.textDisabled.copy(alpha = 0.55f), shape)
                .clickable(enabled = enabled, role = Role.Button) { open = true }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                options.getOrNull(selectedIndex) ?: placeholder,
                fontSize = 15.sp,
                color = if (enabled) c.text else c.textDisabled,
                fontFamily = optionFonts?.getOrNull(selectedIndex),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ExpandMore, null, tint = if (enabled) c.textSecondary else c.textDisabled, modifier = Modifier.size(20.dp))
        }
        if (searchTitle != null) {
            SearchableOptions(open, { open = false }, searchTitle, options, selectedIndex, onSelect, maxWidth)
        } else {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(maxWidth).heightIn(max = 360.dp)) {
                options.forEachIndexed { i, label ->
                    val sel = i == selectedIndex
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                fontSize = 15.sp,
                                color = c.text,
                                fontFamily = optionFonts?.getOrNull(i),
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(i)
                        },
                        leadingIcon = {
                            if (sel) Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp))
                        },
                    )
                }
            }
        }
    }
}

/** Single-line text field in the desktop look (bordered, accent border when focused). */
@Composable
internal fun ZTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = ZayitFonts.ui),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(c.panel)
                    .border(if (focused) 2.dp else 1.dp, if (focused) c.accent else c.textDisabled.copy(alpha = 0.55f), shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

/** Red inline banner (Jewel InlineErrorBanner). */
@Composable
internal fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.error.copy(alpha = if (c.isDark) 0.18f else 0.07f))
            .border(1.dp, c.error.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(c.error), contentAlignment = Alignment.Center) {
            Text("!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = c.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** Accent-coloured text link (Jewel Link). */
@Composable
internal fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Zayit.colors.link,
        fontSize = 15.sp,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
    )
}

internal fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}

// --- Minimal Markdown (headings, paragraphs, quotes, **bold**, [links](url)) for ABOUT.md / CONDITIONS.md ---

private sealed interface MdBlock {
    val text: String

    data class Heading(val level: Int, override val text: String) : MdBlock

    data class Paragraph(override val text: String) : MdBlock

    data class Quote(override val text: String) : MdBlock
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()
    val quote = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
        if (quote.isNotBlank()) out += MdBlock.Quote(quote.toString().trim())
        para.clear()
        quote.clear()
    }
    for (raw in src.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                out += MdBlock.Heading(level, line.drop(level).trim())
            }
            line.startsWith(">") -> quote.append(line.removePrefix(">").trim()).append(' ')
            else -> para.append(line).append(' ')
        }
    }
    flush()
    return out
}

private val mdLinkRegex = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

private fun inlineMarkdown(text: String, linkStyle: SpanStyle, uriHandler: UriHandler): AnnotatedString = buildAnnotatedString {
    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    var bold = false
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            bold = !bold
            i += 2
            continue
        }
        if (text[i] == '[') {
            val m = mdLinkRegex.find(text, i)?.takeIf { it.range.first == i }
            if (m != null) {
                val label = m.groupValues[1]
                val url = m.groupValues[2]
                val inner = label.removePrefix("**").removeSuffix("**")
                val listener = object : LinkInteractionListener {
                    override fun onClick(link: LinkAnnotation) = uriHandler.openSafely(url)
                }
                withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle), listener)) {
                    if (bold || inner != label) withStyle(boldStyle) { append(inner) } else append(inner)
                }
                i = m.range.last + 1
                continue
            }
        }
        var end = i + 1
        while (end < text.length && !text.startsWith("**", end) && text[end] != '[') end++
        val chunk = text.substring(i, end)
        if (bold) withStyle(boldStyle) { append(chunk) } else append(chunk)
        i = end
    }
}

/** Renders the desktop markdown pages (AccentMarkdownView): accent links, justified paragraphs. */
@Composable
internal fun MarkdownText(source: String, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    val uriHandler = LocalUriHandler.current
    val linkStyle = SpanStyle(color = c.link)
    val blocks = remember(source, c.link) { parseMarkdown(source).map { it to inlineMarkdown(it.text, linkStyle, uriHandler) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { (block, text) ->
            when (block) {
                is MdBlock.Heading -> Column(Modifier.padding(top = if (block.level <= 2) 8.dp else 0.dp)) {
                    Text(text, fontSize = if (block.level <= 2) 21.sp else 17.sp, color = c.text, lineHeight = 28.sp)
                    if (block.level <= 2) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(c.divider))
                }
                is MdBlock.Paragraph -> Text(text, fontSize = 15.sp, lineHeight = 23.sp, color = c.text, textAlign = TextAlign.Justify)
                is MdBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 12.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(c.panelBorder))
                    Text(
                        text,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = c.textSecondary,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

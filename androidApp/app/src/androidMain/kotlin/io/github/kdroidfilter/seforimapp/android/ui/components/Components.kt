package io.github.kdroidfilter.seforimapp.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.delay

val IslandShape = RoundedCornerShape(10.dp)

/** A panel of the desktop "Islands" style: white rounded surface with a hairline border. */
@Composable
fun Island(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zayit.colors
    Column(
        modifier
            .clip(IslandShape)
            .background(c.panel)
            .border(1.dp, c.panelBorder, IslandShape),
    ) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
                titleActions()
            }
        }
        content()
    }
}

/** Gold pill button used for every primary action on desktop ("המשך", "התחל", "סגור"). */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Zayit.colors
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) c.accent else c.selectedRow)
            .border(1.dp, if (enabled) c.accent else c.panelBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) c.onAccent else c.textDisabled, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Zayit.colors
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.panelBorder, RoundedCornerShape(10.dp))
            .background(c.panel)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) c.text else c.textDisabled, fontSize = 15.sp)
    }
}

/** Square icon button with a small caption underneath, like the desktop side rails. */
@Composable
fun RailButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    width: Dp = 64.dp,
) {
    val c = Zayit.colors
    // Desktop compact mode (Settings › תצוגה): smaller square buttons without captions, for the side rails only (a
    // set width), not the phone toolbar's weighted ones.
    val compact = width != Dp.Unspecified &&
        (LocalContext.current.applicationContext as? ZayitApplication)?.prefs?.compactRails?.collectAsState()?.value == true
    Column(
        modifier
            .width(if (compact) 48.dp else width)
            .then(if (compact) Modifier.height(48.dp) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.selectedRow else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> c.textDisabled
                selected -> c.text
                else -> c.text
            },
            modifier = Modifier.size(if (compact) 22.dp else 24.dp),
        )
        if (!compact) {
            Text(
                label,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = if (enabled) c.textSecondary else c.textDisabled,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Plain icon button with a 48dp touch target. */
@Composable
fun ZIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Zayit.colors.text, selected: Boolean = false) {
    val c = Zayit.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) c.accentTint else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Filter chip in the desktop look (search result filters, commentator chips). */
@Composable
fun ZChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) c.accentTint else c.panel)
            .border(1.dp, if (selected) c.accent else c.panelBorder, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Icon(leading, null, tint = if (selected) c.accent else c.textSecondary, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 14.sp, color = if (selected) c.text else c.textSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

/** Checkbox row with the accent colour, as in the commentator list and the settings. */
@Composable
fun ZCheckRow(text: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
        )
        Text(text, fontSize = 15.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** One choice of several, as [ZCheckRow] is one switch (desktop RadioButtonRow): its mark, then [text]. */
@Composable
fun ZRadioRow(text: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The row takes the tap: the mark alone has no target of its own
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.textSecondary))
        Text(text, fontSize = 15.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LoadingBox(
    modifier: Modifier = Modifier,
    // A wait usually shorter than this shows no spinner at all, rather than one that flashes in and out
    delayMillis: Long = 0L,
) {
    val shown by produceState(delayMillis <= 0L) {
        if (!value) {
            delay(delayMillis)
            value = true
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (shown) CircularProgressIndicator(color = Zayit.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Zayit.colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, success: Boolean = false) {
    InfoBanner(modifier, success) { Text(text, color = Zayit.colors.text, fontSize = 14.sp, lineHeight = 20.sp) }
}

/**
 * [InfoBanner] around a body of its own (desktop's InlineInformationBanner with content): its mark beside [content],
 * at the [markAlignment] of its height.
 */
@Composable
fun InfoBanner(
    modifier: Modifier = Modifier,
    success: Boolean = false,
    markAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zayit.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (success) c.successTint else c.infoTint)
            .border(1.dp, (if (success) c.success else c.info).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = markAlignment,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(if (success) c.success else c.info),
            contentAlignment = Alignment.Center,
        ) { Text(if (success) "✓" else "i", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Zayit.colors.textSecondary,
    )
}

@Composable
fun HSpacer(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpacer(h: Dp) = Spacer(Modifier.height(h))

val ContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun Modifier.maxContentWidth(max: Dp = 760.dp) = this.widthIn(max = max)

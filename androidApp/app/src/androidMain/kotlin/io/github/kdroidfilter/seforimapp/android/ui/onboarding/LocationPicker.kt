package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.LibraryLocation
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/**
 * Android only: where the library goes when there is a memory card ([LocationChoice]s: the phone, then each card),
 * one selectable row each with its free space and why it cannot take the library, if so. Desktop has no such choice.
 */
@Composable
internal fun LocationPicker(choices: List<LocationChoice>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 520.dp).fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(AndroidStrings.locationTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Zayit.colors.text)
        choices.forEach { choice ->
            val warning = if (!choice.fits) AndroidStrings.locationNotEnough else null
            val space = AndroidStrings.locationSpace.format(formatBytes(choice.space.availableBytes), formatBytes(choice.space.totalBytes))
            LocationRow(
                icon = locationIcon(choice.location),
                title = choice.location.label,
                // FAT32 takes the database in chunks (SplitDatabase): said, not a reason to refuse.
                detail = if (choice.location.isFat) space + "\n" + AndroidStrings.locationFat else space,
                warning = warning,
                selected = choice.location.key == selectedKey,
                onClick = { onSelect(choice.location.key) },
            )
        }
    }
}

internal fun locationIcon(location: LibraryLocation): ImageVector = if (location.removable) Icons.Outlined.SdCard else Icons.Outlined.PhoneAndroid

/** One place, as a radio row: icon, name, free space and an optional warning in the error colour. */
@Composable
internal fun LocationRow(icon: ImageVector, title: String, detail: String, warning: String?, selected: Boolean, onClick: () -> Unit) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.accentTint else c.panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.panelBorder, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The radio mark first: on the right in RTL, where a Hebrew reader starts.
        Box(
            Modifier.size(20.dp).border(2.dp, if (selected) c.accent else c.textSecondary, CircleShape).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(c.accent, CircleShape))
        }
        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.text)
            Text(detail, fontSize = 13.sp, color = c.textSecondary)
            if (warning != null) Text(warning, fontSize = 13.sp, color = c.error)
        }
    }
}

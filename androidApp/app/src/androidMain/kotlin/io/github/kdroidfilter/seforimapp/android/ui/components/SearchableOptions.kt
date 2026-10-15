package io.github.kdroidfilter.seforimapp.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/**
 * The open list of a combobox that narrows as the user types: the touch version of desktop's SpeedSearchArea around
 * the region ListComboBoxes (onboarding/region/RegionConfigScreen.kt, settings/ui/ProfileSettingsScreen.kt). On a
 * phone it is a bottom sheet with a filter field over the list; from 600dp a drop-down under the field (call it from
 * the field's Box, like a DropdownMenu) with the same filter field on top. [onSelect] gets the index in [options].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableOptions(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    menuWidth: Dp,
) {
    if (!expanded) return
    val c = Zayit.colors
    var query by remember { mutableStateOf("") }
    val matches = remember(options, query) {
        val q = normalize(query)
        options.indices.filter { q.isEmpty() || normalize(options[it]).contains(q) }
    }
    val pick: (Int) -> Unit = { i ->
        onDismiss()
        onSelect(i)
    }
    if (LocalConfiguration.current.screenWidthDp >= 600) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, containerColor = c.panel, modifier = Modifier.width(menuWidth).heightIn(max = 380.dp)) {
            FilterField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
            matches.forEach { i -> OptionRow(options[i], i == selectedIndex) { pick(i) } }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panel) {
            Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            FilterField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
                items(matches, key = { it }) { i -> OptionRow(options[i], i == selectedIndex, Modifier.padding(horizontal = 8.dp)) { pick(i) } }
            }
        }
    }
}

// Typing "תל אביב" or "תל-אביב", "בייג'ינג" or "בייגינג" finds the same rows: spaces, dashes and geresh signs are not compared.
private fun normalize(s: String): String = s.lowercase().filterNot { it.isWhitespace() || it in "'\"׳״-־" }

@Composable
private fun FilterField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(color = c.text, fontSize = 16.sp),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(c.ground)
                    .border(1.dp, c.panelBorder, shape)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { inner() }
            }
        },
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Zayit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) c.selectedRow else c.panel)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = c.text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

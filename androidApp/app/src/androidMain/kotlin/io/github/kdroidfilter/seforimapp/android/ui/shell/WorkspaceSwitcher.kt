package io.github.kdroidfilter.seforimapp.android.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToQueue
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.AppUiState
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.MAX_WORKSPACE_NAME_LENGTH
import io.github.kdroidfilter.seforimapp.android.ui.Workspace
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimapp.android.ui.workspaceName

/*
 * Workspaces ("מרחבים"): desktop 0.6.7 core/presentation/components/DesktopSwitcher.kt. A tablet has desktop's
 * 110dp title-bar trigger (icon, name, ▾) with its dropdown; a phone shows the same trigger at the top of the open-tabs
 * page and the list in a bottom sheet. Rows: switch on tap, rename in place (8 letters at most), delete when there are
 * several, and "מרחב חדש" named after the next Hebrew numeral. Touch: the row actions are always visible.
 * Not ported: opening a workspace in a new window (one window on Android), drag reordering, and the compact trigger
 * (icon and ▾ only) that 0.6.5 ec91acdc uses when the tab strip, with its "עוד N" chip, needs the room: the tablet strip
 * here scrolls instead (PARITY.md).
 */

/** Desktop DesktopSwitcherTrigger. */
@Composable
fun WorkspaceTrigger(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "מרחבים", onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.DesktopWindows, null, tint = c.text, modifier = Modifier.size(16.dp))
        Text(name, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.text, modifier = Modifier.size(16.dp))
    }
}

/** Tablet: the trigger in the title bar and its dropdown (desktop 110dp trigger, 172dp list; a bit wider for touch). */
@Composable
fun WorkspaceSwitcher(state: AppUiState, appVm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        WorkspaceTrigger(state.activeWorkspace?.name ?: workspaceName(1), { open = !open }, Modifier.width(110.dp))
        if (open) {
            TitleBarPopup(onDismiss = { open = false }) {
                WorkspaceList(state, appVm, onDismiss = { open = false })
            }
        }
    }
}

/** The workspace rows and "מרחב חדש" (desktop DesktopDropdownContent). */
@Composable
fun ColumnScope.WorkspaceList(state: AppUiState, appVm: AppViewModel, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf<String?>(null) }
    state.workspaces.forEach { ws ->
        if (renaming == ws.id) {
            RenameRow(ws, onRename = { name ->
                appVm.renameWorkspace(ws.id, name)
                renaming = null
            }, onCancel = { renaming = null })
        } else {
            WorkspaceRow(
                workspace = ws,
                active = ws.id == state.activeWorkspaceId,
                canDelete = state.workspaces.size > 1,
                onClick = {
                    appVm.switchWorkspace(ws.id)
                    onDismiss()
                },
                onRename = { renaming = ws.id },
                onDelete = {
                    appVm.deleteWorkspace(ws.id)
                    if (state.workspaces.size <= 2) onDismiss()
                },
            )
        }
    }
    HorizontalDivider(color = Zayit.colors.divider)
    val c = Zayit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                appVm.createWorkspace()
                onDismiss()
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.AddToQueue, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Text("מרחב חדש", color = c.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun WorkspaceRow(workspace: Workspace, active: Boolean, canDelete: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) c.accentTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            workspace.name,
            color = c.text,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SmallAction(Icons.Outlined.Edit, "שנה שם", onRename)
        if (canDelete) SmallAction(Icons.Outlined.Close, "מחק", onDelete)
    }
}

@Composable
private fun RenameRow(workspace: Workspace, onRename: (String) -> Unit, onCancel: () -> Unit) {
    val c = Zayit.colors
    // Desktop DesktopRenameField: the whole name selected, so typing replaces it. A caret at the start left room for
    // one letter, the default name taking 7 of the 8 allowed.
    var value by remember { mutableStateOf(TextFieldValue(workspace.name, selection = TextRange(0, workspace.name.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).height(38.dp).clip(shape).border(1.dp, c.accent, shape).padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value,
                // Desktop's rule: an edit past the limit is refused (cutting it would leave the caret past the text)
                { if (it.text.length <= MAX_WORKSPACE_NAME_LENGTH) value = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontFamily = ZayitFonts.ui),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onRename(value.text) else onCancel() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        SmallAction(Icons.Outlined.Check, "שנה שם") { if (value.text.isNotBlank()) onRename(value.text) else onCancel() }
        SmallAction(Icons.Outlined.Close, "ביטול", onCancel)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Zayit.colors.textSecondary, modifier = Modifier.size(16.dp))
    }
}

/** Phone: the trigger above the open tabs, opening the list in a sheet. */
@Composable
fun PhoneWorkspaceBar(state: AppUiState, appVm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        WorkspaceTrigger(state.activeWorkspace?.name ?: workspaceName(1), { open = true }, Modifier.widthIn(max = 200.dp))
    }
    if (open) {
        ShellSheet(onDismiss = { open = false }) {
            Text("מרחבים", color = Zayit.colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            Column { WorkspaceList(state, appVm, onDismiss = { open = false }) }
        }
    }
}

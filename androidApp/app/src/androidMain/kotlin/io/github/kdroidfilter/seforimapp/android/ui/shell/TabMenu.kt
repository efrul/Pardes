package io.github.kdroidfilter.seforimapp.android.ui.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/** One entry of a tab's menu. */
class TabMenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * A tab's context menu, desktop 0.6.1 core/presentation/tabs/TabsView.kt (990-1090) in its order and words: pin,
 * "העבר ל<מרחב>" for each other workspace, copy link, close all, close others, close right / left. Bulk closes skip
 * pinned tabs. Android adds "שתף" (the link through the share sheet) and, for a pinned tab that has no ×, "סגור לשונית".
 * "פתח בחלון חדש" is left out: one window. In the phone's vertical list ([vertical]) "right" and "left" become
 * above and below.
 */
fun tabMenuItems(context: Context, appVm: AppViewModel, index: Int, vertical: Boolean): List<TabMenuItem> {
    val state = appVm.state.value
    val tab = state.tabs.getOrNull(index) ?: return emptyList()
    val count = state.tabs.size
    val link = tab.destination.toShareLink(appVm.anchorOf(tab.id))
    return buildList {
        add(TabMenuItem(if (tab.pinned) "בטל הצמדה" else "הצמד לשונית", Icons.Outlined.PushPin) { appVm.setPinned(index, !tab.pinned) })
        if (count > 1) {
            state.workspaces.filter { it.id != state.activeWorkspaceId }.forEach { ws ->
                add(TabMenuItem("העבר ל${ws.name}", Icons.Outlined.DesktopWindows) { appVm.moveTabToWorkspace(index, ws.id) })
            }
        }
        if (link != null) {
            add(TabMenuItem("העתק קישור", Icons.Outlined.Link) { copyLink(context, link) })
            add(TabMenuItem("שתף", Icons.Outlined.Share) { shareLink(context, tab.title, link) })
        }
        if (tab.pinned) add(TabMenuItem("סגור לשונית", Icons.Outlined.Close) { appVm.closeTab(index) })
        add(TabMenuItem("סגור את כל הלשוניות", Icons.Outlined.Close) { appVm.closeAll() })
        if (count > 1) add(TabMenuItem("סגור לשוניות אחרות", Icons.Outlined.Close) { appVm.closeOthers(index) })
        // RTL: the tabs before this one stand on its right.
        if (index > 0) add(TabMenuItem(if (vertical) "סגור את הלשוניות שמעל" else "סגור את הלשוניות מימין", Icons.Outlined.Close) { appVm.closeBefore(index) })
        if (index < count - 1) add(TabMenuItem(if (vertical) "סגור את הלשוניות שמתחת" else "סגור את הלשוניות משמאל", Icons.Outlined.Close) { appVm.closeAfter(index) })
    }
}

fun copyLink(context: Context, link: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pardes", link))
}

fun shareLink(context: Context, title: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link).putExtra(Intent.EXTRA_SUBJECT, title)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Tablet: the menu anchored on the long-pressed tab. */
@Composable
fun TabContextMenu(expanded: Boolean, onDismiss: () -> Unit, items: () -> List<TabMenuItem>) {
    ShellMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items().forEach { item ->
            DropdownMenuItem(
                text = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(item.icon, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            )
        }
    }
}

/** Phone: the same items in a bottom sheet, under the tab's title. */
@Composable
fun TabMenuSheet(title: String, items: List<TabMenuItem>, onDismiss: () -> Unit) {
    val c = Zayit.colors
    ShellSheet(onDismiss) {
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onDismiss()
                        item.onClick()
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(item.icon, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                Text(item.label, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

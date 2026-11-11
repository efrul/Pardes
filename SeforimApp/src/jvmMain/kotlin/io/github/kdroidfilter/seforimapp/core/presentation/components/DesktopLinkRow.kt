package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdroid.gematria.converter.toHebrewNumeral
import io.github.kdroidfilter.seforimapp.core.deeplink.shareLink
import io.github.kdroidfilter.seforimapp.core.presentation.utils.setText
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeNavigationEvent
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.desktop_copy_link
import seforimapp.seforimapp.generated.resources.desktop_default_name

/**
 * The desktops' menu's last row: copies this window's desktop as one link, its tabs each where it stands
 * ([shareLink]), to paste into the search bar later or on another machine, where it opens as a new desktop. Not shown
 * for a desktop none of whose tabs can be linked to (a Home tab alone).
 */
@Composable
internal fun DesktopLinkRow(onDismiss: () -> Unit) {
    val desktopManager = LocalAppGraph.current.desktopManager
    val desktopId by LocalOpenWindow.current.desktopId.collectAsState()
    val desktops by desktopManager.desktops.collectAsState()
    val name = desktops.find { it.id == desktopId }?.name.orEmpty()
    // Read as the menu opens: the tabs don't move under it
    val link = remember(desktopId, name) { desktopManager.snapshotOpenDesktop(desktopId).shareLink(name) } ?: return

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val info = JewelTheme.globalColors.text.info
    // The menu's "new desktop" row, with its hover
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(if (isHovered) accent.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(4.dp))
                .clickable(interactionSource = interactionSource, indication = null) {
                    // The menu closes once the link is copied: closing it first would cancel the copy with this scope
                    scope.launch {
                        clipboard.setText(link)
                        onDismiss()
                    }
                }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(key = AllIconsKeys.ToolbarDecorator.AddLink, contentDescription = null, modifier = Modifier.size(16.dp), tint = info)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = stringResource(Res.string.desktop_copy_link), fontSize = 12.sp, color = info)
    }
}

/**
 * Opens the desktop a link pasted into a search bar names ([SearchHomeNavigationEvent.OpenDesktop]): a new desktop
 * holding the link's tabs, which this window switches to, as it does to a desktop made in the menu. A link without a
 * name gets the next default one, and so does one whose name a desktop here already has: the first desktop is named
 * alike on every machine.
 */
@Composable
internal fun rememberDesktopOpener(): (SearchHomeNavigationEvent.OpenDesktop) -> Unit {
    val desktopManager = LocalAppGraph.current.desktopManager
    val windowId = LocalOpenWindow.current.id
    val desktops by desktopManager.desktops.collectAsState()
    val nextName by rememberUpdatedState(
        stringResource(Res.string.desktop_default_name, (desktops.size + 1).toHebrewNumeral(includeGeresh = false) + "׳"),
    )
    return remember(desktopManager, windowId) {
        { link ->
            val name = link.name.takeIf { it.isNotBlank() && desktops.none { desktop -> desktop.name == it } }
            desktopManager.createDesktop(windowId, name ?: nextName, link.tabs)
        }
    }
}

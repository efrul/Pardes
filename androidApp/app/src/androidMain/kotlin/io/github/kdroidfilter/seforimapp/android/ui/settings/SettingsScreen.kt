package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.components.HSpacer
import io.github.kdroidfilter.seforimapp.android.ui.components.Island
import io.github.kdroidfilter.seforimapp.android.ui.shell.PhoneHeader
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.icons.BootstrapPersonLines
import io.github.kdroidfilter.seforimapp.icons.FileArrowDown
import io.github.kdroidfilter.seforimapp.icons.LucideInspectionPanel
import io.github.kdroidfilter.seforimapp.icons.MaterialIconsDisplay_settings
import io.github.kdroidfilter.seforimapp.icons.RadixFontRoman
import io.github.kdroidfilter.seforimapp.icons.TablerInfoSquare
import io.github.kdroidfilter.seforimapp.icons.TablerLicense

/**
 * The pages of the desktop settings window, in the sidebar order and with its icons (SettingsSidebar.kt). [summary]
 * is the phone page list's second line (Android only).
 */
enum class SettingsPage(val title: String, val summary: String, val icon: ImageVector) {
    General("כללי", "גרסה ועדכונים, עץ הספרים, שמירת מצב ומסך", LucideInspectionPanel),
    Display("תצוגה", "צבע מבטא, תמונת רקע ומפרשים בעמוד", MaterialIconsDisplay_settings),
    Profile("פרופיל", "שם, עדה ואזור", BootstrapPersonLines),
    Fonts("גופנים", "גופני הספר, המפרשים, התרגומים והמקורות", RadixFontRoman),
    // Its first card is the database updates (the app's own updates are in General, as on desktop)
    Data("ניהול נתונים", "עדכוני מסד הנתונים, מיקום הספרייה, גיבוי ואיפוס", FileArrowDown),
    About("אודות", "על פרדס ותודות", TablerInfoSquare),
    Terms("תנאים", "מקורות, זכויות ורישיון", TablerLicense),
    // Android only (desktop has no such page): the full licence texts the APK must carry
    Licences("רישיונות", "רישיון פרדס ורישיונות הספריות, הגופנים והנתונים", Icons.Outlined.Copyright),
}

/**
 * Settings, mirroring desktop 0.6.1's settings window (features/settings/SettingsWindow.kt and SettingsSidebar.kt):
 * on phones a list of the pages, each opening full-screen; on tablets the desktop two-pane layout (page list on the
 * right, page on the left, "סגור" at the bottom).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, wide: Boolean) {
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { n -> SettingsPage.entries.firstOrNull { it.name == n } }
    if (wide) {
        WideSettings(page ?: SettingsPage.General, onPage = { pageName = it.name }, onBack = onBack)
    } else {
        BackHandler(enabled = page != null) { pageName = null }
        Column(Modifier.fillMaxSize().background(Zayit.colors.ground)) {
            if (page == null) {
                PhoneHeader("הגדרות", onBack)
                PageList(onOpen = { pageName = it.name })
            } else {
                PhoneHeader(page.title, onBack = { pageName = null })
                Box(Modifier.weight(1f).fillMaxWidth()) { PageContent(page, wide = false) }
            }
        }
    }
}

@Composable
private fun PageContent(page: SettingsPage, wide: Boolean) {
    when (page) {
        SettingsPage.General -> GeneralSettings(wide)
        SettingsPage.Display -> DisplaySettings(wide)
        SettingsPage.Profile -> ProfileSettings(wide)
        SettingsPage.Fonts -> FontsSettings(wide)
        SettingsPage.Data -> DataSettings(wide)
        SettingsPage.About -> AboutSettings(wide)
        SettingsPage.Terms -> TermsSettings(wide)
        SettingsPage.Licences -> LicencesSettings(wide)
    }
}

/** Phone: the sidebar becomes a list of pages with an icon, a summary and a chevron. */
@Composable
private fun PageList(onOpen: (SettingsPage) -> Unit) {
    val c = Zayit.colors
    // Jelly Star: tighter margins and a smaller tile leave the summaries about 130dp instead of 100dp
    val narrow = LocalConfiguration.current.screenWidthDp < 300
    val rowPadding = if (narrow) 10.dp else 14.dp
    val tile = if (narrow) 32.dp else 38.dp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = if (narrow) 8.dp else 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Island(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            SettingsPage.entries.forEachIndexed { i, p ->
                if (i > 0) Box(Modifier.padding(start = rowPadding * 2 + tile).fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Button) { onOpen(p) }
                        .padding(horizontal = rowPadding, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rowPadding),
                ) {
                    Box(Modifier.size(tile).clip(RoundedCornerShape(10.dp)).background(c.accentTint), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, tint = c.accent, modifier = Modifier.size(if (narrow) 19.dp else 22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.summary, fontSize = 13.sp, lineHeight = 17.sp, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textSecondary, modifier = Modifier.size(if (narrow) 18.dp else 22.dp))
                }
            }
        }
    }
}

/** Tablet: the desktop settings window (sidebar on the right, page on the left, "סגור" at the bottom). */
@Composable
private fun WideSettings(page: SettingsPage, onPage: (SettingsPage) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Zayit.colors.ground)) {
        PhoneHeader("הגדרות", onBack)
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Island(Modifier.width(240.dp).fillMaxHeight()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsPage.entries.forEach { p -> SidebarItem(p, p == page) { onPage(p) } }
                }
            }
            Island(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { PageContent(page, wide = true) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Zayit.colors.divider))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                    AccentButton("סגור", onBack)
                }
            }
        }
    }
}

/** Desktop SidebarItem: accent bar, icon and label; the selected page is tinted and semibold. */
@Composable
private fun SidebarItem(page: SettingsPage, selected: Boolean, onClick: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.accent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(20.dp).background(if (selected) c.accent else Color.Transparent))
        HSpacer(10.dp)
        Icon(page.icon, null, tint = c.text, modifier = Modifier.size(20.dp))
        HSpacer(10.dp)
        Text(page.title, fontSize = 15.sp, color = c.text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

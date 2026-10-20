package io.github.kdroidfilter.seforimapp.android.ui.settings

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.data.Community
import io.github.kdroidfilter.seforimapp.android.data.worldPlaces
import io.github.kdroidfilter.seforimapp.android.packageInfoCompat
import io.github.kdroidfilter.seforimapp.android.ui.components.HSpacer
import io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton
import io.github.kdroidfilter.seforimapp.android.ui.theme.AccentColor
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFont
import io.github.kdroidfilter.seforimapp.android.update.AppUpdateStrings
import io.github.kdroidfilter.seforimapp.android.update.AppUpdater

/*
 * The pages of desktop 0.6.7's settings window (features/settings/ui): GeneralSettingsScreen, DisplaySettingsScreen,
 * ProfileSettingsScreen and FontsSettingsScreen. DataSettings.kt and AboutSettings.kt hold the other three.
 */

/** Verse shown under each font choice (desktop FontsSettingsScreen.PREVIEW_TEXT). */
internal const val PREVIEW_TEXT = "הֲבֵ֤ל הֲבָלִים֙ אָמַ֣ר קֹהֶ֔לֶת הֲבֵ֥ל הֲבָלִ֖ים הַכֹּ֥ל הָֽבֶל׃"

@Composable
internal fun appPrefs(): AppPrefs = (LocalContext.current.applicationContext as ZayitApplication).prefs

internal fun appVersion(context: Context): String = runCatching {
    context.packageManager.packageInfoCompat(context.packageName, 0).versionName ?: "?"
}.getOrDefault("?")

// ---------------------------------------------------------------- כללי

/**
 * Desktop GeneralSettingsScreen: the app header (with the update banner), then the three checkbox cards; and, Android
 * only, whether to look for a newer Pardes at every start (desktop always does).
 */
@Composable
internal fun GeneralSettings(wide: Boolean) {
    val prefs = appPrefs()
    val closeTree by prefs.closeTreeOnOpen.collectAsState()
    val persist by prefs.persistSession.collectAsState()
    val keepOn by prefs.keepScreenOn.collectAsState()
    val updateCheck by prefs.appUpdateCheck.collectAsState()
    val context = LocalContext.current
    val canUpdate = remember(context) { AppUpdater.get(context).canUpdate }
    SettingsColumn(wide) {
        AppHeaderCard()
        SettingCheckCard(
            "סגור אוטומטית את עץ הספרים בעת בחירת ספר חדש",
            "כאשר אפשרות זו מופעלת, עץ הניווט בצד ימין ייסגר אוטומטית לאחר בחירת ספר חדש, מה שמאפשר יותר מקום לקריאה.",
            closeTree,
            prefs::setCloseTreeOnOpen,
        )
        SettingCheckCard(
            "שמור מצב בין הפעלות",
            "כאשר אפשרות זו מופעלת, האפליקציה תשמור את הלשוניות הפתוחות ומיקום הקריאה שלך, ותשחזר אותם בהפעלה הבאה.",
            persist,
            prefs::setPersistSession,
        )
        SettingCheckCard(
            "מנע כיבוי מסך בזמן קריאה",
            "כאשר אפשרות זו מופעלת, המסך לא ייכבה ולא ייכנס למצב שינה כל עוד ספר פתוח בלשונית הנוכחית והחלון פעיל.",
            keepOn,
            prefs::setKeepScreenOn,
        )
        if (canUpdate) SettingCheckCard(AppUpdateStrings.autoCheckTitle, AppUpdateStrings.autoCheckDescription, updateCheck, prefs::setAppUpdateCheck)
    }
}

/**
 * Desktop AppHeader for Pardes: the update banner (desktop UpdateStatusBanner: [AppUpdateBanner], from Pardes' own
 * releases), icon, name and version, a credit link to Zayit instead of desktop's "נוצר על ידי", the disclaimer, the
 * licence lines with a link to this app's own source (AGPL §6) and the AGPL §7(b) attribution.
 */
@Composable
private fun AppHeaderCard() {
    val c = Zayit.colors
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember { appVersion(context) }
    SettingsCard {
        AppUpdateBanner()
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon()
            HSpacer(16.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("פרדס", fontSize = 22.sp, color = c.text)
                Text("גרסה $version", fontSize = 13.sp, color = c.textSecondary)
                LinkText("מבוסס על זית מאת אליהו גמבש", { uri.openSafely("https://github.com/kdroidFilter/Zayit") })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("פרדס אינו מטעם אליהו גמבש: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה.", fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
            Text("הליבה של זית היא קוד פתוח תחת רישיון AGPL-3.0, וגם קוד המקור של פרדס זמין תחת רישיון זה.", fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
            LinkText("קוד המקור של פרדס", { uri.openSafely("https://github.com/efrul/Pardes") })
            Text("המודולים הקנייניים של הגרסאות הרשמיות של זית (החיפוש הסמנטי, הסידור החכם המלא וקישורי הפסוקים המצוטטים בתלמוד) אינם כלולים בפרדס.", fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
            Text("⁦Powered by the technologies that drive Zayit — https://zayitapp.com/⁩", fontSize = 12.sp, lineHeight = 17.sp, color = c.textSecondary)
        }
    }
}

/**
 * The launcher icon (פר / דס in gold on olive green) where desktop AppHeader draws its AppIcon.png. The adaptive icon
 * itself cannot be drawn here, so pardes_app_icon repeats its two layers as one vector.
 */
@Composable
internal fun AppIcon(size: Int = 64) {
    Image(
        painterResource(R.drawable.pardes_app_icon),
        contentDescription = "פרדס",
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 3 / 16).dp)),
    )
}

// ---------------------------------------------------------------- תצוגה

/**
 * Desktop DisplaySettingsScreen: accent colour, home wallpaper, compact side rails, commentators per page. The 3D
 * anti-aliasing card is left out as desktop leaves it out on low-end machines: Android has no 3D views. Theme, text
 * size and nikud are not settings on desktop either (title bar and book toolbar), and stay there on Android too.
 */
@Composable
internal fun DisplaySettings(wide: Boolean) {
    val prefs = appPrefs()
    val accent by prefs.accent.collectAsState()
    val wallpaper by prefs.showWallpaper.collectAsState()
    val compact by prefs.compactRails.collectAsState()
    val maxCommentators by prefs.maxCommentatorsPerPage.collectAsState()
    SettingsColumn(wide) {
        AccentCard(accent, prefs::setAccent)
        SettingCheckCard(
            "הצג תמונת רקע בדף הבית",
            "כאשר אפשרות זו מופעלת, יוצג נוף של כרם זיתים כרקע דקורטיבי בדף הבית.",
            wallpaper,
            prefs::setShowWallpaper,
        )
        // The side rails exist only in the wide layout; a phone has its toolbar instead.
        if (wide) {
            SettingCheckCard(
                "מצב קומפקטי לסרגל הצדדי",
                "כאשר אפשרות זו מופעלת, הסרגלים הצדדיים יוצגו בצורה צפופה יותר: האייקונים יהיו קטנים יותר והטקסט שמתחת לאייקונים לא יוצג.",
                compact,
                prefs::setCompactRails,
            )
        }
        SettingsCard {
            LabeledControl("מספר מרבי של מפרשים בעמוד", controlWidth = 140.dp) { m ->
                ZDropdown(
                    listOf("אוטומטי") + (1..AppPrefs.MAX_COMMENTATORS_PER_PAGE).map { it.toString() },
                    maxCommentators,
                    prefs::setMaxCommentatorsPerPage,
                    m,
                )
            }
            Text(
                "קובע כמה מפרשים יוצגו לכל היותר בעמוד אחד. כאשר נבחר \"אוטומטי\", המספר מותאם לגודל המסך. הגבלה למספר מסוים תיצור עמודים נוספים כשיש יותר מפרשים, אך כאשר יש מקום לפחות מכך יוצגו רק מה שנכנס.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Zayit.colors.textSecondary,
            )
        }
    }
}

/** Desktop AccentColorCard: round swatches (system rainbow first), ring and caption on the selected one. */
@Composable
private fun AccentCard(selected: AccentColor, onChange: (AccentColor) -> Unit) {
    val c = Zayit.colors
    // Desktop AccentColor.entries order: System, Default (blue), Teal, Green, Gold.
    val order = listOf(AccentColor.System, AccentColor.Blue, AccentColor.Teal, AccentColor.Green, AccentColor.Gold)
    SettingsCard {
        Text("צבע מבטא", fontSize = 15.sp, color = c.text)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            order.forEach { a ->
                val isSel = a == selected
                val fill = if (a == AccentColor.System) {
                    Modifier.background(
                        Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77), Color(0xFF4D96FF), Color(0xFFA66CFF))),
                        CircleShape,
                    )
                } else {
                    Modifier.background(if (c.isDark) a.dark else a.light, CircleShape)
                }
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // A 48dp touch target around desktop's 28dp swatch (drawn a little larger for a finger).
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { onChange(a) }
                            .semantics {
                                contentDescription = a.label
                                this.selected = isSel
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .then(fill)
                                .border(if (isSel) 2.5.dp else 1.dp, if (isSel) c.text else Color.Black.copy(alpha = 0.2f), CircleShape),
                        )
                    }
                    Text(if (isSel) a.label else "", fontSize = 11.sp, color = c.textSecondary, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun PreviewBox(text: String, font: FontFamily, size: Float) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel.copy(alpha = 0.5f))
            .border(1.dp, c.panelBorder.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = font, fontSize = size.sp, lineHeight = (size * 1.6f).sp, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- פרופיל

@Composable
internal fun ProfileSettings(wide: Boolean) {
    val prefs = appPrefs()
    var first by rememberSaveable { mutableStateOf(prefs.firstName) }
    var last by rememberSaveable { mutableStateOf(prefs.lastName) }
    var community by remember { mutableStateOf(Community.fromStored(prefs.community)) }
    val country by prefs.countryFlow.collectAsState()
    val city by prefs.cityFlow.collectAsState()
    // Desktop's worldPlaces lists (RegionConfigUseCase), shared with onboarding
    val countries = remember { worldPlaces.keys.toList() }
    val cities = remember(country) { worldPlaces[country]?.keys?.toList().orEmpty() }
    SettingsColumn(wide) {
        SettingsCard(title = "פרופיל") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 420.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        NameField("שם פרטי", first, { first = it; prefs.firstName = it.trim() }, Modifier.weight(1f))
                        NameField("שם משפחה", last, { last = it; prefs.lastName = it.trim() }, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NameField("שם פרטי", first, { first = it; prefs.firstName = it.trim() }, Modifier.fillMaxWidth())
                        NameField("שם משפחה", last, { last = it; prefs.lastName = it.trim() }, Modifier.fillMaxWidth())
                    }
                }
            }
            LabeledControl("עדה") { m ->
                ZDropdown(
                    Community.entries.map { it.label },
                    community?.ordinal ?: -1,
                    { i ->
                        val picked = Community.entries[i]
                        community = picked
                        prefs.community = picked.name
                    },
                    m,
                )
            }
        }
        SettingsCard(title = "אזור") {
            LabeledControl("בחרו מדינה") { m ->
                // Desktop saves the country at once and clears the city choice (RegionConfigViewModel.SelectCountry).
                ZDropdown(
                    countries,
                    countries.indexOf(country),
                    { i -> if (countries[i] != country) prefs.setRegion(countries[i], "") },
                    m,
                    searchTitle = "בחרו מדינה",
                )
            }
            LabeledControl("בחרו עיר") { m ->
                ZDropdown(cities, cities.indexOf(city), { i -> prefs.city = cities[i] }, m, enabled = country in countries, searchTitle = "בחרו עיר")
            }
        }
    }
}

@Composable
private fun NameField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 15.sp, color = Zayit.colors.text)
        ZTextField(value, onChange, Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- גופנים

/** Desktop FontsSettingsScreen: the four reader fonts, each previewed in a verse, and "איפוס לברירת מחדל". */
@Composable
internal fun FontsSettings(wide: Boolean) {
    val prefs = appPrefs()
    val book by prefs.bookFont.collectAsState()
    val commentary by prefs.commentaryFont.collectAsState()
    val targum by prefs.targumFont.collectAsState()
    val source by prefs.sourceFont.collectAsState()
    SettingsColumn(wide) {
        FontCard("גופן לטקסט הספר", book, prefs::setBookFont)
        FontCard("גופן למפרשים", commentary, prefs::setCommentaryFont)
        FontCard("גופן לתרגומים", targum, prefs::setTargumFont)
        FontCard("גופן למקורות", source, prefs::setSourceFont)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            OutlineButton("איפוס לברירת מחדל", prefs::resetFonts)
        }
    }
}

@Composable
private fun FontCard(label: String, font: ZayitFont, onChange: (ZayitFont) -> Unit) {
    val fonts = ZayitFont.entries
    SettingsCard {
        LabeledControl(label, controlWidth = 190.dp) { m ->
            ZDropdown(fonts.map { it.label }, fonts.indexOf(font), { onChange(fonts[it]) }, m, optionFonts = fonts.map { it.family })
        }
        PreviewBox(PREVIEW_TEXT, font.family, 20f)
    }
}

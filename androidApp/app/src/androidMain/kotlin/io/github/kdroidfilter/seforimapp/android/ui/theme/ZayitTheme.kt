package io.github.kdroidfilter.seforimapp.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.ZayitApplication

/** Accent colours offered by the desktop app (core/presentation/theme/AccentColor.kt). */
enum class AccentColor(val light: Color, val dark: Color, val label: String) {
    Gold(Color(0xFFBE9117), Color(0xFFD4A843), "זהב"),
    Green(Color(0xFF3D9A50), Color(0xFF5AB869), "ירוק"),
    Teal(Color(0xFF1A998E), Color(0xFF2FC2B6), "טורקיז"),
    Blue(Color(0xFF4682FA), Color(0xFF3574F0), "כחול"),

    /** "מערכת": follows the Android system accent (Material You); the colours are the fallback. */
    System(Color(0xFF4682FA), Color(0xFF3574F0), "מערכת"),
}

enum class ThemeMode { System, Light, Dark }

/** Colours of the desktop "Islands" style. */
@Immutable
data class ZayitColors(
    val isDark: Boolean,
    val ground: Color,
    val panel: Color,
    val panelBorder: Color,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val selectedRow: Color,
    val divider: Color,
    val titleBar: Brush,
    /** The [titleBar]'s dominant colour: the status bar's icons are drawn for it (see SystemBarsOver). */
    val titleBarTone: Color,
    val searchHit: Color,
    val scrim: Color,
    val link: Color,
    val info: Color,
    val infoTint: Color,
    val success: Color,
    val successTint: Color,
    val error: Color,
)

/**
 * [density] places the title bar's gradient in dp, as desktop does; the launcher widgets, which never draw it, leave
 * the default.
 */
fun zayitColors(dark: Boolean, accent: AccentColor, accentOverride: Color? = null, density: Float = 1f): ZayitColors {
    val a = accentOverride ?: if (dark) accent.dark else accent.light
    // Desktop's title bar: the Jewel panel background, tinted with a quarter of the accent where it peaks
    val titleBarBackground = if (dark) Color(0xFF1E1F22) else Color(0xFFF7F8FA)
    val titleBarTone = titleBarGradientColor(titleBarBackground, a)
    val titleBar = TitleBarGradient(titleBarBackground, titleBarTone, density)
    return if (dark) {
        ZayitColors(
            isDark = true,
            ground = Color(0xFF1E1F22),
            panel = Color(0xFF2B2D30),
            panelBorder = Color(0xFF393B40),
            text = Color(0xFFDFE1E5),
            textSecondary = Color(0xFF9DA0A8),
            textDisabled = Color(0xFF6F737A),
            accent = a,
            accentTint = a.copy(alpha = 0.20f),
            onAccent = Color(0xFF1E1F22),
            selectedRow = Color(0xFF393B40),
            divider = Color(0xFF393B40),
            titleBar = titleBar,
            titleBarTone = titleBarTone,
            searchHit = Color(0xFF5C4A12),
            scrim = Color(0x99000000),
            link = a,
            info = Color(0xFF548AF7),
            infoTint = Color(0xFF25324D),
            success = Color(0xFF5FB865),
            successTint = Color(0xFF253627),
            error = Color(0xFFF75464),
        )
    } else {
        ZayitColors(
            isDark = false,
            ground = Color(0xFFF2F3F5),
            panel = Color(0xFFFFFFFF),
            panelBorder = Color(0xFFEBECF0),
            text = Color(0xFF1F1F1F),
            textSecondary = Color(0xFF6C707E),
            textDisabled = Color(0xFFA8ADBD),
            accent = a,
            accentTint = a.copy(alpha = 0.14f),
            onAccent = Color.White,
            selectedRow = Color(0xFFEDEFF3),
            divider = Color(0xFFEBECF0),
            titleBar = titleBar,
            titleBarTone = titleBarTone,
            searchHit = Color(0xFFFBEBB5),
            scrim = Color(0x66000000),
            link = a,
            info = Color(0xFF3574F0),
            infoTint = Color(0xFFF5F8FE),
            success = Color(0xFF208A3C),
            successTint = Color(0xFFF2FCF3),
            error = Color(0xFFDB3B4B),
        )
    }
}

/**
 * Desktop ThemeUtils.titleBarGradientColor(): [background] blended with 25% of [accent], channel by channel in sRGB
 * (Color's lerp would blend in Oklab and miss desktop's colour), fully opaque.
 */
private fun titleBarGradientColor(background: Color, accent: Color): Color {
    val t = 0.25f
    return Color(
        red = background.red * (1f - t) + accent.red * t,
        green = background.green * (1f - t) + accent.green * t,
        blue = background.blue * (1f - t) + accent.blue * t,
        alpha = 1f,
    )
}

// IntUi TitleBarMetrics gradientStartX / gradientEndX, which desktop's title bar keeps.
private const val TITLE_BAR_GRADIENT_START_DP = -100f
private const val TITLE_BAR_GRADIENT_END_DP = 400f

/**
 * Desktop's title bar background (JewelTitleBar with MainTitleBar's gradientStartColor): [background], rising to [tone]
 * 150dp from the left edge and back to [background] at 400dp. Desktop measures from the left whatever the layout
 * direction, which in its RTL window is where the title bar's buttons are; on a phone that is the overflow menu.
 */
@Immutable
private class TitleBarGradient(private val background: Color, private val tone: Color, private val density: Float) : ShaderBrush() {
    override fun createShader(size: Size): Shader = LinearGradientShader(
        from = Offset(TITLE_BAR_GRADIENT_START_DP * density, 0f),
        to = Offset(TITLE_BAR_GRADIENT_END_DP * density, 0f),
        colors = listOf(background, tone, background),
        tileMode = TileMode.Clamp,
    )

    // ZayitColors is provided through a static local: an equal brush must not recompose the whole app
    override fun equals(other: Any?): Boolean =
        other is TitleBarGradient && other.background == background && other.tone == tone && other.density == density

    override fun hashCode(): Int = (background.hashCode() * 31 + tone.hashCode()) * 31 + density.hashCode()
}

/**
 * Font families used by the desktop defaults (AppSettings.DEFAULT_*_FONT), built as desktop's FontCatalog.familyFor
 * builds them.
 */
object ZayitFonts {
    val book = FontFamily(Font(R.font.notoserifhebrew))
    val commentary = FontFamily(Font(R.font.frankruhllibre))
    val rashi = FontFamily(Font(R.font.notorashihebrew))
    val targum = FontFamily(
        Font(R.font.taameyashkenaz_medium, FontWeight.Medium),
        Font(R.font.taameyashkenaz_mediumoblique, FontWeight.Medium, FontStyle.Italic),
        Font(R.font.taameyashkenaz_bold, FontWeight.Bold),
        Font(R.font.taameyashkenaz_boldoblique, FontWeight.Bold, FontStyle.Italic),
    )
    val source = FontFamily(
        Font(R.font.tinos_regular, FontWeight.Normal),
        Font(R.font.tinos_bold, FontWeight.Bold),
        Font(R.font.tinos_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.tinos_bolditalic, FontWeight.Bold, FontStyle.Italic),
    )

    /** The desktop UI renders Hebrew labels with a serif fallback; keep the same feel. */
    val ui = book
}

/**
 * The fonts offered in Settings → גופנים: desktop 0.6.1's FontCatalog.options, same codes (stored in the settings and
 * in backups), same order and Hebrew labels (strings.xml font_*), same faces (FontCatalog.familyFor).
 *
 * Every family is bundled in res/font (SIL OFL, Apache 2.0, or GPL 2 with the Culmus font exception). Desktop's Rashi
 * Amiti is left out: CONDITIONS.md limits it to use inside the Zayit app, and this app is Pardes, not Zayit. A setting
 * or a backup that names it ("rashiamiti") falls back to the default ([fromCode]).
 */
enum class ZayitFont(val code: String, val label: String) {
    NotoSerifHebrew("notoserifhebrew", "נוטו סריף עברית"),
    NotoRashiHebrew("notorashihebrew", "נוטו רש\"י עברית"),
    FrankRuhlLibre("frankruhllibre", "פרנק רוהל ליברה"),
    MiriamLibre("miriamlibre", "מרים ליברה"),
    Cardo("cardo", "קארדו"),
    Tinos("tinos", "טינוס"),
    LibertinusSerif("libertinusserif", "ליברטינוס סריף"),
    BonaNovaSC("bonanovasc", "בונה נובה"),
    KeterAramTsova("keteraramtsova", "כתר ארם צובה"),
    KeterYG("keteryg", "כתר יג"),
    Shofar("shofar", "שופר"),
    TaameyAshkenaz("taameyashkenaz", "טעמי אשכנז"),
    TaameyDavidClm("taameydavidclm", "טעמי דוד"),
    TaameyFrankClm("taameyfrankclm", "טעמי פרנק"),

    // Culmus fonts
    AharoniClm("aharoniclm", "אהרוני"),
    DavidClm("davidclm", "דוד"),
    DrugulinClm("drugulinclm", "דרוגולין"),
    ElliniaClm("elliniaclm", "אליניה"),
    FrankRuehlClm("frankruehlclm", "פרנק רוהל"),
    HadasimClm("hadasimclm", "הדסים"),
    MiriamClm("miriamclm", "מרים"),
    MiriamMonoClm("miriammonoclm", "מרים מונו"),
    NachlieliClm("nachlieliclm", "נחלאלי"),
    SimpleClm("simpleclm", "סימפל"),
    StamAshkenazClm("stamashkenazclm", "סת\"ם אשכנז"),
    StamSefaradClm("stamsefaradclm", "סת\"ם ספרד"),
    YehudaClm("yehudaclm", "יהודה"),
    ;

    /** Built on first use: a family only loads its files when text is drawn with it. */
    val family: FontFamily by lazy { familyFor(this) }

    companion object {
        // Desktop defaults (AppSettings.DEFAULT_*_FONT).
        val DefaultBook = NotoSerifHebrew
        val DefaultCommentary = FrankRuhlLibre
        val DefaultTargum = TaameyAshkenaz
        val DefaultSource = Tinos

        fun fromCode(code: String?, default: ZayitFont): ZayitFont = entries.firstOrNull { it.code == code } ?: default
    }
}

private val Italic = FontStyle.Italic

/** Desktop FontCatalog.familyFor, face by face. */
private fun familyFor(font: ZayitFont): FontFamily = when (font) {
    ZayitFont.NotoSerifHebrew -> ZayitFonts.book
    ZayitFont.NotoRashiHebrew -> ZayitFonts.rashi
    ZayitFont.FrankRuhlLibre -> ZayitFonts.commentary
    // One variable file: each weight sets its wght axis (Font's default variation settings).
    ZayitFont.MiriamLibre -> FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { Font(R.font.miriamlibre_variablefont_wght, it) },
    )
    ZayitFont.Cardo -> FontFamily(
        Font(R.font.cardo_regular, FontWeight.Normal),
        Font(R.font.cardo_bold, FontWeight.Bold),
        Font(R.font.cardo_italic, FontWeight.Normal, Italic),
    )
    ZayitFont.Tinos -> ZayitFonts.source
    ZayitFont.LibertinusSerif -> FontFamily(
        Font(R.font.libertinusserif_regular, FontWeight.Normal),
        Font(R.font.libertinusserif_italic, FontWeight.Normal, Italic),
        Font(R.font.libertinusserif_semibold, FontWeight.SemiBold),
        Font(R.font.libertinusserif_semibolditalic, FontWeight.SemiBold, Italic),
        Font(R.font.libertinusserif_bold, FontWeight.Bold),
        Font(R.font.libertinusserif_bolditalic, FontWeight.Bold, Italic),
    )
    ZayitFont.BonaNovaSC -> FontFamily(
        Font(R.font.bonanovasc_regular, FontWeight.Normal),
        Font(R.font.bonanovasc_bold, FontWeight.Bold),
        Font(R.font.bonanovasc_italic, FontWeight.Normal, Italic),
    )
    ZayitFont.KeterAramTsova -> FontFamily(Font(R.font.keteraramtsova, FontWeight.Normal))
    ZayitFont.KeterYG -> fourFaces(R.font.keteryg_medium, R.font.keteryg_mediumoblique, R.font.keteryg_bold, R.font.keteryg_boldoblique)
    ZayitFont.Shofar -> FontFamily(
        Font(R.font.shofarregular, FontWeight.Normal),
        Font(R.font.shofarregularoblique, FontWeight.Normal, Italic),
        Font(R.font.shofardemi_bold, FontWeight.SemiBold),
        Font(R.font.shofardemi_boldoblique, FontWeight.SemiBold, Italic),
    )
    ZayitFont.TaameyAshkenaz -> ZayitFonts.targum
    ZayitFont.TaameyDavidClm ->
        fourFaces(R.font.taameydavidclm_medium, R.font.taameydavidclm_mediumoblique, R.font.taameydavidclm_bold, R.font.taameydavidclm_boldoblique)
    ZayitFont.TaameyFrankClm ->
        fourFaces(R.font.taameyfrankclm_medium, R.font.taameyfrankclm_mediumoblique, R.font.taameyfrankclm_bold, R.font.taameyfrankclm_boldoblique)
    ZayitFont.AharoniClm ->
        fourFaces(R.font.aharoniclm_book, R.font.aharoniclm_bookoblique, R.font.aharoniclm_bold, R.font.aharoniclm_boldoblique, FontWeight.Normal)
    ZayitFont.DavidClm -> fourFaces(R.font.davidclm_medium, R.font.davidclm_mediumitalic, R.font.davidclm_bold, R.font.davidclm_bolditalic)
    ZayitFont.DrugulinClm -> FontFamily(Font(R.font.drugulinclm_bold, FontWeight.Bold))
    ZayitFont.ElliniaClm ->
        fourFaces(R.font.elliniaclm_light, R.font.elliniaclm_lightitalic, R.font.elliniaclm_bold, R.font.elliniaclm_bolditalic, FontWeight.Light)
    ZayitFont.FrankRuehlClm ->
        fourFaces(R.font.frankruehlclm_medium, R.font.frankruehlclm_mediumoblique, R.font.frankruehlclm_bold, R.font.frankruehlclm_boldoblique)
    ZayitFont.HadasimClm ->
        fourFaces(R.font.hadasimclm_regular, R.font.hadasimclm_regularoblique, R.font.hadasimclm_bold, R.font.hadasimclm_boldoblique, FontWeight.Normal)
    ZayitFont.MiriamClm -> FontFamily(Font(R.font.miriamclm_book, FontWeight.Normal), Font(R.font.miriamclm_bold, FontWeight.Bold))
    ZayitFont.MiriamMonoClm ->
        fourFaces(R.font.miriammonoclm_book, R.font.miriammonoclm_bookoblique, R.font.miriammonoclm_bold, R.font.miriammonoclm_boldoblique, FontWeight.Normal)
    ZayitFont.NachlieliClm ->
        fourFaces(R.font.nachlieliclm_light, R.font.nachlieliclm_lightoblique, R.font.nachlieliclm_bold, R.font.nachlieliclm_boldoblique, FontWeight.Light)
    ZayitFont.SimpleClm -> fourFaces(R.font.simpleclm_medium, R.font.simpleclm_mediumoblique, R.font.simpleclm_bold, R.font.simpleclm_boldoblique)
    ZayitFont.StamAshkenazClm -> FontFamily(Font(R.font.stamashkenazclm, FontWeight.Normal))
    ZayitFont.StamSefaradClm -> FontFamily(Font(R.font.stamsefaradclm, FontWeight.Normal))
    ZayitFont.YehudaClm -> FontFamily(Font(R.font.yehudaclm_light, FontWeight.Light), Font(R.font.yehudaclm_bold, FontWeight.Bold))
}

/** The usual Culmus set: a regular face of [weight] and a bold one, each with its oblique. */
private fun fourFaces(regular: Int, regularItalic: Int, bold: Int, boldItalic: Int, weight: FontWeight = FontWeight.Medium) = FontFamily(
    Font(regular, weight),
    Font(regularItalic, weight, Italic),
    Font(bold, FontWeight.Bold),
    Font(boldItalic, FontWeight.Bold, Italic),
)

/** The fonts chosen in Settings for the reader panes; read them with `Zayit.fonts`. */
@Immutable
data class ReaderFonts(
    val book: FontFamily = ZayitFonts.book,
    val commentary: FontFamily = ZayitFonts.commentary,
    val targum: FontFamily = ZayitFonts.targum,
    val source: FontFamily = ZayitFonts.source,
)

val LocalZayitColors = staticCompositionLocalOf { zayitColors(false, AccentColor.Gold) }

val LocalReaderFonts = staticCompositionLocalOf { ReaderFonts() }

object Zayit {
    val colors: ZayitColors
        @Composable get() = LocalZayitColors.current

    val fonts: ReaderFonts
        @Composable get() = LocalReaderFonts.current
}

private fun materialScheme(c: ZayitColors): ColorScheme =
    if (c.isDark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.accent,
            background = c.ground,
            onBackground = c.text,
            surface = c.panel,
            onSurface = c.text,
            surfaceVariant = c.selectedRow,
            onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.panel,
            surfaceContainerLow = c.panel,
            surfaceContainerHigh = c.panel,
            surfaceContainerHighest = c.selectedRow,
            outline = c.panelBorder,
            outlineVariant = c.divider,
            error = c.error,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.accent,
            background = c.ground,
            onBackground = c.text,
            surface = c.panel,
            onSurface = c.text,
            surfaceVariant = c.selectedRow,
            onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.panel,
            surfaceContainerLow = c.panel,
            surfaceContainerHigh = c.panel,
            surfaceContainerHighest = c.selectedRow,
            outline = c.panelBorder,
            outlineVariant = c.divider,
            error = c.error,
        )
    }

private fun typography(): Typography {
    val base = TextStyle(fontFamily = ZayitFonts.ui)
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.merge(base),
        displayMedium = t.displayMedium.merge(base),
        displaySmall = t.displaySmall.merge(base),
        headlineLarge = t.headlineLarge.merge(base),
        headlineMedium = t.headlineMedium.merge(base),
        headlineSmall = t.headlineSmall.merge(base),
        titleLarge = t.titleLarge.merge(base).copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.merge(base).copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.merge(base).copy(fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.merge(base),
        bodyMedium = t.bodyMedium.merge(base).copy(fontSize = 15.sp),
        bodySmall = t.bodySmall.merge(base),
        labelLarge = t.labelLarge.merge(base).copy(fontSize = 15.sp),
        labelMedium = t.labelMedium.merge(base),
        labelSmall = t.labelSmall.merge(base),
    )
}

/** Reads the font choices from the app preferences (defaults outside the app, e.g. in previews). */
@Composable
private fun rememberPrefsReaderFonts(): ReaderFonts {
    val prefs = (LocalContext.current.applicationContext as? ZayitApplication)?.prefs ?: return remember { ReaderFonts() }
    val book by prefs.bookFont.collectAsState()
    val commentary by prefs.commentaryFont.collectAsState()
    val targum by prefs.targumFont.collectAsState()
    val source by prefs.sourceFont.collectAsState()
    return remember(book, commentary, targum, source) { ReaderFonts(book.family, commentary.family, targum.family, source.family) }
}

@Composable
fun ZayitTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: AccentColor = AccentColor.Gold,
    fonts: ReaderFonts? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val systemAccent = if (accent == AccentColor.System) {
        remember(context, dark) {
            runCatching { (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary }.getOrNull()
        }
    } else {
        null
    }
    val colors = zayitColors(dark, accent, systemAccent, LocalDensity.current.density)
    val readerFonts = fonts ?: rememberPrefsReaderFonts()
    CompositionLocalProvider(
        LocalZayitColors provides colors,
        LocalReaderFonts provides readerFonts,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = materialScheme(colors), typography = typography(), content = content)
    }
}

package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import android.content.SharedPreferences
import io.github.kdroidfilter.seforimapp.android.appwidget.ZayitWidgets
import io.github.kdroidfilter.seforimapp.android.ui.theme.AccentColor
import io.github.kdroidfilter.seforimapp.android.ui.theme.ThemeMode
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Communities of the desktop profile ("עדה"), stored by code like AppSettings.setUserCommunityCode. */
enum class Community(val label: String) {
    SEPHARADE("עדות המזרח"),
    ASHKENAZE("אשכנזי"),
    SEFARD("ספרד"),
    ;

    companion object {
        /** Accepts the stored code or, for values written by older builds, the Hebrew label. */
        fun fromStored(value: String?): Community? = entries.firstOrNull { it.name == value || it.label == value }
    }
}

/**
 * User preferences, mirroring desktop 0.6.10's `core/settings/AppSettings.kt`: the same keys, stored values
 * (enum names, font codes, worldPlaces names) and defaults, so a desktop backup restores here and the other way
 * round ([exportPortablePreferences] / [importPortablePreferences]). Values written by older Android builds under
 * other keys are moved once ([migrate]).
 *
 * A restore staged from Settings → ניהול נתונים is applied first, before anything is read: desktop's
 * PendingUserDataRestore runs at launch before the preferences and the user database are opened, and this object is
 * the first thing [io.github.kdroidfilter.seforimapp.android.ZayitApplication] creates.
 */
class AppPrefs(context: Context) {
    init {
        PendingUserDataRestore.runOnce(context)
    }

    private val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { migrate(context, it) }

    // The launcher widgets show the place, its luach, the colours and the limudim picked on the Home: redrawn when one
    // changes, whoever wrote it (Settings, the Home's options, a restored backup, a reset). Held here: SharedPreferences
    // keeps its listeners weakly.
    private val appContext = context.applicationContext
    private val widgetsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == null || key in WIDGET_KEYS) ZayitWidgets.refreshSoon(appContext) }

    init {
        sp.registerOnSharedPreferenceChangeListener(widgetsListener)
    }

    private val _themeMode = MutableStateFlow(enumOr(sp.getString(KEY_THEME, null), ThemeMode.System))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accent = MutableStateFlow(accentOf(sp.getString(KEY_ACCENT, null)))
    val accent: StateFlow<AccentColor> = _accent.asStateFlow()

    private val _textSize = MutableStateFlow(sp.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE))
    val textSize: StateFlow<Float> = _textSize.asStateFlow()

    /** Android only: desktop keeps the nikud per category in the user database (CategoryDisplaySettingsStore). */
    private val _showNikud = MutableStateFlow(sp.getBoolean(KEY_NIKUD, true))
    val showNikud: StateFlow<Boolean> = _showNikud.asStateFlow()

    private val _showWallpaper = MutableStateFlow(sp.getBoolean(KEY_WALLPAPER, true))
    val showWallpaper: StateFlow<Boolean> = _showWallpaper.asStateFlow()

    private val _closeTreeOnOpen = MutableStateFlow(sp.getBoolean(KEY_CLOSE_TREE, false))
    val closeTreeOnOpen: StateFlow<Boolean> = _closeTreeOnOpen.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(sp.getBoolean(KEY_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    /** "שמור מצב בין הפעלות": restore open tabs and reading positions on the next launch. */
    private val _persistSession = MutableStateFlow(sp.getBoolean(KEY_PERSIST_SESSION, true))
    val persistSession: StateFlow<Boolean> = _persistSession.asStateFlow()

    /** "מצב קומפקטי לסרגל הצדדי": smaller rail icons without captions (tablet rails). */
    private val _compactRails = MutableStateFlow(sp.getBoolean(KEY_COMPACT_RAILS, false))
    val compactRails: StateFlow<Boolean> = _compactRails.asStateFlow()

    /** Android only: look for a newer Pardes at every start (update/AppUpdater). Desktop always checks. */
    private val _appUpdateCheck = MutableStateFlow(sp.getBoolean(KEY_APP_UPDATE_CHECK, true))
    val appUpdateCheck: StateFlow<Boolean> = _appUpdateCheck.asStateFlow()

    private val _searchListSplit = MutableStateFlow(sp.getFloat(KEY_SEARCH_LIST_SPLIT, DEFAULT_SEARCH_LIST_SPLIT))

    /** The share of a search tab's width its results list takes beside the selected passage (desktop 0.6.12). */
    val searchListSplit: StateFlow<Float> = _searchListSplit.asStateFlow()

    /** Desktop isSemanticPackPromptEnabled: whether a start without the semantic search in use opens the settings on it. */
    private val _semanticPackPrompt = MutableStateFlow(sp.getBoolean(KEY_SEMANTIC_PACK_PROMPT, true))
    val semanticPackPrompt: StateFlow<Boolean> = _semanticPackPrompt.asStateFlow()

    /** "מספר מרבי של מפרשים בעמוד": 0 = automatic, otherwise 1..[MAX_COMMENTATORS_PER_PAGE]. */
    private val _maxCommentatorsPerPage = MutableStateFlow(sp.getInt(KEY_MAX_COMMENTATORS, 0).coerceIn(0, MAX_COMMENTATORS_PER_PAGE))
    val maxCommentatorsPerPage: StateFlow<Int> = _maxCommentatorsPerPage.asStateFlow()

    private val _bookFont = MutableStateFlow(ZayitFont.fromCode(sp.getString(KEY_FONT_BOOK, null), ZayitFont.DefaultBook))
    val bookFont: StateFlow<ZayitFont> = _bookFont.asStateFlow()

    private val _commentaryFont = MutableStateFlow(ZayitFont.fromCode(sp.getString(KEY_FONT_COMMENTARY, null), ZayitFont.DefaultCommentary))
    val commentaryFont: StateFlow<ZayitFont> = _commentaryFont.asStateFlow()

    private val _targumFont = MutableStateFlow(ZayitFont.fromCode(sp.getString(KEY_FONT_TARGUM, null), ZayitFont.DefaultTargum))
    val targumFont: StateFlow<ZayitFont> = _targumFont.asStateFlow()

    private val _sourceFont = MutableStateFlow(ZayitFont.fromCode(sp.getString(KEY_FONT_SOURCE, null), ZayitFont.DefaultSource))
    val sourceFont: StateFlow<ZayitFont> = _sourceFont.asStateFlow()

    private val _onboardingFinished = MutableStateFlow(sp.getBoolean(KEY_ONBOARDING, false))
    val onboardingFinished: StateFlow<Boolean> = _onboardingFinished.asStateFlow()

    /** The region as stored (desktop getRegionCountry / getRegionCity: null when unset). */
    val savedCountry: String? get() = sp.getString(KEY_COUNTRY, null)?.takeIf { it.isNotBlank() }
    val savedCity: String? get() = sp.getString(KEY_CITY, null)?.takeIf { it.isNotBlank() }

    // Unset (empty) as on desktop: the zmanim then use desktop's DEFAULT_PLACE, Jerusalem, with no city name.
    private val _country = MutableStateFlow(savedCountry.orEmpty())
    val countryFlow: StateFlow<String> = _country.asStateFlow()

    private val _city = MutableStateFlow(savedCity.orEmpty())

    /** Observable city (the zmanim cards should collect this to follow changes made in Settings). */
    val cityFlow: StateFlow<String> = _city.asStateFlow()

    private val _userCity = MutableStateFlow(userCity(_country.value, _city.value))

    /** Where the zmanim are computed for (desktop HomeUserLocation), following region changes. */
    val userCityFlow: StateFlow<City> = _userCity.asStateFlow()

    private val _community = MutableStateFlow(sp.getString(KEY_COMMUNITY, null).orEmpty())

    /** The stored community code ("SEPHARADE"…, blank when unset), observable. */
    val communityFlow: StateFlow<String> = _community.asStateFlow()

    private val _zmanimOpinion = MutableStateFlow(zmanimOpinionFor(_community.value))

    /** The luach the zmanim follow, from the community (desktop HomeWidgetsState.zmanimOpinion). */
    val zmanimOpinion: StateFlow<ZmanimOpinion> = _zmanimOpinion.asStateFlow()

    private val _siddurNusach = MutableStateFlow(sp.getString(KEY_SIDDUR_NUSACH, null)?.takeIf { it.isNotBlank() })

    /**
     * Desktop siddur_nusach: the nusach code picked in the siddur ("ASHKENAZ", "SEFARD", "EDOT_HAMIZRACH"), or null
     * to follow the community ([siddurNusachFor]).
     */
    val siddurNusach: StateFlow<String?> = _siddurNusach.asStateFlow()

    private val _siddurMinyan = MutableStateFlow(sp.getBoolean(KEY_SIDDUR_MINYAN, true))
    val siddurMinyan: StateFlow<Boolean> = _siddurMinyan.asStateFlow()

    private val _siddurBeitAvel = MutableStateFlow(sp.getBoolean(KEY_SIDDUR_BEIT_AVEL, false))
    val siddurBeitAvel: StateFlow<Boolean> = _siddurBeitAvel.asStateFlow()

    var firstName: String
        get() = sp.getString(KEY_FIRST_NAME, "").orEmpty()
        set(v) = sp.edit().putString(KEY_FIRST_NAME, v.takeIf { it.isNotBlank() }.orEmpty()).apply()
    var lastName: String
        get() = sp.getString(KEY_LAST_NAME, "").orEmpty()
        set(v) = sp.edit().putString(KEY_LAST_NAME, v.takeIf { it.isNotBlank() }.orEmpty()).apply()
    var community: String
        get() = _community.value
        set(v) {
            sp.edit().putString(KEY_COMMUNITY, v).apply()
            _community.value = v
            _zmanimOpinion.value = zmanimOpinionFor(v)
        }
    var country: String
        get() = _country.value
        set(v) {
            _country.value = v
            sp.edit().putString(KEY_COUNTRY, v).apply()
            _userCity.value = userCity(_country.value, _city.value)
        }
    var city: String
        get() = _city.value
        set(v) {
            _city.value = v
            sp.edit().putString(KEY_CITY, v).apply()
            _userCity.value = userCity(_country.value, _city.value)
        }

    fun setRegion(country: String, city: String) {
        this.country = country
        this.city = city
    }

    fun selectedCommentators(bookId: Long): Set<Long>? =
        sp.getString("$KEY_COMMENTATORS$bookId", null)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun setSelectedCommentators(bookId: Long, ids: Set<Long>) {
        sp.edit().putString("$KEY_COMMENTATORS$bookId", ids.joinToString(",")).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        sp.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setAccent(color: AccentColor) {
        _accent.value = color
        sp.edit().putString(KEY_ACCENT, accentCode(color)).apply()
    }

    fun setTextSize(size: Float) {
        val clamped = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        _textSize.value = clamped
        sp.edit().putFloat(KEY_TEXT_SIZE, clamped).apply()
    }

    fun setShowNikud(show: Boolean) {
        _showNikud.value = show
        sp.edit().putBoolean(KEY_NIKUD, show).apply()
    }

    fun setShowWallpaper(show: Boolean) {
        _showWallpaper.value = show
        sp.edit().putBoolean(KEY_WALLPAPER, show).apply()
    }

    fun setCloseTreeOnOpen(close: Boolean) {
        _closeTreeOnOpen.value = close
        sp.edit().putBoolean(KEY_CLOSE_TREE, close).apply()
    }

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        sp.edit().putBoolean(KEY_SCREEN_ON, on).apply()
    }

    /** Desktop setPersistSessionEnabled: turning it off also drops the saved session (desktop's chunked keys). */
    fun setPersistSession(persist: Boolean) {
        _persistSession.value = persist
        val e = sp.edit().putBoolean(KEY_PERSIST_SESSION, persist)
        if (!persist) sp.all.keys.filter { it == KEY_SAVED_SESSION || it.startsWith(KEY_SAVED_SESSION_PREFIX) }.forEach(e::remove)
        e.apply()
    }

    fun setCompactRails(compact: Boolean) {
        _compactRails.value = compact
        sp.edit().putBoolean(KEY_COMPACT_RAILS, compact).apply()
    }

    fun setSearchListSplit(ratio: Float) {
        _searchListSplit.value = ratio
        sp.edit().putFloat(KEY_SEARCH_LIST_SPLIT, ratio).apply()
    }

    fun setAppUpdateCheck(on: Boolean) {
        _appUpdateCheck.value = on
        sp.edit().putBoolean(KEY_APP_UPDATE_CHECK, on).apply()
    }

    fun setSemanticPackPrompt(enabled: Boolean) {
        _semanticPackPrompt.value = enabled
        sp.edit().putBoolean(KEY_SEMANTIC_PACK_PROMPT, enabled).apply()
    }

    fun setMaxCommentatorsPerPage(max: Int) {
        val v = max.coerceIn(0, MAX_COMMENTATORS_PER_PAGE)
        _maxCommentatorsPerPage.value = v
        sp.edit().putInt(KEY_MAX_COMMENTATORS, v).apply()
    }

    fun setBookFont(font: ZayitFont) {
        _bookFont.value = font
        sp.edit().putString(KEY_FONT_BOOK, font.code).apply()
    }

    fun setCommentaryFont(font: ZayitFont) {
        _commentaryFont.value = font
        sp.edit().putString(KEY_FONT_COMMENTARY, font.code).apply()
    }

    fun setTargumFont(font: ZayitFont) {
        _targumFont.value = font
        sp.edit().putString(KEY_FONT_TARGUM, font.code).apply()
    }

    fun setSourceFont(font: ZayitFont) {
        _sourceFont.value = font
        sp.edit().putString(KEY_FONT_SOURCE, font.code).apply()
    }

    /** "איפוס לברירת מחדל" on the fonts page (desktop FontsSettingsEvents.ResetToDefaults writes the default codes). */
    fun resetFonts() {
        setBookFont(ZayitFont.DefaultBook)
        setCommentaryFont(ZayitFont.DefaultCommentary)
        setTargumFont(ZayitFont.DefaultTargum)
        setSourceFont(ZayitFont.DefaultSource)
    }

    fun setSiddurNusach(code: String?) {
        _siddurNusach.value = code?.takeIf { it.isNotBlank() }
        sp.edit().putString(KEY_SIDDUR_NUSACH, code.orEmpty()).apply()
    }

    fun setSiddurMinyan(value: Boolean) {
        _siddurMinyan.value = value
        sp.edit().putBoolean(KEY_SIDDUR_MINYAN, value).apply()
    }

    fun setSiddurBeitAvel(value: Boolean) {
        _siddurBeitAvel.value = value
        sp.edit().putBoolean(KEY_SIDDUR_BEIT_AVEL, value).apply()
    }

    fun setOnboardingFinished(done: Boolean) {
        _onboardingFinished.value = done
        sp.edit().putBoolean(KEY_ONBOARDING, done).apply()
    }

    /** Desktop AppSettings.clearAll: every setting, the setup state included (written synchronously: the app restarts right after). */
    fun resetAll() {
        sp.edit().clear().commit()
        _themeMode.value = ThemeMode.System
        _accent.value = AccentColor.Gold
        _textSize.value = DEFAULT_TEXT_SIZE
        _showNikud.value = true
        _showWallpaper.value = true
        _closeTreeOnOpen.value = false
        _keepScreenOn.value = true
        _persistSession.value = true
        _compactRails.value = false
        _appUpdateCheck.value = true
        _searchListSplit.value = DEFAULT_SEARCH_LIST_SPLIT
        _semanticPackPrompt.value = true
        _maxCommentatorsPerPage.value = 0
        _bookFont.value = ZayitFont.DefaultBook
        _commentaryFont.value = ZayitFont.DefaultCommentary
        _targumFont.value = ZayitFont.DefaultTargum
        _sourceFont.value = ZayitFont.DefaultSource
        _country.value = ""
        _city.value = ""
        _userCity.value = userCity(null, null)
        _community.value = ""
        _zmanimOpinion.value = zmanimOpinionFor(null)
        _siddurNusach.value = null
        _siddurMinyan.value = true
        _siddurBeitAvel.value = false
        _onboardingFinished.value = false
    }

    /** Desktop AppSettings.exportPortablePreferences: the portable settings that are stored, as their string values. */
    fun exportPortablePreferences(): Map<String, String> = exportPortablePreferences(sp)

    /**
     * The Home's catalog row as last laid out, its width to its height in px, or null (Android only, not carried by a
     * backup): the first Home of a cold start at that width keeps that room until its dropdowns load, so the logo, the
     * search bar and the widgets don't jump down.
     */
    var catalogRow: Pair<Int, Int>?
        get() = sp.getInt(KEY_CATALOG_ROW_WIDTH, 0).takeIf { it > 0 }?.let { it to sp.getInt(KEY_CATALOG_ROW_HEIGHT, 0) }
        set(value) {
            if (value != catalogRow) sp.edit().putInt(KEY_CATALOG_ROW_WIDTH, value?.first ?: 0).putInt(KEY_CATALOG_ROW_HEIGHT, value?.second ?: 0).apply()
        }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    companion object {
        /** Desktop AppSettings.DEFAULT_TEXT_SIZE / MIN_TEXT_SIZE / MAX_TEXT_SIZE (the zoom step is 2). */
        const val DEFAULT_TEXT_SIZE = 16f
        const val MIN_TEXT_SIZE = 14f
        const val MAX_TEXT_SIZE = 50f

        /** Desktop MAX_COMMENTATORS_PER_PAGE_LIMIT. */
        const val MAX_COMMENTATORS_PER_PAGE = 6

        internal const val PREFS_NAME = "zayit_settings"

        // Desktop AppSettings keys (KEY_*).
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_WALLPAPER = "show_home_wallpaper"
        private const val KEY_CLOSE_TREE = "close_tree_on_new_book"
        private const val KEY_SCREEN_ON = "keep_screen_awake_on_book"
        private const val KEY_PERSIST_SESSION = "persist_session"
        private const val KEY_SAVED_SESSION = "saved_session_json"
        private const val KEY_SAVED_SESSION_PREFIX = "saved_session_part"
        private const val KEY_COMPACT_RAILS = "compact_mode"
        private const val KEY_MAX_COMMENTATORS = "max_commentators_per_page"
        private const val KEY_FONT_BOOK = "font_book"
        private const val KEY_FONT_COMMENTARY = "font_commentary"
        private const val KEY_FONT_TARGUM = "font_targum"
        private const val KEY_FONT_SOURCE = "font_source"
        private const val KEY_ONBOARDING = "onboarding_finished"
        private const val KEY_FIRST_NAME = "user_first_name"
        private const val KEY_LAST_NAME = "user_last_name"
        private const val KEY_COMMUNITY = "user_community" // the enum name, e.g. "SEPHARADE"
        private const val KEY_COUNTRY = "region_country" // worldPlaces names
        private const val KEY_CITY = "region_city"
        private const val KEY_SIDDUR_NUSACH = "siddur_nusach"
        private const val KEY_SIDDUR_MINYAN = "siddur_minyan"
        private const val KEY_SIDDUR_BEIT_AVEL = "siddur_beit_avel"
        private const val KEY_HOME_WIDGETS_LAYOUT = "home_widgets_layout"
        private const val HOME_WIDGET_SETTINGS_PREFIX = "home_widget."

        // Android only.
        private const val KEY_NIKUD = "show_nikud"
        private const val KEY_COMMENTATORS = "commentators_"
        private const val KEY_SCHEMA = "android_prefs_schema"
        private const val KEY_CATALOG_ROW_WIDTH = "home_catalog_row_width"
        private const val KEY_CATALOG_ROW_HEIGHT = "home_catalog_row_height"
        private const val KEY_APP_UPDATE_CHECK = "app_update_check_on_start"

        // Desktop's key and default; not in a backup, as on desktop (a pane size)
        private const val KEY_SEARCH_LIST_SPLIT = "search_list_split"
        const val DEFAULT_SEARCH_LIST_SPLIT = 0.34f

        // Desktop's key; not in a backup, as on desktop (it is about this device's library)
        private const val KEY_SEMANTIC_PACK_PROMPT = "semantic_pack_prompt"
        private const val SCHEMA = 1

        /** Desktop AppSettings.PORTABLE_KEYS: what a backup carries to another machine. */
        private val PORTABLE_KEYS = setOf(
            KEY_TEXT_SIZE, KEY_LINE_HEIGHT, KEY_MAX_COMMENTATORS, KEY_CLOSE_TREE, KEY_PERSIST_SESSION, KEY_SCREEN_ON,
            KEY_FONT_BOOK, KEY_FONT_COMMENTARY, KEY_FONT_TARGUM, KEY_FONT_SOURCE, KEY_COUNTRY, KEY_CITY, KEY_FIRST_NAME,
            KEY_LAST_NAME, KEY_SIDDUR_NUSACH, KEY_SIDDUR_MINYAN, KEY_SIDDUR_BEIT_AVEL, KEY_COMMUNITY, KEY_THEME, KEY_ACCENT,
            KEY_HOME_WIDGETS_LAYOUT, KEY_WALLPAPER, KEY_COMPACT_RAILS,
        )

        /**
         * What the launcher widgets show (appwidget/WidgetData): the Limud widget's choice and the Temple's count are
         * their Home settings.
         */
        private val WIDGET_KEYS = setOf(
            KEY_COUNTRY, KEY_CITY, KEY_COMMUNITY, KEY_THEME, KEY_ACCENT,
            HOME_WIDGET_SETTINGS_PREFIX + "limud.shown", HOME_WIDGET_SETTINGS_PREFIX + "temple_countdown.count",
        )

        private val FLOAT_KEYS = setOf(KEY_TEXT_SIZE)
        private val INT_KEYS = setOf(KEY_MAX_COMMENTATORS)
        private val BOOLEAN_KEYS = setOf(KEY_CLOSE_TREE, KEY_PERSIST_SESSION, KEY_SCREEN_ON, KEY_SIDDUR_MINYAN, KEY_SIDDUR_BEIT_AVEL, KEY_WALLPAPER, KEY_COMPACT_RAILS)

        private fun isPortableKey(key: String): Boolean = key in PORTABLE_KEYS || key.startsWith(HOME_WIDGET_SETTINGS_PREFIX)

        /** Keys older Android builds used, and the desktop key each value moves to. */
        private val LEGACY_KEYS = mapOf(
            "show_wallpaper" to KEY_WALLPAPER,
            "close_tree_on_open" to KEY_CLOSE_TREE,
            "keep_screen_on" to KEY_SCREEN_ON,
            "first_name" to KEY_FIRST_NAME,
            "last_name" to KEY_LAST_NAME,
            "community" to KEY_COMMUNITY,
            "country" to KEY_COUNTRY,
            "city" to KEY_CITY,
        )

        /** Android's SiddurNusach names (the siddur tool's own preferences) → desktop Nusach codes. */
        private val SIDDUR_NUSACH_CODES = mapOf("Ashkenaz" to "ASHKENAZ", "Sefard" to "SEFARD", "Edot" to "EDOT_HAMIZRACH")

        /** Desktop AccentColor names: blue is "Default". */
        private fun accentCode(color: AccentColor): String = if (color == AccentColor.Blue) "Default" else color.name

        private fun accentOf(stored: String?): AccentColor = when (stored) {
            null -> AccentColor.Gold
            "Default", "Blue" -> AccentColor.Blue
            else -> AccentColor.entries.firstOrNull { it.name == stored } ?: AccentColor.Gold
        }

        /** Desktop nusachOf: the picked nusach, else the community's (ספרד → SEFARD, עדות המזרח → EDOT_HAMIZRACH, else ASHKENAZ). */
        fun siddurNusachFor(code: String?, community: String?): String =
            code?.takeIf { it in SIDDUR_NUSACH_CODES.values } ?: when (Community.fromStored(community)) {
                Community.SEFARD -> "SEFARD"
                Community.SEPHARADE -> "EDOT_HAMIZRACH"
                else -> "ASHKENAZ"
            }

        /** Once: moves the values of older Android builds to the desktop keys and values. */
        private fun migrate(context: Context, sp: SharedPreferences) {
            if (sp.getInt(KEY_SCHEMA, 0) >= SCHEMA) return
            val all = sp.all
            val e = sp.edit()
            LEGACY_KEYS.forEach { (old, new) ->
                val value = all[old] ?: return@forEach
                if (new !in all) putTyped(e, new, value)
                e.remove(old)
            }
            if (all[KEY_ACCENT] == "Blue") e.putString(KEY_ACCENT, "Default")
            if (KEY_SIDDUR_NUSACH !in all) {
                val tools = context.getSharedPreferences("zayit_tools", Context.MODE_PRIVATE)
                SIDDUR_NUSACH_CODES[tools.getString(KEY_SIDDUR_NUSACH, null)]?.let { e.putString(KEY_SIDDUR_NUSACH, it) }
            }
            e.putInt(KEY_SCHEMA, SCHEMA).commit()
        }

        private fun putTyped(e: SharedPreferences.Editor, key: String, value: Any?) {
            when (value) {
                is Boolean -> e.putBoolean(key, value)
                is Float -> e.putFloat(key, value)
                is Int -> e.putInt(key, value)
                is Long -> e.putLong(key, value)
                is String -> e.putString(key, value)
                else -> Unit
            }
        }

        /** As desktop's Java Preferences return them: every value as a string ("16.0", "true", "3"). */
        internal fun exportPortablePreferences(sp: SharedPreferences): Map<String, String> =
            sp.all.filterKeys(::isPortableKey).mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()

        /**
         * Desktop AppSettings.importPortablePreferences: replaces the portable preferences with [preferences] and leaves
         * the others alone. Strings from desktop are stored with the type this app reads them as; the portable keys it
         * does not use (line height, Home widgets) are kept as strings so they go back to desktop in the next backup.
         */
        internal fun importPortablePreferences(sp: SharedPreferences, preferences: Map<String, String>) {
            val e = sp.edit()
            sp.all.keys.filter(::isPortableKey).forEach(e::remove)
            preferences.filterKeys(::isPortableKey).forEach { (key, value) ->
                when (key) {
                    in FLOAT_KEYS -> value.toFloatOrNull()?.let { e.putFloat(key, it) }
                    in INT_KEYS -> value.toIntOrNull()?.let { e.putInt(key, it) }
                    in BOOLEAN_KEYS -> value.toBooleanStrictOrNull()?.let { e.putBoolean(key, it) }
                    else -> e.putString(key, value)
                }
            }
            e.commit()
        }
    }
}

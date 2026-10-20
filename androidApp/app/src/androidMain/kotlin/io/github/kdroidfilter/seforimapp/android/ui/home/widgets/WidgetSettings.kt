package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.content.Context
import android.content.SharedPreferences
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/*
 * Desktop 0.6.1 core/settings/Setting.kt (Setting, SettingCodec, SettingsGroup), features/home/widgets/WidgetSettings.kt
 * and the Home part of AppSettings (home_widgets_layout, home_widget.<id>.<name>), on the app's SharedPreferences.
 * Every value is stored as the string desktop's Preferences store, so a settings backup carries them both ways
 * (AppPrefs.importPortablePreferences keeps these keys as strings).
 */

/** How a setting's value is stored: as a string, the one type every store and the backup carry. */
class SettingCodec<T>(
    val encode: (T) -> String,
    /** Null for a value it can't read (hand edit, an entry since removed): the setting's default then. */
    val decode: (String) -> T?,
)

/** A typed setting saved under its own [key]; read, observed and written through [HomeWidgetStore]. */
class Setting<T> internal constructor(
    val key: String,
    val default: T,
    internal val codec: SettingCodec<T>,
)

/**
 * Settings saved under [prefix], declared as delegated properties named after their key:
 * `val showEarth by boolean(true)` is saved as `<prefix>.showEarth`. Reset all at once with [HomeWidgetStore.reset].
 */
abstract class SettingsGroup(
    val prefix: String,
) {
    private val declared = mutableListOf<Setting<*>>()

    /** Every setting of the group, in declaration order. */
    val all: List<Setting<*>> get() = declared

    protected fun <T> setting(
        default: T,
        codec: SettingCodec<T>,
    ) = PropertyDelegateProvider<SettingsGroup, ReadOnlyProperty<SettingsGroup, Setting<T>>> { _, property ->
        val setting = Setting("$prefix.${property.name}", default, codec).also(declared::add)
        ReadOnlyProperty { _, _ -> setting }
    }

    protected fun boolean(default: Boolean) = setting(default, SettingCodec(Boolean::toString, String::toBooleanStrictOrNull))

    protected fun float(default: Float) = setting(default, SettingCodec(Float::toString, String::toFloatOrNull))

    protected fun stringSet(default: Set<String>) = setting(default, SettingCodec({ it.joinToString(",") }, ::splitList))

    protected inline fun <reified E : Enum<E>> enumSet(default: Set<E>) =
        setting(
            default,
            SettingCodec(
                { set -> set.joinToString(",") { it.name } },
                { text -> splitList(text).mapNotNullTo(mutableSetOf()) { name -> enumValues<E>().find { it.name == name } } },
            ),
        )

    /** One of [E]'s entries; an unknown name reads as [default]. */
    protected inline fun <reified E : Enum<E>> enum(default: E) =
        setting(default, SettingCodec({ it.name }, { name -> enumValues<E>().find { it.name == name } }))

    /** Null by default; an empty or unknown name reads as null. */
    protected inline fun <reified E : Enum<E>> enumOrNull() =
        setting<E?>(null, SettingCodec({ it?.name.orEmpty() }, { name -> enumValues<E>().find { it.name == name } }))
}

@PublishedApi
internal fun splitList(text: String): Set<String> = text.split(',').filterTo(mutableSetOf()) { it.isNotEmpty() }

/**
 * A Home widget's own settings, each saved under `home_widget.<widgetId>.<name>`; given as its [HomeWidget.settings],
 * reset all at once from its options.
 */
abstract class WidgetSettings(
    widgetId: String,
) : SettingsGroup(HomeWidgetStore.HOME_WIDGET_SETTINGS_PREFIX + widgetId)

/**
 * The Home's saved layout and its widgets' settings, in the app's preferences under desktop's keys. Follows changes
 * made behind its back (a settings backup restored) through a preference listener.
 */
class HomeWidgetStore private constructor(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(read(KEY_HOME_WIDGETS_LAYOUT))

    /** "zmanim@0,0,13,4;earth@13,0,7,4", or null for the default layout. */
    val homeWidgetsLayoutFlow: StateFlow<String?> = _layout.asStateFlow()

    private class TypedSettingState<T>(val setting: Setting<T>, val flow: MutableStateFlow<T>) {
        fun reload(raw: String?) {
            flow.value = raw?.let(setting.codec.decode) ?: setting.default
        }
    }

    private val typedSettings = HashMap<String, TypedSettingState<*>>()

    // Held here: SharedPreferences keeps its listeners weakly
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == KEY_HOME_WIDGETS_LAYOUT) _layout.value = read(KEY_HOME_WIDGETS_LAYOUT)
        synchronized(typedSettings) {
            typedSettings.values.filter { key == null || it.setting.key == key }.forEach { it.reload(read(it.setting.key)) }
        }
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read(key: String): String? = sp.all[key]?.toString()

    /** Null goes back to the default layout. */
    fun setHomeWidgetsLayout(layout: String?) {
        sp.edit().apply { if (layout == null) remove(KEY_HOME_WIDGETS_LAYOUT) else putString(KEY_HOME_WIDGETS_LAYOUT, layout) }.apply()
        _layout.value = layout
    }

    operator fun <T> get(setting: Setting<T>): T = stateOf(setting).flow.value

    fun <T> flow(setting: Setting<T>): StateFlow<T> = stateOf(setting).flow.asStateFlow()

    operator fun <T> set(
        setting: Setting<T>,
        value: T,
    ) {
        sp.edit().putString(setting.key, setting.codec.encode(value)).apply()
        stateOf(setting).flow.value = value
    }

    /** Back to the defaults of every setting of [group]. */
    fun reset(group: SettingsGroup) {
        val e = sp.edit()
        group.all.forEach { e.remove(it.key) }
        e.apply()
        group.all.forEach { stateOf(it).reload(null) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stateOf(setting: Setting<T>): TypedSettingState<T> = synchronized(typedSettings) {
        typedSettings.getOrPut(setting.key) {
            val saved = read(setting.key)?.let(setting.codec.decode)
            TypedSettingState(setting, MutableStateFlow(saved ?: setting.default))
        } as TypedSettingState<T>
    }

    companion object {
        private const val KEY_HOME_WIDGETS_LAYOUT = "home_widgets_layout"
        const val HOME_WIDGET_SETTINGS_PREFIX = "home_widget."

        @Volatile
        private var instance: HomeWidgetStore? = null

        fun get(context: Context): HomeWidgetStore = instance ?: synchronized(this) {
            instance ?: HomeWidgetStore(context).also { instance = it }
        }
    }
}

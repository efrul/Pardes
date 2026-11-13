package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.earthwidget.WidgetAntiAliasing
import io.github.kdroidfilter.seforimapp.features.siddur.OpenSiddurSwitch
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences

/**
 * Manages application settings and preferences that persist across app restarts.
 * Uses Multiplatform Settings library for cross-platform storage.
 * App-scoped in the Metro graph; read it from there (constructor or LocalAppGraph).
 */
@Inject
@SingleIn(AppScope::class)
class AppSettings(
    private val settings: Settings,
) {
    // StateFlow to observe text size changes
    private val _textSizeFlow = MutableStateFlow(getTextSize())
    val textSizeFlow: StateFlow<Float> = _textSizeFlow.asStateFlow()

    // The daf's own zoom: its pages' width, over the view's at 1
    private val _dafZoomFlow = MutableStateFlow(getDafZoom())
    val dafZoomFlow: StateFlow<Float> = _dafZoomFlow.asStateFlow()

    // Whether the daf shows its marks: the lines its alignment is not sure of drawn lighter, and the lines the
    // reader's review named tinted, its moves made (the end bar's button; off, as printed)
    private val _dafMarksFlow = MutableStateFlow(isDafMarksShown())
    val dafMarksFlow: StateFlow<Boolean> = _dafMarksFlow.asStateFlow()

    // Pardes: whether pages are shown two side by side, as a book open (the end bar's button: the daf, a scan, a PDF book)
    private val _pageSpreadsFlow = MutableStateFlow(isPageSpreads())
    val pageSpreadsFlow: StateFlow<Boolean> = _pageSpreadsFlow.asStateFlow()

    // The tabs whose reading view shows a daf now, each with its views' count (a tab dragged to another window is
    // composed there before it leaves this one): the zoom keys, buttons and menu zoom their daf, not the text
    private val _dafViewsFlow = MutableStateFlow(emptyMap<String, Int>())
    val dafViewsFlow: StateFlow<Map<String, Int>> = _dafViewsFlow.asStateFlow()

    // StateFlow to observe line height changes
    private val _lineHeightFlow = MutableStateFlow(getLineHeight())
    val lineHeightFlow: StateFlow<Float> = _lineHeightFlow.asStateFlow()

    // StateFlow to observe the max-commentators-per-page setting
    private val _maxCommentatorsPerPageFlow = MutableStateFlow(getMaxCommentatorsPerPage())
    val maxCommentatorsPerPageFlow: StateFlow<Int> = _maxCommentatorsPerPageFlow.asStateFlow()

    // StateFlow for auto-close book tree setting
    private val _closeTreeOnNewBookFlow = MutableStateFlow(getCloseBookTreeOnNewBookSelected())
    val closeBookTreeOnNewBookSelectedFlow: StateFlow<Boolean> = _closeTreeOnNewBookFlow.asStateFlow()

    // StateFlow for database path (nullable)
    private val _databasePathFlow = MutableStateFlow(getDatabasePath())
    val databasePathFlow: StateFlow<String?> = _databasePathFlow.asStateFlow()

    // StateFlow for session persistence setting
    private val _persistSessionFlow = MutableStateFlow(isPersistSessionEnabled())
    val persistSessionFlow: StateFlow<Boolean> = _persistSessionFlow.asStateFlow()

    // StateFlow for keep-screen-awake-while-reading setting
    private val _keepScreenAwakeOnBookFlow = MutableStateFlow(isKeepScreenAwakeOnBookEnabled())
    val keepScreenAwakeOnBookFlow: StateFlow<Boolean> = _keepScreenAwakeOnBookFlow.asStateFlow()

    // StateFlow for the anonymous error reports setting
    private val _errorReportingFlow = MutableStateFlow(isErrorReportingEnabled())
    val errorReportingFlow: StateFlow<Boolean> = _errorReportingFlow.asStateFlow()

    // StateFlow for the automatic update check setting
    private val _autoUpdateCheckFlow = MutableStateFlow(isAutoUpdateCheckEnabled())
    val autoUpdateCheckFlow: StateFlow<Boolean> = _autoUpdateCheckFlow.asStateFlow()

    // Pardes: feedback mode, the search results' grade buttons (SearchFeedbackStore)
    private val _searchFeedbackFlow = MutableStateFlow(isSearchFeedbackEnabled())
    val searchFeedbackFlow: StateFlow<Boolean> = _searchFeedbackFlow.asStateFlow()

    private val _homeWidgetsLayoutFlow = MutableStateFlow(settings.getStringOrNull(KEY_HOME_WIDGETS_LAYOUT))
    val homeWidgetsLayoutFlow: StateFlow<String?> = _homeWidgetsLayoutFlow.asStateFlow()

    // The typed settings (see SettingsGroup), each observed from its first read
    private val typedSettings = ConcurrentHashMap<String, TypedSettingState<*>>()

    init {
        // The Home widgets' options were one opaque string per widget, now typed settings: not carried over
        settings.keys.filter { it.startsWith(LEGACY_HOME_WIDGET_OPTIONS_PREFIX) }.forEach(settings::remove)
        OpenSiddurSwitch.shown = isOpenSiddurEnabled()
    }

    // StateFlow for homepage wallpaper visibility
    private val _showHomeWallpaperFlow = MutableStateFlow(isShowHomeWallpaperEnabled())
    val showHomeWallpaperFlow: StateFlow<Boolean> = _showHomeWallpaperFlow.asStateFlow()

    // StateFlow for compact mode
    private val _bookDetailsPaneFlow = MutableStateFlow(isBookDetailsPaneVisible())

    // The book-details pane (a book's, a search result's), shown or hidden in every tab
    val bookDetailsPaneFlow: StateFlow<Boolean> = _bookDetailsPaneFlow.asStateFlow()
    private val _compactModeFlow = MutableStateFlow(isCompactModeEnabled())
    val compactModeFlow: StateFlow<Boolean> = _compactModeFlow.asStateFlow()

    // The 3D views' anti-aliasing (Earth, solar system), app-wide
    private val _antiAliasingFlow = MutableStateFlow(getAntiAliasing())
    val antiAliasingFlow: StateFlow<WidgetAntiAliasing> = _antiAliasingFlow.asStateFlow()

    // Font preference flows
    private val _bookFontCodeFlow = MutableStateFlow(getBookFontCode())
    val bookFontCodeFlow: StateFlow<String> = _bookFontCodeFlow.asStateFlow()

    private val _commentaryFontCodeFlow = MutableStateFlow(getCommentaryFontCode())
    val commentaryFontCodeFlow: StateFlow<String> = _commentaryFontCodeFlow.asStateFlow()

    private val _targumFontCodeFlow = MutableStateFlow(getTargumFontCode())
    val targumFontCodeFlow: StateFlow<String> = _targumFontCodeFlow.asStateFlow()

    private val _sourceFontCodeFlow = MutableStateFlow(getSourceFontCode())
    val sourceFontCodeFlow: StateFlow<String> = _sourceFontCodeFlow.asStateFlow()

    // The daf view's fonts: the Gemara's, and Rashi's and Tosafot's
    private val _dafGemaraFontCodeFlow = MutableStateFlow(getDafGemaraFontCode())
    val dafGemaraFontCodeFlow: StateFlow<String> = _dafGemaraFontCodeFlow.asStateFlow()

    private val _dafCommentaryFontCodeFlow = MutableStateFlow(getDafCommentaryFontCode())
    val dafCommentaryFontCodeFlow: StateFlow<String> = _dafCommentaryFontCodeFlow.asStateFlow()

    // Find-in-page state (scoped per tab, not persisted)
    private val findQueryFlowByTab = mutableMapOf<String, MutableStateFlow<String>>()
    private val findBarOpenFlowByTab = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val findSmartModeByTab = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val findFocusRequestByTab = mutableMapOf<String, MutableStateFlow<Int>>()
    private val findStepRequestsByTab = mutableMapOf<String, MutableSharedFlow<Boolean>>()

    private fun focusRequestFlowFor(tabId: String): MutableStateFlow<Int> = findFocusRequestByTab.getOrPut(tabId) { MutableStateFlow(0) }

    private fun stepRequestsFor(tabId: String): MutableSharedFlow<Boolean> =
        findStepRequestsByTab.getOrPut(tabId) {
            MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }

    private fun queryFlowFor(tabId: String): MutableStateFlow<String> = findQueryFlowByTab.getOrPut(tabId) { MutableStateFlow("") }

    private fun findOpenFlowFor(tabId: String): MutableStateFlow<Boolean> = findBarOpenFlowByTab.getOrPut(tabId) { MutableStateFlow(false) }

    private fun smartModeFlowFor(tabId: String): MutableStateFlow<Boolean> = findSmartModeByTab.getOrPut(tabId) { MutableStateFlow(false) }

    fun findQueryFlow(tabId: String): StateFlow<String> = queryFlowFor(tabId).asStateFlow()

    fun setFindQuery(
        tabId: String,
        q: String,
    ) {
        queryFlowFor(tabId).value = q
    }

    fun findBarOpenFlow(tabId: String): StateFlow<Boolean> = findOpenFlowFor(tabId).asStateFlow()

    fun openFindBar(tabId: String) {
        findOpenFlowFor(tabId).value = true
    }

    fun closeFindBar(tabId: String) {
        findOpenFlowFor(tabId).value = false
    }

    /** Bumped each time the find field must take the focus with its text selected. */
    fun findFocusRequestFlow(tabId: String): StateFlow<Int> = focusRequestFlowFor(tabId).asStateFlow()

    /** Find next (true) / previous (false) requested from outside the find field. */
    fun findStepRequests(tabId: String): SharedFlow<Boolean> = stepRequestsFor(tabId).asSharedFlow()

    /**
     * Ctrl/Cmd+F, as in Chromium: shows the find bar, or keeps it shown, and focuses its field
     * with the text selected. A new session starts from [selection] when it's short enough.
     */
    fun showFindBar(
        tabId: String,
        selection: String = "",
    ) {
        val open = findOpenFlowFor(tabId)
        // Spaces collapsed as the context menu's "find" does, so both give the same query
        val prefill = selection.replace(WHITESPACE, " ").trim()
        if (!open.value && prefill.length in 2..MAX_FIND_PREFILL_LENGTH) queryFlowFor(tabId).value = prefill
        open.value = true
        focusRequestFlowFor(tabId).value++
    }

    /** Ctrl/Cmd+G and F3, Shift going backwards, as in Chromium: shows the find bar and steps to the next match. */
    fun findNext(
        tabId: String,
        forward: Boolean,
    ) {
        showFindBar(tabId)
        stepRequestsFor(tabId).tryEmit(forward)
    }

    fun findSmartModeFlow(tabId: String): StateFlow<Boolean> = smartModeFlowFor(tabId).asStateFlow()

    fun setFindSmartMode(
        tabId: String,
        enabled: Boolean,
    ) {
        smartModeFlowFor(tabId).value = enabled
    }

    fun toggleFindSmartMode(tabId: String) {
        val flow = smartModeFlowFor(tabId)
        flow.value = !flow.value
    }

    fun getTextSize(): Float = settings[KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE]

    fun setTextSize(size: Float) {
        settings[KEY_TEXT_SIZE] = size
        _textSizeFlow.value = size
    }

    fun increaseTextSize(increment: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize + increment).coerceAtMost(MAX_TEXT_SIZE)
        setTextSize(newSize)
    }

    fun decreaseTextSize(decrement: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize - decrement).coerceAtLeast(MIN_TEXT_SIZE)
        setTextSize(newSize)
    }

    fun getDafZoom(): Float = settings[KEY_DAF_ZOOM, DEFAULT_DAF_ZOOM].coerceIn(MIN_DAF_ZOOM, MAX_DAF_ZOOM)

    fun setDafZoom(zoom: Float) {
        val value = zoom.coerceIn(MIN_DAF_ZOOM, MAX_DAF_ZOOM)
        settings[KEY_DAF_ZOOM] = value
        _dafZoomFlow.value = value
    }

    // Until the button is first pressed: on for whoever had turned on one of the two settings it replaced
    fun isDafMarksShown(): Boolean = settings[KEY_DAF_MARKS, settings[KEY_DAF_MARK_UNSURE, false] || settings[KEY_DAF_REVIEW, false]]

    fun setDafMarksShown(shown: Boolean) {
        settings[KEY_DAF_MARKS] = shown
        _dafMarksFlow.value = shown
    }

    fun isPageSpreads(): Boolean = settings[KEY_PAGE_SPREADS, false]

    fun setPageSpreads(spreads: Boolean) {
        settings[KEY_PAGE_SPREADS] = spreads
        _pageSpreadsFlow.value = spreads
    }

    /** The reading view of [tabId] shows a daf, until [dafHidden]. */
    fun dafShown(tabId: String) {
        _dafViewsFlow.update { views -> views + (tabId to (views[tabId] ?: 0) + 1) }
    }

    fun dafHidden(tabId: String) {
        _dafViewsFlow.update { views ->
            val left = (views[tabId] ?: 0) - 1
            if (left > 0) views + (tabId to left) else views - tabId
        }
    }

    fun showsDaf(tabId: String): Boolean = tabId in _dafViewsFlow.value

    // The text's size as the daf's own zoom last left it, before the size's limits: zoomed out and back, the text is as it was
    private var dafText: Pair<Float, Float>? = null

    /**
     * Zooms the daf to [zoom], as a pinch or Ctrl + wheel over it does, and the text with it by the same ratio: the
     * panes beside a daf (the commentaries, the references) are drawn in the text's size, and the same gestures over the
     * text zoom them too.
     */
    fun zoomDaf(zoom: Float) {
        val before = getDafZoom()
        setDafZoom(zoom)
        val after = getDafZoom()
        if (after == before) return
        val kept = dafText?.takeIf { (at, size) -> at == before && size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE) == getTextSize() }
        val text = (kept?.second ?: getTextSize()) * after / before
        dafText = after to text
        setTextSize(text.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
    }

    /**
     * Zooms in the reading view of [tabId]: the text, and its daf to the next of [DAF_ZOOM_LEVELS] when it shows one. The
     * panes beside a daf are drawn in the text's size (the commentaries, the references), and follow its zoom so.
     */
    fun zoomIn(tabId: String) {
        if (showsDaf(tabId)) {
            val zoom = getDafZoom()
            setDafZoom(DAF_ZOOM_LEVELS.firstOrNull { it > zoom + ZOOM_LEVEL_SLACK } ?: MAX_DAF_ZOOM)
        }
        increaseTextSize()
    }

    /** Zooms out the reading view of [tabId], as [zoomIn]. */
    fun zoomOut(tabId: String) {
        if (showsDaf(tabId)) {
            val zoom = getDafZoom()
            setDafZoom(DAF_ZOOM_LEVELS.lastOrNull { it < zoom - ZOOM_LEVEL_SLACK } ?: MIN_DAF_ZOOM)
        }
        decreaseTextSize()
    }

    // Max commentators per commentaries page (0 = automatic). Stored value is clamped to the
    // supported range so a stale or out-of-range persisted value can never break the grid.
    fun getMaxCommentatorsPerPage(): Int =
        settings[KEY_MAX_COMMENTATORS_PER_PAGE, DEFAULT_MAX_COMMENTATORS_PER_PAGE]
            .coerceIn(MAX_COMMENTATORS_PER_PAGE_AUTO, MAX_COMMENTATORS_PER_PAGE_LIMIT)

    fun setMaxCommentatorsPerPage(value: Int) {
        val clamped = value.coerceIn(MAX_COMMENTATORS_PER_PAGE_AUTO, MAX_COMMENTATORS_PER_PAGE_LIMIT)
        settings[KEY_MAX_COMMENTATORS_PER_PAGE] = clamped
        _maxCommentatorsPerPageFlow.value = clamped
    }

    fun getLineHeight(): Float = settings[KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT]

    fun setLineHeight(height: Float) {
        settings[KEY_LINE_HEIGHT] = height
        _lineHeightFlow.value = height
    }

    fun increaseLineHeight(increment: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight + increment).coerceAtMost(MAX_LINE_HEIGHT)
        setLineHeight(newHeight)
    }

    fun decreaseLineHeight(decrement: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight - decrement).coerceAtLeast(MIN_LINE_HEIGHT)
        setLineHeight(newHeight)
    }

    // Font settings (persist codes for cross-platform stability)
    fun getBookFontCode(): String = settings[KEY_FONT_BOOK, DEFAULT_BOOK_FONT]

    fun setBookFontCode(code: String) {
        settings[KEY_FONT_BOOK] = code
        _bookFontCodeFlow.value = code
    }

    fun getCommentaryFontCode(): String = settings[KEY_FONT_COMMENTARY, DEFAULT_COMMENTARY_FONT]

    fun setCommentaryFontCode(code: String) {
        settings[KEY_FONT_COMMENTARY] = code
        _commentaryFontCodeFlow.value = code
    }

    fun getTargumFontCode(): String = settings[KEY_FONT_TARGUM, DEFAULT_TARGUM_FONT]

    fun setTargumFontCode(code: String) {
        settings[KEY_FONT_TARGUM] = code
        _targumFontCodeFlow.value = code
    }

    fun getDafGemaraFontCode(): String = settings[KEY_FONT_DAF_GEMARA, DEFAULT_DAF_GEMARA_FONT]

    fun setDafGemaraFontCode(code: String) {
        settings[KEY_FONT_DAF_GEMARA] = code
        _dafGemaraFontCodeFlow.value = code
    }

    fun getDafCommentaryFontCode(): String = settings[KEY_FONT_DAF_COMMENTARY, DEFAULT_DAF_COMMENTARY_FONT]

    fun setDafCommentaryFontCode(code: String) {
        settings[KEY_FONT_DAF_COMMENTARY] = code
        _dafCommentaryFontCodeFlow.value = code
    }

    fun getSourceFontCode(): String = settings[KEY_FONT_SOURCE, DEFAULT_SOURCE_FONT]

    fun setSourceFontCode(code: String) {
        settings[KEY_FONT_SOURCE] = code
        _sourceFontCodeFlow.value = code
    }

    fun getCloseBookTreeOnNewBookSelected(): Boolean = settings[KEY_CLOSE_TREE_ON_NEW_BOOK, false]

    fun setCloseBookTreeOnNewBookSelected(value: Boolean) {
        settings[KEY_CLOSE_TREE_ON_NEW_BOOK] = value
        _closeTreeOnNewBookFlow.value = value
    }

    // Database path settings
    // Returns null if not configured or if stored as an empty string
    fun getDatabasePath(): String? {
        val value: String = settings[KEY_DATABASE_PATH, ""]
        return value.ifBlank { null }
    }

    fun setDatabasePath(path: String?) {
        if (path == null || path.isBlank()) {
            // Clear by setting empty string
            settings[KEY_DATABASE_PATH] = ""
            _databasePathFlow.value = null
        } else {
            settings[KEY_DATABASE_PATH] = path
            _databasePathFlow.value = path
        }
    }

    // The folder a full library install or update goes to (LibraryLocation); null for the app's own databases folder.
    // This machine's: not portable
    fun getLibraryInstallDir(): String? = settings[KEY_LIBRARY_INSTALL_DIR, ""].ifBlank { null }

    fun setLibraryInstallDir(path: String?) {
        settings[KEY_LIBRARY_INSTALL_DIR] = path.orEmpty()
    }

    // Whether a start whose library has no semantic pack in use opens the settings on it (SemanticPackPrompt).
    fun isSemanticPackPromptEnabled(): Boolean = settings[KEY_SEMANTIC_PACK_PROMPT, true]

    fun setSemanticPackPromptEnabled(enabled: Boolean) {
        settings[KEY_SEMANTIC_PACK_PROMPT] = enabled
    }

    // Pardes: the newest semantic pack and release of daf layouts a start opened the settings for (StartOffers), each once
    fun getOfferedSemanticPack(): Int = settings[KEY_OFFERED_SEMANTIC_PACK, 0]

    fun setOfferedSemanticPack(pack: Int) {
        settings[KEY_OFFERED_SEMANTIC_PACK] = pack
    }

    fun getOfferedDafRelease(): Int = settings[KEY_OFFERED_DAF_RELEASE, 0]

    fun setOfferedDafRelease(release: Int) {
        settings[KEY_OFFERED_DAF_RELEASE] = release
    }

    // Pardes: whether each search result shows the buttons that grade it, for training the semantic search (off at first)
    fun isSearchFeedbackEnabled(): Boolean = settings[KEY_SEARCH_FEEDBACK, false]

    fun setSearchFeedbackEnabled(enabled: Boolean) {
        settings[KEY_SEARCH_FEEDBACK] = enabled
        _searchFeedbackFlow.value = enabled
    }

    // Pardes: sending feedback (FeedbackSendCard). The sender's id, random, made at first use and renewed on request.
    fun getFeedbackSender(): String = settings.getStringOrNull(KEY_FEEDBACK_SENDER)?.takeIf { it.isNotBlank() } ?: renewFeedbackSender()

    fun renewFeedbackSender(): String {
        val sender = UUID.randomUUID().toString()
        settings[KEY_FEEDBACK_SENDER] = sender
        return sender
    }

    // Whether the user answered the question to send feedback by itself (FeedbackAutoSender), and agreed to
    fun isFeedbackAsked(): Boolean = settings[KEY_FEEDBACK_ASKED, false]

    fun setFeedbackAsked(asked: Boolean) {
        settings[KEY_FEEDBACK_ASKED] = asked
    }

    fun isFeedbackAutoSend(): Boolean = settings[KEY_FEEDBACK_AUTO_SEND, false]

    fun setFeedbackAutoSend(enabled: Boolean) {
        settings[KEY_FEEDBACK_AUTO_SEND] = enabled
    }

    // Whether a kind of feedback (FeedbackSender's kinds) is sent, [default] until the user says
    fun isFeedbackKindSent(
        kind: String,
        default: Boolean,
    ): Boolean = settings[KEY_FEEDBACK_KIND_PREFIX + kind, default]

    fun setFeedbackKindSent(
        kind: String,
        sent: Boolean,
    ) {
        settings[KEY_FEEDBACK_KIND_PREFIX + kind] = sent
    }

    // Up to when a kind of feedback (FeedbackSender's kinds) went in a package, epoch millis, 0 for never
    fun getFeedbackSentAt(kind: String): Long = settings[KEY_FEEDBACK_SENT_PREFIX + kind, 0L]

    fun setFeedbackSentAt(
        kind: String,
        at: Long,
    ) {
        settings[KEY_FEEDBACK_SENT_PREFIX + kind] = at
    }

    // Pardes: where a PDF book the user adds is kept (PdfKeep's name), asked at the first one: null until then; and the
    // folder they chose for it
    fun getPdfKeep(): String? = settings.getStringOrNull(KEY_PDF_KEEP)?.ifBlank { null }

    fun getPdfKeepFolder(): String? = settings.getStringOrNull(KEY_PDF_KEEP_FOLDER)?.ifBlank { null }

    fun setPdfKeep(
        keep: String,
        folder: String?,
    ) {
        settings[KEY_PDF_KEEP] = keep
        settings[KEY_PDF_KEEP_FOLDER] = folder.orEmpty()
    }

    /** Writes the settings to disk now (Java preferences do it within 30 s otherwise), before a step that relies on them. */
    fun flush() {
        // The app's node is under the user root, whose flush writes its descendants
        if (settings is PreferencesSettings) runCatching { Preferences.userRoot().flush() }
    }

    // Session persistence preference
    fun isPersistSessionEnabled(): Boolean = settings[KEY_PERSIST_SESSION, true]

    fun setPersistSessionEnabled(enabled: Boolean) {
        settings[KEY_PERSIST_SESSION] = enabled
        _persistSessionFlow.value = enabled
        if (!enabled) {
            // Clear any previously saved session when disabling persistence
            setSavedSessionJson(null)
        }
    }

    // Keep the screen awake while a book is open and the window is focused (enabled by default)
    fun isKeepScreenAwakeOnBookEnabled(): Boolean = settings[KEY_KEEP_SCREEN_AWAKE_ON_BOOK, true]

    fun setKeepScreenAwakeOnBookEnabled(enabled: Boolean) {
        settings[KEY_KEEP_SCREEN_AWAKE_ON_BOOK] = enabled
        _keepScreenAwakeOnBookFlow.value = enabled
    }

    // Anonymous error reports (enabled by default); apply it through CrashReporting, which starts or stops Sentry
    fun isErrorReportingEnabled(): Boolean = settings[KEY_ERROR_REPORTING, true]

    fun setErrorReportingEnabled(enabled: Boolean) {
        settings[KEY_ERROR_REPORTING] = enabled
        _errorReportingFlow.value = enabled
    }

    // Check for app updates at startup (enabled by default); the settings button still checks on demand
    fun isAutoUpdateCheckEnabled(): Boolean = settings[KEY_AUTO_UPDATE_CHECK, true]

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        settings[KEY_AUTO_UPDATE_CHECK] = enabled
        _autoUpdateCheckFlow.value = enabled
    }

    /** Null goes back to the default layout. */
    fun setHomeWidgetsLayout(layout: String?) {
        if (layout == null) settings.remove(KEY_HOME_WIDGETS_LAYOUT) else settings[KEY_HOME_WIDGETS_LAYOUT] = layout
        _homeWidgetsLayoutFlow.value = layout
    }

    operator fun <T> get(setting: Setting<T>): T = stateOf(setting).flow.value

    fun <T> flow(setting: Setting<T>): StateFlow<T> = stateOf(setting).flow.asStateFlow()

    operator fun <T> set(
        setting: Setting<T>,
        value: T,
    ) {
        settings[setting.key] = setting.codec.encode(value)
        stateOf(setting).flow.value = value
    }

    /** Back to the defaults of every setting of [group]. */
    fun reset(group: SettingsGroup) =
        group.all.forEach {
            settings.remove(it.key)
            stateOf(it).reset()
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stateOf(setting: Setting<T>): TypedSettingState<T> =
        typedSettings.getOrPut(setting.key) {
            val saved = settings.getStringOrNull(setting.key)?.let(setting.codec.decode)
            TypedSettingState(setting, MutableStateFlow(saved ?: setting.default))
        } as TypedSettingState<T>

    private class TypedSettingState<T>(
        val setting: Setting<T>,
        val flow: MutableStateFlow<T>,
    ) {
        fun reset() {
            flow.value = setting.default
        }
    }

    // Homepage wallpaper visibility
    fun isShowHomeWallpaperEnabled(): Boolean = settings[KEY_SHOW_HOME_WALLPAPER, true]

    fun setShowHomeWallpaperEnabled(enabled: Boolean) {
        settings[KEY_SHOW_HOME_WALLPAPER] = enabled
        _showHomeWallpaperFlow.value = enabled
    }

    // The share of a search tab's width its results list takes, beside the selected passage
    fun getSearchListSplit(): Float = settings[KEY_SEARCH_LIST_SPLIT, DEFAULT_SEARCH_LIST_SPLIT]

    fun setSearchListSplit(ratio: Float) {
        settings[KEY_SEARCH_LIST_SPLIT] = ratio
    }

    fun isBookDetailsPaneVisible(): Boolean = settings[KEY_BOOK_DETAILS_PANE, false]

    fun setBookDetailsPaneVisible(visible: Boolean) {
        settings[KEY_BOOK_DETAILS_PANE] = visible
        _bookDetailsPaneFlow.value = visible
    }

    // Compact mode for vertical bars
    fun isCompactModeEnabled(): Boolean = settings[KEY_COMPACT_MODE, false]

    fun setCompactModeEnabled(enabled: Boolean) {
        settings[KEY_COMPACT_MODE] = enabled
        _compactModeFlow.value = enabled
    }

    // ponytail: a mode no longer offered (hand edit, TAA carried to Linux) falls back to off
    fun getAntiAliasing(): WidgetAntiAliasing =
        AVAILABLE_ANTI_ALIASING.firstOrNull { it.name == settings.getStringOrNull(KEY_ANTI_ALIASING) } ?: WidgetAntiAliasing.OFF

    fun setAntiAliasing(mode: WidgetAntiAliasing) {
        settings[KEY_ANTI_ALIASING] = mode.name
        _antiAliasingFlow.value = getAntiAliasing()
    }

    // Saved session blob (JSON)
    fun getSavedSessionJson(): String? {
        // Prefer chunked storage if present
        val partsCount: Int = settings[KEY_SAVED_SESSION_PARTS_COUNT, 0]
        if (partsCount > 0) {
            val sb = StringBuilder(partsCount * SESSION_CHUNK_SIZE)
            for (i in 0 until partsCount) {
                val partKey = "$KEY_SAVED_SESSION_PART_PREFIX$i"
                sb.append(settings[partKey, ""])
            }
            val result = sb.toString()
            return result.ifBlank { null }
        }
        // Backward compatibility (single key)
        val legacy: String = settings[KEY_SAVED_SESSION, ""]
        return legacy.ifBlank { null }
    }

    // Region configuration accessors
    fun getRegionCountry(): String? {
        val value: String = settings[KEY_REGION_COUNTRY, ""]
        return value.ifBlank { null }
    }

    fun setRegionCountry(value: String?) {
        settings[KEY_REGION_COUNTRY] = value?.takeIf { it.isNotBlank() } ?: ""
    }

    fun getRegionCity(): String? {
        val value: String = settings[KEY_REGION_CITY, ""]
        return value.ifBlank { null }
    }

    fun setRegionCity(value: String?) {
        settings[KEY_REGION_CITY] = value?.takeIf { it.isNotBlank() } ?: ""
    }

    // Onboarding finished flag
    fun isOnboardingFinished(): Boolean = settings[KEY_ONBOARDING_FINISHED, false]

    fun setOnboardingFinished(finished: Boolean) {
        settings[KEY_ONBOARDING_FINISHED] = finished
    }

    // User profile accessors
    // Reactive flows to observe user identity changes across the app
    private val _userFirstNameFlow = MutableStateFlow(getUserFirstName() ?: "")
    val userFirstNameFlow: StateFlow<String> = _userFirstNameFlow.asStateFlow()

    private val _userLastNameFlow = MutableStateFlow(getUserLastName() ?: "")
    val userLastNameFlow: StateFlow<String> = _userLastNameFlow.asStateFlow()

    private val _userCommunityCodeFlow = MutableStateFlow(getUserCommunityCode())
    val userCommunityCodeFlow: StateFlow<String?> = _userCommunityCodeFlow.asStateFlow()

    fun getUserFirstName(): String? {
        val value: String = settings[KEY_USER_FIRST_NAME, ""]
        return value.ifBlank { null }
    }

    fun setUserFirstName(value: String?) {
        settings[KEY_USER_FIRST_NAME] = value?.takeIf { it.isNotBlank() } ?: ""
        _userFirstNameFlow.value = getUserFirstName() ?: ""
    }

    fun getUserLastName(): String? {
        val value: String = settings[KEY_USER_LAST_NAME, ""]
        return value.ifBlank { null }
    }

    fun setUserLastName(value: String?) {
        settings[KEY_USER_LAST_NAME] = value?.takeIf { it.isNotBlank() } ?: ""
        _userLastNameFlow.value = getUserLastName() ?: ""
    }

    // Community is stored as a stable code (enum name), not a localized label
    fun getUserCommunityCode(): String? {
        val value: String = settings[KEY_USER_COMMUNITY, ""]
        return value.ifBlank { null }
    }

    fun setUserCommunityCode(value: String?) {
        settings[KEY_USER_COMMUNITY] = value?.takeIf { it.isNotBlank() } ?: ""
        _userCommunityCodeFlow.value = getUserCommunityCode()
    }

    // Siddur: the nusach (null follows the community), and whether the user davens with a minyan
    private val _siddurNusachFlow = MutableStateFlow(getSiddurNusachCode())
    val siddurNusachFlow: StateFlow<String?> = _siddurNusachFlow.asStateFlow()

    fun getSiddurNusachCode(): String? = settings[KEY_SIDDUR_NUSACH, ""].ifBlank { null }

    fun setSiddurNusachCode(value: String?) {
        settings[KEY_SIDDUR_NUSACH] = value.orEmpty()
        _siddurNusachFlow.value = getSiddurNusachCode()
    }

    private val _siddurMinyanFlow = MutableStateFlow(isSiddurMinyan())
    val siddurMinyanFlow: StateFlow<Boolean> = _siddurMinyanFlow.asStateFlow()

    fun isSiddurMinyan(): Boolean = settings[KEY_SIDDUR_MINYAN, true]

    fun setSiddurMinyan(value: Boolean) {
        settings[KEY_SIDDUR_MINYAN] = value
        _siddurMinyanFlow.value = value
    }

    private val _siddurBeitAvelFlow = MutableStateFlow(isSiddurBeitAvel())
    val siddurBeitAvelFlow: StateFlow<Boolean> = _siddurBeitAvelFlow.asStateFlow()

    fun isSiddurBeitAvel(): Boolean = settings[KEY_SIDDUR_BEIT_AVEL, false]

    fun setSiddurBeitAvel(value: Boolean) {
        settings[KEY_SIDDUR_BEIT_AVEL] = value
        _siddurBeitAvelFlow.value = value
    }

    // Siddur: the custom of the State of Israel's days in Iyar (an IsraeliPrayerCustom's name), which the open siddur
    // asks on those days
    private val _siddurIsraeliPrayerCustomFlow = MutableStateFlow(getSiddurIsraeliPrayerCustom())
    val siddurIsraeliPrayerCustomFlow: StateFlow<String> = _siddurIsraeliPrayerCustomFlow.asStateFlow()

    fun getSiddurIsraeliPrayerCustom(): String = settings[KEY_SIDDUR_ISRAELI_PRAYER_CUSTOM, ""]

    fun setSiddurIsraeliPrayerCustom(value: String) {
        settings[KEY_SIDDUR_ISRAELI_PRAYER_CUSTOM] = value
        _siddurIsraeliPrayerCustomFlow.value = value
    }

    // The open smart siddur (addons/siddur/open) is experimental: shown once turned on. OpenSiddurSwitch mirrors the
    // preference for installedSiddur, which is read where there is no graph to take the settings from
    fun isOpenSiddurEnabled(): Boolean = settings[KEY_OPEN_SIDDUR, false]

    fun setOpenSiddurEnabled(enabled: Boolean) {
        settings[KEY_OPEN_SIDDUR] = enabled
        OpenSiddurSwitch.shown = enabled
    }

    // Theme mode (Light/Dark/System) setting
    fun getThemeMode(): IntUiThemes {
        val storedValue: String = settings[KEY_THEME_MODE, IntUiThemes.System.name]
        return try {
            IntUiThemes.valueOf(storedValue)
        } catch (_: IllegalArgumentException) {
            IntUiThemes.System
        }
    }

    fun setThemeMode(theme: IntUiThemes) {
        settings[KEY_THEME_MODE] = theme.name
    }

    // Accent color preset
    fun getAccentColor(): AccentColor {
        val storedValue: String = settings[KEY_ACCENT_COLOR, AccentColor.Gold.name]
        return try {
            AccentColor.valueOf(storedValue)
        } catch (_: IllegalArgumentException) {
            AccentColor.Gold
        }
    }

    fun setAccentColor(accent: AccentColor) {
        settings[KEY_ACCENT_COLOR] = accent.name
    }

    fun setSavedSessionJson(json: String?) {
        if (json.isNullOrBlank()) {
            // Clear legacy and chunked storage
            settings[KEY_SAVED_SESSION] = ""
            val oldCount: Int = settings[KEY_SAVED_SESSION_PARTS_COUNT, 0]
            if (oldCount > 0) {
                for (i in 0 until oldCount) {
                    val partKey = "$KEY_SAVED_SESSION_PART_PREFIX$i"
                    settings[partKey] = ""
                }
                settings[KEY_SAVED_SESSION_PARTS_COUNT] = 0
            }
            return
        }

        // Write chunked to avoid JVM Preferences value-length limits
        val totalLength = json.length
        val parts = (totalLength + SESSION_CHUNK_SIZE - 1) / SESSION_CHUNK_SIZE
        settings[KEY_SAVED_SESSION_PARTS_COUNT] = parts
        for (i in 0 until parts) {
            val start = i * SESSION_CHUNK_SIZE
            val end = minOf(start + SESSION_CHUNK_SIZE, totalLength)
            val partKey = "$KEY_SAVED_SESSION_PART_PREFIX$i"
            settings[partKey] = json.substring(start, end)
        }
        // Clear legacy single key to avoid oversized writes
        settings[KEY_SAVED_SESSION] = ""
    }

    // Clears all persisted settings and resets in-memory flows to defaults
    fun clearAll() {
        settings.clear()
        _textSizeFlow.value = DEFAULT_TEXT_SIZE
        _dafZoomFlow.value = DEFAULT_DAF_ZOOM
        _dafMarksFlow.value = false
        _pageSpreadsFlow.value = false
        _lineHeightFlow.value = DEFAULT_LINE_HEIGHT
        _maxCommentatorsPerPageFlow.value = DEFAULT_MAX_COMMENTATORS_PER_PAGE
        _closeTreeOnNewBookFlow.value = false
        _databasePathFlow.value = null
        _persistSessionFlow.value = true
        _homeWidgetsLayoutFlow.value = null
        typedSettings.values.forEach { it.reset() }
        _showHomeWallpaperFlow.value = true
        _compactModeFlow.value = false
        _antiAliasingFlow.value = WidgetAntiAliasing.OFF
        _bookFontCodeFlow.value = DEFAULT_BOOK_FONT
        _commentaryFontCodeFlow.value = DEFAULT_COMMENTARY_FONT
        _targumFontCodeFlow.value = DEFAULT_TARGUM_FONT
        _sourceFontCodeFlow.value = DEFAULT_SOURCE_FONT
        _dafGemaraFontCodeFlow.value = DEFAULT_DAF_GEMARA_FONT
        _dafCommentaryFontCodeFlow.value = DEFAULT_DAF_COMMENTARY_FONT
        OpenSiddurSwitch.shown = false
    }

    /** The preferences that follow the user to another machine (see [PORTABLE_KEYS]). */
    fun exportPortablePreferences(): Map<String, String> = exportPortablePreferences(settings)

    companion object {
        /**
         * The anti-aliasing modes offered. Not TAA on Linux: Filament's GL backend runs its mobile shaders there, and
         * NVIDIA's GLES compiler rejects TAA's (the engine then aborts).
         */
        val AVAILABLE_ANTI_ALIASING: List<WidgetAntiAliasing> =
            WidgetAntiAliasing.entries.filter { it != WidgetAntiAliasing.TAA || !PlatformInfo.isLinux }

        // Preferences worth carrying to another machine. The database location, the session and
        // the onboarding state belong to this machine only.
        private val PORTABLE_KEYS =
            setOf(
                KEY_TEXT_SIZE,
                KEY_DAF_ZOOM,
                KEY_DAF_MARKS,
                KEY_PAGE_SPREADS,
                KEY_DAF_MARK_UNSURE,
                KEY_DAF_REVIEW,
                KEY_LINE_HEIGHT,
                KEY_MAX_COMMENTATORS_PER_PAGE,
                KEY_CLOSE_TREE_ON_NEW_BOOK,
                KEY_PERSIST_SESSION,
                KEY_KEEP_SCREEN_AWAKE_ON_BOOK,
                KEY_ERROR_REPORTING,
                KEY_AUTO_UPDATE_CHECK,
                KEY_FONT_BOOK,
                KEY_FONT_COMMENTARY,
                KEY_FONT_TARGUM,
                KEY_FONT_SOURCE,
                KEY_FONT_DAF_GEMARA,
                KEY_FONT_DAF_COMMENTARY,
                KEY_REGION_COUNTRY,
                KEY_REGION_CITY,
                KEY_USER_FIRST_NAME,
                KEY_USER_LAST_NAME,
                KEY_SIDDUR_NUSACH,
                KEY_SIDDUR_MINYAN,
                KEY_SIDDUR_BEIT_AVEL,
                KEY_SIDDUR_ISRAELI_PRAYER_CUSTOM,
                KEY_OPEN_SIDDUR,
                KEY_USER_COMMUNITY,
                KEY_THEME_MODE,
                KEY_ACCENT_COLOR,
                KEY_HOME_WIDGETS_LAYOUT,
                KEY_SHOW_HOME_WALLPAPER,
                KEY_COMPACT_MODE,
            )

        private fun isPortableKey(key: String): Boolean = key in PORTABLE_KEYS || key.startsWith(HOME_WIDGET_SETTINGS_PREFIX)

        /**
         * The portable preferences of [settings] as their stored strings: Java Preferences keeps
         * every value as a string, so they round-trip whatever their type.
         */
        fun exportPortablePreferences(settings: Settings): Map<String, String> =
            settings.keys.filter(::isPortableKey).associateWith { settings.getString(it, "") }

        /** Replaces the portable preferences of [settings] with [preferences], leaving the others alone. */
        fun importPortablePreferences(
            settings: Settings,
            preferences: Map<String, String>,
        ) {
            settings.keys.filter(::isPortableKey).forEach(settings::remove)
            preferences.filterKeys(::isPortableKey).forEach { (key, value) -> settings.putString(key, value) }
        }

        // Text size constants
        const val DEFAULT_TEXT_SIZE = 16f
        const val MIN_TEXT_SIZE = 14f
        const val MAX_TEXT_SIZE = 50f
        const val TEXT_SIZE_INCREMENT = 2f

        // The daf's zoom: 1 is as wide as the view (at most the page's widest), the levels a browser's
        const val DEFAULT_DAF_ZOOM = 1f
        const val MIN_DAF_ZOOM = 0.3f
        const val MAX_DAF_ZOOM = 4f
        val DAF_ZOOM_LEVELS =
            listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.67f, 0.75f, 0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f)

        // A zoom a pinch left this close to a level is at that level
        private const val ZOOM_LEVEL_SLACK = 0.005f

        // Line height constants
        const val DEFAULT_LINE_HEIGHT = 1.5f
        const val MIN_LINE_HEIGHT = 1.0f
        const val MAX_LINE_HEIGHT = 2.5f
        const val LINE_HEIGHT_INCREMENT = 0.1f

        // Max commentators displayed per commentaries page.
        // 0 = automatic (fit as many as the available space allows). A positive value acts as a
        // ceiling: the grid never shows more than this per page, but still shows fewer when the
        // pane only has room for fewer.
        const val MAX_COMMENTATORS_PER_PAGE_AUTO = 0
        const val MAX_COMMENTATORS_PER_PAGE_LIMIT = 6
        const val DEFAULT_MAX_COMMENTATORS_PER_PAGE = MAX_COMMENTATORS_PER_PAGE_AUTO

        // Default font codes
        const val DEFAULT_BOOK_FONT = "notoserifhebrew"
        const val DEFAULT_COMMENTARY_FONT = "frankruhllibre"
        const val DEFAULT_TARGUM_FONT = "taameyashkenaz"
        const val DEFAULT_SOURCE_FONT = "tinos"

        // As the Vilna Shas prints them: a square letter for the Gemara, Rashi script for its commentaries
        const val DEFAULT_DAF_GEMARA_FONT = "frankruehlclm"
        const val DEFAULT_DAF_COMMENTARY_FONT = "notorashihebrew"

        // Tab display constants
        const val MAX_TAB_TITLE_LENGTH = 20

        // Preferred max width for tabs in dp units (UI caps to this, shrinks below as needed)
        const val TAB_FIXED_WIDTH_DP = 180

        // Settings keys
        // Longest selection pre-filling a new find session, as Chromium's FindBarController
        private const val MAX_FIND_PREFILL_LENGTH = 250
        private val WHITESPACE = Regex("\\s+")

        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_DAF_ZOOM = "daf_zoom"
        private const val KEY_DAF_MARKS = "daf_marks"
        private const val KEY_PAGE_SPREADS = "page_spreads"

        // The two settings the daf's marks button replaced, read for its first value
        private const val KEY_DAF_MARK_UNSURE = "daf_mark_unsure"
        private const val KEY_DAF_REVIEW = "daf_review"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_MAX_COMMENTATORS_PER_PAGE = "max_commentators_per_page"
        private const val KEY_CLOSE_TREE_ON_NEW_BOOK = "close_tree_on_new_book"
        private const val KEY_DATABASE_PATH = "database_path"
        private const val KEY_LIBRARY_INSTALL_DIR = "library_install_dir"
        private const val KEY_SEMANTIC_PACK_PROMPT = "semantic_pack_prompt"
        private const val KEY_OFFERED_SEMANTIC_PACK = "offered_semantic_pack"
        private const val KEY_OFFERED_DAF_RELEASE = "offered_daf_release"
        private const val KEY_SEARCH_FEEDBACK = "search_feedback"
        private const val KEY_FEEDBACK_SENDER = "feedback_sender"
        private const val KEY_FEEDBACK_SENT_PREFIX = "feedback_sent_"
        private const val KEY_FEEDBACK_ASKED = "feedback_asked"
        private const val KEY_FEEDBACK_AUTO_SEND = "feedback_auto_send"
        private const val KEY_FEEDBACK_KIND_PREFIX = "feedback_kind_"
        private const val KEY_PDF_KEEP = "pdf_keep"
        private const val KEY_PDF_KEEP_FOLDER = "pdf_keep_folder"
        private const val KEY_PERSIST_SESSION = "persist_session"
        private const val KEY_KEEP_SCREEN_AWAKE_ON_BOOK = "keep_screen_awake_on_book"
        private const val KEY_ERROR_REPORTING = "error_reporting"
        private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
        private const val KEY_FONT_BOOK = "font_book"
        private const val KEY_FONT_COMMENTARY = "font_commentary"
        private const val KEY_FONT_TARGUM = "font_targum"
        private const val KEY_FONT_SOURCE = "font_source"
        private const val KEY_FONT_DAF_GEMARA = "font_daf_gemara"
        private const val KEY_FONT_DAF_COMMENTARY = "font_daf_commentary"
        private const val KEY_SAVED_SESSION = "saved_session_json"
        private const val KEY_SAVED_SESSION_PARTS_COUNT = "saved_session_parts_count"
        private const val KEY_SAVED_SESSION_PART_PREFIX = "saved_session_part_"
        private const val SESSION_CHUNK_SIZE = 4000

        // Onboarding state
        private const val KEY_ONBOARDING_FINISHED = "onboarding_finished"

        // Region configuration keys
        private const val KEY_REGION_COUNTRY = "region_country"
        private const val KEY_REGION_CITY = "region_city"

        // User profile keys
        private const val KEY_USER_FIRST_NAME = "user_first_name"
        private const val KEY_USER_LAST_NAME = "user_last_name"
        private const val KEY_SIDDUR_NUSACH = "siddur_nusach"
        private const val KEY_SIDDUR_MINYAN = "siddur_minyan"
        private const val KEY_SIDDUR_BEIT_AVEL = "siddur_beit_avel"
        private const val KEY_SIDDUR_ISRAELI_PRAYER_CUSTOM = "siddur_israeli_prayer_custom"
        private const val KEY_OPEN_SIDDUR = "open_siddur"
        private const val KEY_USER_COMMUNITY = "user_community" // stores a stable code (e.g., "SEPHARADE")

        // Theme configuration
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"

        // Zmanim widgets visibility

        // Home widgets the user placed, in order ("id:SIZE,…"); unset means the default layout
        private const val KEY_HOME_WIDGETS_LAYOUT = "home_widgets_layout"

        // A Home widget's own options: this, then its id
        private const val LEGACY_HOME_WIDGET_OPTIONS_PREFIX = "home_widget_options_"

        /** The Home widgets' own settings, each under `home_widget.<widget id>.`; all portable. */
        const val HOME_WIDGET_SETTINGS_PREFIX = "home_widget."

        // Homepage wallpaper visibility
        private const val KEY_SHOW_HOME_WALLPAPER = "show_home_wallpaper"

        // Compact mode for vertical bars
        private const val KEY_COMPACT_MODE = "compact_mode"

        // Not portable: what a machine's GPU handles
        private const val KEY_ANTI_ALIASING = "anti_aliasing_3d"
        private const val KEY_BOOK_DETAILS_PANE = "book_details_pane"

        // Not portable: a pane size, as the docked panes'
        private const val KEY_SEARCH_LIST_SPLIT = "search_list_split"
        private const val DEFAULT_SEARCH_LIST_SPLIT = 0.34f
    }
}

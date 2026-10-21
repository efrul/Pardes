package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.data.CatalogMenu
import io.github.kdroidfilter.seforimapp.android.data.DatabaseVersion
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.PARDES_BASED_ON
import io.github.kdroidfilter.seforimapp.android.ui.shell.ShellTexts
import io.github.kdroidfilter.seforimapp.android.ui.shell.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Catalog
import io.github.kdroidfilter.seforimapp.legacy.data.InstallingException
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.NoLibraryException
import io.github.kdroidfilter.seforimapp.legacy.data.OutdatedLibraryException
import io.github.kdroidfilter.seforimapp.legacy.data.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * The Home of the main app (HomeScreen), without its tools: the wallpaper under its two blending layers, the catalog's
 * dropdowns, the logo and the search field, then (where the main app has its widgets) the books opened lately, the
 * favourites and the library's sections. With no library it offers the install from files and says where to copy
 * one; while an install runs it says so; a library too old for this app, or one that cannot be read, gets the main
 * app's update screen's words. It also takes the desktop's links (zayit://book/<id>/line/<id>, zayit://search). Before
 * the first Home, the licence is accepted (LicenceActivity).
 */
class MainActivity : BaseActivity() {
    private lateinit var content: LinearLayout
    private var loadJob: Job? = null

    /** Waits for an install to end ([showInstalling]), then opens the library again. */
    private var installWait: Job? = null
    private var pendingLink: Uri? = null

    /** The library the Home shows; null while it opens, and with no library or one that cannot be read. */
    private var homeLibrary: Library.Opened? = null
    private var search: View? = null

    /** Where the Home has the catalog row, filled once the catalog is read ([showCatalog]). */
    private var catalogSlot: LinearLayout? = null

    /** Where the Home has the cards of the books opened lately and of the favourites, and what each shows. */
    private var recentsSlot: LinearLayout? = null
    private var favoritesSlot: LinearLayout? = null
    private var shownRecents: List<Place>? = null
    private var shownFavorites: List<Place>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The main app's onboarding asks for the licence before anything else: the Home comes after it.
        if (!prefs.licenceAccepted) {
            LicenceActivity.start(this, intent)
            finish()
            return
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_library, ShellTexts.BOOK_LIST) { openLibrary(null) }
            title.text = getString(R.string.app_name)
            overflow { shellMenu() }
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))
        // The Home is a panel (desktop's home card), the wallpaper over it when shown.
        val frame = FrameLayout(this).apply { setBackgroundColor(colors.panel) }
        if (wallpaperAllowed()) frame.addView(WallpaperView(this, colors, scope), FrameLayout.LayoutParams(MATCH, MATCH))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val side = dp(if (resources.configuration.screenWidthDp < 360) 10 else 14)
            setPadding(side, dp(8), side, dp(16))
        }
        frame.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(frame, lp(MATCH, 0, 1f))
        setContentView(root)
        if (savedInstanceState == null) pendingLink = intent?.data
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Settings' "חפש את הספרייה מחדש", whatever the Home shows (no library too).
        if (intent.getBooleanExtra(EXTRA_RELOAD, false)) load()
        intent.data?.let {
            pendingLink = it
            handleLink()
        }
    }

    override fun onResume() {
        super.onResume()
        // Left for the licence (onCreate), or rebuilt in another theme (BaseActivity).
        if (isFinishing || rebuilding) return
        val home = homeLibrary ?: run {
            // An install from files ended behind the Home without a library, and opened its library.
            if (loadJob?.isActive != true && installWait?.isActive != true && app.library.current != null) load()
            return
        }
        // The library was closed behind the Home (Settings searches for it again): open it again, or say it is gone.
        // Otherwise only the books opened lately and the favourites can have changed (in a book, in Settings).
        if (app.library.current !== home) load() else refreshPlaces()
    }

    /** A history or favourites dialog of the ⋮ menu removed or cleared some: the cards show it. */
    override fun onPlacesChanged() = refreshPlaces()

    private fun reload() {
        app.library.close()
        load()
    }

    private fun load() {
        loadJob?.cancel()
        installWait?.cancel()
        homeLibrary = null
        showProgress()
        loadJob = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { app.library.open() } }
            result.onSuccess { opened ->
                showHome(opened)
                handleLink()
                showCatalog(opened)
                // The search's indexes open in the background, ready for the first search
                app.search.prepare()
            }.onFailure { error ->
                when (error) {
                    is NoLibraryException -> showNoLibrary()
                    is InstallingException -> showInstalling()
                    is OutdatedLibraryException -> showUpdate(outdated = true, version = error.version)
                    else -> {
                        Log.w(TAG, "The library cannot be opened", error)
                        showUpdate(outdated = false, version = null)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the hero: catalog, logo, search

    /** The catalog row (CatalogRow): its dropdowns, wrapping and centred, each a menu of its sections' books. */
    private fun catalogRow(menus: List<CatalogMenu>): View {
        val row = FlowRow(this, dp(8), dp(8))
        menus.forEach { menu -> row.addView(dropdown(menu.label) { anchor -> catalogMenu(anchor, menu) }) }
        return row
    }

    private fun dropdown(text: String, onClick: (View) -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(36)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        val r = dp(8).toFloat()
        val stroke = maxOf(1, dp(1))
        background = states(
            roundRect(Palette.withAlpha(colors.panel, 0.95f), r, colors.border, stroke),
            roundRect(colors.pressed, r, colors.border, stroke),
            roundRect(colors.focus, r, colors.accent, stroke),
        )
        addView(label(text, 14f, colors.text, maxLines = 1).apply { maxWidth = dp(110) })
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_arrow_down)
            setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        }, lp(dp(16), dp(16)).apply { marginStart = dp(4) })
        keyable(this) { onClick(it) }
    }

    /** A dropdown's menu: its sections' titles in grey, each followed by its books. */
    private fun catalogMenu(anchor: View, menu: CatalogMenu) {
        Dialogs.popup(anchor, colors, menu.items.map { item ->
            val bookId = item.bookId
            if (bookId == null) {
                MenuEntry(item.label)
            } else {
                MenuEntry(item.label) {
                    val lineId = item.lineId
                    if (lineId == null) openBook(bookId, item.label) else openAt(bookId, lineId)
                }
            }
        })
    }

    /** The word פרדס in the icon's letters and gold (HomeScreen LogoImage), half the page wide, 320 to 450dp. */
    private fun logo(): TextView {
        val screen = resources.configuration.screenWidthDp
        val page = screen - if (screen < 360) 20 else 28
        val width = (page * 0.5f).coerceIn(minOf(320f, page * 0.78f), 450f)
        val height = width * 715f / 1556f
        return label("פרדס", height * 0.62f, colors.logo, typeface = Fonts.logo(this)).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = "פרדס"
            setPadding(0, dp(height * 0.16f), 0, dp(height * 0.16f))
        }
    }

    /**
     * The Home's search field (UnifiedSearchBar): a 48dp pill on the panel with a thin grey border, which turns to the
     * accent, 2dp, when focused (where a keypad starts); it opens the search.
     */
    private fun searchField(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(14), 0, dp(14), 0)
        val r = dp(24).toFloat()
        val thin = maxOf(1, dp(1))
        background = states(
            roundRect(colors.panel, r, colors.border, thin),
            roundRect(colors.pressed, r, colors.border, thin),
            roundRect(colors.panel, r, colors.accent, dp(2)),
        )
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        }, lp(dp(16), dp(16)).apply { marginEnd = dp(8) })
        addView(label(SearchActivity.nextHint(), 16f, SearchActivity.PLACEHOLDER_GREY, maxLines = 1))
        contentDescription = "חיפוש"
        keyable(this) { openSearch() }
    }

    // ------------------------------------------------------------------ states

    /** While the library opens: the main app's bare spinner (LoadingBox). */
    private fun showProgress() {
        content.removeAllViews()
        content.addView(spinner(colors), lp(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(80)
        })
    }

    /**
     * The main app's update screen's first step (DatabaseUpdateScreen VersionCheckStep): a library too old for this app
     * ([outdated]: its release and the one needed, then "המשך" to the install from files), or one that cannot be read
     * (reinstall, the hint for a library copied by hand, "נסה שוב" and "המשך").
     */
    private fun showUpdate(outdated: Boolean, version: String?) {
        content.removeAllViews()
        content.addView(card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            addView(label(if (outdated) DesktopStrings.dbUpdateVersionCheckTitle else DesktopStrings.dbUpdateReinstallTitle, 22f, colors.text).apply {
                gravity = Gravity.CENTER
            }, lp(MATCH, WRAP))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_warning)
                // IntelliJ's General.Warning, as the main app's
                setColorFilter(if (colors.dark) 0xFFF2C55C.toInt() else 0xFFFFAF0F.toInt(), PorterDuff.Mode.SRC_IN)
            }, lp(dp(80), dp(80)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(16), 0, dp(12))
            })
            fun body(text: String) = label(text, 15f, colors.text).apply {
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(4), 0, dp(4))
            }
            if (outdated) {
                val current = version?.takeIf { it.isNotEmpty() }?.let(DatabaseVersion::formatForDisplay) ?: DesktopStrings.dbUpdateVersionUnknown
                addView(body(DesktopStrings.dbUpdateCurrentVersion.format(current)), lp(MATCH, WRAP))
                addView(body(DesktopStrings.dbUpdateRequiredVersion.format(DatabaseVersion.formatForDisplay(DatabaseVersion.MINIMUM_REQUIRED_VERSION))), lp(MATCH, WRAP))
            }
            addView(body(if (outdated) DesktopStrings.dbUpdateNeedToUpdate else DesktopStrings.dbUpdateDatabaseMissingError).apply {
                setPadding(0, dp(12), 0, dp(4))
            }, lp(MATCH, WRAP))
            if (!outdated) {
                // Android only: a library copied from a computer usually just needs its permissions fixed. The script's
                // name kept left to right: Android 4.4 would put its parenthesis and hyphen on the wrong sides.
                val hint = AndroidStrings.brokenLibraryHint.replace(SCRIPT, Bidi.ltr(SCRIPT))
                addView(label(hint, 14f, colors.text).apply {
                    setLineSpacing(0f, 1.15f)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = roundRect(colors.accentTint, dp(8).toFloat())
                }, lp(MATCH, WRAP).apply { topMargin = dp(10) })
            }
            val buttons = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(4))
            }
            if (!outdated) buttons.addView(textButton(DesktopStrings.dbUpdateRetry) { reload() }, lp(WRAP, WRAP).apply { marginEnd = dp(12) })
            val next = accentButton(DesktopStrings.dbUpdateContinue, colors) { InstallActivity.start(this@MainActivity, replacing = true) }
            buttons.addView(next)
            addView(buttons, lp(MATCH, WRAP))
            if (!next.isInTouchMode) next.post { next.requestFocus() }
        })
    }

    /**
     * The Home of [opened], built once: coming back to it redraws only the books opened lately and the favourites
     * (refreshPlaces), so it keeps its scroll and a keypad its place.
     */
    private suspend fun showHome(opened: Library.Opened) {
        val roots = withContext(Dispatchers.IO) { runCatching { opened.rootEntries() }.getOrDefault(emptyList()) }
        content.removeAllViews()
        catalogSlot = placesSlot().also { content.addView(it) }
        content.addView(logo(), lp(MATCH, WRAP))
        val search = searchField()
        this.search = search
        content.addView(search, lp(MATCH, WRAP).apply { bottomMargin = dp(16) })
        recentsSlot = placesSlot().also { content.addView(it) }
        favoritesSlot = placesSlot().also { content.addView(it) }
        shownRecents = null
        shownFavorites = null
        content.addView(card("ספרייה").apply {
            roots.forEach { entry ->
                addView(TreeRow(this@MainActivity, colors).bind(entry.title, iconRes = R.drawable.ic_folder, arrowRes = R.drawable.ic_arrow_left).apply {
                    keyable(this) { openLibrary(entry.id) }
                })
            }
        })
        homeLibrary = opened
        refreshPlaces()
        // A keypad starts on the search field, the Home's first stop.
        if (!search.isInTouchMode) search.requestFocus()
    }

    /**
     * The catalog row, read once the rest of the Home is drawn and a link handled, as the main app's CatalogRow loads
     * apart from its Home: a whole library's catalog.pb takes seconds to read on an old phone (12 s on the emulator).
     */
    private suspend fun showCatalog(opened: Library.Opened) {
        val menus = withContext(Dispatchers.IO) { runCatching { Catalog.menus(opened) }.getOrDefault(emptyList()) }
        if (menus.isNotEmpty()) catalogSlot?.addView(catalogRow(menus), lp(MATCH, WRAP).apply { topMargin = dp(4) })
    }

    private fun placesSlot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = lp(MATCH, WRAP)
    }

    /**
     * The cards of the books opened lately and of the favourites, each redrawn only when what it shows has changed:
     * in a book, in Settings, or in the history and favourites dialogs, which leave the Home resumed under them.
     */
    private fun refreshPlaces() {
        if (homeLibrary == null) return
        val recents = prefs.recents.take(PLACES)
        if (recents != shownRecents) {
            shownRecents = recents
            recentsSlot?.let { slot ->
                fillPlaces(slot, "היסטוריה", recents, R.drawable.ic_history, ShellTexts.SHOW_ALL_HISTORY) {
                    PlacesDialogs.recents(this, colors) { refreshPlaces() }
                }
            }
        }
        val favorites = prefs.bookmarks.take(PLACES)
        if (favorites != shownFavorites) {
            shownFavorites = favorites
            favoritesSlot?.let { slot ->
                fillPlaces(slot, "מועדפים", favorites, R.drawable.ic_star, ShellTexts.SHOW_ALL_FAVORITES) {
                    PlacesDialogs.favorites(this, colors) { refreshPlaces() }
                }
            }
        }
    }

    /**
     * [slot]'s card, as the main app's History and Favorites widgets: [title], the first [PLACES] of [places], and
     * [allLabel] to all of them; no card for none (the main app's default Home has neither).
     */
    private fun fillPlaces(slot: LinearLayout, title: String, places: List<Place>, icon: Int, allLabel: String, showAll: () -> Unit) {
        // A keypad on the card being redrawn stays at the same row: removing the row it is on would send it to the
        // title bar.
        val old = slot.getChildAt(0) as? ViewGroup
        val at = old?.let { card -> (0 until card.childCount).firstOrNull { card.getChildAt(it).hasFocus() } } ?: -1
        slot.removeAllViews()
        val keypad = at >= 0 && !slot.isInTouchMode
        if (places.isEmpty()) {
            if (keypad) search?.requestFocus()
            return
        }
        val card = card(title).apply {
            places.take(PLACES).forEach { place -> addView(placeRow(place, icon)) }
            addView(textButton(allLabel) { showAll() })
        }
        slot.addView(card)
        // Its rows come after its title.
        if (keypad) card.getChildAt(at.coerceIn(1, card.childCount - 1)).requestFocus()
    }

    private fun placeRow(place: Place, icon: Int): View =
        TreeRow(this, colors).bind(place.title, iconRes = icon, secondLine = place.note.ifEmpty { "שורה ${place.lineIndex + 1}" }, iconTint = colors.accent).apply {
            keyable(this) { BookActivity.start(this@MainActivity, place.bookId, place.lineIndex, place.title) }
        }

    /** An install from files runs (the library may be replaced): the Home waits for it, and shows it on OK. */
    private fun showInstalling() {
        content.removeAllViews()
        content.addView(logo(), lp(MATCH, WRAP))
        content.addView(card().apply {
            addView(spinner(colors), lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(label(InstallTexts.INSTALLING, 16f, colors.text).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(4))
            })
            addView(textButton(InstallTexts.SHOW_INSTALL) { InstallActivity.start(this@MainActivity, replacing = app.installer.replacing) }.also { if (!it.isInTouchMode) it.post { it.requestFocus() } })
        })
        installWait = scope.launch {
            app.installer.running.first { !it }
            load()
        }
    }

    /**
     * No library yet: the install from files (a bundle, a .zst, or a library folder on the phone), then where to copy a
     * library by hand, the desktop's databases folder, into the app's folder on the phone or a card.
     */
    private fun showNoLibrary() {
        content.removeAllViews()
        val folder = app.library.prepareFolder()
        val cards = app.library.locations().drop(1).filter { it.label == Library.CARD_LABEL }
        content.addView(logo(), lp(MATCH, WRAP))
        content.addView(card().apply {
            addView(label("ברוכים הבאים לפרדס!", 20f, colors.text, bold = true).apply { gravity = Gravity.CENTER })
            addView(label(PARDES_BASED_ON, 14f, colors.secondary).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(4))
            })
        })
        content.addView(card("עדיין אין ספרייה במכשיר").apply {
            addView(label(Bidi.marks(InstallTexts.NO_LIBRARY_INTRO), 15f, colors.text).apply { setLineSpacing(0f, 1.15f) })
            addView(textButton(InstallTexts.TITLE) { InstallActivity.start(this@MainActivity, replacing = false) }.also { if (!it.isInTouchMode) it.post { it.requestFocus() } })
            val steps = buildString {
                append("פרדס קורא את הספרייה של זית למחשב. כדי להעביר אותה בכבל:\n\n")
                append("1. במחשב, מצאו את תיקיית הספרייה של זית למחשב, databases. ב־Windows היא:\n")
                append(Bidi.ltr("%APPDATA%\\io.github.kdroidfilter.seforimapp\\databases")).append("\n\n")
                append("2. חברו את הטלפון למחשב בכבל USB, במצב העברת קבצים (MTP).\n\n")
                append("3. העתיקו את הקובץ seforim.db ואת מה שלצדו, התיקיות seforim.db.lucene ו־seforim.db.lookup.lucene ")
                append("(אינדקס החיפוש) והקבצים lexical.db, catalog.pb ו־release_info.txt, לתיקייה הזו בזיכרון הטלפון:\n")
                append(shortPath(folder?.path ?: "/Android/data/io.github.efrul.pardes/files/library"))
                if (cards.isNotEmpty()) append("\nאו לאותה תיקייה בכרטיס הזיכרון.")
                append("\n\n4. חזרו לפרדס ובחרו \"חפש שוב\".\n\n")
                append("הספרייה המלאה תופסת כ־13 ג׳יגה־בייט: מסד הנתונים כ־8 ואינדקס החיפוש כ־5. בטלפון עם מעט מקום אפשר ")
                append("להעביר ספרייה קטנה של ספרים נבחרים, שבונה הכלי Zayit Subset במחשב. בכרטיס זיכרון בפורמט FAT32 אין מקום ")
                append("לקובץ של 8 ג׳יגה־בייט: את מסד הנתונים המלא שומרים שם מחולק לחלקים (").append(Bidi.ltr("seforim.db, seforim.db001"))
                append(" וכו׳), כפי שפרדס לאנדרואיד 8 ומעלה מחלק אותו, וכפי שההתקנה מקבצים כותבת אותו שם.")
            }
            addView(label(steps, 15f, colors.text).apply {
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(10), 0, 0)
            })
            addView(textButton("חפש שוב") { reload() })
        })
    }

    // ------------------------------------------------------------------ pieces

    /** An Island of the Home, with an optional title. */
    private fun card(title: String? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = island(colors)
        setPadding(dp(8), dp(if (title == null) 12 else 4), dp(8), dp(8))
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(10) }
        if (title != null) addView(label(title, 14f, colors.text, bold = true).apply { setPadding(dp(6), dp(8), dp(6), dp(4)) })
    }

    private fun textButton(text: String, onClick: () -> Unit): TextView = label(text, 15f, colors.accent, bold = true).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        setPadding(dp(12), 0, dp(12), 0)
        background = states(colors, 0, dp(8).toFloat())
        keyable(this) { onClick() }
    }

    private fun openSearch() = startActivity(Intent(this, SearchActivity::class.java))

    private fun openLibrary(categoryId: Long?) = LibraryActivity.start(this, categoryId)

    private fun openBook(id: Long, title: String) = BookActivity.start(this, id, prefs.lastLine(id), title)

    /** A book at a line given by its id (the catalog's links into a book's contents, desktop's links). */
    private fun openAt(bookId: Long, lineId: Long) {
        val opened = app.library.current ?: return
        scope.launch {
            val index = withContext(Dispatchers.IO) { runCatching { opened.lineIndex(lineId) }.getOrNull() }
            BookActivity.start(this@MainActivity, bookId, index)
        }
    }

    /** zayit://book/<bookId>/line/<lineId> (desktop's "copy link") or zayit://search?q=…, once the library is open. */
    private fun handleLink() {
        val link = pendingLink ?: return
        if (app.library.current == null) return
        pendingLink = null
        when (val target = parseZayitDeepLink(link.toString())) {
            is Destination.Search -> startActivity(
                Intent(this, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_QUERY, target.query).putExtra(SearchActivity.EXTRA_RUN, true),
            )
            is Destination.Book -> target.lineId?.let { openAt(target.bookId, it) } ?: BookActivity.start(this, target.bookId, null)
            else -> Unit
        }
    }

    /** The wallpaper, unless Settings turns it off or Android counts the phone as low on memory. */
    private fun wallpaperAllowed(): Boolean {
        if (!prefs.wallpaper) return false
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return !activityManager.isLowRamDevice
    }

    /** A folder as a computer shows it over USB: from Android/ on, the storage it is on aside. */
    private fun shortPath(path: String): String = Bidi.ltr(path.substringAfter("/Android/", "").let { if (it.isEmpty()) path else "Android/$it" })

    /**
     * The Home's wallpaper with desktop HomeView's two layers over it (a horizontal vignette of the panel's colour,
     * and a radial wash of it centred at 65% of the height), drawn once into one bitmap the size of the view: a
     * scroll then only draws that bitmap, which an old phone does at no cost. It is drawn off the main thread, where
     * it would hold up the launch screen's first layout (a decode, then two gradients over every pixel): the panel's
     * plain colour shows until it is ready.
     */
    private class WallpaperView(context: Context, private val colors: Colors, private val scope: CoroutineScope) : View(context) {
        private var bitmap: Bitmap? = null
        private var job: Job? = null

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            job?.cancel()
            bitmap = null
            if (w <= 0 || h <= 0) return
            val key = "$w x $h ${colors.dark}"
            cached?.takeIf { it.first == key }?.second?.get()?.let {
                bitmap = it
                return
            }
            job = scope.launch {
                val composed = withContext(Dispatchers.Default) { compose(w, h) } ?: return@launch
                cached = key to WeakReference(composed)
                bitmap = composed
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        // inDither: deprecated (and ignored) from Android 7.0, where the picture is decoded in 32 bits anyway.
        @Suppress("DEPRECATION")
        private fun compose(w: Int, h: Int): Bitmap? {
            val path = if (colors.dark) "home/homepage_wallpaper_dark_small.jpg" else "home/homepage_wallpaper_small.jpg"
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                // ContentScale.Crop: the picture covers the view; decoded no larger than that needs.
                val cover = maxOf(w.toFloat() / bounds.outWidth, h.toFloat() / bounds.outHeight)
                var sample = 1
                while (cover * sample * 2 <= 1f) sample *= 2
                val picture = context.assets.open(path).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        // The photograph in 16 bits, dithered, where that halves its memory on an old phone's heap: it
                        // is drawn into the 32-bit result, so the washes over it do not band. Android 7.0 no longer
                        // dithers a decode, and keeps 32 bits.
                        if (Build.VERSION.SDK_INT < 24) {
                            inPreferredConfig = Bitmap.Config.RGB_565
                            inDither = true
                        }
                    })
                } ?: return null
                // 32 bits a pixel: in 16 the radial wash shows rings (banding), dithered or not.
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(colors.panel)
                val s = maxOf(w.toFloat() / picture.width, h.toFloat() / picture.height)
                val dw = (picture.width * s).toInt()
                val dh = (picture.height * s).toInt()
                val left = (w - dw) / 2
                val top = (h - dh) / 2
                canvas.drawBitmap(picture, null, Rect(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                picture.recycle()
                val paint = Paint(Paint.DITHER_FLAG)
                paint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), 0f,
                    VIGNETTE_ALPHAS.map { Palette.withAlpha(colors.panel, it) }.toIntArray(), VIGNETTE_STOPS, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(
                    w / 2f, h * 0.65f, w * 0.55f,
                    intArrayOf(
                        colors.panel, colors.panel, Palette.withAlpha(colors.panel, 0.75f),
                        Palette.withAlpha(colors.panel, 0.40f), Palette.withAlpha(colors.panel, 0f),
                    ),
                    floatArrayOf(0f, 0.35f, 0.55f, 0.75f, 1f), Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                out
            }.getOrNull()
        }

        private companion object {
            var cached: Pair<String, WeakReference<Bitmap>>? = null

            /** HomeView's horizontal vignette: the panel's colour at these alphas, at these places across. */
            val VIGNETTE_STOPS = floatArrayOf(0f, 0.04f, 0.08f, 0.13f, 0.20f, 0.28f, 0.38f, 0.50f, 0.62f, 0.72f, 0.80f, 0.87f, 0.92f, 0.96f, 1f)
            val VIGNETTE_ALPHAS = listOf(0.10f, 0.20f, 0.35f, 0.50f, 0.65f, 0.78f, 0.85f, 0.78f, 0.85f, 0.78f, 0.65f, 0.50f, 0.35f, 0.20f, 0.10f)
        }
    }

    companion object {
        /** An intent's extra: open the library again (Settings' "חפש את הספרייה מחדש"). */
        const val EXTRA_RELOAD = "reload"

        /** The books opened lately, and the favourites, that the Home shows. */
        private const val PLACES = 4

        private const val TAG = "Pardes"

        /** The transfer script the main app's hint names. */
        private const val SCRIPT = "push-desktop-library"
    }
}

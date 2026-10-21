package io.github.kdroidfilter.seforimapp.legacy.ui

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.data.BundleNames
import io.github.kdroidfilter.seforimapp.android.data.InstallError
import io.github.kdroidfilter.seforimapp.android.data.InstallState
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.AndroidStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.DesktopStrings
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.installErrorMessage
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.isBusy
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.LegacyInstaller
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The main app's install from files (its onboarding's התקנה לא-מקוונת, and its database update's local file) for a
 * phone without its system picker or without a touch screen: a file browser made for the keypad. Its first page has
 * the system picker where the phone has one (the main app's way, for touch phones), what was found on the phone (a
 * bundle or a library in Pardes's folders or in Download), and the places to browse: Pardes's folders, the phone's
 * storage, each memory card, Download. A folder shows its folders and the files an install takes (bundle parts, a
 * .tar.zst, a seforim.db.zst, a seforim.db), with their sizes; ▲▼ move, OK opens a folder or picks a file, "בחירת
 * התיקייה הזו" picks the folder (a library in it, or its bundle), and Back goes up. Picking a part takes the bundle's
 * other parts and manifest from the same folder, as desktop does. While the install runs ([LegacyInstaller], in
 * [InstallService]'s foreground) the screen shows its progress and stays on; then the main app's end or error.
 */
class InstallActivity : BaseActivity() {
    private lateinit var bar: TopBar
    private lateinit var frame: FrameLayout
    private val installer: LegacyInstaller get() = app.installer

    /**
     * Whether the run replaces a library (Settings › ספרייה) rather than installing the first: the main app's update
     * wording. The screen's own for a run it starts; a run already going or ended has its own ([render]).
     */
    private var replacing = false

    /** The window keeps the screen on: set and cleared only when a run starts and ends, as each change lays it out again. */
    private var keptOn = false

    /** ביטול's question, while it is shown. */
    private var cancelDialog: Dialog? = null

    private enum class Mode { Browser, Progress, Result }

    private var mode: Mode? = null
    private var shownResult: InstallState? = null

    /** The folder the browser shows; null for its first page. */
    private var folder: File? = null
    private var listJob: Job? = null
    private var list: ListView? = null
    private val adapter = RowsAdapter()

    /** The row the browser had selected in each folder it left (the first page under ""), to come back to it. */
    private val positions = HashMap<String, Int>()

    /** Where Back and "לתיקייה שמעל" go from the folder shown: its parent, or the first page from a place's own folder. */
    private var upTarget: File? = null

    /** The places' folders ([places]), canonical, read off the main thread once. */
    @Volatile
    private var placePaths: Set<String>? = null

    /** The progress page's views, updated in place at each state (four times a second at most). */
    private var progressTitle: TextView? = null
    private var progressLine: ProgressBar? = null
    private var progressSpinner: View? = null
    private var progressPercent: TextView? = null
    private var progressBytes: TextView? = null
    private var progressItem: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        replacing = intent.getBooleanExtra(EXTRA_REPLACE, false)
        folder = savedInstanceState?.getString(STATE_FOLDER)?.let(::File)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { onBackPressedCompat() }
            title.text = InstallTexts.TITLE
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))
        frame = FrameLayout(this)
        root.addView(frame, lp(MATCH, 0, 1f))
        setContentView(root)
        askStorageAccess()
        scope.launch { installer.state.collect { render(it) } }
    }

    override fun onResume() {
        super.onResume()
        installer.uiVisible = true
    }

    override fun onPause() {
        installer.uiVisible = false
        super.onPause()
    }

    override fun onDestroy() {
        dismissCancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        folder?.let { outState.putString(STATE_FOLDER, it.path) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = onBackPressedCompat()

    private fun onBackPressedCompat() {
        when (mode) {
            // As the main app's onboarding: while the library installs, Back leaves the app and the install goes on.
            Mode.Progress -> moveTaskToBack(true)
            Mode.Result -> (shownResult as? InstallState.Done)?.let { openApp() } ?: backToFiles()
            else -> if (folder != null) up() else finish()
        }
    }

    // ------------------------------------------------------------------ the state

    private fun render(state: InstallState) {
        // A run this screen did not start (shown from the notification or the Home) speaks as it was started.
        if (state !is InstallState.Idle) replacing = installer.replacing
        // The screen stays on while the library installs (the work goes on with it off, in the service).
        if (state.isBusy != keptOn) {
            keptOn = state.isBusy
            if (keptOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!state.isBusy) dismissCancel()
        when {
            state.isBusy -> showProgress(state)
            state is InstallState.Done || state is InstallState.Failed -> showResult(state)
            else -> showBrowser()
        }
    }

    // ------------------------------------------------------------------ the browser

    private sealed class Row {
        class Header(val text: String) : Row()

        class Note(val text: CharSequence) : Row()

        class Item(val title: CharSequence, val detail: CharSequence?, val icon: Int, val action: () -> Unit) : Row()
    }

    /** What a pick installs: a folder with a library, or files (a bundle's parts and manifest, a .tar.zst, a .zst). */
    private sealed class Pick(val detail: String) {
        class Folder(val dir: File, detail: String) : Pick(detail)

        class Files(val files: List<File>, detail: String) : Pick(detail)
    }

    private fun showBrowser() {
        if (mode == Mode.Browser) return
        mode = Mode.Browser
        shownResult = null
        clearProgressViews()
        frame.removeAllViews()
        val view = ListView(this).apply {
            divider = null
            selector = states(colors, 0, dp(6).toFloat())
            cacheColorHint = 0
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToPadding = false
            adapter = this@InstallActivity.adapter
            setOnItemClickListener { _, _, position, _ -> (this@InstallActivity.adapter.items.getOrNull(position) as? Row.Item)?.action?.invoke() }
        }
        list = view
        val card = FrameLayout(this).apply { background = island(colors) }
        card.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(card, FrameLayout.LayoutParams(MATCH, MATCH).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        show(folder)
    }

    /** Lists [dir] (null: the first page) off the main thread, then puts the keypad back where it was there. */
    private fun show(dir: File?) {
        list?.let { positions[key(folder)] = it.selectedItemPosition }
        listJob?.cancel()
        folder = dir
        bar.setSubtitle(dir?.path?.let(Bidi::ltr))
        upTarget = dir?.parentFile
        listJob = scope.launch {
            val (rows, up) = withContext(Dispatchers.IO) { if (dir == null) firstPage() to null else folderPage(dir) }
            upTarget = up
            adapter.items = rows
            val view = list ?: return@launch
            val at = positions[key(dir)]?.takeIf { it in rows.indices && rows[it] is Row.Item } ?: rows.indexOfFirst { it is Row.Item }
            if (at >= 0) view.setSelection(at)
            // A keypad starts in the list: the title bar's Back took the first focus while it was empty.
            if (!view.isInTouchMode) view.requestFocus()
        }
    }

    private fun key(dir: File?): String = dir?.path ?: ""

    private fun up() {
        if (folder != null) show(upTarget)
    }

    /** The first page: how to pick, the system picker, what was found, and the places to browse. Blocking. */
    private fun firstPage(): List<Row> {
        val rows = ArrayList<Row>()
        rows += Row.Note(Bidi.marks(DesktopStrings.onboardingSelectFilesMessage + "\n" + InstallTexts.ALSO))
        if (systemPicker() != null) {
            rows += Row.Item(DesktopStrings.onboardingChooseFiles, InstallTexts.SYSTEM_PICKER, R.drawable.ic_file_install) { pickWithSystem() }
        }
        val found = findOnDevice()
        if (found.isNotEmpty()) {
            rows += Row.Header(InstallTexts.FOUND)
            found.forEach { pick -> rows += Row.Item(pickTitle(pick), pick.detail + " · " + Bidi.ltr(where(pick)), pickIcon(pick)) { start(pick) } }
        }
        rows += Row.Header(InstallTexts.PLACES)
        places().forEach { (title, dir) -> rows += Row.Item(title, Bidi.ltr(dir.path), R.drawable.ic_folder) { show(dir) } }
        if (!hasStorageAccess()) rows += Row.Note(InstallTexts.NO_PERMISSION)
        return rows
    }

    /** A folder's page (pick it, go up, its folders, then the files an install takes), and where up goes. Blocking. */
    private fun folderPage(dir: File): Pair<List<Row>, File?> {
        val rows = ArrayList<Row>()
        val pick = pickIn(dir)
        // Hebrew first, as the first page's rows: a line that starts with a file's Latin name is laid out left to right.
        rows += Row.Item(InstallTexts.PICK_FOLDER, pick?.let { "${it.detail} · ${pickTitle(it)}" } ?: InstallTexts.NOTHING_HERE, R.drawable.ic_check_circle) {
            if (pick != null) start(pick) else toast(InstallTexts.NOTHING_HERE)
        }
        val upTo = if (isPlace(dir)) null else dir.parentFile
        rows += Row.Item(InstallTexts.UP, upTo?.path?.let(Bidi::ltr) ?: InstallTexts.PLACES, R.drawable.ic_back) { up() }
        val entries = dir.listFiles()
        if (entries == null) {
            rows += Row.Note(if (hasStorageAccess()) InstallTexts.UNREADABLE else InstallTexts.UNREADABLE + "\n" + InstallTexts.NO_PERMISSION)
            return rows to upTo
        }
        val folders = entries.filter { it.isDirectory && !it.isHidden }.sortedBy { it.name.lowercase() }
        val files = entries.filter { it.isFile && installable(it.name) }.sortedBy { it.name.lowercase() }
        folders.forEach { child -> rows += Row.Item(child.name, null, R.drawable.ic_folder) { show(child) } }
        files.forEach { file ->
            val icon = if (file.name == LibraryStore.DB_NAME) R.drawable.ic_book else R.drawable.ic_unarchive
            rows += Row.Item(Bidi.ltr(file.name), formatBytes(file.length()), icon) { pickFile(file) }
        }
        if (folders.isEmpty() && files.isEmpty()) rows += Row.Note(InstallTexts.EMPTY)
        return rows to upTo
    }

    /** A file an install takes: a bundle's part, a .tar.zst, a .zst, a seforim.db. */
    private fun installable(name: String): Boolean =
        BundleNames.partNumber(name) != null || name.endsWith(".zst", ignoreCase = true) || name == LibraryStore.DB_NAME

    /**
     * The places to browse, each once: Pardes's folders on the phone and the cards (where a computer copies a bundle
     * or a library over USB), the phone's storage, each card's, Download, and anything else under /storage. Blocking.
     */
    private fun places(): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val seen = HashSet<String>()
        fun add(title: String, dir: File?) {
            if (dir == null || !dir.isDirectory) return
            if (seen.add(canonical(dir))) out += title to dir
        }
        app.library.locations().filter { it.label != Library.INTERNAL_LABEL }.forEach { add(InstallTexts.APP_FOLDER.format(it.label), it.dir.parentFile) }
        @Suppress("DEPRECATION")
        add(InstallTexts.DOWNLOADS, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        @Suppress("DEPRECATION")
        add(InstallTexts.PHONE_STORAGE, Environment.getExternalStorageDirectory())
        val external = runCatching { getExternalFilesDirs(null).toList() }.getOrDefault(emptyList())
        external.drop(1).filterNotNull().forEach { files -> add(InstallTexts.CARD, File(files.path.substringBefore("/Android/data/"))) }
        File("/storage").listFiles()?.sortedBy { it.name }?.forEach { add(it.name, it) }
        placePaths = seen
        return out
    }

    /** A place's own folder: Back from it goes to the first page. Blocking. */
    private fun isPlace(dir: File): Boolean {
        val paths = placePaths ?: places().let { placePaths.orEmpty() }
        return canonical(dir) in paths || dir.parentFile == null
    }

    /**
     * What may be installed without browsing: a bundle or a .zst in Pardes's folders (as the main app's findLocalBundle)
     * or in Download, and a library in a folder of Download or in Download itself. Pardes's own library is not one.
     * Blocking.
     */
    private fun findOnDevice(): List<Pick> {
        val own = app.library.locations().map { it.dir.parentFile } + app.filesDir
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val libraries = app.library.locations().map { canonical(it.dir) }.toSet()
        val dirs = (own + downloads + (downloads?.listFiles()?.filter { it.isDirectory && !it.isHidden } ?: emptyList()))
            .filterNotNull().distinctBy(::canonical)
        return dirs.filter { canonical(it) !in libraries }.mapNotNull { dir -> runCatching { pickIn(dir) }.getOrNull() }
    }

    /** What picking [dir] installs: its library, else its bundle (one), else a .tar.zst or a .zst in it; null for none. */
    private fun pickIn(dir: File): Pick? {
        val db = File(dir, LibraryStore.DB_NAME)
        if (db.isFile) return Pick.Folder(dir, formatBytes(LibraryStore.libraryBytes(dir)))
        val files = dir.listFiles()?.filter { it.isFile } ?: return null
        val part = files.filter { BundleNames.partNumber(it.name) != null }.minByOrNull { it.name.lowercase() }
        if (part != null) return bundleOf(part)
        val single = files.firstOrNull { it.name.endsWith(".tar.zst", ignoreCase = true) } ?: files.firstOrNull { it.name.endsWith(".zst", ignoreCase = true) }
        return single?.let(::filesFor)
    }

    /** What picking [file] installs. Blocking. */
    private fun pickFor(file: File): Pick? = when {
        BundleNames.partNumber(file.name) != null -> bundleOf(file)
        file.name == LibraryStore.DB_NAME -> file.parentFile?.let { pickIn(it) }
        file.name.endsWith(".zst", ignoreCase = true) -> filesFor(file)
        else -> null
    }

    /** A part's bundle: every part of it in the part's folder, and its manifest there, as desktop takes them. */
    private fun bundleOf(part: File): Pick {
        val bundle = BundleNames.bundleOf(part.name).orEmpty()
        val siblings = part.parentFile?.listFiles()?.filter { it.isFile }.orEmpty()
        val parts = siblings.filter { BundleNames.partNumber(it.name) != null && BundleNames.bundleOf(it.name).equals(bundle, ignoreCase = true) }
            .sortedBy { BundleNames.partNumber(it.name) }
        val manifest = siblings.firstOrNull { it.name.equals(BundleNames.manifestNameFor(bundle), ignoreCase = true) }
        return Pick.Files(parts + listOfNotNull(manifest), InstallTexts.PARTS.format(parts.size) + " · " + formatBytes(parts.sumOf { it.length() }))
    }

    /** A single .tar.zst, or a seforim.db.zst with the release_info.txt beside it (its version). */
    private fun filesFor(file: File): Pick {
        val info = File(file.parentFile, LibraryStore.RELEASE_INFO_NAME).takeIf { !file.name.endsWith(".tar.zst", ignoreCase = true) && it.isFile }
        return Pick.Files(listOfNotNull(file, info), formatBytes(file.length()))
    }

    private fun pickTitle(pick: Pick): CharSequence = when (pick) {
        is Pick.Folder -> InstallTexts.LIBRARY_IN.format(Bidi.ltr(pick.dir.name))
        is Pick.Files -> Bidi.ltr(pick.files.first().name)
    }

    /** The folder a found pick is in, from the storage's root on (as a computer shows it over USB). */
    private fun where(pick: Pick): String {
        val dir = when (pick) {
            is Pick.Folder -> pick.dir
            is Pick.Files -> pick.files.first().parentFile ?: return ""
        }
        val path = dir.path
        val root = places().map { it.second.path }.filter { path.startsWith("$it/") }.minByOrNull { it.length }
        return if (root != null) path.removePrefix("$root/") else path
    }

    private fun pickIcon(pick: Pick): Int = if (pick is Pick.Folder) R.drawable.ic_library else R.drawable.ic_unarchive

    private fun pickFile(file: File) {
        scope.launch {
            val pick = withContext(Dispatchers.IO) { pickFor(file) } ?: return@launch
            start(pick)
        }
    }

    /** Starts the install of [pick]: a folder after saying it is moved, each after asking where when there is a choice. */
    private fun start(pick: Pick) {
        when (pick) {
            is Pick.Folder -> scope.launch {
                val place = withContext(Dispatchers.IO) { where(pick) }
                Dialogs.show(
                    this@InstallActivity,
                    colors,
                    pickTitle(pick).toString(),
                    Dialogs.text(this@InstallActivity, colors, InstallTexts.MOVE_NOTE.format(Bidi.ltr(place))),
                    listOf(InstallTexts.CONTINUE to { chooseTarget { installer.installFolder(pick.dir, it, replacing) } }, DesktopStrings.settingsCancel to {}),
                    focusButton = 0,
                )
            }
            is Pick.Files -> chooseTarget { installer.installFiles(pick.files, it, replacing) }
        }
    }

    /**
     * Where the library goes, when the phone has a memory card (the main app's location step: each place with its free
     * space, FAT32 noted); [then] with null where there is one place only: where the library is, else the phone.
     */
    private fun chooseTarget(then: (Library.Location?) -> Unit) {
        scope.launch {
            val places = withContext(Dispatchers.IO) {
                val current = app.library.currentLocation()?.dir
                app.library.locations().filter { it.label != Library.INTERNAL_LABEL }.map { location ->
                    val (free, total) = location.space()
                    Triple(location, AndroidStrings.locationSpace.format(formatBytes(free), formatBytes(total)), location.dir == current)
                }
            }
            if (places.size <= 1) {
                then(null)
                return@launch
            }
            Dialogs.choices(
                this@InstallActivity,
                colors,
                AndroidStrings.locationTitle,
                places.map { (location, space, _) ->
                    MenuEntry(location.label + " · " + Bidi.ltr(space) + if (location.isFat) " · FAT32" else "", R.drawable.ic_folder) { then(location) }
                },
                checked = places.indexOfFirst { it.third },
            )
        }
    }

    // ------------------------------------------------------------------ the system picker

    /** The system's document picker (Android 4.4), when the phone has one: the main app's way to pick the files. */
    private fun systemPicker(): Intent? =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .takeIf { it.resolveActivity(packageManager) != null }

    private fun pickWithSystem() {
        val intent = systemPicker() ?: return
        runCatching { startActivityForResult(intent, REQUEST_PICK) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK || resultCode != Activity.RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) (0 until clip.itemCount).mapNotNullTo(uris) { clip.getItemAt(it).uri } else data.data?.let { uris += it }
        if (uris.isEmpty()) return
        // The install reads them one by one, possibly after this screen is gone: the picker's grant ends with it.
        uris.forEach { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        chooseTarget { installer.installUris(uris, it, replacing) }
    }

    // ------------------------------------------------------------------ storage access

    /** Android 6.0 to 7.1 ask for it while the app runs; 4.4 and 5.x grant it with the app. */
    private fun hasStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun askStorageAccess() {
        if (Build.VERSION.SDK_INT >= 23 && !hasStorageAccess()) askStorageAccess23()
    }

    // Writing lets a library folder be moved rather than copied (a rename out of Download).
    @TargetApi(23)
    private fun askStorageAccess23() {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE && mode == Mode.Browser) show(folder)
    }

    // ------------------------------------------------------------------ the progress

    private fun showProgress(state: InstallState) {
        if (mode != Mode.Progress) {
            mode = Mode.Progress
            shownResult = null
            bar.setSubtitle(null)
            buildProgress()
        }
        val update = replacing
        progressTitle?.text = when (state) {
            is InstallState.Extracting -> if (update) DesktopStrings.dbUpdateExtracting else DesktopStrings.onboardingExtractingMessage
            InstallState.Verifying -> if (update) DesktopStrings.dbUpdateVerifyingVersion else DesktopStrings.onboardingVerifyingVersion
            else -> if (update) DesktopStrings.dbUpdateOfflineTitle else DesktopStrings.installationOfflineTitle
        }
        val extracting = state as? InstallState.Extracting
        progressLine?.visibility = if (extracting != null) View.VISIBLE else View.GONE
        progressSpinner?.visibility = if (extracting == null) View.VISIBLE else View.GONE
        progressPercent?.visibility = if (extracting != null) View.VISIBLE else View.GONE
        progressBytes?.visibility = View.GONE
        if (extracting != null) {
            progressLine?.progress = (extracting.progress * PROGRESS_MAX).toInt()
            progressPercent?.text = "${(extracting.progress * 100).toInt()}%"
            val total = installer.inputBytes
            if (total > 0) {
                progressBytes?.visibility = View.VISIBLE
                progressBytes?.text = DesktopStrings.onboardingDownloadProgress.format(formatBytes((extracting.progress * total).toLong()), formatBytes(total))
            }
        }
        val item = installer.item
        progressItem?.visibility = if (item != null && state !is InstallState.Verifying) View.VISIBLE else View.GONE
        progressItem?.text = item?.let(Bidi::ltr)
    }

    /** The main app's install step: its illustration, the step, the progress and the item, the note, ביטול. */
    private fun buildProgress() {
        frame.removeAllViews()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = island(colors)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        body.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_unarchive)
            setColorFilter(colors.text, PorterDuff.Mode.SRC_IN)
        }, lp(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        progressTitle = label("", 17f, colors.text, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        body.addView(progressTitle, lp(MATCH, WRAP))
        progressSpinner = spinner(colors).also { body.addView(it, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL }) }
        progressLine = progressLine(colors).apply { max = PROGRESS_MAX }.also { body.addView(it, lp(MATCH, dp(4)).apply { topMargin = dp(4) }) }
        progressPercent = label("", 16f, colors.text, bold = true).apply { gravity = Gravity.CENTER }.also { body.addView(it, lp(MATCH, WRAP).apply { topMargin = dp(8) }) }
        progressBytes = label("", 14f, colors.secondary).apply { gravity = Gravity.CENTER }.also { body.addView(it, lp(MATCH, WRAP)) }
        // One line, cut at its end: Android 4.4's StaticLayout throws on a middle cut of a text of maxLines 1.
        progressItem = label("", 13f, colors.secondary).apply {
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }.also { body.addView(it, lp(MATCH, WRAP).apply { topMargin = dp(4) }) }
        // The main app's note on zstd, for what is unpacked (a library folder is moved or copied as it is).
        if (installer.compressed) {
            body.addView(label(DesktopStrings.onboardingExtractingDidYouKnow, 13f, colors.text).apply {
                setLineSpacing(0f, 1.15f)
                background = roundRect(colors.accentTint, dp(8).toFloat())
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }, lp(MATCH, WRAP).apply { topMargin = dp(14) })
        }
        body.addView(label(InstallTexts.BACKGROUND, 13f, colors.secondary).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = dp(10) })
        val cancel = button(DesktopStrings.settingsCancel) { askCancel() }
        body.addView(cancel, lp(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })
        frame.addView(ScrollView(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipToPadding = false
            addView(body)
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        // A keypad is on ביטול, the page's one control: it asks first, on its safe answer.
        if (!frame.isInTouchMode) cancel.requestFocus()
    }

    /**
     * ביטול throws away what the run has unpacked (hours on an old phone, for the full library): it asks first, with the
     * answer that goes on in a keypad's focus, so that a stray OK loses nothing. The main app has it in its notification
     * only, which a keypad phone may not reach.
     */
    private fun askCancel() {
        if (!installer.isBusy || cancelDialog != null) return
        cancelDialog = Dialogs.show(
            this,
            colors,
            InstallTexts.CANCEL_TITLE,
            Dialogs.text(this, colors, InstallTexts.CANCEL_QUESTION),
            listOf(InstallTexts.CANCEL_YES to { if (installer.isBusy) installer.cancel() }, InstallTexts.CANCEL_NO to {}),
            focusButton = 1,
        ).apply { setOnDismissListener { cancelDialog = null } }
    }

    private fun dismissCancel() {
        cancelDialog?.dismiss()
        cancelDialog = null
    }

    private fun clearProgressViews() {
        progressTitle = null
        progressLine = null
        progressSpinner = null
        progressPercent = null
        progressBytes = null
        progressItem = null
    }

    // ------------------------------------------------------------------ the end

    private fun showResult(state: InstallState) {
        if (mode == Mode.Result && shownResult == state) return
        mode = Mode.Result
        shownResult = state
        bar.setSubtitle(null)
        clearProgressViews()
        frame.removeAllViews()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = island(colors)
            setPadding(dp(16), dp(20), dp(16), dp(16))
        }
        val failed = state as? InstallState.Failed
        val incompatible = failed?.error == InstallError.Incompatible
        val title: String
        val message: String
        when {
            failed == null && replacing -> {
                title = DesktopStrings.dbUpdateSuccessTitle
                message = DesktopStrings.dbUpdateSuccessMessage
            }
            failed == null -> {
                title = DesktopStrings.onboardingReady
                message = DesktopStrings.onboardingFinishReadyMessage
            }
            incompatible && replacing -> {
                title = DesktopStrings.dbUpdateVersionErrorTitle
                message = DesktopStrings.dbUpdateVersionErrorMessage
            }
            incompatible -> {
                title = DesktopStrings.onboardingVersionErrorTitle
                message = DesktopStrings.onboardingVersionErrorMessage
            }
            else -> {
                title = if (replacing) DesktopStrings.dbUpdateExtractionError else DesktopStrings.onboardingErrorOccurred
                message = installErrorMessage(failed)
            }
        }
        body.addView(ImageView(this).apply {
            setImageResource(if (failed == null) R.drawable.ic_check_circle else R.drawable.ic_info)
            setColorFilter(if (failed == null) colors.accent else colors.error, PorterDuff.Mode.SRC_IN)
        }, lp(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        body.addView(label(title, 18f, colors.text, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(6))
        }, lp(MATCH, WRAP))
        body.addView(label(message, 15f, colors.text).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }, lp(MATCH, WRAP))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        val first: View = when {
            failed == null -> button(DesktopStrings.onboardingOpenApp) { openApp() }
            incompatible -> button(DesktopStrings.onboardingRestartInstallation) { backToFiles() }
            else -> button(DesktopStrings.retryButton) { installer.retry() }.also {
                buttons.addView(it)
                buttons.addView(button(DesktopStrings.dbUpdateBack) { backToFiles() })
            }
        }
        if (first.parent == null) buttons.addView(first)
        body.addView(buttons, lp(MATCH, WRAP))
        frame.addView(ScrollView(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipToPadding = false
            addView(body)
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        if (!frame.isInTouchMode) first.requestFocus()
    }

    /** The Home on the new library (it opened when the install ended). */
    private fun openApp() {
        installer.reset()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_RELOAD, true))
        finish()
    }

    /** From an error back to the files, to pick again. */
    private fun backToFiles() {
        installer.reset()
        if (installer.state.value is InstallState.Idle) showBrowser()
    }

    private fun button(text: String, onClick: () -> Unit): TextView = label(text, 15f, colors.accent, bold = true).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        minWidth = dp(88)
        setPadding(dp(14), 0, dp(14), 0)
        background = states(colors, 0, dp(8).toFloat())
        keyable(this) { onClick() }
    }

    private fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    /** The browser's rows: notes and headers between them, which a keypad passes over. */
    private inner class RowsAdapter : BaseAdapter() {
        var items: List<Row> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Row = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 3

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is Row.Header -> 0
            is Row.Note -> 1
            is Row.Item -> 2
        }

        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean = items[position] is Row.Item

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val row = items[position]) {
            is Row.Header -> ((convertView as? TextView) ?: label("", 14f, colors.text, bold = true).apply { setPadding(dp(10), dp(12), dp(10), dp(4)) })
                .apply { text = row.text }
            is Row.Note -> ((convertView as? TextView) ?: label("", 14f, colors.secondary).apply {
                setLineSpacing(0f, 1.15f)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }).apply { text = row.text }
            is Row.Item -> ((convertView as? TreeRow) ?: TreeRow(this@InstallActivity, colors))
                .bind(row.title, iconRes = row.icon, secondLine = row.detail, iconTint = colors.accent)
        }
    }

    companion object {
        /** Settings › ספרייה: the install replaces a library (the main app's database update wording). */
        const val EXTRA_REPLACE = "replace"
        private const val STATE_FOLDER = "folder"
        private const val REQUEST_PICK = 1
        private const val REQUEST_STORAGE = 2
        private const val PROGRESS_MAX = 1000

        fun start(context: Context, replacing: Boolean) {
            context.startActivity(Intent(context, InstallActivity::class.java).putExtra(EXTRA_REPLACE, replacing))
        }
    }
}

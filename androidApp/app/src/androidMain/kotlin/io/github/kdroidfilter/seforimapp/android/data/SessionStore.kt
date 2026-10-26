package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.TabState
import io.github.kdroidfilter.seforimapp.android.ui.Workspace
import io.github.kdroidfilter.seforimapp.android.ui.WorkspaceSnapshot
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The navigation session: every workspace with its tabs (titles, pins), the selected ones, and each tab's reading
 * position. The Android subset of desktop 0.6.10 framework/session/SessionModels.kt `DesktopsState`
 * (desktops, activeDesktopId, snapshots, tabStates).
 */
@Serializable
data class SavedSession(
    val version: Int = 1,
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspaceId: String = "",
    val snapshots: Map<String, WorkspaceSnapshot> = emptyMap(),
    val tabStates: Map<String, TabState> = emptyMap(),
)

/**
 * Reads and writes the session file, `files/session/session_v1.json` (desktop SessionManager writes
 * `<databases>/session/desktops_v1.pb`). The file is written only while "שמור מצב בין הפעלות" is on and read at start
 * only then; an unreadable file is dropped, as desktop does.
 */
object SessionStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    fun encode(session: SavedSession): String = json.encodeToString(SavedSession.serializer(), session)

    fun decode(text: String): SavedSession = json.decodeFromString(SavedSession.serializer(), text)

    private fun file(context: Context) = File(context.filesDir, "session/session_v1.json")

    /** Blocking: call off the main thread. */
    fun load(context: Context): SavedSession? = synchronized(lock) {
        val f = file(context)
        if (!f.isFile) return null
        runCatching { decode(f.readText()) }.getOrElse {
            f.delete()
            null
        }
    }

    /** Blocking: call off the main thread. Written next to the file, then renamed, so a kill never leaves half a file. */
    fun write(context: Context, text: String) = synchronized(lock) {
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    fun delete(context: Context) = synchronized(lock) { runCatching { file(context).delete() } }

    /** Book tabs saved without a title are named from the library before they are shown (desktop enrichMissingTabTitles). */
    suspend fun enrichTitles(session: SavedSession, repository: SeforimRepository): SavedSession {
        val titles = HashMap<Long, String?>()
        suspend fun titleOf(bookId: Long) = titles.getOrPut(bookId) { runCatching { repository.getBookCore(bookId)?.title }.getOrNull() }
        val snapshots = session.snapshots.mapValues { (_, snap) ->
            snap.copy(
                tabs = snap.tabs.map { tab ->
                    val book = tab.destination as? Destination.Book
                    if (book == null || (tab.title.isNotBlank() && book.title.isNotBlank())) {
                        tab
                    } else {
                        val title = titleOf(book.bookId) ?: return@map tab
                        tab.copy(title = tab.title.ifBlank { title }, destination = book.copy(title = book.title.ifBlank { title }))
                    }
                },
            )
        }
        return session.copy(snapshots = snapshots)
    }
}

package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.updater.UpdaterConfig
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings › ניהול נתונים: sending feedback ([FeedbackSendCard]). What there is to send since the last package
 * ([FeedbackSource]; the settings keep up to when each kind went in one), what the user keeps of it ([FeedbackChoice]),
 * and the package ([feedbackPackage]) saved to a file in a folder the user picks. Each kind the package held is sent up
 * to the newest time it read, those the user left out too, so the next package holds only what is newer.
 */
@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class FeedbackSendViewModel(
    private val appSettings: AppSettings,
    database: UserSettingsDb,
) : ViewModel() {
    sealed interface Work {
        data object Idle : Work

        data object Saving : Work

        data class Saved(
            val file: File,
        ) : Work

        data class Failed(
            val problem: Throwable,
        ) : Work
    }

    data class State(
        /** What there is to send, null until read. */
        val contents: FeedbackContents? = null,
        val choice: FeedbackChoice = FeedbackChoice(),
        /** Everything there is, not only what is newer than the last package. */
        val all: Boolean = false,
        /** When the last package was made, epoch millis, 0 for never. */
        val lastSent: Long = 0,
        val sender: String = "",
        val work: Work = Work.Idle,
    ) {
        val busy: Boolean get() = work == Work.Saving
    }

    private val source = FeedbackSource(database) { FeedbackSource.dafFolderOf(LibraryLocation.currentDir(appSettings)) }
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var reading: Job? = null

    /** Reads what there is to send, each time the card is shown: the user may have graded, searched or marked since. */
    fun refresh(): Job {
        val all = _state.value.all
        reading?.cancel()
        return viewModelScope
            .launch(Dispatchers.IO) {
                val contents =
                    runSuspendCatching { source.read(if (all) FeedbackSent() else sent()) }.getOrElse {
                        warnln(it) { "[FeedbackSendViewModel] Feedback not read: ${it.message}" }
                        FeedbackContents()
                    }
                val sender = appSettings.getFeedbackSender()
                val lastSent = appSettings.getFeedbackSentAt(LAST)
                // A read the user's next change made stale
                ensureActive()
                _state.update { it.copy(contents = contents, sender = sender, lastSent = lastSent) }
            }.also { reading = it }
    }

    fun setGrades(on: Boolean) = choose { it.copy(grades = on) }

    fun setSearches(on: Boolean) = choose { it.copy(searches = on) }

    fun setDaf(on: Boolean) = choose { it.copy(daf = on) }

    /** [query] in the package or left out of it, its grades and its searches both. */
    fun setQuery(
        query: String,
        kept: Boolean,
    ) = choose { it.copy(leftOut = if (kept) it.leftOut - query else it.leftOut + query) }

    fun setAll(on: Boolean) {
        _state.update { it.copy(all = on, contents = null) }
        refresh()
    }

    /** A new sender id for the packages from now on: they cannot be told to be from the one who sent the earlier ones. */
    fun renewSender() {
        val sender = appSettings.renewFeedbackSender()
        _state.update { it.copy(sender = sender) }
    }

    /** Saves the package in [folder] (pardes-feedback-<sender>-<time>.json), and the kinds it held as sent. */
    fun saveTo(folder: File) {
        val state = _state.value
        val contents = state.contents ?: return
        if (state.busy || contents.kept(state.choice).isEmpty) return
        _state.update { it.copy(work = Work.Saving) }
        viewModelScope.launch(Dispatchers.IO) {
            runSuspendCatching {
                val now = Instant.now()
                val sender = appSettings.getFeedbackSender()
                val library = LibraryLocation.version(LibraryLocation.currentDir(appSettings))
                val made = feedbackPackage(contents, state.choice, sender, appVersion(), library, now)
                val file = File(folder, fileName(sender, now))
                write(file, made.toJson())
                markSent(contents, state.choice, now.toEpochMilli())
                file
            }.onSuccess { file ->
                // What is newer than the package, as the package left it
                _state.update { it.copy(work = Work.Saved(file), all = false, contents = null) }
                refresh()
            }.onFailure { problem ->
                warnln(problem) { "[FeedbackSendViewModel] Feedback not saved: ${problem.message}" }
                _state.update { it.copy(work = Work.Failed(problem)) }
            }
        }
    }

    fun dismiss() = _state.update { it.copy(work = Work.Idle) }

    private fun choose(change: (FeedbackChoice) -> FeedbackChoice) = _state.update { it.copy(choice = change(it.choice)) }

    private fun sent(): FeedbackSent =
        FeedbackSent(
            grades = appSettings.getFeedbackSentAt(GRADES),
            searches = appSettings.getFeedbackSentAt(SEARCHES),
            daf = appSettings.getFeedbackSentAt(DAF),
        )

    /** The kinds [choice] sent, up to the newest of [contents] (what the user left out of them is not offered again). */
    private fun markSent(
        contents: FeedbackContents,
        choice: FeedbackChoice,
        now: Long,
    ) {
        val newest = contents.newest()
        if (choice.grades) advance(GRADES, newest.grades)
        if (choice.searches) advance(SEARCHES, newest.searches)
        if (choice.daf) advance(DAF, newest.daf)
        appSettings.setFeedbackSentAt(LAST, now)
        appSettings.flush()
    }

    private fun advance(
        kind: String,
        to: Long,
    ) {
        if (to > appSettings.getFeedbackSentAt(kind)) appSettings.setFeedbackSentAt(kind, to)
    }

    private fun appVersion(): String? = runCatching { UpdaterConfig().currentVersion }.getOrNull()?.ifBlank { null }

    companion object {
        /** The kinds, as the settings keep up to when each was sent. */
        const val GRADES = "grades"
        const val SEARCHES = "searches"
        const val DAF = "daf"

        /** When the last package was made. */
        const val LAST = "package"

        private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

        /** A package's file: the sender's id begins it, so that packages from many senders keep apart in one folder. */
        fun fileName(
            sender: String,
            at: Instant,
        ): String = "pardes-feedback-${sender.take(8)}-${stamp.format(at)}.json"

        /** [text] in [file], whole or not at all: written beside it, then put in its place. */
        internal fun write(
            file: File,
            text: String,
        ) {
            val part = File(file.parentFile, file.name + ".part")
            try {
                part.writeText(text)
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                part.delete()
            }
        }
    }
}

package io.github.kdroidfilter.seforimapp.features.settings.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings › ניהול נתונים: sending feedback ([FeedbackSendCard]). What there is to send since the last package
 * ([FeedbackSender.read]), what the user keeps of it (the kinds, kept in the settings for the automatic sending too, and
 * the queries left out of this package), and the package sent to the service, saved to a file, or what was sent
 * deleted. The automatic sending ([FeedbackAutoSender]) is turned on or off here as well, and its sendings show here.
 */
@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class FeedbackSendViewModel(
    private val appSettings: AppSettings,
    private val sender: FeedbackSender,
    private val autoSender: FeedbackAutoSender,
) : ViewModel() {
    sealed interface Work {
        data object Idle : Work

        data object Saving : Work

        data class Saved(
            val file: File,
        ) : Work

        data class Sending(
            val part: Int,
            val parts: Int,
        ) : Work

        data class Sent(
            val packages: Int,
            val at: Long,
        ) : Work

        data object Deleting : Work

        data class Deleted(
            val packages: Int,
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
        /** Whether this build has the service: sending, the automatic sending, and deleting what was sent. */
        val online: Boolean = false,
        /** Whether feedback is sent by itself, at every start and connection of the network. */
        val auto: Boolean = false,
        /** The other apps on this computer whose searches may be sent too. */
        val otherApps: Set<OtherApp> = emptySet(),
    ) {
        val busy: Boolean get() = work == Work.Saving || work is Work.Sending || work == Work.Deleting
    }

    private val _state =
        MutableStateFlow(
            State(choice = sender.choice(), online = sender.service.available, auto = appSettings.isFeedbackAutoSend()),
        )
    val state: StateFlow<State> = _state.asStateFlow()
    private var reading: Job? = null

    init {
        // A sending, the card's or the automatic one: its progress, and what is left to send after it
        viewModelScope.launch {
            sender.status.collect { status ->
                when (status) {
                    is FeedbackSender.Status.Sending -> _state.update { it.copy(work = Work.Sending(status.part, status.parts)) }
                    is FeedbackSender.Status.Sent -> {
                        _state.update { it.copy(work = Work.Sent(status.packages, status.at), all = false) }
                        refresh()
                    }
                    is FeedbackSender.Status.Failed -> _state.update { it.copy(work = Work.Failed(status.problem)) }
                    FeedbackSender.Status.Idle -> Unit
                }
            }
        }
    }

    /** Reads what there is to send, each time the card is shown: the user may have graded, searched or marked since. */
    fun refresh(): Job {
        val all = _state.value.all
        reading?.cancel()
        return viewModelScope
            .launch {
                val contents =
                    runSuspendCatching { sender.read(all) }.getOrElse {
                        warnln(it) { "[FeedbackSendViewModel] Feedback not read: ${it.message}" }
                        FeedbackContents()
                    }
                // A read the user's next change made stale
                ensureActive()
                val otherApps = runSuspendCatching { withContext(Dispatchers.IO) { sender.otherApps() } }.getOrDefault(emptySet())
                _state.update {
                    it.copy(
                        contents = contents,
                        sender = sender.sender(),
                        lastSent = sender.lastSent(),
                        auto = appSettings.isFeedbackAutoSend(),
                        otherApps = otherApps,
                    )
                }
            }.also { reading = it }
    }

    fun setGrades(on: Boolean) = keep { it.copy(grades = on) }

    fun setSearches(on: Boolean) = keep { it.copy(searches = on) }

    fun setDaf(on: Boolean) = keep { it.copy(daf = on) }

    /** [app]'s searches sent too, or not: its files are read from now on, or no longer. */
    fun setOtherApp(
        app: OtherApp,
        on: Boolean,
    ) {
        keep { it.copy(others = if (on) it.others + app else it.others - app) }
        refresh()
    }

    /** Whether the service may show what is sent to everyone. */
    fun setPublish(on: Boolean) = keep { it.copy(publish = on) }

    /** [query] in this package or left out of it, its grades and its searches both. */
    fun setQuery(
        query: String,
        kept: Boolean,
    ) = _state.update { it.copy(choice = it.choice.copy(leftOut = if (kept) it.choice.leftOut - query else it.choice.leftOut + query)) }

    fun setAll(on: Boolean) {
        _state.update { it.copy(all = on, contents = null) }
        refresh()
    }

    /** The automatic sending on, sending what is new now, or off. The user has answered its question, either way. */
    fun setAuto(on: Boolean) {
        appSettings.setFeedbackAutoSend(on)
        appSettings.setFeedbackAsked(true)
        _state.update { it.copy(auto = on) }
        if (on) autoSender.kick()
    }

    /** A new sender id for the packages from now on: they cannot be told to be from the one who sent the earlier ones. */
    fun renewSender() {
        val sender = appSettings.renewFeedbackSender()
        _state.update { it.copy(sender = sender) }
    }

    /** Sends the package to the service; its progress and its end come through [FeedbackSender.status]. */
    fun send() {
        val state = _state.value
        val contents = state.contents ?: return
        if (state.busy || contents.kept(state.choice).isEmpty) return
        viewModelScope.launch {
            runSuspendCatching { sender.send(contents, state.choice) }
                .onFailure { warnln(it) { "[FeedbackSendViewModel] Feedback not sent: ${it.message}" } }
        }
    }

    /** Saves the package in [folder] (pardes-feedback-<sender>-<time>.json), and the kinds it held as sent. */
    fun saveTo(folder: File) {
        val state = _state.value
        val contents = state.contents ?: return
        if (state.busy || contents.kept(state.choice).isEmpty) return
        _state.update { it.copy(work = Work.Saving) }
        viewModelScope.launch {
            runSuspendCatching { sender.save(contents, state.choice, folder) }
                .onSuccess { file ->
                    // What is newer than the package, as the package left it
                    _state.update { it.copy(work = Work.Saved(file), all = false, contents = null) }
                    refresh()
                }.onFailure { problem ->
                    warnln(problem) { "[FeedbackSendViewModel] Feedback not saved: ${problem.message}" }
                    _state.update { it.copy(work = Work.Failed(problem)) }
                }
        }
    }

    /** Deletes from the service everything this sender sent. */
    fun deleteSent() {
        if (_state.value.busy || !_state.value.online) return
        _state.update { it.copy(work = Work.Deleting) }
        viewModelScope.launch {
            runSuspendCatching { sender.deleteSent() }
                .onSuccess { packages -> _state.update { it.copy(work = Work.Deleted(packages)) } }
                .onFailure { problem ->
                    warnln(problem) { "[FeedbackSendViewModel] Sent feedback not deleted: ${problem.message}" }
                    _state.update { it.copy(work = Work.Failed(problem)) }
                }
        }
    }

    fun dismiss() = _state.update { it.copy(work = Work.Idle) }

    // The kinds sent, kept for the automatic sending too
    private fun keep(change: (FeedbackChoice) -> FeedbackChoice) {
        val choice = change(_state.value.choice)
        sender.keep(choice)
        _state.update { it.copy(choice = choice) }
    }
}

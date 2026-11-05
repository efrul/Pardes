package io.github.kdroidfilter.seforimapp.features.settings.feedback

import dev.nucleusframework.updater.UpdaterConfig
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sending feedback, for the card ([FeedbackSendViewModel]) and the automatic sending ([FeedbackAutoSender]): what there
 * is since the last package ([FeedbackSource]), the kinds the user sends (kept in the settings), and the package sent
 * to the service in parts ([FeedbackService], [parts]) or saved to a file. Each kind a package held is then sent up to
 * the newest time it read, those the user left out too, so the next package holds only what is newer. One at a time.
 */
@Inject
@SingleIn(AppScope::class)
class FeedbackSender(
    private val appSettings: AppSettings,
    database: UserSettingsDb,
    val service: FeedbackService,
) {
    sealed interface Status {
        data object Idle : Status

        data class Sending(
            val part: Int,
            val parts: Int,
        ) : Status

        /** Sent at [at] (epoch millis), in [packages] parts. */
        data class Sent(
            val packages: Int,
            val at: Long,
        ) : Status

        data class Failed(
            val problem: Throwable,
        ) : Status
    }

    private val source = FeedbackSource(database) { FeedbackSource.dafFolderOf(LibraryLocation.currentDir(appSettings)) }
    private val mutex = Mutex()
    private val _status = MutableStateFlow<Status>(Status.Idle)

    /** The last sending's, by the card or by itself. */
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The kinds the user sends, as the settings keep them: all three, at first. */
    fun choice(): FeedbackChoice =
        FeedbackChoice(
            grades = appSettings.isFeedbackKindSent(GRADES, true),
            searches = appSettings.isFeedbackKindSent(SEARCHES, true),
            daf = appSettings.isFeedbackKindSent(DAF, true),
        )

    /** The kinds of [choice] kept as the ones the user sends. */
    fun keep(choice: FeedbackChoice) {
        appSettings.setFeedbackKindSent(GRADES, choice.grades)
        appSettings.setFeedbackKindSent(SEARCHES, choice.searches)
        appSettings.setFeedbackKindSent(DAF, choice.daf)
    }

    /** When the last package was sent or saved, epoch millis, 0 for never. */
    fun lastSent(): Long = appSettings.getFeedbackSentAt(LAST)

    fun sender(): String = appSettings.getFeedbackSender()

    /** What there is to send: what is newer than the last package, or [all] of it. */
    suspend fun read(all: Boolean = false): FeedbackContents =
        withContext(Dispatchers.IO) {
            source.read(if (all) FeedbackSent() else sent())
        }

    /**
     * Sends [contents] as [choice] keeps them, in parts, and the kinds it held as sent; how many parts, 0 when there
     * was nothing to send. Throws what stopped it (an IOException without a network, a [FeedbackServiceProblem]).
     */
    suspend fun send(
        contents: FeedbackContents,
        choice: FeedbackChoice,
    ): Int =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (contents.kept(choice).isEmpty) return@withContext 0
                val now = Instant.now()
                val parts = made(contents, choice, now).parts()
                try {
                    parts.forEachIndexed { index, part ->
                        _status.value = Status.Sending(index + 1, parts.size)
                        service.send(part.toJson())
                    }
                } catch (e: CancellationException) {
                    _status.value = Status.Idle
                    throw e
                } catch (e: Exception) {
                    _status.value = Status.Failed(e)
                    throw e
                }
                markSent(contents, choice, now.toEpochMilli())
                _status.value = Status.Sent(parts.size, now.toEpochMilli())
                parts.size
            }
        }

    /** What is new of the kinds the user sends, sent: the automatic sending's. */
    suspend fun sendNew(): Int {
        val choice = choice()
        return send(read(), choice)
    }

    /** Saves [contents] as [choice] keeps them in [folder] (pardes-feedback-<sender>-<time>.json); the kinds as sent. */
    suspend fun save(
        contents: FeedbackContents,
        choice: FeedbackChoice,
        folder: File,
    ): File =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val now = Instant.now()
                val file = File(folder, fileName(sender(), now))
                write(file, made(contents, choice, now).toJson())
                markSent(contents, choice, now.toEpochMilli())
                file
            }
        }

    /** Deletes from the service everything this sender sent; how many packages there were. */
    suspend fun deleteSent(): Int = withContext(Dispatchers.IO) { service.delete(sender()) }

    private fun made(
        contents: FeedbackContents,
        choice: FeedbackChoice,
        now: Instant,
    ): FeedbackPackage {
        val library = LibraryLocation.version(LibraryLocation.currentDir(appSettings))
        return feedbackPackage(contents, choice, sender(), appVersion(), library, now)
    }

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
        /** The kinds, as the settings keep whether each is sent and up to when it was. */
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

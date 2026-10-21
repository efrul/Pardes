package io.github.kdroidfilter.seforimapp.features.database.move

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.core.runtime.AppRestarter.restartApplication
import io.github.kdroidfilter.seforimapp.core.presentation.components.InstallerWindow
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadProgressDetails
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.features.settings.library.problemText
import io.github.kdroidfilter.seforimapp.framework.database.ForeignLibraries
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.database.PendingLibraryMove
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Library
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.library_move_copying
import seforimapp.seforimapp.generated.resources.library_move_done
import seforimapp.seforimapp.generated.resources.library_move_done_copy
import seforimapp.seforimapp.generated.resources.library_move_from
import seforimapp.seforimapp.generated.resources.library_move_interrupted
import seforimapp.seforimapp.generated.resources.library_move_moving
import seforimapp.seforimapp.generated.resources.library_move_not_deleted
import seforimapp.seforimapp.generated.resources.library_move_not_done
import seforimapp.seforimapp.generated.resources.library_move_open_app
import seforimapp.seforimapp.generated.resources.library_move_source_kept
import seforimapp.seforimapp.generated.resources.library_move_stop
import seforimapp.seforimapp.generated.resources.library_move_stopped
import seforimapp.seforimapp.generated.resources.library_move_title_bar
import seforimapp.seforimapp.generated.resources.library_move_to
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The window a start opens instead of the app when the user asked to move the library ([PendingLibraryMove]): it
 * moves it before anything opens it, shows the progress, and restarts the app on the result.
 */
@Composable
fun NucleusApplicationScope.LibraryMoveWindow(request: PendingLibraryMove.Request) {
    val appSettings = LocalAppGraph.current.appSettings
    val mover = remember(request) { LibraryMover(request, appSettings) }
    LaunchedEffect(mover) { withContext(Dispatchers.IO) { mover.run() } }
    InstallerWindow(
        titleBarIcon = Library,
        titleBarText = stringResource(Res.string.library_move_title_bar),
        progress = mover.fraction,
    ) {
        val state by mover.state.collectAsState()
        LibraryMoveView(request, state, onStop = mover::stop, onOpenApp = { restartApplication() })
    }
}

@Composable
private fun LibraryMoveView(
    request: PendingLibraryMove.Request,
    state: LibraryMover.State,
    onStop: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val title =
        when (val outcome = state.outcome) {
            null -> if (state.copying) Res.string.library_move_copying else Res.string.library_move_moving
            is LibraryMover.Outcome.Done -> if (outcome.keptCopy) Res.string.library_move_done_copy else Res.string.library_move_done
            else -> Res.string.library_move_not_done
        }
    OnBoardingScaffold(
        title = stringResource(title),
        bottomAction = {
            if (state.outcome == null) {
                OutlinedButton(onClick = onStop, enabled = !state.stopping) { Text(stringResource(Res.string.library_move_stop)) }
            } else {
                DefaultButton(onClick = onOpenApp) { Text(stringResource(Res.string.library_move_open_app)) }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val outcome = state.outcome) {
                null -> Unit
                is LibraryMover.Outcome.Done -> {
                    if (outcome.keptCopy) Text(stringResource(Res.string.library_move_source_kept))
                    if (outcome.notDeleted.isNotEmpty()) {
                        InlineWarningBanner(
                            text =
                                stringResource(
                                    Res.string.library_move_not_deleted,
                                    outcome.notDeleted.joinToString { isolatedPath(it) },
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is LibraryMover.Outcome.Failed ->
                    InlineErrorBanner(text = problemText(outcome.problem), modifier = Modifier.fillMaxWidth())
                LibraryMover.Outcome.Stopped ->
                    InlineWarningBanner(text = stringResource(Res.string.library_move_stopped), modifier = Modifier.fillMaxWidth())
                LibraryMover.Outcome.Interrupted ->
                    InlineWarningBanner(text = stringResource(Res.string.library_move_interrupted), modifier = Modifier.fillMaxWidth())
            }
            Icon(Library, null, modifier = Modifier.size(128.dp), tint = JewelTheme.globalColors.text.normal)
            PathLine(stringResource(Res.string.library_move_from, isolatedPath(request.from.path)))
            PathLine(stringResource(Res.string.library_move_to, isolatedPath(request.to.path)))
            if (state.outcome == null && state.total > 0) {
                DownloadProgressDetails(
                    downloadedBytes = state.done,
                    totalBytes = state.total,
                    speedBytesPerSec = state.bytesPerSecond,
                )
            }
        }
    }
}

@Composable
private fun PathLine(text: String) {
    Text(text = text, textAlign = TextAlign.Center)
}

/** Runs one [PendingLibraryMove.Request] off the UI thread and reports it. */
private class LibraryMover(
    private val request: PendingLibraryMove.Request,
    private val appSettings: AppSettings,
) {
    sealed interface Outcome {
        data class Done(
            val to: String,
            val keptCopy: Boolean,
            val notDeleted: List<String>,
        ) : Outcome

        data class Failed(
            val problem: LibraryLocation.Problem,
        ) : Outcome

        /** The user stopped it. */
        data object Stopped : Outcome

        /** The app was killed in the previous start's move, which was undone. */
        data object Interrupted : Outcome
    }

    data class State(
        val done: Long = 0L,
        val total: Long = 0L,
        val bytesPerSecond: Long = 0L,
        /** A copy (another drive, or the original kept) rather than a rename on the same drive. */
        val copying: Boolean = true,
        val stopping: Boolean = false,
        val outcome: Outcome? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _fraction = MutableStateFlow(0f)
    val fraction: StateFlow<Float> = _fraction.asStateFlow()

    private val stopped = AtomicBoolean(false)
    private val finished = CountDownLatch(1)

    // Closing the window exits the app: the copy is stopped and its partial files removed before the JVM goes
    private val onExit =
        Thread({
            stopped.set(true)
            finished.await(EXIT_WAIT_SECONDS, TimeUnit.SECONDS)
        }, "library-move-exit")

    private var lastTime = 0L
    private var lastDone = 0L

    fun stop() {
        stopped.set(true)
        _state.value = _state.value.copy(stopping = true)
    }

    fun run() {
        if (finished.count == 0L || _state.value.outcome != null) return
        runCatching { Runtime.getRuntime().addShutdownHook(onExit) }
        try {
            _state.value = _state.value.copy(outcome = if (request.started) undo() else move())
        } finally {
            PendingLibraryMove.clear()
            _fraction.value = 0f
            finished.countDown()
            runCatching { Runtime.getRuntime().removeShutdownHook(onExit) }
        }
    }

    private fun undo(): Outcome =
        if (LibraryLocation.undo(request.from, request.to, request.keep, appSettings)) {
            Outcome.Done(request.to.path, request.keep, emptyList())
        } else {
            Outcome.Interrupted
        }

    private fun move(): Outcome {
        PendingLibraryMove.markStarted(request)
        val keep = request.keep || ForeignLibraries.isForeign(request.from)
        _state.value = _state.value.copy(copying = keep || !LibraryLocation.sameDrive(request.from, request.to))
        lastTime = System.nanoTime()
        return try {
            val result =
                LibraryLocation.move(
                    from = request.from,
                    to = request.to,
                    keep = request.keep,
                    appSettings = appSettings,
                    progress = ::onProgress,
                    stopped = stopped::get,
                )
            Outcome.Done(result.to.path, result.keptCopy, result.notDeleted)
        } catch (_: LibraryLocation.MoveStopped) {
            Outcome.Stopped
        } catch (e: LibraryLocation.MoveException) {
            Outcome.Failed(e.problem)
        } catch (e: Exception) {
            warnln { "[LibraryMoveWindow] Move failed: ${e.message}" }
            Outcome.Failed(LibraryLocation.Problem.Failed(e.message ?: e.javaClass.simpleName))
        }
    }

    // Called every few MB: the speed is measured over half a second at least
    private fun onProgress(
        done: Long,
        total: Long,
    ) {
        val now = System.nanoTime()
        val elapsed = now - lastTime
        if (elapsed < SPEED_WINDOW_NANOS && done < total) return
        val speed = if (elapsed > 0) (done - lastDone) * NANOS_PER_SECOND / elapsed else 0L
        lastTime = now
        lastDone = done
        _fraction.value = if (total > 0) done.toFloat() / total else 1f
        _state.value = _state.value.copy(done = done, total = total, bytesPerSecond = speed)
    }

    private companion object {
        const val EXIT_WAIT_SECONDS = 30L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val SPEED_WINDOW_NANOS = 500_000_000L
    }
}

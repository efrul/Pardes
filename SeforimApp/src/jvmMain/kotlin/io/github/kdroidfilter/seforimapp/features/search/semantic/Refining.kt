package io.github.kdroidfilter.seforimapp.features.search.semantic

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * The refined snippets of a session's pages being worked out (the passages closest in meaning, a run of the model for
 * each batch of them), stopped when the session closes: the view that asked for them then shows another list, and would
 * throw them away. A page's work went on after it, and the pages of a search and of the tabs picked next held every
 * thread the searches run on (HybridSearchEngine.passagesByMeaning).
 */
internal class Refining {
    private val running: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var closed = false

    /**
     * [work]'s result, or [none] when the session closed before or closes meanwhile: [work] is then cancelled, and stops
     * at the model's next run. The caller goes on either way.
     */
    suspend fun <T> run(
        none: T,
        work: suspend () -> T,
    ): T =
        coroutineScope {
            if (closed) return@coroutineScope none
            val job = async { work() }
            running += job
            // Closed between the check and the registration
            if (closed) job.cancel()
            try {
                job.join()
                if (job.isCancelled) none else job.await()
            } finally {
                running -= job
            }
        }

    /** Stops the work running, and any asked for later. */
    fun close() {
        closed = true
        running.forEach { it.cancel() }
    }
}

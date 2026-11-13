package io.github.kdroidfilter.seforimapp.features.settings.downloads

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pardes's repository's release lists (the GitHub API's, and the site's copies of it) as read lately: the semantic packs
 * (SemanticPackReleases) and the daf view's layouts (DafReleases) read the same list, at start ([StartOffers]) and in
 * their settings' cards, and the API answers 60 calls an hour from an address. A list read less than [KEPT_MS] ago is not
 * read again; one that could not be read is.
 */
object ReleaseLists {
    private const val KEPT_MS = 10 * 60_000L

    private val mutex = Mutex()
    private val read = HashMap<String, Pair<Long, String>>()

    // The time in milliseconds, a test's own
    internal var clock: () -> Long = { System.nanoTime() / 1_000_000 }

    /** [url]'s text: as read less than [KEPT_MS] ago, else read with [fetch] (once, whoever else asks for it meanwhile). */
    suspend fun text(
        url: String,
        fetch: suspend (String) -> String,
    ): String =
        mutex.withLock {
            read[url]?.takeIf { (at, _) -> clock() - at < KEPT_MS }?.second
                ?: fetch(url).also { read[url] = clock() to it }
        }

    internal fun forget() = read.clear()
}

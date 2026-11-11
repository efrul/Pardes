package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import java.security.MessageDigest

/**
 * Which of another app's rows Pardes has found and sent, for an app that keeps no time for its searches (Otzaria's
 * history): "what is newer than the last package" cannot be asked of it, so each row is remembered by a hash of its
 * key ([OtzariaSearches.Keyed]), with when it was first found, which stands for the search's time. Kept in the user
 * database (other_app_feedback).
 */
class OtherAppLedger(
    database: UserSettingsDb,
) {
    /** A row as the ledger has it: when Pardes first found it, and whether it went in a package. */
    data class Entry(
        val foundAt: Long,
        val sent: Boolean,
    )

    private val queries = database.searchFeedbackQueries

    /**
     * [keys] of [app] as the ledger has them, those new to it found [now]. With [forgetOthers], the rows the app no
     * longer holds are forgotten: a search that left its history and came back was made again.
     */
    fun found(
        app: OtherApp,
        keys: Collection<String>,
        now: Long,
        forgetOthers: Boolean,
    ): Map<String, Entry> {
        val hashes = keys.associateWith(::hash)
        val seen = hashes.values.toSet()
        var entries = emptyMap<String, Entry>()
        queries.transaction {
            hashes.values.forEach { queries.otherAppFound(app.source, it, now) }
            val kept = queries.otherAppRows(app.source).executeAsList()
            if (forgetOthers) kept.filter { it.key !in seen }.forEach { queries.otherAppGone(app.source, it.key) }
            entries = kept.associate { it.key to Entry(it.foundAt, it.sent) }
        }
        return hashes.mapNotNull { (key, hashed) -> entries[hashed]?.let { key to it } }.toMap()
    }

    /** [keys] of [app] went in a package. */
    fun sent(
        app: OtherApp,
        keys: Collection<String>,
    ) = queries.transaction { keys.forEach { queries.otherAppSent(app.source, hash(it)) } }

    // A key holds the search's words, as long as a passage pasted in: its hash stands for it
    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
}

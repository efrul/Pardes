package io.github.kdroidfilter.seforimapp.android.search

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.store.NIOFSDirectory
import java.io.File

/**
 * Android variant of Pardes desktop's PardesLexicon (SeforimApp/.../framework/search/PardesLexicon.kt, docs/LEXICON.md):
 * lexical.db without its bases that have no forms (they hid the paradigm of ברכות, הלכות, לאכול, תפלין) and with each
 * base's forms numbered by how many lines of the text index hold them (the 32 the search keeps are the common ones),
 * built into [FILE] next to lexical.db and read by the search in its place. Same derivation and fingerprint as desktop;
 * the platform SQLite instead of JDBC, NIOFSDirectory for the index.
 *
 * Desktop's warm-up waits for the build; here the engine opens on the first search, so the build runs in the background
 * and [SearchService]'s library key holds [FILE]: the first search after it ends reopens the engine with it.
 */
object PardesLexicon {
    const val FILE = "pardes-lexical.db"

    /** Desktop's VERSION: bump both when the derivation changes. */
    private const val VERSION = 1
    private const val META = "pardes_meta"
    private const val TAG = "PardesLexicon"

    @Volatile
    private var building: Thread? = null

    /**
     * [FILE] when it is up to date with [lexical] and [indexDir], else [lexical], with [FILE] then rebuilt in the
     * background (a stale one deleted first, so the search never reads one of another library).
     */
    @Synchronized
    fun dictionaryFor(lexical: File, indexDir: File): File {
        val target = File(lexical.parentFile, FILE)
        if (!lexical.isFile) return lexical
        val fingerprint = runCatching { fingerprint(lexical, indexDir) }.getOrElse { return lexical }
        if (storedFingerprint(target) == fingerprint) return target
        if (building?.isAlive == true) return lexical
        target.delete()
        building = Thread({
            runCatching { build(lexical, indexDir, target, fingerprint) }
                .onFailure { Log.w(TAG, "could not build $target; the search keeps lexical.db", it) }
        }, "pardes-lexicon").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
        return lexical
    }

    /** lexical.db's size and time, and the index's commit, as desktop's fingerprint. */
    private fun fingerprint(lexical: File, indexDir: File): String {
        val segments = indexDir.list()?.filter { it.startsWith("segments_") }?.sorted().orEmpty()
        check(segments.isNotEmpty()) { "no index at $indexDir" }
        return "v$VERSION|${lexical.length()}|${lexical.lastModified()}|${segments.joinToString(",")}"
    }

    private fun storedFingerprint(target: File): String? {
        if (!target.isFile) return null
        return runCatching {
            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                db.rawQuery("SELECT value FROM $META WHERE key = 'fingerprint'", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
        }.getOrNull()
    }

    private fun build(lexical: File, indexDir: File, target: File, fingerprint: String) {
        val started = System.nanoTime()
        val tmp = File(target.parentFile, "$FILE.tmp")
        tmp.delete()
        File(tmp.path + "-journal").delete()
        try {
            val db = SQLiteDatabase.openDatabase(
                tmp.path,
                null,
                SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            db.use {
                db.execSQL("ATTACH DATABASE ? AS src", arrayOf(lexical.path))
                val hasExtra = db.rawQuery(
                    "SELECT 1 FROM src.sqlite_master WHERE type = 'table' AND name = 'surface_extra_base'",
                    null,
                ).use { it.moveToFirst() }
                db.beginTransaction()
                try {
                    SCHEMA.forEach(db::execSQL)
                    // Surface ids, each base's surfaces by their lines in the index
                    val surfaces = ArrayList<LongArray>() // id, base id, document frequency
                    DirectoryReader.open(NIOFSDirectory(indexDir.toPath())).use { reader ->
                        db.rawQuery("SELECT id, base_id, value FROM src.surface", null).use { c ->
                            while (c.moveToNext()) {
                                val term = HebrewTextUtils.normalizeHebrew(c.getString(2) ?: "")
                                val df = if (term.isEmpty() || term.contains(' ')) 0 else reader.docFreq(Term("text", term))
                                surfaces += longArrayOf(c.getLong(0), c.getLong(1), df.toLong())
                            }
                        }
                    }
                    surfaces.sortWith(compareBy<LongArray> { it[1] }.thenByDescending { it[2] }.thenBy { it[0] })
                    db.execSQL("CREATE TEMP TABLE smap (old INTEGER PRIMARY KEY, new INTEGER NOT NULL)")
                    db.compileStatement("INSERT INTO smap VALUES (?, ?)").use { st ->
                        surfaces.forEachIndexed { i, s ->
                            st.bindLong(1, s[0])
                            st.bindLong(2, i + 1L)
                            st.executeInsert()
                        }
                    }
                    val extraBases = if (hasExtra) " OR EXISTS (SELECT 1 FROM src.surface_extra_base e WHERE e.base_id = b.id)" else ""
                    db.execSQL(
                        "INSERT INTO base SELECT b.id, b.value FROM src.base b " +
                            "WHERE EXISTS (SELECT 1 FROM src.surface s WHERE s.base_id = b.id)$extraBases",
                    )
                    db.execSQL("INSERT INTO surface SELECT m.new, s.value, s.base_id, NULL FROM src.surface s JOIN smap m ON m.old = s.id ORDER BY m.new")
                    db.execSQL("INSERT INTO variant SELECT id, value FROM src.variant")
                    db.execSQL(
                        "INSERT INTO surface_variant SELECT m.new, sv.variant_id FROM src.surface_variant sv " +
                            "JOIN smap m ON m.old = sv.surface_id ORDER BY m.new, sv.variant_id",
                    )
                    if (hasExtra) {
                        db.execSQL(
                            "INSERT INTO surface_extra_base SELECT m.new, e.base_id FROM src.surface_extra_base e " +
                                "JOIN smap m ON m.old = e.surface_id ORDER BY m.new, e.base_id",
                        )
                    }
                    INDEXES.forEach(db::execSQL)
                    db.execSQL("INSERT INTO $META VALUES ('fingerprint', ?)", arrayOf(fingerprint))
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                db.execSQL("DETACH DATABASE src")
                db.execSQL("ANALYZE")
            }
            check(tmp.renameTo(target)) { "could not move $tmp to $target" }
            Log.i(TAG, "built $target in ${(System.nanoTime() - started) / 1_000_000} ms")
        } finally {
            tmp.delete()
            File(tmp.path + "-journal").delete()
        }
    }

    // The tables and indexes MagicDictionaryIndex reads, as lexical.db declares them (desktop's SCHEMA and INDEXES)
    private val SCHEMA = listOf(
        "CREATE TABLE base (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE)",
        "CREATE TABLE surface (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE, " +
            "base_id INTEGER NOT NULL REFERENCES base(id), notes TEXT)",
        "CREATE TABLE variant (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE)",
        "CREATE TABLE surface_variant (surface_id INTEGER NOT NULL, variant_id INTEGER NOT NULL, PRIMARY KEY (surface_id, variant_id))",
        "CREATE TABLE surface_extra_base (surface_id INTEGER NOT NULL, base_id INTEGER NOT NULL, PRIMARY KEY (surface_id, base_id))",
        "CREATE TABLE $META (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
    )

    private val INDEXES = listOf(
        "CREATE INDEX surface_base_id_index ON surface(base_id)",
        "CREATE INDEX surface_variant_variant_id_index ON surface_variant(variant_id)",
        "CREATE INDEX surface_extra_base_base_id_index ON surface_extra_base(base_id)",
    )
}

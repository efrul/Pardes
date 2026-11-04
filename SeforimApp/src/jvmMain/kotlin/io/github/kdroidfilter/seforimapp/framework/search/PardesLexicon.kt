package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.store.FSDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager

/**
 * Pardes's copy of the library's word dictionary (lexical.db), which the regular search reads in its place
 * ([LuceneSearchEngine]'s `dictionaryPath`). It holds the same words and forms, with two fixes:
 *
 * 1. **No base without forms.** lexical.db (release 20261012193827) has 4,711 bases that no surface points to, and
 *    2,479 of them are spelled like a form of another base (ברכות, הלכות, לאכול, דברים, תפלין). MagicDictionaryIndex
 *    prefers the base spelled like the query word, so such a word got that empty base: no other form at all.
 * 2. **The commonest forms first.** The search keeps a word's first 32 forms, in the order of the surface ids. Here each
 *    base's surfaces are numbered by how many lines of the library's index hold them, so the 32 are the common ones
 *    (שלוש keeps שלש and שלשה, docs/SEARCH_ORDER_ISSUES.md #4). The variants still come after the surfaces.
 *
 * Measured on eval-100 and eval-real (lexical side, docs/LEXICON.md): source in the first ten 13 → 16, a good line in the
 * first three 42 → 47 of 153, about 10 ms more per search (real paradigms instead of nothing).
 *
 * Built from lexical.db and the text index when they change (a library install or update), in the background, into
 * [FILE] next to lexical.db; until it is there the search reads lexical.db itself.
 */
object PardesLexicon {
    const val FILE = "pardes-lexical.db"

    /** Bump when the derivation changes: older files are rebuilt. */
    private const val VERSION = 1
    private const val META = "pardes_meta"
    private const val BATCH = 5_000

    @Volatile
    private var building: Thread? = null

    /**
     * The dictionary the search should read: [FILE] when it is up to date with [lexical] and [indexDir], else
     * [lexical], with [FILE] then rebuilt in the background. A stale [FILE] is deleted first, so the search never
     * reads one of another library. The search opens its dictionary on its first query and keeps it: a rebuild that
     * ends after that serves from the next start.
     */
    fun dictionaryFor(
        lexical: Path,
        indexDir: Path,
    ): Path {
        val target = lexical.resolveSibling(FILE)
        if (!Files.isRegularFile(lexical)) return lexical
        val fingerprint = runCatching { fingerprint(lexical, indexDir) }.getOrElse { return lexical }
        if (storedFingerprint(target) == fingerprint) return target
        runCatching { Files.deleteIfExists(target) }
        building =
            Thread({
                runCatching { build(lexical, indexDir, target, fingerprint) }
                    .onFailure { warnln(it) { "[PardesLexicon] could not build $target; the search keeps lexical.db" } }
            }, "pardes-lexicon").apply {
                isDaemon = true
                start()
            }
        return target
    }

    /**
     * Waits (up to [timeoutMs]) for a rebuild [dictionaryFor] started: the search warm-up calls it before its first
     * query, which opens the dictionary for the whole run.
     */
    fun awaitBuild(timeoutMs: Long) {
        building?.join(timeoutMs)
    }

    /** lexical.db's size and time, and the index's commit: what the file is built from. */
    internal fun fingerprint(
        lexical: Path,
        indexDir: Path,
    ): String {
        val segments =
            Files.list(indexDir).use { s ->
                s
                    .map { it.fileName.toString() }
                    .filter { it.startsWith("segments_") }
                    .sorted()
                    .toList()
            }
        return "v$VERSION|${Files.size(lexical)}|${Files.getLastModifiedTime(lexical).toMillis()}|${segments.joinToString(",")}"
    }

    internal fun storedFingerprint(target: Path): String? {
        if (!Files.isRegularFile(target)) return null
        return runCatching {
            DriverManager.getConnection("jdbc:sqlite:${target.toAbsolutePath()}").use { c ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT value FROM $META WHERE key = 'fingerprint'").use { rs ->
                        if (rs.next()) rs.getString(1) else null
                    }
                }
            }
        }.getOrNull()
    }

    /** Writes the fixed copy of [lexical] to [target] (through a temporary file, moved in place when complete). */
    internal fun build(
        lexical: Path,
        indexDir: Path,
        target: Path,
        fingerprint: String,
    ) {
        val started = System.nanoTime()
        val tmp = target.resolveSibling("$FILE.tmp")
        Files.deleteIfExists(tmp)
        try {
            DriverManager.getConnection("jdbc:sqlite:${tmp.toAbsolutePath()}").use { c ->
                c.autoCommit = false
                c.exec("ATTACH DATABASE '${lexical.toAbsolutePath().toString().replace("'", "''")}' AS src")
                val hasExtra = c.hasTable("src", "surface_extra_base")
                c.exec(SCHEMA)
                // Surface ids, each base's surfaces by their lines in the index
                val surfaces = mutableListOf<LongArray>() // id, base id, document frequency
                DirectoryReader.open(FSDirectory.open(indexDir)).use { reader ->
                    c.createStatement().use { st ->
                        st.executeQuery("SELECT id, base_id, value FROM src.surface").use { rs ->
                            while (rs.next()) {
                                val term = HebrewTextUtils.normalizeHebrew(rs.getString(3) ?: "")
                                val df = if (term.isEmpty() || term.contains(' ')) 0 else reader.docFreq(Term("text", term))
                                surfaces += longArrayOf(rs.getLong(1), rs.getLong(2), df.toLong())
                            }
                        }
                    }
                }
                surfaces.sortWith(compareBy<LongArray> { it[1] }.thenByDescending { it[2] }.thenBy { it[0] })
                c.exec("CREATE TEMP TABLE smap (old INTEGER PRIMARY KEY, new INTEGER NOT NULL)")
                c.prepareStatement("INSERT INTO smap VALUES (?, ?)").use { ps ->
                    surfaces.forEachIndexed { i, s ->
                        ps.setLong(1, s[0])
                        ps.setLong(2, i + 1L)
                        ps.addBatch()
                        if (i % BATCH == 0) ps.executeBatch()
                    }
                    ps.executeBatch()
                }
                val extraBases = if (hasExtra) " OR EXISTS (SELECT 1 FROM src.surface_extra_base e WHERE e.base_id = b.id)" else ""
                c.exec(
                    "INSERT INTO base SELECT b.id, b.value FROM src.base b " +
                        "WHERE EXISTS (SELECT 1 FROM src.surface s WHERE s.base_id = b.id)$extraBases",
                )
                c.exec(
                    "INSERT INTO surface SELECT m.new, s.value, s.base_id, NULL FROM src.surface s JOIN smap m ON m.old = s.id ORDER BY m.new",
                )
                c.exec("INSERT INTO variant SELECT id, value FROM src.variant")
                c.exec(
                    "INSERT INTO surface_variant SELECT m.new, sv.variant_id FROM src.surface_variant sv " +
                        "JOIN smap m ON m.old = sv.surface_id ORDER BY m.new, sv.variant_id",
                )
                if (hasExtra) {
                    c.exec(
                        "INSERT INTO surface_extra_base SELECT m.new, e.base_id FROM src.surface_extra_base e " +
                            "JOIN smap m ON m.old = e.surface_id ORDER BY m.new, e.base_id",
                    )
                }
                c.exec(INDEXES)
                c.prepareStatement("INSERT INTO $META VALUES ('fingerprint', ?)").use {
                    it.setString(1, fingerprint)
                    it.executeUpdate()
                }
                c.commit()
                c.autoCommit = true
                c.exec("DETACH DATABASE src")
                c.exec("ANALYZE")
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            infoln { "[PardesLexicon] built $target in ${(System.nanoTime() - started) / 1_000_000} ms" }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun Connection.exec(sql: String) {
        createStatement().use { st ->
            sql
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { st.execute(it) }
        }
    }

    private fun Connection.hasTable(
        schema: String,
        name: String,
    ): Boolean =
        prepareStatement("SELECT 1 FROM $schema.sqlite_master WHERE type = 'table' AND name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { it.next() }
        }

    // The tables and indexes MagicDictionaryIndex reads, as lexical.db declares them
    private const val SCHEMA = """
        CREATE TABLE base (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE);
        CREATE TABLE surface (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE,
            base_id INTEGER NOT NULL REFERENCES base(id), notes TEXT);
        CREATE TABLE variant (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE);
        CREATE TABLE surface_variant (surface_id INTEGER NOT NULL, variant_id INTEGER NOT NULL,
            PRIMARY KEY (surface_id, variant_id));
        CREATE TABLE surface_extra_base (surface_id INTEGER NOT NULL, base_id INTEGER NOT NULL,
            PRIMARY KEY (surface_id, base_id));
        CREATE TABLE $META (key TEXT PRIMARY KEY, value TEXT NOT NULL)
    """

    private const val INDEXES = """
        CREATE INDEX surface_base_id_index ON surface(base_id);
        CREATE INDEX surface_variant_variant_id_index ON surface_variant(variant_id);
        CREATE INDEX surface_extra_base_base_id_index ON surface_extra_base(base_id)
    """
}

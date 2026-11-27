package io.github.kdroidfilter.seforimapp.features.sefaria

import io.github.kdroidfilter.seforimapp.logger.warnln
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Properties

// Pardes: Sefaria's topics, the persons its texts mention, its passages and Krupnik's dictionary, in a file of their own
// beside the library (pardes-sefaria/sefaria.db, SefariaPack) rather than in the app, the user's word of 2026-11-27:
// read by line, so that the lines' topics and mentions take no memory until asked for, and released and updated apart
// from the app.

/**
 * Sefaria's data file, opened read-only: its tables are [SCHEMA]'s, a line's topics and mentions read by its heRef, and
 * the small tables (the topics, the persons, their relations) read whole by the classes that hold them (SefariaData.kt).
 * One connection, its statements prepared once; a query at a time. Once closed, or when the file can't be read, a query
 * gives no rows: a screen still showing the data while its file is replaced or taken out shows none rather than failing.
 */
class SefariaDb private constructor(
    private val connection: Connection,
) : AutoCloseable {
    private val statements = HashMap<String, PreparedStatement>()
    private var closed = false

    /** The pack the file is ([META_PACK]), 0 when it doesn't say. */
    val pack: Int by lazy { meta(META_PACK)?.toIntOrNull() ?: 0 }

    fun meta(key: String): String? = rows("SELECT value FROM meta WHERE key = ?", key) { it.getString(1) }.firstOrNull()

    /** The rows [sql] gives with [args], each read by [read]. */
    @Synchronized
    fun <T> rows(
        sql: String,
        vararg args: Any,
        read: (ResultSet) -> T,
    ): List<T> {
        if (closed) return emptyList()
        return try {
            val statement = statements.getOrPut(sql) { connection.prepareStatement(sql) }
            args.forEachIndexed { index, arg ->
                when (arg) {
                    is Int -> statement.setInt(index + 1, arg)
                    is Long -> statement.setLong(index + 1, arg)
                    else -> statement.setString(index + 1, arg.toString())
                }
            }
            statement.executeQuery().use { result -> buildList { while (result.next()) add(read(result)) } }
        } catch (e: SQLException) {
            warnln { "[SefariaDb] Not read: ${e.message}" }
            emptyList()
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        statements.values.forEach { runCatching { it.close() } }
        statements.clear()
        runCatching { connection.close() }
    }

    companion object {
        /** The file's format, its meta's [META_FORMAT]: a file of another format is not read. */
        const val FORMAT = 1
        const val META_FORMAT = "format"
        const val META_PACK = "pack"

        /** Sefaria's data's tables: a line by its heRef once ([line]), a list of names joined by `|`. */
        val SCHEMA =
            listOf(
                "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE line (id INTEGER PRIMARY KEY, heref TEXT NOT NULL UNIQUE)",
                // A topic, its other names, its description and how many sources it has
                "CREATE TABLE topic (id INTEGER PRIMARY KEY, name TEXT NOT NULL, others TEXT NOT NULL, description TEXT NOT NULL, " +
                    "sources INTEGER NOT NULL)",
                "CREATE TABLE topic_root (topic INTEGER NOT NULL, ord INTEGER NOT NULL)",
                "CREATE TABLE topic_relation (src INTEGER NOT NULL, kind TEXT NOT NULL, dst INTEGER NOT NULL, ord INTEGER NOT NULL)",
                // The lines a topic is about, a source of its own ([source] 1) or a line of a source's range, in Sefaria's order
                "CREATE TABLE topic_line (line INTEGER NOT NULL, topic INTEGER NOT NULL, source INTEGER NOT NULL, ord INTEGER NOT NULL)",
                "CREATE INDEX topic_line_by_line ON topic_line (line)",
                "CREATE INDEX topic_line_by_topic ON topic_line (topic, source, ord)",
                // A person, and how many lines mention it
                "CREATE TABLE sage (id INTEGER PRIMARY KEY, name TEXT NOT NULL, others TEXT NOT NULL, description TEXT NOT NULL, " +
                    "generation TEXT NOT NULL, wiki TEXT NOT NULL, places INTEGER NOT NULL)",
                "CREATE TABLE ambiguous (id INTEGER PRIMARY KEY, name TEXT NOT NULL, options TEXT NOT NULL)",
                "CREATE TABLE sage_relation (src INTEGER NOT NULL, kind TEXT NOT NULL, dst INTEGER NOT NULL, ord INTEGER NOT NULL)",
                // A person's name in a line, as the line writes it
                "CREATE TABLE mention (line INTEGER NOT NULL, sage INTEGER NOT NULL, text TEXT NOT NULL, ord INTEGER NOT NULL)",
                "CREATE INDEX mention_by_line ON mention (line)",
                "CREATE INDEX mention_by_sage ON mention (sage, ord)",
                // A passage, its lines joined by `|`, and the passage each line is in (a sugya over its Mishnah)
                "CREATE TABLE sugya (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, source TEXT NOT NULL, lines TEXT NOT NULL)",
                "CREATE TABLE sugya_line (line INTEGER PRIMARY KEY, sugya INTEGER NOT NULL)",
                // Krupnik's dictionary, a row for each headword and for each other one, in Sefaria's order
                "CREATE TABLE krupnik (ord INTEGER PRIMARY KEY, headword TEXT NOT NULL, text TEXT NOT NULL)",
            )

        /** [file] opened read-only, or null when it is missing, can't be read or is of another format. */
        fun open(file: File): SefariaDb? {
            if (!file.isFile) return null
            return runCatching {
                // SQLite's SQLITE_OPEN_READONLY, as the library's lines are read (SefariaLines)
                val connection =
                    DriverManager.getConnection(
                        "jdbc:sqlite:${file.absolutePath}",
                        Properties().apply { setProperty("open_mode", "1") },
                    )
                val db = SefariaDb(connection)
                if (db.meta(META_FORMAT)?.toIntOrNull() == FORMAT) {
                    db
                } else {
                    db.close()
                    warnln { "[SefariaDb] $file is not of format $FORMAT" }
                    null
                }
            }.onFailure { warnln { "[SefariaDb] $file not read: ${it.message}" } }
                .getOrNull()
        }
    }
}

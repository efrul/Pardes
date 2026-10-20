package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.io.File

/**
 * This edition's stand-in for the main app's LibraryStore (same name and package, so that the main app's SearchService
 * and LibraryInstallerBase, shared through sharedAppSources, compile unchanged): what they read of it, on this edition's
 * [Library]. Each call asks [Library.open] for the library, which returns the open one, so a library opened again
 * (Settings › ספרייה › "חפש את הספרייה מחדש", a copy over the last) is the one read. Blocking: called off the main
 * thread, as SearchService does.
 */
class LibraryStore(private val library: Library) {
    val libraryDir: File get() = databaseFile.parentFile!!

    val databaseFile: File get() = library.open().file

    val releaseInfoFile: File get() = File(libraryDir, RELEASE_INFO_NAME)

    fun repository(): SeforimRepository = library.open().repository

    /** The main app's names for a library's items, the same desktop names. */
    companion object {
        const val DB_NAME = Library.DB_NAME
        const val TEXT_INDEX_NAME = "$DB_NAME.lucene"
        const val LOOKUP_INDEX_NAME = "$DB_NAME.lookup.lucene"
        const val LEXICAL_NAME = "lexical.db"
        const val CATALOG_NAME = "catalog.pb"
        const val RELEASE_INFO_NAME = "release_info.txt"

        /** The main app's record of a delta applied from files: never written here, removed with the library it was for. */
        const val LOCAL_VERSION_NAME = "local_update_version.txt"

        /** Suffix of an item still being installed; it only gets its real name once complete. */
        const val PARTIAL_SUFFIX = ".part"

        /** What a library consists of, in the order an install moves it into place: the database last. */
        val LIBRARY_ITEMS = listOf(LOOKUP_INDEX_NAME, TEXT_INDEX_NAME, LEXICAL_NAME, CATALOG_NAME, RELEASE_INFO_NAME, LOCAL_VERSION_NAME, DB_NAME)

        /** Bytes of the library in [dir]: its items, with the overflow chunks of a split database (DatabaseChunks). */
        fun libraryBytes(dir: File): Long = LIBRARY_ITEMS.sumOf { name ->
            val f = File(dir, name)
            if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
        } + DatabaseChunks.overflowChunks(File(dir, DB_NAME)).sumOf { it.length() }
    }
}

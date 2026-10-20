package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.io.File

/**
 * This edition's stand-in for the main app's LibraryStore (same name and package, so that the main app's SearchService,
 * shared through sharedAppSources, compiles unchanged): what the search reads of it, on this edition's [Library]. Each
 * call asks [Library.open] for the library, which returns the open one, so a library opened again (Settings › ספרייה ›
 * "חפש את הספרייה מחדש", a copy over the last) is the one read. Blocking: called off the main thread, as SearchService
 * does.
 */
class LibraryStore(private val library: Library) {
    val libraryDir: File get() = databaseFile.parentFile!!

    val databaseFile: File get() = library.open().file

    fun repository(): SeforimRepository = library.open().repository

    companion object {
        const val DB_NAME = Library.DB_NAME
    }
}

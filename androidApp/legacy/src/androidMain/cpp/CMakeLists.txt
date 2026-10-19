# libpardes_sqlite.so: SQLite itself, for Pardes on Android 4.4 to 7.1 (the legacy module). Android 4.4 ships SQLite
# 3.7.11, which cannot read the library's schema (an index on an expression needs 3.9), and requery's SQLite, which the
# main app bundles, is built for API 21. This one is built by NDK r25 for API 19, with:
# - the SQLite amalgamation that Gradle downloads (SQLITE_DIR, task sqliteAmalgamation in build.gradle.kts);
# - the app's multiplex VFS (MULTIPLEX_DIR, the main app's src/androidMain/cpp/multiplex.c), compiled in rather than
#   loaded as an extension, for a library kept in chunks on a FAT32 memory card;
# - pardes_sqlite.c: the JNI calls of db/NativeSqlite.kt and the search's pardes_match() function.
cmake_minimum_required(VERSION 3.22.1)
project(pardes_sqlite C)

add_library(pardes_sqlite SHARED
    ${SQLITE_DIR}/sqlite3.c
    ${MULTIPLEX_DIR}/multiplex.c
    pardes_sqlite.c)

target_include_directories(pardes_sqlite PRIVATE ${SQLITE_DIR} ${MULTIPLEX_DIR})

target_compile_definitions(pardes_sqlite PRIVATE
    # Set here, not by sqlite3.c, so that android_compat.h and every header see 64-bit file offsets too: the library
    # is 8 GB.
    _FILE_OFFSET_BITS=64
    # multiplex.c calls SQLite directly instead of through an extension's routine table.
    SQLITE_CORE
    # One connection is used by one thread at a time (NativeSqliteDriver holds a lock).
    SQLITE_THREADSAFE=2
    # No memory-mapped I/O: mmap64 is missing below API 21 on 32-bit, and old phones have little memory to map 8 GB.
    SQLITE_MAX_MMAP_SIZE=0
    # Temporary tables and indexes in memory: Android has no /tmp (Android's own SQLite is built the same way).
    SQLITE_TEMP_STORE=3
    SQLITE_DEFAULT_MEMSTATUS=0
    SQLITE_OMIT_LOAD_EXTENSION
    SQLITE_OMIT_DEPRECATED
    SQLITE_OMIT_SHARED_CACHE
    SQLITE_HAVE_ISNAN
    HAVE_USLEEP=1
    HAVE_LOCALTIME_R=1)

# What bionic lacks below API 21 (android_compat.h), seen by every file before its first #include.
target_compile_options(pardes_sqlite PRIVATE
    -include ${CMAKE_CURRENT_SOURCE_DIR}/android_compat.h
    -O2 -fvisibility=hidden -Wno-unused-function -Wno-unused-parameter)

target_link_libraries(pardes_sqlite PRIVATE log)
# 16 KB pages (Android 15 devices that use them), as the main app's library.
target_link_options(pardes_sqlite PRIVATE -Wl,-z,max-page-size=16384)

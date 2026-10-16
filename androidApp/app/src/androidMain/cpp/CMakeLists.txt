# The multiplex VFS as a loadable SQLite extension (libpardes_multiplex.so), for a library database kept in
# chunks of less than 4 GiB on a FAT32 memory card. It is loaded into the SQLite of requery's sqlite-android
# (SplitDatabase.kt) and calls it only through the extension routines, so it links to nothing but libc.
# cache.c adds one JNI function to the same library, used by the check of a moved library (LibraryMover.kt).
cmake_minimum_required(VERSION 3.22.1)
project(pardes_multiplex C)

add_library(pardes_multiplex SHARED multiplex.c cache.c)
target_include_directories(pardes_multiplex PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/sqlite)
target_compile_options(pardes_multiplex PRIVATE -O2 -Wall -Wno-unused-function -fvisibility=hidden)
# 16 KB pages (Android 15 devices that use them).
target_link_options(pardes_multiplex PRIVATE -Wl,-z,max-page-size=16384)

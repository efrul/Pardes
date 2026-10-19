/*
** Pardes on Android 4.4: what SQLite needs from bionic and bionic lacks below API 21, on the 32-bit ABIs (armeabi-v7a,
** x86; the 64-bit ones start at API 21). Included before anything else in every file (CMakeLists.txt, -include).
**
** SQLite is compiled with 64-bit file offsets (_FILE_OFFSET_BITS=64), as the library database is 8 GB. Bionic then
** turns mmap into mmap64, which its libc has only from API 21: SQLite would neither compile for API 19 nor load on
** Android 4.4. SQLite maps the WAL index (the -shm file) with it; pardes_mmap does what bionic's mmap64 does, through
** the mmap2 system call (pardes_sqlite.c). Every other call SQLite makes with 64-bit offsets (pread64, pwrite64,
** ftruncate64, lseek64) is in Android 4.4's libc.
*/
#ifndef PARDES_ANDROID_COMPAT_H
#define PARDES_ANDROID_COMPAT_H

#if defined(__ANDROID__) && !defined(__LP64__) && __ANDROID_API__ < 21
#include <sys/types.h>
#include <sys/mman.h>

void *pardes_mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset);
#define mmap pardes_mmap
#endif

#endif

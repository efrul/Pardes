/*
** Pardes: drops a file's pages from the page cache, for the check of a library
** copied to another place (LibraryMover.kt, PageCache). Right after the copy is
** written and flushed its pages are still in RAM, so reading its end back would
** not reach the storage; with them dropped it does, where Android lets the app's
** advice through (a path that goes through FUSE may still be served from the
** lower file system's cache). It has nothing to do with SQLite: it shares the
** library only so that the app ships a single native file.
*/
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <unistd.h>

/*
** PageCache.nativeDropCache(path): 0 once the file's cached pages are dropped,
** or an errno value (posix_fadvise returns its error instead of setting errno).
** Only clean pages go, so the file must have been flushed (fsync) first.
*/
JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_seforimapp_android_data_PageCache_nativeDropCache(JNIEnv *env, jobject thiz, jstring path){
  const char *zPath = (*env)->GetStringUTFChars(env, path, NULL);
  int fd, rc;
  (void)thiz;
  if( zPath==NULL ) return ENOMEM;
  fd = open(zPath, O_RDONLY | O_CLOEXEC);
  rc = fd<0 ? errno : 0;
  (*env)->ReleaseStringUTFChars(env, path, zPath);
  if( fd<0 ) return rc;
  rc = posix_fadvise(fd, 0, 0, POSIX_FADV_DONTNEED);
  close(fd);
  return rc;
}

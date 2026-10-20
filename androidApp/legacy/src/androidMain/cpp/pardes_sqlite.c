/*
** Pardes on Android 4.4: the JNI side of db/NativeSqlite.kt, over the SQLite compiled into this library.
**
** It gives Kotlin what SQLDelight's driver needs (open, prepare, bind, step, read a column) and the column names
** the search's dictionary reads (lexical.db, through data/NativeCursor.kt), and nothing more, so that a book row of
** 4 MB is read as a string, with no cursor window in between. At load it registers the multiplex
** VFS (multiplex.c) as the default one: a library kept in chunks on a FAT32 card (seforim.db, seforim.db001...) opens
** like one in a single file, and a single file larger than a chunk is read as it is.
**
** It also adds pardes_match(text, query), the line test of the search without the desktop's Lucene index
** (data/TextSearch.kt): when the library has no index, or the phone cannot hold or open it (data/LegacySearch.kt),
** lines are scanned, here in C.
*/
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "sqlite3.h"

int sqlite3_multiplex_initialize(const char *zOrigVfsName, int makeDefault);

#if defined(__ANDROID__) && !defined(__LP64__) && __ANDROID_API__ < 21
#include <errno.h>
#include <sys/syscall.h>
#include <unistd.h>

/* bionic's mmap64 (android_compat.h): the offset goes to mmap2 in units of 4096 bytes. */
void *pardes_mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset) {
  if (offset < 0 || (offset & 4095) != 0) {
    errno = EINVAL;
    return MAP_FAILED;
  }
  return (void *) syscall(__NR_mmap2, addr, length, prot, flags, fd, (long) (offset >> 12));
}
#endif

static jclass gExceptionClass;
static jmethodID gExceptionInit;

#define DB(h) ((sqlite3 *) (intptr_t) (h))
#define STMT(h) ((sqlite3_stmt *) (intptr_t) (h))

/* A message as JNI's modified UTF-8 takes it: SQLite's messages are UTF-8, and 4-byte sequences would abort the VM. */
static jstring newStringUtf(JNIEnv *env, const char *z) {
  char buf[1024];
  size_t i = 0;
  for (; *z && i < sizeof(buf) - 1; z++) {
    unsigned char c = (unsigned char) *z;
    if (c >= 0xF0) {
      buf[i++] = '?';
      while ((z[1] & 0xC0) == 0x80) z++;
    } else {
      buf[i++] = (char) c;
    }
  }
  buf[i] = 0;
  return (*env)->NewStringUTF(env, buf);
}

static void throwSqlite(JNIEnv *env, int code, const char *what, const char *msg) {
  char text[768];
  jstring jmsg;
  jobject ex;
  snprintf(text, sizeof(text), "%s: %s (code %d)", what, msg ? msg : sqlite3_errstr(code), code);
  jmsg = newStringUtf(env, text);
  if (jmsg == NULL) return;
  ex = (*env)->NewObject(env, gExceptionClass, gExceptionInit, (jint) code, jmsg);
  if (ex != NULL) (*env)->Throw(env, (jthrowable) ex);
}

static void throwFromDb(JNIEnv *env, sqlite3 *db, int rc, const char *what) {
  throwSqlite(env, db ? sqlite3_extended_errcode(db) : rc, what, db ? sqlite3_errmsg(db) : NULL);
}

/* ------------------------------------------------------------------ text normalization for the search */

enum { KEEP, DROP, SPACE };

/*
** What the search does with a character: letters and digits are kept; Hebrew points and cantillation marks, quotes,
** geresh and gershayim and invisible marks are dropped (so רש"י, רש״י and רשי meet, and a line with niqqud matches a
** query without it); maqaf, sof pasuq, other punctuation and spaces become one space.
*/
static int classify(unsigned c) {
  if (c < 0x80) {
    if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return KEEP;
    if (c == '"' || c == '\'' || c == '`') return DROP;
    return SPACE;
  }
  if (c >= 0x0591 && c <= 0x05C7) {
    if (c == 0x05BE || c == 0x05C0 || c == 0x05C3 || c == 0x05C6) return SPACE;
    return DROP;
  }
  if (c == 0x05F3 || c == 0x05F4) return DROP;
  if (c == 0x00AD || c == 0x034F || c == 0xFEFF) return DROP;
  if ((c >= 0x200B && c <= 0x200F) || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069)) return DROP;
  if (c >= 0x2018 && c <= 0x201F) return DROP;
  if (c == 0x00A0 || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F
      || c == 0x3000) return SPACE;
  if ((c >= 0x2010 && c <= 0x2017) || (c >= 0x2020 && c <= 0x2027)) return SPACE;
  if (c >= 0x0080 && c <= 0x00BF && c != 0x00AA && c != 0x00B5 && c != 0x00BA) return SPACE;
  return KEEP;
}

/* The code point at p (UTF-8, or the modified UTF-8 of JNI) and its length; a broken byte stands for itself. */
static unsigned decode(const unsigned char *p, const unsigned char *end, int *len) {
  unsigned c = p[0];
  if (c < 0x80) {
    *len = 1;
    return c;
  }
  if ((c & 0xE0) == 0xC0 && p + 1 < end && (p[1] & 0xC0) == 0x80) {
    *len = 2;
    return ((c & 0x1F) << 6) | (p[1] & 0x3F);
  }
  if ((c & 0xF0) == 0xE0 && p + 2 < end && (p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80) {
    *len = 3;
    return ((c & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F);
  }
  if ((c & 0xF8) == 0xF0 && p + 3 < end && (p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80 && (p[3] & 0xC0) == 0x80) {
    *len = 4;
    return ((c & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F);
  }
  *len = 1;
  return 0xFFFD;
}

/* Tags that end a word in a line's HTML (a <b> or <big> around a letter does not: <big>ב</big>ראשית is one word). */
static int isBlockTag(const unsigned char *name, size_t n) {
  static const char *const tags[] = {"br", "p", "div", "li", "ul", "ol", "tr", "td", "th", "table", "hr",
                                     "h1", "h2", "h3", "h4", "h5", "h6", "blockquote"};
  size_t i, k;
  for (i = 0; i < sizeof(tags) / sizeof(tags[0]); i++) {
    if (strlen(tags[i]) != n) continue;
    for (k = 0; k < n; k++) {
      unsigned char c = name[k];
      if (c >= 'A' && c <= 'Z') c = (unsigned char) (c - 'A' + 'a');
      if (c != (unsigned char) tags[i][k]) break;
    }
    if (k == n) return 1;
  }
  return 0;
}

static int isAsciiAlnum(unsigned char c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
}

/*
** Writes the search form of in[0..n) to out (room for n + 1 bytes: it never grows) and returns its length: words
** separated by single spaces, no space at either end, ASCII letters in lower case. With html, tags are skipped and
** entities read as spaces (&quot; and &#39; as the quotes they are, dropped).
*/
static size_t normalize(const unsigned char *in, size_t n, unsigned char *out, int html) {
  const unsigned char *p = in, *end = in + n;
  size_t o = 0;
  int pendingSpace = 0;
  while (p < end) {
    int len, kind;
    unsigned c;
    if (html && *p == '<') {
      const unsigned char *q = p + 1, *name;
      size_t nameLen = 0;
      while (q < end && *q != '>') q++;
      if (q < end) {
        name = p + 1;
        if (name < q && *name == '/') name++;
        while (name + nameLen < q && isAsciiAlnum(name[nameLen])) nameLen++;
        if (isBlockTag(name, nameLen)) pendingSpace = o > 0;
        p = q + 1;
        continue;
      }
    }
    if (html && *p == '&') {
      const unsigned char *q = p + 1;
      int k = 0;
      while (q < end && k < 10 && (isAsciiAlnum(*q) || *q == '#')) {
        q++;
        k++;
      }
      if (q < end && *q == ';' && k > 0) {
        size_t m = (size_t) (q - p - 1);
        int quote = (m == 4 && memcmp(p + 1, "quot", 4) == 0) || (m == 4 && memcmp(p + 1, "apos", 4) == 0)
                    || (m == 3 && (memcmp(p + 1, "#39", 3) == 0 || memcmp(p + 1, "#34", 3) == 0));
        if (!quote) pendingSpace = o > 0;
        p = q + 1;
        continue;
      }
    }
    c = decode(p, end, &len);
    kind = classify(c);
    if (kind == SPACE) {
      pendingSpace = o > 0;
    } else if (kind == KEEP) {
      if (pendingSpace) {
        out[o++] = ' ';
        pendingSpace = 0;
      }
      if (len == 1 && c >= 'A' && c <= 'Z') {
        out[o++] = (unsigned char) (c - 'A' + 'a');
      } else {
        memcpy(out + o, p, (size_t) len);
        o += (size_t) len;
      }
    }
    p += len;
  }
  out[o] = 0;
  return o;
}

/* A normalized query: its words, or the whole of it as one (a phrase). */
typedef struct {
  int count;
  char **words;
  char *text;
} Needle;

static void freeNeedle(void *p) {
  Needle *needle = (Needle *) p;
  if (!needle) return;
  sqlite3_free(needle->words);
  sqlite3_free(needle->text);
  sqlite3_free(needle);
}

static Needle *makeNeedle(const unsigned char *query, int n, int phrase) {
  Needle *needle = (Needle *) sqlite3_malloc(sizeof(Needle));
  char *s;
  int words = 0;
  if (!needle) return NULL;
  memset(needle, 0, sizeof(Needle));
  needle->text = (char *) sqlite3_malloc(n + 1);
  needle->words = (char **) sqlite3_malloc((int) sizeof(char *) * (n / 2 + 1));
  if (!needle->text || !needle->words) {
    freeNeedle(needle);
    return NULL;
  }
  normalize(query, (size_t) n, (unsigned char *) needle->text, 0);
  if (phrase) {
    needle->count = needle->text[0] ? 1 : 0;
    needle->words[0] = needle->text;
    return needle;
  }
  for (s = needle->text; *s;) {
    char *space = strchr(s, ' ');
    needle->words[words++] = s;
    if (!space) break;
    *space = 0;
    s = space + 1;
  }
  needle->count = words;
  return needle;
}

/*
** pardes_match(text, query): 1 when query is found in text (a line's HTML), both in their search form: the words in
** that order, whatever points, cantillation, punctuation or maqaf stand between or on them (קורין את שמע finds
** "וְקוֹרִין אֶת שְׁמַע"). pardes_match_all(text, query): 1 when every word of query is in text, anywhere. Either way a
** word may be part of a longer one (השמים is found for שמים). 0 otherwise.
*/
static void matchFunc(sqlite3_context *ctx, int argc, sqlite3_value **argv) {
  const unsigned char *text = sqlite3_value_text(argv[0]);
  int textLen = sqlite3_value_bytes(argv[0]);
  Needle *needle = (Needle *) sqlite3_get_auxdata(ctx, 1);
  unsigned char stackBuf[4096], *buf;
  int i, found = 1;
  (void) argc;
  if (!needle) {
    const unsigned char *query = sqlite3_value_text(argv[1]);
    if (!query) {
      sqlite3_result_int(ctx, 0);
      return;
    }
    needle = makeNeedle(query, sqlite3_value_bytes(argv[1]), sqlite3_user_data(ctx) != NULL);
    if (!needle) {
      sqlite3_result_error_nomem(ctx);
      return;
    }
    sqlite3_set_auxdata(ctx, 1, needle, freeNeedle);
    needle = (Needle *) sqlite3_get_auxdata(ctx, 1);
    if (!needle) {
      sqlite3_result_error_nomem(ctx);
      return;
    }
  }
  if (!text || needle->count == 0) {
    sqlite3_result_int(ctx, 0);
    return;
  }
  buf = textLen < (int) sizeof(stackBuf) ? stackBuf : (unsigned char *) sqlite3_malloc(textLen + 1);
  if (!buf) {
    sqlite3_result_error_nomem(ctx);
    return;
  }
  normalize(text, (size_t) textLen, buf, 1);
  for (i = 0; i < needle->count && found; i++) {
    if (!strstr((const char *) buf, needle->words[i])) found = 0;
  }
  if (buf != stackBuf) sqlite3_free(buf);
  sqlite3_result_int(ctx, found);
}

/* ------------------------------------------------------------------ JNI */

static jlong nOpen(JNIEnv *env, jclass cls, jstring path, jint flags) {
  sqlite3 *db = NULL;
  const char *zPath = (*env)->GetStringUTFChars(env, path, NULL);
  int rc;
  if (!zPath) return 0;
  rc = sqlite3_open_v2(zPath, &db, flags, NULL);
  (*env)->ReleaseStringUTFChars(env, path, zPath);
  if (rc != SQLITE_OK) {
    throwFromDb(env, db, rc, "open");
    sqlite3_close(db);
    return 0;
  }
  sqlite3_extended_result_codes(db, 1);
  sqlite3_busy_timeout(db, 5000);
  /* The user data tells the two apart: a phrase, or all the words. */
  sqlite3_create_function_v2(db, "pardes_match", 2, SQLITE_UTF8 | SQLITE_DETERMINISTIC, (void *) 1, matchFunc, NULL,
                             NULL, NULL);
  sqlite3_create_function_v2(db, "pardes_match_all", 2, SQLITE_UTF8 | SQLITE_DETERMINISTIC, NULL, matchFunc, NULL,
                             NULL, NULL);
  return (jlong) (intptr_t) db;
}

static void nClose(JNIEnv *env, jclass cls, jlong db) {
  sqlite3_close_v2(DB(db));
}

static jlong nPrepare(JNIEnv *env, jclass cls, jlong db, jstring sql) {
  sqlite3_stmt *stmt = NULL;
  const jchar *zSql = (*env)->GetStringChars(env, sql, NULL);
  jsize len = (*env)->GetStringLength(env, sql);
  int rc;
  if (!zSql) return 0;
  rc = sqlite3_prepare16_v2(DB(db), zSql, len * (int) sizeof(jchar), &stmt, NULL);
  (*env)->ReleaseStringChars(env, sql, zSql);
  if (rc != SQLITE_OK) {
    throwFromDb(env, DB(db), rc, "prepare");
    return 0;
  }
  return (jlong) (intptr_t) stmt;
}

static void nFinalize(JNIEnv *env, jclass cls, jlong stmt) {
  sqlite3_finalize(STMT(stmt));
}

static void nReset(JNIEnv *env, jclass cls, jlong stmt) {
  sqlite3_reset(STMT(stmt));
  sqlite3_clear_bindings(STMT(stmt));
}

static void checkBind(JNIEnv *env, jlong stmt, int rc) {
  if (rc != SQLITE_OK) throwFromDb(env, sqlite3_db_handle(STMT(stmt)), rc, "bind");
}

static void nBindNull(JNIEnv *env, jclass cls, jlong stmt, jint i) {
  checkBind(env, stmt, sqlite3_bind_null(STMT(stmt), i));
}

static void nBindLong(JNIEnv *env, jclass cls, jlong stmt, jint i, jlong value) {
  checkBind(env, stmt, sqlite3_bind_int64(STMT(stmt), i, (sqlite3_int64) value));
}

static void nBindDouble(JNIEnv *env, jclass cls, jlong stmt, jint i, jdouble value) {
  checkBind(env, stmt, sqlite3_bind_double(STMT(stmt), i, value));
}

static void nBindText(JNIEnv *env, jclass cls, jlong stmt, jint i, jstring value) {
  const jchar *chars = (*env)->GetStringChars(env, value, NULL);
  jsize len = (*env)->GetStringLength(env, value);
  int rc;
  if (!chars) return;
  rc = sqlite3_bind_text16(STMT(stmt), i, chars, len * (int) sizeof(jchar), SQLITE_TRANSIENT);
  (*env)->ReleaseStringChars(env, value, chars);
  checkBind(env, stmt, rc);
}

static void nBindBlob(JNIEnv *env, jclass cls, jlong stmt, jint i, jbyteArray value) {
  jsize len = (*env)->GetArrayLength(env, value);
  jbyte *bytes = (*env)->GetByteArrayElements(env, value, NULL);
  int rc;
  if (!bytes) return;
  rc = sqlite3_bind_blob(STMT(stmt), i, bytes, len, SQLITE_TRANSIENT);
  (*env)->ReleaseByteArrayElements(env, value, bytes, JNI_ABORT);
  checkBind(env, stmt, rc);
}

static jboolean nStep(JNIEnv *env, jclass cls, jlong stmt) {
  int rc = sqlite3_step(STMT(stmt));
  if (rc == SQLITE_ROW) return JNI_TRUE;
  if (rc == SQLITE_DONE) return JNI_FALSE;
  throwFromDb(env, sqlite3_db_handle(STMT(stmt)), rc, "step");
  return JNI_FALSE;
}

static jint nColumnCount(JNIEnv *env, jclass cls, jlong stmt) {
  return sqlite3_column_count(STMT(stmt));
}

/* A result column's name (its AS name), as UTF-16: the search's dictionary reads its columns by name. */
static jstring nColumnName(JNIEnv *env, jclass cls, jlong stmt, jint i) {
  const jchar *name = (const jchar *) sqlite3_column_name16(STMT(stmt), i);
  jsize len = 0;
  if (!name) {
    throwSqlite(env, SQLITE_NOMEM, "column name", NULL);
    return NULL;
  }
  while (name[len]) len++;
  return (*env)->NewString(env, name, len);
}

static jint nColumnType(JNIEnv *env, jclass cls, jlong stmt, jint i) {
  return sqlite3_column_type(STMT(stmt), i);
}

static jlong nColumnLong(JNIEnv *env, jclass cls, jlong stmt, jint i) {
  return (jlong) sqlite3_column_int64(STMT(stmt), i);
}

static jdouble nColumnDouble(JNIEnv *env, jclass cls, jlong stmt, jint i) {
  return sqlite3_column_double(STMT(stmt), i);
}

static jstring nColumnText(JNIEnv *env, jclass cls, jlong stmt, jint i) {
  const void *text = sqlite3_column_text16(STMT(stmt), i);
  if (!text) {
    if (sqlite3_column_type(STMT(stmt), i) != SQLITE_NULL) {
      throwSqlite(env, SQLITE_NOMEM, "column", NULL);
    }
    return NULL;
  }
  return (*env)->NewString(env, (const jchar *) text, sqlite3_column_bytes16(STMT(stmt), i) / (int) sizeof(jchar));
}

static jbyteArray nColumnBlob(JNIEnv *env, jclass cls, jlong stmt, jint i) {
  const void *blob = sqlite3_column_blob(STMT(stmt), i);
  int len = sqlite3_column_bytes(STMT(stmt), i);
  jbyteArray array;
  if (!blob && sqlite3_column_type(STMT(stmt), i) == SQLITE_NULL) return NULL;
  array = (*env)->NewByteArray(env, len);
  if (array && len > 0) (*env)->SetByteArrayRegion(env, array, 0, len, (const jbyte *) blob);
  return array;
}

static jint nChanges(JNIEnv *env, jclass cls, jlong db) {
  return sqlite3_changes(DB(db));
}

static void nInterrupt(JNIEnv *env, jclass cls, jlong db) {
  sqlite3_interrupt(DB(db));
}

static void nExec(JNIEnv *env, jclass cls, jlong db, jstring sql) {
  char *err = NULL;
  const char *zSql = (*env)->GetStringUTFChars(env, sql, NULL);
  int rc;
  if (!zSql) return;
  rc = sqlite3_exec(DB(db), zSql, NULL, NULL, &err);
  (*env)->ReleaseStringUTFChars(env, sql, zSql);
  if (rc != SQLITE_OK) {
    throwSqlite(env, sqlite3_extended_errcode(DB(db)), "exec", err ? err : sqlite3_errmsg(DB(db)));
  }
  sqlite3_free(err);
}

static jstring nVersion(JNIEnv *env, jclass cls) {
  return (*env)->NewStringUTF(env, sqlite3_libversion());
}

static jstring nNormalize(JNIEnv *env, jclass cls, jstring text) {
  const char *z = (*env)->GetStringUTFChars(env, text, NULL);
  size_t n;
  unsigned char *out;
  jstring result;
  if (!z) return NULL;
  n = strlen(z);
  out = (unsigned char *) malloc(n + 1);
  if (!out) {
    (*env)->ReleaseStringUTFChars(env, text, z);
    return NULL;
  }
  normalize((const unsigned char *) z, n, out, 1);
  (*env)->ReleaseStringUTFChars(env, text, z);
  result = (*env)->NewStringUTF(env, (const char *) out);
  free(out);
  return result;
}

static const JNINativeMethod gMethods[] = {
    {"open", "(Ljava/lang/String;I)J", (void *) nOpen},
    {"close", "(J)V", (void *) nClose},
    {"prepare", "(JLjava/lang/String;)J", (void *) nPrepare},
    {"finalizeStatement", "(J)V", (void *) nFinalize},
    {"reset", "(J)V", (void *) nReset},
    {"bindNull", "(JI)V", (void *) nBindNull},
    {"bindLong", "(JIJ)V", (void *) nBindLong},
    {"bindDouble", "(JID)V", (void *) nBindDouble},
    {"bindText", "(JILjava/lang/String;)V", (void *) nBindText},
    {"bindBlob", "(JI[B)V", (void *) nBindBlob},
    {"step", "(J)Z", (void *) nStep},
    {"columnCount", "(J)I", (void *) nColumnCount},
    {"columnName", "(JI)Ljava/lang/String;", (void *) nColumnName},
    {"columnType", "(JI)I", (void *) nColumnType},
    {"columnLong", "(JI)J", (void *) nColumnLong},
    {"columnDouble", "(JI)D", (void *) nColumnDouble},
    {"columnText", "(JI)Ljava/lang/String;", (void *) nColumnText},
    {"columnBlob", "(JI)[B", (void *) nColumnBlob},
    {"changes", "(J)I", (void *) nChanges},
    {"interrupt", "(J)V", (void *) nInterrupt},
    {"exec", "(JLjava/lang/String;)V", (void *) nExec},
    {"version", "()Ljava/lang/String;", (void *) nVersion},
    {"normalize", "(Ljava/lang/String;)Ljava/lang/String;", (void *) nNormalize},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  jclass cls, ex;
  (void) reserved;
  if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
  cls = (*env)->FindClass(env, "io/github/kdroidfilter/seforimapp/legacy/db/NativeSqlite");
  if (!cls) return JNI_ERR;
  if ((*env)->RegisterNatives(env, cls, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) != 0) return JNI_ERR;
  ex = (*env)->FindClass(env, "io/github/kdroidfilter/seforimapp/legacy/db/SqliteException");
  if (!ex) return JNI_ERR;
  gExceptionClass = (jclass) (*env)->NewGlobalRef(env, ex);
  gExceptionInit = (*env)->GetMethodID(env, ex, "<init>", "(ILjava/lang/String;)V");
  if (!gExceptionInit) return JNI_ERR;
  if (sqlite3_initialize() != SQLITE_OK) return JNI_ERR;
  /* The multiplex VFS over the default unix one, as the default. A second load in this process finds it registered. */
  sqlite3_multiplex_initialize(NULL, 1);
  return JNI_VERSION_1_6;
}

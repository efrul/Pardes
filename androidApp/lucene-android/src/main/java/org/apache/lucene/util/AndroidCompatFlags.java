/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

/**
 * Android: what the jar this class is built into can run, for {@link AndroidCompat}. This one is
 * the main jar's (the app, minSdk 26). The Android 4.4 jar (jarApi19 in ../build.gradle.kts)
 * compiles src/api19/java's class of this name instead.
 */
final class AndroidCompatFlags {
  private AndroidCompatFlags() {}

  /**
   * Whether the signature-polymorphic calls (MethodHandle.invokeExact in AndroidCompatMethodHandles,
   * VarHandle get/set in AndroidCompatApi33) are in this jar: true here, so AndroidCompat.API_30 and
   * API_33 follow the Android version. A method rather than a constant, which javac would copy into
   * AndroidCompat: AndroidCompat's class file stays the same in both jars.
   */
  static boolean polymorphicCalls() {
    return true;
  }
}

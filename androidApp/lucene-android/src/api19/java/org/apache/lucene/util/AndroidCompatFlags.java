/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

/**
 * Android: the Android 4.4 jar's (jarApi19 in ../build.gradle.kts, minSdk 19) class of this name,
 * compiled in place of src/main/java's. See there.
 */
final class AndroidCompatFlags {
  private AndroidCompatFlags() {}

  /**
   * False: D8 cannot dex signature-polymorphic calls below API 26 (AGP stops the build), so this
   * jar's AndroidCompatApi33 and AndroidCompatMethodHandles are stubs, and AndroidCompat.API_30 and
   * API_33 are false on every level. The 4.4 edition also installs on Android 11 and 13 and later
   * (minSdk 19, targetSdk 36): there it runs the reflected constructors and CompatVarHandle's own
   * code, as Android 8.0 to 10 do with the main jar.
   */
  static boolean polymorphicCalls() {
    return false;
  }
}

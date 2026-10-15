/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

/**
 * Android: java.util.HexFormat only exists from API 34. The :lucene-android rewriter retargets the
 * type to this class, which covers what lucene-core uses: {@code HexFormat.of().formatHex(bytes)}
 * (StringHelper.idToString, used for segment and commit ids).
 */
public final class CompatHexFormat {
  private static final CompatHexFormat INSTANCE = new CompatHexFormat();
  private static final char[] DIGITS = "0123456789abcdef".toCharArray();

  private CompatHexFormat() {}

  public static CompatHexFormat of() {
    return INSTANCE;
  }

  public String formatHex(byte[] bytes) {
    final char[] chars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      chars[i * 2] = DIGITS[(bytes[i] >> 4) & 0xF];
      chars[i * 2 + 1] = DIGITS[bytes[i] & 0xF];
    }
    return new String(chars);
  }
}

/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.util.Random;

/**
 * Android: java.util.SplittableRandom is API 24 and core library desugaring does not add it. In the Android 4.4 jar
 * (the rewriter's --api19 mode) the :lucene-android rewriter retargets it here (IntroSelector's pivot shuffle, the
 * HNSW graph builders' level draw): only a source of pseudo-random numbers, so java.util.Random serves.
 */
public final class CompatSplittableRandom {
  private final Random random;

  public CompatSplittableRandom() {
    this.random = new Random();
  }

  public CompatSplittableRandom(long seed) {
    this.random = new Random(seed);
  }

  public int nextInt(int origin, int bound) {
    if (origin >= bound) {
      throw new IllegalArgumentException("bound must be greater than origin");
    }
    final int n = bound - origin;
    if (n > 0) {
      return origin + random.nextInt(n);
    }
    int r;
    do {
      r = random.nextInt();
    } while (r < origin || r >= bound);
    return r;
  }

  public double nextDouble() {
    return random.nextDouble();
  }
}

/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

/**
 * Android: java.util.concurrent.CompletionException is API 24. In the Android 4.4 jar (the rewriter's --api19 mode)
 * the rewriter retargets it here (CheckIndex).
 */
public class CompatCompletionException extends RuntimeException {
  public CompatCompletionException(Throwable cause) {
    super(cause);
  }

  public CompatCompletionException(String message, Throwable cause) {
    super(message, cause);
  }
}

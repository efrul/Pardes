/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

/**
 * Android: ART has no java.lang.Runtime.Version. The :lucene-android rewriter retargets {@code
 * Runtime.version()} to {@link #current()} and the type to this class; Lucene only prints it into
 * segment diagnostics (IndexWriter.setDiagnostics).
 */
public final class CompatRuntimeVersion {
  private static final CompatRuntimeVersion CURRENT = new CompatRuntimeVersion();

  private final String version;

  private CompatRuntimeVersion() {
    final String runtime = System.getProperty("java.runtime.version");
    version =
        runtime != null
            ? runtime
            : System.getProperty("java.vm.name", "unknown")
                + " "
                + System.getProperty("java.vm.version", "");
  }

  public static CompatRuntimeVersion current() {
    return CURRENT;
  }

  @Override
  public String toString() {
    return version;
  }
}
